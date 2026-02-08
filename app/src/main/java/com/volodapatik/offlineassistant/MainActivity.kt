package com.volodapatik.offlineassistant

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.volodapatik.offlineassistant.engine.AssistantEngine
import com.volodapatik.offlineassistant.engine.EngineProvider
import com.volodapatik.offlineassistant.engine.LlamaEngine
import com.volodapatik.offlineassistant.engine.LlamaNative
import com.volodapatik.offlineassistant.engine.SimpleLocalEngine
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role
import com.volodapatik.offlineassistant.ui.ChatAdapter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    private lateinit var engine: AssistantEngine
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var chatEnabled = false
    private var lastInitResult = false
    private var lastInitAttempted = false
    private var lastErrorMessage = ""
    private var currentStatusLine = "LLM: Not found (fallback)"

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        copyModelFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val inputField: EditText = findViewById(R.id.inputField)
        val sendButton: Button = findViewById(R.id.sendButton)
        val onboardingContainer: View = findViewById(R.id.onboardingContainer)
        val selectModelButton: Button = findViewById(R.id.selectModelButton)
        val useFallbackButton: Button = findViewById(R.id.useFallbackButton)
        val diagnosticsButton: Button = findViewById(R.id.diagnosticsButton)
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
            lastInitResult = false
            lastInitAttempted = false
            currentStatusLine = "LLM: Not found (fallback)"
            updateLlmStatus()
            setChatEnabled(true)
            onboardingContainer.isVisible = false
        }

        diagnosticsButton.setOnClickListener {
            val nativeError = if (LlamaEngine.isNativeAvailable()) LlamaNative.lastError() else ""
            if (nativeError.isNotBlank()) {
                lastErrorMessage = nativeError
            }
            AlertDialog.Builder(this)
                .setTitle("Diagnostics")
                .setMessage(buildDiagnosticsText(includeStatusLine = true, includeLastError = true))
                .setPositiveButton("OK", null)
                .show()
        }

        val modelReady = EngineProvider.hasLocalModel(this)
        val nativeAvailable = LlamaEngine.isNativeAvailable()
        if (modelReady && nativeAvailable) {
            lastInitAttempted = true
            val localEngine = LlamaEngine(this)
            lastInitResult = localEngine.isReady
            if (localEngine.isReady) {
                engine = localEngine
                lastErrorMessage = ""
                currentStatusLine = "LLM: Ready"
                setChatEnabled(true)
                onboardingContainer.isVisible = false
            } else {
                engine = SimpleLocalEngine()
                lastErrorMessage = LlamaNative.lastError().ifBlank { "Native init failed." }
                currentStatusLine = "LLM: Init failed (fallback available)"
                setChatEnabled(false)
                onboardingContainer.isVisible = true
            }
        } else {
            engine = SimpleLocalEngine()
            lastInitResult = false
            lastInitAttempted = modelReady && !nativeAvailable
            if (modelReady && !nativeAvailable) {
                lastErrorMessage = "Native library unavailable."
                currentStatusLine = "LLM: Init failed (fallback available)"
            } else {
                lastErrorMessage = ""
                currentStatusLine = "LLM: Not found (fallback)"
            }
            setChatEnabled(false)
            onboardingContainer.isVisible = true
            modelProgress.isVisible = false
        }
        updateLlmStatus()
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
        modelProgress.isIndeterminate = true
        modelProgress.progress = 0
        setChatEnabled(false)
        currentStatusLine = "LLM: Importing model..."
        updateLlmStatus()

        executor.execute {
            val targetFile = LlamaEngine.getLocalModelFile(this)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            val totalBytes = queryContentLength(uri)
            runOnUiThread {
                modelProgress.isIndeterminate = totalBytes <= 0L
                if (totalBytes > 0L) {
                    modelProgress.max = 100
                }
            }
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    throw IOException("Unable to open selected file.")
                }
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var copiedBytes = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes > 0L) {
                                val percent = ((copiedBytes * 100) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    runOnUiThread { modelProgress.progress = percent }
                                }
                            }
                        }
                        try {
                            output.fd.sync()
                        } catch (_: Exception) {
                        }
                    }
                }
                moveTempIntoPlace(tempFile, targetFile)
                val validModel = targetFile.exists() && targetFile.length() >= EngineProvider.MIN_MODEL_BYTES
                runOnUiThread {
                    modelProgress.isVisible = false
                    if (!validModel) {
                        lastInitResult = false
                        lastInitAttempted = false
                        lastErrorMessage = "Model file is too small or missing."
                        currentStatusLine = "LLM: Not found (fallback)"
                        updateLlmStatus()
                        Toast.makeText(
                            this,
                            "Model copy failed or invalid. Using fallback.",
                            Toast.LENGTH_LONG
                        ).show()
                        onboardingContainer.isVisible = true
                        return@runOnUiThread
                    }

                    val nativeReady = LlamaEngine.isNativeAvailable()
                    if (!nativeReady) {
                        lastInitResult = false
                        lastInitAttempted = true
                        lastErrorMessage = "Native library unavailable."
                        currentStatusLine = "LLM: Init failed (fallback available)"
                        updateLlmStatus()
                        Toast.makeText(this, lastErrorMessage, Toast.LENGTH_LONG).show()
                        onboardingContainer.isVisible = true
                        return@runOnUiThread
                    }

                    lastInitAttempted = true
                    val localEngine = LlamaEngine(this)
                    lastInitResult = localEngine.isReady
                    if (localEngine.isReady) {
                        engine = localEngine
                        lastErrorMessage = ""
                        currentStatusLine = "LLM: Ready"
                        updateLlmStatus()
                        onboardingContainer.isVisible = false
                        setChatEnabled(true)
                    } else {
                        engine = SimpleLocalEngine()
                        lastErrorMessage = LlamaNative.lastError().ifBlank { "Native init failed." }
                        currentStatusLine = "LLM: Init failed (fallback available)"
                        updateLlmStatus()
                        Toast.makeText(this, lastErrorMessage, Toast.LENGTH_LONG).show()
                        onboardingContainer.isVisible = true
                    }
                }
            } catch (e: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                runOnUiThread {
                    modelProgress.isVisible = false
                    lastInitResult = false
                    lastInitAttempted = false
                    lastErrorMessage = e.message ?: "Failed to import model."
                    currentStatusLine = "LLM: Not found (fallback)"
                    updateLlmStatus()
                    Toast.makeText(this, lastErrorMessage, Toast.LENGTH_LONG).show()
                    onboardingContainer.isVisible = true
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

    private fun moveTempIntoPlace(tempFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("Failed to replace existing model file.")
        }
        if (tempFile.renameTo(targetFile)) {
            return
        }
        try {
            FileOutputStream(targetFile).use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                    try {
                        output.fd.sync()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            throw IOException("Failed to finalize model file.", e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        if (!targetFile.exists()) {
            throw IOException("Failed to finalize model file.")
        }
    }

    private fun queryContentLength(uri: android.net.Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    -1L
                }
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun updateLlmStatus() {
        val llmStatus: TextView = findViewById(R.id.llmStatus)
        llmStatus.text = buildDiagnosticsText(includeStatusLine = true, includeLastError = false)
    }

    private fun buildDiagnosticsText(
        includeStatusLine: Boolean,
        includeLastError: Boolean
    ): String {
        val modelFile = LlamaEngine.getLocalModelFile(this)
        val nativeAvailable = LlamaEngine.isNativeAvailable()
        val modelExists = modelFile.exists()
        val modelSize = if (modelExists) modelFile.length() else -1L
        val initResult = lastInitResult
        return buildString {
            if (includeStatusLine) {
                appendLine(currentStatusLine)
            }
            appendLine("Native available: $nativeAvailable")
            appendLine("Model exists: $modelExists")
            appendLine("Model size: ${formatBytes(modelSize)}")
            appendLine("Model path: ${modelFile.absolutePath}")
            append("Init result: $initResult")
            if (lastInitAttempted && !initResult) {
                appendLine()
                append("Init failed")
            }
            if (includeLastError) {
                appendLine()
                append("Last error: ")
                append(if (lastErrorMessage.isBlank()) "None" else lastErrorMessage)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "N/A"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        return String.format(Locale.US, "%.2f %s (%d bytes)", value, units[unitIndex], bytes)
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
                lastInitResult = false
                lastInitAttempted = false
                lastErrorMessage = ""
                currentStatusLine = "LLM: Not found (fallback)"
                updateLlmStatus()
                findViewById<View>(R.id.onboardingContainer).isVisible = true
                setChatEnabled(false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
