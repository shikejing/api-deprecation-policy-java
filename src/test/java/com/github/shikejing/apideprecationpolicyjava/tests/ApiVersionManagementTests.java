package com.github.shikejing.apideprecationpolicyjava.tests;

import com.github.shikejing.apideprecationpolicyjava.admin.AuditRecord;
import com.github.shikejing.apideprecationpolicyjava.admin.AuditService;
import com.github.shikejing.apideprecationpolicyjava.version.InvalidVersionTransitionException;
import com.github.shikejing.apideprecationpolicyjava.version.VersionRegistry;
import com.github.shikejing.apideprecationpolicyjava.version.VersionState;
import com.github.shikejing.apideprecationpolicyjava.version.VersionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for version negotiation, deprecation headers, retirement
 * enforcement, concurrent state switches, gray rollout/rollback, permission
 * checks and audit trail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSlowEndpointConfig.class)
class ApiVersionManagementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VersionRegistry registry;

    @Autowired
    private AuditService auditService;

    @AfterEach
    void resetSeededVersions() {
        rollbackIfDeprecated("v1");
        rollbackIfDeprecated("v2");
    }

    private void rollbackIfDeprecated(String version) {
        VersionState state = registry.find(version).orElse(null);
        if (state != null && state.status() == VersionStatus.DEPRECATED) {
            registry.rollback(version, "test-cleanup");
        }
    }

    private void registerIfAbsent(String version) {
        try {
            registry.register(version);
        } catch (InvalidVersionTransitionException alreadyRegistered) {
            // ignore
        }
    }

    private String futureIso(int daysOffset) {
        return Instant.now().plus(daysOffset, ChronoUnit.DAYS).toString();
    }

    // ---------- version negotiation ----------

    @Test
    void defaultVersionIsUsedWhenCallerDoesNotExpressOne() throws Exception {
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(header().string("X-API-Version", "v1"));
    }

    @Test
    void versionCanBeNegotiatedViaHeader() throws Exception {
        mockMvc.perform(get("/api/greeting").header("X-API-Version", "v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2"))
                .andExpect(header().string("X-API-Version", "v2"));
    }

    @Test
    void versionCanBeNegotiatedViaPath() throws Exception {
        mockMvc.perform(get("/api/v2/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2"))
                .andExpect(header().string("X-API-Version", "v2"));
    }

    @Test
    void pathTakesPrecedenceOverHeader() throws Exception {
        mockMvc.perform(get("/api/v2/greeting").header("X-API-Version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2"));
    }

    @Test
    void unknownVersionIsRejectedWithNotFound() throws Exception {
        mockMvc.perform(get("/api/v9/greeting"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VERSION_NOT_FOUND"))
                .andExpect(jsonPath("$.version").value("v9"));
    }

    @Test
    void malformedHeaderVersionIsRejectedWithNotFound() throws Exception {
        mockMvc.perform(get("/api/greeting").header("X-API-Version", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VERSION_NOT_FOUND"))
                .andExpect(jsonPath("$.version").value("nope"));
    }

    // ---------- deprecation headers ----------

    @Test
    void deprecatedVersionCarriesMachineParseableDeprecationHeaders() throws Exception {
        mockMvc.perform(post("/admin/versions/v1/deprecate")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedRetirementTime\":\"" + futureIso(30) + "\",\"grayPercent\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));

        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Deprecation"))
                .andExpect(header().string("Deprecation", org.hamcrest.Matchers.startsWith("@")))
                .andExpect(header().exists("Sunset"))
                .andExpect(header().string("X-API-Version", "v1"))
                .andExpect(jsonPath("$.status").value("DEPRECATED"));
    }

    @Test
    void versionStateCanBeQueried() throws Exception {
        mockMvc.perform(get("/admin/versions/v1").with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ---------- retirement enforcement ----------

    @Test
    void retiredVersionIsRejectedWithReasonDistinctFromNotFound() throws Exception {
        registerIfAbsent("v-retire");
        mockMvc.perform(post("/admin/versions/v-retire/retire")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));

        mockMvc.perform(get("/api/v-retire/greeting"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("VERSION_RETIRED"))
                .andExpect(jsonPath("$.version").value("v-retire"));

        // distinct reason for a version that never existed
        mockMvc.perform(get("/api/v-never/greeting"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VERSION_NOT_FOUND"));
    }

    @Test
    void plannedRetirementIsEnforcedAutomaticallyAfterSunset() throws Exception {
        registerIfAbsent("v-sunset");
        String past = Instant.now().minus(1, ChronoUnit.MINUTES).toString();
        mockMvc.perform(post("/admin/versions/v-sunset/deprecate")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedRetirementTime\":\"" + past + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v-sunset/greeting"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("VERSION_RETIRED"))
                .andExpect(jsonPath("$.version").value("v-sunset"));
    }

    @Test
    void invalidTransitionFromRetiredIsRejected() throws Exception {
        registerIfAbsent("v-invalid");
        mockMvc.perform(post("/admin/versions/v-invalid/retire")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/versions/v-invalid/rollback")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERSION_INVALID_TRANSITION"));

        mockMvc.perform(post("/admin/versions/v-invalid/deprecate")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedRetirementTime\":\"" + futureIso(10) + "\"}"))
                .andExpect(status().isConflict());
    }

    // ---------- in-flight request consistency ----------

    @Test
    void stateSwitchDoesNotAffectInFlightRequests() throws Exception {
        TestSlowEndpointConfig.IN_FLIGHT = new java.util.concurrent.CountDownLatch(1);
        TestSlowEndpointConfig.RELEASE = new java.util.concurrent.CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MvcResult> inFlight = executor.submit(() -> mockMvc.perform(
                get("/api/slow-greeting").header("X-API-Version", "v1"))
                .andExpect(status().isOk())
                .andReturn());

        // wait until the request is in-flight (past version negotiation)
        assertThat(TestSlowEndpointConfig.IN_FLIGHT.await(10, TimeUnit.SECONDS)).isTrue();

        // switch state while the request is in-flight
        registry.deprecate("v1", Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS),
                100, "test-inflight");

        // new requests see the deprecation
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Deprecation"));

        // release the in-flight request; it must keep the AVAILABLE snapshot from entry
        TestSlowEndpointConfig.RELEASE.countDown();
        MvcResult result = inFlight.get(10, TimeUnit.SECONDS);
        assertThat(result.getResponse().getHeader("X-API-Version")).isEqualTo("v1");
        assertThat(result.getResponse().getHeader("Deprecation")).isNull();
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"AVAILABLE\"");
        executor.shutdownNow();
    }

    // ---------- concurrent state changes ----------

    @Test
    void concurrentStateTransitionsNeverProduceMixedStates() throws Exception {
        registerIfAbsent("v-concurrent");
        int threads = 16;
        int iterations = 40;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit((Callable<Void>) () -> {
                for (int j = 0; j < iterations; j++) {
                    int op = ThreadLocalRandom.current().nextInt(4);
                    try {
                        switch (op) {
                            case 0 -> registry.deprecate("v-concurrent", Instant.now(),
                                    Instant.now().plus(30, ChronoUnit.DAYS), 100, "stress");
                            case 1 -> registry.rollback("v-concurrent", "stress");
                            case 2 -> registry.retire("v-concurrent", "stress");
                            default -> {
                                // read-only: validate snapshot coherence
                            }
                        }
                    } catch (InvalidVersionTransitionException expected) {
                        // invalid transitions (e.g. rollback after retire) are rejected
                    }
                    VersionState snapshot = registry.require("v-concurrent");
                    assertSnapshotCoherent(snapshot);
                }
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertSnapshotCoherent(registry.require("v-concurrent"));
    }

    private void assertSnapshotCoherent(VersionState state) {
        assertThat(state.status()).isIn(VersionStatus.values());
        if (state.status() == VersionStatus.DEPRECATED) {
            assertThat(state.deprecationTime()).isNotNull();
        }
        if (state.status() == VersionStatus.AVAILABLE) {
            assertThat(state.deprecationTime()).isNull();
            assertThat(state.plannedRetirementTime()).isNull();
        }
    }

    // ---------- gray rollout and rollback ----------

    @Test
    void grayDeprecationSplitsTrafficAndRollbackMakesAllQueriesConsistent() throws Exception {
        registerIfAbsent("v-gray");
        mockMvc.perform(post("/admin/versions/v-gray/deprecate")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedRetirementTime\":\"" + futureIso(30) + "\",\"grayPercent\":50}"))
                .andExpect(status().isOk());

        Set<String> warned = new HashSet<>();
        Set<String> notWarned = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            String caller = "caller-" + i;
            MvcResult result = mockMvc.perform(get("/api/greeting")
                            .header("X-API-Version", "v-gray")
                            .header("X-Caller-Id", caller))
                    .andExpect(status().isOk())
                    .andReturn();
            if (result.getResponse().getHeader("Deprecation") != null) {
                warned.add(caller);
            } else {
                notWarned.add(caller);
            }
        }
        assertThat(warned).isNotEmpty();
        assertThat(notWarned).isNotEmpty();

        // gray bucketing is deterministic per caller
        String stableCaller = warned.iterator().next();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/greeting")
                            .header("X-API-Version", "v-gray")
                            .header("X-Caller-Id", stableCaller))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Deprecation"));
        }

        // rollback: every caller sees AVAILABLE, no contradictory states
        mockMvc.perform(post("/admin/versions/v-gray/rollback")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/greeting")
                            .header("X-API-Version", "v-gray")
                            .header("X-Caller-Id", "caller-" + i))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Deprecation"))
                    .andExpect(header().doesNotExist("Sunset"));
        }
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/admin/versions/v-gray").with(httpBasic("admin", "admin-pass")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AVAILABLE"));
        }
    }

    // ---------- permission checks ----------

    @Test
    void managementOperationsRequireAdminRoleAndDenialsAreAudited() throws Exception {
        // unauthenticated -> 401
        mockMvc.perform(post("/admin/versions/v1/rollback"))
                .andExpect(status().isUnauthorized());

        // authenticated USER -> 403
        mockMvc.perform(post("/admin/versions/v1/rollback")
                        .with(httpBasic("user", "user-pass")))
                .andExpect(status().isForbidden());

        // ADMIN -> 200
        mockMvc.perform(post("/admin/versions/v1/rollback")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk());

        List<AuditRecord> records = auditService.list();
        assertThat(records).anyMatch(r -> !r.allowed() && "AUTHENTICATE".equals(r.action()));
        assertThat(records).anyMatch(r -> !r.allowed() && "AUTHORIZE".equals(r.action())
                && "user".equals(r.actor()));
    }

    // ---------- audit trail ----------

    @Test
    void auditTrailRecordsEveryManagementOperation() throws Exception {
        registerIfAbsent("v-audit");
        mockMvc.perform(post("/admin/versions/v-audit/deprecate")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedRetirementTime\":\"" + futureIso(30) + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/versions/v-audit/rollback")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/versions/v-audit/retire")
                        .with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk());

        List<AuditRecord> records = auditService.list();
        assertThat(records).extracting(AuditRecord::action)
                .contains("DEPRECATE", "ROLLBACK", "RETIRE");
        assertThat(records).allMatch(AuditRecord::allowed);
        assertThat(records).allMatch(r -> "admin".equals(r.actor()));
        assertThat(records).allMatch(r -> r.timestamp() != null);
    }

    @Test
    void versionCanBeRegisteredViaAdminApi() throws Exception {
        mockMvc.perform(post("/admin/versions")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v-registered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v-registered"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mockMvc.perform(get("/api/v-registered/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v-registered"));
    }

    @Test
    void auditEndpointExposesRecordedOperations() throws Exception {
        mockMvc.perform(get("/admin/audit").with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void grayZeroPercentSuppressesWarningsForAllCallers() throws Exception {
        registerIfAbsent("v-gray0");
        mockMvc.perform(post("/admin/versions/v-gray0/deprecate")
                        .with(httpBasic("admin", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedRetirementTime\":\"" + futureIso(30) + "\",\"grayPercent\":0}"))
                .andExpect(status().isOk());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/greeting")
                            .header("X-API-Version", "v-gray0")
                            .header("X-Caller-Id", "caller-" + i))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Deprecation"))
                    .andExpect(header().doesNotExist("Sunset"));
        }

        // authoritative state is still DEPRECATED
        mockMvc.perform(get("/admin/versions/v-gray0").with(httpBasic("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));
    }
}
