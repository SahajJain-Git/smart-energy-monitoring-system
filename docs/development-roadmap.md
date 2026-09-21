# Smart Energy Monitoring System — Development Roadmap

> **Target Device:** `SEM-ESP32-001`  
> **Methodology:** Layered, Outside-In, Test-Driven Learning Roadmap

---

## 1. Overview of the Development Strategy

In modern software engineering, jumping directly into writing code without an architectural foundation leads to brittle systems, coupled dependencies, and difficult debugging sessions. 

This roadmap follows an **outside-in, contract-first, layered development sequence**. Each phase builds strictly upon the verified foundation of the preceding phase.

---

## 2. Phase-by-Phase Breakdown

```
 Phase 1: Architecture & Setup (Current)
   │
   ▼
 Phase 2: Database Design & Flyway Migrations (MySQL)
   │
   ▼
 Phase 3: Spring Boot Backend & REST APIs (Java)
   │
   ▼
 Phase 4: Frontend Dashboard — REST Polling (HTML / CSS / JS / Chart.js)
   │
   ▼
 Phase 5: ESP32 Embedded Firmware & Calibration (C++)
   │
   ▼
 Phase 6: System Integration & End-to-End Verification
   │
   ▼
 Phase 7: Testing, Security Hardening (HTTPS/Auth) & Advanced Features (WebSocket)
```

---

### Phase 1: Architecture & Setup (Current Phase)
- **Objective:** Establish architectural clarity, component boundaries, energy measurement terminology, communication protocols, technology rationales, and directory structures.
- **Key Deliverables:**
  - Technical architecture specification ([`docs/architecture.md`](architecture.md)).
  - Learning roadmap and phase milestones ([`docs/development-roadmap.md`](development-roadmap.md)).
  - Project [`README.md`](../README.md) and repository [`.gitignore`](../.gitignore).
  - Development environment verification report.
- **Verification Gate:**
  - Clear understanding of system data flow, electrical safety warnings, energy terminology ($V$, $A$, $VA$, $\text{kVAh}$), and responsibilities.
  - No hasty code written prematurely.
  - Development environment verified.

---

### Phase 2: Database Design & MySQL Migrations
- **Why this order?**  
  The database represents the foundational "state of truth" for the application. Defining tables, types, relationships, and constraints in SQL clarifies exactly what data our system handles before we write a single line of application code.
- **Key Deliverables:**
  - Design relational schemas for `devices`, `measurements` (storing $V_{\text{RMS}}$, $I_{\text{RMS}}$, $S$, and incremental $\Delta\text{VAh}$), `energy_summaries` (authoritative hourly/daily/monthly $\text{kVAh}$ rollups), `tariffs`, `calibration_configs`, `fault_events`, and `users`.
  - Create Flyway database migration scripts (`V1__init_schema.sql`, `V2__seed_initial_data.sql`).
  - Verify tables, primary keys, foreign key constraints, and indexing in MySQL 8.
- **Verification Gate:**
  - Flyway executes cleanly against MySQL 8 without errors.
  - Initial seed data (including device `SEM-ESP32-001`) queries correctly via SQL commands.

---

### Phase 3: Spring Boot Backend & REST APIs
- **Why this order?**  
  With the database ready, the backend provides the business engine, validation, and REST API endpoints. We can test the entire backend independently using HTTP clients (curl or Postman) with mock JSON payloads before writing any frontend code or flashing any microcontroller.
- **Key Deliverables:**
  - Initialize Spring Boot Maven project with dependencies (Spring Web, Spring Data JPA, MySQL Connector, Validation, Flyway).
  - Configure `application.properties` with database connection pooling (HikariCP).
  - Create layered architecture:
    - Entities (`@Entity`, `@Table`)
    - Repositories (`JpaRepository`)
    - Data Transfer Objects (`DTO` with validation annotations)
    - Services (`@Service` with business logic, telemetry validation, accumulation of validated incremental ΔVAh into authoritative kVAh summaries, tariff cost estimation)
    - Controllers (`@RestController` for device ingestion and dashboard queries)
    - Global Exception Handler (`@ControllerAdvice`)
