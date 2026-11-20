package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * How many times a week the app was used, for workers who said yes to "Help improve": numbers
 * only, never words, amounts or dates of entries. The team sees how often entries are saved and
 * how often the app got them wrong, without ever seeing anyone's ledger. Sent with the sealed
 * uploads once a week is over.
 */
@Serializable
data class WeekCounts(
    /** ISO week, "2026-W47". */
    val week: String,
    val counts: Map<String, Int>,
    val consent: Int = 0,
    val appVersion: String = "",
)

object Usage {
    /** What is counted. Nothing else is ever added to a week. */
    object Event {
        const val SAVED = "entries_saved"
        const val BY_VOICE = "entries_by_voice"
        const val TYPED = "entries_typed"
        const val FROM_PHOTO = "entries_from_photo"
        /** The worker changed what the app understood before saving. */
        const val FIXED = "entries_fixed_before_saving"
        /** "Wrong, say again" on the confirm screen. */
        const val SAID_WRONG = "said_wrong"
        const val EDITED = "entries_edited_later"
        const val DELETED = "entries_deleted"
        const val QUESTIONS = "questions_answered"
        const val NOT_UNDERSTOOD = "not_understood"
        const val PHOTOS = "photos_taken"
        const val EVIDENCE = "evidence_packs_shared"
        const val REPORTS = "reports_shared"
        /** Days in the week the app was used (counted once a day). */
        const val DAYS = "days_used"
    }

    val known = setOf(
        Event.SAVED, Event.BY_VOICE, Event.TYPED, Event.FROM_PHOTO, Event.FIXED, Event.SAID_WRONG, Event.EDITED,
        Event.DELETED, Event.QUESTIONS, Event.NOT_UNDERSTOOD, Event.PHOTOS, Event.EVIDENCE, Event.REPORTS, Event.DAYS,
    )

    /** Weeks kept on the phone at most; older ones are dropped unsent (no internet for months). */
    const val KEEP_WEEKS = 12

    fun week(d: LocalDate): String = "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    private val json = Json { ignoreUnknownKeys = true }

    /** The phone's store: week -> event -> count. */
    fun read(text: String?): Map<String, Map<String, Int>> =
        text?.let { runCatching { json.decodeFromString<Map<String, Map<String, Int>>>(it) }.getOrNull() } ?: emptyMap()

    fun write(store: Map<String, Map<String, Int>>): String = json.encodeToString(store)

    /** [store] with [event] counted [n] more times in [week]. Unknown events are ignored. */
    fun add(store: Map<String, Map<String, Int>>, week: String, event: String, n: Int = 1): Map<String, Map<String, Int>> {
        if (event !in known || n <= 0) return store
        val w = store[week].orEmpty()
        val updated = store + (week to (w + (event to (w[event] ?: 0) + n)))
        return updated.toSortedMap().entries.toList().takeLast(KEEP_WEEKS).associate { it.key to it.value }
    }

    /** The weeks that are over, ready to send. */
    fun finished(store: Map<String, Map<String, Int>>, today: LocalDate, consent: Int, appVersion: String): List<WeekCounts> {
        val now = week(today)
        return store.filterKeys { it < now }.toSortedMap().map { (w, c) -> WeekCounts(w, c, consent, appVersion) }
    }

    fun toJson(w: WeekCounts): String = json.encodeToString(w)
    fun fromJson(text: String): WeekCounts = json.decodeFromString(text)
}
