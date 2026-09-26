/**
 * Smart Energy Monitoring System — Frontend API Client Module
 * Target Device: SEM-ESP32-001
 *
 * Strictly communicates ONLY with the Spring Boot REST API (:8080).
 * Never connects directly to MySQL or embeds credentials.
 */

const API_CONFIG = {
    BASE_URL: "http://localhost:8080/api/v1",
    DEFAULT_DEVICE_ID: "SEM-ESP32-001",
    REQUEST_TIMEOUT_MS: 8000
};

const AUTH_TOKEN_KEY = "smart_energy_jwt_token";
let inMemoryToken = null; // In-memory fallback for environments without sessionStorage (e.g. Node.js test runner)

/**
 * Retrieve the current JWT token from sessionStorage or memory.
 *
 * @returns {string|null} JWT token string or null
 */
function getAuthToken() {
    try {
        if (typeof sessionStorage !== "undefined") {
            const stored = sessionStorage.getItem(AUTH_TOKEN_KEY);
            if (stored) return stored;
        }
    } catch (_) {}
    return inMemoryToken;
}

/**
 * Store the JWT token in sessionStorage and memory.
 *
 * @param {string|null} token - JWT token string or null to clear
 */
function setAuthToken(token) {
    try {
        if (typeof sessionStorage !== "undefined") {
            if (token) {
                sessionStorage.setItem(AUTH_TOKEN_KEY, token);
            } else {
                sessionStorage.removeItem(AUTH_TOKEN_KEY);
            }
        }
    } catch (_) {}
    inMemoryToken = token;
}

/**
 * Remove stored JWT token from session storage and memory.
 */
function clearAuthToken() {
    setAuthToken(null);
}

/**
 * Check if a JWT token is currently available.
 *
 * @returns {boolean}
 */
function isAuthenticated() {
    return !!getAuthToken();
}

/**
 * Authenticate with the backend using username and password to obtain a JWT token.
 * Maps to: POST /api/v1/auth/login
 *
 * @param {string} username
 * @param {string} password
 * @returns {Promise<Object>} AuthResponse ({ username, role, token, message })
 */
async function login(username, password) {
    const url = `${API_CONFIG.BASE_URL}/auth/login`;

    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Accept": "application/json"
        },
        body: JSON.stringify({ username, password })
    });

    if (!response.ok) {
        let errorDetail = `HTTP ${response.status} (${response.statusText})`;
        try {
            const errorBody = await response.json();
            if (errorBody && errorBody.message) {
                errorDetail = errorBody.message;
            }
        } catch (_) {}
        const error = new Error(errorDetail);
        error.status = response.status;
        throw error;
    }

    const data = await response.json();
    if (data && data.token) {
        setAuthToken(data.token);
    }
    return data;
}

/**
 * Clear the authentication token from session storage and log out.
 */
function logout() {
    clearAuthToken();
}

/**
 * Internal helper to perform a GET request with timeout and HTTP status validation.
 * Automatically attaches Authorization: Bearer <JWT> if an authenticated token is present.
 *
 * @param {string} endpointPath - Relative path starting with '/' (e.g., '/devices/SEM-ESP32-001')
 * @returns {Promise<any>} Parsed JSON response body
 */
async function apiGet(endpointPath) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.REQUEST_TIMEOUT_MS);
    const url = `${API_CONFIG.BASE_URL}${endpointPath}`;

    const headers = {
        "Accept": "application/json"
    };

    const token = getAuthToken();
    if (token) {
        headers["Authorization"] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(url, {
            method: "GET",
            headers: headers,
            signal: controller.signal
        });

        if (!response.ok) {
            if (response.status === 401) {
                clearAuthToken(); // Clear expired or invalid token
            }

            let errorDetail = `HTTP ${response.status} (${response.statusText})`;
            try {
                const errorBody = await response.json();
                if (errorBody && errorBody.message) {
                    errorDetail = `${errorDetail}: ${errorBody.message}`;
                }
            } catch (_) {
                // Ignore JSON parse error on non-JSON error responses
            }
            const error = new Error(errorDetail);
            error.status = response.status;
            throw error;
        }

        return await response.json();
    } catch (err) {
        if (err.name === "AbortError") {
            throw new Error(`Request timed out after ${API_CONFIG.REQUEST_TIMEOUT_MS} ms (${url})`);
        }
        throw err;
    } finally {
        clearTimeout(timeoutId);
    }
}

