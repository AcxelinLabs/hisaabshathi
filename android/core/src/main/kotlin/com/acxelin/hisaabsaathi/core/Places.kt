package com.acxelin.hisaabsaathi.core

/**
 * Indian scripts in Latin letters, roughly as they sound: "ओडिशा" → "odisha", "ఖుర్దా" → "khurdaa".
 * Only for matching a spoken place name to the app's (English) list of states and districts;
 * never shown. Devanagari, Odia, Telugu, Kannada and Tamil share one layout, so one table
 * serves all five.
 */
object Translit {
    private val blocks = intArrayOf(0x0900, 0x0B00, 0x0C00, 0x0C80, 0x0B80)

    private val letters: Map<Int, String> = mapOf(
        // vowels
        0x05 to "a", 0x06 to "aa", 0x07 to "i", 0x08 to "ii", 0x09 to "u", 0x0A to "uu", 0x0B to "ri",
        0x0E to "e", 0x0F to "e", 0x10 to "ai", 0x12 to "o", 0x13 to "o", 0x14 to "au",
        // consonants
        0x15 to "k", 0x16 to "kh", 0x17 to "g", 0x18 to "gh", 0x19 to "ng",
        0x1A to "ch", 0x1B to "chh", 0x1C to "j", 0x1D to "jh", 0x1E to "ny",
        0x1F to "t", 0x20 to "th", 0x21 to "d", 0x22 to "dh", 0x23 to "n",
        0x24 to "t", 0x25 to "th", 0x26 to "d", 0x27 to "dh", 0x28 to "n", 0x29 to "n",
        0x2A to "p", 0x2B to "ph", 0x2C to "b", 0x2D to "bh", 0x2E to "m",
        0x2F to "y", 0x30 to "r", 0x31 to "r", 0x32 to "l", 0x33 to "l", 0x34 to "l", 0x35 to "v",
        0x36 to "sh", 0x37 to "sh", 0x38 to "s", 0x39 to "h",
        // Devanagari letters with a dot (क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़); Odia ଡ଼ ଢ଼ ୟ ୱ
        0x58 to "k", 0x59 to "kh", 0x5A to "g", 0x5B to "z", 0x5C to "r", 0x5D to "rh", 0x5E to "f", 0x5F to "y",
        0x71 to "w",
    )
    private val signs: Map<Int, String> = mapOf(
        0x3E to "aa", 0x3F to "i", 0x40 to "ii", 0x41 to "u", 0x42 to "uu", 0x43 to "ri",
        0x46 to "e", 0x47 to "e", 0x48 to "ai", 0x4A to "o", 0x4B to "o", 0x4C to "au", 0x57 to "au",
    )
    private const val VIRAMA = 0x4D

    private fun offset(c: Int): Int? = blocks.firstOrNull { c in it until it + 0x80 }?.let { c - it }
    private fun isConsonant(o: Int) = o in 0x15..0x39 || o in 0x58..0x5F || o == 0x71

    fun latin(text: String): String {
        val cps = text.codePoints().toArray()
        val out = StringBuilder()
        var i = 0
        while (i < cps.size) {
            val c = cps[i]
            val o = offset(c)
            when {
                o == null -> out.appendCodePoint(Character.toLowerCase(c))
                o in 0x66..0x6F -> out.append('0' + (o - 0x66))
                o == 0x01 || o == 0x02 -> out.append('n') // candrabindu, anusvara
                o == 0x03 -> out.append('h')
                isConsonant(o) -> {
                    out.append(letters[o])
                    var j = i + 1
                    if (j < cps.size && offset(cps[j]) == 0x3C) j++ // nukta
                    val next = cps.getOrNull(j)?.let(::offset)
                    when {
                        next == VIRAMA -> j++
                        next != null && signs.containsKey(next) -> { out.append(signs[next]); j++ }
                        else -> out.append('a')
                    }
                    i = j; continue
                }
                else -> letters[o]?.let(out::append) ?: signs[o]?.let(out::append)
            }
            i++
        }
        return out.toString()
    }
}

/**
 * The place a worker named, out of a list (states, or the districts of one state). Said in any of
 * the app's languages or in English, with words around it ("मैं खोरधा में रहता हूँ"). The names
 * are compared by how they sound: aspiration, voicing and vowels are ignored, as spelling and
 * speech differ most there. Gives a name only when one is clearly closest.
 */
object PlaceMatch {
    fun best(spoken: String, options: List<String>): String? {
        val words = Text.tokens(Translit.latin(spoken)).filter { it.isNotBlank() && it.none(Char::isDigit) }
        if (words.isEmpty()) return null
        // Single words and pairs ("west bengal", "tamil nadu", "east godavari").
        val phrases = (words.indices.map { words[it] } + words.indices.drop(1).map { words[it - 1] + words[it] }).map(::sound).filter { it.length >= 2 }
        var best: String? = null; var bestScore = Int.MAX_VALUE; var tie = false
        for (option in options) {
            val names = aliases(option).map(::sound).filter { it.length >= 2 }
            val score = names.minOfOrNull { n -> phrases.minOf { p -> Fuzzy.distance(p, n, 3) } } ?: continue
            when {
                score < bestScore -> { best = option; bestScore = score; tie = false }
                score == bestScore -> tie = true
            }
        }
        val limit = best?.let { if (sound(it.substringBefore('(')).length >= 5) 1 else 0 } ?: return null
        return best.takeIf { !tie && bestScore <= limit }
    }

    /** "Khordha (Khurda)" → Khordha, Khurda; and the names people say for some states. */
    private fun aliases(option: String): List<String> =
        (listOf(option.substringBefore('(').replace(" ", ""), option.substringAfter('(', "").substringBefore(')').replace(" ", "")) +
            spokenNames[option].orEmpty()).filter { it.isNotBlank() }

    /** What people call some states, where that is not close to the English name. */
    private val spokenNames: Map<String, List<String>> = mapOf(
        "West Bengal" to listOf("pashchimabangaala", "pashchimbangal", "bangaal", "bangla", "bengal", "paschimbanga"),
        "Odisha" to listOf("orissa", "udisa"),
        "Delhi" to listOf("dilli"),
        "Puducherry" to listOf("pondicherry", "pondy"),
        "Jammu and Kashmir" to listOf("kashmir", "jammu", "jammukashmir"),
        "Andaman and Nicobar Islands" to listOf("andaman", "nicobar"),
        "Dadra and Nagar Haveli and Daman and Diu" to listOf("daman", "diu", "dadra", "silvassa"),
        "Chhattisgarh" to listOf("chattisgarh", "chhattisgadh"),
    )

    /** The sound of a word, loosely: k for c/q/g/kh, t for d/th, s for sh/z, vowels only at the start. */
    internal fun sound(word: String): String {
        var w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return w
        w = w.replace("chh", "c").replace("ch", "c").replace("sh", "s").replace("ph", "f")
            .replace(Regex("([kgcjtdpb])h"), "$1")
        val first = if (w[0] in "aeiou") "a" else ""
        val mapped = w.map { c ->
            when (c) {
                'g', 'q', 'c' -> 'k'; 'd' -> 't'; 'b' -> 'p'; 'z', 'j' -> 's'; 'w' -> 'v'; 'f' -> 'p'
                'a', 'e', 'i', 'o', 'u', 'y', 'h' -> ' '
                else -> c
            }
        }.filter { it != ' ' }.joinToString("")
        return first + mapped.replace(Regex("(.)\\1+"), "$1")
    }
}
