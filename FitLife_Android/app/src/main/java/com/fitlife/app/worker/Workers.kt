package com.fitlife.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fitlife.app.MainActivity
import com.fitlife.app.R
import com.fitlife.app.data.repository.FitnessRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

// ── Daily step sync ───────────────────────────────────────────────────────────
@HiltWorker
class DailySyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: FitnessRepository
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        repo.syncStepsFromHealthConnect()
        Result.success()
    } catch (e: Exception) { Result.retry() }
}

// ── Step reminder at 7 PM ─────────────────────────────────────────────────────
@HiltWorker
class StepReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: FitnessRepository
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (isQuietHours()) return Result.success()
        val profile = repo.getProfile() ?: return Result.success()
        val today = repo.getTodaySteps()
        val steps = today?.steps ?: 0
        if (steps < profile.dailyStepGoal * 0.8f) {
            sendNotification(
                title = "Keep moving! 🚶",
                body = "%,d steps done — %,d to go!".format(steps, profile.dailyStepGoal - steps)
            )
        }
        return Result.success()
    }

    private fun sendNotification(title: String, body: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("fitlife_steps", "Step Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (!canNotify(applicationContext)) return
        nm.notify(1001, NotificationCompat.Builder(applicationContext, "fitlife_steps")
            .setSmallIcon(R.drawable.ic_stat_fitlife)
            .setContentTitle(title).setContentText(body)
            .setContentIntent(openAppIntent(applicationContext))
            .setAutoCancel(true).build())
    }
}

// ── Water reminder every 2 hours ──────────────────────────────────────────────
@HiltWorker
class WaterReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: FitnessRepository
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (isQuietHours() || !canNotify(applicationContext)) return Result.success()
        val goal = repo.getProfile()?.dailyWaterGoal?.coerceAtLeast(1) ?: 8
        val today = repo.getWellbeingByDate(LocalDate.now().toString())
        // Expected glasses so far, spread evenly over 08:00–22:00; remind when 2+ behind.
        val hour = LocalTime.now().hour
        val expected = goal * ((hour - 8).coerceIn(0, 14) / 14f)
        if ((today?.waterGlasses ?: 0) < expected - 1.5f) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                nm.createNotificationChannel(
                    NotificationChannel("fitlife_reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                )
            nm.notify(1002, NotificationCompat.Builder(applicationContext, "fitlife_reminders")
                .setSmallIcon(R.drawable.ic_stat_fitlife)
                .setContentTitle("Stay hydrated 💧")
                .setContentText("${today?.waterGlasses ?: 0} of $goal glasses so far — time for a glass of water.")
                .setContentIntent(openAppIntent(applicationContext))
                .setAutoCancel(true).build())
        }
        return Result.success()
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun isQuietHours(): Boolean = LocalTime.now().hour !in 8..21

private fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context, 0,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

// ── Scheduler — called from FitLifeApp.onCreate ───────────────────────────────
object WorkerScheduler {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            "daily_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
        )

        wm.enqueueUniquePeriodicWork(
            "step_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilHour(19), TimeUnit.MILLISECONDS)
                .build()
        )

        wm.enqueueUniquePeriodicWork(
            "water_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WaterReminderWorker>(2, TimeUnit.HOURS).build()
        )
    }

    private fun delayUntilHour(hour: Int): Long {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            if (before(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
