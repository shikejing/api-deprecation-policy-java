package com.github.shikejing.apideprecationpolicyjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for API version management.
 */
@ConfigurationProperties(prefix = "api.version")
public class VersionProperties {

    /** Default version used when the caller does not express one. */
    private String defaultVersion = "v1";

    /** Request header used to express the desired version. */
    private String headerName = "X-API-Version";

    /** URL prefix under which versioned business endpoints are exposed. */
    private String pathPrefix = "/api/";

    /** Header used to identify a caller for gray-release bucketing. */
    private String callerHeaderName = "X-Caller-Id";

    /** Whether planned retirement times are enforced automatically. */
    private boolean enforceRetirement = true;

    /** How often the scheduled retirement check runs (milliseconds). */
    private long retirementCheckIntervalMs = 1000;

    /** Admin account username for version management operations. */
    private String adminUsername = "admin";

    /** Admin account password. */
    private String adminPassword = "admin-pass";

    /** Role required for version management operations. */
    private String adminRole = "ADMIN";

    /** Operator account username (read-only / no management rights). */
    private String operatorUsername = "user";

    /** Operator account password. */
    private String operatorPassword = "user-pass";

    /** Maximum number of audit records kept in memory. */
    private int auditMaxEntries = 10000;

    public String getDefaultVersion() { return defaultVersion; }
    public void setDefaultVersion(String defaultVersion) { this.defaultVersion = defaultVersion; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public String getPathPrefix() { return pathPrefix; }
    public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }

    public String getCallerHeaderName() { return callerHeaderName; }
    public void setCallerHeaderName(String callerHeaderName) { this.callerHeaderName = callerHeaderName; }

    public boolean isEnforceRetirement() { return enforceRetirement; }
    public void setEnforceRetirement(boolean enforceRetirement) { this.enforceRetirement = enforceRetirement; }

    public long getRetirementCheckIntervalMs() { return retirementCheckIntervalMs; }
    public void setRetirementCheckIntervalMs(long retirementCheckIntervalMs) { this.retirementCheckIntervalMs = retirementCheckIntervalMs; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getAdminRole() { return adminRole; }
    public void setAdminRole(String adminRole) { this.adminRole = adminRole; }

    public String getOperatorUsername() { return operatorUsername; }
    public void setOperatorUsername(String operatorUsername) { this.operatorUsername = operatorUsername; }

    public String getOperatorPassword() { return operatorPassword; }
    public void setOperatorPassword(String operatorPassword) { this.operatorPassword = operatorPassword; }

    public int getAuditMaxEntries() { return auditMaxEntries; }
    public void setAuditMaxEntries(int auditMaxEntries) { this.auditMaxEntries = auditMaxEntries; }
}
