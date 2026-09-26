package com.smartenergy.monitoring.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for incoming electrical telemetry POST requests.
 * Received from edge IoT devices such as SEM-ESP32-001.
 *
 * Validated against physical boundaries and MySQL 8 storage precision:
 * - Voltage RMS: V (0.00 to 9999.99, DECIMAL(6,2))
 * - Current RMS: A (0.000 to 999.999, DECIMAL(6,3))
 * - Apparent Power: VA (0.00 to 999999.99, DECIMAL(8,2))
 * - Incremental Apparent Energy: VAh (0.0000 to 999999.9999, DECIMAL(10,4))
 * - Sampling Duration: s (0.01 to 99.99, DECIMAL(4,2))
 */
public class MeasurementRequest {

    @NotBlank(message = "Device ID is required and cannot be empty")
    private String deviceId;

    @NotNull(message = "Voltage RMS is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Voltage RMS must be non-negative")
    @DecimalMax(value = "9999.99", message = "Voltage RMS cannot exceed 9999.99")
    @Digits(integer = 4, fraction = 2, message = "Voltage RMS format must match max 4 integer digits and 2 decimals")
    private BigDecimal voltageRms;

    @NotNull(message = "Current RMS is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Current RMS must be non-negative")
    @DecimalMax(value = "999.999", message = "Current RMS cannot exceed 999.999")
    @Digits(integer = 3, fraction = 3, message = "Current RMS format must match max 3 integer digits and 3 decimals")
    private BigDecimal currentRms;

    @DecimalMin(value = "0.0", inclusive = true, message = "Apparent Power must be non-negative")
    @DecimalMax(value = "999999.99", message = "Apparent Power cannot exceed 999999.99")
    @Digits(integer = 6, fraction = 2, message = "Apparent Power format must match max 6 integer digits and 2 decimals")
    private BigDecimal apparentPower;

    @DecimalMin(value = "0.0", inclusive = true, message = "Incremental Apparent Energy must be non-negative")
    @DecimalMax(value = "999999.9999", message = "Incremental Apparent Energy cannot exceed 999999.9999")
    @Digits(integer = 6, fraction = 4, message = "Incremental Apparent Energy format must match max 6 integer digits and 4 decimals")
    private BigDecimal incrementalApparentEnergy;

    @DecimalMin(value = "0.01", inclusive = true, message = "Sampling duration must be greater than zero")
    @DecimalMax(value = "99.99", message = "Sampling duration cannot exceed 99.99")
    @Digits(integer = 2, fraction = 2, message = "Sampling duration format must match max 2 integer digits and 2 decimals")
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
