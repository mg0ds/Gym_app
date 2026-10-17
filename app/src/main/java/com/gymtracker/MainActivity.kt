package com.gymtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gymtracker.timer.WorkoutTimer
import com.gymtracker.ui.MainViewModel
import com.gymtracker.ui.navigation.GymTrackerNavHost
import com.gymtracker.ui.theme.GymTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var workoutTimer: WorkoutTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Jedyne miejsce, które pilnuje zegara treningu: łapie nowy trening,
        // trening odtworzony po ubiciu aplikacji i powrót z tła. Ekrany nie
        // muszą o niczym pamiętać (patrz `WorkoutTimer.attach`).
        workoutTimer.attach(lifecycleScope)

        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            GymTrackerTheme(themeMode = themeMode) {
                NotificationPermissionGate(viewModel)
                GymTrackerNavHost()
            }
        }
    }
}

/**
 * Prosi o zgodę na powiadomienia, gdy zaczyna się trening.
 *
 * Bez tej zgody (Android 13+) usługa timera działa dalej, ale notyfikacji
 * z czasem treningu nie widać - a to ona pozwala wstrzymać trening przy
 * zgaszonym ekranie. Pytamy **przy pierwszym treningu**, nie na starcie
 * aplikacji: wcześniej użytkownik nie wie, po co ta zgoda.
 *
 * Odmowa jest respektowana do końca życia procesu - nie zamęczamy pytaniem
 * przed każdym treningiem.
 */
@Composable
private fun NotificationPermissionGate(viewModel: MainViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val trackedWorkout by viewModel.trackedWorkout.collectAsStateWithLifecycle()
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) viewModel.onNotificationPermissionGranted() },
    )

    LaunchedEffect(trackedWorkout?.sessionId) {
        if (trackedWorkout != null && !alreadyAsked && !context.canPostNotifications()) {
            alreadyAsked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun Context.canPostNotifications(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
