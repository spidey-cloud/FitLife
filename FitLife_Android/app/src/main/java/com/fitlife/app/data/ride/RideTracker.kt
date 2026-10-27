package com.fitlife.app.data.ride

import com.fitlife.app.data.local.RideEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

enum class RideStatus { IDLE, RECORDING, PAUSED, AUTO_PAUSED }

enum class GpsQuality { SEARCHING, WEAK, GOOD, STRONG;
    val label: String get() = when (this) {
        SEARCHING -> "Searching GPS"
        WEAK -> "GPS Weak"
        GOOD -> "GPS Locked"
        STRONG -> "GPS Strong"
    }
}

/** Immutable snapshot of the ride in progress, observed by the UI and the notification. */
data class RideSession(
    val status: RideStatus = RideStatus.IDLE,
    val startedAtMs: Long = 0L,
    val movingMs: Long = 0L,
    val distanceM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val minMovingSpeedKmh: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val altitudeM: Double? = null,
    val minAltitudeM: Double? = null,
    val maxAltitudeM: Double? = null,
    val gradePct: Double = 0.0,
    val calories: Double = 0.0,
    val gps: GpsQuality = GpsQuality.SEARCHING,
    val accuracyM: Float? = null,
    val route: List<GeoPoint> = emptyList(),
    /** Last ~2 minutes of speed samples (1 per tick) for the live sparkline. */
    val speedHistory: List<Float> = emptyList()
) {
    val isActive: Boolean get() = status != RideStatus.IDLE
    val movingSeconds: Long get() = movingMs / 1000
    val distanceKm: Double get() = distanceM / 1000.0
    val avgSpeedKmh: Double get() = if (movingMs > 0) distanceKm / (movingMs / 3_600_000.0) else 0.0
    val kcalPerHour: Double get() = if (movingMs > 60_000) calories / (movingMs / 3_600_000.0) else 0.0
}

/**
 * Turns raw GPS fixes into ride statistics. Lives as a process-wide singleton so the
 * foreground [com.fitlife.app.service.RideTrackingService] (which feeds it) and the UI
 * (which observes it) share one source of truth.
 *
 * Filtering rules:
 *  • fixes worse than [MAX_ACCURACY_M] update GPS quality but never move the trace
 *  • segments shorter than [MIN_SEGMENT_M] are treated as jitter
 *  • implied speeds over [MAX_SPEED_MPS] (~108 km/h) are rejected as glitches
 *  • auto-pause below [AUTO_PAUSE_KMH] for [AUTO_PAUSE_AFTER_MS]; resume above [AUTO_RESUME_KMH]
 */
@Singleton
class RideTracker @Inject constructor() {

    private val _session = MutableStateFlow(RideSession())
    val session: StateFlow<RideSession> = _session.asStateFlow()

    private var weightKg = 70.0
    private var lastTickMs = 0L
    private var lastAccepted: Fix? = null
    private var smoothedSpeed: Double? = null
    private var smoothedAlt: Double? = null
    private var elevation = RideMath.ElevationAccumulator()
    private var slowSinceMs: Long? = null
    private var lastFixMs = 0L
    /** (cumulative distance, smoothed altitude) window for the grade calculation. */
    private val gradeWindow = ArrayDeque<Pair<Double, Double>>()
    /** Full-resolution trace; the session only exposes a bounded copy for drawing. */
    private val fullRoute = ArrayList<GeoPoint>()

    private data class Fix(val lat: Double, val lng: Double, val timeMs: Long)

    fun start(bodyWeightKg: Float, nowMs: Long = System.currentTimeMillis()) {
        if (_session.value.isActive) return
        reset()
        weightKg = bodyWeightKg.toDouble().coerceIn(30.0, 250.0)
        lastTickMs = nowMs
        _session.value = RideSession(status = RideStatus.RECORDING, startedAtMs = nowMs)
    }

    fun pause() = _session.update {
        if (it.status == RideStatus.RECORDING || it.status == RideStatus.AUTO_PAUSED)
            it.copy(status = RideStatus.PAUSED, speedKmh = 0.0) else it
    }

