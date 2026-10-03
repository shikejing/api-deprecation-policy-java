package com.github.shikejing.apideprecationpolicyjava.admin;

import java.time.Instant;

/**
 * Immutable audit record for a version management operation (allowed or denied).
 */
public record AuditRecord(
        long id,
        Instant timestamp,
        String actor,
        String action,
        String version,
        boolean allowed,
        String detail
) {
}
