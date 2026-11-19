package com.acxelin.hisaabsaathi.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One question to record (app asset voice_prompts.jsonl, from tools/make_voice_questions.py). */
@kotlinx.serialization.Serializable
data class VoicePrompt(
    val id: String,
    val lang: String,
    val text: String,
    val english: String = "",
    val expect: String = "",
    /** What the app should answer, in words: "Money earned, this week". */
    val answer: String = "",
    /** "everyday" or "hard" in the built-in list; empty in lists loaded by the agent. */
    val group: String = "",
)

/**
 * Files of a voice-sample session (HisaabSaathi Recorder): 16 kHz mono 16-bit WAV, the
 * format the app's own speech models hear, and one metadata.csv row per recording, so a
 * folder of sessions loads directly as an audio dataset ("audiofolder": file_name + columns).
 */
object VoiceSamples {
    const val SAMPLE_RATE = 16_000

    /** One row per recording, in metadata.csv and recordings.xlsx. */
    val columns = listOf(
        "file_name", "prompt_id", "question_set", "lang", "mode", "prompt_text", "said_text", "english", "expected", "speaker", "gender", "age", "state",
        "district", "place", "agent", "session", "device", "app_version", "recorded_at", "duration_s", "quality",
    )

    /** How a recording sounds, checked right after it is made so a bad one is redone on the spot. */
    enum class Quality { GOOD, QUIET, LOUD, SILENT, SHORT }

    /**
     * [Quality] of a recording: SHORT under 0.4 s; SILENT when no 20 ms frame rises above a
     * whisper; QUIET when less than a quarter second is clearly voiced; LOUD when more than
     * 0.1% of samples hit the top (distortion). Thresholds suit a phone held about 20 cm away.
     */
    fun quality(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): Quality {
        if (pcm.size < sampleRate * 4 / 10) return Quality.SHORT
        val frame = sampleRate / 50
        var voiced = 0; var loudest = 0.0; var clipped = 0
        var i = 0
        while (i + frame <= pcm.size) {
            var e = 0.0
            for (k in i until i + frame) {
                val v = pcm[k].toInt()
                if (v >= 32_000 || v <= -32_000) clipped++
                val x = v / 32768.0; e += x * x
            }
            val rms = kotlin.math.sqrt(e / frame)
            if (rms > loudest) loudest = rms
            if (rms > 0.015) voiced++
            i += frame
        }
        return when {
            loudest < 0.006 -> Quality.SILENT
            voiced * 20 < 250 -> Quality.QUIET
            clipped * 1000 > pcm.size -> Quality.LOUD
            else -> Quality.GOOD
        }
    }

    /** "hi_017__S7K3Q__1.wav": question, speaker code, take. */
    fun fileName(promptId: String, speaker: String, take: Int): String = "${promptId}__${speaker}__$take.wav"

    /** A short code for a speaker: no names are stored. */
    fun speakerCode(random: kotlin.random.Random = kotlin.random.Random.Default): String {
        val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I
        return "S" + (1..4).map { letters[random.nextInt(letters.length)] }.joinToString("")
    }

