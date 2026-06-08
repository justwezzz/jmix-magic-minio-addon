package org.magic.addons.minio.dto;

import java.util.ArrayList;
import java.util.List;

public class UploadFolderResult {

    private int uploadedFiles;
    private int createdFolders;
    private long totalBytes;
    private List<String> failedFiles;
    private List<String> errors;

    public UploadFolderResult() {
        this.failedFiles = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public int getUploadedFiles() {
        return uploadedFiles;
    }

    public void setUploadedFiles(int uploadedFiles) {
        this.uploadedFiles = uploadedFiles;
    }

    public int getCreatedFolders() {
        return createdFolders;
    }

    public void setCreatedFolders(int createdFolders) {
        this.createdFolders = createdFolders;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public List<String> getFailedFiles() {
        return failedFiles;
    }

    public void setFailedFiles(List<String> failedFiles) {
        this.failedFiles = failedFiles;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int uploadedFiles;
        private int createdFolders;
        private long totalBytes;
        private List<String> failedFiles = new ArrayList<>();
        private List<String> errors = new ArrayList<>();

        public Builder uploadedFiles(int uploadedFiles) {
            this.uploadedFiles = uploadedFiles;
            return this;
        }

        public Builder createdFolders(int createdFolders) {
            this.createdFolders = createdFolders;
            return this;
        }

        public Builder totalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
            return this;
        }

        public Builder failedFiles(List<String> failedFiles) {
            this.failedFiles = failedFiles;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public UploadFolderResult build() {
            UploadFolderResult result = new UploadFolderResult();
            result.uploadedFiles = this.uploadedFiles;
            result.createdFolders = this.createdFolders;
            result.totalBytes = this.totalBytes;
            result.failedFiles = this.failedFiles;
            result.errors = this.errors;
            return result;
        }
    }
}
