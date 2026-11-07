package io.retroshelf

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DeviceSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nativeNavigationRotationAndPersistentFavorites() {
        compose.onNodeWithText("Small games.", substring = true).assertIsDisplayed()
        screenshot("home-portrait")
        compose.onNodeWithText("NES").performClick()
        compose.onNodeWithText("No supported source yet").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.onNodeWithText("No supported source yet").assertExists()
        screenshot("empty-landscape")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark theme").assertIsDisplayed()
        screenshot("settings-landscape")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitForIdle()

        // A test-only record exercises actual Room persistence without any live network.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val game = Game("udb:device-test", "udb", "device-test", "Device fixture game", "Test fixture only", listOf("3DS"), "https://example.com")
        var db = Room.databaseBuilder(context, ShelfDatabase::class.java, "retroshelf.db").build()
        runBlocking { db.shelf().put(StoredGame(game.id, game.provider, Gson().toJson(game), favorite = true)) }
        db.close()
        db = Room.databaseBuilder(context, ShelfDatabase::class.java, "retroshelf.db").build()
        runBlocking { assertTrue(db.shelf().game(game.id)!!.favorite) }
        db.close()
        compose.onNodeWithText("Saved").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Device fixture game").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Device fixture game").performClick()
        compose.onNodeWithText("Test fixture only").assertIsDisplayed()
        compose.onNodeWithContentDescription("Toggle favorite").assertExists()
        screenshot("details-portrait")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Test fixture only").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Toggle favorite").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("No downloads yet").assertIsDisplayed()
        screenshot("downloads-portrait")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
