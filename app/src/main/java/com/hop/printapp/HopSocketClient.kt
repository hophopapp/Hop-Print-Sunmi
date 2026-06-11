package com.hop.printapp

import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject

class HopSocketClient(
    private val userId: String,
    private val cafeId: String,
    private val onNewOrder: (order: Order?) -> Unit,
    private val onOrderUpdated: (orderId: String, status: String) -> Unit,
    private val onConnectionChange: (connected: Boolean) -> Unit = {}
) {
    private val gson = Gson()
    private var socket: Socket? = null

    fun connect() {
        val opts = IO.Options().apply {
            path = "/socket.io/"
            transports = arrayOf("websocket", "polling")
            query = "role=admin&userId=$userId&cafeId=$cafeId"
            reconnection = true
            reconnectionDelay = 2000L
            reconnectionDelayMax = 10000L
        }

        socket = IO.socket("https://api.hophop.cafe", opts).apply {
            on(Socket.EVENT_CONNECT, Emitter.Listener {
                onConnectionChange(true)
            })

            on(Socket.EVENT_DISCONNECT, Emitter.Listener {
                onConnectionChange(false)
            })

            on(Socket.EVENT_CONNECT_ERROR, Emitter.Listener {
                onConnectionChange(false)
            })

            on("newOrder", Emitter.Listener { args ->
                val data = args.getOrNull(0) as? JSONObject ?: return@Listener
                val orderJson = data.optJSONObject("order") ?: return@Listener
                val parsed = runCatching {
                    gson.fromJson(orderJson.toString(), Order::class.java)
                }.getOrNull()
                onNewOrder(parsed)
            })

            on("orderUpdated", Emitter.Listener { args ->
                val data = args.getOrNull(0) as? JSONObject ?: return@Listener
                val order = data.optJSONObject("order") ?: return@Listener
                val orderId = order.optString("_id")
                val status = order.optString("status")
                if (orderId.isNotBlank() && status.isNotBlank()) onOrderUpdated(orderId, status)
            })

            connect()
        }
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    val isConnected: Boolean get() = socket?.connected() == true
}
