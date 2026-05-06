# DriveTracker

> A real-time vehicle telemetry and trip tracking app for Android, with Android Auto support.

[![Android CI](https://github.com/YOUR_USERNAME/DriveTracker/actions/workflows/android.yml/badge.svg)](https://github.com/YOUR_USERNAME/DriveTracker/actions/workflows/android.yml)
[![Release](https://github.com/YOUR_USERNAME/DriveTracker/actions/workflows/release.yml/badge.svg)](https://github.com/YOUR_USERNAME/DriveTracker/releases)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-brightgreen)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF)](https://kotlinlang.org)

---

## Features

### Live Drive Tracking
- **Real-time speed** displayed large and clearly during an active trip
- **Foreground service** keeps tracking running reliably with the screen off
- **Pause / Resume** support — paused time is excluded from duration and stats
- **Stop detection** — automatically counts stops and measures idle time

### Telemetry & Maneuver Detection
- **Hard brake events** — detected when deceleration exceeds 6 m/s² (~0.6G) while slowing; shown live and pinned on the route map
- **Sharp turns** — gyroscope-based left/right turn detection (counted separately)
- **Peak G-force** — highest lateral/total force recorded across the trip
- **0–100 km/h sprint timer** — best time from standstill to 100 km/h
- **Top speed & top corner speed** — highest speed and highest speed recorded mid-corner

### Safety Score
Each trip is graded **A–F** based on driving behaviour:

| Factor | Penalty |
|--------|---------|
| Each hard brake | −5 pts (max −25) |
| Peak G > 0.7G | −5 pts |
| Peak G > 0.9G | −12 pts |
| Peak G > 1.2G | −20 pts |

A smooth, everyday drive comfortably scores an **A (90+)**.

### Trip History & Maps
- Full trip list sorted by date with grade badge, distance, duration and top speed
- Interactive **OSMDroid (OpenStreetMap)** route map with start/end markers and pins for every hard-brake and sharp-turn event
- Three **time-series charts** (Speed, G-Force, Elevation) with actual clock time on the X-axis

### Android Auto
- Native **Car App Library** UI displayed on the car's head unit
- Shows live speed, average speed, and total distance — optimised for in-car use

### Privacy First
All data is stored locally in a **Room database** on the device. No cloud accounts, no API keys, no analytics.

---

## Screenshots

| Ongoing Trip | Trip Detail | History | Profile |
|---|---|---|---|
| *(speed, stats, safety grade)* | *(map + charts)* | *(trip list)* | *(lifetime stats)* |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Persistence | Room + Flow |
| Location | Google Play Services — FusedLocationProvider |
| Sensors | Android `SensorManager` (accelerometer, gyroscope) |
| Maps | OSMDroid (OpenStreetMap, no API key) |
| Android Auto | AndroidX Car App Library |
| Async | Kotlin Coroutines + StateFlow |

---

## Architecture

```
DriveTrackingService (Foreground Service)
  ├── FusedLocationProviderClient  → speed, distance, GPS points
  ├── SensorManager (Accelerometer) → G-force, hard brakes
  └── SensorManager (Gyroscope)    → turn detection

DriveRepository
  └── Room DAO → DriveSession + DriveDataPoint

UI (Jetpack Compose)
  ├── OngoingTripScreen  ← LiveDriveData (StateFlow)
  ├── HistoryScreen      ← Flow<List<DriveSession>>
  ├── TripDetailScreen   ← Flow<DriveSession> + Flow<List<DriveDataPoint>>
  └── ProfileScreen      ← Flow<DriveStats> (aggregated)
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android device or emulator running API 26+
- For Android Auto: a head unit or the **Desktop Head Unit (DHU)** emulator

### Build & Run

**Using Android Studio**

1. Clone the repo:
   ```bash
   git clone https://github.com/YOUR_USERNAME/DriveTracker.git
   cd DriveTracker
   ```
2. Open the project in Android Studio.
3. Click **Run** — the app will request Location and Notification permissions on first launch.

**Using the Makefile**

```bash
make build      # compile debug APK
make install    # build + adb install on connected device
make release    # compile signed release APK (requires signing env vars)
make bundle     # build release AAB for Play Store upload
make clean      # wipe build outputs
make lint       # run Android lint
make test       # run unit tests
make check      # lint + tests together
make help       # list all targets
```

### Required Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS tracking |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `FOREGROUND_SERVICE` | Background trip tracking |
| `FOREGROUND_SERVICE_LOCATION` | Android 14+ foreground location |
| `POST_NOTIFICATIONS` | Persistent tracking notification |

---

## CI / CD

### Continuous Integration

Every push and pull request to `main` runs a full build via **GitHub Actions**:

```
.github/workflows/android.yml  →  ./gradlew build
```

### Release Workflow

Push a version tag — no secrets or setup required:

```bash
git tag v1.2.3
git push origin v1.2.3
```

The workflow (`.github/workflows/release.yml`) will:
1. Derive `versionCode` / `versionName` from the tag (`v1.2.3` → code `10203`, name `1.2.3`)
2. Build a release APK signed with the debug keystore (fine for GitHub sideloading)
3. Create a **GitHub Release** with auto-generated notes and the APK attached as `DriveTracker-v1.2.3.apk`
4. Mark as pre-release automatically if the tag contains `-` (e.g. `v1.0.0-beta`)

> **No secrets needed.** The APK is signed with the Android debug keystore so it can be installed on any device directly from the GitHub Releases page.

---

## License

MIT — see [LICENSE](LICENSE).
