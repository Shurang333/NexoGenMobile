package com.example.nexogen

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class SessionFragment : Fragment() {

    private lateinit var screenView: ImageView
    private lateinit var statusText: TextView
    private lateinit var keyboardPanel: LinearLayout
    private lateinit var textInput: EditText
    private lateinit var btnRightClick: Button

    private var ws: WebSocket? = null
    private var wsReady = false
    private var serverHost: String = ""
    private var peerId: String = ""
    private var fps: Int = 15
    private var quality: Int = 50

    private var currentFrameWidth = 0
    private var currentFrameHeight = 0

    private var lastRightClick = false
    private var lastTextValue = ""

    private var lastTwoFingerY = 0f
    private var longPressRunnable: Runnable? = null
    private var touchMoved = false

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var mjpeg: MjpegClient? = null

    companion object {
        private const val ARG_PEER_ID = "peerId"
        private const val ARG_SERVER = "server"
        private const val ARG_FPS = "fps"
        private const val ARG_QUALITY = "quality"

        fun newInstance(peerId: String, server: String, fps: Int, quality: Int): SessionFragment {
            val f = SessionFragment()
            f.arguments = Bundle().apply {
                putString(ARG_PEER_ID, peerId)
                putString(ARG_SERVER, server)
                putInt(ARG_FPS, fps)
                putInt(ARG_QUALITY, quality)
            }
            return f
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_session, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peerId = arguments?.getString(ARG_PEER_ID) ?: ""
        val server = arguments?.getString(ARG_SERVER) ?: ""
        fps = arguments?.getInt(ARG_FPS, 15) ?: 15
        quality = arguments?.getInt(ARG_QUALITY, 50) ?: 50

        serverHost = if (server.startsWith("http")) server.trimEnd('/')
        else "http://$server".trimEnd('/')

        screenView = view.findViewById(R.id.screenView)
        statusText = view.findViewById(R.id.sessionStatusText)
        keyboardPanel = view.findViewById(R.id.keyboardPanel)
        textInput = view.findViewById(R.id.textInput)
        btnRightClick = view.findViewById(R.id.btnRightClick)

        view.findViewById<Button>(R.id.btnKeyboard).setOnClickListener {
            keyboardPanel.visibility =
                if (keyboardPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (keyboardPanel.visibility == View.VISIBLE) textInput.requestFocus()
        }

        btnRightClick.setOnClickListener {
            lastRightClick = !lastRightClick
            btnRightClick.text = if (lastRightClick) "ПКМ ●" else "ПКМ"
        }

        view.findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            disconnect()
            (activity as? MainActivity)?.exitSession()
        }

        setupTouch()
        setupKeyboardInput()
        connect()
    }

    private fun connect() {
        setStatus("connecting...", 0xFFFFB800.toInt())

        mjpeg = MjpegClient(
            onFrame = { bmp ->
                currentFrameWidth = bmp.width
                currentFrameHeight = bmp.height
                activity?.runOnUiThread { screenView.setImageBitmap(bmp) }
            },
            onError = { msg ->
                activity?.runOnUiThread { setStatus("stream: $msg", 0xFFFF3D71.toInt()) }
            }
        )
        mjpeg?.start("$serverHost/stream")

        val wsUrl = serverHost.replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws"
        val request = Request.Builder().url(wsUrl).build()

        ws = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                wsReady = true
                activity?.runOnUiThread { setStatus("connected", 0xFF00FFA3.toInt()) }

                // регистрация + конфиг
                val reg = JSONObject().apply {
                    put("type", "register")
                    put("id", peerId)
                }
                webSocket.send(reg.toString())

                sendConfig()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                wsReady = false
                Log.e("SessionFragment", "ws failure", t)
                activity?.runOnUiThread { setStatus("disconnected", 0xFFFF3D71.toInt()) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                wsReady = false
                activity?.runOnUiThread { setStatus("closed", 0xFF7E8A99.toInt()) }
            }
        })
    }

    private fun sendConfig() {
        if (!wsReady) return
        val cfg = JSONObject().apply {
            put("type", "config")
            put("fps", fps)
            put("quality", quality)
            put("audio", false)
            put("control", true)
        }
        ws?.send(cfg.toString())
    }

    private fun disconnect() {
        mjpeg?.stop()
        mjpeg = null
        ws?.close(1000, "bye")
        ws = null
        wsReady = false
    }

    private fun setStatus(text: String, color: Int) {
        statusText.text = text
        statusText.setTextColor(color)
    }

    private fun sendJson(obj: JSONObject) {
        if (!wsReady) return
        ws?.send(obj.toString())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        screenView.setOnTouchListener { _, event ->
            val rect = screenView.contentRect()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchMoved = false
                    val (x, y) = norm(event.x, event.y, rect)
                    sendJson(JSONObject().apply {
                        put("type", "mousedown")
                        put("x", x)
                        put("y", y)
                        if (lastRightClick) put("right", true)
                    })
                    startLongPress(event.x, event.y, rect)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2) {
                        val avgY = (event.getY(0) + event.getY(1)) / 2f
                        if (lastTwoFingerY != 0f) {
                            val delta = ((lastTwoFingerY - avgY) * 4).toInt()
                            if (abs(delta) > 2) {
                                sendJson(JSONObject().apply {
                                    put("type", "scroll")
                                    put("delta", delta)
                                })
                            }
                        }
                        lastTwoFingerY = avgY
                        touchMoved = true
                        cancelLongPress()
                    } else {
                        touchMoved = true
                        val (x, y) = norm(event.x, event.y, rect)
                        sendJson(JSONObject().apply {
                            put("type", "move")
                            put("x", x)
                            put("y", y)
                        })
                        cancelLongPress()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    sendJson(JSONObject().apply {
                        put("type", "mouseup")
                        if (lastRightClick) put("right", true)
                    })
                    if (!touchMoved) lastRightClick = false
                    lastTwoFingerY = 0f
                    cancelLongPress()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    lastTwoFingerY = 0f
                    true
                }
                else -> false
            }
        }
    }

    private fun startLongPress(x: Float, y: Float, rect: RectF) {
        cancelLongPress()
        longPressRunnable = Runnable {
            touchMoved = true
            val (nx, ny) = norm(x, y, rect)
            sendJson(JSONObject().apply {
                put("type", "click")
                put("x", nx)
                put("y", ny)
                put("right", true)
            })
        }
        screenView.postDelayed(longPressRunnable, 500)
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { screenView.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun norm(x: Float, y: Float, rect: RectF): Pair<Double, Double> {
        val nx = ((x - rect.left) / rect.width()).coerceIn(0f, 1f).toDouble()
        val ny = ((y - rect.top) / rect.height()).coerceIn(0f, 1f).toDouble()
        return nx to ny
    }

    private fun setupKeyboardInput() {
        textInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString() ?: ""
                if (value.length > lastTextValue.length) {
                    val added = value.substring(lastTextValue.length)
                    sendJson(JSONObject().apply {
                        put("type", "text")
                        put("value", added)
                    })
                } else if (value.length < lastTextValue.length) {
                    repeat(lastTextValue.length - value.length) {
                        sendJson(JSONObject().apply {
                            put("type", "key")
                            put("vk", 0x08)
                        })
                    }
                }
                lastTextValue = value
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        textInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sendJson(JSONObject().apply {
                    put("type", "key")
                    put("vk", 0x0D)
                })
                textInput.setText("")
                lastTextValue = ""
                true
            } else false
        }
    }

    private fun ImageView.contentRect(): RectF {
        val vw = width.toFloat()
        val vh = height.toFloat()

        if (currentFrameWidth <= 0 || currentFrameHeight <= 0) {
            return RectF(0f, 0f, vw, vh)
        }

        val scale = minOf(vw / currentFrameWidth, vh / currentFrameHeight)
        val rw = currentFrameWidth * scale
        val rh = currentFrameHeight * scale
        val left = (vw - rw) / 2f
        val top = (vh - rh) / 2f
        return RectF(left, top, left + rw, top + rh)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mjpeg?.stop()
        ws?.close(1000, "bye")
    }
}