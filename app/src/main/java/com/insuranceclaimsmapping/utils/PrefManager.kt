package com.insuranceclaimsmapping.utils

import android.content.Context
import android.content.SharedPreferences

class PrefManager(context: Context) {
    private val pref: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun setLogin(isLoggedIn: Boolean) = pref.edit().putBoolean(IS_LOGGED_IN, isLoggedIn).apply()
    fun isLoggedIn(): Boolean = pref.getBoolean(IS_LOGGED_IN, false)

    fun setEmail(email: String) = pref.edit().putString(KEY_EMAIL, email).apply()
    fun getEmail(): String? = pref.getString(KEY_EMAIL, null)

    fun setRole(role: String) = pref.edit().putString(KEY_ROLE, role).apply()
    fun getRole(): String? = pref.getString(KEY_ROLE, null)

    fun setCustomId(id: String) = pref.edit().putString(KEY_CUSTOM_ID, id).apply()
    fun getCustomId(): String? = pref.getString(KEY_CUSTOM_ID, null)

    fun setNotificationsEnabled(enabled: Boolean) = pref.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    fun getNotificationsEnabled(): Boolean = pref.getBoolean(KEY_NOTIFICATIONS, true)

    fun setDarkModeEnabled(enabled: Boolean) = pref.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    fun getDarkModeEnabled(): Boolean = pref.getBoolean(KEY_DARK_MODE, false)

    fun setOnboardingShown(shown: Boolean) = pref.edit().putBoolean(KEY_ONBOARDING, shown).apply()
    fun isOnboardingShown(): Boolean = pref.getBoolean(KEY_ONBOARDING, false)

    fun logout() {
        val keepNotifications = getNotificationsEnabled()
        val keepDarkMode = getDarkModeEnabled()
        pref.edit().clear().apply()
        setNotificationsEnabled(keepNotifications)
        setDarkModeEnabled(keepDarkMode)
    }

    companion object {
        private const val PREF_NAME = "InsuranceClaimsPref"
        private const val IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_CUSTOM_ID = "customId"
        private const val KEY_NOTIFICATIONS = "notificationsEnabled"
        private const val KEY_DARK_MODE = "darkModeEnabled"
        private const val KEY_ONBOARDING = "onboardingShown"
    }
}
