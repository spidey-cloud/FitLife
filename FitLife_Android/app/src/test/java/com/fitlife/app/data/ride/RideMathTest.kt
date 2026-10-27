package com.fitlife.app.data.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMathTest {

    @Test
    fun distance_oneDegreeOfLatitude_isAbout111km() {
        val d = RideMath.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 500.0)
    }

    @Test
    fun distance_samePoint_isZero() {
        assertEquals(0.0, RideMath.distanceMeters(22.57, 88.36, 22.57, 88.36), 1e-9)
    }

    @Test
    fun distance_isSymmetric() {
        val a = RideMath.distanceMeters(22.57, 88.36, 22.65, 88.45)
        val b = RideMath.distanceMeters(22.65, 88.45, 22.57, 88.36)
        assertEquals(a, b, 1e-6)
    }

    @Test
    fun met_bands() {
        assertEquals(0.0, RideMath.cyclingMet(2.0), 0.0)     // standing still
        assertEquals(4.0, RideMath.cyclingMet(12.0), 0.0)    // leisurely
        assertEquals(8.0, RideMath.cyclingMet(20.0), 0.0)    // moderate
        assertEquals(15.8, RideMath.cyclingMet(35.0), 0.0)   // racing
    }

    @Test
    fun calories_oneHourAt20kmh_70kg() {
        // MET 8 × 70 kg × 1 h = 560 kcal
        assertEquals(560.0, RideMath.caloriesFor(20.0, 70.0, 3600.0), 0.01)
    }

    @Test
    fun elevation_ignoresNoiseBelowThreshold() {
        val acc = RideMath.ElevationAccumulator(3.0)
        // every trough-to-peak swing is < 3 m
        listOf(100.0, 101.4, 99.2, 101.0, 100.5, 102.0).forEach(acc::add)
        assertEquals(0.0, acc.gain, 1e-9)
    }

    @Test
    fun elevation_countsRealClimbsOnly() {
        val acc = RideMath.ElevationAccumulator(3.0)
        listOf(0.0, 10.0, 5.0, 15.0).forEach(acc::add)
        // +10 to the first peak, descent resets the reference to 5, then +10 again
        assertEquals(20.0, acc.gain, 1e-9)
    }

    @Test
    fun downsample_keepsEndpointsAndLimit() {
        val pts = (0 until 1000).map { GeoPoint(it.toDouble(), 0.0, 0.0) }
        val out = RideMath.downsample(pts, 100)
        assertEquals(100, out.size)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
    }

    @Test
    fun downsample_shortListUnchanged() {
        val pts = listOf(GeoPoint(1.0, 2.0, 3.0), GeoPoint(4.0, 5.0, 6.0))
        assertEquals(pts, RideMath.downsample(pts, 600))
    }

    @Test
    fun route_roundTrip() {
        val pts = listOf(GeoPoint(22.572645, 88.363892, 9.0), GeoPoint(22.580001, 88.370002, 12.0))
        val back = RideMath.decodeRoute(RideMath.encodeRoute(pts))
        assertEquals(2, back.size)
        assertEquals(pts[0].lat, back[0].lat, 1e-5)
        assertEquals(pts[1].lng, back[1].lng, 1e-5)
        assertEquals(pts[1].alt, back[1].alt, 0.5)
    }

    @Test
    fun route_decodeIgnoresGarbage() {
        assertTrue(RideMath.decodeRoute("").isEmpty())
        assertEquals(1, RideMath.decodeRoute("1.0,2.0,3.0;not,a,point;;").size)
    }

    @Test
    fun durationFormatting() {
        assertEquals("42:17", RideMath.formatDuration(2537))
        assertEquals("1:02:05", RideMath.formatDuration(3725))
        assertEquals("0:00", RideMath.formatDuration(0))
        assertEquals("5:03", RideMath.formatDuration(303))
        assertEquals("42m 15s", RideMath.formatDurationShort(2535))
    }

    @Test
    fun rideTitles() {
        assertEquals("Morning Ride", RideMath.titleForHour(8))
        assertEquals("Night Ride", RideMath.titleForHour(23))
    }
}
