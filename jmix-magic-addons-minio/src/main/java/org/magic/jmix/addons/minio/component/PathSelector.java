package org.magic.jmix.addons.minio.component;

import io.jmix.core.Messages;
import org.magic.jmix.addons.minio.dto.MinioTreeNode;
import org.magic.jmix.addons.minio.dto.NodeType;
import org.magic.jmix.addons.minio.service.MinioService;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * 路径选择器组件
 * 包含面包屑导航和文件夹树，支持懒加载
 */
public class PathSelector extends Composite<VerticalLayout> {

    private static final Logger log = LoggerFactory.getLogger(PathSelector.class);
    private static final String MSG_PREFIX = "org.magic.jmix.addons.minio/";

    private final MinioService minioService;
    private final Messages messages;

    // UI 组件
    private Span breadcrumb;
    private Button rootButton;
    private TreeGrid<MinioTreeNode> treeGrid;
    private TreeData<MinioTreeNode> treeData;
    private TreeDataProvider<MinioTreeNode> treeDataProvider;

    // 状态
    private String bucket;
    private String selectedPath = "";

    // 异步懒加载生命周期管理（对齐 MinioBrowserView）：
    // generation —— treeData 每次清空（切 bucket / 重新加载）递增，回调据此丢弃过期结果；
    // loadingPaths —— 正在加载的 path 集合，防止同一文件夹重复展开触发并发任务。
    private long generation = 0;
    private final java.util.Set<String> loadingPaths = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // 组件级线程池（守护线程），懒加载，detach 时关闭
    private ExecutorService loadExecutor;

    public PathSelector(MinioService minioService, Messages messages) {
        this.minioService = minioService;
        this.messages = messages;
    }

    private String msg(String key) {
        return messages.getMessage(MSG_PREFIX + key);
    }

    @Override
    protected VerticalLayout initContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidthFull();

        // 面包屑（宽度铺满，超出显示省略号）
        breadcrumb = new Span(msg("pathSelector.currentPathRoot"));
        breadcrumb.getElement().getThemeList().add("badge contrast");
        breadcrumb.getStyle().set("overflow", "hidden");
        breadcrumb.getStyle().set("text-overflow", "ellipsis");
        breadcrumb.getStyle().set("white-space", "nowrap");
        breadcrumb.getStyle().set("display", "block");
        breadcrumb.getStyle().set("min-width", "0");

        // 根目录按钮（居左，不伸缩）
        rootButton = new Button(msg("pathSelector.root"), VaadinIcon.HOME.create(), e -> {
            selectedPath = "";
            treeGrid.deselectAll();
            updateBreadcrumb();
        });
        rootButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        // 水平布局：根目录按钮居左，面包屑铺满剩余空间
        HorizontalLayout headerLayout = new HorizontalLayout(rootButton, breadcrumb);
        headerLayout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        headerLayout.setSpacing(true);
        headerLayout.setWidthFull();
        headerLayout.setFlexGrow(1, breadcrumb);  // 面包屑铺满剩余空间
        headerLayout.setFlexShrink(1, breadcrumb);  // 允许面包屑收缩，长路径时触发省略号而非撑破容器
        headerLayout.setFlexGrow(0, rootButton);  // 按钮不伸缩
        headerLayout.setFlexShrink(0, rootButton);  // 按钮不收缩，保持固有宽度

        // 文件夹树
        treeGrid = createTreeGrid();
        treeGrid.setHeight("300px");

        layout.add(headerLayout, treeGrid);

        // 组件 detach（对话框关闭）时关闭线程池，避免线程泄漏
        layout.addDetachListener(e -> shutdownLoadExecutor());

