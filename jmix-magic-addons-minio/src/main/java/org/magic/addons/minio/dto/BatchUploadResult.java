package org.magic.addons.minio.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量上传结果。
 */
public class BatchUploadResult {

    private int successCount;
    private List<FailedFile> failedFiles;
    private long totalBytes;

    public BatchUploadResult() {
        this.failedFiles = new ArrayList<>();
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public List<FailedFile> getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(List<FailedFile> failedFiles) {
        this.failedFiles = failedFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    /**
     * 合并另一个批量上传结果到当前结果。
     * 合并成功数量、总字节数和失败文件列表。
     *
     * @param other 要合并的另一个结果
     * @return 合并后的当前结果
     */
    public BatchUploadResult merge(BatchUploadResult other) {
        if (other != null) {
            this.successCount += other.successCount;
            this.totalBytes += other.totalBytes;
            this.failedFiles.addAll(other.failedFiles);
        }
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int successCount;
        private List<FailedFile> failedFiles = new ArrayList<>();
        private long totalBytes;

        public Builder successCount(int successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failedFiles(List<FailedFile> failedFiles) {
            this.failedFiles = failedFiles;
            return this;
        }

        public Builder totalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
            return this;
        }

        public BatchUploadResult build() {
            BatchUploadResult result = new BatchUploadResult();
            result.successCount = this.successCount;
            result.failedFiles = this.failedFiles;
            result.totalBytes = this.totalBytes;
            return result;
        }
    }
}
