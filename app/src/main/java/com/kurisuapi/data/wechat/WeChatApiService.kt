package com.kurisuapi.data.wechat

import retrofit2.http.*

interface WeChatApiService {

    @GET("ilink/bot/get_bot_qrcode")
    suspend fun getQrCode(
        @Query("bot_type") botType: Int = 3,
        @Header("iLink-App-Id") appId: String = "bot",
        @Header("iLink-App-ClientVersion") clientVersion: String = CHANNEL_VERSION_BITCODE
    ): String

    @GET("ilink/bot/get_qrcode_status")
    suspend fun checkQrCodeStatus(
        @Query("qrcode") qrcode: String,
        @Header("iLink-App-Id") appId: String = "bot",
        @Header("iLink-App-ClientVersion") clientVersion: String = CHANNEL_VERSION_BITCODE
    ): String

    @POST("ilink/bot/getupdates")
    suspend fun getUpdates(
        @Body request: GetUpdatesRequest,
        @Header("AuthorizationType") authType: String = "ilink_bot_token",
        @Header("X-WECHAT-UIN") uin: String,
        @Header("Authorization") authorization: String,
        @Header("iLink-App-Id") appId: String = "bot",
        @Header("iLink-App-ClientVersion") clientVersion: String = CHANNEL_VERSION_BITCODE
    ): GetUpdatesResponse

    @POST("ilink/bot/sendmessage")
    suspend fun sendMessage(
        @Body request: SendMessageRequest,
        @Header("AuthorizationType") authType: String = "ilink_bot_token",
        @Header("X-WECHAT-UIN") uin: String,
        @Header("Authorization") authorization: String,
        @Header("iLink-App-Id") appId: String = "bot",
        @Header("iLink-App-ClientVersion") clientVersion: String = CHANNEL_VERSION_BITCODE
    ): String

    @POST("ilink/bot/getconfig")
    suspend fun getConfig(
        @Body request: GetConfigRequest,
        @Header("AuthorizationType") authType: String = "ilink_bot_token",
        @Header("X-WECHAT-UIN") uin: String,
        @Header("Authorization") authorization: String,
        @Header("iLink-App-Id") appId: String = "bot",
        @Header("iLink-App-ClientVersion") clientVersion: String = CHANNEL_VERSION_BITCODE
    ): GetConfigResponse

    @POST("ilink/bot/sendtyping")
    suspend fun sendTyping(
        @Body request: SendTypingRequest,
        @Header("AuthorizationType") authType: String = "ilink_bot_token",
        @Header("X-WECHAT-UIN") uin: String,
        @Header("Authorization") authorization: String,
        @Header("iLink-App-Id") appId: String = "bot",
        @Header("iLink-App-ClientVersion") clientVersion: String = CHANNEL_VERSION_BITCODE
    ): String
}

// Channel version as bitcode: (major << 16) | (minor << 8) | patch
val CHANNEL_VERSION_BITCODE: String = run {
    val parts = CHANNEL_VERSION.split(".").map { it.toIntOrNull() ?: 0 }
    // 注意：Kotlin 中 shl / or 是同优先级左结合的中缀函数，没有 C 的位运算优先级，
    // 必须显式加括号，否则会算成 ((((a shl 16) or b) shl 8) or c)
    val major = parts.getOrElse(0) { 0 } and 0xFF
    val minor = parts.getOrElse(1) { 0 } and 0xFF
    val patch = parts.getOrElse(2) { 0 } and 0xFF
    ((major shl 16) or (minor shl 8) or patch).toString()
}
