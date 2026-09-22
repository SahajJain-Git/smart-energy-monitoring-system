# Smart Energy Monitoring System — Database Design & Schema Specification

> **Document Version:** 1.0.0  
> **Target Database:** MySQL 8.0+ (InnoDB, UTF-8 MB4)  
> **Schema Name:** `smart_energy_db`  
> **Primary Hardware Target:** `SEM-ESP32-001`  
> **Status:** Phase 2 Complete (Verified Schema & Flyway Migrations)

---

## 1. Database Architecture & Design Philosophy

The `smart_energy_db` relational database serves as the persistent single source of truth for the **Smart Energy Monitoring System**.

### Core Engineering Principles Applied:
1. **Third Normal Form (3NF) Compliance:** All entity tables are normalized to eliminate duplicate attributes and update anomalies.
2. **Controlled Denormalization for IoT:** The `energy_summaries` table provides pre-aggregated hourly, daily, and monthly rollups ($\text{kVAh}$) to make analytics queries fast without scanning millions of raw measurement rows.
3. **Multi-Layered Constraints:** Enforced database-level primary keys, foreign keys (`ON DELETE RESTRICT`), unique constraints, and `CHECK` constraints to reject negative electrical values or invalid status codes.
4. **Automated Schema Evolution via Flyway:** All database schemas are tracked as immutable, version-controlled migration files in `database/migrations/`.

---

## 2. Entity-Relationship (ER) Model

```
 ┌──────────────┐          1:N         ┌───────────────────┐
 │    users     │                      │      devices      │
 └──────────────┘                      └─────────┬─────────┘
                                                 │
                  ┌──────────────────────────────┼──────────────────────────────┐
                  │ 1:N                          │ 1:N                          │ 1:N
                  ▼                              ▼                              ▼
       ┌────────────────────┐         ┌────────────────────┐         ┌────────────────────┐
       │    measurements    │         │  energy_summaries  │         │    fault_events    │
       └────────────────────┘         └────────────────────┘         └────────────────────┘
                  │                              │
                  │                              │ (Applied calculation)
                  ▼                              ▼
       ┌────────────────────┐         ┌────────────────────┐
       │calibration_configs │         │      tariffs       │
       └────────────────────┘         └────────────────────┘
```

---

## 3. Detailed Table Specifications

### Table 1: `devices` (Hardware Registry)
Parent table representing physical sensor boards and monitoring nodes.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Internal surrogate identifier for fast joins. |
| `device_id` | `VARCHAR(50)` | NO | `UNIQUE` | Hardware business identifier (e.g., `SEM-ESP32-001`). |
| `name` | `VARCHAR(100)` | NO | | Friendly label (e.g., *'Smart Energy Monitor Prototype 1'*). |
| `location` | `VARCHAR(150)` | YES | | Physical installation place (e.g., *'Lab Test Bench A'*). |
| `status` | `VARCHAR(20)` | NO | `CHECK IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')` | Operational state (`DEFAULT 'ACTIVE'`). |
| `created_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Initial registration timestamp. |
| `updated_at` | `TIMESTAMP` | NO | `ON UPDATE CURRENT_TIMESTAMP` | Timestamp of last metadata update. |

---

### Table 2: `measurements` (Time-Series Telemetry)
High-resolution time-series storage for electrical telemetry dispatched from the ESP32.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Unique measurement identifier. |
| `device_id` | `BIGINT` | NO | `FOREIGN KEY (devices.id) ON DELETE RESTRICT` | Reference to parent device. |
| `voltage_rms` | `DECIMAL(6, 2)` | NO | `CHECK >= 0` | Measured Voltage RMS in **Volts (V)**. |
| `current_rms` | `DECIMAL(6, 3)` | NO | `CHECK >= 0` | Measured Current RMS in **Amperes (A)**. |
| `apparent_power`| `DECIMAL(8, 2)` | NO | `CHECK >= 0` | Instantaneous Apparent Power in **Volt-Amperes (VA)** ($S = V \times I$). |
| `incremental_apparent_energy` | `DECIMAL(10, 4)` | NO | `CHECK >= 0` | Apparent energy slice in **Volt-Ampere hours ($\Delta\text{VAh}$)**. |
| `sampling_duration_seconds` | `DECIMAL(4, 2)` | NO | `DEFAULT 1.00` | Duration of sampling period in seconds. |
| `recorded_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Timestamp when the measurement was taken. |

