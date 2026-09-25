package com.smartenergy.monitoring.dto;

import com.smartenergy.monitoring.entity.Measurement;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the JSON response structure for electrical telemetry.
 * Returned to dashboard clients, historical queries, and ingestion confirmations.
 *
 * Electrical telemetry units:
 * - Voltage RMS: V
 * - Current RMS: A
 * - Apparent Power: VA
 * - Incremental Apparent Energy: VAh
 */
public class MeasurementResponse {

    private Long id;
    private String deviceId;
    private BigDecimal voltageRms;
    private BigDecimal currentRms;
    private BigDecimal apparentPower;
    private BigDecimal incrementalApparentEnergy;
    private BigDecimal samplingDurationSeconds;
    private LocalDateTime recordedAt;

    public MeasurementResponse() {
    }

    public MeasurementResponse(Long id, String deviceId, BigDecimal voltageRms, BigDecimal currentRms,
                               BigDecimal apparentPower, BigDecimal incrementalApparentEnergy,
                               BigDecimal samplingDurationSeconds, LocalDateTime recordedAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.voltageRms = voltageRms;
        this.currentRms = currentRms;
        this.apparentPower = apparentPower;
        this.incrementalApparentEnergy = incrementalApparentEnergy;
        this.samplingDurationSeconds = samplingDurationSeconds;
        this.recordedAt = recordedAt;
    }

    /**
     * Factory method converting a JPA Measurement entity to an API response DTO.
     */
    public static MeasurementResponse fromEntity(Measurement measurement) {
        if (measurement == null) {
            return null;
        }
        return new MeasurementResponse(
                measurement.getId(),
                measurement.getDevice() != null ? measurement.getDevice().getDeviceId() : null,
                measurement.getVoltageRms(),
                measurement.getCurrentRms(),
                measurement.getApparentPower(),
                measurement.getIncrementalApparentEnergy(),
                measurement.getSamplingDurationSeconds(),
                measurement.getRecordedAt()
        );
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public BigDecimal getVoltageRms() {
        return voltageRms;
    }

    public void setVoltageRms(BigDecimal voltageRms) {
        this.voltageRms = voltageRms;
    }

    public BigDecimal getCurrentRms() {
        return currentRms;
    }

    public void setCurrentRms(BigDecimal currentRms) {
        this.currentRms = currentRms;
    }

    public BigDecimal getApparentPower() {
        return apparentPower;
    }

    public void setApparentPower(BigDecimal apparentPower) {
        this.apparentPower = apparentPower;
    }

    public BigDecimal getIncrementalApparentEnergy() {
        return incrementalApparentEnergy;
    }

    public void setIncrementalApparentEnergy(BigDecimal incrementalApparentEnergy) {
        this.incrementalApparentEnergy = incrementalApparentEnergy;
    }

    public BigDecimal getSamplingDurationSeconds() {
        return samplingDurationSeconds;
    }

    public void setSamplingDurationSeconds(BigDecimal samplingDurationSeconds) {
        this.samplingDurationSeconds = samplingDurationSeconds;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public String toString() {
        return "MeasurementResponse{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", voltageRms=" + voltageRms +
                ", currentRms=" + currentRms +
                ", apparentPower=" + apparentPower +
                ", incrementalApparentEnergy=" + incrementalApparentEnergy +
                ", samplingDurationSeconds=" + samplingDurationSeconds +
                ", recordedAt=" + recordedAt +
                '}';
    }
}
