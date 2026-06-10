# MinIO 集成测试实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 MinioService 添加集成测试，验证代码在真实 MinIO 环境下的正确性。

**Architecture:** 创建独立的测试模块 `jmix-minio-integration-test`，使用 `@SpringBootTest` 启动 Spring 上下文，连接用户本地的 MinIO 服务。每个测试使用独立的 Bucket（时间戳命名），测试后自动清理。

**Tech Stack:** Jmix 2.8.1, Spring Boot, JUnit 5, AssertJ, MinIO SDK 8.5.10

---

## 文件结构

```
jmix-minio-addon/
├── settings.gradle                              # 修改：添加新模块
└── jmix-minio-integration-test/                 # 新增模块
    ├── build.gradle                             # 模块配置
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── org/magic/addons/minio/test/
        │   │       └── IntegrationTestApplication.java
        │   └── resources/
        │       └── application.yml
        └── test/
            └── java/
                └── org/magic/addons/minio/test/
                    ├── BaseIntegrationTest.java
                    ├── MinioCrudIntegrationTest.java
                    ├── MinioBatchIntegrationTest.java
                    └── MinioRenameIntegrationTest.java
```

---

### Task 1: 创建模块目录结构

**Files:**
- Modify: `settings.gradle`
- Create: `jmix-minio-integration-test/build.gradle`

- [ ] **Step 1: 修改 settings.gradle 添加新模块**

```groovy
rootProject.name = 'jmix-magic-addons-minio'

include 'jmix-magic-addons-minio'
include 'jmix-magic-addons-minio-starter'
include 'jmix-minio-integration-test'
```

- [ ] **Step 2: 创建 build.gradle**

```groovy
plugins {
    id 'io.jmix'
}

archivesBaseName = 'jmix-minio-integration-test'

dependencies {
    implementation project(':jmix-magic-addons-minio')
    implementation project(':jmix-magic-addons-minio-starter')

    // Jmix
    implementation 'io.jmix.core:jmix-core-starter'
    implementation 'io.jmix.flowui:jmix-flowui-starter'
    implementation 'io.jmix.flowui:jmix-flowui-themes'
    implementation 'io.jmix.security:jmix-security-starter'
    implementation 'io.jmix.security:jmix-security-flowui-starter'

    // Test
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.assertj:assertj-core'
}
```

- [ ] **Step 3: 创建目录结构**

```bash
mkdir -p jmix-minio-integration-test/src/main/java/org/magic/addons/minio/test
mkdir -p jmix-minio-integration-test/src/main/resources
mkdir -p jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test
```

- [ ] **Step 4: 提交模块配置**

```bash
git add settings.gradle jmix-minio-integration-test/build.gradle
git commit -m "feat: add integration test module structure"
```

---

### Task 2: 创建 Spring Boot 入口和配置

**Files:**
- Create: `jmix-minio-integration-test/src/main/java/org/magic/addons/minio/test/IntegrationTestApplication.java`
- Create: `jmix-minio-integration-test/src/main/resources/application.yml`

- [ ] **Step 1: 创建 IntegrationTestApplication.java**

```java
package org.magic.addons.minio.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IntegrationTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 application.yml（用户可修改配置）**

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  upload:
    thread-pool-size: 10
    batch-size: 50

spring:
  main:
    allow-bean-definition-overriding: true
```

- [ ] **Step 3: 提交入口和配置**

```bash
git add jmix-minio-integration-test/src/
git commit -m "feat: add Spring Boot entry and config for integration test"
```

---

### Task 3: 创建测试基类

**Files:**
- Create: `jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/BaseIntegrationTest.java`

- [ ] **Step 1: 创建 BaseIntegrationTest.java**

