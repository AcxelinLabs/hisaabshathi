package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model manifest: signed by the team (tools/models/model_manifest.py), checked on the phone. */
class ModelUpdatesTest {
    private fun res(name: String) = javaClass.getResource("/model_manifest/$name")!!.readBytes()
    private val manifest = res("models.json")
    private val sig = String(res("models.json.sig"))
    private val key = String(res("test_model_key.pub.b64"))

    private fun release(version: Int, minApp: Int = 0, file: String = "hisaabsaathi-qwen3-ft-v$version-q8.litertlm") = ModelRelease(
        "slm-hisaab", version, file,
        "https://huggingface.co/AcxelinLabs/hisaabsaathi-model/resolve/0123456789abcdef0123456789abcdef01234567/$file",
        600_000_000, "a".repeat(64), minApp,
    )

    @Test fun aManifestSignedByThePythonToolIsAccepted() {
        assertTrue(ModelUpdates.verify(manifest, sig, key))
        val m = ModelUpdates.parse(String(manifest))!!
        assertEquals(2, m.models.single().version)
        assertTrue(ModelUpdates.acceptable(m.models.single()))
    }

    @Test fun anythingChangedIsRefused() {
        val altered = String(manifest).replace("\"version\": 2", "\"version\": 9").toByteArray()
        assertFalse(ModelUpdates.verify(altered, sig, key))
        assertFalse(ModelUpdates.verify(manifest, sig.replaceRange(10, 12, "AA"), key))
        assertFalse(ModelUpdates.verify(manifest, "not base64!", key))
        assertFalse(ModelUpdates.verify(manifest, sig, "")) // no key built in: never trusted
    }

    @Test fun onlyNewerVersionsThisAppCanUse() {
        val m = ModelManifest(models = listOf(release(1), release(2), release(3, minApp = 99)))
        assertEquals(2, ModelUpdates.newer(m, "slm-hisaab", appVersion = 40, current = 1)?.version)
        assertEquals(3, ModelUpdates.newer(m, "slm-hisaab", appVersion = 99, current = 1)?.version)
        // An old (replayed) manifest never moves a phone backwards.
        assertNull(ModelUpdates.newer(m, "slm-hisaab", appVersion = 40, current = 2))
        assertNull(ModelUpdates.newer(m, "other-model", appVersion = 99, current = 0))
    }

    @Test fun onlyPinnedFilesFromTheTeamsRepo() {
        assertTrue(ModelUpdates.acceptable(release(2)))
        assertFalse("unpinned main", ModelUpdates.acceptable(release(2).copy(url = "https://huggingface.co/AcxelinLabs/hisaabsaathi-model/resolve/main/hisaabsaathi-qwen3-ft-v2-q8.litertlm")))
        assertFalse("another repo", ModelUpdates.acceptable(release(2).copy(url = release(2).url.replace("AcxelinLabs", "someone"))))
        assertFalse("path in name", ModelUpdates.acceptable(release(2, file = "../../evil.litertlm")))
        assertFalse("name differs from url", ModelUpdates.acceptable(release(2).copy(file = "other.litertlm")))
        assertFalse("not a hash", ModelUpdates.acceptable(release(2).copy(sha256 = "xyz")))
    }

    @Test fun everythingButTheModelInUseIsCleanedUp() {
        val names = listOf("v1.litertlm", "v2.litertlm", "v2.litertlm.part", "v0.litertlm.part", "current.json", "old.task")
        assertEquals(listOf("v1.litertlm", "v0.litertlm.part", "old.task"),
            ModelUpdates.leftovers(names, current = "v2.litertlm", pending = null, keepAlso = setOf("current.json")))
        // While v2 downloads, v1 stays in use and v2's part is kept.
        assertEquals(listOf("v0.litertlm.part", "old.task"),
            ModelUpdates.leftovers(names, current = "v1.litertlm", pending = "v2.litertlm", keepAlso = setOf("current.json")))
    }

    @Test fun fingerprintOfAFile() {
        val f = java.io.File.createTempFile("model", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ModelUpdates.sha256(f))
    }
}
