package org.magic.addons.autoconfigure.minio;

import org.magic.addons.minio.MinioConfiguration;
import org.magic.addons.minio.MinioProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * MinIO 自动配置类
 *
 * 自动配置 MinioClient 并注册到 Spring 容器
 */
@AutoConfiguration
@Import({MinioConfiguration.class})
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
