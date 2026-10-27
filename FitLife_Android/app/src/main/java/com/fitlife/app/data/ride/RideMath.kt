package com.fitlife.app.data.ride

import kotlin.math.*

/** One GPS point of a ride trace. */
data class GeoPoint(val lat: Double, val lng: Double, val alt: Double)

/**
 * Pure, Android-free maths used by the ride recorder. Kept separate so it can be unit-tested
 * on the JVM (see `RideMathTest`).
 */
object RideMath {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Cycling MET (Compendium of Physical Activities) by speed.
     * kcal = MET × body-weight(kg) × hours.
     */
    fun cyclingMet(speedKmh: Double): Double = when {
        speedKmh < 3.0 -> 0.0          // standing still / walking the bike: not counted
        speedKmh < 16.0 -> 4.0
        speedKmh < 19.0 -> 6.8
        speedKmh < 22.5 -> 8.0
        speedKmh < 25.5 -> 10.0
        speedKmh < 30.5 -> 12.0
        else -> 15.8
    }

    fun caloriesFor(speedKmh: Double, weightKg: Double, seconds: Double): Double =
        cyclingMet(speedKmh) * weightKg * (seconds / 3600.0)

    /** Exponential moving average. */
    fun ema(previous: Double?, value: Double, alpha: Double): Double =
        if (previous == null) value else previous + alpha * (value - previous)

    /**
     * Elevation gain with hysteresis: altitude must rise at least [thresholdM] above the
     * lowest point since the last counted climb before it is added. Filters GPS altitude noise.
     */
    class ElevationAccumulator(private val thresholdM: Double = 3.0) {
        var gain = 0.0
            private set
        private var reference: Double? = null

        fun add(smoothedAltitude: Double) {
            val ref = reference
            if (ref == null) { reference = smoothedAltitude; return }
            when {
                smoothedAltitude - ref >= thresholdM -> { gain += smoothedAltitude - ref; reference = smoothedAltitude }
                smoothedAltitude < ref -> reference = smoothedAltitude
            }
        }
    }

    /** Reduces a trace to at most [maxPoints] points, always keeping the first and last. */
    fun downsample(points: List<GeoPoint>, maxPoints: Int): List<GeoPoint> {
        if (points.size <= maxPoints || maxPoints < 2) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        return (0 until maxPoints).map { i -> points[(i * step).roundToInt().coerceAtMost(points.lastIndex)] }
    }

    /** "lat,lng,alt;lat,lng,alt" with 5-decimal (~1 m) precision. */
    fun encodeRoute(points: List<GeoPoint>): String =
        points.joinToString(";") { p ->
            String.format(java.util.Locale.US, "%.5f,%.5f,%.0f", p.lat, p.lng, p.alt)
        }

    fun decodeRoute(encoded: String): List<GeoPoint> =
        if (encoded.isBlank()) emptyList()
        else encoded.split(';').mapNotNull { part ->
            val v = part.split(',')
            if (v.size < 2) null
            else {
                val lat = v[0].toDoubleOrNull(); val lng = v[1].toDoubleOrNull()
                if (lat == null || lng == null) null
                else GeoPoint(lat, lng, v.getOrNull(2)?.toDoubleOrNull() ?: 0.0)
            }
        }

    /** Ride title from the local start hour, e.g. "Morning Ride". */
    fun titleForHour(hour: Int): String = when (hour) {
        in 5..10 -> "Morning Ride"
        in 11..13 -> "Lunch Ride"
        in 14..17 -> "Afternoon Ride"
        in 18..21 -> "Evening Ride"
        else -> "Night Ride"
    }

    /** 42:17 or 1:02:05 */
    fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)   // "5:03", like bike computers
    }

    /** 42m 15s or 1h 02m */
    fun formatDurationShort(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(java.util.Locale.US, "%dh %02dm", h, m)
        else String.format(java.util.Locale.US, "%dm %02ds", m, s)
    }
}
