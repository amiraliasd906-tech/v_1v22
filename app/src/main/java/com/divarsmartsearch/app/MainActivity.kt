package com.divarsmartsearch.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.divarsmartsearch.app.data.local.LocalPreferencesDataStore
import com.divarsmartsearch.app.data.local.dao.AppSettingsDao
import com.divarsmartsearch.app.presentation.navigation.DivarNavGraph
import com.divarsmartsearch.app.presentation.theme.DivarSmartSearchTheme
import com.divarsmartsearch.app.service.BackgroundScanService
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything runs locally on this device — there is no server to
 * register a push-notification token with, so all we need on startup is
 * the local notification permission (for Android 13+) and the dark-mode
 * preference for theming.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var localPreferences: LocalPreferencesDataStore
    @Inject lateinit var appSettingsDao: AppSettingsDao

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // If the person already turned on background scanning in a
        // previous session, make sure the service is actually running —
        // it may have been killed by the system since then.
        lifecycleScope.launch {
            if (appSettingsDao.get()?.backgroundScanEnabled == true) {
                BackgroundScanService.start(applicationContext)
            }
        }

        setContent {
            val darkModeEnabled by localPreferences.darkModeEnabled.collectAsState(initial = true)

            DivarSmartSearchTheme(darkTheme = darkModeEnabled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DivarNavGraph()
                }
            }
        }
    }
}
