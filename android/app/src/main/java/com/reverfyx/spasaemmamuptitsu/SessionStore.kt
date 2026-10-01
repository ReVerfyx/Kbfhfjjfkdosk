package com.reverfyx.spasaemmamuptitsu

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("spasaem_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("token") else putString("token", value)
            }.apply()
        }

    var username: String?
        get() = prefs.getString("username", null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("username") else putString("username", value)
            }.apply()
        }

    fun clear() = prefs.edit().clear().apply()
}
