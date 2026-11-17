package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.api.GeminiScannerService
import com.example.ui.screens.AiScanScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiScanScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `demo preset returns structured Doliprane info and unmocked call surfaces real error when offline`() = runBlocking {
        val dolipraneResult = GeminiScannerService.analyzeMedicationImage(
            bitmap = null,
            promptHint = "Doliprane 1000 mg comprimé effervescent",
            allowDemoPreset = true
        )
        assertEquals("Doliprane", dolipraneResult.name)
        assertEquals("1000 mg", dolipraneResult.dosage)
        assertEquals("Paracétamol", dolipraneResult.activeIngredient)
        assertTrue(dolipraneResult.isDemoMode)

        val liveResult = GeminiScannerService.analyzeMedicationImage(
            bitmap = null,
            promptHint = "Photo boîte ou ordonnance de médicament",
            allowDemoPreset = false
        )
        assertEquals("", liveResult.name)
        assertNotNull(liveResult.errorMessage)
    }

    @Test
    fun `clicking analyze shows medication info card immediately below scan card`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AiScanScreen(
                    onAddMedication = { _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("scan_camera_preview_box").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_analyze_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("scan_results_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("scan_results_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_status_banner").assertIsDisplayed()
    }
}
