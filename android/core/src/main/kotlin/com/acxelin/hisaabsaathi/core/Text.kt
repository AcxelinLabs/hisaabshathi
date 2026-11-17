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
                // Long and short vowel signs are often confused by speech recognition.
                else -> sb.append(shortVowel[c] ?: c.lowercaseChar())
            }
        }
        return sb.toString().replace(finalVirama, "")
    }

    private val shortVowel: Map<Char, Char> = mapOf(
        'ी' to 'ि', 'ू' to 'ु', 'ई' to 'इ', 'ऊ' to 'उ', // Devanagari
        'ୀ' to 'ି', 'ୂ' to 'ୁ', 'ଈ' to 'ଇ', 'ଊ' to 'ଉ', // Odia
        'ీ' to 'ి', 'ూ' to 'ు', 'ఈ' to 'ఇ', 'ఊ' to 'ఉ', // Telugu
        'ೀ' to 'ಿ', 'ೂ' to 'ು', 'ಈ' to 'ಇ', 'ಊ' to 'ಉ', // Kannada
        'ீ' to 'ி', 'ூ' to 'ு', 'ஈ' to 'இ', 'ஊ' to 'உ', // Tamil
    )

    /** A virama at the end of a word is written inconsistently ("ହଜାର୍" / "ହଜାର"). */
    private val finalVirama = Regex("[\\u094D\\u0B4D\\u0C4D\\u0CCD\\u0BCD](?=[\\s,.;:!?।॥()\"'/|\\-]|$)")

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
