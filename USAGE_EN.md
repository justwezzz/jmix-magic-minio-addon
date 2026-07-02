# MinIO Addon Usage Guide

This guide is for host project developers, explaining how to integrate and use the MinIO file storage plugin.

> [中文文档](USAGE.md)

---

## Table of Contents

1. [Install Plugin](#install-plugin)
2. [Quick Start](#quick-start)
3. [Configuration Reference](#configuration-reference)
4. [Integration Methods](#integration-methods)
5. [MinioService API](#minioservice-api)
6. [File Preview and Interaction](#file-preview-and-interaction)
7. [Security Configuration](#security-configuration)
8. [Message Key Reference](#message-key-reference)
9. [FAQ](#faq)

---

## Install Plugin

Plugin is provided as JAR package, need to install to local Maven repository first.

### Step 1: Get JAR Package

Get following files from plugin developer:
- `jmix-magic-addons-minio-0.0.1.jar`
- `jmix-magic-addons-minio-starter-0.0.1.jar`

### Step 2: Publish to Local Maven Repository

Execute in plugin project root:

```bash
./gradlew publishToMavenLocal
```

---

## Quick Start

Just **3 steps** to add MinIO file browser to your Jmix project.

### 1. Add Dependency (build.gradle)

```groovy
repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.github.justwezzz:jmix-magic-addons-minio-starter:0.0.1'
}
```

### 2. Configure MinIO Connection (application.properties)

```properties
# MinIO Connection (Required)
magic.minio.endpoint=http://localhost:9000      # MinIO server address
magic.minio.access-key=minioadmin               # Access key
magic.minio.secret-key=minioadmin               # Secret key
```

### 3. Add Menu (menu.xml)

```xml
<menu id="data-management" title="Data Management" opened="true">
    <item view="minio_BrowserView" title="MinIO Management"/>
</menu>
```

### Done!

Start project and click menu to open MinIO file browser:
- ✅ Tree view of Bucket / Folder / File
- ✅ Upload, download, delete, rename
- ✅ Double-click preview, search

---

## Configuration Reference

### MinIO Connection (Required)

| Config | Description | Example |
|--------|-------------|---------|
| `magic.minio.endpoint` | MinIO server address | `http://localhost:9000` |
| `magic.minio.access-key` | Access key | `minioadmin` |
| `magic.minio.secret-key` | Secret key | `minioadmin` |

### Download/Upload Limits (Optional)

| Config | Default | Description |
|--------|---------|-------------|
| `magic.minio.download.max-files` | `1000` | Max files for ZIP package download |
| `magic.minio.download.max-size` | `100MB` | Max single file download size |
| `magic.minio.upload.max-size` | `50MB` | Max single file upload size |
| `magic.minio.upload.thread-pool-size` | CPU cores × 2 | Folder upload thread pool size |

### Full Configuration Example

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
magic.minio.upload.thread-pool-size=8           # Folder upload thread pool size (default CPU cores × 2)
```

---

## Integration Methods

### Option 1: Access via Menu (Recommended)

Add menu item in host project's `menu.xml`, view ID is fixed as `minio_BrowserView`:

```xml
<menu id="data-management" title="Data Management" opened="true">
    <item view="minio_BrowserView" title="MinIO Management"/>
</menu>
```

> **Tip**: Menu title can be overridden in host project message files.

### Option 2: Extend View for Customization

If you need to integrate in a custom view, extend `MinioBrowserView`:

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
    // Custom extension logic
}
```

> Note: Use `DefaultMainViewParent.class` for layout to maintain app's main interface layout.

### Option 3: Direct Service Injection

Don't use UI, directly call MinIO service API:

```java
@Autowired
private MinioService minioService;
```

See [MinioService API](#minioservice-api) for details.

---

## MinioService API

`MinioService` is the core service, encapsulating all MinIO operations.

### Bucket Operations

| Method | Description |
|--------|-------------|
| `listBuckets()` | List all buckets |
| `createBucket(name)` | Create bucket |
| `deleteBucket(name)` | Delete bucket |
| `renameBucket(oldName, newName)` | Rename bucket |

### File Operations

| Method | Description |
|--------|-------------|
| `listObjects(bucket, prefix)` | List objects (for lazy loading) |
| `uploadFile(bucket, objectName, inputStream, size)` | Upload file |
| `downloadFile(bucket, objectName)` | Download file (returns InputStream) |
| `deleteFile(bucket, objectName)` | Delete file |
| `createFolder(bucket, folderPath)` | Create folder |
| `batchDelete(bucket, items)` | Batch delete |

### Helper Methods

| Method | Description |
|--------|-------------|
| `countFiles(bucket, folderPath)` | Count files under folder |
| `listFolderObjectPaths(bucket, folderPath)` | List all object paths under folder |
| `formatSize(bytes)` | Format file size (B/KB/MB/GB) |
| `getPresignedUrl(bucket, objectName, expiry)` | Get presigned URL (for browser open) |
| `searchPaged(bucket, keyword, cursor, pageSize)` | Paginated search (cursor pagination) |
| `readTextContent(bucket, objectName)` | Read text file content (for preview) |

### Usage Examples

```java
@Autowired
private MinioService minioService;

// Upload file
try (InputStream is = new FileInputStream(file)) {
    minioService.uploadFile("my-bucket", "2026/report.pdf", is, file.length());
}

// Download file
try (InputStream is = minioService.downloadFile("my-bucket", "2026/report.pdf")) {
    // Process stream
}

// Search files (paginated)
PagedSearchResult result = minioService.searchPaged("my-bucket", "report", null, 50);
// result.getItems() - current page results
// result.getNextCursor() - next page cursor
// result.isHasMore() - has more results
```

---

## File Preview and Interaction

File browser provides following interaction methods:

### Double-click Preview (by file type)

| File Type | Behavior |
|-----------|----------|
| Text/Code (txt, xml, json, md, java, sql, etc.) | Inline preview (CodeEditor read-only, syntax highlight) |
| Image (jpg, png, gif, svg, etc.) | Inline preview (Image) |
| PDF / Audio/Video (mp3, mp4, etc.) | Browser new tab native preview |
| Other formats | Prompt dialog for download |

> Preview is cancelled with notification when file size is 0.

### Ctrl + Click Filename

When holding Ctrl and hovering over filename, filename becomes hyperlink style (blue + underline), click to open in new browser tab.

### Search

- Enter keyword and press Enter to search (minimum 2 characters)
- **Cursor Pagination Lazy Loading**: Auto-loads next page on scroll to bottom (50 items per page), no manual "load more" button; based on core addon's `CursorLazyGrid`, async loading doesn't block UI
- **Keyword Highlight**: Matched segments in search result filenames are highlighted with `<mark>` (case insensitive)
- Double-click search result to locate in file tree (auto-expand + scroll to target)

### Batch Operations

- **Shift + Click**: Range selection
- **Context Menu**: Download, delete, select all files in folder, refresh
- **Select All Button**: Toggle select all/deselect all (button text changes dynamically)

---

## Security Configuration

Plugin doesn't define roles. Define custom roles and permissions in host project as needed.

### Control Access via Role

```java
import io.jmix.securityflowui.view.role.ResourceRole;
import io.jmix.securityflowui.view.role.ViewPolicy;

@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
```

### Control via Menu Policy

```java
@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    @MenuPolicy(menuIds = "data-management")
    void minioAccess();
}
```

---

## Message Key Reference

Plugin has built-in Chinese (default) and English bilingual support, auto-switches based on user Locale. Host project can override in own message files.

### Common Message Keys

| Message Key | Description |
|-------------|-------------|
| `org.magic.jmix.addons.minio.view/minioBrowserView.title` | View title |
| `org.magic.jmix.addons.minio.view/minioBrowserView.selectAll` | Select all button |
| `org.magic.jmix.addons.minio.view/minioBrowserView.deselectAllBtn` | Deselect all button |
| `org.magic.jmix.addons.minio.view/minioBrowserView.selectFolderContents` | Select all files in folder |
| `org.magic.jmix.addons.minio.view/minioBrowserView.searchDialogTitle` | Search dialog title |
| `org.magic.jmix.addons.minio.view/minioBrowserView.searchResultTitle` | Search result title (`Search Results: "%s" (Loaded %d items)`)

### Override Example

```properties
# src/main/resources/com/example/messages.properties
org.magic.jmix.addons.minio.view/minioBrowserView.title=File Management
```

---

## FAQ

### Q: Startup error - MinIO connection failed?

**Cause**: MinIO service not started, or endpoint misconfigured.

**Solution**:
1. Confirm MinIO service is running
2. Check `magic.minio.endpoint` is correct
3. Visit endpoint in browser to verify accessibility

### Q: Upload failed - exceeds size limit?

**Cause**: Exceeded `magic.minio.upload.max-size` limit.

**Solution**: Adjust config:
```properties
magic.minio.upload.max-size=200MB
```

### Q: Download many files failed?

**Cause**: Exceeded `magic.minio.download.max-files` limit (ZIP packaging).

**Solution**: Reduce selected files, or increase config:
```properties
magic.minio.download.max-files=2000
```

### Q: Double-click file no preview?

**Cause**: File type not in preview support range, or file size is 0.

**Solution**:
- Text/image/PDF/audio/video以外的 formats will prompt for download
- Empty files (0 bytes) cannot be previewed

### Q: Chinese filename garbled?

**Cause**: MinIO object name encoding issue.

**Solution**: Ensure MinIO server uses UTF-8, plugin handles Chinese filenames.

---

## Dependency Versions

| Dependency | Version |
|------------|---------|
| Jmix | 2.8.1 |
| Java | 17 |
| MinIO Java SDK | 8.5.10 |
| Vaadin Flow | 24.x |

### Addon Built-in Dependencies

| Dependency | Usage |
|------------|-------|
| `jmix-core` | Core framework |
| `jmix-flowui` | UI framework |
| `minio` | MinIO Java SDK |

Plugin has zero external dependencies (no Lombok).