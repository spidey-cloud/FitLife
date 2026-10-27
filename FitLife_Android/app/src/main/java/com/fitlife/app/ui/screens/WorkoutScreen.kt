package com.fitlife.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.local.ExerciseEntity
import com.fitlife.app.data.local.WorkoutEntity
import com.fitlife.app.data.ride.RideMath
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.DayMark
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

// ═══════════════════════════════════════════════════════════════════════
// TEMPLATES
// ═══════════════════════════════════════════════════════════════════════

private data class TemplateExercise(val name: String, val sets: Int, val reps: Int = 0, val seconds: Int = 0)

private data class WorkoutTemplate(
    val emoji: String,
    val name: String,
    val category: String,
    val focus: String,
    val minutes: Int,
    val level: String,
    val exercises: List<TemplateExercise>
)

private val TEMPLATES = listOf(
    WorkoutTemplate("🦾", "Upper Body Hypertrophy", "Strength", "Chest, Back & Arms", 45, "Intermediate", listOf(
        TemplateExercise("Bench Press", 4, 8), TemplateExercise("Barbell Row", 4, 8),
        TemplateExercise("Overhead Press", 3, 10), TemplateExercise("Bicep Curls", 3, 12))),
    WorkoutTemplate("🦵", "Legs & Glutes Power", "Strength", "Legs & Glutes", 50, "Advanced", listOf(
        TemplateExercise("Back Squat", 5, 5), TemplateExercise("Romanian Deadlift", 4, 8),
        TemplateExercise("Walking Lunges", 3, 12), TemplateExercise("Hip Thrust", 4, 10))),
    WorkoutTemplate("💪", "Push Day A", "Strength", "Chest & Shoulders", 45, "Intermediate", listOf(
        TemplateExercise("Bench Press", 3, 8), TemplateExercise("Overhead Press", 3, 10),
        TemplateExercise("Tricep Dips", 3, 12), TemplateExercise("Lateral Raises", 3, 15))),
    WorkoutTemplate("🏋️", "Pull Day A", "Strength", "Back & Biceps", 45, "Intermediate", listOf(
        TemplateExercise("Deadlift", 3, 5), TemplateExercise("Barbell Row", 3, 8),
        TemplateExercise("Bicep Curls", 3, 12), TemplateExercise("Face Pulls", 3, 15))),
    WorkoutTemplate("🔥", "HIIT Blast", "HIIT", "Full body", 25, "All levels", listOf(
        TemplateExercise("Burpees", 3, 12), TemplateExercise("Jump Rope", 3, seconds = 60),
        TemplateExercise("Mountain Climbers", 3, 20), TemplateExercise("Box Jumps", 3, 10))),
    WorkoutTemplate("🧘", "Morning Yoga", "Yoga", "Mobility", 20, "Beginner", listOf(
        TemplateExercise("Sun Salutation", 1, 5), TemplateExercise("Warrior I & II", 1, seconds = 120),
        TemplateExercise("Downward Dog", 1, seconds = 60), TemplateExercise("Child's Pose", 1, seconds = 90))),
    WorkoutTemplate("🏃", "5K Run", "Cardio", "Endurance", 35, "Beginner", listOf(
        TemplateExercise("Warm-up walk", 1, seconds = 300), TemplateExercise("Steady run · 5 km", 1, seconds = 1800),
        TemplateExercise("Cool-down walk", 1, seconds = 300))),
)

private val CATEGORIES = listOf("Strength" to "💪", "HIIT" to "🔥", "Cardio" to "🏃", "Yoga" to "🧘", "Flexibility" to "🤸")

private val SUGGESTIONS = mapOf(
    "Strength" to listOf("Squat", "Bench Press", "Deadlift", "Pull-ups", "Overhead Press", "Barbell Row"),
    "HIIT" to listOf("Burpees", "Mountain Climbers", "Jump Squats", "High Knees", "Box Jumps"),
    "Cardio" to listOf("Run", "Cycling", "Rowing", "Jump Rope", "Stair Climber"),
    "Yoga" to listOf("Sun Salutation", "Warrior II", "Tree Pose", "Downward Dog", "Pigeon Pose"),
    "Flexibility" to listOf("Hamstring Stretch", "Hip Flexor Stretch", "Cat-Cow", "Shoulder Stretch")
)

