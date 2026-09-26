/**
 * Smart Energy Monitoring System — Frontend API Client Tests
 * Target Device: SEM-ESP32-001
 *
 * Uses Node.js built-in test runner (node:test) and assertions (node:assert/strict).
 * Tests API functions with mocked fetch, error handling, timeouts, and energy calculation formulas.
 */

const { describe, it, beforeEach, afterEach } = require("node:test");
const assert = require("node:assert/strict");
const {
    API_CONFIG,
    getAuthToken,
    setAuthToken,
    clearAuthToken,
    isAuthenticated,
    login,
    logout,
    apiGet,
    fetchAllDevices,
    fetchDeviceInfo,
    fetchLatestMeasurement,
    fetchMeasurementHistory,
    calculateEnergySummary
} = require("../js/api.js");

describe("Smart Energy Monitoring System - API Client Module", () => {
    let originalFetch;

    beforeEach(() => {
        originalFetch = global.fetch;
    });

    afterEach(() => {
        global.fetch = originalFetch;
    });

    describe("Configuration", () => {
        it("should have correct target device and base URL", () => {
            assert.equal(API_CONFIG.BASE_URL, "http://localhost:8080/api/v1");
            assert.equal(API_CONFIG.DEFAULT_DEVICE_ID, "SEM-ESP32-001");
            assert.equal(API_CONFIG.REQUEST_TIMEOUT_MS, 8000);
        });
    });

    describe("Device Endpoints", () => {
        it("fetchAllDevices() should call /devices and return device array", async () => {
            const mockDevices = [
                {
                    id: 1,
                    deviceId: "SEM-ESP32-001",
                    name: "Smart Energy Monitor Prototype 1",
                    location: "Lab Test Bench A",
                    status: "ACTIVE"
                }
            ];

            let requestedUrl = null;
            let requestedMethod = null;

            global.fetch = async (url, options) => {
                requestedUrl = url;
                requestedMethod = options.method;
                return {
                    ok: true,
                    status: 200,
                    json: async () => mockDevices
                };
            };

            const result = await fetchAllDevices();
            assert.equal(requestedUrl, "http://localhost:8080/api/v1/devices");
            assert.equal(requestedMethod, "GET");
            assert.deepEqual(result, mockDevices);
            assert.equal(result.length, 1);
            assert.equal(result[0].deviceId, "SEM-ESP32-001");
        });

        it("fetchDeviceInfo() should call /devices/{deviceId} with default device SEM-ESP32-001", async () => {
            const mockDevice = {
                id: 1,
                deviceId: "SEM-ESP32-001",
                name: "Smart Energy Monitor Prototype 1",
                location: "Lab Test Bench A",
                status: "ACTIVE"
            };

            let requestedUrl = null;
            global.fetch = async (url) => {
                requestedUrl = url;
                return {
                    ok: true,
                    status: 200,
                    json: async () => mockDevice
                };
            };

            const result = await fetchDeviceInfo();
            assert.equal(requestedUrl, "http://localhost:8080/api/v1/devices/SEM-ESP32-001");
            assert.equal(result.deviceId, "SEM-ESP32-001");
            assert.equal(result.status, "ACTIVE");
        });

        it("fetchDeviceInfo() should URL-encode special characters in deviceId", async () => {
            let requestedUrl = null;
            global.fetch = async (url) => {
                requestedUrl = url;
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ deviceId: "NODE/001 #A" })
                };
            };

            await fetchDeviceInfo("NODE/001 #A");
            assert.equal(requestedUrl, "http://localhost:8080/api/v1/devices/NODE%2F001%20%23A");
        });
    });

    describe("Measurement Endpoints", () => {
        it("fetchLatestMeasurement() should query /measurements/latest with deviceId param", async () => {
            const mockMeasurement = {
                id: 101,
                deviceId: "SEM-ESP32-001",
                voltageRms: 231.42,
                currentRms: 2.150,
                apparentPower: 497.55,
                incrementalApparentEnergy: 0.6910,
                samplingDurationSeconds: 5.0,
                recordedAt: "2026-09-26T10:00:00"
            };

            let requestedUrl = null;
            global.fetch = async (url) => {
                requestedUrl = url;
                return {
                    ok: true,
                    status: 200,
                    json: async () => mockMeasurement
                };
            };

            const result = await fetchLatestMeasurement("SEM-ESP32-001");
            assert.equal(requestedUrl, "http://localhost:8080/api/v1/measurements/latest?deviceId=SEM-ESP32-001");
            assert.equal(result.deviceId, "SEM-ESP32-001");
            assert.equal(result.voltageRms, 231.42);
            assert.equal(result.apparentPower, 497.55);
        });

        it("fetchMeasurementHistory() should include startTime and endTime query parameters", async () => {
            let requestedUrl = null;
            global.fetch = async (url) => {
                requestedUrl = url;
                return {
                    ok: true,
                    status: 200,
                    json: async () => []
                };
            };

            const start = "2026-09-26T00:00:00";
            const end = "2026-09-26T12:00:00";
            await fetchMeasurementHistory("SEM-ESP32-001", start, end);

            const parsed = new URL(requestedUrl);
            assert.equal(parsed.pathname, "/api/v1/measurements/history");
            assert.equal(parsed.searchParams.get("deviceId"), "SEM-ESP32-001");
            assert.equal(parsed.searchParams.get("startTime"), start);
            assert.equal(parsed.searchParams.get("endTime"), end);
        });

        it("fetchMeasurementHistory() without timestamps should only include deviceId", async () => {
            let requestedUrl = null;
            global.fetch = async (url) => {
                requestedUrl = url;
                return {
                    ok: true,
                    status: 200,
                    json: async () => []
                };
            };

            await fetchMeasurementHistory("SEM-ESP32-001");
            assert.equal(requestedUrl, "http://localhost:8080/api/v1/measurements/history?deviceId=SEM-ESP32-001");
        });
    });

    describe("HTTP Error and Timeout Handling", () => {
        it("should throw formatted Error on HTTP 404 response with JSON body", async () => {
            global.fetch = async () => ({
                ok: false,
                status: 404,
                statusText: "Not Found",
                json: async () => ({ message: "Device not found with identifier: SEM-ESP32-001" })
            });

            await assert.rejects(
                async () => await fetchDeviceInfo("SEM-ESP32-001"),
                (err) => {
                    assert.equal(err.status, 404);
                    assert.match(err.message, /HTTP 404 \(Not Found\): Device not found/);
                    return true;
                }
            );
        });

        it("should throw formatted Error on HTTP 500 without JSON body", async () => {
            global.fetch = async () => ({
                ok: false,
                status: 500,
                statusText: "Internal Server Error",
                json: async () => { throw new Error("Not JSON"); }
            });

            await assert.rejects(
                async () => await apiGet("/devices"),
                (err) => {
                    assert.equal(err.status, 500);
                    assert.match(err.message, /HTTP 500 \(Internal Server Error\)/);
                    return true;
                }
            );
        });

        it("should catch AbortError and throw timeout error message", async () => {
            global.fetch = async () => {
                const abortErr = new Error("The operation was aborted");
                abortErr.name = "AbortError";
                throw abortErr;
            };

            await assert.rejects(
                async () => await apiGet("/devices"),
                (err) => {
                    assert.match(err.message, /Request timed out after 8000 ms/);
                    return true;
                }
            );
        });
    });

    describe("Apparent Energy & Cost Calculations (calculateEnergySummary)", () => {
        it("should return zeros for empty or null history", () => {
            assert.deepEqual(calculateEnergySummary([]), {
                totalVah: 0,
                totalKvah: 0,
                estimatedCostInr: 0,
                windowCount: 0
            });

            assert.deepEqual(calculateEnergySummary(null), {
                totalVah: 0,
                totalKvah: 0,
                estimatedCostInr: 0,
                windowCount: 0
            });

            assert.deepEqual(calculateEnergySummary(undefined), {
                totalVah: 0,
                totalKvah: 0,
                estimatedCostInr: 0,
                windowCount: 0
            });
        });

        it("should correctly accumulate incrementalApparentEnergy across windows", () => {
            // S = 230V * 1A = 230 VA. For 5s window: ΔVAh = 230 * (5/3600) = 0.31944 VAh
            const history = [
                { incrementalApparentEnergy: 100.0 },
                { incrementalApparentEnergy: 250.0 },
                { incrementalApparentEnergy: 150.0 }
            ];

            const summary = calculateEnergySummary(history, 8.00);

            // Total VAh = 100 + 250 + 150 = 500 VAh
            assert.equal(summary.totalVah, 500.0);
            // Total kVAh = 500 / 1000 = 0.5 kVAh
            assert.equal(summary.totalKvah, 0.5);
            // Estimated Cost = 0.5 * 8.00 = 4.00 INR
            assert.equal(summary.estimatedCostInr, 4.00);
            assert.equal(summary.windowCount, 3);
        });

        it("should support custom tariff rate in INR per kVAh", () => {
            const history = [
                { incrementalApparentEnergy: 1000.0 } // 1000 VAh = 1.0 kVAh
            ];

            const rate = 9.50;
            const summary = calculateEnergySummary(history, rate);

            assert.equal(summary.totalVah, 1000.0);
            assert.equal(summary.totalKvah, 1.0);
            assert.equal(summary.estimatedCostInr, 9.50);
        });

        it("should handle string numbers and missing incrementalApparentEnergy gracefully", () => {
            const history = [
                { incrementalApparentEnergy: "120.5" },
                { incrementalApparentEnergy: null },
                { incrementalApparentEnergy: undefined },
                { incrementalApparentEnergy: "invalid" },
                { incrementalApparentEnergy: 79.5 }
            ];

            const summary = calculateEnergySummary(history, 8.00);

            // 120.5 + 0 + 0 + 0 + 79.5 = 200.0 VAh
            assert.equal(summary.totalVah, 200.0);
            assert.equal(summary.totalKvah, 0.2);
            assert.equal(summary.estimatedCostInr, 1.6);
            assert.equal(summary.windowCount, 5);
        });
    });

    describe("JWT Token Management & Authentication Plumbing", () => {
        beforeEach(() => {
            clearAuthToken();
        });

        afterEach(() => {
            clearAuthToken();
        });

        it("should save, retrieve, and clear JWT token in storage", () => {
            assert.equal(getAuthToken(), null);
            assert.equal(isAuthenticated(), false);

            const sampleToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sample.token";
            setAuthToken(sampleToken);

            assert.equal(getAuthToken(), sampleToken);
            assert.equal(isAuthenticated(), true);

            clearAuthToken();
            assert.equal(getAuthToken(), null);
            assert.equal(isAuthenticated(), false);
        });

        it("logout() should clear the stored JWT token", () => {
            setAuthToken("sample.jwt.token");
            assert.equal(isAuthenticated(), true);

            logout();
            assert.equal(getAuthToken(), null);
            assert.equal(isAuthenticated(), false);
        });

        it("login(username, password) should call POST /auth/login and store token upon success", async () => {
            let requestUrl = null;
            let requestBody = null;
            let requestHeaders = null;

            global.fetch = async (url, options) => {
                requestUrl = url;
                requestBody = JSON.parse(options.body);
                requestHeaders = options.headers;
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({
                        username: "admin",
                        role: "ADMIN",
                        token: "mocked.jwt.token.123",
                        message: "Authentication successful"
                    })
                };
            };

            const response = await login("admin", "AdminPassword123!");

            assert.equal(requestUrl, "http://localhost:8080/api/v1/auth/login");
            assert.equal(requestBody.username, "admin");
            assert.equal(requestBody.password, "AdminPassword123!");
            assert.equal(response.token, "mocked.jwt.token.123");
            assert.equal(getAuthToken(), "mocked.jwt.token.123");
            assert.equal(isAuthenticated(), true);
        });

        it("login(username, password) should throw error and not store token on 401 failure", async () => {
            global.fetch = async () => ({
                ok: false,
                status: 401,
                statusText: "Unauthorized",
                json: async () => ({ message: "Invalid username or password" })
            });

            await assert.rejects(
                async () => await login("admin", "WrongPassword!"),
                (err) => {
                    assert.equal(err.status, 401);
                    assert.match(err.message, /Invalid username or password/);
                    return true;
                }
            );

            assert.equal(getAuthToken(), null);
        });

        it("apiGet should attach Authorization: Bearer <token> when token is present", async () => {
            setAuthToken("valid.jwt.bearer.token");
            let sentHeaders = null;

            global.fetch = async (url, options) => {
                sentHeaders = options.headers;
                return {
                    ok: true,
                    status: 200,
                    json: async () => []
                };
            };

            await apiGet("/devices");

            assert.ok(sentHeaders);
            assert.equal(sentHeaders["Authorization"], "Bearer valid.jwt.bearer.token");
            assert.equal(sentHeaders["Accept"], "application/json");
        });

        it("apiGet should NOT attach Authorization header when token is null", async () => {
            clearAuthToken();
            let sentHeaders = null;

            global.fetch = async (url, options) => {
                sentHeaders = options.headers;
                return {
                    ok: true,
                    status: 200,
                    json: async () => []
                };
            };

            await apiGet("/devices");

            assert.ok(sentHeaders);
            assert.equal(sentHeaders["Authorization"], undefined);
        });

        it("apiGet should automatically clear token when receiving HTTP 401 response", async () => {
            setAuthToken("expired.or.invalid.token");
            assert.equal(isAuthenticated(), true);

            global.fetch = async () => ({
                ok: false,
                status: 401,
                statusText: "Unauthorized",
                json: async () => ({ message: "Full authentication is required" })
            });

            await assert.rejects(
                async () => await apiGet("/devices"),
                (err) => {
                    assert.equal(err.status, 401);
                    return true;
                }
            );

            // Token must be wiped upon receiving 401
            assert.equal(getAuthToken(), null);
            assert.equal(isAuthenticated(), false);
        });
    });
});
