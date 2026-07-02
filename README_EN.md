# Jmix MinIO Addon

A MinIO file storage plugin for Jmix 2.x framework, providing a complete file browser interface.

> 📖 **For integration and usage details, see [USAGE_EN.md](USAGE_EN.md)** | [中文文档](README.md)

## Features

- **Bucket Management**: Create, delete, rename buckets
- **File Browsing**: Tree-structured lazy loading file browser
- **File Preview**: Double-click for inline preview of text/images (CodeEditor read-only / Image), PDF/audio/video use browser native preview, other formats prompt for download; Ctrl + click filename to open in new browser tab
- **File Upload**: Single file and folder upload support (preserving directory structure), localized
- **File Download**: Single file download and multi-file ZIP package download
- **Search**: Cursor pagination lazy loading search (auto-loads on scroll to bottom, filename keyword highlight)
- **Batch Operations**: Batch delete, Shift range selection, context menu

## Installation

### 1. Add Dependency

Add to your project's `build.gradle`:

```groovy
dependencies {
    implementation 'io.github.justwezzz:jmix-magic-addons-minio-starter:0.0.1'
}
```

### 2. Configure MinIO Connection

Configure in `application.properties`:

```properties
# MinIO Connection (Required)
magic.minio.endpoint=http://localhost:9000      # MinIO server address
magic.minio.access-key=minioadmin               # Access key
magic.minio.secret-key=minioadmin               # Secret key

# Download Configuration (Optional)
magic.minio.download.max-files=1000             # Max files for ZIP package download
magic.minio.download.max-size=100MB             # Max single file download size

# Upload Configuration (Optional)
magic.minio.upload.max-size=50MB                # Max single file upload size
```

## Usage

### Option 1: Access via Menu

#### Auto-merged Menu (composite-menu=true)

When host project sets `jmix.ui.composite-menu=true` in `application.properties`, plugin menu will be auto-merged into host menu.

#### Manual Menu Addition (composite-menu=false, Recommended)

When `jmix.ui.composite-menu=false`, manually add menu item in host project's `menu.xml`:

```xml
<menu id="data-management" title="Data Management" opened="true">
    <item view="minio_BrowserView" title="MinIO Management"/>
</menu>
```

> **Tip**: Recommend using `composite-menu=false` + manual menu addition for precise control over menu position and hierarchy.

### Option 2: Reference in View

If you need to integrate MinIO functionality in a custom view, extend or compose MinioBrowserView:

```java
@Route(value = "my-minio", layout = DefaultMainViewParent.class)
@ViewController(id = "my_MinioView")
@ViewDescriptor(path = "minio/minio-browser-view.xml")
public class MyMinioView extends MinioBrowserView {
    // Custom extension logic
}
```

> Note: Use `DefaultMainViewParent.class` for layout to maintain app's main interface layout.

### Option 3: Direct Service Injection

```java
@Autowired
private MinioService minioService;

// Bucket Operations
minioService.listBuckets();
minioService.createBucket(name);
minioService.deleteBucket(name);
minioService.renameBucket(oldName, newName);

// File Operations
minioService.listObjects(bucket, prefix);
minioService.uploadFile(bucket, objectName, inputStream, size);
minioService.downloadFile(bucket, objectName);
minioService.deleteFile(bucket, objectName);
minioService.createFolder(bucket, folderPath);
minioService.batchDelete(bucket, items);

// Helper Methods
minioService.countFiles(bucket, folderPath);
minioService.listFolderObjectPaths(bucket, folderPath);
minioService.formatSize(bytes);
```

## Security Configuration

Plugin doesn't define roles. Define custom roles and permissions in host project as needed, use `@ViewPolicy` to control view access:

```java
@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
```

## i18n

Plugin has built-in Chinese (default) and English bilingual support, auto-switches based on user Locale.

## Tech Stack

- Jmix 2.8.1
- Vaadin Flow
- MinIO Java SDK 8.5.10
- Zero external dependencies (no Lombok)

## Build

```bash
./gradlew build
```

## Publish to Local Maven

```bash
./gradlew publishToMavenLocal
```

## License

Apache License 2.0