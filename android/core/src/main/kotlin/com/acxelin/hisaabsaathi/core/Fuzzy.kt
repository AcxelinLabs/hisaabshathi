package com.acxelin.hisaabsaathi.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Tolerant word matching for speech-recognition output, which often drops or swaps one
 * letter ("एवांस" for "एडवांस", "అడ్వాస్" for "అడ్వాన్స్") or adds an ending
 * ("சம்பாதித்த" for "சம்பாதிச்சேன்"). Short words must match exactly: fuzzy matching them
 * would confuse unrelated words ("date" and "rate").
 */
object Fuzzy {
    private fun isLatin(s: String) = s.all { it.code < 128 }

    private fun length(s: String) = s.codePointCount(0, s.length)

    /** Edits allowed for a word of this length: 0 means exact match only. */
    fun allowed(word: String): Int {
        val n = length(word)
        val min = if (isLatin(word)) 5 else 4
        return when {
            n < min -> 0
            n >= 8 -> 2
            else -> 1
        }
    }

    /** Edit distance, giving up (returning max + 1) once it is certainly above [max]. */
    fun distance(a: String, b: String, max: Int): Int {
        val x = a.codePoints().toArray(); val y = b.codePoints().toArray()
        if (kotlin.math.abs(x.size - y.size) > max) return max + 1
        var prev = IntArray(y.size + 1) { it }
        for (i in 1..x.size) {
            val cur = IntArray(y.size + 1); cur[0] = i
            var best = cur[0]
            for (j in 1..y.size) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
                best = minOf(best, cur[j])
            }
            if (best > max) return max + 1
            prev = cur
        }
        return prev[y.size]
    }

    private fun commonPrefix(a: String, b: String): Int {
        val x = a.codePoints().toArray(); val y = b.codePoints().toArray()
        var i = 0
        while (i < x.size && i < y.size && x[i] == y[i]) i++
        return i
    }

    /** Whether [token] is close enough to one of [words] to be taken as it. */
    fun matches(token: String, words: Collection<String>): Boolean {
        val edits = allowed(token)
        if (edits == 0) return false
        val latin = isLatin(token)
        for (w in words) {
            if (allowed(w) == 0) continue
            if (distance(token, w, minOf(edits, allowed(w))) <= minOf(edits, allowed(w))) return true
            // Indian languages add endings: a long shared beginning is the same word.
            if (!latin && !isLatin(w)) {
                val p = commonPrefix(token, w)
                if (p >= 5 && p >= 0.6 * minOf(length(token), length(w))) return true
            }
        }
        return false
    }

    /** The one entry of [table] within one edit of [token], or null if none or more than one. */
    fun <V> unique(token: String, table: Map<String, V>, minLength: Int = 5): V? {
        if (isLatin(token) || length(token) < minLength || token in Keywords.known) return null
        val first = token.codePointAt(0)
        var found: V? = null
        var count = 0
        for ((k, v) in table) {
            // Same first letter: "చెయ్యి" (do) must not become "వెయ్యి" (thousand).
            // Not shorter than the number: "ಇನ್ನೂ" (yet) must not become "ಇನ್ನೂರ" (two hundred).
            if (length(k) < minLength || k.codePointAt(0) != first || length(token) < length(k)) continue
            if (distance(token, k, 1) <= 1) {
                if (found != v) count++
                found = v
                if (count > 1) return null
            }
        }
        return found
    }
}

/**
 * A keyword list whose `in` check tolerates small speech-recognition errors, but only for
 * words the app does not know exactly: "खर्च" (spent) must never match "खर्ची" (advance)
 * just because they are one letter apart.
 */
class Keywords(val exact: Set<String>) : Set<String> by exact {
    private val cache = ConcurrentHashMap<String, Boolean>()

    init { known.addAll(exact) }

    override fun contains(element: String): Boolean =
        element in exact || (element !in known && cache.getOrPut(element) { Fuzzy.matches(element, exact) })

    companion object {
        /** Every exact keyword of every list. */
        val known: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    operator fun plus(other: Set<String>): Keywords = Keywords(exact + (if (other is Keywords) other.exact else other))
}
