package com.github.shikejing.apideprecationpolicyjava.version;

/**
 * Holds the resolved version state snapshot for the duration of a request.
 */
public final class VersionContext {
    private static final ThreadLocal<VersionState> CURRENT = new ThreadLocal<>();

    private VersionContext() {
    }

    public static void set(VersionState state) {
        CURRENT.set(state);
    }

    public static VersionState current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
