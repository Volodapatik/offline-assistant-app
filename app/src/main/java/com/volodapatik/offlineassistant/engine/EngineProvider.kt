package com.volodapatik.offlineassistant.engine

import android.content.Context

data class EngineSelection(
    val engine: AssistantEngine,
    val statusLabel: String,
)

object EngineProvider {
    private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L

    fun create(context: Context): EngineSelection {
        val modelReady = hasLocalModel(context)
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

    fun hasLocalModel(context: Context): Boolean {
        val modelFile = LlamaEngine.getLocalModelFile(context)
        return modelFile.exists() && modelFile.length() >= MIN_MODEL_BYTES
    }
}
