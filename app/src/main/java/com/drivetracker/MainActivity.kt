package com.drivetracker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drivetracker.data.model.LiveDriveData
import com.drivetracker.sensors.DriveTrackingService
import com.drivetracker.ui.history.HistoryScreen
import com.drivetracker.ui.history.TripDetailScreen
import com.drivetracker.ui.ongoing.OngoingTripScreen
import com.drivetracker.ui.profile.ProfileScreen
import com.drivetracker.ui.theme.DarkBackground
import com.drivetracker.ui.theme.DriveTrackerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var trackingService by mutableStateOf<DriveTrackingService?>(null)
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as DriveTrackingService.LocalBinder
            trackingService = localBinder.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            trackingService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        enableEdgeToEdge()
        setContent {
            DriveTrackerTheme {
                val liveData by DriveTrackingService.liveData.collectAsState()
                val isTracking by DriveTrackingService.isTracking.collectAsState()

                DriveTrackerApp(
                    isTracking = isTracking,
                    liveData = liveData,
                    onStartTracking = { startTracking() },
                    onStopTracking = { stopTracking() },
                    onPauseTracking = { trackingService?.pauseTracking() },
                    onResumeTracking = { trackingService?.resumeTracking() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, DriveTrackingService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            trackingService = null
        }
    }

    private fun startTracking() {
        val intent = Intent(this, DriveTrackingService::class.java)
        startForegroundService(intent)
        trackingService?.startTracking()
    }

    private fun stopTracking() {
        trackingService?.stopTrackingAndSave()
        val intent = Intent(this, DriveTrackingService::class.java)
        stopService(intent)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DriveTrackerApp(
    isTracking: Boolean,
    liveData: LiveDriveData,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onPauseTracking: () -> Unit,
    onResumeTracking: () -> Unit
) {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val locationPermissions = rememberMultiplePermissionsState(permissions = permissions)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (!locationPermissions.allPermissionsGranted) {
                PermissionScreen(onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() })
            } else {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "profile") {
                    composable("profile") {
                        ProfileScreen(
                            liveSpeed = liveData.speedKmh,
                            isTracking = isTracking,
                            onContinue = { navController.navigate("history") },
                            onStartTrip = {
                                onStartTracking()
                                navController.navigate("ongoing")
                            },
                            onResumeTrip = {
                                navController.navigate("ongoing")
                            }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            onBack = { navController.popBackStack() },
                            onTripClick = { sessionId ->
                                navController.navigate("trip_detail/$sessionId")
                            }
                        )
                    }
                    composable("ongoing") {
                        val isPaused by DriveTrackingService.isPaused.collectAsState()
                        OngoingTripScreen(
                            liveData = liveData,
                            isPaused = isPaused,
                            onPauseToggle = {
                                if (isPaused) onResumeTracking()
                                else onPauseTracking()
                            },
                            onStopTrip = {
                                onStopTracking()
                                navController.popBackStack("profile", false)
                            }
                        )
                    }
                    composable("trip_detail/{sessionId}") { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                        TripDetailScreen(
                            sessionId = sessionId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Permission Required",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "DriveTracker needs location access to track your drives, measure speed, and calculate distance. It also needs notification access to run in the background.",
            color = Color(0xFF8E8E93),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
    }
}
