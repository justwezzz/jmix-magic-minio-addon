# MinIO 插件审查报告

> 审查日期：2026-06-08
> 对照宿主项目（jmix_cc）的 CLAUDE.md 和 Skills 开发规范

---

## 一、MinIO 浏览器布局优化建议

### 现状

当前 MinIO 浏览器是 **Split 左右布局**（30% Bucket 列表 + 70% 文件树），整体结构合理。

### 优化建议

| # | 项目 | 现状 | 建议 |
|---|------|------|------|
| 1 | **硬编码中文** | `selectAllFilesBtn` 文本写死 "全选" | 改为 `msg://` 键引用 |
| 2 | **按钮栏风格不统一** | 上传用了 `dropdownButton`，但删除是独立按钮 | 对齐宿主风格：主操作（创建文件夹、上传）放前面，危险操作（删除）放后面或放入「更多」下拉 |
| 3 | **缺少确认对话框** | 删除 Bucket、删除文件直接执行 | 对齐宿主模式：删除前弹确认框（`dialogs.createOptionDialog`） |
| 4 | **搜索框样式** | `searchField` 宽度写死 15em | 对齐宿主的 genericFilter 风格，或至少适配面板宽度 |
| 5 | **状态栏信息** | `statsLabel` 只显示文件数 | 可增加当前路径面包屑，方便定位 |
| 6 | **右键菜单** | 无 | 可为文件/文件夹增加右键上下文菜单（下载、删除、复制路径等） |

---

## 二、不符合项目开发规范的问题

### 🔴 严重（违反硬性规则）

| # | 问题 | 位置 | 规范 |
|---|------|------|------|
| 1 | **硬编码 UI 文本** | XML 中部分按钮文本未用 `msg://` 键 | CLAUDE.md: "所有标签必须使用 msg:// 键" |
| 2 | **缺少英文 messages** | 只有 `messages.properties` 和 `messages_zh_CN.properties`，没有 `messages_en.properties` | CLAUDE.md: "必须添加到所有 locale 文件" |
| 3 | **单文件 1788 行** | `MinioBrowserView.java` 一个类 1788 行 | 应拆分，业务逻辑抽到 Service |

### 🟡 中等（最佳实践）

| # | 问题 | 说明 |
|---|------|------|
| 4 | **DTO 使用了 Lombok** | `MinioBucketDto`、`MinioTreeNode` 等用了 `@Data`、`@Builder` | CLAUDE.md 禁止 Lombok on entities，DTO 虽然非 JPA 实体豁免，但 Jmix `@JmixEntity` DTO 建议保持一致 |
| 5 | **三个角色权限完全相同** | `MinioAdminRole`、`MinioUserRole`、`MinioMinimalRole` 都只有 `@ViewPolicy(viewIds = "minio_BrowserView")`，没有实际权限区分 | README 描述了三级权限但未实现 |
| 6 | **MinioService 构造器注入** | 用了 `@Autowired` 字段注入 | CLAUDE.md: "Services 使用 constructor injection only" |
| 7 | **视图继承 StandardView** | 应继承宿主项目的 `BaseListView`（如果想在多标签页系统中正常工作） | 需要确认 addon 视图是否适配多标签页系统 |
| 8 | **@Route 使用 DefaultMainViewParent** | 这是标准 Jmix 方式，但在宿主多标签页系统中需要验证兼容性 | 可能需要改为宿主的 `MainView.class` |

### 🟢 轻微

| # | 问题 | 说明 |
|---|------|------|
| 9 | **module.properties 只配了菜单** | 应考虑是否需要其他模块配置 |
| 10 | **PathSelector 组件** | 自定义组件，没有独立的 JavaDoc 或说明 |
