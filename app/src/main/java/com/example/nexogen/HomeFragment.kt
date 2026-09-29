package com.example.nexogen

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class HomeFragment : Fragment() {

    private lateinit var settings: SettingsStore
    private lateinit var sessions: SessionRepository

    private lateinit var serverInput: EditText
    private lateinit var peerIdInput: EditText
    private lateinit var peerIdError: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnTest: Button
    private lateinit var testResult: TextView

    private lateinit var lanSwitch: Switch
    private lateinit var lanBlock: LinearLayout
    private lateinit var serverFieldWrapper: LinearLayout
    private lateinit var btnScan: Button
    private lateinit var scanStatus: TextView
    private lateinit var discoveredList: LinearLayout

    private var idFormatting = false
    private var scanning = false

    // Выбранное устройство в LAN-режиме
    private var selectedLanServer: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsStore(requireContext())
        sessions = SessionRepository(requireContext())

        bindViews(view)
        restoreState()
        wireListeners()

        applyLanMode(settings.lanMode, autoScan = true)
    }

    private fun bindViews(v: View) {
        serverInput = v.findViewById(R.id.serverInput)
        peerIdInput = v.findViewById(R.id.peerIdInput)
        peerIdError = v.findViewById(R.id.peerIdError)
        btnConnect = v.findViewById(R.id.btnConnect)
        btnTest = v.findViewById(R.id.btnTestConnection)
        testResult = v.findViewById(R.id.connectionTestResult)

        lanSwitch = v.findViewById(R.id.lanSwitch)
        lanBlock = v.findViewById(R.id.lanBlock)
        serverFieldWrapper = v.findViewById(R.id.serverFieldWrapper)
        btnScan = v.findViewById(R.id.btnScan)
        scanStatus = v.findViewById(R.id.scanStatus)
        discoveredList = v.findViewById(R.id.discoveredList)
    }

    private fun restoreState() {
        serverInput.setText(settings.server)
        peerIdInput.setText(settings.peerId)
        lanSwitch.isChecked = settings.lanMode
    }

    private fun wireListeners() {

        // Переключатель LAN
        lanSwitch.setOnCheckedChangeListener { _, checked ->
            settings.lanMode = checked
            applyLanMode(checked, autoScan = true)
        }

        // Авто-формат ID
        peerIdInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (idFormatting) return
                val raw = s?.toString() ?: ""

                val hex = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(12)
                val sb = StringBuilder()
                for (i in hex.indices) {
                    if (i > 0 && i % 4 == 0) sb.append('-')
                    sb.append(hex[i])
                }
                val formatted = sb.toString()

                val hadInvalid = raw.any {
                    it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' && it != '-'
                }
                peerIdError.visibility = if (hadInvalid) View.VISIBLE else View.GONE

                if (formatted != raw) {
                    idFormatting = true
                    peerIdInput.setText(formatted)
                    peerIdInput.setSelection(formatted.length)
                    idFormatting = false
                }
            }
        })

        btnTest.setOnClickListener { testConnection() }
        btnConnect.setOnClickListener { startSession() }
        btnScan.setOnClickListener { scanLan() }
    }

    /**
     * Включает / выключает LAN-режим.
     * LAN on: скрываем поле сервера, показываем блок сканирования, запускаем скан.
     * LAN off: показываем поле сервера, скрываем блок сканирования.
     */
    private fun applyLanMode(enabled: Boolean, autoScan: Boolean) {
        if (enabled) {
            serverFieldWrapper.visibility = View.GONE
            lanBlock.visibility = View.VISIBLE

            if (autoScan) scanLan()
        } else {
            serverFieldWrapper.visibility = View.VISIBLE
            lanBlock.visibility = View.GONE

            // Очищаем выбор LAN
            selectedLanServer = null
            discoveredList.removeAllViews()
            scanStatus.text = "нажмите ПОИСК"
            scanStatus.setTextColor(0xFF7E8A99.toInt())
        }
    }

    // === СКАНИРОВАНИЕ ===

    private fun scanLan() {
        if (scanning) return
        scanning = true

        btnScan.isEnabled = false
        scanStatus.visibility = View.VISIBLE
        scanStatus.setTextColor(0xFFFFB800.toInt())
        scanStatus.text = "поиск..."
        discoveredList.removeAllViews()

        thread {
            val servers = DiscoveryClient().discover(timeoutMs = 2000)

            activity?.runOnUiThread {
                scanning = false
                btnScan.isEnabled = true

                if (servers.isEmpty()) {
                    scanStatus.setTextColor(0xFFFF3D71.toInt())
                    scanStatus.text = "не найдено. проверьте, что Streamer запущен и firewall открыт"
                } else {
                    scanStatus.setTextColor(0xFF00FFA3.toInt())
                    scanStatus.text = "найдено: ${servers.size}"
                    renderDiscovered(servers)
                }
            }
        }
    }

    private fun renderDiscovered(servers: List<DiscoveredServer>) {
        discoveredList.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        for (s in servers) {
            val item = inflater.inflate(R.layout.item_discovered, discoveredList, false)

            item.findViewById<TextView>(R.id.deviceHostname).text = s.hostname
            item.findViewById<TextView>(R.id.deviceAddress).text = s.url

            // Подсветка выбранного
            if (s.url == selectedLanServer) {
                item.setBackgroundResource(R.drawable.bg_input_selected)
            }

            item.setOnClickListener {
                selectedLanServer = s.url
                settings.server = s.url
                scanStatus.setTextColor(0xFF00FFA3.toInt())
                scanStatus.text = "выбрано: ${s.hostname}"
                renderDiscovered(servers) // перерисовать — выделить
            }

            discoveredList.addView(item)
        }
    }

    // === ПОДКЛЮЧЕНИЕ ===

    private fun isValidPeerId(id: String): Boolean {
        val hex = id.replace("-", "")
        return hex.length == 12 && hex.all { it in '0'..'9' || it in 'A'..'F' }
    }

    /**
     * Возвращает адрес сервера в зависимости от режима.
     * LAN: берём выбранное устройство (settings.server уже обновляется при выборе)
     * Manual: берём из поля
     */
    private fun resolveServerAddress(): String? {
        return if (lanSwitch.isChecked) {
            selectedLanServer ?: settings.server.ifEmpty { null }
        } else {
            serverInput.text.toString().trim().ifEmpty { null }
        }
    }

    private fun testConnection() {
        val server = resolveServerAddress()
        if (server.isNullOrEmpty()) {
            showTestResult(
                if (lanSwitch.isChecked) "выберите устройство из списка" else "укажите адрес",
                0xFFFF3D71.toInt()
            )
            return
        }

        val url = if (server.startsWith("http")) server.trimEnd('/') else "http://$server".trimEnd('/')
        showTestResult("проверка...", 0xFFFFB800.toInt())

        thread {
            val result = try {
                val conn = URL("$url/info").openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) Pair("✓ сервер отвечает (HTTP $code)", 0xFF00FFA3.toInt())
                else Pair("✗ сервер вернул HTTP $code", 0xFFFF3D71.toInt())
            } catch (e: Exception) {
                Pair("✗ ${e.message ?: "ошибка связи"}", 0xFFFF3D71.toInt())
            }

            activity?.runOnUiThread {
                showTestResult(result.first, result.second)
            }
        }
    }

    private fun showTestResult(text: String, color: Int) {
        testResult.text = text
        testResult.setTextColor(color)
        testResult.visibility = View.VISIBLE
    }

    private fun startSession() {
        val server = resolveServerAddress()
        val peerId = peerIdInput.text.toString().trim()

        if (server.isNullOrEmpty()) {
            showTestResult(
                if (lanSwitch.isChecked) "выберите устройство из списка" else "укажите адрес",
                0xFFFF3D71.toInt()
            )
            return
        }
        if (!isValidPeerId(peerId)) {
            peerIdError.visibility = View.VISIBLE
            showTestResult("неверный ID (нужно XXXX-XXXX-XXXX)", 0xFFFF3D71.toInt())
            return
        }

        settings.server = server
        settings.peerId = peerId

        sessions.upsert(
            SessionRecord(
                peerId = peerId,
                server = server,
                fps = settings.fps,
                quality = settings.quality,
                lastConnectedAt = System.currentTimeMillis(),
                status = "ACTIVE"
            )
        )

        (activity as? MainActivity)?.enterSession(peerId, server, settings.fps, settings.quality)
    }
}