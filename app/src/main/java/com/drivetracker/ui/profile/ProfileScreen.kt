package com.drivetracker.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivetracker.ui.theme.AccentTeal
import com.drivetracker.ui.theme.CardBackground
import com.drivetracker.ui.theme.DarkBackground
import com.drivetracker.ui.theme.TextPrimary
import com.drivetracker.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    liveSpeed: Float = 0f,
    isTracking: Boolean = false,
    onContinue: () -> Unit = {},
    onStartTrip: () -> Unit = {},
    onResumeTrip: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val stats by viewModel.driveStats.collectAsState()

    val avgGrade = when {
        stats.avgSafetyScore >= 90 -> "A"
        stats.avgSafetyScore >= 75 -> "B"
        stats.avgSafetyScore >= 60 -> "C"
        stats.avgSafetyScore >= 45 -> "D"
        else -> "F"
    }
    val avgGradeColor = when (avgGrade) {
        "A" -> Color(0xFF34C759)
        "B" -> Color(0xFF30D158)
        "C" -> Color(0xFFFF9500)
        "D" -> Color(0xFFFF6B00)
        else -> Color(0xFFFF3B30)
    }

    Scaffold(containerColor = DarkBackground) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Dashboard", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Safety score banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(avgGradeColor.copy(alpha = 0.12f))
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Overall Safety Score", color = TextSecondary, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${stats.avgSafetyScore}/100",
                                    color = avgGradeColor,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Across ${stats.totalTrips} trips", color = TextSecondary, fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(avgGradeColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(avgGrade, color = avgGradeColor, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Stats row 1
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Total Distance", "%.1f".format(stats.totalDistanceKm), "km", Modifier.weight(1f))
                        StatCard("Top Speed", "${stats.topSpeedKmh.toInt()}", "km/h", Modifier.weight(1f))
                    }
                }

                // Stats row 2
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Drive Time", formatDuration(stats.totalDurationMs), "", Modifier.weight(1f))
                        StatCard("Total Trips", "${stats.totalTrips}", "trips", Modifier.weight(1f))
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Text("Driving Events", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    EventCard(
                        title = "Hard Brakes",
                        value = "${stats.totalBrakeEvents}",
                        description = "Sudden decelerations detected",
                        iconColor = Color(0xFFFF3B30)
                    )
                }

                item {
                    EventCard(
                        title = "Hard Brakes",
                        value = "${stats.totalBrakeEvents}",
                        description = "Emergency braking events detected",
                        iconColor = Color(0xFFFF9500)
                    )
                }

                item {
                    EventCard(
                        title = "Sharp Turns",
                        value = "${stats.totalLeftTurns + stats.totalRightTurns}",
                        description = "${stats.totalLeftTurns}L / ${stats.totalRightTurns}R detected",
                        iconColor = Color(0xFF0A84FF)
                    )
                }

                item {
                    EventCard(
                        title = "Fastest 0–100",
                        value = if (stats.best0to100Ms > 0) "%.1f s".format(stats.best0to100Ms / 1000f) else "--",
                        description = "Best acceleration time",
                        iconColor = Color(0xFF34C759)
                    )
                }
            }

            // Buttons pinned at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, DarkBackground, DarkBackground)
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { if (isTracking) onResumeTrip() else onStartTrip() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) Color(0xFF0A84FF) else Color(0xFF34C759)
                    )
                ) {
                    Icon(if (isTracking) Icons.Filled.PlayArrow else Icons.Filled.DirectionsCar, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isTracking) "Ongoing Trip — View" else "Start New Trip",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("View Trip History", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun StatCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = TextSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(unit, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
fun EventCard(title: String, value: String, description: String, iconColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconColor)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextSecondary, fontSize = 13.sp)
        }
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
