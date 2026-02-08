package com.volodapatik.offlineassistant.engine

internal object LlamaNative {
    external fun init(modelPath: String, contextSize: Int, threads: Int): Boolean
    external fun generate(prompt: String, maxTokens: Int): String
    external fun lastError(): String
    external fun reset()
    external fun release()
}
