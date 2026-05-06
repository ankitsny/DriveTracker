package com.drivetracker.sensors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.drivetracker.MainActivity
import com.drivetracker.data.model.DriveDataPoint
import com.drivetracker.data.model.DriveSession
import com.drivetracker.data.model.LiveDriveData
import com.drivetracker.data.repo.DriveRepository
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

@AndroidEntryPoint
class DriveTrackingService : Service(), SensorEventListener {

    @Inject lateinit var repository: DriveRepository

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var locationHandlerThread: HandlerThread

    // Session tracking state
    private var sessionStartTime = 0L
    private var lastLocation: Location? = null
    private var totalDistanceM = 0f
    private var stoppedTimeMs = 0L
    private var lastStopStart = 0L
    private var isStopped = true
    private var stopsCount = 0
    private var stopCandidateStart = 0L   // time we first dropped below STOP_ENTRY_KMH
    private var topSpeedKmh = 0f
    private var topCornerSpeedKmh = 0f

    // Stop detection thresholds
    private val STOP_ENTRY_KMH = 3f        // below this → candidate for stop
    private val STOP_EXIT_KMH  = 7f        // above this → definitely moving again
    private val STOP_MIN_MS    = 4_000L    // must hold still for 4 s to count as a stop

    // Accelerometer state
    private var maxAccelMs2 = 0f
    private var maxDecelMs2 = 0f
    private var peakGForce = 0f
    private var currentLinAccel = 0f
    private var lastSpeedForAccelCheck = 0f
    private var lastTimeForAccelCheck = 0L
    private var lastBrakeEventTime = 0L

    private var pendingHardBrake = false
    private var pendingSharpTurn = false

    private var pauseStartTime = 0L
    private var totalPausedTimeMs = 0L

    // 0-100 tracking
    private var was0 = false
    private var accelStartTime = 0L
    private var best0to100Ms = 0L

    // Maneuvers
    private var leftTurns = 0
    private var rightTurns = 0
    private var brakeEvents = 0

    // Gyroscope
    private var lastGyroZ = 0f
    private var inTurn = false
    private var turnDirection = 0

    private val TURN_THRESHOLD = 0.8f

    // Ring buffer for live charts (max 200 points, in RAM)
    private val recentPoints = ArrayDeque<DriveDataPoint>(200)
    private val CHART_BUFFER_SIZE = 200

    // Batch flush buffer for DB streaming writes
    private val pendingFlush = mutableListOf<DriveDataPoint>()
    private val FLUSH_BATCH_SIZE = 30

    // Session ID resolved asynchronously after DB insert
    private var sessionIdDeferred: CompletableDeferred<Long>? = null

    inner class LocalBinder : Binder() {
        fun getService(): DriveTrackingService = this@DriveTrackingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationHandlerThread = HandlerThread("LocationHandlerThread").also { it.start() }
        createNotificationChannel()
    }

    fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true
        sessionStartTime = System.currentTimeMillis()
        lastStopStart = sessionStartTime
        totalPausedTimeMs = 0L
        _isPaused.value = false
        recentPoints.clear()
        pendingFlush.clear()

        // Insert a skeletal session immediately to get sessionId
        val deferred = CompletableDeferred<Long>()
        sessionIdDeferred = deferred
        serviceScope.launch {
            val id = repository.saveSession(DriveSession(tripName = generateTripName()))
            deferred.complete(id)
        }

