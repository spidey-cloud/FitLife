package com.fitlife.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.local.RideEntity
import com.fitlife.app.data.ride.GpsQuality
import com.fitlife.app.data.ride.RideMath
import com.fitlife.app.data.ride.RideSession
import com.fitlife.app.data.ride.RideStatus
import com.fitlife.app.service.RideTrackingService
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.RideViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CyclingScreen(
    rideVm: RideViewModel,
    onOpenLive: () -> Unit,
    onOpenSummary: (Long, Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    val hub by rideVm.hub.collectAsState()
    val session by rideVm.session.collectAsState()
    val context = LocalContext.current
    val c = FitTheme.colors
    var showGpsOff by remember { mutableStateOf(false) }
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RideEntity?>(null) }

    fun launchRide() {
        if (!RideTrackingService.isGpsEnabled(context)) { showGpsOff = true; return }
        RideTrackingService.start(context)
        onOpenLive()
    }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permState = rememberMultiplePermissionsState(permissions) { results ->
        askedPermission = true
        val locationOk = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) launchRide()
    }

    fun startRide() {
        if (RideTrackingService.hasLocationPermission(context)) launchRide()
        else permState.launchMultiplePermissionRequest()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (session.isActive) {
            item {
                ScreenHeader("Active ride", "${fmt1(session.distanceKm)} km in-flight") {
                    GpsPill(session.gps)
                }
            }
            item { LiveRideDashboard(session, hub.todayKm, hub.dailyGoalKm, onOpenLive) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val paused = session.status == RideStatus.PAUSED
                    SecondaryButton(
                        if (paused) "Resume" else "Pause ride",
                        onClick = { if (paused) rideVm.resume() else rideVm.pause() },
                        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showFinish = true },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.ride, contentColor = c.onAccent)
                    ) { Text("Finish & save"); Spacer(Modifier.width(6.dp)); Text("🏁") }
                }
            }
        } else {
            item { ScreenHeader("Cycling", "GPS ride tracking · works offline") }

            // ── Hero: daily goal + start ──────────────────────────────────────
            item {
                HeroCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(
                            hub.goalProgress, size = 112.dp, strokeWidth = 11.dp,
                            color = if (c.isNightRide) c.ride else MaterialTheme.colorScheme.onPrimary,
                            trackColor = LocalContentColor.current.copy(alpha = 0.18f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmt1(hub.todayKm), style = MaterialTheme.typography.headlineSmall)
                                Eyebrow("km today", color = LocalContentColor.current.copy(alpha = 0.8f))
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${(hub.goalProgress * 100).toInt().coerceAtMost(999)}% of ${hub.dailyGoalKm.toInt()} km",
                                style = MaterialTheme.typography.titleLarge)
                            Text("Daily ride goal", style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.8f))
                            if (hub.todaySeconds > 0) Text(
                                "${RideMath.formatDurationShort(hub.todaySeconds)} · +${hub.todayClimbM.toInt()} m",
                                style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { startRide() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (c.isNightRide) c.ride else MaterialTheme.colorScheme.surface,
                            contentColor = if (c.isNightRide) c.onAccent else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.DirectionsBike, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start ride", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (askedPermission && !RideTrackingService.hasLocationPermission(context)) item {
                FitCard(containerColor = MaterialTheme.colorScheme.errorContainer, borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text("Location is needed to measure distance and speed. It's only used while a ride is recording and never leaves your phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }) { Text("Open app settings") }
                }
            }

            // ── This week ─────────────────────────────────────────────────────
            item {
                FitCard {
                    Text("This week", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Distance", fmt1(hub.weekKm), unit = "km", color = c.ride)
                        StatBlock("Rides", "${hub.weekRides}")
                        StatBlock("All time", fmt1(hub.totalKm), unit = "km")
                    }
                    Spacer(Modifier.height(16.dp))
                    val labels = java.time.DayOfWeek.values().map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
                    val todayIdx = java.time.LocalDate.now().dayOfWeek.value - 1
                    BarChart(hub.weekByDay.map { it.toFloat() }, labels, color = c.ride, highlightIndex = todayIdx,
                        goal = hub.dailyGoalKm, height = 110.dp)
                }
            }

            // ── Last ride ─────────────────────────────────────────────────────
            hub.lastRide?.let { last ->
                item { SectionHeader("Last ride") }
                item {
                    val points = remember(last.id) { RideMath.decodeRoute(last.route) }
                    FitCard(onClick = { onOpenSummary(last.id, false) }, contentPadding = PaddingValues(0.dp)) {
                        if (points.size >= 2) RouteTrace(points, c.ride, height = 160.dp, modifier = Modifier.padding(top = 8.dp))
                        Column(Modifier.padding(16.dp)) {
                            Text(last.title, style = MaterialTheme.typography.titleMedium)
                            Text(friendlyDate(last.date) + " · " + startTime(last.startEpochMs),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatBlock("Distance", fmt1(last.distanceMeters / 1000f), unit = "km")
                                StatBlock("Time", RideMath.formatDuration(last.movingSeconds))
                                StatBlock("Avg", fmt1(last.avgSpeedKmh), unit = "km/h")
                                StatBlock("Climb", "+${last.elevationGainM.toInt()}", unit = "m")
                            }
                        }
                    }
                }
            }

            // ── History ───────────────────────────────────────────────────────
            if (hub.rides.size > 1) {
                item { SectionHeader("History", "${hub.rides.size} rides") }
                items(hub.rides.drop(1), key = { it.id }) { r ->
                    RideRow(r, onClick = { onOpenSummary(r.id, false) }, onDelete = { pendingDelete = r })
                }
            } else if (hub.rides.isEmpty()) item {
                EmptyState("🚴", "No rides yet", "Tap Start ride — FitLife measures distance, speed, climb and calories with GPS, even offline.")
            }
        }
    }

    if (showGpsOff) {
        AlertDialog(
            onDismissRequest = { showGpsOff = false },
            icon = { Icon(Icons.Outlined.LocationOff, null) },
            title = { Text("Turn on location") },
            text = { Text("Location services are off. Turn them on to record distance and speed.") },
            confirmButton = {
                Button(onClick = {
                    showGpsOff = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }, shape = CircleShape) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showGpsOff = false }) { Text("Not now") } }
        )
    }

    if (showFinish) {
        FinishRideDialog(
            session = session,
            onKeepRiding = { showFinish = false },
            onSave = {
                showFinish = false
                rideVm.finish { saved ->
                    if (saved != null) onOpenSummary(saved.id, true)
                    else onMessage("Ride too short to save — discarded")
                }
            },
            onDiscard = { showFinish = false; rideVm.discard(); onMessage("Ride discarded") }
        )
    }

    pendingDelete?.let { r ->
        ConfirmDeleteDialog(
            title = "Delete ride?",
            text = "“${r.title}” (${fmt1(r.distanceMeters / 1000f)} km) will be removed. You can undo right after.",
            onConfirm = { rideVm.deleteRide(r) },
            onDismiss = { pendingDelete = null }
        )
    }
}

