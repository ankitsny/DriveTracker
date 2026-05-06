package com.drivetracker.data.repo

import com.drivetracker.data.local.DriveSessionDao
import com.drivetracker.data.model.DriveDataPoint
import com.drivetracker.data.model.DriveSession
import com.drivetracker.data.model.DriveStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val dao: DriveSessionDao
) {
    fun getAggregatedStats(): Flow<DriveStats> {
        return dao.getAggregatedStats().map { agg ->
            DriveStats(
                totalDistanceKm = agg.totalDistanceKm,
                totalDurationMs = agg.totalDurationMs,
                totalStoppedTimeMs = agg.totalStoppedTimeMs,
                totalTrips = agg.totalTrips,
                topSpeedKmh = agg.topSpeedKmh,
                best0to100Ms = agg.best0to100Ms,
                totalLeftTurns = agg.totalLeftTurns,
                totalRightTurns = agg.totalRightTurns,
                totalBrakeEvents = agg.totalBrakeEvents,
                totalLaneChanges = agg.totalLaneChanges,
                maxDecelerationMs2 = agg.maxDecelerationMs2,
                maxAccelerationMs2 = agg.maxAccelerationMs2,
                peakGForce = agg.peakGForce,
                topCornerSpeedKmh = agg.topCornerSpeedKmh,
                avgSafetyScore = agg.avgSafetyScore.toInt()
            )
        }
    }

    fun getAllSessions(): Flow<List<DriveSession>> = dao.getAllSessions()

    fun getSessionById(sessionId: Long): Flow<DriveSession?> = dao.getSessionById(sessionId)

    fun getSessionDataPoints(sessionId: Long): Flow<List<DriveDataPoint>> = dao.getDataPointsForSession(sessionId)

    suspend fun saveSession(session: DriveSession): Long = dao.insertSession(session)

    suspend fun updateSession(session: DriveSession) = dao.updateSession(session)

    suspend fun saveDataPoints(points: List<DriveDataPoint>) = dao.insertDataPoints(points)

    suspend fun deleteSession(session: DriveSession) = dao.deleteSession(session)
}
