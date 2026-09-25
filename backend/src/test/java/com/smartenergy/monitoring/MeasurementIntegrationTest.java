package com.smartenergy.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.monitoring.dto.MeasurementRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying the REST API contracts, validation rules,
 * apparent energy physics calculations, and database persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeasurementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/measurements - Should ingest telemetry and calculate apparent power and energy")
    void testRecordMeasurementSuccess() throws Exception {
        MeasurementRequest request = new MeasurementRequest();
        request.setDeviceId("SEM-ESP32-001");
        request.setVoltageRms(new BigDecimal("230.00"));
        request.setCurrentRms(new BigDecimal("5.000"));
        request.setSamplingDurationSeconds(new BigDecimal("1.00"));

        mockMvc.perform(post("/api/v1/measurements")
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
    }

    @Test
    @DisplayName("POST /api/v1/measurements - Should return 400 Bad Request when validation fails")
    void testRecordMeasurementValidationFailure() throws Exception {
        MeasurementRequest invalidRequest = new MeasurementRequest();
        invalidRequest.setDeviceId(""); // Blank device ID
        invalidRequest.setVoltageRms(new BigDecimal("-10.00")); // Negative voltage
        invalidRequest.setCurrentRms(new BigDecimal("-2.000")); // Negative current

        mockMvc.perform(post("/api/v1/measurements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.deviceId", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.voltageRms", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.currentRms", notNullValue()));
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
}
