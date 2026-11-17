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
 * The on-device fine-tuned language model (Qwen2.5-0.5B via MediaPipe/LiteRT). It is asked
 * only to say what each number in a sentence is; [Slm.parse] rejects any answer that does
 * not label exactly the numbers that were spoken. It is consulted only when the rules were
 * unsure or found nothing, so it can never override a confident rule result. Returns null
 * whenever the model is missing or fails, and the caller keeps the rule result.
 */
class SlmEngine(private val context: Context, private val packs: PackManager) {
    private val lock = Mutex()
    private var llm: LlmInference? = null

    private fun installed() = Catalog.slmHisaab.takeIf { packs.isReady(it) }

    fun available(): Boolean = installed() != null

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
