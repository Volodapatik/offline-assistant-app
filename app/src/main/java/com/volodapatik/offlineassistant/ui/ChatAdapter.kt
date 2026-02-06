package com.volodapatik.offlineassistant.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.volodapatik.offlineassistant.R
import com.volodapatik.offlineassistant.model.ChatMessage
import com.volodapatik.offlineassistant.model.Role

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private val items = mutableListOf<ChatMessage>()

    fun submitMessages(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun updateMessage(index: Int, message: ChatMessage) {
        if (index !in items.indices) return
        items[index] = message
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageRow: LinearLayout = itemView.findViewById(R.id.messageRow)
        private val bubbleContainer: LinearLayout = itemView.findViewById(R.id.bubbleContainer)
        private val roleText: TextView = itemView.findViewById(R.id.roleText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: ChatMessage) {
            val isUser = message.role == Role.USER
            roleText.text = if (isUser) "You" else "Assistant"
            messageText.text = message.text
            bubbleContainer.setBackgroundResource(
                if (isUser) R.drawable.bubble_user else R.drawable.bubble_assistant
            )
            messageRow.gravity = if (isUser) Gravity.END else Gravity.START
        }
    }
}
