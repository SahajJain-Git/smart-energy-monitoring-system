package com.smartenergy.monitoring.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for incoming electrical telemetry POST requests.
 * Received from edge IoT devices such as SEM-ESP32-001.
 *
 * Electrical units:
 * - Voltage RMS: V (Volts)
 * - Current RMS: A (Amperes)
 * - Apparent Power: VA (Volt-Amperes, S = V_rms * I_rms)
 * - Incremental Apparent Energy: VAh (delta_VAh = S * (t / 3600))
 */
public class MeasurementRequest {

    @NotBlank(message = "Device ID is required and cannot be empty")
    private String deviceId;

    @NotNull(message = "Voltage RMS is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Voltage RMS must be non-negative")
    private BigDecimal voltageRms;

    @NotNull(message = "Current RMS is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Current RMS must be non-negative")
    private BigDecimal currentRms;

    @DecimalMin(value = "0.0", inclusive = true, message = "Apparent Power must be non-negative")
    private BigDecimal apparentPower;

    @DecimalMin(value = "0.0", inclusive = true, message = "Incremental Apparent Energy must be non-negative")
    private BigDecimal incrementalApparentEnergy;

    @DecimalMin(value = "0.01", inclusive = true, message = "Sampling duration must be greater than zero")
    private BigDecimal samplingDurationSeconds;

    private LocalDateTime recordedAt;

    public MeasurementRequest() {
    }

    public MeasurementRequest(String deviceId, BigDecimal voltageRms, BigDecimal currentRms,
                              BigDecimal apparentPower, BigDecimal incrementalApparentEnergy,
                              BigDecimal samplingDurationSeconds, LocalDateTime recordedAt) {
        this.deviceId = deviceId;
        this.voltageRms = voltageRms;
        this.currentRms = currentRms;
        this.apparentPower = apparentPower;
        this.incrementalApparentEnergy = incrementalApparentEnergy;
        this.samplingDurationSeconds = samplingDurationSeconds;
        this.recordedAt = recordedAt;
    }

    // Getters and Setters

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
        return "MeasurementRequest{" +
                "deviceId='" + deviceId + '\'' +
                ", voltageRms=" + voltageRms +
                ", currentRms=" + currentRms +
                ", apparentPower=" + apparentPower +
                ", incrementalApparentEnergy=" + incrementalApparentEnergy +
                ", samplingDurationSeconds=" + samplingDurationSeconds +
                ", recordedAt=" + recordedAt +
                '}';
    }
}
