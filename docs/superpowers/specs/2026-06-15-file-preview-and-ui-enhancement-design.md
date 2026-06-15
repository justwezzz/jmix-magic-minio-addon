# 文件预览与界面优化设计

## 背景

当前 MinIO 文件浏览器需要增强以下功能：
1. 支持常见文件类型的预览（文本、图片、视频）
2. 状态栏从左侧移动到右侧，并增加统计信息
3. 右键菜单新增"选中目录下所有文件"功能

## 变更概览

| 功能 | 描述 |
|------|------|
| 文件预览 | 双击文件在行下方展开详情区域，根据文件类型显示不同预览控件 |
| 状态栏移动 | 从左侧 Bucket 面板移到右侧文件树下方 |
| 统计信息 | 显示当前 Bucket、已加载数量、选中数量 |
| 右键菜单 | 新增"选中目录下所有文件"（仅文件夹显示） |

---

## 详细设计

### 1. 文件预览功能

#### 1.1 支持的文件类型

| 类型 | 扩展名 | 控件 | 数据来源 |
|------|--------|------|----------|
| 文本 | txt, xml, json, md, log, csv, yml, yaml, html, css, js, ts, java, py, sql, properties, sh, bat 等 | `CodeEditor` | 从 MinIO 读取文本内容 |
| 图片 | jpg, jpeg, png, gif, bmp, svg, webp, ico | `Image` | 预签名 URL（有效期 5 分钟） |
| 视频 | mp4, webm | `Html` (video 标签) | 预签名 URL（有效期 1 小时） |
| 不支持 | avi, mkv, zip, rar, 7z, doc, docx, xls, xlsx 等 | 无 | Notification 提示 |

#### 1.2 MinioService 新增方法

