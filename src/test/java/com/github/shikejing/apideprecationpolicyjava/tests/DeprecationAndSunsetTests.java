package com.github.shikejing.apideprecationpolicyjava.tests;

import com.github.shikejing.apideprecationpolicyjava.versioning.VersionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 弃用提示（机器可解析的 Deprecation / Sunset 响应头）与下线拦截。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeprecationAndSunsetTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    VersionRegistry registry;

    @Test
    void deprecatedVersionCarriesMachineReadableHeaders() throws Exception {
        Instant sunsetAt = Instant.now().plus(30, ChronoUnit.DAYS);
        registry.register("v11");
        registry.deprecate("v11", sunsetAt, 100);

        mvc.perform(get("/api/echo").header("X-Api-Version", "v11"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", startsWith("@")))
                .andExpect(header().exists("Sunset"));
    }

    @Test
    void availableVersionHasNoDeprecationHeaders() throws Exception {
        registry.register("v12");

        mvc.perform(get("/api/echo").header("X-Api-Version", "v12"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Sunset"));
    }

    @Test
    void sunsetVersionIsRejectedWithDistinctCode() throws Exception {
        registry.register("v13");
        registry.sunset("v13");

        mvc.perform(get("/api/echo").header("X-Api-Version", "v13"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("VERSION_SUNSET"));
    }

    @Test
    void sunsetRejectionDiffersFromUnknownVersion() throws Exception {
        registry.register("v14");
        registry.sunset("v14");

        // 已下线：410 + VERSION_SUNSET
        mvc.perform(get("/api/echo").header("X-Api-Version", "v14"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("VERSION_SUNSET"));
        // 不存在：404 + VERSION_UNKNOWN
        mvc.perform(get("/api/echo").header("X-Api-Version", "v14-never-existed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERSION_UNKNOWN"));
    }

    @Test
    void callsAfterPlannedSunsetTimeAreRejectedEvenIfStateNotFlipped() throws Exception {
        // 计划下线时间已过，即使状态仍登记为 DEPRECATED，也必须强制拦截
        registry.register("v15");
        registry.deprecate("v15", Instant.now().minus(1, ChronoUnit.SECONDS), 100);

        mvc.perform(get("/api/echo").header("X-Api-Version", "v15"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("VERSION_SUNSET"));
    }
}
