package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.ui.AppViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Voice first, end to end with a fake "worker" (what they say, one answer per listen): the app
 * asks for what is missing, reads it back, hears "yes" and saves, without a single tap. And the
 * reminder opens the app listening.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class VoiceFirstTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val vm by lazy { AppViewModel(app) }

    private fun waitUntil(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!ok()) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); Thread.sleep(10)
            check(System.currentTimeMillis() < end) { "never: $what; still to say: ${TestApp.heard}; capture=${vm.capture.value}" }
        }
    }

    @Before fun ready() { TestApp.heard.clear(); waitUntil("settings") { vm.settings.value != null } }
    @After fun quiet() { TestApp.heard.clear() }

    @Test fun piecesWithoutARateAreAskedForThenSavedByYes() {
        // Says the work, is asked the rate, says it, hears it back, says yes.
        TestApp.heard.addAll(listOf("आज एक सौ बीस पीस बनाए", "पांच रुपये", "हाँ"))
        vm.micTapped()
        waitUntil("saved") { vm.entries.value.isNotEmpty() }
        val e = vm.entries.value.single()
        assertEquals(EntryType.WORK, e.type)
        assertEquals(120, e.pieces)
        assertEquals(500L, e.ratePaise)
        assertEquals(60_000L, e.amountPaise) // 120 × ₹5, worked out by the app, never by the model
        assertEquals("voice", e.source)
        assertTrue(TestApp.heard.isEmpty())
    }

    @Test fun hoursWithoutAnAmountAreAskedFor() {
        TestApp.heard.addAll(listOf("आज आठ घंटे काम किया", "पांच सौ", "हाँ"))
        vm.micTapped()
        waitUntil("saved") { vm.entries.value.isNotEmpty() }
        val e = vm.entries.value.single()
        assertEquals(8.0, e.hours!!, 0.0)
        assertEquals(50_000L, e.amountPaise)
    }

    @Test fun noAnswerLeavesItForTheWorkerAndSavesNothing() {
        TestApp.heard.addAll(listOf("आज एक सौ बीस पीस बनाए", "कुछ नहीं पता"))
        vm.micTapped()
        waitUntil("asked and left") { TestApp.heard.isEmpty() && vm.capture.value is com.acxelin.hisaabsaathi.ui.Capture.Review }
        repeat(20) { shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
        assertTrue("nothing saved without an amount", vm.entries.value.isEmpty())
        assertEquals(0L, (vm.capture.value as com.acxelin.hisaabsaathi.ui.Capture.Review).drafts.single().amountPaise)
    }

    @Test fun theReminderOpensTheAppListening() {
        TestApp.heard.addAll(listOf("तीन सौ एडवांस लिया", "हाँ"))
        vm.requestListen()
        vm.listenFromReminder()
        waitUntil("saved") { vm.entries.value.isNotEmpty() }
        assertEquals(EntryType.ADVANCE, vm.entries.value.single().type)
        assertEquals(30_000L, vm.entries.value.single().amountPaise)
    }

    @Test fun setupReadsTheQuestionThenListensForTheAnswer() {
        // A fresh install: no speech pack yet. The question is said, then the app listens.
        TestApp.heard.add("मेरा नाम रमेश है")
        var name: String? = null
        vm.askAndListen("q_name", "आपका नाम क्या है?") { text -> com.acxelin.hisaabsaathi.core.SpokenDetails.name(text).also { name = it } }
        waitUntil("heard the name") { name != null }
        assertEquals("रमेश", name)
        assertTrue(TestApp.heard.isEmpty())
    }
}
