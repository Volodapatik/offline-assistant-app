package com.volodapatik.offlineassistant.engine

import android.content.Context
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import java.io.File

class LlamaEngine(private val context: Context) : AssistantEngine {
    private val ready: Boolean
    val isReady: Boolean get() = ready
    private val threads: Int = resolveThreads()

    init {
        val modelFile = getLocalModelFile(context)
        ready = if (modelFile.exists()) {
            try {
                LlamaNative.init(modelFile.absolutePath, EngineConfig.CONTEXT_SIZE, threads)
            } catch (e: UnsatisfiedLinkError) {
                false
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    override fun generateReply(input: String, history: List<ChatMessage>): AssistantResponse {
        if (!ready) {
            return AssistantResponse(
                header = "Local engine unavailable",
                body = "Offline model not installed. Using local fallback engine."
            )
        }
        val prompt = buildPrompt(input, history)
        val reply = try {
            LlamaNative.generate(prompt, EngineConfig.MAX_TOKENS)
        } catch (e: Exception) {
            ""
        }
        if (reply.isBlank()) {
            return SimpleLocalEngine().generateReply(input, history)
        }
        return AssistantResponse(
            header = "Offline assistant",
            body = reply.trim()
        )
    }

    private fun buildPrompt(input: String, history: List<ChatMessage>): String {
        val systemLine = "You are a helpful assistant. Reply in the same language as the user."
        val trimmedHistory = trimHistory(history)
        return buildString {
            appendLine("System: $systemLine")
            trimmedHistory.forEach { message ->
                val role = if (message.role == Role.USER) "User" else "Assistant"
                appendLine("$role: ${message.text}")
            }
            appendLine("User: $input")
            append("Assistant: ")
        }
    }

    private fun trimHistory(history: List<ChatMessage>): List<ChatMessage> {
        if (history.isEmpty()) {
            return emptyList()
        }
        val maxMessages = EngineConfig.MAX_HISTORY_MESSAGES
        val maxChars = EngineConfig.MAX_HISTORY_CHARS
        val selected = ArrayDeque<ChatMessage>()
        var totalChars = 0
        for (message in history.asReversed()) {
            val lineLength = message.text.length + 12
            if (selected.size >= maxMessages) {
                break
            }
            if (totalChars + lineLength > maxChars) {
                break
            }
            selected.addFirst(message)
            totalChars += lineLength
        }
        return selected.toList()
    }

    private fun resolveThreads(): Int {
        val available = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val requested = EngineConfig.THREADS
        if (requested <= 0) {
            return available
        }
        return requested.coerceAtMost(available).coerceAtLeast(1)
    }

    companion object {
        fun getLocalModelFile(context: Context): File {
            val modelDir = File(context.filesDir, "models")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            return File(modelDir, EngineConfig.MODEL_ASSET)
        }

        fun isNativeAvailable(): Boolean {
            return try {
                System.loadLibrary("llama_jni")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
    }
}
