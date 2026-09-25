package com.smartenergy.monitoring.controller;

import com.smartenergy.monitoring.dto.DeviceResponse;
import com.smartenergy.monitoring.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing Device Registry endpoints.
 * Base Path: /api/v1/devices
 */
@RestController
@RequestMapping("/api/v1/devices")
@CrossOrigin(origins = "*")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Lists all registered hardware devices (e.g. SEM-ESP32-001).
     *
     * @return HTTP 200 with list of devices
     */
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    /**
     * Retrieves status and metadata for a specific device identifier.
     *
     * @param deviceId the unique hardware identifier (e.g., SEM-ESP32-001)
     * @return HTTP 200 with DeviceResponse
     */
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDeviceById(@PathVariable String deviceId) {
        return ResponseEntity.ok(deviceService.getDeviceByIdentifier(deviceId));
    }
}
