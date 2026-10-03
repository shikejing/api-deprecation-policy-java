package com.github.shikejing.apideprecationpolicyjava.audit;

import java.time.Instant;

/**
 * 一条审计记录：谁在什么时间对哪个版本做了什么操作，结果如何。
 */
public final class AuditEvent {

    private final Instant timestamp;
    private final String operator;
    private final String action;
    private final String version;
    private final boolean success;
    private final String detail;

    public AuditEvent(Instant timestamp, String operator, String action,
                      String version, boolean success, String detail) {
        this.timestamp = timestamp;
        this.operator = operator;
        this.action = action;
        this.version = version;
        this.success = success;
        this.detail = detail;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getOperator() { return operator; }
    public String getAction() { return action; }
    public String getVersion() { return version; }
    public boolean isSuccess() { return success; }
    public String getDetail() { return detail; }
}
