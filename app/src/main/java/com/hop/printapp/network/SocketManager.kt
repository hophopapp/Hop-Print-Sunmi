package com.hop.printapp.network

import com.google.gson.Gson
import com.hop.printapp.model.NewOrderEvent
import com.hop.printapp.model.OrderUpdatedEvent
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {

    private var socket: Socket? = null
    private val gson = Gson()

    val isConnected: Boolean
        get() = socket?.connected() == true

    fun connect(baseUrl: String, userId: String, cafeId: String) {
        disconnect()

        val socketUrl = baseUrl.removeSuffix("/api/v1/").removeSuffix("/api/v1").removeSuffix("/")
        val opts = IO.Options().apply {
            query = "role=admin&userId=$userId&cafeId=$cafeId"
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 2000
        }

        socket = IO.socket(socketUrl, opts)
        socket?.connect()
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    fun onNewOrder(callback: (NewOrderEvent) -> Unit) {
        socket?.on("newOrder") { args ->
            if (args.isNotEmpty()) {
                try {
                    val json = args[0] as? JSONObject ?: return@on
                    val event = gson.fromJson(json.toString(), NewOrderEvent::class.java)
                    callback(event)
                } catch (_: Exception) {}
            }
        }
    }

    fun onOrderUpdated(callback: (OrderUpdatedEvent) -> Unit) {
        socket?.on("orderUpdated") { args ->
            if (args.isNotEmpty()) {
                try {
                    val json = args[0] as? JSONObject ?: return@on
                    val event = gson.fromJson(json.toString(), OrderUpdatedEvent::class.java)
                    callback(event)
                } catch (_: Exception) {}
            }
        }
    }

    fun onConnect(callback: () -> Unit) {
        socket?.on(Socket.EVENT_CONNECT) { callback() }
    }

    fun onDisconnect(callback: () -> Unit) {
        socket?.on(Socket.EVENT_DISCONNECT) { callback() }
    }
}
