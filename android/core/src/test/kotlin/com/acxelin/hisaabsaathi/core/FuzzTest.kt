package com.acxelin.hisaabsaathi.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Throws thousands of random utterances at the understanding and reply code: number words
 * from every language, ledger keywords, digits, punctuation, garbled script, very long input.
 * Whatever a speech model or a worker's typing produces, the app must answer, never crash,
 * and never produce a negative or absurd amount.
 */
class FuzzTest {
    private val corpus: List<String> = javaClass.getResource("/corpus.jsonl")!!.readText().lines()
        .filter { it.isNotBlank() }
        .map { Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(it)!!.groupValues[1] }

    private val vocabulary: List<String> = corpus.flatMap { it.split(' ') }.filter { it.isNotBlank() }.distinct() +
        listOf(
            "0", "1", "00", "999999999999", "12.5", "1,200", "₹", "₹500", "rs.", "-5", ".", "..", "", " ", "\n", "|", "\"",
            "ఫైవ్", "హండ్రెడ్", "అండ్", "टू", "ऐट", "থ্রি", "ಸೆವೆನ್", "ஃபைவ்", "and", "lakh", "lakh", "करोड़",
            "्", "்", "ाा", "ଆା", "😀", "‍", "Ω", "null", "NaN", "Infinity",
        )

    private fun sentence(r: Random): String {
        val n = r.nextInt(1, 18)
        return (0 until n).joinToString(if (r.nextInt(6) == 0) "" else " ") { vocabulary[r.nextInt(vocabulary.size)] }
    }

    private fun exercise(text: String) {
        val u = Understand.of(text)
        for (l in Lang.entries) {
            val s = Strings(l)
            when (u) {
                is Understood.Entries -> {
                    for (d in u.extraction.entries) {
                        assertTrue("negative amount from \"$text\": $d", d.amountPaise >= 0)
                        assertTrue("absurd amount from \"$text\": $d", d.amountPaise <= 1_000_000_000_000L)
                    }
                    s.readback(u.extraction.entries)
                    Corrections.describe(u.extraction.entries)
                }
                is Understood.Ask -> s.answer(u.query, Totals())
                is Understood.Talk -> s.chat(u.chat, "18 November 2026")
                is Understood.Unknown -> s.chatFallback
            }
        }
        Slm.prompt(text)
        Slm.parse("hours=8 rate=70 wage=560", text)
        Slm.parse(text, text)
        NumberWords.numbersIn(text)
    }

    @Test fun randomUtterancesNeverCrash() {
        val r = Random(20261118)
        repeat(20_000) {
            val text = sentence(r)
            try { exercise(text) } catch (e: Throwable) { throw AssertionError("crashed on \"$text\"", e) }
        }
    }

    @Test fun extremeInputs() {
        val long = (1..400).joinToString(" ") { "एक सौ" }
        for (text in listOf("", " ", "\u0000", long, "९".repeat(500), "1".repeat(40), "लाख लाख लाख करोड़", "साढ़े", "सवा पौने डेढ़ ढाई")) {
            try { exercise(text) } catch (e: Throwable) { throw AssertionError("crashed on \"${text.take(60)}\"", e) }
        }
    }
}