    fun resume(nowMs: Long = System.currentTimeMillis()) {
        lastTickMs = nowMs
        slowSinceMs = null
        _session.update { if (it.status == RideStatus.PAUSED) it.copy(status = RideStatus.RECORDING) else it }
    }

    /** Called once per second by the service: advances moving time, calories and GPS staleness. */
    fun tick(nowMs: Long = System.currentTimeMillis()) {
        val s = _session.value
        if (!s.isActive) return
        val dt = (nowMs - lastTickMs).coerceIn(0L, 5_000L)
        lastTickMs = nowMs
        val stale = lastFixMs == 0L || nowMs - lastFixMs > 10_000L
        _session.update { cur ->
            val moving = cur.status == RideStatus.RECORDING
            val speed = if (stale || !moving) 0.0 else cur.speedKmh
            cur.copy(
                movingMs = if (moving) cur.movingMs + dt else cur.movingMs,
                calories = if (moving) cur.calories + RideMath.caloriesFor(speed, weightKg, dt / 1000.0) else cur.calories,
                speedKmh = speed,
                gps = if (stale) GpsQuality.SEARCHING else cur.gps,
                speedHistory = (cur.speedHistory + speed.toFloat()).takeLast(120)
            )
        }
    }

    /** Feed a location fix. Primitive parameters keep this class free of Android types. */
    fun onLocation(
        lat: Double, lng: Double,
        altitude: Double?, speedMps: Float?, accuracyM: Float?, timeMs: Long,
        receivedAtMs: Long = System.currentTimeMillis()
    ) {
        val s = _session.value
        if (!s.isActive) return
        lastFixMs = receivedAtMs   // same clock as tick(), used for GPS staleness
        val quality = when {
            accuracyM == null -> GpsQuality.GOOD
            accuracyM <= 6f -> GpsQuality.STRONG
            accuracyM <= 15f -> GpsQuality.GOOD
            else -> GpsQuality.WEAK
        }
        if (accuracyM != null && accuracyM > MAX_ACCURACY_M) {
            _session.update { it.copy(gps = quality, accuracyM = accuracyM) }
            return
        }
        if (s.status == RideStatus.PAUSED) {
            _session.update { it.copy(gps = quality, accuracyM = accuracyM) }
            lastAccepted = Fix(lat, lng, timeMs)   // don't bridge the pause gap into distance
            return
        }

        val prev = lastAccepted
        var segment = 0.0
        var derivedSpeed: Double? = null
        if (prev != null) {
            segment = RideMath.distanceMeters(prev.lat, prev.lng, lat, lng)
            val dtSec = (timeMs - prev.timeMs) / 1000.0
            if (dtSec > 0) derivedSpeed = segment / dtSec
            if (derivedSpeed != null && derivedSpeed > MAX_SPEED_MPS) return   // glitch
        }
        val rawSpeedMps = speedMps?.toDouble() ?: derivedSpeed ?: 0.0
        val speedKmh = RideMath.ema(smoothedSpeed, rawSpeedMps * 3.6, 0.35).coerceAtLeast(0.0)
        smoothedSpeed = speedKmh

        // Auto-pause / auto-resume
        var status = s.status
        if (status == RideStatus.RECORDING && speedKmh < AUTO_PAUSE_KMH) {
            val since = slowSinceMs ?: timeMs.also { slowSinceMs = it }
            if (timeMs - since >= AUTO_PAUSE_AFTER_MS) status = RideStatus.AUTO_PAUSED
        } else if (speedKmh >= AUTO_PAUSE_KMH) slowSinceMs = null
        if (status == RideStatus.AUTO_PAUSED && speedKmh >= AUTO_RESUME_KMH) {
            status = RideStatus.RECORDING; slowSinceMs = null
        }

        val countSegment = prev == null || segment >= MIN_SEGMENT_M
        val addDistance = if (prev != null && countSegment && status == RideStatus.RECORDING) segment else 0.0
        if (countSegment) lastAccepted = Fix(lat, lng, timeMs)

        var alt = smoothedAlt
        if (altitude != null) {
            alt = RideMath.ema(smoothedAlt, altitude, 0.2)
            smoothedAlt = alt
            if (status == RideStatus.RECORDING) elevation.add(alt)
        }
        if (countSegment) fullRoute.add(GeoPoint(lat, lng, alt ?: 0.0))

        _session.update { cur ->
            val distance = cur.distanceM + addDistance
            val grade = alt?.let { updateGrade(distance, it) } ?: cur.gradePct
            val moving = status == RideStatus.RECORDING && speedKmh >= AUTO_RESUME_KMH
            cur.copy(
                status = status,
                distanceM = distance,
                speedKmh = if (status == RideStatus.RECORDING) speedKmh else 0.0,
                maxSpeedKmh = if (moving) max(cur.maxSpeedKmh, speedKmh) else cur.maxSpeedKmh,
                minMovingSpeedKmh = when {
                    !moving -> cur.minMovingSpeedKmh
                    cur.minMovingSpeedKmh == 0.0 -> speedKmh
                    else -> min(cur.minMovingSpeedKmh, speedKmh)
                },
                elevationGainM = elevation.gain,
                altitudeM = alt ?: cur.altitudeM,
                minAltitudeM = alt?.let { a -> cur.minAltitudeM?.let { min(it, a) } ?: a } ?: cur.minAltitudeM,
                maxAltitudeM = alt?.let { a -> cur.maxAltitudeM?.let { max(it, a) } ?: a } ?: cur.maxAltitudeM,
                gradePct = grade,
                gps = quality,
                accuracyM = accuracyM,
                route = if (countSegment) liveRoute() else cur.route
            )
        }
    }

