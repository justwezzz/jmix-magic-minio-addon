# 双击预览行为修改设计

## 背景

当前双击文件的行为：
- 文本/图片/视频：在行详情中展开预览
- 其他类型：显示"不支持预览"通知

用户希望修改为：
- 文本/图片：保持行内预览
- 浏览器可直接打开的类型：新标签页打开
- 不支持的类型：询问是否下载

## 文件类型分类

| 分类 | 扩展名 | 双击行为 |
|------|--------|----------|
| **文本** | txt, xml, json, md, log, csv, yml, yaml, html, htm, css, scss, js, ts, java, py, sql, properties, sh, bat, gradle, kt, go, rs, c, cpp, h, hpp, vue, jsx, tsx | 行内预览（CodeEditor） |
| **图片** | jpg, jpeg, png, gif, bmp, svg, webp, ico | 行内预览（Image） |
| **浏览器可直接打开** | pdf, mp3, wav, ogg, aac, mp4, webm, ogv | 新标签页打开（预签名 URL） |
| **不支持预览** | 其他所有类型 | 对话框询问是否下载 |

---

## 实现细节

### 1. 新增常量集合

```java
private static final Set<String> BROWSER_SUPPORTED_EXTENSIONS = Set.of(
    "pdf", "mp3", "wav", "ogg", "aac", "mp4", "webm", "ogv"
);
```

### 2. 删除视频相关代码

- 删除 `VIDEO_EXTENSIONS` 常量
- 删除 `isVideoFile()` 方法
- 删除 `createVideoPreview()` 方法
- 修改 `isSupportedPreviewType()` 方法（不再需要，逻辑改为分别判断）

### 3. 修改双击事件处理

**修改前：**
```java
fileTreeGrid.addItemDoubleClickListener(event -> {
    MinioTreeNode item = event.getItem();
    if (item.getType() == NodeType.FOLDER) {
        return;
    }

    String extension = getFileExtension(item.getName()).toLowerCase();

    if (!isSupportedPreviewType(extension)) {
        showNotification(msg("minioBrowserView.previewNotSupported"), NotificationVariant.LUMO_WARNING);
        return;
    }

    boolean isVisible = fileTreeGrid.isDetailsVisible(item);
    fileTreeGrid.setDetailsVisible(item, !isVisible);
});
```

**修改后：**
```java
fileTreeGrid.addItemDoubleClickListener(event -> {
    MinioTreeNode item = event.getItem();
    if (item.getType() == NodeType.FOLDER) {
        return;
    }

    String extension = getFileExtension(item.getName()).toLowerCase();

    if (isTextFile(extension) || isImageFile(extension)) {
        // 文本/图片：切换行详情展开
        boolean isVisible = fileTreeGrid.isDetailsVisible(item);
        fileTreeGrid.setDetailsVisible(item, !isVisible);
    } else if (isBrowserSupported(extension)) {
        // 浏览器支持：新标签页打开
        openInNewTab(item);
    } else {
        // 不支持：询问下载
        showUnsupportedPreviewDialog(item);
    }
});
```

### 4. 新增方法

**判断浏览器支持类型：**
```java
private boolean isBrowserSupported(String extension) {
    return BROWSER_SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
}
```

**新标签页打开：**
```java
private void openInNewTab(MinioTreeNode item) {
    String presignedUrl = minioService.getPresignedUrl(
        item.getBucket(),
        item.getPath(),
        3600  // 1小时有效
    );
    getUI().ifPresent(ui -> ui.getPage().open(presignedUrl, "_blank"));
}
```

**询问下载对话框：**
```java
private void showUnsupportedPreviewDialog(MinioTreeNode item) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(msg("minioBrowserView.previewNotSupportedTitle"));
    dialog.setWidth("400px");

    Span message = new Span(msg("minioBrowserView.previewNotSupportedMessage"));

    Button downloadBtn = new Button(msg("minioBrowserView.download"), e -> {
        dialog.close();
        downloadSingleFile(item);
    });
    downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelBtn = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

    dialog.add(message);
    dialog.getFooter().add(cancelBtn, downloadBtn);
    dialog.open();
}
```

### 5. 修改预览渲染器

**修改前：**
```java
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

**修改后：**
```java
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
        } else {
            return new Span();
        }
    });
}
```

---

## 国际化文本

**messages.properties（中文，主语言）新增：**

```properties
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedTitle=不支持预览
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedMessage=此文件格式不支持预览，是否下载？
```

**messages_en.properties（英文，次级语言）新增：**

```properties
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedTitle=Preview Not Supported
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedMessage=This file format is not supported for preview. Download instead?
```

---

## 涉及的文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `MinioBrowserView.java` | 修改 | 修改双击逻辑、新增方法、删除视频预览代码 |
| `messages.properties` | 修改 | 新增中文国际化文本 |
| `messages_en.properties` | 修改 | 新增英文国际化文本 |
