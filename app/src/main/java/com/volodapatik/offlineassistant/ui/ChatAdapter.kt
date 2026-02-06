package com.volodapatik.offlineassistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        private val roleText: TextView = itemView.findViewById(R.id.roleText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: ChatMessage) {
            roleText.text = if (message.role == Role.USER) "You" else "Assistant"
            messageText.text = message.text
        }
    }
}
