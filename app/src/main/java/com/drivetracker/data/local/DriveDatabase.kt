package com.drivetracker.data.local

import androidx.room.*
import com.drivetracker.data.model.DriveDataPoint
import com.drivetracker.data.model.DriveSession
import com.drivetracker.data.model.DriveStats
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DriveSession): Long

    @Query("SELECT * FROM drive_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<DriveSession>>

    @Query("SELECT * FROM drive_sessions ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSession(): DriveSession?

    @Query("SELECT * FROM drive_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionById(sessionId: Long): Flow<DriveSession?>

    @Query("""
        SELECT 
            COALESCE(SUM(distanceKm), 0) as totalDistanceKm,
            COALESCE(SUM(durationMs), 0) as totalDurationMs,
            COALESCE(SUM(stoppedTimeMs), 0) as totalStoppedTimeMs,
            COUNT(*) as totalTrips,
            COALESCE(MAX(topSpeedKmh), 0) as topSpeedKmh,
            COALESCE(MIN(CASE WHEN best0to100Ms > 0 THEN best0to100Ms END), 0) as best0to100Ms,
            COALESCE(SUM(leftTurns), 0) as totalLeftTurns,
            COALESCE(SUM(rightTurns), 0) as totalRightTurns,
            COALESCE(SUM(brakeEvents), 0) as totalBrakeEvents,
            COALESCE(SUM(laneChanges), 0) as totalLaneChanges,
            COALESCE(MAX(maxDecelerationMs2), 0) as maxDecelerationMs2,
            COALESCE(MAX(maxAccelerationMs2), 0) as maxAccelerationMs2,
            COALESCE(MAX(peakGForce), 0) as peakGForce,
            COALESCE(MAX(topCornerSpeedKmh), 0) as topCornerSpeedKmh,
            COALESCE(AVG(safetyScore), 100) as avgSafetyScore
        FROM drive_sessions
    """)
    fun getAggregatedStats(): Flow<AggregatedStats>

    @Delete
    suspend fun deleteSession(session: DriveSession)

    @Update
    suspend fun updateSession(session: DriveSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoints(points: List<DriveDataPoint>)

    @Query("SELECT * FROM drive_data_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getDataPointsForSession(sessionId: Long): Flow<List<DriveDataPoint>>
}

data class AggregatedStats(
    val totalDistanceKm: Float,
    val totalDurationMs: Long,
    val totalStoppedTimeMs: Long,
    val totalTrips: Int,
    val topSpeedKmh: Float,
    val best0to100Ms: Long,
    val totalLeftTurns: Int,
    val totalRightTurns: Int,
    val totalBrakeEvents: Int,
    val totalLaneChanges: Int,
    val maxDecelerationMs2: Float,
    val maxAccelerationMs2: Float,
    val peakGForce: Float,
    val topCornerSpeedKmh: Float,
    val avgSafetyScore: Float = 100f
)

@Database(
    entities = [DriveSession::class, DriveDataPoint::class],
    version = 5,
    exportSchema = false
)
abstract class DriveDatabase : RoomDatabase() {
    abstract fun driveSessionDao(): DriveSessionDao

    companion object {
        const val DATABASE_NAME = "drive_tracker_db"
    }
}
