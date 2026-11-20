package com.acxelin.hisaabsaathi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.EntryType
import com.acxelin.hisaabsaathi.core.Lang
import com.acxelin.hisaabsaathi.core.PhotoKind
import com.acxelin.hisaabsaathi.core.Strings
import com.acxelin.hisaabsaathi.ui.AppViewModel
import com.acxelin.hisaabsaathi.ui.Capture
import com.acxelin.hisaabsaathi.ui.HisaabTheme
import com.acxelin.hisaabsaathi.ui.MainActivity
import com.acxelin.hisaabsaathi.ui.PhotoKindDialog
import com.acxelin.hisaabsaathi.ui.PhotosScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Photos: the worker says what a photo is of. Bills, slips and salary slips become a line to
 * confirm (typed in when the amount cannot be read); bank statements and other papers are kept.
 * Photos not saved are not kept, and go with their line or with "erase everything".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w360dp-h780dp-xxhdpi")
class PhotosTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<TestApp>()
    private val vm by lazy { AppViewModel(app) }
    private val dir get() = File(app.filesDir, "attachments").apply { mkdirs() }

    private fun waitUntil(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!ok()) {
            shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20)
            check(System.currentTimeMillis() < end) { "never: $what; photos=${vm.photos.value} files=${dir.list()?.toList()} capture=${vm.capture.value}" }
        }
    }

    /** A "photo" of a paper: a small picture with a few lines on it. */
    private fun photo(name: String, tint: Int = Color.WHITE): File {
        val f = File(dir, name)
        val bmp = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(tint)
            val p = Paint().apply { color = Color.DKGRAY; strokeWidth = 6f }
            for (y in 60..340 step 40) drawLine(30f, y.toFloat(), 270f, y.toFloat(), p)
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return f
    }

    private fun take(kind: PhotoKind, text: String, name: String = "scan-${System.nanoTime()}.jpg", tint: Int = Color.WHITE): File {
        TestApp.fakePhotoText = text
        val f = photo(name, tint)
        vm.photoTaken(android.net.Uri.fromFile(f), f.path, kind)
        return f
    }

    @Test fun salarySlipBecomesAPaymentWithItsPhoto() {
        val f = take(PhotoKind.SALARY, "Sai Constructions\nGross 15,000\nDeductions 1,500\nNet Pay 13,500")
        waitUntil("review") { vm.capture.value is Capture.Review }
        val r = vm.capture.value as Capture.Review
        assertEquals(EntryType.PAYMENT, r.drafts.single().type)
        assertEquals(1_350_000L, r.drafts.single().amountPaise) // the net, not the gross
        vm.confirmSave()
        waitUntil("saved") { vm.entries.value.isNotEmpty() }
        val e = vm.entries.value.single()
        assertEquals("photo:SALARY", e.source)
        assertEquals(f.path, e.attachment)
        waitUntil("listed") { vm.photos.value.any { it.entry?.id == e.id } }
        // Deleted, then the undo time ends: the photo goes too.
        vm.delete(e.id)
        waitUntil("deleted") { vm.entries.value.isEmpty() }
        vm.forgetDeleted()
        waitUntil("photo gone") { !f.exists() }
    }

    @Test fun unreadableLoanPaperAsksForTheAmountAndIsNotKeptIfDismissed() {
        val f = take(PhotoKind.LOAN, "")
        waitUntil("review") { vm.capture.value is Capture.Review }
        val r = vm.capture.value as Capture.Review
        assertEquals(EntryType.ADVANCE, r.drafts.single().type)
        assertEquals(0L, r.drafts.single().amountPaise)
        assertFalse("nothing to save until the amount is typed", r.canSave)
        vm.updateAmount(0, "2000")
        assertTrue((vm.capture.value as Capture.Review).canSave)
        vm.dismiss()
        waitUntil("photo of a line not saved is gone") { !f.exists() }
        assertTrue(vm.entries.value.isEmpty())
    }

    @Test fun bankStatementIsKeptAsAPaperAndCanBeDeleted() {
        take(PhotoKind.BANK, "State Bank of India\nStatement")
        waitUntil("kept") { vm.photos.value.any { it.kind == PhotoKind.BANK } }
        assertTrue(vm.entries.value.isEmpty())
        assertNull(vm.photos.value.single().entry)
        val paper = vm.photos.value.single()
        assertTrue(File(paper.path).name.startsWith("paper-BANK-"))
        vm.deletePaper(paper.path)
        waitUntil("deleted") { vm.photos.value.isEmpty() && !File(paper.path).exists() }
    }

    @Test fun eraseEverythingTakesThePhotosToo() {
        take(PhotoKind.OTHER, "")
        waitUntil("kept") { vm.photos.value.isNotEmpty() }
        vm.eraseAll()
        waitUntil("all gone") { dir.listFiles().orEmpty().isEmpty() && vm.photos.value.isEmpty() }
    }

    @Test fun screens() {
        val s = Strings(Lang.HI)
        rule.activity.setContent { HisaabTheme(Lang.HI) { PhotoKindDialog(s, onDismiss = {}) {} } }
        rule.onNodeWithText(s.photoKindName(PhotoKind.SALARY)).assertExists()
        rule.onRoot().captureRoboImage("build/screens/40-photo-kind.png")

        take(PhotoKind.BANK, "", tint = Color.rgb(230, 240, 255))
        take(PhotoKind.SALARY, "Net Pay 9,800", tint = Color.rgb(255, 248, 225))
        waitUntil("review") { vm.capture.value is Capture.Review }
        vm.confirmSave()
        waitUntil("saved") { vm.entries.value.isNotEmpty() }
        take(PhotoKind.OTHER, "", tint = Color.rgb(235, 245, 235))
        waitUntil("three photos") { vm.photos.value.size == 3 }
        rule.activity.setContent { HisaabTheme(Lang.HI) { PhotosScreen(vm, s, onBack = {}, onTakePhoto = {}) } }
        rule.waitUntil(10_000) { shadowOf(android.os.Looper.getMainLooper()).idle(); runCatching { rule.onNodeWithText(s.photoKindName(PhotoKind.BANK)).assertExists() }.isSuccess }
        Thread.sleep(300); shadowOf(android.os.Looper.getMainLooper()).idle(); rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/41-photos.png")
    }
}
