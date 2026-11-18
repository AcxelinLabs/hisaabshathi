package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.ExpenseCategory
import com.acxelin.hisaabsaathi.core.HashChain
import com.acxelin.hisaabsaathi.export.Exporter
import com.acxelin.hisaabsaathi.ui.AppViewModel
import com.acxelin.hisaabsaathi.ui.Capture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Changing a saved line, undoing a delete, and a backup copy restored on a "new phone". */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class LedgerEditTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val vm = AppViewModel(app)

    private fun waitUntil(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!ok()) {
            shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20)
            check(System.currentTimeMillis() < end) { "never: $what" }
        }
    }

    private fun save(text: String) {
        vm.submitText(text)
        waitUntil("review of $text") { vm.capture.value is Capture.Review }
        val before = vm.entries.value.size
        vm.confirmSave()
        waitUntil("saved $text") { vm.entries.value.size > before }
        vm.dismiss()
    }

    private fun chainIntact() = runBlocking { HashChain.firstBroken(app.ledger.oldestFirst()) < 0 }

    @Test fun editKeepsPlaceRecordsWhatChangedAndKeepsChainIntact() {
        save("सब्जी पर अस्सी रुपये खर्च किए")
        save("तीन सौ एडवांस लिया")
        val veg = vm.entries.value.single { it.type == EntryType.EXPENSE }
        assertFalse(vm.editEntry(veg, EntryType.EXPENSE, "8o", veg.epochDay, ExpenseCategory.FOOD)) // slip: nothing changes
        assertTrue(vm.editEntry(veg, EntryType.EXPENSE, "90", veg.epochDay - 1, ExpenseCategory.TRAVEL))
        waitUntil("edit") { vm.entries.value.any { it.amountPaise == 9_000L } }
        val edited = vm.entries.value.single { it.id == veg.id }
        assertEquals(ExpenseCategory.TRAVEL, edited.category)
        assertEquals(veg.epochDay - 1, edited.epochDay)
        assertTrue(edited.note, "edited" in edited.note && "was EXPENSE 80 " in edited.note)
        assertTrue(chainIntact())
        // A day in the future is not allowed.
        assertTrue(vm.editEntry(edited, EntryType.EXPENSE, "90", edited.epochDay + 30, ExpenseCategory.TRAVEL))
        waitUntil("future day capped") { vm.entries.value.single { it.id == veg.id }.epochDay <= java.time.LocalDate.now().toEpochDay() }
    }

    @Test fun deleteCanBeUndone() {
        save("सब्जी पर अस्सी रुपये खर्च किए")
        save("तीन सौ एडवांस लिया")
        val first = vm.entries.value.single { it.type == EntryType.EXPENSE }
        vm.delete(first.id)
        waitUntil("deleted") { vm.entries.value.size == 1 }
        assertEquals(first.id, vm.lastDeleted.value?.id)
        vm.undoDelete()
        waitUntil("restored") { vm.entries.value.size == 2 }
        assertNull(vm.lastDeleted.value)
        assertEquals(first.id, vm.entries.value.single { it.type == EntryType.EXPENSE }.id)
        assertTrue(chainIntact())
    }

    @Test fun backupCopyRestoresOnceOnANewPhone() = runBlocking {
        save("सब्जी पर अस्सी रुपये खर्च किए")
        save("तीन सौ एडवांस लिया")
        val json = Exporter(app).evidenceJson(app.ledger.oldestFirst()).readText()
        val lines = Exporter.readBackup(json)!!
        assertEquals(2, lines.size)
        // "New phone": empty the ledger, restore twice; the second time adds nothing.
        app.ledger.eraseAll()
        assertEquals(2, app.ledger.merge(lines))
        assertEquals(0, app.ledger.merge(lines))
        assertEquals(listOf(8_000L, 30_000L), app.ledger.oldestFirst().map { it.amountPaise })
        assertTrue(chainIntact())
        assertNull(Exporter.readBackup("{\"app\":\"Other\",\"entries\":[]}"))
        assertNull(Exporter.readBackup("not json"))
    }
}
