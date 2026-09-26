package com.smartenergy.monitoring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.AuthRequest;
import com.smartenergy.monitoring.dto.MeasurementRequest;
import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.entity.User;
import com.smartenergy.monitoring.repository.DeviceRepository;
import com.smartenergy.monitoring.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test suite for Phase 7 Step 7C: ESP32 Device API-Key Authentication.
 * Verifies device API-key authentication, device ID binding, device status enforcement,
 * privilege separation from user JWT APIs, and sanitized error responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceApiKeyAuthenticationTest {

    private static final String TEST_DEVICE_API_KEY = "test-device-api-key-for-sem-esp32-001-secret-32chars";
    private static final String DEVICE_ID = "SEM-ESP32-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Device originalDevice;

    @BeforeEach
    void setUp() {
        // Ensure device exists and is ACTIVE
        originalDevice = deviceRepository.findByDeviceId(DEVICE_ID)
                .orElseGet(() -> deviceRepository.save(new Device(DEVICE_ID, "Test Prototype", "Lab Bench A", "ACTIVE")));
        originalDevice.setStatus("ACTIVE");
        deviceRepository.save(originalDevice);

        // Ensure test user exists for user token tests
        if (!userRepository.existsByUsername("test_operator")) {
            User user = new User("test_operator", "operator@example.com", passwordEncoder.encode("OperatorPass123!"), "OPERATOR", true);
            userRepository.save(user);
        }
    }

    @AfterEach
    void tearDown() {
        // Restore active status
        if (originalDevice != null) {
            originalDevice.setStatus("ACTIVE");
            deviceRepository.save(originalDevice);
        }
    }

    private MeasurementRequest createValidMeasurementRequest(String deviceId) {
        MeasurementRequest req = new MeasurementRequest();
        req.setDeviceId(deviceId);
        req.setVoltageRms(new BigDecimal("230.50"));
        req.setCurrentRms(new BigDecimal("3.120"));
        req.setApparentPower(new BigDecimal("719.16"));
        req.setIncrementalApparentEnergy(new BigDecimal("0.9988"));
        req.setSamplingDurationSeconds(new BigDecimal("5.00"));
        req.setRecordedAt(LocalDateTime.now());
        return req;
    }

    // =========================================================================
    // 1. Missing / Invalid / Empty X-API-Key Tests
    // =========================================================================

    @Test
    @DisplayName("DEV-AUTH 1: POST measurement without X-API-Key header returns HTTP 401 Unauthorized")
    void testPostMeasurement_WithoutApiKey_Returns401() throws Exception {
        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        mockMvc.perform(post("/api/v1/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Missing or empty 'X-API-Key' header")));
    }

    @Test
    @DisplayName("DEV-AUTH 2: POST measurement with invalid X-API-Key returns HTTP 401 Unauthorized")
    void testPostMeasurement_WithInvalidApiKey_Returns401() throws Exception {
        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", "totally-invalid-wrong-device-key-99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Invalid device API key")));
    }

    @Test
    @DisplayName("DEV-AUTH 3: POST measurement with empty X-API-Key returns HTTP 401 Unauthorized")
    void testPostMeasurement_WithEmptyApiKey_Returns401() throws Exception {
        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Missing or empty 'X-API-Key' header")));

        mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // =========================================================================
    // 2. Successful Telemetry Ingestion Tests
    // =========================================================================

    @Test
    @DisplayName("DEV-AUTH 4 & 5: Valid key with matching SEM-ESP32-001 deviceId is accepted (201 Created)")
    void testPostMeasurement_WithValidKeyAndMatchingDeviceId_Accepted() throws Exception {
        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        MvcResult result = mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId", is(DEVICE_ID)))
                .andExpect(jsonPath("$.voltageRms", is(230.50)))
                .andReturn();

        // Verify API key never leaks in response
        String responseContent = result.getResponse().getContentAsString();
        assertThat(responseContent).doesNotContain(TEST_DEVICE_API_KEY);
    }

    // =========================================================================
    // 3. Device ID Binding & Inactive Device Tests
    // =========================================================================

    @Test
    @DisplayName("DEV-AUTH 6: Valid key for SEM-ESP32-001 with different payload deviceId returns HTTP 403 Forbidden")
    void testPostMeasurement_WithValidKeyDifferentDeviceId_Returns403() throws Exception {
        // Request payload specifies spoofed / mismatched device ID
        MeasurementRequest request = createValidMeasurementRequest("SPOOFED-DEVICE-999");

        mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", containsString("Device identity mismatch")));
    }

    @Test
    @DisplayName("DEV-AUTH 7: Valid key but inactive device in database returns HTTP 403 Forbidden")
    void testPostMeasurement_WithValidKeyInactiveDevice_Returns403() throws Exception {
        // Deactivate device
        originalDevice.setStatus("INACTIVE");
        deviceRepository.save(originalDevice);

        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", containsString("Device is inactive")));
    }

    // =========================================================================
    // 4. Privilege Separation: Device Key Cannot Access User APIs
    // =========================================================================

    @Test
    @DisplayName("DEV-AUTH 8: Valid device API key cannot access GET /api/v1/devices (returns 401)")
    void testDeviceKeyCannotAccessUserDevicesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/devices")
                        .header("X-API-Key", TEST_DEVICE_API_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("DEV-AUTH 9: Valid device API key cannot access GET /api/v1/measurements/history (returns 401)")
    void testDeviceKeyCannotAccessUserHistoryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/history")
                        .header("X-API-Key", TEST_DEVICE_API_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    // =========================================================================
    // 5. Privilege Separation: User JWT Cannot Authenticate Ingestion
    // =========================================================================

    @Test
    @DisplayName("DEV-AUTH 10: Human user JWT token cannot authenticate POST /api/v1/measurements without device key (returns 401)")
    void testUserJwtCannotAuthenticateTelemetryIngestion() throws Exception {
        String userToken = jwtTokenProvider.generateToken("admin", "ADMIN");
        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        // Client supplies Bearer JWT but NO X-API-Key
        mockMvc.perform(post("/api/v1/measurements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Missing or empty 'X-API-Key' header")));
    }

    // =========================================================================
    // 6. Security Sanitization & Regression Checks
    // =========================================================================

    @Test
    @DisplayName("DEV-AUTH 11: Human user JWT continues to access user GET endpoints normally")
    void testUserJwtCanAccessUserEndpoints() throws Exception {
        String userToken = jwtTokenProvider.generateToken("test_operator", "OPERATOR");

        mockMvc.perform(get("/api/v1/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/measurements/latest")
                        .param("deviceId", DEVICE_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DEV-AUTH 12: Device API key is never exposed in error responses")
    void testApiKeyNeverExposedInErrorResponses() throws Exception {
        MeasurementRequest request = createValidMeasurementRequest(DEVICE_ID);

        MvcResult result = mockMvc.perform(post("/api/v1/measurements")
                        .header("X-API-Key", "confidential-random-attempt-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("confidential-random-attempt-key");
        assertThat(body).doesNotContain(TEST_DEVICE_API_KEY);
    }
}
