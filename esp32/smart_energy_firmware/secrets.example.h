#ifndef SECRETS_H
#define SECRETS_H

// ============================================================================
// Smart Energy Monitoring System - Wi-Fi & Backend Endpoint Template
// Copy this file to "secrets.h" (which is git-ignored) and fill in your values.
// NEVER commit real Wi-Fi passwords to Git!
// ============================================================================

#define WIFI_SSID        "YOUR_WIFI_SSID"
#define WIFI_PASSWORD    "YOUR_WIFI_PASSWORD"

// Local IPv4 address of the PC running the Spring Boot Backend (port 8080)
#define BACKEND_HOST     "192.168.1.100"
#define BACKEND_PORT     8080
#define BACKEND_API_PATH "/api/v1/measurements"

// Device-specific API key for SEM-ESP32-001 hardware telemetry authentication
#define DEVICE_API_KEY   "YOUR_DEVICE_API_KEY"

#endif // SECRETS_H
