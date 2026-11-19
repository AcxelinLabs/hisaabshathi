package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Questions written after the question words were chosen, in phrasings and words not added
 * to them (questions_unseen.jsonl), with everyday non-ledger questions. Never tuned on: it
 * measures how the rules do on new ways of asking. Writes build/unseen-questions-report.txt.
 */
class UnseenQuestionsTest {
    @Test fun score() {
        val rows = javaClass.getResource("/questions_unseen.jsonl")!!.readText().lines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }
        val report = StringBuilder()
        var right = 0; var falseAsk = 0; var negatives = 0
        val by = sortedMapOf<String, IntArray>()
        for (r in rows) {
            val text = r["text"]!!.jsonPrimitive.content; val want = r["expect"]!!.jsonPrimitive.content
            val got = when (val u = Understand.of(text)) {
                is Understood.Ask -> "ASK ${u.query.period} ${u.query.topic}"
                else -> "NONE"
            }
            val w = want.split(" "); val g = got.split(" ")
            val ok = if (w[0] == "ASK" && g[0] == "ASK") (w[1] == "ANY" || w[1] == g[1]) && w[2] == g[2] else want == got
            if (want == "NONE") { negatives++; if (got != "NONE") falseAsk++ }
            if (ok) right++ else report.appendLine("MISS ${r["id"]!!.jsonPrimitive.content}  want $want  got $got  $text")
            by.getOrPut(r["lang"]!!.jsonPrimitive.content) { IntArray(2) }.also { it[0] += if (ok) 1 else 0; it[1]++ }
        }
        val head = "Unseen questions: $right/${rows.size} right; everyday questions taken as ledger questions: $falseAsk/$negatives  " +
            by.map { (k, v) -> "$k ${v[0]}/${v[1]}" }.joinToString(", ")
        File("build").mkdirs()
        File("build/unseen-questions-report.txt").writeText(head + "\n\n" + report)
        println(head)
    }
}
