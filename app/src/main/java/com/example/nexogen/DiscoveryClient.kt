package com.example.nexogen

import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class DiscoveredServer(
    val hostname: String,
    val address: String,
    val port: Int,
    val version: Int
) {
    val url: String get() = "$address:$port"
}

class DiscoveryClient {

    /**
     * Шлёт UDP-broadcast на 255.255.255.255:5555, ждёт ответы timeoutMs миллисекунд,
     * возвращает список найденных серверов.
     */
    fun discover(timeoutMs: Int = 2000): List<DiscoveredServer> {
        val results = linkedMapOf<String, DiscoveredServer>()

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300

                val payload = "NEXOGEN_DISCOVER".toByteArray(Charsets.UTF_8)
                val broadcast = InetAddress.getByName("255.255.255.255")
                val sendPacket = DatagramPacket(payload, payload.size, broadcast, 5555)
                socket.send(sendPacket)

                val buffer = ByteArray(4096)
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)

                        val text = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                        val server = parseResponse(text) ?: continue

                        results[server.url] = server
                    } catch (e: SocketTimeoutException) {
                        // продолжаем ждать
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DiscoveryClient", "discovery error", e)
        }

        return results.values.toList()
    }

    private fun parseResponse(text: String): DiscoveredServer? {
        return try {
            val o = JSONObject(text)
            if (o.optString("service") != "nexogen") return null

            val hostname = o.optString("hostname", "unknown")
            val port = o.optInt("port", 8080)
            val version = o.optInt("version", 1)

            val primary = o.optString("primaryAddress", "")
            if (primary.isNotEmpty()) {
                return DiscoveredServer(hostname, primary, port, version)
            }

            val arr = o.optJSONArray("addresses") ?: return null
            if (arr.length() == 0) return null
            val addr = arr.getString(0)
            DiscoveredServer(hostname, addr, port, version)
        } catch (e: Exception) {
            null
        }
    }
}