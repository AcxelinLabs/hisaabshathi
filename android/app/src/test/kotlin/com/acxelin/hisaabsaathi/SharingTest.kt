package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.Improve
import com.acxelin.hisaabsaathi.core.Sample
import com.acxelin.hisaabsaathi.core.Seal
import com.acxelin.hisaabsaathi.core.ShareClient
import com.acxelin.hisaabsaathi.core.ShareServer
import com.acxelin.hisaabsaathi.ui.AppViewModel
import com.acxelin.hisaabsaathi.ui.Capture
import com.acxelin.hisaabsaathi.work.Uploader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

/** "Help improve HisaabSaathi": what is kept, and how it is sent and deleted. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SharingTest {
    private val app = ApplicationProvider.getApplicationContext<HisaabApp>()

    private class FakeServer : ShareServer {
        var registered = 0
        val batches = mutableListOf<Pair<String, ByteArray>>()
        val deleted = mutableListOf<String>()
        var fail: Exception? = null
        override fun register(appVersion: String) = ShareClient.Install("inst${++registered}", "tok$registered")
        override fun upload(token: String, zip: ByteArray, count: Int) { fail?.let { throw it }; batches += token to zip }
        override fun deleteInstall(installId: String, token: String) { fail?.let { throw it }; deleted += installId }
    }

    private val server = FakeServer()
    private val uploader = Uploader(app.settings, app.samples, server, Seal(keys.public.encoded), "test")

    companion object {
        /** A throwaway team key pair, made once for these tests. */
        val keys: java.security.KeyPair by lazy { java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair() }

        /** What the pipeline does with a batch: open the seal (server/hisaab_improve/crypto.py). */
        fun open(sealed: ByteArray): ByteArray {
            val b = java.nio.ByteBuffer.wrap(sealed)
            check(String(ByteArray(4).also(b::get)) == "HSE1") { "sent unsealed" }
            b.position(12)
            val wrapped = ByteArray(b.short.toInt() and 0xffff).also(b::get)
            val nonce = ByteArray(12).also(b::get)
            val header = sealed.copyOf(b.position())
            val aes = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(javax.crypto.Cipher.DECRYPT_MODE, keys.private, Seal.OAEP) }.doFinal(wrapped)
            val gcm = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(aes, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce)); updateAAD(header)
            }
            return gcm.doFinal(sealed, header.size, sealed.size - header.size)
        }
    }

    private fun sample(i: Int) = Sample(
        Improve.newId(), 1000L + i, "hi", "typed", "इस हफ्ते कितना कमाया $i", "ASK WEEK EARNED", "", Improve.Outcome.ANSWERED,
    )

    private fun queued() = runBlocking { app.samples.oldest(1000) }

    @Before fun freshState() = runBlocking {
        // Settings live in a file that can outlast one test; start each from nothing shared.
        app.settings.forgetInstall()
        app.settings.pendingDeletes().forEach { app.settings.deleteDone(it.first) }
        app.samples.clear()
    }

    @Test fun sendsOnlyAfterYes() = runBlocking {
        app.settings.setShare(false)
        repeat(3) { app.samples.add(sample(it), null) }
        assertTrue(uploader.run())
        assertTrue(server.batches.isEmpty())

        app.settings.setShare(true)
        val before = app.settings.flow.first().sharedCount
        assertTrue(uploader.run())
        assertEquals(1, server.batches.size)
        assertEquals(3, Improve.readBatch(open(server.batches.single().second)).first.size)
        assertTrue(queued().isEmpty())
        assertEquals(before + 3, app.settings.flow.first().sharedCount)
    }

    @Test fun manyGoInBatchesUnderOneInstall() = runBlocking {
        app.settings.setShare(true)
        repeat(30) { app.samples.add(sample(it), null) }
        assertTrue(uploader.run())
        assertEquals(listOf(25, 5), server.batches.map { Improve.readBatch(open(it.second)).first.size })
        assertEquals(1, server.registered)
        assertEquals(setOf("tok1"), server.batches.map { it.first }.toSet())
    }

    @Test fun offlineKeepsEverything() = runBlocking {
        app.settings.setShare(true)
        repeat(3) { app.samples.add(sample(it), null) }
        server.fail = IOException("no internet")
        assertFalse(uploader.run())
        assertEquals(3, queued().size)
    }

    @Test fun forgottenTokenRegistersAgain() = runBlocking {
        app.settings.setShare(true)
        app.samples.add(sample(1), null)
        server.fail = ShareClient.Refused(401, "unknown token")
        assertFalse(uploader.run())
        assertNull(app.settings.install())
        server.fail = null
        assertTrue(uploader.run())
        assertEquals(2, server.registered)
        assertTrue(queued().isEmpty())
    }

    @Test fun deleteWhatIShared() = runBlocking {
        app.settings.setShare(true)
        repeat(3) { app.samples.add(sample(it), null) }
        assertTrue(uploader.run())
        app.samples.add(sample(9), null)
        app.samples.clear()
        app.settings.requestDelete()
        assertEquals(0, app.settings.flow.first().sharedCount)
        server.fail = IOException("offline")
        assertFalse(uploader.run()) // waits for the internet, keeps the request
        assertEquals(1, app.settings.pendingDeletes().size)
        server.fail = null
        assertTrue(uploader.run())
        assertEquals(listOf("inst1"), server.deleted)
        assertTrue(app.settings.pendingDeletes().isEmpty())
        // Sharing again later starts a new, unrelated install.
        app.samples.add(sample(10), null)
        assertTrue(uploader.run())
        assertEquals("tok2", server.batches.last().first)
    }

    @Test fun withoutAServiceSamplesWait() = runBlocking {
        app.settings.setShare(true)
        app.samples.add(sample(1), null)
        assertTrue(Uploader(app.settings, app.samples, null, Seal(keys.public.encoded), "test").run())
        assertEquals(1, queued().size)
        // A service but no team key: still nothing leaves the phone.
        assertTrue(Uploader(app.settings, app.samples, server, null, "test").run())
        assertTrue(server.batches.isEmpty() && server.registered == 0)
        assertEquals(1, queued().size)
    }

    // ---- what the app keeps while the worker uses it

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun waitUntil(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!ok()) {
            idle(); Thread.sleep(20)
            check(System.currentTimeMillis() < end) { "never: $what" }
        }
    }

    private fun viewModel(share: Boolean): AppViewModel {
        runBlocking { app.settings.setShare(share) }
        val vm = AppViewModel(app)
        waitUntil("settings") { vm.settings.value?.share == share }
        return vm
    }

    @Test fun nothingIsKeptWithoutYes() {
        val vm = viewModel(share = false)
        vm.submitText("इस हफ्ते कितना कमाया")
        waitUntil("answer") { vm.capture.value is Capture.Message }
        Thread.sleep(300); idle()
        assertTrue(queued().isEmpty())
    }

    @Test fun keepsQuestionsAndHowEntriesEnded() {
        val vm = viewModel(share = true)
        vm.submitText("इस हफ्ते कितना कमाया")
        waitUntil("answer") { vm.capture.value is Capture.Message }
        waitUntil("question kept") { queued().size == 1 }
        val q = queued().single()
        assertEquals("ASK WEEK EARNED", q.understood)
        assertEquals(Improve.Outcome.ANSWERED, q.outcome)
        assertEquals("typed", q.source)
        assertTrue(q.reply, q.reply.isNotEmpty())
        assertFalse(q.hasAudio)
        assertEquals(Improve.CONSENT_VERSION, q.consent)

        vm.dismiss()
        vm.submitText("सब्जी पर अस्सी रुपये खर्च किए")
        waitUntil("review") { vm.capture.value is Capture.Review }
        vm.updateAmount(0, "90")
        vm.confirmSave()
        waitUntil("entry kept") { queued().size == 2 }
        val e = queued().last()
        assertEquals(Improve.Outcome.CORRECTED, e.outcome)
        assertEquals("ENTRY EXPENSE 80 FOOD", e.understood)
        assertEquals("EXPENSE 90 FOOD", e.kept)

        // "Wrong, say again" is kept once, as rejected (not also as closed).
        vm.dismiss()
        vm.submitText("तीन सौ एडवांस लिया")
        waitUntil("review") { vm.capture.value is Capture.Review }
        vm.retry()
        waitUntil("rejection kept") { queued().size == 3 }
        Thread.sleep(300); idle()
        assertEquals(3, queued().size)
        assertEquals(Improve.Outcome.REJECTED, queued().last().outcome)
    }

    @Test fun numbersThatIdentifySomeoneAreNeverKept() {
        val vm = viewModel(share = true)
        vm.submitText("मेरा मोबाइल नंबर 98765 43210 है")
        waitUntil("answer") { vm.capture.value is Capture.Message || vm.capture.value is Capture.Review }
        Thread.sleep(300); idle()
        assertTrue(queued().isEmpty())
    }

    @Test fun sayingNoLaterDeletesWhatWaits() {
        val vm = viewModel(share = true)
        vm.submitText("इस हफ्ते कितना कमाया")
        waitUntil("kept") { queued().size == 1 }
        vm.setShare(false)
        waitUntil("deleted") { queued().isEmpty() }
        assertEquals(false, runBlocking { app.settings.flow.first().share })
    }
}