    /** A CSV line with RFC 4180 quoting (sentences contain commas and quotes). */
    fun csvLine(values: List<String>): String = values.joinToString(",") { v ->
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /** Reads one CSV line written by [csvLine]. */
    fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /** A WAV file of [pcm] samples. */
    fun wav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { pcm.forEach { putShort(it) } }.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        return ByteArrayOutputStream(44 + data.size).apply { write(header); write(data) }.toByteArray()
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** One prompt as a line of the prompts file. */
    fun promptLine(p: VoicePrompt): String = json.encodeToString(VoicePrompt.serializer(), p)

    /** The prompts file, one JSON object per line. */
    fun parsePrompts(jsonLines: String): List<VoicePrompt> =
        jsonLines.lineSequence().filter { it.isNotBlank() }.map { json.decodeFromString(VoicePrompt.serializer(), it) }.toList()

    /** The number in an id: "hi_017" is 17, "hi_1000" is 1000. */
    fun number(id: String): Int = id.substringAfterLast('_').toIntOrNull() ?: 0

    /** The prompts of one language in order (a prompt with no language suits every language). */
    fun promptsFor(all: List<VoicePrompt>, lang: String): List<VoicePrompt> =
        all.filter { it.lang == lang || it.lang.isEmpty() }.sortedWith(compareBy({ number(it.id) }, { it.id }))

    /**
     * [count] questions at random, preferring those recorded least so far ([recorded]: times
     * each id was recorded on this phone), so random speakers still cover the whole list.
     */
    fun randomBatch(prompts: List<VoicePrompt>, recorded: Map<String, Int>, count: Int, random: kotlin.random.Random = kotlin.random.Random.Default): List<VoicePrompt> =
        prompts.shuffled(random).sortedBy { recorded[it.id] ?: 0 }.take(count)

    private val safeId = Regex("[^A-Za-z0-9_-]")

    /** A name usable in a file name: letters, digits, "_" and "-". */
    fun slug(s: String): String = s.trim().replace(Regex("\\s+"), "-").replace(safeId, "").take(40).ifEmpty { "list" }

    /**
     * A question list the agent loads from the phone (from WhatsApp, Drive...), named [setId]:
     * - .jsonl: one {"text": ..., "lang"?, "id"?, "english"?, "expect"?} per line;
     * - .csv / .tsv: a header with a "text" (or "sentence", "question") column and optional
     *   lang, id, english, expected columns; without such a header, the first column is the text;
     * - anything else: one sentence per line.
     * A row without a language suits every language. Ids are made when missing.
     */
    fun parseQuestionList(content: String, fileName: String, setId: String): List<VoicePrompt> {
        val lines = content.removePrefix("\uFEFF").lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        val name = fileName.lowercase()
        // The phone may not say the file's name: a first line like "text,lang" still means CSV.
        val headerLike = lines.firstOrNull().orEmpty().lowercase().split(',', '\t').map { it.trim() }
            .any { it in setOf("text", "sentence", "question", "prompt_text", "prompt") }
        val csvByContent = headerLike && !name.endsWith(".jsonl") && !name.endsWith(".json") && !name.endsWith(".tsv")
        val raw: List<Map<String, String>> = when {
            name.endsWith(".jsonl") || name.endsWith(".json") -> lines.mapNotNull { l ->
                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(l) as kotlinx.serialization.json.JsonObject }.getOrNull()
                    ?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty() }
            }
            name.endsWith(".csv") || name.endsWith(".tsv") || csvByContent -> {
                val split: (String) -> List<String> = if (name.endsWith(".tsv")) { l -> l.split('\t') } else ::parseCsvLine
                val head = split(lines.firstOrNull().orEmpty()).map { it.trim().lowercase() }
                val textCol = head.indexOfFirst { it in setOf("text", "sentence", "question", "prompt_text", "prompt") }
                if (textCol < 0) lines.map { mapOf("text" to split(it).first()) }
                else lines.drop(1).map { l -> val cells = split(l); head.indices.associate { i -> head[i] to cells.getOrElse(i) { "" } } + ("text" to cells.getOrElse(textCol) { "" }) }
            }
            else -> lines.map { mapOf("text" to it.trim()) }
        }
        val used = HashSet<String>()
        return raw.mapIndexedNotNull { i, r ->
            val text = r["text"].orEmpty().trim()
            if (text.isEmpty()) return@mapIndexedNotNull null
            var id = r["id"]?.let(::slug)?.takeIf { it.isNotEmpty() && it != "list" } ?: "${setId}_%03d".format(i + 1)
            while (!used.add(id)) id += "x"
            VoicePrompt(
                id = id, lang = r["lang"].orEmpty().trim().lowercase(), text = text, english = r["english"].orEmpty().trim(),
                expect = (r["expected"] ?: r["expect"]).orEmpty().trim(),
            )
        }
    }

    /**
     * The [count] questions for a new speaker, starting where earlier speakers of the language
     * stopped ([alreadyRecorded] recordings so far), so speakers together cover every question.
     */
    fun nextBatch(prompts: List<VoicePrompt>, alreadyRecorded: Int, count: Int): List<VoicePrompt> {
        if (prompts.isEmpty()) return emptyList()
        val start = alreadyRecorded % prompts.size
        return List(minOf(count, prompts.size)) { prompts[(start + it) % prompts.size] }
    }
}
