/**
 * Smart Energy Monitoring System — Main Application Controller
 * Target Device: SEM-ESP32-001
 *
 * Phase 5 Implementation:
 * - Step 6: API Communication Module integration
 * - Step 7: Display Device Information (Device ID, Name, Location, Status, Last Recorded)
 * - (Steps 8-12 will add Live Cards, Energy/Cost, Charts, Polling, and Error States)
 */

/**
 * Format an ISO 8601 timestamp string into a readable Indian/local time string.
 * Example: "2026-09-26T00:49:34" -> "26 Sep 2026, 00:49:34"
 *
 * @param {string} isoString - ISO formatted timestamp string
 * @returns {string} Human-readable date and time
 */
function formatTimestamp(isoString) {
    if (!isoString) return "--";
    try {
        const date = new Date(isoString);
        if (isNaN(date.getTime())) return isoString;
        return date.toLocaleString("en-IN", {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false
        });
    } catch (_) {
        return isoString;
    }
}

/**
 * Update the Hardware Node Information panel in the DOM.
 * Target Elements:
 *   - #device-id: e.g. "SEM-ESP32-001"
 *   - #device-name: e.g. "Smart Energy Monitor Prototype 1"
 *   - #device-location: e.g. "Lab Test Bench A"
 *   - #device-status: e.g. "ACTIVE"
 *   - #last-recorded-at: e.g. "26 Sep 2026, 00:49:34"
 *
 * @param {Object} deviceInfo - DeviceResponse from GET /api/v1/devices/{deviceId}
 * @param {string} [latestRecordedAt] - Timestamp from latest MeasurementResponse
 */
function updateDeviceInfo(deviceInfo, latestRecordedAt) {
    if (!deviceInfo) return;

    const deviceIdEl = document.getElementById("device-id");
    const deviceNameEl = document.getElementById("device-name");
    const deviceLocationEl = document.getElementById("device-location");
    const deviceStatusEl = document.getElementById("device-status");
    const lastRecordedAtEl = document.getElementById("last-recorded-at");

    if (deviceIdEl) {
        deviceIdEl.textContent = deviceInfo.deviceId || "--";
    }

    if (deviceNameEl) {
        deviceNameEl.textContent = deviceInfo.name || "--";
    }

    if (deviceLocationEl) {
        deviceLocationEl.textContent = deviceInfo.location || "--";
    }

    if (deviceStatusEl) {
        const rawStatus = (deviceInfo.status || "UNKNOWN").toUpperCase();
        deviceStatusEl.textContent = rawStatus;
        if (rawStatus === "ACTIVE") {
            deviceStatusEl.style.color = "#16a34a"; // Green for ACTIVE
        } else {
            deviceStatusEl.style.color = "#dc2626"; // Red for INACTIVE / ERROR
        }
    }

    if (lastRecordedAtEl) {
        lastRecordedAtEl.textContent = latestRecordedAt ? formatTimestamp(latestRecordedAt) : "--";
    }
}

/**
 * Update the Live Electrical Measurements cards in the DOM.
 * Target Elements:
 *   - #voltage-rms: e.g. "230.15" or "0.00" (V)
 *   - #current-rms: e.g. "4.250" or "0.000" (A)
 *   - #apparent-power: e.g. "978.14" or "0.00" (VA)
 *   - #sampling-window-label: e.g. "Sampling Window: 5.0 s"
 *
 * Strictly Apparent Energy Terminology:
 *   - V = Voltage RMS
 *   - A = Current RMS
 *   - VA = Apparent Power (S = Vrms * Irms)
 *
 * @param {Object} measurement - MeasurementResponse from GET /api/v1/measurements/latest
 */
function updateLiveMeasurements(measurement) {
    const voltageEl = document.getElementById("voltage-rms");
    const currentEl = document.getElementById("current-rms");
    const powerEl = document.getElementById("apparent-power");
    const windowLabelEl = document.getElementById("sampling-window-label");

    if (!measurement) {
        if (voltageEl) voltageEl.textContent = "--";
        if (currentEl) currentEl.textContent = "--";
        if (powerEl) powerEl.textContent = "--";
        if (windowLabelEl) windowLabelEl.textContent = "Sampling Window: -- s";
        return;
    }

    const vRms = Number(measurement.voltageRms);
    const iRms = Number(measurement.currentRms);
    const sVa = Number(measurement.apparentPower);
    const duration = Number(measurement.samplingDurationSeconds);

    if (voltageEl) {
        voltageEl.textContent = !isNaN(vRms) ? vRms.toFixed(2) : "--";
    }

    if (currentEl) {
        currentEl.textContent = !isNaN(iRms) ? iRms.toFixed(3) : "--";
    }

    if (powerEl) {
        powerEl.textContent = !isNaN(sVa) ? sVa.toFixed(2) : "--";
    }

    if (windowLabelEl) {
        windowLabelEl.textContent = !isNaN(duration)
            ? `Sampling Window: ${duration.toFixed(1)} s`
            : "Sampling Window: -- s";
    }
}

