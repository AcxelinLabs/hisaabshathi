package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * The 1,000 questions per language of tools/make_voice_questions.py, run through the app's
 * understanding as typed text. Shows which misses are the app's own, before speech recognition
 * adds its errors. With voice_heard.jsonl ({"id", "heard"}: what the speech model wrote for a
 * spoken question) it scores real speech instead.
 *
 * Writes build/voice-questions-report.txt; nothing is asserted (a measurement, not a gate).
 */
class VoiceQuestionsTest {
    private fun rows(name: String) = javaClass.getResource(name)?.readText()?.lines()?.filter { it.isNotBlank() }
        ?.map { Json.parseToJsonElement(it).jsonObject }.orEmpty()

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** What the app does with a sentence, in the dataset's terms. */
    private fun got(text: String): String = when (val u = Understand.of(text)) {
        is Understood.Ask -> "ASK ${u.query.period.let { if (it == QueryPeriod.DAY) "DAY" else it.name }} ${u.query.topic}"
        is Understood.Talk -> "TALK ${u.chat}"
        is Understood.Entries -> "ENTRY"
        is Understood.Unknown -> "NONE"
    }

    private fun verdict(expect: String, got: String, supported: Boolean): String {
        val e = expect.split(" "); val g = got.split(" ")
        if (!supported) return "not answerable yet; app gave $got"
        return when {
            e[0] == "ASK" && g[0] == "ASK" -> {
                val periodOk = e[1] == "ANY" || e[1] == g[1]
                val topicOk = e[2] == g[2]
                when {
                    periodOk && topicOk -> "right"
                    topicOk -> "wrong period"
                    periodOk -> "wrong topic"
                    else -> "wrong period and topic"
                }
            }
            e[0] == "ASK" && g[0] == "ENTRY" -> "taken as an entry"
            e[0] == "ASK" -> "not understood"
            expect == got -> "right"
            e[0] == "NONE" -> "wrongly answered as $got"
            else -> "not understood"
        }
    }

    /**
     * One line per recording: {"rec": file name, "id": question id, "heard": what the speech
     * model wrote}. Several speakers may say one question.
     */
    private data class Take(val rec: String, val id: String, val text: String)

    @Test fun score() {
        val questions = rows("/voice_questions.jsonl").associateBy { str(it, "id")!! }
        val heard = rows("/voice_heard.jsonl").mapNotNull { r ->
            val id = str(r, "id") ?: return@mapNotNull null
            Take(str(r, "rec") ?: id, id, str(r, "heard") ?: "")
        }
        val takes = heard.ifEmpty { questions.values.map { Take(str(it, "id")!!, str(it, "id")!!, str(it, "text")!!) } }
        val report = StringBuilder()
        val misses = StringBuilder()
        val source = if (heard.isEmpty()) "typed text" else "speech model output of ${heard.size} recordings (voice_heard.jsonl)"
        report.appendLine("Voice question set, through the app's understanding: $source")
        report.appendLine("Right = the app gives the expected answer (any period when none was said).")
        report.appendLine("Questions 1-200: everyday phrasings. 201-300: new phrasings and hard questions; some ask what the app cannot answer yet. 301-1000: more everyday phrasings.")
        report.appendLine()
        for (lang in listOf("hi", "or", "te", "kn", "ta", "en")) {
            for ((part, range) in listOf("1-200" to 1..200, "201-300" to 201..300, "301-1000" to 301..1000)) {
                val counts = sortedMapOf<String, Int>()
                var n = 0; var right = 0; var notYet = 0
                for (t in takes) {
                    val q = questions[t.id] ?: continue
                    if (str(q, "lang") != lang || VoiceSamples.number(t.id) !in range) continue
                    val supported = q["supported"]!!.jsonPrimitive.boolean
                    val expect = str(q, "expect")!!
                    val g = got(t.text)
                    val v = verdict(expect, g, supported)
                    if (!supported) { notYet++; continue }
                    n++
                    counts[v] = (counts[v] ?: 0) + 1
                    if (v == "right") right++ else misses.appendLine("${t.rec}\t$v\texpected $expect\tgot $g\t${t.text}\t(${str(q, "english")})")
                }
                if (n == 0 && notYet == 0) continue
                report.appendLine("$lang $part: $right / $n answerable right (${if (n > 0) right * 100 / n else 0}%)" + if (notYet > 0) "; $notYet not answerable yet" else "")
                counts.filterKeys { it != "right" }.forEach { (k, v) -> report.appendLine("    $k: $v") }
            }
        }
        File("build").mkdirs()
        File("build/voice-questions-report.txt").writeText(report.toString())
        File("build/voice-questions-misses.tsv").writeText(misses.toString())
        println(report)
    }
}
