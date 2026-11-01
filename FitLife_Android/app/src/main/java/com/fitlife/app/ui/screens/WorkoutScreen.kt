package com.fitlife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.data.local.WorkoutEntity
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutScreen(vm: FitnessViewModel, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val state by vm.workoutState.collectAsState()
    val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US))

    var showNewWorkoutDrawer by remember { mutableStateOf(false) }
    var newWorkoutName by remember { mutableStateOf("") }
    var newWorkoutDuration by remember { mutableStateOf("45") }
    var newWorkoutCalories by remember { mutableStateOf("320") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightHud.Black)
            .padding(contentPadding)
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Workout Planner",
                            color = NightHud.White,
                            fontFamily = NightHud.Headline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Real Logged Workouts • $todayLabel",
                            color = NightHud.Muted,
                            fontFamily = NightHud.Mono,
                            fontSize = 11.sp
                        )
                    }
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(NightHud.Container)
                            .border(1.dp, NightHud.Border, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NightHud.Neon)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "SYNCED",
                            color = NightHud.White,
                            fontFamily = NightHud.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Real Workouts List
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Logged Workouts", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${state.allWorkouts.size} SESSIONS", color = NightHud.Neon, fontFamily = NightHud.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (state.allWorkouts.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(NightHud.Lowest)
                            .border(1.dp, NightHud.Border, RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏋️", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No workouts logged yet", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap 'NEW WORKOUT' below to log your training session.", color = NightHud.Muted, fontFamily = NightHud.Mono, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(state.allWorkouts) { workout ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NightHud.Lowest)
                            .border(1.dp, NightHud.Border, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NightHud.Container),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💪", fontSize = 20.sp)
                            }
                            Column {
                                Text(workout.name, color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${workout.category} • ${workout.durationMinutes} min • ${workout.caloriesBurned} kcal", color = NightHud.Muted, fontFamily = NightHud.Mono, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { vm.deleteWorkout(workout.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = NightHud.Red)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // New Workout Floating Button
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 76.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Button(
                onClick = { showNewWorkoutDrawer = true },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NightHud.Neon, contentColor = NightHud.Black),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("NEW WORKOUT", fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // New Workout Drawer Sheet
        if (showNewWorkoutDrawer) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(onClick = { showNewWorkoutDrawer = false }),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(NightHud.Low)
                        .border(1.dp, NightHud.Border, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .padding(24.dp)
                        .clickable(enabled = false) {},
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Log New Workout", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { showNewWorkoutDrawer = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = NightHud.Muted)
                        }
                    }

                    OutlinedTextField(
                        value = newWorkoutName,
                        onValueChange = { newWorkoutName = it },
                        label = { Text("Workout Name (e.g. Strength Training)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NightHud.Container,
                            unfocusedContainerColor = NightHud.Container,
                            focusedBorderColor = NightHud.Neon,
                            unfocusedBorderColor = NightHud.Border,
                            focusedTextColor = NightHud.White,
                            unfocusedTextColor = NightHud.White
                        )
                    )
                    OutlinedTextField(
                        value = newWorkoutDuration,
                        onValueChange = { newWorkoutDuration = it },
                        label = { Text("Duration (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NightHud.Container,
                            unfocusedContainerColor = NightHud.Container,
                            focusedBorderColor = NightHud.Neon,
                            unfocusedBorderColor = NightHud.Border,
                            focusedTextColor = NightHud.White,
                            unfocusedTextColor = NightHud.White
                        )
                    )
                    OutlinedTextField(
                        value = newWorkoutCalories,
                        onValueChange = { newWorkoutCalories = it },
                        label = { Text("Calories Burned (kcal)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NightHud.Container,
                            unfocusedContainerColor = NightHud.Container,
                            focusedBorderColor = NightHud.Neon,
                            unfocusedBorderColor = NightHud.Border,
                            focusedTextColor = NightHud.White,
                            unfocusedTextColor = NightHud.White
                        )
                    )

                    Button(
                        onClick = {
                            val workout = WorkoutEntity(
                                date = LocalDate.now().toString(),
                                name = newWorkoutName.ifBlank { "Strength Session" },
                                category = "Strength",
                                durationMinutes = newWorkoutDuration.toIntOrNull() ?: 45,
                                caloriesBurned = newWorkoutCalories.toIntOrNull() ?: 320,
                                completed = true
                            )
                            vm.saveWorkout(workout, emptyList())
                            vm.postMessage("Workout logged successfully! 💪")
                            showNewWorkoutDrawer = false
                            newWorkoutName = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NightHud.Neon, contentColor = NightHud.Black)
                    ) {
                        Text("Save Workout", fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
