package com.fitlife.app.data.steps

import com.fitlife.app.data.steps.BodyProfile.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StepMathTest {

    private val man = BodyProfile(heightCm = 170.0, weightKg = 70.0, sex = Sex.MALE, ageYears = 30)

    // ── Stride ────────────────────────────────────────────────────────────────

    @Test
    fun stride_followsHeightAndSex() {
        assertEquals(0.7055, StepMath.strideMeters(man), 1e-4)                                   // 170 × 0.415
        assertEquals(0.6608, StepMath.strideMeters(BodyProfile(160.0, 55.0, Sex.FEMALE, 30)), 1e-4) // 160 × 0.413
        assertEquals(0.7470, StepMath.strideMeters(man.copy(heightCm = 180.0)), 1e-4)
    }

    @Test
    fun distance_isStepsTimesOwnStride() {
        assertEquals(7055f, StepMath.metrics(10_000, man).distanceMeters, 1f)
        val tall = StepMath.metrics(10_000, man.copy(heightCm = 190.0)).distanceMeters
        val short = StepMath.metrics(10_000, man.copy(heightCm = 155.0)).distanceMeters
        assertTrue("taller person covers more ground per step", tall > short + 1000f)
    }

    // ── Energy ────────────────────────────────────────────────────────────────

    @Test
    fun bmr_matchesRevisedHarrisBenedict() {
        // 88.362 + 13.397×70 + 4.799×170 − 5.677×30
        assertEquals(1671.67, StepMath.bmrKcalPerDay(man), 0.05)
        // 447.593 + 9.247×55 + 3.098×160 − 4.330×30
        assertEquals(1321.96, StepMath.bmrKcalPerDay(BodyProfile(160.0, 55.0, Sex.FEMALE, 30)), 0.05)
    }

    @Test
    fun activeKcal_referenceValue() {
        // stride 0.7055 m × 100 spm → 4.233 km/h → MET 3.146; 100 min at (3.146 − 1) × 1671.67/24 kcal/h
        val m = StepMath.metrics(10_000, man)
        assertEquals(4.233, m.speedKmh, 0.001)
        assertEquals(3.146, m.met, 0.001)
        assertEquals(100, m.activeMinutes)
        assertEquals(249, m.activeKcal)
    }

    @Test
    fun heavierPerson_burnsMore() {
        val light = StepMath.metrics(10_000, man.copy(weightKg = 60.0)).activeKcal
        val heavy = StepMath.metrics(10_000, man.copy(weightKg = 100.0)).activeKcal
        assertTrue("$heavy vs $light", heavy > light * 1.2)
    }

    @Test
    fun olderPerson_burnsSlightlyLess() {
        val young = StepMath.metrics(10_000, man.copy(ageYears = 20)).activeKcal
        val old = StepMath.metrics(10_000, man.copy(ageYears = 70)).activeKcal
        assertTrue("$old vs $young", old < young)
    }

    @Test
    fun differentPeople_getDifferentNumbers() {
        val smallWoman = StepMath.metrics(10_000, BodyProfile(158.0, 52.0, Sex.FEMALE, 35))
        val bigMan = StepMath.metrics(10_000, BodyProfile(188.0, 95.0, Sex.MALE, 35))
        assertTrue(bigMan.distanceMeters > smallWoman.distanceMeters)
        assertTrue(bigMan.activeKcal > smallWoman.activeKcal * 1.5)
    }

    @Test
    fun scalesLinearlyWithSteps() {
        val a = StepMath.metrics(5_000, man)
        val b = StepMath.metrics(10_000, man)
        assertEquals(a.distanceMeters * 2, b.distanceMeters, 1f)
        assertEquals(a.activeKcal * 2.0, b.activeKcal.toDouble(), 1.0)
    }

    @Test
    fun zeroAndNegativeSteps_giveZero() {
        for (n in listOf(0, -50)) {
            val m = StepMath.metrics(n, man)
            assertEquals(0, m.steps)
            assertEquals(0f, m.distanceMeters, 0f)
            assertEquals(0, m.activeKcal)
            assertEquals(0, m.activeMinutes)
        }
    }

    // ── MET curve ─────────────────────────────────────────────────────────────

    @Test
    fun met_isInterpolatedAndClamped() {
        assertEquals(3.0, StepMath.walkingMet(4.0), 1e-9)
        assertEquals(3.25, StepMath.walkingMet(4.4), 1e-9)       // halfway between 3.0 and 3.5
        assertEquals(2.0, StepMath.walkingMet(1.0), 1e-9)
        assertEquals(7.0, StepMath.walkingMet(12.0), 1e-9)
        // no jumps: tiny speed change → tiny MET change
        assertTrue(StepMath.walkingMet(4.801) - StepMath.walkingMet(4.799) < 0.01)
    }

    // ── Profile parsing ───────────────────────────────────────────────────────

    @Test
    fun profile_parsesStoredFields() {
        val today = LocalDate.of(2026, 10, 27)
        val p = BodyProfile.of(165f, 62f, "Female", "1996-01-01", today)
        assertEquals(BodyProfile(165.0, 62.0, Sex.FEMALE, 30), p)
        assertEquals(Sex.MALE, BodyProfile.of(170f, 70f, "Male", "", today).sex)
        assertEquals(Sex.UNSPECIFIED, BodyProfile.of(170f, 70f, "", "", today).sex)
    }

    @Test
    fun profile_missingOrNonsenseValues_fallBackToDefaults() {
        val today = LocalDate.of(2026, 10, 27)
        val p = BodyProfile.of(0f, -3f, "", "abcd", today)
        assertEquals(BodyProfile.DEFAULT, p)
        assertEquals(BodyProfile.DEFAULT.ageYears, BodyProfile.of(170f, 70f, "Male", "2026-01-01", today).ageYears)
    }
}
