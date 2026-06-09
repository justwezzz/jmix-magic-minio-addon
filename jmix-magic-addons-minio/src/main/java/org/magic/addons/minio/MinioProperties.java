package org.magic.addons.minio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 插件配置属性
 *
 * 在 application.properties 中配置:
 * jmix.minio.endpoint=http://localhost:9000
 * jmix.minio.access-key=minioadmin
 * jmix.minio.secret-key=minioadmin
 */
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

    // ==================== Getters & Setters ====================

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Download getDownload() {
        return download;
    }

    public void setDownload(Download download) {
        this.download = download;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    // ==================== Inner classes ====================

    public static class Download {
        /**
         * ZIP 打包最大文件数
         */
        private int maxFiles = 1000;

        /**
         * 单文件最大大小
         */
        private String maxSize = "100MB";

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public String getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(String maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static class Upload {
        /**
         * 上传最大大小
         */
        private String maxSize = "50MB";

        /**
         * 批量上传线程池大小
         */
        private int threadPoolSize = 10;

        /**
         * 批量上传批次大小
         */
        private int batchSize = 50;

        public String getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(String maxSize) {
            this.maxSize = maxSize;
        }

        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public void setThreadPoolSize(int threadPoolSize) {
            if (threadPoolSize <= 0) {
                throw new IllegalArgumentException("threadPoolSize must be greater than 0, got: " + threadPoolSize);
            }
            this.threadPoolSize = threadPoolSize;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be greater than 0, got: " + batchSize);
            }
            this.batchSize = batchSize;
        }
    }
}
