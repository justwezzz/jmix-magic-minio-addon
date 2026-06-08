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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * MinIO 插件模块配置
 */
@Configuration
@ComponentScan(basePackages = "org.magic.addons.minio")
@JmixModule(dependsOn = {FlowuiConfiguration.class, SecurityConfiguration.class})
@PropertySource(name = "org.magic.addons.minio", value = "classpath:/org/magic/addons/minio/module.properties")
public class MinioConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MinioConfiguration.class);

    @PostConstruct
    public void init() {
        log.info("=== MinioConfiguration initialized ===");
    }

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
