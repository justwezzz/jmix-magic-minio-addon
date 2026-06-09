# Unit Tests Design

## Overview

为 Jmix MinIO 插件添加单元测试，采用分层测试策略：纯逻辑测试 + Mock 交互测试。

## Project Structure

```
jmix-magic-addons-minio/
└── src/
    ├── main/java/.../minio/
    │   └── service/MinioService.java
    └── test/
        └── java/org/magic/addons/minio/
            └── service/
                ├── MinioServicePureTest.java      # 纯逻辑测试
                └── MinioServiceMockTest.java      # Mock 交互测试
```

## Test Categories

### 1. 纯逻辑测试 (MinioServicePureTest)

测试无依赖的辅助方法，直接实例化 `MinioService(null, null, null)`：

| 方法 | 测试场景 |
|------|----------|
| `isPlaceholder()` | 普通文件返回 false，`.minio_placeholder` 结尾返回 true，null 返回 false |
| `extractFileName()` | 带路径、不带路径、空字符串、null |
| `extractParentPath()` | 多级路径、单级、根路径、空、null |
| `formatSize()` | B/KB/MB/GB 边界值、负数、null |

### 2. Mock 交互测试 (MinioServiceMockTest)

使用 Mockito 模拟 MinioClient，测试与 MinIO 的交互：

| 方法 | Mock 行为 | 验证点 |
|------|-----------|--------|
| `listBuckets()` | `minioClient.listBuckets()` 返回 Bucket 列表 | 正确转换为 `MinioBucketDto` |
| `createBucket()` | `bucketExists()` 返回 false | 调用 `makeBucket()` |
| `createBucket()` | `bucketExists()` 返回 true | 抛出 `IllegalArgumentException` |
| `deleteBucket()` | `listObjects()` + `removeObjects()` | 正确删除所有对象后删除 Bucket |
| `bucketExists()` | `bucketExists()` 返回 true/false | 返回正确布尔值 |
| `uploadFile()` | `putObject()` 成功 | 正确传递 bucket/object/stream 参数 |
| `uploadFile()` | 占位文件名 | 抛出 `IllegalArgumentException` |
| `downloadFile()` | `getObject()` 返回 InputStream | 返回正确的流 |
| `deleteFile()` | `removeObject()` 成功 | 正确传递参数 |
| `batchUpload()` | 多文件并行上传 | 结果聚合正确，successCount/totalBytes 正确 |
| `batchUpload()` | 部分文件失败 | failedFiles 包含失败记录 |
| `batchDelete()` | `removeObjects()` 返回结果 | deletedFiles/deletedFolders 统计正确 |

## Dependencies

`build.gradle` 添加：

```groovy
dependencies {
    // ... 现有依赖 ...
    
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testImplementation 'org.mockito:mockito-core'
    testImplementation 'org.mockito:mockito-junit-jupiter'
    testImplementation 'org.assertj:assertj-core'
}
```

## Test Implementation Details

### MinioServicePureTest

```java
package org.magic.addons.minio.service;

import org.magic.addons.minio.MinioProperties;
import org.magic.addons.minio.service.MinioService;
import io.jmix.core.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.concurrent.ExecutorService;

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

### MinioServiceMockTest

```java
package org.magic.addons.minio.service;

import io.jmix.core.Messages;
import io.minio.*;
import io.minio.messages.Bucket;
import org.magic.addons.minio.MinioProperties;
import org.magic.addons.minio.dto.MinioBucketDto;
import org.magic.addons.minio.dto.MinioTreeNode;
import org.magic.addons.minio.dto.NodeType;
import org.magic.addons.minio.service.MinioService;
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

## Test Coverage Summary

| 类别 | 测试用例数 |
|------|-----------|
| 纯逻辑测试 | ~12 个 |
| Mock 交互测试 | ~15 个 |
| **总计** | **~27 个** |

## Running Tests

```bash
./gradlew test
```

## Notes

1. 使用 AssertJ 流式断言，代码更清晰
2. 使用 JUnit 5 参数化测试减少重复代码
3. Mock 交互测试通过反射注入 MinioClient，避免修改生产代码
4. 测试不依赖真实 MinIO 服务，执行快速
