package com.example.nexogen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var settings: SettingsStore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsStore(requireContext())

        // Сеть
        val signalingInput = view.findViewById<EditText>(R.id.signalingInput)
        val stunInput = view.findViewById<EditText>(R.id.stunInput)

        signalingInput.setText(settings.signalingServer)
        stunInput.setText(settings.stunServer)

        signalingInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) settings.signalingServer = signalingInput.text.toString()
        }
        stunInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) settings.stunServer = stunInput.text.toString()
        }

        // Сессия
        val fpsSeek = view.findViewById<SeekBar>(R.id.fpsSeek)
        val fpsValue = view.findViewById<TextView>(R.id.fpsValue)
        val qualitySeek = view.findViewById<SeekBar>(R.id.qualitySeek)
        val qualityValue = view.findViewById<TextView>(R.id.qualityValue)
        val audioSwitch = view.findViewById<Switch>(R.id.audioSwitch)
        val controlSwitch = view.findViewById<Switch>(R.id.controlSwitch)

        fpsSeek.progress = settings.fps.coerceIn(1, 60)
        fpsValue.text = fpsSeek.progress.toString()

        qualitySeek.progress = settings.quality.coerceIn(10, 100)
        qualityValue.text = qualitySeek.progress.toString()

        audioSwitch.isChecked = settings.audioEnabled
        controlSwitch.isChecked = settings.controlEnabled

        fpsSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                fpsValue.text = p.toString()
                settings.fps = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        qualitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                qualityValue.text = p.toString()
                settings.quality = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        audioSwitch.setOnCheckedChangeListener { _, checked -> settings.audioEnabled = checked }
        controlSwitch.setOnCheckedChangeListener { _, checked -> settings.controlEnabled = checked }
    }
}