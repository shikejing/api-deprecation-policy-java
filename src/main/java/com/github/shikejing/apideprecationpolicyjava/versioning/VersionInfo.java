package com.github.shikejing.apideprecationpolicyjava.versioning;

import java.time.Instant;

/**
 * 单个版本的不可变生命周期快照。任何状态变更都产生新的实例，
 * 保证单次请求内读取到的状态一致，不会出现新旧混合。
 */
public final class VersionInfo {

    private final String version;
    private final VersionState state;
    private final Instant deprecatedAt;
    private final Instant sunsetAt;
    /** 弃用灰度比例 0-100：仅对该比例的请求呈现弃用语义 */
    private final int rolloutPercent;
    private final Instant updatedAt;

    public VersionInfo(String version, VersionState state, Instant deprecatedAt,
                       Instant sunsetAt, int rolloutPercent, Instant updatedAt) {
        this.version = version;
        this.state = state;
        this.deprecatedAt = deprecatedAt;
        this.sunsetAt = sunsetAt;
        this.rolloutPercent = rolloutPercent;
        this.updatedAt = updatedAt;
    }

    public String getVersion() { return version; }
    public VersionState getState() { return state; }
    public Instant getDeprecatedAt() { return deprecatedAt; }
    public Instant getSunsetAt() { return sunsetAt; }
    public int getRolloutPercent() { return rolloutPercent; }
    public Instant getUpdatedAt() { return updatedAt; }
}
