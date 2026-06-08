package org.magic.addons.minio.dto;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;

import java.time.LocalDateTime;

@JmixEntity(name = "minio_MinioBucketDto")
public class MinioBucketDto {

    @JmixProperty
    private String name;
    @JmixProperty
    private LocalDateTime creationDate;

    public MinioBucketDto() {
    }

    public MinioBucketDto(String name, LocalDateTime creationDate) {
        this.name = name;
        this.creationDate = creationDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
}
