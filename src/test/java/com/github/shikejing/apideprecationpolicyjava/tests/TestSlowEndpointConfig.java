package com.github.shikejing.apideprecationpolicyjava.tests;

import com.github.shikejing.apideprecationpolicyjava.version.VersionContext;
import com.github.shikejing.apideprecationpolicyjava.version.VersionState;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test-only blocking endpoint used to verify that in-flight requests keep the
 * version state snapshot captured when they started.
 */
@TestConfiguration
public class TestSlowEndpointConfig {

    static CountDownLatch IN_FLIGHT = new CountDownLatch(1);
    static CountDownLatch RELEASE = new CountDownLatch(1);

    @RestController
    static class SlowController {

        @GetMapping("/api/slow-greeting")
        public Map<String, Object> slow() throws InterruptedException {
            VersionState state = VersionContext.current();
            IN_FLIGHT.countDown();
            if (!RELEASE.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out waiting for release");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("version", state.version());
            body.put("status", state.status().name());
            return body;
        }
    }
}
