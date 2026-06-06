# ESP32 Crash-Sensor Firmware

This directory holds the firmware for the in-vehicle crash-sensor node. The Android
**Publisher** app connects to this node over Bluetooth Low Energy (BLE) and consumes a
live stream of accelerometer, impact, and GPS readings.

```
firmware/
└── car_crash_sensor/
    └── car_crash_sensor.ino   ← canonical firmware (matches the Android app)
```

> The sketch lives in a folder whose name matches the `.ino`, as the Arduino IDE requires.
> Open `firmware/car_crash_sensor/car_crash_sensor.ino` directly in the Arduino IDE.

---

## Hardware

| Component | Detail |
|-----------|--------|
| Board | ESP32 dev board (BLE-capable) |
| Accelerometer | MPU6050 (I²C), sensitivity 16384 LSB/g |
| GNSS | GPS module on a hardware UART (TinyGPS++), 9600 baud |

### Wiring

| Signal | ESP32 pin |
|--------|-----------|
| MPU6050 SDA | GPIO 21 |
| MPU6050 SCL | GPIO 22 |
| GPS RX (ESP32 receives) | GPIO 16 |
| GPS TX (ESP32 transmits) | GPIO 17 |

### Required Arduino libraries

- `BLEDevice` / `BLEServer` / `BLEUtils` (bundled with the ESP32 Arduino core)
- `MPU6050` (Electronic Cats / Jeff Rowberg)
- `TinyGPS++`

---

## Canonical firmware — `car_crash_sensor.ino`

A BLE **GATT server** named `ESP32_CarCrash`. It must match the UUIDs hard-coded in the
app (`app/.../util/Esp32BluetoothService.kt`):

| Item | Value |
|------|-------|
| Device name | `ESP32_CarCrash` |
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` |
| Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` (READ · WRITE · NOTIFY) |
| Notify interval | every 100 ms while a client is connected |

### Behaviour

1. Read raw accelerometer values and convert to g (`raw / 16384.0`).
2. Compute impact magnitude `√(x² + y² + z²)` and apply a low-pass filter
   (`0.9 × previous + 0.1 × current`) to suppress noise.
3. Parse any available GPS bytes (TinyGPS++).
4. While a BLE client is connected, `notify()` a string payload every 100 ms.

### Wire format

A single ASCII string on the characteristic:

```
ACC:<x>,<y>,<z>|IMPACT:<force>|GPS:<lat>,<lon>
```

Example: `ACC:0.01,-0.02,1.00|IMPACT:1.00|GPS:12.971599,77.594566`
(GPS reports `0,0` until a fix is acquired.)

> **Crash detection runs in the app, not the firmware.** This sketch only measures and
> streams. The Android side applies the impact threshold and severity classification.
> See `docs/ARCHITECTURE.md`.

---

## Alternate design (not used by the app)

An earlier experimental firmware drove three transports at once — Bluetooth Classic (SPP),
BLE, **and** direct WiFi→MQTT publishing — and performed crash thresholding on-device. It
has been removed to avoid confusion, but the design is recorded here for reference:

| Item | Value |
|------|-------|
| Device name | `ESP32_CrashDetector` |
| BLE Service / Characteristic | `0000ffe0-…` / `0000ffe1-…` (**incompatible** with the app's UUIDs) |
| On-device crash threshold | `5.0 g` (adjustable via the `SET_THRESHOLD:` command) |
| Direct MQTT topics | `esp32/sensor_data`, `esp32/crash_alert`, `esp32/status`, `esp32/test` |
| Serial/BT commands | `GET_STATUS`, `SET_THRESHOLD:<g>`, `CALIBRATE`, `TEST_CRASH`, `MQTT_TEST` |

That approach made the ESP32 publish to the broker itself. The shipped architecture instead
keeps the ESP32 as a **dumb BLE sensor** and lets the phone own GPS, crash logic, medical
data, and MQTT — which is why the canonical firmware above is the one the app expects.

---

## Flashing

1. Install the **ESP32 board package** in the Arduino IDE
   (Boards Manager → "esp32" by Espressif).
2. Install the libraries listed above.
3. Open `car_crash_sensor/car_crash_sensor.ino`, select your ESP32 board + port.
4. Upload, then open Serial Monitor at **115200 baud** to confirm:
   `BLE Server ready - waiting for connections...`
5. In the app's **Bluetooth Test** screen, scan for `ESP32_CarCrash` and connect.
