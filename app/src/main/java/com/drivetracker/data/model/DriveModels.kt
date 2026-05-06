package com.drivetracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "drive_sessions")
data class DriveSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val distanceKm: Float = 0f,
    val durationMs: Long = 0L,
    val stoppedTimeMs: Long = 0L,
    val topSpeedKmh: Float = 0f,
    val leftTurns: Int = 0,
    val rightTurns: Int = 0,
    val brakeEvents: Int = 0,
    val laneChanges: Int = 0,
    val maxDecelerationMs2: Float = 0f,
    val maxAccelerationMs2: Float = 0f,
    val peakGForce: Float = 0f,
    val topCornerSpeedKmh: Float = 0f,
    val best0to100Ms: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drive_data_points",
    foreignKeys = [ForeignKey(
        entity = DriveSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class DriveDataPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val speedKmh: Float,
    val linAccel: Float,
    val gForce: Float,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val isHardBrake: Boolean = false,
    val isSharpTurn: Boolean = false
)

data class DriveStats(
    val totalDistanceKm: Float = 0f,
    val totalDurationMs: Long = 0L,
    val totalStoppedTimeMs: Long = 0L,
    val totalTrips: Int = 0,
    val topSpeedKmh: Float = 0f,
    val best0to100Ms: Long = 0L,
    val totalLeftTurns: Int = 0,
    val totalRightTurns: Int = 0,
    val totalBrakeEvents: Int = 0,
    val totalLaneChanges: Int = 0,
    val maxDecelerationMs2: Float = 0f,
    val maxAccelerationMs2: Float = 0f,
    val peakGForce: Float = 0f,
    val topCornerSpeedKmh: Float = 0f,
    val currentSpeedKmh: Float = 0f
) {
    val leftTurnPercentage: Float
        get() {
            val total = totalLeftTurns + totalRightTurns
            return if (total == 0) 0.5f else totalLeftTurns.toFloat() / total
        }

    val formattedDuration: String
        get() = formatDuration(totalDurationMs)

    val formattedStoppedTime: String
        get() = formatDuration(totalStoppedTimeMs)

    val formattedBest0to100: String
        get() = if (best0to100Ms == 0L) "—" else "${best0to100Ms / 1000f}s"

    private fun formatDuration(ms: Long): String {
        if (ms == 0L) return "0m"
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}

data class LiveDriveData(
    val speedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val durationMs: Long = 0L,
    val stoppedTimeMs: Long = 0L,
    val stopsCount: Int = 0,
    val leftTurns: Int = 0,
    val rightTurns: Int = 0,
    val maxAccelMs2: Float = 0f,
    val maxDecelMs2: Float = 0f,
    val peakGForce: Float = 0f,
    val topCornerSpeedKmh: Float = 0f,
    val isTracking: Boolean = false,
    val speedHistory: List<Float> = emptyList(),
    val gForceHistory: List<Float> = emptyList()
) {
    val avgSpeedKmh: Float
        get() {
            val hours = durationMs / 3600000f
            return if (hours > 0.001f) distanceKm / hours else 0f
        }
}
