package com.example.budgetapptracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    System("Auto"),
    Light("Light"),
    Dark("Dark"),
}

// Small settings that are not money data: the user's name, the app lock switch and the theme.
class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(prefs.getString(KEY_NAME, null))
    private val _lockEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) }
            ?: ThemeMode.System
    )

    private val _recapDismissedMonth = MutableStateFlow(prefs.getString(KEY_RECAP_DISMISSED, null))

    // null until the welcome screen has been completed.
    val name: StateFlow<String?> = _name.asStateFlow()
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    // The month whose recap was closed with "Got it", written like "2026-10".
    val recapDismissedMonth: StateFlow<String?> = _recapDismissedMonth.asStateFlow()

    fun save(name: String, lockEnabled: Boolean) {
        val trimmed = name.trim()
        prefs.edit()
            .putString(KEY_NAME, trimmed)
            .putBoolean(KEY_LOCK, lockEnabled)
            .apply()
        _name.value = trimmed
        _lockEnabled.value = lockEnabled
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun dismissRecap(month: String) {
        prefs.edit().putString(KEY_RECAP_DISMISSED, month).apply()
        _recapDismissedMonth.value = month
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_THEME = "theme_mode"
        const val KEY_RECAP_DISMISSED = "recap_dismissed_month"
    }
}
