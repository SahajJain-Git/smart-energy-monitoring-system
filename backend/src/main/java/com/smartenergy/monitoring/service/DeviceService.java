package com.smartenergy.monitoring.service;

import com.smartenergy.monitoring.dto.DeviceResponse;
import com.smartenergy.monitoring.exception.ResourceNotFoundException;
import com.smartenergy.monitoring.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing edge device registry operations.
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * Retrieves all registered edge devices in the system.
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll().stream()
                .map(DeviceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves device details by its business hardware identifier (e.g., SEM-ESP32-001).
     */
    @Transactional(readOnly = true)
    public DeviceResponse getDeviceByIdentifier(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .map(DeviceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with ID: " + deviceId));
    }
}
