package com.github.shikejing.apideprecationpolicyjava.tests;

import com.github.shikejing.apideprecationpolicyjava.versioning.VersionInfo;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionRegistry;
import com.github.shikejing.apideprecationpolicyjava.versioning.VersionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 并发状态切换：切换对新请求立即生效，进行中的请求不出现新旧混合的响应；
 * 回退后任意一次查询得到的状态一致。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentStateSwitchTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    VersionRegistry registry;

    @Test
    void concurrentRequestsNeverSeeMixedResponses() throws Exception {
        registry.register("v21");
        Instant sunsetAt = Instant.now().plus(30, ChronoUnit.DAYS);

        int threads = 8;
        int rounds = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                List<String> violations = new ArrayList<>();
                start.await();
                for (int i = 0; i < rounds; i++) {
                    MockHttpServletResponse resp = mvc.perform(
                                    get("/api/echo").header("X-Api-Version", "v21"))
                            .andReturn().getResponse();
                    int status = resp.getStatus();
                    boolean hasDeprecation = resp.getHeader("Deprecation") != null;
                    boolean hasSunset = resp.getHeader("Sunset") != null;
                    boolean bodyIsError = resp.getContentAsString().contains("\"code\"");
                    // 单个响应必须完整地属于某一种状态，不允许新旧混合
                    boolean consistentAvailable = status == 200 && !hasDeprecation && !hasSunset && !bodyIsError;
                    boolean consistentDeprecated = status == 200 && hasDeprecation && hasSunset && !bodyIsError;
                    boolean consistentSunset = status == 410 && !hasDeprecation && bodyIsError
                            && resp.getContentAsString().contains("VERSION_SUNSET");
                    if (!consistentAvailable && !consistentDeprecated && !consistentSunset) {
                        violations.add("mixed response: status=" + status
                                + ", deprecation=" + hasDeprecation
                                + ", sunset=" + hasSunset
                                + ", body=" + resp.getContentAsString());
                    }
                }
                return violations;
            }));
        }

        // 主线程在请求风暴期间并发切换状态：弃用 -> 下线
        start.countDown();
        registry.deprecate("v21", sunsetAt, 100);
        Thread.sleep(5);
        registry.sunset("v21");

        List<String> allViolations = new ArrayList<>();
        for (Future<List<String>> f : futures) {
            allViolations.addAll(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        assertThat(allViolations).isEmpty();

        // 切换对新请求立即生效
        mvc.perform(get("/api/echo").header("X-Api-Version", "v21"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(410));
    }

    @Test
    void rollbackYieldsConsistentStateAcrossQueries() throws Exception {
        registry.register("v22");
        registry.deprecate("v22", Instant.now().plus(30, ChronoUnit.DAYS), 40);
        registry.rollback("v22");

        // 回退后任意一次查询（注册表直查 + HTTP 查询）都必须一致为 AVAILABLE
        for (int i = 0; i < 20; i++) {
            VersionInfo info = registry.find("v22").orElseThrow();
            assertThat(info.getState()).isEqualTo(VersionState.AVAILABLE);
            mvc.perform(get("/admin/versions/v22"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                            .contains("\"state\":\"AVAILABLE\""));
            mvc.perform(get("/api/echo").header("X-Api-Version", "v22"))
                    .andExpect(result -> {
                        assertThat(result.getResponse().getStatus()).isEqualTo(200);
                        assertThat(result.getResponse().getHeader("Deprecation")).isNull();
                    });
        }
    }

    @Test
    void grayRolloutIsDeterministicPerCaller() throws Exception {
        registry.register("v23");
        registry.deprecate("v23", Instant.now().plus(30, ChronoUnit.DAYS), 50);

        // 同一调用方在过渡窗口内多次请求，观察到的弃用语义必须稳定
        for (String caller : List.of("caller-a", "caller-b", "caller-c")) {
            Boolean first = null;
            for (int i = 0; i < 10; i++) {
                boolean deprecated = mvc.perform(get("/api/echo")
                                .header("X-Api-Version", "v23")
                                .header("X-Caller-Id", caller))
                        .andReturn().getResponse().getHeader("Deprecation") != null;
                if (first == null) {
                    first = deprecated;
                } else {
                    assertThat(deprecated).as("caller %s sees stable rollout", caller).isEqualTo(first);
                }
            }
        }
    }
}
