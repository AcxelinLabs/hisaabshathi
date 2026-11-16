package com.acxelin.hisaabsaathi.models

import android.app.ActivityManager
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

    private const val QWEN = "$HF/litert-community"
    val slmSmall = Pack(
        id = "slm-small",
        title = "Qwen2.5-0.5B-Instruct (8-bit)",
        licence = "Apache-2.0",
        files = listOf(
            PackFile(
                "$QWEN/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                "slm/qwen2.5-0.5b-q8.task", 546_660_344,
            ),
        ),
    )
    val slmLarge = Pack(
        id = "slm-large",
        title = "Qwen2.5-1.5B-Instruct (8-bit)",
        licence = "Apache-2.0",
        files = listOf(
            PackFile(
                "$QWEN/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                "slm/qwen2.5-1.5b-q8.task", 1_597_913_616,
            ),
        ),
    )

    fun stt(lang: Lang): Pack = when (lang) {
        Lang.HI -> sttHindi
        Lang.OR -> sttOdia
        Lang.TE -> sttTelugu
        Lang.KN -> sttKannada
        Lang.TA -> sttTamil
        Lang.EN -> sttEnglish
    }

    /** Bigger model only on phones with 6 GB RAM or more. */
    fun slmFor(context: Context): Pack {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return if (info.totalMem >= 5_500_000_000L) slmLarge else slmSmall
    }

    val all = listOf(sttHindi, sttOdia, sttTelugu, sttKannada, sttTamil, sttEnglish, slmSmall, slmLarge)
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
