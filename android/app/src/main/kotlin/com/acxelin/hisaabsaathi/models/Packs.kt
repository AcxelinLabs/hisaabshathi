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
import kotlinx.coroutines.ensureActive
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
    /** [waiting]: paused until the phone is online again; it carries on by itself. */
    data class Downloading(val fraction: Float, val waiting: Boolean = false) : PackState
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
    private const val OWN = "$HF/AcxelinLabs/hisaabsaathi-model/resolve/main"

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
    /**
     * NVIDIA Parakeet TDT-CTC 110M (CTC branch), ONNX export by k2-fsa, 8-bit weights by
     * Acxelin Labs. In the app's engine it beat the previous English model on Indian-accented
     * English: Svarah 13.6% vs 16.1% word errors, Karya conversation 53% vs 68%, FLEURS
     * 10.7% vs 11.3%, and runs faster.
     */
    val sttEnglish = Pack(
        id = "stt-en",
        title = "English speech (NVIDIA Parakeet 110M)",
        licence = "CC-BY-4.0 (NVIDIA)",
        files = listOf(
            vad,
            PackFile("$OWN/speech/en-parakeet/tokens.txt", "speech/en-parakeet/tokens.txt", 9_953),
            PackFile("$OWN/speech/en-parakeet/model.int8.onnx", "speech/en-parakeet/model.int8.onnx", 173_887_662),
        ),
    )

    /**
     * Qwen3-0.6B fine-tuned for the app's two model jobs (tools/finetune_compare.py): say what
     * each number in a worker's sentence is, and say whether a sentence the rules did not
     * understand is a ledger question and for which period. Chosen over Gemma 3 1B and
     * Qwen2.5-0.5B trained on the same data (README, "Choosing the language model").
     * Converted to 8-bit LiteRT-LM with litert-torch; in the LiteRT-LM runtime 138/140 held-out
     * corpus prompts right, about 1.1 s an answer on a PC (tools/litertlm_answers.py).
     */
    val slmHisaab: Pack get() = SlmModel.current.let { r ->
        Pack(
            id = SlmModel.ID,
            title = "HisaabSaathi Qwen3-0.6B, fine-tuned (8-bit), v${r.version}",
            licence = "Apache-2.0 (Qwen3); fine-tune by Acxelin Labs",
            files = listOf(PackFile(r.url, "slm/${r.file}", r.bytes)),
        )
    }

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
    /** Indian English (trained on IISc's LIMMITS/SYSPIN voices): optional, the worker downloads it if they want it. */
    val voiceEnglish = voicePack("en", "English", "eea5d4a578c0f18173825a037487cf9c47ed4247", 3039, 17_692_766)

    fun voice(lang: Lang): Pack? = when (lang) {
        Lang.HI -> voiceHindi
        Lang.OR -> voiceOdia
        Lang.TE -> voiceTelugu
        Lang.KN -> voiceKannada
        Lang.TA -> voiceTamil
        Lang.EN -> voiceEnglish
    }

    /** Voices that download by themselves with the speech pack; the others only when the worker asks. */
    fun voiceDownloadsByItself(lang: Lang) = lang != Lang.EN

    val canDownloadSlm: Boolean get() = BuildConfig.HISAAB_MODEL_URL.isNotBlank()

    /** LiteRT-LM ships 64-bit libraries only; 32-bit phones use the rules alone. */
    val slmSupported: Boolean get() = android.os.Process.is64Bit()

    fun stt(lang: Lang): Pack = when (lang) {
        Lang.HI -> sttHindi
        Lang.OR -> sttOdia
        Lang.TE -> sttTelugu
        Lang.KN -> sttKannada
        Lang.TA -> sttTamil
        Lang.EN -> sttEnglish
    }

    val all get() = listOf(sttHindi, sttOdia, sttTelugu, sttKannada, sttTamil, sttEnglish, slmHisaab, voiceHindi, voiceOdia, voiceTelugu, voiceKannada, voiceTamil, voiceEnglish)
}

