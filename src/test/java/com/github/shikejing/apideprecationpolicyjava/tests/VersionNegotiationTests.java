package com.github.shikejing.apideprecationpolicyjava.tests;

import com.github.shikejing.apideprecationpolicyjava.versioning.VersionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 版本协商：请求头、路径、默认版本。
 */
@SpringBootTest
@AutoConfigureMockMvc
class VersionNegotiationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    VersionRegistry registry;

    @BeforeEach
    void setUp() {
        registry.register("v1");
        registry.register("v2");
    }

    @Test
    void headerTakesPrecedence() throws Exception {
        mvc.perform(get("/api/echo").header("X-Api-Version", "v2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Api-Version-Served", "v2"))
                .andExpect(jsonPath("$.version").value("v2"));
    }

    @Test
    void pathVersionIsHonored() throws Exception {
        mvc.perform(get("/api/v2/echo"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Api-Version-Served", "v2"))
                .andExpect(jsonPath("$.message").value("hello from v2"));
    }

    @Test
    void fallsBackToConfiguredDefaultVersion() throws Exception {
        mvc.perform(get("/api/echo"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Api-Version-Served", "v1"))
                .andExpect(jsonPath("$.version").value("v1"));
    }

    @Test
    void unknownVersionIsRejectedWithDistinctCode() throws Exception {
        mvc.perform(get("/api/echo").header("X-Api-Version", "v99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERSION_UNKNOWN"));
    }
}
