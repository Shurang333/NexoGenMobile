package com.example.nexogen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MjpegClient(
    private val onFrame: (Bitmap) -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var running = false

    private var thread: Thread? = null

    fun start(url: String) {
        stop()
        running = true
        thread = Thread {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body ?: run {
                    onError("empty body")
                    return@Thread
                }

                val stream: InputStream = body.byteStream()
                readMjpeg(stream)
            } catch (e: Exception) {
                if (running) {
                    Log.e("MjpegClient", "stream error", e)
                    onError(e.message ?: "stream error")
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun readMjpeg(stream: InputStream) {
        val buffer = ByteArray(64 * 1024)
        val headerBuffer = StringBuilder()

        var inBody = false
        var contentLength = -1
        var frame: ByteArray? = null
        var frameOffset = 0

        while (running) {
            val read = stream.read(buffer)
            if (read <= 0) break

            var i = 0
            while (i < read) {
                if (!inBody) {
                    val b = buffer[i]
                    headerBuffer.append(b.toInt().toChar())

                    if (headerBuffer.length >= 4 && headerBuffer.endsWith("\r\n\r\n")) {
                        val header = headerBuffer.toString()
                        contentLength = -1

                        for (line in header.split("\r\n")) {
                            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                            }
                        }

                        headerBuffer.clear()

                        if (contentLength > 0) {
                            frame = ByteArray(contentLength)
                            frameOffset = 0
                            inBody = true
                        }
                    }
                    i++
                } else {
                    val remaining = contentLength - frameOffset
                    val available = read - i
                    val take = minOf(remaining, available)

                    System.arraycopy(buffer, i, frame!!, frameOffset, take)
                    frameOffset += take
                    i += take

                    if (frameOffset >= contentLength) {
                        val bmp = BitmapFactory.decodeByteArray(frame, 0, frame.size)
                        if (bmp != null) onFrame(bmp)
                        inBody = false
                        frame = null
                        contentLength = -1
                    }
                }
            }
        }
    }
}