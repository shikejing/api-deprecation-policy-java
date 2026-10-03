package com.github.shikejing.apideprecationpolicyjava.version;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the lifecycle state of every registered API version.
 *
 * <p>State objects are immutable and transitions are performed atomically via
 * {@link ConcurrentHashMap#compute}, so concurrent state changes never produce
 * torn or mixed states. A transition only affects requests that start after it;
 * in-flight requests keep the snapshot captured by the negotiation filter.
 */
@Component
public class VersionRegistry {

    private final ConcurrentHashMap<String, VersionState> versions = new ConcurrentHashMap<>();

    @Value("${api.version.retirement-check-interval-ms:1000}")
    private long retirementCheckIntervalMs;

    /**
     * Registers a new version in {@link VersionStatus#AVAILABLE} state.
     *
     * @throws InvalidVersionTransitionException if the version is already registered
     */
    public VersionState register(String version) {
        VersionState state = new VersionState(version, VersionStatus.AVAILABLE, null, null, 100,
                Instant.now(), "system");
        VersionState existing = versions.putIfAbsent(version, state);
        if (existing != null) {
            throw new InvalidVersionTransitionException("Version already registered: " + version);
        }
        return state;
    }

    public Optional<VersionState> find(String version) {
        return Optional.ofNullable(versions.get(version));
    }

    /**
     * Returns the current immutable state snapshot of the version.
     *
     * @throws VersionNotFoundException if the version is not registered
     */
    public VersionState require(String version) {
        VersionState state = versions.get(version);
        if (state == null) {
            throw new VersionNotFoundException(version);
        }
        return state;
    }

    public Collection<VersionState> list() {
        return List.copyOf(versions.values());
    }

    /**
     * Transitions a version to {@link VersionStatus#DEPRECATED}.
     *
     * @param deprecationTime      when the deprecation takes effect (now if null)
     * @param plannedRetirementTime when the version will be retired (may be null)
     * @param grayPercent           percentage of callers that see deprecation warnings (0-100)
     * @throws VersionNotFoundException         if the version is not registered
     * @throws InvalidVersionTransitionException if the version is already retired
     */
    public VersionState deprecate(String version, Instant deprecationTime, Instant plannedRetirementTime,
                                   int grayPercent, String operator) {
        if (grayPercent < 0 || grayPercent > 100) {
            throw new IllegalArgumentException("grayPercent must be between 0 and 100");
        }
        Instant effectiveDeprecationTime = deprecationTime != null ? deprecationTime : Instant.now();
        return versions.compute(version, (key, current) -> {
            if (current == null) {
                throw new VersionNotFoundException(version);
            }
            if (current.status() == VersionStatus.RETIRED) {
                throw new InvalidVersionTransitionException("Cannot deprecate a retired version: " + version);
            }
            return new VersionState(version, VersionStatus.DEPRECATED, effectiveDeprecationTime,
                    plannedRetirementTime, grayPercent, Instant.now(), operator);
        });
    }

    /**
     * Rolls a deprecated version back to {@link VersionStatus#AVAILABLE}.
     *
     * @throws VersionNotFoundException         if the version is not registered
     * @throws InvalidVersionTransitionException if the version is retired
     */
    public VersionState rollback(String version, String operator) {
        return versions.compute(version, (key, current) -> {
            if (current == null) {
                throw new VersionNotFoundException(version);
            }
            if (current.status() == VersionStatus.RETIRED) {
                throw new InvalidVersionTransitionException("Cannot roll back a retired version: " + version);
            }
            return new VersionState(version, VersionStatus.AVAILABLE, null, null, 100,
                    Instant.now(), operator);
        });
    }

    /**
     * Retires a version immediately. Idempotent for already-retired versions.
     *
     * @throws VersionNotFoundException if the version is not registered
     */
    public VersionState retire(String version, String operator) {
        return versions.compute(version, (key, current) -> {
            if (current == null) {
                throw new VersionNotFoundException(version);
            }
            if (current.status() == VersionStatus.RETIRED) {
                return current;
            }
            return new VersionState(version, VersionStatus.RETIRED, current.deprecationTime(),
                    current.plannedRetirementTime(), current.grayPercent(), Instant.now(), operator);
        });
    }

    /**
     * Atomically transitions a deprecated version to {@link VersionStatus#RETIRED}
     * if its planned retirement time has passed.
     */
    public Optional<VersionState> markRetiredIfDue(String version) {
        return Optional.ofNullable(versions.computeIfPresent(version, (key, current) -> {
            if (current.status() == VersionStatus.DEPRECATED
                    && current.plannedRetirementTime() != null
                    && !current.plannedRetirementTime().isAfter(Instant.now())) {
                return new VersionState(version, VersionStatus.RETIRED, current.deprecationTime(),
                        current.plannedRetirementTime(), current.grayPercent(), Instant.now(),
                        "system:scheduled-retirement");
            }
            return current;
        }));
    }

    /**
     * Periodically retires all deprecated versions whose planned retirement time has passed.
     */
    @Scheduled(fixedDelayString = "${api.version.retirement-check-interval-ms:1000}")
    public void enforcePlannedRetirements() {
        for (VersionState state : versions.values()) {
            if (state.status() == VersionStatus.DEPRECATED
                    && state.plannedRetirementTime() != null
                    && !state.plannedRetirementTime().isAfter(Instant.now())) {
                markRetiredIfDue(state.version());
            }
        }
    }
}
