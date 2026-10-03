package com.github.shikejing.apideprecationpolicyjava.admin;

import com.github.shikejing.apideprecationpolicyjava.audit.AuditEvent;
import com.github.shikejing.apideprecationpolicyjava.audit.AuditLog;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionInfo;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionRegistry;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersioningProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 版本生命周期管理端点：登记、弃用（含灰度）、下线、回退、查询与审计。
 *
 * 权限模型：所有变更类操作必须携带有效的 X-Admin-Token；
 * 越权请求返回 403 并写入审计记录（success=false），可追溯。
 * 状态查询对所有调用方开放。
 */
@RestController
@RequestMapping("/admin")
public class VersionAdminController {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final VersionRegistry registry;
    private final AuditLog auditLog;
    private final VersioningProperties properties;
    private final Clock clock = Clock.systemUTC();

    public VersionAdminController(VersionRegistry registry, AuditLog auditLog,
                                  VersioningProperties properties) {
        this.registry = registry;
        this.auditLog = auditLog;
        this.properties = properties;
    }

    /** 查询全部版本状态（开放）。 */
    @GetMapping("/versions")
    public List<VersionInfo> list() {
        return registry.findAll();
    }

    /** 查询单个版本状态（开放）。 */
    @GetMapping("/versions/{version}")
    public ResponseEntity<?> get(@PathVariable String version) {
        return registry.find(version)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("code", "VERSION_UNKNOWN",
                                "message", "API version '" + version + "' does not exist")));
    }

    /** 登记新版本。 */
    @PostMapping("/versions/{version}/register")
    public ResponseEntity<?> register(@PathVariable String version, HttpServletRequest request) {
        ResponseEntity<?> denied = checkPermission(request, "REGISTER", version);
        if (denied != null) {
            return denied;
        }
        VersionInfo info = registry.register(version);
        audit("REGISTER", version, true, "state=" + info.getState(), request);
        return ResponseEntity.ok(info);
    }

    /** 弃用版本：必须给出计划下线时间，可指定灰度比例。 */
    @PostMapping("/versions/{version}/deprecate")
    public ResponseEntity<?> deprecate(@PathVariable String version,
                                       @RequestParam String sunsetAt,
                                       @RequestParam(defaultValue = "100") int rolloutPercent,
                                       HttpServletRequest request) {
        ResponseEntity<?> denied = checkPermission(request, "DEPRECATE", version);
        if (denied != null) {
            return denied;
        }
        try {
            VersionInfo info = registry.deprecate(version, Instant.parse(sunsetAt), rolloutPercent);
            audit("DEPRECATE", version, true,
                    "sunsetAt=" + sunsetAt + ",rollout=" + rolloutPercent, request);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException | IllegalStateException | java.time.format.DateTimeParseException e) {
            audit("DEPRECATE", version, false, e.getMessage(), request);
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", String.valueOf(e.getMessage())));
        }
    }

    /** 立即下线版本。 */
    @PostMapping("/versions/{version}/sunset")
    public ResponseEntity<?> sunset(@PathVariable String version, HttpServletRequest request) {
        ResponseEntity<?> denied = checkPermission(request, "SUNSET", version);
        if (denied != null) {
            return denied;
        }
        try {
            VersionInfo info = registry.sunset(version);
            audit("SUNSET", version, true, "state=SUNSET", request);
            return ResponseEntity.ok(info);
        } catch (IllegalStateException e) {
            audit("SUNSET", version, false, e.getMessage(), request);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "VERSION_UNKNOWN", "message", String.valueOf(e.getMessage())));
        }
    }

    /** 回退：过渡窗口内撤销弃用，恢复为可用。 */
    @PostMapping("/versions/{version}/rollback")
    public ResponseEntity<?> rollback(@PathVariable String version, HttpServletRequest request) {
        ResponseEntity<?> denied = checkPermission(request, "ROLLBACK", version);
        if (denied != null) {
            return denied;
        }
        try {
            VersionInfo info = registry.rollback(version);
            audit("ROLLBACK", version, true, "state=AVAILABLE", request);
            return ResponseEntity.ok(info);
        } catch (IllegalStateException e) {
            audit("ROLLBACK", version, false, e.getMessage(), request);
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REQUEST", "message", String.valueOf(e.getMessage())));
        }
    }

    /** 查询审计记录（需权限）。 */
    @GetMapping("/audit")
    public ResponseEntity<?> audit(HttpServletRequest request) {
        ResponseEntity<?> denied = checkPermission(request, "AUDIT_QUERY", "*");
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(auditLog.findAll());
    }

    /** 权限校验：越权返回 403 并记录审计。 */
    private ResponseEntity<?> checkPermission(HttpServletRequest request, String action, String version) {
        String token = request.getHeader(ADMIN_TOKEN_HEADER);
        if (token != null && !token.isBlank() && token.equals(properties.getAdminToken())) {
            return null;
        }
        audit(action, version, false, "permission denied", request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "FORBIDDEN", "message", "valid admin token required"));
    }

    private void audit(String action, String version, boolean success, String detail,
                       HttpServletRequest request) {
        String operator = request.getHeader(ADMIN_TOKEN_HEADER) != null ? "admin" : "anonymous";
        auditLog.record(new AuditEvent(Instant.now(clock), operator, action, version, success, detail));
    }
}
