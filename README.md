# Jmix MinIO Addon

一个用于 Jmix 2.x 框架的 MinIO 文件存储插件，提供完整的文件浏览器界面。

> 📖 **宿主项目集成与使用详见 [USAGE.md](USAGE.md)** | **[English Documentation](README_EN.md)**

## 功能特性

- **Bucket 管理**：创建、删除、重命名 Bucket
- **文件浏览**：树形结构懒加载浏览文件
- **文件预览**：双击文本/图片内联预览（CodeEditor 只读 / Image），PDF/音视频用浏览器原生预览，其他格式弹框提示下载；Ctrl + 单击文件名用浏览器新标签打开
- **文件上传**：支持单文件和文件夹上传（保留目录结构），已汉化
- **文件下载**：单文件下载和多文件 ZIP 打包下载
- **搜索功能**：游标分页懒加载搜索（滚动到底自动加载，文件名关键字高亮）
- **批量操作**：批量删除、Shift 范围选择、右键菜单

## 安装

### 1. 添加依赖

在项目的 `build.gradle` 中添加：

```groovy
dependencies {
    implementation 'io.github.justwezzz:jmix-magic-addons-minio-starter:0.0.1'
}
```

### 2. 配置 MinIO 连接

在 `application.properties` 中配置：

```properties
# MinIO 连接（必填）
magic.minio.endpoint=http://localhost:9000      # MinIO 服务地址
magic.minio.access-key=minioadmin               # 访问密钥
magic.minio.secret-key=minioadmin               # 秘密密钥

# 下载配置（可选）
magic.minio.download.max-files=1000             # ZIP 打包下载最大文件数
magic.minio.download.max-size=100MB             # 单文件下载最大大小

# 上传配置（可选）
magic.minio.upload.max-size=50MB                # 单文件上传最大大小
```

## 使用方式

### 方式一：通过菜单访问

#### 自动合并菜单（composite-menu=true）

当宿主项目 `application.properties` 设置 `jmix.ui.composite-menu=true` 时，插件菜单会自动合并到宿主菜单中。

#### 手动添加菜单（composite-menu=false，推荐）

当 `jmix.ui.composite-menu=false` 时，需要手动在宿主项目的 `menu.xml` 中添加菜单项：

```xml
<menu id="data-management" title="数据管理" opened="true">
    <item view="minio_BrowserView" title="MinIO 管理"/>
</menu>
```

> **提示**：推荐使用 `composite-menu=false` + 手动添加菜单，这样可以精确控制菜单位置和层级，避免无关的插件菜单混入。

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

// Bucket 操作
minioService.listBuckets();
minioService.createBucket(name);
minioService.deleteBucket(name);
minioService.renameBucket(oldName, newName);

// 文件操作
minioService.listObjects(bucket, prefix);
minioService.uploadFile(bucket, objectName, inputStream, size);
minioService.downloadFile(bucket, objectName);
minioService.deleteFile(bucket, objectName);
minioService.createFolder(bucket, folderPath);
minioService.batchDelete(bucket, items);

// 辅助方法
minioService.countFiles(bucket, folderPath);
minioService.listFolderObjectPaths(bucket, folderPath);
minioService.formatSize(bytes);
```

## 安全配置

插件不预定义角色。请在宿主项目中根据需要自定义角色和权限，通过 `@ViewPolicy` 控制视图访问：

```java
@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
```

## i18n

插件内置中文（默认）和英文双语支持，会根据用户 Locale 自动切换。

## 技术栈

- Jmix 2.8.1
- Vaadin Flow
- MinIO Java SDK 8.5.10
- 零外部依赖（不含 Lombok）

## 构建

```bash
./gradlew build
```

## 发布到本地 Maven

```bash
./gradlew publishToMavenLocal
```

## License

Apache License 2.0
