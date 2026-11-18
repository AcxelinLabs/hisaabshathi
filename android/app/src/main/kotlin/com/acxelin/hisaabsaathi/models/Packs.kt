package com.acxelin.hisaabsaathi.models

import com.acxelin.hisaabsaathi.BuildConfig
import android.content.Context
import com.acxelin.hisaabsaathi.core.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

data class PackFile(val url: String, val path: String, val bytes: Long)

data class Pack(val id: String, val title: String, val licence: String, val files: List<PackFile>) {
    val bytes: Long get() = files.sumOf { it.bytes }
}

sealed interface PackState {
    data object Missing : PackState
    data class Downloading(val fraction: Float) : PackState
    data object Ready : PackState
    data class Failed(val message: String) : PackState
}

/**
 * Offline model packs. They are fetched once from Hugging Face, kept in app-private
 * storage, and everything afterwards runs with no network.
 */
object Catalog {
    private const val HF = "https://huggingface.co"
    private const val STT = "$HF/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"

    // Shared by every speech pack; listed in each so any one pack is complete on its own.
    private val vad = PackFile("$HF/csukuangfj/vad/resolve/main/silero_vad_v5.onnx", "speech/silero_vad_v5.onnx", 2_313_101)
    private val indicTokens = PackFile("$STT/tokens.txt", "speech/tokens.txt", 67_605)

    private fun indic(code: String, name: String, bytes: Long) = Pack(
        id = "stt-$code",
        title = "$name speech (AI4Bharat IndicConformer)",
        licence = "MIT (AI4Bharat); ONNX export Apache-2.0",
        files = listOf(vad, indicTokens, PackFile("$STT/$code/model.int8.onnx", "speech/$code/model.int8.onnx", bytes)),
    )

    val sttHindi = indic("hi", "Hindi", 197_595_593)
    val sttOdia = indic("or", "Odia", 197_584_928)
    val sttTelugu = indic("te", "Telugu", 197_595_693)
    val sttKannada = indic("kn", "Kannada", 197_595_728)
    val sttTamil = indic("ta", "Tamil", 197_595_513)
    val sttEnglish = Pack(
        id = "stt-en",
        title = "English speech (NVIDIA NeMo fast-conformer)",
        licence = "CC-BY-4.0 (NVIDIA)",
        files = listOf(
            vad,
            PackFile("$STT/en/tokens.txt", "speech/en/tokens.txt", 11_433),
            PackFile("$STT/en/model.int8.onnx", "speech/en/model.int8.onnx", 174_610_057),
        ),
    )

    /**
     * Qwen2.5-0.5B-Instruct fine-tuned to label the numbers in a worker's sentence
     * (tools/finetune_slm.py), converted to 8-bit LiteRT and packed for MediaPipe
     * (tools/make_task.py). 133/140 held-out corpus prompts labelled right before
     * conversion, 37/40 after.
     */
    val slmHisaab = Pack(
        id = "slm-hisaab",
        title = "HisaabSaathi Qwen2.5-0.5B, fine-tuned (8-bit)",
        licence = "Apache-2.0 (Qwen2.5); fine-tune by Acxelin Labs",
        files = listOf(PackFile(BuildConfig.HISAAB_MODEL_URL, "slm/hisaabsaathi-qwen05-ft-q8.task", 513_718_731)),
    )

    /**
     * Indic Tiny TTS voices (female and male) by Sulabh Katiyar, CC-BY-4.0, trained on
     * AI4Bharat Rasa (CC-BY-4.0); vocoder Vocos (MIT). Pinned to the tested revision.
     * English uses the phone's own voice.
     */
    private fun voicePack(code: String, name: String, rev: String, config: Long, encoder: Long) = Pack(
        id = "voice-$code",
        title = "${name} voice (Indic Tiny TTS)",
        licence = "CC-BY-4.0 (Sulabh Katiyar; AI4Bharat Rasa); vocoder MIT",
        files = listOf(
            PackFile("$HF/sulabhkatiyar/indic-tiny-tts-${name.lowercase()}/resolve/$rev/config.json", "voice/$code/config.json", config),
            PackFile("$HF/sulabhkatiyar/indic-tiny-tts-${name.lowercase()}/resolve/$rev/onnx/text_encoder.onnx", "voice/$code/onnx/text_encoder.onnx", encoder),
            PackFile("$HF/sulabhkatiyar/indic-tiny-tts-${name.lowercase()}/resolve/$rev/onnx/flow_decoder.onnx", "voice/$code/onnx/flow_decoder.onnx", 19_169_253),
            PackFile("$HF/sulabhkatiyar/indic-tiny-tts-${name.lowercase()}/resolve/$rev/onnx/vocoder.onnx", "voice/$code/onnx/vocoder.onnx", 54_150_036),
        ),
    )
    val voiceHindi = voicePack("hi", "Hindi", "1fb1b108ee6ffb73ee74bd68b283ec13eb39e957", 3443, 17_695_838)
    val voiceOdia = voicePack("or", "Odia", "cc5e90366f144ddbf50a3ea99dd832f6ac839b19", 3269, 17_693_790)
    val voiceTelugu = voicePack("te", "Telugu", "fb552545f06d76d7b5d6365827b69c0bda465767", 3216, 17_692_254)
    val voiceKannada = voicePack("kn", "Kannada", "73714eb01cdebc79367f7d1ddf2f4c73adff2be6", 3358, 17_696_862)
    val voiceTamil = voicePack("ta", "Tamil", "c3a32ece466031c8a18e72047ab37f5c143cd2b0", 3087, 17_687_134)