class PackManager(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val root = File(context.filesDir, "models")
    private val jobs = HashMap<String, Job>()

    init {
        // Which smart model is current (a newer one may have been fetched since the app was built),
        // and nothing else stays in its folder: older models (0.5.x .task, Qwen2.5, a replaced
        // version) and half-done downloads of versions no longer wanted.
        SlmModel.load(File(root, "slm"))
        SlmModel.cleanUp(File(root, "slm"))
        // English moved to Parakeet in 0.6.1; free the old model's 175 MB.
        File(root, "speech/en/model.int8.onnx").delete()
        File(root, "speech/en/tokens.txt").delete()
    }

    private val _states = MutableStateFlow(Catalog.all.associate { it.id to initial(it) })
    val states: StateFlow<Map<String, PackState>> = _states

    init { watchQueuedDownloads() }

    fun file(f: PackFile): File = File(root, f.path)

    fun isReady(pack: Pack): Boolean = pack.files.all { file(it).let { f -> f.exists() && f.length() == it.bytes } }

    private fun initial(pack: Pack): PackState = if (isReady(pack)) PackState.Ready else PackState.Missing

    /** A pack's files changed under it (a new smart model was switched in): show its state afresh. */
    fun refresh(pack: Pack) { _states.value = _states.value + (pack.id to initial(pack)) }

    /** The folder the smart model lives in. */
    val slmDir: File get() = File(root, "slm")

    private fun set(pack: Pack, state: PackState) = _states.update { it + (pack.id to state) }

    /** Bytes on disk so far (finished files and partial downloads). */
    private fun have(f: PackFile): Long = file(f).takeIf { it.exists() }?.length()?.takeIf { it == f.bytes }
        ?: File(file(f).path + ".part").takeIf { it.exists() }?.length()?.coerceAtMost(f.bytes) ?: 0L

    private fun fraction(pack: Pack): Float = pack.files.sumOf { have(it) }.toFloat() / pack.bytes

    /**
     * Starts (or continues) a download as a background job: it keeps going when the app is
     * closed, shows its progress in the notification bar, waits for the internet and resumes
     * where it stopped. It does not start without room for it on the phone.
     */
    fun download(pack: Pack, wifiOnly: Boolean = false) {
        if (isReady(pack)) return
        (_states.value[pack.id] as? PackState.Downloading)?.let { if (!it.waiting) return }
        val need = pack.files.sumOf { it.bytes - have(it) } + SPACE_MARGIN
        val free = root.apply { mkdirs() }.usableSpace
        if (free in 1 until need) return set(pack, PackState.Failed("space:${(need - free) / 1_000_000 + 1}"))
        set(pack, PackState.Downloading(fraction(pack)))
        if (runCatching { PackDownloadWorker.enqueue(appContext, pack.id, wifiOnly) }.isSuccess) return
        if (wifiOnly) return // never on mobile data without the worker asking
        // No background jobs available (should not happen on a phone): download while the app is open.
        jobs[pack.id] = scope.launch(Dispatchers.IO) {
            try { downloadNow(pack) } catch (e: Exception) { set(pack, PackState.Failed(e.message ?: e.javaClass.simpleName)) }
        }
    }

    /** Downloads every file of [pack] now, reporting progress; used by the background job. */
    suspend fun downloadNow(pack: Pack, onProgress: (Float) -> Unit = {}) {
        var doneBefore = 0L
        for (f in pack.files) {
            fetch(f) { got ->
                val p = (doneBefore + got).toFloat() / pack.bytes
                set(pack, PackState.Downloading(p)); onProgress(p)
            }
            doneBefore += f.bytes
        }
        if (!isReady(pack)) error("size mismatch")
        set(pack, PackState.Ready)
    }

    /** Called by the background job when it stops for good, or pauses for the internet. */
    internal fun failed(pack: Pack, why: String) = set(pack, PackState.Failed(why))
    internal fun waiting(pack: Pack) = set(pack, PackState.Downloading(fraction(pack), waiting = true))

    /** After a restart, show downloads that are still queued as in progress. */
    private fun watchQueuedDownloads() {
        val wm = runCatching { androidx.work.WorkManager.getInstance(appContext) }.getOrNull() ?: return
        for (pack in Catalog.all) scope.launch {
            runCatching {
                wm.getWorkInfosForUniqueWorkFlow(PackDownloadWorker.name(pack.id)).collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    if (isReady(pack)) return@collect set(pack, PackState.Ready)
                    when (info.state) {
                        androidx.work.WorkInfo.State.ENQUEUED ->
                            set(pack, PackState.Downloading(fraction(pack), waiting = info.runAttemptCount > 0))
                        androidx.work.WorkInfo.State.RUNNING ->
                            if (_states.value[pack.id] !is PackState.Downloading) set(pack, PackState.Downloading(fraction(pack)))
                        androidx.work.WorkInfo.State.FAILED ->
                            set(pack, PackState.Failed(info.outputData.getString(PackDownloadWorker.KEY_WHY) ?: "failed"))
                        else -> {}
                    }
                }
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
        runCatching { androidx.work.WorkManager.getInstance(appContext).cancelUniqueWork(PackDownloadWorker.name(pack.id)) }
        // Shared files (VAD, tokens) stay if another installed pack still needs them.
        val shared = Catalog.all.filter { it.id != pack.id && isReady(it) }.flatMap { it.files }.map { it.path }.toSet()
        pack.files.filter { it.path !in shared }.forEach { file(it).delete() }
        set(pack, PackState.Missing)
    }

    /** Downloads one file, resuming a partial download if there is one. */
    private suspend fun fetch(f: PackFile, progress: (Long) -> Unit) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val target = file(f)
        if (target.exists() && target.length() == f.bytes) return@withContext
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
                if (code in 400..499) error("HTTP $code") // wrong address: retrying will not help
                if (code != 200 && code != 206) throw java.io.IOException("HTTP $code")
                if (code == 200) have = 0 // server ignored the range: start again
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
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
        if (part.length() != f.bytes) throw java.io.IOException("incomplete download") // resumes on the next try
        if (!part.renameTo(target)) error("could not save file")
        progress(f.bytes)
    }
}

