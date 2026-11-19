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

class QuestionListTest {
    @Test fun csvWithHeaderLanguagesAndQuotes() {
        val csv = "\uFEFFid,lang,text,english\nq1,hi,\"भाई, कितना कमाया\",how much did I earn\nq2,,today's total,\n"
        val list = VoiceSamples.parseQuestionList(csv, "My list.csv", "my-list")
        assertEquals(listOf("q1", "q2"), list.map { it.id })
        assertEquals("भाई, कितना कमाया", list[0].text); assertEquals("hi", list[0].lang); assertEquals("", list[1].lang)
        assertEquals(2, VoiceSamples.promptsFor(list, "hi").size) // no language: suits every language
        assertEquals(1, VoiceSamples.promptsFor(list, "ta").size)
    }

    @Test fun plainTextAndCsvWithoutHeader() {
        assertEquals(listOf("one", "two"), VoiceSamples.parseQuestionList("one\n\n two \n", "q.txt", "s").map { it.text })
        val noHeader = VoiceSamples.parseQuestionList("आज कितना,x\nकल कितना,y\n", "q.csv", "s")
        assertEquals(listOf("आज कितना", "कल कितना"), noHeader.map { it.text })
        assertEquals(listOf("s_001", "s_002"), noHeader.map { it.id })
    }

    @Test fun jsonLinesAndUnsafeIds() {
        val list = VoiceSamples.parseQuestionList("{\"id\":\"a/../b\",\"text\":\"hi\"}\n{\"id\":\"a/../b\",\"text\":\"again\"}\nnot json\n", "x.jsonl", "s")
        assertEquals(listOf("ab", "abx"), list.map { it.id }) // never a path, never a duplicate
    }

    @Test fun randomPrefersLeastRecorded() {
        val prompts = (1..10).map { VoicePrompt("hi_%03d".format(it), "hi", "t$it") }
        val recorded = prompts.take(7).associate { it.id to 3 }
        val batch = VoiceSamples.randomBatch(prompts, recorded, 3, kotlin.random.Random(1))
        assertEquals(setOf("hi_008", "hi_009", "hi_010"), batch.map { it.id }.toSet())
    }

    @Test fun idsBeyondNineHundredNinetyNineSortByNumber() {
        val p = listOf("hi_1000", "hi_101", "hi_009").map { VoicePrompt(it, "hi", it) }
        assertEquals(listOf("hi_009", "hi_101", "hi_1000"), VoiceSamples.promptsFor(p, "hi").map { it.id })
    }
}

class RecordingQualityTest {
    private fun tone(seconds: Double, amplitude: Double) =
        ShortArray((16_000 * seconds).toInt()) { (amplitude * 32767 * kotlin.math.sin(it * 0.07)).toInt().toShort() }

    @Test fun normalSpeechLevelIsGood() = assertEquals(VoiceSamples.Quality.GOOD, VoiceSamples.quality(tone(1.5, 0.2)))
    @Test fun nothingHeard() = assertEquals(VoiceSamples.Quality.SILENT, VoiceSamples.quality(tone(1.5, 0.002)))
    @Test fun tooQuiet() = assertEquals(VoiceSamples.Quality.QUIET, VoiceSamples.quality(tone(1.5, 0.012)))
    @Test fun tooShort() = assertEquals(VoiceSamples.Quality.SHORT, VoiceSamples.quality(tone(0.3, 0.2)))

    @Test fun distortedIsLoud() {
        val pcm = tone(1.5, 1.6).map { it.toInt().coerceIn(-32768, 32767).toShort() }.toShortArray() // clipped sine
        val clipped = ShortArray(pcm.size) { (if (it % 200 == 0) 32767 else pcm[it].toInt() / 2).toShort() }
        assertEquals(VoiceSamples.Quality.LOUD, VoiceSamples.quality(clipped))
    }

    @Test fun briefWordInSilenceIsQuiet() {
        val pcm = ShortArray(32_000) // 2 s of silence with 0.1 s of speech
        for (i in 8_000 until 9_600) pcm[i] = (6000 * kotlin.math.sin(i * 0.07)).toInt().toShort()
        assertEquals(VoiceSamples.Quality.QUIET, VoiceSamples.quality(pcm))
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
