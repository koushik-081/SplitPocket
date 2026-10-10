package com.example.budgetapptracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Small settings that are not money data: the user's name and the app lock switch.
class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(prefs.getString(KEY_NAME, null))
    private val _lockEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))

    // null until the welcome screen has been completed.
    val name: StateFlow<String?> = _name.asStateFlow()
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    fun save(name: String, lockEnabled: Boolean) {
        val trimmed = name.trim()
        prefs.edit()
            .putString(KEY_NAME, trimmed)
            .putBoolean(KEY_LOCK, lockEnabled)
            .apply()
        _name.value = trimmed
        _lockEnabled.value = lockEnabled
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_LOCK = "lock_enabled"
    }
}