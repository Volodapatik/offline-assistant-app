package com.volodapatik.offlineassistant

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.volodapatik.offlineassistant.engine.AssistantEngine
import com.volodapatik.offlineassistant.engine.EngineProvider
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import com.volodapatik.offlineassistant.ui.ChatAdapter

class MainActivity : AppCompatActivity() {
    private lateinit var engine: AssistantEngine
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val inputField: EditText = findViewById(R.id.inputField)
        val sendButton: Button = findViewById(R.id.sendButton)

        engine = EngineProvider.create(this)
        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val sendAction = sendAction@{
            val input = inputField.text.toString().trim()
            if (input.isEmpty()) return@sendAction

            appendMessage(ChatMessage(role = Role.USER, text = input), recyclerView)

            val response = engine.generateReply(input, messages.toList())
            val assistantText = "${response.header}\n${response.body}"
            appendMessage(ChatMessage(role = Role.ASSISTANT, text = assistantText), recyclerView)

            inputField.text.clear()
        }

        sendButton.setOnClickListener { sendAction() }
        inputField.setOnEditorActionListener { _, actionId, event ->
            val imeSend = actionId == EditorInfo.IME_ACTION_SEND
            val enterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (imeSend || enterKey) {
                sendAction()
                true
            } else {
                false
            }
        }
    }

    private fun appendMessage(message: ChatMessage, recyclerView: RecyclerView) {
        messages.add(message)
        adapter.submitMessages(messages)
        recyclerView.post { recyclerView.scrollToPosition(messages.lastIndex) }
    }
}
