package org.magic.addons.minio.security;

import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * MinIO 最小权限角色
 * 只读权限：浏览文件、下载
 */
@ResourceRole(name = "MinIO Minimal", code = "minio-minimal")
public interface MinioMinimalRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
