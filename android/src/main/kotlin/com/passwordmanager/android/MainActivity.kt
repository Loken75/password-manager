package com.passwordmanager.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.passwordmanager.android.data.AndroidConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.ui.navigation.AppNavigation
import com.passwordmanager.android.ui.theme.PasswordManagerTheme
import com.passwordmanager.config.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var configRepo: AndroidConfigRepository
    private var autoLockJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        configRepo = AndroidConfigRepository(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (SessionHolder.isUnlocked()) {
                    val minutes = configRepo.getAutoLockMinutes()
                    autoLockJob = lifecycleScope.launch {
                        delay(minutes * 60_000L)
                        SessionHolder.lock()
                    }
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                autoLockJob?.cancel()
                autoLockJob = null
            }
        })

        setContent {
            val themeMode by configRepo.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )

            PasswordManagerTheme(themeMode = themeMode) {
                AppNavigation(configRepo = configRepo)
            }
        }
    }
}
