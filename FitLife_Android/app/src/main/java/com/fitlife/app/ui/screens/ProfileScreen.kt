package com.fitlife.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(vm: FitnessViewModel, healthConnectAvailable: Boolean, onBack: () -> Unit) {
    val state by vm.profileState.collectAsState()
    val p = state.profile
    val c = FitTheme.colors

    var name by remember(state.loaded) { mutableStateOf(p.name) }
    var birthYear by remember(state.loaded) { mutableStateOf(p.dateOfBirth.take(4)) }
    var sex by remember(state.loaded) { mutableStateOf(p.sex) }
    var height by remember(state.loaded) { mutableStateOf(p.heightCm.toInt().toString()) }
    var weight by remember(state.loaded) { mutableStateOf(fmt1(p.weightKg).removeSuffix(".0")) }
    var activity by remember(state.loaded) { mutableStateOf(p.activityLevel) }
    var goal by remember(state.loaded) { mutableStateOf(p.weightGoal) }
    var steps by remember(state.loaded) { mutableStateOf(p.dailyStepGoal.toString()) }
    var water by remember(state.loaded) { mutableStateOf(p.dailyWaterGoal.toString()) }
    var rideKm by remember(state.loaded) { mutableStateOf(p.dailyRideGoalKm.toInt().toString()) }
    var weekly by remember(state.loaded) { mutableStateOf(p.weeklyWorkoutGoal.toString()) }

    val year = birthYear.toIntOrNull()
    val thisYear = LocalDate.now().year
    val h = height.toFloatOrNull(); val w = weight.replace(',', '.').toFloatOrNull()
    val valid = name.isNotBlank() && h != null && h in 100f..250f && w != null && w in 25f..300f &&
        (year == null || year in (thisYear - 100)..(thisYear - 10))
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Profile & settings", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("⚖️", "BMI", if (state.bmi > 0) fmt1(state.bmi) else "—", "", c.steps, Modifier.weight(1f),
                        sub = when { state.bmi <= 0f -> ""; state.bmi < 18.5f -> "Underweight"; state.bmi < 25f -> "Healthy range"; state.bmi < 30f -> "Overweight"; else -> "Obese" })
                    MetricTile("🔥", "Daily target", fmtInt(state.tdee), "kcal", c.burn, Modifier.weight(1f), sub = "From your profile")
                }
            }

            // ── Appearance ────────────────────────────────────────────────────
            item {
                FitCard {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Text("Light uses Kinetic Clarity; dark uses Night Ride.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    PillTabs(listOf("System", "Light", "Dark"), listOf("system", "light", "dark").indexOf(p.themeMode).coerceAtLeast(0)) {
                        vm.setThemeMode(listOf("system", "light", "dark")[it])
                    }
                }
            }

            // ── About you ─────────────────────────────────────────────────────
            item {
                FitCard {
                    Text("About you", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(birthYear, { v -> birthYear = v.filter(Char::isDigit).take(4) }, label = { Text("Birth year") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                        OutlinedTextField(height, { v -> height = v.filter(Char::isDigit).take(3) }, label = { Text("Height") }, suffix = { Text("cm") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                        OutlinedTextField(weight, { v -> weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) }, label = { Text("Weight") },
                            suffix = { Text("kg") }, singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Spacer(Modifier.height(12.dp))
                    Eyebrow("Sex (for calorie formula)")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Male", "Female").forEach { s ->
                            FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(s) }, shape = CircleShape)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Eyebrow("Activity level")
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sedentary" to "Sedentary", "light" to "Light", "moderate" to "Moderate", "active" to "Active", "very_active" to "Very active")
                            .forEach { (k, l) -> FilterChip(selected = activity == k, onClick = { activity = k }, label = { Text(l) }, shape = CircleShape) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Eyebrow("Weight goal")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("lose" to "Lose", "maintain" to "Maintain", "gain" to "Gain").forEach { (k, l) ->
                            FilterChip(selected = goal == k, onClick = { goal = k }, label = { Text(l) }, shape = CircleShape)
                        }
                    }
                }
            }

            // ── Goals ─────────────────────────────────────────────────────────
            item {
                FitCard {
                    Text("Daily & weekly goals", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(steps, { v -> steps = v.filter(Char::isDigit).take(5) }, label = { Text("Steps") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                        OutlinedTextField(water, { v -> water = v.filter(Char::isDigit).take(2) }, label = { Text("Water") }, suffix = { Text("gl") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(rideKm, { v -> rideKm = v.filter(Char::isDigit).take(3) }, label = { Text("Ride") }, suffix = { Text("km") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                        OutlinedTextField(weekly, { v -> weekly = v.filter(Char::isDigit).take(1) }, label = { Text("Workouts") }, suffix = { Text("/ wk") },
                            singleLine = true, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, keyboardOptions = numKb)
                    }
                }
            }

            item {
                PrimaryButton("Save profile", enabled = valid, icon = Icons.Filled.Check, onClick = {
                    vm.saveProfile(
                        p.copy(
                            name = name.trim(),
                            dateOfBirth = year?.let { "$it-01-01" } ?: p.dateOfBirth,
                            sex = sex,
                            heightCm = h ?: p.heightCm,
                            weightKg = w ?: p.weightKg,
                            activityLevel = activity,
                            weightGoal = goal,
                            dailyStepGoal = (steps.toIntOrNull() ?: p.dailyStepGoal).coerceIn(1000, 50000),
                            dailyWaterGoal = (water.toIntOrNull() ?: p.dailyWaterGoal).coerceIn(1, 16),
                            dailyRideGoalKm = (rideKm.toFloatOrNull() ?: p.dailyRideGoalKm).coerceIn(1f, 500f),
                            weeklyWorkoutGoal = (weekly.toIntOrNull() ?: p.weeklyWorkoutGoal).coerceIn(1, 7)
                        )
                    )
                })
            }

            // ── Connections & privacy ─────────────────────────────────────────
            item {
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(color = c.success, emoji = "❤️")
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Health Connect", style = MaterialTheme.typography.titleSmall)
                            Text(if (healthConnectAvailable) "Available — workouts and rides sync when permitted" else "Not installed on this device",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Your health data stays on this device. It's excluded from cloud backups and device-to-device transfer, " +
                                "network traffic is HTTPS-only, and ride locations are only collected while you're recording.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
