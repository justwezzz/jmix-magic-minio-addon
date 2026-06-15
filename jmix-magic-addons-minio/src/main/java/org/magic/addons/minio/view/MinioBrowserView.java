package org.magic.addons.minio.view;

import io.jmix.flowui.view.DefaultMainViewParent;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.data.grid.ContainerDataGridItems;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.DataComponents;
import io.jmix.core.Metadata;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.metamodel.model.MetaPropertyPath;
import org.magic.addons.minio.component.PathSelector;
import org.magic.addons.minio.dto.BatchDeleteResult;
import org.magic.addons.minio.dto.MinioBucketDto;
import org.magic.addons.minio.dto.MinioTreeNode;
import org.magic.addons.minio.dto.NodeType;
import org.magic.addons.minio.dto.PagedSearchResult;
import org.magic.addons.minio.service.MinioService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.jmix.flowui.view.MessageBundle;

import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.kit.component.codeeditor.CodeEditorMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Route(value = "minio", layout = DefaultMainViewParent.class)
@ViewController(id = "minio_BrowserView")
@ViewDescriptor(path = "minio/minio-browser-view.xml")
public class MinioBrowserView extends StandardView {

    private static final Logger log = LoggerFactory.getLogger(MinioBrowserView.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MinioService minioService;

    @Autowired
    private DataComponents dataComponents;

    @Autowired
    private Metadata metadata;

    @Autowired
    private io.jmix.core.Messages messages;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private DataGrid<MinioBucketDto> bucketDataGrid;

    @ViewComponent
    private VerticalLayout bucketPanel;

    private CollectionContainer<MinioBucketDto> bucketDc;

    @ViewComponent
    private TreeDataGrid<MinioTreeNode> fileTreeGrid;

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private Span bucketLabel;

    @ViewComponent
    private Span loadedLabel;

    @ViewComponent
    private Span selectedLabel;

    @Value("${jmix.minio.download.max-files:1000}")
    private int downloadMaxFiles;

    // Shift 选择锚点
    private MinioTreeNode selectionAnchor = null;

    private MinioBucketDto selectedBucket;
    private TreeData<MinioTreeNode> treeData;
    private TreeDataProvider<MinioTreeNode> treeDataProvider;
    private Map<String, MinioTreeNode> pathToNodeMap;  // 用于快速查找节点

    // 搜索相关字段
    private Dialog searchDialog;
    private Grid<MinioTreeNode> searchResultGrid;
    private String searchCursor;
    private String currentSearchKeyword;
    private List<MinioTreeNode> searchResults;
    private Button loadMoreButton;

    @ViewComponent
    private MenuItem selectFolderContentsItem;

    private MinioTreeNode contextMenuTargetFolder;

    // ==================== i18n helpers ====================

    private String msg(String key) {
        return messageBundle.getMessage(key);
    }

    private String msg(String key, Object... args) {
        return String.format(messageBundle.getMessage(key), args);
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        // 创建集合容器用于绑定 DataGrid
        bucketDc = dataComponents.createCollectionContainer(MinioBucketDto.class);
        bucketDataGrid.setItems(new ContainerDataGridItems<>(bucketDc));

        // 搜索框弹性宽度
        searchField.getStyle().set("flex-grow", "1");

        // 设置 bucketPanel 的 flex 布局，使 DataGrid 占据剩余空间并独立滚动
        bucketPanel.getStyle().set("overflow", "hidden");
        bucketDataGrid.getStyle().set("flex-grow", "1");
        bucketDataGrid.getStyle().set("min-height", "0");

        initBucketGrid();
        initFileTreeGrid();
        initSearchField();

        // 设置文件预览渲染器
        fileTreeGrid.setItemDetailsRenderer(createPreviewRenderer());

        // 设置详情默认不展开（双击触发）
        fileTreeGrid.setDetailsVisibleOnClick(false);

        // 添加选择监听器更新统计
        fileTreeGrid.addSelectionListener(e -> updateSelectedStats());

        loadBuckets();
    }

    @Subscribe("renameBucketAction")
    public void onRenameBucketAction(final ActionPerformedEvent event) {
        onRenameBucketClick();
    }

    @Subscribe("deleteBucketAction")
    public void onDeleteBucketAction(final ActionPerformedEvent event) {
        onDeleteBucketClick();
    }

    @Subscribe("createBucketAction")
    public void onCreateBucketAction(final ActionPerformedEvent event) {
        showCreateBucketDialog();
    }

    @Subscribe("refreshBucketAction")
    public void onRefreshBucketAction(final ActionPerformedEvent event) {
        loadBuckets();
        clearFileTree();
        showNotification(msg("minioBrowserView.bucketListRefreshed"), NotificationVariant.LUMO_SUCCESS);
    }

    @Subscribe("uploadFileAction")
    public void onUploadFileAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            showNotification(msg("minioBrowserView.selectBucketFirst"), NotificationVariant.LUMO_WARNING);
            return;
        }
        showUploadFileDialog();
    }

    @Subscribe("uploadFolderAction")
    public void onUploadFolderAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            showNotification(msg("minioBrowserView.selectBucketFirst"), NotificationVariant.LUMO_WARNING);
            return;
        }
        showUploadFolderDialog();
    }

    private void initBucketGrid() {
        // 清除所有自动生成的列
        bucketDataGrid.removeAllColumns();

        MetaClass bucketMeta = metadata.getClass(MinioBucketDto.class);

        // Bucket 名称列：绑定 name property 走 Jmix 排序 + 图标渲染
        // 注：DataGrid + ContainerDataGridItems 排序只认带 MetaPropertyPath 的列，setComparator 无效
        bucketDataGrid.addColumn("nameWithIcon", bucketMeta.getPropertyPath("name"))
                .setHeader(msg("minioBrowserView.columnName"))
                .setRenderer(new ComponentRenderer<>(bucket -> {
                    HorizontalLayout layout = new HorizontalLayout();
                    layout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
                    layout.setSpacing(true);

                    Icon icon = VaadinIcon.DATABASE.create();
                    icon.setColor("#4CAF50");

                    layout.add(icon, new Span(bucket.getName()));
                    return layout;
                }))
                .setAutoWidth(true).setResizable(true);

        // 创建时间列：绑定 creationDate property 走 Jmix 排序 + 格式化渲染
        bucketDataGrid.addColumn("creationDateFormatted", bucketMeta.getPropertyPath("creationDate"))
                .setHeader(msg("minioBrowserView.columnCreationDate"))
                .setRenderer(new ComponentRenderer<>(bucket -> {
                    LocalDateTime date = bucket.getCreationDate();
                    if (date == null) {
                        return new Span("-");
                    }
                    return new Span(date.format(DATE_TIME_FORMATTER));
                }))
                .setAutoWidth(true).setResizable(true);

        // 选择事件
        bucketDataGrid.addSelectionListener(e -> {
            e.getFirstSelectedItem().ifPresent(this::onBucketSelected);
        });
    }

    private void loadBuckets() {
        try {
            List<MinioBucketDto> buckets = minioService.listBuckets();
            bucketDc.setItems(buckets);
        } catch (Exception e) {
            // 显示错误提示
            bucketLabel.setText(msg("minioBrowserView.connectionFailed"));
            loadedLabel.setText("");
            selectedLabel.setText("");
        }
    }

    private void onBucketSelected(MinioBucketDto bucket) {
        this.selectedBucket = bucket;

        // 初始化文件树数据
        loadFileTreeData();

        // 更新统计信息
        updateStats();
    }

    /**
     * 加载文件树数据到 TreeData（懒加载模式）
     */
    private void loadFileTreeData() {
        if (selectedBucket == null) return;

        // 创建新的 TreeData
        treeData = new TreeData<>();
        pathToNodeMap = new HashMap<>();

        // 只加载根目录下的对象
        List<MinioTreeNode> rootItems = minioService.listObjects(selectedBucket.getName(), null);

        // 添加根节点
        for (MinioTreeNode node : rootItems) {
            pathToNodeMap.put(node.getPath(), node);
            treeData.addItem(null, node);

            // 如果是文件夹，添加一个虚拟子节点（占位符），这样会显示展开箭头
            if (node.getType() == NodeType.FOLDER) {
                addPlaceholderChild(node);
            }
        }

        // 设置 DataProvider
        treeDataProvider = new TreeDataProvider<>(treeData);
        fileTreeGrid.setDataProvider(treeDataProvider);

        // 添加展开监听器，实现懒加载
        fileTreeGrid.addExpandListener(event -> {
            for (MinioTreeNode node : event.getItems()) {
                onFolderExpand(node);
            }
        });
    }

    /**
     * 添加占位符子节点（用于显示展开箭头）
     */
    private void addPlaceholderChild(MinioTreeNode parent) {
        MinioTreeNode placeholder = MinioTreeNode.builder()
                .id(parent.getPath() + ".placeholder")
                .type(NodeType.FILE)
                .name(msg("minioBrowserView.placeholderLoading"))
                .path(parent.getPath() + ".placeholder")
                .bucket(parent.getBucket())
                .build();
        treeData.addItem(parent, placeholder);
    }

    /**
     * 文件夹展开时的懒加载
     */
    private void onFolderExpand(MinioTreeNode folder) {
        if (folder.getType() != NodeType.FOLDER) return;

        // 检查是否已经加载过（没有占位符节点）
        List<MinioTreeNode> children = treeData.getChildren(folder);
        if (children.isEmpty()) return;

        // 检查第一个子节点是否是占位符
        MinioTreeNode firstChild = children.get(0);
        if (!firstChild.getPath().endsWith(".placeholder")) {
            // 已经加载过真实数据，不需要再加载
            return;
        }

        // 移除占位符节点
        treeData.removeItem(firstChild);

        // 加载真实的子节点
        List<MinioTreeNode> subItems = minioService.listObjects(folder.getBucket(), folder.getPath());

        for (MinioTreeNode node : subItems) {
            pathToNodeMap.put(node.getPath(), node);
            treeData.addItem(folder, node);

            // 如果子节点也是文件夹，添加占位符
            if (node.getType() == NodeType.FOLDER) {
                addPlaceholderChild(node);
            }
        }

        // 刷新数据
        treeDataProvider.refreshAll();
    }

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

    // ==================== Bucket 操作方法 ====================

    private void showCreateBucketDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogCreateBucketTitle"));
        dialog.setWidth("400px");

        TextField nameField = new TextField(msg("minioBrowserView.dialogBucketNameField"));
        nameField.setWidthFull();
        nameField.setRequired(true);
        nameField.setHelperText(msg("minioBrowserView.dialogBucketNameHelper"));

        Button createButton = new Button(msg("minioBrowserView.dialogCreate"), e -> {
            String name = nameField.getValue().trim();
            if (validateBucketName(name)) {
                try {
                    minioService.createBucket(name);
                    dialog.close();
                    loadBuckets();
                    showNotification(msg("minioBrowserView.bucketCreated"), NotificationVariant.LUMO_SUCCESS);
                } catch (Exception ex) {
                    showNotification(String.format(msg("minioBrowserView.createFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
                }
            }
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(nameField);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.open();
        nameField.focus();
    }

    private void onDeleteBucketClick() {
        MinioBucketDto selected = bucketDataGrid.getSingleSelectedItem();
        if (selected == null) {
            showNotification(msg("minioBrowserView.selectBucketFirst"), NotificationVariant.LUMO_WARNING);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogDeleteBucketTitle"));
        dialog.setWidth("400px");

        Span message = new Span(String.format(msg("minioBrowserView.dialogDeleteBucketConfirm"), selected.getName()));
        message.getElement().getThemeList().add("warning");

        Button deleteButton = new Button(msg("minioBrowserView.dialogDelete"), e -> {
            try {
                minioService.deleteBucket(selected.getName());
                dialog.close();
                loadBuckets();
                clearFileTree();
                showNotification(msg("minioBrowserView.bucketDeleted"), NotificationVariant.LUMO_SUCCESS);
            } catch (IllegalStateException ex) {
                showNotification(String.format(msg("minioBrowserView.deleteFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
            } catch (Exception ex) {
                showNotification(String.format(msg("minioBrowserView.deleteFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
            }
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(message);
        dialog.getFooter().add(cancelButton, deleteButton);
        dialog.open();
    }

    private void onRenameBucketClick() {
        MinioBucketDto selected = bucketDataGrid.getSingleSelectedItem();
        if (selected == null) {
            showNotification(msg("minioBrowserView.selectBucketFirst"), NotificationVariant.LUMO_WARNING);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogRenameBucketTitle"));
        dialog.setWidth("400px");

        TextField nameField = new TextField(msg("minioBrowserView.dialogNewName"));
        nameField.setWidthFull();
        nameField.setValue(selected.getName());
        nameField.setRequired(true);
        nameField.setHelperText(msg("minioBrowserView.dialogBucketNameHelper"));

        Button renameButton = new Button(msg("minioBrowserView.dialogRename"), e -> {
            String newName = nameField.getValue().trim();
            if (newName.equals(selected.getName())) {
                showNotification(msg("minioBrowserView.sameName"), NotificationVariant.LUMO_WARNING);
                return;
            }
            if (validateBucketName(newName)) {
                try {
                    minioService.renameBucket(selected.getName(), newName);
                    dialog.close();
                    loadBuckets();
                    clearFileTree();
                    showNotification(msg("minioBrowserView.bucketRenamed"), NotificationVariant.LUMO_SUCCESS);
                } catch (Exception ex) {
                    showNotification(String.format(msg("minioBrowserView.renameFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
                }
            }
        });
        renameButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(nameField);
        dialog.getFooter().add(cancelButton, renameButton);
        dialog.open();
        nameField.focus();
    }

    private boolean validateBucketName(String name) {
        if (name == null || name.isEmpty()) {
            showNotification(msg("minioBrowserView.validationBucketNameEmpty"), NotificationVariant.LUMO_ERROR);
            return false;
        }
        if (name.length() < 3 || name.length() > 63) {
            showNotification(msg("minioBrowserView.validationBucketNameLength"), NotificationVariant.LUMO_ERROR);
            return false;
        }
        // MinIO Bucket 命名规则：
        // - 只能包含小写字母、数字和短横线
        // - 必须以字母或数字开头和结尾
        if (!name.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$") && name.length() > 1) {
            showNotification(msg("minioBrowserView.validationBucketNamePattern"), NotificationVariant.LUMO_ERROR);
            return false;
        }
        // 单字符情况
        if (name.length() == 1 && !name.matches("^[a-z0-9]$")) {
            showNotification(msg("minioBrowserView.validationBucketNameAlphanumeric"), NotificationVariant.LUMO_ERROR);
            return false;
        }
        // 两字符情况
        if (name.length() == 2 && !name.matches("^[a-z0-9][a-z0-9]$")) {
            showNotification(msg("minioBrowserView.validationBucketNameAlphanumeric"), NotificationVariant.LUMO_ERROR);
            return false;
        }
        return true;
    }

    /**
     * 创建 Vaadin Upload 组件的 i18n 配置，使用 Jmix Messages 翻译
     */
    private com.vaadin.flow.component.upload.UploadI18N createUploadI18n() {
        com.vaadin.flow.component.upload.UploadI18N i18n = new com.vaadin.flow.component.upload.UploadI18N();
        i18n.setDropFiles(new com.vaadin.flow.component.upload.UploadI18N.DropFiles()
                .setOne(msg("minioBrowserView.uploadI18n.dropFilesOne"))
                .setMany(msg("minioBrowserView.uploadI18n.dropFilesMany")))
            .setAddFiles(new com.vaadin.flow.component.upload.UploadI18N.AddFiles()
                .setOne(msg("minioBrowserView.uploadI18n.addFilesOne"))
                .setMany(msg("minioBrowserView.uploadI18n.addFilesMany")))
            .setError(new com.vaadin.flow.component.upload.UploadI18N.Error()
                .setTooManyFiles(msg("minioBrowserView.uploadI18n.errorTooManyFiles"))
                .setFileIsTooBig(msg("minioBrowserView.uploadI18n.errorFileTooBig"))
                .setIncorrectFileType(msg("minioBrowserView.uploadI18n.errorIncorrectFileType")))
            .setUploading(new com.vaadin.flow.component.upload.UploadI18N.Uploading()
                .setStatus(new com.vaadin.flow.component.upload.UploadI18N.Uploading.Status()
                    .setConnecting(msg("minioBrowserView.uploadI18n.statusConnecting"))
                    .setStalled(msg("minioBrowserView.uploadI18n.statusStalled"))
                    .setProcessing(msg("minioBrowserView.uploadI18n.statusProcessing"))
                    .setHeld(msg("minioBrowserView.uploadI18n.statusHeld")))
                .setRemainingTime(new com.vaadin.flow.component.upload.UploadI18N.Uploading.RemainingTime()
                    .setPrefix(msg("minioBrowserView.uploadI18n.remainingTimePrefix"))
                    .setUnknown(msg("minioBrowserView.uploadI18n.remainingTimeUnknown")))
                .setError(new com.vaadin.flow.component.upload.UploadI18N.Uploading.Error()
                    .setServerUnavailable(msg("minioBrowserView.uploadI18n.errorServerUnavailable"))
                    .setUnexpectedServerError(msg("minioBrowserView.uploadI18n.errorUnexpectedServerError"))
                    .setForbidden(msg("minioBrowserView.uploadI18n.errorForbidden"))))
            .setFile(new com.vaadin.flow.component.upload.UploadI18N.File()
                .setRetry(msg("minioBrowserView.uploadI18n.fileRetry"))
                .setStart(msg("minioBrowserView.uploadI18n.fileStart"))
                .setRemove(msg("minioBrowserView.uploadI18n.fileRemove")))
            .setUnits(List.of(msg("minioBrowserView.uploadI18n.units").split("[\\s,]+")));
        return i18n;
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = new Notification(message, 3000, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(variant);
        notification.open();
    }

    private void clearFileTree() {
        treeData = new TreeData<>();
        pathToNodeMap = new HashMap<>();
        treeDataProvider = new TreeDataProvider<>(treeData);
        fileTreeGrid.setDataProvider(treeDataProvider);
        selectedBucket = null;
        updateStats();
    }

    // ==================== 文件操作方法 ====================

    @Subscribe("createFolderAction")
    public void onCreateFolderAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            showNotification(msg("minioBrowserView.selectBucketFirst"), NotificationVariant.LUMO_WARNING);
            return;
        }
        showCreateFolderDialog();
    }

    @Subscribe("deleteFilesAction")
    public void onDeleteFilesAction(final ActionPerformedEvent event) {
        Set<MinioTreeNode> selected = fileTreeGrid.getSelectedItems();
        if (selected.isEmpty()) {
            showNotification(msg("minioBrowserView.selectFilesToDelete"), NotificationVariant.LUMO_WARNING);
            return;
        }
        showDeleteConfirmDialog(selected);
    }

    @Subscribe("downloadAction")
    public void onDownloadAction(final ActionPerformedEvent event) {
        doDownloadSelected();
    }

    @Subscribe("refreshFileAction")
    public void onRefreshFileAction(final ActionPerformedEvent event) {
        if (selectedBucket != null) {
            refreshFileTree();
            showNotification(msg("minioBrowserView.filesRefreshed"), NotificationVariant.LUMO_SUCCESS);
        }
    }

    @Subscribe("selectAllFilesAction")
    public void onSelectAllFilesAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            showNotification(msg("minioBrowserView.selectBucketFirst"), NotificationVariant.LUMO_WARNING);
            return;
        }

        // 从内存中获取所有节点
        if (pathToNodeMap == null || pathToNodeMap.isEmpty()) {
            showNotification(msg("minioBrowserView.noFilesInDirectory"), NotificationVariant.LUMO_WARNING);
            return;
        }

        List<MinioTreeNode> allItems = new ArrayList<>(pathToNodeMap.values());

        // 检查当前是否已经全选
        Set<MinioTreeNode> currentSelected = fileTreeGrid.getSelectedItems();
        boolean isAllSelected = currentSelected.size() == allItems.size() && currentSelected.containsAll(allItems);

        if (isAllSelected) {
            // 已全选，则清空选择（全反选）
            fileTreeGrid.deselectAll();
            showNotification(msg("minioBrowserView.deselectAll"), NotificationVariant.LUMO_SUCCESS);
        } else {
            // 未全选，则全选
            fileTreeGrid.asMultiSelect().setValue(allItems.stream().collect(Collectors.toSet()));
            showNotification(String.format(msg("minioBrowserView.selectedCount"), allItems.size()), NotificationVariant.LUMO_SUCCESS);
        }
    }

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

    private void doDownloadSelected() {
        Set<MinioTreeNode> selected = fileTreeGrid.getSelectedItems();
        if (selected.isEmpty()) {
            showNotification(msg("minioBrowserView.selectToDownload"), NotificationVariant.LUMO_WARNING);
            return;
        }

        // 统计文件数量
        int fileCount = countFilesInSelection(selected);
        if (downloadMaxFiles > 0 && fileCount > downloadMaxFiles) {
            showNotification(String.format(msg("minioBrowserView.tooManyFiles"),
                    fileCount, downloadMaxFiles), NotificationVariant.LUMO_WARNING);
            return;
        }

        // 判断下载方式
        if (selected.size() == 1) {
            MinioTreeNode item = selected.iterator().next();
            if (item.getType() == NodeType.FILE) {
                downloadSingleFile(item);
            } else {
                downloadAsZip(selected);
            }
        } else {
            downloadAsZip(selected);
        }
    }

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

    private void initFileTreeGrid() {
        // 初始化时设置一个空的 DataProvider
        treeData = new TreeData<>();
        pathToNodeMap = new HashMap<>();
        treeDataProvider = new TreeDataProvider<>(treeData);
        fileTreeGrid.setDataProvider(treeDataProvider);

        // 名称列（使用层级列保留树形结构，用 VaadinIcon 替代 emoji）
        // 自动充满剩余空间；文件名超长时省略 + 悬停 tooltip（同 ConfigBrowseView value 字段写法）
        fileTreeGrid.addComponentHierarchyColumn(node -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
            layout.setSpacing(true);
            layout.setPadding(false);
            layout.setMargin(false);

            Icon icon;
            if (node.getType() == NodeType.FOLDER) {
                icon = VaadinIcon.FOLDER.create();
                icon.setColor("#FFA726");
            } else {
                icon = getFileIcon(node.getName());
            }
            icon.setSize("16px");

            Span name = new Span(node.getName());
            // 省略号 + tooltip —— 同 ConfigBrowseView value 字段写法
            name.getStyle().set("overflow", "hidden");
            name.getStyle().set("text-overflow", "ellipsis");
            name.getStyle().set("white-space", "nowrap");
            name.getStyle().set("display", "block");
            // 仅当文本真的被省略时显示 tooltip（动态检测，避免固定字符阈值与动态列宽不匹配）
            name.getElement().executeJs(
                    "var el=this;el.addEventListener('mouseenter',function(){" +
                    "el.title=(el.scrollWidth>el.clientWidth)?el.textContent:'';});");
            // icon 包装结构必需：让 Span 在 HorizontalLayout 内收缩，触发省略号
            layout.setFlexGrow(1, name);
            name.setMinWidth("0");

            layout.add(icon, name);
            return layout;
        }).setHeader(msg("minioBrowserView.columnName")).setKey("name").setFlexGrow(1).setResizable(true);

        fileTreeGrid.addColumn(node -> {
            if (node.getSize() == null) return "-";
            return minioService.formatSize(node.getSize());
        }).setHeader(msg("minioBrowserView.columnSize")).setKey("size").setWidth("100px").setFlexGrow(0).setResizable(true);

        fileTreeGrid.addColumn(node -> {
            if (node.getLastModified() == null) return "-";
            return node.getLastModified().format(DATE_TIME_FORMATTER);
        }).setHeader(msg("minioBrowserView.columnLastModified")).setKey("lastModified").setWidth("180px").setFlexGrow(0).setResizable(true);

        // Shift + 点击范围选择（使用 ClientItemToggleListener）
        GridMultiSelectionModel<MinioTreeNode> selectionModel =
                (GridMultiSelectionModel<MinioTreeNode>) fileTreeGrid.getSelectionModel();
        selectionModel.addClientItemToggleListener(event -> {
            MinioTreeNode item = event.getItem();

            // 如果锚点未设置，设置为当前项
            if (selectionAnchor == null) {
                selectionAnchor = item;
            }

            if (event.isShiftKey()) {
                // 计算锚点和当前项之间的范围
                List<MinioTreeNode> range = getNodesBetween(selectionAnchor, item);
                if (!range.isEmpty()) {
                    // 根据当前项的选中状态更新范围选择
                    if (event.isSelected()) {
                        fileTreeGrid.asMultiSelect().setValue(new HashSet<>(range));
                    } else {
                        // 取消选择：从当前选择中移除范围内的项
                        Set<MinioTreeNode> currentSelection = new HashSet<>(fileTreeGrid.getSelectedItems());
                        currentSelection.removeAll(range);
                        fileTreeGrid.asMultiSelect().setValue(currentSelection);
                    }
                }
            }

            // 更新锚点为当前项
            selectionAnchor = item;
        });

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
    }

    /**
     * 根据文件扩展名获取对应的 VaadinIcon 图标
     */
    private Icon getFileIcon(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();

        VaadinIcon vaadinIcon = switch (extension) {
            // 图片文件
            case "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico" -> VaadinIcon.PICTURE;
            // PDF
            case "pdf" -> VaadinIcon.FILE_TEXT;
            // Word
            case "doc", "docx" -> VaadinIcon.FILE_TEXT;
            // Excel/CSV
            case "xls", "xlsx", "csv" -> VaadinIcon.TABLE;
            // PowerPoint
            case "ppt", "pptx" -> VaadinIcon.FILE_TEXT;
            // 代码文件
            case "java", "js", "ts", "py", "go", "rs", "c", "cpp", "h", "cs", "php", "rb", "swift", "kt", "scala"
                -> VaadinIcon.CODE;
            // 标记语言和配置
            case "html", "htm", "css", "scss", "sass", "less", "xml", "json", "yaml", "yml", "md", "markdown", "sql"
                -> VaadinIcon.FILE_CODE;
            // 压缩文件
            case "zip", "rar", "7z", "tar", "gz", "bz2" -> VaadinIcon.FILE_ZIP;
            // 音频文件
            case "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" -> VaadinIcon.HEADPHONES;
            // 视频文件
            case "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> VaadinIcon.MOVIE;
            // 默认文件图标
            default -> VaadinIcon.FILE;
        };

        Icon icon = vaadinIcon.create();
        icon.setSize("16px");
        icon.setColor("#666666");
        return icon;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

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

    private void showCreateFolderDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogCreateFolderTitle"));
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // 路径选择器
        PathSelector pathSelector = new PathSelector(minioService, messages);
        pathSelector.setBucket(selectedBucket.getName());
        pathSelector.setSelectedPath(inferDefaultPath());

        // 文件夹名称输入
        TextField nameField = new TextField(msg("minioBrowserView.dialogFolderNameField"));
        nameField.setWidthFull();
        nameField.setRequired(true);

        content.add(pathSelector, nameField);

        Button createButton = new Button(msg("minioBrowserView.dialogCreate"), e -> {
            String name = nameField.getValue().trim();
            if (name.isEmpty()) {
                showNotification(msg("minioBrowserView.validationFolderNameEmpty"), NotificationVariant.LUMO_WARNING);
                return;
            }
            try {
                String targetPath = pathSelector.getSelectedPath();
                String folderPath = targetPath + name + "/";
                minioService.createFolder(selectedBucket.getName(), folderPath);
                dialog.close();

                // 增量添加文件夹节点
                MinioTreeNode folderNode = MinioTreeNode.builder()
                        .id(folderPath)
                        .type(NodeType.FOLDER)
                        .name(name)
                        .path(folderPath)
                        .bucket(selectedBucket.getName())
                        .lastModified(LocalDateTime.now())
                        .build();
                addNodeToTree(folderNode);

                showNotification(msg("minioBrowserView.folderCreated"), NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                showNotification(String.format(msg("minioBrowserView.createFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
            }
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.open();
        nameField.focus();
    }

    private void showUploadFileDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogUploadFileTitle"));
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // 路径选择器
        PathSelector pathSelector = new PathSelector(minioService, messages);
        pathSelector.setBucket(selectedBucket.getName());
        pathSelector.setSelectedPath(inferDefaultPath());

        // 使用 Vaadin Upload 组件
        UploadHandler handler = UploadHandler.inMemory((metadata, data) -> {
            String fileName = metadata.fileName();
            long contentLength = metadata.contentLength();
            try {
                String targetPath = pathSelector.getSelectedPath();
                String objectPath = targetPath + fileName;
                minioService.uploadFile(
                        selectedBucket.getName(),
                        objectPath,
                        new ByteArrayInputStream(data),
                        contentLength
                );
                dialog.close();

                // 增量添加节点到树
                MinioTreeNode newNode = MinioTreeNode.builder()
                        .id(objectPath)
                        .type(NodeType.FILE)
                        .name(fileName)
                        .path(objectPath)
                        .bucket(selectedBucket.getName())
                        .size(contentLength)
                        .lastModified(LocalDateTime.now())
                        .build();
                addNodeToTree(newNode);

                showNotification(String.format(msg("minioBrowserView.fileUploaded"), fileName), NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                showNotification(String.format(msg("minioBrowserView.uploadFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
            }
        });

        Upload upload = new Upload(handler);
        upload.setMaxFiles(1);
        upload.setWidthFull();
        upload.setI18n(createUploadI18n());

        upload.addFileRejectedListener(event -> {
            showNotification(String.format(msg("minioBrowserView.fileRejected"), event.getErrorMessage()), NotificationVariant.LUMO_ERROR);
        });

        content.add(pathSelector, upload);

        Button cancelButton = new Button(msg("minioBrowserView.dialogClose"), e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(cancelButton);
        dialog.open();
    }

    private void showUploadFolderDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogUploadFolderTitle"));
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);

        // 路径选择器
        PathSelector pathSelector = new PathSelector(minioService, messages);
        pathSelector.setBucket(selectedBucket.getName());
        pathSelector.setSelectedPath(inferDefaultPath());

        // 说明文字
        Span infoText = new Span(msg("minioBrowserView.uploadFolderInfoText"));
        infoText.getElement().setProperty("whiteSpace", "pre-wrap");
        content.add(pathSelector, infoText);

        // 上传统计
        AtomicInteger uploadedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        Set<String> createdFolders = ConcurrentHashMap.newKeySet();
        Set<String> addedNodePaths = ConcurrentHashMap.newKeySet();

        UploadHandler handler = UploadHandler.inMemory((metadata, data) -> {
            String fileName = metadata.fileName();
            long contentLength = metadata.contentLength();

            try {
                // 统一路径分隔符
                fileName = fileName.replace("\\", "/");

                String targetPath = pathSelector.getSelectedPath();
                String objectPath = targetPath + fileName;

                // 确保所有父文件夹存在
                ensureParentFolders(selectedBucket.getName(), objectPath, createdFolders);

                // 增量添加文件夹节点到树
                addFolderNodesToTree(objectPath, addedNodePaths);

                // 上传文件
                minioService.uploadFile(
                        selectedBucket.getName(),
                        objectPath,
                        new ByteArrayInputStream(data),
                        contentLength
                );

                uploadedCount.incrementAndGet();

            } catch (Exception ex) {
                failedCount.incrementAndGet();
                log.error("上传失败: {}", fileName, ex);
            }
        });

        Upload upload = new Upload(handler);
        upload.setWidthFull();
        upload.setDropAllowed(true);
        upload.setI18n(createUploadI18n());
        String uploadId = "upload-" + System.currentTimeMillis();
        upload.setId(uploadId);

        // 文件上传被拒绝
        upload.addFileRejectedListener(event -> {
            showNotification(String.format(msg("minioBrowserView.fileRejected"), event.getErrorMessage()), NotificationVariant.LUMO_WARNING);
        });

        // 所有文件上传完成
        upload.addAllFinishedListener(event -> {
            int success = uploadedCount.get();
            int failed = failedCount.get();

            dialog.close();

            if (failed > 0) {
                showNotification(String.format(msg("minioBrowserView.uploadPartial"), success, failed),
                        NotificationVariant.LUMO_WARNING);
            } else {
                showNotification(String.format(msg("minioBrowserView.uploadComplete"), success), NotificationVariant.LUMO_SUCCESS);
            }
        });

        content.add(upload);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(cancelButton);
        dialog.open();

        // 在对话框打开后设置 webkitdirectory 属性并修复 vaadin-upload 的路径处理
        getUI().ifPresent(ui -> ui.access(() -> {
            ui.getPage().executeJs(
                "setTimeout(function() {" +
                "  var uploadEl = document.getElementById($0);" +
                "  if (uploadEl) {" +
                // 设置 webkitdirectory 属性
                "    var input = uploadEl.querySelector('input[type=\"file\"]') || " +
                "                (uploadEl.shadowRoot && uploadEl.shadowRoot.querySelector('input[type=\"file\"]'));" +
                "    if (input) {" +
                "      input.setAttribute('webkitdirectory', '');" +
                "      input.setAttribute('directory', '');" +
                "      input.setAttribute('mozdirectory', '');" +
                "    }" +
                // 修复 vaadin-upload 的 _addFiles 方法，使用 webkitRelativePath
                "    if (uploadEl._addFiles) {" +
                "      var originalAddFiles = uploadEl._addFiles.bind(uploadEl);" +
                "      uploadEl._addFiles = function(files) {" +
                "        var processedFiles = [];" +
                "        for (var i = 0; i < files.length; i++) {" +
                "          var file = files[i];" +
                "          if (file.webkitRelativePath) {" +
                // 使用 webkitRelativePath 作为文件名
                "            var newFile = new File([file], file.webkitRelativePath, {type: file.type, lastModified: file.lastModified});" +
                "            newFile.formDataName = file.formDataName || 'file';" +
                "            processedFiles.push(newFile);" +
                "          } else {" +
                "            processedFiles.push(file);" +
                "          }" +
                "        }" +
                "        originalAddFiles(processedFiles);" +
                "      };" +
                "    }" +
                "  }" +
                "}, 100);",
                uploadId
            );
        }));
    }

    /**
     * 添加文件路径中的所有文件夹节点到树
     *
     * @param objectPath     文件对象路径
     * @param addedNodePaths 已添加的节点路径集合
     */
    private void addFolderNodesToTree(String objectPath, Set<String> addedNodePaths) {
        String parentPath = minioService.extractParentPath(objectPath);
        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }

        // 分解路径，逐级添加文件夹节点
        String[] parts = parentPath.split("/");
        StringBuilder currentPathBuilder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            currentPathBuilder.append(part).append("/");
            String folderPath = currentPathBuilder.toString();

            // 使用 Set 避免重复添加
            if (addedNodePaths.add(folderPath)) {
                // 创建文件夹节点
                MinioTreeNode folderNode = MinioTreeNode.builder()
                        .id(folderPath)
                        .type(NodeType.FOLDER)
                        .name(part)
                        .path(folderPath)
                        .bucket(selectedBucket.getName())
                        .lastModified(LocalDateTime.now())
                        .build();
                addNodeToTree(folderNode);
            }
        }
    }

    /**
     * 确保文件的所有父文件夹存在
     * 使用 Set 跟踪已创建的文件夹，避免重复检查
     *
     * @param bucket         Bucket 名称
     * @param objectPath     对象路径
     * @param createdFolders 已创建的文件夹集合
     */
    private void ensureParentFolders(String bucket, String objectPath, Set<String> createdFolders) {
        String parentPath = minioService.extractParentPath(objectPath);

        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }

        // 分解路径，逐级确保文件夹存在
        String[] parts = parentPath.split("/");
        StringBuilder currentPathBuilder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            currentPathBuilder.append(part).append("/");
            String folderPath = currentPathBuilder.toString();

            // 使用 Set 的 add 方法来避免重复创建（add 返回 true 表示是新元素）
            if (createdFolders.add(folderPath)) {
                // 这是一个新文件夹，确保它存在
                log.debug("创建文件夹: bucket={}, path={}", bucket, folderPath);
                minioService.ensureFolderExists(bucket, folderPath);
            }
        }
    }

    private void showDeleteConfirmDialog(Set<MinioTreeNode> items) {
        int fileCount = 0;
        int folderCount = 0;
        for (MinioTreeNode item : items) {
            if (item.getType() == NodeType.FILE) {
                fileCount++;
            } else if (item.getType() == NodeType.FOLDER) {
                folderCount++;
            }
        }

        String message = String.format(msg("minioBrowserView.dialogDeleteFilesConfirm"), fileCount, folderCount);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogConfirmDeleteTitle"));
        dialog.setWidth("400px");

        Span messageSpan = new Span(message);
        messageSpan.getElement().getThemeList().add("warning");

        Button deleteButton = new Button(msg("minioBrowserView.dialogDelete"), e -> {
            try {
                BatchDeleteResult result = minioService.batchDelete(
                        selectedBucket.getName(),
                        new ArrayList<>(items)
                );
                dialog.close();

                // 增量删除节点
                for (MinioTreeNode item : items) {
                    removeNodeFromTree(item);
                }

                showNotification(String.format(msg("minioBrowserView.filesDeleted"),
                        result.getDeletedFiles(), result.getDeletedFolders()),
                        NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                showNotification(String.format(msg("minioBrowserView.deleteFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
            }
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(messageSpan);
        dialog.getFooter().add(cancelButton, deleteButton);
        dialog.open();
    }

    private void refreshFileTree() {
        if (selectedBucket == null) return;
        loadFileTreeData();
    }

    /**
     * 增量添加节点到树
     *
     * @param node 要添加的节点
     */
    private void addNodeToTree(MinioTreeNode node) {
        if (treeData == null || pathToNodeMap == null) return;

        // 检查是否已存在
        if (pathToNodeMap.containsKey(node.getPath())) {
            return;
        }

        // 添加到 Map
        pathToNodeMap.put(node.getPath(), node);

        // 找到父节点
        String parentPath = minioService.extractParentPath(node.getPath());
        MinioTreeNode parent = parentPath != null && !parentPath.isEmpty() ? pathToNodeMap.get(parentPath) : null;

        // 如果有父节点，检查并处理占位符
        if (parent != null) {
            List<MinioTreeNode> children = treeData.getChildren(parent);
            if (!children.isEmpty()) {
                MinioTreeNode firstChild = children.get(0);
                if (firstChild.getPath().endsWith(".placeholder")) {
                    // 父节点未展开过，需要加载完整内容
                    // 移除占位符
                    treeData.removeItem(firstChild);

                    // 从服务器加载该目录的所有子节点
                    List<MinioTreeNode> existingNodes = minioService.listObjects(parent.getBucket(), parent.getPath());
                    for (MinioTreeNode existingNode : existingNodes) {
                        if (!pathToNodeMap.containsKey(existingNode.getPath())) {
                            pathToNodeMap.put(existingNode.getPath(), existingNode);
                            treeData.addItem(parent, existingNode);

                            // 如果是文件夹，添加占位符
                            if (existingNode.getType() == NodeType.FOLDER) {
                                addPlaceholderChild(existingNode);
                            }
                        }
                    }
                }
            }
        }

        // 检查是否已经添加到 TreeData（可能在上面加载时已经添加了）
        if (parent == null) {
            // 根节点
            treeData.addItem(null, node);
        } else {
            // 检查父节点的子节点中是否已包含此节点
            List<MinioTreeNode> siblings = treeData.getChildren(parent);
            boolean alreadyExists = siblings.stream().anyMatch(n -> n.getPath().equals(node.getPath()));
            if (!alreadyExists) {
                treeData.addItem(parent, node);
            }
        }

        // 如果是文件夹，添加占位符子节点
        if (node.getType() == NodeType.FOLDER) {
            addPlaceholderChild(node);
        }

        // 刷新 DataProvider
        treeDataProvider.refreshAll();
    }

    /**
     * 增量删除节点
     *
     * @param node 要删除的节点
     */
    private void removeNodeFromTree(MinioTreeNode node) {
        if (treeData == null || pathToNodeMap == null) return;

        // 从 Map 中删除
        pathToNodeMap.remove(node.getPath());

        // 从 TreeData 中删除（会递归删除子节点）
        try {
            treeData.removeItem(node);
        } catch (IllegalArgumentException e) {
            // 节点可能已经不存在，忽略
            log.debug("节点不存在，跳过删除: {}", node.getPath());
        }

        // 刷新 DataProvider
        treeDataProvider.refreshAll();
    }

    // ==================== 搜索功能方法 ====================

    private void initSearchField() {
        // 回车触发搜索
        searchField.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> {
            String keyword = searchField.getValue().trim();
            if (keyword.length() >= 2 && selectedBucket != null) {
                performSearch(keyword);
            } else if (keyword.length() < 2 && selectedBucket != null) {
                showNotification(msg("minioBrowserView.searchMinLength"), NotificationVariant.LUMO_WARNING);
            }
        });
    }

    private void performSearch(String keyword) {
        // 每次都重新搜索（不做关键词相同判断）
        currentSearchKeyword = keyword;
        searchCursor = null;
        searchResults = new ArrayList<>();

        // 创建搜索结果对话框
        if (searchDialog == null) {
            searchDialog = new Dialog();
            searchDialog.setHeaderTitle(msg("minioBrowserView.searchDialogTitle"));
            searchDialog.setWidth("800px");
            searchDialog.setHeight("600px");

            searchResultGrid = new Grid<>();
            searchResultGrid.setWidthFull();
            searchResultGrid.setHeight("500px");

            // 文件名列
            searchResultGrid.addColumn(MinioTreeNode::getName)
                .setHeader(msg("minioBrowserView.columnFileName"))
                .setAutoWidth(true);

            // 路径列
            searchResultGrid.addColumn(MinioTreeNode::getPath)
                .setHeader(msg("minioBrowserView.columnPath"))
                .setAutoWidth(true);

            // 大小列
            searchResultGrid.addColumn(node -> minioService.formatSize(node.getSize()))
                .setHeader(msg("minioBrowserView.columnSize"))
                .setWidth("100px");

            // 修改时间列
            searchResultGrid.addColumn(node -> {
                if (node.getLastModified() == null) return "-";
                return node.getLastModified().format(DATE_TIME_FORMATTER);
            }).setHeader(msg("minioBrowserView.columnLastModified")).setWidth("150px");

            // 双击定位到文件树
            searchResultGrid.addItemDoubleClickListener(e -> {
                MinioTreeNode item = e.getItem();
                navigateToItem(item);
                searchDialog.close();
            });

            loadMoreButton = new Button(msg("minioBrowserView.searchLoadMore"), e -> loadMoreSearchResults());
            Button closeButton = new Button(msg("minioBrowserView.dialogClose"), e -> searchDialog.close());

            HorizontalLayout footer = new HorizontalLayout(loadMoreButton, closeButton);
            searchDialog.getFooter().add(footer);
            searchDialog.add(searchResultGrid);
        }

        // 加载第一页结果
        loadMoreSearchResults();

        searchDialog.open();
    }

    private void loadMoreSearchResults() {
        if (selectedBucket == null || currentSearchKeyword == null) {
            return;
        }

        try {
            PagedSearchResult result = minioService.searchPaged(
                selectedBucket.getName(),
                currentSearchKeyword,
                searchCursor,
                50
            );

            searchResults.addAll(result.getItems());
            searchResultGrid.setItems(searchResults);
            searchCursor = result.getNextCursor();

            // 更新对话框标题
            String title = String.format(msg("minioBrowserView.searchResultTitle"),
                currentSearchKeyword, searchResults.size());
            if (result.isHasMore()) {
                title += msg("minioBrowserView.searchResultHasMore");
            }
            searchDialog.setHeaderTitle(title);

            // 更新加载更多按钮状态
            loadMoreButton.setVisible(result.isHasMore());

        } catch (Exception e) {
            showNotification(String.format(msg("minioBrowserView.searchFailed"), e.getMessage()), NotificationVariant.LUMO_ERROR);
        }
    }

    private void navigateToItem(MinioTreeNode item) {
        String path = item.getPath();
        String[] parts = path.split("/");

        // 构建需要展开的父文件夹路径列表
        List<String> pathsToExpand = new ArrayList<>();
        StringBuilder currentPathBuilder = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            currentPathBuilder.append(parts[i]).append("/");
            pathsToExpand.add(currentPathBuilder.toString());
        }

        // 异步展开父文件夹并选中目标
        expandAndSelect(pathsToExpand, item, 0);
    }

    /**
     * 递归展开父文件夹，最后选中目标节点
     */
    private void expandAndSelect(List<String> pathsToExpand, MinioTreeNode targetItem, int currentIndex) {
        if (currentIndex >= pathsToExpand.size()) {
            // 所有父文件夹已展开，选中目标节点
            selectTargetNode(targetItem);
            return;
        }

        String folderPath = pathsToExpand.get(currentIndex);
        MinioTreeNode folderNode = pathToNodeMap.get(folderPath);

        if (folderNode == null) {
            // 文件夹节点不存在，可能需要先加载
            showNotification(String.format(msg("minioBrowserView.cannotLocate"), folderPath), NotificationVariant.LUMO_WARNING);
            return;
        }

        // 检查文件夹是否已展开
        if (fileTreeGrid.isExpanded(folderNode)) {
            // 已展开，继续下一级
            expandAndSelect(pathsToExpand, targetItem, currentIndex + 1);
        } else {
            // 展开文件夹并加载子节点
            fileTreeGrid.expand(folderNode);
            loadFolderChildren(folderNode);

            // 延迟后继续展开下一级（等待数据加载）
            fileTreeGrid.getUI().ifPresent(ui -> ui.access(() -> {
                ui.beforeClientResponse(fileTreeGrid, ctx -> {
                    expandAndSelect(pathsToExpand, targetItem, currentIndex + 1);
                });
            }));
        }
    }

    /**
     * 加载文件夹的子节点
     */
    private void loadFolderChildren(MinioTreeNode folderNode) {
        List<MinioTreeNode> children = treeData.getChildren(folderNode);

        // 检查是否已加载（不是占位符）
        if (children.size() == 1 && children.get(0).getPath().endsWith(".placeholder")) {
            // 移除占位符，加载真实数据
            treeData.removeItem(children.get(0));
            pathToNodeMap.remove(children.get(0).getPath());

            List<MinioTreeNode> realChildren = minioService.listObjects(folderNode.getBucket(), folderNode.getPath());
            for (MinioTreeNode child : realChildren) {
                if (!pathToNodeMap.containsKey(child.getPath())) {
                    pathToNodeMap.put(child.getPath(), child);
                    treeData.addItem(folderNode, child);

                    // 如果是文件夹，添加占位符
                    if (child.getType() == NodeType.FOLDER) {
                        addPlaceholderChild(child);
                    }
                }
            }
            treeDataProvider.refreshAll();
        }
    }

        /**
     * 选中目标节点
     */
    private void selectTargetNode(MinioTreeNode targetItem) {
        // 确保 targetItem 在 pathToNodeMap 中
        MinioTreeNode nodeToSelect = pathToNodeMap.get(targetItem.getPath());
        if (nodeToSelect == null) {
            showNotification(String.format(msg("minioBrowserView.fileNotFound"), targetItem.getName()), NotificationVariant.LUMO_WARNING);
            return;
        }

        // 选中节点
        fileTreeGrid.select(nodeToSelect);

        // 获取扁平化列表中的索引
        List<MinioTreeNode> flatList = flattenTreeData();
        int targetIndex = -1;
        for (int i = 0; i < flatList.size(); i++) {
            if (flatList.get(i).equals(nodeToSelect)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex >= 0) {
            // 滚动到目标索引之前的几行，让目标行出现在视口中上部
            int scrollIndex = Math.max(0, targetIndex - 8);
            final int index = scrollIndex;
            UI.getCurrent().access(() -> {
                UI.getCurrent().beforeClientResponse(fileTreeGrid, ctx -> {
                    fileTreeGrid.getElement().executeJs("this.scrollToIndex($0)", index);
                });
            });
        }

        showNotification(String.format(msg("minioBrowserView.located"), targetItem.getName()), NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * 推断默认路径
     * - 如果选中文件夹 → 使用其路径
     * - 如果选中文件 → 使用其父路径
     * - 否则 → 根目录
     */
    private String inferDefaultPath() {
        Optional<MinioTreeNode> selected = fileTreeGrid.getSelectionModel()
                .getFirstSelectedItem();

        if (selected.isPresent()) {
            MinioTreeNode node = selected.get();
            if (node.getType() == NodeType.FOLDER) {
                return node.getPath();
            } else if (node.getType() == NodeType.FILE) {
                return minioService.extractParentPath(node.getPath());
            }
        }

        return "";  // 根目录
    }

    /**
     * 统计选中项目中的文件总数
     */
    private int countFilesInSelection(Set<MinioTreeNode> items) {
        int count = 0;
        for (MinioTreeNode item : items) {
            if (item.getType() == NodeType.FILE) {
                count++;
            } else if (item.getType() == NodeType.FOLDER) {
                count += minioService.countFiles(item.getBucket(), item.getPath());
            }
        }
        return count;
    }

    private void downloadSingleFile(MinioTreeNode item) {
        try {
            // 先读取文件内容到内存
            byte[] fileContent;
            try (InputStream stream = minioService.downloadFile(item.getBucket(), item.getPath())) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int nRead;
                while ((nRead = stream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                fileContent = buffer.toByteArray();
            }

            // 使用 DownloadHandler 创建下载
            DownloadHandler downloadHandler = DownloadHandler.fromInputStream(
                    event -> new DownloadResponse(
                            new ByteArrayInputStream(fileContent),
                            item.getName(),
                            "application/octet-stream",
                            (long) fileContent.length
                    )
            );

            // 使用 Anchor 组件触发下载（正确的 Vaadin 方式）
            Anchor downloadLink = new Anchor();
            downloadLink.setHref(downloadHandler);
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.getElement().getStyle().set("display", "none");

            // 添加到 UI 并触发点击
            UI.getCurrent().access(() -> {
                UI.getCurrent().add(downloadLink);
                downloadLink.getElement().executeJs("this.click()");
                // 延迟移除，确保下载开始
                UI.getCurrent().getPage().executeJs(
                    "setTimeout(function() { $0.remove(); }, 1000);",
                    downloadLink.getElement()
                );
            });

        } catch (Exception ex) {
            showNotification(String.format(msg("minioBrowserView.downloadFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * 打包选中项目为 ZIP 并下载
     */
    private void downloadAsZip(Set<MinioTreeNode> items) {
        try {
            // 确定 ZIP 文件名
            String zipFileName;
            if (items.size() == 1) {
                MinioTreeNode item = items.iterator().next();
                zipFileName = item.getName() + ".zip";
            } else {
                zipFileName = "download.zip";
            }

            // 创建内存中的 ZIP
            ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
            byte[] zipContent;

            try (ZipOutputStream zipOut = new ZipOutputStream(zipBuffer)) {
                // 用于跟踪已使用的 entry 名称，处理重复
                Set<String> usedEntryNames = new HashSet<>();

                // 遍历所有选中项，添加到 ZIP
                for (MinioTreeNode item : items) {
                    if (item.getType() == NodeType.FILE) {
                        // 使用完整路径作为 entry 名称，确保唯一性
                        String entryName = getUniqueEntryName(item.getPath(), usedEntryNames);
                        addFileToZip(zipOut, item, entryName);
                    } else if (item.getType() == NodeType.FOLDER) {
                        addFolderToZip(zipOut, item.getBucket(), item.getPath(), usedEntryNames);
                    }
                }
            }

            // 发送下载
            zipContent = zipBuffer.toByteArray();

            DownloadHandler downloadHandler = DownloadHandler.fromInputStream(
                    event -> new DownloadResponse(
                            new ByteArrayInputStream(zipContent),
                            zipFileName,
                            "application/zip",
                            (long) zipContent.length
                    )
            );

            // 使用 Anchor 组件触发下载（正确的 Vaadin 方式）
            Anchor downloadLink = new Anchor();
            downloadLink.setHref(downloadHandler);
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.getElement().getStyle().set("display", "none");

            // 添加到 UI 并触发点击
            UI.getCurrent().access(() -> {
                UI.getCurrent().add(downloadLink);
                downloadLink.getElement().executeJs("this.click()");
                // 延迟移除，确保下载开始
                UI.getCurrent().getPage().executeJs(
                    "setTimeout(function() { $0.remove(); }, 1000);",
                    downloadLink.getElement()
                );
            });

        } catch (Exception ex) {
            showNotification(String.format(msg("minioBrowserView.downloadFailed"), ex.getMessage()), NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * 添加单个文件到 ZIP
     */
    private void addFileToZip(ZipOutputStream zipOut, MinioTreeNode item, String entryName) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);

        try (InputStream stream = minioService.downloadFile(item.getBucket(), item.getPath())) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = stream.read(buffer)) > 0) {
                zipOut.write(buffer, 0, len);
            }
        }
        zipOut.closeEntry();
    }

    /**
     * 递归添加文件夹到 ZIP
     */
    private void addFolderToZip(ZipOutputStream zipOut, String bucket, String folderPath, Set<String> usedEntryNames) throws Exception {
        List<String> objectPaths = minioService.listFolderObjectPaths(bucket, folderPath);

        for (String objectName : objectPaths) {
            String entryName = getUniqueEntryName(objectName, usedEntryNames);

            ZipEntry entry = new ZipEntry(entryName);
            zipOut.putNextEntry(entry);

            try (InputStream stream = minioService.downloadFile(bucket, objectName)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = stream.read(buffer)) > 0) {
                    zipOut.write(buffer, 0, len);
                }
            }
            zipOut.closeEntry();
        }
    }

    /**
     * 获取唯一的 ZIP entry 名称，处理重复文件名
     */
    private String getUniqueEntryName(String path, Set<String> usedEntryNames) {
        // 去掉前导的 "/"
        String entryName = path.startsWith("/") ? path.substring(1) : path;

        // 如果已经存在，添加数字后缀
        if (usedEntryNames.contains(entryName)) {
            int counter = 1;
            String baseName = entryName;
            int dotIndex = entryName.lastIndexOf('.');
            String extension = "";
            String nameWithoutExt = entryName;

            if (dotIndex > 0 && dotIndex < entryName.length() - 1) {
                extension = entryName.substring(dotIndex);
                nameWithoutExt = entryName.substring(0, dotIndex);
            }

            while (usedEntryNames.contains(entryName)) {
                entryName = nameWithoutExt + "_" + counter + extension;
                counter++;
            }
        }

        usedEntryNames.add(entryName);
        return entryName;
    }

    /**
     * 获取两个节点之间所有已加载的节点（用于 Shift 范围选择）
     */
    private List<MinioTreeNode> getNodesBetween(MinioTreeNode a, MinioTreeNode b) {
        // 扁平化遍历 TreeData，获取所有已加载节点
        List<MinioTreeNode> allNodes = flattenTreeData();

        // 找到两个节点的索引
        int indexA = -1;
        int indexB = -1;
        for (int i = 0; i < allNodes.size(); i++) {
            MinioTreeNode node = allNodes.get(i);
            if (node.equals(a)) {
                indexA = i;
            }
            if (node.equals(b)) {
                indexB = i;
            }
        }

        if (indexA == -1 || indexB == -1) {
            return new ArrayList<>();
        }

        // 返回范围内的节点
        int start = Math.min(indexA, indexB);
        int end = Math.max(indexA, indexB);
        return new ArrayList<>(allNodes.subList(start, end + 1));
    }

    /**
     * 扁平化遍历 TreeData，获取所有已加载节点
     */
    private List<MinioTreeNode> flattenTreeData() {
        List<MinioTreeNode> result = new ArrayList<>();
        if (treeData == null) {
            return result;
        }

        // 递归遍历
        List<MinioTreeNode> roots = treeData.getRootItems();
        for (MinioTreeNode root : roots) {
            flattenNode(root, result);
        }
        return result;
    }

    /**
     * 递归扁平化单个节点及其子节点
     */
    private void flattenNode(MinioTreeNode node, List<MinioTreeNode> result) {
        // 过滤掉占位符节点
        if (!node.getPath().endsWith(".placeholder")) {
            result.add(node);
        }

        List<MinioTreeNode> children = treeData.getChildren(node);
        for (MinioTreeNode child : children) {
            flattenNode(child, result);
        }
    }
}
