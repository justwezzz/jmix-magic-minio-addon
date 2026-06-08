package org.magic.addons.minio.dto;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JmixEntity(name = "minio_MinioBucketDto")
public class MinioBucketDto {
    @JmixProperty
    private String name;
    @JmixProperty
    private LocalDateTime creationDate;
}
