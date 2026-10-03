package com.github.shikejing.apideprecationpolicyjava.versioning;

/**
 * 请求级版本上下文：在一次请求内持有协商结果与状态快照，
 * 保证进行中的请求不会因为并发状态切换出现新旧混合的响应。
 */
public final class VersionContext {

    private static final ThreadLocal<VersionInfo> CURRENT = new ThreadLocal<>();

    private VersionContext() {
    }

    public static void set(VersionInfo info) {
        CURRENT.set(info);
    }

    public static VersionInfo current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
