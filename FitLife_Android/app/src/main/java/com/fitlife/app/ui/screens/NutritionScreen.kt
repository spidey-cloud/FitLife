package com.fitlife.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlife.app.data.food.FoodItem
import com.fitlife.app.data.food.FoodRepository
import com.fitlife.app.data.local.FoodLogEntity
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

private data class Meal(val key: String, val label: String, val emoji: String)

private val MEALS = listOf(
    Meal("Breakfast", "Breakfast", "☀️"),
    Meal("Lunch", "Lunch", "🥗"),
    Meal("Dinner", "Dinner", "🌙"),
    Meal("Snack", "Snacks", "🍎"),
)

/** Default meal for "+ Add Food" by time of day. */
private fun mealForNow(): String = when (java.time.LocalTime.now().hour) {
    in 4..10 -> "Breakfast"
    in 11..15 -> "Lunch"
    in 16..17 -> "Snack"
    in 18..22 -> "Dinner"
    else -> "Snack"
}

@Composable
fun NutritionScreen(vm: FitnessViewModel, foodRepo: FoodRepository, onOpenPlans: () -> Unit) {
    val s by vm.calorieState.collectAsState()
    val c = FitTheme.colors
    var addTo by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Nutrition", "Calories & macros for today") }

        // ── Plan chip ─────────────────────────────────────────────────────────
        item {
            Surface(onClick = onOpenPlans, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.activePlan != null) "✂️" else "🎯")
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (s.activePlan?.name ?: "Profile goal") + " · ${fmtInt(s.calorieGoal)} kcal",
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (s.activePlan != null) "P ${s.proteinTarget.toInt()}g · C ${s.carbsTarget.toInt()}g · F ${s.fatTarget.toInt()}g"
                            else "No diet plan active — tap to choose one",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Outlined.Tune, "Diet plans")
                }
            }
        }

        // ── Calories remaining ────────────────────────────────────────────────
        item {
            FitCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val over = s.remaining < 0
                        Eyebrow(if (over) "Calories over" else "Calories remaining")
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmtInt(kotlin.math.abs(s.remaining)), style = MaterialTheme.typography.displaySmall,
                                color = if (over) c.warning else MaterialTheme.colorScheme.onSurface)
                            Text(" kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Text("${fmtInt(s.totalCalories)} of ${fmtInt(s.calorieGoal)} consumed",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.burnedCalories > 0) Text("🔥 ${fmtInt(s.burnedCalories)} kcal burned in workouts",
                            style = MaterialTheme.typography.bodySmall, color = c.burn, modifier = Modifier.padding(top = 4.dp))
                    }
                    RingProgress(s.progress, size = 96.dp, strokeWidth = 10.dp, color = if (s.progress > 1f) c.warning else c.protein) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                            Eyebrow("of goal")
                        }
                    }
                }
            }
        }

        // ── Macros ────────────────────────────────────────────────────────────
        item {
            FitCard {
                Text("Macronutrients", style = MaterialTheme.typography.titleLarge)
                Text("Daily goals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                MacroBar("Protein", s.proteinG, s.proteinTarget, c.protein)
                Spacer(Modifier.height(14.dp))
                MacroBar("Carbs", s.carbsG, s.carbsTarget, c.carbs)
                Spacer(Modifier.height(14.dp))
                MacroBar("Healthy fats", s.fatG, s.fatTarget, c.fat)
            }
        }

        item { PrimaryButton("Add food", onClick = { addTo = mealForNow() }, icon = Icons.Filled.Add) }

        // ── Meals ─────────────────────────────────────────────────────────────
        item {
            SectionHeader("Today's meals", "${s.byMeal.keys.size} of 4 meals logged")
        }
        items(MEALS, key = { it.key }) { meal ->
            val foods = s.byMeal[meal.key].orEmpty()
            val kcal = foods.sumOf { it.calories }
            val isOpen = meal.key in expanded
            FitCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(color = c.carbs, emoji = meal.emoji)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meal.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (foods.isEmpty()) "Not logged yet" else "${fmtInt(kcal)} kcal · ${foods.size} item${if (foods.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (foods.isNotEmpty()) IconButton(onClick = {
                        expanded = if (isOpen) expanded - meal.key else expanded + meal.key
                    }) {
                        Icon(if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            if (isOpen) "Hide ${meal.label} items" else "Show ${meal.label} items")
                    }
                    if (foods.isEmpty()) {
                        FilledTonalButton(onClick = { addTo = meal.key }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Log")
                        }
                    } else {
                        FilledTonalIconButton(onClick = { addTo = meal.key }) { Icon(Icons.Filled.Add, "Add to ${meal.label}") }
                    }
                }
                AnimatedVisibility(isOpen && foods.isNotEmpty()) {
                    Column(Modifier.padding(top = 8.dp)) {
                        foods.forEach { f -> LoggedFoodRow(f, onDelete = { vm.deleteFood(f) }) }
                    }
                }
            }
        }
    }

    addTo?.let { meal ->
        AddFoodSheet(
            meal = meal,
            foodRepo = foodRepo,
            recent = s.recentFoods,
            onMealChange = { addTo = it },
            onDismiss = { addTo = null },
            onLog = { vm.logFood(it) }
        )
    }
}