```java
package org.magic.addons.minio.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.magic.addons.minio.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@SpringBootTest
public abstract class BaseIntegrationTest {

    @Autowired
    protected MinioService minioService;

    protected String testBucket;

    @BeforeEach
    void setUp() {
        testBucket = "test-" + System.currentTimeMillis();
        minioService.createBucket(testBucket);
    }

    @AfterEach
    void tearDown() {
        try {
            minioService.deleteBucket(testBucket);
        } catch (Exception e) {
            // 忽略清理失败
        }
    }

    protected InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    protected String readContent(InputStream stream) throws Exception {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: 提交测试基类**

```bash
git add jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/BaseIntegrationTest.java
git commit -m "feat: add BaseIntegrationTest with bucket lifecycle"
```

---

### Task 4: 创建 CRUD 集成测试

**Files:**
- Create: `jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/MinioCrudIntegrationTest.java`

- [ ] **Step 1: 创建 MinioCrudIntegrationTest.java**

```java
package org.magic.addons.minio.test;

import org.junit.jupiter.api.Test;
import org.magic.addons.minio.dto.MinioTreeNode;
import org.magic.addons.minio.dto.NodeType;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MinioCrudIntegrationTest extends BaseIntegrationTest {

    @Test
    void testUploadAndDownload() throws Exception {
        // given
        String fileName = "test-" + UUID.randomUUID() + ".txt";
        String content = "Hello MinIO Integration Test";
        long size = content.getBytes().length;

        // when - 上传
        minioService.uploadFile(testBucket, fileName, content(content), size);

        // then - 下载并验证内容
        InputStream downloaded = minioService.downloadFile(testBucket, fileName);
        String downloadedContent = readContent(downloaded);
        assertThat(downloadedContent).isEqualTo(content);
    }

    @Test
    void testListObjects() throws Exception {
        // given - 上传多个文件
        minioService.uploadFile(testBucket, "file1.txt", content("content1"), 8);
        minioService.uploadFile(testBucket, "file2.txt", content("content2"), 8);
        minioService.uploadFile(testBucket, "subfolder/file3.txt", content("content3"), 8);

        // when
        List<MinioTreeNode> objects = minioService.listObjects(testBucket, "");

        // then - 应该有文件和文件夹
        assertThat(objects).isNotEmpty();
        assertThat(objects.stream().anyMatch(n -> n.getName().equals("file1.txt"))).isTrue();
        assertThat(objects.stream().anyMatch(n -> n.getName().equals("file2.txt"))).isTrue();
        assertThat(objects.stream().anyMatch(n -> n.getType() == NodeType.FOLDER)).isTrue();
    }

    @Test
    void testCreateFolder() {
        // given
        String folderPath = "test-folder";

        // when
        minioService.createFolder(testBucket, folderPath);

        // then - 文件夹应该存在
        assertThat(minioService.folderExists(testBucket, folderPath)).isTrue();
    }

    @Test
    void testDeleteFile() throws Exception {
        // given - 上传文件
        String fileName = "to-delete.txt";
        minioService.uploadFile(testBucket, fileName, content("content"), 7);

        // when - 删除
        minioService.deleteFile(testBucket, fileName);

        // then - 文件不存在于列表中
        List<MinioTreeNode> objects = minioService.listObjects(testBucket, "");
        assertThat(objects.stream().noneMatch(n -> n.getName().equals(fileName))).isTrue();
    }

    @Test
    void testBucketExists() {
        // then - 测试 Bucket 已创建
        assertThat(minioService.bucketExists(testBucket)).isTrue();

        // 不存在的 Bucket
        assertThat(minioService.bucketExists("non-existent-bucket-" + System.currentTimeMillis())).isFalse();
    }

    @Test
    void testGetFileInfo() throws Exception {
        // given
        String fileName = "info-test.txt";
        String content = "test content for info";
        minioService.uploadFile(testBucket, fileName, content(content), content.length());

        // when
        MinioTreeNode info = minioService.getFileInfo(testBucket, fileName);

        // then
        assertThat(info).isNotNull();
        assertThat(info.getName()).isEqualTo(fileName);
        assertThat(info.getSize()).isEqualTo((long) content.length());
        assertThat(info.getType()).isEqualTo(NodeType.FILE);
    }
}
```

- [ ] **Step 2: 运行 CRUD 测试验证**

```bash
./gradlew :jmix-minio-integration-test:test --tests "MinioCrudIntegrationTest"
```

Expected: 所有测试通过

- [ ] **Step 3: 提交 CRUD 测试**

```bash
git add jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/MinioCrudIntegrationTest.java
git commit -m "feat: add CRUD integration tests"
```

---

### Task 5: 创建批量操作集成测试

**Files:**
- Create: `jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/MinioBatchIntegrationTest.java`

- [ ] **Step 1: 创建 MinioBatchIntegrationTest.java**

```java
package org.magic.addons.minio.test;

