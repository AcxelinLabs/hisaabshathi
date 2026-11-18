package com.acxelin.hisaabsaathi.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Player for the Indic Tiny TTS voices (Matcha acoustic model + Vocos vocoder, CC-BY-4.0 /
 * MIT, by Sulabh Katiyar, trained on AI4Bharat Rasa). Reads only a voice's data files:
 * config.json and onnx/{text_encoder,flow_decoder,vocoder}.onnx. Same steps as the tested
 * reference tools/tts/tiny_tts_ref.py: characters -> text encoder -> durations -> length
 * regulation -> 4-step Euler flow -> mel -> vocoder -> inverse STFT.
 */
class TinyTts(dir: File, threads: Int = 2) : AutoCloseable {
    private val cfg = Json.parseToJsonElement(File(dir, "config.json").readText()).jsonObject
    val sampleRate: Int = cfg["sample_rate"]!!.jsonPrimitive.int
    /** "female" / "male" -> speaker name. */
    val voices: Map<String, String> = cfg["voices"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val vocab: Map<Char, Long> = cfg["vocab"]!!.jsonObject["symbols"]!!.jsonArray.withIndex()
        .mapNotNull { (i, s) -> s.jsonPrimitive.content.takeIf { it.startsWith("c:") && it.length == 3 }?.let { it[2] to i.toLong() } }.toMap()
    private val syn = cfg["synthesis"]!!.jsonObject
    private val steps = syn["n_timesteps"]!!.jsonPrimitive.int
    private val temperature = syn["temperature"]!!.jsonPrimitive.content.toFloat()
    private val pause = syn["sentence_pause_s"]?.jsonPrimitive?.content?.toFloat() ?: 0.15f
    private val lengthScale = syn["length_scale"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content.toFloat() }
    private val melMean: Float; private val melStd: Float
    private val nFft: Int; private val hop: Int
    private val ids = cfg["ids"]!!.jsonObject
    private val env = OrtEnvironment.getEnvironment()
    private val sessions: Map<String, OrtSession>

    init {
        val stats = cfg["mel_stats"]!!.jsonArray
        melMean = stats[0].jsonPrimitive.content.toFloat(); melStd = stats[1].jsonPrimitive.content.toFloat()
        val head = cfg["vocoder"]!!.jsonObject["head"]!!.jsonObject
        nFft = head["n_fft"]!!.jsonPrimitive.int; hop = head["hop_length"]!!.jsonPrimitive.int
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads); setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        sessions = listOf("text_encoder", "flow_decoder", "vocoder").associateWith {
            env.createSession(File(dir, "onnx/$it.onnx").path, opts)
        }
    }

    /** The exact characters the model reads: numbers spelled out, unknown symbols dropped. */
    fun normalize(text: String, lang: Lang): String {
        val t = Normalizer.normalize(NumberSpeller.spellNumbers(text, lang), Normalizer.Form.NFC)
        val sb = StringBuilder()
        for (c in t) sb.append(
            when {
                c in vocab -> c
                Character.getType(c).let { it == Character.OTHER_PUNCTUATION.toInt() || it == Character.DASH_PUNCTUATION.toInt() } ->
                    if (',' in vocab) ',' else ' '
                else -> ' '
            },
        )
        return sb.toString().replace(Regex(" +"), " ").trim()
    }

    fun chunks(norm: String): List<String> =
        norm.split(Regex("(?<=[.?!।|])\\s+")).map { it.trim() }.filter { s -> s.any { it.isLetter() } }

    /**
     * Speech for [text] as float samples in [-1, 1] at [sampleRate]. [noise] supplies the
     * random starting point (tests pass the reference's noise to compare outputs exactly).
     */
    fun synthesize(text: String, lang: Lang, voice: String = "female", seed: Long = 0, noise: ((Int) -> FloatArray)? = null): FloatArray {
        val spk = voices[voice] ?: voices.values.first()
        val rng = java.util.Random(seed)
        val draw = noise ?: { n -> FloatArray(n) { rng.nextGaussian().toFloat() } }
        val out = ArrayList<FloatArray>()
        for (c in chunks(normalize(text, lang))) {
            if (out.isNotEmpty()) out += FloatArray((pause * sampleRate).toInt())
            out += synthChunk(c, spk, draw)
        }
        val all = FloatArray(out.sumOf { it.size }); var p = 0
        for (a in out) { a.copyInto(all, p); p += a.size }
        return all
    }

    private fun longTensor(v: LongArray, shape: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(v), shape)
    private fun floatTensor(v: FloatArray, shape: LongArray) = OnnxTensor.createTensor(env, FloatBuffer.wrap(v), shape)

