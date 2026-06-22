package org.magic.jmix.addons.minio.service;

import io.jmix.core.Messages;
import org.magic.jmix.addons.minio.MinioProperties;
import org.magic.jmix.addons.minio.dto.*;
import io.minio.*;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    private static final String MSG_PREFIX = "org.magic.jmix.addons.minio/";

    private static final String PLACEHOLDER_FILE = ".minio_placeholder";

    private final MinioProperties minioProperties;
    private final Messages messages;
    private final ExecutorService uploadThreadPool;

    // 缓存的 MinioClient 实例（懒重建：参数变化时才重建）
    private volatile MinioClient cachedClient;
    private volatile String cachedEndpoint;
    private volatile String cachedAccessKey;
    private volatile String cachedSecretKey;

    public MinioService(MinioProperties minioProperties, Messages messages,
                        @Qualifier("minio_uploadThreadPool") ExecutorService uploadThreadPool) {
        this.minioProperties = minioProperties;
        this.messages = messages;
        this.uploadThreadPool = uploadThreadPool;
    }

    private String msg(String key) {
        return messages.getMessage(MSG_PREFIX + key, Locale.getDefault());
    }

    private String msg(String key, Object... args) {
        return String.format(messages.getMessage(MSG_PREFIX + key, Locale.getDefault()), args);
    }

    /**
     * 获取 MinioClient 实例（懒重建）。
     * <p>
     * 检测到 endpoint/accessKey/secretKey 变化时才重建，否则复用缓存实例。
     * OkHttp 内部维护连接池和调度器线程池，复用可避免资源浪费。
     */
    private synchronized MinioClient getClient() {
        String endpoint = minioProperties.getEndpoint();
        String accessKey = minioProperties.getAccessKey();
        String secretKey = minioProperties.getSecretKey();

        if (cachedClient != null
                && eq(endpoint, cachedEndpoint)
                && eq(accessKey, cachedAccessKey)
                && eq(secretKey, cachedSecretKey)) {
            return cachedClient;
        }

        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        cachedClient = client;
        cachedEndpoint = endpoint;
        cachedAccessKey = accessKey;
        cachedSecretKey = secretKey;

        return client;
    }

    private static boolean eq(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    // ==================== 辅助方法 ====================

    public boolean isPlaceholder(String objectName) {
        return objectName != null && objectName.endsWith("/" + PLACEHOLDER_FILE);
    }

    public String extractParentPath(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return "";
        }

        // 去掉末尾的 '/'（文件夹路径以 '/' 结尾）
        if (objectName.endsWith("/")) {
            objectName = objectName.substring(0, objectName.length() - 1);
        }

        int lastSlash = objectName.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "";
        }
        return objectName.substring(0, lastSlash + 1);
    }

    public String extractFileName(String objectName) {
        if (objectName == null) {
            return "";
        }
        int lastSlash = objectName.lastIndexOf('/');
        if (lastSlash >= 0) {
            return objectName.substring(lastSlash + 1);
        }
        return objectName;
    }

    /**
     * 将 MinIO Item 统一构造为 MinioTreeNode。
     * 处理文件夹/文件类型判断、名称提取（文件夹去掉末尾斜杠）、大小、修改时间（安全解析）。
     * 供 listObjects / searchPaged 复用，避免构造逻辑重复。
     */
    private MinioTreeNode toTreeNode(String bucket, Item item) {
        String objectName = item.objectName();
        boolean isDir = item.isDir();

        // 安全获取 lastModified（解析失败保持为 null，不中断列举）
        LocalDateTime lastModified = null;
        try {
            if (item.lastModified() != null) {
                lastModified = item.lastModified().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception e) {
            log.debug("lastModified 解析失败，保持为 null: object={}", objectName);
        }

        return MinioTreeNode.builder()
                .id(bucket + "/" + objectName)
                .type(isDir ? NodeType.FOLDER : NodeType.FILE)
                .name(isDir ? extractFileName(objectName.substring(0, objectName.length() - 1))
                        : extractFileName(objectName))
                .path(objectName)
                .bucket(bucket)
                .size(isDir ? null : item.size())
                .lastModified(lastModified)
                .build();
    }

    public String formatSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return "-";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 生成预签名 URL（用于浏览器预览）。
     *
     * @param bucket        Bucket 名称
     * @param objectPath    对象路径
     * @param expirySeconds 过期时间（秒）
     * @return 预签名 URL（带正确的 Content-Type 和 inline disposition）
     */
    public String getPresignedUrl(String bucket, String objectPath, int expirySeconds) {
        try {
            // 获取文件的 Content-Type
            String contentType = detectContentType(objectPath);

            // 设置响应头参数，让浏览器预览而非下载
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("response-content-type", contentType);
            reqParams.put("response-content-disposition", "inline");

            return getClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .method(Method.GET)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .extraQueryParams(reqParams)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成预签名 URL 失败: bucket={}, path={}", bucket, objectPath, e);
            throw new RuntimeException(msg("service.presignedUrlFailed"), e);
        }
    }

    /**
     * 根据文件扩展名检测 Content-Type。
     */
    private String detectContentType(String objectPath) {
        String extension = getFileExtension(objectPath).toLowerCase();
        return switch (extension) {
            // 文档类型
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            // 图片类型
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            // 音频类型
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "aac" -> "audio/aac";
            // 视频类型
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "ogv" -> "video/ogg";
            // 文本类型
            case "txt" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "csv" -> "text/csv";
            // 默认
            default -> "application/octet-stream";
        };
    }

    /**
     * 获取文件扩展名。
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * 读取文本文件内容。
     *
     * @param bucket     Bucket 名称
     * @param objectPath 对象路径
     * @return 文本内容
     */
    public String readTextContent(String bucket, String objectPath) {
        try (InputStream stream = downloadFile(bucket, objectPath)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取文本内容失败: bucket={}, path={}", bucket, objectPath, e);
            throw new RuntimeException(msg("service.readTextFailed"), e);
        }
    }

    // ==================== Bucket 操作 ====================

    public List<MinioBucketDto> listBuckets() {
        try {
            List<io.minio.messages.Bucket> buckets = getClient().listBuckets();
            return buckets.stream()
                    .map(b -> new MinioBucketDto(b.name(),
                            b.creationDate() != null ?
                                    b.creationDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取 Bucket 列表失败", e);
            throw new RuntimeException(msg("service.connectionFailed"), e);
        }
    }

    public void createBucket(String name) {
        try {
            boolean exists = getClient().bucketExists(
                    BucketExistsArgs.builder().bucket(name).build()
            );
            if (exists) {
                throw new IllegalArgumentException(msg("service.bucketNameExists", name));
            }
            getClient().makeBucket(MakeBucketArgs.builder().bucket(name).build());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建 Bucket 失败: {}", name, e);
            throw new RuntimeException(msg("service.bucketCreateFailed", e.getMessage()), e);
        }
    }

    public void deleteBucket(String name) {
        try {
            // 先删除 Bucket 中的所有对象
            Iterable<io.minio.Result<Item>> items = getClient().listObjects(
                    ListObjectsArgs.builder().bucket(name).recursive(true).build()
            );

            // 收集所有对象名称
            List<DeleteObject> objectsToDelete = new ArrayList<>();
            for (io.minio.Result<Item> result : items) {
                objectsToDelete.add(new DeleteObject(result.get().objectName()));
            }

            // 批量删除对象
            if (!objectsToDelete.isEmpty()) {
                log.debug(msg("service.deletingBucketObjects"), name, objectsToDelete.size());
                Iterable<io.minio.Result<DeleteError>> errors = getClient().removeObjects(
                        RemoveObjectsArgs.builder().bucket(name).objects(objectsToDelete).build()
                );
                // 检查是否有删除错误
                for (io.minio.Result<DeleteError> error : errors) {
                    log.warn(msg("service.deleteObjectFailed"), error.get().objectName(), error.get().message());
                }
            }

            // 删除空 Bucket
            getClient().removeBucket(RemoveBucketArgs.builder().bucket(name).build());
        } catch (Exception e) {
            log.error(msg("service.bucketDeleteFailedLog"), name, e);
            throw new RuntimeException(msg("service.bucketDeleteFailed", e.getMessage()), e);
        }
    }

    /**
     * 重命名 Bucket。
     * MinIO 不支持直接重命名，需要复制所有对象到新 Bucket 再删除原 Bucket。
     */
    public void renameBucket(String oldName, String newName) {
        try {
            // 1. 创建新 Bucket
            createBucket(newName);

            // 2. 复制所有对象
            Iterable<io.minio.Result<Item>> objects = getClient().listObjects(
                    ListObjectsArgs.builder()
                            .bucket(oldName)
                            .recursive(true)
                            .build()
            );

            // 收集对象名称，避免二次遍历
            List<String> objectNames = new ArrayList<>();
            for (io.minio.Result<Item> result : objects) {
                Item item = result.get();
                String objectName = item.objectName();
                objectNames.add(objectName);

                getClient().copyObject(
                        CopyObjectArgs.builder()
                                .bucket(newName)
                                .object(objectName)
                                .source(CopySource.builder()
                                        .bucket(oldName)
                                        .object(objectName)
                                        .build())
                                .build()
                );
            }

            // 3. 删除原 Bucket 中的所有对象（批量删除）
            List<DeleteObject> objectsToDelete = objectNames.stream()
                    .map(DeleteObject::new)
                    .collect(Collectors.toList());
            if (!objectsToDelete.isEmpty()) {
                getClient().removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(oldName)
                                .objects(objectsToDelete)
                                .build()
                );
            }

            // 4. 删除空 Bucket
            deleteBucket(oldName);

            log.debug("重命名 Bucket: {} -> {}", oldName, newName);

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("重命名 Bucket 失败: {} -> {}", oldName, newName, e);
            throw new RuntimeException(msg("service.renameBucketFailed", e.getMessage()), e);
        }
    }

    public boolean bucketExists(String name) {
        try {
            return getClient().bucketExists(
                    BucketExistsArgs.builder().bucket(name).build()
            );
        } catch (Exception e) {
            log.error("检查 Bucket 存在失败: {}", name, e);
            return false;
        }
    }

    // ==================== 文件/文件夹操作 ====================

    public List<MinioTreeNode> listObjects(String bucket, String prefix) {
        try {
            if (prefix == null) {
                prefix = "";
            }

            Iterable<io.minio.Result<Item>> results = getClient().listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(false)
                            .build()
            );

            List<MinioTreeNode> nodes = new ArrayList<>();

            for (io.minio.Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                if (isPlaceholder(objectName)) {
                    continue;
                }

                MinioTreeNode node = toTreeNode(bucket, item);

                nodes.add(node);
            }

            nodes.sort((a, b) -> {
                if (a.getType() != b.getType()) {
                    return a.getType() == NodeType.FOLDER ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });

            return nodes;
        } catch (Exception e) {
            log.error("列出对象失败: bucket={}, prefix={}", bucket, prefix, e);
            throw new RuntimeException(msg("service.fileListFailed", e.getMessage()), e);
        }
    }

    public void createFolder(String bucket, String folderPath) {
        try {
            if (!folderPath.endsWith("/")) {
                folderPath += "/";
            }

            String placeholderPath = folderPath + PLACEHOLDER_FILE;

            getClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(placeholderPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );

            log.debug("创建文件夹: bucket={}, path={}", bucket, folderPath);
        } catch (Exception e) {
            log.error("创建文件夹失败: bucket={}, path={}", bucket, folderPath, e);
            throw new RuntimeException(msg("service.folderCreateFailed", e.getMessage()), e);
        }
    }

    /**
     * 统计文件夹中的文件数量（递归）
     */
    public int countFiles(String bucket, String folderPath) {
        try {
            if (!folderPath.endsWith("/")) {
                folderPath += "/";
            }

            Iterable<io.minio.Result<Item>> results = getClient().listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folderPath)
                            .recursive(true)
                            .build()
            );

            int count = 0;
            for (io.minio.Result<Item> result : results) {
                Item item = result.get();
                if (!isPlaceholder(item.objectName()) && !item.isDir()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.error("统计文件夹文件数失败: bucket={}, path={}", bucket, folderPath, e);
            return 0;
        }
    }

    /**
     * 列出文件夹中所有文件的对象路径（递归，排除占位文件和目录）
     *
     * @param bucket     Bucket 名称
     * @param folderPath 文件夹路径
     * @return 文件对象路径列表
     */
    public List<String> listFolderObjectPaths(String bucket, String folderPath) {
        try {
            if (!folderPath.endsWith("/")) {
                folderPath += "/";
            }

            Iterable<io.minio.Result<Item>> results = getClient().listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folderPath)
                            .recursive(true)
                            .build()
            );

            List<String> paths = new ArrayList<>();
            for (io.minio.Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                if (!isPlaceholder(objectName) && !item.isDir()) {
                    paths.add(objectName);
                }
            }
            return paths;
        } catch (Exception e) {
            log.error("列出文件夹对象失败: bucket={}, path={}", bucket, folderPath, e);
            throw new RuntimeException(msg("service.fileListFailed", e.getMessage()), e);
        }
    }

    /**
     * 检查文件夹是否存在
     *
     * @param bucket     Bucket 名称
     * @param folderPath 文件夹路径（以 / 结尾）
     * @return true 如果文件夹存在
     */
    private boolean folderExists(String bucket, String folderPath) {
        try {
            // 检查文件夹的占位文件是否存在
            String placeholderPath = folderPath + PLACEHOLDER_FILE;

            getClient().statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(placeholderPath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            // 对象不存在或其他错误
            return false;
        }
    }

    /**
     * 确保文件夹存在，不存在则创建
     *
     * @param bucket     Bucket 名称
     * @param folderPath 文件夹路径
     * @return true 如果创建了新文件夹，false 如果文件夹已存在
     */
    public boolean ensureFolderExists(String bucket, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            return false;
        }

        if (!folderPath.endsWith("/")) {
            folderPath += "/";
        }

        if (!folderExists(bucket, folderPath)) {
            createFolder(bucket, folderPath);
            return true;
        }
        return false;
    }

    public void deleteFolder(String bucket, String folderPath) {
        try {
            if (!folderPath.endsWith("/")) {
                folderPath += "/";
            }

            Iterable<io.minio.Result<Item>> results = getClient().listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folderPath)
                            .recursive(true)
                            .build()
            );

            List<DeleteObject> objectsToDelete = new ArrayList<>();
            for (io.minio.Result<Item> result : results) {
                Item item = result.get();
                objectsToDelete.add(new DeleteObject(item.objectName()));
            }

            if (!objectsToDelete.isEmpty()) {
                getClient().removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(bucket)
                                .objects(objectsToDelete)
                                .build()
                );
            }

            log.debug("删除文件夹: bucket={}, path={}, 删除对象数={}", bucket, folderPath, objectsToDelete.size());
        } catch (Exception e) {
            log.error("删除文件夹失败: bucket={}, path={}", bucket, folderPath, e);
            throw new RuntimeException(msg("service.folderDeleteFailed", e.getMessage()), e);
        }
    }

    public void uploadFile(String bucket, String objectName, InputStream stream, long size) {
        try {
            if (isPlaceholder(objectName)) {
                throw new IllegalArgumentException(msg("service.reservedFilename", PLACEHOLDER_FILE));
            }

            getClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(stream, size, -1)
                            .build()
            );

            log.debug("上传文件: bucket={}, object={}", bucket, objectName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传文件失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException(msg("service.uploadFailed", e.getMessage()), e);
        }
    }

    public InputStream downloadFile(String bucket, String objectName) {
        try {
            return getClient().getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("下载文件失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException(msg("service.downloadFailed", e.getMessage()), e);
        }
    }

    public void deleteFile(String bucket, String objectName) {
        try {
            getClient().removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );

            log.debug("删除文件: bucket={}, object={}", bucket, objectName);
        } catch (Exception e) {
            log.error("删除文件失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException(msg("service.fileDeleteFailed", e.getMessage()), e);
        }
    }

    public BatchDeleteResult batchDelete(String bucket, List<MinioTreeNode> items) {
        BatchDeleteResult result = BatchDeleteResult.builder()
                .deletedFiles(0)
                .deletedFolders(0)
                .failedItems(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();

        List<DeleteObject> allObjectsToDelete = new ArrayList<>();

        for (MinioTreeNode item : items) {
            try {
                if (item.getType() == NodeType.FILE) {
                    allObjectsToDelete.add(new DeleteObject(item.getPath()));
                    result.setDeletedFiles(result.getDeletedFiles() + 1);

                } else if (item.getType() == NodeType.FOLDER) {
                    String folderPath = item.getPath();
                    if (!folderPath.endsWith("/")) {
                        folderPath += "/";
                    }

                    Iterable<io.minio.Result<Item>> folderItems = getClient().listObjects(
                            ListObjectsArgs.builder()
                                    .bucket(bucket)
                                    .prefix(folderPath)
                                    .recursive(true)
                                    .build()
                    );

                    int folderFileCount = 0;
                    for (io.minio.Result<Item> r : folderItems) {
                        Item i = r.get();
                        allObjectsToDelete.add(new DeleteObject(i.objectName()));
                        if (!isPlaceholder(i.objectName())) {
                            folderFileCount++;
                        }
                    }
                    result.setDeletedFiles(result.getDeletedFiles() + folderFileCount);
                    result.setDeletedFolders(result.getDeletedFolders() + 1);
                }

            } catch (Exception e) {
                result.getFailedItems().add(item.getName());
                result.getErrors().add(item.getName() + ": " + e.getMessage());
            }
        }

        if (!allObjectsToDelete.isEmpty()) {
            try {
                // removeObjects 返回 Iterable，必须遍历才会实际执行删除
                Iterable<io.minio.Result<io.minio.messages.DeleteError>> deleteResults =
                    getClient().removeObjects(
                        RemoveObjectsArgs.builder()
                                .bucket(bucket)
                                .objects(allObjectsToDelete)
                                .build()
                    );

                // 遍历结果以触发实际删除，并检查错误
                for (io.minio.Result<io.minio.messages.DeleteError> r : deleteResults) {
                    try {
                        io.minio.messages.DeleteError error = r.get();
                        if (error != null) {
                            log.warn("删除对象失败: object={}, message={}",
                                error.objectName(), error.message());
                            result.getErrors().add(error.objectName() + ": " + error.message());
                        }
                    } catch (Exception e) {
                        log.error("处理删除结果失败", e);
                    }
                }
            } catch (Exception e) {
                log.error("批量删除失败", e);
                result.getErrors().add(msg("service.batchDeleteFailed", e.getMessage()));
            }
        }

        return result;
    }

    public PagedSearchResult searchPaged(String bucket, String keyword, String cursor, int pageSize) {
        try {
            keyword = keyword.toLowerCase();

            String startAfter = null;
            if (cursor != null && !cursor.isEmpty()) {
                try {
                    String raw = new String(Base64.getDecoder().decode(cursor));
                    startAfter = raw.split("\\|")[0];
                } catch (Exception e) {
                    // 游标无效，从头开始
                }
            }

            ListObjectsArgs.Builder argsBuilder = ListObjectsArgs.builder()
                    .bucket(bucket)
                    .recursive(true)
                    .maxKeys(1000);

            if (startAfter != null) {
                argsBuilder.startAfter(startAfter);
            }

            Iterable<io.minio.Result<Item>> items = getClient().listObjects(argsBuilder.build());

            List<MinioTreeNode> matchedItems = new ArrayList<>();
            String lastProcessedKey = startAfter;
            boolean hasMore = false;

            Iterator<io.minio.Result<Item>> iterator = items.iterator();
            while (iterator.hasNext()) {
                try {
                    Item item = iterator.next().get();
                    String objectName = item.objectName();
                    lastProcessedKey = objectName;

                    if (isPlaceholder(objectName)) {
                        continue;
                    }

                    String fileName = extractFileName(objectName);
                    if (fileName.toLowerCase().contains(keyword)) {
                        MinioTreeNode node = toTreeNode(bucket, item);

                        matchedItems.add(node);

                        if (matchedItems.size() >= pageSize) {
                            hasMore = iterator.hasNext();
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 忽略单个对象错误
                }
            }

            if (!hasMore) {
                hasMore = iterator.hasNext();
            }

            String nextCursor = null;
            if (hasMore && lastProcessedKey != null) {
                String raw = lastProcessedKey + "|" + matchedItems.size();
                nextCursor = Base64.getEncoder().encodeToString(raw.getBytes());
            }

            return PagedSearchResult.builder()
                    .items(matchedItems)
                    .nextCursor(nextCursor)
                    .hasMore(hasMore)
                    .totalFetched(matchedItems.size())
                    .build();

        } catch (Exception e) {
            log.error("搜索失败: bucket={}, keyword={}", bucket, keyword, e);
            throw new RuntimeException(msg("service.searchFailed", e.getMessage()), e);
        }
    }

    // ==================== 文件夹上传 ====================

    /**
     * 上传文件夹（递归上传所有文件，为每个子文件夹创建占位文件）
     *
     * @param bucket 目标 Bucket
     * @param targetPath 目标路径（MinIO 中的前缀）
     * @param localFolder 本地文件夹路径
     * @param progressConsumer 进度回调（可选）
     * @return 上传结果
     */
    public UploadFolderResult uploadFolder(String bucket, String targetPath,
            Path localFolder,
            Consumer<Double> progressConsumer) {

        UploadFolderResult result = UploadFolderResult.builder()
                .uploadedFiles(0)
                .createdFolders(0)
                .totalBytes(0)
                .failedFiles(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();

        try {
            // 规范化目标路径
            if (targetPath == null) {
                targetPath = "";
            } else if (!targetPath.isEmpty() && !targetPath.endsWith("/")) {
                targetPath += "/";
            }

            // 获取本地文件夹名称
            String folderName = localFolder.getFileName().toString();
            String remoteBasePath = targetPath + folderName + "/";

            // 创建根文件夹的占位文件
            createFolder(bucket, remoteBasePath);
            result.setCreatedFolders(1);

            // 收集所有文件（Files.walk 必须用 try-with-resources 关闭，否则目录句柄泄漏）
            List<Path> allFiles = new ArrayList<>();
            try (java.util.stream.Stream<Path> paths = Files.walk(localFolder)) {
                paths.filter(path -> !Files.isDirectory(path))
                     .forEach(allFiles::add);
            }

            // 计算总大小
            long totalSize = 0;
            for (Path file : allFiles) {
                try {
                    totalSize += Files.size(file);
                } catch (Exception e) {
                    // ignore
                }
            }

            // 记录已上传大小
            long uploadedSize = 0;

            // 跟踪已创建的文件夹
            Set<String> createdFolders = new HashSet<>();

            // 上传所有文件
            for (Path file : allFiles) {
                try {
                    // 计算相对路径
                    String relativePath = localFolder.relativize(file).toString()
                        .replace(File.separatorChar, '/');
                    String remotePath = remoteBasePath + relativePath;

                    // 确保父文件夹存在（创建占位文件）
                    String parentPath = extractParentPath(remotePath);
                    if (parentPath != null && !parentPath.isEmpty() && !createdFolders.contains(parentPath)) {
                        // 递归创建所有父文件夹
                        ensureParentFoldersExist(bucket, parentPath, createdFolders);
                    }

                    // 上传文件
                    long fileSize = Files.size(file);
                    try (InputStream stream = Files.newInputStream(file)) {
                        uploadFile(bucket, remotePath, stream, fileSize);
                    }

                    result.setUploadedFiles(result.getUploadedFiles() + 1);
                    result.setTotalBytes(result.getTotalBytes() + fileSize);

                    // 更新进度
                    uploadedSize += fileSize;
                    if (progressConsumer != null && totalSize > 0) {
                        double progress = (double) uploadedSize / totalSize;
                        progressConsumer.accept(progress);
                    }

                } catch (Exception e) {
                    result.getFailedFiles().add(file.toString());
                    result.getErrors().add(file.getFileName() + ": " + e.getMessage());
                }
            }

            // 更新创建的文件夹数量
            result.setCreatedFolders(result.getCreatedFolders() + createdFolders.size());

            log.debug("上传文件夹完成: bucket={}, local={}, uploaded={} files, created={} folders",
                bucket, localFolder, result.getUploadedFiles(), result.getCreatedFolders());

        } catch (Exception e) {
            log.error("上传文件夹失败: bucket={}, local={}", bucket, localFolder, e);
            result.getErrors().add(msg("service.uploadFailed", e.getMessage()));
        }

        return result;
    }

    /**
     * 确保父文件夹存在（递归创建占位文件）
     */
    private void ensureParentFoldersExist(String bucket, String folderPath, Set<String> createdFolders) {
        if (folderPath == null || folderPath.isEmpty()) {
            return;
        }

        // 分解路径并逐级创建
        String[] parts = folderPath.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            currentPath.append(part).append("/");
            String path = currentPath.toString();

            if (!createdFolders.contains(path)) {
                try {
                    createFolder(bucket, path);
                    createdFolders.add(path);
                } catch (Exception e) {
                    // 文件夹可能已存在，忽略
                }
            }
        }
    }

    // ==================== 批量上传 ====================

    /**
     * 异步批量上传文件到 MinIO。
     * <p>
     * 使用共享线程池，线程数由配置决定（默认 CPU核数 × 2）。
     * 每个文件作为独立任务提交，线程池自动调度执行。
     * 单个文件上传失败不会影响其他文件的上传。
     *
     * @param bucket 目标 Bucket 名称
     * @param requests 上传请求列表
     * @return CompletableFuture 包含批量上传结果
     */
    public CompletableFuture<BatchUploadResult> batchUploadAsync(String bucket, List<UploadRequest> requests) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException(msg("service.bucketRequired"));
        }
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(BatchUploadResult.builder()
                    .successCount(0)
                    .totalBytes(0)
                    .failedFiles(new ArrayList<>())
                    .build());
        }

        log.debug(msg("service.batchUploadStart"), bucket, requests.size());

        // 为每个文件创建独立的异步任务
        List<CompletableFuture<BatchUploadResult>> futures = requests.stream()
                .map(req -> CompletableFuture.supplyAsync(
                        () -> uploadSingle(bucket, req),
                        uploadThreadPool
                ))
                .collect(Collectors.toList());

        return mergeResults(futures);
    }

    /**
     * 上传单个文件（在线程池中执行）。
     *
     * @param bucket 目标 Bucket
     * @param request 上传请求
     * @return 上传结果（成功或失败）
     */
    private BatchUploadResult uploadSingle(String bucket, UploadRequest request) {
        BatchUploadResult result = BatchUploadResult.builder()
                .successCount(0)
                .totalBytes(0)
                .failedFiles(new ArrayList<>())
                .build();

        InputStream stream = null;
        try {
            stream = resolveInputStream(request);
            uploadFile(bucket, request.getObjectName(), stream, request.getSize());

            result.setSuccessCount(1);
            result.setTotalBytes(request.getSize());

            log.debug(msg("service.batchUploadSuccess"), bucket, request.getObjectName());

        } catch (Exception e) {
            log.warn(msg("service.batchUploadFileFailed"), bucket, request.getObjectName(), e.getMessage());

            FailedFile failedFile = FailedFile.builder()
                    .objectName(request.getObjectName())
                    .errorMessage(e.getMessage())
                    .build();
            result.getFailedFiles().add(failedFile);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }

        return result;
    }

    /**
     * 合并多个异步上传结果。
     *
     * @param futures 异步任务列表
     * @return 合并后的结果
     */
    private CompletableFuture<BatchUploadResult> mergeResults(
            List<CompletableFuture<BatchUploadResult>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .reduce(new BatchUploadResult(), BatchUploadResult::merge));
    }

    /**
     * 解析上传请求的输入流。
     * <p>
     * 对于 Path 和 File 类型的请求，会创建新的 InputStream。
     * 对于已经包含 InputStream 的请求，直接返回。
     *
     * @param request 上传请求
     * @return 输入流
     * @throws IOException 如果无法创建输入流
     */
    private InputStream resolveInputStream(UploadRequest request) throws IOException {
        // 如果已有 InputStream，直接返回
        InputStream existing = request.getInputStream();
        if (existing != null) {
            return existing;
        }

        // 尝试从 Path 创建
        if (request.getPath() != null) {
            return Files.newInputStream(request.getPath());
        }

        // 尝试从 File 创建
        if (request.getFile() != null) {
            return new java.io.FileInputStream(request.getFile());
        }

        throw new IOException(msg("service.resolveInputStreamFailed", request.getObjectName()));
    }
}
