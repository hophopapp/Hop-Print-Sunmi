package com.hop.printapp

import com.google.gson.annotations.SerializedName

data class Order(
    @SerializedName("_id") val id: String = "",
    @SerializedName("status") val orderStatus: String = "",
    val paymentStatus: String = "",
    val paymentMethod: String = "",
    val totalPrice: Double = 0.0,
    val discountAmount: Double = 0.0,
    @SerializedName("orderDate") val createdAt: String = "",
    val pickupTime: String? = null,
    val notes: String? = null,
    val items: List<OrderItem>? = null,
    val user: OrderUser? = null
)

data class OrderItem(
    val quantity: Int = 1,
    val menuItem: MenuItem? = null,
    val addons: List<OrderAddon>? = null
)

data class MenuItem(
    @SerializedName("_id") val id: String = "",
    val name: String = "",
    val description: String? = null,
    val price: Double = 0.0
)

data class OrderAddon(
    val type: String = "",
    val option: AddonOption? = null
)

data class AddonOption(
    @SerializedName("_id") val id: String = "",
    val name: String = ""
)

data class OrderUser(
    @SerializedName("_id") val id: String = "",
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null
)
