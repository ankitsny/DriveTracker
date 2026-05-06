# DriveTracker 🏎️

DriveTracker is a powerful Android application that turns your phone into an advanced vehicle telemetry and trip tracking system. It records your drives in real-time, plots your routes on interactive maps, and seamlessly connects to your car's built-in infotainment screen via Android Auto.

## Features

- **Advanced Telemetry Tracking**: Monitors G-forces, acceleration, braking events, sharp turns, and tracks 0-100 km/h sprint times using your device's internal gyroscope and accelerometer.
- **Trip History & Mapping**: Uses OSMDroid (OpenStreetMap) to visually map your past trips, dropping pins on your start/end points and highlighting exactly where hard braking or sharp turns occurred.
- **Android Auto Integration**: Run DriveTracker directly on your car's dashboard! A distraction-free, native automotive UI displays your Live Speed, Average Speed, and Total Distance right on your car's head unit.
- **Rich Data Visualization**: Beautiful, custom-built Jetpack Compose charts plot your Speed, G-Force, and Elevation across the duration of your trip.
- **Privacy First**: All data is recorded and stored locally on your device using a Room Database. No cloud subscriptions or API keys required.

## Technologies Used

- **Kotlin & Jetpack Compose**: 100% modern declarative UI.
- **Hilt**: Dependency Injection.
- **Room Database**: Local persistence.
- **OSMDroid**: Free, open-source map tiles and route drawing.
- **AndroidX Car App Library**: For native Android Auto support.

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and Run! 
   *Note: DriveTracker requires Location and Notification permissions to operate securely in the background while you drive.*
