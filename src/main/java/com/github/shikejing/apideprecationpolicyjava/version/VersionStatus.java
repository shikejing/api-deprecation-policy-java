package com.github.shikejing.apideprecationpolicyjava.version;

/**
 * Lifecycle status of an API version.
 */
public enum VersionStatus {
    /** Available for all callers. */
    AVAILABLE,
    /** Deprecated: responses carry deprecation / sunset headers. */
    DEPRECATED,
    /** Retired: all calls are rejected. */
    RETIRED
}
