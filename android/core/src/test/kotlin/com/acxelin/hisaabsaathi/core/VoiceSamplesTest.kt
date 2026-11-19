package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceSamplesTest {
    @Test fun wavHeaderDescribes16kMono16Bit() {
        val wav = VoiceSamples.wav(ShortArray(16_000) { (it % 100).toShort() })
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4)); assertEquals("data", String(wav, 36, 4))
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(16_000, b.getInt(24)) // sample rate
        assertEquals(16, b.getShort(34).toInt()) // bits
        assertEquals(32_000, b.getInt(40)) // data bytes
        assertEquals(44 + 32_000, wav.size)
    }

    @Test fun csvRoundTripsSentencesWithCommasAndQuotes() {
        val values = listOf("hi_001__SABCD__1.wav", "भाई, \"इस हफ्ते\" कितना कमाया", "plain")
        assertEquals(values, VoiceSamples.parseCsvLine(VoiceSamples.csvLine(values)))
    }

    @Test fun speakersTogetherCoverEveryQuestion() {
        val prompts = (1..300).map { VoicePrompt("hi_%03d".format(it), "hi", "t$it", "e", "ASK WEEK ALL", "a") }
        val first = VoiceSamples.nextBatch(prompts, 0, 50)
        val second = VoiceSamples.nextBatch(prompts, 50, 50)
        assertEquals("hi_001", first.first().id); assertEquals("hi_051", second.first().id)
        val wrap = VoiceSamples.nextBatch(prompts, 290, 25)
        assertEquals("hi_291", wrap.first().id); assertEquals("hi_015", wrap.last().id)
    }

    @Test fun speakerCodesHaveNoLookAlikeLetters() {
        repeat(200) { val c = VoiceSamples.speakerCode(); assertTrue(c, c.matches(Regex("S[A-HJ-NP-Z2-9]{4}"))) }
    }
}

class XlsxTest {
    @Test fun workbookHasTheSheetAndEscapedText() {
        val bytes = Xlsx.write("Recordings", listOf("file_name", "prompt_text"), listOf(listOf("hi_001.wav", "भाई <कितना> & \"कमाया\"")))
        val parts = HashMap<String, String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; parts[e.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(parts.keys.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml")))
        val sheet = parts["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet, "भाई &lt;कितना&gt; &amp; &quot;कमाया&quot;" in sheet)
        assertTrue(sheet, """<c r="B2" t="inlineStr">""" in sheet)
        assertEquals("AA", Xlsx.column(26)); assertEquals("Z", Xlsx.column(25))
    }
}
