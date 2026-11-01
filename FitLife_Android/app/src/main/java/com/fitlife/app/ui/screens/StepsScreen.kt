package com.fitlife.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.data.local.DailyStepsEntity
import com.fitlife.app.ui.viewmodel.FitnessViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StepsScreen(vm: FitnessViewModel, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val state by vm.stepsState.collectAsState()
    val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US))

    var showLogModal by remember { mutableStateOf(false) }
    var manualSteps by remember { mutableStateOf("1000") }

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
                            "Steps & Activity",
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                color = NightHud.TextBody,
                                fontFamily = NightHud.Mono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Hero Progress Card (240px Ring)
            item {
                val progress = if (state.stepGoal > 0) (state.todaySteps.toFloat() / state.stepGoal).coerceIn(0f, 1f) else 0.78f
                val animProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(800), label = "ring")

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NightHud.Low)
                        .border(1.dp, NightHud.Border, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 18f
                            val radius = (size.minDimension - stroke) / 2f
                            drawCircle(color = NightHud.Container, radius = radius, style = Stroke(stroke))
                            drawArc(
                                color = NightHud.Neon,
                                startAngle = -90f,
                                sweepAngle = 360f * animProgress,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "GOAL: ${state.stepGoal.formatNum()}",
                                color = NightHud.Muted,
                                fontFamily = NightHud.Mono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.todaySteps.formatNum(),
                                color = NightHud.White,
                                fontFamily = NightHud.Mono,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 42.sp,
                                letterSpacing = (-1).sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "STEPS",
                                color = NightHud.Neon,
                                fontFamily = NightHud.Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatPill("Distance", "%.2f km".format(state.todayDistanceKm), Modifier.weight(1f))
                        StatPill("Burned", "${state.todayCaloriesBurned} kcal", Modifier.weight(1f))
                    }
                }
            }

            // Activity Trend 7-Day Bar Chart
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(NightHud.Low)
                        .border(1.dp, NightHud.Border, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Activity Trend",
                            color = NightHud.White,
                            fontFamily = NightHud.Headline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Past 7 Days",
                            color = NightHud.Muted,
                            fontFamily = NightHud.Mono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val weekData = buildWeekData(state.weekHistory)
                    val maxVal = maxOf(weekData.maxOfOrNull { it.second } ?: 1, state.stepGoal).toFloat()

                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weekData.forEach { (_, value, isToday) ->
                                val fraction = (value / maxVal).coerceIn(0.1f, 1f)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isToday) NightHud.Neon else NightHud.Container)
                                        .border(1.dp, if (isToday) NightHud.Neon else NightHud.Border, RoundedCornerShape(6.dp))
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            weekData.forEach { (label, _, isToday) ->
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = if (isToday) NightHud.Neon else NightHud.Muted,
                                    fontFamily = NightHud.Mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Recent History List
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Recent History",
                        color = NightHud.Muted,
                        fontFamily = NightHud.Mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )

                    HistoryRow("Today", "${state.todaySteps.formatNum()} steps", true)
                    HistoryRow("Yesterday", "10,420 steps", false)
                }
            }

            // Manual Step Entry Button
            item {
                Button(
                    onClick = { showLogModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NightHud.Neon, contentColor = NightHud.Black)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Log Steps Manually",
                        fontFamily = NightHud.Headline,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }

        // Manual Step Entry Modal Bottom Sheet
        if (showLogModal) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(onClick = { showLogModal = false }),
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("👟", fontSize = 24.sp)
                            Text(
                                "Manual Step Entry",
                                color = NightHud.White,
                                fontFamily = NightHud.Headline,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(onClick = { showLogModal = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = NightHud.Muted)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Step Count",
                            color = NightHud.Muted,
                            fontFamily = NightHud.Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = manualSteps,
                            onValueChange = { manualSteps = it.filter(Char::isDigit) },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(500, 1000, 2500).forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    val curr = manualSteps.toIntOrNull() ?: 0
                                    manualSteps = (curr + preset).toString()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = NightHud.Container, contentColor = NightHud.White),
                                border = BorderStroke(1.dp, NightHud.Border)
                            ) {
                                Text("+$preset", fontFamily = NightHud.Mono, fontSize = 12.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            vm.syncSteps()
                            showLogModal = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NightHud.Neon, contentColor = NightHud.Black)
                    ) {
                        Text(
                            "Save to Activity History",
                            fontFamily = NightHud.Headline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NightHud.Lowest)
            .border(1.dp, NightHud.Border, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label.uppercase(),
            color = NightHud.Muted,
            fontFamily = NightHud.Mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = NightHud.White,
            fontFamily = NightHud.Mono,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun HistoryRow(label: String, value: String, isToday: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightHud.Low)
            .border(1.dp, NightHud.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isToday) NightHud.Neon else NightHud.Muted)
            )
            Text(
                label,
                color = NightHud.White,
                fontFamily = NightHud.Headline,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Text(
            value,
            color = NightHud.White,
            fontFamily = NightHud.Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

private fun buildWeekData(history: List<DailyStepsEntity>): List<Triple<String, Int, Boolean>> {
    val shortDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val todayStr = LocalDate.now().toString()
    return (6 downTo 0).map { i ->
        val d = LocalDate.now().minusDays(i.toLong())
        val entry = history.find { it.date == d.toString() }
        val dayIndex = d.dayOfWeek.value % 7 // Sunday = 0, Monday = 1, ..., Saturday = 6
        val dayLabel = shortDays[dayIndex]
        val isToday = (d.toString() == todayStr)
        Triple(dayLabel, entry?.steps ?: 0, isToday)
    }
}

private fun Int.formatNum(): String = "%,d".format(this)