/**
 * Fetch all registered energy monitoring devices.
 * Maps to: GET /api/v1/devices
 *
 * @returns {Promise<Array<Object>>} List of DeviceResponse objects
 */
async function fetchAllDevices() {
    return await apiGet("/devices");
}

/**
 * Fetch metadata and operational status for a specific device.
 * Maps to: GET /api/v1/devices/{deviceId}
 *
 * @param {string} [deviceId="SEM-ESP32-001"] - Unique hardware identifier
 * @returns {Promise<Object>} DeviceResponse ({ id, deviceId, name, location, status, createdAt, updatedAt })
 */
async function fetchDeviceInfo(deviceId = API_CONFIG.DEFAULT_DEVICE_ID) {
    const encodedId = encodeURIComponent(deviceId);
    return await apiGet(`/devices/${encodedId}`);
}

/**
 * Fetch the single most recent telemetry measurement for a device.
 * Maps to: GET /api/v1/measurements/latest?deviceId={deviceId}
 *
 * @param {string} [deviceId="SEM-ESP32-001"] - Unique hardware identifier
 * @returns {Promise<Object>} MeasurementResponse ({ id, deviceId, voltageRms, currentRms, apparentPower, incrementalApparentEnergy, samplingDurationSeconds, recordedAt })
 */
async function fetchLatestMeasurement(deviceId = API_CONFIG.DEFAULT_DEVICE_ID) {
    const params = new URLSearchParams({ deviceId });
    return await apiGet(`/measurements/latest?${params.toString()}`);
}

/**
 * Fetch chronological historical measurements for a device within a time window.
 * Maps to: GET /api/v1/measurements/history?deviceId={deviceId}&startTime={ISO}&endTime={ISO}
 *
 * @param {string} [deviceId="SEM-ESP32-001"] - Unique hardware identifier
 * @param {string} [startTime] - Optional ISO-8601 start timestamp
 * @param {string} [endTime] - Optional ISO-8601 end timestamp
 * @returns {Promise<Array<Object>>} Chronological list of MeasurementResponse objects
 */
async function fetchMeasurementHistory(deviceId = API_CONFIG.DEFAULT_DEVICE_ID, startTime = null, endTime = null) {
    const params = new URLSearchParams({ deviceId });
    if (startTime) {
        params.append("startTime", startTime);
    }
    if (endTime) {
        params.append("endTime", endTime);
    }
    return await apiGet(`/measurements/history?${params.toString()}`);
}

/**
 * Calculate aggregated apparent energy (kVAh) and estimated cost (INR)
 * from historical measurement windows.
 *
 * Strictly Apparent Energy formulas:
 *   Total VAh = sum of incrementalApparentEnergy (VAh) across windows
 *   Total kVAh = Total VAh / 1000.0
 *   Estimated Cost (INR) = Total kVAh * tariffRatePerKvah
 *
 * @param {Array<Object>} historyList - Array of MeasurementResponse records
 * @param {number} [tariffRateInr=8.00] - Tariff rate in INR per kVAh
 * @returns {{ totalVah: number, totalKvah: number, estimatedCostInr: number, windowCount: number }}
 */
function calculateEnergySummary(historyList, tariffRateInr = 8.00) {
    if (!Array.isArray(historyList) || historyList.length === 0) {
        return {
            totalVah: 0,
            totalKvah: 0,
            estimatedCostInr: 0,
            windowCount: 0
        };
    }

    const totalVah = historyList.reduce((acc, record) => {
        const deltaVah = Number(record.incrementalApparentEnergy) || 0;
        return acc + deltaVah;
    }, 0);

    const totalKvah = totalVah / 1000.0;
    const estimatedCostInr = totalKvah * tariffRateInr;

    return {
        totalVah,
        totalKvah,
        estimatedCostInr,
        windowCount: historyList.length
    };
}

// Expose API functions globally for app.js (browser)
if (typeof window !== "undefined") {
    window.SmartEnergyApi = {
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
    };
}

// CommonJS export for Node.js automated test runner
if (typeof module !== "undefined" && module.exports) {
    module.exports = {
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
    };
}