* **Index:** `idx_measurements_device_recorded (device_id, recorded_at DESC)`

---

### Table 3: `energy_summaries` (Authoritative Rollups)
Pre-aggregated rollups calculated by the backend service. Single source of truth for rolling consumption.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Unique summary record identifier. |
| `device_id` | `BIGINT` | NO | `FOREIGN KEY (devices.id) ON DELETE RESTRICT` | Reference to parent device. |
| `summary_type` | `VARCHAR(20)` | NO | `CHECK IN ('HOURLY', 'DAILY', 'MONTHLY')` | Rollup granularity. |
| `total_apparent_energy_kvah` | `DECIMAL(10, 4)` | NO | `CHECK >= 0` | Total apparent energy accumulated in **kVAh**. |
| `avg_apparent_power_va` | `DECIMAL(8, 2)` | NO | `CHECK >= 0` | Average apparent power during period (**VA**). |
| `peak_apparent_power_va`| `DECIMAL(8, 2)` | NO | `CHECK >= 0` | Maximum instantaneous power during period (**VA**). |
| `estimated_cost` | `DECIMAL(10, 2)` | NO | `CHECK >= 0, DEFAULT 0.00` | Estimated cost calculated via active tariff. |
| `period_start` | `TIMESTAMP` | NO | | Beginning of summary window. |
| `period_end` | `TIMESTAMP` | NO | | End of summary window. |
| `created_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Time this rollup was computed. |

* **Constraint:** `uq_device_summary_period UNIQUE (device_id, summary_type, period_start)`

---

### Table 4: `tariffs` (Electricity Pricing Schedules)
Stores electricity rate plans per unit of apparent energy ($\text{kVAh}$) strictly for educational prototype cost estimation.
> **Prototype Billing Disclaimer:** Rates are applied directly to Estimated Apparent Energy ($\text{kVAh}$) assuming near-unity power factor ($\text{PF} \approx 1.0$). This is an educational estimation tool, **not** an official utility billing mechanism.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Unique tariff record identifier. |
| `name` | `VARCHAR(100)` | NO | | Human-readable tariff name. |
| `rate_per_kvah` | `DECIMAL(8, 4)` | NO | `CHECK >= 0` | Pricing rate per kVAh (e.g., `7.5000 INR/kVAh` simulation rate). |
| `currency` | `VARCHAR(10)` | NO | `DEFAULT 'INR'` | Currency designation (`INR`, `USD`, `EUR`). |
| `effective_from`| `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Effective start timestamp. |
| `effective_to` | `TIMESTAMP` | YES | | Expiration timestamp (`NULL` = indefinitely active). |
| `is_active` | `BOOLEAN` | NO | `DEFAULT TRUE` | Active/disabled flag. |
| `created_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Audit creation timestamp. |

* **Index:** `idx_tariffs_active_period (is_active, effective_from)`

---

### Table 5: `calibration_configs` (Sensor Calibration Parameters)
Hardware-specific scaling factors and zero-offsets applied to analog sensors.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Unique calibration profile ID. |
| `device_id` | `BIGINT` | NO | `FOREIGN KEY (devices.id) ON DELETE RESTRICT` | Target device reference. |
| `voltage_calibration_factor` | `DECIMAL(8, 4)` | NO | `CHECK > 0, DEFAULT 1.0000` | Multiplier for voltage RMS calculation. |
| `voltage_offset` | `DECIMAL(6, 2)` | NO | `DEFAULT 0.00` | Zero-offset for voltage sensor (V). |
| `current_sensitivity_mv_per_a` | `DECIMAL(8, 2)` | NO | `CHECK > 0, DEFAULT 100.00` | Sensitivity in mV/A (185.00 for project hardware ACS712-5A). |
| `current_zero_offset` | `DECIMAL(6, 3)` | NO | `DEFAULT 0.000` | Quiescent current zero-offset (A). |
| `is_active` | `BOOLEAN` | NO | `DEFAULT TRUE` | Active configuration flag. |
| `calibrated_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Timestamp of calibration. |
| `notes` | `VARCHAR(255)` | YES | | Audit notes on calibration reference equipment. |