/**
 * Update the Apparent Energy & Estimated Cost cards in the DOM.
 * Target Elements:
 *   - #incremental-energy-vah: Latest window apparent energy in VAh (e.g. "0.0000")
 *   - #aggregated-energy-kvah: Sum of all windows in kVAh (e.g. "0.0000")
 *   - #estimated-cost-inr: Total cost in INR (e.g. "0.00")
 *   - #history-count-label: e.g. "Records Analyzed: 247"
 *   - #tariff-rate-display: e.g. "8.00"
 *
 * Strictly Apparent Energy Formulas:
 *   - Incremental Apparent Energy: ΔVAh = S * (Δt / 3600)
 *   - Aggregated Apparent Energy: Total kVAh = sum(ΔVAh) / 1000
 *   - Estimated Cost: Cost (INR) = Total kVAh * tariff_rate
 *
 * @param {Object} latestMeasurement - MeasurementResponse object
 * @param {Array<Object>} historyList - Array of historical MeasurementResponse records
 * @param {number} [tariffRate=8.00] - Cost in INR per kVAh
 */
function updateEnergyAndCost(latestMeasurement, historyList, tariffRate = 8.00) {
    const incEnergyEl = document.getElementById("incremental-energy-vah");
    const aggEnergyEl = document.getElementById("aggregated-energy-kvah");
    const estCostEl = document.getElementById("estimated-cost-inr");
    const countLabelEl = document.getElementById("history-count-label");
    const tariffDisplayEl = document.getElementById("tariff-rate-display");

    if (tariffDisplayEl) {
        tariffDisplayEl.textContent = tariffRate.toFixed(2);
    }

    // 1. Latest window incremental apparent energy (VAh)
    if (incEnergyEl) {
        if (latestMeasurement && latestMeasurement.incrementalApparentEnergy !== undefined) {
            const incVah = Number(latestMeasurement.incrementalApparentEnergy);
            incEnergyEl.textContent = !isNaN(incVah) ? incVah.toFixed(4) : "0.0000";
        } else {
            incEnergyEl.textContent = "--";
        }
    }

    // 2. Aggregated apparent energy (kVAh) and Estimated Cost (INR) from historical windows
    const summary = window.SmartEnergyApi.calculateEnergySummary(historyList, tariffRate);

    if (aggEnergyEl) {
        aggEnergyEl.textContent = summary.totalKvah.toFixed(4);
    }

    if (estCostEl) {
        estCostEl.textContent = summary.estimatedCostInr.toFixed(2);
    }

    if (countLabelEl) {
        countLabelEl.textContent = `Records Analyzed: ${summary.windowCount}`;
    }
}

/**
 * Chart.js instances map to enable smooth, in-place data updates without memory leaks.
 */
const chartInstances = {
    voltage: null,
    current: null,
    apparentPower: null
};

/**
 * Format timestamp for chart X-axis labels (HH:mm:ss).
 *
 * @param {string} isoString
 * @returns {string} Formatted time string
 */
function formatChartTime(isoString) {
    if (!isoString) return "";
    try {
        const d = new Date(isoString);
        if (isNaN(d.getTime())) return "";
        return d.toLocaleTimeString("en-IN", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false
        });
    } catch (_) {
        return "";
    }
}

/**
 * Render or update a Chart.js line chart instance.
 *
 * @param {string} chartKey - 'voltage' | 'current' | 'apparentPower'
 * @param {string} canvasId - HTML canvas element id
 * @param {string} title - Human-readable metric title
 * @param {Array<string>} labels - X-axis timestamps
 * @param {Array<number>} data - Y-axis telemetry values
 * @param {string} borderColor - Hex line color
 * @param {string} backgroundColor - RGBA area fill color
 * @param {string} unit - Measurement unit (V, A, VA)
 */
