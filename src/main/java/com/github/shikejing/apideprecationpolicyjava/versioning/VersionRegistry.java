package com.github.shikejing.apideprecationpolicyjava.versioning;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 版本注册表：线程安全地登记与查询各版本的生命周期状态。
 *
 * 并发语义：每个版本对应一个不可变的 {@link VersionInfo} 快照，
 * 状态切换通过 ConcurrentHashMap 原子替换完成，对新请求立即生效；
 * 进行中的请求若已持有旧快照，则在本次请求内始终看到同一份状态，
 * 不会出现新旧混合的响应。
 */
public class VersionRegistry {

    private final ConcurrentHashMap<String, VersionInfo> versions = new ConcurrentHashMap<>();
    private final Clock clock;

    public VersionRegistry() {
        this(Clock.systemUTC());
    }

    public VersionRegistry(Clock clock) {
        this.clock = clock;
    }

    /** 查询单个版本当前状态快照。 */
    public Optional<VersionInfo> find(String version) {
        return Optional.ofNullable(versions.get(version));
    }

    /** 查询全部已登记版本，按版本号排序，保证多次查询结果稳定一致。 */
    public List<VersionInfo> findAll() {
        return versions.values().stream()
                .sorted(Comparator.comparing(VersionInfo::getVersion))
                .toList();
    }

    /** 登记新版本（已存在则原样返回，幂等）。 */
    public VersionInfo register(String version) {
        return versions.computeIfAbsent(version, v ->
                new VersionInfo(v, VersionState.AVAILABLE, null, null, 100, Instant.now(clock)));
    }

    /**
     * 将版本置为已弃用。
     *
     * @param sunsetAt       计划下线时间（机器可解析，写入响应头）
     * @param rolloutPercent 弃用灰度比例 0-100，过渡窗口内可逐步放量或回退
     */
    public VersionInfo deprecate(String version, Instant sunsetAt, int rolloutPercent) {
        if (rolloutPercent < 0 || rolloutPercent > 100) {
            throw new IllegalArgumentException("rolloutPercent must be between 0 and 100");
        }
        if (sunsetAt == null) {
            throw new IllegalArgumentException("sunsetAt must not be null");
        }
        return versions.compute(version, (v, old) -> {
            if (old != null && old.getState() == VersionState.SUNSET) {
                throw new IllegalStateException("version " + v + " is already sunset");
            }
            Instant deprecatedAt = old != null && old.getDeprecatedAt() != null
                    ? old.getDeprecatedAt() : Instant.now(clock);
            return new VersionInfo(v, VersionState.DEPRECATED, deprecatedAt,
                    sunsetAt, rolloutPercent, Instant.now(clock));
        });
    }

    /** 将版本置为已下线。 */
    public VersionInfo sunset(String version) {
        return versions.compute(version, (v, old) -> {
            if (old == null) {
                throw new IllegalStateException("version " + v + " is not registered");
            }
            return new VersionInfo(v, VersionState.SUNSET, old.getDeprecatedAt(),
                    old.getSunsetAt(), old.getRolloutPercent(), Instant.now(clock));
        });
    }

    /** 回退：将弃用中的版本恢复为可用（已下线的版本不可回退）。 */
    public VersionInfo rollback(String version) {
        return versions.compute(version, (v, old) -> {
            if (old == null) {
                throw new IllegalStateException("version " + v + " is not registered");
            }
            if (old.getState() == VersionState.SUNSET) {
                throw new IllegalStateException("version " + v + " is already sunset");
            }
            return new VersionInfo(v, VersionState.AVAILABLE, null, null, 100, Instant.now(clock));
        });
    }
}
