package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.ui.AppViewModel
import com.acxelin.hisaabsaathi.ui.Capture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The confirm step, driven through the real view model and database. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ReviewFlowTest {
    private val vm = AppViewModel(ApplicationProvider.getApplicationContext())

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun waitUntil(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!ok()) {
            idle(); Thread.sleep(20)
            check(System.currentTimeMillis() < end) { "never: $what (capture = ${vm.capture.value})" }
        }
    }

    private fun reviewOf(text: String): Capture.Review {
        vm.submitText(text)
        waitUntil("review") { vm.capture.value is Capture.Review }
        return vm.capture.value as Capture.Review
    }

    @Test fun doubleTapOnSaveSavesOnce() {
        reviewOf("सब्जी पर अस्सी रुपये खर्च किए")
        vm.confirmSave(); vm.confirmSave()
        waitUntil("saved") { vm.capture.value is Capture.Message }
        waitUntil("one entry") { vm.entries.value.isNotEmpty() }
        Thread.sleep(200); idle()
        assertEquals(1, vm.entries.value.size)
    }

    @Test fun wrongLineCanBeRemovedAndDayChanged() {
        val r = reviewOf("आज आठ घंटे काम किया एक सौ बीस पीस पांच रुपये रेट तीन सौ एडवांस लिया")
        assertEquals(2, r.drafts.size)
        vm.removeDraft(1)
        val one = vm.capture.value as Capture.Review
        assertEquals(listOf(EntryType.WORK), one.drafts.map { it.type })
        vm.removeDraft(0) // the last line stays
        assertEquals(1, (vm.capture.value as Capture.Review).drafts.size)
        vm.updateDraft(0, one.drafts[0].copy(dayOffset = -1))
        vm.confirmSave()
        waitUntil("saved") { vm.entries.value.isNotEmpty() }
        assertEquals(java.time.LocalDate.now().minusDays(1).toEpochDay(), vm.entries.value.single().epochDay)
    }

    @Test fun typingSlipBlocksSavingAndEmptyBoxIsZero() {
        reviewOf("सब्जी पर अस्सी रुपये खर्च किए")
        vm.updateAmount(0, "8o")
        assertFalse((vm.capture.value as Capture.Review).canSave)
        vm.confirmSave()
        assertTrue(vm.capture.value is Capture.Review) // nothing happened
        vm.updateAmount(0, "")
        assertEquals(0L, (vm.capture.value as Capture.Review).drafts[0].amountPaise)
        assertFalse((vm.capture.value as Capture.Review).canSave) // nothing worth saving
        vm.updateAmount(0, "90,50")
        assertEquals(9_050L, (vm.capture.value as Capture.Review).drafts[0].amountPaise)
        assertTrue((vm.capture.value as Capture.Review).canSave)
    }

    @Test fun understandingCanBeCancelled() {
        vm.submitText("सब्जी पर अस्सी रुपये खर्च किए")
        vm.cancelThinking()
        idle()
        assertEquals(Capture.Idle, vm.capture.value)
    }
}