private const val SPACE_MARGIN = 50_000_000L

/**
 * Downloads one pack in the background (WorkManager). Network errors are retried with
 * back-off, so a download that stops on a weak connection carries on when it returns.
 */
class PackDownloadWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val packs = (applicationContext as com.acxelin.hisaabsaathi.HisaabApp).packs
        val pack = Catalog.all.firstOrNull { it.id == inputData.getString(KEY_PACK) } ?: return Result.failure()
        // In the foreground with a notification, Android does not stop a long download; if the
        // phone refuses that (app in the background), it still runs and resumes later if stopped.
        runCatching { setForeground(info(pack, packs.let { 0f })) }
        var lastShown = -1
        return try {
            packs.downloadNow(pack) { p ->
                val pct = (p * 100).toInt()
                if (pct != lastShown) { lastShown = pct; runCatching { notify(pack, p) } }
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 50) { packs.waiting(pack); Result.retry() }
            else fail(packs, pack, e.message ?: "network")
        } catch (e: Exception) {
            fail(packs, pack, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(packs: PackManager, pack: Pack, why: String): Result {
        packs.failed(pack, why)
        return Result.failure(androidx.work.workDataOf(KEY_WHY to why))
    }

    private fun notification(pack: Pack, fraction: Float): android.app.Notification {
        val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW))
        return androidx.core.app.NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(pack.title)
            .setProgress(100, (fraction * 100).toInt(), false)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .build()
    }

    private fun info(pack: Pack, fraction: Float) =
        androidx.work.ForegroundInfo(NOTIFICATION_ID, notification(pack, fraction), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun notify(pack: Pack, fraction: Float) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return
        androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification(pack, fraction))
    }

    companion object {
        const val KEY_PACK = "pack"
        const val KEY_WHY = "why"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 7

        fun name(packId: String) = "pack-$packId"

        /** [wifiOnly]: a download the app starts by itself waits for Wi-Fi; one the worker asks for may use mobile data. */
        fun enqueue(context: Context, packId: String, wifiOnly: Boolean = false) {
            val network = if (wifiOnly) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
            val request = androidx.work.OneTimeWorkRequestBuilder<PackDownloadWorker>()
                .setInputData(androidx.work.workDataOf(KEY_PACK to packId))
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            // A tap on Download replaces a job still waiting for Wi-Fi.
            val policy = if (wifiOnly) androidx.work.ExistingWorkPolicy.KEEP else androidx.work.ExistingWorkPolicy.REPLACE
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(name(packId), policy, request)
        }
    }
}

/**
 * Which version of the smart model the phone uses. Built into the app is v1 (the model the app
 * was tested with); a newer one, named by the team's signed manifest, replaces it on the phone
 * (work/ModelUpdates.kt). The choice is kept in slm/current.json next to the model.
 */
object SlmModel {
    const val ID = "slm-hisaab"

    /** The model this build was tested with, pinned to its Hugging Face commit. */
    val builtIn = com.acxelin.hisaabsaathi.core.ModelRelease(
        id = ID, version = 1, file = "hisaabsaathi-qwen3-ft-q8.litertlm", url = BuildConfig.HISAAB_MODEL_URL,
        bytes = 616_873_984, sha256 = "917030d2567e5ddee1ba53c20d95a575f9ca825aebf2a87e71363f099e2290b2",
    )

    @Volatile var current: com.acxelin.hisaabsaathi.core.ModelRelease = builtIn
        private set

    /** The version being downloaded, if any (its file is kept through clean-ups). */
    @Volatile var pending: String? = null

    const val RECORD = "current.json"

    fun load(dir: File) {
        val saved = runCatching { File(dir, RECORD).readText() }.getOrNull()?.let(com.acxelin.hisaabsaathi.core.ModelUpdates::decodeRelease)
        current = saved?.takeIf { it.id == ID && it.version > builtIn.version && com.acxelin.hisaabsaathi.core.ModelUpdates.acceptable(it) } ?: builtIn
    }

    /** From now on the phone uses [r] (already downloaded and checked). */
    fun switchTo(dir: File, r: com.acxelin.hisaabsaathi.core.ModelRelease) {
        dir.mkdirs()
        val tmp = File(dir, "$RECORD.tmp")
        tmp.writeText(com.acxelin.hisaabsaathi.core.ModelUpdates.encode(r))
        if (!tmp.renameTo(File(dir, RECORD))) { File(dir, RECORD).delete(); tmp.renameTo(File(dir, RECORD)) }
        current = r
    }

    /** Deletes every file in [dir] that is not the current model, the one downloading, or the record. */
    fun cleanUp(dir: File) {
        val names = dir.list()?.toList() ?: return
        for (n in com.acxelin.hisaabsaathi.core.ModelUpdates.leftovers(names, current.file, pending, setOf(RECORD)))
            runCatching { File(dir, n).let { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
    }
}
