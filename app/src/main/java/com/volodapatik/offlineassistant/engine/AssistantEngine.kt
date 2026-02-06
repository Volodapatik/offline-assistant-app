package com.volodapatik.offlineassistant.engine

import com.volodapatik.offlineassistant.model.ChatMessage

data class AssistantResponse(
    val header: String,
    val body: String,
)

interface AssistantEngine {
    fun generateReply(input: String, history: List<ChatMessage>): AssistantResponse
}