function renderLineChart(chartKey, canvasId, title, labels, data, borderColor, backgroundColor, unit) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    // In-place dataset update if chart instance already exists
    if (chartInstances[chartKey]) {
        chartInstances[chartKey].data.labels = labels;
        chartInstances[chartKey].data.datasets[0].data = data;
        chartInstances[chartKey].update("none");
        return;
    }

    // Guard against environments where Chart is not defined
    if (typeof Chart === "undefined") {
        console.warn(`[Charts] Chart.js library is not loaded. Cannot render #${canvasId}.`);
        return;
    }

    const ctx = canvas.getContext("2d");
    chartInstances[chartKey] = new Chart(ctx, {
        type: "line",
        data: {
            labels: labels,
            datasets: [{
                label: `${title} (${unit})`,
                data: data,
                borderColor: borderColor,
                backgroundColor: backgroundColor,
                borderWidth: 2,
                pointRadius: labels.length > 25 ? 1 : 3,
                pointHoverRadius: 5,
                fill: true,
                tension: 0.25
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: "index",
                intersect: false
            },
            plugins: {
                legend: {
                    display: true,
                    labels: {
                        boxWidth: 12,
                        font: { size: 11, family: "Segoe UI, sans-serif" }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${title}: ${ctx.parsed.y} ${unit}`
                    }
                }
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: {
                        maxTicksLimit: 8,
                        font: { size: 10 }
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: { color: "rgba(0, 0, 0, 0.05)" },
                    ticks: {
                        font: { size: 10 },
                        callback: (val) => `${val} ${unit}`
                    }
                }
            }
        }
    });
}

/**
 * Update all 3 historical charts with latest chronological telemetry.
 *
 * @param {Array<Object>} historyList - Chronological list of MeasurementResponse objects
 */
function updateCharts(historyList) {
    if (!Array.isArray(historyList) || historyList.length === 0) {
        // Empty state: render placeholders so the UI does not collapse
        renderLineChart("voltage", "voltageChart", "Voltage RMS", ["No Data"], [0], "#2563eb", "rgba(37, 99, 235, 0.08)", "V");
        renderLineChart("current", "currentChart", "Current RMS", ["No Data"], [0], "#d97706", "rgba(217, 119, 6, 0.08)", "A");
        renderLineChart("apparentPower", "apparentPowerChart", "Apparent Power", ["No Data"], [0], "#7c3aed", "rgba(124, 58, 237, 0.08)", "VA");
        return;
    }

    // Keep the most recent 40 windows for optimal visual clarity
    const sampleWindow = historyList.slice(-40);
    const labels = sampleWindow.map(r => formatChartTime(r.recordedAt));
    const voltageData = sampleWindow.map(r => Number(r.voltageRms) || 0);
    const currentData = sampleWindow.map(r => Number(r.currentRms) || 0);
    const powerData = sampleWindow.map(r => Number(r.apparentPower) || 0);

    // 1. Voltage RMS Chart (V)
    renderLineChart(
        "voltage",
        "voltageChart",
        "Voltage RMS",
        labels,
        voltageData,
        "#2563eb",
        "rgba(37, 99, 235, 0.12)",
        "V"
    );

    // 2. Current RMS Chart (A)
    renderLineChart(
        "current",
        "currentChart",
        "Current RMS",
        labels,
        currentData,
        "#d97706",
        "rgba(217, 119, 6, 0.12)",
        "A"
    );

    // 3. Apparent Power Chart (VA)
    renderLineChart(
        "apparentPower",
        "apparentPowerChart",
        "Apparent Power",
        labels,
        powerData,
        "#7c3aed",
        "rgba(124, 58, 237, 0.12)",
        "VA"
    );
}

/**
 * Main dashboard coordinator for loading and refreshing data.
 */
async function refreshDashboard() {
    const statusBannerEl = document.getElementById("status-banner");
    const connectionBadgeEl = document.getElementById("connection-badge");
    const refreshBtn = document.getElementById("refresh-btn") || document.getElementById("btn-refresh");

    try {
        // 1. Loading State Indicator
        if (refreshBtn) {
            refreshBtn.disabled = true;
            refreshBtn.textContent = "Updating...";
        }
        if (connectionBadgeEl) {
            connectionBadgeEl.className = "status-badge status-loading";
            connectionBadgeEl.textContent = "Updating...";
        }

        // Fetch device metadata, latest measurement, and historical measurements concurrently
        const [deviceInfo, latestMeasurement, historyList] = await Promise.all([
            window.SmartEnergyApi.fetchDeviceInfo("SEM-ESP32-001"),
            window.SmartEnergyApi.fetchLatestMeasurement("SEM-ESP32-001"),
            window.SmartEnergyApi.fetchMeasurementHistory("SEM-ESP32-001")
        ]);

        // Step 7: Update Device Information in the DOM
        const latestTime = latestMeasurement ? latestMeasurement.recordedAt : null;
        updateDeviceInfo(deviceInfo, latestTime);

        // Step 8: Update Live Measurement Cards (V, A, VA, Window Duration)
        updateLiveMeasurements(latestMeasurement);

        // Step 9: Update Apparent Energy & Cost Information (VAh, kVAh, INR)
        updateEnergyAndCost(latestMeasurement, historyList, 8.00);

        // Step 10: Update Historical Trends with Chart.js
        updateCharts(historyList);

        // Update Connection Status Badges & Banner
        if (connectionBadgeEl) {
            connectionBadgeEl.className = "status-badge status-online";
            connectionBadgeEl.textContent = "Online";
        }

        // 2. Empty Data State vs Active Data State
        if (!latestMeasurement || !historyList || historyList.length === 0) {
            if (statusBannerEl) {
                statusBannerEl.className = "status-banner banner-warning";
                statusBannerEl.textContent = `Node ${deviceInfo.deviceId} (${deviceInfo.name}) is registered, but no telemetry has been recorded yet. Waiting for ESP32...`;
            }
        } else {
            if (statusBannerEl) {
                statusBannerEl.className = "status-banner banner-ok";
                statusBannerEl.textContent = `Connected to ${deviceInfo.deviceId} (${deviceInfo.name}) — Active telemetry stream with ${historyList.length} recorded windows.`;
            }
        }

        if (refreshBtn) {
            refreshBtn.textContent = "Refresh Now";
        }

        console.log("[Dashboard] Device Info loaded successfully:", deviceInfo);
        if (latestMeasurement) {
            console.log("[Dashboard] Latest Measurement:", latestMeasurement);
        }
        console.log(`[Dashboard] History analyzed & plotted: ${historyList ? historyList.length : 0} records.`);
    } catch (error) {
        // 3. Error State Handling (Backend offline or network failure)
        console.error("[Dashboard] Error refreshing telemetry:", error);
        if (connectionBadgeEl) {
            connectionBadgeEl.className = "status-badge status-error";
            connectionBadgeEl.textContent = "API Offline";
        }
        if (statusBannerEl) {
            statusBannerEl.className = "status-banner banner-error";
            statusBannerEl.textContent = `Backend Unreachable (${error.message}). Please verify the Spring Boot service is running on port 8080 and MySQL is active.`;
        }
        if (refreshBtn) {
            refreshBtn.textContent = "Retry Connection";
        }
    } finally {
        if (refreshBtn) {
            refreshBtn.disabled = false;
        }
    }
}

/**
 * Polling configuration and lifecycle state.
 */
const POLLING_CONFIG = {
    INTERVAL_MS: 5000, // 5000 ms cadence matches the ESP32's 5.0 s batch upload rate
    ENABLED: true
};

let pollingTimerId = null;
let isPollingActive = false;
let isFetchInProgress = false;

/**
 * Execute a single poll cycle guarded against overlapping network requests.
 */
async function executePollCycle() {
    if (isFetchInProgress) {
        console.warn("[Polling] Previous telemetry fetch still pending. Skipping overlapping tick.");
        return;
    }

    isFetchInProgress = true;
    try {
        await refreshDashboard();
    } catch (err) {
        console.error("[Polling] Error during polling cycle:", err);
    } finally {
        isFetchInProgress = false;
        // Schedule next cycle only after the current network operation has fully completed
        if (isPollingActive) {
            pollingTimerId = setTimeout(executePollCycle, POLLING_CONFIG.INTERVAL_MS);
        }
    }
}

/**
 * Start the automatic background polling loop.
 */
function startPolling() {
    if (isPollingActive) return;
    isPollingActive = true;
    console.log(`[Polling] Started auto-refresh polling every ${POLLING_CONFIG.INTERVAL_MS / 1000}s.`);
    executePollCycle();
}

/**
 * Stop the automatic background polling loop.
 */
function stopPolling() {
    isPollingActive = false;
    if (pollingTimerId) {
        clearTimeout(pollingTimerId);
        pollingTimerId = null;
    }
    console.log("[Polling] Stopped auto-refresh polling.");
}

/**
 * Reset polling timer upon explicit user manual refresh.
 */
function handleManualRefresh() {
    if (pollingTimerId) {
        clearTimeout(pollingTimerId);
        pollingTimerId = null;
    }
    executePollCycle();
}

// Initial boot on DOM ready
document.addEventListener("DOMContentLoaded", () => {
    // Attach manual refresh button listener
    const refreshBtn = document.getElementById("refresh-btn") || document.getElementById("btn-refresh");
    if (refreshBtn) {
        refreshBtn.addEventListener("click", handleManualRefresh);
    }

    // Pause polling when browser tab is inactive to preserve client CPU and network bandwidth
    if (typeof document.addEventListener === "function") {
        document.addEventListener("visibilitychange", () => {
            if (document.hidden) {
                console.log("[Polling] Tab inactive: pausing polling.");
                stopPolling();
            } else {
                console.log("[Polling] Tab active: resuming polling.");
                startPolling();
            }
        });
    }

    // Start initial polling cycle
    startPolling();
});

// Expose dashboard controller for manual debugging and tests
window.SmartEnergyDashboard = {
    refreshDashboard,
    startPolling,
    stopPolling,
    isPollingActive: () => isPollingActive
};
