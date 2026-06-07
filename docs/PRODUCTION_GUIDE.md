# Production / Release Guide

How to produce a signed release build, plus an honest description of the operational scaffolding in
the `production/` package. This is an academic project — "production" here means *release-mode
build*, not a certified deployment.

---

## Release build

### 1. Signing config

Release signing is driven by `keystore.properties` at the repo root. It is **git-ignored**; a
redacted template ships as `keystore.properties.example`.

```bash
# 1. Create a keystore (once)
keytool -genkeypair -v \
  -keystore keystore/car-crash-detection.keystore \
  -alias car-crash-detection-key \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. Copy the template and fill in your values
cp keystore.properties.example keystore.properties
#    edit storeFile / storePassword / keyAlias / keyPassword
```

```properties
# keystore.properties (DO NOT COMMIT)
storeFile=../keystore/car-crash-detection.keystore
storePassword=********
keyAlias=car-crash-detection-key
keyPassword=********
```

If `keystore.properties` is absent or the keystore file is missing, the release build **falls back
to debug signing automatically** (see the `signingConfigs`/`buildTypes` logic in
`app/build.gradle.kts`).

### 2. Build

```bash
./gradlew clean
./gradlew assembleRelease          # → app/build/outputs/apk/release/
# Windows: scripts\build_production.bat   (falls back to debug if no keystore)
```

### 3. Build types

| Type | Minify / shrink | Logging | Debug features |
|------|-----------------|---------|----------------|
| `debug` | off | on | on |
| `release` | R8 + resource shrinking | off | off |

Release also disables logging and debug features via `BuildConfig` flags and applies the rules in
`app/proguard-rules.pro` (which keep Paho MQTT, Room, Lottie, CameraX, and the `AndroidXMqttClient`
wrapper). The module is also configured for **16 KB page-size** compatibility (ABI filters, native
libs not extracted).

---

## Operational scaffolding (`production/`)

> **Honest scope note.** The classes below are implemented as structured backends, but **not all of
> their flows are wired into shipping UI**. Treat them as extension points and a record of intent,
> not finished, button-by-button features. `ProductionDashboardFragment` is the entry surface.

| Class | What it provides |
|-------|------------------|
| `ProductionMonitor` | Periodic sampling of memory, battery, storage, and network; performance and usage metrics; a `SystemStatusReport` exporter. |
| `MaintenanceManager` | Uptime / update / maintenance bookkeeping. |
| `InstallationManager` | First-run initialization (directories, default config, demo data), validation, and factory reset. |
| `demo/DemoScenarioManager` | Models multi-step demonstration scenarios for presentations. |
| `testing/IntegrationTestSuite` | In-app self-tests across DB / MQTT / GPS / ESP32 and an end-to-end scenario. |

If you extend the app, these give you a place to hang real monitoring, scheduled maintenance, or
guided demos.

---

## Pre-demo checklist

- [ ] Release (or debug) APK installed on the demo device(s)
- [ ] Mosquitto broker running and reachable; IP set in **MQTT Settings**
- [ ] Both phones on the same network; permissions granted
- [ ] (Optional) ESP32 node powered and advertising as `ESP32_CarCrash`
- [ ] Publisher → Subscriber alert verified once before presenting

---

## Security note

Because this targets a local lab broker, the sample Mosquitto config allows anonymous connections
and medical/location data travels unencrypted. For any real use: enable **TLS** and **broker
authentication**, restrict the broker to a trusted network, and treat the medical profile data as
sensitive (encrypt at rest, limit retention).

---

**App version:** 1.1.0 (versionCode 2) · **Min/Target SDK:** 24 / 34
