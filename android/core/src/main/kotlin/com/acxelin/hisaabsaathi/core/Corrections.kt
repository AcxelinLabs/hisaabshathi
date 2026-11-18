package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One time the app got an utterance wrong: what the speech model heard, what the app
 * understood, and what the worker kept after fixing it (null when they said it was wrong and
 * spoke again). Collected from real use, these show the misheard words and phrasings in
 * each language. They stay in the phone's encrypted database; the worker decides whether to
 * share them.
 */
@Serializable
data class Correction(
    val createdAtMs: Long,
    val lang: String,
    /** "voice" or "typed". */
    val source: String,
    val heard: String,
    val understood: String,
    val kept: String?,
) {
    val outcome: String get() = if (kept == null) "rejected" else "corrected"
}

object Corrections {
    /** Keep only the newest this many on the phone. */
    const val LIMIT = 500

    /** Scanned bills are not speech or typing; their mistakes are OCR, not language. */
    private fun counts(source: String) = source == "voice" || source == "typed"

    /** The worker changed what the app understood before saving; null if nothing changed. */
    fun corrected(now: Long, lang: Lang, source: String, heard: String, understood: List<DraftEntry>, kept: List<DraftEntry>): Correction? =
        if (!counts(source) || understood == kept) null
        else Correction(now, lang.code, source, heard, describe(understood), describe(kept))

    /** The worker said the app understood it wrong. */
    fun rejected(now: Long, lang: Lang, source: String, heard: String, understood: List<DraftEntry>): Correction? =
        if (!counts(source)) null else Correction(now, lang.code, source, heard, describe(understood), null)

    /** Short, language-neutral text: "WORK 600 h8 p120 r5; ADVANCE 300". */
    fun describe(drafts: List<DraftEntry>): String = drafts.joinToString("; ") { d ->
        buildString {
            append(d.type.name).append(' ').append(Money.format(d.amountPaise))
            d.hours?.let { append(" h").append(if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()) }
            d.pieces?.let { append(" p").append(it) }
            d.ratePaise?.let { append(" r").append(Money.format(it)) }
            d.category?.let { append(' ').append(it.name) }
            if (d.dayOffset != 0) append(" d").append(d.dayOffset)
        }
    }

    private val json = Json { encodeDefaults = true }

    /** One JSON object per line, oldest first, for sharing. */
    fun toJsonLines(list: List<Correction>): String =
        list.sortedBy { it.createdAtMs }.joinToString("\n", postfix = "\n") { json.encodeToString(it) }
}
