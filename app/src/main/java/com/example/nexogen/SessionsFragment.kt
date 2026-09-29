package com.example.nexogen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SessionsFragment : Fragment() {

    private lateinit var sessions: SessionRepository
    private lateinit var list: RecyclerView
    private lateinit var emptyText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_sessions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessions = SessionRepository(requireContext())

        list = view.findViewById(R.id.sessionsList)
        emptyText = view.findViewById(R.id.emptyText)

        list.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            sessions.clear()
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = sessions.getAll()
        if (items.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            list.visibility = View.VISIBLE
            list.adapter = SessionAdapter(items) { record ->
                // повторное подключение по тапу
                (activity as? MainActivity)?.enterSession(
                    record.peerId, record.server, record.fps, record.quality
                )
            }
        }
    }
}