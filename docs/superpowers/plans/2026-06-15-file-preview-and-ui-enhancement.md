# 文件预览与界面优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-step. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 MinIO 文件浏览器添加文件预览功能、状态栏移动与统计、右键菜单增强功能。

**Architecture:** 使用 TreeDataGrid 的 ItemDetailsRenderer 实现文件预览；状态栏从左侧移动到右侧并增加统计信息；右键菜单新增"选中目录下所有文件"功能。

**Tech Stack:** Java 17, Spring Boot, Jmix Flow UI, MinIO SDK, Vaadin Components

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `MinioService.java` | 修改 | 新增 `getPresignedUrl()` 和 `readTextContent()` 方法 |
| `MinioBrowserView.java` | 修改 | 实现文件预览、状态栏统计、右键菜单功能 |
| `minio-browser-view.xml` | 修改 | 调整布局，移动状态栏，添加菜单项 |
| `messages.properties` | 修改 | 新增中文国际化文本 |
| `messages_en.properties` | 修改 | 新增英文国际化文本 |

---

### Task 1: MinioService 新增预签名 URL 和文本读取方法

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/service/MinioService.java`

- [ ] **Step 1: 添加必要的 import 语句**

在文件开头的 import 区域添加：

```java
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
```

- [ ] **Step 2: 添加 getPresignedUrl 方法**

在 `MinioService.java` 的辅助方法区域（`formatSize` 方法之后）添加：

```java
/**
 * 生成预签名 URL（用于图片/视频预览）。
 *
 * @param bucket        Bucket 名称
 * @param objectPath    对象路径
 * @param expirySeconds 过期时间（秒）
 * @return 预签名 URL
 */
public String getPresignedUrl(String bucket, String objectPath, int expirySeconds) {
    try {
        return getClient().getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .method(Method.GET)
                        .expiry(expirySeconds, TimeUnit.SECONDS)
                        .build()
        );
    } catch (Exception e) {
        log.error("生成预签名 URL 失败: bucket={}, path={}", bucket, objectPath, e);
        throw new RuntimeException(msg("service.presignedUrlFailed"), e);
    }
}

/**
 * 读取文本文件内容。
 *
 * @param bucket     Bucket 名称
 * @param objectPath 对象路径
 * @return 文本内容
 */
public String readTextContent(String bucket, String objectPath) {
    try (InputStream stream = downloadFile(bucket, objectPath)) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
        log.error("读取文本内容失败: bucket={}, path={}", bucket, objectPath, e);
        throw new RuntimeException(msg("service.readTextFailed"), e);
    }
}
```

- [ ] **Step 3: 添加国际化文本到 messages.properties**

在文件末尾添加：

```properties
# ===== Preview =====
org.magic.addons.minio/service.presignedUrlFailed=生成预签名 URL 失败
org.magic.addons.minio/service.readTextFailed=读取文本内容失败
```

- [ ] **Step 4: 添加国际化文本到 messages_en.properties**

在文件末尾添加：

```properties
# ===== Preview =====
org.magic.addons.minio/service.presignedUrlFailed=Failed to generate presigned URL
org.magic.addons.minio/service.readTextFailed=Failed to read text content
```

- [ ] **Step 5: 提交 MinioService 修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/service/MinioService.java
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages.properties
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages_en.properties
git commit -m "feat: add getPresignedUrl and readTextContent methods to MinioService"
```

---

### Task 2: 修改 XML 布局 - 移动状态栏和添加菜单项

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/view/minio/minio-browser-view.xml`

- [ ] **Step 1: 添加新的 action 定义**

在 `<actions>` 标签内，`refreshFileAction` 之后添加：

```xml
<action id="selectFolderContentsAction" text="msg://minioBrowserView.selectFolderContents" icon="CHECK"/>
```

- [ ] **Step 2: 移除左侧 bucketPanel 中的 statsBar**

将以下代码从 bucketPanel 中删除（约第 49-51 行）：

```xml
<!-- 删除这部分 -->
<hbox id="statsBar" classNames="stats-bar" width="100%">
    <span id="statsLabel" text="msg://minioBrowserView.selectBucket"/>
</hbox>
```

- [ ] **Step 3: 在右侧 filePanel 底部添加新的 statsBar**

在 `fileTreeGrid` 之后、`</vbox>` 之前添加：

```xml
<hbox id="statsBar" classNames="stats-bar" width="100%" padding="false">
    <span id="bucketLabel" text="msg://minioBrowserView.selectBucket"/>
    <span id="separator1" text=" | "/>
    <span id="loadedLabel"/>
    <span id="separator2" text=" | "/>
    <span id="selectedLabel"/>
