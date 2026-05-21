package com.hop.printapp

import com.google.gson.annotations.SerializedName

data class Order(
    @SerializedName("_id") val id: String = "",
    val orderStatus: String = "",
    val paymentStatus: String = "",
    val totalPrice: Double = 0.0,
    val createdAt: String = "",
    val pickupTime: String? = null,
    val items: List<OrderItem>? = null,
    val user: OrderUser? = null
)

data class OrderItem(
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0
)

data class OrderUser(
    @SerializedName("_id") val id: String = "",
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null
)
