package com.hop.printapp

import android.content.Context
import androidx.core.content.edit

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("hop_session", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString("accessToken", null)
        set(v) = prefs.edit { putString("accessToken", v) }

    var refreshToken: String?
        get() = prefs.getString("refreshToken", null)
        set(v) = prefs.edit { putString("refreshToken", v) }

    var userId: String?
        get() = prefs.getString("userId", null)
        set(v) = prefs.edit { putString("userId", v) }

    var cafeId: String?
        get() = prefs.getString("cafeId", null)
        set(v) = prefs.edit { putString("cafeId", v) }

    val isLoggedIn: Boolean get() = !accessToken.isNullOrEmpty()

    fun clear() = prefs.edit { clear() }
}
