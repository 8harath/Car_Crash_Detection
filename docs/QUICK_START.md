# Quick Start

The fastest path to seeing the app run. For the full system (ESP32 + broker + two phones), use
[SETUP_GUIDE.md](SETUP_GUIDE.md).

## Prerequisites
- Android Studio (recent) + JDK 11
- An Android device or emulator, API 24+ (Android 7.0+)
- *(To exercise messaging)* a reachable Mosquitto broker and ideally a second device

## Run it

**Android Studio (recommended)**
1. File → Open → select the project folder; let Gradle sync finish.
2. Connect a device (USB debugging on) or start an emulator.
3. Press **Run** ▶️.

**Command line**
```bash
./gradlew assembleDebug
./gradlew installDebug
```

## First launch — what to click
1. **Role selection** — choose **Crash Victim** (Publisher) or **Emergency Responder** (Subscriber).
2. **Enter your name** — stored in the local Room database.
3. **MQTT Settings** — enter your broker **IP** + **port** (default `192.168.0.101:1883`) and enable the service. (No code change needed to switch brokers.)
4. **Publisher:** manage your medical profile, connect to the ESP32 in **Bluetooth Test**, and trigger an alert.
   **Subscriber:** watch the alert list; open an incident to see medical info; send a response/ETA.

## Smoke-test checklist
- [ ] App launches without crashing
- [ ] Role selection + name entry work and persist across restarts
- [ ] MQTT Settings saves the broker IP/port and shows a connected status
- [ ] (Two devices) a Publisher alert appears on the Subscriber
- [ ] (With hardware) Bluetooth Test discovers `ESP32_CarCrash` and shows live data

## If something's off
- App won't launch / crashes → check Logcat; confirm API 24+ and granted permissions.
- Build errors → JDK 11 set, Android Studio updated, then **File → Invalidate Caches / Restart**.
- MQTT won't connect → see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
