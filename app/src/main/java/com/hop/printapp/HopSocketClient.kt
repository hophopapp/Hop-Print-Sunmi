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
    private val onPrintOrder: (orderId: String, totalPrice: Double, customerName: String?) -> Unit = { _, _, _ -> },
    private val onConnectionChange: (connected: Boolean) -> Unit = {}
) {
    private var adminSocket: Socket? = null
    private var printerSocket: Socket? = null

    fun connect() {
        connectAdmin()
        connectPrinter()
    }

    private fun connectAdmin() {
        val opts = IO.Options().apply {
            path = "/socket.io/"
            transports = arrayOf("websocket", "polling")
            query = "role=admin&userId=$userId&cafeId=$cafeId"
            reconnection = true
            reconnectionDelay = 2000L
            reconnectionDelayMax = 10000L
        }

        adminSocket = IO.socket("https://api.hophop.cafe", opts).apply {
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
                val orderId = data.optString("orderId").ifBlank {
                    data.optString("_id").ifBlank {
                        data.optJSONObject("order")?.optString("_id") ?: ""
                    }
                }
                val total = data.optDouble("totalPrice", 0.0)
                    .takeIf { it > 0 } ?: data.optDouble("total", 0.0)
                val userObj = data.optJSONObject("user")
                val name = userObj?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: userObj?.optString("email")?.takeIf { it.isNotBlank() }
                    ?: userObj?.optString("phone")?.takeIf { it.isNotBlank() }
                onNewOrder(orderId, total, name)
            })

            on("orderUpdated", Emitter.Listener { args ->
                val data = args.getOrNull(0) as? JSONObject ?: return@Listener
                val orderId = data.optString("orderId")
                val status = data.optString("status")
                if (orderId.isNotBlank() && status.isNotBlank()) onOrderUpdated(orderId, status)
            })

            connect()
        }
    }

    // Second socket joining the global printers room — receives printOrder events
    private fun connectPrinter() {
        val opts = IO.Options().apply {
            path = "/socket.io/"
            transports = arrayOf("websocket", "polling")
            query = "role=printer&userId=$userId&cafeId=$cafeId"
            reconnection = true
            reconnectionDelay = 2000L
            reconnectionDelayMax = 10000L
        }

        printerSocket = IO.socket("https://api.hophop.cafe", opts).apply {
            on("printOrder", Emitter.Listener { args ->
                val data = args.getOrNull(0) as? JSONObject ?: return@Listener
                val orderId = data.optString("orderId").ifBlank {
                    data.optString("_id").ifBlank {
                        data.optJSONObject("order")?.optString("_id") ?: ""
                    }
                }
                val total = data.optDouble("totalPrice", 0.0)
                    .takeIf { it > 0 } ?: data.optDouble("total", 0.0)
                val userObj = data.optJSONObject("user")
                val name = userObj?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: userObj?.optString("email")?.takeIf { it.isNotBlank() }
                    ?: userObj?.optString("phone")?.takeIf { it.isNotBlank() }
                onPrintOrder(orderId, total, name)
            })

            connect()
        }
    }

    fun disconnect() {
        adminSocket?.off()
        adminSocket?.disconnect()
        adminSocket = null
        printerSocket?.off()
        printerSocket?.disconnect()
        printerSocket = null
    }

    val isConnected: Boolean get() = adminSocket?.connected() == true
}
