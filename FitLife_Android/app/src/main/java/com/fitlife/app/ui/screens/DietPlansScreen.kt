package com.fitlife.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.local.DietPlanEntity
import com.fitlife.app.data.local.UserProfileEntity
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

private data class PlanTemplate(val key: String, val emoji: String, val name: String, val blurb: String)

private val PLAN_TEMPLATES = listOf(
    PlanTemplate("fatloss", "✂️", "Fat loss", "−500 kcal · high protein"),
    PlanTemplate("muscle", "💪", "Muscle gain", "+300 kcal · high protein"),
    PlanTemplate("maintain", "⚖️", "Maintain", "Balanced at maintenance"),
    PlanTemplate("keto", "🥑", "Keto", "Very low carb · high fat"),
)

/** Personalised targets from maintenance calories and body weight. */
private fun buildPlan(key: String, maintenance: Int, weightKg: Float): DietPlanEntity {
    val kcal = when (key) {
        "fatloss" -> maintenance - 500
        "muscle" -> maintenance + 300
        "keto" -> maintenance - 300
        else -> maintenance
    }.coerceAtLeast(1200)
    val protein = (weightKg * if (key == "fatloss" || key == "muscle") 2.0f else 1.6f).coerceIn(60f, 250f)
    val (carbs, fat) = if (key == "keto") {
        val c = 25f
        c to ((kcal - protein * 4 - c * 4) / 9f).coerceAtLeast(40f)
    } else {
        val f = kcal * (if (key == "maintain") 0.30f else 0.25f) / 9f
        ((kcal - protein * 4 - f * 9) / 4f).coerceAtLeast(50f) to f
    }
    val t = PLAN_TEMPLATES.first { it.key == key }
    return DietPlanEntity(
        name = t.name, goal = key, dailyCalorieTarget = kcal,
        proteinTargetG = protein.roundToInt().toFloat(), carbsTargetG = carbs.roundToInt().toFloat(), fatTargetG = fat.roundToInt().toFloat(),
        isActive = true, createdAt = LocalDate.now().toString()
    )
}

