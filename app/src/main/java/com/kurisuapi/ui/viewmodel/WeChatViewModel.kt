package com.kurisuapi.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.data.wechat.WeChatRepository
import com.kurisuapi.domain.bridge.*
import com.kurisuapi.service.WeChatBotService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val weChatBridge: WeChatBridge,
    private val weChatRepository: WeChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = weChatBridge.connectionState

    private val _qrCodeData = MutableStateFlow<QrCodeData?>(null)
    val qrCodeData: StateFlow<QrCodeData?> = _qrCodeData.asStateFlow()

    private val _loginStatus = MutableStateFlow<String?>(null)
    val loginStatus: StateFlow<String?> = _loginStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 机器人行为设置
    private val _botProactiveEnabled = MutableStateFlow(false)
    val botProactiveEnabled: StateFlow<Boolean> = _botProactiveEnabled.asStateFlow()

    private val _botProactiveInterval = MutableStateFlow(SettingsRepository.DEFAULT_PROACTIVE_INTERVAL)
    val botProactiveInterval: StateFlow<Int> = _botProactiveInterval.asStateFlow()

    private val _proactiveMaxPerDay = MutableStateFlow(SettingsRepository.DEFAULT_PROACTIVE_MAX_PER_DAY)
    val proactiveMaxPerDay: StateFlow<Int> = _proactiveMaxPerDay.asStateFlow()

    private val _proactiveTodayCount = MutableStateFlow(0)
    val proactiveTodayCount: StateFlow<Int> = _proactiveTodayCount.asStateFlow()

    private val _proactiveQuietStart = MutableStateFlow(SettingsRepository.DEFAULT_PROACTIVE_QUIET_START)
    val proactiveQuietStart: StateFlow<Int> = _proactiveQuietStart.asStateFlow()

    private val _proactiveQuietEnd = MutableStateFlow(SettingsRepository.DEFAULT_PROACTIVE_QUIET_END)
    val proactiveQuietEnd: StateFlow<Int> = _proactiveQuietEnd.asStateFlow()

    private val _showThinking = MutableStateFlow(true)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    private val _autoMemoryEnabled = MutableStateFlow(true)
    val autoMemoryEnabled: StateFlow<Boolean> = _autoMemoryEnabled.asStateFlow()

    private val _circadianEnabled = MutableStateFlow(false) // 默认关闭
    val circadianEnabled: StateFlow<Boolean> = _circadianEnabled.asStateFlow()

    // Bug4: 防止重复点击，记录当前是否正在登录
    private var isLoggingIn = false

    init {
        // Auto-connect if already logged in
        if (weChatRepository.isLoggedIn) {
            connect()
        }
        // 加载机器人行为设置
        loadBotSettings()
        loadShowThinking()
        loadAutoMemory()
        loadCircadian()
    }

    private fun loadBotSettings() {
        viewModelScope.launch {
            _botProactiveEnabled.value = settingsRepository.isBotProactiveEnabled()
            _botProactiveInterval.value = settingsRepository.getBotProactiveInterval()
        }
        // 主动消息追踪设置
        loadProactiveTracking()
    }

    private fun loadProactiveTracking() {
        viewModelScope.launch {
            _proactiveMaxPerDay.value = settingsRepository.getProactiveMaxPerDay()
            _proactiveTodayCount.value = settingsRepository.getProactiveDailyCount()
            _proactiveQuietStart.value = settingsRepository.getProactiveQuietStart()
            _proactiveQuietEnd.value = settingsRepository.getProactiveQuietEnd()
        }
    }

    private fun loadShowThinking() {
        viewModelScope.launch {
            _showThinking.value = settingsRepository.isBotShowThinkingEnabled()
        }
    }

    private fun loadAutoMemory() {
        viewModelScope.launch {
            _autoMemoryEnabled.value = settingsRepository.isAutoMemoryEnabled()
        }
    }

    fun setBotProactiveEnabled(enabled: Boolean) {
        _botProactiveEnabled.value = enabled
        viewModelScope.launch {
            settingsRepository.setBotProactiveEnabled(enabled)
        }
    }

    fun setBotProactiveInterval(minutes: Int) {
        val clamped = minutes.coerceIn(10, 1440)
        _botProactiveInterval.value = clamped
        viewModelScope.launch {
            settingsRepository.setBotProactiveInterval(clamped)
        }
    }

    fun setProactiveMaxPerDay(max: Int) {
        val clamped = max.coerceIn(1, 20)
        _proactiveMaxPerDay.value = clamped
        viewModelScope.launch {
            settingsRepository.setProactiveMaxPerDay(clamped)
        }
    }

    fun setProactiveQuietStart(hour: Int) {
        _proactiveQuietStart.value = hour.coerceIn(0, 23)
        viewModelScope.launch { settingsRepository.setProactiveQuietStart(hour.coerceIn(0, 23)) }
    }

    fun setProactiveQuietEnd(hour: Int) {
        _proactiveQuietEnd.value = hour.coerceIn(0, 23)
        viewModelScope.launch { settingsRepository.setProactiveQuietEnd(hour.coerceIn(0, 23)) }
    }

    fun setShowThinking(enabled: Boolean) {
        _showThinking.value = enabled
        viewModelScope.launch {
            settingsRepository.setBotShowThinkingEnabled(enabled)
        }
    }

    fun setAutoMemoryEnabled(enabled: Boolean) {
        _autoMemoryEnabled.value = enabled
        viewModelScope.launch {
            settingsRepository.setAutoMemoryEnabled(enabled)
        }
    }

    private fun loadCircadian() {
        viewModelScope.launch {
            _circadianEnabled.value = settingsRepository.isCircadianEnabled()
        }
    }

    fun setCircadianEnabled(enabled: Boolean) {
        _circadianEnabled.value = enabled
        viewModelScope.launch {
            settingsRepository.setCircadianEnabled(enabled)
        }
    }



    fun startLogin() {
        // Bug4: 防止重复点击
        if (isLoggingIn) return
        isLoggingIn = true

        viewModelScope.launch {
            _error.value = null
            _loginStatus.value = "正在获取二维码..."

            val result = weChatBridge.startLogin()
            result.fold(
                onSuccess = { qrData ->
                    _qrCodeData.value = qrData
                    _loginStatus.value = "请扫描二维码"
                    // Start polling for QR code status
                    pollQrCodeStatus(qrData.qrcode)
                },
                onFailure = { e ->
                    _error.value = "获取二维码失败，请检查网络连接：${e.message ?: "未知错误"}"
                    _loginStatus.value = null
                    isLoggingIn = false
                }
            )
        }
    }

    private fun pollQrCodeStatus(qrcode: String) {
        viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 60  // ~3 minutes

            try {
                while (attempts < maxAttempts) {
                    val result = weChatBridge.checkLoginStatus(qrcode)
                    result.fold(
                        onSuccess = { status ->
                            when (status) {
                                is LoginStatus.Waiting -> {
                                    _loginStatus.value = "等待扫码..."
                                }
                                is LoginStatus.Scaned -> {
                                    _loginStatus.value = "已扫码，请在手机上确认"
                                }
                                is LoginStatus.Confirmed -> {
                                    _loginStatus.value = "登录成功！"
                                    _qrCodeData.value = null
                                    isLoggingIn = false
                                    connect()
                                    return@launch
                                }
                                is LoginStatus.Expired -> {
                                    _loginStatus.value = "二维码已过期"
                                    _qrCodeData.value = null
                                    isLoggingIn = false
                                    // Bug 12 fix: reset bridge connection state
                                    weChatBridge.disconnect()
                                    return@launch
                                }
                                is LoginStatus.Error -> {
                                    _error.value = status.message
                                    _loginStatus.value = null
                                    isLoggingIn = false
                                    // Bug 12 fix: reset bridge connection state
                                    weChatBridge.disconnect()
                                    return@launch
                                }
                            }
                        },
                        onFailure = { e ->
                            // Bug 11 fix: don't double-increment; only increment once at end
                            _error.value = "检查登录状态失败，请检查网络连接：${e.message ?: "未知错误"}"
                        }
                    )

                    attempts++  // Bug 11 fix: single increment point
                    delay(3_000)
                }

                // 超时
                _loginStatus.value = "二维码已过期，请重新获取"
                _qrCodeData.value = null
                isLoggingIn = false
                // Bug 12 fix: reset bridge connection state on timeout
                weChatBridge.disconnect()
            } finally {
                isLoggingIn = false
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            val result = weChatBridge.connect()
            result.fold(
                onSuccess = {
                    _loginStatus.value = "已连接"
                    WeChatBotService.start(appContext)
                },
                onFailure = { e ->
                    _error.value = "连接微信服务失败，请确认服务已启动：${e.message ?: "未知错误"}"
                }
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            weChatBridge.disconnect()
            WeChatBotService.stop(appContext)
            _loginStatus.value = "已断开"
        }
    }

    fun logout() {
        viewModelScope.launch {
            weChatRepository.clearSession()
            weChatBridge.disconnect()
            WeChatBotService.stop(appContext)
            _qrCodeData.value = null
            _loginStatus.value = null
            _error.value = null
            isLoggingIn = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
