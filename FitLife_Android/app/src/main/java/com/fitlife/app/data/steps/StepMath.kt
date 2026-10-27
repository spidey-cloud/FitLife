package com.fitlife.app.data.steps

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The body measurements the step estimates depend on.
 * Out-of-range values (e.g. a half-filled profile) are clamped to plausible adult ranges.
 */
data class BodyProfile(
    val heightCm: Double,
    val weightKg: Double,
    val sex: Sex,
    val ageYears: Int
) {
    enum class Sex { MALE, FEMALE, UNSPECIFIED }

    companion object {
        val DEFAULT = BodyProfile(170.0, 70.0, Sex.UNSPECIFIED, 25)

        /** Builds a profile from the stored fields (dateOfBirth is "yyyy-MM-dd" or just a year). */
        fun of(
            heightCm: Float,
            weightKg: Float,
            sex: String,
            dateOfBirth: String,
            today: LocalDate = LocalDate.now()
        ): BodyProfile {
            val birthYear = dateOfBirth.take(4).toIntOrNull()
            val age = birthYear?.let { today.year - it }?.takeIf { it in 5..110 } ?: DEFAULT.ageYears
            return BodyProfile(
                heightCm = heightCm.toDouble().takeIf { it in 100.0..230.0 } ?: DEFAULT.heightCm,
                weightKg = weightKg.toDouble().takeIf { it in 25.0..300.0 } ?: DEFAULT.weightKg,
                sex = when (sex.trim().lowercase()) {
                    "male", "m" -> Sex.MALE
                    "female", "f" -> Sex.FEMALE
                    else -> Sex.UNSPECIFIED
                },
                ageYears = age
            )
        }
    }
}

/** Distance, energy and time derived from a step count for one specific person. */
data class StepMetrics(
    val steps: Int,
    val distanceMeters: Float,
    /** Net active energy: what walking burned on top of resting metabolism. */
    val activeKcal: Int,
    val activeMinutes: Int,
    val strideMeters: Double,
    val speedKmh: Double,
    val met: Double
)

/**
 * Personalised step maths.
 *
 *  • **Stride** — walking step length ≈ height × 0.415 (men) / 0.413 (women)
 *    (the standard pedometer rule of thumb; the average of the two when sex isn't given).
 *  • **Time & pace** — a daily step total has no timing, so walking is assumed at a typical
 *    moderate cadence of [CADENCE_SPM] steps/min (Tudor-Locke et al.). Pace = stride × cadence,
 *    so taller people with a longer stride walk faster for the same number of steps.
 *  • **Intensity** — the MET for that pace, interpolated from the walking entries of the
 *    Compendium of Physical Activities.
 *  • **Energy** — "corrected METs": 1 MET is taken as *this person's* resting rate
 *    (revised Harris-Benedict BMR ÷ 24 h, from weight, height, age and sex) rather than a
 *    generic 1 kcal/kg/h. Only the part above rest — (MET − 1) — is counted as active energy,
 *    matching what Health Connect and wearables report as "active calories".
 */
object StepMath {

    const val CADENCE_SPM = 100.0

    fun strideMeters(body: BodyProfile): Double {
        val factor = when (body.sex) {
            BodyProfile.Sex.MALE -> 0.415
            BodyProfile.Sex.FEMALE -> 0.413
            BodyProfile.Sex.UNSPECIFIED -> 0.414
        }
        return (body.heightCm / 100.0 * factor).coerceIn(0.40, 1.10)
    }

    /** Revised Harris-Benedict (Roza & Shizgal 1984) resting energy, kcal/day. */
    fun bmrKcalPerDay(body: BodyProfile): Double {
        val male = 88.362 + 13.397 * body.weightKg + 4.799 * body.heightCm - 5.677 * body.ageYears
        val female = 447.593 + 9.247 * body.weightKg + 3.098 * body.heightCm - 4.330 * body.ageYears
        val bmr = when (body.sex) {
            BodyProfile.Sex.MALE -> male
            BodyProfile.Sex.FEMALE -> female
            BodyProfile.Sex.UNSPECIFIED -> (male + female) / 2
        }
        return bmr.coerceAtLeast(800.0)
    }

    // (km/h, MET) — Compendium walking codes 17151, 17152, 17190, 17200, 17220, 17230,
    // with "very slow" (17150, 2.0 MET) anchored at 2.4 km/h.
    private val walkingMetTable = listOf(
        2.4 to 2.0, 3.2 to 2.8, 4.0 to 3.0, 4.8 to 3.5, 5.6 to 4.3, 6.4 to 5.0, 7.2 to 7.0
    )

    /** Walking MET for [speedKmh], linearly interpolated (no jumps between bands). */
    fun walkingMet(speedKmh: Double): Double {
        val t = walkingMetTable
        if (speedKmh <= t.first().first) return t.first().second
        if (speedKmh >= t.last().first) return t.last().second
        val i = t.indexOfFirst { it.first >= speedKmh }
        val (x0, y0) = t[i - 1]
        val (x1, y1) = t[i]
        return y0 + (y1 - y0) * (speedKmh - x0) / (x1 - x0)
    }

    fun metrics(steps: Int, body: BodyProfile): StepMetrics {
        val n = steps.coerceAtLeast(0)
        val stride = strideMeters(body)
        val minutes = n / CADENCE_SPM
        val speedKmh = stride * CADENCE_SPM * 60.0 / 1000.0
        val met = walkingMet(speedKmh)
        val kcalPerHourAtRest = bmrKcalPerDay(body) / 24.0
        val activeKcal = (met - 1.0) * kcalPerHourAtRest * (minutes / 60.0)
        return StepMetrics(
            steps = n,
            distanceMeters = (n * stride).toFloat(),
            activeKcal = activeKcal.roundToInt(),
            activeMinutes = minutes.toInt(),
            strideMeters = stride,
            speedKmh = speedKmh,
            met = met
        )
    }
}
