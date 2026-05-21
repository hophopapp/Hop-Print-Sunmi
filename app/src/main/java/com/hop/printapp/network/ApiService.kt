package com.hop.printapp.network

import com.hop.printapp.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("admin/orders")
    suspend fun getOrders(
        @Query("cafe") cafeId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("sortBy") sortBy: String = "createdAt",
        @Query("sortOrder") sortOrder: Int = -1,
        @Query("orderStatus") orderStatus: String? = null
    ): Response<OrdersResponse>

    @PATCH("admin/order/{orderId}")
    suspend fun updateOrderStatus(
        @Path("orderId") orderId: String,
        @Body body: UpdateStatusRequest
    ): Response<Any>
}
