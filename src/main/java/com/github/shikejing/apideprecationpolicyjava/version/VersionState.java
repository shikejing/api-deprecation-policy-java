package com.github.shikejing.apideprecationpolicyjava.version;

import java.time.Instant;

/**
 * Immutable snapshot of a version's lifecycle state.
 */
public record VersionState(
        String version,
        VersionStatus status,
        Instant deprecationTime,
        Instant plannedRetirementTime,
        int grayPercent,
        Instant updatedAt,
        String updatedBy
) {
}
