package org.magic.jmix.addons.minio.dto;

public enum NodeType {
    ROOT,       // 虚拟根节点
    BUCKET,     // Bucket
    FOLDER,     // 文件夹
    FILE        // 文件
}
