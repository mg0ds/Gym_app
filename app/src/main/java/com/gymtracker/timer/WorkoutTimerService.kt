package com.gymtracker.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gymtracker.MainActivity
import com.gymtracker.R
import com.gymtracker.domain.timer.TrackedWorkout
import com.gymtracker.domain.timer.formatElapsed
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Usługa pierwszoplanowa z notyfikacją treningu.
 *
 * Po co usługa, skoro czas i tak liczy się z bazy? Bo **notyfikacja nie może
 * zniknąć**: dzięki niej trening widać przy zgaszonym ekranie, a użytkownik
 * może go wstrzymać albo zakończyć bez wracania do aplikacji. Proces w tle
 * z samą notyfikacją zostałby uśpiony razem z ekranem.
 *
 * Usługa **nie liczy czasu i nie trzyma stanu** - pokazuje tylko to, co widzi
 * w [WorkoutTimer]. Nie ma tu tykania: czas w notyfikacji odmierza systemowy
 * chronometr, a stan zmienia się wyłącznie w reakcji na akcję użytkownika.
 * Dzięki temu usługa nie zużywa baterii i nie da się rozjechać z ekranem.
 *
 * Akcje z notyfikacji działają także wtedy, gdy proces wstał od nowa (aplikacja
 * była ubita) - wtedy usługa odtwarza zegar z bazy przez [WorkoutTimer.restoreIfNeeded].
 */
@AndroidEntryPoint
class WorkoutTimerService : Service() {

    @Inject
    lateinit var timer: WorkoutTimer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // `startForeground` musi się zdarzyć w kilka sekund od startu usługi -
        // dlatego notyfikacja „pusta” powstaje od razu, a właściwa chwilę później.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        scope.launch {
            timer.tracked.collect { tracked ->
                if (tracked != null) updateNotification(tracked)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            // Każda akcja najpierw się upewnia, że wiemy, czego dotyczy.
            timer.restoreIfNeeded()
            when (intent?.action) {
                ACTION_PAUSE -> timer.pause()
                ACTION_RESUME -> timer.resume()
                ACTION_FINISH -> timer.finish()
            }
            // Stan się zmienił (albo dopiero dostaliśmy zgodę na powiadomienia,
            // ACTION_REFRESH) - upewniamy się, że notyfikacja nadąża.
            timer.tracked.value?.let { updateNotification(it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(tracked: TrackedWorkout?) {
        // Bez zgody na powiadomienia (Android 13+) aktualizacja notyfikacji jest
        // zabroniona. Usługa działa dalej - czas treningu liczy się z bazy,
        // a nie z tego, czy system pokazuje notyfikację.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return

        tracked ?: return
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildNotification(tracked))
    }

    private fun buildNotification(tracked: TrackedWorkout?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (tracked == null) {
            return builder
                .setContentTitle(getString(R.string.timer_notification_title))
                .setContentText(getString(R.string.timer_notification_starting))
                .build()
        }

        // W pauzie chronometr systemowy musiałby pokazać godzinę, a nie czas
        // treningu - dlatego czas stoi w treści notyfikacji, jako zwykły tekst.
        if (tracked.timer.isPaused) {
            return builder
                .setContentTitle(tracked.name)
                .setContentText(
                    getString(
                        R.string.timer_notification_paused,
                        formatElapsed(tracked.timer.elapsedMs(System.currentTimeMillis())),
                    ),
                )
                .setShowWhen(false)
                .addAction(0, getString(R.string.timer_action_resume), actionIntent(ACTION_RESUME))
                .addAction(0, getString(R.string.timer_action_finish), actionIntent(ACTION_FINISH))
                .build()
        }

        return builder
            .setContentTitle(tracked.name)
            .setContentText(getString(R.string.timer_notification_running))
            .setWhen(System.currentTimeMillis() - tracked.timer.elapsedMs(System.currentTimeMillis()))
            .setShowWhen(true)
            .setUsesChronometer(true)
            .addAction(0, getString(R.string.timer_action_pause), actionIntent(ACTION_PAUSE))
            .addAction(0, getString(R.string.timer_action_finish), actionIntent(ACTION_FINISH))
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, WorkoutTimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.timer_channel_description)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val ACTION_PAUSE = "com.gymtracker.timer.PAUSE"
        const val ACTION_RESUME = "com.gymtracker.timer.RESUME"
        const val ACTION_FINISH = "com.gymtracker.timer.FINISH"

        /** Bez zmiany stanu - tylko ponowienie notyfikacji (np. po zgodzie na powiadomienia). */
        const val ACTION_REFRESH = "com.gymtracker.timer.REFRESH"

        private const val CHANNEL_ID = "workout_timer"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 1
    }
}
