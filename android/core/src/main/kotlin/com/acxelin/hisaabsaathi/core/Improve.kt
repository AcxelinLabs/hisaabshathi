package com.acxelin.hisaabsaathi.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One time a worker who agreed to help improve the app spoke or typed to it: what the speech
 * model heard, what the app understood and said back, and how it ended (saved, fixed, said to
 * be wrong). A voice clip goes with it when there is one. There is no name or phone number;
 * the only place is the state the worker chose. Kept in the phone's encrypted database until
 * it is sent, and sent only while the worker's "share" choice is on.
 */
@Serializable
data class Sample(
    /** Random, 16 hex characters; also the name of the voice clip. */
    val id: String,
    val createdAtMs: Long,
    val lang: String,
    /** "voice" or "typed". */
    val source: String,
    /** What the speech model wrote, or what was typed. */
    val heard: String,
    /** What the app made of it: [Improve.understood]. */
    val understood: String,
    /** What the app said or showed back. */
    val reply: String,
    /** One of [Improve.Outcome]. */
    val outcome: String,
    /** What the worker saved after fixing it (entries only), in [Corrections.describe] form. */
    val kept: String? = null,
    val region: String = "",
    val appVersion: String = "",
    /** Which speech engine heard it: "pack" (the app's own) or "phone". */
    val engine: String = "",
    val audioSeconds: Double = 0.0,
    /** [VoiceSamples.Quality] of the clip, lower case; empty without a clip. */
    val quality: String = "",
    val hasAudio: Boolean = false,
    /** The [Improve.CONSENT_VERSION] the worker said yes to; the pipeline drops samples without one it knows. */
    val consent: Int = 0,
)

object Improve {
    /**
     * The version of the "Help improve" question the worker answered. Raise it when the
     * question's text changes what is collected or where it goes: everyone is asked again.
     */
    const val CONSENT_VERSION = 2

    /** From this consent version on, weekly counts ([Usage]) are part of what the worker agreed to. */
    const val COUNTS_FROM_CONSENT = 2

    /** At most this many wait on the phone; the oldest go first. */
    const val LIMIT = 200

    /** Samples per upload. */
    const val BATCH = 25

    /** How an exchange ended. */
    object Outcome {
        /** A question or small talk, answered. */
        const val ANSWERED = "answered"
        /** The app said it did not understand. */
        const val NOT_UNDERSTOOD = "not_understood"
        /** Entries saved as the app understood them. */
        const val SAVED = "saved"
        /** Entries saved after the worker changed them ([Sample.kept] says how). */
        const val CORRECTED = "corrected"
        /** The worker said the app got it wrong. */
        const val REJECTED = "rejected"
        /** The worker closed the card without saying. */
        const val DISMISSED = "dismissed"
        /** A spoken yes or no to "is this right?". */
        const val CONFIRM = "confirm"
    }

    private val longNumber = Regex("""\d(?:[\s\-.]?\d){9,}""")
    private val handle = Regex("""[\w.\-]{2,}@[\w.\-]{2,}""")

    /**
     * Something a worker should not have to share: a phone, Aadhaar, bank or card number
     * (10 or more digits, even spaced or in Indian scripts, or said as words), or a UPI id or
     * email. Such exchanges are never kept, voice clip included. Wage amounts are far shorter.
     */
    fun sensitive(text: String): Boolean {
        val digits = buildString { text.forEach { c -> append(if (Character.isDigit(c)) Character.forDigit(Character.digit(c, 10), 10) else c) } }
        if (longNumber.containsMatchIn(digits) || handle.containsMatchIn(text)) return true
        return runCatching { NumberWords.numbersIn(text).any { it >= 1e9 } }.getOrDefault(false)
    }

    /** Sources worth learning from: speech and typing, not scanned bills. */
    fun counts(source: String) = source == "voice" || source == "typed"

    /** Short, language-neutral: "ASK WEEK EARNED", "ENTRY WORK 600 h8", "TALK DATE", "NONE". */
    fun understood(u: Understood): String = when (u) {
        is Understood.Ask -> "ASK ${u.query.period.name} ${u.query.topic.name}"
        is Understood.Talk -> "TALK ${u.chat.name}"
        is Understood.Entries -> "ENTRY " + Corrections.describe(u.extraction.entries)
        is Understood.Unknown -> "NONE"
    }

    private val random = SecureRandom()

    fun newId(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun toJson(s: Sample): String = json.encodeToString(s)
    fun fromJson(text: String): Sample = json.decodeFromString(text)

    /**
     * One upload, a zip:
     * - `batch.json`: format, app version, when it was made, how many samples;
     * - `samples.jsonl`: one [Sample] per line, oldest first;
     * - `audio/<id>.wav`: 16 kHz mono 16-bit, for samples with a clip;
 * - `counts.jsonl`: one [WeekCounts] per finished week (numbers only), when there are any.
     * Clips are stored (not compressed again): WAV hardly shrinks and the phone saves the work.
     */
    fun batch(samples: List<Sample>, audio: (String) -> ByteArray?, appVersion: String, nowMs: Long, weeks: List<WeekCounts> = emptyList()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray, store: Boolean = false) {
                val e = ZipEntry(name)
                if (store) {
                    e.method = ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = bytes.size.toLong()
                    e.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(e); zip.write(bytes); zip.closeEntry()
            }
            val sorted = samples.sortedBy { it.createdAtMs }
            put("batch.json", """{"format":1,"app_version":${json.encodeToString(appVersion)},"created_at_ms":$nowMs,"count":${sorted.size}}""".toByteArray())
            put("samples.jsonl", sorted.joinToString("\n", postfix = "\n") { toJson(it) }.toByteArray())
            for (s in sorted) if (s.hasAudio) audio(s.id)?.let { put("audio/${s.id}.wav", it, store = true) }
            if (weeks.isNotEmpty()) put("counts.jsonl", weeks.joinToString("\n", postfix = "\n") { Usage.toJson(it) }.toByteArray())
        }
        return out.toByteArray()
    }

    /** The weekly counts in a [batch] (for checks and tests). */
    fun readCounts(zip: ByteArray): List<WeekCounts> {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "counts.jsonl") return z.readBytes().decodeToString().lines().filter { it.isNotBlank() }.map(Usage::fromJson)
            }
        }
        return emptyList()
    }

    /** The samples and clips in a [batch] (for checks and tests). */
    fun readBatch(zip: ByteArray): Pair<List<Sample>, Map<String, ByteArray>> {
        var samples = emptyList<Sample>()
        val audio = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val bytes = z.readBytes()
                when {
                    e.name == "samples.jsonl" -> samples = bytes.decodeToString().lines().filter { it.isNotBlank() }.map(::fromJson)
                    e.name.startsWith("audio/") -> audio[e.name.removePrefix("audio/").removeSuffix(".wav")] = bytes
                }
            }
        }
        return samples to audio
    }

    /** A correction from the old local log, as a sample (no clip), when the worker agrees to share. */
    fun fromCorrection(c: Correction, region: String, appVersion: String): Sample = Sample(
        id = newId(), createdAtMs = c.createdAtMs, lang = c.lang, source = c.source, heard = c.heard,
        understood = "ENTRY " + c.understood, reply = "",
        outcome = if (c.kept == null) Outcome.REJECTED else Outcome.CORRECTED, kept = c.kept,
        region = region, appVersion = appVersion,
    )
}