/** Compendium-style MET by category, used to estimate calories from duration and weight. */
private fun metFor(category: String) = when (category) {
    "HIIT" -> 8.0; "Cardio" -> 7.0; "Strength" -> 5.0; "Yoga" -> 3.0; "Flexibility" -> 2.5; else -> 4.0
}

private fun estimateKcal(category: String, minutes: Int, weightKg: Float) =
    (metFor(category) * weightKg * minutes / 60.0).toInt()

private fun exerciseLabel(e: ExerciseEntity): String =
    if (e.durationSeconds > 0) "${if (e.sets > 1) "${e.sets} × " else ""}${RideMath.formatDurationShort(e.durationSeconds.toLong())}"
    else "${e.sets} × ${e.repsPerSet}"

private fun categoryEmoji(category: String) = CATEGORIES.firstOrNull { it.first == category }?.second ?: "🏋️"

// ═══════════════════════════════════════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun WorkoutScreen(vm: FitnessViewModel) {
    val state by vm.workoutState.collectAsState()
    val profile by vm.profileState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheetPrefill by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    val c = FitTheme.colors

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ScreenHeader("Workout planner", "Plan it, tick it off, keep the streak") }
            item { PillTabs(listOf("Today", "Templates", "History"), tab) { tab = it } }
            item { WeekStrip(state.weekDays, state.weeklyCount, state.weeklyGoal) }

            when (tab) {
                0 -> {
                    if (state.todayWorkouts.isEmpty()) item {
                        FitCard {
                            EmptyState("💪", "No workout scheduled", "Pick a template or design your own routine for today.",
                                actionLabel = "Browse templates", onAction = { tab = 1 })
                        }
                    }
                    items(state.plannedToday, key = { "p${it.id}" }) { w ->
                        ScheduledWorkoutCard(
                            workout = w,
                            vm = vm,
                            onDelete = { pendingDelete = w }
                        )
                    }
                    if (state.completedToday.isNotEmpty()) {
                        item { SectionHeader("Completed today") }
                        items(state.completedToday, key = { "c${it.id}" }) { w ->
                            WorkoutRow(w, onDelete = { pendingDelete = w })
                        }
                    }
                }
                1 -> {
                    item { SectionHeader("Templates", "Tap Use to schedule one for today") }
                    items(TEMPLATES, key = { it.name }) { t ->
                        TemplateCard(t, onUse = { sheetPrefill = t; showSheet = true })
                    }
                }
                else -> {
                    val history = state.allWorkouts.filter { it.completed || it.date < LocalDate.now().toString() }
                        .sortedByDescending { it.date }
                    if (history.isEmpty()) item {
                        EmptyState("📋", "No history yet", "Completed workouts show up here.")
                    } else {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricTile("✅", "Completed", "${state.allWorkouts.count { it.completed }}", "sessions", c.success, Modifier.weight(1f))
                                MetricTile("⏱️", "Total time", "${state.totalMinutes}", "min", c.workout, Modifier.weight(1f))
                            }
                        }
                        history.groupBy { it.date }.forEach { (date, list) ->
                            item(key = "h$date") { Eyebrow(friendlyDate(date), Modifier.padding(top = 8.dp)) }
                            items(list, key = { "w${it.id}" }) { w -> WorkoutRow(w, onDelete = { pendingDelete = w }) }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { sheetPrefill = null; showSheet = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New workout") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }

    if (showSheet) {
        DesignRoutineSheet(
            prefill = sheetPrefill,
            weightKg = profile.profile.weightKg,
            onDismiss = { showSheet = false },
            onSave = { w, ex -> vm.saveWorkout(w, ex); showSheet = false; tab = 0 }
        )
    }

    pendingDelete?.let { w ->
        ConfirmDeleteDialog(
            title = "Remove routine?",
            text = "“${w.name}” will be deleted. You can restore it from the Undo banner.",
            onConfirm = { vm.deleteWorkout(w) },
            onDismiss = { pendingDelete = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PIECES
// ═══════════════════════════════════════════════════════════════════════

/** Segmented pill tabs shared by several screens. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp)) {
            labels.forEachIndexed { i, label ->
                val sel = i == selected
                Surface(
                    onClick = { onSelect(i) },
                    shape = CircleShape,
                    color = if (sel) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (sel) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(days: List<DayMark>, done: Int, goal: Int) {
    val c = FitTheme.colors
    FitCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$done of $goal workouts this week", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Eyebrow("Goal: $goal / wk")
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                val mark = days.getOrElse(i) { DayMark.NONE }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow(l)
                    Spacer(Modifier.height(6.dp))
                    val desc = when (mark) {
                        DayMark.DONE -> "done"; DayMark.TODAY -> "today"; DayMark.PLANNED -> "planned"; else -> "no workout"
                    }
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (mark == DayMark.DONE) c.success.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (mark == DayMark.TODAY) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .semantics { contentDescription = "$l: $desc" },
                        contentAlignment = Alignment.Center
                    ) {
                        when (mark) {
                            DayMark.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = c.success, modifier = Modifier.size(20.dp))
                            DayMark.TODAY -> Icon(Icons.Outlined.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            DayMark.PLANNED -> Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            else -> Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledWorkoutCard(workout: WorkoutEntity, vm: FitnessViewModel, onDelete: () -> Unit) {
    val exercises by vm.exercisesFor(workout.id).collectAsState(initial = emptyList())
    var started by rememberSaveable(workout.id) { mutableStateOf(false) }
    var startedAt by rememberSaveable(workout.id) { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var menu by remember { mutableStateOf(false) }
    val c = FitTheme.colors
    val doneCount = exercises.count { it.done }

    LaunchedEffect(started, startedAt) {
        while (started) { elapsed = (System.currentTimeMillis() - startedAt) / 1000; delay(1000) }
    }

    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Scheduled today", Modifier.weight(1f))
            Pill(if (started) "In progress" else "Ready", if (started) c.burn else c.success, dot = true)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
        Text(workout.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOf("${workout.durationMinutes} min", "${exercises.size} exercises", workout.focus.ifBlank { workout.category })
                .joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (exercises.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            exercises.forEachIndexed { i, e ->
                ExerciseCheckRow(index = i + 1, exercise = e, onToggle = { vm.setExerciseDone(e, !e.done) })
            }
            Spacer(Modifier.height(8.dp))
            LinearMeter(doneCount.toFloat() / exercises.size, c.success, height = 6.dp)
            Text("$doneCount of ${exercises.size} done", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (!started) {
            PrimaryButton("Start workout", onClick = { started = true; startedAt = System.currentTimeMillis() }, icon = Icons.Filled.PlayArrow)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Elapsed")
                    Text(RideMath.formatDuration(elapsed), style = MaterialTheme.typography.headlineMedium)
                }
                Button(
                    onClick = {
                        val mins = (elapsed / 60).toInt()
                        // Use the real session length when it was tracked for at least 5 minutes.
                        val finished = if (mins >= 5) workout.copy(
                            durationMinutes = mins,
                            caloriesBurned = (workout.caloriesBurned * mins / workout.durationMinutes.coerceAtLeast(1))
                        ) else workout
                        vm.completeWorkout(finished)
                        started = false
                    },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.success, contentColor = c.onAccent)
                ) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text("Finish workout") }
            }
        }
    }
}

@Composable
private fun ExerciseCheckRow(index: Int, exercise: ExerciseEntity, onToggle: () -> Unit) {
    val c = FitTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("$index", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleSmall,
                textDecoration = if (exercise.done) TextDecoration.LineThrough else null,
                color = if (exercise.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(exerciseLabel(exercise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (exercise.done) c.success else Color.Transparent)
                .border(2.dp, if (exercise.done) c.success else MaterialTheme.colorScheme.outline, CircleShape)
                .semantics { contentDescription = if (exercise.done) "${exercise.name} done" else "${exercise.name} not done" },
            contentAlignment = Alignment.Center
        ) {
            if (exercise.done) Icon(Icons.Filled.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WorkoutRow(w: WorkoutEntity, onDelete: () -> Unit) {
    val c = FitTheme.colors
    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = if (w.completed) c.success else c.workout, emoji = categoryEmoji(w.category))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${w.durationMinutes} min · ${w.caloriesBurned} kcal · ${w.category}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (w.completed) Icon(Icons.Filled.CheckCircle, "Completed", tint = c.success, modifier = Modifier.size(20.dp))
            else Pill("Missed", MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete ${w.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TemplateCard(t: WorkoutTemplate, onUse: () -> Unit) {
    val c = FitTheme.colors
    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = c.workout, emoji = t.emoji, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t.minutes} min • ${t.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.exercises.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onUse, shape = CircleShape) { Text("Use") }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// DESIGN ROUTINE SHEET
// ═══════════════════════════════════════════════════════════════════════

private data class DraftExercise(val name: String, val sets: Int, val reps: Int, val seconds: Int = 0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DesignRoutineSheet(
    prefill: WorkoutTemplate?,
    weightKg: Float,
    onDismiss: () -> Unit,
    onSave: (WorkoutEntity, List<ExerciseEntity>) -> Unit
) {
    var title by remember { mutableStateOf(prefill?.name ?: "") }
    var minutes by remember { mutableStateOf((prefill?.minutes ?: 45).toString()) }
    var category by remember { mutableStateOf(prefill?.category ?: "Strength") }
    var focus by remember { mutableStateOf(prefill?.focus ?: "") }
    val drafts = remember {
        mutableStateListOf<DraftExercise>().apply {
            prefill?.exercises?.forEach { add(DraftExercise(it.name, it.sets, it.reps, it.seconds)) }
        }
    }
    var newName by remember { mutableStateOf("") }
    var newSets by remember { mutableStateOf("3") }
    var newReps by remember { mutableStateOf("10") }
    var alreadyDone by remember { mutableStateOf(false) }

    val mins = minutes.toIntOrNull() ?: 0
    val kcal = estimateKcal(category, mins, weightKg)
    val valid = title.isNotBlank() && mins in 1..600

    fun addDraft(name: String) {
        if (name.isBlank()) return
        drafts.add(DraftExercise(name.trim().take(60), newSets.toIntOrNull()?.coerceIn(1, 20) ?: 3, newReps.toIntOrNull()?.coerceIn(1, 200) ?: 10))
        newName = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("✨ Design routine", style = MaterialTheme.typography.headlineSmall) }
            item {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Workout title") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(minutes, { v -> minutes = v.filter(Char::isDigit).take(3) }, label = { Text("Target duration") },
                        suffix = { Text("min") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(focus, { focus = it.take(40) }, label = { Text("Focus") }, placeholder = { Text("e.g. Chest") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium)
                }
            }
            item {
                Eyebrow("Category")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { (name, emoji) ->
                        FilterChip(selected = category == name, onClick = { category = name },
                            label = { Text("$name $emoji") }, shape = CircleShape)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Exercises", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Pill("${drafts.size} selected", MaterialTheme.colorScheme.primary)
                }
            }
            items(drafts.size) { i ->
                val d = drafts[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall)
                        Text(if (d.seconds > 0) "${d.sets} × ${RideMath.formatDurationShort(d.seconds.toLong())}" else "${d.sets} × ${d.reps}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { drafts.removeAt(i) }) { Icon(Icons.Outlined.Close, "Remove ${d.name}") }
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newName, { newName = it }, label = { Text("Add exercise") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(newSets, { v -> newSets = v.filter(Char::isDigit).take(2) }, label = { Text("Sets") },
                                singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(newReps, { v -> newReps = v.filter(Char::isDigit).take(3) }, label = { Text("Reps") },
                                singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            FilledIconButton(onClick = { addDraft(newName) }, enabled = newName.isNotBlank()) {
                                Icon(Icons.Filled.Add, "Add exercise")
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SUGGESTIONS[category].orEmpty().filter { s -> drafts.none { it.name == s } }.forEach { s ->
                                SuggestionChip(onClick = { addDraft(s) }, label = { Text("+ $s") }, shape = CircleShape)
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Already completed", style = MaterialTheme.typography.titleSmall)
                        Text("Log it as done instead of scheduling it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = alreadyDone, onCheckedChange = { alreadyDone = it })
                }
            }
            item {
                Text("≈ $kcal kcal estimated for $mins min of ${category.lowercase()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                PrimaryButton(
                    if (alreadyDone) "Save as completed" else "Save & activate routine",
                    onClick = {
                        if (newName.isNotBlank()) addDraft(newName)
                        val w = WorkoutEntity(
                            date = LocalDate.now().toString(),
                            name = title.trim(),
                            category = category,
                            durationMinutes = mins,
                            caloriesBurned = kcal,
                            completed = alreadyDone,
                            focus = focus.trim()
                        )
                        val ex = drafts.map {
                            ExerciseEntity(workoutId = 0, name = it.name, sets = it.sets, repsPerSet = it.reps,
                                durationSeconds = it.seconds, done = alreadyDone)
                        }
                        onSave(w, ex)
                    },
                    enabled = valid,
                    icon = Icons.Filled.Check
                )
            }
        }
    }
}
