package com.fitlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlife.app.data.local.*
import com.fitlife.app.data.repository.FitnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════
// ONE-SHOT MESSAGES
// ═══════════════════════════════════════════════════════════════════════

/** Snackbar message, optionally with an action such as "Undo". */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

// ═══════════════════════════════════════════════════════════════════════
// UI STATE MODELS
// ═══════════════════════════════════════════════════════════════════════

data class StepsUiState(
    val todaySteps: Int = 0,
    val todayCaloriesBurned: Int = 0,
    val todayDistanceKm: Float = 0f,
    val todayActiveMinutes: Int = 0,
    val stepGoal: Int = 10000,
    val streakDays: Int = 0,
    val weekHistory: List<DailyStepsEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val healthConnectAvailable: Boolean = false
)

/** Status of one weekday in the Workout Planner week strip. */
enum class DayMark { DONE, TODAY, PLANNED, NONE, FUTURE }

data class WorkoutUiState(
    val todayWorkouts: List<WorkoutEntity> = emptyList(),
    val plannedToday: List<WorkoutEntity> = emptyList(),
    val completedToday: List<WorkoutEntity> = emptyList(),
    val allWorkouts: List<WorkoutEntity> = emptyList(),
    val weeklyCount: Int = 0,
    val weeklyGoal: Int = 4,
    val weekDays: List<DayMark> = List(7) { DayMark.NONE },
    val totalMinutes: Int = 0,
    val todayCaloriesBurned: Int = 0
)

data class CalorieUiState(
    val todayFoods: List<FoodLogEntity> = emptyList(),
    val totalCalories: Int = 0,
    /** Active diet plan target if a plan is active, otherwise the profile TDEE goal. */
    val calorieGoal: Int = 2000,
    val burnedCalories: Int = 0,
    val netCalories: Int = 0,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val proteinTarget: Float = 150f,
    val carbsTarget: Float = 200f,
    val fatTarget: Float = 65f,
    val activePlan: DietPlanEntity? = null,
    val byMeal: Map<String, List<FoodLogEntity>> = emptyMap(),
    val recentFoods: List<FoodLogEntity> = emptyList()
) {
    val remaining: Int get() = calorieGoal - totalCalories
    val progress: Float get() = if (calorieGoal > 0) totalCalories.toFloat() / calorieGoal else 0f
}

data class DietUiState(
    val activePlan: DietPlanEntity? = null,
    val allPlans: List<DietPlanEntity> = emptyList(),
    val todayCalories: Int = 0,
    val todayProtein: Float = 0f,
    val todayCarbs: Float = 0f,
    val todayFat: Float = 0f
)

data class WellbeingUiState(
    val todaySleep: SleepLogEntity? = null,
    val lastSleep: SleepLogEntity? = null,
    val recentSleep: List<SleepLogEntity> = emptyList(),
    val avgSleepHours: Float = 0f,
    val todayWellbeing: WellbeingLogEntity? = null,
    val weekWellbeing: List<WellbeingLogEntity> = emptyList(),
    val meditationMinutes: Int = 0,
    val waterGoal: Int = 8,
    val moodSavedToday: Boolean = false
)

