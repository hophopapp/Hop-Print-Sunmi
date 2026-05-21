package com.hop.printapp

/**
 * Converts an Order into the plain-text receipt String expected by
 * SunmiPrinterHelper.printText().  This is the single shared formatter used
 * by both the manual Print button (OrdersActivity) and the socket auto-print
 * handler, so receipt layout is never duplicated.
 */
object OrderFormatter {

    fun format(order: Order): String {
        val sb = StringBuilder()
        sb.appendLine("=== HOP CAFE ===")
        sb.appendLine("Order: #${order.id.takeLast(6).uppercase()}")

        order.user?.let { u ->
            val label = u.name ?: u.email ?: u.phone
            if (!label.isNullOrBlank()) sb.appendLine("Customer: $label")
        }

        sb.appendLine("Status: ${order.orderStatus.capitalized()}")
        sb.appendLine("Payment: ${order.paymentStatus.capitalized()}")

        if (!order.pickupTime.isNullOrBlank()) {
            sb.appendLine("Pickup: ${order.pickupTime.take(16).replace('T', ' ')}")
        }

        if (!order.items.isNullOrEmpty()) {
            sb.appendLine("----------------")
            for (item in order.items) {
                sb.appendLine(item.name)
                val lineTotal = item.price * item.quantity
                sb.appendLine("  x${item.quantity}  \$${String.format("%.2f", lineTotal)}")
            }
        }

        sb.appendLine("----------------")
        sb.appendLine("TOTAL: \$${String.format("%.2f", order.totalPrice)}")
        sb.append("================")
        return sb.toString()
    }

    /** Minimal receipt when only socket payload data is available (no items yet). */
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
