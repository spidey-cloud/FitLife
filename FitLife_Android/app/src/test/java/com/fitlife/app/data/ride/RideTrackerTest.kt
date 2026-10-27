package com.fitlife.app.data.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Simulated rides: one GPS fix and one tick per second, driven with explicit timestamps.
 */
class RideTrackerTest {

    private val t0 = 1_700_000_000_000L
    private val metersPerDegLat = 111_195.0

    /** Rides due north at [kmh] for [seconds]; returns the final time. */
    private fun RideTracker.rideNorth(
        startMs: Long, startLat: Double, kmh: Double, seconds: Int,
        accuracy: Float = 5f, altitude: (Int) -> Double = { 10.0 }
    ): Pair<Long, Double> {
        val mps = kmh / 3.6
        var lat = startLat
        var now = startMs
        repeat(seconds) { i ->
            now += 1000
            lat += mps / metersPerDegLat
            onLocation(lat, 88.36, altitude(i), mps.toFloat(), accuracy, now, receivedAtMs = now)
            tick(now)
        }
        return now to lat
    }

    @Test
    fun steadyRide_measuresDistanceTimeSpeedAndCalories() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 10.0, 0f, 5f, t0, receivedAtMs = t0)
        tr.rideNorth(t0, 22.5, kmh = 20.0, seconds = 600)
        val s = tr.session.value

        assertEquals(RideStatus.RECORDING, s.status)
        assertEquals(3333.0, s.distanceM, 3333.0 * 0.02)          // 20 km/h × 10 min
        assertEquals(600L, s.movingSeconds)
        assertEquals(20.0, s.avgSpeedKmh, 0.5)
        assertEquals(20.0, s.speedKmh, 0.5)
        assertEquals(93.3, s.calories, 5.0)                         // 8 MET × 70 kg × 1/6 h
        assertEquals(GpsQuality.STRONG, s.gps)
        assertTrue(s.route.size in 2..RideTracker.LIVE_ROUTE_POINTS)
    }

    @Test
    fun standingStill_withGpsJitter_addsNoDistanceAndAutoPauses() {
        val tr = RideTracker()
        tr.start(70f, t0)
        val rnd = Random(42)
        var now = t0
        repeat(60) {
            now += 1000
            val jitterLat = 22.5 + (rnd.nextDouble() - 0.5) * 4.0 / metersPerDegLat   // ±2 m
            val jitterLng = 88.36 + (rnd.nextDouble() - 0.5) * 4.0 / metersPerDegLat
            tr.onLocation(jitterLat, jitterLng, 10.0, 0.3f, 8f, now, receivedAtMs = now)
            tr.tick(now)
        }
        val s = tr.session.value
        assertTrue("distance ${s.distanceM}", s.distanceM < 10.0)
        assertEquals(RideStatus.AUTO_PAUSED, s.status)
        assertTrue("moving ${s.movingSeconds}", s.movingSeconds <= 10)   // only until auto-pause kicked in
    }

    @Test
    fun autoPause_resumesWhenRidingAgain() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 10.0, 0f, 5f, t0, receivedAtMs = t0)
        var now = t0
        repeat(15) { now += 1000; tr.onLocation(22.5, 88.36, 10.0, 0f, 5f, now, receivedAtMs = now); tr.tick(now) }
        assertEquals(RideStatus.AUTO_PAUSED, tr.session.value.status)
        tr.rideNorth(now, 22.5, kmh = 18.0, seconds = 20)
        assertEquals(RideStatus.RECORDING, tr.session.value.status)
        assertTrue(tr.session.value.distanceM > 50.0)
    }

    @Test
    fun inaccurateFixes_areIgnored() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.rideNorth(t0, 22.5, kmh = 20.0, seconds = 60, accuracy = 50f)
        val s = tr.session.value
        assertEquals(0.0, s.distanceM, 0.0)
        assertEquals(GpsQuality.WEAK, s.gps)
    }

    @Test
    fun teleportGlitch_isRejected() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 10.0, 0f, 5f, t0, receivedAtMs = t0)
        val (now, lat) = tr.rideNorth(t0, 22.5, kmh = 20.0, seconds = 30)
        val before = tr.session.value.distanceM
        // 1 km jump in one second (3,600 km/h) — must not count
        tr.onLocation(lat + 1000 / metersPerDegLat, 88.36, 10.0, null, 5f, now + 1000, receivedAtMs = now + 1000)
        assertEquals(before, tr.session.value.distanceM, 1e-9)
    }

    @Test
    fun manualPause_doesNotCountTimeOrBridgeTheGap() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 10.0, 0f, 5f, t0, receivedAtMs = t0)
        var (now, lat) = tr.rideNorth(t0, 22.5, kmh = 20.0, seconds = 60)
        val distBefore = tr.session.value.distanceM
        val timeBefore = tr.session.value.movingSeconds

        tr.pause()
        // Walk the bike 500 m over 5 minutes while paused
        repeat(300) {
            now += 1000; lat += (500.0 / 300) / metersPerDegLat
            tr.onLocation(lat, 88.36, 10.0, 1.6f, 5f, now, receivedAtMs = now); tr.tick(now)
        }
        assertEquals(RideStatus.PAUSED, tr.session.value.status)
        assertEquals(distBefore, tr.session.value.distanceM, 1e-9)
        assertEquals(timeBefore, tr.session.value.movingSeconds)

        tr.resume(now)
        tr.rideNorth(now, lat, kmh = 20.0, seconds = 60)
        // Second leg adds ~333 m; the 500 m paused gap is not bridged
        assertEquals(distBefore + 333.0, tr.session.value.distanceM, 25.0)
    }

    @Test
    fun climb_isAccumulatedAndGradeComputed() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 0.0, 0f, 5f, t0, receivedAtMs = t0)
        // 5.56 m/s for 120 s ≈ 667 m while climbing 0.3 m per second ≈ 5 % grade
        tr.rideNorth(t0, 22.5, kmh = 20.0, seconds = 120, altitude = { i -> (i + 1) * 0.3 })
        val s = tr.session.value
        assertEquals(36.0, s.elevationGainM, 6.0)
        assertEquals(5.4, s.gradePct, 1.5)
        assertNotNull(s.maxAltitudeM)
    }

    @Test
    fun finish_returnsEntityAndResets() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 10.0, 0f, 5f, t0, receivedAtMs = t0)
        val (now, _) = tr.rideNorth(t0, 22.5, kmh = 20.0, seconds = 300)
        val ride = tr.finish(now + 1000)

        assertNotNull(ride)
        ride!!
        assertEquals(1666f, ride.distanceMeters, 50f)
        assertEquals(20f, ride.avgSpeedKmh, 0.7f)
        assertTrue(ride.route.isNotBlank())
        assertTrue(RideMath.decodeRoute(ride.route).size in 2..RideTracker.MAX_SAVED_POINTS)
        assertEquals(RideStatus.IDLE, tr.session.value.status)
    }

    @Test
    fun finish_tooShortRide_isDiscarded() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.tick(t0 + 5_000)
        assertNull(tr.finish(t0 + 6_000))
        assertEquals(RideStatus.IDLE, tr.session.value.status)
    }

    @Test
    fun gps_goesStale_whenFixesStop() {
        val tr = RideTracker()
        tr.start(70f, t0)
        tr.onLocation(22.5, 88.36, 10.0, 5f, 5f, t0, receivedAtMs = t0)
        tr.tick(t0 + 12_000)
        assertEquals(GpsQuality.SEARCHING, tr.session.value.gps)
        assertEquals(0.0, tr.session.value.speedKmh, 0.0)
    }
}
