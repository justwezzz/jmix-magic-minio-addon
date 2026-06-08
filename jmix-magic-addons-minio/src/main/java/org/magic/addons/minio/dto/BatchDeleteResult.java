package org.magic.addons.minio.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchDeleteResult {

    private int deletedFiles;
    private int deletedFolders;
    private List<String> failedItems;
    private List<String> errors;

    public BatchDeleteResult() {
        this.failedItems = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public int getDeletedFiles() {
        return deletedFiles;
    }

    public void setDeletedFiles(int deletedFiles) {
        this.deletedFiles = deletedFiles;
    }

    public int getDeletedFolders() {
        return deletedFolders;
    }

    public void setDeletedFolders(int deletedFolders) {
        this.deletedFolders = deletedFolders;
    }

    public List<String> getFailedItems() {
        return failedItems;
    }

    public void setFailedItems(List<String> failedItems) {
        this.failedItems = failedItems;
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
        private int deletedFiles;
        private int deletedFolders;
        private List<String> failedItems = new ArrayList<>();
        private List<String> errors = new ArrayList<>();

        public Builder deletedFiles(int deletedFiles) {
            this.deletedFiles = deletedFiles;
            return this;
        }

        public Builder deletedFolders(int deletedFolders) {
            this.deletedFolders = deletedFolders;
            return this;
        }

        public Builder failedItems(List<String> failedItems) {
            this.failedItems = failedItems;
            return this;
        }

        public Builder errors(List<String> errors) {
            this.errors = errors;
            return this;
        }

        public BatchDeleteResult build() {
            BatchDeleteResult result = new BatchDeleteResult();
            result.deletedFiles = this.deletedFiles;
            result.deletedFolders = this.deletedFolders;
            result.failedItems = this.failedItems;
            result.errors = this.errors;
            return result;
        }
    }
}
