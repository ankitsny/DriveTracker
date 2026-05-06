package com.drivetracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivetracker.data.model.DriveSession
import com.drivetracker.ui.theme.CardBackground
import com.drivetracker.ui.theme.DarkBackground
import com.drivetracker.ui.theme.TextPrimary
import com.drivetracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onTripClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val sessions by viewModel.allSessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip History", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DirectionsCar, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No trips recorded yet.", color = TextSecondary, fontSize = 16.sp)
                    Text("Start your first drive!", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions) { session ->
                    TripCard(session, onClick = { onTripClick(session.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCard(session: DriveSession, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(session.timestamp))
    val hours = session.durationMs / 3_600_000
    val minutes = (session.durationMs % 3_600_000) / 60_000
    val durationString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    val displayName = session.tripName.ifBlank { dateString }
    val grade = when {
        session.safetyScore >= 90 -> "A"
        session.safetyScore >= 75 -> "B"
        session.safetyScore >= 60 -> "C"
        session.safetyScore >= 45 -> "D"
        else -> "F"
    }
    val gradeColor = when (grade) {
        "A" -> Color(0xFF34C759)
        "B" -> Color(0xFF30D158)
        "C" -> Color(0xFFFF9500)
        "D" -> Color(0xFFFF6B00)
        else -> Color(0xFFFF3B30)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = "Trip", tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = dateString, color = TextSecondary, fontSize = 12.sp)
                }
                // Safety grade badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(gradeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(grade, color = gradeColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TripStat("Distance", "${"%.1f".format(session.distanceKm)} km")
                TripStat("Duration", durationString)
                TripStat("Top Speed", "${session.topSpeedKmh.toInt()} km/h")
            }
        }
    }
}

@Composable
fun TripStat(label: String, value: String) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
