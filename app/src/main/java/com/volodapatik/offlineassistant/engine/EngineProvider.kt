package com.volodapatik.offlineassistant.engine

import android.content.Context

data class EngineSelection(
    val engine: AssistantEngine,
    val statusLabel: String,
)

object EngineProvider {
    private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L

    fun create(context: Context): EngineSelection {
        val modelReady = isModelLikelyPresent(context)
        val nativeReady = LlamaEngine.isNativeAvailable()
        return if (modelReady && nativeReady) {
            EngineSelection(
                engine = LlamaEngine(context),
                statusLabel = "LLM: Ready",
            )
        } else {
            EngineSelection(
                engine = SimpleLocalEngine(),
                statusLabel = "LLM: Not found (fallback)",
            )
        }
    }

    private fun isModelLikelyPresent(context: Context): Boolean {
        val assetManager = context.assets
        val directSize = try {
            assetManager.openFd(EngineConfig.MODEL_ASSET).use { it.length }
        } catch (e: Exception) {
            -1L
        }
        if (directSize >= MIN_MODEL_BYTES) {
            return true
        }
        return try {
            assetManager.open(EngineConfig.MODEL_ASSET).use { input ->
                input.available().toLong() >= MIN_MODEL_BYTES
            }
        } catch (e: Exception) {
            false
        }
    }
}
