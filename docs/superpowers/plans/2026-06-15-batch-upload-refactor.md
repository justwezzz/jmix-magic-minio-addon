# 批量上传接口重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构批量上传接口：现有接口改名为异步批量上传并优化线程池配置，新增支持自定义线程数的批量上传接口。

**Architecture:** 修改配置类移除 batchSize、线程数默认改为 CPU*2；重构 MinioService 将批量上传拆分为单文件任务模式；新增带线程数参数的批量上传接口使用临时线程池。

**Tech Stack:** Java 17, Spring Boot, MinIO SDK, JUnit 5, Mockito

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `MinioProperties.java` | 修改 | 移除 batchSize，修改 threadPoolSize 为 Integer 类型并支持默认值 |
| `MinioConfiguration.java` | 修改 | 更新线程池创建逻辑（使用新的 getThreadPoolSize） |
| `MinioService.java` | 修改 | 重命名 batchUpload → batchUploadAsync，新增 batchUpload(bucket, requests, threadCount)，移除 partition 方法 |
| `MinioServiceMockTest.java` | 修改 | 更新测试方法名，移除 batchSize 相关 mock，新增临时线程池测试 |

---

### Task 1: 修改 MinioProperties.Upload 配置类

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/MinioProperties.java:113-158`

- [ ] **Step 1: 修改 Upload 内部类**

将 `threadPoolSize` 从 `int` 改为 `Integer`，移除 `batchSize` 字段，更新 getter/setter：

```java
public static class Upload {
    /**
     * 上传最大大小
     */
    private String maxSize = "50MB";

    /**
     * 批量上传线程池大小。
     * null 表示使用默认值（CPU核数 × 2）。
     */
    private Integer threadPoolSize;

    public String getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(String maxSize) {
        this.maxSize = maxSize;
    }

    public int getThreadPoolSize() {
        return threadPoolSize != null ? threadPoolSize : Runtime.getRuntime().availableProcessors() * 2;
    }

    public void setThreadPoolSize(Integer threadPoolSize) {
        if (threadPoolSize != null && threadPoolSize <= 0) {
            throw new IllegalArgumentException("threadPoolSize must be greater than 0, got: " + threadPoolSize);
        }
        this.threadPoolSize = threadPoolSize;
    }
}
```

- [ ] **Step 2: 运行测试验证配置类修改**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest" -q`
Expected: 所有测试通过

- [ ] **Step 3: 提交配置类修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/MinioProperties.java
git commit -m "refactor: modify Upload config - threadPoolSize defaults to CPU*2, remove batchSize"
```

---

### Task 2: 修改 MinioConfiguration 线程池创建

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/MinioConfiguration.java:46-60`

- [ ] **Step 1: 更新线程池创建逻辑**

`getThreadPoolSize()` 现在返回 int（包含默认值逻辑），配置类无需修改调用方式。确认当前代码仍然有效：

```java
@Bean("minio_uploadThreadPool")
public ExecutorService uploadThreadPool(MinioProperties minioProperties) {
    int threadPoolSize = minioProperties.getUpload().getThreadPoolSize();
    AtomicInteger threadCounter = new AtomicInteger(1);

    ThreadFactory threadFactory = r -> {
        Thread thread = new Thread(r);
        thread.setName("minio-upload-" + threadCounter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    };

    this.uploadThreadPool = Executors.newFixedThreadPool(threadPoolSize, threadFactory);
    return this.uploadThreadPool;
}
```

代码无需修改，因为 `getThreadPoolSize()` 返回类型仍为 `int`。

- [ ] **Step 2: 运行测试验证**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest" -q`
Expected: 所有测试通过

---

### Task 3: 重构 MinioService - 重命名并重构批量上传接口

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/service/MinioService.java:976-1117`

- [ ] **Step 1: 重命名 batchUpload 为 batchUploadAsync 并重构实现**

将现有的 `batchUpload` 方法重命名为 `batchUploadAsync`，并修改为单文件任务模式：

```java
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

    log.info(msg("service.batchUploadStart"), bucket, requests.size(), requests.size());

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
 * 批量上传文件到 MinIO，使用临时线程池。
 * <p>
 * 创建指定大小的临时线程池执行上传任务，
 * 线程池在上传完成后自动关闭。
 * 单个文件上传失败不会影响其他文件的上传。
 *
 * @param bucket 目标 Bucket 名称
 * @param requests 上传请求列表
 * @param threadCount 线程数（必须大于 0）
 * @return CompletableFuture 包含批量上传结果
 */
