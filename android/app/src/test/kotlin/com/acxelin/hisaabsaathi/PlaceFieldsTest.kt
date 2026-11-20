package com.acxelin.hisaabsaathi

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.ui.AppViewModel
import com.acxelin.hisaabsaathi.ui.Chalk
import com.acxelin.hisaabsaathi.ui.DetailsForm
import com.acxelin.hisaabsaathi.ui.HisaabTheme
import com.acxelin.hisaabsaathi.ui.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** My details: the PIN fills in state and district; dropdowns choose; wrong PINs say so. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
class PlaceFieldsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun pump() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/screens/$name.png")
    private fun waitFor(text: String) = rule.waitUntil(10_000) { pump(); rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun pinFillsInAndDropdownsChoose() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        val s = Strings(Lang.EN)
        rule.activity.setContent {
            HisaabTheme(Lang.EN) {
                Column(Modifier.fillMaxSize().background(Chalk).padding(16.dp)) {
                    val draft by vm.draft.collectAsStateWithLifecycle()
                    DetailsForm(vm, draft, s)
                }
            }
        }
        val pin = rule.onAllNodes(hasSetTextAction())[2] // name, age, PIN, state, district

        // One district: both filled in.
        pin.performTextInput("751002")
        waitFor("Found: Khordha (Khurda), Odisha")
        assertEquals("Odisha" to "Khordha (Khurda)", vm.draft.value.state to vm.draft.value.district)
        shot("30-pin-found")

        // Two possible districts: the state is filled in and the worker chooses from the dropdown,
        // which lists the two first.
        pin.performTextReplacement("400001")
        waitFor("This PIN code is in more than one district")
        assertEquals("Maharashtra" to "", vm.draft.value.state to vm.draft.value.district)
        rule.onAllNodes(hasSetTextAction())[4].performClick(); pump()
        waitFor("Mumbai Suburban")
        shot("31-district-dropdown")
        rule.onNodeWithText("Mumbai Suburban").performClick(); pump()
        waitFor("Found: Mumbai Suburban, Maharashtra")
        assertEquals("Mumbai Suburban", vm.draft.value.district)

        // Typing narrows the state list.
        rule.onAllNodes(hasSetTextAction())[3].performClick(); pump()
        rule.onAllNodes(hasSetTextAction())[3].performTextInput("bih"); pump()
        waitFor("Bihar")
        rule.onNodeWithText("Bihar").performClick(); pump()
        rule.waitUntil(5_000) { pump(); vm.draft.value.state == "Bihar" }
        assertEquals("", vm.draft.value.district) // a new state: the district is chosen again

        // A PIN no place has, and one that cannot be a PIN: said plainly, nothing else changes.
        pin.performTextReplacement("111111")
        waitFor("No place has this PIN code")
        assertEquals("Bihar", vm.draft.value.state)
        shot("32-pin-unknown")
        pin.performTextReplacement("012345")
        waitFor("A PIN code has 6 digits and starts with 1 to 8.")
    }
}
