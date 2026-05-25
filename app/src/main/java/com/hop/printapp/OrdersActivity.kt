package com.hop.printapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hop.printapp.databinding.ActivityOrdersBinding
import kotlinx.coroutines.launch

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var session: SessionManager
    private lateinit var printer: SunmiPrinterHelper
    private lateinit var adapter: OrderAdapter

    private var socketClient: HopSocketClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // null = all statuses; default to pending on launch
    private var currentFilter: String? = "pending"

    private var currentPage = 1
    private var totalPages = 1
    private var isLoadingMore = false

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        if (!session.isLoggedIn) {
            redirectToLogin()
            return
        }

        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.title_orders)

        printer = SunmiPrinterHelper(this)
        adapter = OrderAdapter(mutableListOf()) { order -> showOrderDialog(order) }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadOrders() }

        setupFilterChips()
        setupScrollListener()
        loadOrders()
    }

    override fun onStart() {
        super.onStart()
        printer.bind(
            onConnected = { mainHandler.post { updatePrinterStatus(true) } },
            onDisconnected = { mainHandler.post { updatePrinterStatus(false) } }
        )
        connectSocket()
    }

    override fun onStop() {
        super.onStop()
        printer.unbind()
        socketClient?.disconnect()
        socketClient = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_orders, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_manual_print -> {
            startActivity(Intent(this, MainActivity::class.java))
            true
        }
        R.id.action_logout -> {
            logout()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Filter chips ─────────────────────────────────────────────────────────

    private fun setupFilterChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipAll) -> null
                checkedIds.contains(R.id.chipPending) -> "pending"
                checkedIds.contains(R.id.chipProcessing) -> "processing"
                checkedIds.contains(R.id.chipCompleted) -> "completed"
                checkedIds.contains(R.id.chipCanceled) -> "canceled"
                else -> currentFilter
            }
            loadOrders()
        }
    }

    // ── Scroll / pagination ──────────────────────────────────────────────────

    private fun setupScrollListener() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoadingMore || currentPage >= totalPages) return
                val lm = recyclerView.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 4) {
                    loadOrders(currentPage + 1)
                }
            }
        })
    }

    // ── Orders loading ───────────────────────────────────────────────────────

    private fun loadOrders(page: Int = 1) {
        if (page == 1) {
            binding.swipeRefresh.isRefreshing = true
            binding.emptyText.visibility = View.GONE
        } else {
            if (isLoadingMore) return
            isLoadingMore = true
            binding.loadMoreProgress.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            val token = session.accessToken ?: run { redirectToLogin(); return@launch }
            val cafeId = session.cafeId ?: ""

            when (val result = HopApiClient.getOrders(token, cafeId, currentFilter, page)) {
                is HopApiClient.ApiResult.Success -> {
                    val data = result.data
                    currentPage = data.currentPage
                    totalPages = data.totalPages
                    if (page == 1) {
                        binding.swipeRefresh.isRefreshing = false
                        adapter.setOrders(data.orders)
                        binding.emptyText.visibility =
                            if (data.orders.isEmpty()) View.VISIBLE else View.GONE
                    } else {
                        isLoadingMore = false
                        binding.loadMoreProgress.visibility = View.GONE
                        adapter.appendOrders(data.orders)
                    }
                }
                is HopApiClient.ApiResult.Error -> {
                    if (page == 1) {
                        binding.swipeRefresh.isRefreshing = false
                        binding.emptyText.visibility = View.VISIBLE
                        binding.emptyText.text = result.message
                    } else {
                        isLoadingMore = false
                        binding.loadMoreProgress.visibility = View.GONE
                    }
                    if (result.isUnauthorized) handleUnauthorized()
                    else showToast(result.message)
                }
            }
        }
    }

    // ── Socket ───────────────────────────────────────────────────────────────

    private fun connectSocket() {
        val userId = session.userId ?: return
        val cafeId = session.cafeId ?: return

        socketClient = HopSocketClient(
            userId = userId,
            cafeId = cafeId,
            onNewOrder = { orderId, totalPrice, customerName ->
                mainHandler.post { handleNewOrder(orderId, totalPrice, customerName) }
            },
            onOrderUpdated = { orderId, status ->
                mainHandler.post { handleOrderUpdated(orderId, status) }
            },
            onPrintOrder = { orderId, totalPrice, customerName ->
                mainHandler.post { handlePrintOrder(orderId, totalPrice, customerName) }
            },
            onConnectionChange = { connected ->
                mainHandler.post { updateSocketStatus(connected) }
            }
        ).also { it.connect() }
    }

    // ── Socket event handlers ────────────────────────────────────────────────

    /**
     * Called on "newOrder" socket event.
     *
     * Strategy:
     *  1. Immediately print a minimal receipt with the payload data so the
     *     kitchen gets the ticket fast.
     *  2. Refresh the full orders list in background; if the full order is
     *     found, replace the prepended placeholder with real data.
     */
    private fun handleNewOrder(orderId: String, totalPrice: Double, customerName: String?) {
        showToast(getString(R.string.toast_new_order))

        // Step 1 — print immediately with what we have
        val minimalText = OrderFormatter.formatMinimal(orderId, totalPrice, customerName)
        printOrderReceipt(minimalText)

        // Step 2 — reload page 1 to surface the new order; reprint with full data if found
        lifecycleScope.launch {
            val token = session.accessToken ?: return@launch
            val cafeId = session.cafeId ?: return@launch
            when (val result = HopApiClient.getOrders(token, cafeId, currentFilter, page = 1)) {
                is HopApiClient.ApiResult.Success -> {
                    val data = result.data
                    currentPage = data.currentPage
                    totalPages = data.totalPages
                    adapter.setOrders(data.orders)
                    binding.emptyText.visibility =
                        if (data.orders.isEmpty()) View.VISIBLE else View.GONE

                    val fullOrder = data.orders.find { it.id == orderId }
                    if (fullOrder != null && !fullOrder.items.isNullOrEmpty()) {
                        printOrderReceipt(OrderFormatter.format(fullOrder))
                    }
                }
                is HopApiClient.ApiResult.Error -> {
                    if (result.isUnauthorized) handleUnauthorized()
                }
            }
        }
    }

    private fun handleOrderUpdated(orderId: String, status: String) {
        adapter.updateOrderStatus(orderId, status)
    }

    /**
     * Called on "printOrder" event from the global printers room (printer socket).
     * Print immediately with payload data, then upgrade to full receipt if the
     * order is available in the API.
     */
    private fun handlePrintOrder(orderId: String, totalPrice: Double, customerName: String?) {
        val minimalText = OrderFormatter.formatMinimal(orderId, totalPrice, customerName)
        printOrderReceipt(minimalText)

        if (orderId.isBlank()) return
        lifecycleScope.launch {
            val token = session.accessToken ?: return@launch
            val cafeId = session.cafeId ?: return@launch
            when (val result = HopApiClient.getOrders(token, cafeId, null, page = 1)) {
                is HopApiClient.ApiResult.Success -> {
                    val fullOrder = result.data.orders.find { it.id == orderId }
                    if (fullOrder != null && !fullOrder.items.isNullOrEmpty()) {
                        printOrderReceipt(OrderFormatter.format(fullOrder))
                    }
                }
                else -> {}
            }
        }
    }

    // ── Shared print function ─────────────────────────────────────────────────
    //
    // printOrderReceipt() is the single entry point used by BOTH:
    //   • the manual Print button in MainActivity (via SunmiPrinterHelper directly)
    //   • the socket "newOrder" auto-print handler above
    //
    // It calls the identical SunmiPrinterHelper.printText() path the Print button uses.

    private fun printOrderReceipt(receiptText: String) {
        if (!printer.isConnected) {
            showToast(getString(R.string.error_printer_not_connected))
            return
        }
        printer.printText(receiptText) { success, message ->
            if (!success) mainHandler.post { showToast("Print failed: $message") }
        }
    }

    // ── Order action dialog ──────────────────────────────────────────────────

    private fun showOrderDialog(order: Order) {
        val title = "Order #${order.id.takeLast(6).uppercase()}"
        val actions = arrayOf(
            getString(R.string.action_print_receipt),
            getString(R.string.action_change_status)
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> printOrderReceipt(OrderFormatter.format(order))
                    1 -> showStatusDialog(order)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStatusDialog(order: Order) {
        val statuses = arrayOf("pending", "processing", "completed", "canceled")
        val labels = statuses.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
        val current = statuses.indexOf(order.orderStatus)

        AlertDialog.Builder(this)
            .setTitle("Order #${order.id.takeLast(6).uppercase()}")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                val newStatus = statuses[which]
                if (newStatus != order.orderStatus) updateStatus(order.id, newStatus)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateStatus(orderId: String, status: String) {
        lifecycleScope.launch {
            val token = session.accessToken ?: return@launch
            when (val result = HopApiClient.updateOrderStatus(token, orderId, status)) {
                is HopApiClient.ApiResult.Success -> {
                    adapter.updateOrderStatus(orderId, status)
                }
                is HopApiClient.ApiResult.Error -> {
                    if (result.isUnauthorized) handleUnauthorized()
                    else showToast(result.message)
                }
            }
        }
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private fun updatePrinterStatus(connected: Boolean) {
        binding.printerStatusText.text = getString(
            if (connected) R.string.status_printer_connected else R.string.status_printer_disconnected
        )
        binding.printerStatusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (connected) R.color.primary else android.R.color.holo_red_dark
            )
        )
    }

    private fun updateSocketStatus(connected: Boolean) {
        binding.socketStatusText.text = getString(
            if (connected) R.string.status_socket_connected else R.string.status_socket_disconnected
        )
        binding.socketStatusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (connected) R.color.primary else android.R.color.holo_red_dark
            )
        )
    }

    private fun handleUnauthorized() {
        lifecycleScope.launch {
            val rt = session.refreshToken
            if (!rt.isNullOrEmpty()) {
                when (val r = HopApiClient.refreshToken(rt)) {
                    is HopApiClient.ApiResult.Success -> {
                        session.accessToken = r.data.accessToken
                        if (r.data.refreshToken.isNotEmpty()) session.refreshToken = r.data.refreshToken
                        loadOrders()
                        return@launch
                    }
                    else -> {}
                }
            }
            redirectToLogin()
        }
    }

    private fun logout() {
        socketClient?.disconnect()
        socketClient = null
        session.clear()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
