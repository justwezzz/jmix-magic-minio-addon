package org.magic.addons.minio.security;

import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * MinIO 管理员角色
 * 完整权限：包括 Bucket 管理
 */
@ResourceRole(name = "MinIO Admin", code = "minio-admin")
public interface MinioAdminRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