---

### Table 6: `fault_events` (Incident & Anomaly Log)
Audit log tracking electrical safety threshold breaches and communication timeouts.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Unique incident ID. |
| `device_id` | `BIGINT` | NO | `FOREIGN KEY (devices.id) ON DELETE RESTRICT` | Device experiencing the event. |
| `fault_type` | `VARCHAR(50)` | NO | | Fault code (`OVER_VOLTAGE`, `OVER_CURRENT`, etc.). |
| `severity` | `VARCHAR(20)` | NO | `CHECK IN ('INFO', 'WARNING', 'CRITICAL')` | Severity level. |
| `trigger_value` | `DECIMAL(8, 2)` | YES | | Actual observed reading during trip. |
| `threshold_value`| `DECIMAL(8, 2)`| YES | | Configured safety threshold violated. |
| `status` | `VARCHAR(20)` | NO | `CHECK IN ('TRIGGERED', 'ACKNOWLEDGED', 'RESOLVED')` | Lifecycle state (`DEFAULT 'TRIGGERED'`). |
| `message` | `VARCHAR(255)` | YES | | Descriptive incident summary. |
| `triggered_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Occurrence timestamp. |
| `resolved_at` | `TIMESTAMP` | YES | | Timestamp when circuit returned to normal. |

* **Index:** `idx_faults_device_status_triggered (device_id, status, triggered_at DESC)`

---

### Table 7: `users` (Dashboard Authentication & Roles)
Identity and role-based access control records for dashboard users.

| Column Name | Data Type | Nullable | Key / Constraint | Description |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `BIGINT` | NO | `PRIMARY KEY AUTO_INCREMENT` | Unique user surrogate ID. |
| `username` | `VARCHAR(50)` | NO | `UNIQUE` | Unique login username. |
| `email` | `VARCHAR(100)` | NO | `UNIQUE` | User email for alerts and notifications. |
| `password_hash` | `VARCHAR(255)` | NO | | BCrypt hash. (Seeded in locked placeholder state in public migrations; configured locally in Phase 3). |
| `role` | `VARCHAR(20)` | NO | `CHECK IN ('ADMIN', 'OPERATOR', 'VIEWER')` | Access control role (`DEFAULT 'VIEWER'`). |
| `is_active` | `BOOLEAN` | NO | `DEFAULT TRUE` | Soft-deactivation flag (`FALSE` for initial locked bootstrap admin). |
| `created_at` | `TIMESTAMP` | NO | `DEFAULT CURRENT_TIMESTAMP` | Account creation timestamp. |
| `updated_at` | `TIMESTAMP` | NO | `ON UPDATE CURRENT_TIMESTAMP` | Account settings update timestamp. |

---

## 4. Flyway Migrations Inventory

| Migration File | Execution Type | Description |
| :--- | :--- | :--- |
| [`database/migrations/V1__initial_schema.sql`](../database/migrations/V1__initial_schema.sql) | DDL (Structure) | Creates all 7 tables, check constraints, foreign keys, and indexes in referential dependency order. |
| [`database/migrations/V2__seed_initial_data.sql`](../database/migrations/V2__seed_initial_data.sql) | DML (Reference Data) | Seeds prototype device `SEM-ESP32-001`, default ACS712-5A calibration profile (185 mV/A), baseline simulation tariff (7.5000 INR/kVAh), and locked bootstrap `admin` account. |
