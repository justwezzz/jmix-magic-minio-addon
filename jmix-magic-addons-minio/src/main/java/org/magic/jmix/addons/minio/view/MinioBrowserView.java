package org.magic.jmix.addons.minio.view;

import io.jmix.flowui.view.DefaultMainViewParent;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.data.grid.ContainerDataGridItems;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.DataComponents;
import io.jmix.core.Metadata;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.metamodel.model.MetaPropertyPath;
import org.magic.jmix.addons.minio.component.PathSelector;
import org.magic.jmix.addons.minio.dto.BatchDeleteResult;
import org.magic.jmix.addons.minio.dto.MinioBucketDto;
import org.magic.jmix.addons.minio.dto.MinioTreeNode;
import org.magic.jmix.addons.minio.dto.NodeType;
import org.magic.jmix.addons.minio.dto.PagedSearchResult;
import org.magic.jmix.addons.minio.service.MinioService;
import org.magic.jmix.addons.core.notification.NotificationUtil;
import org.magic.jmix.addons.core.component.TreeGridScrollHelper;
import org.magic.jmix.addons.core.component.CursorLazyGrid;
import org.magic.jmix.addons.core.component.CursorPagedDataProvider;
import org.magic.jmix.addons.core.component.CursorPage;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.FooterRow;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.grid.contextmenu.GridMenuItem;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Route(value = "minio", layout = DefaultMainViewParent.class)
@ViewController(id = "minio_BrowserView")
@ViewDescriptor(path = "minio/minio-browser-view.xml")
public class MinioBrowserView extends StandardView {

    private static final Logger log = LoggerFactory.getLogger(MinioBrowserView.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 游标分页搜索：每页条数（对齐 minioService.searchPaged） */
    private static final int SEARCH_PAGE_SIZE = 50;

    @Autowired
    private MinioService minioService;

    @Autowired
    private DataComponents dataComponents;

    @Autowired
    private Metadata metadata;

    @Autowired
    private io.jmix.core.Messages messages;

    @Autowired
    private io.jmix.flowui.UiComponents uiComponents;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private DataGrid<MinioBucketDto> bucketDataGrid;

    @ViewComponent
    private VerticalLayout bucketPanel;

    @ViewComponent
    private VerticalLayout filePanel;

    private CollectionContainer<MinioBucketDto> bucketDc;

    @ViewComponent
    private TreeDataGrid<MinioTreeNode> fileTreeGrid;

    @ViewComponent
    private TextField searchField;

    private Span statsLabel;

    @ViewComponent
    private io.jmix.flowui.kit.action.Action selectAllFilesAction;

    @Value("${magic.minio.download.max-files:1000}")
    private int downloadMaxFiles;

    // Shift 选择锚点
    private MinioTreeNode selectionAnchor = null;

    private MinioBucketDto selectedBucket;
    private TreeData<MinioTreeNode> treeData;
    private TreeDataProvider<MinioTreeNode> treeDataProvider;
    private Map<String, MinioTreeNode> pathToNodeMap;  // 用于快速查找节点

    // 异步懒加载生命周期管理：
    // treeGeneration —— treeData 每次重建（切 bucket/刷新/清空）递增，回调据此丢弃过期结果；
    // loadingPaths   —— 正在加载的 path 集合，防止同一文件夹重复展开触发并发任务。
    private long treeGeneration = 0;
    private final Set<String> loadingPaths = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // 搜索相关字段
    private Dialog searchDialog;
    private Grid<MinioTreeNode> searchResultGrid;
    private String currentSearchKeyword;
    private boolean searchDialogMaximized = false;

    // 游标分页搜索：provider 引用 + View 级 Executor（生命周期长于 Dialog 内的 Grid，避免 Dialog close 误杀）
    private CursorPagedDataProvider<MinioTreeNode> searchDataProvider;
    private ExecutorService searchExecutor;

    @ViewComponent
    private GridMenuItem<MinioTreeNode> selectFolderContentsItem;

    // 右键菜单目标目录（用于"选中目录下所有文件"）
    private MinioTreeNode contextMenuTargetFolder;

    // 右键菜单点击的节点（用于上传时默认选中目标目录）
    private MinioTreeNode contextMenuTargetNode;

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

        filePanel.getStyle().set("overflow", "visible");

        initBucketGrid();
        initFileTreeGrid();
        initSearchField();

        // 设置文件预览渲染器
        fileTreeGrid.setItemDetailsRenderer(createPreviewRenderer());

        // 设置详情默认不展开（双击触发）
        fileTreeGrid.setDetailsVisibleOnClick(false);

        // 添加选择监听器更新统计
        fileTreeGrid.addSelectionListener(e -> updateSelectedStats());

        // 状态栏：用 fileTreeGrid 的 FooterRow 承载（替代独立 hbox）
        initStatsFooter();

        // 初始化状态栏文本
        updateStats();

        // Ctrl + 鼠标悬停文件名 → 超链接样式 —— 对照 FileStorageBrowseView
        initCtrlLinkHover();

        loadBuckets();

        // View 销毁时关闭游标分页线程池（生命周期绑定 View，而非 Dialog 内的 Grid）—— 对照 FileStorageBrowseView
        addDetachListener(detachEvent -> shutdownSearchExecutor());
    }

