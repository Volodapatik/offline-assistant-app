package com.volodapatik.offlineassistant.engine

import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role

class SimpleLocalEngine : AssistantEngine {
    override fun generateReply(input: String, history: List<ChatMessage>): AssistantResponse {
        val userMessages = history.count { it.role == Role.USER }
        val body = buildString {
            appendLine("Offline model not installed. Using local fallback engine.")
            appendLine("Echo: $input")
            appendLine("Input length: ${input.length}")
            append("User messages in memory: $userMessages")
        }
        return AssistantResponse(
            header = "Local fallback",
            body = body,
        )
    }
}