data class ProfileUiState(
    val profile: UserProfileEntity = UserProfileEntity(),
    val bmi: Float = 0f,
    val tdee: Int = 0,
    val onboardingComplete: Boolean = false,
    /** False until the first database read finishes — avoids flashing onboarding on launch. */
    val loaded: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════════════════

@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val repo: FitnessRepository
) : ViewModel() {

    private val TODAY = LocalDate.now().toString()

    private val _steps = MutableStateFlow(StepsUiState())
    val stepsState: StateFlow<StepsUiState> = _steps.asStateFlow()

    private val _workout = MutableStateFlow(WorkoutUiState())
    val workoutState: StateFlow<WorkoutUiState> = _workout.asStateFlow()

    private val _calories = MutableStateFlow(CalorieUiState())
    val calorieState: StateFlow<CalorieUiState> = _calories.asStateFlow()

    private val _diet = MutableStateFlow(DietUiState())
    val dietState: StateFlow<DietUiState> = _diet.asStateFlow()

    private val _wellbeing = MutableStateFlow(WellbeingUiState())
    val wellbeingState: StateFlow<WellbeingUiState> = _wellbeing.asStateFlow()

    private val _profile = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profile.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    private fun say(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _message.tryEmit(UiMessage(text, actionLabel, onAction))
    }

    /** Plain one-off message from the UI layer (e.g. "Ride discarded"). */
    fun toast(text: String) = say(text)

    init {
        observeProfile()
        observeSteps()
        observeWorkouts()
        observeCalories()
        observeDiet()
        observeWellbeing()
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    private fun observeProfile() {
        repo.observeProfile()
            .onEach { p ->
                if (p == null) {
                    _profile.update { it.copy(loaded = true, onboardingComplete = false) }
                    return@onEach
                }
                val bmi = if (p.heightCm > 0) {
                    val h = p.heightCm / 100f
                    p.weightKg / (h * h)
                } else 0f
                val tdee = computeTdee(p)
                _profile.update { it.copy(profile = p, bmi = bmi, tdee = tdee, onboardingComplete = p.onboardingComplete, loaded = true) }
                _steps.update { it.copy(stepGoal = p.dailyStepGoal) }
                _workout.update { it.copy(weeklyGoal = p.weeklyWorkoutGoal) }
                _wellbeing.update { it.copy(waterGoal = p.dailyWaterGoal.coerceAtLeast(1)) }
                if (_calories.value.activePlan == null) _calories.update { it.withTargets(null, p.dailyCalorieGoal) }
            }
            .launchIn(viewModelScope)
    }

    fun saveProfile(profile: UserProfileEntity) = viewModelScope.launch {
        val tdee = computeTdee(profile)
        repo.saveProfile(profile.copy(dailyCalorieGoal = tdee))
        say("Profile saved")
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        repo.getProfile()?.let { repo.saveProfile(it.copy(themeMode = mode)) }
    }

    fun completeOnboarding(profile: UserProfileEntity) = viewModelScope.launch {
        val tdee = computeTdee(profile)
        repo.saveProfile(profile.copy(onboardingComplete = true, dailyCalorieGoal = tdee))
    }

    /** Revised Harris-Benedict BMR × activity factor, adjusted for the weight goal. */
    fun computeTdee(p: UserProfileEntity): Int {
        val age = p.dateOfBirth.take(4).toIntOrNull()?.let { LocalDate.now().year - it } ?: 25
        val bmr = if (p.sex == "Male")
            88.36 + (13.4 * p.weightKg.toDouble()) + (4.8 * p.heightCm.toDouble()) - (5.7 * age)
        else
            447.6 + (9.2 * p.weightKg.toDouble()) + (3.1 * p.heightCm.toDouble()) - (4.3 * age)
        val multiplier = when (p.activityLevel) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "very_active" -> 1.9
            else -> 1.55
        }
        val base = (bmr * multiplier).toInt()
        return when (p.weightGoal) {
            "lose" -> base - 500
            "gain" -> base + 300
            else -> base
        }.coerceAtLeast(1200)
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    private fun observeSteps() {
        repo.observeLast30DaysSteps()
            .onEach { list ->
                val today = list.firstOrNull { it.date == TODAY }
                val streak = repo.computeStreak()
                _steps.update {
                    it.copy(
                        todaySteps = today?.steps ?: 0,
                        todayCaloriesBurned = today?.caloriesBurned ?: 0,
                        todayDistanceKm = (today?.distanceMeters ?: 0f) / 1000f,
                        todayActiveMinutes = today?.activeMinutes ?: 0,
                        streakDays = streak,
                        weekHistory = list.take(7),
                        healthConnectAvailable = repo.isHealthConnectAvailable
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun addStepsManually(date: String, count: Int) = viewModelScope.launch {
        repo.addStepsManually(date, count)
        say("Added ${"%,d".format(count)} steps")
    }

    fun syncSteps() = viewModelScope.launch {
        _steps.update { it.copy(isSyncing = true) }
        val synced = repo.syncStepsFromHealthConnect()
        if (synced > 0) say("Synced ${"%,d".format(synced)} steps from Health Connect ✓")
        else say("Nothing new to sync")
        _steps.update { it.copy(isSyncing = false) }
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    private fun observeWorkouts() {
        repo.observeAllWorkouts()
            .onEach { workouts ->
                val now = LocalDate.now()
                val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val doneThisWeek = workouts.filter { it.completed && it.date >= monday.toString() && it.date <= now.toString() }
                val days = (0..6).map { i ->
                    val d = monday.plusDays(i.toLong()).toString()
                    val onDay = workouts.filter { it.date == d }
                    when {
                        onDay.any { it.completed } -> DayMark.DONE
                        d == TODAY -> DayMark.TODAY
                        onDay.isNotEmpty() -> DayMark.PLANNED
                        d > TODAY -> DayMark.FUTURE
                        else -> DayMark.NONE
                    }
                }
                val today = workouts.filter { w -> w.date == TODAY }
                _workout.update {
                    it.copy(
                        todayWorkouts = today,
                        plannedToday = today.filter { w -> !w.completed },
                        completedToday = today.filter { w -> w.completed },
                        allWorkouts = workouts,
                        weeklyCount = doneThisWeek.size,
                        weekDays = days,
                        totalMinutes = workouts.filter { w -> w.completed }.sumOf { w -> w.durationMinutes },
                        todayCaloriesBurned = today.filter { w -> w.completed }.sumOf { w -> w.caloriesBurned }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun exercisesFor(workoutId: Long): Flow<List<ExerciseEntity>> = repo.observeExercises(workoutId)

    fun saveWorkout(workout: WorkoutEntity, exercises: List<ExerciseEntity>) = viewModelScope.launch {
        repo.saveWorkout(workout, exercises)
        say(if (workout.completed) "${workout.name} logged 💪" else "${workout.name} scheduled for today")
    }

    fun setExerciseDone(exercise: ExerciseEntity, done: Boolean) = viewModelScope.launch {
        repo.setExerciseDone(exercise.id, done)
    }

    fun completeWorkout(workout: WorkoutEntity) = viewModelScope.launch {
        repo.completeWorkout(workout)
        say(
            if (repo.isHealthConnectAvailable) "Workout complete · synced to Health Connect ✓"
            else "Workout complete 🎉"
        )
    }

    /** Deletes immediately and offers Undo (restores the workout and its exercises). */
    fun deleteWorkout(workout: WorkoutEntity) = viewModelScope.launch {
        val exercises = repo.getExercises(workout.id)
        repo.deleteWorkout(workout.id)
        say("Routine deleted.", "Undo") {
            viewModelScope.launch { repo.restoreWorkout(workout, exercises) }
        }
    }

    // ── Calories / Nutrition ─────────────────────────────────────────────────

    private fun CalorieUiState.withTargets(plan: DietPlanEntity?, profileGoal: Int): CalorieUiState {
        if (plan != null) return copy(
            activePlan = plan,
            calorieGoal = plan.dailyCalorieTarget,
            proteinTarget = plan.proteinTargetG,
            carbsTarget = plan.carbsTargetG,
            fatTarget = plan.fatTargetG,
            netCalories = totalCalories - burnedCalories
        )
        val goal = profileGoal.takeIf { it > 0 } ?: 2000
        // No plan: balanced 30 / 40 / 30 split of the profile goal
        return copy(
            activePlan = null,
            calorieGoal = goal,
            proteinTarget = goal * 0.30f / 4f,
            carbsTarget = goal * 0.40f / 4f,
            fatTarget = goal * 0.30f / 9f,
            netCalories = totalCalories - burnedCalories
        )
    }

    private fun observeCalories() {
        combine(
            repo.observeFoodByDate(TODAY),
            repo.observeAllWorkouts(),
            repo.observeActivePlan(),
            repo.observeRecentFoods()
        ) { foods, workouts, plan, recent ->
            val total = foods.sumOf { it.calories }
            val burned = workouts.filter { it.date == TODAY && it.completed }.sumOf { it.caloriesBurned }
            val profileGoal = _profile.value.profile.dailyCalorieGoal
            _calories.update {
                it.copy(
                    todayFoods = foods,
                    totalCalories = total,
                    burnedCalories = burned,
                    proteinG = foods.sumOf { f -> f.proteinG.toDouble() }.toFloat(),
                    carbsG = foods.sumOf { f -> f.carbsG.toDouble() }.toFloat(),
                    fatG = foods.sumOf { f -> f.fatG.toDouble() }.toFloat(),
                    byMeal = foods.groupBy { food -> food.mealType },
                    recentFoods = recent.distinctBy { f -> f.foodName.lowercase() }.take(8)
                ).withTargets(plan, profileGoal)
            }
        }.launchIn(viewModelScope)
    }

    fun logFood(food: FoodLogEntity) = viewModelScope.launch {
        val id = repo.logFood(food)
        say("${food.foodName.take(32)} logged", "Undo") {
            viewModelScope.launch { repo.deleteFood(id) }
        }
    }

    fun deleteFood(food: FoodLogEntity) = viewModelScope.launch {
        repo.deleteFood(food.id)
        say("Item removed", "Undo") { viewModelScope.launch { repo.restoreFood(food) } }
    }

    // ── Diet ──────────────────────────────────────────────────────────────────

    private fun observeDiet() {
        combine(
            repo.observeActivePlan(),
            repo.observeAllPlans(),
            repo.observeFoodByDate(TODAY)
        ) { active, plans, foods ->
            _diet.update {
                it.copy(
                    activePlan = active,
                    allPlans = plans,
                    todayCalories = foods.sumOf { f -> f.calories },
                    todayProtein = foods.sumOf { f -> f.proteinG.toDouble() }.toFloat(),
                    todayCarbs = foods.sumOf { f -> f.carbsG.toDouble() }.toFloat(),
                    todayFat = foods.sumOf { f -> f.fatG.toDouble() }.toFloat()
                )
            }
        }.launchIn(viewModelScope)
    }

    fun saveDietPlan(plan: DietPlanEntity) = viewModelScope.launch {
        if (plan.id != 0L) {
            repo.updatePlan(plan)
            say("${plan.name} updated ✓")
        } else {
            repo.savePlan(plan)
            say("${plan.name} activated ✓")
        }
    }

    fun activatePlan(id: Long) = viewModelScope.launch {
        repo.activatePlan(id)
        say("Plan activated")
    }

    fun deletePlan(plan: DietPlanEntity) = viewModelScope.launch {
        repo.deletePlan(plan.id)
        say("Plan deleted", "Undo") { viewModelScope.launch { repo.restorePlan(plan) } }
    }

    // ── Wellbeing ─────────────────────────────────────────────────────────────

    private fun observeWellbeing() {
        combine(
            repo.observeSleep(),
            repo.observeWellbeing()
        ) { sleepList, wbList ->
            val todaySleep = sleepList.firstOrNull { it.date == TODAY }
            val last7 = sleepList.take(7)
            val avgSleep = if (last7.isEmpty()) 0f
            else last7.map { it.durationMinutes / 60f }.average().toFloat()
            val todayWb = wbList.firstOrNull { it.date == TODAY }
            _wellbeing.update {
                it.copy(
                    todaySleep = todaySleep,
                    lastSleep = sleepList.firstOrNull(),
                    recentSleep = sleepList,
                    avgSleepHours = avgSleep,
                    todayWellbeing = todayWb,
                    weekWellbeing = wbList,
                    meditationMinutes = todayWb?.meditationMinutes ?: 0,
                    moodSavedToday = todayWb?.notes?.contains(MOOD_SAVED_MARK) == true
                )
            }
        }.launchIn(viewModelScope)
    }

    fun logSleep(entry: SleepLogEntity) = viewModelScope.launch {
        repo.logSleep(entry)
        say("Sleep logged 🌙")
    }

    private suspend fun editWellbeing(date: String, block: WellbeingLogEntity.() -> WellbeingLogEntity) {
        val existing = repo.getWellbeingByDate(date) ?: WellbeingLogEntity(date = date)
        repo.saveWellbeing(existing.block())
    }

    fun logMood(date: String, mood: Int, stress: Int) = viewModelScope.launch {
        editWellbeing(date) {
            copy(moodScore = mood, stressLevel = stress,
                notes = if (notes.contains(MOOD_SAVED_MARK)) notes else notes + MOOD_SAVED_MARK)
        }
        say("Check-in saved ✓")
    }

    fun addWater(date: String) = viewModelScope.launch {
        val existing = repo.getWellbeingByDate(date) ?: WellbeingLogEntity(date = date)
        if (existing.waterGlasses < 16) {
            repo.saveWellbeing(existing.copy(waterGlasses = existing.waterGlasses + 1))
            say("Hydration updated 💧")
        }
    }

    fun removeWater(date: String) = viewModelScope.launch {
        editWellbeing(date) { copy(waterGlasses = (waterGlasses - 1).coerceAtLeast(0)) }
    }

    fun setWater(date: String, glasses: Int) = viewModelScope.launch {
        editWellbeing(date) { copy(waterGlasses = glasses.coerceIn(0, 16)) }
    }

    fun addMeditationMinutes(date: String, minutes: Int) = viewModelScope.launch {
        if (minutes <= 0) return@launch
        editWellbeing(date) { copy(meditationMinutes = meditationMinutes + minutes) }
        say("🧘 $minutes min meditation logged")
    }

    private companion object {
        /** Marker stored in WellbeingLogEntity.notes once the user saved a mood check-in today. */
        const val MOOD_SAVED_MARK = "[checkin]"
    }
}
