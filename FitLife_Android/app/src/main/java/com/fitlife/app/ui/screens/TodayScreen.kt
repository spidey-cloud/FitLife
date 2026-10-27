package com.fitlife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.ride.RideMath
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import com.fitlife.app.ui.viewmodel.RideViewModel
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(
    vm: FitnessViewModel,
    rideVm: RideViewModel,
    onOpenActivity: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenRide: () -> Unit,
    onOpenLiveRide: () -> Unit,
    onOpenWellbeing: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val steps by vm.stepsState.collectAsState()
    val workout by vm.workoutState.collectAsState()
    val cal by vm.calorieState.collectAsState()
    val wb by vm.wellbeingState.collectAsState()
    val profile by vm.profileState.collectAsState()
    val hub by rideVm.hub.collectAsState()
    val session by rideVm.session.collectAsState()
    val c = FitTheme.colors
    val today = remember { LocalDate.now().toString() }

    val water = wb.todayWellbeing?.waterGlasses ?: 0
    val stepP = if (steps.stepGoal > 0) steps.todaySteps.toFloat() / steps.stepGoal else 0f
    val waterP = water.toFloat() / wb.waterGoal
    val calP = cal.progress
    // Daily score = average completion of the three everyday goals (each capped at 100 %).
    val daily = listOf(stepP, waterP, calP).map { it.coerceIn(0f, 1f) }.average().toFloat()
    val goalsHit = listOf(stepP, waterP, calP).count { it >= 1f }
    val activeBurn = steps.todayCaloriesBurned + workout.todayCaloriesBurned + hub.todayCalories

    val sleep = wb.lastSleep
    val readiness = when {
        sleep == null -> "Log last night's sleep to see your readiness"
        sleep.durationMinutes >= 420 && sleep.qualityRating >= 3 -> "Readiness is high — go for it"
        sleep.durationMinutes >= 360 -> "Readiness is moderate"
        else -> "Short on sleep — keep today light"
    }
    val firstName = profile.profile.name.trim().substringBefore(' ').ifBlank { "there" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "${greetingFor(LocalTime.now().hour)}, $firstName 👋",
                subtitle = readiness
            ) {
                Surface(
                    onClick = onOpenProfile,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp).semantics { contentDescription = "Profile and settings" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(firstName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        // ── Live ride banner ──────────────────────────────────────────────────
        if (session.isActive) item {
            FitCard(onClick = onOpenLiveRide, containerColor = c.ride.copy(alpha = 0.12f), borderColor = c.ride.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(color = c.ride, icon = Icons.AutoMirrored.Outlined.DirectionsBike)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ride in progress", style = MaterialTheme.typography.titleMedium)
                        Text("${fmt1(session.distanceKm)} km · ${RideMath.formatDuration(session.movingSeconds)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill("Live", c.ride, dot = true)
                }
            }
        }

        // ── Hero: daily ring + streak ─────────────────────────────────────────
        item {
            HeroCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(
                        progress = daily, size = 128.dp, strokeWidth = 12.dp,
                        color = if (c.isNightRide) c.ride else MaterialTheme.colorScheme.onPrimary,
                        trackColor = LocalContentColor.current.copy(alpha = 0.18f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(daily * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                            Eyebrow("Daily", color = LocalContentColor.current.copy(alpha = 0.8f))
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Surface(shape = CircleShape, color = LocalContentColor.current.copy(alpha = 0.16f)) {
                            Text("🔥 ${steps.streakDays}D STREAK", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("$goalsHit of 3 goals hit", style = MaterialTheme.typography.titleLarge)
                        Text("Steps · water · calories logged", style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // ── 2 × 2 metric tiles ────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("👟", "Steps", fmtInt(steps.todaySteps), "/ ${if (steps.stepGoal % 1000 == 0) "${steps.stepGoal / 1000}" else fmt1(steps.stepGoal / 1000f)}k", c.steps,
                    Modifier.weight(1f), progress = stepP, onClick = onOpenActivity)
                MetricTile("🔥", "Active burn", fmtInt(activeBurn), "kcal", c.burn,
                    Modifier.weight(1f), sub = "Steps, workouts & rides", onClick = onOpenActivity)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("💧", "Water", "$water", "/ ${wb.waterGoal} gl", c.water,
                    Modifier.weight(1f), progress = waterP, onClick = { vm.addWater(today) })
                val left = cal.remaining
                MetricTile("🍽️", "Calories", fmtInt(kotlin.math.abs(left)), if (left >= 0) "kcal left" else "kcal over",
                    if (left >= 0) c.protein else c.warning, Modifier.weight(1f),
                    progress = calP, onClick = onOpenNutrition)
            }
        }

        // ── Next workout ──────────────────────────────────────────────────────
        item {
            val next = workout.plannedToday.firstOrNull()
            FitCard(onClick = onOpenWorkout) {
                Eyebrow("Next workout")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (next != null) {
                            Text(next.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${next.focus.ifBlank { next.category }} • ${next.durationMinutes}m",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (workout.completedToday.isNotEmpty()) {
                            Text("${workout.completedToday.first().name} done ✓", style = MaterialTheme.typography.titleLarge)
                            Text("${workout.weeklyCount} of ${workout.weeklyGoal} workouts this week",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Nothing scheduled", style = MaterialTheme.typography.titleLarge)
                            Text("Plan a session from a template", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilledIconButton(
                        onClick = onOpenWorkout,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(if (next != null) Icons.Filled.PlayArrow else Icons.Filled.Add,
                            if (next != null) "Start ${next.name}" else "Plan a workout")
                    }
                }
            }
        }

        // ── Ride ──────────────────────────────────────────────────────────────
        if (!session.isActive) item {
            FitCard(onClick = onOpenRide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(color = c.ride, icon = Icons.AutoMirrored.Outlined.DirectionsBike)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cycling", style = MaterialTheme.typography.titleMedium)
                        Text("${fmt1(hub.todayKm)} of ${hub.dailyGoalKm.toInt()} km today · ${fmt1(hub.weekKm)} km this week",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                LinearMeter(hub.goalProgress, c.ride, height = 6.dp)
            }
        }

        // ── Quick rows: water + sleep ─────────────────────────────────────────
        item {
            FitCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💧", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$water of ${wb.waterGoal} glasses logged", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(wb.waterGoal.coerceAtMost(12)) { i ->
                                Box(Modifier.size(width = 14.dp, height = 6.dp).clip(CircleShape)
                                    .background(if (i < water) c.water else MaterialTheme.colorScheme.surfaceVariant))
                            }
                        }
                    }
                    FilledTonalButton(onClick = { vm.addWater(today) }, shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Log")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("😴", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (sleep != null) {
                            val h = sleep.durationMinutes / 60; val m = sleep.durationMinutes % 60
                            val label = when (sleep.qualityRating) { 5 -> "Great"; 4 -> "Rested"; 3 -> "Okay"; 2 -> "Tired"; else -> "Poor" }
                            Text("${h}h ${m}m · $label", style = MaterialTheme.typography.titleSmall)
                            Text(friendlyDate(sleep.date).let { if (it == "Today") "Last night" else it },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("No sleep logged", style = MaterialTheme.typography.titleSmall)
                            Text("Track sleep, mood & mindfulness", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = onOpenWellbeing) { Text(if (sleep != null) "Wellbeing" else "Log sleep") }
                }
            }
        }
    }
}