</hbox>
```

- [ ] **Step 4: 更新 filePanel 的 expand 属性**

确保 filePanel 的 expand 属性指向 fileTreeGrid（状态栏不应被压缩）：

```xml
<vbox id="filePanel" width="100%" height="100%" padding="false" classNames="pl-s" expand="fileTreeGrid">
```

- [ ] **Step 5: 在右键菜单中添加新菜单项**

在 `contextMenu` 中，`selectAllFilesAction` 之前添加：

```xml
<item id="selectFolderContentsItem" action="selectFolderContentsAction" icon="CHECK"/>
```

完整的 contextMenu 应该类似：

```xml
<contextMenu>
    <item action="createFolderAction" icon="FOLDER_ADD"/>
    <item action="uploadFileAction" icon="FILE_ADD"/>
    <item action="uploadFolderAction" icon="FOLDER_ADD"/>
    <separator/>
    <item action="downloadAction" icon="DOWNLOAD"/>
    <separator/>
    <item id="selectFolderContentsItem" action="selectFolderContentsAction" icon="CHECK"/>
    <item action="selectAllFilesAction" icon="CHECK"/>
    <item action="deleteFilesAction" icon="TRASH"/>
    <separator/>
    <item action="refreshFileAction" icon="REFRESH"/>
</contextMenu>
```

- [ ] **Step 6: 添加国际化文本到 messages.properties**

在文件末尾添加：

```properties
# ===== Preview =====
org.magic.addons.minio.view/minioBrowserView.previewNotSupported=此文件格式不支持预览，请下载后查看
org.magic.addons.minio.view/minioBrowserView.previewLoading=加载中...
org.magic.addons.minio.view/minioBrowserView.previewError=预览加载失败: %s

# ===== Stats =====
org.magic.addons.minio.view/minioBrowserView.currentBucket=当前 Bucket: %s
org.magic.addons.minio.view/minioBrowserView.loadedStats=已加载: %d 个文件夹, %d 个文件
org.magic.addons.minio.view/minioBrowserView.selectedStats=选中: %d 个文件夹, %d 个文件

# ===== Context Menu =====
org.magic.addons.minio.view/minioBrowserView.selectFolderContents=选中目录下所有文件
org.magic.addons.minio.view/minioBrowserView.folderEmpty=该目录为空
org.magic.addons.minio.view/minioBrowserView.selectedFolderContents=已选中 %d 个项目
```

- [ ] **Step 7: 添加国际化文本到 messages_en.properties**

在文件末尾添加：

```properties
# ===== Preview =====
org.magic.addons.minio.view/minioBrowserView.previewNotSupported=This file format is not supported for preview, please download to view
org.magic.addons.minio.view/minioBrowserView.previewLoading=Loading...
org.magic.addons.minio.view/minioBrowserView.previewError=Preview failed: %s

# ===== Stats =====
org.magic.addons.minio.view/minioBrowserView.currentBucket=Current Bucket: %s
org.magic.addons.minio.view/minioBrowserView.loadedStats=Loaded: %d folders, %d files
org.magic.addons.minio.view/minioBrowserView.selectedStats=Selected: %d folders, %d files

# ===== Context Menu =====
org.magic.addons.minio.view/minioBrowserView.selectFolderContents=Select all in folder
org.magic.addons.minio.view/minioBrowserView.folderEmpty=Folder is empty
org.magic.addons.minio.view/minioBrowserView.selectedFolderContents=%d items selected
```

- [ ] **Step 8: 提交 XML 布局修改**

```bash
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/view/minio/minio-browser-view.xml
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages.properties
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages_en.properties
git commit -m "feat: move statsBar to right panel, add selectFolderContents action"
```

---

### Task 3: MinioBrowserView 添加文件类型判断辅助方法

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java`

- [ ] **Step 1: 添加必要的 import 语句**

在文件开头的 import 区域添加：

```java
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.kit.component.codeeditor.CodeEditorMode;
import com.vaadin.flow.component.menubar.MenuItem;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
```

- [ ] **Step 2: 添加文件类型判断常量和方法**

在 `MinioBrowserView` 类中，在 `getFileExtension` 方法之后添加：

