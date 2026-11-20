package com.acxelin.hisaabsaathi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Strings
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

    /** No network in tests: setup must not start the voice downloads by itself. */
    override val autoDownloadVoice: Boolean = false

    /** Tests start on the app itself; the welcome has its own test. */
    override val showWelcome: Boolean = false

    /** Where "Use my location" finds the phone in tests (Robolectric's location service is not used). */
    override suspend fun oneLocation(): android.location.Location? = fakeLocation?.let { (la, lo) ->
        android.location.Location("test").apply { latitude = la; longitude = lo; time = System.currentTimeMillis() }
    }

    companion object {
        var fakeLocation: Pair<Double, Double>? = null
    }
}

/**
 * Drives the real screens, view model and database through one worker's first session:
 * setup, a typed entry, confirmation, the ledger, the summary, a question, the wage check.
 * Voice capture itself needs a phone; here the transcript is typed in.
 * Screenshots are written to app/build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
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
        org.robolectric.shadows.ShadowLog.stream = System.out
        // --- setup. The first screen asks in every language; the worker's own choice is final.
        waitFor("Choose your language")
        rule.onNodeWithText("अपनी भाषा चुनें").assertExists() // the same question in Hindi (and the others)
        click("हिन्दी")
        waitFor("भाषा आप कभी भी सेटिंग में बदल सकते हैं।")
        shot("01-language")
        click("आगे")
        waitFor("आपकी जानकारी")
        shot("01b-details")
        click("खुद भरूँगा")
        waitFor("आपका नाम क्या है?")
        rule.onNode(hasSetTextAction()).performTextInput("Ramesh Behera")
        shot("01c-name")
        click("आगे")
        waitFor("आपकी उम्र कितनी है?")
        rule.onNode(hasSetTextAction()).performTextInput("35")
        shot("01d-age")
        click("आगे")
        // Where you work: the PIN fills in state and district.
        waitFor("आपका पिन कोड क्या है?")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("751002")
        waitFor("मिल गया: Khordha (Khurda), Odisha")
        shot("02-place")
        click("आगे")
        waitFor("अपनी फोटो लें")
        shot("02b-photo")
        click("छोड़ें") // no camera in tests
        // Help improve the app: asked, read aloud, nothing chosen for the worker.
        waitFor("हिसाब साथी को बेहतर बनाने में मदद करेंगे?")
        check(rule.onAllNodes(hasText("आगे") and androidx.compose.ui.test.isEnabled()).fetchSemanticsNodes().isEmpty()) { "Next must wait for a yes or no" }
        click("हाँ, भेजें")
        shot("03b-help-improve")
        click("आगे")
        waitFor("समझदार मॉडल")
        shot("03-smart-model")
        click("शुरू करें")

        // --- the profile page, from the photo button at the top of the home screen
        // Setup saves in the background: wait for the home screen before tapping.
        rule.waitUntil(15_000) { pump(); rule.onAllNodesWithContentDescription("मेरी प्रोफ़ाइल").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("मेरी प्रोफ़ाइल").performClick()
        waitFor("Ramesh Behera")
        rule.onNodeWithText("751002").assertExists()
        shot("03c-profile")
        rule.onNodeWithContentDescription("हो गया").performClick()

        // --- home
        waitFor("दबाएं और काम या खर्च बोलें")
        shot("04-home-empty")

        // --- mic before the voice pack is downloaded: say so, never "not understood"
        org.robolectric.Shadows.shadowOf(rule.activity.application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        rule.onNodeWithContentDescription("दबाएं और काम या खर्च बोलें").performClick()
        // The phone's own recogniser has no offline Hindi model: it fails at once with "language unavailable".
        rule.waitUntil(10_000) { pump(); org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer() != null }
        org.robolectric.Shadows.shadowOf(org.robolectric.shadows.ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(13)
        waitFor("आवाज़ पैक नहीं है")
        check(rule.onAllNodesWithText("समझ नहीं आया", substring = true).fetchSemanticsNodes().isEmpty())
        shot("04b-mic-without-voice-pack")
        rule.onNodeWithContentDescription("हो गया").performClick()

        // --- an entry (typed here; spoken on a phone)
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction())
            .performTextInput("आज आठ घंटे काम किया एक सौ बीस पीस पांच रुपये रेट तीन सौ एडवांस लिया")
        click("आगे")
        waitFor("क्या यह सही है?")
        // Each line is a short summary with a big amount; nothing to edit unless asked.
        rule.onNodeWithText("₹600").assertIsDisplayed()
        rule.onNodeWithText("₹300").assertExists()
        check(rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()) { "amount boxes should stay folded" }
        rule.onNodeWithText("120 पीस × ₹5, 8 घंटे", substring = true).assertExists()
        shot("05-confirm")
        click("सही है, सेव करें")

        // --- saved: today's card shows earned 600, advance 300, due 300
        waitFor("सेव हो गया")
        rule.waitUntil(15_000) { pump(); rule.onAllNodesWithText("₹600").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("₹300").fetchSemanticsNodes().let { check(it.size >= 2) { "advance and due should both be ₹300" } }
        shot("06-home-after-save")
        rule.onNodeWithContentDescription("हो गया").performClick()

        // --- an expense, to prove more than one kind of entry
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction()).performTextInput("सब्जी पर अस्सी रुपये खर्च किए")
        click("आगे")
        waitFor("क्या यह सही है?")
        rule.onNodeWithText("₹80").assertExists()
        shot("05b-confirm-expense")
        click("बदलें")
        rule.onNode(hasSetTextAction() and hasText("80")).assertExists()
        shot("05c-confirm-change")
        // The worker fixes the amount: kept to send, as they agreed (shown in Settings below).
        rule.onNode(hasSetTextAction() and hasText("80")).performTextReplacement("90")
        click("सही है, सेव करें")
        waitFor("सेव हो गया")
        rule.onNodeWithContentDescription("हो गया").performClick()

        // --- a question
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction()).performTextInput("इस हफ्ते कितना कमाया")
        click("आगे")
        waitFor("इस हफ्ते आपने 600 रुपये कमाए")
        shot("07-question-answer")
        rule.onNodeWithContentDescription("हो गया").performClick()

        // --- simple questions always get an answer
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction()).performTextInput("आपका नाम क्या है")
        click("आगे")
        waitFor("मैं हिसाब साथी हूँ")
        shot("07b-name-answer")
        rule.onNodeWithContentDescription("हो गया").performClick()
        click("लिखकर डालें")
        rule.onNode(hasSetTextAction()).performTextInput("एक चुटकुला सुनाओ")
        click("आगे")
        waitFor("यह मैं नहीं समझा")
        rule.onNodeWithContentDescription("हो गया").performClick()

        // --- other tabs
        click("खाता")
        waitFor("खाना")
        shot("08-ledger")
        // Delete a line, then undo (the edit dialog is drawn in EditFormScreenshotTest).
        rule.onAllNodesWithContentDescription("हटाएं").onFirst().performClick(); pump()
        click("हटाएं")
        waitFor("हटा दिया")
        shot("08c-undo")
        click("वापस लाएं")
        waitFor("खाना")
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
        // Two entries (one fixed), a question, a name question and one not understood: 5 kept to send.
        waitFor("5 भेजने बाकी")
        rule.onNodeWithText("भेजा हुआ मिटाएं").performScrollTo()
        shot("11b-settings-help-improve")
        rule.onRoot().performTouchInput { swipeDown() } // back to the top for the language buttons
        // --- a question and its answer in every language (home screen, typed; spoken on a phone)
        val questions = listOf(
            Lang.OR to "ଏହି ସପ୍ତାହରେ କେତେ ରୋଜଗାର ହେଲା",
            Lang.TE to "ఈ వారం ఎంత సంపాదించాను",
            Lang.KN to "ಈ ವಾರ ನಾನು ಎಷ್ಟು ದುಡಿದೆ",
            Lang.TA to "இந்த வாரம் எவ்வளவு சம்பாதிச்சேன்",
            Lang.EN to "how much did I earn this week",
            Lang.HI to "इस हफ्ते कितना कमाया",
        )
        var current = Strings(Lang.HI)
        for ((lang, question) in questions) {
            val s = Strings(lang)
            click(lang.nativeName)
            waitFor(s.settings)
            rule.onNodeWithContentDescription(s.done).performClick()
            click(s.tabHome)
            waitFor(s.tapAndSpeak)
            shot("20-home-${lang.code}")
            click(s.typeInstead)
            rule.onNode(hasSetTextAction()).performTextInput(question)
            shot("21-typing-${lang.code}")
            click(s.next)
            waitFor("600")
            waitFor(question)
            shot("22-answer-${lang.code}")
            rule.onNodeWithContentDescription(s.done).performClick()
            rule.onNodeWithContentDescription(s.settings).performClick()
            current = s
        }
        // --- a simple question in English, and the other tabs in English
        rule.onNodeWithContentDescription(current.done).performClick()
    }
}