        startForeground(NOTIFICATION_ID, buildNotification(0f, 0f))
        startLocationUpdates()
        startSensorUpdates()
    }

    fun stopTrackingAndSave() {
        if (!_isTracking.value) return
        if (_isPaused.value) {
            totalPausedTimeMs += (System.currentTimeMillis() - pauseStartTime)
        }
        if (isStopped && lastStopStart > 0L) {
            stoppedTimeMs += (System.currentTimeMillis() - lastStopStart)
        }
        _isTracking.value = false
        _isPaused.value = false
        stopLocationUpdates()
        stopSensorUpdates()

        val durationMs = System.currentTimeMillis() - sessionStartTime - totalPausedTimeMs
        val score = calculateSafetyScore()
        val tripName = generateTripName()

        val finalSession = DriveSession(
            distanceKm = totalDistanceM / 1000f,
            durationMs = durationMs,
            stoppedTimeMs = stoppedTimeMs,
            topSpeedKmh = topSpeedKmh,
            leftTurns = leftTurns,
            rightTurns = rightTurns,
            brakeEvents = brakeEvents,
            maxDecelerationMs2 = maxDecelMs2,
            maxAccelerationMs2 = maxAccelMs2,
            peakGForce = peakGForce,
            topCornerSpeedKmh = topCornerSpeedKmh,
            best0to100Ms = best0to100Ms,
            safetyScore = score,
            tripName = tripName
        )

        // Snapshot mutable state NOW before resetSessionState() wipes it
        val deferredSnapshot  = sessionIdDeferred
        val remainingPoints   = pendingFlush.toList()

        resetSessionState()          // safe to reset — snapshots above are captured
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Flush remaining points then write the final session record
        serviceScope.launch {
            val sessionId = deferredSnapshot?.await() ?: return@launch
            if (remainingPoints.isNotEmpty()) {
                repository.saveDataPoints(remainingPoints.map { it.copy(sessionId = sessionId) })
            }
            repository.updateSession(finalSession.copy(id = sessionId))
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (_isPaused.value) return
            val location = result.lastLocation ?: return
            val speedKmh = location.speed * 3.6f

            // Top speed
            if (speedKmh > topSpeedKmh) topSpeedKmh = speedKmh

            // Distance logic - filter GPS drift
            if (location.hasAccuracy() && location.accuracy < 20f && speedKmh > 3f) {
                lastLocation?.let { prev ->
                    val dist = prev.distanceTo(location)
                    if (dist > 1f) totalDistanceM += dist
                }
                lastLocation = location
            } else if (speedKmh > 3f) {
                lastLocation = location
            }

            val now = System.currentTimeMillis()
            val currentDuration = now - sessionStartTime - totalPausedTimeMs

            // Stop detection — minimum duration + hysteresis
            // Entry: < STOP_ENTRY_KMH for at least STOP_MIN_MS  → confirmed stop
            // Exit:  > STOP_EXIT_KMH                            → moving again
            if (speedKmh < STOP_ENTRY_KMH) {
                if (!isStopped) {
                    if (stopCandidateStart == 0L) stopCandidateStart = now
                    if (now - stopCandidateStart >= STOP_MIN_MS) {
                        isStopped = true
                        lastStopStart = stopCandidateStart
                        stopsCount++
                        stopCandidateStart = 0L
                    }
                }
            } else if (speedKmh > STOP_EXIT_KMH) {
                stopCandidateStart = 0L
                if (isStopped && lastStopStart > 0L) {
                    stoppedTimeMs += (now - lastStopStart)
                    lastStopStart = 0L
                }
                isStopped = false
            }

            // Stream point to DB: add to chart ring buffer + flush buffer
            val point = DriveDataPoint(
                sessionId = 0, // Will be replaced on flush with real ID
                timestamp = now,
                speedKmh = speedKmh,
                linAccel = currentLinAccel,
                gForce = currentLinAccel / SensorManager.GRAVITY_EARTH,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                isHardBrake = pendingHardBrake,
                isSharpTurn = pendingSharpTurn
            )
            pendingHardBrake = false
            pendingSharpTurn = false

            // Ring buffer — only last 200 points kept in RAM for charts
            if (recentPoints.size >= CHART_BUFFER_SIZE) recentPoints.removeFirst()
            recentPoints.addLast(point)

            // Batch flush to DB — streams ALL points to disk, no eviction
            pendingFlush.add(point)
            if (pendingFlush.size >= FLUSH_BATCH_SIZE) {
                val toFlush = pendingFlush.toList()
                pendingFlush.clear()
                serviceScope.launch {
                    val sessionId = sessionIdDeferred?.await() ?: return@launch
                    repository.saveDataPoints(toFlush.map { it.copy(sessionId = sessionId) })
                }
            }

            // 0-100 tracking
            if (speedKmh < 2f) {
                was0 = true
                accelStartTime = now
            } else if (was0 && speedKmh >= 100f) {
                val elapsed = now - accelStartTime
                if (best0to100Ms == 0L || elapsed < best0to100Ms) best0to100Ms = elapsed
                was0 = false
            }

            // Top corner speed
            if (speedKmh > topCornerSpeedKmh && peakGForce > 0.3f && inTurn) {
                topCornerSpeedKmh = speedKmh
            }

            // Build live snapshot from ring buffer
            val speedList  = recentPoints.map { it.speedKmh }
            val gForceList = recentPoints.map { it.gForce }
            val score = calculateSafetyScore()

            _liveData.value = LiveDriveData(
                speedKmh = speedKmh,
                distanceKm = totalDistanceM / 1000f,
                durationMs = currentDuration,
                stoppedTimeMs = stoppedTimeMs,
                stopsCount = stopsCount,
                leftTurns = leftTurns,
                rightTurns = rightTurns,
                brakeEvents = brakeEvents,
                maxAccelMs2 = maxAccelMs2,
                maxDecelMs2 = maxDecelMs2,
                peakGForce = peakGForce,
                topCornerSpeedKmh = topCornerSpeedKmh,
                safetyScore = score,
                isTracking = true,
                speedHistory = speedList,
                gForceHistory = gForceList
            )

            // Update foreground notification with live speed
            updateNotification(speedKmh, totalDistanceM / 1000f)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (_isPaused.value) return
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> handleAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val linAccel = sqrt(x * x + y * y + z * z)
        currentLinAccel = linAccel

        val currentSpeed = _liveData.value.speedKmh
        val speedDiff = currentSpeed - lastSpeedForAccelCheck

        val now = System.currentTimeMillis()
        if (now - lastTimeForAccelCheck > 1000) {
            lastSpeedForAccelCheck = currentSpeed
            lastTimeForAccelCheck = now
        }

        if (linAccel > 2f) {
            if (speedDiff > 0 && linAccel > maxAccelMs2) {
                maxAccelMs2 = linAccel
            } else if (speedDiff < 0 && linAccel > 6.0f) {
                if (linAccel > maxDecelMs2) maxDecelMs2 = linAccel
                if (now - lastBrakeEventTime > 2000) {
                    brakeEvents++
                    lastBrakeEventTime = now
                    pendingHardBrake = true
                }
            }
        }

        val gMag = linAccel / SensorManager.GRAVITY_EARTH
        if (gMag > peakGForce) peakGForce = gMag
    }

    private fun handleGyroscope(event: SensorEvent) {
        val gyroZ = event.values[2]
        val now = System.currentTimeMillis()

        // Turn detection
        // Android gyroZ: positive = counter-clockwise from above = left turn
        //                negative = clockwise from above = right turn
        if (!inTurn) {
            if (abs(gyroZ) > TURN_THRESHOLD) {
                inTurn = true
                turnDirection = if (gyroZ > 0) -1 else 1  // -1 = left, 1 = right
            }
        } else {
            if (abs(gyroZ) < TURN_THRESHOLD * 0.35f) {
                if (turnDirection < 0) leftTurns++ else rightTurns++
                inTurn = false
                turnDirection = 0
                pendingSharpTurn = true
            }
        }

        lastGyroZ = gyroZ
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                locationHandlerThread.looper  // Background thread, not main!
            )
        } catch (e: SecurityException) {}
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startSensorUpdates() {
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopSensorUpdates() {
        sensorManager.unregisterListener(this)
    }

    private fun calculateSafetyScore(): Int {
        var score = 100

        // Hard emergency brakes: -5 each, capped at -25
        score -= (brakeEvents * 5).coerceAtMost(25)

        // Peak lateral/total G-force tiers (only penalize genuinely aggressive forces)
        score -= when {
            peakGForce > 1.2f -> 20
            peakGForce > 0.9f -> 12
            peakGForce > 0.7f -> 5
            else -> 0
        }

        return score.coerceIn(0, 100)
    }

    fun getSafetyGrade(score: Int): String = when {
        score >= 90 -> "A"
        score >= 75 -> "B"
        score >= 60 -> "C"
        score >= 45 -> "D"
        else -> "F"
    }

    private fun generateTripName(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 6  -> "Night Drive"
            hour < 12 -> "Morning Drive"
            hour < 17 -> "Afternoon Drive"
            hour < 21 -> "Evening Drive"
            else      -> "Night Drive"
        }
    }

    private fun resetSessionState() {
        totalDistanceM = 0f
        stoppedTimeMs = 0L
        stopsCount = 0
        topSpeedKmh = 0f
        topCornerSpeedKmh = 0f
        maxAccelMs2 = 0f
        maxDecelMs2 = 0f
        peakGForce = 0f
        best0to100Ms = 0L
        leftTurns = 0
        rightTurns = 0
        brakeEvents = 0
        lastLocation = null
        was0 = false
        isStopped = true
        stopCandidateStart = 0L
        inTurn = false
        turnDirection = 0
        currentLinAccel = 0f
        lastSpeedForAccelCheck = 0f
        lastTimeForAccelCheck = 0L
        lastBrakeEventTime = 0L
        pendingHardBrake = false
        pendingSharpTurn = false
        lastGyroZ = 0f
        lastStopStart = 0L
        pauseStartTime = 0L
        totalPausedTimeMs = 0L
        recentPoints.clear()
        pendingFlush.clear()
        sessionIdDeferred = null
        _liveData.value = LiveDriveData()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Drive Tracking", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active drive tracking" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(speedKmh: Float, distanceKm: Float): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriveTracker — Active")
            .setContentText("${speedKmh.toInt()} km/h  •  ${"%.1f".format(distanceKm)} km")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(speedKmh: Float, distanceKm: Float) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(speedKmh, distanceKm))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopLocationUpdates()
        stopSensorUpdates()
        locationHandlerThread.quitSafely()
    }

    companion object {
        const val CHANNEL_ID = "drive_tracking_channel"
        const val NOTIFICATION_ID = 1001

        private val _liveData = MutableStateFlow(LiveDriveData())
        val liveData: StateFlow<LiveDriveData> = _liveData

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused
    }

    fun pauseTracking() {
        if (!_isPaused.value) {
            _isPaused.value = true
            pauseStartTime = System.currentTimeMillis()
        }
    }

    fun resumeTracking() {
        if (_isPaused.value) {
            _isPaused.value = false
            totalPausedTimeMs += (System.currentTimeMillis() - pauseStartTime)
        }
    }
}
