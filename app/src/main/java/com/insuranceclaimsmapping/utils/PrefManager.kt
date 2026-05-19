package com.insuranceclaimsmapping.utils

import android.content.Context
import android.content.SharedPreferences

class PrefManager(context: Context) {
    private val pref: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()

    fun setLogin(isLoggedIn: Boolean) {
        editor.putBoolean(IS_LOGGED_IN, isLoggedIn)
        editor.commit()
    }

    fun isLoggedIn(): Boolean {
        return pref.getBoolean(IS_LOGGED_IN, false)
    }

    fun setEmail(email: String) {
        editor.putString(KEY_EMAIL, email)
        editor.commit()
    }

    fun getEmail(): String? {
        return pref.getString(KEY_EMAIL, null)
    }

    fun setRole(role: String) {
        editor.putString(KEY_ROLE, role)
        editor.commit()
    }

    fun getRole(): String? {
        return pref.getString(KEY_ROLE, null)
    }

    fun setCustomId(id: String) {
        editor.putString(KEY_CUSTOM_ID, id)
        editor.commit()
    }

    fun getCustomId(): String? {
        return pref.getString(KEY_CUSTOM_ID, null)
    }

    fun logout() {
        val keepNotifications = getNotificationsEnabled()
        editor.clear()
        editor.commit()
        setNotificationsEnabled(keepNotifications) // Preserve this preference across logins
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        editor.putBoolean(KEY_NOTIFICATIONS, enabled)
        editor.commit()
    }

    fun getNotificationsEnabled(): Boolean {
        return pref.getBoolean(KEY_NOTIFICATIONS, true) // Default to true
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        editor.putBoolean(KEY_DARK_MODE, enabled)
        editor.commit()
    }

    fun getDarkModeEnabled(): Boolean {
        return pref.getBoolean(KEY_DARK_MODE, false) // Default to light
    }

    companion object {
        private const val PREF_NAME = "InsuranceClaimsPref"
        private const val IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_CUSTOM_ID = "customId"
        private const val KEY_NOTIFICATIONS = "notificationsEnabled"
        private const val KEY_DARK_MODE = "darkModeEnabled"
    }
}
