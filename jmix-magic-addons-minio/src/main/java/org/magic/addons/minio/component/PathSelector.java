package org.magic.addons.minio.component;

import org.magic.addons.minio.dto.MinioTreeNode;
import org.magic.addons.minio.dto.NodeType;
import org.magic.addons.minio.service.MinioService;
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

/**
 * 路径选择器组件
 * 包含面包屑导航和文件夹树，支持懒加载
 */
public class PathSelector extends Composite<VerticalLayout> {

    private final MinioService minioService;

    // UI 组件
    private Span breadcrumb;
    private Button rootButton;
    private TreeGrid<MinioTreeNode> treeGrid;
    private TreeData<MinioTreeNode> treeData;
    private TreeDataProvider<MinioTreeNode> treeDataProvider;

    // 状态
    private String bucket;
    private String selectedPath = "";

    public PathSelector(MinioService minioService) {
        this.minioService = minioService;
    }

    @Override
    protected VerticalLayout initContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setWidthFull();

        // 面包屑和返回根目录按钮
        breadcrumb = new Span("当前路径: 根目录");
        breadcrumb.getElement().getThemeList().add("badge contrast");

        rootButton = new Button("根目录", VaadinIcon.HOME.create(), e -> {
            selectedPath = "";
            treeGrid.deselectAll();
            updateBreadcrumb();
        });
        rootButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout headerLayout = new HorizontalLayout(breadcrumb, rootButton);
        headerLayout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        headerLayout.setSpacing(true);

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
        grid.addHierarchyColumn(node -> {
            // 使用 Unicode 符号作为图标前缀
            String iconPrefix = node.getType() == NodeType.FOLDER ? "📁 " : "📄 ";
            return iconPrefix + node.getName();
        }).setHeader("选择目标文件夹").setKey("name").setAutoWidth(true);

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
                .collect(java.util.stream.Collectors.toList());

        // 移除占位符
        for (MinioTreeNode placeholder : placeholders) {
            treeData.removeItem(placeholder);
        }

        // 加载真实子文件夹
        try {
            List<MinioTreeNode> subFolders = minioService.listObjects(bucket, parent.getPath())
                    .stream()
                    .filter(node -> node.getType() == NodeType.FOLDER)
                    .collect(java.util.stream.Collectors.toList());

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
            breadcrumb.setText("当前路径: 根目录");
            return;
        }

        // 解析路径层级
        String[] parts = selectedPath.replace("/", " ").trim().split("\\s+");
        StringBuilder display = new StringBuilder("当前路径: ");

        if (parts.length <= 3) {
            // 3级以内完整显示
            for (String part : parts) {
                if (!part.isEmpty()) {
                    display.append("📁 ").append(part).append(" / ");
                }
            }
        } else {
            // 超过3级省略前面
            display.append("... / ");
            for (int i = parts.length - 2; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    display.append("📁 ").append(parts[i]).append(" / ");
                }
            }
        }

        breadcrumb.setText(display.toString());
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
                    .collect(java.util.stream.Collectors.toList());

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
                .name("加载中...")
                .path(parent.getPath() + ".placeholder")
                .bucket(bucket)
                .build();
        treeData.addItem(parent, placeholder);
    }
}