```java
/**
 * 生成预签名 URL（用于图片/视频预览）
 * @param bucket Bucket 名称
 * @param objectPath 对象路径
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
 * 读取文本文件内容
 * @param bucket Bucket 名称
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

#### 1.3 预览渲染器实现

使用 TreeDataGrid 的 `setItemDetailsRenderer()` 方法实现行详情展开：

```java
private ComponentRenderer<Component, MinioTreeNode> createPreviewRenderer() {
    return new ComponentRenderer<>(node -> {
        if (node.getType() == NodeType.FOLDER) {
            return new Span(); // 文件夹不显示详情
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

#### 1.4 双击事件处理

```java
fileTreeGrid.addItemDoubleClickListener(event -> {
    MinioTreeNode item = event.getItem();
    if (item.getType() == NodeType.FOLDER) {
        return; // 文件夹不处理
    }

    String extension = getFileExtension(item.getName()).toLowerCase();

    if (!isSupportedPreviewType(extension)) {
        // 不支持的格式：显示 Notification，不展开详情
        showNotification(msg("minioBrowserView.previewNotSupported"),
            NotificationVariant.LUMO_WARNING);
        return;
    }

    // 支持的格式：切换详情展开状态（允许同时展开多个）
    boolean isVisible = fileTreeGrid.isDetailsVisible(item);
    fileTreeGrid.setDetailsVisible(item, !isVisible);
});
```

#### 1.5 各类型预览组件

**文本预览（使用 CodeEditor）：**

```java
private Component createTextPreview(MinioTreeNode node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setWidthFull();
    layout.setPadding(true);
    layout.setSpacing(false);
    layout.getStyle().set("position", "relative");

    // 关闭按钮（右上角）
    Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
    closeBtn.getStyle()
        .set("position", "absolute")
        .set("right", "8px")
        .set("top", "8px")
        .set("z-index", "1");
    closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    // CodeEditor 显示文本
    CodeEditor codeEditor = new CodeEditor();
    codeEditor.setWidthFull();
    codeEditor.setHeight("300px");
    codeEditor.setMode(detectLanguage(getFileExtension(node.getName())));
    codeEditor.setReadOnly(true);

    // 异步加载文本内容
    CompletableFuture.runAsync(() -> {
        try {
            String content = minioService.readTextContent(node.getBucket(), node.getPath());
            getUI().ifPresent(ui -> ui.access(() -> codeEditor.setValue(content)));
        } catch (Exception e) {
            getUI().ifPresent(ui -> ui.access(() ->
                codeEditor.setValue(msg("minioBrowserView.previewError", e.getMessage()))));
        }
    });

    layout.add(closeBtn, codeEditor);
    return layout;
}
```

**图片预览（使用 Image + 预签名 URL）：**

```java
private Component createImagePreview(MinioTreeNode node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setWidthFull();
    layout.setPadding(true);
    layout.setSpacing(false);
    layout.getStyle().set("position", "relative");

    Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
    closeBtn.getStyle().set("position", "absolute").set("right", "8px").set("top", "8px");
    closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

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

**视频预览（使用 Html 渲染 video 标签）：**

```java
private Component createVideoPreview(MinioTreeNode node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setWidthFull();
    layout.setPadding(true);
    layout.setSpacing(false);
    layout.getStyle().set("position", "relative");

    Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
    closeBtn.getStyle().set("position", "absolute").set("right", "8px").set("top", "8px");
    closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

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

#### 1.6 文件类型判断辅助方法

```java
private static final Set<String> TEXT_EXTENSIONS = Set.of(
    "txt", "xml", "json", "md", "log", "csv", "yml", "yaml",
    "html", "htm", "css", "scss", "js", "ts", "java", "py",
    "sql", "properties", "sh", "bat", "gradle", "xml", "md"
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
        case "js", "ts" -> CodeEditorMode.JAVASCRIPT;
        case "json" -> CodeEditorMode.JSON;
        case "xml" -> CodeEditorMode.XML;
        case "html", "htm" -> CodeEditorMode.HTML;
        case "css", "scss" -> CodeEditorMode.CSS;
        case "sql" -> CodeEditorMode.SQL;
        case "py" -> CodeEditorMode.PYTHON;
        case "md" -> CodeEditorMode.MARKDOWN;
        case "yaml", "yml" -> CodeEditorMode.YAML;
        default -> CodeEditorMode.PLAIN_TEXT;
    };
}
```

---

### 2. 状态栏移动与统计

#### 2.1 布局调整

**修改前：**
```
┌─────────────────┐
│ Bucket 工具栏    │
├─────────────────┤
│ Bucket DataGrid │
├─────────────────┤
│ 状态栏          │  ← 在左侧
└─────────────────┘
```

**修改后：**
```
┌─────────────────────────────────┐
│ 文件工具栏                        │
├─────────────────────────────────┤
│ TreeDataGrid                     │
├─────────────────────────────────┤
│ 状态栏                           │  ← 移到右侧
│ 当前 Bucket | 已加载 | 选中统计    │
└─────────────────────────────────┘
```

#### 2.2 XML 布局修改

```xml
<!-- 左侧：Bucket 列表（移除 statsBar） -->
<vbox id="bucketPanel" width="100%" height="100%" padding="false" classNames="pl-xxs pr-s" expand="bucketDataGrid">
    <hbox id="bucketToolbar" classNames="buttons-panel" width="100%">
        <!-- 工具栏按钮 -->
    </hbox>
    <dataGrid id="bucketDataGrid" width="100%" height="100%" selectionMode="SINGLE" metaClass="minio_MinioBucketDto">
        <!-- 列定义 -->
    </dataGrid>
    <!-- statsBar 已移除 -->
</vbox>

<!-- 右侧：文件列表（添加 statsBar） -->
<vbox id="filePanel" width="100%" height="100%" padding="false" classNames="pl-s" expand="fileTreeGrid">
    <hbox id="fileToolbar" classNames="buttons-panel" width="100%">
        <!-- 工具栏按钮 -->
    </hbox>

    <treeDataGrid id="fileTreeGrid" width="100%" height="100%" selectionMode="MULTI" metaClass="minio_MinioTreeNode">
        <!-- 列定义 -->
    </treeDataGrid>

    <!-- 状态栏（新增三个 Span） -->
    <hbox id="statsBar" classNames="stats-bar" width="100%" padding="false">
        <span id="bucketLabel" text="msg://minioBrowserView.selectBucket"/>
        <span id="separator1" text=" | "/>
        <span id="loadedLabel"/>
        <span id="separator2" text=" | "/>
        <span id="selectedLabel"/>
    </hbox>
</vbox>
```

#### 2.3 统计信息更新逻辑

```java
@ViewComponent
private Span bucketLabel;

@ViewComponent
private Span loadedLabel;

@ViewComponent
private Span selectedLabel;

/**
 * 更新状态栏统计信息
 * 格式：当前 Bucket: xxx | 已加载: x 个文件夹, x 个文件 | 选中: x 个文件夹, x 个文件
 */
private void updateStats() {
    // 1. 当前 Bucket 信息
    if (selectedBucket == null) {
        bucketLabel.setText(msg("minioBrowserView.selectBucket"));
        loadedLabel.setText("");
        selectedLabel.setText("");
        return;
    }
    bucketLabel.setText(String.format(msg("minioBrowserView.currentBucket"), selectedBucket.getName()));

    // 2. 已加载统计（从 pathToNodeMap 计算）
    int folderCount = 0;
    int fileCount = 0;
    for (MinioTreeNode node : pathToNodeMap.values()) {
        if (!node.getPath().endsWith(".placeholder")) {
            if (node.getType() == NodeType.FOLDER) {
                folderCount++;
            } else {
                fileCount++;
            }
        }
    }
    loadedLabel.setText(String.format(msg("minioBrowserView.loadedStats"), folderCount, fileCount));

    // 3. 选中统计
    updateSelectedStats();
}

/**
 * 更新选中统计
 */
private void updateSelectedStats() {
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

#### 2.4 选择监听器

```java
// 在 initFileTreeGrid() 中添加选择监听
fileTreeGrid.addSelectionListener(e -> {
    updateSelectedStats();
});
```

---

### 3. 右键菜单新增功能

#### 3.1 新增 Action 和菜单项

**XML 配置：**

```xml
<actions>
    <!-- 现有 actions -->
    ...

    <!-- 新增：选中目录下所有文件 -->
    <action id="selectFolderContentsAction" text="msg://minioBrowserView.selectFolderContents" icon="CHECK"/>
</actions>

<layout>
    <treeDataGrid id="fileTreeGrid" ...>
        <contextMenu>
            <item action="createFolderAction" icon="FOLDER_ADD"/>
            <item action="uploadFileAction" icon="FILE_ADD"/>
            <item action="uploadFolderAction" icon="FOLDER_ADD"/>
            <separator/>
            <item action="downloadAction" icon="DOWNLOAD"/>
            <separator/>
            <!-- 新增：只在文件夹上显示 -->
            <item id="selectFolderContentsItem" action="selectFolderContentsAction" icon="CHECK"/>
            <item action="selectAllFilesAction" icon="CHECK"/>
            <item action="deleteFilesAction" icon="TRASH"/>
            <separator/>
            <item action="refreshFileAction" icon="REFRESH"/>
        </contextMenu>
    </treeDataGrid>
</layout>
```

#### 3.2 动态显示/隐藏菜单项

```java
@ViewComponent
private MenuItem selectFolderContentsItem;

// 在 initFileTreeGrid() 中添加右键菜单打开监听
fileTreeGrid.addContextMenuOpenedListener(event -> {
    // 获取右键点击的项
    Optional<MinioTreeNode> item = event.getItem();

    // 只有右键点击文件夹时才显示"选中目录下所有文件"菜单
    boolean isFolder = item.isPresent() && item.get().getType() == NodeType.FOLDER;
    selectFolderContentsItem.setVisible(isFolder);
});
```

#### 3.3 选中目录下直接内容

```java
@Subscribe("selectFolderContentsAction")
public void onSelectFolderContentsAction(final ActionPerformedEvent event) {
    // 获取当前选中的文件夹（通过右键菜单的目标）
    // 注意：需要在右键点击时记录目标节点
    if (contextMenuTargetFolder == null) {
        return;
    }

    MinioTreeNode folder = contextMenuTargetFolder;

    // 获取该文件夹下的直接子节点（第一层，不含子目录内容）
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

#### 3.4 记录右键菜单目标

```java
private MinioTreeNode contextMenuTargetFolder;

// 在右键菜单打开时记录目标
fileTreeGrid.addContextMenuOpenedListener(event -> {
    event.getItem().ifPresent(item -> {
        if (item.getType() == NodeType.FOLDER) {
            contextMenuTargetFolder = item;
        } else {
            contextMenuTargetFolder = null;
        }
    });
    selectFolderContentsItem.setVisible(event.getItem().isPresent()
        && event.getItem().get().getType() == NodeType.FOLDER);
});
```

---

### 4. 国际化文本

**messages.properties 新增：**

```properties
# 文件预览
minioBrowserView.previewNotSupported = 此文件格式不支持预览，请下载后查看
minioBrowserView.previewClose = 关闭预览
minioBrowserView.previewLoading = 加载中...
minioBrowserView.previewError = 预览加载失败: %s

# 状态栏
minioBrowserView.currentBucket = 当前 Bucket: %s
minioBrowserView.loadedStats = 已加载: %d 个文件夹, %d 个文件
minioBrowserView.selectedStats = 选中: %d 个文件夹, %d 个文件

# 右键菜单
minioBrowserView.selectFolderContents = 选中目录下所有文件
minioBrowserView.folderEmpty = 该目录为空
minioBrowserView.selectedFolderContents = 已选中 %d 个项目

# MinioService
service.presignedUrlFailed = 生成预签名 URL 失败
service.readTextFailed = 读取文本内容失败
```

---

## 实现步骤

1. **MinioService 新增方法**
   - 添加 `getPresignedUrl()` 方法
   - 添加 `readTextContent()` 方法
   - 添加相应国际化文本

2. **修改 XML 布局**
   - 移除左侧 statsBar
   - 在右侧 filePanel 底部添加新的 statsBar
   - 添加新的 action 和菜单项

3. **实现文件预览功能**
   - 添加文件类型判断辅助方法
   - 实现 `createPreviewRenderer()`
   - 实现各类型预览组件创建方法
   - 添加双击事件监听器

4. **实现状态栏统计**
   - 添加 `updateStats()` 方法
   - 添加 `updateSelectedStats()` 方法
   - 添加选择监听器

5. **实现右键菜单功能**
   - 添加 `selectFolderContentsAction` 处理
   - 添加右键菜单打开监听器（动态显示菜单项）

6. **添加国际化文本**
   - 更新 messages.properties

---

## 涉及的文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `MinioService.java` | 修改 | 新增 `getPresignedUrl()` 和 `readTextContent()` 方法 |
| `MinioBrowserView.java` | 修改 | 实现文件预览、状态栏统计、右键菜单功能 |
| `minio-browser-view.xml` | 修改 | 调整布局，移动状态栏，添加菜单项 |
| `messages.properties` | 修改 | 新增国际化文本 |
