package com.gymtracker.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Budzik końca przerwy: domyka przerwę i pokazuje alarm.
 *
 * Receiver bywa pierwszym, co wstaje po ubiciu procesu - dlatego nie zakłada
 * niczego o pamięci aplikacji i nie potrzebuje działającej usługi. `goAsync()`
 * daje nam chwilę na zapis do bazy po powrocie z `onReceive`, bo domknięcie
 * przerwy to operacja na dysku.
 */
@AndroidEntryPoint
class RestAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var restTimer: RestTimer

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestAlarmScheduler.ACTION_REST_ALARM) return
        val intervalId = intent.getLongExtra(RestAlarmScheduler.EXTRA_INTERVAL_ID, 0L)
        if (intervalId <= 0L) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                restTimer.onAlarm(intervalId)
            } finally {
                pending.finish()
            }
        }
    }
}
