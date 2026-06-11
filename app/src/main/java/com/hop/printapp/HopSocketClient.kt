package com.hop.printapp

import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject

class HopSocketClient(
    private val userId: String,
    private val cafeId: String,
    private val onNewOrder: (orderId: String, totalPrice: Double, customerName: String?) -> Unit,
    private val onOrderUpdated: (orderId: String, status: String) -> Unit,
    private val onConnectionChange: (connected: Boolean) -> Unit = {}
) {
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
                val order = data.optJSONObject("order") ?: return@Listener
                val orderId = order.optString("_id")
                val total = order.optDouble("totalPrice", 0.0)
                val userObj = order.optJSONObject("user")
                val name = userObj?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: userObj?.optString("email")?.takeIf { it.isNotBlank() }
                onNewOrder(orderId, total, name)
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
