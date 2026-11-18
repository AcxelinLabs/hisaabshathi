package com.acxelin.hisaabsaathi.core

import kotlin.math.abs

/**
 * Prompt and answer handling for the on-device small language model.
 *
 * A model this small (0.5–1.5 billion parameters) invents numbers when asked to write a
 * ledger entry itself. So the app's own code finds the numbers in what was said, and the
 * model is asked only to say what each number is. It can therefore never add an amount
 * the worker did not say, and all arithmetic stays in plain code.
 */
object Slm {
    val labels = listOf("hours", "pieces", "rate", "wage", "received", "advance", "expense", "other")

    private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /** Null when nothing was said that could be labelled. */
    fun prompt(transcript: String): String? {
        val numbers = NumberWords.numbersIn(transcript)
        if (numbers.isEmpty()) return null
        return """
A daily-wage worker in India said this (Hindi, Odia, Telugu, Kannada, Tamil or English):
"${transcript.replace('"', ' ')}"
The numbers in it, in order: ${numbers.joinToString(", ") { fmt(it) }}
Say what each number is. Use exactly one of these words for each:
hours = hours worked
pieces = pieces or items made
rate = pay for one piece or one hour
wage = money earned for the work
received = wage money the worker was actually paid
advance = money taken early from the employer or contractor
expense = money the worker spent
other = anything else
Answer with one line per number, in the same order, like this:
${numbers.joinToString("\n") { fmt(it) + "=" }}
""".trimStart()
    }

    /** For things that are neither a ledger entry nor one of the fixed simple questions. */
    fun chatPrompt(question: String, languageName: String): String = """
You are HisaabSaathi, a helper inside a wage-ledger phone app for daily-wage workers in India.
Reply in $languageName, in one or two short, simple sentences.
You note work, advances, wages and expenses, and tell totals. You cannot do anything else.
If the question is simple general knowledge, answer it briefly. Otherwise say what you can do.
Never state amounts of money the user did not say.
User: ${question.replace('"', ' ')}
Reply:""".trimStart()

    /** Trims a model reply to something short enough to speak; null if there is nothing usable. */
    fun cleanChat(modelOutput: String?): String? {
        val text = modelOutput?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
        if (text.startsWith("{") || text.startsWith("[")) return null
        val sentences = text.split(Regex("(?<=[.!?।॥])\\s+")).take(2).joinToString(" ")
        return sentences.take(280).trim().takeIf { it.length >= 2 }
    }

    private val line = Regex("(\\d+(?:\\.\\d+)?)\\s*[=:\\-]\\s*([a-z]+)")

    /**
     * Builds draft entries from the model's labels. Returns null when the answer does not
     * label exactly the numbers that were spoken, in order.
     */
    fun parse(modelOutput: String, transcript: String, dayOffset: Int = 0): List<DraftEntry>? {
        val spoken = NumberWords.numbersIn(transcript)
        val found = line.findAll(modelOutput.lowercase()).map { it.groupValues[1].toDouble() to it.groupValues[2] }.toList()
        if (spoken.isEmpty() || found.size != spoken.size) return null
        for (i in spoken.indices) if (abs(found[i].first - spoken[i]) > 0.005 || found[i].second !in labels) return null

        fun first(label: String) = found.firstOrNull { it.second == label }?.first
        val hours = first("hours"); val pieces = first("pieces"); val rate = first("rate"); val wage = first("wage")
        val out = ArrayList<DraftEntry>()
        if (hours != null || pieces != null || wage != null) {
            val earned = wage ?: if (rate != null) (pieces ?: hours)?.let { it * rate } else null
            out += DraftEntry(
                EntryType.WORK, Money.rupeesToPaise(earned ?: 0.0), hours = hours, pieces = pieces?.toInt(),
                ratePaise = rate?.let(Money::rupeesToPaise), dayOffset = dayOffset,
            )
        }
        // Expense categories come from the same word lists the rules use.
        val rules = RuleBasedExtractor.extract(transcript).entries
        val category = rules.firstOrNull { it.type == EntryType.EXPENSE }?.category
        for ((value, label) in found) {
            val paise = Money.rupeesToPaise(value)
            when (label) {
                "advance" -> out += DraftEntry(EntryType.ADVANCE, paise, dayOffset = dayOffset)
                "received" -> {
                    // "Got 500 wages": earned and paid. The rules know whether a wage word was said.
                    if (out.none { it.type == EntryType.WORK } && rules.any { it.type == EntryType.WORK && it.amountPaise == paise }) {
                        out.add(0, DraftEntry(EntryType.WORK, paise, dayOffset = dayOffset))
                    }
                    out += DraftEntry(EntryType.PAYMENT, paise, dayOffset = dayOffset)
                }
                "expense" -> out += DraftEntry(EntryType.EXPENSE, paise, category = category ?: ExpenseCategory.OTHER, dayOffset = dayOffset)
            }
        }
        return out.takeIf { it.isNotEmpty() }
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
        return Extraction(rules.transcript, entries = fromModel, lowConfidence = unsure).sane()
    }

    /** Whether it is worth spending seconds of model time on this utterance. */
    fun needed(rules: Extraction): Boolean = rules.query == null && (rules.isEmpty || rules.lowConfidence)
}
