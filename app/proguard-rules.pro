# DriveTracker ProGuard Rules

# --- App data models (Room entities must not be obfuscated) ---
-keep class com.drivetracker.data.** { *; }
-keep class com.drivetracker.sensors.** { *; }

# --- Hilt ---
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# --- Room ---
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# --- OSMDroid ---
-keep class org.osmdroid.** { *; }

# --- Android Auto (Car App Library) ---
-keep class androidx.car.app.** { *; }
-keep class com.drivetracker.auto.** { *; }

# --- Kotlin Coroutines / StateFlow ---
-keepnames class kotlinx.coroutines.** { *; }

# --- Suppress warnings for GMS location ---
-dontwarn com.google.android.gms.**
