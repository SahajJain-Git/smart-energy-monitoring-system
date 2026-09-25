// ============================================================================
// Project: Smart Energy Monitoring System
// Device:  SEM-ESP32-001
// Step 17: Production Telemetry Engine with Retry, Backoff & Batch Accumulation
// ============================================================================

#include "pins.h"
#include "calibration.h"
#include "secrets.h"
#include <WiFi.h>
#include <math.h>

bool isEmaInitialized = false;
float emaVoltageRms = 0.0f;
float emaCurrentRms = 0.0f;
double totalApparentEnergyVah = 0.0;

// Wi-Fi & HTTP Reliability Parameters (Step 17)
unsigned long lastWifiReconnectAttemptMs = 0;
const unsigned long WIFI_RECONNECT_INTERVAL_MS = 10000;

const int POST_EVERY_N_WINDOWS = 5;       // Transmit telemetry every 5 windows (~5.00 s)
const int MAX_HTTP_RETRIES = 3;           // Up to 3 attempts per transmission
const unsigned long BASE_RETRY_DELAY_MS = 250; // Exponential backoff: 250ms, 500ms, 1000ms

int windowCounter = 0;
int consecutiveHttpFailures = 0;
unsigned long nextAllowedPostMs = 0;

// Unsent batch accumulators so zero energy is lost across windows or retries
double batchSumVoltage = 0.0;
double batchSumCurrent = 0.0;
int batchWindowCount = 0;
float pendingDurationSeconds = 0.0f;
float pendingDeltaVah = 0.0f;

struct EnergyMeasurement {
  float samplingDurationSeconds;
  float midpointCurrentAdc;
  float midpointVoltageAdc;
  bool currentSensorOk;
  bool voltageSensorOk;
  bool systemHealthy;
  float voltageRms;
  float currentRms;
  float apparentPowerVa;
  float incrementalApparentEnergyVah;
  double cumulativeApparentEnergyKvah;
};

float computeApparentPowerVa(float voltageRms, float currentRms) {
  return voltageRms * currentRms;
}

float computeIncrementalApparentEnergyVah(float apparentPowerVa, float dtSeconds) {
  return apparentPowerVa * (dtSeconds / 3600.0f);
}

void connectToWiFi() {
  Serial.print("  Connecting to Wi-Fi SSID: ");
  Serial.println(WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("  [Wi-Fi Connected!]");
    Serial.print("  ESP32 IP       : ");
    Serial.println(WiFi.localIP());
    Serial.print("  Backend Target : http://");
    Serial.print(BACKEND_HOST);
    Serial.print(":");
    Serial.print(BACKEND_PORT);
    Serial.println(BACKEND_API_PATH);
  }
}

void ensureWiFiConnected() {
  if (WiFi.status() == WL_CONNECTED) return;
  unsigned long now = millis();
  if (now - lastWifiReconnectAttemptMs >= WIFI_RECONNECT_INTERVAL_MS) {
    lastWifiReconnectAttemptMs = now;
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  }
}

int sendSingleHttpPost(const char* jsonPayload, int contentLen, String& responseBodyOut) {
  WiFiClient client;
  client.setTimeout(3);

  if (!client.connect(BACKEND_HOST, BACKEND_PORT)) {
    return -2; // TCP connect error
  }

  client.print("POST ");
  client.print(BACKEND_API_PATH);
  client.println(" HTTP/1.1");
  client.print("Host: ");
  client.print(BACKEND_HOST);
  client.print(":");
  client.println(BACKEND_PORT);
  client.println("Content-Type: application/json");
  client.print("Content-Length: ");
  client.println(contentLen);
  client.println("Connection: close");
  client.println();
  client.print(jsonPayload);

  unsigned long waitStart = millis();
  while (!client.available() && (millis() - waitStart) < 3000) {
    delay(10);
  }

  if (!client.available()) {
    client.stop();
    return -3; // Read timeout
  }

  String statusLine = client.readStringUntil('\n');
  statusLine.trim();
  int httpStatusCode = 0;
  int firstSpace = statusLine.indexOf(' ');
  if (firstSpace > 0 && statusLine.length() >= firstSpace + 4) {
    httpStatusCode = statusLine.substring(firstSpace + 1, firstSpace + 4).toInt();
  }

  bool headersEnded = false;
  while (client.available()) {
    String line = client.readStringUntil('\n');
    line.trim();
    if (!headersEnded) {
      if (line.length() == 0) headersEnded = true;
    } else if (line.length() > 0 && line.startsWith("{")) {
      responseBodyOut = line;
    }
  }
  client.stop();
  return httpStatusCode;
}

