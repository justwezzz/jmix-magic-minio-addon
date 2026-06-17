package org.magic.jmix.addons.minio.component;

import io.jmix.core.Messages;
import org.magic.jmix.addons.minio.dto.MinioTreeNode;
import org.magic.jmix.addons.minio.dto.NodeType;
import org.magic.jmix.addons.minio.service.MinioService;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 路径选择器组件
 * 包含面包屑导航和文件夹树，支持懒加载
 */
public class PathSelector extends Composite<VerticalLayout> {

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
        breadcrumb.setWidthFull();

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
        headerLayout.setFlexGrow(0, rootButton);  // 按钮不伸缩

        // 文件夹树
        treeGrid = createTreeGrid();
        treeGrid.setHeight("300px");

        layout.add(headerLayout, treeGrid);
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

                loadChildren(parent);
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

        // 移除占位符
        for (MinioTreeNode placeholder : placeholders) {
            treeData.removeItem(placeholder);
        }

        // 加载真实子文件夹
        try {
            List<MinioTreeNode> subFolders = minioService.listObjects(bucket, parent.getPath())
                    .stream()
                    .filter(node -> node.getType() == NodeType.FOLDER)
                    .collect(Collectors.toList());

            for (MinioTreeNode folder : subFolders) {
                treeData.addItem(parent, folder);
                addPlaceholderChild(folder);
            }

            treeDataProvider.refreshAll();
        } catch (Exception e) {
            // 加载失败
        }
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
            // 加载失败时显示空树
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
