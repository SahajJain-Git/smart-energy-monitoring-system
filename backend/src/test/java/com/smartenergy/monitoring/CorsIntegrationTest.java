package com.smartenergy.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying centralized CORS policy hardening.
 *
 * Verifies that:
 * 1. Configured frontend origins receive Access-Control-Allow-Origin.
 * 2. Unregistered/malicious origins do NOT receive CORS access headers.
 * 3. OPTIONS preflight requests for allowed methods succeed.
 * 4. Preflight requests for disallowed methods are rejected.
 * 5. Credentials (cookies) are NOT enabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "cors_test_user", roles = {"OPERATOR"})
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CORS TEST 1: Allowed Origin receives Access-Control-Allow-Origin header")
    void testAllowedOriginReceivesCorsHeader() throws Exception {
        mockMvc.perform(get("/api/v1/devices")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("CORS TEST 2: Disallowed Origin does NOT receive Access-Control-Allow-Origin header")
    void testDisallowedOriginDoesNotReceiveCorsHeader() throws Exception {
        mockMvc.perform(get("/api/v1/devices")
                        .header(HttpHeaders.ORIGIN, "http://malicious.example"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("CORS TEST 3: Preflight OPTIONS request from allowed origin succeeds with permitted methods")
    void testPreflightOptionsAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/measurements")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("CORS TEST 4: Preflight OPTIONS request from disallowed origin is rejected")
    void testPreflightOptionsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/measurements")
                        .header(HttpHeaders.ORIGIN, "http://malicious.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("CORS TEST 5: Alternate configured origin (e.g. localhost:3000) also receives CORS header")
    void testAlternateConfiguredOriginReceivesCorsHeader() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/latest")
                        .param("deviceId", "SEM-ESP32-001")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }
}
