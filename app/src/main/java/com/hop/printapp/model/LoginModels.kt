package com.hop.printapp.model

data class LoginRequest(
    val identifier: String,
    val password: String,
    val type: String = "Admin"
)

data class LoginResponse(
    val success: Boolean,
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

data class User(
    val _id: String,
    val name: String,
    val email: String?,
    val mobile: String?,
    val role: String,
    val assignedCafe: String?,
    val cafeId: String?
)
