package com.passwordmanager.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passwordmanager.android.ui.components.PasswordField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test proving the Compose UI mounts and reacts on a real device. Uses the
 * self-contained [PasswordField] component (no Hilt / no Activity) so the test stays
 * fast and isolated while still exercising the real Compose runtime.
 */
@RunWith(AndroidJUnit4::class)
class PasswordFieldInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passwordField_rendersAndTogglesVisibility() {
        composeRule.setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                PasswordField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Master password"
                )
            }
        }

        composeRule.onNodeWithText("Master password").assertIsDisplayed()
        composeRule.onNodeWithText("Master password").performTextInput("hunter2")

        // Field starts masked: the toggle offers to "Show password".
        composeRule.onNodeWithContentDescription("Show password").performClick()
        // After tapping, it flips to offering "Hide password".
        composeRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }
}
