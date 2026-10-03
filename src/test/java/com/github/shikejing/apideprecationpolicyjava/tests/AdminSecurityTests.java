package com.github.shikejing.apideprecationpolicyjava.tests;

import com.github.shikejing.apideprecationpolicyjava.audit.AuditEvent;
import com.github.shikejing.apideprecationpolicyjava.audit.AuditLog;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionRegistry;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 权限校验与审计：越权请求被拒绝且可追溯，合法变更完整留痕。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityTests {

    private static final String TOKEN = "test-admin-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    VersionRegistry registry;

    @Autowired
    AuditLog auditLog;

    @Test
    void changeWithoutTokenIsRejectedAndAudited() throws Exception {
        int before = auditLog.findAll().size();

        mvc.perform(post("/admin/versions/v31/deprecate")
                        .param("sunsetAt", Instant.now().plus(30, ChronoUnit.DAYS).toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 越权不影响版本状态
        assertThat(registry.find("v31")).isEmpty();

        // 越权可追溯：审计中留下 success=false 的记录
        List<AuditEvent> events = auditLog.findAll();
        assertThat(events.size()).isGreaterThan(before);
        AuditEvent last = events.get(events.size() - 1);
        assertThat(last.getAction()).isEqualTo("DEPRECATE");
        assertThat(last.getVersion()).isEqualTo("v31");
        assertThat(last.isSuccess()).isFalse();
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        mvc.perform(post("/admin/versions/v32/sunset")
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedLifecycleIsAppliedAndAudited() throws Exception {
        String sunsetAt = Instant.now().plus(30, ChronoUnit.DAYS).toString();

        mvc.perform(post("/admin/versions/v33/register").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"));

        mvc.perform(post("/admin/versions/v33/deprecate")
                        .header("X-Admin-Token", TOKEN)
                        .param("sunsetAt", sunsetAt)
                        .param("rolloutPercent", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DEPRECATED"))
                .andExpect(jsonPath("$.rolloutPercent").value(80));

        mvc.perform(post("/admin/versions/v33/rollback").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"));

        mvc.perform(post("/admin/versions/v33/deprecate")
                        .header("X-Admin-Token", TOKEN)
                        .param("sunsetAt", sunsetAt))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/versions/v33/sunset").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUNSET"));

        assertThat(registry.find("v33").orElseThrow().getState()).isEqualTo(VersionState.SUNSET);

        // 完整审计：每个变更操作都有成功记录
        List<AuditEvent> events = auditLog.findAll().stream()
                .filter(e -> "v33".equals(e.getVersion()))
                .toList();
        assertThat(events).extracting(AuditEvent::getAction)
                .contains("REGISTER", "DEPRECATE", "ROLLBACK", "SUNSET");
        assertThat(events).allMatch(AuditEvent::isSuccess);
    }

    @Test
    void auditQueryRequiresToken() throws Exception {
        mvc.perform(get("/admin/audit"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void versionStateIsQueryable() throws Exception {
        registry.register("v34");
        mvc.perform(get("/admin/versions/v34"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"));
    }
}
