package com.smartenergy.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.MeasurementRequest;
import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.exception.GlobalExceptionHandler;
import com.smartenergy.monitoring.repository.DeviceRepository;
import com.smartenergy.monitoring.repository.MeasurementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying REST API contracts, validation rules, apparent energy physics,
 * database persistence, and comprehensive boundary / malformed edge-case behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "test_user", roles = {"OPERATOR"})
@Transactional
class MeasurementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeasurementRepository measurementRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    private static final String TEST_DEVICE_API_KEY = "test-device-api-key-for-sem-esp32-001-secret-32chars";

    // =========================================================================
    // BASELINE TESTS (Phase 3 Integration Verification)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/measurements - Should ingest telemetry and calculate apparent power and energy")
    void testRecordMeasurementSuccess() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setSamplingDurationSeconds(new BigDecimal("1.00"));

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.deviceId", is("SEM-ESP32-001")))
                .andExpect(jsonPath("$.voltageRms", is(230.00)))
                .andExpect(jsonPath("$.currentRms", is(5.000)))
                // S = 230.00 * 5.000 = 1150.00 VA
                .andExpect(jsonPath("$.apparentPower", is(1150.00)))
                // delta_VAh = 1150.00 * (1.00 / 3600) = 0.3194 VAh
                .andExpect(jsonPath("$.incrementalApparentEnergy", is(0.3194)))
                .andExpect(jsonPath("$.recordedAt", notNullValue()));

        assertThat(measurementRepository.count()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("POST /api/v1/measurements - Should return 400 Bad Request when validation fails")
    void testRecordMeasurementValidationFailure() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest invalidRequest = new MeasurementRequest();
        invalidRequest.setDeviceId(""); // Blank device ID
        invalidRequest.setVoltageRms(new BigDecimal("-10.00")); // Negative voltage
        invalidRequest.setCurrentRms(new BigDecimal("-2.000")); // Negative current

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.deviceId", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.voltageRms", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.currentRms", notNullValue()));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("GET /api/v1/measurements/latest - Should return latest measurement")
    void testGetLatestMeasurement() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/latest")
                        .param("deviceId", "SEM-ESP32-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId", is("SEM-ESP32-001")))
                .andExpect(jsonPath("$.voltageRms", notNullValue()))
                .andExpect(jsonPath("$.currentRms", notNullValue()))
                .andExpect(jsonPath("$.apparentPower", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/measurements/history - Should return historical telemetry list")
    void testGetHistoricalMeasurements() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/history")
                        .param("deviceId", "SEM-ESP32-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)));
    }

    @Test
    @DisplayName("GET /api/v1/devices - Should return list of registered devices")
    void testGetAllDevices() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].deviceId", is("SEM-ESP32-001")));
    }

    @Test
    @DisplayName("GET /api/v1/devices/{deviceId} - Should return device details")
    void testGetDeviceById() throws Exception {
        mockMvc.perform(get("/api/v1/devices/SEM-ESP32-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId", is("SEM-ESP32-001")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("GET /api/v1/devices/{deviceId} - Should return 404 for unknown device")
    void testGetDeviceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/devices/NON_EXISTENT_999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // =========================================================================
    // PHASE 7 STEPS 3 & 4: HARDENED EDGE-CASE & BOUNDARY REGRESSION TESTS
    // =========================================================================

    @Test
    @DisplayName("TEST A: POST /api/v1/measurements - Malformed JSON body returns sanitized 400 Bad Request")
    void testMalformedJsonBody() throws Exception {
        long initialCount = measurementRepository.count();

        // Intentionally broken JSON string (unterminated payload)
        String malformedJson = "{\"deviceId\":\"SEM-ESP32-001\",\"voltageRms\":230.0,";

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Malformed JSON request or invalid request field format")))
                .andExpect(jsonPath("$.message", not(containsString("JsonParseException"))))
                .andExpect(jsonPath("$.message", not(containsString("Unexpected end-of-input"))));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST B: POST /api/v1/measurements - Zero sampling duration should return 400 Bad Request")
    void testZeroSamplingDuration() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("2.000"));
        request.setSamplingDurationSeconds(BigDecimal.ZERO); // Fails @DecimalMin("0.01")

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.samplingDurationSeconds", is("Sampling duration must be greater than zero")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST C: POST /api/v1/measurements - Negative sampling duration should return 400 Bad Request")
    void testNegativeSamplingDuration() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("2.000"));
        request.setSamplingDurationSeconds(new BigDecimal("-1.00")); // Fails @DecimalMin("0.01")

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.samplingDurationSeconds", notNullValue()));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST D: POST /api/v1/measurements - Negative voltage should return 400 Bad Request")
    void testNegativeVoltage() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("-230.00")); // Fails @DecimalMin("0.0")
        request.setCurrentRms(new BigDecimal("2.000"));

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.voltageRms", is("Voltage RMS must be non-negative")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST E: POST /api/v1/measurements - Negative current should return 400 Bad Request")
    void testNegativeCurrent() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("-5.000")); // Fails @DecimalMin("0.0")

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.currentRms", is("Current RMS must be non-negative")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST F: POST /api/v1/measurements - Negative apparent power should return 400 Bad Request")
    void testNegativeApparentPower() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setApparentPower(new BigDecimal("-100.00")); // Fails @DecimalMin("0.0")

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.apparentPower", is("Apparent Power must be non-negative")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST G: POST /api/v1/measurements - Negative incremental energy should return 400 Bad Request")
    void testNegativeIncrementalEnergy() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setIncrementalApparentEnergy(new BigDecimal("-1.0000")); // Fails @DecimalMin("0.0")

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.incrementalApparentEnergy", is("Incremental Apparent Energy must be non-negative")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST H: POST /api/v1/measurements - Blank device ID should return 400 Bad Request")
    void testBlankDeviceId() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("   "); // Blank whitespace
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.deviceId", is("Device ID is required and cannot be empty")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST I: POST /api/v1/measurements - Null device ID should return 400 Bad Request")
    void testNullDeviceId() throws Exception {
        long initialCount = measurementRepository.count();

        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId(null); // Null device ID
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.deviceId", is("Device ID is required and cannot be empty")));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST J: POST /api/v1/measurements - Invalid timestamp string returns sanitized 400 Bad Request")
    void testInvalidTimestampString() throws Exception {
        long initialCount = measurementRepository.count();

        String payload = "{\"deviceId\":\"SEM-ESP32-001\",\"voltageRms\":230.00,\"currentRms\":5.000,\"recordedAt\":\"not-a-date\"}";

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Malformed JSON request or invalid request field format")))
                .andExpect(jsonPath("$.message", not(containsString("InvalidFormatException"))));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST K: POST /api/v1/measurements - Extreme numeric overflow rejected by DTO validation with 400")
    void testExtremeNumericOverflow() throws Exception {
        long initialCount = measurementRepository.count();

        // 9999999999.99 exceeds schema DECIMAL(6,2) and is intercepted by @DecimalMax / @Digits
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("9999999999.99"));
        request.setCurrentRms(new BigDecimal("5.000"));

        mockMvc.perform(post("/api/v1/measurements").header("X-API-Key", TEST_DEVICE_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.voltageRms", notNullValue()))
                .andExpect(jsonPath("$.error", not(containsString("Data truncation"))));

        assertThat(measurementRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("TEST L: GET /api/v1/measurements/history - Inverted date range (from > to) returns 400 Bad Request")
    void testInvertedHistoryDateRange() throws Exception {
        // from = 15:00:00 > to = 14:00:00
        mockMvc.perform(get("/api/v1/measurements/history")
                        .param("deviceId", "SEM-ESP32-001")
                        .param("startTime", "2026-09-26T15:00:00")
                        .param("endTime", "2026-09-26T14:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Start time must be before or equal to end time.")));
    }

    @Test
    @DisplayName("TEST M: GET /api/v1/measurements/latest - Unknown device ID should return 404 Not Found")
    void testLatestMeasurementUnknownDevice() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/latest")
                        .param("deviceId", "DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Device not found with ID: DOES-NOT-EXIST")));
    }

    @Test
    @DisplayName("TEST N: GET /api/v1/measurements/history - Unknown device ID should return 404 Not Found")
    void testHistoryUnknownDevice() throws Exception {
        mockMvc.perform(get("/api/v1/measurements/history")
                        .param("deviceId", "DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Device not found with ID: DOES-NOT-EXIST")));
    }

    @Test
    @DisplayName("TEST O: GET /api/v1/measurements/latest - Existing device without telemetry records should return 404")
    void testLatestMeasurementDeviceWithoutTelemetry() throws Exception {
        // Register an active test device that has never posted telemetry
        Device emptyDevice = new Device("EMPTY-TEST-NODE-001", "Zero Telemetry Test Device", "Lab Shelf", "ACTIVE");
        deviceRepository.save(emptyDevice);

        mockMvc.perform(get("/api/v1/measurements/latest")
                        .param("deviceId", "EMPTY-TEST-NODE-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("No telemetry measurements found for device: EMPTY-TEST-NODE-001")));
    }

    @Test
    @DisplayName("TEST P: GET /api/v1/measurements/history - Valid device with zero records in window should return 200 []")
    void testHistoryEmptyWindow() throws Exception {
        // Distant future window containing zero records
        mockMvc.perform(get("/api/v1/measurements/history")
                        .param("deviceId", "SEM-ESP32-001")
                        .param("startTime", "2099-01-01T00:00:00")
                        .param("endTime", "2099-01-01T01:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // STEP 4 TARGETED EXCEPTION SANITIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Exception Sanitization: DataIntegrityViolationException returns sanitized 400 without exposing SQL details")
    void testDataIntegrityViolationSanitized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/measurements");

        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("Cannot add or update a child row: foreign key constraint fails (`smart_energy_db`.`measurements`, CONSTRAINT `fk_measurements_device`)");

        var response = globalExceptionHandler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo("Request data could not be stored because it violates a data constraint.");
        assertThat(response.getBody().getMessage()).doesNotContain("foreign key");
        assertThat(response.getBody().getMessage()).doesNotContain("smart_energy_db");
    }

    @Test
    @DisplayName("Exception Sanitization: Generic unhandled Exception returns sanitized 500 without leaking details")
    void testGenericExceptionSanitized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/measurements");

        Exception ex = new RuntimeException("Sensitive database credentials password=secret leaked in exception");

        var response = globalExceptionHandler.handleGenericException(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected internal server error occurred.");
        assertThat(response.getBody().getMessage()).doesNotContain("password=secret");
        assertThat(response.getBody().getMessage()).doesNotContain("Sensitive");
    }
}
