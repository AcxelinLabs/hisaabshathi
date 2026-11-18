package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Runs speech-recogniser output through the app's own number reader and understanding code
 * (tools/asr/pipeline_eval.py). Skipped unless PIPELINE_IN names a JSONL file of
 * {"id","ref","hyp"} rows; writes PIPELINE_OUT with, for both the reference transcript and
 * the recogniser's text: the number words the app recognises, the values it reads, and what
 * `Understand` makes of the sentence.
 */
class PipelineEvalTest {
    private val corpus = CorpusTest()

    private fun numberWords(text: String): List<String> {
        val toks = RuleBasedExtractor.tokensOf(text)
        return NumberWords.parse(toks).filterIsInstance<NumberWords.Num>().flatMap { toks.subList(it.start, it.end) }
    }

    private fun side(text: String) = JsonObject(
        mapOf(
            "num_words" to JsonArray(numberWords(text).map { JsonPrimitive(it) }),
            "values" to JsonArray(RuleBasedExtractor.numbersIn(text).map { JsonPrimitive(it) }),
            "understood" to JsonPrimitive(corpus.actualFor(text)),
        ),
    )

    @Test fun run() {
        val input = System.getenv("PIPELINE_IN") ?: return
        val out = File(System.getenv("PIPELINE_OUT") ?: "$input.out")
        out.bufferedWriter().use { w ->
            File(input).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val r = Json.parseToJsonElement(line).jsonObject
                val row = JsonObject(
                    mapOf(
                        "id" to r["id"]!!,
                        "ref" to side(r["ref"]!!.jsonPrimitive.content),
                        "hyp" to side(r["hyp"]!!.jsonPrimitive.content),
                    ),
                )
                w.write(row.toString()); w.newLine()
            }
        }
    }
}
