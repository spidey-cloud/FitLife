package com.fitlife.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlife.app.ui.theme.FitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════
// FORMATTING HELPERS
// ═══════════════════════════════════════════════════════════════════════

fun fmtInt(n: Int): String = String.format(Locale.getDefault(), "%,d", n)
fun fmt1(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
fun fmt1(v: Float): String = fmt1(v.toDouble())

/** "Today", "Yesterday", or "Mon, 3 Mar" for an ISO yyyy-MM-dd date. */
fun friendlyDate(iso: String): String = try {
    val d = LocalDate.parse(iso)
    val today = LocalDate.now()
    when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }
} catch (e: Exception) { iso }

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello"
}

// ═══════════════════════════════════════════════════════════════════════
// SURFACES
// ═══════════════════════════════════════════════════════════════════════

/** Standard card: flat surface, hairline border, 24 dp corners (both design systems). */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = FitTheme.colors.cardBorder,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = containerColor, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Gradient hero surface (blue→cyan in Kinetic Clarity, deep lime-tinted in Night Ride). */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = FitTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .then(
                if (c.isNightRide) Modifier.border(1.dp, c.ride.copy(alpha = 0.35f), shape) else Modifier
            )
    ) {
        Column(Modifier.padding(24.dp)) {
            CompositionLocalProvider(LocalContentColor provides c.onHero) { content() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TEXT
// ═══════════════════════════════════════════════════════════════════════

/** Small uppercase label ("DAILY", "GPS LOCKED"…) — JetBrains Mono in Night Ride. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
}

/** Large page title used at the top of each tab instead of a TopAppBar. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CHIPS & BADGES
// ═══════════════════════════════════════════════════════════════════════

/** Pill with tinted background, e.g. "🔥 7D STREAK", "READY", "GPS LOCKED". */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leading: String? = null,
    dot: Boolean = false,
    filled: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (filled) color else color.copy(alpha = 0.14f),
        contentColor = if (filled) FitTheme.colors.onAccent else color
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (filled) FitTheme.colors.onAccent else color))
                Spacer(Modifier.width(6.dp))
            }
            if (leading != null) { Text(leading, fontSize = 13.sp); Spacer(Modifier.width(4.dp)) }
            Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    Pill(text = "${days}D streak", color = FitTheme.colors.burn, leading = "🔥", modifier = modifier)
}

/** Rounded square with an emoji or icon on a tinted background. */
@Composable
fun IconBadge(
    color: Color,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    size: Dp = 44.dp
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            emoji != null -> Text(emoji, fontSize = (size.value * 0.45f).sp)
            icon != null -> Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.5f))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PROGRESS
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 14.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) drawArc(
                color, -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun LinearMeter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "meter")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(trackColor)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(color))
    }
}

/** Metric tile from the dashboards: emoji, label, big value + unit, sub-line, optional meter. */
@Composable
fun MetricTile(
    emoji: String,
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    progress: Float? = null,
    onClick: (() -> Unit)? = null
) {
    FitCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(color = color, emoji = emoji, size = 34.dp)
            Spacer(Modifier.width(8.dp))
            Eyebrow(label, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            LinearMeter(progress, color, height = 6.dp)
        }
    }
}

/** Macro line: "Protein   110g / 180g" + meter. Over-target shows the excess amount. */
@Composable
fun MacroBar(label: String, current: Float, target: Float, color: Color, modifier: Modifier = Modifier) {
    val over = target > 0 && current > target
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = "$label ${current.toInt()} of ${target.toInt()} grams"
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${current.toInt()}g", style = MaterialTheme.typography.titleSmall)
            Text(" / ${target.toInt()}g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LinearMeter(if (target > 0) current / target else 0f, color)
        if (over) Text("+${(current - target).toInt()}g over", style = MaterialTheme.typography.labelSmall,
            color = FitTheme.colors.warning, modifier = Modifier.padding(top = 4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CHARTS (simple)
// ═══════════════════════════════════════════════════════════════════════

/**
 * Vertical bars with day labels. [highlightIndex] is drawn in [color]; bars that reached
 * [goal] use [color] at full strength, others are muted. Optional dashed goal line.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int = values.lastIndex,
    goal: Float? = null,
    height: Dp = 140.dp
) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(goal ?: 0f).coerceAtLeast(1f)
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val goalColor = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.52f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / max) * size.height
                val reached = goal != null && v >= goal
                val c = when {
                    i == highlightIndex -> color
                    reached -> color.copy(alpha = 0.55f)
                    else -> if (v > 0) color.copy(alpha = 0.28f) else muted
                }
                val x = slot * i + (slot - barW) / 2
                val barH = if (v > 0) h.coerceAtLeast(6.dp.toPx()) else 6.dp.toPx()
                drawRoundRect(c, Offset(x, size.height - barH), Size(barW, barH), CornerRadius(barW / 2, barW / 2))
            }
            if (goal != null && goal > 0) {
                val y = size.height - (goal / max) * size.height
                var x = 0f
                while (x < size.width) {
                    drawLine(goalColor, Offset(x, y), Offset((x + 8.dp.toPx()).coerceAtMost(size.width), y), 1.5.dp.toPx())
                    x += 14.dp.toPx()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == highlightIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BUTTONS
// ═══════════════════════════════════════════════════════════════════════

/** Full-width 56 dp pill — the main call to action on each screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// STATES & DIALOGS
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction, shape = CircleShape) { Text(actionLabel) }
        }
    }
}

/** "Remove Routine?"-style confirmation used before every destructive action. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Delete"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                shape = CircleShape
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Read-only field that opens a Material date picker. Replaces the old free-text
 * "yyyy-MM-dd" inputs. Future dates are blocked unless [allowFuture] is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    allowFuture: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    val display = remember(date) {
        val f = friendlyDate(date.toString())
        if (f == "Today" || f == "Yesterday") "$f · ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        else date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
    }
    Box(modifier) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, "Choose date") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        // Transparent overlay so a tap anywhere opens the picker.
        Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.medium).clickable { showPicker = true })
    }
    if (showPicker) {
        val todayUtc = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = allowFuture || utcTimeMillis <= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

/** 5-step mood picker; each option has an accessible label. */
@Composable
fun MoodSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val moods = listOf("😞" to "Awful", "😕" to "Low", "😐" to "Okay", "🙂" to "Good", "😄" to "Great")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        moods.forEachIndexed { i, (emoji, label) ->
            val score = i + 1
            val isSel = selected == score
            Surface(
                onClick = { onSelect(score) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSel) FitTheme.colors.mood.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSel) BorderStroke(2.dp, FitTheme.colors.mood) else null,
                modifier = Modifier.size(width = 58.dp, height = 68.dp).semantics { contentDescription = "Mood: $label" }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(emoji, fontSize = 24.sp)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Label/value pair used in summaries and dialogs. */
@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
