#ifndef CALIBRATION_H
#define CALIBRATION_H

// ============================================================================
// Smart Energy Monitoring System - Sensor Calibration & Filter Configuration
// Device ID: SEM-ESP32-001
// ============================================================================

// 1. ACS712-5A Current Sensor Calibration (GPIO34 / ADC1_CH6)
#define ACS712_SENSITIVITY_V_PER_A   0.185f
#define CURRENT_CALIBRATION_TRIM     1.000f

// 2. ZMPT101B AC Voltage Sensor Calibration (GPIO35 / ADC1_CH7)
#define ZMPT101B_CALIBRATION_FACTOR  500.00f
#define VOLTAGE_CALIBRATION_TRIM     1.000f

// 3. Sampling Window & Digital Filter Configuration
#define SAMPLING_WINDOW_MS           1000UL
#define SAMPLE_PAIR_DELAY_US         250
#define EMA_ALPHA                    0.30f

// 4. Zero-Load Noise Gate Thresholds
#define VOLTAGE_NOISE_THRESHOLD_V    20.00f
#define CURRENT_NOISE_THRESHOLD_A    0.050f

// ----------------------------------------------------------------------------
// 5. Step 14: Hardware Sensor Fault & Disconnection Detection Bounds
// Healthy observed midpoints on SEM-ESP32-001:
//   - ACS712 (GPIO34)   : ~3096 counts (valid operating band: 1200 .. 3700)
//   - ZMPT101B (GPIO35) : ~1930 counts (valid operating band: 1000 .. 3700)
// If midpoint drops near 0 (unpowered/GND short) or >3700 (3.3V rail short),
// flag a sensor hardware fault and zero-clamp the measurement.
// ----------------------------------------------------------------------------
#define ACS712_MIN_VALID_MIDPOINT    1200.0f
#define ACS712_MAX_VALID_MIDPOINT    3700.0f

#define ZMPT101B_MIN_VALID_MIDPOINT  1000.0f
#define ZMPT101B_MAX_VALID_MIDPOINT  3700.0f

#endif // CALIBRATION_H
