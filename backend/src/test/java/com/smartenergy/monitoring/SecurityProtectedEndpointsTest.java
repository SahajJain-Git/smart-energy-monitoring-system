package com.smartenergy.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.AuthRequest;
import com.smartenergy.monitoring.dto.MeasurementRequest;
import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.UserRepository;
import com.smartenergy.monitoring.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying JWT protection on user-facing GET APIs and public access checkpoints.
 * Covers all 10 endpoint scenarios required for Phase 7 Step 7B-2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityProtectedEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_USER = "jwt_test_user";
    private static final String TEST_PASSWORD = "ValidPassword123!";
    private String validJwt;

    @BeforeEach
    void setUp() {
        cleanUser();

        User user = new User(
                TEST_USER,
                "jwt_test@smartenergy.local",
                passwordEncoder.encode(TEST_PASSWORD),
                "OPERATOR",
                true
        );
        userRepository.save(user);

        validJwt = jwtTokenProvider.generateToken(TEST_USER, "OPERATOR");
    }

    @AfterEach
    void tearDown() {
        cleanUser();
    }

    private void cleanUser() {
        userRepository.findByUsername(TEST_USER).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("ENDPOINT TEST 1: POST /api/v1/auth/login without JWT -> allowed (HTTP 200)")
    void testLogin_AllowedWithoutJwt() throws Exception {
        AuthRequest request = new AuthRequest(TEST_USER, TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.username", is(TEST_USER)));
    }

    @Test
    @DisplayName("ENDPOINT TEST 2: GET /api/v1/devices without JWT -> HTTP 401 Unauthorized")
    void testGetDevices_WithoutJwt_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("ENDPOINT TEST 3: GET /api/v1/devices with valid JWT -> HTTP 200 OK")
    void testGetDevices_WithValidJwt_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ENDPOINT TEST 4: GET /api/v1/devices with malformed JWT -> HTTP 401 Unauthorized")
    void testGetDevices_WithMalformedJwt_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("ENDPOINT TEST 5: GET /api/v1/measurements/latest without JWT -> HTTP 401 Unauthorized")
    void testGetLatestMeasurement_WithoutJwt_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/latest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("ENDPOINT TEST 6: GET /api/v1/measurements/latest with valid JWT -> allowed (HTTP 200 or 404 if empty)")
    void testGetLatestMeasurement_WithValidJwt_Allowed() throws Exception {
        // May return 200 if measurements exist or 404 if empty in test DB, but strictly NOT 401
        mockMvc.perform(get("/api/v1/measurements/latest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().is(org.hamcrest.Matchers.in(java.util.List.of(200, 404))));
    }

    @Test
    @DisplayName("ENDPOINT TEST 7: GET /api/v1/measurements/history without JWT -> HTTP 401 Unauthorized")
    void testGetHistory_WithoutJwt_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("ENDPOINT TEST 8: GET /api/v1/measurements/history with valid JWT -> allowed (HTTP 200)")
    void testGetHistory_WithValidJwt_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ENDPOINT TEST 9: POST /api/v1/measurements without JWT -> remains allowed for ESP32 checkpoint")
    void testPostMeasurement_WithoutJwt_Allowed() throws Exception {
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.50"));
        request.setCurrentRms(new BigDecimal("1.250"));
        request.setApparentPower(new BigDecimal("288.13"));
        request.setIncrementalApparentEnergy(new BigDecimal("0.4002"));
        request.setSamplingDurationSeconds(new BigDecimal("5.00"));

        // Requires X-API-Key in Step 7C: returns 201 Created when authenticated
        mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", "test-device-api-key-for-sem-esp32-001-secret-32chars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId", is("SEM-ESP32-001")));
    }

    @Test
    @DisplayName("ENDPOINT TEST 10: OPTIONS requests -> continue working without credentials")
    void testOptionsPreflight_ContinuesWorking() throws Exception {
        mockMvc.perform(options("/api/v1/devices")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"));
    }
}