    private fun synthChunk(chunk: String, spk: String, draw: (Int) -> FloatArray): FloatArray {
        val sym = chunk.map { vocab.getValue(it) }
        val x = LongArray(2 * sym.size + 1).also { a -> sym.forEachIndexed { i, s -> a[2 * i + 1] = s } }
        val speakers = ids["speakers"]!!.jsonObject
        val encIn = mapOf(
            "tokens" to longTensor(x, longArrayOf(1, x.size.toLong())),
            "token_lengths" to longTensor(longArrayOf(x.size.toLong()), longArrayOf(1)),
            "language" to longTensor(longArrayOf(ids["language"]!!.jsonPrimitive.content.toLong()), longArrayOf(1)),
            "speaker" to longTensor(longArrayOf(speakers[spk]!!.jsonPrimitive.content.toLong()), longArrayOf(1)),
            "style" to longTensor(longArrayOf(ids["styles"]!!.jsonObject["read"]!!.jsonPrimitive.content.toLong()), longArrayOf(1)),
        )
        val (muX, logW, cond, channels) = sessions.getValue("text_encoder").run(encIn).use { r ->
            val mu = r.get(0) as OnnxTensor; val lw = r.get(1) as OnnxTensor; val cd = r.get(2) as OnnxTensor
            val c = mu.info.shape[1].toInt()
            Quad(mu.floatBuffer.toArray(), lw.floatBuffer.toArray(), cd.floatBuffer.toArray() to cd.info.shape, c)
        }
        encIn.values.forEach { it.close() }
        val tx = x.size
        // durations
        val ls = lengthScale[spk] ?: 1f
        val w = FloatArray(tx) { (ceil(exp(logW[it].toDouble())).toFloat()) * ls }
        val yLen = max(w.fold(0.0) { a, b -> a + b }.toFloat(), 1f).toInt()
        val yPad = (yLen + 1) / 2 * 2
        // length regulation: frame j copies token i iff cum[i-1] <= j < cum[i]
        val cum = FloatArray(tx).also { var s = 0.0; for (i in 0 until tx) { s += w[i]; it[i] = s.toFloat() } }
        val muY = FloatArray(channels * yPad); val mask = FloatArray(yPad)
        var tok = 0
        for (j in 0 until yPad) {
            while (tok < tx && cum[tok] <= j) tok++
            if (tok < tx && j < yLen) {
                mask[j] = 1f
                for (c in 0 until channels) muY[c * yPad + j] = muX[c * tx + tok]
            }
        }
        // Euler flow from temperature-scaled noise
        val z = draw(channels * yPad)
        var xs = FloatArray(z.size) { z[it] * temperature }
        val dt = 1f / steps; var t = 0f
        val flow = sessions.getValue("flow_decoder")
        val condT = floatTensor(cond.first, cond.second); val muT = floatTensor(muY, longArrayOf(1, channels.toLong(), yPad.toLong()))
        val maskT = floatTensor(mask, longArrayOf(1, 1, yPad.toLong()))
        repeat(steps) {
            val xT = floatTensor(xs, longArrayOf(1, channels.toLong(), yPad.toLong())); val tT = floatTensor(floatArrayOf(t), longArrayOf(1))
            val v = flow.run(mapOf("x" to xT, "mask" to maskT, "mu" to muT, "t" to tT, "cond" to condT)).use { (it.get(0) as OnnxTensor).floatBuffer.toArray() }
            xT.close(); tT.close()
            xs = FloatArray(xs.size) { i -> xs[i] + dt * v[i] }
            t += dt
        }
        condT.close(); muT.close(); maskT.close()
        // mel = x * std + mean, first yLen frames
        val mel = FloatArray(channels * yLen) { k -> val c = k / yLen; val j = k % yLen; xs[c * yPad + j] * melStd + melMean }
        val melT = floatTensor(mel, longArrayOf(1, channels.toLong(), yLen.toLong()))
        val (re, im, bins, frames) = sessions.getValue("vocoder").run(mapOf("mel" to melT)).use { r ->
            val a = r.get(0) as OnnxTensor; val b = r.get(1) as OnnxTensor
            Quad(a.floatBuffer.toArray(), b.floatBuffer.toArray(), a.info.shape[1].toInt(), a.info.shape[2].toInt())
        }
        melT.close()
        return Istft.inverse(re, im, bins, frames, nFft, hop).also { a -> for (i in a.indices) a[i] = a[i].coerceIn(-1f, 1f) }
    }

    override fun close() { sessions.values.forEach { it.close() } }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private fun FloatBuffer.toArray(): FloatArray { val a = FloatArray(remaining()); get(a); return a }
}

/** Inverse short-time Fourier transform with a periodic Hann window and centre padding (torch.istft). */
object Istft {
    fun inverse(re: FloatArray, im: FloatArray, bins: Int, frames: Int, nFft: Int, hop: Int): FloatArray {
        val win = DoubleArray(nFft) { 0.5 - 0.5 * cos(2 * PI * it / nFft) }
        val total = nFft + hop * (frames - 1)
        val y = DoubleArray(total); val envl = DoubleArray(total)
        val fr = DoubleArray(nFft); val fi = DoubleArray(nFft)
        for (t in 0 until frames) {
            // full conjugate-symmetric spectrum from the half spectrum
            for (k in 0 until bins) { fr[k] = re[k * frames + t].toDouble(); fi[k] = im[k * frames + t].toDouble() }
            for (k in bins until nFft) { fr[k] = fr[nFft - k]; fi[k] = -fi[nFft - k] }
            fi[0] = 0.0; if (nFft % 2 == 0) fi[nFft / 2] = 0.0
            fft(fr, fi, inverse = true)
            for (n in 0 until nFft) {
                val v = fr[n] / nFft * win[n]
                y[t * hop + n] += v; envl[t * hop + n] += win[n] * win[n]
            }
        }
        val pad = nFft / 2
        return FloatArray(total - 2 * pad) { i -> (y[i + pad] / max(envl[i + pad], 1e-11)).toFloat() }
    }

    /** In-place radix-2 complex FFT (n a power of two). */
    fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) { var tmp = re[i]; re[i] = re[j]; re[j] = tmp; tmp = im[i]; im[i] = im[j]; im[j] = tmp }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * if (inverse) 1 else -1
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
