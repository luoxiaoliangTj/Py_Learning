package com.tangtang.stockadvisor.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val tushareToken: String = "",
    val backendUrl: String = "http://127.0.0.1:8000",
    val enableNotifications: Boolean = true,
    val enableDarkMode: Boolean = false,
    val refreshInterval: Int = 5,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("stock_advisor_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            tushareToken = prefs.getString("tushare_token", "") ?: "",
            backendUrl = prefs.getString("backend_url", "http://127.0.0.1:8000") ?: "http://127.0.0.1:8000",
            enableNotifications = prefs.getBoolean("enable_notifications", true),
            enableDarkMode = prefs.getBoolean("enable_dark_mode", false),
            refreshInterval = prefs.getInt("refresh_interval", 5)
        )
    }

    fun saveSettings(
        tushareToken: String,
        backendUrl: String,
        enableNotifications: Boolean,
        enableDarkMode: Boolean
    ) {
        prefs.edit().apply {
            putString("tushare_token", tushareToken)
            putString("backend_url", backendUrl)
            putBoolean("enable_notifications", enableNotifications)
            putBoolean("enable_dark_mode", enableDarkMode)
            apply()
        }
        _uiState.value = _uiState.value.copy(
            tushareToken = tushareToken,
            backendUrl = backendUrl,
            enableNotifications = enableNotifications,
            enableDarkMode = enableDarkMode,
            saved = true
        )
    }
}