        return layout;
    }

    private TreeGrid<MinioTreeNode> createTreeGrid() {
        TreeGrid<MinioTreeNode> grid = new TreeGrid<>();
        grid.setWidthFull();

        // 初始化 TreeData（但不设置 DataProvider，等数据加载后再设置）
        treeData = new TreeData<>();

        // 名称列（使用 HierarchyColumn 显示树形结构和展开箭头）
        grid.addHierarchyColumn(MinioTreeNode::getName)
                .setHeader(msg("pathSelector.selectFolder"))
                .setKey("name")
                .setAutoWidth(true);

        // 选择事件
        grid.addSelectionListener(e -> {
            e.getFirstSelectedItem().ifPresent(node -> {
                selectedPath = node.getPath();
                updateBreadcrumb();
            });
        });

        // 懒加载展开事件
        grid.addExpandListener(event -> {
            for (MinioTreeNode parent : event.getItems()) {
                // 展开时同时选中该节点（解决点击箭头不选中的问题）
                grid.select(parent);
                selectedPath = parent.getPath();
                updateBreadcrumb();

                loadChildrenAsync(parent);
            }
        });

        this.treeGrid = grid;
        return grid;
    }

    private void loadChildren(MinioTreeNode parent) {
        // 检查是否已加载（没有占位符）
        List<MinioTreeNode> children = treeData.getChildren(parent);
        boolean hasPlaceholder = children.stream()
                .anyMatch(n -> n.getPath().endsWith(".placeholder"));

        if (!hasPlaceholder) {
            return; // 已加载，跳过
        }

        // 收集占位符到新列表（避免并发修改）
        List<MinioTreeNode> placeholders = children.stream()
                .filter(n -> n.getPath().endsWith(".placeholder"))
                .collect(Collectors.toList());

        // 加载真实子文件夹（占位符先保留，加载期间显示「加载中...」提示）
        try {
            List<MinioTreeNode> subFolders = minioService.listObjects(bucket, parent.getPath())
                    .stream()
                    .filter(node -> node.getType() == NodeType.FOLDER)
                    .collect(Collectors.toList());

            // 加载完成，移除占位符
            for (MinioTreeNode placeholder : placeholders) {
                treeData.removeItem(placeholder);
            }

            for (MinioTreeNode folder : subFolders) {
                treeData.addItem(parent, folder);
                addPlaceholderChild(folder);
            }

            treeDataProvider.refreshAll();
        } catch (Exception e) {
            log.error("PathSelector 同步加载文件夹失败: bucket={}, path={}", bucket, parent.getPath(), e);
        }
    }

    /**
     * 异步加载子文件夹（用户手动展开触发）。
     * 占位符「加载中...」立即显示，真实数据由后台线程拉取，完成后回 UI 线程填充——
     * 避免同步阻塞导致加载期间只能看到 Vaadin 全局进度条。
     * 三层过期校验：代际、parent 仍在树中、占位符仍在，任一不满足即丢弃。
     */
    private void loadChildrenAsync(MinioTreeNode parent) {
        // 检查是否已加载（没有占位符）
        List<MinioTreeNode> children;
        try {
            children = treeData.getChildren(parent);
        } catch (IllegalArgumentException e) {
            return;  // parent 已不在层级，放弃
        }
        List<MinioTreeNode> placeholders = children.stream()
                .filter(n -> n.getPath().endsWith(".placeholder"))
                .collect(Collectors.toList());
        if (placeholders.isEmpty()) {
            return;  // 已加载，跳过
        }

        // 防重入：同一文件夹加载中再次展开，跳过
        if (!loadingPaths.add(parent.getPath())) {
            return;
        }

        final List<MinioTreeNode> placeholderList = placeholders;
        final String path = parent.getPath();
        final long gen = generation;
        final UI ui = UI.getCurrent();
        final ExecutorService executor = getLoadExecutor();

        try {
            executor.execute(() -> {
                List<MinioTreeNode> subFolders;
                try {
                    subFolders = minioService.listObjects(bucket, path).stream()
                            .filter(node -> node.getType() == NodeType.FOLDER)
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    log.error("PathSelector 异步加载文件夹失败: bucket={}, path={}", bucket, path, e);
                    return;
                } finally {
                    if (ui != null) {
                        ui.access(() -> loadingPaths.remove(path));
                    } else {
                        loadingPaths.remove(path);
                    }
                }
                if (ui == null) {
                    return;
                }
                ui.access(() -> applyAsyncLoadResult(parent, placeholderList, subFolders, gen, path));
            });
        } catch (RejectedExecutionException e) {
            // 对话框已关闭、executor 已 shutdown：静默丢弃并清理加载标记
            loadingPaths.remove(path);
            log.debug("PathSelector 异步加载被拒绝（executor 已关闭）: path={}", path);
        }
    }

    /**
     * 将异步加载结果写回树（在 UI 线程执行）。
     */
    private void applyAsyncLoadResult(MinioTreeNode parent, List<MinioTreeNode> placeholders,
                                      List<MinioTreeNode> subFolders, long gen, String path) {
        try {
            // ① 代际校验：treeData 已重建（切 bucket / 重新加载），整个回调作废
            if (gen != generation) {
                log.debug("PathSelector 异步加载回调过期（代际不匹配）: path={}", path);
                return;
            }
            // ② parent 仍在树中：getChildren 抛异常说明 parent 已不在层级，丢弃
            List<MinioTreeNode> currentChildren = treeData.getChildren(parent);
            // ③ 占位符仍在：用户未做影响该层结构的操作
            boolean anyPlaceholderLeft = currentChildren.stream()
                    .anyMatch(c -> placeholders.contains(c));
            if (!anyPlaceholderLeft) {
                log.debug("PathSelector 异步加载回调过期（占位符已不在）: path={}", path);
                return;
            }

            // 移除占位符
            for (MinioTreeNode placeholder : placeholders) {
                if (currentChildren.contains(placeholder)) {
                    treeData.removeItem(placeholder);
                }
            }

            for (MinioTreeNode folder : subFolders) {
                treeData.addItem(parent, folder);
                addPlaceholderChild(folder);
            }

            treeDataProvider.refreshAll();
        } catch (IllegalArgumentException e) {
            log.debug("PathSelector 异步加载回调防御性拦截: path={}", path);
        }
    }

    /**
     * 获取组件级线程池（懒加载，守护线程）。
     */
    private ExecutorService getLoadExecutor() {
        if (loadExecutor == null) {
            java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.ThreadFactory factory = r -> {
                Thread t = new Thread(r, "minio-pathselector-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            };
            loadExecutor = Executors.newCachedThreadPool(factory);
        }
        return loadExecutor;
    }

    /**
     * 关闭组件级线程池（组件 detach 时调用）。
     */
    private void shutdownLoadExecutor() {
        if (loadExecutor == null) {
            return;
        }
        loadExecutor.shutdown();
        try {
            if (!loadExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                loadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            loadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.debug("minio-pathselector executor shutdown");
    }

    private void updateBreadcrumb() {
        if (selectedPath == null || selectedPath.isEmpty()) {
            breadcrumb.setText(msg("pathSelector.currentPathRoot"));
            breadcrumb.getElement().removeAttribute("title");
            return;
        }

        // 显示完整路径，开头加 / 表示从根目录开始，CSS 自动处理省略
        String display = msg("pathSelector.currentPathPrefix") + " /" + selectedPath;
        breadcrumb.setText(display);

        // 用 JS 检测是否被截断，如果是则设置 tooltip
        breadcrumb.getElement().executeJs(
                "var el = this;" +
                "setTimeout(function() {" +
                "  if (el.scrollWidth > el.clientWidth) {" +
                "    el.title = el.textContent;" +
                "  } else {" +
                "    el.removeAttribute('title');" +
                "  }" +
                "}, 0);"
        );
    }

    // 公共 API
    public String getSelectedPath() {
        return selectedPath;
    }

    public void setSelectedPath(String path) {
        this.selectedPath = path != null ? path : "";
        // 确保 UI 已初始化
        if (breadcrumb != null) {
            updateBreadcrumb();
        }
    }

    /**
     * 设置路径并展开选中对应的节点
     * @param path 目标路径（如 "folder1/folder2"）
     */
    public void setSelectedPathAndExpand(String path) {
        this.selectedPath = path != null ? path : "";
        if (breadcrumb != null) {
            updateBreadcrumb();
        }

        // 如果路径为空，不选中任何节点
        if (selectedPath.isEmpty()) {
            if (treeGrid != null) {
                treeGrid.deselectAll();
            }
            return;
        }

        // 确保 TreeGrid 已初始化
        if (treeGrid == null || treeData == null) {
            return;
        }

        // 展开路径并选中目标节点
        expandAndSelectPath(selectedPath);
    }

    /**
     * 展开路径并选中目标节点
     */
    private void expandAndSelectPath(String targetPath) {
        // 解析路径层级（如 "folder1/folder2" -> ["folder1", "folder2"]）
        String[] parts = targetPath.split("/");
        if (parts.length == 0) {
            return;
        }

        // 逐层展开并查找节点
        MinioTreeNode currentNode = null;
        StringBuilder currentPath = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            currentPath.append(part);

            // 查找当前层级的节点
            MinioTreeNode found = findNodeByPath(currentNode, currentPath.toString());
            if (found == null) {
                // 节点未找到，可能是懒加载未完成，尝试加载
                if (currentNode != null) {
                    loadChildren(currentNode);
                    found = findNodeByPath(currentNode, currentPath.toString());
                }
                if (found == null) {
                    break; // 实在找不到，放弃
                }
            }

            currentNode = found;

            // 如果不是最后一层，展开该节点
            if (i < parts.length - 1) {
                treeGrid.expand(currentNode);
                currentPath.append("/");
            }
        }

        // 选中最终节点
        if (currentNode != null && currentNode.getPath().equals(targetPath)) {
            treeGrid.select(currentNode);
            treeGrid.expand(currentNode);
        }
    }

    /**
     * 在指定父节点下查找指定路径的子节点
     */
    private MinioTreeNode findNodeByPath(MinioTreeNode parent, String path) {
        List<MinioTreeNode> children = parent == null
                ? treeData.getRootItems()
                : treeData.getChildren(parent);

        for (MinioTreeNode child : children) {
            if (child.getPath().equals(path)) {
                return child;
            }
        }
        return null;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
        // 确保 UI 已初始化（getContent() 会触发 initContent()）
        getContent();
        loadRootItems();
    }

    public void loadRootItems() {
        if (bucket == null || bucket.isEmpty()) {
            return;
        }

        // 确保 treeData 已初始化
        if (treeData == null) {
            getContent();
        }

        // 代际递增 + 清加载标记：使此前发起的异步加载回调全部过期失效
        generation++;
        loadingPaths.clear();
        treeData.clear();
        selectedPath = "";
        updateBreadcrumb();

        try {
            // 加载根目录下的文件夹
            List<MinioTreeNode> rootFolders = minioService.listObjects(bucket, null)
                    .stream()
                    .filter(node -> node.getType() == NodeType.FOLDER)
                    .collect(Collectors.toList());

            // 添加到树根
            for (MinioTreeNode folder : rootFolders) {
                treeData.addItem(null, folder);
                addPlaceholderChild(folder);
            }

            // 设置 DataProvider（在添加数据后设置，确保 TreeGrid 正确识别父子关系）
            treeDataProvider = new TreeDataProvider<>(treeData);
            treeGrid.setDataProvider(treeDataProvider);
        } catch (Exception e) {
            log.error("PathSelector 加载根目录文件夹失败: bucket={}", bucket, e);
        }
    }

    /**
     * 添加占位符子节点，显示展开箭头
     */
    private void addPlaceholderChild(MinioTreeNode parent) {
        MinioTreeNode placeholder = MinioTreeNode.builder()
                .id(parent.getPath() + ".placeholder")
                .type(NodeType.FILE)
                .name(msg("pathSelector.loading"))
                .path(parent.getPath() + ".placeholder")
                .bucket(bucket)
                .build();
        treeData.addItem(parent, placeholder);
    }
}
