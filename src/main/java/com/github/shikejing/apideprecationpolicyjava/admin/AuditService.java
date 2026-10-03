package com.github.shikejing.apideprecationpolicyjava.admin;

import com.github.shikejing.apideprecationpolicyjava.config.VersionProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only audit log for version management operations.
 *
 * <p>Both allowed operations and denied attempts (unauthenticated or unauthorized)
 * are recorded, so every management action is traceable.
 */
@Service
public class AuditService {

    private final VersionProperties properties;
    private final List<AuditRecord> records = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    public AuditService(VersionProperties properties) {
        this.properties = properties;
    }

    /**
     * Records a management operation or a denied attempt.
     */
    public void record(String actor, String action, String version, boolean allowed, String detail) {
        long id = sequence.incrementAndGet();
        AuditRecord record = new AuditRecord(id, Instant.now(), actor, action, version, allowed, detail);
        if (records.size() >= properties.getAuditMaxEntries()) {
            records.remove(0);
        }
        records.add(record);
    }

    public List<AuditRecord> list() {
        return List.copyOf(records);
    }
}
