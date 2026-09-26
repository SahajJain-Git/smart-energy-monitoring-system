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

/**
 * Internal helper to perform a GET request with timeout and HTTP status validation.
 *
 * @param {string} endpointPath - Relative path starting with '/' (e.g., '/devices/SEM-ESP32-001')
 * @returns {Promise<any>} Parsed JSON response body
 */
async function apiGet(endpointPath) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.REQUEST_TIMEOUT_MS);
    const url = `${API_CONFIG.BASE_URL}${endpointPath}`;

    try {
        const response = await fetch(url, {
            method: "GET",
            headers: {
                "Accept": "application/json"
            },
            signal: controller.signal
        });

        if (!response.ok) {
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

// Expose API functions globally for app.js
window.SmartEnergyApi = {
    API_CONFIG,
    fetchAllDevices,
    fetchDeviceInfo,
    fetchLatestMeasurement,
    fetchMeasurementHistory,
    calculateEnergySummary
};
