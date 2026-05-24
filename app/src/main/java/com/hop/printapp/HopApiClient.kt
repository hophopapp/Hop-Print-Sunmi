package com.hop.printapp

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object HopApiClient {

    private const val BASE = "https://api.hophop.cafe/api/v1"
    private val JSON_MEDIA = "application/json".toMediaType()
    private val gson = Gson()
    private val http = OkHttpClient()

    // ── Result types ────────────────────────────────────────────────────────

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>() {
            val isUnauthorized get() = code == 401
        }
    }

    data class LoginData(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val cafeId: String
    )

    // ── Auth ────────────────────────────────────────────────────────────────

    suspend fun login(identifier: String, password: String): ApiResult<LoginData> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(
                    mapOf("identifier" to identifier, "password" to password, "type" to "Admin")
                )
                val req = Request.Builder()
                    .url("$BASE/auth/login")
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()
                val resp = http.newCall(req).execute()
                val json = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val obj = gson.fromJson(json, JsonObject::class.java)
                    val user = obj.getAsJsonObject("user")
                    // assignedCafe may be a plain ObjectId string or a populated object
                    val cafeElement = user.get("assignedCafe")
                    val cafeId = when {
                        cafeElement == null || cafeElement.isJsonNull -> ""
                        cafeElement.isJsonObject ->
                            cafeElement.asJsonObject.get("_id")?.asString ?: ""
                        else -> cafeElement.asString
                    }.ifEmpty {
                        // fallback to cafeId field if assignedCafe was empty
                        runCatching { user.get("cafeId")?.asString }.getOrNull() ?: ""
                    }
                    ApiResult.Success(
                        LoginData(
                            accessToken = obj.get("accessToken").asString,
                            refreshToken = obj.get("refreshToken").asString,
                            userId = user.get("_id").asString,
                            cafeId = cafeId
                        )
                    )
                } else {
                    val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
                    val msg = obj?.get("message")?.asString ?: "Login failed (${resp.code})"
                    ApiResult.Error(msg, resp.code)
                }
            } catch (e: IOException) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }

    suspend fun refreshToken(refreshToken: String): ApiResult<LoginData> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(mapOf("refreshToken" to refreshToken))
                val req = Request.Builder()
                    .url("$BASE/auth/refresh-token")
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()
                val resp = http.newCall(req).execute()
                val json = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    val obj = gson.fromJson(json, JsonObject::class.java)
                    ApiResult.Success(
                        LoginData(
                            accessToken = obj.get("accessToken").asString,
                            refreshToken = runCatching { obj.get("refreshToken").asString }
                                .getOrDefault(""),
                            userId = "",
                            cafeId = ""
                        )
                    )
                } else {
                    ApiResult.Error("Token refresh failed", resp.code)
                }
            } catch (e: IOException) {
                ApiResult.Error(e.message ?: "Network error")
            }
        }

    // ── Orders ──────────────────────────────────────────────────────────────

    data class OrdersPage(
        val orders: List<Order>,
        val currentPage: Int,
        val totalPages: Int,
        val totalOrders: Int
    )

    suspend fun getOrders(
        token: String,
        cafeId: String,
        orderStatus: String? = "pending",  // null = no filter (all statuses)
        page: Int = 1,
        limit: Int = 20
    ): ApiResult<OrdersPage> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.hophop.cafe/api/v1/admin/orders".toHttpUrl()
                .newBuilder()
                .apply { if (cafeId.isNotEmpty()) addQueryParameter("cafe", cafeId) }
                .apply { if (orderStatus != null) addQueryParameter("orderStatus", orderStatus) }
                .addQueryParameter("sortBy", "createdAt")
                .addQueryParameter("sortOrder", "-1")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", limit.toString())
                .build()
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val resp = http.newCall(req).execute()
            val json = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val obj = gson.fromJson(json, JsonObject::class.java)
                val arr = obj.getAsJsonArray("data")
                val orders = arr.mapNotNull { element ->
                    runCatching { gson.fromJson(element, Order::class.java) }.getOrNull()
                }
                ApiResult.Success(
                    OrdersPage(
                        orders = orders,
                        currentPage = obj.get("currentPage")?.asInt ?: page,
                        totalPages = obj.get("totalPages")?.asInt ?: 1,
                        totalOrders = obj.get("totalOrders")?.asInt ?: orders.size
                    )
                )
            } else {
                val obj = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()
                val msg = obj?.get("message")?.asString ?: "Failed to load orders (${resp.code})"
                ApiResult.Error(msg, resp.code)
            }
        } catch (e: IOException) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun updateOrderStatus(
        token: String,
        orderId: String,
        status: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf("status" to status))
            val req = Request.Builder()
                .url("$BASE/admin/order/$orderId")
                .header("Authorization", "Bearer $token")
                .patch(body.toRequestBody(JSON_MEDIA))
                .build()
            val resp = http.newCall(req).execute()
            if (resp.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Update failed (${resp.code})", resp.code)
        } catch (e: IOException) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }
}
