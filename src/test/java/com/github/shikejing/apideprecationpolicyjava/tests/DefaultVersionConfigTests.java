package com.github.shikejing.apideprecationpolicyjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the default version is configurable: when no version is expressed,
 * the configured default is used deterministically.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "api.version.default-version=v2")
class DefaultVersionConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredDefaultVersionIsUsedWhenCallerSilent() throws Exception {
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v2"))
                .andExpect(header().string("X-API-Version", "v2"));
    }

    @Test
    void explicitHeaderOverridesConfiguredDefault() throws Exception {
        mockMvc.perform(get("/api/greeting").header("X-API-Version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"));
    }
}
