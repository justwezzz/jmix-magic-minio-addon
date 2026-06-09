# Unit Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Jmix MinIO 插件添加单元测试，覆盖辅助方法和核心业务逻辑。

**Architecture:** 分层测试策略 - 纯逻辑测试直接实例化 MinioService，Mock 交互测试通过反射注入 Mock MinioClient。

**Tech Stack:** JUnit 5, Mockito, AssertJ

---

## File Structure

```
jmix-magic-addons-minio/
├── build.gradle                          # 添加测试依赖
└── src/test/java/org/magic/addons/minio/
    └── service/
        ├── MinioServicePureTest.java     # 纯逻辑测试
        └── MinioServiceMockTest.java     # Mock 交互测试
```

---

### Task 1: 添加测试依赖

**Files:**
- Modify: `jmix-magic-addons-minio/build.gradle`

- [ ] **Step 1: 添加测试依赖到 build.gradle**

```groovy
plugins {
    id 'io.jmix'
}

archivesBaseName = 'jmix-magic-addons-minio'

dependencies {
    api 'io.jmix.core:jmix-core-starter'
    api 'io.jmix.flowui:jmix-flowui-starter'
    api 'io.jmix.flowui:jmix-flowui-themes'
    api 'io.jmix.security:jmix-security-starter'
    api 'io.jmix.security:jmix-security-flowui-starter'
    api 'io.minio:minio:8.5.10'

    implementation 'org.springframework.boot:spring-boot-autoconfigure'

    // Test dependencies
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.mockito:mockito-core'
    testImplementation 'org.mockito:mockito-junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
}
```

- [ ] **Step 2: 验证依赖可用**

Run: `./gradlew dependencies --configuration testRuntimeClasspath`
Expected: 输出包含 junit-jupiter, mockito, assertj

---

### Task 2: 创建纯逻辑测试类

