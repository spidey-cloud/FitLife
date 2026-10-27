package com.fitlife.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ActivityScreen(vm: FitnessViewModel) {
    val s by vm.stepsState.collectAsState()
    val c = FitTheme.colors
    var showManual by remember { mutableStateOf(false) }

    // Past 7 days oldest → newest, zero-filled for days without data.
    val today = remember { LocalDate.now() }
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val byDate = s.weekHistory.associateBy { it.date }
    val values = days.map { (byDate[it.toString()]?.steps ?: 0).toFloat() }
    val labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    val progress = if (s.stepGoal > 0) s.todaySteps.toFloat() / s.stepGoal else 0f

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader("Activity", "Steps, distance & movement") {
                if (s.healthConnectAvailable) {
                    IconButton(onClick = { vm.syncSteps() }, enabled = !s.isSyncing) {
                        if (s.isSyncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Sync, "Sync with Health Connect")
                    }
                }
            }
        }

        // ── Goal ring ─────────────────────────────────────────────────────────
        item {
            FitCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RingProgress(progress = progress, size = 210.dp, strokeWidth = 18.dp, color = c.steps) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Eyebrow("Goal: ${fmtInt(s.stepGoal)}")
                            Text(fmtInt(s.todaySteps), style = MaterialTheme.typography.displaySmall)
                            Eyebrow("Steps")
                        }
                    }
                }
                if (progress >= 1f) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Pill("Goal reached", c.success, leading = "🎉")
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock("Distance", fmt1(s.todayDistanceKm), unit = "km")
                    StatBlock("Burned", fmtInt(s.todayCaloriesBurned), unit = "kcal", color = c.burn)
                    StatBlock("Active", "${s.todayActiveMinutes}", unit = "min")
                }
            }
        }

        // ── Trend ─────────────────────────────────────────────────────────────
        item {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Activity trend", style = MaterialTheme.typography.titleLarge)
                        Text("Past 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.streakDays > 0) StreakChip(s.streakDays)
                }
                Spacer(Modifier.height(16.dp))
                BarChart(values, labels, color = c.steps, goal = s.stepGoal.toFloat())
                Spacer(Modifier.height(8.dp))
                Text("Avg ${fmtInt(values.average().toInt())} steps/day · dashed line = goal",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            PrimaryButton("Log steps manually", onClick = { showManual = true }, icon = Icons.Filled.Add)
        }

        // ── History ───────────────────────────────────────────────────────────
        item { SectionHeader("Recent history") }
        val history = days.reversed().map { d -> d to (byDate[d.toString()]?.steps ?: 0) }
        if (history.all { it.second == 0 }) item {
            EmptyState("👟", "No steps yet", "Carry your phone and FitLife counts steps automatically — or add them manually.")
        } else items(history, key = { it.first.toString() }) { (date, count) ->
            val reached = count >= s.stepGoal
            FitCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reached) Icon(Icons.Outlined.CheckCircle, "Goal reached", tint = c.success, modifier = Modifier.size(22.dp))
                    else Box(Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(friendlyDate(date.toString()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(fmtInt(count), style = MaterialTheme.typography.titleMedium)
                    Text(" steps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                LinearMeter(if (s.stepGoal > 0) count.toFloat() / s.stepGoal else 0f, c.steps.copy(alpha = if (reached) 1f else 0.6f), height = 4.dp)
            }
        }
    }

    if (showManual) {
        ManualStepsSheet(
            onDismiss = { showManual = false },
            onSave = { date, count -> vm.addStepsManually(date.toString(), count); showManual = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualStepsSheet(onDismiss: () -> Unit, onSave: (LocalDate, Int) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var text by remember { mutableStateOf("") }
    val count = text.toIntOrNull() ?: 0
    val valid = count in 1..100_000

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("👟 Manual step entry", style = MaterialTheme.typography.headlineSmall)
            Text("Steps are added to that day's total.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            DateField("Date of activity", date, { date = it })
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                label = { Text("Step count") },
                suffix = { Text("steps") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                supportingText = { if (text.isNotEmpty() && !valid) Text("Enter 1 – 100,000 steps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Eyebrow("Quick presets")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500, 1000, 2500).forEach { p ->
                    AssistChip(
                        onClick = { text = ((text.toIntOrNull() ?: 0) + p).coerceAtMost(100_000).toString() },
                        label = { Text("+${fmtInt(p)}") },
                        shape = CircleShape
                    )
                }
            }
            PrimaryButton("Save to activity history", onClick = { onSave(date, count) }, enabled = valid, icon = Icons.Filled.Check)
        }
    }
}
