package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun biometric_lock_screen_displays_french_rgpd_protection() {
    composeTestRule.setContent {
      MyApplicationTheme {
        BiometricLockScreen(
          isBiometricAvailable = true,
          statusDescription = "Biométrie et code de verrouillage prêts",
          errorMessage = null,
          isAuthenticating = false,
          onTriggerBiometric = {},
          onOpenSecuritySettings = {},
          onContinueUnconfigured = {}
        )
      }
    }

    composeTestRule.onNodeWithText("Dossier Médical Protégé").assertIsDisplayed()
    composeTestRule.onNodeWithTag("biometric_unlock_button").assertIsDisplayed()
  }
}
