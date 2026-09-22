-- ==============================================================================
-- Smart Energy Monitoring System
-- Database Migration: V1__initial_schema.sql
-- Description: Creates foundational tables, constraints, foreign keys, and indexes.
-- Target Database: MySQL 8.0+ (InnoDB, UTF-8 MB4)
-- Device Identifier Reference: SEM-ESP32-001
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Table: devices (Hardware Registry)
-- Parent table for all hardware sensors and energy telemetry.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(150),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_device_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 2. Table: tariffs (Electricity Pricing Schedules)
-- Stores tariff rates per kVAh for educational cost estimation.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tariffs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    rate_per_kvah DECIMAL(8, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tariff_rate_positive CHECK (rate_per_kvah >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 3. Table: users (Application Access & Role-Based Access Control)
-- Stores credentials and roles for dashboard access. Passwords stored as hashes.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 4. Table: measurements (Instantaneous Time-Series Telemetry)
-- Stores electrical telemetry received from devices.
-- Values: V_rms (V), I_rms (A), Apparent Power (VA), Incremental Apparent Energy (VAh).
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS measurements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    voltage_rms DECIMAL(6, 2) NOT NULL,
    current_rms DECIMAL(6, 3) NOT NULL,
    apparent_power DECIMAL(8, 2) NOT NULL,
    incremental_apparent_energy DECIMAL(10, 4) NOT NULL,
    sampling_duration_seconds DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_measurements_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_voltage_positive CHECK (voltage_rms >= 0),
    CONSTRAINT chk_current_positive CHECK (current_rms >= 0),
    CONSTRAINT chk_power_positive CHECK (apparent_power >= 0),
    CONSTRAINT chk_energy_positive CHECK (incremental_apparent_energy >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 5. Table: energy_summaries (Authoritative Rollups)
-- Pre-computed hourly, daily, and monthly apparent energy summaries.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS energy_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    summary_type VARCHAR(20) NOT NULL,
    total_apparent_energy_kvah DECIMAL(10, 4) NOT NULL,
    avg_apparent_power_va DECIMAL(8, 2) NOT NULL,
    peak_apparent_power_va DECIMAL(8, 2) NOT NULL,
    estimated_cost DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_summaries_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_summary_type CHECK (summary_type IN ('HOURLY', 'DAILY', 'MONTHLY')),
    CONSTRAINT chk_summary_energy_positive CHECK (total_apparent_energy_kvah >= 0),
    CONSTRAINT chk_summary_power_positive CHECK (avg_apparent_power_va >= 0 AND peak_apparent_power_va >= 0),
    CONSTRAINT chk_summary_cost_positive CHECK (estimated_cost >= 0),
    CONSTRAINT uq_device_summary_period UNIQUE (device_id, summary_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 6. Table: calibration_configs (Sensor Calibration Factors)
-- Stores board-specific calibration offsets and sensitivity coefficients.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS calibration_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    voltage_calibration_factor DECIMAL(8, 4) NOT NULL DEFAULT 1.0000,
    voltage_offset DECIMAL(6, 2) NOT NULL DEFAULT 0.00,
    current_sensitivity_mv_per_a DECIMAL(8, 2) NOT NULL DEFAULT 100.00,
    current_zero_offset DECIMAL(6, 3) NOT NULL DEFAULT 0.000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    calibrated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(255),

    CONSTRAINT fk_calibration_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_cal_voltage_factor CHECK (voltage_calibration_factor > 0),
    CONSTRAINT chk_cal_current_sensitivity CHECK (current_sensitivity_mv_per_a > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 7. Table: fault_events (System Fault & Anomaly Log)
-- Records abnormal electrical events (over-voltage, over-current, voltage sags).
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fault_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    fault_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    trigger_value DECIMAL(8, 2),
    threshold_value DECIMAL(8, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'TRIGGERED',
    message VARCHAR(255),
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,

    CONSTRAINT fk_faults_device
        FOREIGN KEY (device_id) REFERENCES devices(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_fault_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_fault_status CHECK (status IN ('TRIGGERED', 'ACKNOWLEDGED', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------------------
-- 8. Performance Indexes (Time-Series and Fast Filtering)
-- ------------------------------------------------------------------------------
CREATE INDEX idx_measurements_device_recorded 
    ON measurements (device_id, recorded_at DESC);

CREATE INDEX idx_faults_device_status_triggered 
    ON fault_events (device_id, status, triggered_at DESC);

CREATE INDEX idx_tariffs_active_period 
    ON tariffs (is_active, effective_from);
