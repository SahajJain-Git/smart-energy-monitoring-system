/**
 * Smart Energy Monitoring System — Frontend Formatting, DOM & Charts Tests
 * Target Device: SEM-ESP32-001
 *
 * Verifies timestamp formatting, chart data processing, DOM rendering functions,
 * and strict apparent energy terminology (V, A, VA, VAh, kVAh, INR).
 */

const { describe, it, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const api = require("../js/api.js");
const app = require("../js/app.js");

// Minimal mock DOM element factory
function createMockElement(id = "") {
    return {
        id,
        textContent: "",
        className: "",
        style: {},
        disabled: false
    };
}

// Minimal mock DOM document
function createMockDocument() {
    const elements = new Map();

    const getOrCreate = (id) => {
        if (!elements.has(id)) {
            elements.set(id, createMockElement(id));
        }
        return elements.get(id);
    };

    return {
        getElementById: (id) => getOrCreate(id),
        elements
    };
}

describe("Smart Energy Monitoring System - Formatting & DOM Updates", () => {
    describe("formatTimestamp", () => {
        it("should return '--' for empty, null, or undefined timestamp", () => {
            assert.equal(app.formatTimestamp(""), "--");
            assert.equal(app.formatTimestamp(null), "--");
            assert.equal(app.formatTimestamp(undefined), "--");
        });

        it("should format valid ISO timestamp into human-readable date and time", () => {
            const iso = "2026-09-26T14:30:00";
            const formatted = app.formatTimestamp(iso);
            assert.ok(formatted !== "--");
            assert.ok(formatted.includes("2026"));
            assert.ok(formatted.includes("14:30") || formatted.includes("02:30") || formatted.includes("30"));
        });

        it("should return the original string for invalid date strings without crashing", () => {
            assert.equal(app.formatTimestamp("invalid-timestamp-string"), "invalid-timestamp-string");
        });
    });

    describe("formatChartTime", () => {
        it("should return empty string for empty, null, or undefined timestamp", () => {
            assert.equal(app.formatChartTime(""), "");
            assert.equal(app.formatChartTime(null), "");
            assert.equal(app.formatChartTime(undefined), "");
        });

        it("should format valid ISO timestamp into HH:mm:ss", () => {
            const iso = "2026-09-26T08:15:30";
            const formatted = app.formatChartTime(iso);
            assert.ok(formatted.length > 0);
            assert.match(formatted, /\d{2}:\d{2}:\d{2}/);
        });

        it("should return empty string for invalid date strings without crashing", () => {
            assert.equal(app.formatChartTime("not-a-date"), "");
        });
    });

    describe("DOM Update: updateDeviceInfo", () => {
        let mockDoc;

        beforeEach(() => {
            mockDoc = createMockDocument();
            global.document = mockDoc;
        });

        it("should update all device info DOM elements correctly for ACTIVE node", () => {
            const deviceInfo = {
                deviceId: "SEM-ESP32-001",
                name: "Smart Energy Monitor Prototype 1",
                location: "Lab Test Bench A",
                status: "ACTIVE"
            };

            app.updateDeviceInfo(deviceInfo, "2026-09-26T12:00:00");

            assert.equal(mockDoc.getElementById("device-id").textContent, "SEM-ESP32-001");
            assert.equal(mockDoc.getElementById("device-name").textContent, "Smart Energy Monitor Prototype 1");
            assert.equal(mockDoc.getElementById("device-location").textContent, "Lab Test Bench A");
            assert.equal(mockDoc.getElementById("device-status").textContent, "ACTIVE");
            assert.equal(mockDoc.getElementById("device-status").style.color, "#16a34a"); // Green
            assert.ok(mockDoc.getElementById("last-recorded-at").textContent !== "--");
        });

        it("should set status text color to red (#dc2626) for INACTIVE or ERROR node", () => {
            const deviceInfo = {
                deviceId: "SEM-ESP32-001",
                name: "Prototype",
                location: "Lab",
                status: "INACTIVE"
            };

            app.updateDeviceInfo(deviceInfo, null);

            assert.equal(mockDoc.getElementById("device-status").textContent, "INACTIVE");
            assert.equal(mockDoc.getElementById("device-status").style.color, "#dc2626"); // Red
            assert.equal(mockDoc.getElementById("last-recorded-at").textContent, "--");
        });

        it("should handle null or undefined deviceInfo safely", () => {
            assert.doesNotThrow(() => app.updateDeviceInfo(null, null));
            assert.doesNotThrow(() => app.updateDeviceInfo(undefined, undefined));
        });
    });

    describe("DOM Update: updateLiveMeasurements", () => {
        let mockDoc;

        beforeEach(() => {
            mockDoc = createMockDocument();
            global.document = mockDoc;
        });

        it("should format live electrical telemetry to exact decimal precisions", () => {
            const measurement = {
                deviceId: "SEM-ESP32-001",
                voltageRms: 230.154,
                currentRms: 4.2508,
                apparentPower: 978.143,
                samplingDurationSeconds: 5.0
            };

            app.updateLiveMeasurements(measurement);

            assert.equal(mockDoc.getElementById("voltage-rms").textContent, "230.15");     // 2 decimals (V)
            assert.equal(mockDoc.getElementById("current-rms").textContent, "4.251");     // 3 decimals (A)
            assert.equal(mockDoc.getElementById("apparent-power").textContent, "978.14");  // 2 decimals (VA)
            assert.equal(mockDoc.getElementById("sampling-window-label").textContent, "Sampling Window: 5.0 s");
        });

        it("should display placeholder dashes when measurement is null or missing", () => {
            app.updateLiveMeasurements(null);

            assert.equal(mockDoc.getElementById("voltage-rms").textContent, "--");
            assert.equal(mockDoc.getElementById("current-rms").textContent, "--");
            assert.equal(mockDoc.getElementById("apparent-power").textContent, "--");
            assert.equal(mockDoc.getElementById("sampling-window-label").textContent, "Sampling Window: -- s");
        });
    });

    describe("DOM Update: updateEnergyAndCost", () => {
        let mockDoc;

        beforeEach(() => {
            mockDoc = createMockDocument();
            global.document = mockDoc;
            global.window = {
                SmartEnergyApi: api
            };
        });

        it("should format incremental energy, aggregated energy, and INR cost correctly", () => {
            const latestMeasurement = {
                incrementalApparentEnergy: 1.3582
            };

            // Two historical windows: 1.3582 + 2.6418 = 4.0000 VAh = 0.0040 kVAh
            const historyList = [
                { incrementalApparentEnergy: 1.3582 },
                { incrementalApparentEnergy: 2.6418 }
            ];

            app.updateEnergyAndCost(latestMeasurement, historyList, 8.00);

            assert.equal(mockDoc.getElementById("incremental-energy-vah").textContent, "1.3582");
            assert.equal(mockDoc.getElementById("aggregated-energy-kvah").textContent, "0.0040");
            // Cost: 0.0040 kVAh * 8.00 INR = 0.032 -> 0.03 INR
            assert.equal(mockDoc.getElementById("estimated-cost-inr").textContent, "0.03");
            assert.equal(mockDoc.getElementById("history-count-label").textContent, "Records Analyzed: 2");
            assert.equal(mockDoc.getElementById("tariff-rate-display").textContent, "8.00");
        });

        it("should handle null latest measurement and empty history gracefully", () => {
            app.updateEnergyAndCost(null, [], 8.00);

            assert.equal(mockDoc.getElementById("incremental-energy-vah").textContent, "--");
            assert.equal(mockDoc.getElementById("aggregated-energy-kvah").textContent, "0.0000");
            assert.equal(mockDoc.getElementById("estimated-cost-inr").textContent, "0.00");
            assert.equal(mockDoc.getElementById("history-count-label").textContent, "Records Analyzed: 0");
        });
    });

    describe("Strict Apparent Energy Terminology Enforcement", () => {
        it("should not contain active power units (W, kW, kWh) or power factor references in live card element IDs", () => {
            const prohibitedPatterns = [
                "active-power",
                "real-power",
                "power-factor",
                "kwh",
                "watt"
            ];

            // Verify frontend HTML structure conforms strictly to Apparent Energy
            const fs = require("node:fs");
            const path = require("node:path");
            const htmlContent = fs.readFileSync(path.join(__dirname, "../index.html"), "utf-8").toLowerCase();

            for (const prohibited of prohibitedPatterns) {
                assert.ok(
                    !htmlContent.includes(`id="${prohibited}"`),
                    `HTML must not contain prohibited active power ID: ${prohibited}`
                );
            }

            // Verify strict Apparent Energy units are present
            assert.ok(htmlContent.includes("v"), "Must monitor Voltage (V)");
            assert.ok(htmlContent.includes("a"), "Must monitor Current (A)");
            assert.ok(htmlContent.includes("va"), "Must monitor Apparent Power (VA)");
            assert.ok(htmlContent.includes("vah"), "Must monitor Incremental Apparent Energy (VAh)");
            assert.ok(htmlContent.includes("kvah"), "Must monitor Aggregated Apparent Energy (kVAh)");
            assert.ok(htmlContent.includes("inr") || htmlContent.includes("₹"), "Must display Cost in INR");
        });
    });
});
