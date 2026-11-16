package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/**
 * Prompt and answer handling for the on-device small language model. The model only
 * labels what was said. It never does arithmetic, and any number it returns that the
 * worker did not say makes the whole answer invalid.
 */
object Slm {
    fun prompt(transcript: String): String = """
You convert one spoken sentence from an Indian daily-wage worker (Hindi, Odia, English or mixed) into JSON for a wage ledger.
Output ONLY a JSON array. Each item: {"type":"work"|"advance"|"payment"|"expense","amount":number,"hours":number,"pieces":number,"rate":number,"category":"food"|"travel"|"rent"|"phone"|"health"|"home"|"other"}
Rules:
- work: wages earned. Give hours, pieces, rate if said. amount only if a total wage was said.
- advance: money taken early from the employer or contractor (advance, kharchi, udhar).
- payment: wages actually received.
- expense: money the worker spent; set category.
- Use only numbers that were spoken. Never calculate. Omit fields that were not said.
- If nothing fits, output [].
Example: "आज आठ घंटे काम किया एक सौ बीस पीस पांच रुपये रेट तीन सौ एडवांस लिया"
[{"type":"work","hours":8,"pieces":120,"rate":5},{"type":"advance","amount":300}]
Example: "sabzi par 80 rupaye kharch"
[{"type":"expense","amount":80,"category":"food"}]
Sentence: "${transcript.replace('"', ' ')}"
JSON:""".trimStart()

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** Returns null when the model output is unusable or names a number that was not spoken. */
    fun parse(modelOutput: String, transcript: String, dayOffset: Int = 0): List<DraftEntry>? {
        val start = modelOutput.indexOf('[')
        val end = modelOutput.indexOf(']', start + 1)
        if (start < 0 || end < 0) return null
        val array = runCatching { json.parseToJsonElement(modelOutput.substring(start, end + 1)) }.getOrNull() as? JsonArray
            ?: return null
        val spoken = NumberWords.numbersIn(transcript)
        fun said(v: Double) = spoken.any { abs(it - v) < 0.005 }

        val out = ArrayList<DraftEntry>()
        for (item in array) {
            val o = item as? JsonObject ?: return null
            val type = when (o.str("type")) {
                "work" -> EntryType.WORK
                "advance" -> EntryType.ADVANCE
                "payment" -> EntryType.PAYMENT
                "expense" -> EntryType.EXPENSE
                else -> return null
            }
            val hours = o.num("hours")
            val pieces = o.num("pieces")
            val rate = o.num("rate")
            var amount = o.num("amount")
            if (listOfNotNull(hours, pieces, rate).any { !said(it) }) return null
            val computed = if (rate != null) (pieces ?: hours)?.let { it * rate } else null
            if (amount != null && !said(amount)) {
                // The model multiplied for us; accept only if it matches our own arithmetic.
                if (computed == null || abs(computed - amount) > 0.005) return null
            }
            if (type == EntryType.WORK && computed != null) amount = computed
            if (type != EntryType.WORK && amount == null) return null
            out += DraftEntry(
                type = type,
                amountPaise = Money.rupeesToPaise(amount ?: 0.0),
                hours = hours.takeIf { type == EntryType.WORK },
                pieces = pieces?.toInt().takeIf { type == EntryType.WORK },
                ratePaise = rate?.let(Money::rupeesToPaise).takeIf { type == EntryType.WORK },
                category = if (type == EntryType.EXPENSE) {
                    ExpenseCategory.entries.firstOrNull { it.name.equals(o.str("category"), ignoreCase = true) }
                        ?: ExpenseCategory.OTHER
                } else null,
                dayOffset = dayOffset,
            )
        }
        return out
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content?.lowercase()

    private fun JsonObject.num(key: String): Double? {
        val e: JsonElement = this[key] ?: return null
        val v = (e as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() } ?: return null
        return v.takeIf { it > 0 }
    }

    /**
     * Final decision for an utterance. Questions and confident rule results are used as they
     * are; the model's answer is used when the rules were unsure or found nothing and the
     * model's answer passed [parse].
     */
    fun choose(rules: Extraction, modelOutput: String?): Extraction {
        if (rules.query != null || modelOutput == null) return rules
        val dayOffset = rules.entries.firstOrNull()?.dayOffset ?: 0
        val fromModel = parse(modelOutput, rules.transcript, dayOffset)
        if (fromModel.isNullOrEmpty()) return rules
        val unsure = fromModel.any { it.type == EntryType.WORK && it.amountPaise == 0L }
        return Extraction(rules.transcript, entries = fromModel, lowConfidence = unsure)
    }

    /** Whether it is worth spending seconds of model time on this utterance. */
    fun needed(rules: Extraction): Boolean = rules.query == null && (rules.isEmpty || rules.lowConfidence)
}
