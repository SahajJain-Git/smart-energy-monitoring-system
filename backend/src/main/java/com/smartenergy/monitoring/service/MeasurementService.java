package com.smartenergy.monitoring.service;

import com.smartenergy.monitoring.dto.MeasurementRequest;
import com.smartenergy.monitoring.dto.MeasurementResponse;
import com.smartenergy.monitoring.entity.Device;
import com.smartenergy.monitoring.entity.Measurement;
import com.smartenergy.monitoring.exception.ResourceNotFoundException;
import com.smartenergy.monitoring.repository.DeviceRepository;
import com.smartenergy.monitoring.repository.MeasurementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing telemetry data ingestion and queries for the Smart Energy Monitoring System.
 * 
 * Implements apparent-energy calculations:
 * - Apparent Power: S = V_rms * I_rms (VA)
 * - Incremental Apparent Energy: delta_VAh = S * (t / 3600) (VAh)
 */
@Service
public class MeasurementService {

    private final DeviceRepository deviceRepository;
    private final MeasurementRepository measurementRepository;

    public MeasurementService(DeviceRepository deviceRepository, MeasurementRepository measurementRepository) {
        this.deviceRepository = deviceRepository;
        this.measurementRepository = measurementRepository;
    }

    /**
     * Ingests a new electrical measurement from an edge device (e.g. SEM-ESP32-001).
     * Calculates apparent power and incremental apparent energy if omitted by the device.
     *
     * @param request the telemetry payload
     * @return the persisted telemetry response DTO
     */
    @Transactional
    public MeasurementResponse recordMeasurement(MeasurementRequest request) {
        // 1. Resolve or auto-register edge device
        Device device = deviceRepository.findByDeviceId(request.getDeviceId())
                .orElseGet(() -> {
                    Device newDevice = new Device(
                            request.getDeviceId(),
                            "Edge Sensor " + request.getDeviceId(),
                            "Lab / Unspecified",
                            "ACTIVE"
                    );
                    return deviceRepository.save(newDevice);
                });

        // 2. Resolve sampling duration (default 1.00 second)
        BigDecimal durationSeconds = (request.getSamplingDurationSeconds() != null)
                ? request.getSamplingDurationSeconds()
                : new BigDecimal("1.00");

        // 3. Compute Apparent Power (VA) if omitted: S = V_rms * I_rms
        BigDecimal apparentPower = request.getApparentPower();
        if (apparentPower == null) {
            apparentPower = request.getVoltageRms()
                    .multiply(request.getCurrentRms())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Compute Incremental Apparent Energy (VAh) if omitted: delta_VAh = S * (duration / 3600)
        BigDecimal incrementalEnergy = request.getIncrementalApparentEnergy();
        if (incrementalEnergy == null) {
            incrementalEnergy = apparentPower
                    .multiply(durationSeconds)
                    .divide(new BigDecimal("3600"), 4, RoundingMode.HALF_UP);
        }

        // 5. Resolve timestamp
        LocalDateTime recordedAt = (request.getRecordedAt() != null)
                ? request.getRecordedAt()
                : LocalDateTime.now();

        // 6. Build and persist Measurement entity
        Measurement measurement = new Measurement(
                device,
                request.getVoltageRms().setScale(2, RoundingMode.HALF_UP),
                request.getCurrentRms().setScale(3, RoundingMode.HALF_UP),
                apparentPower.setScale(2, RoundingMode.HALF_UP),
                incrementalEnergy.setScale(4, RoundingMode.HALF_UP),
                durationSeconds.setScale(2, RoundingMode.HALF_UP),
                recordedAt
        );

        Measurement saved = measurementRepository.save(measurement);
        return MeasurementResponse.fromEntity(saved);
    }

    /**
     * Retrieves the latest instantaneous measurement for a device.
     *
     * @param deviceId the hardware identifier string (e.g., SEM-ESP32-001)
     * @return the most recent measurement response
     * @throws ResourceNotFoundException if device or telemetry does not exist
     */
    @Transactional(readOnly = true)
    public MeasurementResponse getLatestMeasurement(String deviceId) {
        if (!deviceRepository.existsByDeviceId(deviceId)) {
            throw new ResourceNotFoundException("Device not found with ID: " + deviceId);
        }

        return measurementRepository.findTopByDevice_DeviceIdOrderByRecordedAtDesc(deviceId)
                .map(MeasurementResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("No telemetry measurements found for device: " + deviceId));
    }

    /**
     * Retrieves historical measurements for a device within a given time range.
     *
     * @param deviceId  the hardware identifier string
     * @param startTime beginning of interval (inclusive)
     * @param endTime   end of interval (inclusive)
     * @return list of chronological measurements
     */
    @Transactional(readOnly = true)
    public List<MeasurementResponse> getHistoricalMeasurements(String deviceId, LocalDateTime startTime, LocalDateTime endTime) {
        if (!deviceRepository.existsByDeviceId(deviceId)) {
            throw new ResourceNotFoundException("Device not found with ID: " + deviceId);
        }

        return measurementRepository.findByDevice_DeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(deviceId, startTime, endTime)
                .stream()
                .map(MeasurementResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