```java
// ==================== 文件预览支持 ====================

private static final Set<String> TEXT_EXTENSIONS = Set.of(
    "txt", "xml", "json", "md", "log", "csv", "yml", "yaml",
    "html", "htm", "css", "scss", "js", "ts", "java", "py",
    "sql", "properties", "sh", "bat", "gradle", "kt", "go",
    "rs", "c", "cpp", "h", "hpp", "vue", "jsx", "tsx"
);

private static final Set<String> IMAGE_EXTENSIONS = Set.of(
    "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico"
);

private static final Set<String> VIDEO_EXTENSIONS = Set.of(
    "mp4", "webm"
);

private boolean isTextFile(String extension) {
    return TEXT_EXTENSIONS.contains(extension.toLowerCase());
}

private boolean isImageFile(String extension) {
    return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
}

private boolean isVideoFile(String extension) {
    return VIDEO_EXTENSIONS.contains(extension.toLowerCase());
}

private boolean isSupportedPreviewType(String extension) {
    return isTextFile(extension) || isImageFile(extension) || isVideoFile(extension);
}

private CodeEditorMode detectLanguage(String extension) {
    return switch (extension.toLowerCase()) {
        case "java" -> CodeEditorMode.JAVA;
        case "js" -> CodeEditorMode.JAVASCRIPT;
        case "ts" -> CodeEditorMode.TEXT;
        case "json" -> CodeEditorMode.JSON;
        case "xml" -> CodeEditorMode.XML;
        case "html", "htm" -> CodeEditorMode.HTML;
        case "css", "scss" -> CodeEditorMode.CSS;
        case "sql" -> CodeEditorMode.SQL;
        case "py" -> CodeEditorMode.PYTHON;
        case "md" -> CodeEditorMode.MARKDOWN;
        case "yaml", "yml" -> CodeEditorMode.YAML;
        case "properties" -> CodeEditorMode.PROPERTIES;
        case "groovy", "gradle" -> CodeEditorMode.GROOVY;
        default -> CodeEditorMode.TEXT;
    };
}
```

- [ ] **Step 3: 提交辅助方法修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java
git commit -m "feat: add file type detection helper methods for preview"
```

---

### Task 4: MinioBrowserView 实现文件预览渲染器

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java`

- [ ] **Step 1: 添加预览相关成员变量**

在类成员变量区域（`searchField` 之后）添加：

```java
@ViewComponent
private Span bucketLabel;

@ViewComponent
private Span loadedLabel;

@ViewComponent
private Span selectedLabel;

@ViewComponent
private MenuItem selectFolderContentsItem;

private MinioTreeNode contextMenuTargetFolder;
```

- [ ] **Step 2: 添加 createPreviewRenderer 方法**

在 `initFileTreeGrid` 方法之前添加：

```java
/**
 * 创建文件预览渲染器。
 */
private ComponentRenderer<Component, MinioTreeNode> createPreviewRenderer() {
    return new ComponentRenderer<>(node -> {
        if (node.getType() == NodeType.FOLDER) {
            return new Span();
        }

        String extension = getFileExtension(node.getName()).toLowerCase();

        if (isTextFile(extension)) {
            return createTextPreview(node);
        } else if (isImageFile(extension)) {
            return createImagePreview(node);
        } else if (isVideoFile(extension)) {
            return createVideoPreview(node);
        } else {
            return new Span();
        }
    });
}
```

- [ ] **Step 3: 添加文本预览组件创建方法**

```java
/**
 * 创建文本文件预览组件。
 */
private Component createTextPreview(MinioTreeNode node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setWidthFull();
    layout.setPadding(true);
    layout.setSpacing(false);
    layout.getStyle().set("position", "relative");

    // 关闭按钮
    Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
    closeBtn.getStyle()
            .set("position", "absolute")
            .set("right", "8px")
            .set("top", "8px")
            .set("z-index", "1");
    closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    // CodeEditor
    CodeEditor codeEditor = new CodeEditor();
    codeEditor.setWidthFull();
    codeEditor.setHeight("300px");
    codeEditor.setMode(detectLanguage(getFileExtension(node.getName())));
    codeEditor.setReadOnly(true);
    codeEditor.setValue(msg("minioBrowserView.previewLoading"));

    // 异步加载文本内容
    CompletableFuture.runAsync(() -> {
        try {
            String content = minioService.readTextContent(node.getBucket(), node.getPath());
            getUI().ifPresent(ui -> ui.access(() -> {
                if (!content.isEmpty()) {
                    codeEditor.setValue(content);
                } else {
                    codeEditor.setValue("");
                }
            }));
        } catch (Exception e) {
            getUI().ifPresent(ui -> ui.access(() ->
                codeEditor.setValue(msg("minioBrowserView.previewError", e.getMessage()))));
        }
    });

    layout.add(closeBtn, codeEditor);
    return layout;
}
```

