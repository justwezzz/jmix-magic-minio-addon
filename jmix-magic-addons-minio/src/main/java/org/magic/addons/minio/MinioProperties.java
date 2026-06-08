package org.magic.addons.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 插件配置属性
 *
 * 在 application.properties 中配置:
 * jmix.minio.endpoint=http://localhost:9000
 * jmix.minio.access-key=minioadmin
 * jmix.minio.secret-key=minioadmin
 */
@Data
@ConfigurationProperties(prefix = "jmix.minio")
public class MinioProperties {

    /**
     * MinIO 服务端点
     */
    private String endpoint;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 私有密钥
     */
    private String secretKey;

    /**
     * 下载配置
     */
    private Download download = new Download();

    /**
     * 上传配置
     */
    private Upload upload = new Upload();

    @Data
    public static class Download {
        /**
         * ZIP 打包最大文件数
         */
        private int maxFiles = 1000;

        /**
         * 单文件最大大小
         */
        private String maxSize = "100MB";
    }

    @Data
    public static class Upload {
        /**
         * 上传最大大小
         */
        private String maxSize = "50MB";
    }
}
