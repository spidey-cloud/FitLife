package com.fitlife.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.data.ride.RideMath
import com.fitlife.app.data.ride.RideStatus
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitLifeTheme
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.RideViewModel
import com.fitlife.app.ui.viewmodel.SavedRide
import kotlin.math.abs

@Composable
fun ActiveRideScreen(
    rideVm: RideViewModel,
    onMinimize: () -> Unit,
    onSaved: (SavedRide) -> Unit,
    onEnded: (String) -> Unit
) {
    // The HUD always uses the high-contrast Night Ride system, whatever the app theme.
    FitLifeTheme(forceNightRide = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HudContent(rideVm, onMinimize, onSaved, onEnded)
        }
    }
}

@Composable
private fun HudContent(
    rideVm: RideViewModel,
    onMinimize: () -> Unit,
    onSaved: (SavedRide) -> Unit,
    onEnded: (String) -> Unit
) {
    val s by rideVm.session.collectAsState()
    val c = FitTheme.colors
    var locked by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

    // Keep the display on while the HUD is visible.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // The service starts the tracker asynchronously, so only treat "inactive" as "ended"
    // after we've seen the ride active at least once (e.g. finished from the hub).
    var seenActive by remember { mutableStateOf(false) }
    LaunchedEffect(s.isActive) {
        if (s.isActive) seenActive = true
        else if (seenActive && !finishing) onEnded("")
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6_000)
        if (!seenActive && !rideVm.session.value.isActive) onEnded("Couldn't start ride tracking — check location access")
    }

    BackHandler(enabled = locked) { /* swallow back while locked */ }

    val paused = s.status == RideStatus.PAUSED
    val autoPaused = s.status == RideStatus.AUTO_PAUSED

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── Status row ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinimize) { Icon(Icons.Outlined.KeyboardArrowDown, "Minimise — ride keeps recording") }
                GpsPill(s.gps)
                Spacer(Modifier.weight(1f))
                RecPill(s.status)
            }

            Spacer(Modifier.height(24.dp))

            // ── Speed ─────────────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Speed ${fmt1(s.speedKmh)} kilometres per hour"
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Eyebrow("Speed")
                Text(
                    fmt1(s.speedKmh),
                    fontSize = 112.sp,
                    lineHeight = 112.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayLarge,
                    color = if (paused || autoPaused) MaterialTheme.colorScheme.onSurfaceVariant else c.ride
                )
                Text("km/h", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(24.dp))

            // ── Distance / time ───────────────────────────────────────────────
            Row(Modifier.fillMaxWidth()) {
                HudStat("Distance", fmt1(s.distanceKm), "km", Modifier.weight(1f))
                HudStat("Time", RideMath.formatDuration(s.movingSeconds), null, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))
            Sparkline(s.speedHistory, c.ride, height = 40.dp)
            Spacer(Modifier.height(20.dp))

            // ── Controls ──────────────────────────────────────────────────────
            Button(
                onClick = { if (paused) rideVm.resume() else rideVm.pause() },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (paused) c.ride else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (paused) c.onAccent else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (paused) "Resume ride" else "Pause ride", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showFinish = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = c.ride, contentColor = c.onAccent)
                ) { Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp)); Text("Finish & save") }
                OutlinedButton(
                    onClick = { locked = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = CircleShape
                ) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(6.dp)); Text("Lock HUD") }
            }

            Spacer(Modifier.height(24.dp))
            Eyebrow("Map & stats", Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))

            // ── Route ─────────────────────────────────────────────────────────
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Box {
                    RouteTrace(s.route, c.ride, height = 220.dp, showLiveDot = true)
                    Pill("Departed ${startTime(s.startedAtMs)}", MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── Secondary stats ───────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HudTile("Elevation", "+${s.elevationGainM.toInt()} m",
                    "${if (s.gradePct >= 0) "+" else "−"}${fmt1(abs(s.gradePct))}% grade", Modifier.weight(1f))
                HudTile("Avg speed", "${fmt1(s.avgSpeedKmh)}", "km/h", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HudTile("Calories", "${s.calories.toInt()}", "kcal", Modifier.weight(1f))
                HudTile("Max speed", fmt1(s.maxSpeedKmh), "km/h", Modifier.weight(1f))
            }
            if (s.gps == com.fitlife.app.data.ride.GpsQuality.SEARCHING) {
                Spacer(Modifier.height(16.dp))
                Text("Searching for GPS… stay outdoors with a clear view of the sky.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (locked) LockOverlay(onUnlock = { locked = false })
    }

    if (showFinish) {
        FinishRideDialog(
            session = s,
            onKeepRiding = { showFinish = false },
            onSave = {
                showFinish = false
                finishing = true
                rideVm.finish { saved ->
                    if (saved != null) onSaved(saved) else onEnded("Ride too short to save — discarded")
                }
            },
            onDiscard = {
                showFinish = false
                finishing = true
                rideVm.discard()
                onEnded("Ride discarded")
            }
        )
    }
}

@Composable
private fun RecPill(status: RideStatus) {
    val c = FitTheme.colors
    val pulse = rememberInfiniteTransition(label = "rec")
    val a by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "recAlpha")
    when (status) {
        RideStatus.RECORDING -> Pill("Live rec", MaterialTheme.colorScheme.error, dot = true, modifier = Modifier.alpha(a))
        RideStatus.AUTO_PAUSED -> Pill("Auto-paused", c.warning, dot = true)
        RideStatus.PAUSED -> Pill("Paused", c.warning, dot = true)
        RideStatus.IDLE -> Pill("Stopped", MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HudStat(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun HudTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Eyebrow(label)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Blocks all touches (pocket / rain) until the user long-presses the unlock button. */
@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { /* consume */ } },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(Modifier.padding(bottom = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onUnlock() }) }
                    .semantics { contentDescription = "Locked. Long-press to unlock" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.LockOpen, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            Text("Hold to unlock", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}
