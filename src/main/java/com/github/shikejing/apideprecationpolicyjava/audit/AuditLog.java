package com.github.shikejing.apideprecationpolicyjava.audit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 审计日志：线程安全地追加与查询审计记录。
 * 所有版本变更、下线操作以及越权尝试都会落一条记录，可追溯。
 */
public class AuditLog {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    public void record(AuditEvent event) {
        events.add(event);
    }

    /** 返回当前全部审计记录的不可变副本。 */
    public List<AuditEvent> findAll() {
        return List.copyOf(events);
    }
}
