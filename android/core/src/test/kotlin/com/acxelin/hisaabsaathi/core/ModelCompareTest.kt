package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Compares fine-tuned language models (tools/finetune_compare.py) on the app's real path.
 *
 * 1. Real speech: every spoken ledger sentence of the speech test, as the speech models
 *    actually wrote it (speech_transcripts.jsonl). Rules alone, then rules plus the model
 *    when the rules are unsure, exactly as the app decides.
 * 2. Questions the rules may not understand (questions_heldout.jsonl, phrasings not used in
 *    training): rules alone, then rules plus the model's question job.
 *
 * writePrompts makes the exact prompts the app would send; the Python side answers them
 * with each model (core/src/test/resources/slm_sp_<tag>.jsonl and slm_q_<tag>.jsonl);
 * scoreModels writes build/model-compare-report.txt. Informational: nothing is asserted.
 */
class ModelCompareTest {
    private val corpusTest = CorpusTest()

    private fun lines(name: String) = javaClass.getResource(name)?.readText()?.lines()?.filter { it.isNotBlank() }
        ?.map { Json.parseToJsonElement(it).jsonObject }.orEmpty()

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun num(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    private fun show(e: Extraction): String = when {
        e.query != null -> "ASK ${e.query.period} ${e.query.topic}"
        e.entries.isEmpty() -> "UNKNOWN"
        else -> e.entries.joinToString("; ") { d ->
            listOfNotNull(
                d.type.name, Money.format(d.amountPaise), d.hours?.let { "h${num(it)}" }, d.pieces?.let { "p$it" },
                d.ratePaise?.let { "r${Money.format(it)}" }, d.category?.name, d.dayOffset.takeIf { it != 0 }?.let { "d$it" },
            ).joinToString(" ")
        }
    }

    /** Spoken ledger entries with what the speech model heard (not silence, not chat). */
    private fun speechRows() = lines("/speech_transcripts.jsonl").filter {
        it["expected"]!!.jsonObject["kind"]!!.jsonPrimitive.content == "entries" && str(it, "heard") != null
    }

    private fun esc(s: String) = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(s))

    @Test fun writePrompts() {
        File("build").mkdirs()
        File("build/slm_speech_prompts.jsonl").writeText(
            speechRows().mapIndexed { i, r ->
                val heard = str(r, "heard")!!
                "{\"id\":$i,\"lang\":${esc(r["lang"]!!.jsonPrimitive.content)},\"heard\":${esc(heard)},\"prompt\":${Slm.prompt(heard)?.let(::esc) ?: "null"}}"
            }.joinToString("\n"),
        )
        File("build/slm_question_prompts.jsonl").writeText(
            lines("/questions_heldout.jsonl").joinToString("\n") { r ->
                val text = r["text"]!!.jsonPrimitive.content
                "{\"lang\":${esc(r["lang"]!!.jsonPrimitive.content)},\"text\":${esc(text)},\"expect\":${esc(r["expect"]!!.jsonPrimitive.content)},\"prompt\":${esc(Slm.questionPrompt(text))}}"
            },
        )
    }

    @Test fun scoreModels() {
        val report = StringBuilder()
        val speech = speechRows()
        val questions = lines("/questions_heldout.jsonl")
        val tags = File("src/test/resources").listFiles().orEmpty().mapNotNull { Regex("slm_sp_(.+)\\.jsonl").matchEntire(it.name)?.groupValues?.get(1) }.sorted()

        // 1. Real speech.
        fun speechScore(answers: Map<Int, String?>?): Pair<Int, Map<String, IntArray>> {
            var right = 0; val by = sortedMapOf<String, IntArray>()
            speech.forEachIndexed { i, r ->
                val heard = str(r, "heard")!!
                val want = corpusTest.expectedFor(r["expected"]!!.jsonObject)
                val rules = RuleBasedExtractor.extract(heard)
                val final = if (answers != null && Slm.needed(rules)) Slm.choose(rules, answers[i]) else rules
                val ok = show(final) == want
                if (ok) right++
                by.getOrPut(r["lang"]!!.jsonPrimitive.content) { IntArray(2) }.also { it[0] += if (ok) 1 else 0; it[1]++ }
            }
            return right to by
        }
        report.appendLine("1. Spoken ledger entries as the speech models wrote them: ${speech.size} utterances, entry exactly right")
        val (rulesRight, rulesBy) = speechScore(null)
        report.appendLine("  rules alone: $rulesRight  ${rulesBy.map { (k, v) -> "$k ${v[0]}/${v[1]}" }.joinToString(", ")}")
        for (tag in tags) {
            val answers = lines("/slm_sp_$tag.jsonl").associate { it["id"]!!.jsonPrimitive.content.toInt() to str(it, "output") }
            val (right, by) = speechScore(answers)
            report.appendLine("  rules + $tag: $right  ${by.map { (k, v) -> "$k ${v[0]}/${v[1]}" }.joinToString(", ")}")
        }

        // 2. Questions.
        fun asked(text: String, modelOutput: String?, useModel: Boolean): String {
            when (val u = Understand.of(text)) {
                is Understood.Ask -> return "${u.query.period} ${u.query.topic}"
                is Understood.Unknown -> if (useModel) Slm.parseQuestion(modelOutput)?.let { return "${it.period} ${it.topic}" }
                else -> {}
            }
            return "NONE"
        }
        report.appendLine()
        report.appendLine("2. Questions in phrasings not used in training: ${questions.count { str(it, "expect") != "NONE" }} questions, ${questions.count { str(it, "expect") == "NONE" }} other sentences")
        fun questionLine(name: String, outputs: Map<String, String?>?) {
            var right = 0; var falseAsk = 0; var missed = 0
            val by = sortedMapOf<String, IntArray>()
            for (q in questions) {
                val text = str(q, "text")!!; val want = str(q, "expect")!!
                val got = asked(text, outputs?.get(text), outputs != null)
                val ok = got == want
                if (ok) right++ else if (want == "NONE") falseAsk++ else if (got == "NONE") missed++
                by.getOrPut(str(q, "lang")!!) { IntArray(2) }.also { it[0] += if (ok) 1 else 0; it[1]++ }
            }
            report.appendLine("  $name: $right/${questions.size} right; missed questions $missed; non-questions taken as questions $falseAsk  " +
                by.map { (k, v) -> "$k ${v[0]}/${v[1]}" }.joinToString(", "))
        }
        questionLine("rules alone", null)
        for (tag in tags) {
            val outputs = lines("/slm_q_$tag.jsonl").associate { str(it, "text")!! to str(it, "output") }
            if (outputs.isNotEmpty()) questionLine("rules + $tag", outputs)
        }
        File("build").mkdirs()
        File("build/model-compare-report.txt").writeText(report.toString())
    }
}
