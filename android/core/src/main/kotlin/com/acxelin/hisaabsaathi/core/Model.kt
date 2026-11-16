package com.acxelin.hisaabsaathi.core

enum class Lang(val code: String, val nativeName: String) {
    HI("hi", "हिन्दी"),
    OR("or", "ଓଡ଼ିଆ"),
    EN("en", "English");

    companion object {
        fun fromCode(code: String?): Lang = entries.firstOrNull { it.code == code } ?: HI
    }
}

enum class EntryType { WORK, ADVANCE, PAYMENT, EXPENSE }

enum class ExpenseCategory { FOOD, TRAVEL, RENT, PHONE, HEALTH, HOME, OTHER }

/** One line the worker is about to confirm. Money is in paise. */
data class DraftEntry(
    val type: EntryType,
    val amountPaise: Long,
    val hours: Double? = null,
    val pieces: Int? = null,
    val ratePaise: Long? = null,
    val category: ExpenseCategory? = null,
    /** 0 = today, -1 = yesterday. */
    val dayOffset: Int = 0,
)

enum class QueryPeriod { DAY, WEEK, MONTH, YEAR }

enum class QueryTopic { ALL, EARNED, ADVANCE, EXPENSE, DUE, MIN_WAGE }

data class Query(val period: QueryPeriod, val topic: QueryTopic)

/**
 * What was understood from one utterance. Either [entries] to confirm or a [query] to
 * answer. [lowConfidence] means the type of at least one amount was a guess.
 */
data class Extraction(
    val transcript: String,
    val entries: List<DraftEntry> = emptyList(),
    val query: Query? = null,
    val lowConfidence: Boolean = false,
) {
    val isEmpty: Boolean get() = entries.isEmpty() && query == null
}

object Money {
    fun rupeesToPaise(rupees: Double): Long = Math.round(rupees * 100)

    /** "600" or "12.50" — no currency sign, no grouping, ASCII digits. */
    fun format(paise: Long): String {
        val neg = paise < 0
        val abs = Math.abs(paise)
        val whole = abs / 100
        val frac = abs % 100
        val s = if (frac == 0L) whole.toString() else "$whole.${frac.toString().padStart(2, '0')}"
        return if (neg) "-$s" else s
    }
}
