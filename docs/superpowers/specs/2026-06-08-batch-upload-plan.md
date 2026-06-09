# Batch Upload Implementation Plan

基于设计文档 `2026-06-08-batch-upload-design.md` 的实现步骤。

## Step 1: 创建 DTO 类

在 `org.magic.addons.minio.dto` 包下创建三个类：

### 1.1 UploadRequest.java

```java
public class UploadRequest {
    private String objectName;
    private InputStream inputStream;
    private Long size;
    private Path path;
    private File file;

    // 三种工厂方法
    public static UploadRequest fromInputStream(String objectName, InputStream stream, long size)
    public static UploadRequest fromPath(Path localPath, String objectName)
    public static UploadRequest fromFile(File file, String objectName)

    // fromPath/fromFile 不传 objectName 时使用原文件名的重载
    public static UploadRequest fromPath(Path localPath)
    public static UploadRequest fromFile(File file)
}
```

### 1.2 BatchUploadResult.java

```java
public class BatchUploadResult {
    private int successCount;
    private List<FailedFile> failedFiles;
    private long totalBytes;

    // merge 方法用于合并多个批次结果
    public BatchUploadResult merge(BatchUploadResult other)
}
```

### 1.3 FailedFile.java

```java
public class FailedFile {
    private String objectName;
    private String errorMessage;
}
```

## Step 2: 扩展 MinioProperties

在 `Upload` 内部类中添加线程池配置：

```java
public static class Upload {
    private String maxSize = "50MB";

    // 新增
    private int threadPoolSize = 10;
    private int batchSize = 50;
}
```

## Step 3: 创建线程池 Bean

在 `MinioConfiguration.java` 中添加：

```java
@Bean("minio_uploadThreadPool")
public ExecutorService uploadThreadPool(MinioProperties properties) {
    return Executors.newFixedThreadPool(
        properties.getUpload().getThreadPoolSize(),
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "minio-upload-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        }
    );
}
```

## Step 4: 实现 MinioService.batchUpload()

在 `MinioService.java` 中添加：

```java
@Autowired
@Qualifier("minio_uploadThreadPool")
private ExecutorService uploadThreadPool;

public CompletableFuture<BatchUploadResult> batchUpload(String bucket, List<UploadRequest> requests) {
    return CompletableFuture.supplyAsync(() -> {
        int batchSize = properties.getUpload().getBatchSize();
        List<List<UploadRequest>> batches = partition(requests, batchSize);

        List<CompletableFuture<BatchUploadResult>> futures = batches.stream()
            .map(batch -> CompletableFuture.supplyAsync(
                () -> uploadBatch(bucket, batch),
                uploadThreadPool
            ))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
            .map(CompletableFuture::join)
            .reduce(new BatchUploadResult(), BatchUploadResult::merge);
    }, uploadThreadPool);
}

private BatchUploadResult uploadBatch(String bucket, List<UploadRequest> batch) {
    // 顺序处理这一批文件，跳过失败继续
}

private InputStream resolveInputStream(UploadRequest request) {
    // 根据 request 类型（inputStream/path/file）获取 InputStream
}

private List<List<UploadRequest>> partition(List<UploadRequest> list, int size) {
    // 分批工具方法
}
```

## Step 5: 添加消息键

在 `messages.properties` 和 `messages_en.properties` 中添加：

```properties
# messages.properties
org.magic.addons.minio/service.batchUploadFailed=批量上传失败: %s
org.magic.addons.minio/service.batchUploadComplete=批量上传完成，成功 %d 个，失败 %d 个

# messages_en.properties
org.magic.addons.minio/service.batchUploadFailed=Batch upload failed: %s
org.magic.addons.minio/service.batchUploadComplete=Batch upload completed, %d succeeded, %d failed
```

## File Summary

| File | Action |
|------|--------|
| `dto/UploadRequest.java` | Create |
| `dto/BatchUploadResult.java` | Create |
| `dto/FailedFile.java` | Create |
| `service/MinioService.java` | Modify - add batchUpload() |
| `MinioConfiguration.java` | Modify - add thread pool bean |
| `MinioProperties.java` | Modify - add thread pool config |
| `messages.properties` | Modify - add keys |
| `messages_en.properties` | Modify - add keys |
