package com.fitlife.app.data.repository

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.fitlife.app.data.healthconnect.HealthConnectManager
import com.fitlife.app.data.local.*
import com.fitlife.app.data.steps.BodyProfile
import com.fitlife.app.data.steps.StepMath
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FitnessRepository @Inject constructor(
    private val db: FitLifeDatabase,
    private val hc: HealthConnectManager
) {

    // ── Profile ───────────────────────────────────────────────────────────────

    fun observeProfile(): Flow<UserProfileEntity?> = db.profileDao().observe()
    suspend fun getProfile(): UserProfileEntity? = db.profileDao().get()
    suspend fun saveProfile(profile: UserProfileEntity) {
        val before = getProfile()?.bodyProfile()
        db.profileDao().upsert(profile)
        // Height / weight / sex / age changed → every stored day's distance & kcal changes too.
        if (before != profile.bodyProfile()) recalculateStepMetrics()
    }

    /** The current user's body measurements (defaults until onboarding is done). */
    suspend fun bodyProfile(): BodyProfile = getProfile()?.bodyProfile() ?: BodyProfile.DEFAULT

    // ── Steps ─────────────────────────────────────────────────────────────────

    fun observeLast30DaysSteps(): Flow<List<DailyStepsEntity>> = db.stepsDao().observeLast30Days()

    suspend fun getTodaySteps(): DailyStepsEntity? =
        db.stepsDao().getByDate(LocalDate.now().toString())

    suspend fun addStepsManually(date: String, count: Int) {
        val existing = db.stepsDao().getByDate(date)
            ?: DailyStepsEntity(date = date)
        db.stepsDao().upsert(stepsEntity(date, existing.steps + count, bodyProfile(), existing.syncedWithFit))
    }

    suspend fun syncStepsFromHealthConnect(): Int {
        if (!hc.isAvailable || !hc.hasAllPermissions()) return 0
        val today = LocalDate.now()
        val summary = hc.getDailySummary(today)
        if (summary.steps == 0) return 0
        val existing = db.stepsDao().getByDate(today.toString())
            ?: DailyStepsEntity(date = today.toString())
        // Use whichever is higher — HC or local
        val best = maxOf(existing.steps, summary.steps)
        db.stepsDao().upsert(stepsEntity(today.toString(), best, bodyProfile(), synced = true))
        return best
    }

    /**
     * Re-derives distance / kcal / active minutes for every stored day from its step count and
     * the current profile. Only rows whose values actually change are written, and a row is
     * skipped if its step count changed while we were computing. Returns the rows updated.
     */
    suspend fun recalculateStepMetrics(): Int {
        val body = bodyProfile()
        var updated = 0
        for (row in db.stepsDao().getAll()) {
            val m = StepMath.metrics(row.steps, body)
            val same = row.caloriesBurned == m.activeKcal && row.activeMinutes == m.activeMinutes &&
                kotlin.math.abs(row.distanceMeters - m.distanceMeters) < 0.5f
            if (!same) updated += db.stepsDao().updateMetricsIfStepsUnchanged(
                row.date, row.steps, m.activeKcal, m.distanceMeters, m.activeMinutes
            )
        }
        return updated
    }

    suspend fun computeStreak(): Int {
        val profile = getProfile() ?: return 0
        val goal = profile.dailyStepGoal
        var streak = 0
        var checkDate = LocalDate.now()
        for (i in 0..60) {
            val s = db.stepsDao().getByDate(checkDate.toString())
            if (s != null && s.steps >= goal) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else if (i == 0) {
                // Today isn't finished yet — don't break the streak before the day is over.
                checkDate = checkDate.minusDays(1)
            } else break
        }
        return streak
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    fun observeAllWorkouts(): Flow<List<WorkoutEntity>> = db.workoutDao().observeAll()
    fun observeTodayWorkouts(date: String): Flow<List<WorkoutEntity>> = db.workoutDao().observeByDate(date)
    fun observeExercises(workoutId: Long): Flow<List<ExerciseEntity>> = db.workoutDao().observeExercises(workoutId)
    suspend fun getExercises(workoutId: Long): List<ExerciseEntity> = db.workoutDao().getExercises(workoutId)

    suspend fun saveWorkout(workout: WorkoutEntity, exercises: List<ExerciseEntity>): Long {
        val id = db.workoutDao().insert(workout)
        exercises.forEach { db.workoutDao().insertExercise(it.copy(workoutId = id)) }
        if (workout.completed) writeWorkoutToHealthConnect(workout)
        return id
    }

    /** Re-inserts a deleted workout with its original ids (used by "Undo"). */
    suspend fun restoreWorkout(workout: WorkoutEntity, exercises: List<ExerciseEntity>) {
        db.workoutDao().insert(workout)
        exercises.forEach { db.workoutDao().insertExercise(it) }
    }

    suspend fun setExerciseDone(exerciseId: Long, done: Boolean) =
        db.workoutDao().setExerciseDone(exerciseId, done)

    suspend fun completeWorkout(workout: WorkoutEntity) {
        db.workoutDao().update(workout.copy(completed = true))
        writeWorkoutToHealthConnect(workout)
    }

    private suspend fun writeWorkoutToHealthConnect(workout: WorkoutEntity) {
        if (hc.isAvailable && hc.hasAllPermissions()) {
            val now = Instant.now()
            val start = now.minusSeconds(workout.durationMinutes.coerceAtLeast(1) * 60L)
            val type = when (workout.category) {
                "Cardio" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                "HIIT" -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
                "Yoga" -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
                "Flexibility" -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
                else -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            }
            hc.writeExerciseSession(workout.name, start, now, type)
        }
    }

    suspend fun deleteWorkout(id: Long) {
        db.workoutDao().deleteExercisesForWorkout(id)
        db.workoutDao().deleteById(id)
    }

    // ── Food Log ──────────────────────────────────────────────────────────────

    fun observeFoodByDate(date: String): Flow<List<FoodLogEntity>> = db.foodLogDao().observeByDate(date)
    fun observeTotalCalories(date: String): Flow<Int?> = db.foodLogDao().observeTotalCalories(date)
    fun observeRecentFoods(): Flow<List<FoodLogEntity>> =
        db.foodLogDao().observeRecent(LocalDate.now().minusDays(30).toString())

    suspend fun logFood(food: FoodLogEntity): Long {
        val id = db.foodLogDao().insert(food)
        // Mirror to Health Connect
        if (hc.isAvailable && hc.hasAllPermissions()) {
            hc.writeNutrition(
                name = food.foodName,
                time = Instant.now(),
                calories = food.calories.toDouble(),
                proteinG = food.proteinG.toDouble(),
                carbsG = food.carbsG.toDouble(),
                fatG = food.fatG.toDouble()
            )
        }
        return id
    }

    /** Re-inserts a removed entry locally only (no duplicate Health Connect record). */
    suspend fun restoreFood(food: FoodLogEntity) { db.foodLogDao().insert(food) }

    suspend fun deleteFood(id: Long) = db.foodLogDao().deleteById(id)

    suspend fun getMacroTotals(date: String): Triple<Float, Float, Float> {
        val p = db.foodLogDao().totalProtein(date) ?: 0f
        val c = db.foodLogDao().totalCarbs(date) ?: 0f
        val f = db.foodLogDao().totalFat(date) ?: 0f
        return Triple(p, c, f)
    }

    // ── Diet Plans ────────────────────────────────────────────────────────────

    fun observeActivePlan(): Flow<DietPlanEntity?> = db.dietPlanDao().observeActivePlan()
    fun observeAllPlans(): Flow<List<DietPlanEntity>> = db.dietPlanDao().observeAll()

    suspend fun savePlan(plan: DietPlanEntity): Long {
        db.dietPlanDao().deactivateAll()
        return db.dietPlanDao().insert(plan.copy(isActive = true))
    }

    /** Updates an existing plan in place (keeps its active flag). */
    suspend fun updatePlan(plan: DietPlanEntity) = db.dietPlanDao().update(plan)

    suspend fun restorePlan(plan: DietPlanEntity) {
        if (plan.isActive) db.dietPlanDao().deactivateAll()
        db.dietPlanDao().insert(plan)
    }

    suspend fun activatePlan(id: Long) {
        db.dietPlanDao().deactivateAll()
        db.dietPlanDao().activate(id)
    }

    suspend fun deletePlan(id: Long) = db.dietPlanDao().deleteById(id)

    // ── Sleep ─────────────────────────────────────────────────────────────────

    fun observeSleep(): Flow<List<SleepLogEntity>> = db.sleepDao().observeLast14()
    suspend fun getSleepByDate(date: String) = db.sleepDao().getByDate(date)
    suspend fun logSleep(entry: SleepLogEntity) = db.sleepDao().upsert(entry)

    // ── Wellbeing ─────────────────────────────────────────────────────────────

    fun observeWellbeing(): Flow<List<WellbeingLogEntity>> = db.wellbeingDao().observeLast7()
    suspend fun getWellbeingByDate(date: String) = db.wellbeingDao().getByDate(date)
    suspend fun saveWellbeing(entry: WellbeingLogEntity) = db.wellbeingDao().upsert(entry)

    // ── Cycling ───────────────────────────────────────────────────────────────

    fun observeRides(): Flow<List<RideEntity>> = db.rideDao().observeAll()
    fun observeRide(id: Long): Flow<RideEntity?> = db.rideDao().observeById(id)

    /** Persists a finished ride and mirrors it to Health Connect as a biking session. */
    suspend fun saveRide(ride: RideEntity): Long {
        val id = db.rideDao().insert(ride)
        if (hc.isAvailable && hc.hasAllPermissions()) {
            hc.writeExerciseSession(
                name = ride.title,
                start = Instant.ofEpochMilli(ride.startEpochMs),
                end = Instant.ofEpochMilli(ride.endEpochMs.coerceAtLeast(ride.startEpochMs + 1000)),
                type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            )
        }
        return id
    }

    suspend fun restoreRide(ride: RideEntity) { db.rideDao().insert(ride) }
    suspend fun deleteRide(id: Long) = db.rideDao().deleteById(id)

    /** Personal-best labels earned by [ride] compared with all other rides. */
    suspend fun personalBests(ride: RideEntity): List<String> {
        val result = mutableListOf<String>()
        val longest = db.rideDao().longestDistanceExcluding(ride.id)
        val fastest = db.rideDao().fastestAvgExcluding(ride.id)
        if (longest != null && ride.distanceMeters > longest) result += "Longest ride"
        if (fastest != null && ride.avgSpeedKmh > fastest && ride.distanceMeters >= 1000f) result += "Fastest average speed"
        return result
    }

    // ── Health Connect helpers ────────────────────────────────────────────────

    val isHealthConnectAvailable get() = hc.isAvailable
    suspend fun hasHealthPermissions() = hc.hasAllPermissions()
    val healthPermissions get() = hc.requiredPermissions

    companion object {
        /**
         * Single place that turns a step count into a stored day, personalised for [body]
         * (stride from height & sex, energy from weight, height, age & sex — see [StepMath]).
         */
        fun stepsEntity(date: String, steps: Int, body: BodyProfile, synced: Boolean = false): DailyStepsEntity {
            val m = StepMath.metrics(steps, body)
            return DailyStepsEntity(
                date = date,
                steps = m.steps,
                caloriesBurned = m.activeKcal,
                distanceMeters = m.distanceMeters,
                activeMinutes = m.activeMinutes,
                syncedWithFit = synced
            )
        }
    }
}

/** Maps the stored profile to the measurements the step / energy maths needs. */
fun UserProfileEntity.bodyProfile(): BodyProfile =
    BodyProfile.of(heightCm = heightCm, weightKg = weightKg, sex = sex, dateOfBirth = dateOfBirth)
