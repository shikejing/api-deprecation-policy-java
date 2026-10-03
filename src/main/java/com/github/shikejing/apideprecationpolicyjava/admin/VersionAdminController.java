package com.github.shikejing.apideprecationpolicyjava.admin;

import com.github.shikejing.apideprecationpolicyjava.version.VersionRegistry;
import com.github.shikejing.apideprecationpolicyjava.version.VersionState;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Management endpoints for registering versions and driving lifecycle transitions.
 * Every operation is audited via {@link AuditService}.
 */
@RestController
@RequestMapping("/admin/versions")
public class VersionAdminController {

    private final VersionRegistry registry;
    private final AuditService auditService;

    public VersionAdminController(VersionRegistry registry, AuditService auditService) {
        this.registry = registry;
        this.auditService = auditService;
    }

    public record RegisterRequest(String version) {
    }

    public record DeprecateRequest(Instant deprecationTime, Instant plannedRetirementTime, Integer grayPercent) {
    }

    /** Registers a new version in AVAILABLE state. */
    @PostMapping
    public VersionState register(@RequestBody RegisterRequest request) {
        String actor = currentActor();
        VersionState state = registry.register(request.version());
        auditService.record(actor, "REGISTER", state.version(), true,
                "version registered in state " + state.status());
        return state;
    }

    /** Lists all registered versions with their current state. */
    @GetMapping
    public List<VersionState> list() {
        return List.copyOf(registry.list());
    }

    /** Queries the current state of a single version. */
    @GetMapping("/{version}")
    public VersionState get(@PathVariable String version) {
        return registry.require(version);
    }

    /**
     * Deprecates a version. Optionally accepts an explicit deprecation time,
     * a planned retirement time and a gray percentage (0-100) for partial rollout.
     */
    @PostMapping("/{version}/deprecate")
    public VersionState deprecate(@PathVariable String version,
                                  @RequestBody(required = false) DeprecateRequest request) {
        String actor = currentActor();
        Instant deprecationTime = request != null && request.deprecationTime() != null
                ? request.deprecationTime() : Instant.now();
        Instant plannedRetirementTime = request != null ? request.plannedRetirementTime() : null;
        int grayPercent = request != null && request.grayPercent() != null ? request.grayPercent() : 100;
        VersionState state = registry.deprecate(version, deprecationTime, plannedRetirementTime,
                grayPercent, actor);
        auditService.record(actor, "DEPRECATE", version, true,
                "deprecationTime=" + deprecationTime + ", plannedRetirementTime=" + plannedRetirementTime
                        + ", grayPercent=" + grayPercent);
        return state;
    }

    /** Rolls a deprecated version back to AVAILABLE. */
    @PostMapping("/{version}/rollback")
    public VersionState rollback(@PathVariable String version) {
        String actor = currentActor();
        VersionState state = registry.rollback(version, actor);
        auditService.record(actor, "ROLLBACK", version, true,
                "version rolled back to state " + state.status());
        return state;
    }

    /** Retires a version immediately. */
    @PostMapping("/{version}/retire")
    public VersionState retire(@PathVariable String version) {
        String actor = currentActor();
        VersionState state = registry.retire(version, actor);
        auditService.record(actor, "RETIRE", version, true,
                "version retired by operator");
        return state;
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }
}
