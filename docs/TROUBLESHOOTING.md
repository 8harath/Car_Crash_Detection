# Troubleshooting

Symptom → fix for the most common problems. Broker scripts referenced here live in `scripts/`
(see [../scripts/README.md](../scripts/README.md)).

---

## MQTT

### "Connection failed" / never connects
1. **Is the broker running?** `scripts/check_mosquitto.bat`, or `mosquitto_sub -h localhost -t test -v`.
2. **Right address?** Open the app's **MQTT Settings** and confirm the **IP + port** match your
   broker (default `192.168.0.101:1883`). The broker is set *in the app*, not in `MqttConfig.kt`.
3. **Same network?** Phone and broker must be on the same LAN. Test with `ping <broker-ip>`.
4. **Firewall?** Allow inbound TCP **1883** on the broker machine.
5. **Anonymous allowed?** The sample configs permit anonymous clients; if you enabled auth, set the
   username/password in MQTT Settings.

### Connected, but messages don't arrive
- Confirm both devices use the **same broker** and the roles are correct (Publisher vs Subscriber).
- Topics must match `util/MqttTopics.kt` (`emergency/...`). Subscribers listen on the `emergency/alerts/+`
  family; publishers post to `emergency/alerts/{incidentId}`.
- Watch Logcat for publish/subscribe lines, or run `python scripts/test_local_broker.py` to prove the
  broker round-trips messages independently of the app.

### Drops intermittently
- Check Wi-Fi signal; prefer the broker on a wired/stable host.
- Keep-alive is 60 s and reconnect retries 5× with a 5 s delay (`MqttConfig`).

---

## ESP32 / Bluetooth

### ESP32 not found during scan
1. Serial Monitor (115200) should show `BLE Server ready - waiting for connections...`.
2. Confirm it advertises as **`ESP32_CarCrash`** with a BLE scanner like **nRF Connect**.
3. Grant **Bluetooth** *and* **Location** permissions — Android requires location for BLE scanning.
4. **UUIDs must match.** The firmware and `util/Esp32BluetoothService.kt` must share
   service `4fafc201-…` and characteristic `beb5483e-…`.

### Connects but no sensor data
- Verify MPU6050 wiring (SDA→GPIO21, SCL→GPIO22, 3.3 V) and the GPS UART (GPIO16/17).
- The Serial Monitor should print `Sent via BLE: ACC:…|IMPACT:…|GPS:…` every 100 ms.

### Upload to ESP32 fails
- Try a different USB cable/port; hold the **BOOT** button during upload.

---

## Build & app

### Gradle sync / build errors
- JDK **11** installed and selected; Android Studio up to date.
- **File → Invalidate Caches / Restart**, then a clean build.
- This module's namespace/applicationId is `com.bharath.carcrashdetection`.

### App crashes on launch
- Check Logcat (filter by the app package). Confirm API 24+ and that runtime permissions were granted.
- Clearing app data resets the local DB and preferences (including the saved broker).

### Data disappears after an update
- The Room DB uses **destructive migration** — a schema-version bump intentionally wipes local data.
  Export/back up before changing entities.

---

## Built-in debug tools
- **MQTT Test** and **Bluetooth Test** screens in the app for isolated checks.
- **Logcat** for live logs; **App Inspection → Database Inspector** to view Room contents.

---

## Known limitations (current state)

> Salvaged and updated from earlier internal test notes — these reflect what is and isn't solid.

- **BLE is the supported sensor transport.** `Esp32BluetoothService` also contains
  **Bluetooth-Classic** and, via `Esp32WifiDirectService`, **Wi-Fi-Direct** paths that are only
  partially implemented. Use the BLE firmware in `firmware/` (its UUIDs match the app). An older
  experimental firmware advertised different UUIDs (`0000ffe0/0000ffe1`) and will **not** connect.
- **Local, unsecured broker by design.** The sample Mosquitto config allows anonymous connections.
  For anything beyond a lab demo, enable TLS + authentication.
- **`production/`, `demo/`, `testing/` packages are scaffolding** — useful structure, but not all
  flows are wired into the UI (see [PRODUCTION_GUIDE.md](PRODUCTION_GUIDE.md)).
- **Not a certified safety system.** Threshold-based detection tuned for demonstration only.
