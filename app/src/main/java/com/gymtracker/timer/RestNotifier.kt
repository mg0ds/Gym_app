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
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gymtracker.MainActivity
import com.gymtracker.R
import com.gymtracker.data.prefs.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Kanał alarmu przerwy odpowiadający parze przełączników (dźwięk, wibracja).
 *
 * Nazwy kanałów są widoczne w systemowych ustawieniach powiadomień, więc mówią,
 * co dany kanał robi - użytkownik, który szuka tam dźwięku, ma zobaczyć jedno
 * „Przerwa: dźwięk i wibracja”, a nie cztery zagadkowe wpisy.
 */
internal data class RestAlertChannel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val sound: Boolean,
    val vibration: Boolean,
)

/**
 * Kanały powiadomień są **niezmienne** po utworzeniu: dźwięk i wibrację ustawia
 * się raz, a potem zmienia je tylko użytkownik w ustawieniach systemu (kolejne
 * `createNotificationChannel` z tym samym identyfikatorem nie nadpisze tych
 * cech). Dlatego para przełączników nie przestawia jednego kanału, tylko
 * wybiera jeden z czterech.
 *
 * Kanał tworzymy **leniwie**, przy pierwszym alarmie: w systemowych ustawieniach
 * pojawia się wyłącznie ten, którego użytkownik naprawdę użył. Identyfikator
 * pary „dźwięk + wibracja” zostaje ten sam co w M5, żeby aktualizacja nie
 * zgubiła tego, co użytkownik już sobie w tym kanale ustawił.
 */
internal fun restAlertChannel(sound: Boolean, vibration: Boolean): RestAlertChannel = when {
    sound && vibration -> RestAlertChannel(
        id = LEGACY_CHANNEL_ID,
        nameRes = R.string.rest_channel_name,
        descriptionRes = R.string.rest_channel_description,
        sound = true,
        vibration = true,
    )

    sound -> RestAlertChannel(
        id = "rest_timer_sound",
        nameRes = R.string.rest_channel_sound_name,
        descriptionRes = R.string.rest_channel_sound_description,
        sound = true,
        vibration = false,
    )

    vibration -> RestAlertChannel(
        id = "rest_timer_vibration",
        nameRes = R.string.rest_channel_vibration_name,
        descriptionRes = R.string.rest_channel_vibration_description,
        sound = false,
        vibration = true,
    )

    else -> RestAlertChannel(
        id = "rest_timer_silent",
        nameRes = R.string.rest_channel_silent_name,
        descriptionRes = R.string.rest_channel_silent_description,
        sound = false,
        vibration = false,
    )
}

/** Identyfikator kanału z M5 - domyślne ustawienie musi trafiać w ten sam kanał. */
internal const val LEGACY_CHANNEL_ID = "rest_timer"

/**
 * Jednorazowy alarm „przerwa się skończyła”.
 *
 * To **inna** notyfikacja niż ta od usługi treningu: tamta wisi cicho cały
 * trening, ta ma zwrócić uwagę. Kanał powstaje przy pierwszym alarmie, a nie
 * przy starcie aplikacji - kanał bez wpisu w ustawieniach systemu tylko by
 * zaśmiecał ekran użytkownika, który nigdy nie korzysta z przerw.
 */
@Singleton
class RestNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
) {

    /** Koniec przerwy: dźwięk, wibracja i heads-up z nazwą ćwiczenia. */
    suspend fun showRestFinished(exerciseName: String) {
        // Bez zgody na powiadomienia (Android 13+) `notify` jest zabronione.
        // Sprawdzenie jest tutaj, a nie w pomocniku, bo lint pilnuje właśnie
        // tej metody - i słusznie: to jedyne miejsce, które pokazuje notyfikację.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        val channel = restAlertChannel(
            sound = settings.restSoundEnabled.first(),
            vibration = settings.restVibrationEnabled.first(),
        )
        ensureChannel(channel)

        val notification = NotificationCompat.Builder(context, channel.id)
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

    private fun ensureChannel(channel: RestAlertChannel) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channel.id) != null) return

        val sound = if (channel.sound) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            null
        }
        val attributes = if (channel.sound) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
        } else {
            null
        }

        manager.createNotificationChannel(
            NotificationChannel(
                channel.id,
                context.getString(channel.nameRes),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(channel.descriptionRes)
                enableVibration(channel.vibration)
                if (channel.vibration) vibrationPattern = VIBRATION_PATTERN
                setSound(sound, attributes)
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
        const val NOTIFICATION_ID = 1_002
        const val REQUEST_OPEN = 2

        /** Krótko, ale wyraźnie: dwa razy „seria” po 300 ms. */
        val VIBRATION_PATTERN = longArrayOf(0L, 300L, 150L, 300L)
    }
}
