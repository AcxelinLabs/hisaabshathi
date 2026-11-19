package com.acxelin.hisaabsaathi.recorder

import com.acxelin.hisaabsaathi.core.VoicePrompt
import com.acxelin.hisaabsaathi.core.VoiceSamples
import com.acxelin.hisaabsaathi.core.Xlsx
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One saved recording of one question. */
data class Recording(
    val promptId: String, val file: String, val recordedAt: Long, val durationMs: Long, val place: String,
    val quality: String = "",
    /** What the speaker said, if the agent typed it (own words, or recording anything). */
    val said: String = "",
)

/** One speaker, recorded by one agent on one phone: what the agent filled in, and what was recorded. */
data class Session(
    val id: String,
    val createdAt: Long,
    val agent: String,
    val lang: String,
    val speaker: String,
    val gender: String,
    val age: String,
    val state: String,
    val district: String,
    val place: String,
    val mode: String,
    val planned: List<String>,
    /** Which questions of the list: all, or only the hard ones. */
    val part: String = "all",
    /** The question list: "hisaabsaathi" (built in), a list the agent loaded, or "free" (anything). */
    val set: String = "hisaabsaathi",
    /** "random" or "in_order". */
    val order: String = "in_order",
    val consent: Boolean = false,
    val recordings: List<Recording> = emptyList(),
    val exportedAt: Long? = null,
    val device: String = "",
    val appVersion: String = "",
    /** Which list of questions the session was given (the prompts file's checksum). */
    val promptsVersion: String = "",
) {
    fun recordingFor(promptId: String): Recording? = recordings.lastOrNull { it.promptId == promptId }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("created_at", createdAt); put("agent", agent); put("lang", lang); put("speaker", speaker)
        put("gender", gender); put("age", age); put("state", state); put("district", district); put("place", place); put("mode", mode); put("part", part); put("set", set); put("order", order)
        put("planned", JSONArray(planned)); put("consent", consent); put("exported_at", exportedAt ?: JSONObject.NULL)
        put("device", device); put("app_version", appVersion); put("prompts_version", promptsVersion)
        put("recordings", JSONArray(recordings.map {
            JSONObject().put("prompt_id", it.promptId).put("file", it.file).put("recorded_at", it.recordedAt)
                .put("duration_ms", it.durationMs).put("place", it.place).put("quality", it.quality).put("said", it.said)
        }))
    }

    companion object {
        fun fromJson(o: JSONObject) = Session(
            id = o.getString("id"), createdAt = o.getLong("created_at"), agent = o.optString("agent"), lang = o.getString("lang"),
            speaker = o.getString("speaker"), gender = o.optString("gender"), age = o.optString("age"), state = o.optString("state"),
            district = o.optString("district"), place = o.optString("place"), mode = o.optString("mode"),
            planned = o.getJSONArray("planned").let { a -> List(a.length()) { a.getString(it) } },
            part = o.optString("part", "all"), set = o.optString("set", "hisaabsaathi"), order = o.optString("order", "in_order"),
            consent = o.optBoolean("consent"),
            recordings = o.optJSONArray("recordings")?.let { a ->
                List(a.length()) {
                    val r = a.getJSONObject(it)
                    Recording(r.getString("prompt_id"), r.getString("file"), r.getLong("recorded_at"), r.getLong("duration_ms"), r.optString("place"), r.optString("quality"), r.optString("said"))
                }
            }.orEmpty(),
            exportedAt = if (o.isNull("exported_at")) null else o.optLong("exported_at"),
            device = o.optString("device"), appVersion = o.optString("app_version"), promptsVersion = o.optString("prompts_version"),
        )

        /** "hi-20261119-1405-SABCD": language, date and time, speaker. Sorts by date within a language. */
        fun newId(lang: String, speaker: String, now: Long): String =
            "$lang-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(now)) + "-$speaker"
    }
}

/**
 * Sessions in the app's private storage, one folder each: session.json, consent.wav and the
 * recordings. A recording is on disk before the agent moves to the next question, so closing
 * the app or a flat battery loses at most the one being spoken.
 */
class SessionStore(private val root: File) {
    init { root.mkdirs() }

    fun dir(id: String) = File(root, id)
    fun audioDir(id: String) = File(dir(id), "audio").apply { mkdirs() }
    fun consentFile(id: String) = File(dir(id), "consent.wav")

    fun all(): List<Session> = root.listFiles().orEmpty().mapNotNull { d ->
        runCatching { Session.fromJson(JSONObject(File(d, "session.json").readText())) }.getOrNull()
    }.sortedByDescending { it.createdAt }

    fun load(id: String): Session? = runCatching { Session.fromJson(JSONObject(File(dir(id), "session.json").readText())) }.getOrNull()

    /** Written to a temporary file first, so a crash mid-write never leaves a broken session. */
    fun save(s: Session) {
        dir(s.id).mkdirs()
        val tmp = File(dir(s.id), "session.json.tmp")
        tmp.writeText(s.toJson().toString(2))
        tmp.renameTo(File(dir(s.id), "session.json"))
    }

