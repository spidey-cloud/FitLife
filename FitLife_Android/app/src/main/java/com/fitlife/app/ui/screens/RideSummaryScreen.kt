package com.fitlife.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.local.RideEntity
import com.fitlife.app.data.ride.RideMath
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.RideViewModel

@Composable
fun RideSummaryScreen(
    rideVm: RideViewModel,
    rideId: Long,
    justSaved: Boolean,
    onBack: () -> Unit
) {
    val rideState = rideVm.ride(rideId).collectAsState(initial = null)
    val ride = rideState.value
    val c = FitTheme.colors
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var personalBests by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(ride?.id) { ride?.let { personalBests = rideVm.personalBests(it) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Ride summary", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (ride != null) {
                IconButton(onClick = { share(context, ride) }) { Icon(Icons.Outlined.Share, "Share ride") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete ride") }
            }
        }

        if (ride == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val points = remember(ride.id) { RideMath.decodeRoute(ride.route) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (justSaved) item {
                FitCard(containerColor = c.success.copy(alpha = 0.12f), borderColor = c.success.copy(alpha = 0.4f)) {
                    Text("🎉 Ride completed & saved!", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (rideVm.healthConnectAvailable) "Added to your activity log and Health Connect (if permitted)."
                        else "Added to your activity log.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column {
                    Text(ride.title, style = MaterialTheme.typography.headlineMedium)
                    Text("${friendlyDate(ride.date)} · ${startTime(ride.startEpochMs)} – ${startTime(ride.endEpochMs)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                FitCard(contentPadding = PaddingValues(0.dp)) {
                    if (points.size >= 2) RouteTrace(points, c.ride, height = 220.dp, modifier = Modifier.padding(top = 8.dp))
                    else Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("No GPS trace recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("🚴", "Distance", fmt1(ride.distanceMeters / 1000f), "km", c.ride, Modifier.weight(1f))
                    MetricTile("⏱️", "Moving time", RideMath.formatDurationShort(ride.movingSeconds), "", c.steps, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("🏔️", "Elevation", "+${ride.elevationGainM.toInt()}", "m", c.water, Modifier.weight(1f),
                        sub = "Min ${ride.minAltitudeM.toInt()}m · Peak ${ride.maxAltitudeM.toInt()}m")
                    MetricTile("⚡", "Avg speed", fmt1(ride.avgSpeedKmh), "km/h", c.burn, Modifier.weight(1f),
                        sub = "Max ${fmt1(ride.maxSpeedKmh)} km/h")
                }
            }

            if (points.size >= 2 && points.any { it.alt != 0.0 }) item {
                FitCard {
                    Text("Elevation profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    ElevationChart(points.map { it.alt }, c.water)
                }
            }

            item {
                FitCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Calories", "${ride.calories}", unit = "kcal", color = c.burn)
                        StatBlock("Max speed", fmt1(ride.maxSpeedKmh), unit = "km/h")
                        StatBlock("Pacing min", fmt1(ride.minMovingSpeedKmh), unit = "km/h")
                    }
                }
            }

            if (personalBests.isNotEmpty()) item {
                FitCard(containerColor = c.warning.copy(alpha = 0.12f), borderColor = c.warning.copy(alpha = 0.4f)) {
                    Text("🏆 Personal best${if (personalBests.size > 1) "s" else ""}", style = MaterialTheme.typography.titleMedium)
                    personalBests.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            item {
                PrimaryButton("Share ride", onClick = { share(context, ride) }, icon = Icons.Outlined.Share)
            }
            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) {
                    Text("Done")
                }
            }
        }
    }

    if (confirmDelete && ride != null) {
        ConfirmDeleteDialog(
            title = "Delete ride?",
            text = "“${ride.title}” will be removed from your history. You can undo right after.",
            onConfirm = { rideVm.deleteRide(ride); onBack() },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Shares a plain-text summary (no location data is included). */
private fun share(context: android.content.Context, ride: RideEntity) {
    val text = buildString {
        append("🚴 ${ride.title} on FitLife\n")
        append("${fmt1(ride.distanceMeters / 1000f)} km · ${RideMath.formatDurationShort(ride.movingSeconds)} · ")
        append("${fmt1(ride.avgSpeedKmh)} km/h avg · +${ride.elevationGainM.toInt()} m · ${ride.calories} kcal")
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, "Share ride"))
}
