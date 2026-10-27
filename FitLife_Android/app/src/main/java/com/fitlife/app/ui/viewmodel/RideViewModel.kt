package com.fitlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitlife.app.data.local.RideEntity
import com.fitlife.app.data.repository.FitnessRepository
import com.fitlife.app.data.ride.RideSession
import com.fitlife.app.data.ride.RideTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class RideHubState(
    val rides: List<RideEntity> = emptyList(),
    val todayKm: Double = 0.0,
    val todaySeconds: Long = 0,
    val todayCalories: Int = 0,
    val todayClimbM: Double = 0.0,
    val dailyGoalKm: Float = 20f,
    val weekKm: Double = 0.0,
    val weekRides: Int = 0,
    /** Mon..Sun km for the current week. */
    val weekByDay: List<Double> = List(7) { 0.0 },
    val totalKm: Double = 0.0,
    val lastRide: RideEntity? = null
) {
    val goalProgress: Float get() = if (dailyGoalKm > 0) (todayKm / dailyGoalKm).toFloat() else 0f
}

/** Result of saving a ride: the new id and any personal bests it set. */
data class SavedRide(val id: Long, val personalBests: List<String>)

@HiltViewModel
class RideViewModel @Inject constructor(
    private val repo: FitnessRepository,
    private val tracker: RideTracker
) : ViewModel() {

    val session: StateFlow<RideSession> = tracker.session

    val hub: StateFlow<RideHubState> = combine(repo.observeRides(), repo.observeProfile()) { rides, profile ->
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val todays = rides.filter { it.date == today.toString() }
        val week = rides.filter { it.date >= monday.toString() && it.date <= today.toString() }
        RideHubState(
            rides = rides,
            todayKm = todays.sumOf { it.distanceMeters.toDouble() } / 1000.0,
            todaySeconds = todays.sumOf { it.movingSeconds },
            todayCalories = todays.sumOf { it.calories },
            todayClimbM = todays.sumOf { it.elevationGainM.toDouble() },
            dailyGoalKm = profile?.dailyRideGoalKm ?: 20f,
            weekKm = week.sumOf { it.distanceMeters.toDouble() } / 1000.0,
            weekRides = week.size,
            weekByDay = (0..6).map { i ->
                val d = monday.plusDays(i.toLong()).toString()
                week.filter { it.date == d }.sumOf { it.distanceMeters.toDouble() } / 1000.0
            },
            totalKm = rides.sumOf { it.distanceMeters.toDouble() } / 1000.0,
            lastRide = rides.firstOrNull()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RideHubState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun ride(id: Long): Flow<RideEntity?> = repo.observeRide(id)

    fun pause() = tracker.pause()
    fun resume() = tracker.resume()
    fun discard() = tracker.discard()

    /** Finishes the ride, persists it and reports the saved id (or null if too short to keep). */
    fun finish(onSaved: (SavedRide?) -> Unit) {
        val entity = tracker.finish()
        if (entity == null) { onSaved(null); return }
        viewModelScope.launch {
            val id = repo.saveRide(entity)
            val pbs = repo.personalBests(entity.copy(id = id))
            onSaved(SavedRide(id, pbs))
        }
    }

    fun deleteRide(ride: RideEntity) = viewModelScope.launch {
        repo.deleteRide(ride.id)
        _messages.tryEmit(UiMessage("Ride deleted", "Undo") { viewModelScope.launch { repo.restoreRide(ride) } })
    }

    suspend fun personalBests(ride: RideEntity): List<String> = repo.personalBests(ride)

    val healthConnectAvailable: Boolean get() = repo.isHealthConnectAvailable
}
