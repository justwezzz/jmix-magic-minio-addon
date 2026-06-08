package org.magic.addons.minio.dto;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * MinIO 文件树节点
 *
 * equals/hashCode 基于 path + bucket，确保相同路径的节点被视为同一节点。
 * 这对于 TreeData 的增量更新至关重要。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JmixEntity(name = "minio_MinioTreeNode")
public class MinioTreeNode {
    @JmixProperty
    private String id;              // 唯一标识
    @JmixProperty
    private NodeType type;          // 节点类型
    @JmixProperty
    private String name;            // 显示名称
    @JmixProperty
    private String path;            // 完整路径
    @JmixProperty
    private String bucket;          // 所属 bucket
    @JmixProperty
    private Long size;              // 文件大小（字节）
    @JmixProperty
    private LocalDateTime lastModified;  // 修改时间
    @JmixProperty
    private MinioTreeNode parent;   // 父节点（用于树形结构）

    /**
     * equals 基于 path + bucket，确保相同路径的节点被视为同一节点
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MinioTreeNode that = (MinioTreeNode) o;
        return Objects.equals(path, that.path) && Objects.equals(bucket, that.bucket);
    }

    /**
     * hashCode 基于 path + bucket
     */
    @Override
    public int hashCode() {
        return Objects.hash(path, bucket);
    }

    /**
     * 创建虚拟根节点
     */
    public static MinioTreeNode root() {
        return MinioTreeNode.builder()
                .id("root")
                .type(NodeType.ROOT)
                .name("Root")
                .path("")
                .build();
    }

    /**
     * 获取显示图标
     */
    public String getIcon() {
        return switch (type) {
            case ROOT -> "🌐";
            case BUCKET -> "🪣";
            case FOLDER -> "📁";
            case FILE -> "📄";
        };
    }
}
