package org.magic.addons.minio.dto;

/**
 * 上传失败的文件信息。
 */
public class FailedFile {

    private String objectName;
    private String errorMessage;

    public FailedFile() {
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String objectName;
        private String errorMessage;

        public Builder objectName(String objectName) {
            this.objectName = objectName;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public FailedFile build() {
            FailedFile failedFile = new FailedFile();
            failedFile.objectName = this.objectName;
            failedFile.errorMessage = this.errorMessage;
            return failedFile;
        }
    }
}
