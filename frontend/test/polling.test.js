/**
 * Smart Energy Monitoring System — Frontend Polling & State Management Tests
 * Target Device: SEM-ESP32-001
 *
 * Verifies polling lifecycle, interval configuration, overlap prevention,
 * and error/empty/online state transitions in the dashboard controller.
 */

const { describe, it, beforeEach, afterEach } = require("node:test");
const assert = require("node:assert/strict");
const api = require("../js/api.js");
const app = require("../js/app.js");

// Mock DOM elements
function createMockElement(id = "") {
    return {
        id,
        textContent: "",
        className: "",
        style: {},
        disabled: false
    };
}

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

describe("Smart Energy Monitoring System - Polling & Lifecycle State", () => {
    let mockDoc;

    beforeEach(() => {
        mockDoc = createMockDocument();
        global.document = mockDoc;
        global.window = {
            SmartEnergyApi: {
                fetchDeviceInfo: async () => ({
                    deviceId: "SEM-ESP32-001",
                    name: "Smart Energy Monitor Prototype 1",
                    location: "Lab Test Bench A",
                    status: "ACTIVE"
                }),
                fetchLatestMeasurement: async () => ({
                    deviceId: "SEM-ESP32-001",
                    voltageRms: 230.5,
                    currentRms: 3.12,
                    apparentPower: 719.16,
                    incrementalApparentEnergy: 0.9988,
                    samplingDurationSeconds: 5.0,
                    recordedAt: "2026-09-26T12:00:00"
                }),
                fetchMeasurementHistory: async () => [
                    {
                        deviceId: "SEM-ESP32-001",
                        voltageRms: 230.5,
                        currentRms: 3.12,
                        apparentPower: 719.16,
                        incrementalApparentEnergy: 0.9988,
                        samplingDurationSeconds: 5.0,
                        recordedAt: "2026-09-26T12:00:00"
                    }
                ],
                calculateEnergySummary: api.calculateEnergySummary
            }
        };
        app.stopPolling();
        app.setFetchInProgress(false);
    });

    afterEach(() => {
        app.stopPolling();
        app.setFetchInProgress(false);
    });

    describe("Polling Configuration", () => {
        it("should have 5000ms polling interval matching ESP32 telemetry cadence", () => {
            assert.equal(app.POLLING_CONFIG.INTERVAL_MS, 5000);
            assert.equal(app.POLLING_CONFIG.ENABLED, true);
        });
    });

    describe("Polling Lifecycle", () => {
        it("should activate polling with startPolling() and deactivate with stopPolling()", () => {
            assert.equal(app.isPollingActive(), false);

            app.startPolling();
            assert.equal(app.isPollingActive(), true);

            app.stopPolling();
            assert.equal(app.isPollingActive(), false);
        });

        it("calling startPolling() when already active should not duplicate", () => {
            app.startPolling();
            assert.equal(app.isPollingActive(), true);

            // Second call should return early
            app.startPolling();
            assert.equal(app.isPollingActive(), true);

            app.stopPolling();
        });
    });

    describe("Overlap Guard (isFetchInProgress)", () => {
        it("should skip executePollCycle if a previous fetch is still pending", async () => {
            let apiCallCount = 0;
            global.window.SmartEnergyApi.fetchDeviceInfo = async () => {
                apiCallCount++;
                return { deviceId: "SEM-ESP32-001" };
            };

            // Simulate fetch currently in progress
            app.setFetchInProgress(true);

            await app.executePollCycle();

            // API should not have been called because overlap guard blocked it
            assert.equal(apiCallCount, 0);

            // Reset fetchInProgress
            app.setFetchInProgress(false);
            await app.executePollCycle();
            assert.equal(apiCallCount, 1);
        });
    });

    describe("refreshDashboard State Transitions", () => {
        it("should set online badge and OK banner when API calls succeed with data", async () => {
            await app.refreshDashboard();

            const badge = mockDoc.getElementById("connection-badge");
            assert.equal(badge.className, "status-badge status-online");
            assert.equal(badge.textContent, "Online");

            const banner = mockDoc.getElementById("status-banner");
            assert.equal(banner.className, "status-banner banner-ok");
            assert.ok(banner.textContent.includes("Connected to SEM-ESP32-001"));
            assert.ok(banner.textContent.includes("Active telemetry stream"));
        });

        it("should set warning banner when device is registered but no measurements exist", async () => {
            global.window.SmartEnergyApi.fetchLatestMeasurement = async () => null;
            global.window.SmartEnergyApi.fetchMeasurementHistory = async () => [];

            await app.refreshDashboard();

            const badge = mockDoc.getElementById("connection-badge");
            assert.equal(badge.className, "status-badge status-online");

            const banner = mockDoc.getElementById("status-banner");
            assert.equal(banner.className, "status-banner banner-warning");
            assert.ok(banner.textContent.includes("no telemetry has been recorded yet"));
        });

        it("should set error badge and banner when API throws network or server error", async () => {
            global.window.SmartEnergyApi.fetchDeviceInfo = async () => {
                throw new Error("Failed to fetch");
            };

            await app.refreshDashboard();

            const badge = mockDoc.getElementById("connection-badge");
            assert.equal(badge.className, "status-badge status-error");
            assert.equal(badge.textContent, "API Offline");

            const banner = mockDoc.getElementById("status-banner");
            assert.equal(banner.className, "status-banner banner-error");
            assert.ok(banner.textContent.includes("Backend Unreachable"));
            assert.ok(banner.textContent.includes("Failed to fetch"));

            const refreshBtn = mockDoc.getElementById("refresh-btn");
            assert.equal(refreshBtn.textContent, "Retry Connection");
            assert.equal(refreshBtn.disabled, false);
        });

        it("should set Auth Required badge and warning banner when API returns HTTP 401 Unauthorized", async () => {
            const authError = new Error("Full authentication is required");
            authError.status = 401;

            global.window.SmartEnergyApi.fetchDeviceInfo = async () => {
                throw authError;
            };

            await app.refreshDashboard();

            const badge = mockDoc.getElementById("connection-badge");
            assert.equal(badge.className, "status-badge status-error");
            assert.equal(badge.textContent, "Auth Required");

            const banner = mockDoc.getElementById("status-banner");
            assert.equal(banner.className, "status-banner banner-warning");
            assert.ok(banner.textContent.includes("Authentication required or session expired"));

            const refreshBtn = mockDoc.getElementById("refresh-btn");
            assert.equal(refreshBtn.textContent, "Log In Required");
        });
    });
});
