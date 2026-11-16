package com.acxelin.hisaabsaathi.core

/** Script-level clean-up shared by the number reader and the keyword matcher. */
object Text {
    private const val NUKTA_DEVA = '़'
    private const val NUKTA_ODIA = '଼'

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFC)) {
            when {
                c == NUKTA_DEVA || c == NUKTA_ODIA -> {}
                c in '०'..'९' -> sb.append('0' + (c - '०'))
                c in '୦'..'୯' -> sb.append('0' + (c - '୦'))
                c == 'ँ' -> sb.append('ं')
                c == 'ଁ' -> sb.append('ଂ')
                c == '₹' -> sb.append(" rs ")
                c == '‌' || c == '‍' -> {}
                else -> sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    private val thousands = Regex("(?<=\\d),(?=\\d{2,3}(\\D|$))")
    private val looseDot = Regex("(?<!\\d)\\.|\\.(?!\\d)")
    private val splitter = Regex("[\\s,;:!?।॥()\"“”‘’/|\\-]+")
    private val digitWord = Regex("(?<=\\d)(?=[^\\d.])|(?<=[^\\d.])(?=\\d)")

    /** Lower-cased, digit-normalized words; "1,200" becomes "1200", "rs.300" becomes "rs", "300". */
    fun tokens(input: String): List<String> =
        normalize(input)
            .replace(thousands, "")
            .replace(looseDot, " ")
            .replace(digitWord, " ")
            .split(splitter)
            .map { it.trim('.', '\'') }
            .filter { it.isNotEmpty() }
}
