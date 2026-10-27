package com.fitlife.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fitlife.app.MainActivity
import com.fitlife.app.R
import com.fitlife.app.data.repository.FitnessRepository
import com.fitlife.app.data.ride.RideMath
import com.fitlife.app.data.ride.RideStatus
import com.fitlife.app.data.ride.RideTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground service that records a bike ride with the platform GPS provider
 * (no Google Play Services dependency) and feeds fixes into [RideTracker].
 *
 * Privacy: uses **while-in-use** location only. The service is started from a visible
 * screen after the user taps "Start Ride", so ACCESS_BACKGROUND_LOCATION is not needed.
 * The service stops itself as soon as the ride is finished or discarded.
 */
@AndroidEntryPoint
class RideTrackingService : Service() {

    @Inject lateinit var tracker: RideTracker
    @Inject lateinit var repo: FitnessRepository

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var locationManager: LocationManager? = null
    private var tickerJob: Job? = null
    private var lastNotificationMs = 0L
    private var observingSession = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            tracker.onLocation(
                lat = location.latitude,
                lng = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                timeMs = location.time
            )
        }
        // Required on API < 30 where these are abstract
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> { tracker.pause(); refreshNotification(force = true) }
            ACTION_RESUME -> { tracker.resume(); refreshNotification(force = true) }
            ACTION_STOP -> { stopRecording(); return START_NOT_STICKY }
            else -> startRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (!hasLocationPermission(this)) { stopSelf(); return }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (e: Exception) {
            stopSelf(); return
        }

        if (!tracker.session.value.isActive) {
            scope.launch {
                val weight = repo.getProfile()?.weightKg ?: 70f
                tracker.start(weight)
                requestUpdates()
                startTicker()
            }
        } else {
            requestUpdates()
            startTicker()
        }

        // Stop automatically once the ride is finished/discarded from the UI.
        if (observingSession) return
        observingSession = true
        scope.launch {
            tracker.session.collectLatest { s ->
                if (!s.isActive && tickerJob != null) stopRecording()
            }
        }
    }

    @SuppressLint("MissingPermission") // checked in hasLocationPermission()
    private fun requestUpdates() {
        if (!hasLocationPermission(this)) return
        val lm = locationManager ?: (getSystemService(LOCATION_SERVICE) as LocationManager).also { locationManager = it }
        try {
            lm.removeUpdates(listener)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            // Network fixes help before the first GPS lock; poor ones are filtered by accuracy.
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5_000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopRecording()
        } catch (e: IllegalArgumentException) {
            // provider missing on this device
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                tracker.tick()
                refreshNotification()
            }
        }
    }

    private fun stopRecording() {
        tickerJob?.cancel(); tickerJob = null
        locationManager?.removeUpdates(listener)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(listener)
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun refreshNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationMs < 5_000L) return
        lastNotificationMs = now
        if (!tracker.session.value.isActive) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = tracker.session.value
        val open = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_RIDE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val paused = s.status == RideStatus.PAUSED
        val toggle = PendingIntent.getService(
            this, 11,
            Intent(this, RideTrackingService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (s.status) {
            RideStatus.PAUSED -> "Ride paused"
            RideStatus.AUTO_PAUSED -> "Ride auto-paused"
            else -> "Recording ride"
        }
        val text = String.format(
            Locale.US, "%.2f km · %s · %.1f km/h",
            s.distanceKm, RideMath.formatDuration(s.movingSeconds), s.speedKmh
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (paused) "Resume" else "Pause", toggle)
            .addAction(0, "Open", open)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Ride recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows live stats while a bike ride is being recorded"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_ID = "fitlife_ride"
        const val NOTIF_ID = 9002
        const val ACTION_START = "com.fitlife.app.ride.START"
        const val ACTION_PAUSE = "com.fitlife.app.ride.PAUSE"
        const val ACTION_RESUME = "com.fitlife.app.ride.RESUME"
        const val ACTION_STOP = "com.fitlife.app.ride.STOP"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun isGpsEnabled(context: Context): Boolean {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return try {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) { false }
        }

        fun start(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, RideTrackingService::class.java).setAction(action))
        }
    }
}
