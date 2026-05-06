package com.drivetracker.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import com.drivetracker.sensors.DriveTrackingService
import kotlinx.coroutines.launch

class DriveCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return DriveCarSession()
    }
}

class DriveCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return DriveCarScreen(carContext)
    }
}

class DriveCarScreen(carContext: CarContext) : Screen(carContext) {
    init {
        lifecycleScope.launch {
            DriveTrackingService.liveData.collect {
                invalidate()
            }
        }
        lifecycleScope.launch {
            DriveTrackingService.isTracking.collect {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val isTracking = DriveTrackingService.isTracking.value
        val data = DriveTrackingService.liveData.value

        val row = Row.Builder()
            .setTitle(if (isTracking) "Live Speed: ${data.speedKmh.toInt()} km/h" else "DriveTracker Idle")
            .addText(if (isTracking) "Avg: ${data.avgSpeedKmh.toInt()} km/h | Dist: ${"%.1f".format(data.distanceKm)} km" else "Open the app on your phone to start.")
            .build()

        val pane = Pane.Builder().addRow(row).build()

        return PaneTemplate.Builder(pane)
            .setTitle("DriveTracker Dashboard")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
