package org.magic.addons.minio.dto;

import java.util.ArrayList;
import java.util.List;

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
     * Merges another BatchUploadResult into this one.
     * Combines success counts, total bytes, and failed files lists.
     *
     * @param other the other BatchUploadResult to merge
     * @return this BatchUploadResult with combined values
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
