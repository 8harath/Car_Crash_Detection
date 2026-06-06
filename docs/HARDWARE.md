# Hardware Guide

The in-vehicle sensing is done by an **ESP32** dev board reading an **MPU6050** accelerometer and a
**GPS module**, streaming to the phone over Bluetooth Low Energy. This page covers the physical
side; the firmware itself (sketch, UUIDs, wire format, flashing) is documented in
**[../firmware/README.md](../firmware/README.md)**.

---

## Bill of materials

| Part | Notes |
|------|-------|
| ESP32 dev board | Any BLE-capable ESP32 (e.g. ESP32-WROOM DevKit) |
| MPU6050 | 6-axis accelerometer/gyro, I²C, 3.3 V |
| GPS module | UART (e.g. NEO-6M), 9600 baud, 3.3 V logic |
| Jumper wires, breadboard | For prototyping |
| Micro-USB cable | Power + flashing |

---

## Wiring

```
        ESP32                         MPU6050
   ┌───────────────┐             ┌───────────────┐
   │ 3V3 ──────────┼─────────────┤ VCC           │
   │ GND ──────────┼─────────────┤ GND           │
   │ GPIO21 (SDA) ─┼─────────────┤ SDA           │
   │ GPIO22 (SCL) ─┼─────────────┤ SCL           │
   │               │             └───────────────┘
   │               │                  GPS module
   │               │             ┌───────────────┐
   │ 3V3 ──────────┼─────────────┤ VCC           │
   │ GND ──────────┼─────────────┤ GND           │
   │ GPIO16 (RX) ──┼─────────────┤ TX            │
   │ GPIO17 (TX) ──┼─────────────┤ RX            │
   └───────────────┘             └───────────────┘
```

| Signal | ESP32 pin |
|--------|-----------|
| MPU6050 SDA | GPIO 21 |
| MPU6050 SCL | GPIO 22 |
| GPS → ESP32 (GPS TX → ESP RX) | GPIO 16 |
| ESP32 → GPS (ESP TX → GPS RX) | GPIO 17 |

> Power both sensors from **3.3 V**, not 5 V. The MPU6050's default I²C address is `0x68`.

---

## What the node does

1. Samples the MPU6050 and converts raw counts to g (`raw / 16384.0`).
2. Computes an impact magnitude `√(x²+y²+z²)` and low-pass filters it.
3. Reads GPS NMEA via TinyGPS++.
4. While a phone is connected over BLE, `notify()`s a string **every 100 ms**:

   ```
   ACC:<x>,<y>,<z>|IMPACT:<force>|GPS:<lat>,<lon>
   ```

**Crash detection lives in the app, not the node.** The ESP32 is intentionally a "dumb" sensor; the
Publisher app applies the impact threshold and severity classification. This keeps the firmware
simple and lets detection logic evolve without re-flashing hardware. See
[ARCHITECTURE.md](ARCHITECTURE.md#4-esp32--bluetooth).

---

## BLE contract (must match the app)

| Item | Value |
|------|-------|
| Device name | `ESP32_CarCrash` |
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` |
| Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` |

These exact values are hard-coded in `app/.../util/Esp32BluetoothService.kt`. If you change them in
the firmware, change them in the app too.

---

## Bring-up checklist

- [ ] Sensors wired per the table above, powered from 3.3 V
- [ ] Firmware flashed (see [../firmware/README.md](../firmware/README.md))
- [ ] Serial Monitor @ 115200 shows `BLE Server ready - waiting for connections...`
- [ ] A BLE scanner (e.g. **nRF Connect**) sees `ESP32_CarCrash` advertising the service UUID
- [ ] The app's **Bluetooth Test** screen discovers and connects to the node
- [ ] Live `ACC|IMPACT|GPS` values update as you move the board
