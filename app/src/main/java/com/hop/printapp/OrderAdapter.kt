package com.hop.printapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OrderAdapter(
    private var orders: MutableList<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>() {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val displayFormat = SimpleDateFormat("MMM d, HH:mm", Locale.US)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.orderCard)
        val orderIdText: TextView = itemView.findViewById(R.id.orderIdText)
        val customerText: TextView = itemView.findViewById(R.id.customerText)
        val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        val totalText: TextView = itemView.findViewById(R.id.totalText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        holder.orderIdText.text = "#${order.id.takeLast(6).uppercase()}"

        val u = order.user
        holder.customerText.text = when {
            !u?.name.isNullOrBlank() -> u!!.name
            !u?.email.isNullOrBlank() -> u!!.email
            !u?.phone.isNullOrBlank() -> u!!.phone
            else -> holder.itemView.context.getString(R.string.label_guest)
        }

        holder.statusBadge.text = order.orderStatus.replaceFirstChar { it.uppercase() }
        holder.statusBadge.setBackgroundColor(statusColor(order.orderStatus))

        holder.totalText.text = "\$${String.format("%.2f", order.totalPrice)}"

        holder.timeText.text = runCatching {
            val date = isoFormat.parse(order.createdAt.take(19))
            if (date != null) displayFormat.format(date) else order.createdAt.take(16)
        }.getOrDefault(order.createdAt.take(16))

        holder.card.setOnClickListener { onOrderClick(order) }
    }

    override fun getItemCount() = orders.size

    fun setOrders(newOrders: List<Order>) {
        orders.clear()
        orders.addAll(newOrders)
        notifyDataSetChanged()
    }

    fun prependOrder(order: Order) {
        orders.add(0, order)
        notifyItemInserted(0)
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        val index = orders.indexOfFirst { it.id == orderId }
        if (index >= 0) {
            orders[index] = orders[index].copy(orderStatus = newStatus)
            notifyItemChanged(index)
        }
    }

    fun findById(orderId: String): Order? = orders.find { it.id == orderId }

    private fun statusColor(status: String): Int = when (status.lowercase()) {
        "pending" -> Color.parseColor("#FF6F00")
        "processing" -> Color.parseColor("#1565C0")
        "completed" -> Color.parseColor("#2E7D32")
        "canceled" -> Color.parseColor("#C62828")
        else -> Color.parseColor("#757575")
    }
}
