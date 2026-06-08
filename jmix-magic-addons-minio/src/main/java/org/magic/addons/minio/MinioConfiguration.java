package org.magic.addons.minio;

import io.jmix.core.annotation.JmixModule;
import io.jmix.core.impl.scanning.AnnotationScanMetadataReaderFactory;
import io.jmix.flowui.FlowuiConfiguration;
import io.jmix.flowui.sys.ViewControllersConfiguration;
import io.jmix.security.SecurityConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Collections;

/**
 * MinIO 插件模块配置
 */
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
        viewControllers.setBasePackages(Collections.singletonList("org.magic.addons.minio.view"));
        return viewControllers;
    }
}
