package com.fitlife.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.data.food.FoodRepository
import com.fitlife.app.data.local.FoodLogEntity
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CaloriesScreen(vm: FitnessViewModel, foodRepo: FoodRepository, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val state by vm.calorieState.collectAsState()
    val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US))

    var showAddFoodModal by remember { mutableStateOf(false) }
    var selectedMeal by remember { mutableStateOf("Lunch") }

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
                            "Nutrition & Macros",
                            color = NightHud.White,
                            fontFamily = NightHud.Headline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            todayLabel,
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
                            "SYNC",
                            color = NightHud.White,
                            fontFamily = NightHud.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Active Diet Plan Banner
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(NightHud.Container)
                        .border(1.dp, NightHud.Border, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NightHud.Lowest)
                                .border(1.dp, NightHud.Border, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✂️", fontSize = 18.sp)
                        }
                        Column {
                            Text("Summer Cut · 2,150 kcal", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("High protein target", color = NightHud.Neon, fontFamily = NightHud.Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = NightHud.Muted)
                    }
                }
            }

            // Calorie Hero Ring & Remaining Equation
            item {
                val calProgress = if (state.calorieGoal > 0) (state.totalCalories.toFloat() / state.calorieGoal).coerceIn(0f, 1f) else 0.66f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(NightHud.Lowest)
                        .border(1.dp, NightHud.Border, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "CALORIES REMAINING",
                            color = NightHud.Muted,
                            fontFamily = NightHud.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${state.netCalories}",
                                color = NightHud.White,
                                fontFamily = NightHud.Mono,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 40.sp,
                                lineHeight = 40.sp
                            )
                            Text(
                                "kcal",
                                color = NightHud.Muted,
                                fontFamily = NightHud.Headline,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Text(
                            "${state.totalCalories} of ${state.calorieGoal} consumed",
                            color = NightHud.Muted,
                            fontFamily = NightHud.Mono,
                            fontSize = 11.sp
                        )
                    }

                    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 14f
                            val radius = (size.minDimension - stroke) / 2f
                            drawCircle(color = NightHud.Container, radius = radius, style = Stroke(stroke))
                            drawArc(
                                color = NightHud.Neon,
                                startAngle = -90f,
                                sweepAngle = 360f * calProgress,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(calProgress * 100).toInt()}%",
                                color = NightHud.Neon,
                                fontFamily = NightHud.Mono,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "OF GOAL",
                                color = NightHud.Muted,
                                fontFamily = NightHud.Mono,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Macro Target Bars
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(NightHud.Container)
                        .border(1.dp, NightHud.Border, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Macronutrients", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("DAILY GOALS", color = NightHud.Muted, fontFamily = NightHud.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    MacroBarRow("Protein", state.proteinG, 180f, NightHud.Neon)
                    MacroBarRow("Carbs", state.carbsG, 120f, NightHud.Cyan)
                    MacroBarRow("Healthy Fats", state.fatG, 50f, NightHud.Amber)
                }
            }

            // Add Food Action Button
            item {
                Button(
                    onClick = { selectedMeal = "Lunch"; showAddFoodModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NightHud.Neon, contentColor = NightHud.Black)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Food", fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Meal Logging Sections
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's Meals", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("4 MEALS", color = NightHud.Muted, fontFamily = NightHud.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    MealSectionCard("☀️", "Breakfast", "485 kcal", true) { selectedMeal = "Breakfast"; showAddFoodModal = true }
                    MealSectionCard("🥗", "Lunch", "680 kcal", true) { selectedMeal = "Lunch"; showAddFoodModal = true }
                    MealSectionCard("🌙", "Dinner", "Not logged yet", false) { selectedMeal = "Dinner"; showAddFoodModal = true }
                    MealSectionCard("🍎", "Snacks", "255 kcal", true) { selectedMeal = "Snacks"; showAddFoodModal = true }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Add Food Modal Sheet
        if (showAddFoodModal) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(onClick = { showAddFoodModal = false }),
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
                        Column {
                            Text("Add to $selectedMeal", color = NightHud.Neon, fontFamily = NightHud.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Log Food & Nutrition", color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        IconButton(onClick = { showAddFoodModal = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = NightHud.Muted)
                        }
                    }

                    OutlinedTextField(
                        value = "Greek Yogurt 0%",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NightHud.Lowest,
                            unfocusedContainerColor = NightHud.Lowest,
                            focusedBorderColor = NightHud.Neon,
                            unfocusedBorderColor = NightHud.Border,
                            focusedTextColor = NightHud.White,
                            unfocusedTextColor = NightHud.White
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            vm.syncSteps()
                            showAddFoodModal = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NightHud.Neon, contentColor = NightHud.Black)
                    ) {
                        Text("Log Food", fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroBarRow(label: String, current: Float, target: Float, color: Color) {
    val progress = (current / target).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(label, color = NightHud.White, fontFamily = NightHud.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text("${current.toInt()}g / ${target.toInt()}g", color = NightHud.Muted, fontFamily = NightHud.Mono, fontSize = 12.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(NightHud.Lowest)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun MealSectionCard(emoji: String, title: String, subtitle: String, isLogged: Boolean, onAddClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightHud.Container)
            .border(1.dp, NightHud.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(emoji, fontSize = 22.sp)
            Column {
                Text(title, color = NightHud.White, fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = if (isLogged) NightHud.Muted else NightHud.Dim, fontFamily = NightHud.Mono, fontSize = 11.sp)
            }
        }
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLogged) NightHud.Lowest else NightHud.Neon,
                contentColor = if (isLogged) NightHud.Neon else NightHud.Black
            ),
            border = if (isLogged) BorderStroke(1.dp, NightHud.Border) else null,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isLogged) "Add" else "Log", fontFamily = NightHud.Headline, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
