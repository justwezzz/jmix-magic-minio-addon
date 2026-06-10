# MinIO 集成测试设计

## 背景

当前插件项目已有 34 个单元测试（Mock 测试 + 纯逻辑测试），覆盖了 MinioService 的核心方法。但单元测试使用 Mock MinioClient，无法验证与真实 MinIO 服务器的交互是否正确。

用户本地有运行的 MinIO 服务，希望通过集成测试验证：
- 网络连接和认证是否正确
- 真实文件上传/下载是否正常
- 批量操作的并发行为是否正确
- 复杂业务流程（如 renameBucket）是否完整

## 目标

为关键接口添加集成测试，验证代码在真实 MinIO 环境下的正确性。

## 测试范围

| 方法 | 测试重点 |
|------|----------|
| `batchUpload` | 并发上传、错误处理、结果统计 |
| `batchDelete` | 递归删除文件夹、计数正确 |
| `renameBucket` | 复制所有对象、数据完整性 |
| 基本 CRUD | Bucket 创建/删除、文件上传/下载、列表查询 |

**不在范围**：纯逻辑方法（`isPlaceholder`、`formatSize` 等）已有完整单元测试覆盖。

## 架构设计

### 新增模块

创建独立的测试模块 `jmix-minio-integration-test`，承载 Spring Boot 测试上下文。

```
jmix-minio-addon/
└── jmix-minio-integration-test/
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── org.magic.addons.minio.test.IntegrationTestApplication.java
        │   └── resources/
        │       └── application.yml
        └── test/
            └── java/
                └── org.magic.addons.minio.test/
                    ├── BaseIntegrationTest.java
                    ├── MinioCrudIntegrationTest.java
                    ├── MinioBatchIntegrationTest.java
                    └── MinioRenameIntegrationTest.java
```

### 模块依赖

```groovy
dependencies {
    implementation project(':jmix-magic-addons-minio')
    implementation project(':jmix-magic-addons-minio-starter')

    // Jmix
    implementation 'io.jmix.core:jmix-core-starter'
    implementation 'io.jmix.flowui:jmix-flowui-starter'

    // Test
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.assertj:assertj-core'
}
```

### 配置文件

用户通过 `application.yml` 配置 MinIO 连接：

```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  upload:
    thread-pool-size: 10
    batch-size: 10

spring:
  main:
    allow-bean-definition-overriding: true
```

## 测试设计

### 测试基类

`BaseIntegrationTest` 提供测试隔离和资源清理：

```java
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
        minioService.deleteBucket(testBucket);
    }

    protected InputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
```

### 测试用例

**MinioCrudIntegrationTest**：基本 CRUD 操作

| 测试方法 | 验证内容 |
|----------|----------|
| `testUploadAndDownload` | 上传文件后能下载，内容一致 |
| `testListObjects` | 上传多个文件后列表正确 |
| `testCreateFolder` | 创建文件夹（通过占位文件） |
| `testDeleteFile` | 删除文件后列表中不存在 |
| `testBucketExists` | 创建后存在，删除后不存在 |

**MinioBatchIntegrationTest**：批量操作

| 测试方法 | 验证内容 |
|----------|----------|
| `testBatchUploadSuccess` | 批量上传 100 个文件，全部成功 |
| `testBatchUploadPartialFailure` | 部分请求失败时，结果统计正确 |
| `testBatchDeleteFiles` | 批量删除文件，计数正确 |
| `testBatchDeleteFolder` | 删除文件夹包含子文件，计数正确 |

**MinioRenameIntegrationTest**：重命名操作

| 测试方法 | 验证内容 |
|----------|----------|
| `testRenameBucket` | 重命名后新 Bucket 存在，旧 Bucket 不存在，数据完整 |
| `testRenameBucketWithFiles` | 重命名包含文件的 Bucket，验证所有文件迁移 |

## 测试数据管理

- **Bucket 命名**：使用时间戳 `test-{timestamp}` 确保唯一
- **测试隔离**：每个测试方法独立的 Bucket，不共享数据
- **清理策略**：`@AfterEach` 自动删除 Bucket 及所有对象
- **文件命名**：使用随机 UUID 避免冲突

## 验证方式

```bash
# 运行集成测试
./gradlew :jmix-minio-integration-test:test

# 查看测试报告
open jmix-minio-integration-test/build/reports/tests/test/index.html
```

## 风险与限制

| 风险 | 缓解措施 |
|------|----------|
| MinIO 服务不可用 | 测试前检查连接，失败时跳过 |
| 网络延迟影响测试时间 | 设置合理超时 |
| 清理失败残留 Bucket | 使用唯一命名，定期手动清理 |

## 文件清单

| 文件 | 作用 |
|------|------|
| `settings.gradle` | 添加新模块引用 |
| `jmix-minio-integration-test/build.gradle` | 模块配置和依赖 |
| `IntegrationTestApplication.java` | Spring Boot 入口 |
| `application.yml` | MinIO 连接配置 |
| `BaseIntegrationTest.java` | 测试基类 |
| `MinioCrudIntegrationTest.java` | CRUD 测试 |
| `MinioBatchIntegrationTest.java` | 批量操作测试 |
| `MinioRenameIntegrationTest.java` | 重命名测试 |