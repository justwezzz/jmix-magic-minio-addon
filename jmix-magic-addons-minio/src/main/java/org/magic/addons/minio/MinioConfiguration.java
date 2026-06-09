package org.magic.addons.minio;

import io.jmix.core.annotation.JmixModule;
import io.jmix.core.impl.scanning.AnnotationScanMetadataReaderFactory;
import io.jmix.flowui.FlowuiConfiguration;
import io.jmix.flowui.sys.ViewControllersConfiguration;
import io.jmix.security.SecurityConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 线程池用于批量上传
     */
    @Bean("minio_uploadThreadPool")
    public ExecutorService uploadThreadPool(MinioProperties minioProperties) {
        int threadPoolSize = minioProperties.getUpload().getThreadPoolSize();
        AtomicInteger threadCounter = new AtomicInteger(1);

        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setName("minio-upload-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        return Executors.newFixedThreadPool(threadPoolSize, threadFactory);
    }
}
