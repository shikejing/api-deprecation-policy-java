package com.github.shikejing.apideprecationpolicyjava.versioning;

/**
 * 版本生命周期状态。
 */
public enum VersionState {
    /** 可用 */
    AVAILABLE,
    /** 已弃用（过渡窗口内仍可用，但响应需携带弃用与计划下线时间） */
    DEPRECATED,
    /** 已下线（调用必须被拒绝） */
    SUNSET
}
