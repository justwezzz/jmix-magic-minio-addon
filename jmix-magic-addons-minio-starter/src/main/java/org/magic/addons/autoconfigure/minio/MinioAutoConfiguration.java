package org.magic.addons.autoconfigure.minio;

import org.magic.addons.minio.MinioConfiguration;
import org.magic.addons.minio.MinioProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * MinIO 自动配置类
 *
 * 注册 MinioProperties（支持热更新），MinioClient 由 MinioService 按需创建。
 */
@AutoConfiguration
@Import({MinioConfiguration.class})
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {
}
