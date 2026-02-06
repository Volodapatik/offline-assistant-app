package com.volodapatik.offlineassistant.model

data class ChatMessage(
    val role: Role,
    val text: String,
)

enum class Role {
    USER,
    ASSISTANT,
}
