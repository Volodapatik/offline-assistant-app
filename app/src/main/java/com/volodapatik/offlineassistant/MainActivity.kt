package com.volodapatik.offlineassistant

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.volodapatik.offlineassistant.engine.AssistantEngine
import com.volodapatik.offlineassistant.engine.EngineProvider
import com.volodapatik.offlineassistant.engine.LlamaEngine
import com.volodapatik.offlineassistant.engine.SimpleLocalEngine
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import com.volodapatik.offlineassistant.ui.ChatAdapter
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    private lateinit var engine: AssistantEngine
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var chatEnabled = false

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // ignore
        }
        copyModelFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val inputField: EditText = findViewById(R.id.inputField)
        val sendButton: Button = findViewById(R.id.sendButton)
        val llmStatus: TextView = findViewById(R.id.llmStatus)
        val onboardingContainer: View = findViewById(R.id.onboardingContainer)
        val selectModelButton: Button = findViewById(R.id.selectModelButton)
        val useFallbackButton: Button = findViewById(R.id.useFallbackButton)
        val modelProgress: ProgressBar = findViewById(R.id.modelProgress)

        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val sendAction = sendAction@{
            val input = inputField.text.toString().trim()
            if (input.isEmpty()) return@sendAction
            if (!chatEnabled) return@sendAction

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

        selectModelButton.setOnClickListener {
            pickModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        useFallbackButton.setOnClickListener {
            engine = SimpleLocalEngine()
            llmStatus.text = "LLM: Not found (fallback)"
            setChatEnabled(true)
            onboardingContainer.isVisible = false
        }

        if (EngineProvider.hasLocalModel(this) && LlamaEngine.isNativeAvailable()) {
            engine = LlamaEngine(this)
            llmStatus.text = "LLM: Ready"
            setChatEnabled(true)
            onboardingContainer.isVisible = false
        } else {
            llmStatus.text = "LLM: Not found (fallback)"
            setChatEnabled(false)
            onboardingContainer.isVisible = true
            modelProgress.isVisible = false
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

    private fun setChatEnabled(enabled: Boolean) {
        chatEnabled = enabled
        findViewById<EditText>(R.id.inputField).isEnabled = enabled
        findViewById<Button>(R.id.sendButton).isEnabled = enabled
    }

    private fun copyModelFromUri(uri: android.net.Uri) {
        val onboardingContainer: View = findViewById(R.id.onboardingContainer)
        val modelProgress: ProgressBar = findViewById(R.id.modelProgress)
        modelProgress.isVisible = true
        setChatEnabled(false)

        executor.execute {
            val targetFile = LlamaEngine.getLocalModelFile(this)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                runOnUiThread {
                    modelProgress.isVisible = false
                    if (EngineProvider.hasLocalModel(this) && LlamaEngine.isNativeAvailable()) {
                        engine = LlamaEngine(this)
                        findViewById<TextView>(R.id.llmStatus).text = "LLM: Ready"
                        onboardingContainer.isVisible = false
                        setChatEnabled(true)
                    } else {
                        Toast.makeText(
                            this,
                            "Model copy failed or invalid. Using fallback.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    modelProgress.isVisible = false
                    Toast.makeText(this, "Failed to import model.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun removeLocalModel() {
        val modelFile: File = LlamaEngine.getLocalModelFile(this)
        if (modelFile.exists()) {
            modelFile.delete()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_model -> {
                pickModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                true
            }
            R.id.action_remove_model -> {
                removeLocalModel()
                findViewById<TextView>(R.id.llmStatus).text = "LLM: Not found (fallback)"
                findViewById<View>(R.id.onboardingContainer).isVisible = true
                setChatEnabled(false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
