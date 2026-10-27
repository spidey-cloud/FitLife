package com.fitlife.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════
// Design systems from the Stitch project "FitLife UI Redesign System"
//
//  • Kinetic Clarity — light theme. Primary #2563EB · Secondary #06B6D4 ·
//    Tertiary #10B981 · Neutral #64748B · Plus Jakarta Sans
//  • Night Ride — dark theme. Primary #CCFF00 · Secondary #00F5FF ·
//    Tertiary #FF9900 · Neutral #18181B · Space Grotesk / Geist / JetBrains Mono
// ═══════════════════════════════════════════════════════════════════════

object KineticClarity {
    val Primary = Color(0xFF2563EB)
    val PrimaryDark = Color(0xFF1D4ED8)
    val Secondary = Color(0xFF06B6D4)
    val Tertiary = Color(0xFF10B981)
    val Neutral = Color(0xFF64748B)

    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceLow = Color(0xFFF1F5F9)
    val SurfaceHigh = Color(0xFFE2E8F0)
    val Ink = Color(0xFF0F172A)
    val InkMuted = Color(0xFF64748B)
    val Outline = Color(0xFFE2E8F0)
    val OutlineStrong = Color(0xFFCBD5E1)
    val Error = Color(0xFFEF4444)

    val Orange = Color(0xFFF97316)
    val Amber = Color(0xFFF59E0B)
    val Indigo = Color(0xFF6366F1)
    val Violet = Color(0xFF8B5CF6)
}

object NightRide {
    val Lime = Color(0xFFCCFF00)
    val Cyan = Color(0xFF00F5FF)
    val Orange = Color(0xFFFF9900)
    val Neutral = Color(0xFF18181B)

    val Background = Color(0xFF09090B)
    val Surface = Color(0xFF18181B)
    val SurfaceLow = Color(0xFF111113)
    val SurfaceHigh = Color(0xFF27272A)
    val SurfaceHighest = Color(0xFF3F3F46)
    val Ink = Color(0xFFFAFAFA)
    val InkMuted = Color(0xFFA1A1AA)
    val Outline = Color(0xFF27272A)
    val OutlineStrong = Color(0xFF3F3F46)
    val Error = Color(0xFFFF5C5C)
    val Violet = Color(0xFFA78BFA)
    val Pink = Color(0xFFF472B6)
}

/**
 * Fixed meaning per metric, independent of Material roles, so "fat" or "burned" never
 * look like an error and each metric keeps one colour on every screen.
 */
@Immutable
data class FitColors(
    val steps: Color,
    val burn: Color,
    val water: Color,
    val sleep: Color,
    val ride: Color,
    val workout: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val success: Color,
    val warning: Color,
    val mood: Color,
    /** Content colour to put on top of an accent fill (e.g. text on a lime button). */
    val onAccent: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val cardBorder: Color,
    val isNightRide: Boolean
)

val KineticFitColors = FitColors(
    steps = KineticClarity.Primary,
    burn = KineticClarity.Orange,
    water = KineticClarity.Secondary,
    sleep = KineticClarity.Indigo,
    ride = KineticClarity.Tertiary,
    workout = KineticClarity.Violet,
    protein = KineticClarity.Primary,
    carbs = KineticClarity.Amber,
    fat = KineticClarity.Tertiary,
    success = KineticClarity.Tertiary,
    warning = KineticClarity.Amber,
    mood = KineticClarity.Violet,
    onAccent = Color.White,
    heroStart = KineticClarity.Primary,
    heroEnd = KineticClarity.Secondary,
    onHero = Color.White,
    cardBorder = KineticClarity.Outline,
    isNightRide = false
)

val NightRideFitColors = FitColors(
    steps = NightRide.Lime,
    burn = NightRide.Orange,
    water = NightRide.Cyan,
    sleep = NightRide.Violet,
    ride = NightRide.Lime,
    workout = NightRide.Orange,
    protein = NightRide.Lime,
    carbs = NightRide.Orange,
    fat = NightRide.Cyan,
    success = NightRide.Lime,
    warning = NightRide.Orange,
    mood = NightRide.Pink,
    onAccent = Color(0xFF0A0A0A),
    heroStart = Color(0xFF1C1F12),
    heroEnd = Color(0xFF111113),
    onHero = NightRide.Ink,
    cardBorder = NightRide.Outline,
    isNightRide = true
)

val LocalFitColors = staticCompositionLocalOf { KineticFitColors }