    fun voice(lang: Lang): Pack? = when (lang) {
        Lang.HI -> voiceHindi
        Lang.OR -> voiceOdia
        Lang.TE -> voiceTelugu
        Lang.KN -> voiceKannada
        Lang.TA -> voiceTamil
        Lang.EN -> null
    }

    val canDownloadSlm: Boolean get() = BuildConfig.HISAAB_MODEL_URL.isNotBlank()

    fun stt(lang: Lang): Pack = when (lang) {
        Lang.HI -> sttHindi
        Lang.OR -> sttOdia
        Lang.TE -> sttTelugu
        Lang.KN -> sttKannada
        Lang.TA -> sttTamil
        Lang.EN -> sttEnglish
    }

    val all = listOf(sttHindi, sttOdia, sttTelugu, sttKannada, sttTamil, sttEnglish, slmHisaab, voiceHindi, voiceOdia, voiceTelugu, voiceKannada, voiceTamil)
}

class PackManager(context: Context, private val scope: CoroutineScope) {
    private val root = File(context.filesDir, "models")
    private val jobs = HashMap<String, Job>()

    private val _states = MutableStateFlow(Catalog.all.associate { it.id to initial(it) })
    val states: StateFlow<Map<String, PackState>> = _states

    fun file(f: PackFile): File = File(root, f.path)

    fun isReady(pack: Pack): Boolean = pack.files.all { file(it).let { f -> f.exists() && f.length() == it.bytes } }

    private fun initial(pack: Pack): PackState = if (isReady(pack)) PackState.Ready else PackState.Missing

    private fun set(pack: Pack, state: PackState) = _states.update { it + (pack.id to state) }

    fun download(pack: Pack) {
        if (jobs[pack.id]?.isActive == true || isReady(pack)) return
        set(pack, PackState.Downloading(0f))
        jobs[pack.id] = scope.launch(Dispatchers.IO) {
            try {
                var doneBefore = 0L
                for (f in pack.files) {
                    fetch(f) { got -> set(pack, PackState.Downloading((doneBefore + got).toFloat() / pack.bytes)) }
                    doneBefore += f.bytes
                }
                set(pack, if (isReady(pack)) PackState.Ready else PackState.Failed("size mismatch"))
            } catch (e: Exception) {
                set(pack, PackState.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /**
     * Copies a single-file pack the worker or field team picked on the phone (for example
     * a model shared over Bluetooth or a USB cable) into app storage. Rejects a file whose
     * size does not match, so a wrong or half-copied file is never loaded.
     */
    fun importFrom(context: Context, pack: Pack, uri: android.net.Uri) {
        val f = pack.files.single()
        if (jobs[pack.id]?.isActive == true) return
        set(pack, PackState.Downloading(0f))
        jobs[pack.id] = scope.launch(Dispatchers.IO) {
            val target = file(f)
            val part = File(target.path + ".part")
            try {
                target.parentFile?.mkdirs()
                var copied = 0L
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "cannot open file" }
                    part.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (scope.isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); copied += n
                            if (copied % (8L shl 20) < n) set(pack, PackState.Downloading(copied.toFloat() / f.bytes))
                        }
                    }
                }
                if (copied != f.bytes) error("wrong file")
                if (!part.renameTo(target)) error("could not save file")
                set(pack, PackState.Ready)
            } catch (e: Exception) {
                part.delete()
                set(pack, PackState.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun delete(pack: Pack) {
        jobs.remove(pack.id)?.cancel()
        // Shared files (VAD, tokens) stay if another installed pack still needs them.
        val shared = Catalog.all.filter { it.id != pack.id && isReady(it) }.flatMap { it.files }.map { it.path }.toSet()
        pack.files.filter { it.path !in shared }.forEach { file(it).delete() }
        set(pack, PackState.Missing)
    }

    /** Downloads one file, resuming a partial download if there is one. */
    private suspend fun fetch(f: PackFile, progress: (Long) -> Unit) {
        val target = file(f)
        if (target.exists() && target.length() == f.bytes) return
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        var have = if (part.exists()) part.length() else 0L
        if (have > f.bytes) { part.delete(); have = 0 }
        if (have < f.bytes) {
            val conn = URL(f.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            try {
                val code = conn.responseCode
                if (code != 200 && code != 206) error("HTTP $code")
                if (code == 200) have = 0 // server ignored the range: start again
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (scope.isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            if (have - lastReport > 512 * 1024) { progress(have); lastReport = have }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        if (part.length() != f.bytes) error("incomplete download")
        if (!part.renameTo(target)) error("could not save file")
        progress(f.bytes)
    }
}
