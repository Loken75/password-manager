package com.passwordmanager.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.ui.navigation.AppNavigation
import com.passwordmanager.android.ui.theme.PasswordManagerTheme
import com.passwordmanager.config.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var configRepo: ConfigRepository
    @Inject lateinit var sessionHolder: SessionHolder

    private var autoLockJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Apply saved language locale on startup
        val savedLang = configRepo.getLanguage()
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty || currentLocales.get(0)?.language != savedLang) {
            val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(savedLang)
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        }

        // Prevent screenshots and task switcher from showing passwords
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (sessionHolder.isUnlocked()) {
                    val minutes = configRepo.getAutoLockMinutes()
                    autoLockJob = lifecycleScope.launch {
                        delay(minutes * 60_000L)
                        sessionHolder.lock()
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
                AppNavigation()
            }
        }
    }
}
