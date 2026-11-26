package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.DraftEntry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.ui.AppViewModel
import com.acxelin.hisaabsaathi.ui.Capture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * When the app goes to the background, what was in progress is kept (never saved into the ledger
 * unconfirmed) and comes back, with the screen the worker was on.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ResumeTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    private fun waitUntil(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!ok()) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); Thread.sleep(10)
            check(System.currentTimeMillis() < end) { "never: $what" }
        }
    }

    @Before fun fresh() { runBlocking { app.settings.clear() } }

    @Test fun linesWaitingForAYesComeBackUnsaved() {
        val vm = AppViewModel(app)
        waitUntil("settings") { vm.settings.value != null }
        val drafts = listOf(DraftEntry(EntryType.WORK, 60_000, hours = 8.0), DraftEntry(EntryType.EXPENSE, 3_000, detail = "food=TEA,SAMOSA"))
        vm.capture.value = Capture.Review("आज आठ घंटे", drafts, false, "voice")
        vm.typedText = "120 पीस"
        vm.wentAway()
        waitUntil("kept") { runBlocking { app.settings.flow.first().pendingReview.isNotEmpty() } }
        // The app is opened again later (a new start): the same lines wait for a yes; nothing was saved.
        val again = AppViewModel(app)
        waitUntil("restored") { again.capture.value is Capture.Review }
        assertEquals(drafts, (again.capture.value as Capture.Review).drafts)
        assertEquals("120 पीस", again.typedText)
        assertTrue(again.entries.value.isEmpty())
        // Kept only once: the next start begins clean.
        waitUntil("cleared") { runBlocking { app.settings.flow.first().pendingReview.isEmpty() } }
    }

    @Test fun theScreenIsRemembered() {
        val vm = AppViewModel(app)
        vm.rememberScreen("summary")
        waitUntil("saved") { runBlocking { app.settings.flow.first().lastScreen == "summary" } }
        vm.rememberSetupStep("PLACE:DISTRICT")
        waitUntil("step") { runBlocking { app.settings.flow.first().setupStep == "PLACE:DISTRICT" } }
    }
}
