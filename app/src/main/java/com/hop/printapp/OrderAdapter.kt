package com.hop.printapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hop.printapp.databinding.ItemOrderBinding
import com.hop.printapp.model.Order
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OrderAdapter(
    private val onStatusClick: (Order) -> Unit,
    private val onPrintClick: (Order) -> Unit
) : ListAdapter<Order, OrderAdapter.OrderViewHolder>(OrderDiffCallback()) {

    inner class OrderViewHolder(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = getItem(position)
        val b = holder.binding

        b.orderIdText.text = "#${order._id.takeLast(6).uppercase()}"
        b.customerNameText.text = order.user?.name ?: "Guest"
        b.totalPriceText.text = "$${String.format("%.2f", order.totalPrice)}"

        val itemNames = order.items.mapNotNull { it.menuItem?.name }
        val itemCount = order.items.sumOf { it.quantity }
        b.itemsSummaryText.text = if (itemNames.isNotEmpty()) {
            "${itemCount} item${if (itemCount != 1) "s" else ""}: ${itemNames.joinToString(", ")}"
        } else {
            "${itemCount} item${if (itemCount != 1) "s" else ""}"
        }

        b.orderTimeText.text = formatTime(order.createdAt)

        b.statusChip.text = order.status.replaceFirstChar { it.uppercase() }
        val chipColor = when (order.status) {
            "pending" -> R.color.status_pending
            "processing" -> R.color.status_processing
            "completed" -> R.color.status_completed
            "canceled" -> R.color.status_canceled
            else -> R.color.status_pending
        }
        b.statusChip.setChipBackgroundColorResource(chipColor)

        b.statusChip.setOnClickListener { onStatusClick(order) }
        b.printButton.setOnClickListener { onPrintClick(order) }
    }

    private fun formatTime(isoDate: String?): String {
        if (isoDate == null) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoDate) ?: return ""
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            formatter.format(date)
        } catch (_: Exception) {
            ""
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order) = oldItem._id == newItem._id
        override fun areContentsTheSame(oldItem: Order, newItem: Order) = oldItem == newItem
    }
}
