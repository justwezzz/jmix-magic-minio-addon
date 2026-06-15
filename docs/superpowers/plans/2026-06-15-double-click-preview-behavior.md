# 双击预览行为修改实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修改双击文件预览行为：文本/图片保持行内预览，浏览器支持类型新标签页打开，不支持类型询问下载。

**Architecture:** 修改 MinioBrowserView 的双击事件处理逻辑，新增浏览器支持类型集合和询问下载对话框，删除视频预览相关代码。

**Tech Stack:** Jmix Flow UI, Vaadin Dialog, MinIO 预签名 URL

---

## 文件结构

| 文件 | 责任 |
|------|------|
| `MinioBrowserView.java` | 双击事件处理、预览渲染器、新标签页打开、询问下载对话框 |
| `messages.properties` | 中文国际化文本 |
| `messages_en.properties` | 英文国际化文本 |

---

### Task 1: 新增浏览器支持类型常量和判断方法

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java:1094-1123`

- [ ] **Step 1: 添加 BROWSER_SUPPORTED_EXTENSIONS 常量**

在 `TEXT_EXTENSIONS` 常量定义之前添加：

```java
private static final Set<String> BROWSER_SUPPORTED_EXTENSIONS = Set.of(
    "pdf", "mp3", "wav", "ogg", "aac", "mp4", "webm", "ogv"
);
```

- [ ] **Step 2: 添加 isBrowserSupported 方法**

在 `isVideoFile` 方法之后添加：

```java
private boolean isBrowserSupported(String extension) {
    return BROWSER_SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
}
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew :jmix-magic-addons-minio:compileJava -q`
Expected: BUILD SUCCESSFUL

---

### Task 2: 删除视频预览相关代码

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java:1105-1119,869-907,756-773`

- [ ] **Step 1: 删除 VIDEO_EXTENSIONS 常量**

删除以下代码：

```java
private static final Set<String> VIDEO_EXTENSIONS = Set.of(
    "mp4", "webm"
);
```

- [ ] **Step 2: 删除 isVideoFile 方法**

删除以下代码：

```java
private boolean isVideoFile(String extension) {
    return VIDEO_EXTENSIONS.contains(extension.toLowerCase());
}
```

- [ ] **Step 3: 删除 createVideoPreview 方法**

删除整个 `createVideoPreview` 方法（约 40 行）。

- [ ] **Step 4: 修改 createPreviewRenderer 方法**

将方法修改为：

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

- [ ] **Step 5: 验证编译**

Run: `.\gradlew :jmix-magic-addons-minio:compileJava -q`
Expected: BUILD SUCCESSFUL

---

### Task 3: 新增新标签页打开和询问下载方法

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java`

- [ ] **Step 1: 添加 openInNewTab 方法**

在 `getFileIcon` 方法之前添加：

```java
/**
 * 在新标签页打开文件（使用预签名 URL）。
 */
private void openInNewTab(MinioTreeNode item) {
    String presignedUrl = minioService.getPresignedUrl(
        item.getBucket(),
        item.getPath(),
        3600  // 1小时有效
    );
    getUI().ifPresent(ui -> ui.getPage().open(presignedUrl, "_blank"));
}
```

- [ ] **Step 2: 添加 showUnsupportedPreviewDialog 方法**

在 `openInNewTab` 方法之后添加：

```java
/**
 * 显示不支持预览的对话框，询问是否下载。
 */
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

- [ ] **Step 3: 验证编译**

Run: `.\gradlew :jmix-magic-addons-minio:compileJava -q`
Expected: BUILD SUCCESSFUL

---

### Task 4: 修改双击事件处理逻辑

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java:993-1011`

- [ ] **Step 1: 修改双击事件处理**

将 `initFileTreeGrid` 方法中的双击事件处理代码修改为：

```java
// 双击文件预览
fileTreeGrid.addItemDoubleClickListener(event -> {
    MinioTreeNode item = event.getItem();
    if (item.getType() == NodeType.FOLDER) {
        return; // 文件夹不处理
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

- [ ] **Step 2: 删除不再需要的 isSupportedPreviewType 方法**

删除以下方法：

```java
private boolean isSupportedPreviewType(String extension) {
    return isTextFile(extension) || isImageFile(extension) || isVideoFile(extension);
}
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew :jmix-magic-addons-minio:compileJava -q`
Expected: BUILD SUCCESSFUL

---

### Task 5: 添加国际化文本

**Files:**
- Modify: `jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages.properties`
- Modify: `jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages_en.properties`

- [ ] **Step 1: 添加中文国际化文本**

在 `messages.properties` 文件末尾添加：

```properties
# ===== Unsupported Preview Dialog =====
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedTitle=不支持预览
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedMessage=此文件格式不支持预览，是否下载？
```

- [ ] **Step 2: 添加英文国际化文本**

在 `messages_en.properties` 文件末尾添加：

```properties
# ===== Unsupported Preview Dialog =====
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedTitle=Preview Not Supported
org.magic.addons.minio.view/minioBrowserView.previewNotSupportedMessage=This file format is not supported for preview. Download instead?
```

- [ ] **Step 3: 验证编译**

Run: `.\gradlew :jmix-magic-addons-minio:compileJava -q`
Expected: BUILD SUCCESSFUL

---

### Task 6: 编译验证和测试

**Files:**
- None (验证步骤)

- [ ] **Step 1: 运行完整编译**

Run: `.\gradlew :jmix-magic-addons-minio:build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 提交更改**

```bash
git add jmix-magic-addons-minio/src/main/java/org/magic/addons/minio/view/MinioBrowserView.java
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages.properties
git add jmix-magic-addons-minio/src/main/resources/org/magic/addons/minio/messages_en.properties
git commit -m "$(cat <<'EOF'
feat: 修改双击预览行为

- 文本/图片文件保持行内预览
- 浏览器支持类型（pdf, mp3, wav, ogg, aac, mp4, webm, ogv）新标签页打开
- 不支持类型显示询问下载对话框
- 删除视频行内预览代码

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## 自审检查

**1. Spec 覆盖：**
- ✅ 新增 BROWSER_SUPPORTED_EXTENSIONS 常量 - Task 1
- ✅ 新增 isBrowserSupported 方法 - Task 1
- ✅ 删除 VIDEO_EXTENSIONS 常量 - Task 2
- ✅ 删除 isVideoFile 方法 - Task 2
- ✅ 删除 createVideoPreview 方法 - Task 2
- ✅ 修改 createPreviewRenderer 方法 - Task 2
- ✅ 新增 openInNewTab 方法 - Task 3
- ✅ 新增 showUnsupportedPreviewDialog 方法 - Task 3
- ✅ 修改双击事件处理 - Task 4
- ✅ 删除 isSupportedPreviewType 方法 - Task 4
- ✅ 添加国际化文本 - Task 5

**2. 占位符扫描：** 无 TBD、TODO 或模糊描述

**3. 类型一致性：** 所有方法签名一致