// Step 17: Transmit with Exponential Backoff Retry & Cooldown Protection
bool postBatchWithRetry(float avgV, float avgI, float durationSec) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("  [Step 17 Retry Engine] Wi-Fi offline; retaining batch in buffer.");
    return false;
  }

  // Round to match backend DTO scale validation
  float vRounded  = roundf(avgV * 100.0f) / 100.0f;
  float iRounded  = roundf(avgI * 1000.0f) / 1000.0f;
  float dtRounded = roundf(durationSec * 100.0f) / 100.0f;
  if (dtRounded < 0.01f) dtRounded = 1.00f;
  float sRounded  = roundf((vRounded * iRounded) * 100.0f) / 100.0f;
  float deRounded = roundf((sRounded * (dtRounded / 3600.0f)) * 10000.0f) / 10000.0f;

  char jsonPayload[256];
  int contentLen = snprintf(
    jsonPayload, sizeof(jsonPayload),
    "{\"deviceId\":\"%s\",\"voltageRms\":%.2f,\"currentRms\":%.3f,"
    "\"apparentPower\":%.2f,\"incrementalApparentEnergy\":%.4f,"
    "\"samplingDurationSeconds\":%.2f}",
    DEVICE_ID, vRounded, iRounded, sRounded, deRounded, dtRounded
  );

  for (int attempt = 1; attempt <= MAX_HTTP_RETRIES; attempt++) {
    String responseBody = "";
    int status = sendSingleHttpPost(jsonPayload, contentLen, responseBody);

    if (status == 201 || status == 200) {
      Serial.print("  [Step 17 POST Success (Attempt ");
      Serial.print(attempt);
      Serial.print("/3)] HTTP ");
      Serial.print(status);
      Serial.print(" | Body: ");
      Serial.println(responseBody);
      consecutiveHttpFailures = 0;
      return true;
    }

    unsigned long backoffMs = BASE_RETRY_DELAY_MS * (1UL << (attempt - 1));
    Serial.print("  [Step 17 Retry ");
    Serial.print(attempt);
    Serial.print("/3] HTTP error (");
    Serial.print(status);
    Serial.print("). Backing off ");
    Serial.print(backoffMs);
    Serial.println(" ms...");
    delay(backoffMs);
  }

  // All 3 attempts failed -> apply cooldown backoff (5s, 10s, 20s, max 60s)
  consecutiveHttpFailures++;
  unsigned long cooldownSec = 5UL * (1UL << min(consecutiveHttpFailures - 1, 3));
  if (cooldownSec > 60UL) cooldownSec = 60UL;
  nextAllowedPostMs = millis() + (cooldownSec * 1000UL);

  Serial.print("  [Step 17 Cooldown] Backend unreachable. Retaining batch; next retry in ");
  Serial.print(cooldownSec);
  Serial.println(" s.");
  return false;
}

