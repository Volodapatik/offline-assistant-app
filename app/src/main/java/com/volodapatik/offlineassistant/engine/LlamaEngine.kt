package com.volodapatik.offlineassistant.engine

import android.content.Context
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import java.io.File

class LlamaEngine(private val context: Context) : AssistantEngine {
    private val ready: Boolean

    init {
        val modelFile = ensureModelFile()
        ready = try {
            LlamaNative.init(modelFile.absolutePath, EngineConfig.CONTEXT_SIZE, EngineConfig.THREADS)
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: Exception) {
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
        val reply = LlamaNative.generate(prompt, EngineConfig.MAX_TOKENS)
        return AssistantResponse(
            header = "Offline assistant",
            body = reply.trim()
        )
    }

    private fun buildPrompt(input: String, history: List<ChatMessage>): String {
        val contextLines = history.takeLast(6).joinToString("\n") { message ->
            val role = if (message.role == Role.USER) "User" else "Assistant"
            "$role: ${message.text}"
        }
        return buildString {
            if (contextLines.isNotBlank()) {
                appendLine(contextLines)
            }
            appendLine("System: Keep responses short, helpful, and focused on coding.")
            append("User: ")
            appendLine(input)
            append("Assistant: ")
        }
    }

    private fun ensureModelFile(): File {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        val modelFile = File(modelDir, EngineConfig.MODEL_ASSET)
        if (!modelFile.exists()) {
            context.assets.open(EngineConfig.MODEL_ASSET).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return modelFile
    }

    companion object {
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
