package com.trionsandroid.app.ui.settings

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Loading : ConnectionTestState
    data object Success : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}
