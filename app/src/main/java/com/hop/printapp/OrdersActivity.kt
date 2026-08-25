package com.hop.printapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hop.printapp.databinding.ActivityOrdersBinding
import kotlinx.coroutines.launch

private const val IDLE_WARN_MS = 110 * 60 * 1000L   // 1 hr 50 min
private const val IDLE_TIMEOUT_MS = 120 * 60 * 1000L // 2 hr

class OrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: OrderAdapter

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Inactivity timer ─────────────────────────────────────────────────────

    private val idleHandler = Handler(Looper.getMainLooper())
    private var idleWarningDialog: AlertDialog? = null

    private val warnRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            idleWarningDialog = AlertDialog.Builder(this)
                .setTitle("Still there?")
                .setMessage("You've been inactive for a while. You'll be logged out in 10 minutes.")
                .setPositiveButton("Stay logged in") { _, _ -> resetIdleTimer() }
                .setCancelable(false)
                .show()
        }
    }

    private val logoutRunnable = Runnable {
        idleWarningDialog?.dismiss()
        idleWarningDialog = null
        if (!isFinishing && !isDestroyed) redirectToLogin(sessionExpired = false)
    }

    private fun resetIdleTimer() {
        idleWarningDialog?.dismiss()
        idleWarningDialog = null
        idleHandler.removeCallbacks(warnRunnable)
        idleHandler.removeCallbacks(logoutRunnable)
        idleHandler.postDelayed(warnRunnable, IDLE_WARN_MS)
        idleHandler.postDelayed(logoutRunnable, IDLE_TIMEOUT_MS)
    }

    private fun cancelIdleTimer() {
        idleHandler.removeCallbacks(warnRunnable)
        idleHandler.removeCallbacks(logoutRunnable)
        idleWarningDialog?.dismiss()
        idleWarningDialog = null
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetIdleTimer()
    }

    // null = all statuses; default to pending on launch
    private var currentFilter: String? = "pending"

    private var currentPage = 1
    private var totalPages = 1
    private var isLoadingMore = false

    // ── Service binding ──────────────────────────────────────────────────────

    private var printerService: PrinterService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            printerService = (service as PrinterService.LocalBinder).getService()
            serviceBound = true
            printerService?.listener = serviceListener
            // Sync UI with current service state immediately
            updatePrinterStatus(printerService?.isPrinterConnected == true)
            updateSocketStatus(printerService?.isSocketConnected == true)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
            printerService = null
        }
    }

    private val serviceListener = object : PrinterService.Listener {
        override fun onPrinterStatus(connected: Boolean) = updatePrinterStatus(connected)
        override fun onSocketStatus(connected: Boolean) = updateSocketStatus(connected)
        override fun onNewOrder() {
            showToast(getString(R.string.toast_new_order))
            loadOrders()
        }
        override fun onOrderUpdated(orderId: String, status: String) {
            adapter.updateOrderStatus(orderId, status)
        }
    }

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

        adapter = OrderAdapter(mutableListOf()) { order -> showOrderDialog(order) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadOrders() }

        setupFilterChips()
        setupScrollListener()
        loadOrders()

        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, PrinterService::class.java).apply {
            putExtra(PrinterService.EXTRA_USER_ID, session.userId)
            putExtra(PrinterService.EXTRA_CAFE_ID, session.cafeId)
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        resetIdleTimer()
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            printerService?.listener = null
            unbindService(serviceConnection)
            serviceBound = false
            printerService = null
        }
        cancelIdleTimer()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_orders, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_add_points -> {
            startActivity(Intent(this, AddPointsActivity::class.java))
            true
        }
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

    // ── Print (manual, via dialog) ───────────────────────────────────────────

    private fun printOrderReceipt(receiptText: String) {
        val svc = printerService
        if (svc == null || !svc.isPrinterConnected) {
            showToast(getString(R.string.error_printer_not_connected))
            return
        }
        svc.print(receiptText) { success, message ->
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
                is HopApiClient.ApiResult.Success -> adapter.updateOrderStatus(orderId, status)
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
            redirectToLogin(sessionExpired = true)
        }
    }

    private fun logout() {
        cancelIdleTimer()
        session.clear()
        stopService(Intent(this, PrinterService::class.java))
        redirectToLogin()
    }

    private fun redirectToLogin(sessionExpired: Boolean = false) {
        session.clear()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (sessionExpired) putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, true)
        })
        finish()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0
                )
            }
        }
    }
}