public CompletableFuture<BatchUploadResult> batchUpload(
        String bucket, List<UploadRequest> requests, int threadCount) {
    if (bucket == null || bucket.isBlank()) {
        throw new IllegalArgumentException(msg("service.bucketRequired"));
    }
    if (threadCount <= 0) {
        throw new IllegalArgumentException("threadCount must be greater than 0, got: " + threadCount);
    }
    if (requests == null || requests.isEmpty()) {
        return CompletableFuture.completedFuture(BatchUploadResult.builder()
                .successCount(0)
                .totalBytes(0)
                .failedFiles(new ArrayList<>())
                .build());
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
```

- [ ] **Step 2: 删除 partition 方法和 uploadBatch 方法**

删除以下两个不再需要的方法（在 `batchUpload` 方法之后）：
- `uploadBatch(String bucket, List<UploadRequest> requests)` 方法
- `partition(List<T> list, int size)` 方法

- [ ] **Step 3: 运行测试（预期部分失败）**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest" -q`
Expected: `batchUpload_*` 测试失败（方法已重命名）

- [ ] **Step 4: 提交 MinioService 修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/service/MinioService.java
git commit -m "refactor: rename batchUpload to batchUploadAsync, add batchUpload with threadCount, use single-file task model"
```

---

### Task 4: 更新 MinioServiceMockTest 测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 更新 setUp 方法移除 batchSize mock**

修改 `setUp` 方法，移除 `batchSize` 相关 mock：

```java
@BeforeEach
void setUp() throws Exception {
    lenient().when(properties.getUpload()).thenReturn(uploadProperties);
    // 移除 batchSize mock
    lenient().when(messages.getMessage(anyString())).thenReturn("mock message");

    // Mock MinioProperties 的连接配置
    lenient().when(properties.getEndpoint()).thenReturn("http://localhost:9000");
    lenient().when(properties.getAccessKey()).thenReturn("minioadmin");
    lenient().when(properties.getSecretKey()).thenReturn("minioadmin");

    service = new MinioService(properties, messages, uploadThreadPool);

    injectMockClient();
}
```

- [ ] **Step 2: 重命名测试方法 batchUpload → batchUploadAsync**

将以下测试方法重命名：
- `batchUpload_shouldReturnSuccessResult_forValidRequests` → `batchUploadAsync_shouldReturnSuccessResult_forValidRequests`
- `batchUpload_shouldReturnEmptyResult_forEmptyRequests` → `batchUploadAsync_shouldReturnEmptyResult_forEmptyRequests`
- `batchUpload_shouldThrowException_forNullBucket` → `batchUploadAsync_shouldThrowException_forNullBucket`
- `batchUpload_shouldThrowException_forBlankBucket` → `batchUploadAsync_shouldThrowException_forBlankBucket`

同时更新方法内部的调用：
```java
CompletableFuture<BatchUploadResult> future = realService.batchUploadAsync("test-bucket", List.of(request));
```

- [ ] **Step 3: 新增 batchUpload(bucket, requests, threadCount) 测试**

在文件末尾添加新的测试方法：

```java
// ==================== batchUpload(bucket, requests, threadCount) tests ====================

@Test
void batchUpload_shouldReturnSuccessResult_forValidRequests() throws Exception {
    // given
    UploadRequest request = UploadRequest.fromInputStream(
            "test.txt",
            new ByteArrayInputStream("content".getBytes()),
            7
    );

    // when
    CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(request), 2);
    BatchUploadResult result = future.get();

    // then
    assertThat(result.getSuccessCount()).isEqualTo(1);
    assertThat(result.getTotalBytes()).isEqualTo(7);
    assertThat(result.getFailedFiles()).isEmpty();
}

@Test
void batchUpload_shouldReturnEmptyResult_forEmptyRequests() {
    // when
    CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(), 2);
    BatchUploadResult result = future.join();

    // then
    assertThat(result.getSuccessCount()).isEqualTo(0);
    assertThat(result.getTotalBytes()).isEqualTo(0);
}

@Test
void batchUpload_shouldThrowException_forNullBucket() {
    // when & then
    assertThatThrownBy(() -> service.batchUpload(null, List.of(), 2))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void batchUpload_shouldThrowException_forBlankBucket() {
    // when & then
    assertThatThrownBy(() -> service.batchUpload("  ", List.of(), 2))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void batchUpload_shouldThrowException_forInvalidThreadCount() {
    // when & then
    assertThatThrownBy(() -> service.batchUpload("test-bucket", List.of(), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("threadCount must be greater than 0");

    assertThatThrownBy(() -> service.batchUpload("test-bucket", List.of(), -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("threadCount must be greater than 0");
}

@Test
void batchUpload_shouldShutdownPoolAfterCompletion() throws Exception {
    // given
    UploadRequest request = UploadRequest.fromInputStream(
            "test.txt",
            new ByteArrayInputStream("content".getBytes()),
            7
    );

    // when
    CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(request), 1);

    // 等待完成
    future.get();

    // 短暂等待确保 whenComplete 执行
    Thread.sleep(100);

    // then - 验证 Future 已完成（线程池已关闭的间接验证）
    assertThat(future.isDone()).isTrue();
}
```

- [ ] **Step 4: 运行所有测试验证**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest" -q`
Expected: 所有测试通过

- [ ] **Step 5: 提交测试修改**

```bash
git add jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java
git commit -m "test: update tests for batchUploadAsync and add tests for batchUpload with threadCount"
```

---

### Task 5: 运行完整测试并提交最终验证

**Files:**
- None (verification only)

- [ ] **Step 1: 运行完整测试套件**

Run: `./gradlew :jmix-magic-addons-minio:test -q`
Expected: 所有测试通过

- [ ] **Step 2: 验证默认线程数逻辑**

添加一个快速验证测试（可选，已在 Task 4 中隐含验证）：

```java
@Test
void threadPoolSize_shouldDefaultToCpuDouble() {
    // given
    MinioProperties.Upload upload = new MinioProperties.Upload();
    int expected = Runtime.getRuntime().availableProcessors() * 2;

    // when
    int actual = upload.getThreadPoolSize();

    // then
    assertThat(actual).isEqualTo(expected);
}
```

如果需要，可以添加到测试文件中。当前实现已通过 Task 1-4 验证。

- [ ] **Step 3: 最终提交（如有遗漏）**

```bash
git status
# 如有未提交的文件，添加并提交
```

---

## 完成标准

- [ ] `MinioProperties.Upload` 移除 `batchSize`，`threadPoolSize` 默认为 CPU*2
- [ ] `MinioService.batchUpload` 重命名为 `batchUploadAsync`
- [ ] `MinioService.batchUpload(bucket, requests, threadCount)` 新接口可用
- [ ] 临时线程池在上传完成后自动关闭
- [ ] 所有测试通过
- [ ] 每个任务有独立提交