@Composable
private fun LoggedFoodRow(f: FoodLogEntity, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 56.dp)) {
            Text(f.foodName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("P ${f.proteinG.toInt()}g · C ${f.carbsG.toInt()}g · F ${f.fatG.toInt()}g")
                    if (f.servingGrams > 0) append(" · ${f.servingGrams.toInt()} g")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${f.calories}", style = MaterialTheme.typography.titleSmall)
        Text(" kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, "Remove ${f.foodName}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// ADD FOOD SHEET
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddFoodSheet(
    meal: String,
    foodRepo: FoodRepository,
    recent: List<FoodLogEntity>,
    onMealChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onLog: (FoodLogEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var local by remember { mutableStateOf(emptyList<FoodItem>()) }
    var online by remember { mutableStateOf(emptyList<FoodItem>()) }
    var searching by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var manual by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now().toString() }
    val mealLabel = MEALS.firstOrNull { it.key == meal }?.label ?: meal

    // Instant local results; debounced online search (Open Food Facts) for 3+ characters.
    LaunchedEffect(query) {
        local = foodRepo.searchLocal(query)
        online = emptyList(); offline = false
        if (query.trim().length >= 3) {
            delay(450)
            searching = true
            val r = foodRepo.searchApiDetailed(query)
            val localNames = local.map { it.displayName.lowercase() }.toSet()
            online = r.items.filter { it.displayName.lowercase() !in localNames }
            offline = r.failed
            searching = false
        } else searching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Eyebrow("Log food & nutrition")
                Text("Add to $mealLabel", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MEALS.forEach { m ->
                        FilterChip(selected = m.key == meal, onClick = { onMealChange(m.key) },
                            label = { Text("${m.emoji} ${m.label}") }, shape = CircleShape)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60); selected = null },
                    placeholder = { Text("Search foods, e.g. oats, paneer, banana") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                )
            }

            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item { Eyebrow("Recent", Modifier.padding(top = 6.dp)) }
                    items(recent, key = { "r${it.id}" }) { f ->
                        FitCard(contentPadding = PaddingValues(14.dp), onClick = {
                            onLog(f.copy(id = 0, date = today, mealType = meal))
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.foodName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("P ${f.proteinG.toInt()}g · C ${f.carbsG.toInt()}g · F ${f.fatG.toInt()}g",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${f.calories} kcal", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.Add, "Log again", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else item {
                    EmptyState("🥗", "Search the food database", "Hundreds of verified foods offline, plus Open Food Facts when you're online.")
                }
            } else {
                if (local.isNotEmpty()) item { Eyebrow("Verified database", Modifier.padding(top = 6.dp)) }
                items(local) { item ->
                    FoodResultCard(item, expanded = selected == item, onToggle = { selected = if (selected == item) null else item },
                        onLog = { onLog(it.copy(date = today, mealType = meal)); selected = null })
                }
                if (online.isNotEmpty()) item { Eyebrow("Open Food Facts", Modifier.padding(top = 6.dp)) }
                items(online) { item ->
                    FoodResultCard(item, expanded = selected == item, onToggle = { selected = if (selected == item) null else item },
                        onLog = { onLog(it.copy(date = today, mealType = meal)); selected = null })
                }
                if (offline) item {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Online search unavailable — showing offline results only.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!searching && local.isEmpty() && online.isEmpty() && !offline && query.trim().length >= 3) item {
                    EmptyState("🔍", "No matches", "Try a simpler name, or enter it manually below.")
                }
            }

            item {
                TextButton(onClick = { manual = !manual }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (manual) "Hide manual entry" else "Enter food manually")
                }
            }
            if (manual) item {
                ManualFoodForm(onLog = { onLog(it.copy(date = today, mealType = meal)); manual = false })
            }
        }
    }
}

/** Search result that expands inline to choose grams (per-100 g foods) or servings (portion foods). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodResultCard(item: FoodItem, expanded: Boolean, onToggle: () -> Unit, onLog: (FoodLogEntity) -> Unit) {
    val c = FitTheme.colors
    var amount by remember(item) { mutableStateOf(if (item.per100g) "100" else "1") }
    val qty = amount.replace(',', '.').toFloatOrNull() ?: 0f
    val factor = if (item.per100g) qty / 100f else qty
    val valid = if (item.per100g) qty in 1f..2000f else qty in 0.25f..20f

    FitCard(contentPadding = PaddingValues(14.dp), onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = if (item.source == "api") c.water else c.success, emoji = if (item.source == "api") "🌍" else "✅", size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (item.brand.isNotBlank()) append("${item.brand} · ")
                        append("${item.portionLabel} · P ${fmt1(item.proteinG)}g · C ${fmt1(item.carbsG)}g · F ${fmt1(item.fatG)}g")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${item.calories}", style = MaterialTheme.typography.titleMedium)
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow(if (item.per100g) "Amount (grams)" else "Servings of ${item.portionLabel}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = if (item.per100g) listOf("50", "100", "150", "200", "250") else listOf("0.5", "1", "1.5", "2", "3")
                    presets.forEach { p ->
                        FilterChip(selected = amount == p, onClick = { amount = p },
                            label = { Text(if (item.per100g) "$p g" else "× $p") }, shape = CircleShape)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        suffix = { Text(if (item.per100g) "g" else "×") },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp),
                        shape = MaterialTheme.shapes.medium
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${(item.calories * factor).toInt()} kcal", style = MaterialTheme.typography.titleMedium)
                        Text("P ${fmt1(item.proteinG * factor)} · C ${fmt1(item.carbsG * factor)} · F ${fmt1(item.fatG * factor)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PrimaryButton("Log", icon = Icons.Filled.Check, enabled = valid, onClick = {
                    val label = if (item.per100g) item.displayName
                    else "${item.displayName} · ${if (qty == 1f) "" else "${fmt1(qty).removeSuffix(".0")} × "}${item.portionLabel}"
                    onLog(
                        FoodLogEntity(
                            date = "", mealType = "",
                            foodName = label.take(80),
                            calories = (item.calories * factor).toInt(),
                            proteinG = item.proteinG * factor,
                            carbsG = item.carbsG * factor,
                            fatG = item.fatG * factor,
                            // Grams are only meaningful for per-100 g foods; 0 = portion-based.
                            servingGrams = if (item.per100g) qty else 0f
                        )
                    )
                })
            }
        }
    }
}

@Composable
private fun ManualFoodForm(onLog: (FoodLogEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var cb by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    val kcalInt = kcal.toIntOrNull()
    val valid = name.isNotBlank() && kcalInt != null && kcalInt in 0..5000
    fun num(v: String) = v.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 500f) ?: 0f
    val numberKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    FitCard(contentPadding = PaddingValues(14.dp)) {
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Food name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(kcal, { v -> kcal = v.filter(Char::isDigit).take(4) }, label = { Text("Calories") }, suffix = { Text("kcal") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(p, { p = it.take(5) }, label = { Text("Protein") }, suffix = { Text("g") }, singleLine = true,
                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numberKb)
            OutlinedTextField(cb, { cb = it.take(5) }, label = { Text("Carbs") }, suffix = { Text("g") }, singleLine = true,
                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numberKb)
            OutlinedTextField(f, { f = it.take(5) }, label = { Text("Fat") }, suffix = { Text("g") }, singleLine = true,
                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numberKb)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Log food", enabled = valid, icon = Icons.Filled.Check, onClick = {
            onLog(FoodLogEntity(date = "", mealType = "", foodName = name.trim(), calories = kcalInt ?: 0,
                proteinG = num(p), carbsG = num(cb), fatG = num(f), servingGrams = 0f))
        })
    }
}
