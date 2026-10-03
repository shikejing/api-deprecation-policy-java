package com.github.shikejing.apideprecationpolicyjava.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Query endpoint for the audit trail.
 */
@RestController
@RequestMapping("/admin/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditRecord> list() {
        return auditService.list();
    }
}
