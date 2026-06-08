# Jmix 插件开发踩坑指南

本文档记录了开发 Jmix MinIO 插件过程中遇到的问题及其解决方案，供后续插件开发参考。

## 目录

1. [视图注册问题](#1-视图注册问题)
2. [路由布局问题](#2-路由布局问题)
3. [视图描述符路径问题](#3-视图描述符路径问题)
4. [消息国际化问题](#4-消息国际化问题)
5. [菜单配置问题](#5-菜单配置问题)

---

## 1. 视图注册问题

### 问题现象

启动应用后访问视图时报错：
```
NoSuchViewException: View 'minio_BrowserView' is not defined
```

### 根本原因

Jmix 通过 `ViewRegistry` 查找视图控制器。对于插件模块，需要显式注册 `@ViewController` 注解的类，否则 Jmix 无法发现它们。

### 解决方案

在插件的 Configuration 类中添加 `ViewControllersConfiguration` Bean：

```java
@Configuration
@ComponentScan(basePackages = "org.magic.addons.minio")
@JmixModule(dependsOn = {FlowuiConfiguration.class, SecurityConfiguration.class})
@PropertySource(name = "org.magic.addons.minio", value = "classpath:/org/magic/addons/minio/module.properties")
public class MinioConfiguration {

    @Bean("minio_ViewControllers")
    public ViewControllersConfiguration viewControllersConfiguration(
            ApplicationContext applicationContext,
            AnnotationScanMetadataReaderFactory metadataReaderFactory) {
        ViewControllersConfiguration viewControllers =
                new ViewControllersConfiguration(applicationContext, metadataReaderFactory);
        // 设置要扫描的视图控制器包路径
        viewControllers.setBasePackages(Collections.singletonList("org.magic.addons.minio.view"));
        return viewControllers;
    }
}
```

### 关键点

- Bean 名称需要有模块前缀（如 `minio_ViewControllers`）避免冲突
- `setBasePackages()` 指定包含 `@ViewController` 注解类的包路径
- 需要注入 `AnnotationScanMetadataReaderFactory` 用于注解扫描

---

## 2. 路由布局问题

### 问题现象

启动时报错：
```
No @ViewController annotation for class StandardMainView
```

### 根本原因

`@Route` 注解的 `layout` 属性指定的布局类必须有 `@ViewController` 注解。`StandardMainView` 是 Jmix 内部类，没有这个注解。

### 解决方案

使用 `DefaultMainViewParent` 作为布局：

```java
// 错误
@Route(value = "minio", layout = StandardMainView.class)

// 正确
@Route(value = "minio", layout = DefaultMainViewParent.class)
```

### 说明

- `DefaultMainViewParent` 是 Jmix 提供的抽象布局类，带有 `@ViewController` 注解
- 它允许视图继承应用的主界面布局（菜单、侧边栏等）

---

## 3. 视图描述符路径问题

### 问题现象

启动时报错：
```
Template not found: minio-browser-view.xml
```

### 根本原因

`@ViewDescriptor` 的 `path` 属性是相对于 classpath 的路径，不是相对于控制器类的路径。

### 解决方案

使用完整的资源路径：

```java
// 错误 - 相对路径找不到
@ViewDescriptor(path = "minio-browser-view.xml")

// 正确 - 相对于 classpath 的完整路径
@ViewDescriptor(path = "minio/minio-browser-view.xml")
```

### 目录结构示例

```
src/main/resources/
└── org/magic/addons/minio/
    └── view/
        └── minio/
            └── minio-browser-view.xml  # @ViewDescriptor(path = "minio/minio-browser-view.xml")
```

---

## 4. 消息国际化问题

### 问题现象

UI 中显示的是消息键（如 `minioBrowserView.createBucket`），而不是翻译后的文本。

### 根本原因

这是最复杂的问题，涉及 Jmix 消息解析机制：

#### 4.1 消息文件位置

`JmixMessageSource` 在初始化时，会为每个 Jmix 模块添加 basename：
```
{moduleBasePackage}/messages
```

例如，模块 `org.magic.addons.minio` 的 basename 是 `org/magic/addons/minio/messages`。

**消息文件必须放在模块的 base package 目录下**，而不是视图控制器的包目录。

#### 4.2 消息键格式

Jmix 消息键使用 `group/key` 格式，其中：
- `group` 是视图控制器类的包名（如 `org.magic.addons.minio.view`）
- `key` 是具体的消息标识（如 `minioBrowserView.createBucket`）

消息文件中的键必须包含完整的消息组前缀。

### 解决方案

#### 步骤 1：正确放置消息文件

```
# 错误位置
src/main/resources/org/magic/addons/minio/view/messages.properties

# 正确位置（模块 base package 目录）
src/main/resources/org/magic/addons/minio/messages.properties
```

#### 步骤 2：使用正确的键格式

```properties
# 错误 - 缺少消息组前缀
minioBrowserView.title=MinIO 文件浏览器
minioBrowserView.createBucket=创建 Bucket

# 正确 - 包含完整消息组路径
org.magic.addons.minio.view/minioBrowserView.title=MinIO 文件浏览器
org.magic.addons.minio.view/minioBrowserView.createBucket=创建 Bucket
```

### 消息解析流程详解

1. 视图 XML 中使用 `msg://minioBrowserView.createBucket`（简短格式）
2. Jmix 根据视图控制器类的包名确定消息组：`org.magic.addons.minio.view`
3. 生成消息代码：`org.magic.addons.minio.view/minioBrowserView.createBucket`
4. `JmixMessageSource` 在 `org/magic/addons/minio/messages.properties` 中查找该键

### 目录结构示例

```
src/main/resources/
└── org/magic/addons/minio/
    ├── module.properties          # 模块配置
    ├── menu.xml                   # 菜单配置
    └── messages.properties        # 消息文件（正确位置）
        # 内容示例：
        # org.magic.addons.minio.view/minioBrowserView.title=MinIO 文件浏览器
        # org.magic.addons.minio.view/minioBrowserView.createBucket=创建 Bucket
```

---

## 5. 菜单配置问题

### 问题现象

应用启动时报错菜单项已存在，或插件菜单不显示。

### 根本原因

- 菜单项 ID 冲突
- `module.properties` 中未正确配置菜单路径

### 解决方案

#### 5.1 配置 module.properties

```properties
# 指定菜单配置文件路径
jmix.ui.menu-config = org/magic/addons/minio/menu.xml
```

#### 5.2 使用唯一的菜单 ID

```xml
<?xml version="1.0" encoding="UTF-8"?>
<menu-config xmlns="http://jmix.io/schema/flowui/menu">
    <!-- 使用模块前缀避免 ID 冲突 -->
    <menu id="minio" title="msg://minio.menu.title" opened="true">
        <item view="minio_BrowserView" title="msg://minio.menu.browser"/>
    </menu>
</menu-config>
```

#### 5.3 避免与应用菜单冲突

如果应用项目已有同名菜单项，需要：
- 修改插件菜单 ID
- 或在应用项目中移除冲突的菜单配置

---

## 快速检查清单

开发新插件时，请确保：

- [ ] Configuration 类有 `@JmixModule` 注解
- [ ] Configuration 类有 `@PropertySource` 指向 `module.properties`
- [ ] 添加了 `ViewControllersConfiguration` Bean 注册视图
- [ ] `@Route` 的 `layout` 使用 `DefaultMainViewParent.class`
- [ ] `@ViewDescriptor` 的 `path` 使用完整 classpath 路径
- [ ] 消息文件放在模块 base package 目录（如 `org/magic/addons/minio/messages.properties`）
- [ ] 消息键包含完整的消息组前缀（如 `org.magic.addons.minio.view/key`）
- [ ] 菜单 ID 使用模块前缀避免冲突

---

## 参考资源

- [Jmix 官方文档 - 视图](https://docs.jmix.io/jmix/flow-ui/views.html)
- [Jmix 官方文档 - 消息国际化](https://docs.jmix.io/jmix/localization.html)
- [Jmix 官方文档 - 插件开发](https://docs.jmix.io/jmix/addons.html)