    private fun liveRoute(): List<GeoPoint> =
        if (fullRoute.size <= LIVE_ROUTE_POINTS) fullRoute.toList()
        else RideMath.downsample(fullRoute, LIVE_ROUTE_POINTS)

    private fun updateGrade(distance: Double, alt: Double): Double {
        gradeWindow.addLast(distance to alt)
        while (gradeWindow.size > 2 && distance - gradeWindow.first().first > 120.0) gradeWindow.removeFirst()
        val first = gradeWindow.first()
        val run = distance - first.first
        return if (run >= 30.0) ((alt - first.second) / run * 100.0).coerceIn(-30.0, 30.0) else 0.0
    }

    /** Ends the ride and returns the entity to persist, or null if nothing was recorded. */
    fun finish(nowMs: Long = System.currentTimeMillis()): RideEntity? {
        tick(nowMs)
        val s = _session.value
        val trace = RideMath.downsample(fullRoute.toList(), MAX_SAVED_POINTS)
        reset()
        if (!s.isActive || (s.distanceM < 10.0 && s.movingMs < 30_000L)) return null
        val start = Instant.ofEpochMilli(s.startedAtMs).atZone(ZoneId.systemDefault())
        return RideEntity(
            date = start.toLocalDate().toString(),
            title = RideMath.titleForHour(start.hour),
            startEpochMs = s.startedAtMs,
            endEpochMs = nowMs,
            movingSeconds = s.movingSeconds,
            distanceMeters = s.distanceM.toFloat(),
            avgSpeedKmh = s.avgSpeedKmh.toFloat(),
            maxSpeedKmh = s.maxSpeedKmh.toFloat(),
            minMovingSpeedKmh = s.minMovingSpeedKmh.toFloat(),
            elevationGainM = s.elevationGainM.toFloat(),
            minAltitudeM = (s.minAltitudeM ?: 0.0).toFloat(),
            maxAltitudeM = (s.maxAltitudeM ?: 0.0).toFloat(),
            calories = s.calories.toInt(),
            route = RideMath.encodeRoute(trace)
        )
    }

    /** Drops the ride without saving. */
    fun discard() = reset()

    private fun reset() {
        _session.value = RideSession()
        lastAccepted = null
        smoothedSpeed = null
        smoothedAlt = null
        elevation = RideMath.ElevationAccumulator()
        slowSinceMs = null
        lastFixMs = 0L
        gradeWindow.clear()
        fullRoute.clear()
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val MIN_SEGMENT_M = 3.0
        const val MAX_SPEED_MPS = 30.0
        const val AUTO_PAUSE_KMH = 2.0
        const val AUTO_RESUME_KMH = 4.0
        const val AUTO_PAUSE_AFTER_MS = 8_000L
        const val MAX_SAVED_POINTS = 600
        const val LIVE_ROUTE_POINTS = 400
    }
}
