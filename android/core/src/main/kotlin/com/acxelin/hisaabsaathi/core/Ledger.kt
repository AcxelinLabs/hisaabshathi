package com.acxelin.hisaabsaathi.core

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A saved ledger line, independent of the database. */
data class Entry(
    val id: Long = 0,
    val epochDay: Long,
    val createdAtMs: Long,
    val type: EntryType,
    val amountPaise: Long,
    val hours: Double? = null,
    val pieces: Int? = null,
    val ratePaise: Long? = null,
    val category: ExpenseCategory? = null,
    /** What the worker said, or "scan"/"manual". Kept as evidence. */
    val note: String = "",
    val source: String = "voice",
    val attachment: String? = null,
    val prevHash: String = "",
    val hash: String = "",
)

data class Totals(
    val earnedPaise: Long = 0,
    val advancePaise: Long = 0,
    val paidPaise: Long = 0,
    val expensePaise: Long = 0,
    val hours: Double = 0.0,
    val pieces: Int = 0,
    val daysWorked: Int = 0,
) {
    /** What the employer still owes. */
    val duePaise: Long get() = earnedPaise - advancePaise - paidPaise

    /** Cash that reached the worker minus what they spent. */
    val inHandPaise: Long get() = advancePaise + paidPaise - expensePaise

    val avgDailyEarnedPaise: Long get() = if (daysWorked == 0) 0 else earnedPaise / daysWorked
}

object Ledger {
    fun totals(entries: Collection<Entry>): Totals {
        var earned = 0L; var advance = 0L; var paid = 0L; var expense = 0L
        var hours = 0.0; var pieces = 0
        val workDays = HashSet<Long>()
        for (e in entries) {
            when (e.type) {
                EntryType.WORK -> {
                    earned += e.amountPaise
                    hours += e.hours ?: 0.0
                    pieces += e.pieces ?: 0
                    workDays += e.epochDay
                }
                EntryType.ADVANCE -> advance += e.amountPaise
                EntryType.PAYMENT -> paid += e.amountPaise
                EntryType.EXPENSE -> expense += e.amountPaise
            }
        }
        return Totals(earned, advance, paid, expense, hours, pieces, workDays.size)
    }

    /** Inclusive epoch-day range of the period containing [today]. Weeks start on Monday. */
    fun range(period: QueryPeriod, today: LocalDate): LongRange {
        val (from, to) = when (period) {
            QueryPeriod.DAY -> today to today
            QueryPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
            QueryPeriod.MONTH -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
            QueryPeriod.YEAR -> today.withDayOfYear(1) to today.with(TemporalAdjusters.lastDayOfYear())
        }
        return from.toEpochDay()..to.toEpochDay()
    }

    fun fromDraft(d: DraftEntry, today: LocalDate, nowMs: Long, note: String, source: String, attachment: String? = null) =
        Entry(
            epochDay = today.plusDays(d.dayOffset.toLong()).toEpochDay(),
            createdAtMs = nowMs,
            type = d.type,
            amountPaise = d.amountPaise,
            hours = d.hours,
            pieces = d.pieces,
            ratePaise = d.ratePaise,
            category = d.category,
            note = note,
            source = source,
            attachment = attachment,
        )
}

/**
 * Each entry's hash covers its content and the previous entry's hash, so editing or
 * removing an old line breaks every hash after it. This makes an exported record
 * tamper-evident; it does not prove the entry was true when it was made.
 */
object HashChain {
    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

    fun canonical(e: Entry): String = listOf(
        e.epochDay, e.createdAtMs, e.type.name, e.amountPaise, e.hours ?: "", e.pieces ?: "",
        e.ratePaise ?: "", e.category?.name ?: "", e.source, e.note.replace('|', ' '),
    ).joinToString("|")

    fun hash(prevHash: String, e: Entry): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$prevHash|${canonical(e)}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun seal(prevHash: String, e: Entry): Entry = e.copy(prevHash = prevHash, hash = hash(prevHash, e))

    /** Index of the first broken entry (oldest first), or -1 if the chain is intact. */
    fun firstBroken(entriesOldestFirst: List<Entry>): Int {
        var prev = GENESIS
        for ((i, e) in entriesOldestFirst.withIndex()) {
            if (e.prevHash != prev || e.hash != hash(prev, e)) return i
            prev = e.hash
        }
        return -1
    }
}
