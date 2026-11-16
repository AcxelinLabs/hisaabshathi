package com.acxelin.hisaabsaathi.ai

import android.content.Context
import com.acxelin.hisaabsaathi.core.Slm
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The on-device small language model (Qwen2.5 via MediaPipe/LiteRT). It is asked only to
 * label what the worker said as JSON; [Slm.choose] then rejects any answer containing a
 * number that was not spoken. Returns null whenever the model is missing or fails, and the
 * caller keeps the rule-based result.
 */
class SlmEngine(private val context: Context, private val packs: PackManager) {
    private val lock = Mutex()
    private var llm: LlmInference? = null

    private fun installed() = listOf(Catalog.slmLarge, Catalog.slmSmall).firstOrNull { packs.isReady(it) }

    fun available(): Boolean = installed() != null

    suspend fun label(transcript: String): String? = withContext(Dispatchers.Default) {
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
                engine.generateResponse(Slm.prompt(transcript))
            }.getOrNull()
        }
    }

    suspend fun unload() = lock.withLock {
        llm?.close()
        llm = null
    }
}
