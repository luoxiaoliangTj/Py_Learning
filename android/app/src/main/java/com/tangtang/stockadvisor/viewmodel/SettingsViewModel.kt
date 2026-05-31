package com.tangtang.stockadvisor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val tushareToken: String = "",
    val apiBaseUrl: String = "http://10.0.2.2:8000/",
    val isDarkMode: Boolean = false,
    val isTokenValid: Boolean? = null,
    val isTokenValidating: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: com.tangtang.stockadvisor.data.repository.StockRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateTushareToken(token: String) {
        _uiState.value = _uiState.value.copy(tushareToken = token, isTokenValid = null)
    }

    fun updateApiBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(apiBaseUrl = url)
    }

    fun toggleDarkMode() {
        _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }

    fun validateToken() {
        val token = _uiState.value.tushareToken
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入Tushare Token")
            return
        }

        _uiState.value = _uiState.value.copy(isTokenValidating = true, error = null)
        viewModelScope.launch {
            val result = repository.validateToken(token)
            result.onSuccess { isValid ->
                _uiState.value = _uiState.value.copy(
                    isTokenValidating = false,
                    isTokenValid = isValid,
                    successMessage = if (isValid) "Token验证成功" else "Token无效"
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isTokenValidating = false,
                    isTokenValid = false,
                    error = e.message ?: "Token验证失败"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
