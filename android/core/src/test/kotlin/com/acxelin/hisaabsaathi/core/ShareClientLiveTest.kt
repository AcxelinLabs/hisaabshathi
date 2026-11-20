package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The phone's client against the real upload service (server/), when one is running:
 * HISAAB_SHARE_URL=http://127.0.0.1:8099 ./gradlew :core:test --tests '*ShareClientLiveTest*'
 * Skipped otherwise.
 */
class ShareClientLiveTest {
    @Test fun registersUploadsAndDeletes() {
        val url = System.getenv("HISAAB_SHARE_URL").orEmpty()
        assumeTrue("no service running", url.isNotEmpty())
        val client = ShareClient(url)
        val install = client.register("live-test")
        val wav = VoiceSamples.wav(ShortArray(16_000) { (it % 200 - 100).toShort() })
        val samples = listOf(
            Sample(Improve.newId(), 1, "hi", "voice", "इस हफ्ते कितना कमाया", "ASK WEEK EARNED", "इस हफ्ते आपने 600 रुपये कमाए", Improve.Outcome.ANSWERED, hasAudio = true, audioSeconds = 1.0, quality = "good", consent = 1),
            Sample(Improve.newId(), 2, "hi", "typed", "300 एडवांस", "ENTRY ADVANCE 300", "", Improve.Outcome.SAVED, kept = "ADVANCE 300", consent = 1),
        )
        // Sealed with the shared TEST-ONLY key, as the app seals with the team key.
        val seal = Seal.fromBase64(javaClass.getResource("/test_team_key.pub.b64")!!.readText())!!
        val zip = seal.seal(Improve.batch(samples, { if (it == samples[0].id) wav else null }, "live-test", 3))
        client.upload(install.token, zip, samples.size)
        println("LIVE install=${install.installId} bytes=${zip.size}")
        if (System.getenv("HISAAB_LIVE_KEEP") == null) {
            client.deleteInstall(install.installId, install.token)
            assertEquals(401, assertThrows(ShareClient.Refused::class.java) { client.upload(install.token, zip, 2) }.code)
        }
    }
}
