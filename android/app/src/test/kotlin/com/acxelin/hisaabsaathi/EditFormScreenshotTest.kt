package com.acxelin.hisaabsaathi

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.Entry
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.ui.EditEntryForm
import com.acxelin.hisaabsaathi.ui.HisaabTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "change a saved line" form: what it looks like, and that it hands back what was chosen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
class EditFormScreenshotTest {
    // Hosted in the app's own activity: the bare test activity exists only in debug builds.
    @get:Rule val rule = createAndroidComposeRule<com.acxelin.hisaabsaathi.ui.MainActivity>()

    @Test fun editForm() {
        val e = Entry(id = 3, epochDay = java.time.LocalDate.now().toEpochDay(), createdAtMs = 1, type = EntryType.EXPENSE, amountPaise = 8_000, category = ExpenseCategory.FOOD)
        var saved: List<Any>? = null
        rule.runOnUiThread {
            // Replaces the app's own screen (the test rule's setContent refuses a second one).
            rule.activity.setContent {
                HisaabTheme(Lang.HI) { EditEntryForm(e, Strings(Lang.HI), onCancel = {}) { t, a, d, c -> saved = listOf(t, a, d, c) } }
            }
        }
        rule.waitForIdle()
        // Each choice is one button showing what is chosen; tapping it lists the others.
        rule.onNodeWithText("खाना").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("आना-जाना").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextReplacement("90")
        rule.onRoot().captureRoboImage("build/screens/08b-edit-entry.png")
        rule.onNodeWithText("हो गया").performClick()
        assertEquals(listOf(EntryType.EXPENSE, "90", e.epochDay, ExpenseCategory.TRAVEL), saved)
    }
}
