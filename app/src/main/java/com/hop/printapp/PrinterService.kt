package com.hop.printapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class PrinterService : Service() {

    // ── Binder ───────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): PrinterService = this@PrinterService
    }

    private val binder = LocalBinder()

    // ── Listener (Activity registers this when visible) ──────────────────────

    interface Listener {
        fun onPrinterStatus(connected: Boolean)
        fun onSocketStatus(connected: Boolean)
        fun onNewOrder()
        fun onOrderUpdated(orderId: String, status: String)
    }

    var listener: Listener? = null

    // ── State ────────────────────────────────────────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var printer: SunmiPrinterHelper
    private var socketClient: HopSocketClient? = null

    val isPrinterConnected get() = printer.isConnected
    val isSocketConnected get() = socketClient?.isConnected == true

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        printer = SunmiPrinterHelper(this)
        printer.bind(
            onConnected = { mainHandler.post { listener?.onPrinterStatus(true) } },
            onDisconnected = { mainHandler.post { listener?.onPrinterStatus(false) } }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        // Only connect socket on first start; service restarts via START_STICKY without extras
        if (socketClient == null) {
            val userId = intent?.getStringExtra(EXTRA_USER_ID) ?: return START_STICKY
            val cafeId = intent.getStringExtra(EXTRA_CAFE_ID) ?: return START_STICKY
            connectSocket(userId, cafeId)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        printer.unbind()
        socketClient?.disconnect()
        socketClient = null
        super.onDestroy()
    }

    // ── Public API for Activity ──────────────────────────────────────────────

    fun print(text: String, onResult: (Boolean, String) -> Unit) {
        printer.printText(text, onResult)
    }

    // ── Socket ───────────────────────────────────────────────────────────────

    private fun connectSocket(userId: String, cafeId: String) {
        socketClient = HopSocketClient(
            userId = userId,
            cafeId = cafeId,
            onNewOrder = { order ->
                // Print even when screen is off — service owns the printer
                val text = if (order != null && !order.items.isNullOrEmpty()) {
                    OrderFormatter.format(order)
                } else {
                    OrderFormatter.formatMinimal(
                        order?.id ?: "",
                        order?.totalPrice ?: 0.0,
                        order?.user?.name ?: order?.user?.email
                    )
                }
                if (printer.isConnected) {
                    printer.printText(text) { _, _ -> }
                }
                mainHandler.post { listener?.onNewOrder() }
            },
            onOrderUpdated = { orderId, status ->
                mainHandler.post { listener?.onOrderUpdated(orderId, status) }
            },
            onConnectionChange = { connected ->
                mainHandler.post { listener?.onSocketStatus(connected) }
            }
        ).also { it.connect() }
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, OrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening for new orders…")
            .setSmallIcon(R.drawable.ic_launcher_fallback)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Order alerts",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shown while the app listens for new orders"
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_CAFE_ID = "cafeId"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "hop_orders"
    }
}
