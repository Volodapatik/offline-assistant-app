package com.volodapatik.offlineassistant

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.volodapatik.offlineassistant.engine.AssistantEngine
import com.volodapatik.offlineassistant.engine.EngineProvider
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import com.volodapatik.offlineassistant.ui.ChatAdapter
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var engine: AssistantEngine
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val inputField: EditText = findViewById(R.id.inputField)
        val sendButton: Button = findViewById(R.id.sendButton)
        val llmStatus: TextView = findViewById(R.id.llmStatus)

        val selection = EngineProvider.create(this)
        engine = selection.engine
        llmStatus.text = selection.statusLabel
        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val sendAction = sendAction@{
            val input = inputField.text.toString().trim()
            if (input.isEmpty()) return@sendAction

            appendMessage(ChatMessage(role = Role.USER, text = input), recyclerView)
            val historySnapshot = messages.toList()
            val thinkingIndex = appendMessage(
                ChatMessage(role = Role.ASSISTANT, text = "Thinking..."),
                recyclerView
            )

            inputField.text.clear()

            executor.execute {
                val response = engine.generateReply(input, historySnapshot)
                val assistantText = "${response.header}\n${response.body}"
                runOnUiThread {
                    updateMessage(thinkingIndex, ChatMessage(Role.ASSISTANT, assistantText), recyclerView)
                }
            }
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

    private fun appendMessage(message: ChatMessage, recyclerView: RecyclerView): Int {
        messages.add(message)
        adapter.submitMessages(messages)
        val index = messages.lastIndex
        recyclerView.post { recyclerView.scrollToPosition(index) }
        return index
    }

    private fun updateMessage(index: Int, message: ChatMessage, recyclerView: RecyclerView) {
        if (index !in messages.indices) return
        messages[index] = message
        adapter.updateMessage(index, message)
        recyclerView.post { recyclerView.scrollToPosition(messages.lastIndex) }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
