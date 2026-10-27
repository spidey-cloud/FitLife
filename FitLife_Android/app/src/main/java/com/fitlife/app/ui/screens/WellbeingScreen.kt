package com.fitlife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.local.SleepLogEntity
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WellbeingScreen(vm: FitnessViewModel, onBack: () -> Unit) {
    val state by vm.wellbeingState.collectAsState()
    val c = FitTheme.colors
    val today = remember { LocalDate.now().toString() }
    var showSleep by remember { mutableStateOf(false) }

    // Check-in controls start from today's saved values once they load.
    var mood by remember { mutableIntStateOf(3) }
    var stress by remember { mutableFloatStateOf(5f) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(state.todayWellbeing) {
        if (!edited) state.todayWellbeing?.let { mood = it.moodScore; stress = it.stressLevel.toFloat() }
    }

    // Meditation timer — stopping early still logs the whole minutes completed.
    var medTotal by remember { mutableIntStateOf(0) }
    var medLeft by remember { mutableIntStateOf(0) }
    var medActive by remember { mutableStateOf(false) }
    LaunchedEffect(medActive) {
        while (medActive && medLeft > 0) { delay(1000); medLeft-- }
        if (medActive && medLeft == 0) { medActive = false; vm.addMeditationMinutes(today, medTotal / 60) }
    }

    val water = state.todayWellbeing?.waterGlasses ?: 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Wellbeing", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Sleep ─────────────────────────────────────────────────────────
            item {
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(color = c.sleep, emoji = "😴")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sleep", style = MaterialTheme.typography.titleMedium)
                            val last = state.lastSleep
                            Text(
                                if (last != null) "${last.durationMinutes / 60}h ${last.durationMinutes % 60}m · ${friendlyDate(last.date)}"
                                else "Nothing logged yet",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { showSleep = true }, shape = CircleShape) { Text("Log") }
                    }
                    if (state.recentSleep.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        val nights = state.recentSleep.take(7).reversed()
                        BarChart(
                            values = nights.map { it.durationMinutes / 60f },
                            labels = nights.map { LocalDate.parse(it.date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                            color = c.sleep, goal = 8f, height = 100.dp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Avg ${fmt1(state.avgSleepHours)} h over the last ${nights.size} nights · dashed line = 8 h",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Mood & stress ─────────────────────────────────────────────────
            item {
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("How are you feeling?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (state.moodSavedToday && !edited) Pill("Saved", c.success)
                    }
                    Spacer(Modifier.height(12.dp))
                    MoodSelector(mood, { mood = it; edited = true })
                    Spacer(Modifier.height(16.dp))
                    Text("Stress: ${stress.toInt()} / 10", style = MaterialTheme.typography.titleSmall)
                    Slider(value = stress, onValueChange = { stress = it; edited = true }, valueRange = 1f..10f, steps = 8)
                    PrimaryButton(
                        if (state.moodSavedToday) "Update check-in" else "Save check-in",
                        onClick = { vm.logMood(today, mood, stress.toInt()); edited = false },
                        icon = Icons.Filled.Check
                    )
                }
            }

            // ── Water ─────────────────────────────────────────────────────────
            item {
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(color = c.water, emoji = "💧")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Hydration", style = MaterialTheme.typography.titleMedium)
                            Text("$water of ${state.waterGoal} glasses", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalIconButton(onClick = { vm.removeWater(today) }, enabled = water > 0) { Icon(Icons.Filled.Remove, "Remove a glass") }
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(onClick = { vm.addWater(today) }) { Icon(Icons.Filled.Add, "Add a glass") }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(state.waterGoal.coerceAtMost(12)) { i ->
                            Box(Modifier.weight(1f).height(28.dp).clip(MaterialTheme.shapes.extraSmall)
                                .background(if (i < water) c.water else MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            }

            // ── Meditation ────────────────────────────────────────────────────
            item {
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(color = c.mood, emoji = "🧘")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Meditation", style = MaterialTheme.typography.titleMedium)
                            Text("Today: ${state.meditationMinutes} min", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (!medActive) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 20).forEach { m ->
                                OutlinedButton(
                                    onClick = { medTotal = m * 60; medLeft = m * 60; medActive = true },
                                    modifier = Modifier.weight(1f), shape = CircleShape, contentPadding = PaddingValues(0.dp)
                                ) { Text("$m min") }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            RingProgress(1f - medLeft.toFloat() / medTotal.coerceAtLeast(1), size = 160.dp, color = c.mood) {
                                Text(String.format(Locale.US, "%02d:%02d", medLeft / 60, medLeft % 60), style = MaterialTheme.typography.headlineLarge)
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                medActive = false
                                vm.addMeditationMinutes(today, (medTotal - medLeft) / 60)
                            }, shape = CircleShape) { Text("Stop & log") }
                        }
                    }
                }
            }

            // ── Weekly insights ───────────────────────────────────────────────
            if (state.weekWellbeing.isNotEmpty()) item {
                FitCard {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val wk = state.weekWellbeing
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Avg mood", fmt1(wk.map { it.moodScore }.average()), unit = "/5", color = c.mood)
                        StatBlock("Avg stress", fmt1(wk.map { it.stressLevel }.average()), unit = "/10")
                        StatBlock("Avg water", fmt1(wk.map { it.waterGlasses }.average()), unit = "gl", color = c.water)
                    }
                }
            }
        }
    }

    if (showSleep) LogSleepSheet(onDismiss = { showSleep = false }, onSave = { vm.logSleep(it); showSleep = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSleepSheet(onDismiss: () -> Unit, onSave: (SleepLogEntity) -> Unit) {
    var wakeDate by remember { mutableStateOf(LocalDate.now()) }
    val bed = rememberTimePickerState(initialHour = 23, initialMinute = 0)
    val wake = rememberTimePickerState(initialHour = 7, initialMinute = 0)
    var editing by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableIntStateOf(3) }

    var mins = (wake.hour * 60 + wake.minute) - (bed.hour * 60 + bed.minute)
    if (mins <= 0) mins += 1440

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("😴 Log sleep", style = MaterialTheme.typography.headlineSmall)
            DateField("Woke up on", wakeDate, { wakeDate = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeTile("Bedtime", bed.hour, bed.minute, Modifier.weight(1f)) { editing = "bed" }
                TimeTile("Wake up", wake.hour, wake.minute, Modifier.weight(1f)) { editing = "wake" }
            }
            Text("${mins / 60}h ${mins % 60}m of sleep", style = MaterialTheme.typography.titleMedium)
            Eyebrow("Quality")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("😫", "😕", "😐", "🙂", "😴").forEachIndexed { i, e ->
                    FilterChip(selected = quality == i + 1, onClick = { quality = i + 1 }, label = { Text(e) }, shape = CircleShape)
                }
            }
            PrimaryButton("Save sleep", icon = Icons.Filled.Check, onClick = {
                onSave(SleepLogEntity(
                    date = wakeDate.toString(),
                    bedtimeHour = bed.hour, bedtimeMinute = bed.minute,
                    wakeHour = wake.hour, wakeMinute = wake.minute,
                    durationMinutes = mins, qualityRating = quality
                ))
            })
        }
    }

    editing?.let { which ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (which == "bed") "Bedtime" else "Wake up") },
            text = { TimePicker(state = if (which == "bed") bed else wake) },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun TimeTile(label: String, hour: Int, minute: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FitCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Eyebrow(label)
        Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute), style = MaterialTheme.typography.headlineSmall)
    }
}