@Composable
fun DietPlansScreen(vm: FitnessViewModel, onBack: () -> Unit) {
    val diet by vm.dietState.collectAsState()
    val profile by vm.profileState.collectAsState()
    val c = FitTheme.colors
    var editing by remember { mutableStateOf<DietPlanEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DietPlanEntity?>(null) }
    val maintenance = remember(profile.profile) { vm.computeTdee(profile.profile.copy(weightGoal = "maintain")) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Diet plans", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Your active plan sets the calorie and macro targets on the Nutrition screen. " +
                        "Without a plan, targets come from your profile (${fmtInt(profile.profile.dailyCalorieGoal)} kcal).",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item { SectionHeader("Quick start", "Personalised from your maintenance of ${fmtInt(maintenance)} kcal") }
            items(PLAN_TEMPLATES, key = { it.key }) { t ->
                val plan = buildPlan(t.key, maintenance, profile.profile.weightKg)
                FitCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(color = c.protein, emoji = t.emoji)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${t.name} · ${fmtInt(plan.dailyCalorieTarget)} kcal", style = MaterialTheme.typography.titleSmall)
                            Text("${t.blurb}\nP ${plan.proteinTargetG.toInt()}g · C ${plan.carbsTargetG.toInt()}g · F ${plan.fatTargetG.toInt()}g",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val existing = diet.allPlans.firstOrNull { it.goal == t.key }
                        FilledTonalButton(onClick = {
                            // Re-use an earlier plan of the same type instead of piling up duplicates.
                            if (existing != null) {
                                vm.saveDietPlan(plan.copy(id = existing.id, isActive = true))
                                vm.activatePlan(existing.id)
                            } else vm.saveDietPlan(plan)
                        }, shape = CircleShape, enabled = existing?.isActive != true || existing.dailyCalorieTarget != plan.dailyCalorieTarget) {
                            Text(if (existing?.isActive == true && existing.dailyCalorieTarget == plan.dailyCalorieTarget) "Active" else "Use")
                        }
                    }
                }
            }

            item {
                SectionHeader("My plans", actionLabel = "New plan", onAction = { editing = null; showEditor = true })
            }
            if (diet.allPlans.isEmpty()) item {
                EmptyState("🥗", "No plans yet", "Use a quick-start plan above or create your own.")
            }
            items(diet.allPlans, key = { it.id }) { p ->
                FitCard(
                    contentPadding = PaddingValues(16.dp),
                    borderColor = if (p.isActive) MaterialTheme.colorScheme.primary else c.cardBorder
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                if (p.isActive) { Spacer(Modifier.width(8.dp)); Pill("Active", MaterialTheme.colorScheme.primary) }
                            }
                            Text("${fmtInt(p.dailyCalorieTarget)} kcal · P ${p.proteinTargetG.toInt()}g · C ${p.carbsTargetG.toInt()}g · F ${p.fatTargetG.toInt()}g",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = p; showEditor = true }) { Icon(Icons.Outlined.Edit, "Edit ${p.name}") }
                        IconButton(onClick = { pendingDelete = p }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${p.name}") }
                    }
                    if (!p.isActive) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.activatePlan(p.id) }, shape = CircleShape) { Text("Activate") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Create custom plan")
                }
            }
        }
    }

    if (showEditor) {
        PlanEditorSheet(
            initial = editing,
            profile = profile.profile,
            onDismiss = { showEditor = false },
            onSave = { vm.saveDietPlan(it); showEditor = false }
        )
    }
    pendingDelete?.let { p ->
        ConfirmDeleteDialog(
            title = "Delete plan?",
            text = "“${p.name}” will be removed." + if (p.isActive) " Targets will fall back to your profile goal." else "",
            onConfirm = { vm.deletePlan(p) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditorSheet(
    initial: DietPlanEntity?,
    profile: UserProfileEntity,
    onDismiss: () -> Unit,
    onSave: (DietPlanEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kcal by remember { mutableStateOf((initial?.dailyCalorieTarget ?: profile.dailyCalorieGoal).toString()) }
    var p by remember { mutableStateOf((initial?.proteinTargetG ?: (profile.weightKg * 1.8f).roundToInt().toFloat()).toInt().toString()) }
    var cb by remember { mutableStateOf((initial?.carbsTargetG ?: 200f).toInt().toString()) }
    var f by remember { mutableStateOf((initial?.fatTargetG ?: 65f).toInt().toString()) }
    val kcalInt = kcal.toIntOrNull() ?: 0
    val macroKcal = (p.toIntOrNull() ?: 0) * 4 + (cb.toIntOrNull() ?: 0) * 4 + (f.toIntOrNull() ?: 0) * 9
    val valid = name.isNotBlank() && kcalInt in 1000..6000
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (initial == null) "New diet plan" else "Edit plan", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Plan name") }, placeholder = { Text("e.g. Summer cut") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            OutlinedTextField(kcal, { v -> kcal = v.filter(Char::isDigit).take(4) }, label = { Text("Daily calories") }, suffix = { Text("kcal") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb,
                isError = kcal.isNotEmpty() && kcalInt !in 1000..6000,
                supportingText = { if (kcal.isNotEmpty() && kcalInt !in 1000..6000) Text("1,000 – 6,000 kcal") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(p, { v -> p = v.filter(Char::isDigit).take(3) }, label = { Text("Protein") }, suffix = { Text("g") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                OutlinedTextField(cb, { v -> cb = v.filter(Char::isDigit).take(3) }, label = { Text("Carbs") }, suffix = { Text("g") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                OutlinedTextField(f, { v -> f = v.filter(Char::isDigit).take(3) }, label = { Text("Fat") }, suffix = { Text("g") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
            }
            val diff = macroKcal - kcalInt
            Text(
                "Macros add up to ${fmtInt(macroKcal)} kcal" +
                    if (kcalInt > 0 && kotlin.math.abs(diff) > 100) " (${if (diff > 0) "+" else ""}${fmtInt(diff)} vs target)" else " ✓",
                style = MaterialTheme.typography.bodySmall,
                color = if (kcalInt > 0 && kotlin.math.abs(diff) > 100) FitTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryButton(if (initial == null) "Save & activate" else "Save changes", enabled = valid, icon = Icons.Filled.Check, onClick = {
                onSave(
                    (initial ?: DietPlanEntity(name = "", dailyCalorieTarget = 0, proteinTargetG = 0f, carbsTargetG = 0f, fatTargetG = 0f,
                        goal = "custom", isActive = true, createdAt = LocalDate.now().toString())).copy(
                        name = name.trim(),
                        dailyCalorieTarget = kcalInt,
                        proteinTargetG = (p.toIntOrNull() ?: 0).toFloat(),
                        carbsTargetG = (cb.toIntOrNull() ?: 0).toFloat(),
                        fatTargetG = (f.toIntOrNull() ?: 0).toFloat()
                    )
                )
            })
        }
    }
}
