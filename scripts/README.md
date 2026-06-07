# `scripts/` — helper scripts

Utility scripts for running the MQTT broker, building the app, and testing connectivity. Most
`.bat` files target **Windows**; `.sh` files target **macOS/Linux**. Python scripts need
`paho-mqtt` (`pip install paho-mqtt`).

---

## MQTT broker

| Script | Platform | Purpose |
|--------|----------|---------|
| `mosquitto_config.conf` | — | Broker config template (port 1883, anonymous, persistence). |
| `mosquitto_local.conf` | — | Local-dev variant (current-dir persistence, verbose). |
| `setup_local_mqtt.sh` / `.bat` | Unix / Win | Install (if needed), start the broker, print the LAN IP, smoke-test with `mosquitto_pub`. |
| `start_mosquitto.sh` / `.bat` | Unix / Win | Start Mosquitto with `mosquitto_local.conf` and verbose logging. |
| `check_mosquitto.bat` | Win | Verify the service is installed, port 1883 is listening, and a test publish works. |

## Connectivity diagnostics

| Script | Platform | Purpose |
|--------|----------|---------|
| `diagnose_mqtt_connection.bat` | Win | Check whether the broker is reachable. |
| `diagnose_mqtt_communication.bat` | Win | End-to-end publish/subscribe check. |
| `test_mqtt_connection.bat` | Win | Quick connection test. |
| `test_mqtt_local_broker.bat` | Win | Test against a localhost broker. |
| `test_mqtt_broker.bat` | Win | Test against a configured/remote broker. |
| `test_bluetooth_setup.bat` | Win | ESP32 / Bluetooth bring-up check. |

## Python tests

| Script | Purpose |
|--------|---------|
| `mqtt_test.py` | Interactive CLI: subscribes to the `esp32/*` topics and lets you publish commands (pairs with the alternate firmware). Targets `localhost:1883`. |
| `test_local_broker.py` | Connects to `localhost:1883`, publishes to `emergency/test/local`, `emergency/custom/local`, `emergency/alerts/local`, and verifies delivery. |
| `test_mqtt_broker.py` | Same idea against a remote broker IP (edit the host at the top of the file). |
| `test_ip_validation_and_messaging.py` | Reference/spec for the app's IP-validation rules + a simulated pub/sub message flow. |

## Build

| Script | Platform | Output |
|--------|----------|--------|
| `build_apk.bat` | Win | Clean + `assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. |
| `build_production.sh` | Unix | `assembleRelease` (requires `keystore.properties`; see [../docs/PRODUCTION_GUIDE.md](../docs/PRODUCTION_GUIDE.md)). |
| `build_production.bat` | Win | Attempts release; falls back to a debug build if no keystore. |

---

### Notes
- The MQTT topics used by these scripts (`emergency/...`) match `app/.../util/MqttTopics.kt`. The
  `esp32/*` topics are used by the alternate direct-MQTT firmware described in
  [../firmware/README.md](../firmware/README.md), not the BLE firmware the app uses.
- Before running a broker test, edit the target host/IP near the top of the `.py` file to match
  your machine.
- The Windows `.bat` build scripts invoke the Gradle wrapper. From a fresh clone on Windows you may
  need `gradlew.bat` at the repository root (this repo ships the Unix `gradlew`); the simplest path
  is to build from Android Studio or with `./gradlew` on macOS/Linux.