internal fun startTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

@Composable
internal fun GpsPill(gps: GpsQuality) {
    val c = FitTheme.colors
    val color = when (gps) {
        GpsQuality.STRONG, GpsQuality.GOOD -> c.success
        GpsQuality.WEAK -> c.warning
        GpsQuality.SEARCHING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(gps.label, color, leading = "🛰️")
}

@Composable
private fun LiveRideDashboard(s: RideSession, todayKm: Double, goalKm: Float, onOpenLive: () -> Unit) {
    val c = FitTheme.colors
    val goalPct = if (goalKm > 0) (((todayKm + s.distanceKm) / goalKm) * 100).toInt() else 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FitCard(contentPadding = PaddingValues(0.dp), onClick = onOpenLive) {
            Box {
                RouteTrace(s.route, c.ride, height = 190.dp, showLiveDot = true)
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill("Departed ${startTime(s.startedAtMs)}", MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onOpenLive) { Icon(Icons.Outlined.OpenInFull, "Open live HUD") }
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                    Pill("${fmt1(s.distanceKm)} km", c.ride, leading = "🏁")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("⏱️", "Duration", RideMath.formatDuration(s.movingSeconds), "", c.steps, Modifier.weight(1f),
                sub = when (s.status) { RideStatus.AUTO_PAUSED -> "Auto-paused · idle"; RideStatus.PAUSED -> "Paused"; else -> "Moving time" })
            MetricTile("🚴", "Distance", fmt1(s.distanceKm), "km", c.ride, Modifier.weight(1f),
                sub = "$goalPct% of ${goalKm.toInt()} km daily goal")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("🏔️", "Climb gain", "+${s.elevationGainM.toInt()}", "m", c.water, Modifier.weight(1f),
                sub = if (s.minAltitudeM != null && s.maxAltitudeM != null) "Min ${s.minAltitudeM.toInt()}m · Peak ${s.maxAltitudeM.toInt()}m" else "Waiting for altitude")
            MetricTile("⚡", "Active energy", "${s.calories.toInt()}", "kcal", c.burn, Modifier.weight(1f),
                sub = if (s.kcalPerHour > 0) "🔥 ${s.kcalPerHour.toInt()} kcal/hr pace" else "Calculating…")
        }
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pace & speed", style = MaterialTheme.typography.titleMedium)
                    Text("Live GPS · ${fmt1(s.speedKmh)} km/h now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill("Live", c.ride, dot = true)
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(s.speedHistory, c.ride)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("Avg speed", fmt1(s.avgSpeedKmh), unit = "km/h")
                StatBlock("Max peak", fmt1(s.maxSpeedKmh), unit = "km/h", color = c.ride)
                StatBlock("Pacing min", fmt1(s.minMovingSpeedKmh), unit = "km/h")
            }
        }
    }
}

