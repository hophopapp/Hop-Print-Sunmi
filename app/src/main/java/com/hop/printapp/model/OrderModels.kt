package com.hop.printapp.model

data class OrdersResponse(
    val status: String,
    val data: List<Order>,
    val totalOrders: Int,
    val totalPages: Int,
    val currentPage: Int
)

data class Order(
    val _id: String,
    val user: OrderUser?,
    val product: Cafe?,
    val items: List<OrderItem>,
    val totalPrice: Double,
    val discountAmount: Double?,
    val status: String,
    val paymentStatus: String?,
    val paymentMethod: String?,
    val orderDate: String?,
    val notes: String?,
    val pickupTime: String?,
    val createdAt: String?,
    val usedToken: Int?
)

data class OrderUser(
    val _id: String?,
    val name: String?,
    val email: String?
)

data class Cafe(
    val _id: String?,
    val name: String?
)

data class OrderItem(
    val menuItem: MenuItemDetail?,
    val addons: List<AddonEntry>?,
    val quantity: Int
)

data class MenuItemDetail(
    val _id: String?,
    val name: String,
    val description: String?,
    val price: Double
)

data class AddonEntry(
    val type: String?,
    val option: ItemOptionRef?
)

data class ItemOptionRef(
    val _id: String?,
    val name: String?,
    val choices: List<Choice>?
)

data class Choice(
    val _id: String?,
    val label: String?,
    val additionalPrice: Double?
)

data class NewOrderEvent(
    val message: String?,
    val orderId: String,
    val totalPrice: Double?,
    val user: OrderUser?
)

data class OrderUpdatedEvent(
    val message: String?,
    val orderId: String,
    val user: String?,
    val status: String
)

data class UpdateStatusRequest(
    val status: String
)
