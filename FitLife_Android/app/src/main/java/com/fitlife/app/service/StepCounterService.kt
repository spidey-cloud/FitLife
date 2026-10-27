package com.fitlife.app.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fitlife.app.MainActivity
import com.fitlife.app.R
import com.fitlife.app.data.local.FitLifeDatabase
import com.fitlife.app.data.repository.FitnessRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Foreground service that registers the hardware step-counter sensor
 * and persists step counts to Room automatically all day.
 *
 * Android's TYPE_STEP_COUNTER gives the total steps since last reboot. v2 counts the
 * **difference between consecutive sensor readings** and adds it to today's row. Before v2
 * the service stored an offset; a service restart then reset today's count to 0 and every
 * sensor event overwrote steps the user had added manually.
 */
@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject lateinit var database: FitLifeDatabase

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeLock = Mutex()

    /** Last raw sensor value seen by this service instance (-1 = none yet). */
    private var lastSensorTotal: Int = -1
    private var lastNotifiedSteps: Int = -1

    companion object {
        const val CHANNEL_ID = "fitlife_steps_service"
        const val NOTIF_ID = 9001

        /** Starts the service only if step counting is permitted (required for the health FGS type). */
        fun start(context: Context) {
            if (!hasActivityPermission(context)) return
            val intent = Intent(context, StepCounterService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(intent)
                else
                    context.startService(intent)
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException when started from the background
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }

        fun hasActivityPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(0),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
            )
        } catch (e: Exception) {
            stopSelf(); return
        }
        initSensor()
    }

    private fun initSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val totalSinceReboot = event.values[0].toInt()

        scope.launch {
            writeLock.withLock {
                // First reading after (re)start: only establish the baseline.
                if (lastSensorTotal < 0) { lastSensorTotal = totalSinceReboot; return@withLock }
                // Counter goes backwards only after a reboot — then all of it is new.
                val delta = if (totalSinceReboot >= lastSensorTotal) totalSinceReboot - lastSensorTotal
                            else totalSinceReboot
                lastSensorTotal = totalSinceReboot
                if (delta <= 0) return@withLock

                val today = LocalDate.now().toString()
                val dao = database.stepsDao()
                val existing = dao.getByDate(today)
                val todaySteps = (existing?.steps ?: 0) + delta
                dao.upsert(FitnessRepository.stepsEntity(today, todaySteps, existing?.syncedWithFit ?: false))

                // Refresh the ongoing notification roughly every 100 steps
                if (lastNotifiedSteps < 0 || todaySteps - lastNotifiedSteps >= 100 || todaySteps < lastNotifiedSteps) {
                    lastNotifiedSteps = todaySteps
                    withContext(Dispatchers.Main) {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIF_ID, buildNotification(todaySteps))
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        scope.cancel()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Step Tracking",
                NotificationManager.IMPORTANCE_LOW   // silent — no sound/vibration
            ).apply {
                description = "Tracks your steps automatically in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FitLife — Step Tracker")
            .setContentText("%,d steps today".format(steps))
            .setSmallIcon(R.drawable.ic_stat_fitlife)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
