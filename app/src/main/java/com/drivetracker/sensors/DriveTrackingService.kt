package com.drivetracker.sensors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
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
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager

    // Session tracking state
    private var sessionStartTime = 0L
    private var lastLocation: Location? = null
    private var totalDistanceM = 0f
    private var stoppedTimeMs = 0L
    private var lastStopStart = 0L
    private var isStopped = true
    private var stopsCount = 0
    private var topSpeedKmh = 0f
    private var topCornerSpeedKmh = 0f

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
    private var laneChanges = 0

    // Gyroscope
    private var lastGyroZ = 0f
    private var turnThreshold = 0.5f
    private var inTurn = false
    private var turnDirection = 0

    private val sessionDataPoints = mutableListOf<DriveDataPoint>()

    inner class LocalBinder : Binder() {
        fun getService(): DriveTrackingService = this@DriveTrackingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true
        sessionStartTime = System.currentTimeMillis()
        lastStopStart = sessionStartTime
        totalPausedTimeMs = 0L
        _isPaused.value = false
        sessionDataPoints.clear()
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        startSensorUpdates()
    }

    fun stopTrackingAndSave() {
        if (!_isTracking.value) return
        if (_isPaused.value) {
            totalPausedTimeMs += (System.currentTimeMillis() - pauseStartTime)
        }
        _isTracking.value = false
        _isPaused.value = false
        stopLocationUpdates()
        stopSensorUpdates()

        val durationMs = System.currentTimeMillis() - sessionStartTime - totalPausedTimeMs
        val session = DriveSession(
            distanceKm = totalDistanceM / 1000f,
            durationMs = durationMs,
            stoppedTimeMs = stoppedTimeMs,
            topSpeedKmh = topSpeedKmh,
            leftTurns = leftTurns,
            rightTurns = rightTurns,
            brakeEvents = brakeEvents,
            laneChanges = laneChanges,
            maxDecelerationMs2 = maxDecelMs2,
            maxAccelerationMs2 = maxAccelMs2,
            peakGForce = peakGForce,
            topCornerSpeedKmh = topCornerSpeedKmh,
            best0to100Ms = best0to100Ms
        )
        
        val pointsToSave = sessionDataPoints.toList()

        serviceScope.launch {
            val sessionId = repository.saveSession(session)
            val linkedPoints = pointsToSave.map { it.copy(sessionId = sessionId) }
            repository.saveDataPoints(linkedPoints)
        }
        
        resetSessionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (_isPaused.value) return

            val location = result.lastLocation ?: return
            val speedKmh = location.speed * 3.6f

            // Top speed
            if (speedKmh > topSpeedKmh) topSpeedKmh = speedKmh

            // Distance logic to prevent GPS drift
            if (location.hasAccuracy() && location.accuracy < 20f && speedKmh > 3f) {
                lastLocation?.let { prev ->
                    val dist = prev.distanceTo(location)
                    if (dist > 1f) {
                        totalDistanceM += dist
                    }
                }
                lastLocation = location
            } else if (speedKmh > 3f) {
                lastLocation = location
            }

            val now = System.currentTimeMillis()
            val currentDuration = now - sessionStartTime - totalPausedTimeMs

            // Save data point first
            sessionDataPoints.add(
                DriveDataPoint(
                    sessionId = 0,
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
            )
            pendingHardBrake = false
            pendingSharpTurn = false

            // Stop detection
            if (speedKmh < 3f) {
                if (!isStopped) {
                    isStopped = true
                    lastStopStart = now
                    stopsCount++
                }
                stoppedTimeMs += now - lastStopStart
                lastStopStart = now
            } else {
                isStopped = false
            }

            // Update live data for UI
            val speedList = sessionDataPoints.takeLast(120).map { it.speedKmh }
            val gForceList = sessionDataPoints.takeLast(120).map { it.gForce }

            _liveData.value = LiveDriveData(
                speedKmh = speedKmh,
                distanceKm = totalDistanceM / 1000f,
                durationMs = currentDuration,
                stoppedTimeMs = stoppedTimeMs,
                stopsCount = stopsCount,
                leftTurns = leftTurns,
                rightTurns = rightTurns,
                maxAccelMs2 = maxAccelMs2,
                maxDecelMs2 = maxDecelMs2,
                peakGForce = peakGForce,
                topCornerSpeedKmh = topCornerSpeedKmh,
                isTracking = true,
                speedHistory = speedList,
                gForceHistory = gForceList
            )

            if (speedKmh < 2f) {
                was0 = true
                accelStartTime = now
            } else if (was0 && speedKmh >= 100f) {
                val elapsed = now - accelStartTime
                if (best0to100Ms == 0L || elapsed < best0to100Ms) {
                    best0to100Ms = elapsed
                }
                was0 = false
            }

            // Top corner speed
            if (speedKmh > topCornerSpeedKmh && peakGForce > 0.3f && inTurn) {
                topCornerSpeedKmh = speedKmh
            }
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
            } else if (speedDiff < 0 && linAccel > 4.5f) {
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
        if (!inTurn) {
            if (abs(gyroZ) > 0.8f) {
                inTurn = true
                turnDirection = if (gyroZ > 0) 1 else -1
            }
        } else {
            if (abs(gyroZ) < 0.3f) {
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
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
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
        laneChanges = 0
        lastLocation = null
        was0 = false
        isStopped = true
        inTurn = false
        _liveData.value = LiveDriveData()
        sessionDataPoints.clear()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Drive Tracking", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active drive tracking" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriveTracker")
            .setContentText("Tracking your drive…")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopLocationUpdates()
        stopSensorUpdates()
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
