package com.hop.printapp.storage

import android.content.Context
import android.content.SharedPreferences
import com.hop.printapp.model.User

object SessionManager {

    private const val PREFS_NAME = "hop_print_session"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) = prefs.edit().putString("user_name", value).apply()

    var cafeId: String?
        get() = prefs.getString("cafe_id", null)
        set(value) = prefs.edit().putString("cafe_id", value).apply()

    var userRole: String?
        get() = prefs.getString("user_role", null)
        set(value) = prefs.edit().putString("user_role", value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrEmpty() && !cafeId.isNullOrEmpty()

    fun saveLoginData(accessToken: String, refreshToken: String, user: User) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.userId = user._id
        this.userName = user.name
        this.cafeId = user.cafeId ?: user.assignedCafe
        this.userRole = user.role
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