EnergyMeasurement acquireEnergyMeasurement() {
  unsigned long startMs = millis();
  unsigned long startUs = micros();

  long count = 0;
  double sumCurrentAdc = 0.0;
  double sumSqCurrentAdc = 0.0;
  double sumVoltageAdc = 0.0;
  double sumSqVoltageAdc = 0.0;

  while ((millis() - startMs) < SAMPLING_WINDOW_MS) {
    int rawCurrent = analogRead(PIN_ACS712_CURRENT);
    int rawVoltage = analogRead(PIN_ZMPT101B_VOLTAGE);

    sumCurrentAdc += (double)rawCurrent;
    sumSqCurrentAdc += (double)rawCurrent * (double)rawCurrent;

    sumVoltageAdc += (double)rawVoltage;
    sumSqVoltageAdc += (double)rawVoltage * (double)rawVoltage;

    count++;
    delayMicroseconds(SAMPLE_PAIR_DELAY_US);
  }

  unsigned long elapsedUs = micros() - startUs;
  float dtSeconds = (float)elapsedUs / 1000000.0f;

  float meanCurrentAdc = (float)(sumCurrentAdc / (double)count);
  float meanVoltageAdc = (float)(sumVoltageAdc / (double)count);

  bool currentOk = (meanCurrentAdc >= ACS712_MIN_VALID_MIDPOINT &&
                    meanCurrentAdc <= ACS712_MAX_VALID_MIDPOINT);
  bool voltageOk = (meanVoltageAdc >= ZMPT101B_MIN_VALID_MIDPOINT &&
                    meanVoltageAdc <= ZMPT101B_MAX_VALID_MIDPOINT);
  bool allHealthy = currentOk && voltageOk;

  double varianceCurrentAdc = (sumSqCurrentAdc / (double)count) - ((double)meanCurrentAdc * (double)meanCurrentAdc);
  double varianceVoltageAdc = (sumSqVoltageAdc / (double)count) - ((double)meanVoltageAdc * (double)meanVoltageAdc);

  if (varianceCurrentAdc < 0.0) varianceCurrentAdc = 0.0;
  if (varianceVoltageAdc < 0.0) varianceVoltageAdc = 0.0;

  float rmsCurrentPinVolts = (sqrt(varianceCurrentAdc) / (float)ADC_MAX_COUNT) * ADC_REFERENCE_VOLTAGE;
  float rmsVoltagePinVolts = (sqrt(varianceVoltageAdc) / (float)ADC_MAX_COUNT) * ADC_REFERENCE_VOLTAGE;

  float rawI = (rmsCurrentPinVolts / ACS712_SENSITIVITY_V_PER_A) * CURRENT_CALIBRATION_TRIM;
  float rawV = (rmsVoltagePinVolts * ZMPT101B_CALIBRATION_FACTOR) * VOLTAGE_CALIBRATION_TRIM;

  if (!currentOk) rawI = 0.0f;
  if (!voltageOk) rawV = 0.0f;

  if (!isEmaInitialized) {
    emaVoltageRms = rawV;
    emaCurrentRms = rawI;
    isEmaInitialized = true;
  } else {
    emaVoltageRms = (EMA_ALPHA * rawV) + ((1.0f - EMA_ALPHA) * emaVoltageRms);
    emaCurrentRms = (EMA_ALPHA * rawI) + ((1.0f - EMA_ALPHA) * emaCurrentRms);
  }

  float finalV = (!voltageOk || emaVoltageRms < VOLTAGE_NOISE_THRESHOLD_V) ? 0.00f  : emaVoltageRms;
  float finalI = (!currentOk || emaCurrentRms < CURRENT_NOISE_THRESHOLD_A) ? 0.000f : emaCurrentRms;

  float apparentPowerVa = computeApparentPowerVa(finalV, finalI);
  float deltaVah = allHealthy ? computeIncrementalApparentEnergyVah(apparentPowerVa, dtSeconds) : 0.0f;

  totalApparentEnergyVah += (double)deltaVah;

  EnergyMeasurement m;
  m.samplingDurationSeconds = dtSeconds;
  m.midpointCurrentAdc = meanCurrentAdc;
  m.midpointVoltageAdc = meanVoltageAdc;
  m.currentSensorOk = currentOk;
  m.voltageSensorOk = voltageOk;
  m.systemHealthy = allHealthy;
  m.voltageRms = finalV;
  m.currentRms = finalI;
  m.apparentPowerVa = apparentPowerVa;
  m.incrementalApparentEnergyVah = deltaVah;
  m.cumulativeApparentEnergyKvah = totalApparentEnergyVah / 1000.0;
  return m;
}

void setup() {
  Serial.begin(SERIAL_BAUD_RATE);
  delay(1000);

  analogReadResolution(ADC_RESOLUTION_BITS);
  pinMode(PIN_ACS712_CURRENT, INPUT);
  pinMode(PIN_ZMPT101B_VOLTAGE, INPUT);

  Serial.println();
  Serial.println("====================================================================");
  Serial.println("  SMART ENERGY MONITORING SYSTEM - STEP 17/18 PRODUCTION FIRMWARE");
  Serial.println("====================================================================");
  connectToWiFi();
  Serial.println("====================================================================");
  Serial.println();
}

void loop() {
  ensureWiFiConnected();
  EnergyMeasurement m = acquireEnergyMeasurement();
  windowCounter++;

  if (m.systemHealthy) {
    batchSumVoltage += (double)m.voltageRms;
    batchSumCurrent += (double)m.currentRms;
    batchWindowCount++;
    pendingDurationSeconds += m.samplingDurationSeconds;
    pendingDeltaVah += m.incrementalApparentEnergyVah;
  }

  Serial.print("[Step 17 Telemetry #");
  Serial.print(windowCounter);
  Serial.print("] Health: ");
  Serial.print(m.systemHealthy ? "OK" : "FAULT");
  Serial.print(" | V: ");
  Serial.print(m.voltageRms, 2);
  Serial.print("V | I: ");
  Serial.print(m.currentRms, 3);
  Serial.print("A | S: ");
  Serial.print(m.apparentPowerVa, 2);
  Serial.print("VA | Batch dt: ");
  Serial.print(pendingDurationSeconds, 2);
  Serial.println("s");

  // Transmit accumulated 5-window batch when due and outside cooldown
  if (batchWindowCount >= POST_EVERY_N_WINDOWS && millis() >= nextAllowedPostMs) {
    float avgV = (float)(batchSumVoltage / (double)batchWindowCount);
    float avgI = (float)(batchSumCurrent / (double)batchWindowCount);

    if (postBatchWithRetry(avgV, avgI, pendingDurationSeconds)) {
      // Clear batch accumulators only after confirmed HTTP 201 Created
      batchSumVoltage = 0.0;
      batchSumCurrent = 0.0;
      batchWindowCount = 0;
      pendingDurationSeconds = 0.0f;
      pendingDeltaVah = 0.0f;
    }
  }
}
