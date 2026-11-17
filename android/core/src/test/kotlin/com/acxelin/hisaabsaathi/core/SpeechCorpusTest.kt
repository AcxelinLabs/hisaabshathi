package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Scores the end-to-end speech test (tools/speech_test.py): spoken sentences in six
 * languages, played through a copy of the app's capture loop and speech models, then
 * through the app's own understanding and reply code here. Writes build/speech-report.txt.
 *
 * Hard requirements (asserted): nothing throws for any transcript in any language, and
 * silence or room noise is never treated as speech. Recognition accuracy is reported, not
 * asserted, because the test voice is synthetic.
 */
class SpeechCorpusTest {
    private val corpusTest = CorpusTest()

    @Test fun speechTranscripts() {
        val res = javaClass.getResource("/speech_transcripts.jsonl") ?: return
        val rows = res.readText().lines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }
        val stats = sortedMapOf<String, IntArray>() // exact, sensible, total
        val silenceProblems = ArrayList<String>()
        val notes = ArrayList<String>()
        for (r in rows) {
            val lang = r["lang"]!!.jsonPrimitive.content
            val scenario = r["scenario"]!!.jsonPrimitive.content
            val heard = (r["heard"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val expected = r["expected"]!!.jsonObject
            if (expected["kind"]!!.jsonPrimitive.content == "silence") {
                if (heard != null) silenceProblems += "$lang $scenario: captured audio from silence"
                continue
            }
            val key = "$lang/$scenario"
            val c = stats.getOrPut(key) { IntArray(3) }
            c[2]++
            if (heard == null) { notes += "[$key] nothing heard: ${r["said"]!!.jsonPrimitive.content}"; continue }

            // The full reply path must never throw, in every app language.
            val u = Understand.of(heard)
            for (l in Lang.entries) {
                val s = Strings(l)
                when (u) {
                    is Understood.Entries -> s.readback(u.extraction.entries)
                    is Understood.Ask -> s.answer(u.query, Totals())
                    is Understood.Talk -> s.chat(u.chat, "17 November 2026")
                    is Understood.Unknown -> s.chatFallback
                }
            }
            val want = corpusTest.expectedFor(expected)
            val got = corpusTest.actualFor(heard)
            val sameKind = want.substringBefore(' ') == got.substringBefore(' ') ||
                (want.first().isUpperCase() && got.first().isUpperCase() && !want.startsWith("ASK") && !want.startsWith("TALK") &&
                    !got.startsWith("ASK") && !got.startsWith("TALK") && got != "UNKNOWN")
            if (want == got) c[0]++
            if (want == got || sameKind) c[1]++
            if (want != got) notes += "[$key] said: ${r["said"]!!.jsonPrimitive.content}\n      heard: $heard\n      want $want\n      got  $got"
        }
        val total = stats.values.sumOf { it[2] }
        val report = buildString {
            appendLine("Speech test: $total utterances; exact ${stats.values.sumOf { it[0] }}; right kind of reply ${stats.values.sumOf { it[1] }}")
            appendLine("Silence/noise clips wrongly treated as speech: ${silenceProblems.size}")
            stats.forEach { (k, v) -> appendLine("  $k: exact ${v[0]}/${v[2]}, right kind ${v[1]}/${v[2]}") }
            notes.forEach { appendLine(it) }
        }
        File("build").mkdirs(); File("build/speech-report.txt").writeText(report)
        assertTrue(silenceProblems.joinToString(), silenceProblems.isEmpty())
    }
}