- [ ] **Step 4: 添加图片预览组件创建方法**

```java
/**
 * 创建图片预览组件。
 */
private Component createImagePreview(MinioTreeNode node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setWidthFull();
    layout.setPadding(true);
    layout.setSpacing(false);
    layout.getStyle().set("position", "relative");

    // 关闭按钮
    Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
    closeBtn.getStyle()
            .set("position", "absolute")
            .set("right", "8px")
            .set("top", "8px")
            .set("z-index", "1");
    closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    // 图片
    Image image = new Image();
    image.setMaxWidth("100%");
    image.setMaxHeight("400px");
    image.setAlt(node.getName());

    String presignedUrl = minioService.getPresignedUrl(node.getBucket(), node.getPath(), 300);
    image.setSrc(presignedUrl);

    layout.add(closeBtn, image);
    return layout;
}
```

- [ ] **Step 5: 添加视频预览组件创建方法**

```java
/**
 * 创建视频预览组件。
 */
private Component createVideoPreview(MinioTreeNode node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setWidthFull();
    layout.setPadding(true);
    layout.setSpacing(false);
    layout.getStyle().set("position", "relative");

    // 关闭按钮
    Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
    closeBtn.getStyle()
            .set("position", "absolute")
            .set("right", "8px")
            .set("top", "8px")
            .set("z-index", "1");
    closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    // 视频
    String presignedUrl = minioService.getPresignedUrl(node.getBucket(), node.getPath(), 3600);
    String videoType = "video/" + getFileExtension(node.getName());

    Html video = new Html(String.format(
        "<video controls style='max-width:100%%; max-height:400px;'>"
        + "<source src='%s' type='%s'>"
        + "您的浏览器不支持视频播放</video>",
        presignedUrl, videoType
    ));

    layout.add(closeBtn, video);
    return layout;
}
```

- [ ] **Step 6: 提交预览渲染器修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java
git commit -m "feat: add file preview renderers for text, image and video"
```

---

### Task 5: MinioBrowserView 实现双击预览和状态栏统计

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java`

- [ ] **Step 1: 在 onInit 中初始化预览渲染器和选择监听**

修改 `onInit` 方法，在 `initSearchField()` 之后添加：

```java
// 设置文件预览渲染器
fileTreeGrid.setItemDetailsRenderer(createPreviewRenderer());

// 设置详情默认不展开（双击触发）
fileTreeGrid.setDetailsVisibleOnClick(false);

// 添加选择监听器更新统计
fileTreeGrid.addSelectionListener(e -> updateSelectedStats());
```

- [ ] **Step 2: 添加双击事件监听器**

在 `initFileTreeGrid` 方法末尾添加：

```java
// 双击文件预览
fileTreeGrid.addItemDoubleClickListener(event -> {
    MinioTreeNode item = event.getItem();
    if (item.getType() == NodeType.FOLDER) {
        return; // 文件夹不处理
    }

    String extension = getFileExtension(item.getName()).toLowerCase();

    if (!isSupportedPreviewType(extension)) {
        // 不支持的格式
        showNotification(msg("minioBrowserView.previewNotSupported"), NotificationVariant.LUMO_WARNING);
        return;
    }

    // 切换详情展开状态
    boolean isVisible = fileTreeGrid.isDetailsVisible(item);
    fileTreeGrid.setDetailsVisible(item, !isVisible);
});
```

- [ ] **Step 3: 更新 updateStats 方法**

替换现有的 `updateStats` 方法：

```java
private void updateStats() {
    // 1. 当前 Bucket 信息
    if (selectedBucket == null) {
        bucketLabel.setText(msg("minioBrowserView.selectBucket"));
        loadedLabel.setText("");
        selectedLabel.setText("");
        return;
    }
    bucketLabel.setText(String.format(msg("minioBrowserView.currentBucket"), selectedBucket.getName()));

    // 2. 已加载统计
    int folderCount = 0;
    int fileCount = 0;
    if (pathToNodeMap != null) {
        for (MinioTreeNode node : pathToNodeMap.values()) {
            if (!node.getPath().endsWith(".placeholder")) {
                if (node.getType() == NodeType.FOLDER) {
                    folderCount++;
                } else {
                    fileCount++;
                }
            }
        }
    }
    loadedLabel.setText(String.format(msg("minioBrowserView.loadedStats"), folderCount, fileCount));

    // 3. 选中统计
    updateSelectedStats();
}
```

- [ ] **Step 4: 添加 updateSelectedStats 方法**

在 `updateStats` 方法之后添加：

