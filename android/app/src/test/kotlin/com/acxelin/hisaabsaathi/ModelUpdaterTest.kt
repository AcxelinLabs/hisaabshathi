package com.acxelin.hisaabsaathi

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.acxelin.hisaabsaathi.core.ModelManifest
import com.acxelin.hisaabsaathi.core.ModelRelease
import com.acxelin.hisaabsaathi.core.ModelUpdates
import com.acxelin.hisaabsaathi.models.SlmModel
import com.acxelin.hisaabsaathi.work.ModelUpdater
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

/**
 * A newer model, named by the team's signed manifest: downloaded next to the old one, checked,
 * tried, switched to, and the old one deleted. Anything wrong leaves the phone as it was.
 * The network and the model are stood in for; the files and the rules are real.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ModelUpdaterTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private lateinit var dir: File
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.genKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
    private val newModel = ByteArray(1_200_000) { (it * 31 % 251).toByte() }
    private val url = "https://example.invalid/models.json"
    private var tried = 0
    private var switchedTo: ModelRelease? = null

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun release(version: Int = 2, bytes: ByteArray = newModel, minApp: Int = 0): ModelRelease {
        val file = "hisaabsaathi-qwen3-ft-v$version-q8.litertlm"
        return ModelRelease(
            SlmModel.ID, version, file,
            "https://huggingface.co/AcxelinLabs/hisaabsaathi-model/resolve/0123456789abcdef0123456789abcdef01234567/$file",
            bytes.size.toLong(), sha(bytes), minApp,
        )
    }

    private fun signed(r: ModelRelease, signer: java.security.PrivateKey = keys.private): Pair<ByteArray, ByteArray> {
        val manifest = ModelUpdates.encodeManifest(ModelManifest(models = listOf(r))).toByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(signer); update(manifest); sign() }
        return manifest to Base64.getEncoder().encode(sig)
    }

    private fun updater(
        manifest: Pair<ByteArray, ByteArray>?,
        served: ByteArray = newModel,
        installed: Boolean = true,
        works: Boolean? = true,
    ) = ModelUpdater(
        dir = dir, publicKey = publicKey, manifestUrl = url, appVersion = 40,
        prefs = app.getSharedPreferences("model_updates_test", 0),
        installed = { installed },
        fetch = { u -> when (u) { url -> manifest?.first; "$url.sig" -> manifest?.second; else -> null } },
        download = { _, target, bytes -> File(target.path + ".part").writeBytes(served); File(target.path + ".part").renameTo(target); target.length() == bytes },
        tryModel = { tried++; works },
        switched = { switchedTo = it },
    )

    @Before fun setUp() {
        dir = File(app.filesDir, "models-test/slm").apply { deleteRecursively(); mkdirs() }
        File(dir, SlmModel.builtIn.file).writeBytes(ByteArray(10)) // stands for the old model
        File(dir, "half-of-an-abandoned-version.litertlm.part").writeBytes(ByteArray(10))
        app.getSharedPreferences("model_updates_test", 0).edit().clear().commit()
        SlmModel.load(dir) // nothing saved yet: the built-in v1
    }

    @After fun tearDown() { dir.deleteRecursively(); SlmModel.load(dir); SlmModel.pending = null }

    @Test fun aNewerModelIsCheckedTriedSwitchedToAndTheOldOneDeleted() = runBlocking {
        assertTrue(updater(signed(release())).run())
        assertEquals(2, SlmModel.current.version)
        assertEquals(2, switchedTo?.version)
        assertEquals(1, tried)
        // Only the new model and the record of it remain: the old one and leftovers are gone.
        assertEquals(setOf(release().file, SlmModel.RECORD), dir.list()!!.toSet())
        // Remembered across a restart.
        SlmModel.load(dir)
        assertEquals(2, SlmModel.current.version)
    }

    @Test fun aManifestNotSignedByTheTeamIsIgnored() = runBlocking {
        val stranger = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.genKeyPair()
        assertTrue(updater(signed(release(), stranger.private)).run())
        assertEquals(1, SlmModel.current.version)
        assertEquals(0, tried)
        assertTrue(File(dir, SlmModel.builtIn.file).exists())
    }

    @Test fun aDownloadThatDoesNotMatchItsFingerprintIsThrownAway() = runBlocking {
        val corrupted = newModel.copyOf().also { it[5] = (it[5] + 1).toByte() }
        assertTrue(updater(signed(release()), served = corrupted).run())
        assertEquals(1, SlmModel.current.version)
        assertEquals(0, tried)
        assertFalse(File(dir, release().file).exists())
        assertTrue(File(dir, SlmModel.builtIn.file).exists())
    }

    @Test fun aModelThatDoesNotWorkHereIsNotUsedAndGivenUpAfterThreeTries() = runBlocking {
        repeat(3) { assertTrue(updater(signed(release()), works = false).run()) }
        assertEquals(1, SlmModel.current.version)
        assertEquals(3, tried)
        assertFalse("given up: its file is deleted", File(dir, release().file).exists())
        assertTrue(File(dir, SlmModel.builtIn.file).exists())
        // Not tried again.
        assertTrue(updater(signed(release()), works = true).run())
        assertEquals(1, SlmModel.current.version)
    }

    @Test fun tooBusyToTryMeansTryAgainLaterKeepingTheDownload() = runBlocking {
        assertFalse(updater(signed(release()), works = null).run())
        assertEquals(1, SlmModel.current.version)
        assertTrue(File(dir, release().file).exists())
        assertTrue(updater(signed(release()), works = true).run())
        assertEquals(2, SlmModel.current.version)
    }

    @Test fun anOldOrTooNewManifestChangesNothing() = runBlocking {
        assertTrue(updater(signed(release(version = 1))).run()) // not newer than the one in use
        assertTrue(updater(signed(release(minApp = 999))).run()) // needs a newer app
        assertEquals(1, SlmModel.current.version)
        assertEquals(0, tried)
    }

    @Test fun aPhoneWithoutTheSmartModelJustRemembersTheNewVersion() = runBlocking {
        assertTrue(updater(signed(release()), installed = false).run())
        assertEquals(2, SlmModel.current.version)
        assertEquals(0, tried)
        assertFalse("nothing downloaded", File(dir, release().file).exists())
    }

    @Test fun offlineMeansTryAgainLater() = runBlocking {
        assertFalse(updater(null).run())
        assertEquals(1, SlmModel.current.version)
    }

    @Test fun anAcceptableReleaseIsWhatTheToolWrites() {
        assertTrue(ModelUpdates.acceptable(release()))
    }
}
