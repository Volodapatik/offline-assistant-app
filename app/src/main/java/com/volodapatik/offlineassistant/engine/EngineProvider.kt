package com.volodapatik.offlineassistant.engine

import android.content.Context

object EngineProvider {
    private const val MIN_MODEL_BYTES = 1_000_000

    fun create(context: Context): AssistantEngine {
        val modelReady = isModelLikelyPresent(context)
        val nativeReady = LlamaEngine.isNativeAvailable()
        return if (modelReady && nativeReady) {
            LlamaEngine(context)
        } else {
            SimpleLocalEngine()
        }
    }

    private fun isModelLikelyPresent(context: Context): Boolean {
        return try {
            context.assets.open(EngineConfig.MODEL_ASSET).use { input ->
                input.available() >= MIN_MODEL_BYTES
            }
        } catch (e: Exception) {
            false
        }
    }
}
