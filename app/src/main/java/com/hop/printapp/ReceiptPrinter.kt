package com.hop.printapp

import android.os.RemoteException
import com.hop.printapp.model.Order
import woyou.aidlservice.jiuiv5.ICallback
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ReceiptPrinter {

    fun printOrder(order: Order, printer: SunmiPrinterHelper, onResult: (Boolean, String) -> Unit) {
        val svc = printer.service
        if (svc == null) {
            onResult(false, "Printer not connected")
            return
        }

        try {
            svc.printerInit(null)

            svc.setAlignment(1, null)
            svc.setFontSize(28f, null)
            svc.printText("================================\n", null)
            svc.printText("HOP COFFEE\n", null)
            svc.printText("================================\n", null)

            svc.setAlignment(0, null)
            svc.setFontSize(24f, null)
            svc.printText("Order: #${order._id.takeLast(6).uppercase()}\n", null)
            svc.printText("Date:  ${formatDateTime(order.createdAt)}\n", null)
            svc.printText("Customer: ${order.user?.name ?: "Guest"}\n", null)
            if (!order.pickupTime.isNullOrEmpty()) {
                svc.printText("Pickup: ${formatTime(order.pickupTime)}\n", null)
            }
            if (!order.notes.isNullOrEmpty()) {
                svc.printText("Notes: ${order.notes}\n", null)
            }
            svc.printText("--------------------------------\n", null)

            for (item in order.items) {
                val name = item.menuItem?.name ?: "Item"
                val qty = item.quantity
                val price = item.menuItem?.price?.let { it * qty } ?: 0.0
                svc.printColumnsString(
                    arrayOf(name, "x$qty", "$${String.format("%.2f", price)}"),
                    intArrayOf(16, 5, 11),
                    intArrayOf(0, 1, 2),
                    null
                )

                item.addons?.forEach { addon ->
                    val addonName = addon.option?.name ?: addon.type ?: return@forEach
                    val addonChoices = addon.option?.choices
                    if (addonChoices != null && addonChoices.isNotEmpty()) {
                        for (choice in addonChoices) {
                            val label = choice.label ?: continue
                            val extra = choice.additionalPrice ?: 0.0
                            if (extra > 0) {
                                svc.printColumnsString(
                                    arrayOf("  $label", "", "+$${String.format("%.2f", extra)}"),
                                    intArrayOf(16, 5, 11),
                                    intArrayOf(0, 1, 2),
                                    null
                                )
                            } else {
                                svc.setFontSize(20f, null)
                                svc.printText("  $label\n", null)
                                svc.setFontSize(24f, null)
                            }
                        }
                    } else {
                        svc.setFontSize(20f, null)
                        svc.printText("  $addonName\n", null)
                        svc.setFontSize(24f, null)
                    }
                }
            }

            svc.printText("--------------------------------\n", null)

            svc.setFontSize(26f, null)
            svc.printColumnsString(
                arrayOf("TOTAL:", "$${String.format("%.2f", order.totalPrice)}"),
                intArrayOf(16, 16),
                intArrayOf(0, 2),
                null
            )

            val discount = order.discountAmount ?: 0.0
            if (discount > 0) {
                svc.setFontSize(22f, null)
                svc.printColumnsString(
                    arrayOf("Discount:", "-$${String.format("%.2f", discount)}"),
                    intArrayOf(16, 16),
                    intArrayOf(0, 2),
                    null
                )
            }

            svc.setFontSize(24f, null)
            svc.setAlignment(1, null)
            svc.printText("================================\n", null)
            svc.printText("Thank you for ordering\n", null)
            svc.printText("with Hop!\n", null)
            svc.printText("================================\n", null)

            svc.lineWrap(4, object : ICallback.Stub() {
                override fun onRunResult(isSuccess: Boolean) {
                    onResult(isSuccess, if (isSuccess) "Receipt printed" else "Print failed")
                }
                override fun onReturnString(result: String?) {}
                override fun onRaiseException(code: Int, msg: String?) {
                    onResult(false, msg ?: "Print error")
                }
                override fun onPrintResult(code: Int, msg: String?) {}
            })
        } catch (e: RemoteException) {
            onResult(false, e.message ?: "Remote exception")
        }
    }

    private fun formatDateTime(isoDate: String?): String {
        if (isoDate == null) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoDate) ?: return ""
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        } catch (_: Exception) { "" }
    }

    private fun formatTime(isoDate: String?): String {
        if (isoDate == null) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoDate) ?: return ""
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } catch (_: Exception) { "" }
    }
}
