# Smart Energy Monitoring System

> **Device Identifier:** `SEM-ESP32-001`  
> **Status:** Phase 2 — Database Design & Migrations Complete  
> **Target Environment:** IoT Energy Prototype (Research & Learning)

---

## 1. Project Overview

The **Smart Energy Monitoring System** is an end-to-end IoT platform designed to collect electrical measurements from AC electrical circuits, process and calibrate the readings, store historical records reliably, and display real-time and historical power consumption data on an interactive dashboard.

This project is built from the ground up as a **learning and research platform** to master:
- **Embedded C/C++ firmware** on the ESP32 microcontroller with analog sensor sampling.
- **Enterprise backend engineering** with Java, Spring Boot, Spring Data JPA, and RESTful APIs.
- **Relational database design** and schema versioning with MySQL and Flyway.
- **Frontend web engineering** with HTML5, modern CSS, JavaScript (Fetch API polling), and Chart.js.
- **System integration and IoT protocols** connecting microcontrollers to distributed backends over Wi-Fi and HTTP.

---

## 2. Electrical Safety Warning

> [!CAUTION]
> **ELECTRICAL HAZARD & SAFETY NOTICE:**  
> This system is designed to monitor Alternating Current (AC) circuits operating at **hazardous mains voltages (110V – 240V AC)**.
> - Mains AC voltage presents severe risks of **lethal electric shock, electrocution, electrical fires, and catastrophic component destruction**.
> - This project is strictly an **educational and research prototype**. It is **not** a certified, industrially isolated, or revenue-grade utility meter.
> - **No unsafe mains wiring instructions are provided in this repository.**
> - All initial hardware prototyping and firmware testing should be conducted using **safe, low-voltage AC sources** (such as an isolated 12V AC step-down transformer) or benchtop signal generators before any connection to line voltages.
> - Never touch any exposed sensor pins, jumpers, or microcontroller terminals while energized.

---

## 3. Prototype & Measurement Disclaimers

> [!IMPORTANT]
> ### Apparent Power vs. True Active Power
> The current hardware configuration utilizes:
> - **ACS712** Current Sensor (Hall-effect analog sensor)
> - **ZMPT101B** Voltage Sensor (Transformer-based analog module)
> 
> Because the current prototype measures Voltage RMS ($V_{\text{RMS}}$) and Current RMS ($I_{\text{RMS}}$) without sampling the simultaneous phase angle ($\phi$) or dynamic power factor ($\cos\phi$) between the voltage and current waveforms:
> - The calculated power is strictly **Apparent Power ($S = V_{\text{RMS}} \times I_{\text{RMS}}$)**, measured in **Volt-Amperes (VA)**, not True/Active Power ($P = V \times I \times \cos\phi$) in Watts (W).
> - Energy accumulated over time is strictly **Estimated Apparent Energy**, measured in **Volt-Ampere hours (VAh)** or **kilo-Volt-Ampere hours (kVAh)**, not actual active energy in kilowatt-hours (kWh).
> 
> ### Estimated Cost & Tariff Assumptions
> - Any estimated billing cost calculated by the system applies a nominal tariff rate directly to the **Estimated Apparent Energy (kVAh)**, or assumes an ideal near-unity power factor ($\text{PF} \approx 1.0$, as seen in purely resistive loads).
> - This calculation is strictly for educational estimation and **must never be treated as utility-meter billing accuracy**.

---

## 4. Key Features

- **Continuous AC Waveform Sampling:** High-frequency sampling of analog voltage and current waveforms using ESP32 ADC1 (GPIO34, GPIO35).
- **On-Chip RMS Computation:** Cycle-synchronized discrete numerical calculation of $V_{\text{RMS}}$ (Volts) and $I_{\text{RMS}}$ (Amperes).
- **Local Incremental Apparent Energy:** ESP32 computes incremental apparent energy ($\Delta \text{VAh}$) across each reporting period.
- **RESTful Telemetry Ingestion:** Structured JSON telemetry transmitted periodically via HTTP POST (plain HTTP for local development; HTTPS + token authentication for production).
- **Authoritative Backend Processing:** Spring Boot validates incoming data, enforces safety thresholds, persists readings, aggregates hourly/daily/monthly summaries, and applies tariff models for cost estimation.
- **Persistent Relational Storage:** Time-stamped measurements, device registries, energy summaries, and tariff configurations in MySQL 8.
- **Interactive Visual Dashboard:** Real-time metrics display and dynamic consumption charts rendered with Chart.js using periodic REST API polling (WebSocket reserved as a future enhancement).
- **Deterministic Database Migrations:** Version-controlled, reproducible SQL migration scripts managed by Flyway.

---

## 5. Architectural Division of Energy Responsibilities

To avoid competing or conflicting calculations, the system enforces a strict hierarchy:

