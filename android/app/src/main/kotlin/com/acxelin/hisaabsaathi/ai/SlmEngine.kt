package com.acxelin.hisaabsaathi.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The on-device fine-tuned language model (Qwen3-0.6B via LiteRT-LM), run in a
 * separate process by [LlmService] so a crash there cannot close the app.
 *
 * It is asked only to say what each number in a sentence is; [com.acxelin.hisaabsaathi.core.Slm.parse]
 * rejects any answer that does not label exactly the numbers that were spoken. It is
 * consulted only when the rules were unsure or found nothing, so it can never override a
 * confident rule result. [generate] returns null whenever the model is missing, slow or
 * fails; the caller keeps the rule result. After two failures on a phone it is switched
 * off for good on that phone, and [problem] says why.
 */
class SlmEngine(private val context: Context, private val packs: PackManager) {
    private val lock = Mutex()
    private val prefs = context.getSharedPreferences("slm", Context.MODE_PRIVATE)
        .also { com.acxelin.hisaabsaathi.speech.resetAfterUpdate(it) }
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null

    /** Last failure, for the Settings screen; empty when all is well. */
    var problem: String = prefs.getString("problem", "").orEmpty()
        private set

    private fun installed() = Catalog.slmHisaab.takeIf { Catalog.slmSupported && packs.isReady(it) }

    private val failures get() = prefs.getInt("failures", 0)

    fun available(): Boolean = installed() != null && failures < 2

    /**
     * Loading the model needs about 1 GB at first (weights are repacked, then cached). On a
     * busy 3–4 GB phone that can make Android close the app, so below this much free memory
     * the rules answer alone. Not counted as a failure: it may work next time.
     */
    private fun enoughMemory(): Boolean = runCatching {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        !mi.lowMemory && mi.availMem > MIN_FREE_BYTES
    }.getOrDefault(true)

    private fun fail(why: String) {
        problem = why
        prefs.edit().putInt("failures", failures + 1).putString("problem", why).apply()
        disconnect()
    }

    private suspend fun connect(): Messenger? {
        service?.let { return it }
        val ready = CompletableDeferred<Messenger?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                ready.complete(binder?.let(::Messenger)); service = binder?.let(::Messenger)
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
            override fun onBindingDied(name: ComponentName?) { service = null }
        }
        if (!context.bindService(Intent(context, LlmService::class.java), conn, Context.BIND_AUTO_CREATE)) return null
        connection = conn
        return withTimeoutOrNull(10_000) { ready.await() }
    }

    private fun disconnect() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        service = null
    }

    suspend fun generate(prompt: String): String? = lock.withLock {
        val pack = installed() ?: return@withLock null
        if (failures >= 2) return@withLock null
        if (service == null && !enoughMemory()) return@withLock null   // not loaded yet and memory is tight
        ask(packs.file(pack.files.single()).path, prompt, countFailures = true)
    }

    /**
     * A downloaded model, before the phone switches to it: does it load here and answer a known
     * sentence the way the app needs? true / false; null when it could not be tried now (memory
     * is tight): try again later. Never counts against the model in use.
     */
    suspend fun tryModel(path: String): Boolean? = lock.withLock {
        if (!enoughMemory()) return@withLock null
        val sentence = "आज आठ घंटे काम किया 600 रुपये मिले"
        val prompt = com.acxelin.hisaabsaathi.core.Slm.prompt(sentence) ?: return@withLock null
        val answer = ask(path, prompt, countFailures = false)
        disconnect() // free its memory; the model in use loads again when next needed
        answer != null && com.acxelin.hisaabsaathi.core.Slm.parse(answer, sentence) != null
    }

    /** A new model was switched in: it gets a fresh start (earlier failures were the old one's). */
    fun newModel() {
        prefs.edit().putInt("failures", 0).putString("problem", "").apply()
        problem = ""
    }

    private suspend fun ask(path: String, prompt: String, countFailures: Boolean): String? {
        fun failed(why: String): String? { if (countFailures) fail(why) else disconnect(); return null }
        val remote = connect() ?: return failed("model service did not start")
        val answer = CompletableDeferred<Bundle>()
        val replies = Messenger(Handler(Looper.getMainLooper()) { m -> answer.complete(m.data); true })
        val msg = Message.obtain(null, LlmService.MSG_GENERATE).apply {
            data = Bundle().apply {
                putString(LlmService.KEY_PATH, path)
                putString(LlmService.KEY_PROMPT, prompt)
            }
            replyTo = replies
        }
        try {
            remote.send(msg)
        } catch (e: Exception) {
            return failed("model process stopped: ${e.javaClass.simpleName}")
        }
        // First use loads ~500 MB; later answers take a second or two on a mid-range phone.
        val data = withTimeoutOrNull(45_000) { answer.await() }
        return when {
            data == null -> failed(if (service == null) "model process crashed" else "model took too long")
            data.getString(LlmService.KEY_ERROR) != null -> failed("model error: ${data.getString(LlmService.KEY_ERROR)}")
            else -> data.getString(LlmService.KEY_TEXT)
        }
    }

    /** Frees the model's memory (its whole process) before speech recognition loads. */
    suspend fun unload() = lock.withLock { disconnect() }

    private companion object {
        const val MIN_FREE_BYTES = 1_500_000_000L
    }
}
