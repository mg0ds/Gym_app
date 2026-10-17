package com.gymtracker.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Budzik końca przerwy.
 *
 * Dlaczego nie wystarczy `delay` w korutynie? Bo gdy ekran gaśnie, proces
 * zaczyna być usypiany, a jego własne timery nie budzą urządzenia - przerwa
 * „kończyłaby się” dopiero przy następnym przebudzeniu. Alarm systemowy jest
 * jedynym mechanizmem, który obudzi telefon (i proces, gdyby został ubity).
 *
 * Dokładny alarm wymaga na Androidzie 12+ zgody „Alarmy i przypomnienia”, której
 * aplikacja fitness nie może od użytkownika wymagać. Dlatego: gdy zgoda jest -
 * `setExactAndAllowWhileIdle`, gdy jej nie ma - `setAndAllowWhileIdle`, czyli
 * ten sam moment, ale system może go przesunąć w głębokim Doze. W praktyce
 * podczas treningu telefon jest w ręce, więc opóźnienia nie widać, a ekran
 * treningu i tak odlicza na żywo.
 */
@Singleton
class RestAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(intervalId: Long, atEpochMs: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(intervalId)
        if (hasExactAlarms(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pending)
        }
    }

    /** Wołane przy każdej zmianie stanu przerwy - budzik jest tylko jeden. */
    fun cancel() {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(null))
    }

    private fun hasExactAlarms(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    /**
     * Ekstra nie wchodzą do porównania `PendingIntent`, więc jeden request code
     * obsługuje całą przerwę - `FLAG_UPDATE_CURRENT` podmienia tylko numer
     * wiersza, którego alarm dotyczy.
     */
    private fun pendingIntent(intervalId: Long?): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REST_ALARM,
        Intent(context, RestAlarmReceiver::class.java)
            .setAction(ACTION_REST_ALARM)
            .putExtra(EXTRA_INTERVAL_ID, intervalId ?: 0L),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REST_ALARM = "com.gymtracker.timer.REST_ALARM"
        const val EXTRA_INTERVAL_ID = "interval_id"

        private const val REQUEST_REST_ALARM = 7_701
    }
}
