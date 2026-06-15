# 批量上传接口重构设计

## 背景

当前批量上传接口 `batchUpload` 使用固定线程池（10线程）和固定批次大小（50文件/批）。需要重构为更灵活的模型，并新增一个允许调用者指定线程数的接口。

## 变更概览

| 项目 | 当前实现 | 重构后 |
|------|---------|--------|
| 接口名称 | `batchUpload` | `batchUploadAsync` |
| 线程数默认值 | 固定 10 | CPU核数 × 2 |
| 线程数配置 | `threadPoolSize` | `threadPoolSize`（可覆盖默认值） |
| 任务分配 | 按 batchSize 分片，每线程处理一批 | 每任务处理单文件，线程池自动调度 |
| batchSize | 必需配置 | 移除（不再需要） |

新增接口：`batchUpload(bucket, requests, threadCount)` - 调用者指定线程数，使用临时线程池。

## 详细设计

### 1. 配置类变更

**MinioProperties.Upload**

```java
public static class Upload {
    private String maxSize = "50MB";
    private Integer threadPoolSize;  // null 表示使用默认值（CPU*2）

    public int getThreadPoolSize() {
        // 返回配置值或默认值
        return threadPoolSize != null ? threadPoolSize : Runtime.getRuntime().availableProcessors() * 2;
    }

    public void setThreadPoolSize(Integer threadPoolSize) {
        if (threadPoolSize != null && threadPoolSize <= 0) {
            throw new IllegalArgumentException("threadPoolSize must be greater than 0");
        }
        this.threadPoolSize = threadPoolSize;
    }

    // 移除 batchSize 字段
}
```

**配置文件示例 (application.yml)**

```yaml
jmix:
  minio:
    upload:
      max-size: 50MB
      thread-pool-size: 20  # 可选，不配置则使用 CPU*2
```

### 2. MinioService 接口变更

**现有接口重命名**

```java
/**
 * 异步批量上传文件到 MinIO。
 * 使用共享线程池，线程数由配置决定（默认 CPU核数 × 2）。
 */
public CompletableFuture<BatchUploadResult> batchUploadAsync(String bucket, List<UploadRequest> requests)
```

**新增接口**

```java
/**
 * 批量上传文件到 MinIO，使用临时线程池。
 * 线程池在上传完成后自动关闭。
 *
 * @param bucket 目标 Bucket 名称
 * @param requests 上传请求列表
 * @param threadCount 线程数
 * @return CompletableFuture 包含批量上传结果
 */
public CompletableFuture<BatchUploadResult> batchUpload(
        String bucket,
        List<UploadRequest> requests,
        int threadCount)
```

### 3. 处理逻辑变更

**batchUploadAsync 实现要点：**

1. 参数校验（bucket 非空、requests 非空）
2. 为每个文件创建独立任务
3. 提交到共享线程池 `uploadThreadPool`
4. 使用 `CompletableFuture.allOf` 等待所有任务完成
5. 合并结果返回

**batchUpload(bucket, requests, threadCount) 实现要点：**

1. 参数校验（bucket 非空、requests 非空、threadCount > 0）
2. 创建临时线程池 `Executors.newFixedThreadPool(threadCount)`
3. 为每个文件创建独立任务并提交
4. 使用 `whenComplete` 确保线程池关闭
5. 返回 Future

**核心代码结构：**

```java
public CompletableFuture<BatchUploadResult> batchUploadAsync(String bucket, List<UploadRequest> requests) {
    if (bucket == null || bucket.isBlank()) {
        throw new IllegalArgumentException(msg("service.bucketRequired"));
    }
    if (requests == null || requests.isEmpty()) {
        return CompletableFuture.completedFuture(emptyResult());
    }

    log.info("异步批量上传开始: bucket={}, 文件数={}", bucket, requests.size());

    List<CompletableFuture<BatchUploadResult>> futures = requests.stream()
            .map(req -> CompletableFuture.supplyAsync(
                    () -> uploadSingle(bucket, req),
                    uploadThreadPool
            ))
            .collect(Collectors.toList());

    return mergeResults(futures);
}

public CompletableFuture<BatchUploadResult> batchUpload(
        String bucket, List<UploadRequest> requests, int threadCount) {
    if (bucket == null || bucket.isBlank()) {
        throw new IllegalArgumentException(msg("service.bucketRequired"));
    }
    if (threadCount <= 0) {
        throw new IllegalArgumentException("threadCount must be greater than 0");
    }
    if (requests == null || requests.isEmpty()) {
        return CompletableFuture.completedFuture(emptyResult());
    }

    log.info("批量上传开始: bucket={}, 文件数={}, 线程数={}", bucket, requests.size(), threadCount);

    ExecutorService tempPool = Executors.newFixedThreadPool(threadCount);

    List<CompletableFuture<BatchUploadResult>> futures = requests.stream()
            .map(req -> CompletableFuture.supplyAsync(
                    () -> uploadSingle(bucket, req),
                    tempPool
            ))
            .collect(Collectors.toList());

    return mergeResults(futures)
            .whenComplete((result, ex) -> tempPool.shutdown());
}

private BatchUploadResult uploadSingle(String bucket, UploadRequest request) {
    // 单文件上传逻辑，失败时记录错误
}

private CompletableFuture<BatchUploadResult> mergeResults(
        List<CompletableFuture<BatchUploadResult>> futures) {
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .reduce(new BatchUploadResult(), BatchUploadResult::merge));
}
```

### 4. 测试变更

**MinioServiceMockTest 更新：**

1. 重命名测试方法：`batchUpload_xxx` → `batchUploadAsync_xxx`
2. 移除 `batchSize` 相关 mock 设置
3. 新增测试：
   - `batchUploadAsync_shouldUseDefaultThreadPoolSize` - 验证默认线程数 = CPU*2
   - `batchUpload_shouldCreateTempThreadPool` - 验证临时线程池创建
   - `batchUpload_shouldShutdownPoolAfterCompletion` - 验证线程池自动关闭
   - `batchUpload_shouldThrowException_forInvalidThreadCount` - 验证参数校验

### 5. 兼容性考虑

- **配置兼容**：移除 `batchSize` 字段，旧配置文件中该字段将被忽略（Spring Boot 宽松绑定）
- **接口兼容**：`batchUpload` 方法签名变更，调用方需修改代码
- **线程池兼容**：共享线程池大小可能变化（10 → CPU*2），需在文档中说明

## 实现步骤

1. 修改 `MinioProperties.Upload`：移除 `batchSize`，修改 `threadPoolSize` 默认值逻辑
2. 修改 `MinioConfiguration`：更新线程池创建逻辑
3. 修改 `MinioService`：
   - 重命名 `batchUpload` → `batchUploadAsync`
   - 新增 `batchUpload(bucket, requests, threadCount)`
   - 重构任务提交逻辑（单文件任务）
   - 移除 `partition` 方法
4. 更新 `MinioServiceMockTest` 测试
5. 更新文档/messages 文件（如有需要）
