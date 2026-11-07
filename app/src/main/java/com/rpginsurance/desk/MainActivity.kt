package com.rpginsurance.desk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpginsurance.desk.ui.BriefScreen
import com.rpginsurance.desk.ui.BriefViewModel
import com.rpginsurance.desk.ui.DeskTheme
import com.rpginsurance.desk.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: BriefViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI re-checks on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeskTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    if (app.settings.dailyNotification) requestNotificationPermission(openSettingsIfBlocked = false)
                }

                if (showSettings) {
                    BackHandler { showSettings = false; viewModel.onResume() }
                    SettingsScreen(
                        onBack = { showSettings = false; viewModel.onResume() },
                        onRequestNotificationPermission = { requestNotificationPermission(openSettingsIfBlocked = true) },
                    )
                } else {
                    BriefScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    /**
     * Asks for POST_NOTIFICATIONS (Android 13+). When the user taps "Allow notifications" and the
     * system won't show the prompt any more, the app's notification settings open instead.
     */
    private fun requestNotificationPermission(openSettingsIfBlocked: Boolean) {
        val needsRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val promptAvailable = needsRuntime &&
            (!openSettingsIfBlocked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) || !askedBefore())
        when {
            promptAvailable -> {
                markAsked()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            openSettingsIfBlocked && !Notifications.canPost(this) -> startActivity(
                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private val prefs get() = getSharedPreferences("settings", MODE_PRIVATE)
    private fun askedBefore() = prefs.getBoolean("asked_notifications", false)
    private fun markAsked() = prefs.edit().putBoolean("asked_notifications", true).apply()
}