    /**
     * 状态栏：用 fileTreeGrid 的 FooterRow 承载（替代独立 hbox）。
     * 单行 footer：statsLabel 放入最左侧 name 列的 footer cell（该列 flexGrow 撑满剩余宽度）。
     * 注意：多选 checkbox 列是框架内部列，不在 getColumns() 内、无法纳入 join，
     * 故文本从 name 列左缘起（非最左），但单行无空白、无 join 异常。
     */
    private void initStatsFooter() {
        statsLabel = new Span();
        statsLabel.getStyle().set("padding", "0").set("margin", "0").set("line-height", "1");

        FooterRow statsFooter = fileTreeGrid.appendFooterRow();
        statsFooter.getCell(fileTreeGrid.getColumnByKey("name")).setComponent(statsLabel);

        // 把 footer 第一行第一个 td（多选 checkbox 列对应的空 cell）塌缩到 0 宽，
        // 让 statsLabel 顶到最左。注入 shadow style（普通 CSS 穿不透 shadow DOM）。
        fileTreeGrid.getElement().executeJs(
                "const s=document.createElement('style');" +
                "s.textContent='tfoot tr:first-child td:first-child" +
                "{width:0!important;min-width:0!important;padding:0!important;}';" +
                "this.shadowRoot.appendChild(s);");
    }

    /**
     * 注入客户端脚本：按住 Ctrl 时鼠标悬停文件名，文件名变超链接样式（蓝色+下划线+手型）。
     * 用 document 级监听 + class 标记，避免每次 mouseover 都做服务端往返。
     * —— 照搬 FileStorageBrowseView#initCtrlLinkHover
     */
    private void initCtrlLinkHover() {
        String js = """
                (function(){
                  if(window.__fsCtrlLink) return;
                  window.__fsCtrlLink = true;
                  function apply(el){ el.style.color='#1976D2'; el.style.textDecoration='underline'; el.style.cursor='pointer'; }
                  function clear(el){ el.style.color=''; el.style.textDecoration=''; el.style.cursor=''; }
                  document.addEventListener('mouseover', function(e){
                    var el = e.target.closest && e.target.closest('.fs-file-name');
                    if(el && e.ctrlKey) apply(el);
                  });
                  document.addEventListener('mouseout', function(e){
                    var el = e.target.closest && e.target.closest('.fs-file-name');
                    if(el) clear(el);
                  });
                  document.addEventListener('keydown', function(e){
                    if(e.key==='Control'){
                      var hovered = document.querySelector('.fs-file-name:hover');
                      if(hovered) apply(hovered);
                    }
                  });
                  document.addEventListener('keyup', function(e){
                    if(e.key==='Control'){ document.querySelectorAll('.fs-file-name').forEach(clear); }
                  });
                })();
                """;
        getElement().executeJs(js);
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
        NotificationUtil.success(msg("minioBrowserView.bucketListRefreshed"));
    }

