package com.acxelin.hisaabsaathi.ai

import android.content.Context
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The on-device small language model (Qwen2.5 via MediaPipe/LiteRT). It labels what the
 * worker said as JSON ([Slm.choose] rejects any answer containing a number that was not
 * spoken) and answers questions the fixed replies do not cover. Returns null whenever the
 * model is missing or fails, and the caller falls back to rules and fixed replies.
 */
class SlmEngine(private val context: Context, private val packs: PackManager) {
    private val lock = Mutex()
    private var llm: LlmInference? = null

    private fun installed() = listOf(Catalog.slmLarge, Catalog.slmSmall).firstOrNull { packs.isReady(it) }

    fun available(): Boolean = com.acxelin.hisaabsaathi.core.Slm.TRUSTED && installed() != null

    suspend fun generate(prompt: String): String? = withContext(Dispatchers.Default) {
        lock.withLock {
            val pack = installed() ?: return@withLock null
            runCatching {
                val engine = llm ?: LlmInference.createFromOptions(
                    context,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(packs.file(pack.files.single()).path)
                        .setMaxTokens(768)
                        .build(),
                ).also { llm = it }
                engine.generateResponse(prompt)
            }.getOrNull()
        }
    }

    suspend fun unload() = lock.withLock {
        llm?.close()
        llm = null
    }
}