| Responsibility | Component | Details |
| :--- | :--- | :--- |
| **Instantaneous Electrical Metrics** | **ESP32** | Computes $V_{\text{RMS}}$ (V), $I_{\text{RMS}}$ (A), and Apparent Power $S$ (VA). |
| **Local Incremental Apparent Energy** | **ESP32** | Integrates apparent power across the local telemetry interval ($\Delta \text{VAh} = S \times \Delta t / 3600$). |
| **Validation & Persistence** | **Backend** | Validates incoming payloads and persists raw measurement records into MySQL. |
| **Authoritative Aggregation** | **Backend** | Single source of truth for rolling hourly, daily, and monthly apparent energy summaries ($\text{kVAh}$). |
| **Cost Estimation & Alerts** | **Backend** | Evaluates utility tariffs against aggregated apparent energy and triggers fault/overload alerts. |

---

## 6. High-Level System Architecture

```
 ┌──────────────────────────────────────┐
 │               ESP32                  │
 │                                      │
 │  ACS712 (GPIO34)  → Current RMS (A) │
 │  ZMPT101B (GPIO35)→ Voltage RMS (V) │
 │                                      │
 │  Apparent Power: S = Vrms * Irms(VA) │
 │  Incremental Apparent Energy (VAh)   │
 └──────────────────┬───────────────────┘
                    │
                 Wi-Fi
                    │
                 HTTP POST (JSON)
                 [Plain HTTP: Local Dev / Prototype]
                 [HTTPS + Auth: Production Deployment]
                    │
                    ▼
 ┌──────────────────────────────────────┐
 │         Spring Boot Backend          │
 │                                      │
 │ REST Ingestion Controller            │
 │ Validation & Business Logic          │
 │ Authoritative Aggregation (kVAh)     │
 │ Tariff Cost Estimation & Faults      │
 │ Spring Data JPA / Hibernate          │
 └──────────────────┬───────────────────┘
                    │
                    │ JDBC / SQL
                    ▼
 ┌──────────────────────────────────────┐
 │            MySQL Database            │
 │                                      │
 │ Measurements, Devices, Summaries     │
 └──────────────────────────────────────┘
                    ▲
                    │
                 REST API Polling (JSON)
                 [WebSocket: Future Enhancement]
                    │
 ┌──────────────────┴───────────────────┐
 │               Frontend               │
 │                                      │
 │ HTML5 / CSS3                         │
 │ JavaScript (Fetch API Polling)       │
 │ Chart.js (V, A, VA, kVAh Trends)     │
 └──────────────────────────────────────┘
```

---

## 7. Technology Stack

| Domain | Technology | Problem Solved in This Project |
| :--- | :--- | :--- |
| **Embedded Hardware** | ESP32 (NodeMCU-32S / WROOM) | High-speed dual-core MCU handling microsecond ADC sampling and Wi-Fi networking. |
| **Sensors** | ACS712 (Current), ZMPT101B (Voltage) | Analog transducers connected to ADC1 (GPIO34, GPIO35). |
| **Embedded Software**| C/C++ (Arduino Framework) | Real-time sampling loops, RMS calculation, JSON serialization, HTTP client. |
| **Backend Language** | Java 25 (LTS) | Strongly-typed, object-oriented language for robust enterprise backend services. |
| **Backend Framework**| Spring Boot | Dependency injection, embedded web server, validation, and REST API controllers. |
| **ORM / Data Access**| Spring Data JPA / Hibernate | Object-Relational Mapping to eliminate boilerplate SQL and handle entity persistence. |
| **Database** | MySQL 8.0 Community Server | ACID-compliant relational storage for telemetry, device metadata, and aggregations. |
| **Schema Migration** | Flyway | Version-controlled, reproducible SQL migration scripts across environments. |
| **Frontend Core** | HTML5, CSS3, Modern JavaScript | Lightweight, responsive user interface communicating via asynchronous Fetch API polling. |
| **Data Visualization**| Chart.js | Interactive live and historical line charts for voltage, current, apparent power, and apparent energy. |
| **Version Control** | Git & GitHub | Distributed version control, revision history, and collaborative workflow. |

---

## 8. Repository Layout

```
smart-energy-monitoring-system/
│
├── backend/          # Spring Boot application (Maven, Java source code, application properties)
├── frontend/         # Web dashboard (HTML, CSS, JavaScript, assets)
├── esp32/            # ESP32 firmware source code, libraries, and calibration tools
├── database/         # Flyway SQL migration scripts and seed data
├── docs/             # Technical architecture guides, API contracts, and roadmap
├── .gitignore        # Comprehensive git ignore rules for Java, IDEs, and ESP32
└── README.md         # Project overview and guide (this document)
```

---

## 9. Development Roadmap

- **Phase 1:** Architecture & Setup *(Completed)*
- **Phase 2:** Database Design & MySQL Migrations *(Completed)*
- **Phase 3:** Spring Boot Backend Implementation *(Upcoming)*
- **Phase 4:** Frontend Dashboard Development (REST Polling)
- **Phase 5:** ESP32 Firmware Development & Calibration
- **Phase 6:** End-to-End System Integration
- **Phase 7:** Testing, Security Hardening & Deployment (HTTPS, Auth, optional WebSocket)

See [`docs/development-roadmap.md`](docs/development-roadmap.md) for full phase breakdown and verification gates.
