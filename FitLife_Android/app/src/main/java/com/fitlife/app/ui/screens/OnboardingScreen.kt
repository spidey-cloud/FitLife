package com.fitlife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.data.local.UserProfileEntity
import com.fitlife.app.ui.components.*
import com.fitlife.app.ui.theme.FitTheme
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: FitnessViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("Male") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var weightGoal by rememberSaveable { mutableStateOf("maintain") }
    var activity by rememberSaveable { mutableStateOf("moderate") }
    var stepGoal by rememberSaveable { mutableFloatStateOf(10000f) }
    val total = 4

    val ageOk = age.isEmpty() || age.toIntOrNull() in 10..100
    val heightOk = height.isEmpty() || height.toFloatOrNull()?.let { it in 100f..250f } == true
    val weightOk = weight.isEmpty() || weight.replace(',', '.').toFloatOrNull()?.let { it in 25f..300f } == true
    val canContinue = when (step) { 1 -> name.isNotBlank() && ageOk && heightOk && weightOk; else -> true }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp)) {
        // Progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.height(6.dp).width(if (i == step) 28.dp else 12.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut() },
            label = "onboarding",
            modifier = Modifier.weight(1f)
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    0 -> Welcome()
                    1 -> {
                        Title("Tell us about you", "We use this to personalise goals and calorie targets. It never leaves your phone.")
                        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Your name") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(age, { v -> age = v.filter(Char::isDigit).take(3) }, label = { Text("Age") }, singleLine = true,
                                isError = !ageOk, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(height, { v -> height = v.filter(Char::isDigit).take(3) }, label = { Text("Height") },
                                suffix = { Text("cm") }, singleLine = true, isError = !heightOk, modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(weight, { v -> weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                                label = { Text("Weight") }, suffix = { Text("kg") }, singleLine = true, isError = !weightOk,
                                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        Spacer(Modifier.height(16.dp))
                        Eyebrow("Sex (used by the calorie formula)")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Male", "Female").forEach { o ->
                                FilterChip(selected = sex == o, onClick = { sex = o }, label = { Text(o) }, shape = CircleShape)
                            }
                        }
                    }
                    2 -> {
                        Title("Your goal", "Sets your daily calorie target.")
                        listOf(
                            Triple("lose", "🔥 Lose weight", "Gentle deficit · about −500 kcal/day"),
                            Triple("maintain", "⚖️ Maintain", "Eat at your maintenance calories"),
                            Triple("gain", "💪 Build muscle", "Small surplus · about +300 kcal/day"),
                        ).forEach { (k, t, sub) ->
                            ChoiceCard(t, sub, selected = weightGoal == k) { weightGoal = k }
                        }
                        Spacer(Modifier.height(12.dp))
                        Eyebrow("Activity level")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("sedentary" to "Sedentary", "light" to "Light", "moderate" to "Moderate", "active" to "Active", "very_active" to "Very active")
                                .forEach { (k, l) -> FilterChip(selected = activity == k, onClick = { activity = k }, label = { Text(l) }, shape = CircleShape) }
                        }
                    }
                    else -> {
                        Title("Daily step goal", "You can change this any time in your profile.")
                        Text(fmtInt(stepGoal.toInt()), style = MaterialTheme.typography.displayMedium,
                            color = FitTheme.colors.steps, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("steps per day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        Slider(value = stepGoal, onValueChange = { stepGoal = (it / 500f).toInt() * 500f }, valueRange = 3000f..25000f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            listOf(5000, 7500, 10000, 15000).forEach { v ->
                                FilterChip(selected = stepGoal.toInt() == v, onClick = { stepGoal = v.toFloat() },
                                    label = { Text(fmtInt(v)) }, shape = CircleShape)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) SecondaryButton("Back", onClick = { step-- }, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (step < total - 1) step++
                    else vm.completeOnboarding(
                        UserProfileEntity(
                            name = name.trim().ifBlank { "Friend" },
                            dateOfBirth = "${LocalDate.now().year - (age.toIntOrNull() ?: 25)}-01-01",
                            sex = sex,
                            heightCm = height.toFloatOrNull() ?: 170f,
                            weightKg = weight.replace(',', '.').toFloatOrNull() ?: 70f,
                            dailyStepGoal = stepGoal.toInt(),
                            weightGoal = weightGoal,
                            activityLevel = activity,
                            onboardingComplete = true
                        )
                    )
                },
                enabled = canContinue,
                modifier = Modifier.weight(if (step > 0) 1.4f else 1f).heightIn(min = 56.dp),
                shape = CircleShape
            ) { Text(if (step < total - 1) "Continue" else "Start FitLife 🎉", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun Welcome() {
    val c = FitTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd))),
            contentAlignment = Alignment.Center
        ) { Text("F", fontSize = 48.sp, style = MaterialTheme.typography.displayMedium, color = c.onHero) }
        Spacer(Modifier.height(28.dp))
        Text("Welcome to FitLife", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Steps, workouts, rides, nutrition and wellbeing — in one calm place.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        listOf("👟" to "Automatic step counting", "🚴" to "GPS ride tracking, even offline", "🥗" to "Calorie & macro tracking", "🔒" to "Private — data stays on your phone")
            .forEach { (e, t) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(color = MaterialTheme.colorScheme.primary, emoji = e, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(t, style = MaterialTheme.typography.titleSmall)
                }
            }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ChoiceCard(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else FitTheme.colors.cardBorder),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