import org.junit.jupiter.api.Test;
import org.magic.addons.minio.dto.BatchDeleteResult;
import org.magic.addons.minio.dto.BatchUploadResult;
import org.magic.addons.minio.dto.MinioTreeNode;
import org.magic.addons.minio.dto.NodeType;
import org.magic.addons.minio.dto.UploadRequest;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MinioBatchIntegrationTest extends BaseIntegrationTest {

    @Test
    void testBatchUploadSuccess() throws Exception {
        // given - 准备 100 个上传请求
        List<UploadRequest> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String fileName = "batch/file-" + i + ".txt";
            byte[] content = ("content-" + i).getBytes();
            requests.add(UploadRequest.fromInputStream(
                    fileName,
                    new ByteArrayInputStream(content),
                    content.length
            ));
        }

        // when
        CompletableFuture<BatchUploadResult> future = minioService.batchUpload(testBucket, requests);
        BatchUploadResult result = future.get();

        // then
        assertThat(result.getSuccessCount()).isEqualTo(100);
        assertThat(result.getFailedFiles()).isEmpty();
        assertThat(result.getTotalBytes()).isGreaterThan(0);
    }

    @Test
    void testBatchDeleteFiles() throws Exception {
        // given - 上传多个文件
        List<MinioTreeNode> filesToDelete = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String fileName = "delete/file-" + i + ".txt";
            minioService.uploadFile(testBucket, fileName, content("content"), 7);
            filesToDelete.add(MinioTreeNode.builder()
                    .type(NodeType.FILE)
                    .name("file-" + i + ".txt")
                    .path(fileName)
                    .build());
        }

        // when
        BatchDeleteResult result = minioService.batchDelete(testBucket, filesToDelete);

        // then
        assertThat(result.getDeletedFiles()).isEqualTo(10);
        assertThat(result.getDeletedFolders()).isEqualTo(0);
        assertThat(result.getFailedItems()).isEmpty();
    }

    @Test
    void testBatchDeleteFolder() throws Exception {
        // given - 创建文件夹并上传文件
        String folderPath = "folder-to-delete/";
        minioService.createFolder(testBucket, folderPath);
        minioService.uploadFile(testBucket, folderPath + "file1.txt", content("content1"), 8);
        minioService.uploadFile(testBucket, folderPath + "file2.txt", content("content2"), 8);
        minioService.uploadFile(testBucket, folderPath + "subdir/file3.txt", content("content3"), 8);

        MinioTreeNode folderNode = MinioTreeNode.builder()
                .type(NodeType.FOLDER)
                .name("folder-to-delete")
                .path(folderPath)
                .build();

        // when
        BatchDeleteResult result = minioService.batchDelete(testBucket, List.of(folderNode));

        // then - 应该删除文件夹和所有子文件
        assertThat(result.getDeletedFolders()).isEqualTo(1);
        assertThat(result.getDeletedFiles()).isGreaterThanOrEqualTo(3); // 3 个文件 + 占位文件
    }
}
```

- [ ] **Step 2: 运行批量操作测试验证**

```bash
./gradlew :jmix-minio-integration-test:test --tests "MinioBatchIntegrationTest"
```

Expected: 所有测试通过

- [ ] **Step 3: 提交批量操作测试**

```bash
git add jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/MinioBatchIntegrationTest.java
git commit -m "feat: add batch operation integration tests"
```

---

### Task 6: 创建重命名集成测试

**Files:**
- Create: `jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/MinioRenameIntegrationTest.java`

- [ ] **Step 1: 创建 MinioRenameIntegrationTest.java**

```java
package org.magic.addons.minio.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.magic.addons.minio.dto.MinioTreeNode;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinioRenameIntegrationTest extends BaseIntegrationTest {

    private String renamedBucket;

    @AfterEach
    void tearDownRename() {
        // 清理重命名后的 Bucket
        if (renamedBucket != null) {
            try {
                minioService.deleteBucket(renamedBucket);
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    @Test
    void testRenameBucket() throws Exception {
        // given - 在原 Bucket 中上传文件
        String content = "test content for rename";
        minioService.uploadFile(testBucket, "test-file.txt", content(content), content.length());

        // when - 重命名
        renamedBucket = "renamed-" + System.currentTimeMillis();
        minioService.renameBucket(testBucket, renamedBucket);

        // then - 验证新 Bucket 存在，旧 Bucket 不存在
        assertThat(minioService.bucketExists(renamedBucket)).isTrue();
        assertThat(minioService.bucketExists(testBucket)).isFalse();

        // 验证文件已迁移
        InputStream downloaded = minioService.downloadFile(renamedBucket, "test-file.txt");
        String downloadedContent = readContent(downloaded);
        assertThat(downloadedContent).isEqualTo(content);
    }

    @Test
    void testRenameBucketWithMultipleFiles() throws Exception {
        // given - 上传多个文件和文件夹
        minioService.uploadFile(testBucket, "file1.txt", content("content1"), 8);
        minioService.uploadFile(testBucket, "file2.txt", content("content2"), 8);
        minioService.createFolder(testBucket, "subfolder");
        minioService.uploadFile(testBucket, "subfolder/file3.txt", content("content3"), 8);

        // when - 重命名
        renamedBucket = "renamed-multi-" + System.currentTimeMillis();
        minioService.renameBucket(testBucket, renamedBucket);

        // then - 验证所有文件已迁移
        assertThat(minioService.bucketExists(renamedBucket)).isTrue();

        List<MinioTreeNode> objects = minioService.listAllObjects(renamedBucket);
        assertThat(objects.size()).isGreaterThanOrEqualTo(3); // 至少 3 个文件

        // 验证文件内容
        InputStream file1 = minioService.downloadFile(renamedBucket, "file1.txt");
        assertThat(readContent(file1)).isEqualTo("content1");
    }
}
```

- [ ] **Step 2: 运行重命名测试验证**

```bash
./gradlew :jmix-minio-integration-test:test --tests "MinioRenameIntegrationTest"
```

Expected: 所有测试通过

- [ ] **Step 3: 提交重命名测试**

```bash
git add jmix-minio-integration-test/src/test/java/org/magic/addons/minio/test/MinioRenameIntegrationTest.java
git commit -m "feat: add rename bucket integration tests"
```

---

### Task 7: 验证所有集成测试

- [ ] **Step 1: 运行所有集成测试**

```bash
./gradlew :jmix-minio-integration-test:test
```

Expected: 所有测试通过

- [ ] **Step 2: 查看测试报告**

```bash
open jmix-minio-integration-test/build/reports/tests/test/index.html
```

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat: complete MinIO integration tests

- Add jmix-minio-integration-test module
- CRUD tests: upload, download, list, delete, folder operations
- Batch tests: batchUpload, batchDelete with files and folders
- Rename tests: renameBucket with data integrity verification"
```

---

## 验证清单

| 验证项 | 命令 |
|--------|------|
| 编译通过 | `./gradlew :jmix-minio-integration-test:compileJava` |
| 测试通过 | `./gradlew :jmix-minio-integration-test:test` |
| 测试报告 | `jmix-minio-integration-test/build/reports/tests/test/index.html` |

## 注意事项

1. **MinIO 服务必须运行**：测试前确保本地 MinIO 服务已启动（默认 `localhost:9000`）
2. **配置文件**：用户可在 `application.yml` 中修改 MinIO 连接配置
3. **测试隔离**：每个测试使用独立的 Bucket（时间戳命名），测试后自动清理
4. **清理残留**：如果测试异常中断，可能有残留 Bucket，需手动清理