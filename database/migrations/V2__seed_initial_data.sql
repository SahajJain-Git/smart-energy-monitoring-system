-- ==============================================================================
-- Smart Energy Monitoring System
-- Database Migration: V2__seed_initial_data.sql
-- Description: Seeds initial baseline reference records:
--              1. Prototype device SEM-ESP32-001
--              2. Default calibration configuration for SEM-ESP32-001 (ACS712-5A, ZMPT101B)
--              3. Prototype simulation electricity tariff (Educational Estimation)
--              4. Locked bootstrap administrative user record
-- Target Database: MySQL 8.0+
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Seed Initial Hardware Device: SEM-ESP32-001
-- ------------------------------------------------------------------------------
INSERT INTO devices (device_id, name, location, status)
VALUES (
    'SEM-ESP32-001',
    'Smart Energy Monitor Prototype 1',
    'Lab Test Bench A',
    'ACTIVE'
);

-- ------------------------------------------------------------------------------
-- 2. Seed Default Calibration Configuration for SEM-ESP32-001
-- Uses a subquery to look up the device's primary key dynamically.
-- Hardware Baseline:
--   - Current Sensor: Allegro ACS712-5A (nominal sensitivity: 185.00 mV/A)
--   - Voltage Sensor: ZMPT101B potential transformer module
-- ------------------------------------------------------------------------------
INSERT INTO calibration_configs (
    device_id, 
    voltage_calibration_factor, 
    voltage_offset, 
    current_sensitivity_mv_per_a, 
    current_zero_offset, 
    is_active, 
    notes
)
VALUES (
    (SELECT id FROM devices WHERE device_id = 'SEM-ESP32-001'),
    1.0000,
    0.00,
    185.00,
    0.000,
    TRUE,
    'Initial factory baseline calibration for ACS712-5A (185 mV/A) and ZMPT101B'
);

-- ------------------------------------------------------------------------------
-- 3. Seed Default Baseline Electricity Tariff
-- Educational / Prototype Simulation Notice:
--   - Rate: 7.5000 INR per kVAh (representative domestic/lab slab rate).
--   - Purpose: Prototype cost estimation only.
--   - Assumption: Applied directly to Apparent Energy (kVAh) assuming near-unity
--     power factor (PF ~ 1.0). This is NOT an official utility billing tariff.
-- ------------------------------------------------------------------------------
INSERT INTO tariffs (name, rate_per_kvah, currency, effective_from, is_active)
VALUES (
    'Prototype Simulation Tariff (Educational Estimation)',
    7.5000,
    'INR',
    CURRENT_TIMESTAMP,
    TRUE
);

-- ------------------------------------------------------------------------------
-- 4. Seed Initial Bootstrap Administrator Account (Locked State)
-- Security Best Practice for Public Repositories:
--   - We DO NOT commit active credentials or known password hashes (like 'admin123').
--   - The account is seeded in a DISABLED / LOCKED state (is_active = FALSE).
--   - The password_hash contains an invalid non-hash token, preventing authentication.
--   - Activation and secure credential assignment are performed locally during
--     Phase 3 application startup via gitignored configuration.
-- ------------------------------------------------------------------------------
INSERT INTO users (username, email, password_hash, role, is_active)
VALUES (
    'admin',
    'admin@smartenergy.local',
    'LOCKED_BOOTSTRAP_ACCOUNT_CONFIGURE_IN_PHASE_3',
    'ADMIN',
    FALSE
);