    @Subscribe("uploadFileAction")
    public void onUploadFileAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            NotificationUtil.warning(msg("minioBrowserView.selectBucketFirst"));
            return;
        }
        showUploadFileDialog(inferUploadTargetPath());
    }

    @Subscribe("uploadFolderAction")
    public void onUploadFolderAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            NotificationUtil.warning(msg("minioBrowserView.selectBucketFirst"));
            return;
        }
        showUploadFolderDialog(inferUploadTargetPath());
    }

    /**
     * 推断上传目标路径（优先使用右键菜单点击的节点）
     */
    private String inferUploadTargetPath() {
        // 优先使用右键菜单点击的节点
        if (contextMenuTargetNode != null) {
            if (contextMenuTargetNode.getType() == NodeType.FOLDER) {
                return contextMenuTargetNode.getPath();
            } else if (contextMenuTargetNode.getType() == NodeType.FILE) {
                return minioService.extractParentPath(contextMenuTargetNode.getPath());
            }
        }

        // 否则使用当前选中的节点
        return inferDefaultPath();
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
            statsLabel.setText(msg("minioBrowserView.connectionFailed"));
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

        // 创建新的 TreeData（代际递增：使此前发起的异步加载回调全部过期失效）
        treeGeneration++;
        loadingPaths.clear();
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
     * 文件夹展开时的懒加载（异步）。
     * 展开时占位符「加载中...」立即显示（RPC 即刻返回），真实数据由后台线程拉取，
     * 完成后回 UI 线程填充——避免同步阻塞导致加载期间只能看到 Vaadin 全局进度条。
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

        // 防重入：同一文件夹加载中再次展开，跳过（add 返回 false 表示已在集合中）
        if (!loadingPaths.add(folder.getPath())) {
            return;
        }

        // 占位符先保留（加载期间显示「加载中...」），后台拉取真实数据
        final MinioTreeNode placeholder = firstChild;
        final String bucket = folder.getBucket();
        final String path = folder.getPath();
        final UI ui = UI.getCurrent();  // 在 UI 线程捕获，供后台线程回调
        final long gen = treeGeneration;  // 捕获当前代际，回调时据此判断 treeData 是否已重建
        final ExecutorService executor = getSearchExecutor();  // 复用 View 级线程池

        try {
            executor.execute(() -> {
                List<MinioTreeNode> subItems;
                try {
                    subItems = minioService.listObjects(bucket, path);
                } catch (Exception e) {
                    log.error("异步加载文件夹失败: bucket={}, path={}", bucket, path, e);
                    return;
                } finally {
                    // 无论成功失败，回到 UI 线程清理加载标记
                    if (ui != null) {
                        ui.access(() -> loadingPaths.remove(path));
                    } else {
                        loadingPaths.remove(path);
                    }
                }
                if (ui == null) {
                    return;
                }
                ui.access(() -> applyExpandResult(folder, placeholder, subItems, gen, bucket, path));
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // View 已 detach、executor 已 shutdown：静默丢弃，并清理加载标记
            loadingPaths.remove(path);
            log.debug("异步加载被拒绝（executor 已关闭）: bucket={}, path={}", bucket, path);
        }
    }

    /**
     * 将异步加载结果写回树（在 UI 线程执行）。
     * 三层过期校验：代际、节点身份、占位符存在性，任一不满足即丢弃，避免污染已变化的 UI 状态。
     */
    private void applyExpandResult(MinioTreeNode folder, MinioTreeNode placeholder,
                                   List<MinioTreeNode> subItems, long gen, String bucket, String path) {
        try {
            // ① 代际校验：treeData 已重建（切 bucket/刷新/清空），整个回调作废
            if (gen != treeGeneration) {
                log.debug("异步加载回调过期（代际不匹配）: bucket={}, path={}", bucket, path);
                return;
            }
            // ② 节点身份校验：folder 仍是当前 treeData 里那个节点（未被删除/替换）。
            //    用 pathToNodeMap.get + == 比较：Map 查询不抛异常，比 getChildren 安全。
            if (pathToNodeMap.get(folder.getPath()) != folder) {
                log.debug("异步加载回调过期（节点已失效）: bucket={}, path={}", bucket, path);
                return;
            }
            // ③ 占位符校验：folder 仍在树中（②通过即保证 getChildren 安全），占位符还在才写入
            if (!treeData.getChildren(folder).contains(placeholder)) {
                log.debug("异步加载回调过期（占位符已不在）: bucket={}, path={}", bucket, path);
                return;
            }

            // 移除占位符节点（同步清理 pathToNodeMap，对齐 loadFolderChildren）
            treeData.removeItem(placeholder);
            pathToNodeMap.remove(placeholder.getPath());

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
            // 展开懒加载后已加载统计已变化，刷新状态栏
            updateStats();
        } catch (IllegalArgumentException e) {
            // 最后防线：理论上前三层已拦住所有「节点不在层级」的情况，
            // 此处仅防御性兜底，保证 UI 状态绝不被回调破坏。
            log.debug("异步加载回调防御性拦截: bucket={}, path={}", bucket, path);
        }
    }

    /**
     * 拼接状态栏完整文本（单 label 模式，对照 FileStorageBrowseView）
     */
    private String buildStatsText() {
        if (selectedBucket == null) {
            return msg("minioBrowserView.selectBucket");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(msg("minioBrowserView.currentBucket"), selectedBucket.getName()));

        // 已加载统计（排除占位符）
        int loadedFolders = 0;
        int loadedFiles = 0;
        if (pathToNodeMap != null) {
            for (MinioTreeNode node : pathToNodeMap.values()) {
                if (!node.getPath().endsWith(".placeholder")) {
                    if (node.getType() == NodeType.FOLDER) {
                        loadedFolders++;
                    } else {
                        loadedFiles++;
                    }
                }
            }
        }
        sb.append(" | ").append(String.format(msg("minioBrowserView.loadedStats"), loadedFolders, loadedFiles));

        // 选中统计
        if (fileTreeGrid != null) {
            Set<MinioTreeNode> selected = fileTreeGrid.getSelectedItems();
            if (!selected.isEmpty()) {
                int selFolders = 0;
                int selFiles = 0;
                for (MinioTreeNode node : selected) {
                    if (node.getType() == NodeType.FOLDER) {
                        selFolders++;
                    } else {
                        selFiles++;
                    }
                }
                sb.append(" | ").append(String.format(msg("minioBrowserView.selectedStats"), selFolders, selFiles));
            }
        }
        return sb.toString();
    }

    private void updateStats() {
        statsLabel.setText(buildStatsText());
        updateSelectAllActionText();
    }

    /**
     * 选中变化时更新状态栏（文本 + 全选按钮）
     */
    private void updateSelectedStats() {
        if (fileTreeGrid == null || statsLabel == null) {
            return;
        }
        statsLabel.setText(buildStatsText());
        updateSelectAllActionText();
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
                    NotificationUtil.success(msg("minioBrowserView.bucketCreated"));
                } catch (Exception ex) {
                    NotificationUtil.error(String.format(msg("minioBrowserView.createFailed"), ex.getMessage()));
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
            NotificationUtil.warning(msg("minioBrowserView.selectBucketFirst"));
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
                NotificationUtil.success(msg("minioBrowserView.bucketDeleted"));
            } catch (IllegalStateException ex) {
                NotificationUtil.error(String.format(msg("minioBrowserView.deleteFailed"), ex.getMessage()));
            } catch (Exception ex) {
                NotificationUtil.error(String.format(msg("minioBrowserView.deleteFailed"), ex.getMessage()));
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
            NotificationUtil.warning(msg("minioBrowserView.selectBucketFirst"));
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
                NotificationUtil.warning(msg("minioBrowserView.sameName"));
                return;
            }
            if (validateBucketName(newName)) {
                try {
                    minioService.renameBucket(selected.getName(), newName);
                    dialog.close();
                    loadBuckets();
                    clearFileTree();
                    NotificationUtil.success(msg("minioBrowserView.bucketRenamed"));
                } catch (Exception ex) {
                    NotificationUtil.error(String.format(msg("minioBrowserView.renameFailed"), ex.getMessage()));
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
            NotificationUtil.error(msg("minioBrowserView.validationBucketNameEmpty"));
            return false;
        }
        if (name.length() < 3 || name.length() > 63) {
            NotificationUtil.error(msg("minioBrowserView.validationBucketNameLength"));
            return false;
        }
        // MinIO Bucket 命名规则：
        // - 只能包含小写字母、数字和短横线
        // - 必须以字母或数字开头和结尾
        if (!name.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$") && name.length() > 1) {
            NotificationUtil.error(msg("minioBrowserView.validationBucketNamePattern"));
            return false;
        }
        // 单字符情况
        if (name.length() == 1 && !name.matches("^[a-z0-9]$")) {
            NotificationUtil.error(msg("minioBrowserView.validationBucketNameAlphanumeric"));
            return false;
        }
        // 两字符情况
        if (name.length() == 2 && !name.matches("^[a-z0-9][a-z0-9]$")) {
            NotificationUtil.error(msg("minioBrowserView.validationBucketNameAlphanumeric"));
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

    private void clearFileTree() {
        treeGeneration++;
        loadingPaths.clear();
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
            NotificationUtil.warning(msg("minioBrowserView.selectBucketFirst"));
            return;
        }
        showCreateFolderDialog(inferUploadTargetPath());
    }

    @Subscribe("deleteFilesAction")
    public void onDeleteFilesAction(final ActionPerformedEvent event) {
        Set<MinioTreeNode> selected = fileTreeGrid.getSelectedItems();
        if (selected.isEmpty()) {
            NotificationUtil.warning(msg("minioBrowserView.selectFilesToDelete"));
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
            NotificationUtil.success(msg("minioBrowserView.filesRefreshed"));
        }
    }

    @Subscribe("selectAllFilesAction")
    public void onSelectAllFilesAction(final ActionPerformedEvent event) {
        if (selectedBucket == null) {
            NotificationUtil.warning(msg("minioBrowserView.selectBucketFirst"));
            return;
        }

        if (pathToNodeMap == null || pathToNodeMap.isEmpty()) {
            NotificationUtil.warning(msg("minioBrowserView.noFilesInDirectory"));
            return;
        }

        // 只全选文件，不选目录
        List<MinioTreeNode> allFiles = pathToNodeMap.values().stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .collect(Collectors.toList());

        if (allFiles.isEmpty()) {
            NotificationUtil.warning(msg("minioBrowserView.noFilesInDirectory"));
            return;
        }

        Set<MinioTreeNode> current = new HashSet<>(fileTreeGrid.getSelectedItems());
        if (isAllFilesSelected()) {
            // 取消所有文件选中（保留已选目录）
            current.removeAll(allFiles);
            fileTreeGrid.asMultiSelect().setValue(current);
            NotificationUtil.success(msg("minioBrowserView.deselectAll"));
        } else {
            // 全选所有文件（保留已选目录）
            current.addAll(allFiles);
            fileTreeGrid.asMultiSelect().setValue(current);
            NotificationUtil.success(String.format(msg("minioBrowserView.selectedCount"), allFiles.size()));
        }

        updateSelectedStats();
    }

    /**
     * 是否已选中所有文件节点（不含目录）
     */
    private boolean isAllFilesSelected() {
        if (pathToNodeMap == null || pathToNodeMap.isEmpty()) {
            return false;
        }
        List<MinioTreeNode> allFiles = pathToNodeMap.values().stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .collect(Collectors.toList());
        if (allFiles.isEmpty()) {
            return false;
        }
        Set<MinioTreeNode> currentFiles = fileTreeGrid.getSelectedItems().stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .collect(Collectors.toSet());
        return currentFiles.size() == allFiles.size() && currentFiles.containsAll(allFiles);
    }

    /**
     * 全选按钮文字切换：全选文件 ↔ 取消全选
     */
    private void updateSelectAllActionText() {
        if (selectAllFilesAction == null) {
            return;
        }
        selectAllFilesAction.setText(isAllFilesSelected()
                ? msg("minioBrowserView.deselectAllBtn")
                : msg("minioBrowserView.selectAll"));
    }

    @Subscribe("selectFolderContentsAction")
    public void onSelectFolderContentsAction(final ActionPerformedEvent event) {
        if (contextMenuTargetFolder == null) {
            return;
        }

        MinioTreeNode folder = contextMenuTargetFolder;

        // 确保子节点已加载（移除占位符并加载真实数据）—— 对齐 loadChildrenIfNeeded
        loadFolderChildren(folder);

        // 收集第一层文件节点（过滤占位符与子文件夹，不递归）—— 对齐 type==FILE
        List<MinioTreeNode> toSelect = treeData.getChildren(folder).stream()
                .filter(node -> !node.getPath().endsWith(".placeholder"))
                .filter(node -> node.getType() == NodeType.FILE)
                .collect(Collectors.toList());

        if (toSelect.isEmpty()) {
            NotificationUtil.warning(msg("minioBrowserView.folderEmpty"));
            return;
        }

        // 追加到现有选中 —— 对齐 getSelectedItems()+addAll
        Set<MinioTreeNode> current = new HashSet<>(fileTreeGrid.getSelectedItems());
        current.addAll(toSelect);
        fileTreeGrid.asMultiSelect().setValue(current);
        NotificationUtil.success(String.format(msg("minioBrowserView.selectedFolderContents"), toSelect.size()));
    }

    private void doDownloadSelected() {
        Set<MinioTreeNode> selected = fileTreeGrid.getSelectedItems();
        if (selected.isEmpty()) {
            NotificationUtil.warning(msg("minioBrowserView.selectToDownload"));
            return;
        }

        // 过滤只保留文件和目录（忽略其他节点）
        List<MinioTreeNode> toDownload = selected.stream()
                .filter(n -> n.getType() == NodeType.FILE || n.getType() == NodeType.FOLDER)
                .collect(java.util.stream.Collectors.toList());

        if (toDownload.isEmpty()) {
            NotificationUtil.warning(msg("minioBrowserView.selectToDownload"));
            return;
        }

        // 单个文件直接下载，其他情况显示确认框
        boolean singleFile = toDownload.size() == 1 && toDownload.get(0).getType() == NodeType.FILE;
        if (singleFile) {
            downloadSingleFile(toDownload.get(0));
        } else {
            // 统计用户选中的文件数和目录数（不递归）
            long fileCount = toDownload.stream().filter(n -> n.getType() == NodeType.FILE).count();
            long folderCount = toDownload.stream().filter(n -> n.getType() == NodeType.FOLDER).count();
            showDownloadConfirmDialog(toDownload, fileCount, folderCount);
        }
    }

    private void showDownloadConfirmDialog(List<MinioTreeNode> toDownload, long fileCount, long folderCount) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogConfirmDownloadTitle"));
        dialog.setWidth("400px");

        String confirmText = folderCount > 0
                ? String.format(msg("minioBrowserView.dialogDownloadFilesWithFolders"), fileCount, folderCount)
                : String.format(msg("minioBrowserView.dialogDownloadFiles"), fileCount);
        Span message = new Span(confirmText);

        Button downloadButton = new Button(msg("minioBrowserView.dialogDownload"), e -> {
            dialog.close();
            // 用户确认后再统计实际文件数量（递归计算）并检查限制
            int actualFileCount = countFilesInSelection(new java.util.HashSet<>(toDownload));
            if (downloadMaxFiles > 0 && actualFileCount > downloadMaxFiles) {
                NotificationUtil.warning(String.format(msg("minioBrowserView.tooManyFiles"),
                        actualFileCount, downloadMaxFiles));
                return;
            }
            downloadAsZip(new java.util.HashSet<>(toDownload));
        });
        downloadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(message);
        dialog.getFooter().add(cancelButton, downloadButton);
        dialog.open();
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
        // 右侧留白，避免 CodeEditor 挡住右上角的关闭按钮 —— 对照 FileStorageBrowseView
        layout.getStyle().set("padding-right", "2em");

        // 关闭按钮
        Button closeBtn = new Button("×", e -> fileTreeGrid.setDetailsVisible(node, false));
        closeBtn.getStyle()
                .set("position", "absolute")
                .set("right", "8px")
                .set("top", "8px")
                .set("z-index", "1");
        closeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        // 使用 UiComponents 创建 CodeEditor（确保正确初始化）
        CodeEditor codeEditor = uiComponents.create(CodeEditor.class);
        codeEditor.setWidthFull();
        codeEditor.setHeight("300px");
        codeEditor.setMode(detectLanguage(getFileExtension(node.getName())));
        codeEditor.setReadOnly(true);
        codeEditor.setShowPrintMargin(false);
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

        // 使用 JavaScript 阻止 Vaadin Grid contextMenu，保留浏览器默认菜单
        layout.getElement().executeJs(
            "this.addEventListener('contextmenu', e => { e.stopPropagation(); }, true);"
        );

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
        layout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

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

        // 使用 JavaScript 阻止 Vaadin Grid contextMenu，保留浏览器默认菜单
        layout.getElement().executeJs(
            "this.addEventListener('contextmenu', e => { e.stopPropagation(); }, true);"
        );

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
            // 文件节点标记：Ctrl + 悬停时变超链接样式 —— 对照 FileStorageBrowseView
            if (node.getType() == NodeType.FILE) {
                name.addClassNames("fs-file-name");
            }
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

            // 文件大小为 0，取消预览 —— 对照 FileStorageBrowseView
            if (item.getSize() == null || item.getSize() == 0) {
                NotificationUtil.warning(msg("minioBrowserView.emptyFile"));
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

        // Ctrl+点击文件：用浏览器打开预览（grid ItemClickListener，可靠触发）—— 对照 FileStorageBrowseView
        fileTreeGrid.addItemClickListener(event -> {
            MinioTreeNode item = event.getItem();
            if (event.isCtrlKey() && item != null && item.getType() == NodeType.FILE) {
                openInNewTab(item);
                // 撤销选中 + 去掉单元格 focus 框（蓝色框）
                fileTreeGrid.deselect(item);
                fileTreeGrid.getElement().executeJs("this.activeItem = null; this.blur();");
            }
        });
    }

    /**
     * 右键菜单动态内容处理 - 控制菜单项显示。
     * 只有右键点击文件夹时才显示"选中目录下所有文件"菜单。
     * 当右键点击预览区域（item为null）时隐藏整个菜单。
     */
    @Install(to = "fileTreeGridContextMenu", subject = "dynamicContentHandler")
    private boolean fileTreeGridContextMenuDynamicContentHandler(MinioTreeNode item) {
        if (item == null) {
            return false;  // 预览区域不显示右键菜单
        }
        // 记录右键目标节点（用于上传时默认选中）
        contextMenuTargetNode = item;

        // 记录右键目标文件夹（用于"选中目录下所有文件"）
        if (item.getType() == NodeType.FOLDER) {
            contextMenuTargetFolder = item;
        } else {
            contextMenuTargetFolder = null;
        }

        // 只有右键点击文件夹时才显示"选中目录下所有文件"菜单
        boolean isFolder = item.getType() == NodeType.FOLDER;
        if (selectFolderContentsItem != null) {
            selectFolderContentsItem.setVisible(isFolder);
        }
        return true;
    }

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

    private static final Set<String> BROWSER_SUPPORTED_EXTENSIONS = Set.of(
        "pdf", "mp3", "wav", "ogg", "aac", "mp4", "webm", "ogv"
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
        "txt", "xml", "json", "md", "log", "csv", "yml", "yaml",
        "html", "htm", "css", "scss", "js", "ts", "java", "py",
        "sql", "properties", "sh", "bat", "gradle", "kt", "go",
        "rs", "c", "cpp", "h", "hpp", "vue", "jsx", "tsx"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico"
    );

    private boolean isTextFile(String extension) {
        return TEXT_EXTENSIONS.contains(extension.toLowerCase());
    }

    private boolean isImageFile(String extension) {
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }

    private boolean isBrowserSupported(String extension) {
        return BROWSER_SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
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

    private void showCreateFolderDialog(String targetPath) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogCreateFolderTitle"));
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // 路径选择器（自动展开定位到 targetPath，对齐上传对话框）
        PathSelector pathSelector = new PathSelector(minioService, messages);
        pathSelector.setBucket(selectedBucket.getName());
        pathSelector.setSelectedPathAndExpand(targetPath);

        // 文件夹名称输入
        TextField nameField = new TextField(msg("minioBrowserView.dialogFolderNameField"));
        nameField.setWidthFull();
        nameField.setRequired(true);

        content.add(pathSelector, nameField);

        Button createButton = new Button(msg("minioBrowserView.dialogCreate"), e -> {
            String name = nameField.getValue().trim();
            if (name.isEmpty()) {
                NotificationUtil.warning(msg("minioBrowserView.validationFolderNameEmpty"));
                return;
            }
            try {
                String selectedPath = pathSelector.getSelectedPath();
                String folderPath = selectedPath + name + "/";
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

                NotificationUtil.success(msg("minioBrowserView.folderCreated"));
            } catch (Exception ex) {
                NotificationUtil.error(String.format(msg("minioBrowserView.createFailed"), ex.getMessage()));
            }
        });
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button(msg("minioBrowserView.dialogCancel"), e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.open();
        nameField.focus();
    }

    private void showUploadFileDialog(String targetPath) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogUploadFileTitle"));
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // 路径选择器
        PathSelector pathSelector = new PathSelector(minioService, messages);
        pathSelector.setBucket(selectedBucket.getName());
        pathSelector.setSelectedPathAndExpand(targetPath);

        // 使用 Vaadin Upload 组件
        UploadHandler handler = UploadHandler.inMemory((metadata, data) -> {
            String fileName = metadata.fileName();
            long contentLength = metadata.contentLength();
            try {
                String targetPathFromSelector = pathSelector.getSelectedPath();
                String objectPath = targetPathFromSelector + fileName;
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

                NotificationUtil.success(String.format(msg("minioBrowserView.fileUploaded"), fileName));
            } catch (Exception ex) {
                NotificationUtil.error(String.format(msg("minioBrowserView.uploadFailed"), ex.getMessage()));
            }
        });

        Upload upload = new Upload(handler);
        upload.setMaxFiles(1);
        upload.setWidthFull();
        upload.setI18n(createUploadI18n());

        upload.addFileRejectedListener(event -> {
            NotificationUtil.error(String.format(msg("minioBrowserView.fileRejected"), event.getErrorMessage()));
        });

        content.add(pathSelector, upload);

        Button cancelButton = new Button(msg("minioBrowserView.dialogClose"), e -> dialog.close());

        dialog.add(content);
        dialog.getFooter().add(cancelButton);
        dialog.open();
    }

    private void showUploadFolderDialog(String targetPath) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(msg("minioBrowserView.dialogUploadFolderTitle"));
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);

        // 路径选择器
        PathSelector pathSelector = new PathSelector(minioService, messages);
        pathSelector.setBucket(selectedBucket.getName());
        pathSelector.setSelectedPathAndExpand(targetPath);

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

                String selectedPath = pathSelector.getSelectedPath();
                String objectPath = selectedPath + fileName;

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
            NotificationUtil.warning(String.format(msg("minioBrowserView.fileRejected"), event.getErrorMessage()));
        });

        // 所有文件上传完成
        upload.addAllFinishedListener(event -> {
            int success = uploadedCount.get();
            int failed = failedCount.get();

            dialog.close();

            if (failed > 0) {
                NotificationUtil.warning(String.format(msg("minioBrowserView.uploadPartial"), success, failed));
            } else {
                NotificationUtil.success(String.format(msg("minioBrowserView.uploadComplete"), success));
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

                NotificationUtil.success(String.format(msg("minioBrowserView.filesDeleted"),
                        result.getDeletedFiles(), result.getDeletedFolders()));
            } catch (Exception ex) {
                NotificationUtil.error(String.format(msg("minioBrowserView.deleteFailed"), ex.getMessage()));
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
                    // 移除占位符（同步清理 pathToNodeMap，对齐 loadFolderChildren）
                    treeData.removeItem(firstChild);
                    pathToNodeMap.remove(firstChild.getPath());

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
        // 新增节点后刷新状态栏统计
        updateStats();
    }

    /**
     * 增量删除节点
     *
     * @param node 要删除的节点
     */
    private void removeNodeFromTree(MinioTreeNode node) {
        if (treeData == null || pathToNodeMap == null) return;

        // 占位符是 UI 占位（显示「加载中...」），非真实 MinIO 对象，禁止删除
        // （防御用户在加载期间误选占位符删除，导致该文件夹变空）
        if (node.getPath().endsWith(".placeholder")) {
            return;
        }
        // 节点身份校验：已被删除 / treeData 已重建则跳过（占位符已被上一行拦截，此处 node 必在 map 中）
        if (pathToNodeMap.get(node.getPath()) != node) {
            return;
        }

        // 递归清理子树：pathToNodeMap 条目 + Grid 选中状态
        // （treeData.removeItem 会递归删 TreeData 子孙，但 pathToNodeMap 和 selection 需手动同步，
        //   否则状态栏的「已加载」「选中」统计仍计入已删节点）
        removeSubtreeFromMapsAndSelection(node);

        // 从 TreeData 中删除（会递归删除子节点）
        try {
            treeData.removeItem(node);
        } catch (IllegalArgumentException e) {
            // 节点可能已经不存在，忽略
            log.debug("节点不存在，跳过删除: {}", node.getPath());
        }

        // 刷新 DataProvider
        treeDataProvider.refreshAll();
        // 删除节点后刷新状态栏统计
        updateStats();
    }

    /**
     * 递归清理节点及其已加载子孙的 pathToNodeMap 条目和 Grid 选中状态（删目录时子孙也要同步清理）。
     */
    private void removeSubtreeFromMapsAndSelection(MinioTreeNode node) {
        // getChildren 在 node 已不在层级时（批量删父子时子先被父递归删除 / 异步加载移除占位符 /
        // treeData 重建后残留引用）会抛 IllegalArgumentException。按空子树处理，继续清理 map/selection。
        List<MinioTreeNode> children;
        try {
            // getChildren 返回不可变实时视图，拷贝后再遍历避免遍历中结构变更
            children = new ArrayList<>(treeData.getChildren(node));
        } catch (IllegalArgumentException e) {
            children = java.util.Collections.emptyList();
        }
        for (MinioTreeNode child : children) {
            removeSubtreeFromMapsAndSelection(child);
        }
        pathToNodeMap.remove(node.getPath());  // remove 不存在的 key 是 no-op，安全
        fileTreeGrid.deselect(node);           // deselect 不存在的项也是 no-op，安全
    }

    // ==================== 搜索功能方法 ====================

    private void initSearchField() {
        // 回车触发搜索
        searchField.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> {
            String keyword = searchField.getValue().trim();
            if (keyword.length() >= 2 && selectedBucket != null) {
                performSearch(keyword);
            } else if (keyword.length() < 2 && selectedBucket != null) {
                NotificationUtil.warning(msg("minioBrowserView.searchMinLength"));
            }
        });
    }

    private void performSearch(String keyword) {
        currentSearchKeyword = keyword;

        // 创建搜索结果对话框
        if (searchDialog == null) {
            initSearchDialog();
        }

        // 切换关键词：重置 provider（清空游标缓存、重新加载第一页）
        if (searchDataProvider != null) {
            searchDataProvider.reset();
        }
        // 初始标题（已加载 0 条），后续随翻页由 itemCount 监听更新
        searchDialog.setHeaderTitle(String.format(msg("minioBrowserView.searchResultTitle"), keyword, 0));
        searchDialog.open();
    }

    private void initSearchDialog() {
        searchDialog = new Dialog();
        searchDialog.setWidth("60%");
        searchDialog.setHeight("70%");
        searchDialog.setResizable(true);
        searchDialog.setModal(true);
        searchDialog.setCloseOnOutsideClick(false);
        searchDialog.setCloseOnEsc(false);
        searchDialog.setDraggable(true);

        // 最大化按钮
        final Button[] maximizeBtnRef = new Button[1];
        Button maximizeBtn = new Button(VaadinIcon.EXPAND.create(), e -> {
            if (searchDialogMaximized) {
                searchDialog.setWidth("60%");
                searchDialog.setHeight("70%");
                maximizeBtnRef[0].setIcon(VaadinIcon.EXPAND.create());
                searchDialogMaximized = false;
            } else {
                searchDialog.setWidth("100vw");
                searchDialog.setHeight("100vh");
                maximizeBtnRef[0].setIcon(VaadinIcon.COMPRESS.create());
                searchDialogMaximized = true;
            }
        });
        maximizeBtnRef[0] = maximizeBtn;
        maximizeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // 关闭按钮
        Button closeBtn = new Button(VaadinIcon.CLOSE.create(), e -> searchDialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        searchDialog.getHeader().add(maximizeBtn, closeBtn);

        searchResultGrid = new Grid<>();
        searchResultGrid.setSizeFull();

        // 文件名列：LitRenderer 高亮匹配关键字（不区分大小写、<mark>），保留省略号/tooltip
        searchResultGrid.addColumn(createHighlightedNameRenderer())
            .setHeader(msg("minioBrowserView.columnFileName")).setFlexGrow(1).setResizable(true);

        // 路径列（省略号 + tooltip）
        searchResultGrid.addComponentColumn(node -> createEllipsisCell(node.getPath()))
            .setHeader(msg("minioBrowserView.columnPath")).setFlexGrow(1).setResizable(true);

        // 大小列
        searchResultGrid.addColumn(node -> minioService.formatSize(node.getSize()))
            .setHeader(msg("minioBrowserView.columnSize")).setWidth("100px").setFlexGrow(0).setResizable(true);

        // 修改时间列
        searchResultGrid.addColumn(node -> {
            if (node.getLastModified() == null) return "-";
            return node.getLastModified().format(DATE_TIME_FORMATTER);
        }).setHeader(msg("minioBrowserView.columnLastModified")).setWidth("180px").setFlexGrow(0).setResizable(true);

        // 双击定位到文件树
        searchResultGrid.addItemDoubleClickListener(e -> {
            MinioTreeNode item = e.getItem();
            searchDialog.close();
            navigateToItem(item);
        });

        // 接入 CursorLazyGrid（游标分页懒加载，替代「加载更多」按钮）
        // 传 View 级 Executor：Dialog close 会使 Grid detach，组件自建 Executor 会随之 shutdown 导致二次搜索失败；
        // 改由 View 持有 Executor（生命周期长于 Dialog 内 Grid），View detach 时统一关闭。—— 对照 FileStorageBrowseView
        searchDataProvider = CursorLazyGrid.install(searchResultGrid, this::fetchSearchPage)
                .pageSize(SEARCH_PAGE_SIZE)
                .executor(getSearchExecutor())
                .apply();

        // 已加载条数变化时更新标题（总数未知，显示「已加载 N 条」）
        searchResultGrid.getGenericDataView().addItemCountChangeListener(event ->
                updateSearchTitle(searchDataProvider.getLoadedCount()));

        searchDialog.add(searchResultGrid);
    }

    /**
     * 更新搜索对话框标题：搜索结果: "keyword" (已加载 N 条)。
     */
    private void updateSearchTitle(int loadedCount) {
        if (currentSearchKeyword == null) {
            return;
        }
        searchDialog.setHeaderTitle(
                String.format(msg("minioBrowserView.searchResultTitle"), currentSearchKeyword, loadedCount));
    }

    /**
     * 游标分页 fetcher：包一层 minioService.searchPaged（真游标），返回 CursorPage。
     * 在后台 executor 线程执行，异常捕获后回 UI 线程通知。
     */
    private CursorPage<MinioTreeNode> fetchSearchPage(String cursor) {
        if (selectedBucket == null || currentSearchKeyword == null) {
            return CursorPage.of(new ArrayList<>(), null, false);
        }
        try {
            PagedSearchResult result = minioService.searchPaged(
                    selectedBucket.getName(),
                    currentSearchKeyword,
                    cursor,
                    SEARCH_PAGE_SIZE);
            return CursorPage.of(result.getItems(), result.getNextCursor(), result.isHasMore());
        } catch (Exception e) {
            log.error("游标分页搜索失败: bucket={}, keyword={}", selectedBucket.getName(), currentSearchKeyword, e);
            // 后台线程：回 UI 线程显示错误通知
            UI currentUi = UI.getCurrent();
            if (currentUi != null) {
                String failedMsg = String.format(msg("minioBrowserView.searchFailed"), e.getMessage());
                currentUi.access(() -> NotificationUtil.error(failedMsg));
            }
            return CursorPage.of(new ArrayList<>(), null, false);
        }
    }

    /**
     * 文件名列渲染器：LitRenderer 高亮匹配关键字的片段（不区分大小写、第一处匹配），
     * 保留省略号与 tooltip。文件名拆为「前段/匹配段/后段」三个属性，
     * 模板对匹配段硬编码 <mark>（三段经 ${item.xxx} 各自转义，防 XSS）。—— 对照 FileStorageBrowseView
     */
    private LitRenderer<MinioTreeNode> createHighlightedNameRenderer() {
        return LitRenderer.<MinioTreeNode>of("""
                <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;display:block"
                      title="${item.name}"
                      @mouseenter="this.title=(this.scrollWidth>this.clientWidth)?this.textContent:''">
                  ${item.before}<mark>${item.match}</mark>${item.after}
                </span>
                """)
                .withProperty("name", node -> node.getName() == null ? "" : node.getName())
                .withProperty("before", node -> highlightSegments(node.getName()).before())
                .withProperty("match", node -> highlightSegments(node.getName()).match())
                .withProperty("after", node -> highlightSegments(node.getName()).after());
    }

    /**
     * 文件名按当前搜索关键字拆分：第一处匹配段 + 前段 + 后段（不区分大小写）。
     */
    private NameSegments highlightSegments(String name) {
        if (name == null || currentSearchKeyword == null || currentSearchKeyword.isEmpty()) {
            return new NameSegments(name == null ? "" : name, "", "");
        }
        int pos = name.toLowerCase().indexOf(currentSearchKeyword.toLowerCase());
        if (pos < 0) {
            return new NameSegments(name, "", "");
        }
        int end = pos + currentSearchKeyword.length();
        return new NameSegments(name.substring(0, pos), name.substring(pos, end), name.substring(end));
    }

    /** 文件名拆分段：前段 / 匹配段 / 后段。 */
    private record NameSegments(String before, String match, String after) {
    }

    /**
     * 创建游标分页线程池（守护线程，View 级生命周期）。
     */
    private ExecutorService createSearchExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "minio-search-cursor-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newCachedThreadPool(threadFactory);
    }

    /**
     * 获取 View 级线程池（懒加载），供游标分页搜索与文件夹异步懒加载复用。
     */
    private ExecutorService getSearchExecutor() {
        if (searchExecutor == null) {
            searchExecutor = createSearchExecutor();
        }
        return searchExecutor;
    }

    /**
     * 关闭游标分页线程池（View detach 时调用）。
     */
    private void shutdownSearchExecutor() {
        if (searchExecutor == null) {
            return;
        }
        searchExecutor.shutdown();
        try {
            if (!searchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            searchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.debug("minio-search-cursor executor shutdown");
    }

    /**
     * 创建省略号单元格（文本超出显示省略号，悬停显示完整内容 tooltip）
     */
    private Span createEllipsisCell(String text) {
        Span span = new Span(text);
        span.getStyle().set("overflow", "hidden");
        span.getStyle().set("text-overflow", "ellipsis");
        span.getStyle().set("white-space", "nowrap");
        span.getStyle().set("display", "block");
        span.getElement().executeJs(
            "var el=this;el.addEventListener('mouseenter',function(){" +
            "el.title=(el.scrollWidth>el.clientWidth)?el.textContent:'';});");
        return span;
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
            NotificationUtil.warning(String.format(msg("minioBrowserView.cannotLocate"), folderPath));
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
            // 先缓存引用：getChildren 返回内部列表的实时视图（UnmodifiableList），
            // removeItem 后视图同步变空，不能再 children.get(0) —— 对照 onFolderExpand:373
            MinioTreeNode placeholder = children.get(0);
            treeData.removeItem(placeholder);
            pathToNodeMap.remove(placeholder.getPath());

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
            // 加载子节点后刷新状态栏统计
            updateStats();
        }
    }

        /**
     * 选中目标节点
     */
    private void selectTargetNode(MinioTreeNode targetItem) {
        // 确保 targetItem 在 pathToNodeMap 中
        MinioTreeNode nodeToSelect = pathToNodeMap.get(targetItem.getPath());
        if (nodeToSelect == null) {
            NotificationUtil.warning(String.format(msg("minioBrowserView.fileNotFound"), targetItem.getName()));
            return;
        }

        // 选中节点
        fileTreeGrid.select(nodeToSelect);

        // 使用 TreeGridScrollHelper 滚动到目标节点
        Optional<Integer> idx = TreeGridScrollHelper.scrollToNode(
            fileTreeGrid,
            treeData,
            nodeToSelect,
            node -> !node.getPath().endsWith(".placeholder"),
            null
        );

        NotificationUtil.success(String.format(msg("minioBrowserView.located"), targetItem.getName()));
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
            NotificationUtil.error(String.format(msg("minioBrowserView.downloadFailed"), ex.getMessage()));
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
                        // 文件保留完整路径
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
            NotificationUtil.error(String.format(msg("minioBrowserView.downloadFailed"), ex.getMessage()));
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
            // 保留完整路径
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
