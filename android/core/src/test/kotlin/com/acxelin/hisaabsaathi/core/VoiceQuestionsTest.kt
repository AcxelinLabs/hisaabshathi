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
 * The 1,200 questions to be recorded in real voices (tools/make_voice_questions.py), run
 * through the app's understanding as typed text. Shows which misses are the app's own, before
 * speech recognition adds its errors. With recordings, voice_heard.jsonl ({"id", "heard"})
 * scores what the speech model wrote instead.
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
        return when {
            e[0] == "ASK" && g[0] == "ASK" -> {
                val periodOk = e[1] == "ANY" || e[1] == g[1]
                val topicOk = e[2] == g[2]
                when {
                    !supported -> if (topicOk) "unsupported period, answered for ${g[1]}" else "unsupported period, wrong topic"
                    periodOk && topicOk -> "right"
                    topicOk -> "wrong period"
                    periodOk -> "wrong topic"
                    else -> "wrong period and topic"
                }
            }
            e[0] == "ASK" && g[0] == "ENTRY" -> "taken as an entry"
            e[0] == "ASK" -> if (supported) "not understood" else "unsupported period, not understood"
            expect == got -> "right"
            e[0] == "NONE" -> "wrongly answered as $got"
            else -> "not understood"
        }
    }

    @Test fun score() {
        val questions = rows("/voice_questions.jsonl")
        val heard = rows("/voice_heard.jsonl").associate { str(it, "id")!! to str(it, "heard") }
        val report = StringBuilder()
        val misses = StringBuilder()
        val source = if (heard.isEmpty()) "typed text" else "speech model output (voice_heard.jsonl)"
        report.appendLine("Voice question set, through the app's understanding: $source")
        report.appendLine("A question counts as right when the app gives the expected answer (any period when none was said).")
        report.appendLine()
        val langs = listOf("hi", "or", "te", "kn", "ta", "en")
        for (lang in langs) {
            val rs = questions.filter { str(it, "lang") == lang }
            val counts = sortedMapOf<String, Int>()
            var supportedN = 0; var supportedRight = 0
            for (q in rs) {
                val id = str(q, "id")!!
                val text = if (heard.isEmpty()) str(q, "text")!! else heard[id] ?: continue
                val expect = str(q, "expect")!!
                val supported = q["supported"]!!.jsonPrimitive.boolean
                val g = got(text)
                val v = verdict(expect, g, supported)
                counts[v] = (counts[v] ?: 0) + 1
                if (supported) { supportedN++; if (v == "right") supportedRight++ }
                if (v != "right") misses.appendLine("$id\t$v\texpected $expect\tgot $g\t$text\t(${str(q, "english")})")
            }
            report.appendLine("$lang: $supportedRight / $supportedN answerable questions right (${if (supportedN > 0) supportedRight * 100 / supportedN else 0}%)")
            counts.filterKeys { it != "right" }.forEach { (k, v) -> report.appendLine("    $k: $v") }
        }
        File("build").mkdirs()
        File("build/voice-questions-report.txt").writeText(report.toString())
        File("build/voice-questions-misses.tsv").writeText(misses.toString())
        println(report)
    }
}
