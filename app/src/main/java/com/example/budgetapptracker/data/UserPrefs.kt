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
    private val _autoFile = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FILE, true))
    private val _ownNames = MutableStateFlow(prefs.getString(KEY_OWN_NAMES, "").orEmpty())
    private val _eveningReminder = MutableStateFlow(prefs.getBoolean(KEY_EVENING_REMINDER, false))
    private val _sortPromptedOn = MutableStateFlow(prefs.getString(KEY_SORT_PROMPTED_ON, null))

    // null until the welcome screen has been completed.
    val name: StateFlow<String?> = _name.asStateFlow()
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    // The month whose recap was closed with "Got it", written like "2026-10".
    val recapDismissedMonth: StateFlow<String?> = _recapDismissedMonth.asStateFlow()
    // File payments to an envelope by themselves when the guess is reliable.
    val autoFile: StateFlow<Boolean> = _autoFile.asStateFlow()
    // Your name as banks show it, comma separated, to spot transfers to your own accounts.
    val ownNames: StateFlow<String> = _ownNames.asStateFlow()
    // A 9 PM notification when expenses are still waiting for an envelope.
    val eveningReminder: StateFlow<Boolean> = _eveningReminder.asStateFlow()
    // The day ("2026-11-17") the sorting sheet last opened by itself, so it pops up once a day.
    val sortPromptedOn: StateFlow<String?> = _sortPromptedOn.asStateFlow()

    // ownNames split into separate names, ignoring blanks.
    val ownNameList: List<String>
        get() = _ownNames.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

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

    fun setAutoFile(on: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FILE, on).apply()
        _autoFile.value = on
    }

    fun setOwnNames(names: String) {
        prefs.edit().putString(KEY_OWN_NAMES, names).apply()
        _ownNames.value = names
    }

    fun setEveningReminder(on: Boolean) {
        prefs.edit().putBoolean(KEY_EVENING_REMINDER, on).apply()
        _eveningReminder.value = on
    }

    fun markSortPrompted(day: String) {
        prefs.edit().putString(KEY_SORT_PROMPTED_ON, day).apply()
        _sortPromptedOn.value = day
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_THEME = "theme_mode"
        const val KEY_RECAP_DISMISSED = "recap_dismissed_month"
        const val KEY_AUTO_FILE = "auto_file"
        const val KEY_OWN_NAMES = "own_names"
        const val KEY_EVENING_REMINDER = "evening_reminder"
        const val KEY_SORT_PROMPTED_ON = "sort_prompted_on"
    }
}
