package com.hop.printapp

import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hop.printapp.databinding.ActivityOrdersBinding
import com.hop.printapp.model.Order
import com.hop.printapp.model.UpdateStatusRequest
import com.hop.printapp.network.RetrofitClient
import com.hop.printapp.network.SocketManager
import com.hop.printapp.storage.SessionManager
import kotlinx.coroutines.launch

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var printer: SunmiPrinterHelper
    private lateinit var adapter: OrderAdapter
    private var orders = mutableListOf<Order>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.init(this)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        printer = SunmiPrinterHelper(this)

        adapter = OrderAdapter(
            onStatusClick = { order -> cycleStatus(order) },
            onPrintClick = { order -> printOrder(order) }
        )
        binding.ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.ordersRecyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { fetchOrders() }

        connectSocket()
        fetchOrders()
    }

    override fun onStart() {
        super.onStart()
        printer.bind(
            onConnected = { runOnUiThread { updatePrinterStatus(true) } },
            onDisconnected = { runOnUiThread { updatePrinterStatus(false) } }
        )
    }

    override fun onStop() {
        super.onStop()
        printer.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.disconnect()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.btn_logout)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            logout()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun connectSocket() {
        val userId = SessionManager.userId ?: return
        val cafeId = SessionManager.cafeId ?: return
        val baseUrl = RetrofitClient.BASE_URL

        SocketManager.connect(baseUrl, userId, cafeId)

        SocketManager.onNewOrder { event ->
            runOnUiThread { playAlertSound() }
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.api.getOrders(cafeId)
                    if (response.isSuccessful) {
                        val newOrders = response.body()?.data ?: emptyList()
                        runOnUiThread { updateOrderList(newOrders) }

                        val newOrder = newOrders.find { it._id == event.orderId }
                        if (newOrder != null && printer.isConnected) {
                            ReceiptPrinter.printOrder(newOrder, printer) { success, msg ->
                                runOnUiThread {
                                    Toast.makeText(this@OrdersActivity,
                                        if (success) "Order #${event.orderId.takeLast(6)} printed"
                                        else "Print failed: $msg",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@OrdersActivity, "Error loading order: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        SocketManager.onOrderUpdated { event ->
            runOnUiThread {
                val index = orders.indexOfFirst { it._id == event.orderId }
                if (index >= 0) {
                    orders[index] = orders[index].copy(status = event.status)
                    adapter.submitList(orders.toList())
                }
            }
        }
    }

    private fun fetchOrders() {
        val cafeId = SessionManager.cafeId ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getOrders(cafeId)
                if (response.isSuccessful) {
                    val fetched = response.body()?.data ?: emptyList()
                    runOnUiThread { updateOrderList(fetched) }
                } else if (response.code() == 401) {
                    runOnUiThread { logout() }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@OrdersActivity, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                runOnUiThread { binding.swipeRefresh.isRefreshing = false }
            }
        }
    }

    private fun updateOrderList(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
        adapter.submitList(orders.toList())
        binding.emptyText.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun cycleStatus(order: Order) {
        val nextStatus = when (order.status) {
            "pending" -> "processing"
            "processing" -> "completed"
            else -> return
        }

        val index = orders.indexOfFirst { it._id == order._id }
        if (index >= 0) {
            orders[index] = orders[index].copy(status = nextStatus)
            adapter.submitList(orders.toList())
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.updateOrderStatus(order._id, UpdateStatusRequest(nextStatus))
                if (!response.isSuccessful) {
                    runOnUiThread {
                        if (index >= 0) {
                            orders[index] = orders[index].copy(status = order.status)
                            adapter.submitList(orders.toList())
                        }
                        Toast.makeText(this@OrdersActivity, "Failed to update status", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@OrdersActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun printOrder(order: Order) {
        if (!printer.isConnected) {
            Toast.makeText(this, "Printer not connected", Toast.LENGTH_SHORT).show()
            return
        }
        ReceiptPrinter.printOrder(order, printer) { success, msg ->
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun updatePrinterStatus(connected: Boolean) {
        binding.printerStatus.text = if (connected) "Printer connected" else "Printer not connected"
        binding.printerStatus.setBackgroundColor(
            ContextCompat.getColor(this, if (connected) R.color.primary else R.color.status_canceled)
        )
        binding.printerStatus.setTextColor(
            ContextCompat.getColor(this, R.color.on_primary)
        )
    }

    private fun playAlertSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(this, uri)?.play()
        } catch (_: Exception) {}
    }

    private fun logout() {
        SocketManager.disconnect()
        SessionManager.clear()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
