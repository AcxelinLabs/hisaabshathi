package com.acxelin.hisaabsaathi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.data.LedgerDb
import com.acxelin.hisaabsaathi.ui.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Same app, but the ledger lives in memory (SQLCipher's native library cannot load on a PC). */
class TestApp : HisaabApp() {
    override fun openDb(): LedgerDb =
        Room.inMemoryDatabaseBuilder(this, LedgerDb::class.java).allowMainThreadQueries().build()
}

/**
 * Drives the real screens, view model and database through one worker's first session:
 * setup, a typed entry, confirmation, the ledger, the summary, a question, the wage check.
 * Voice capture itself needs a phone; here the transcript is typed in.
 * Screenshots are written to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w360dp-h780dp-xhdpi")
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/screens/$name.png")

    /** Background work (settings, database) posts back to the main thread; let it run. */
    private fun pump() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun waitFor(text: String) = try {
        rule.waitUntil(15_000) {
            pump()
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("Never saw \"$text\". On screen:\n" + rule.onRoot().printToString(), e)
    }

    /** Clicks once the control is on screen and enabled (settings are saved in the background). */
    private fun click(text: String) {
        val clickable = androidx.compose.ui.test.isEnabled() and androidx.compose.ui.test.hasClickAction()
        var enabled = hasText(text, substring = true) and clickable
        rule.waitUntil(15_000) { pump(); rule.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        // Prefer the control whose label is exactly this text ("हिसाब" tab, not the "हिसाब साथी" title).
        (hasText(text) and clickable).let { if (rule.onAllNodes(it).fetchSemanticsNodes().isNotEmpty()) enabled = it }
        val node = rule.onAllNodes(enabled).onFirst()
        runCatching { node.performScrollTo() } // only works inside scrolling screens
        node.performClick()
        pump()
    }

    @Test fun firstSession() {
        // --- setup (Hindi is the default language)
        waitFor("अपनी भाषा चुनें")
        shot("01-language")
        click("आगे")
        waitFor("Odisha")
        click("Odisha")
        shot("02-state")
        click("आगे")
        waitFor("आवाज़ पैक")
        shot("03-voice-pack")
        click("शुरू करें")

        // --- home
        waitFor("दबाएं और काम या खर्च बोलें")
        shot("04-home-empty")

        // --- an entry (typed here; spoken on a phone)
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction())
            .performTextInput("आज आठ घंटे काम किया एक सौ बीस पीस पांच रुपये रेट तीन सौ एडवांस लिया")
        click("आगे")
        waitFor("क्या यह सही है?")
        rule.onNode(hasSetTextAction() and hasText("600")).assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText("300")).assertExists()
        rule.onNodeWithText("120 पीस × ₹5, 8 घंटे").assertExists()
        shot("05-confirm")
        click("सही है, सेव करें")

        // --- saved: today's card shows earned 600, advance 300, due 300
        waitFor("सेव हो गया")
        rule.waitUntil(15_000) { pump(); rule.onAllNodesWithText("₹600").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("₹300").fetchSemanticsNodes().let { check(it.size >= 2) { "advance and due should both be ₹300" } }
        shot("06-home-after-save")
        click("हो गया")

        // --- an expense, to prove more than one kind of entry
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction()).performTextInput("सब्जी पर अस्सी रुपये खर्च किए")
        click("आगे")
        waitFor("क्या यह सही है?")
        rule.onNode(hasSetTextAction() and hasText("80")).assertExists()
        click("सही है, सेव करें")
        waitFor("सेव हो गया")
        click("हो गया")

        // --- a question
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction()).performTextInput("इस हफ्ते कितना कमाया")
        click("आगे")
        waitFor("इस हफ्ते आपने 600 रुपये कमाए")
        shot("07-question-answer")
        click("हो गया")

        // --- other tabs
        click("खाता")
        waitFor("खाना")
        shot("08-ledger")
        click("हिसाब")
        waitFor("बचत का लक्ष्य")
        shot("09-summary")
        click("मजदूरी व मदद")
        waitFor("सरकारी न्यूनतम मजदूरी")
        rule.onNodeWithText("रिकॉर्ड से छेड़छाड़ नहीं हुई", substring = true).assertExists()
        shot("10-wage")

        // --- settings, then the same home screen in Odia and English
        rule.onNodeWithContentDescription("सेटिंग").performClick()
        waitFor("ऑफ़लाइन पैक")
        shot("11-settings")
        // --- Telugu, Kannada and Tamil home screens
        for ((name, done, home, prompt, file) in listOf(
            listOf("తెలుగు", "", "", "", "14-home-telugu"), listOf("ಕನ್ನಡ", "", "", "", "15-home-kannada"), listOf("தமிழ்", "", "", "", "16-home-tamil"),
        )) {
            click(name)
            pump()
            rule.waitForIdle()
            shot(file.replace("home", "settings"))
        }
        click("ଓଡ଼ିଆ")
        waitFor("ସେଟିଂ")
        rule.onNodeWithContentDescription("ହୋଇଗଲା").performClick()
        click("ହୋମ୍")
        waitFor("ଦବାଇ କାମ କିମ୍ବା ଖର୍ଚ୍ଚ କୁହନ୍ତୁ")
        shot("12-home-odia")
        rule.onNodeWithContentDescription("ସେଟିଂ").performClick()
        click("English")
        waitFor("Settings")
        rule.onNodeWithContentDescription("Done").performClick()
        click("Home")
        waitFor("Tap and speak")
        shot("13-home-english")
    }
}
