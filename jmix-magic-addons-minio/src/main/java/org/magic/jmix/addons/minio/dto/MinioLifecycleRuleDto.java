package org.magic.jmix.addons.minio.dto;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;

import java.time.LocalDate;

@JmixEntity(name = "minio_LifecycleRuleDto")
public class MinioLifecycleRuleDto {

    @JmixProperty
    @JmixId
    private String id;

    @JmixProperty
    private Boolean enabled = true;

    @JmixProperty
    private String prefix;

    @JmixProperty
    private LocalDate expirationDate;

    @JmixProperty
    private Integer retentionDays;

    @JmixProperty
    private Integer noncurrentVersionExpirationDays;

    @JmixProperty
    private Boolean expiredObjectDeleteMarker;

    @JmixProperty
    private Integer abortIncompleteMultipartUploadDays;

    public MinioLifecycleRuleDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Integer getNoncurrentVersionExpirationDays() {
        return noncurrentVersionExpirationDays;
    }

    public void setNoncurrentVersionExpirationDays(Integer noncurrentVersionExpirationDays) {
        this.noncurrentVersionExpirationDays = noncurrentVersionExpirationDays;
    }

    public Boolean getExpiredObjectDeleteMarker() {
        return expiredObjectDeleteMarker;
    }

    public void setExpiredObjectDeleteMarker(Boolean expiredObjectDeleteMarker) {
        this.expiredObjectDeleteMarker = expiredObjectDeleteMarker;
    }

    public Integer getAbortIncompleteMultipartUploadDays() {
        return abortIncompleteMultipartUploadDays;
    }

    public void setAbortIncompleteMultipartUploadDays(Integer abortIncompleteMultipartUploadDays) {
        this.abortIncompleteMultipartUploadDays = abortIncompleteMultipartUploadDays;
    }
}