```java
/**
 * 更新选中统计。
 */
private void updateSelectedStats() {
    if (fileTreeGrid == null || selectedLabel == null) {
        return;
    }

    Set<MinioTreeNode> selected = fileTreeGrid.getSelectedItems();
    int folderCount = 0;
    int fileCount = 0;
    for (MinioTreeNode node : selected) {
        if (node.getType() == NodeType.FOLDER) {
            folderCount++;
        } else {
            fileCount++;
        }
    }
    selectedLabel.setText(String.format(msg("minioBrowserView.selectedStats"), folderCount, fileCount));
}
```

- [ ] **Step 5: 移除旧的 statsLabel 相关代码**

删除类成员变量中的：
```java
// 删除这行
@ViewComponent
private Span statsLabel;
```

更新 `clearFileTree` 方法，将 `statsLabel.setText` 改为调用 `updateStats()`：

```java
private void clearFileTree() {
    treeData = new TreeData<>();
    pathToNodeMap = new HashMap<>();
    treeDataProvider = new TreeDataProvider<>(treeData);
    fileTreeGrid.setDataProvider(treeDataProvider);
    selectedBucket = null;
    updateStats();  // 替换 statsLabel.setText
}
```

- [ ] **Step 6: 提交双击预览和状态栏统计修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java
git commit -m "feat: implement double-click preview and status bar statistics"
```

---

### Task 6: MinioBrowserView 实现右键菜单"选中目录下所有文件"

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java`

- [ ] **Step 1: 在 initFileTreeGrid 中添加右键菜单监听器**

在 `initFileTreeGrid` 方法末尾（双击监听器之后）添加：

```java
// 右键菜单动态显示
fileTreeGrid.addContextMenuOpenedListener(event -> {
    // 记录右键目标
    event.getItem().ifPresent(item -> {
        if (item.getType() == NodeType.FOLDER) {
            contextMenuTargetFolder = item;
        } else {
            contextMenuTargetFolder = null;
        }
    });

    // 只有右键点击文件夹时才显示"选中目录下所有文件"菜单
    boolean isFolder = event.getItem().isPresent()
            && event.getItem().get().getType() == NodeType.FOLDER;
    if (selectFolderContentsItem != null) {
        selectFolderContentsItem.setVisible(isFolder);
    }
});
```

- [ ] **Step 2: 添加 selectFolderContentsAction 处理方法**

在类中添加：

```java
@Subscribe("selectFolderContentsAction")
public void onSelectFolderContentsAction(final ActionPerformedEvent event) {
    if (contextMenuTargetFolder == null) {
        return;
    }

    MinioTreeNode folder = contextMenuTargetFolder;

    // 获取该文件夹下的直接子节点（第一层）
    List<MinioTreeNode> children = treeData.getChildren(folder);

    // 过滤掉占位符节点
    List<MinioTreeNode> toSelect = children.stream()
            .filter(node -> !node.getPath().endsWith(".placeholder"))
            .collect(Collectors.toList());

    if (toSelect.isEmpty()) {
        showNotification(msg("minioBrowserView.folderEmpty"), NotificationVariant.LUMO_WARNING);
        return;
    }

    // 选中这些节点
    fileTreeGrid.asMultiSelect().setValue(new HashSet<>(toSelect));
    showNotification(String.format(msg("minioBrowserView.selectedFolderContents"), toSelect.size()),
            NotificationVariant.LUMO_SUCCESS);
}
```

- [ ] **Step 3: 提交右键菜单功能修改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java
git commit -m "feat: add 'select all in folder' context menu action"
```

---

### Task 7: 编译验证和测试

**Files:**
- None (verification only)

- [ ] **Step 1: 编译项目**

Run: `./gradlew :jmix-magic-addons-minio:compileJava --console=plain -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行测试**

Run: `./gradlew :jmix-magic-addons-minio:test --console=plain -q`
Expected: Tests pass (可能有 2 个预先存在的失败测试，与本次修改无关)

- [ ] **Step 3: 最终状态检查**

Run: `git status`
Expected: 工作目录干净，所有更改已提交

---

## 完成标准

- [ ] `MinioService.getPresignedUrl()` 和 `readTextContent()` 方法可用
- [ ] 双击文件可在行下方展开预览（文本、图片、视频）
- [ ] 不支持的文件格式显示 Notification 提示
- [ ] 状态栏从左侧移到右侧，显示三项统计信息
- [ ] 右键点击文件夹显示"选中目录下所有文件"菜单
- [ ] 所有测试通过
- [ ] 每个任务有独立提交
