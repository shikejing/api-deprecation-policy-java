package com.github.shikejing.apideprecationpolicyjava.versioning;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 版本协商与治理相关配置项。
 */
@ConfigurationProperties(prefix = "api.versioning")
public class VersioningProperties {

    /** 未显式声明版本时使用的默认版本（行为必须确定） */
    private String defaultVersion = "v1";

    /** 版本协商请求头名称 */
    private String headerName = "X-Api-Version";

    /** 是否允许从路径段（如 /api/v1/...）解析版本 */
    private boolean pathNegotiationEnabled = true;

    /** 管理端点鉴权令牌（X-Admin-Token） */
    private String adminToken = "change-me";

    public String getDefaultVersion() { return defaultVersion; }
    public void setDefaultVersion(String defaultVersion) { this.defaultVersion = defaultVersion; }
    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    public boolean isPathNegotiationEnabled() { return pathNegotiationEnabled; }
    public void setPathNegotiationEnabled(boolean pathNegotiationEnabled) { this.pathNegotiationEnabled = pathNegotiationEnabled; }
    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
}
