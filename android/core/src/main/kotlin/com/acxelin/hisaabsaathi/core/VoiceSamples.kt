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
    val english: String,
    val expect: String,
    /** What the app should answer, in words: "Money earned, this week". */
    val answer: String,
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
        "file_name", "prompt_id", "lang", "mode", "prompt_text", "english", "expected", "speaker", "gender", "age", "state",
        "district", "place", "agent", "session", "device", "app_version", "recorded_at", "duration_s",
    )

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

    /** The prompts file, one JSON object per line. */
    fun parsePrompts(jsonLines: String): List<VoicePrompt> =
        jsonLines.lineSequence().filter { it.isNotBlank() }.map { json.decodeFromString(VoicePrompt.serializer(), it) }.toList()

    /** The prompts of one language, in order. */
    fun promptsFor(all: List<VoicePrompt>, lang: String): List<VoicePrompt> = all.filter { it.lang == lang }.sortedBy { it.id }

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