- **Verification Gate:**
  - `POST /api/v1/measurements` successfully validates and persists mock telemetry to MySQL, returning HTTP 201.
  - `GET /api/v1/measurements/latest` retrieves the most recent record with HTTP 200.

---

### Phase 4: Frontend Web Dashboard (REST Polling)
- **Why this order?**  
  Because the backend REST APIs are active and tested, the frontend web interface can be built against real, predictable HTTP responses.
- **Key Deliverables:**
  - Clean, responsive dashboard layout using semantic HTML5 and modern CSS (Flexbox / CSS Grid).
  - Real-time gauge/card indicators for Voltage ($V$), Current ($A$), Apparent Power ($VA$), and Estimated Apparent Energy ($\text{kVAh}$).
  - Dynamic time-series line charts using Chart.js.
  - Asynchronous HTTP polling engine using modern JavaScript `fetch()` polling at 2–3 second intervals (WebSocket is deferred to Phase 7 as an optimization).
- **Verification Gate:**
  - Opening the web dashboard in a browser displays real-time data fetched from the Spring Boot backend via REST polling and dynamically updates the Chart.js visual canvas.

---

### Phase 5: ESP32 Firmware Development & Sensor Calibration
- **Why this order?**  
  Embedded development involves physical hardware, signal noise, and analog quirks. Developing the firmware after the backend is ready allows us to immediately verify network requests and inspect received data in the database as we calibrate the sensors.
- **Electrical Safety Notice:** Initial testing and ADC calibration must use safe, low-voltage AC sources (e.g. 12V AC step-down transformer) before connecting to any higher voltages.
- **Key Deliverables:**
  - Arduino C/C++ firmware structure: ADC sampling engine on ADC1 (GPIO34 for ACS712, GPIO35 for ZMPT101B).
  - Cycle-synchronized RMS calculation over $50\text{Hz}$ mains waveforms.
  - Local incremental apparent energy ($\Delta\text{VAh}$) calculation.
  - Offset removal and linear calibration routines.
  - Wi-Fi management with auto-reconnect logic.
  - HTTPClient dispatcher sending JSON payloads to the Spring Boot ingestion endpoint over local HTTP.
- **Verification Gate:**
  - ESP32 boots, connects to local Wi-Fi, computes stable RMS readings with no load and with a known resistive test load, and successfully transmits JSON telemetry over HTTP.

---

### Phase 6: System Integration & End-to-End Verification
- **Why this order?**  
  Brings all hardware, firmware, network, backend, database, and frontend layers together in an operational environment.
- **Key Deliverables:**
  - End-to-end telemetry pipeline verification under continuous operation.
  - Network failure handling (Wi-Fi dropouts, server restarts, queuing/reconnect behavior).
  - Verification that backend authoritative rollups correctly accumulate incremental $\Delta\text{VAh}$ into daily/monthly $\text{kVAh}$.
- **Verification Gate:**
  - Turning on an electrical test load causes real-time apparent power updates on the web dashboard within 2 seconds without system crash or data loss.

---

### Phase 7: Testing, Security Hardening & Deployment
- **Why this order?**  
  Once core functionality is verified, the system is hardened for secure, production-grade deployment.
- **Key Deliverables:**
  - Unit tests (JUnit 5, Mockito) and integration tests (`@SpringBootTest`).
  - Production security: Transition from plain HTTP to HTTPS (TLS/SSL).
  - Device authentication: API tokens or pre-shared keys in HTTP headers.
  - User authentication for dashboard access: Spring Security + JWT or session auth.
  - Optional performance enhancement: Upgrade frontend live updates from REST polling to WebSocket push streaming.
- **Verification Gate:**
  - 100% passing automated test suite; unauthorized requests to ingestion or query endpoints are cleanly blocked with HTTP 401/403.
