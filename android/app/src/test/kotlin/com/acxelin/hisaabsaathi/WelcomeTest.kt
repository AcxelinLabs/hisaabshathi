package com.acxelin.hisaabsaathi

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.ui.HisaabTheme
import com.acxelin.hisaabsaathi.ui.MainActivity
import com.acxelin.hisaabsaathi.ui.WelcomeScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The welcome when the app opens: in the worker's language, then on to the app by itself. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
class WelcomeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun movesOnByItself() {
        var done = false
        rule.activity.setContent { HisaabTheme(Lang.EN) { WelcomeScreen(Strings(Lang.EN), playSound = false) { done = true } } }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Welcome to your HisaabSaathi").assertExists()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertTrue("the welcome moves on by itself", done)
    }

    @Test fun inEachLanguage() {
        for (lang in Lang.entries) {
            rule.activity.setContent { HisaabTheme(lang) { WelcomeScreen(Strings(lang), playSound = false) {} } }
            rule.mainClock.advanceTimeBy(600)
            rule.onNodeWithText(Strings(lang).welcome).assertExists()
            rule.onRoot().captureRoboImage("build/screens/00-welcome-${lang.code}.png")
        }
    }
}
