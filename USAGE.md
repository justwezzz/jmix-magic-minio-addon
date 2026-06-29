# MinIO Addon 使用指南

本指南面向宿主项目开发人员，说明如何集成和使用 MinIO 文件存储插件。

---

## 目录

1. [安装插件](#安装插件)
2. [快速上手](#快速上手)
3. [配置项参考](#配置项参考)
4. [集成方式](#集成方式)
5. [MinioService API](#minioservice-api)
6. [文件预览与交互](#文件预览与交互)
7. [安全配置](#安全配置)
8. [消息键参考](#消息键参考)
9. [常见问题](#常见问题)

---

## 安装插件

插件以 JAR 包形式提供，需要先安装到本地 Maven 仓库。

### 步骤 1：获取 JAR 包

从插件开发者处获取以下文件：
- `jmix-magic-addons-minio-0.0.1.jar`
- `jmix-magic-addons-minio-starter-0.0.1.jar`

### 步骤 2：发布到本地 Maven 仓库

在插件项目根目录执行：

```bash
./gradlew publishToMavenLocal
```

---

## 快速上手

只需 **3 个步骤**，即可让你的 Jmix 项目拥有 MinIO 文件浏览器。

### 1. 添加依赖（build.gradle）

```groovy
repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.github.justwezzz:jmix-magic-addons-minio-starter:0.0.1'
}
```

### 2. 配置 MinIO 连接（application.properties）

```properties
# MinIO 连接（必填）
magic.minio.endpoint=http://localhost:9000      # MinIO 服务地址
magic.minio.access-key=minioadmin               # 访问密钥
magic.minio.secret-key=minioadmin               # 秘密密钥
```

### 3. 添加菜单（menu.xml）

```xml
<menu id="data-management" title="数据管理" opened="true">
    <item view="minio_BrowserView" title="MinIO 管理"/>
</menu>
```

### 完成！

启动项目后，点击菜单即可打开 MinIO 文件浏览器：
- ✅ 树形浏览 Bucket / 文件夹 / 文件
- ✅ 上传、下载、删除、重命名
- ✅ 双击预览、搜索

---

## 配置项参考

### MinIO 连接（必填）

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `magic.minio.endpoint` | MinIO 服务地址 | `http://localhost:9000` |
| `magic.minio.access-key` | 访问密钥 | `minioadmin` |
| `magic.minio.secret-key` | 秘密密钥 | `minioadmin` |

### 下载/上传限制（可选）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `magic.minio.download.max-files` | `1000` | ZIP 打包下载最大文件数 |
| `magic.minio.download.max-size` | `100MB` | 单文件下载最大大小 |
| `magic.minio.upload.max-size` | `50MB` | 单文件上传最大大小 |
| `magic.minio.upload.thread-pool-size` | CPU核数×2 | 文件夹上传线程池大小 |

### 完整配置示例

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
magic.minio.upload.thread-pool-size=8           # 文件夹上传线程池大小（默认 CPU核数×2）
```

---

## 集成方式

### 方式一：通过菜单访问（推荐）

在宿主项目的 `menu.xml` 中添加菜单项，视图 ID 固定为 `minio_BrowserView`：

```xml
<menu id="data-management" title="数据管理" opened="true">
    <item view="minio_BrowserView" title="MinIO 管理"/>
</menu>
```

> **提示**：菜单标题可在宿主项目消息文件中覆盖。

### 方式二：继承视图自定义

如果需要在自定义视图中集成，可继承 `MinioBrowserView`：

```java
import io.jmix.flowui.view.DefaultMainViewParent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import com.vaadin.flow.router.Route;
import org.magic.jmix.addons.minio.view.MinioBrowserView;

@Route(value = "my-minio", layout = DefaultMainViewParent.class)
@ViewController(id = "my_MinioView")
@ViewDescriptor(path = "minio/minio-browser-view.xml")
public class MyMinioView extends MinioBrowserView {
    // 自定义扩展逻辑
}
```

> 注意：`layout` 应使用 `DefaultMainViewParent.class` 以保持应用的主界面布局。

### 方式三：直接注入服务

不使用界面，直接调用 MinIO 服务 API：

```java
@Autowired
private MinioService minioService;
```

详见 [MinioService API](#minioservice-api)。

---

## MinioService API

`MinioService` 是核心服务，封装了所有 MinIO 操作。

### Bucket 操作

| 方法 | 说明 |
|------|------|
| `listBuckets()` | 列出所有 Bucket |
| `createBucket(name)` | 创建 Bucket |
| `deleteBucket(name)` | 删除 Bucket |
| `renameBucket(oldName, newName)` | 重命名 Bucket |

### 文件操作

| 方法 | 说明 |
|------|------|
| `listObjects(bucket, prefix)` | 列出对象（懒加载用） |
| `uploadFile(bucket, objectName, inputStream, size)` | 上传文件 |
| `downloadFile(bucket, objectName)` | 下载文件（返回 InputStream） |
| `deleteFile(bucket, objectName)` | 删除文件 |
| `createFolder(bucket, folderPath)` | 创建文件夹 |
| `batchDelete(bucket, items)` | 批量删除 |

### 辅助方法

| 方法 | 说明 |
|------|------|
| `countFiles(bucket, folderPath)` | 统计文件夹下文件数 |
| `listFolderObjectPaths(bucket, folderPath)` | 列出文件夹下所有对象路径 |
| `formatSize(bytes)` | 格式化文件大小（B/KB/MB/GB） |
| `getPresignedUrl(bucket, objectName, expiry)` | 获取预签名 URL（用于浏览器打开） |
| `searchPaged(bucket, keyword, cursor, pageSize)` | 分页搜索（游标翻页） |
| `readTextContent(bucket, objectName)` | 读取文本文件内容（预览用） |

### 使用示例

```java
@Autowired
private MinioService minioService;

// 上传文件
try (InputStream is = new FileInputStream(file)) {
    minioService.uploadFile("my-bucket", "2026/report.pdf", is, file.length());
}

// 下载文件
try (InputStream is = minioService.downloadFile("my-bucket", "2026/report.pdf")) {
    // 处理流
}

// 搜索文件（分页）
PagedSearchResult result = minioService.searchPaged("my-bucket", "report", null, 50);
// result.getItems() - 当前页结果
// result.getNextCursor() - 下一页游标
// result.isHasMore() - 是否还有更多
```

---

## 文件预览与交互

文件浏览器提供以下交互方式：

### 双击预览（按文件类型）

| 文件类型 | 行为 |
|---------|------|
| 文本/代码（txt, xml, json, md, java, sql 等） | 内联预览（CodeEditor 只读，语法高亮） |
| 图片（jpg, png, gif, svg 等） | 内联预览（Image） |
| PDF / 音视频（mp3, mp4 等） | 浏览器新标签页原生预览 |
| 其他格式 | 弹框提示是否下载 |

> 文件大小为 0 时取消预览并提示。

### Ctrl + 单击文件名

按住 Ctrl 鼠标悬停文件名时，文件名变超链接样式（蓝色 + 下划线），单击用浏览器新标签页打开。

### 搜索

- 输入关键字回车搜索（至少 2 字符）
- **游标分页懒加载**：滚动到底自动加载下一页（每页 50 条），无需手动点「加载更多」；基于 core addon 的 `CursorLazyGrid`，异步加载不卡 UI
- **关键字高亮**：搜索结果文件名中匹配关键字的片段以 `<mark>` 高亮（不区分大小写）
- 双击搜索结果定位到文件树（自动展开 + 滚动到目标）

### 批量操作

- **Shift + 点击**：范围选择
- **右键菜单**：下载、删除、全选目录下文件、刷新
- **全选按钮**：切换全选/取消全选（按钮文字动态切换）

---

## 安全配置

插件不预定义角色。请在宿主项目中根据需要自定义角色和权限。

### 通过角色控制访问

```java
import io.jmix.securityflowui.view.role.ResourceRole;
import io.jmix.securityflowui.view.role.ViewPolicy;

@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
```

### 通过菜单策略控制

```java
@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    @MenuPolicy(menuIds = "data-management")
    void minioAccess();
}
```

---

## 消息键参考

插件内置中文（默认）和英文双语支持，会根据用户 Locale 自动切换。宿主项目可在自己的消息文件中覆盖。

### 常用消息键

| 消息键 | 说明 |
|--------|------|
| `org.magic.jmix.addons.minio.view/minioBrowserView.title` | 视图标题 |
| `org.magic.jmix.addons.minio.view/minioBrowserView.selectAll` | 全选按钮 |
| `org.magic.jmix.addons.minio.view/minioBrowserView.deselectAllBtn` | 取消全选按钮 |
| `org.magic.jmix.addons.minio.view/minioBrowserView.selectFolderContents` | 全选目录下文件 |
| `org.magic.jmix.addons.minio.view/minioBrowserView.searchDialogTitle` | 搜索对话框标题 |
| `org.magic.jmix.addons.minio.view/minioBrowserView.searchResultTitle` | 搜索结果标题（`搜索结果: "%s" (已加载 %d 条)`） |

### 覆盖示例

```properties
# src/main/resources/com/example/messages.properties
org.magic.jmix.addons.minio.view/minioBrowserView.title=文件管理
```

---

## 常见问题

### Q: 启动报错连接 MinIO 失败？

**原因**：MinIO 服务未启动，或 endpoint 配置错误。

**解决**：
1. 确认 MinIO 服务已启动
2. 检查 `magic.minio.endpoint` 是否正确
3. 浏览器访问 endpoint 确认可达

### Q: 上传文件失败提示超过大小限制？

**原因**：超过 `magic.minio.upload.max-size` 限制。

**解决**：调整配置：
```properties
magic.minio.upload.max-size=200MB
```

### Q: 下载大量文件失败？

**原因**：超过 `magic.minio.download.max-files` 限制（ZIP 打包）。

**解决**：减少选中文件数，或调大配置：
```properties
magic.minio.download.max-files=2000
```

### Q: 双击文件没有预览？

**原因**：文件类型不在预览支持范围，或文件大小为 0。

**解决**：
- 文本/图片/PDF/音视频以外格式会提示下载
- 空文件（0 字节）无法预览

### Q: 中文文件名乱码？

**原因**：MinIO 对象名编码问题。

**解决**：确保 MinIO 服务端使用 UTF-8，插件已处理中文文件名。

---

## 依赖版本

| 依赖 | 版本 |
|------|------|
| Jmix | 2.8.1 |
| Java | 17 |
| MinIO Java SDK | 8.5.10 |
| Vaadin Flow | 24.x |

### addon 内置依赖

| 依赖 | 用途 |
|------|------|
| `jmix-core` | 核心框架 |
| `jmix-flowui` | UI 框架 |
| `minio` | MinIO Java SDK |

插件零外部依赖（不含 Lombok）。
