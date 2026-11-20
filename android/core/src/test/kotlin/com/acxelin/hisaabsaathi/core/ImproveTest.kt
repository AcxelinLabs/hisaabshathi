package com.acxelin.hisaabsaathi.core

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.security.MessageDigest

class ImproveTest {
    private fun sample(id: String, at: Long, audio: Boolean) = Sample(
        id = id, createdAtMs = at, lang = "hi", source = if (audio) "voice" else "typed", heard = "इस हफ्ते कितना कमाया",
        understood = "ASK WEEK EARNED", reply = "इस हफ्ते आपने 600 रुपये कमाए", outcome = Improve.Outcome.ANSWERED,
        region = "Odisha", appVersion = "0.8.0", engine = if (audio) "pack" else "", hasAudio = audio,
    )

    @Test fun describesWhatWasUnderstood() {
        assertEquals("ASK WEEK EARNED", Improve.understood(Understand.of("how much did I earn this week")))
        assertEquals("TALK NAME", Improve.understood(Understand.of("what is your name")))
        assertEquals("ENTRY ADVANCE 300", Improve.understood(Understand.of("took 300 rupees advance")))
        assertEquals("NONE", Improve.understood(Understood.Unknown("tell me a joke")))
    }

    @Test fun phoneAadhaarBankAndUpiAreNeverKept() {
        for (t in listOf(
            "मेरा नंबर 98765 43210 है", "call 9876543210", "आधार 1234 5678 9012", "account 123-456-789-012",
            "९८७६५४३२१०", "ପଇସା ramesh@okicici କୁ ପଠାନ୍ତୁ", "mail me at a.b@example.com",
        )) assertTrue(t, Improve.sensitive(t))
        for (t in listOf(
            "आज 500 रुपये कमाए", "आज आठ घंटे काम किया 120 पीस 5 रुपये रेट 300 एडवांस लिया", "इस हफ्ते कितना कमाया",
            "1,20,000 total this year", "20 11 2026 ko 600 mile", "ఈ వారం 2500 సంపాదించాను",
        )) assertFalse(t, Improve.sensitive(t))
    }

    @Test fun idsAreRandomHex() {
        val ids = List(200) { Improve.newId() }
        assertEquals(200, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[0-9a-f]{16}")) })
    }

    @Test fun batchHoldsSamplesOldestFirstAndTheirClips() {
        val wav = VoiceSamples.wav(ShortArray(8000) { (it % 50).toShort() })
        val zip = Improve.batch(listOf(sample("bbbb", 2, audio = true), sample("aaaa", 1, audio = false)), { if (it == "bbbb") wav else null }, "0.8.0", 99)
        val (samples, audio) = Improve.readBatch(zip)
        assertEquals(listOf("aaaa", "bbbb"), samples.map { it.id })
        assertEquals("इस हफ्ते कितना कमाया", samples[0].heard)
        assertEquals(setOf("bbbb"), audio.keys)
        assertArrayEquals(wav, audio["bbbb"])
    }

    @Test fun oldCorrectionsBecomeSamplesWithoutClips() {
        val s = Improve.fromCorrection(Correction(5, "or", "voice", "heard", "WORK 6", null), "Odisha", "0.8.0")
        assertEquals(Improve.Outcome.REJECTED, s.outcome)
        assertEquals("ENTRY WORK 6", s.understood)
        assertFalse(s.hasAudio)
        assertEquals(Improve.Outcome.CORRECTED, Improve.fromCorrection(Correction(5, "or", "typed", "h", "WORK 6", "WORK 600"), "", "").outcome)
    }

    @Test fun onlyHttpsOrThisComputer() {
        assertTrue(ShareClient.safe("https://improve.example.org"))
        assertTrue(ShareClient.safe("http://127.0.0.1:8080"))
        assertFalse(ShareClient.safe("http://improve.example.org"))
        assertFalse(ShareClient.safe("ftp://improve.example.org"))
        assertThrows(IllegalArgumentException::class.java) { ShareClient("http://improve.example.org") }
    }

    /** The whole protocol against a stand-in service on this computer. */
    @Test fun registersUploadsAndDeletes() {
        val stored = HashMap<String, ByteArray>()
        var completed = ""
        var deleted = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base = "http://127.0.0.1:${server.address.port}"
        fun reply(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
            val b = body.toByteArray(); ex.sendResponseHeaders(code, if (b.isEmpty()) -1 else b.size.toLong()); if (b.isNotEmpty()) ex.responseBody.use { it.write(b) }; ex.close()
        }
        server.createContext("/v1/installs") { ex ->
            when (ex.requestMethod) {
                "POST" -> reply(ex, 201, """{"install_id":"inst1","token":"tok1"}""")
                "DELETE" -> {
                    if (ex.requestHeaders.getFirst("Authorization") != "Bearer tok1") return@createContext reply(ex, 401, "{}")
                    deleted = ex.requestURI.path.substringAfterLast('/'); reply(ex, 202, """{"deleted":1}""")
                }
            }
        }
        server.createContext("/v1/batches") { ex ->
            if (ex.requestHeaders.getFirst("Authorization") != "Bearer tok1") return@createContext reply(ex, 401, "{}")
            if (ex.requestURI.path.endsWith("/complete")) { completed = ex.requestURI.path.split('/')[3]; reply(ex, 200, """{"ok":true}""") }
            else { ex.requestBody.readBytes(); reply(ex, 200, """{"batch_id":"b1","upload_url":"$base/blob/b1.zip","headers":{"x-ms-blob-type":"BlockBlob"}}""") }
        }
        server.createContext("/blob/") { ex ->
            if (ex.requestHeaders.getFirst("x-ms-blob-type") != "BlockBlob") return@createContext reply(ex, 400, "")
            stored[ex.requestURI.path] = ex.requestBody.readBytes(); reply(ex, 201, "")
        }
        server.start()
        try {
            val client = ShareClient(base)
            val install = client.register("0.8.0")
            assertEquals(ShareClient.Install("inst1", "tok1"), install)
            val zip = Improve.batch(listOf(sample("aaaa", 1, false)), { null }, "0.8.0", 1)
            client.upload(install.token, zip, 1)
            assertArrayEquals(zip, stored["/blob/b1.zip"])
            assertEquals("b1", completed)
            val refused = assertThrows(ShareClient.Refused::class.java) { client.upload("wrong", zip, 1) }
            assertEquals(401, refused.code)
            client.deleteInstall("inst1", "tok1")
            assertEquals("inst1", deleted)
            assertEquals(64, MessageDigest.getInstance("SHA-256").digest(zip).size * 2)
        } finally {
            server.stop(0)
        }
    }
}
