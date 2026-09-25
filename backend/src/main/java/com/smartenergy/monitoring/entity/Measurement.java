package com.smartenergy.monitoring.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing instantaneous electrical telemetry received from an edge device.
 * Maps to the 'measurements' table created in Flyway migration V1__initial_schema.sql.
 * 
 * Electrical units:
 * - Voltage RMS: V (Volts)
 * - Current RMS: A (Amperes)
 * - Apparent Power: VA (Volt-Amperes, S = V_rms * I_rms)
 * - Incremental Apparent Energy: VAh (Volt-Ampere-hours, delta_VAh = S * (t / 3600))
 */
@Entity
@Table(name = "measurements")
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "voltage_rms", nullable = false, precision = 6, scale = 2)
    private BigDecimal voltageRms;

    @Column(name = "current_rms", nullable = false, precision = 6, scale = 3)
    private BigDecimal currentRms;

    @Column(name = "apparent_power", nullable = false, precision = 8, scale = 2)
    private BigDecimal apparentPower;

    @Column(name = "incremental_apparent_energy", nullable = false, precision = 10, scale = 4)
    private BigDecimal incrementalApparentEnergy;

    @Column(name = "sampling_duration_seconds", nullable = false, precision = 4, scale = 2)
    private BigDecimal samplingDurationSeconds = new BigDecimal("1.00");

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    // Default constructor required by JPA
    public Measurement() {
    }

    public Measurement(Device device, BigDecimal voltageRms, BigDecimal currentRms,
                       BigDecimal apparentPower, BigDecimal incrementalApparentEnergy,
                       BigDecimal samplingDurationSeconds, LocalDateTime recordedAt) {
        this.device = device;
        this.voltageRms = voltageRms;
        this.currentRms = currentRms;
        this.apparentPower = apparentPower;
        this.incrementalApparentEnergy = incrementalApparentEnergy;
        this.samplingDurationSeconds = (samplingDurationSeconds != null) ? samplingDurationSeconds : new BigDecimal("1.00");
        this.recordedAt = (recordedAt != null) ? recordedAt : LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
        if (this.samplingDurationSeconds == null) {
            this.samplingDurationSeconds = new BigDecimal("1.00");
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
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
        return "Measurement{" +
                "id=" + id +
                ", deviceId=" + (device != null ? device.getDeviceId() : "null") +
                ", voltageRms=" + voltageRms +
                ", currentRms=" + currentRms +
                ", apparentPower=" + apparentPower +
                ", incrementalApparentEnergy=" + incrementalApparentEnergy +
                ", samplingDurationSeconds=" + samplingDurationSeconds +
                ", recordedAt=" + recordedAt +
                '}';
    }
}
