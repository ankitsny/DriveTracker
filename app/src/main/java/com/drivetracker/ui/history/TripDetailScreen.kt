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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {
    private val _points  = MutableStateFlow<List<DriveDataPoint>>(emptyList())
    val points: StateFlow<List<DriveDataPoint>> = _points

    private val _session = MutableStateFlow<DriveSession?>(null)

    // Merge session record with stats computed from data points.
    // If the session was saved as a skeleton (all zeros due to old bug), we fill in
    // every recoverable field from the data-point stream instead.
    val session: StateFlow<DriveSession?> = combine(_session, _points) { s, pts ->
        if (s == null || pts.isEmpty()) return@combine s
        // Session already has real data — nothing to repair
        if (s.distanceKm > 0f || s.durationMs > 0L) return@combine s
        s.repairFromPoints(pts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun loadTrip(sessionId: Long) {
        viewModelScope.launch {
            repository.getSessionById(sessionId).collect { s -> _session.value = s }
        }
        viewModelScope.launch {
            repository.getSessionDataPoints(sessionId).collect { p -> _points.value = p }
        }
    }

    fun deleteTrip(session: DriveSession) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    private fun DriveSession.repairFromPoints(pts: List<DriveDataPoint>): DriveSession {
        val gps = pts.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        // Distance — sum of consecutive GPS point gaps
        var distM = 0f
        gps.zipWithNext { a, b ->
            val out = FloatArray(1)
            android.location.Location.distanceBetween(
                a.latitude, a.longitude, b.latitude, b.longitude, out
            )
            distM += out[0]
        }

        // Stopped time — sum of segments where speed < 3 km/h
        var stoppedMs = 0L
        pts.zipWithNext { a, b ->
            if (a.speedKmh < 3f && b.speedKmh < 3f) stoppedMs += (b.timestamp - a.timestamp)
        }

        return copy(
            distanceKm       = distM / 1000f,
            durationMs       = if (pts.size >= 2) pts.last().timestamp - pts.first().timestamp else 0L,
            stoppedTimeMs    = stoppedMs,
            topSpeedKmh      = pts.maxOf { it.speedKmh },
            maxAccelerationMs2 = pts.maxOf { it.linAccel },
            maxDecelerationMs2 = pts.maxOf { it.linAccel },
            peakGForce       = pts.maxOf { it.gForce },
            brakeEvents      = pts.count { it.isHardBrake },
            leftTurns        = pts.count { it.isSharpTurn } / 2,
            rightTurns       = pts.count { it.isSharpTurn } - (pts.count { it.isSharpTurn } / 2),
            safetyScore      = run {
                val brakes = pts.count { it.isHardBrake }
                val peakG  = pts.maxOf { it.gForce }
                val s = 100 - (brakes * 5).coerceAtMost(25) - when {
                    peakG > 1.2f -> 20
                    peakG > 0.9f -> 12
                    peakG > 0.7f -> 5
                    else -> 0
                }
                s.coerceIn(0, 100)
            }
        )
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
        Box(modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center
        ) {
            Text("No data", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    // Downsample to max 120 points to keep Path complexity manageable
    val chartData = if (data.size > 120) {
        val step = data.size / 100
        data.filterIndexed { index, _ -> index % step == 0 || index == data.lastIndex }
    } else data

    val textMeasurer = rememberTextMeasurer()
    val maxVal = chartData.maxOfOrNull { it.second }?.let { if (it == 0f) 1f else it * 1.1f } ?: 1f
    val minVal = chartData.minOfOrNull { it.second }?.let { if (it > 0f) 0f else it * 1.1f } ?: 0f
    val minTime = chartData.first().first
    val maxTime = chartData.last().first
    val totalDurationMs = (maxTime - minTime).coerceAtLeast(1L)

    val textStyle = TextStyle(color = TextSecondary, fontSize = 10.sp)

    fun formatTime(timestampMs: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampMs
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1E))
            .padding(top = 20.dp, start = 4.dp, end = 8.dp)
    ) {
        try {
            val insetX       = 38.dp.toPx()
            val xLabelHeight = 22.dp.toPx()
            val width  = (size.width - insetX).coerceAtLeast(1f)
            // Reserve the bottom slice for X-axis time labels
            val height = (size.height - xLabelHeight).coerceAtLeast(1f)
            val range  = (maxVal - minVal).coerceAtLeast(0.001f)

            fun xOf(timestamp: Long) =
                insetX + ((timestamp - minTime).toFloat() / totalDurationMs) * width

            fun yOf(value: Float) =
                height - ((value - minVal) / range) * height

            // --- Horizontal grid lines (4 levels) ---
            val numHLines = 4
            for (i in 0..numHLines) {
                val fraction = i.toFloat() / numHLines
                val y = fraction * height
                val lineColor = if (i == numHLines) Color.White.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(insetX, y),
                    end   = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = if (i == numHLines) 1.5f else 1f
                )
                // Y-axis value label
                val yVal = maxVal - fraction * range
                if (i < numHLines) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${yVal.toInt()}",
                        style = textStyle,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y - 7.dp.toPx())
                    )
                }
            }

            // --- Vertical time grid (every 25% of duration) ---
            val numVLines = 4
            for (i in 0..numVLines) {
                val fraction = i.toFloat() / numVLines
                val x = insetX + fraction * width
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end   = androidx.compose.ui.geometry.Offset(x, height),
                    strokeWidth = 1f
                )
                val elapsedMs = (fraction * totalDurationMs).toLong()
                val label = formatTime(minTime + elapsedMs)
                val layout = textMeasurer.measure(label, style = textStyle)
                val labelX = (x - layout.size.width / 2f).coerceIn(insetX, size.width - layout.size.width)
                // Draw inside the reserved bottom label slice (below chart height)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = textStyle,
                    topLeft = androidx.compose.ui.geometry.Offset(labelX, height + 4.dp.toPx())
                )
            }

            // --- Build smooth bezier line & gradient fill paths ---
            val linePath  = Path()
            val fillPath  = Path()

            chartData.forEachIndexed { index, pair ->
                val x = xOf(pair.first)
                val y = yOf(pair.second)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    val prev  = chartData[index - 1]
                    val prevX = xOf(prev.first)
                    val prevY = yOf(prev.second)
                    val cpX   = (prevX + x) / 2f
                    // Smooth cubic bezier — control points share same Y as endpoints
                    linePath.cubicTo(cpX, prevY, cpX, y, x, y)
                    fillPath.cubicTo(cpX, prevY, cpX, y, x, y)
                }
            }

            // Close fill path along the bottom
            val lastX = xOf(chartData.last().first)
            fillPath.lineTo(lastX, height)
            fillPath.close()

            // --- Draw gradient fill under the curve ---
            drawPath(
                path  = fillPath,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY   = height
                )
            )

            // --- Draw the line itself ---
            drawPath(
                path  = linePath,
                color = color,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap   = androidx.compose.ui.graphics.StrokeCap.Round,
                    join  = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            // --- Draw a glowing dot at the last point ---
            val lastY = yOf(chartData.last().second)
            drawCircle(color = color.copy(alpha = 0.3f), radius = 8.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(lastX, lastY))
            drawCircle(color = color, radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(lastX, lastY))

        } catch (e: Exception) {
            // Silently catch layout/hardware exceptions
        }
    }
}
