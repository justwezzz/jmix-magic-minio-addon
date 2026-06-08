# Jmix MinIO Addon

一个用于 Jmix 2.x 框架的 MinIO 文件存储插件，提供完整的文件浏览器界面。

## 功能特性

- **Bucket 管理**：创建、删除、重命名 Bucket
- **文件浏览**：树形结构懒加载浏览文件
- **文件上传**：支持单文件和文件夹上传（保留目录结构）
- **文件下载**：单文件下载和多文件 ZIP 打包下载
- **搜索功能**：分页搜索文件
- **批量操作**：批量删除、Shift 范围选择

## 安装

### 1. 添加依赖

在项目的 `build.gradle` 中添加：

```groovy
dependencies {
    implementation 'org.magic.addons:jmix-magic-addons-minio-starter:1.0.0-SNAPSHOT'
}
```

### 2. 配置 MinIO 连接

在 `application.properties` 中配置：

```properties
# MinIO 连接（必填）
jmix.minio.endpoint=http://localhost:9000
jmix.minio.access-key=minioadmin
jmix.minio.secret-key=minioadmin

# 可选配置
jmix.minio.download.max-files=1000
jmix.minio.download.max-size=100MB
jmix.minio.upload.max-size=50MB
```

## 使用方式

### 方式一：通过菜单访问

插件会自动在菜单中添加 "MinIO" -> "MinIO 文件浏览器" 菜单项。

### 方式二：在视图中引用

如果需要在自定义视图中集成 MinIO 功能，可以继承或组合 MinioBrowserView：

```java
@Route(value = "my-minio", layout = DefaultMainViewParent.class)
@ViewController(id = "my_MinioView")
@ViewDescriptor(path = "minio/minio-browser-view.xml")
public class MyMinioView extends MinioBrowserView {
    // 自定义扩展逻辑
}
```

> 注意：`layout` 应使用 `DefaultMainViewParent.class` 以保持应用的主界面布局。

### 方式三：直接注入服务

```java
@Autowired
private MinioService minioService;

// 使用示例
public void uploadFile() {
    minioService.uploadFile(bucket, objectName, inputStream, size);
}

public List<MinioTreeNode> listFiles(String bucket, String path) {
    return minioService.listObjects(bucket, path);
}
```

## 安全配置

插件提供三个预定义角色：

| 角色代码 | 角色 | 权限说明 |
|---------|------|----------|
| `minio-minimal` | 最小权限 | 只读：浏览、下载 |
| `minio-user` | 用户权限 | 常用：浏览、上传、下载、删除 |
| `minio-admin` | 管理员权限 | 完整：包括 Bucket 管理 |

在项目中分配角色：

```java
@ResourceRoleReference(roleCode = "minio-user")
public class MyUserRole {
}
```

## 技术栈

- Jmix 2.8.1
- Vaadin Flow
- MinIO Java SDK 8.5.10

## 构建

```bash
./gradlew build
```

## 发布到本地 Maven

```bash
./gradlew publishToMavenLocal
```

## 文档

- [插件开发踩坑指南](docs/plugin-development-pitfalls.md) - 记录开发过程中遇到的问题及解决方案

## License

MIT License
