# Smart Energy Monitoring System — ESP32 Edge Firmware

**Device ID:** `SEM-ESP32-001`  
**Target MCU:** ESP32 (`ESP32-D0WD-V3`, Dual-Core 240 MHz)  
**Framework:** Arduino Core for ESP32

---

## 1. Safety Notice

> **WARNING:** This prototype interfaces with sensors capable of measuring mains AC electricity ($110\text{V} - 240\text{V}$ AC). Mains voltage is lethal. Never touch, wire, or modify circuits while energized. Always isolate power before handling hardware. This is an educational/research prototype and not a certified utility billing meter.

---

## 2. Hardware & Sensor Pinout

We strictly use **ADC1** pins (`GPIO34` and `GPIO35`) because **ADC2** pins conflict with the ESP32 Wi-Fi driver. Both `GPIO34` and `GPIO35` are input-only pins with no internal pull-up or pull-down resistors, preventing signal distortion.

| Sensor / Component | Quantity Measured | ESP32 Pin | ADC Channel | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **ACS712-5A** | Current RMS ($I_{\text{rms}}$ in $\text{A}$) | `GPIO34` | `ADC1_CH6` | Nominal sensitivity: $185\text{ mV/A}$ |
| **ZMPT101B** | Voltage RMS ($V_{\text{rms}}$ in $\text{V}$) | `GPIO35` | `ADC1_CH7` | AC Voltage Transformer Sensor |

---

## 3. Electrical Measurement Terminology

This system measures **apparent electrical quantities** only:
- **Voltage RMS ($V_{\text{rms}}$):** Volts ($\text{V}$)
- **Current RMS ($I_{\text{rms}}$):** Amperes ($\text{A}$)
- **Apparent Power ($S$):** Volt-Amperes ($\text{VA}$), where $S = V_{\text{rms}} \times I_{\text{rms}}$
- **Incremental Apparent Energy ($\Delta E$):** Volt-Ampere-Hours ($\text{VAh}$), where $\Delta\text{VAh} = S \times \frac{\Delta t}{3600}$
- **Aggregated Apparent Energy:** Kilovolt-Ampere-Hours ($\text{kVAh}$)

*(Phase angle, power factor, and active power in Watts/kWh are not claimed).*

---

## 4. Directory Structure

```text
esp32/
├── README.md                          # Firmware documentation & pinout table
├── include/
│   └── pins.h                         # Reference header for pin & ADC constants
└── smart_energy_firmware/             # Arduino IDE Sketch Directory
    ├── smart_energy_firmware.ino      # Main firmware entry point (setup & loop)
    └── pins.h                         # Local header loaded as tab in Arduino IDE
```
