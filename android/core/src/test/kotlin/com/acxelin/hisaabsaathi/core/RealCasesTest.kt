package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * What real workers said (shared with their agreement), through today's understanding. The
 * cases come from the training pipeline (server/: `pipeline export`) and are read from the
 * path in HISAAB_REAL_CASES, never from this repository: workers' words stay off GitHub.
 *
 * - "gate": true: the worker confirmed the app's understanding. A change that breaks one fails here.
 * - "gate": false: a target (the worker's fix, or an answered question): measured, not enforced.
 *
 * Writes build/real-cases-report.txt, build/real-cases-misses.tsv, and build/real-cases-metrics.json
 * (per-language rates for server/hisaab_improve/gates.py). Skipped without cases.
 */
class RealCasesTest {
    @Test fun score() {
        val path = System.getenv("HISAAB_REAL_CASES").orEmpty()
        assumeTrue("HISAAB_REAL_CASES not set", path.isNotEmpty() && File(path).isFile)
        data class Case(val lang: String, val text: String, val expect: String, val gate: Boolean)
        val cases = File(path).readLines().filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }.map {
            Case(it["lang"]!!.jsonPrimitive.content, it["text"]!!.jsonPrimitive.content, it["expect"]!!.jsonPrimitive.content, (it["gate"] as JsonPrimitive).boolean)
        }
        val report = StringBuilder("Real cases (${cases.size}) through today's understanding\n\n")
        val misses = StringBuilder()
        val broken = mutableListOf<String>()
        val rates = mapOf(true to sortedMapOf<String, Double>(), false to sortedMapOf<String, Double>())
        for ((lang, group) in cases.groupBy { it.lang }.toSortedMap()) {
            for ((gate, part) in group.groupBy { it.gate }) {
                var right = 0
                for (c in part) {
                    val got = Improve.understood(Understand.of(c.text))
                    if (got == c.expect) right++
                    else {
                        misses.appendLine("$lang\t${if (c.gate) "GATE" else "target"}\texpected ${c.expect}\tgot $got\t${c.text}")
                        if (c.gate) broken += "$lang: \"${c.text}\" expected ${c.expect}, got $got"
                    }
                }
                report.appendLine("$lang ${if (gate) "confirmed (must stay right)" else "targets"}: $right / ${part.size}")
                rates.getValue(gate)[lang] = right.toDouble() / part.size
            }
        }
        File("build").mkdirs()
        File("build/real-cases-report.txt").writeText(report.toString())
        File("build/real-cases-misses.tsv").writeText(misses.toString())
        fun obj(m: Map<String, Double>) = m.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
        File("build/real-cases-metrics.json").writeText("{\"gate_right_rate\":${obj(rates.getValue(true))},\"target_right_rate\":${obj(rates.getValue(false))}}\n")
        println(report)
        assertTrue("confirmed cases now misunderstood:\n" + broken.joinToString("\n"), broken.isEmpty())
    }
}
