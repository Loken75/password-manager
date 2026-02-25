package com.passwordmanager.android.ui.settings

import com.passwordmanager.android.test.FakeConfigRepository
import com.passwordmanager.android.test.MainDispatcherExtension
import com.passwordmanager.config.ThemeMode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MainDispatcherExtension::class)
class SettingsViewModelTest {

    private lateinit var configRepo: FakeConfigRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        configRepo = FakeConfigRepository()
        viewModel = SettingsViewModel(configRepo)
    }

    @Test
    fun `init loads config values`() {
        val state = viewModel.uiState.value
        assertEquals("en", state.language)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(15, state.autoLockMinutes)
        assertEquals(30, state.clipboardClearSeconds)
    }

    @Test
    fun `setTheme updates state and repo`() {
        viewModel.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertEquals(ThemeMode.DARK, configRepo.getThemeMode())
    }

    @Test
    fun `setLanguage updates state and repo`() {
        viewModel.setLanguage("fr")
        assertEquals("fr", viewModel.uiState.value.language)
        assertEquals("fr", configRepo.getLanguage())
    }

    @Test
    fun `setAutoLockMinutes clamps and updates`() {
        viewModel.setAutoLockMinutes(30)
        assertEquals(30, viewModel.uiState.value.autoLockMinutes)
        assertEquals(30, configRepo.getAutoLockMinutes())

        // Clamp to min
        viewModel.setAutoLockMinutes(0)
        assertEquals(1, viewModel.uiState.value.autoLockMinutes)

        // Clamp to max
        viewModel.setAutoLockMinutes(100)
        assertEquals(60, viewModel.uiState.value.autoLockMinutes)
    }

    @Test
    fun `setClipboardClearSeconds clamps and updates`() {
        viewModel.setClipboardClearSeconds(60)
        assertEquals(60, viewModel.uiState.value.clipboardClearSeconds)
        assertEquals(60, configRepo.getClipboardClearSeconds())

        // Clamp to min
        viewModel.setClipboardClearSeconds(1)
        assertEquals(5, viewModel.uiState.value.clipboardClearSeconds)

        // Clamp to max
        viewModel.setClipboardClearSeconds(200)
        assertEquals(120, viewModel.uiState.value.clipboardClearSeconds)
    }
}
