package com.fitlife.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.data.local.UserProfileEntity
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate

@Composable
fun OnboardingScreen(vm: FitnessViewModel) {
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    // Profile fields
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("Male") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var weightGoal by remember { mutableStateOf("maintain") }
    var activityLevel by remember { mutableStateOf("moderate") }
    var stepGoal by remember { mutableFloatStateOf(10000f) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top progress dots
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(totalSteps) { i ->
                    val active = i <= step
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(if (i == step) 32.dp else 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            // Step content
            AnimatedContent(targetState = step, transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            }, label = "ob_step") { currentStep ->
                when (currentStep) {
                    0 -> WelcomeStep(onConnectGoogleFit = { vm.connectGoogleFitAndSkip() })
                    1 -> ProfileStep(name, age, sex, height, weight,
                        onName={name=it}, onAge={age=it}, onSex={sex=it}, onHeight={height=it}, onWeight={weight=it})
                    2 -> GoalStep(weightGoal, activityLevel, onGoal={weightGoal=it}, onActivity={activityLevel=it})
                    3 -> StepGoalStep(stepGoal, onSlider={stepGoal=it})
                }
            }

            Spacer(Modifier.weight(1f))

            // Navigation buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, Modifier.weight(1f)) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (step < totalSteps - 1) {
                            step++
                        } else {
                            val profile = UserProfileEntity(
                                name = name.ifBlank { "Friend" },
                                dateOfBirth = "${LocalDate.now().year - (age.toIntOrNull() ?: 25)}-01-01",
                                sex = sex,
                                heightCm = height.toFloatOrNull() ?: 170f,
                                weightKg = weight.toFloatOrNull() ?: 70f,
                                dailyStepGoal = stepGoal.toInt(),
                                weightGoal = weightGoal,
                                activityLevel = activityLevel,
                                onboardingComplete = true
                            )
                            vm.completeOnboarding(profile)
                        }
                    },
                    modifier = Modifier.weight(if (step > 0) 1f else Float.MAX_VALUE)
                ) {
                    Text(if (step < totalSteps - 1) "Continue" else "Start FitLife 🎉",
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WelcomeStep(onConnectGoogleFit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("F", fontSize = 44.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Welcome to FitLife", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Your all-in-one fitness companion.\nSteps · Workouts · Calories · Diet · Wellbeing",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
        Spacer(Modifier.height(24.dp))
        FeatureRow(Icons.Outlined.DirectionsWalk, "Step Counter", "Track steps with Google Health Connect")
        FeatureRow(Icons.Outlined.FitnessCenter, "Workout Planner", "Log exercises and track progress")
        FeatureRow(Icons.Outlined.Restaurant, "Calorie & Diet", "Monitor nutrition and macro targets")
        FeatureRow(Icons.Outlined.Favorite, "Digital Wellbeing", "Sleep, mood, water and mindfulness")
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConnectGoogleFit,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Connect Google Fit & Skip Setup", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileStep(name:String, age:String, sex:String, height:String, weight:String,
                        onName:(String)->Unit, onAge:(String)->Unit, onSex:(String)->Unit,
                        onHeight:(String)->Unit, onWeight:(String)->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Tell us about you", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("We'll personalise your goals and calorie targets.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(name, onName, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(age, onAge, label = { Text("Age") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Column(Modifier.weight(1f)) {
                Text("Sex", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Male","Female").forEach { s ->
                        FilterChip(selected = sex == s, onClick = { onSex(s) }, label = { Text(s) })
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(height, onHeight, label = { Text("Height (cm)") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(weight, onWeight, label = { Text("Weight (kg)") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
}

@Composable
private fun GoalStep(weightGoal:String, activityLevel:String, onGoal:(String)->Unit, onActivity:(String)->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Your fitness goal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("We'll set your daily calorie target accordingly.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        listOf(
            Triple("lose","🔥 Lose Weight","Calorie deficit · burn more than you eat"),
            Triple("maintain","⚖️ Maintain Weight","Stay balanced · eat at your TDEE"),
            Triple("gain","💪 Build Muscle","Calorie surplus · fuel your gains"),
        ).forEach { (key, title, sub) ->
            Card(
                onClick = { onGoal(key) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(14.dp),
                border = if (weightGoal == key) CardDefaults.outlinedCardBorder() else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (weightGoal == key) MaterialTheme.colorScheme.primaryContainer.copy(.4f)
                    else MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title.take(2), fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title.drop(3), fontWeight = FontWeight.SemiBold)
                        Text(sub, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    if (weightGoal == key)
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Activity level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("sedentary" to "Sedentary","light" to "Light","moderate" to "Moderate","active" to "Active").forEach { (k,l) ->
                FilterChip(selected = activityLevel == k, onClick = { onActivity(k) }, label = { Text(l) })
            }
        }
    }
}

@Composable
private fun StepGoalStep(stepGoal: Float, onSlider: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Daily step goal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("You can change this anytime in your profile.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Text("%,d".format(stepGoal.toInt()), style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("steps per day", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Slider(value = stepGoal, onValueChange = onSlider, valueRange = 3000f..25000f, steps = 43,
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("3,000", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("25,000", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(5000, 7500, 10000, 15000).forEach { v ->
                AssistChip(onClick = { onSlider(v.toFloat()) },
                    label = { Text("%,d".format(v)) })
            }
        }
    }
}
