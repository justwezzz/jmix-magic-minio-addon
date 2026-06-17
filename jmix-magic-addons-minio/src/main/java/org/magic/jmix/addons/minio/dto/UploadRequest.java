package org.magic.jmix.addons.minio.dto;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 批量上传请求对象。
 * 抽象不同的输入源（InputStream、Path、File）为统一的请求对象。
 */
public class UploadRequest {

    private String objectName;
    private InputStream inputStream;
    private long size;
    private Path path;
    private File file;

    private UploadRequest() {
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 从 InputStream 创建上传请求。
     *
     * @param objectName MinIO 中的目标对象名称
     * @param inputStream 输入流
     * @param size 数据大小（字节）
     * @return 新的上传请求
     */
    public static UploadRequest fromInputStream(String objectName, InputStream inputStream, long size) {
        Objects.requireNonNull(objectName, "objectName 不能为空");
        Objects.requireNonNull(inputStream, "inputStream 不能为空");
        if (size < 0) {
            throw new IllegalArgumentException("size 不能为负数");
        }
        UploadRequest request = new UploadRequest();
        request.objectName = objectName;
        request.inputStream = inputStream;
        request.size = size;
        return request;
    }

    /**
     * 从本地 Path 创建上传请求，指定目标对象名称。
     * <p>
     * 注意：此方法不会立即打开 InputStream，而是在上传时按需创建，
     * 以避免资源泄漏。
     *
     * @param localPath 本地文件路径
     * @param objectName MinIO 中的目标对象名称
     * @return 新的上传请求
     * @throws IOException 如果文件无法读取
     */
    public static UploadRequest fromPath(Path localPath, String objectName) throws IOException {
        Objects.requireNonNull(localPath, "localPath 不能为空");
        Objects.requireNonNull(objectName, "objectName 不能为空");
        if (!Files.exists(localPath)) {
            throw new IllegalArgumentException("localPath 不存在: " + localPath);
        }
        if (!Files.isRegularFile(localPath)) {
            throw new IllegalArgumentException("localPath 不是普通文件: " + localPath);
        }
        UploadRequest request = new UploadRequest();
        request.objectName = objectName;
        request.path = localPath;
        request.size = Files.size(localPath);
        // 不在此处打开 InputStream，由 MinioService.resolveInputStream() 按需创建
        return request;
    }

    /**
     * 从本地 Path 创建上传请求，使用原文件名作为目标对象名称。
     *
     * @param localPath 本地文件路径
     * @return 新的上传请求
     * @throws IOException 如果文件无法读取
     */
    public static UploadRequest fromPath(Path localPath) throws IOException {
        return fromPath(localPath, localPath.getFileName().toString());
    }

    /**
     * 从 File 创建上传请求，指定目标对象名称。
     * <p>
     * 注意：此方法不会立即打开 InputStream，而是在上传时按需创建，
     * 以避免资源泄漏。
     *
     * @param file 要上传的文件
     * @param objectName MinIO 中的目标对象名称
     * @return 新的上传请求
     * @throws IOException 如果文件无法读取
     */
    public static UploadRequest fromFile(File file, String objectName) throws IOException {
        Objects.requireNonNull(file, "file 不能为空");
        Objects.requireNonNull(objectName, "objectName 不能为空");
        if (!file.exists()) {
            throw new IllegalArgumentException("file 不存在: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("file 不是普通文件: " + file.getAbsolutePath());
        }
        UploadRequest request = new UploadRequest();
        request.objectName = objectName;
        request.file = file;
        request.size = file.length();
        // 不在此处打开 InputStream，由 MinioService.resolveInputStream() 按需创建
        return request;
    }

    /**
     * 从 File 创建上传请求，使用原文件名作为目标对象名称。
     *
     * @param file 要上传的文件
     * @return 新的上传请求
     * @throws IOException 如果文件无法读取
     */
    public static UploadRequest fromFile(File file) throws IOException {
        return fromFile(file, file.getName());
    }

    // ==================== Getter 方法 ====================

    public String getObjectName() {
        return objectName;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getSize() {
        return size;
    }

    public Path getPath() {
        return path;
    }

    public File getFile() {
        return file;
    }
}