    /** Saves one recording of [promptId] (replacing an earlier take) and returns the updated session. */
    fun addRecording(s: Session, promptId: String, pcm: ShortArray, place: String, now: Long, said: String = ""): Session {
        // A new take gets the next number ("__2.wav"): the old file is removed only after the new one is written.
        val old = s.recordingFor(promptId)
        val take = (old?.file?.substringAfterLast("__")?.substringBefore(".wav")?.toIntOrNull() ?: 0) + 1
        val name = VoiceSamples.fileName(promptId, s.speaker, take)
        File(audioDir(s.id), name).writeBytes(VoiceSamples.wav(pcm))
        old?.let { File(audioDir(s.id), it.file).delete() }
        val rec = Recording(promptId, name, now, pcm.size * 1000L / VoiceSamples.SAMPLE_RATE, place, VoiceSamples.quality(pcm).name.lowercase(), said.trim())
        val updated = s.copy(recordings = s.recordings.filterNot { it.promptId == promptId } + rec)
        save(updated)
        return updated
    }

    fun delete(id: String) { dir(id).deleteRecursively() }

    /** Bytes the sessions take on the phone. */
    fun bytesUsed(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/**
 * One zip per session, laid out like the shared dataset so it can be uploaded as it is:
 *
 *     data/<lang>/<session>/audio/<question>__<speaker>__<take>.wav
 *     data/<lang>/<session>/metadata.csv      one row per recording (loads as an audio dataset)
 *     data/<lang>/<session>/recordings.xlsx   the same rows, for Excel
 *     data/<lang>/<session>/session.json      who, where, which phone
 *     data/<lang>/<session>/questions.jsonl   the questions this speaker was given
 *     data/<lang>/<session>/consent.wav       the speaker's spoken yes
 */
object SessionExport {
    fun rows(s: Session, prompts: Map<String, VoicePrompt>): List<List<String>> = s.recordings.sortedBy { it.promptId }.map { r ->
        val p = prompts[r.promptId]
        listOf(
            "audio/${r.file}", r.promptId, s.set, s.lang, s.mode, p?.text.orEmpty(), r.said, p?.english.orEmpty(), p?.expect.orEmpty(), s.speaker,
            s.gender, s.age, s.state, s.district, r.place, s.agent, s.id, s.device, s.appVersion,
            Instant.ofEpochMilli(r.recordedAt).toString(), "%.2f".format(java.util.Locale.ROOT, r.durationMs / 1000.0), r.quality,
        )
    }

    fun zip(store: SessionStore, s: Session, prompts: Map<String, VoicePrompt>, outDir: File): File {
        outDir.mkdirs()
        // Old exports are copies: drop those older than a day (a share may still be reading a newer one).
        val dayAgo = System.currentTimeMillis() - 24 * 3600_000L
        outDir.listFiles().orEmpty().filter { it.lastModified() < dayAgo || it.name.endsWith(".part") }.forEach { it.delete() }
        val out = File(outDir, "hisaabsaathi-voices-${s.id}.zip")
        // Written under another name and renamed when complete: a half-written zip never looks finished.
        val part = File(outDir, out.name + ".part")
        val base = "data/${s.lang}/${s.id}/"
        val rows = rows(s, prompts)
        ZipOutputStream(part.outputStream().buffered()).use { z ->
            fun put(name: String, bytes: ByteArray, store: Boolean = false) {
                val e = ZipEntry(name)
                if (store) { e.method = ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = bytes.size.toLong(); e.crc = java.util.zip.CRC32().apply { update(bytes) }.value }
                z.putNextEntry(e); z.write(bytes); z.closeEntry()
            }
            put(base + "metadata.csv", (listOf(VoiceSamples.columns) + rows).joinToString("\n", postfix = "\n") { VoiceSamples.csvLine(it) }.toByteArray())
            put(base + "recordings.xlsx", Xlsx.write("Recordings", VoiceSamples.columns, rows, widths = listOf(34, 12, 14, 6, 10, 40, 30, 40, 22, 8, 8, 8, 14, 14, 12, 12, 26, 18, 8, 22, 8, 8)))
            put(base + "session.json", s.toJson().toString(2).toByteArray())
            // The questions this speaker was given, so the zip is complete even for a list loaded on the phone.
            put(base + "questions.jsonl", s.planned.mapNotNull { prompts[it] }.joinToString("") { p ->
                org.json.JSONObject().put("id", p.id).put("lang", p.lang).put("text", p.text).put("english", p.english).put("expected", p.expect).toString() + "\n"
            }.toByteArray())
            store.consentFile(s.id).takeIf { it.exists() }?.let { put(base + "consent.wav", it.readBytes()) }
            for (r in s.recordings) {
                val f = File(store.audioDir(s.id), r.file)
                if (f.exists()) put(base + "audio/" + r.file, f.readBytes(), store = true)
            }
        }
        out.delete()
        if (!part.renameTo(out)) error("could not save ${out.name}")
        return out
    }
}
