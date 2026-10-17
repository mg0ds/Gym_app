package com.gymtracker.timer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gymtracker.MainActivity
import com.gymtracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jednorazowy alarm „przerwa się skończyła”.
 *
 * To **inna** notyfikacja niż ta od usługi treningu: tamta wisi cicho cały
 * trening, ta ma zwrócić uwagę. Dlatego ma własny kanał o wysokiej ważności
 * (dźwięk + wibracja + heads-up) i znika po dotknięciu.
 *
 * Kanał powstaje przed pierwszą notyfikacją, a nie przy starcie aplikacji -
 * kanał bez wpisu w ustawieniach systemu tylko by zaśmiecał ekran użytkownika,
 * który nigdy nie korzysta z przerw.
 */
@Singleton
class RestNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Koniec przerwy: dźwięk, wibracja i heads-up z nazwą ćwiczenia. */
    fun showRestFinished(exerciseName: String) {
        // Bez zgody na powiadomienia (Android 13+) `notify` jest zabronione.
        // Sprawdzenie jest tutaj, a nie w pomocniku, bo lint pilnuje właśnie
        // tej metody - i słusznie: to jedyne miejsce, które pokazuje notyfikację.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(
                context.getString(R.string.rest_notification_finished_title),
            )
            .setContentText(exerciseName)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Chowamy alarm - np. gdy użytkownik zalogował serię albo pominął przerwę. */
    fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.rest_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.rest_channel_description)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build(),
                )
                setShowBadge(false)
            },
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 1_002
        const val REQUEST_OPEN = 2

        /** Krótko, ale wyraźnie: dwa razy „seria” po 300 ms. */
        val VIBRATION_PATTERN = longArrayOf(0L, 300L, 150L, 300L)
    }
}
