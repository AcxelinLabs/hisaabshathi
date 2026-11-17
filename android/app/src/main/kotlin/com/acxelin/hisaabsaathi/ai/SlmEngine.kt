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
 * The on-device fine-tuned language model (Qwen2.5-0.5B via MediaPipe/LiteRT), run in a
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
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null

    /** Last failure, for the Settings screen; empty when all is well. */
    var problem: String = prefs.getString("problem", "").orEmpty()
        private set

    private fun installed() = Catalog.slmHisaab.takeIf { packs.isReady(it) }

    private val failures get() = prefs.getInt("failures", 0)

    fun available(): Boolean = installed() != null && failures < 2

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
        val remote = connect() ?: return@withLock null.also { fail("model service did not start") }
        val answer = CompletableDeferred<Bundle>()
        val replies = Messenger(Handler(Looper.getMainLooper()) { m -> answer.complete(m.data); true })
        val msg = Message.obtain(null, LlmService.MSG_GENERATE).apply {
            data = Bundle().apply {
                putString(LlmService.KEY_PATH, packs.file(pack.files.single()).path)
                putString(LlmService.KEY_PROMPT, prompt)
            }
            replyTo = replies
        }
        try {
            remote.send(msg)
        } catch (e: Exception) {
            return@withLock null.also { fail("model process stopped: ${e.javaClass.simpleName}") }
        }
        // First use loads ~500 MB; later answers take a second or two on a mid-range phone.
        val data = withTimeoutOrNull(45_000) { answer.await() }
        when {
            data == null -> null.also { fail(if (service == null) "model process crashed" else "model took too long") }
            data.getString(LlmService.KEY_ERROR) != null -> null.also { fail("model error: ${data.getString(LlmService.KEY_ERROR)}") }
            else -> data.getString(LlmService.KEY_TEXT)
        }
    }

    /** Frees the model's memory (its whole process) before speech recognition loads. */
    suspend fun unload() = lock.withLock { disconnect() }
}