**Files:**
- Create: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServicePureTest.java`

- [ ] **Step 1: 创建测试类并添加 isPlaceholder 测试**

```java
package org.magic.addons.minio.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinioServicePureTest {

    private final MinioService service = new MinioService(null, null, null);

    @Test
    void isPlaceholder_shouldReturnTrue_forPlaceholderFile() {
        assertThat(service.isPlaceholder("folder/.minio_placeholder")).isTrue();
        assertThat(service.isPlaceholder(".minio_placeholder")).isTrue();
    }

    @Test
    void isPlaceholder_shouldReturnFalse_forNormalFile() {
        assertThat(service.isPlaceholder("folder/file.txt")).isFalse();
        assertThat(service.isPlaceholder("file.txt")).isFalse();
    }

    @Test
    void isPlaceholder_shouldReturnFalse_forNull() {
        assertThat(service.isPlaceholder(null)).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServicePureTest.isPlaceholder*"`
Expected: 3 tests passed

---

### Task 3: 添加 extractFileName 和 extractParentPath 测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServicePureTest.java`

- [ ] **Step 1: 添加 extractFileName 和 extractParentPath 测试方法**

在现有测试类中追加以下方法：

```java
    // ==================== extractFileName tests ====================

    @Test
    void extractFileName_shouldExtract_fromPath() {
        assertThat(service.extractFileName("folder/subfolder/file.txt")).isEqualTo("file.txt");
        assertThat(service.extractFileName("folder/file.txt")).isEqualTo("file.txt");
    }

    @Test
    void extractFileName_shouldReturn_asIs_forNoPath() {
        assertThat(service.extractFileName("file.txt")).isEqualTo("file.txt");
    }

    @Test
    void extractFileName_shouldReturnEmpty_forNull() {
        assertThat(service.extractFileName(null)).isEmpty();
    }

    @Test
    void extractFileName_shouldReturnEmpty_forEmptyString() {
        assertThat(service.extractFileName("")).isEmpty();
    }

    // ==================== extractParentPath tests ====================

    @Test
    void extractParentPath_shouldExtract_fromMultiLevelPath() {
        assertThat(service.extractParentPath("folder/subfolder/file.txt")).isEqualTo("folder/subfolder/");
    }

    @Test
    void extractParentPath_shouldReturnEmpty_forRootLevelFile() {
        assertThat(service.extractParentPath("file.txt")).isEmpty();
    }

    @Test
    void extractParentPath_shouldHandle_folderPath() {
        assertThat(service.extractParentPath("folder/subfolder/")).isEqualTo("folder/");
    }

    @Test
    void extractParentPath_shouldReturnEmpty_forNull() {
        assertThat(service.extractParentPath(null)).isEmpty();
    }

    @Test
    void extractParentPath_shouldReturnEmpty_forEmptyString() {
        assertThat(service.extractParentPath("")).isEmpty();
    }
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServicePureTest"`
Expected: 11 tests passed

---

### Task 4: 添加 formatSize 测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServicePureTest.java`

- [ ] **Step 1: 添加 formatSize 测试方法**

在现有测试类中追加以下方法：

```java
    // ==================== formatSize tests ====================

    @Test
    void formatSize_shouldFormatBytes() {
        assertThat(service.formatSize(0L)).isEqualTo("0 B");
        assertThat(service.formatSize(500L)).isEqualTo("500 B");
        assertThat(service.formatSize(1023L)).isEqualTo("1023 B");
    }

    @Test
    void formatSize_shouldFormatKilobytes() {
        assertThat(service.formatSize(1024L)).isEqualTo("1.0 KB");
        assertThat(service.formatSize(1536L)).isEqualTo("1.5 KB");
    }

    @Test
    void formatSize_shouldFormatMegabytes() {
        assertThat(service.formatSize(1048576L)).isEqualTo("1.0 MB");
        assertThat(service.formatSize(1572864L)).isEqualTo("1.5 MB");
    }

    @Test
    void formatSize_shouldFormatGigabytes() {
        assertThat(service.formatSize(1073741824L)).isEqualTo("1.0 GB");
    }

    @Test
    void formatSize_shouldHandleNegativeAndNull() {
        assertThat(service.formatSize(-1L)).isEqualTo("-");
        assertThat(service.formatSize(null)).isEqualTo("-");
    }
}
```

- [ ] **Step 2: 运行所有纯逻辑测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServicePureTest"`
Expected: 16 tests passed

---

### Task 5: 创建 Mock 交互测试类基础结构

**Files:**
- Create: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 创建测试类基础结构和 listBuckets 测试**

```java
package org.magic.addons.minio.service;

import io.jmix.core.Messages;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.magic.addons.minio.MinioProperties;
import org.magic.addons.minio.dto.MinioBucketDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioServiceMockTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private Messages messages;

    @Mock
    private MinioProperties properties;

    @Mock
    private MinioProperties.Upload uploadProperties;

    @Mock
    private ExecutorService uploadThreadPool;

    private MinioService service;

    @BeforeEach
    void setUp() throws Exception {
        when(properties.getUpload()).thenReturn(uploadProperties);
        when(uploadProperties.getBatchSize()).thenReturn(50);
        when(messages.getMessage(anyString())).thenReturn("mock message");

        service = new MinioService(properties, messages, uploadThreadPool);

        // 通过反射注入 mock 的 minioClient
        injectMockClient();
    }

    private void injectMockClient() throws Exception {
        Field clientField = MinioService.class.getDeclaredField("cachedClient");
        clientField.setAccessible(true);
        clientField.set(service, minioClient);

        Field endpointField = MinioService.class.getDeclaredField("cachedEndpoint");
        endpointField.setAccessible(true);
        endpointField.set(service, "http://localhost:9000");

        Field accessKeyField = MinioService.class.getDeclaredField("cachedAccessKey");
        accessKeyField.setAccessible(true);
        accessKeyField.set(service, "minioadmin");

        Field secretKeyField = MinioService.class.getDeclaredField("cachedSecretKey");
        secretKeyField.setAccessible(true);
        secretKeyField.set(service, "minioadmin");
    }

    // ==================== listBuckets tests ====================

    @Test
    void listBuckets_shouldReturnDtoList() throws Exception {
        // given
        Bucket bucket = mock(Bucket.class);
        when(bucket.name()).thenReturn("test-bucket");
        when(bucket.creationDate()).thenReturn(ZonedDateTime.now());
        when(minioClient.listBuckets()).thenReturn(List.of(bucket));

        // when
        List<MinioBucketDto> result = service.listBuckets();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-bucket");
    }

    @Test
    void listBuckets_shouldReturnEmptyList_whenNoBuckets() throws Exception {
        // given
        when(minioClient.listBuckets()).thenReturn(List.of());

        // when
        List<MinioBucketDto> result = service.listBuckets();

        // then
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest.listBuckets*"`
Expected: 2 tests passed

---

### Task 6: 添加 Bucket 操作测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 添加 createBucket 和 bucketExists 测试**

在现有测试类中追加以下方法：

```java
    // ==================== createBucket tests ====================

    @Test
    void createBucket_shouldCallMakeBucket_whenNotExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // when
        service.createBucket("new-bucket");

        // then
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void createBucket_shouldThrowException_whenAlreadyExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.createBucket("existing-bucket"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== bucketExists tests ====================

    @Test
    void bucketExists_shouldReturnTrue_whenBucketExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // when
        boolean result = service.bucketExists("test-bucket");

        // then
        assertThat(result).isTrue();
    }

    @Test
    void bucketExists_shouldReturnFalse_whenBucketNotExists() throws Exception {
        // given
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // when
        boolean result = service.bucketExists("test-bucket");

        // then
        assertThat(result).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest"`
Expected: 6 tests passed

---

### Task 7: 添加 deleteBucket 测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 添加 deleteBucket 测试**

在 Task 6 的代码块中追加以下 import 和方法（需要添加在闭合括号 `}` 之前）：

```java
import io.minio.ListObjectsArgs;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.messages.Item;
import io.minio.messages.DeleteObject;

    // ==================== deleteBucket tests ====================

    @Test
    void deleteBucket_shouldDeleteAllObjectsAndBucket() throws Exception {
        // given
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("file.txt");

        io.minio.Result<Item> result = mock(io.minio.Result.class);
        when(result.get()).thenReturn(item);

        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(result));

        // when
        service.deleteBucket("test-bucket");

        // then
        verify(minioClient).removeObjects(any(RemoveObjectsArgs.class));
        verify(minioClient).removeBucket(any(RemoveBucketArgs.class));
    }
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest"`
Expected: 7 tests passed

---

### Task 8: 添加文件操作测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 添加 uploadFile、downloadFile、deleteFile 测试**

在现有测试类中追加以下 import 和方法：

```java
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

    // ==================== uploadFile tests ====================

    @Test
    void uploadFile_shouldCallPutObject() throws Exception {
        // given
        InputStream stream = new ByteArrayInputStream("test content".getBytes());

        // when
        service.uploadFile("test-bucket", "test.txt", stream, 12);

        // then
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_shouldThrowException_forPlaceholderFile() {
        // when & then
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() -> service.uploadFile("bucket", "folder/.minio_placeholder", stream, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== downloadFile tests ====================

    @Test
    void downloadFile_shouldReturnInputStream() throws Exception {
        // given
        InputStream mockStream = new ByteArrayInputStream("content".getBytes());
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockStream);

        // when
        InputStream result = service.downloadFile("test-bucket", "test.txt");

        // then
        assertThat(result).isNotNull();
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    // ==================== deleteFile tests ====================

    @Test
    void deleteFile_shouldCallRemoveObject() throws Exception {
        // when
        service.deleteFile("test-bucket", "test.txt");

        // then
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest"`
Expected: 10 tests passed

---

### Task 9: 添加 batchUpload 测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 添加 batchUpload 测试**

在现有测试类中追加以下 import 和方法：

```java
import org.magic.addons.minio.dto.BatchUploadResult;
import org.magic.addons.minio.dto.FailedFile;
import org.magic.addons.minio.dto.UploadRequest;
import java.util.concurrent.CompletableFuture;

    // ==================== batchUpload tests ====================

    @Test
    void batchUpload_shouldReturnSuccessResult_forValidRequests() throws Exception {
        // given
        UploadRequest request = UploadRequest.fromInputStream(
                "test.txt",
                new ByteArrayInputStream("content".getBytes()),
                7
        );

        // when
        CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of(request));
        BatchUploadResult result = future.get();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getTotalBytes()).isEqualTo(7);
        assertThat(result.getFailedFiles()).isEmpty();
    }

    @Test
    void batchUpload_shouldReturnEmptyResult_forEmptyRequests() {
        // when
        CompletableFuture<BatchUploadResult> future = service.batchUpload("test-bucket", List.of());
        BatchUploadResult result = future.join();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getTotalBytes()).isEqualTo(0);
    }

    @Test
    void batchUpload_shouldThrowException_forNullBucket() {
        // when & then
        assertThatThrownBy(() -> service.batchUpload(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchUpload_shouldThrowException_forBlankBucket() {
        // when & then
        assertThatThrownBy(() -> service.batchUpload("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest"`
Expected: 14 tests passed

---

### Task 9: 添加 batchDelete 测试

**Files:**
- Modify: `jmix-magic-addons-minio/src/test/java/org/magic/addons/minio/service/MinioServiceMockTest.java`

- [ ] **Step 1: 添加 batchDelete 测试**

在现有测试类中追加以下 import 和方法：

```java
import org.magic.addons.minio.dto.BatchDeleteResult;

    // ==================== batchDelete tests ====================

    @Test
    void batchDelete_shouldDeleteFilesAndCountCorrectly() throws Exception {
        // given
        MinioTreeNode fileNode = MinioTreeNode.builder()
                .type(NodeType.FILE)
                .name("test.txt")
                .path("test.txt")
                .build();

        // when
        BatchDeleteResult result = service.batchDelete("test-bucket", List.of(fileNode));

        // then
        assertThat(result.getDeletedFiles()).isEqualTo(1);
        assertThat(result.getDeletedFolders()).isEqualTo(0);
        verify(minioClient).removeObjects(any(RemoveObjectsArgs.class));
    }

    @Test
    void batchDelete_shouldHandleFolderDeletion() throws Exception {
        // given
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("folder/file.txt");
        when(item.isDir()).thenReturn(false);

        io.minio.Result<Item> result = mock(io.minio.Result.class);
        when(result.get()).thenReturn(item);

        MinioTreeNode folderNode = MinioTreeNode.builder()
                .type(NodeType.FOLDER)
                .name("folder")
                .path("folder")
                .build();

        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(result));

        // when
        BatchDeleteResult deleteResult = service.batchDelete("test-bucket", List.of(folderNode));

        // then
        assertThat(deleteResult.getDeletedFolders()).isEqualTo(1);
        assertThat(deleteResult.getDeletedFiles()).isEqualTo(1);
    }
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test --tests "MinioServiceMockTest"`
Expected: 16 tests passed

---

### Task 10: 运行所有测试并提交

**Files:**
- All test files

- [ ] **Step 1: 运行所有测试验证通过**

Run: `./gradlew :jmix-magic-addons-minio:test`
Expected: BUILD SUCCESSFUL, ~30 tests passed

- [ ] **Step 2: 提交所有更改**

```bash
git add jmix-magic-addons-minio/build.gradle
git add jmix-magic-addons-minio/src/test/
git add docs/superpowers/specs/2026-06-09-unit-tests-design.md
git commit -m "$(cat <<'EOF'
feat: add unit tests for MinioService

- Add MinioServicePureTest for helper methods (isPlaceholder, extractFileName, extractParentPath, formatSize)
- Add MinioServiceMockTest for MinIO interaction (listBuckets, createBucket, uploadFile, batchUpload, etc.)
- Use JUnit 5, Mockito, AssertJ

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Summary

| Task | Description | Tests Added |
|------|-------------|-------------|
| Task 1 | 添加测试依赖 | - |
| Task 2 | 创建纯逻辑测试类 | 3 |
| Task 3 | extractFileName/extractParentPath 测试 | 8 |
| Task 4 | formatSize 测试 | 5 |
| Task 5 | Mock 测试基础 + listBuckets | 2 |
| Task 6 | createBucket/bucketExists 测试 | 4 |
| Task 7 | deleteBucket 测试 | 1 |
| Task 8 | 文件操作测试 | 4 |
| Task 9 | batchUpload 测试 | 4 |
| Task 9 | batchDelete 测试 | 2 |
| **Total** | | **~33 tests** |
