package com.kurisuapi.domain.bridge

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MessageBridge {
    val connectionState: StateFlow<ConnectionState>
    val incomingMessages: SharedFlow<IncomingMessage>

    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun startLogin(): Result<QrCodeData>
    suspend fun checkLoginStatus(qrcode: String): Result<LoginStatus>
    suspend fun sendMessage(peerId: String, content: String): Result<Unit>
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    WAITING_SCAN,
    SCANED,
    LOGGED_IN,
    POLLING,
    ERROR
}

data class QrCodeData(
    val qrcode: String,           // QR code identifier
    val qrcodeImgContent: String  // Image URL or base64
)

sealed class LoginStatus {
    data object Waiting : LoginStatus()
    data object Scaned : LoginStatus()
    data class Confirmed(
        val botToken: String,
        val accountId: String,
        val userId: String,
        val baseUrl: String?
    ) : LoginStatus()
    data object Expired : LoginStatus()
    data class Error(val message: String) : LoginStatus()
}

data class IncomingMessage(
    val messageId: String,
    val peerId: String,
    val peerName: String,
    val content: String,
    val timestamp: Long,
    val type: Int,  // MessageItemType
    val contextToken: String?
)
