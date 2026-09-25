package com.smartenergy.monitoring.controller;

import com.smartenergy.monitoring.dto.MeasurementRequest;
import com.smartenergy.monitoring.dto.MeasurementResponse;
import com.smartenergy.monitoring.service.MeasurementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller exposing electrical telemetry ingestion and query endpoints for edge hardware.
 * Base Path: /api/v1/measurements
 */
@RestController
@RequestMapping("/api/v1/measurements")
@CrossOrigin(origins = "*")
public class MeasurementController {

    private final MeasurementService measurementService;

    public MeasurementController(MeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    /**
     * Ingests a new instantaneous electrical telemetry measurement from an edge device.
     * Validates electrical variables and persists them to the database.
     *
     * @param request validated telemetry payload
     * @return HTTP 201 CREATED with the persisted MeasurementResponse
     */
    @PostMapping
    public ResponseEntity<MeasurementResponse> recordMeasurement(@Valid @RequestBody MeasurementRequest request) {
        MeasurementResponse response = measurementService.recordMeasurement(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves the single latest electrical telemetry measurement for a device.
     * Used for real-time dashboard display and live hardware gauges.
     *
     * @param deviceId the hardware identifier string (default: SEM-ESP32-001)
     * @return HTTP 200 OK with the latest MeasurementResponse
     */
    @GetMapping("/latest")
    public ResponseEntity<MeasurementResponse> getLatestMeasurement(
            @RequestParam(name = "deviceId", defaultValue = "SEM-ESP32-001") String deviceId) {
        MeasurementResponse response = measurementService.getLatestMeasurement(deviceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves historical electrical telemetry measurements for a device within a time window.
     * Used for time-series charts, energy consumption profiles, and telemetry analytics.
     *
     * Defaults to the last 24 hours if timestamps are omitted.
     *
     * @param deviceId  hardware identifier string (default: SEM-ESP32-001)
     * @param startTime beginning of interval (ISO 8601 format: 2026-09-25T00:00:00)
     * @param endTime   end of interval (ISO 8601 format: 2026-09-25T23:59:59)
     * @return list of chronological measurements
     */
    @GetMapping("/history")
    public ResponseEntity<List<MeasurementResponse>> getHistoricalMeasurements(
            @RequestParam(name = "deviceId", defaultValue = "SEM-ESP32-001") String deviceId,
            @RequestParam(name = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(name = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        if (startTime == null) {
            startTime = endTime.minusHours(24);
        }

        List<MeasurementResponse> responseList = measurementService.getHistoricalMeasurements(deviceId, startTime, endTime);
        return ResponseEntity.ok(responseList);
    }
}
