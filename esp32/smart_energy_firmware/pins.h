#ifndef PINS_H
#define PINS_H

#include <Arduino.h>

// ============================================================================
// Smart Energy Monitoring System - Hardware Pin & ADC Configuration
// Device ID: SEM-ESP32-001
// ============================================================================

// Device Identifier (must match backend database registration)
#define DEVICE_ID "SEM-ESP32-001"

// Serial Monitor Communication Speed
#define SERIAL_BAUD_RATE 115200

// ----------------------------------------------------------------------------
// Sensor GPIO Pin Assignments (ADC1 Only - Safe to use simultaneously with Wi-Fi)
// ----------------------------------------------------------------------------

// ACS712-5A Current Sensor connected to GPIO34 (ADC1 Channel 6)
// Note: GPIO34 is an input-only pin on ESP32.
#define PIN_ACS712_CURRENT 34

// ZMPT101B AC Voltage Sensor connected to GPIO35 (ADC1 Channel 7)
// Note: GPIO35 is an input-only pin on ESP32.
#define PIN_ZMPT101B_VOLTAGE 35

// ----------------------------------------------------------------------------
// ESP32 ADC Hardware Parameters
// ----------------------------------------------------------------------------

// 12-bit ADC resolution produces integer values from 0 to 4095
#define ADC_RESOLUTION_BITS 12
#define ADC_MAX_COUNT 4095
#define ADC_REFERENCE_VOLTAGE 3.3f

#endif // PINS_H
