# Batch Upload Design

## Overview

为 MinioService 添加面向开发者的多线程批量上传接口，支持大批量文件高效上传。

## Interface

```java
CompletableFuture<BatchUploadResult> batchUpload(String bucket, List<UploadRequest> requests);
```

- **返回**：`CompletableFuture<BatchUploadResult>`，调用方可通过 `get()` 或 `thenApply()` 获取最终结果
- **行为**：立即返回，文件上传在后台线程池异步执行

## UploadRequest

通用入参抽象，通过静态工厂方法创建：

```java
UploadRequest.fromInputStream(String objectName, InputStream stream, long size)
UploadRequest.fromPath(Path localPath, String objectName)
UploadRequest.fromFile(File file, String objectName)
```

- `objectName`：上传到 MinIO 的目标路径/文件名
- `fromPath` 和 `fromFile` 的 `objectName` 可选，不传时使用原文件名

## BatchUploadResult

```java
public class BatchUploadResult {
    private int successCount;
    private List<FailedFile> failedFiles;
    private long totalBytes;
}

public class FailedFile {
    private String objectName;
    private String errorMessage;
}
```

## Thread Pool

插件内置线程池，通过 `application.properties` 配置：

```properties
jmix.minio.upload.thread-pool-size=10
```

默认 10 个线程。线程池在 `MinioConfiguration` 中创建为 Spring Bean，`MinioService` 注入使用。

## Internal Flow

1. 将 `requests` 按 `batchSize`（默认 50）分成 N 批
2. 每批提交为一个 `CompletableFuture` 到线程池
3. 每个线程顺序处理自己那一批文件，跳过失败继续
4. 使用 `CompletableFuture.allOf()` 等待所有批次完成
5. 通过 `reduce` 合并所有批次结果为 `BatchUploadResult`
6. `future.complete(result)` 返回最终结果

## Error Handling

- 某个文件上传失败不影响其他文件
- 失败的文件记录到 `BatchUploadResult.failedFiles`
- 所有批次完成后统一返回结果

## Files to Create/Modify

1. **New**: `UploadRequest.java` - 入参 DTO
2. **New**: `BatchUploadResult.java` - 结果 DTO
3. **New**: `FailedFile.java` - 失败文件 DTO
4. **Modify**: `MinioService.java` - 添加 `batchUpload()` 方法
5. **Modify**: `MinioConfiguration.java` - 创建线程池 Bean
6. **Modify**: `MinioProperties.java` - 添加线程池配置属性
7. **Modify**: `messages.properties` / `messages_en.properties` - 添加相关消息键
