package com.example.nexogen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private val items: List<SessionRecord>,
    private val onItemClick: (SessionRecord) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val statusDot: View = v.findViewById(R.id.statusDot)
        val sessionId: TextView = v.findViewById(R.id.sessionId)
        val sessionStatus: TextView = v.findViewById(R.id.sessionStatus)
        val sessionServer: TextView = v.findViewById(R.id.sessionServer)
        val sessionMeta: TextView = v.findViewById(R.id.sessionMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.sessionId.text = item.peerId
        holder.sessionStatus.text = item.status
        holder.sessionServer.text = item.server
        holder.sessionMeta.text = "${item.fps} FPS · quality ${item.quality}"

        val dotRes = when (item.status) {
            "ACTIVE" -> R.drawable.bg_status_dot_green
            "FAILED" -> R.drawable.bg_status_dot_red
            else -> R.drawable.bg_status_dot_gray
        }
        holder.statusDot.setBackgroundResource(dotRes)

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}