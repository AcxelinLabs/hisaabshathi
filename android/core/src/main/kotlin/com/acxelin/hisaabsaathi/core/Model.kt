package com.acxelin.hisaabsaathi.core

enum class Lang(val code: String, val nativeName: String, val englishName: String) {
    HI("hi", "हिन्दी", "Hindi"),
    OR("or", "ଓଡ଼ିଆ", "Odia"),
    TE("te", "తెలుగు", "Telugu"),
    KN("kn", "ಕನ್ನಡ", "Kannada"),
    TA("ta", "தமிழ்", "Tamil"),
    EN("en", "English", "English");

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

    /** The largest amount one entry may hold: ₹1 crore. Anything bigger is a typing slip. */
    const val MAX_PAISE = 1_000_000_000L

    private val decimalComma = Regex("""^\d+,\d{1,2}$""")
    private val plain = Regex("""^\d+(\.\d{0,2})?$""")

    /**
     * An amount the worker typed, in paise: 0 for an empty box, null if it is not a plain
     * amount ("abc", "1e9", "NaN", three decimals, or above [MAX_PAISE]). Accepts "12,50"
     * (decimal comma), "1,200" (grouping), and Devanagari or Odia digits.
     */
    fun parseTyped(input: String): Long? {
        var t = Text.normalize(input).trim().replace(" ", "")
        if (t.isEmpty()) return 0
        t = if (decimalComma.matches(t)) t.replace(',', '.') else t.replace(",", "")
        if (!plain.matches(t)) return null
        val whole = t.substringBefore('.')
        if (whole.length > 8) return null
        val frac = t.substringAfter('.', "").padEnd(2, '0')
        val paise = whole.toLong() * 100 + frac.toLong()
        return paise.takeIf { it <= MAX_PAISE }
    }

    /** "600" or "12.50" — no currency sign, no grouping, ASCII digits. */
    fun format(paise: Long): String {
        val neg = paise < 0
        val abs = Math.abs(paise)
        val whole = abs / 100
        val frac = abs % 100
        val s = if (frac == 0L) whole.toString() else "$whole.${frac.toString().padStart(2, '0')}"
        return if (neg) "-$s" else s
    }

    /** For the screen: Indian digit grouping, "1,20,000". Speech uses [format] so voices read it right. */
    fun formatGrouped(paise: Long): String {
        val plain = format(paise)
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val whole = body.substringBefore('.')
        val frac = if ('.' in body) "." + body.substringAfter('.') else ""
        if (whole.length <= 3) return plain
        val head = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return (if (neg) "-" else "") + head + "," + whole.takeLast(3) + frac
    }
}
