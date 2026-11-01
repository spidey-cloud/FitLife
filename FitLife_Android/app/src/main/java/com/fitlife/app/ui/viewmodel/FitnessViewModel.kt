package com.fitlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlife.app.data.local.*
import com.fitlife.app.data.repository.FitnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

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

data class WorkoutUiState(
    val todayWorkouts: List<WorkoutEntity> = emptyList(),
    val allWorkouts: List<WorkoutEntity> = emptyList(),
    val weeklyCount: Int = 0,
    val totalMinutes: Int = 0
)

data class CalorieUiState(
    val todayFoods: List<FoodLogEntity> = emptyList(),
    val totalCalories: Int = 0,
    val calorieGoal: Int = 2000,
    val burnedCalories: Int = 0,
    val netCalories: Int = 0,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val byMeal: Map<String, List<FoodLogEntity>> = emptyMap()
)

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
    val recentSleep: List<SleepLogEntity> = emptyList(),
    val avgSleepHours: Float = 0f,
    val todayWellbeing: WellbeingLogEntity? = null,
    val weekWellbeing: List<WellbeingLogEntity> = emptyList(),
    val meditationMinutes: Int = 0
)

data class ProfileUiState(
    val profile: UserProfileEntity = UserProfileEntity(),
    val bmi: Float = 0f,
    val tdee: Int = 0,
    val onboardingComplete: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════════════════

@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val repo: FitnessRepository
) : ViewModel() {

    private val TODAY = LocalDate.now().toString()

    // State flows
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

    // One-shot messages
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun postMessage(msg: String) = viewModelScope.launch {
        _message.emit(msg)
    }

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
            .filterNotNull()
            .onEach { p ->
                val bmi = if (p.heightCm > 0) {
                    val h = p.heightCm / 100f
                    p.weightKg / (h * h)
                } else 0f
                val tdee = computeTdee(p)
                _profile.update { it.copy(profile = p, bmi = bmi, tdee = tdee, onboardingComplete = p.onboardingComplete) }
                _steps.update { it.copy(stepGoal = p.dailyStepGoal) }
                _calories.update { it.copy(calorieGoal = p.dailyCalorieGoal) }
            }
            .launchIn(viewModelScope)
    }

    fun saveProfile(profile: UserProfileEntity) = viewModelScope.launch {
        val tdee = computeTdee(profile)
        repo.saveProfile(profile.copy(dailyCalorieGoal = tdee))
        _message.emit("Profile saved")
    }

    fun completeOnboarding(profile: UserProfileEntity) = viewModelScope.launch {
        val tdee = computeTdee(profile)
        repo.saveProfile(profile.copy(onboardingComplete = true, dailyCalorieGoal = tdee))
    }

    fun connectGoogleFitAndSkip() = viewModelScope.launch {
        val profile = UserProfileEntity(
            name = "Google Fit User",
            weightKg = 70f,
            heightCm = 170f,
            dailyStepGoal = 10000,
            onboardingComplete = true
        )
        repo.saveProfile(profile)
        repo.syncStepsFromHealthConnect()
        val bmi = 70f / ((170f/100f)*(170f/100f))
        _profile.update { it.copy(profile = profile, bmi = bmi, tdee = 2200, onboardingComplete = true) }
        _message.emit("Connected Google Fit & synchronized! 🎉")
    }

    private fun computeTdee(p: UserProfileEntity): Int {
        val bmr = if (p.sex == "Male")
            88.36 + (13.4 * p.weightKg.toDouble()) + (4.8 * p.heightCm.toDouble()) - (5.7 * (p.dateOfBirth.take(4).toIntOrNull()?.let { LocalDate.now().year - it } ?: 25))
        else
            447.6 + (9.2 * p.weightKg.toDouble()) + (3.1 * p.heightCm.toDouble()) - (4.3 * (p.dateOfBirth.take(4).toIntOrNull()?.let { LocalDate.now().year - it } ?: 25))
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
                val week = list.take(7)
                val streak = repo.computeStreak()
                _steps.update {
                    it.copy(
                        todaySteps = today?.steps ?: 0,
                        todayCaloriesBurned = today?.caloriesBurned ?: 0,
                        todayDistanceKm = (today?.distanceMeters ?: 0f) / 1000f,
                        todayActiveMinutes = today?.activeMinutes ?: 0,
                        streakDays = streak,
                        weekHistory = week,
                        healthConnectAvailable = repo.isHealthConnectAvailable
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun addStepsManually(date: String, count: Int) = viewModelScope.launch {
        repo.addStepsManually(date, count)
        _message.emit("Added ${count.formatSteps()} steps")
    }

    fun syncSteps() = viewModelScope.launch {
        _steps.update { it.copy(isSyncing = true) }
        val synced = repo.syncStepsFromHealthConnect()
        if (synced > 0) _message.emit("Synced $synced steps from Google Fit ✓")
        else _message.emit("Nothing new to sync")
        _steps.update { it.copy(isSyncing = false) }
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    private fun observeWorkouts() {
        repo.observeAllWorkouts()
            .onEach { workouts ->
                val weekAgo = LocalDate.now().minusDays(7).toString()
                val weekWorkouts = workouts.filter { it.date >= weekAgo }
                _workout.update {
                    it.copy(
                        todayWorkouts = workouts.filter { w -> w.date == TODAY },
                        allWorkouts = workouts,
                        weeklyCount = weekWorkouts.size,
                        totalMinutes = workouts.sumOf { w -> w.durationMinutes }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun saveWorkout(workout: WorkoutEntity, exercises: List<ExerciseEntity>) = viewModelScope.launch {
        repo.saveWorkout(workout, exercises)
        _message.emit("${workout.name} saved! 💪")
    }

    fun completeWorkout(workout: WorkoutEntity) = viewModelScope.launch {
        repo.completeWorkout(workout)
        _message.emit("Workout synced to Health Connect ✓")
    }

    fun deleteWorkout(id: Long) = viewModelScope.launch {
        repo.deleteWorkout(id)
        _message.emit("Workout deleted")
    }

    // ── Calories ──────────────────────────────────────────────────────────────

    private fun observeCalories() {
        combine(
            repo.observeFoodByDate(TODAY),
            repo.observeTotalCalories(TODAY),
            repo.observeAllWorkouts()
        ) { foods, totalCal, workouts ->
            val (p, c, f) = repo.getMacroTotals(TODAY)
            val burned = workouts.filter { it.date == TODAY }.sumOf { it.caloriesBurned }
            val goal = _profile.value.profile.dailyCalorieGoal.takeIf { it > 0 } ?: 2000
            _calories.update {
                it.copy(
                    todayFoods = foods,
                    totalCalories = totalCal ?: 0,
                    burnedCalories = burned,
                    netCalories = (totalCal ?: 0) - burned,
                    proteinG = p, carbsG = c, fatG = f,
                    byMeal = foods.groupBy { food -> food.mealType },
                    calorieGoal = goal
                )
            }
        }.launchIn(viewModelScope)
    }

    fun logFood(food: FoodLogEntity) = viewModelScope.launch {
        repo.logFood(food)
        _message.emit("${food.foodName} logged ✓")
    }

    fun deleteFood(id: Long) = viewModelScope.launch {
        repo.deleteFood(id)
        _message.emit("Food removed")
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
        repo.savePlan(plan)
        _message.emit("${plan.name} activated ✓")
    }

    fun activatePlan(id: Long) = viewModelScope.launch {
        repo.activatePlan(id)
        _message.emit("Plan activated")
    }

    fun deletePlan(id: Long) = viewModelScope.launch {
        repo.deletePlan(id)
        _message.emit("Plan deleted")
    }

    // ── Wellbeing ─────────────────────────────────────────────────────────────

    private fun observeWellbeing() {
        combine(
            repo.observeSleep(),
            repo.observeWellbeing()
        ) { sleepList, wbList ->
            val todaySleep = sleepList.firstOrNull { it.date == TODAY }
            val avgSleep = if (sleepList.isEmpty()) 0f
            else sleepList.map { it.durationMinutes / 60f }.average().toFloat()
            val todayWb = wbList.firstOrNull { it.date == TODAY }
            _wellbeing.update {
                it.copy(
                    todaySleep = todaySleep,
                    recentSleep = sleepList,
                    avgSleepHours = avgSleep,
                    todayWellbeing = todayWb,
                    weekWellbeing = wbList,
                    meditationMinutes = todayWb?.meditationMinutes ?: 0
                )
            }
        }.launchIn(viewModelScope)
    }

    fun logSleep(entry: SleepLogEntity) = viewModelScope.launch {
        repo.logSleep(entry)
        _message.emit("Sleep logged 🌙")
    }

    fun updateWellbeing(date: String, block: WellbeingLogEntity.() -> WellbeingLogEntity) = viewModelScope.launch {
        val existing = repo.getWellbeingByDate(date) ?: WellbeingLogEntity(date = date)
        repo.saveWellbeing(existing.block())
    }

    fun logMood(date: String, mood: Int, stress: Int) = updateWellbeing(date) {
        copy(moodScore = mood, stressLevel = stress)
    }

    fun addWater(date: String) = viewModelScope.launch {
        val existing = repo.getWellbeingByDate(date) ?: WellbeingLogEntity(date = date)
        if (existing.waterGlasses < 12) {
            repo.saveWellbeing(existing.copy(waterGlasses = existing.waterGlasses + 1))
            _message.emit("💧 Water logged!")
        }
    }

    fun addMeditationMinutes(date: String, minutes: Int) = viewModelScope.launch {
        val existing = repo.getWellbeingByDate(date) ?: WellbeingLogEntity(date = date)
        repo.saveWellbeing(existing.copy(meditationMinutes = existing.meditationMinutes + minutes))
        _message.emit("🧘 $minutes min meditation logged!")
    }
}

private fun Int.formatSteps() = if (this >= 1000) "${this / 1000},${"%03d".format(this % 1000)}" else toString()
