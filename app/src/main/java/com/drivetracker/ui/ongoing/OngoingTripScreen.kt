package com.drivetracker.ui.ongoing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivetracker.data.model.LiveDriveData
import com.drivetracker.ui.theme.AccentTeal
import com.drivetracker.ui.theme.CardBackground
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
    val safetyGradeColor = when {
        liveData.safetyScore >= 90 -> Color(0xFF34C759)
        liveData.safetyScore >= 75 -> Color(0xFF30D158)
        liveData.safetyScore >= 60 -> Color(0xFFFF9500)
        liveData.safetyScore >= 45 -> Color(0xFFFF6B00)
        else -> Color(0xFFFF3B30)
    }
    val safetyGrade = when {
        liveData.safetyScore >= 90 -> "A"
        liveData.safetyScore >= 75 -> "B"
        liveData.safetyScore >= 60 -> "C"
        liveData.safetyScore >= 45 -> "D"
        else -> "F"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ongoing Trip", color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (isPaused) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF9500).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("PAUSED", color = Color(0xFFFF9500), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                actions = {
                    // Live safety score in header
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(safetyGradeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(safetyGrade, color = safetyGradeColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
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
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Resume Trip" else "Pause Trip",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                FloatingActionButton(
                    onClick = onStopTrip,
                    containerColor = Color(0xFFFF3B30),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop Trip",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big speed display
            Text(
                text = "${liveData.speedKmh.toInt()}",
                color = if (isPaused) TextSecondary else TextPrimary,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold
            )
            Text("km/h", color = TextSecondary, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(24.dp))

            // Primary stats
            LiveStatRow {
                LiveStat("Distance", "${"%.2f".format(liveData.distanceKm)} km")
                val h = liveData.durationMs / 3_600_000
                val m = (liveData.durationMs % 3_600_000) / 60_000
                val s = (liveData.durationMs % 60_000) / 1_000
                LiveStat("Duration", if (h > 0) "${h}h ${m}m" else "${m}m ${s}s")
                LiveStat("Avg Speed", "${"%.1f".format(liveData.avgSpeedKmh)} km/h")
            }
            Spacer(Modifier.height(12.dp))

            // Turn & stop stats
            LiveStatRow {
                LiveStat("Stops", "${liveData.stopsCount}")
                LiveStat("Left Turns", "${liveData.leftTurns}")
                LiveStat("Right Turns", "${liveData.rightTurns}")
            }
            Spacer(Modifier.height(12.dp))

            // G-force & accel stats
            LiveStatRow {
                LiveStat("Max Accel", "${"%.1f".format(liveData.maxAccelMs2)} m/s²")
                LiveStat("Max Decel", "${"%.1f".format(liveData.maxDecelMs2)} m/s²")
                LiveStat("Peak G", "${"%.2f".format(liveData.peakGForce)} G")
            }
            Spacer(Modifier.height(12.dp))

            // Lane changes + corner speed
            LiveStatRow {
                val stoppedMins = liveData.stoppedTimeMs / 60000
                LiveStat("Stopped", "${stoppedMins}m")
                LiveStat("Hard Brakes", "${liveData.brakeEvents}")
                LiveStat("Top Corner", "${liveData.topCornerSpeedKmh.toInt()} km/h")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Safety score card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Safety Score", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${liveData.safetyScore}/100",
                            color = safetyGradeColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(safetyGradeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(safetyGrade, color = safetyGradeColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp)) // Padding for FABs
        }
    }
}

@Composable
fun LiveStatRow(content: @Composable RowScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content
        )
    }
}

@Composable
fun LiveStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
