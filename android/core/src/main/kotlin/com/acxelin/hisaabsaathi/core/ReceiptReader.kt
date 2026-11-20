package com.acxelin.hisaabsaathi.core

/** Picks the amount out of the text recognised on a photographed bill, slip or salary slip. */
object ReceiptReader {
    private val totalWords = setOf("total", "amount", "net", "payable", "कुल", "जोड़", "योग", "राशि", "टोटल", "ମୋଟ").map(Text::normalize).toSet()
    private val advanceWords = setOf("advance", "एडवांस", "अग्रिम", "खर्ची", "ଆଡଭାନ୍ସ", "ଅଗ୍ରୀମ").map(Text::normalize).toSet()
    /** On a salary slip the amount paid is the net, not the gross. */
    private val netWords = setOf("net pay", "net salary", "net amount", "take home", "in hand", "नेट", "हाथ में", "शुद्ध").map(Text::normalize).toSet()
    private val amount = Regex("\\d{1,7}(\\.\\d{1,2})?")

    /** Null when no plausible amount was found. The result always needs the worker's confirmation. */
    fun read(ocrText: String): DraftEntry? = read(ocrText, null)

    /**
     * With [kind] (what the worker said the photo is) the line's type follows it; without, an
     * advance slip is told from a bill by its words.
     */
    fun read(ocrText: String, kind: PhotoKind?): DraftEntry? {
        val lines = ocrText.lines().map { Text.normalize(it).replace(Regex("(?<=\\d),(?=\\d)"), "") }
        fun numbers(line: String) = amount.findAll(line).map { it.value.toDouble() }.filter { it in 1.0..500_000.0 }.toList()

        val net = if (kind == PhotoKind.SALARY) lines.filter { l -> netWords.any { it in l } }.flatMap(::numbers).maxOrNull() else null
        val fromTotalLine = net ?: lines.filter { l -> totalWords.any { it in l } }.flatMap(::numbers).maxOrNull()
        // Otherwise the largest money-looking number (ignores long digit runs such as phone numbers).
        val value = fromTotalLine ?: lines.filterNot { Regex("\\d{8,}").containsMatchIn(it) }.flatMap(::numbers).maxOrNull()
            ?: return null
        val type = kind?.entryType ?: if (lines.any { l -> advanceWords.any { it in l } }) EntryType.ADVANCE else EntryType.EXPENSE
        return blank(type).copy(amountPaise = Money.rupeesToPaise(value))
    }

    /** A line of [type] with no amount yet: the worker types it when the photo cannot be read. */
    fun blank(type: EntryType) = DraftEntry(
        type = type,
        amountPaise = 0,
        category = if (type == EntryType.EXPENSE) ExpenseCategory.OTHER else null,
    )
}
