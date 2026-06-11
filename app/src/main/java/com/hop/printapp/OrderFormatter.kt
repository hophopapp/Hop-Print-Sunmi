package com.hop.printapp

object OrderFormatter {

    fun format(order: Order): String {
        val sb = StringBuilder()
        sb.appendLine("=== HOP CAFE ===")
        sb.appendLine("Order: #${order.id.takeLast(6).uppercase()}")
        sb.appendLine("Status: ${order.orderStatus.capitalized()}")

        order.user?.let { u ->
            val label = u.name?.takeIf { it.isNotBlank() } ?: u.email?.takeIf { it.isNotBlank() }
            if (!label.isNullOrBlank()) sb.appendLine("Customer: $label")
        }

        sb.appendLine("Payment: ${order.paymentStatus.capitalized()}")

        if (!order.pickupTime.isNullOrBlank()) {
            sb.appendLine("Pickup: ${order.pickupTime.take(16).replace('T', ' ')}")
        }

        if (!order.notes.isNullOrBlank()) {
            sb.appendLine("Note: ${order.notes}")
        }

        if (!order.items.isNullOrEmpty()) {
            sb.appendLine("----------------")
            for (item in order.items) {
                val itemName = item.menuItem?.name ?: "(item)"
                val unitPrice = item.menuItem?.price ?: 0.0
                val lineTotal = unitPrice * item.quantity
                sb.appendLine("$itemName  x${item.quantity}  \$${String.format("%.2f", lineTotal)}")
                item.addons?.forEach { addon ->
                    val optName = addon.option?.name?.takeIf { it.isNotBlank() } ?: return@forEach
                    sb.appendLine("  + $optName")
                }
            }
        }

        sb.appendLine("----------------")
        sb.appendLine("TOTAL: \$${String.format("%.2f", order.totalPrice)}")
        sb.append("================")
        return sb.toString()
    }

    fun formatMinimal(orderId: String, totalPrice: Double, customerName: String?): String {
        val sb = StringBuilder()
        sb.appendLine("=== HOP CAFE ===")
        sb.appendLine("Order: #${orderId.takeLast(6).uppercase()}")
        if (!customerName.isNullOrBlank()) sb.appendLine("Customer: $customerName")
        sb.appendLine("----------------")
        sb.appendLine("TOTAL: \$${String.format("%.2f", totalPrice)}")
        sb.append("================")
        return sb.toString()
    }

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }
}
