package com.aris.voice

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import android.graphics.drawable.GradientDrawable

data class Message(val content: String, val isUserMessage: Boolean)

class ChatAdapter(private val messages: List<Message>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)

        fun bind(message: Message) {
            messageText.text = message.content
            
            // Set margins correctly based on sender
            val params = messageText.layoutParams as LinearLayout.LayoutParams
            val context = itemView.context
            
            if (message.isUserMessage) {
                messageContainer.gravity = Gravity.END
                params.marginStart = dpToPx(context, 48)
                params.marginEnd = dpToPx(context, 0)
                
                // Style for user (e.g. metallic silver bubble)
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#B0B0B0")) // Metallic silver
                bg.cornerRadius = dpToPx(context, 16).toFloat()
                messageText.background = bg
                messageText.setTextColor(Color.parseColor("#121212")) // Deep charcoal text
            } else {
                messageContainer.gravity = Gravity.START
                params.marginStart = dpToPx(context, 0)
                params.marginEnd = dpToPx(context, 48)
                
                // Style for agent (e.g. deep charcoal card bubble)
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#1C1C1F")) // Deep charcoal
                bg.cornerRadius = dpToPx(context, 16).toFloat()
                messageText.background = bg
                messageText.setTextColor(Color.parseColor("#F1F5F9")) // Soft white text
            }
            messageText.layoutParams = params
        }
        
        private fun dpToPx(context: android.content.Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }
    }
}
