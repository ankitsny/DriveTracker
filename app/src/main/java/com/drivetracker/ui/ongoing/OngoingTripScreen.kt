package com.drivetracker.ui.ongoing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivetracker.data.model.LiveDriveData
import com.drivetracker.ui.theme.AccentTeal
import com.drivetracker.ui.theme.DarkBackground
import com.drivetracker.ui.theme.TextPrimary
import com.drivetracker.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OngoingTripScreen(
    liveData: LiveDriveData,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onStopTrip: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ongoing Trip", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground,
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(
                    onClick = onPauseToggle,
                    containerColor = if (isPaused) Color(0xFF34C759) else Color(0xFFFF9500),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Resume Trip" else "Pause Trip",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                FloatingActionButton(
                    onClick = onStopTrip,
                    containerColor = Color(0xFFFF3B30),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop Trip",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Speed
            Text(
                text = "${liveData.speedKmh.toInt()}",
                color = TextPrimary,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold
            )
            Text("km/h", color = TextSecondary, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(32.dp))

            // Primary Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiveStat("Distance", "${"%.1f".format(liveData.distanceKm)} km")
                
                val hours = liveData.durationMs / 3_600_000
                val minutes = (liveData.durationMs % 3_600_000) / 60_000
                val durationString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                LiveStat("Duration", durationString)
                
                LiveStat("Avg Speed", "${"%.1f".format(liveData.avgSpeedKmh)} km/h")
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Secondary Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiveStat("Stops", "${liveData.stopsCount}")
                LiveStat("Left Turns", "${liveData.leftTurns}")
                LiveStat("Right Turns", "${liveData.rightTurns}")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiveStat("Max Accel", "${"%.1f".format(liveData.maxAccelMs2)} m/s²")
                LiveStat("Max Decel", "${"%.1f".format(liveData.maxDecelMs2)} m/s²")
                LiveStat("Peak G", "${"%.2f".format(liveData.peakGForce)} G")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val stoppedMins = liveData.stoppedTimeMs / 60000
                LiveStat("Stopped Time", "${stoppedMins}m")
                LiveStat("Top Corner", "${liveData.topCornerSpeedKmh.toInt()} km/h")
            }

            Spacer(modifier = Modifier.height(40.dp))

            Spacer(modifier = Modifier.height(100.dp)) // Padding for FAB
        }
    }
}

@Composable
fun LiveStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}


