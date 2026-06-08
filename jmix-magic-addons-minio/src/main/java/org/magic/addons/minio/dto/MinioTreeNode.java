package org.magic.addons.minio.dto;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * MinIO 文件树节点
 *
 * equals/hashCode 基于 path + bucket，确保相同路径的节点被视为同一节点。
 * 这对于 TreeData 的增量更新至关重要。
 */
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

    public MinioTreeNode() {
    }

    // ==================== Getters & Setters ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public MinioTreeNode getParent() {
        return parent;
    }

    public void setParent(MinioTreeNode parent) {
        this.parent = parent;
    }

    // ==================== equals & hashCode ====================

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

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private NodeType type;
        private String name;
        private String path;
        private String bucket;
        private Long size;
        private LocalDateTime lastModified;
        private MinioTreeNode parent;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder size(Long size) {
            this.size = size;
            return this;
        }

        public Builder lastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder parent(MinioTreeNode parent) {
            this.parent = parent;
            return this;
        }

        public MinioTreeNode build() {
            MinioTreeNode node = new MinioTreeNode();
            node.id = this.id;
            node.type = this.type;
            node.name = this.name;
            node.path = this.path;
            node.bucket = this.bucket;
            node.size = this.size;
            node.lastModified = this.lastModified;
            node.parent = this.parent;
            return node;
        }
    }
}
