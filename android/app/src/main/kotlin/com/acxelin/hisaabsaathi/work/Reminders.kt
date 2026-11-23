package com.acxelin.hisaabsaathi.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.data.SettingsStore
import com.acxelin.hisaabsaathi.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Two local reminders a day, at 3 PM and 9 PM phone time, scheduled on the phone itself.
 * No push server is involved. Android may deliver them a few minutes late to save battery.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private val times = listOf("reminder_15" to LocalTime.of(15, 0), "reminder_21" to LocalTime.of(21, 0))

    /** Never throws: a reminder problem must not interrupt the worker. */
    fun schedule(context: Context) = runCatching { scheduleOrThrow(context) }.let { }

    private fun scheduleOrThrow(context: Context) {
        val wm = WorkManager.getInstance(context)
        val now = LocalDateTime.now()
        for ((name, time) in times) {
            var next = now.toLocalDate().atTime(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            times.forEach { wm.cancelUniqueWork(it.first) }
        }
    }

    fun notify(context: Context, strings: Strings) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, strings.reminderTitle, NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            // Opens the app already listening: from the lock screen to a saved entry without a search for the mic.
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_LISTEN, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            1,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(strings.reminderTitle)
                .setContentText(strings.reminderBody)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).flow.first()
        if (settings.reminders && settings.onboarded) Reminders.notify(applicationContext, Strings(settings.lang))
        return Result.success()
    }
}
