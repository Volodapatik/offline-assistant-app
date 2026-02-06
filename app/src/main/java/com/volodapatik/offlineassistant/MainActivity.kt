package com.volodapatik.offlineassistant

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.volodapatik.offlineassistant.engine.AssistantEngine
import com.volodapatik.offlineassistant.engine.SimpleLocalEngine
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import com.volodapatik.offlineassistant.ui.ChatAdapter

class MainActivity : AppCompatActivity() {
    private val engine: AssistantEngine = SimpleLocalEngine()
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val inputField: EditText = findViewById(R.id.inputField)
        val sendButton: Button = findViewById(R.id.sendButton)

        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        sendButton.setOnClickListener {
            val input = inputField.text.toString().trim()
            if (input.isEmpty()) return@setOnClickListener

            appendMessage(ChatMessage(role = Role.USER, text = input), recyclerView)

            val response = engine.generateReply(input, messages.toList())
            val assistantText = "${response.header}\n${response.body}"
            appendMessage(ChatMessage(role = Role.ASSISTANT, text = assistantText), recyclerView)

            inputField.text.clear()
        }
    }

    private fun appendMessage(message: ChatMessage, recyclerView: RecyclerView) {
        messages.add(message)
        adapter.submitMessages(messages)
        recyclerView.scrollToPosition(messages.lastIndex)
    }
}
