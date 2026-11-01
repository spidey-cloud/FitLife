package com.fitlife.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fitlife.app.MainActivity
import com.fitlife.app.data.local.DailyStepsEntity
import com.fitlife.app.data.local.FitLifeDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDate
import javax.inject.Inject

/**
 * Foreground service that registers the hardware step-counter sensor
 * and persists step counts to Room automatically all day.
 *
 * Android's TYPE_STEP_COUNTER gives the total steps since last reboot.
 * We store the "offset" (value at midnight) and subtract it to get today's steps.
 */
@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject lateinit var database: FitLifeDatabase

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // The raw sensor value recorded at the start of today
    private var todayOffset: Int = -1
    private var lastSavedDate: String = ""

    companion object {
        const val CHANNEL_ID = "fitlife_steps_service"
        const val NOTIF_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(0))
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
        if (CyclingService.liveStats.value.isRecording) return // Ignore steps while cycling is active to prevent road bump false counts

        val totalSinceReboot = event.values[0].toInt()
        val today = LocalDate.now().toString()

        scope.launch {
            val dao = database.stepsDao()

            // Reset offset at midnight (new day)
            if (today != lastSavedDate) {
                todayOffset = totalSinceReboot
                lastSavedDate = today
            }

            // First reading of the day — set offset from DB or sensor
            if (todayOffset == -1) {
                val existing = dao.getByDate(today)
                todayOffset = if (existing != null && existing.steps > 0) {
                    totalSinceReboot - existing.steps
                } else {
                    totalSinceReboot
                }
                lastSavedDate = today
            }

            val todaySteps = (totalSinceReboot - todayOffset).coerceAtLeast(0)

            val entity = DailyStepsEntity(
                date = today,
                steps = todaySteps,
                caloriesBurned = (todaySteps * 0.04).toInt(),
                distanceMeters = todaySteps * 0.762f,
                activeMinutes = todaySteps / 100,
                syncedWithFit = false
            )
            dao.upsert(entity)

            // Update notification every 500 steps
            if (todaySteps % 500 == 0 || todaySteps < 10) {
                withContext(Dispatchers.Main) {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIF_ID, buildNotification(todaySteps))
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
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
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
