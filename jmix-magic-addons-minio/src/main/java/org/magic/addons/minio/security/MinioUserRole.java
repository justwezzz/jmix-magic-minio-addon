package org.magic.addons.minio.security;

import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * MinIO 用户角色
 * 常用权限：浏览、上传、下载、删除
 */
@ResourceRole(name = "MinIO User", code = "minio-user")
public interface MinioUserRole {

    @ViewPolicy(viewIds = "minio_BrowserView")
    void minioBrowserView();
}