@Composable
private fun RideRow(r: RideEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val c = FitTheme.colors
    FitCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = c.ride, icon = Icons.AutoMirrored.Outlined.DirectionsBike)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${friendlyDate(r.date)} · ${fmt1(r.distanceMeters / 1000f)} km · ${RideMath.formatDuration(r.movingSeconds)} · ${fmt1(r.avgSpeedKmh)} km/h",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "Delete ${r.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "🏁 End activity?" confirmation with the ride's headline stats. */
@Composable
internal fun FinishRideDialog(
    session: RideSession,
    onKeepRiding: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val c = FitTheme.colors
    var confirmDiscard by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onKeepRiding,
        icon = { Text("🏁", style = MaterialTheme.typography.headlineMedium) },
        title = { Text(if (confirmDiscard) "Discard this ride?" else "End activity?") },
        text = {
            Column {
                Text(
                    if (confirmDiscard) "The recording will be deleted and nothing is saved."
                    else "Your ride stats will be saved to your activity log.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!confirmDiscard) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Distance", fmt1(session.distanceKm), unit = "km", color = c.ride)
                        StatBlock("Duration", RideMath.formatDurationShort(session.movingSeconds))
                        StatBlock("Calories", "${session.calories.toInt()}", unit = "kcal")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmDiscard = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("Discard ride", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (confirmDiscard) Button(
                onClick = onDiscard, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            ) { Text("Discard") }
            else Button(onClick = onSave, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = c.ride, contentColor = c.onAccent)) {
                Icon(Icons.Filled.Flag, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save summary")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (confirmDiscard) confirmDiscard = false else onKeepRiding() }) {
                Text(if (confirmDiscard) "Back" else "Keep riding")
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
