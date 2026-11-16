package com.acxelin.hisaabsaathi.core

/** Picks the amount out of the text recognised on a photographed bill or advance slip. */
object ReceiptReader {
    private val totalWords = setOf("total", "amount", "net", "payable", "कुल", "जोड़", "योग", "राशि", "टोटल", "ମୋଟ").map(Text::normalize).toSet()
    private val advanceWords = setOf("advance", "एडवांस", "अग्रिम", "खर्ची", "ଆଡଭାନ୍ସ", "ଅଗ୍ରୀମ").map(Text::normalize).toSet()
    private val amount = Regex("\\d{1,7}(\\.\\d{1,2})?")

    /** Null when no plausible amount was found. The result always needs the worker's confirmation. */
    fun read(ocrText: String): DraftEntry? {
        val lines = ocrText.lines().map { Text.normalize(it).replace(Regex("(?<=\\d),(?=\\d)"), "") }
        fun numbers(line: String) = amount.findAll(line).map { it.value.toDouble() }.filter { it in 1.0..500_000.0 }.toList()

        val fromTotalLine = lines.filter { l -> totalWords.any { it in l } }.flatMap(::numbers).maxOrNull()
        // Otherwise the largest money-looking number (ignores long digit runs such as phone numbers).
        val value = fromTotalLine ?: lines.filterNot { Regex("\\d{8,}").containsMatchIn(it) }.flatMap(::numbers).maxOrNull()
            ?: return null
        val isAdvance = lines.any { l -> advanceWords.any { it in l } }
        return DraftEntry(
            type = if (isAdvance) EntryType.ADVANCE else EntryType.EXPENSE,
            amountPaise = Money.rupeesToPaise(value),
            category = if (isAdvance) null else ExpenseCategory.OTHER,
        )
    }
}
