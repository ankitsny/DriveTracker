package com.drivetracker.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivetracker.data.model.DriveDataPoint
import com.drivetracker.data.model.DriveSession
import com.drivetracker.data.repo.DriveRepository
import com.drivetracker.ui.theme.AccentTeal
import com.drivetracker.ui.theme.DarkBackground
import com.drivetracker.ui.theme.TextPrimary
import com.drivetracker.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {
    private val _points = MutableStateFlow<List<DriveDataPoint>>(emptyList())
    val points: StateFlow<List<DriveDataPoint>> = _points

    private val _session = MutableStateFlow<DriveSession?>(null)
    val session: StateFlow<DriveSession?> = _session

    fun loadTrip(sessionId: Long) {
        viewModelScope.launch {
            repository.getSessionById(sessionId).collect { s ->
                _session.value = s
            }
        }
        viewModelScope.launch {
            repository.getSessionDataPoints(sessionId).collect { p ->
                _points.value = p
            }
        }
    }

    fun deleteTrip(session: DriveSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId) {
        viewModel.loadTrip(sessionId)
    }
    
    val points by viewModel.points.collectAsState()
    val session by viewModel.session.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = {
                            viewModel.deleteTrip(session!!)
                            onBack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Trip", tint = Color(0xFFFF3B30))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (session == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val s = session!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                Text(dateFormat.format(Date(s.timestamp)), color = TextSecondary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(24.dp))
                
                // Primary Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailStat("Distance", "${"%.1f".format(s.distanceKm)} km")
                    
                    val hours = s.durationMs / 3_600_000
                    val minutes = (s.durationMs % 3_600_000) / 60_000
                    val durationString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    DetailStat("Duration", durationString)
                    
                    val avgHours = s.durationMs / 3600000f
                    val avgSpeed = if (avgHours > 0.001f) s.distanceKm / avgHours else 0f
                    DetailStat("Avg Speed", "${"%.1f".format(avgSpeed)} km/h")
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Secondary Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailStat("Top Speed", "${s.topSpeedKmh.toInt()} km/h")
                    DetailStat("Left Turns", "${s.leftTurns}")
                    DetailStat("Right Turns", "${s.rightTurns}")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailStat("Max Accel", "${"%.1f".format(s.maxAccelerationMs2)} m/s²")
                    DetailStat("Max Decel", "${"%.1f".format(s.maxDecelerationMs2)} m/s²")
                    DetailStat("Peak G", "${"%.2f".format(s.peakGForce)} G")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val stoppedMins = s.stoppedTimeMs / 60000
                    DetailStat("Stopped Time", "${stoppedMins}m")
                    DetailStat("Top Corner", "${s.topCornerSpeedKmh.toInt()} km/h")
                    
                    val sizeKb = (points.size * 40) / 1024
                    DetailStat("Data Size", "${sizeKb} KB")
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Map visualization
                val mapPoints = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                if (mapPoints.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        Text("Route Map", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(16.dp))
                        AndroidView(
                            factory = { context ->
                                MapView(context).apply {
                                    setMultiTouchControls(true)
                                }
                            },
                            update = { view ->
                                view.overlays.clear()
                                val polyline = Polyline()
                                val geoPoints = mapPoints.map { GeoPoint(it.latitude, it.longitude) }
                                polyline.setPoints(geoPoints)
                                polyline.color = android.graphics.Color.parseColor("#0A84FF")
                                polyline.width = 12f
                                view.overlays.add(polyline)
                                
                                val startMarker = Marker(view).apply {
                                    position = geoPoints.first()
                                    title = "Start"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                view.overlays.add(startMarker)
                                
                                val endMarker = Marker(view).apply {
                                    position = geoPoints.last()
                                    title = "End"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                view.overlays.add(endMarker)

                                mapPoints.filter { it.isHardBrake }.forEach { p ->
                                    val m = Marker(view).apply {
                                        position = GeoPoint(p.latitude, p.longitude)
                                        title = "Hard Brake"
                                    }
                                    view.overlays.add(m)
                                }
                                mapPoints.filter { it.isSharpTurn }.forEach { p ->
                                    val m = Marker(view).apply {
                                        position = GeoPoint(p.latitude, p.longitude)
                                        title = "Sharp Turn"
                                    }
                                    view.overlays.add(m)
                                }

                                view.controller.setZoom(15.0)
                                view.controller.setCenter(geoPoints.first())
                                view.invalidate()
                            },
                            modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Charts
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text("Speed over Time (km/h)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    val speeds = points.map { Pair(it.timestamp, it.speedKmh) }
                    AdvancedLineChart(data = speeds, color = AccentTeal, modifier = Modifier.fillMaxWidth().height(200.dp))
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text("Acceleration (G-Force)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    val forces = points.map { Pair(it.timestamp, it.gForce) }
                    AdvancedLineChart(data = forces, color = Color(0xFFFF3B30), modifier = Modifier.fillMaxWidth().height(200.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text("Elevation (meters)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    val elevations = points.map { Pair(it.timestamp, it.altitude.toFloat()) }
                    AdvancedLineChart(data = elevations, color = Color(0xFFFF9500), modifier = Modifier.fillMaxWidth().height(200.dp))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                if (points.isEmpty()) {
                    Text("No telemetry data for this trip.", color = TextSecondary)
                } else {
                    Text("${points.size} telemetry points recorded.", color = TextSecondary)
                }
                
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AdvancedLineChart(data: List<Pair<Long, Float>>, color: Color, modifier: Modifier) {
    if (data.isEmpty()) {
        Box(modifier = modifier.background(Color(0xFF1C1C1E)))
        return
    }
    
    // Downsample data to prevent hardware crash for Path complexity
    val chartData = if (data.size > 150) {
        val step = data.size / 100
        data.filterIndexed { index, _ -> index % step == 0 || index == data.lastIndex }
    } else data

    val textMeasurer = rememberTextMeasurer()
    val maxVal = chartData.maxOfOrNull { it.second }?.coerceAtLeast(1f) ?: 1f
    val minVal = chartData.minOfOrNull { it.second }?.coerceAtMost(0f) ?: 0f
    val minTime = chartData.first().first
    val maxTime = chartData.last().first
    
    val textStyle = TextStyle(color = TextSecondary, fontSize = 10.sp)

    Canvas(modifier = modifier.background(Color(0xFF1C1C1E)).padding(top = 16.dp, start = 8.dp, end = 16.dp)) {
        try {
            val insetX = 40.dp.toPx()
            val insetBottom = 20.dp.toPx()
            val width = (size.width - insetX).coerceAtLeast(1f)
            val height = (size.height - insetBottom).coerceAtLeast(1f)
            val range = (maxVal - minVal).coerceAtLeast(0.001f)

            // X and Y axes
            drawLine(
                color = Color.DarkGray,
                start = androidx.compose.ui.geometry.Offset(insetX, height),
                end = androidx.compose.ui.geometry.Offset(size.width, height),
                strokeWidth = 2f
            )
            drawLine(
                color = Color.DarkGray,
                start = androidx.compose.ui.geometry.Offset(insetX, 0f),
                end = androidx.compose.ui.geometry.Offset(insetX, height),
                strokeWidth = 2f
            )

            val stepX = width / (chartData.size - 1).coerceAtLeast(1).toFloat()
            val path = Path()
            chartData.forEachIndexed { index, pair ->
                val x = insetX + (index * stepX)
                val y = height - ((pair.second - minVal) / range) * height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 4f))

            if (textMeasurer != null) {
                // Draw Max/Min Y
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${maxVal.toInt()}",
                    style = textStyle,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, -10.dp.toPx())
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${minVal.toInt()}",
                    style = textStyle,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, height - 10.dp.toPx())
                )

                val format = java.text.SimpleDateFormat("mm:ss", java.util.Locale.getDefault())

                // X-axis time grid
                val numLabels = 4
                for (i in 1 until numLabels) {
                    val fraction = i.toFloat() / numLabels
                    val gridX = insetX + fraction * width
                    drawLine(
                        color = Color.DarkGray.copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(gridX, 0f),
                        end = androidx.compose.ui.geometry.Offset(gridX, height),
                        strokeWidth = 1f
                    )
                    
                    val time = minTime + (fraction * (maxTime - minTime)).toLong()
                    val timeStr = format.format(java.util.Date(time))
                    val layout = textMeasurer.measure(timeStr, style = textStyle)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeStr,
                        style = textStyle,
                        topLeft = androidx.compose.ui.geometry.Offset(gridX - layout.size.width / 2, height + 4.dp.toPx())
                    )
                }

                // Draw Start/End X (Time)
                val startStr = format.format(java.util.Date(minTime))
                val endStr = format.format(java.util.Date(maxTime))
                
                drawText(
                    textMeasurer = textMeasurer,
                    text = startStr,
                    style = textStyle,
                    topLeft = androidx.compose.ui.geometry.Offset(insetX, height + 4.dp.toPx())
                )
                
                val endTextLayout = textMeasurer.measure(endStr, style = textStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = endStr,
                    style = textStyle,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - endTextLayout.size.width, height + 4.dp.toPx())
                )
            }
        } catch (e: Exception) {
            // Silently catch layout/hardware exceptions
        }
    }
}
