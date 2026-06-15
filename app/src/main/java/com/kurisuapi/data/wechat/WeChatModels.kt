package com.kurisuapi.data.wechat

import com.google.gson.annotations.SerializedName

// === QR Code Login ===

// 通用 API 响应包装（兼容各种返回格式）
data class WeChatApiResponse<T>(
    val ret: Int? = null,
    val errcode: Int? = null,
    val errmsg: String? = null,
    val data: T? = null,
    // 直接字段（当响应没有包装在 data 中时）
    val qrcode: String? = null,
    @SerializedName("qr_code")
    val qrCode: String? = null,
    @SerializedName("qrcode_img_content")
    val qrcodeImgContent: String? = null,
    @SerializedName("qr_code_url")
    val qrCodeUrl: String? = null,
    val url: String? = null
)

data class QrCodeResponse(
    val qrcode: String? = null,
    @SerializedName("qrcode_img_content")
    val qrcodeImgContent: String? = null,
    @SerializedName("qr_code")
    val qrCode: String? = null,
    @SerializedName("qr_code_url")
    val qrCodeUrl: String? = null,
    @SerializedName("url")
    val url: String? = null,
    val errcode: Int? = null,
    val errmsg: String? = null
) {
    fun getQrcodeId(): String? = qrcode ?: qrCode
    fun getImageContent(): String? = qrcodeImgContent ?: qrCodeUrl ?: url
    fun hasError(): Boolean = errcode != null && errcode != 0
    fun getErrorMessage(): String = errmsg ?: "未知错误 (errcode=$errcode)"
}

data class QrCodeStatusResponse(
    val status: String,  // "wait", "scaned", "scaned_but_redirect", "confirmed", "expired"
    @SerializedName("bot_token")
    val botToken: String? = null,
    val baseurl: String? = null,
    @SerializedName("ilink_bot_id")
    val ilinkBotId: String? = null,
    @SerializedName("ilink_user_id")
    val ilinkUserId: String? = null,
    @SerializedName("redirect_host")
    val redirectHost: String? = null
)

// === Messages ===

data class GetUpdatesRequest(
    @SerializedName("get_updates_buf")
    val getUpdatesBuf: String? = null,
    @SerializedName("base_info")
    val baseInfo: BaseInfo = BaseInfo()
)

data class GetUpdatesResponse(
    val ret: Int = 0,
    @SerializedName("errcode")
    val errCode: Int? = null,
    val msgs: List<WeChatMessageItem>? = null,
    @SerializedName("get_updates_buf")
    val getUpdatesBuf: String? = null,
    @SerializedName("longpolling_timeout_ms")
    val longPollingTimeoutMs: Long? = null
)

data class BaseInfo(
    @SerializedName("channel_version")
    val channelVersion: String = CHANNEL_VERSION
)

data class WeChatMessageItem(
    @SerializedName("message_id")
    val messageId: String? = null,
    @SerializedName("context_token")
    val contextToken: String? = null,
    @SerializedName("from_user_id")
    val fromUserId: String? = null,
    @SerializedName("to_user_id")
    val toUserId: String? = null,
    @SerializedName("message_type")
    val messageType: Int = 0,
    @SerializedName("item_list")
    val itemList: List<MessageItemContent>? = null,
    @SerializedName("create_time_ms")
    val createTimeMs: Long? = null
)

data class MessageItemContent(
    val type: Int = 0,
    @SerializedName("text_item")
    val textItem: TextItem? = null,
    @SerializedName("image_item")
    val imageItem: MediaItem? = null,
    @SerializedName("voice_item")
    val voiceItem: MediaItem? = null,
    @SerializedName("file_item")
    val fileItem: FileItem? = null,
    @SerializedName("video_item")
    val videoItem: MediaItem? = null
)

data class TextItem(
    val text: String? = null
)

data class MediaItem(
    val media: MediaInfo? = null,
    @SerializedName("aes_key")
    val aesKey: String? = null,
    val text: String? = null  // For voice transcription
)

data class MediaInfo(
    @SerializedName("encrypt_query_param")
    val encryptQueryParam: String? = null,
    @SerializedName("full_url")
    val fullUrl: String? = null,
    @SerializedName("aes_key")
    val aesKey: String? = null,
    @SerializedName("encrypt_type")
    val encryptType: Int = 0
)

data class FileItem(
    val media: MediaInfo? = null,
    @SerializedName("file_name")
    val fileName: String? = null,
    val len: String? = null
)

// === Send Message ===

data class SendMessageRequest(
    val msg: SendMessageBody,
    @SerializedName("base_info")
    val baseInfo: BaseInfo = BaseInfo()
)

data class SendMessageBody(
    @SerializedName("from_user_id")
    val fromUserId: String = "",
    @SerializedName("to_user_id")
    val toUserId: String,
    @SerializedName("context_token")
    val contextToken: String,
    @SerializedName("message_type")
    val messageType: Int = MessageType.BOT,
    @SerializedName("message_state")
    val messageState: Int = MessageState.FINISH,
    @SerializedName("item_list")
    val itemList: List<MessageItemContent>,
    @SerializedName("client_id")
    val clientId: String = "kurisu-api:${System.currentTimeMillis()}-${(0..0xFFFFFFF).random().toString(16)}"
)

// === Config ===

data class GetConfigRequest(
    @SerializedName("ilink_user_id")
    val ilinkUserId: String,
    @SerializedName("context_token")
    val contextToken: String = "",
    @SerializedName("base_info")
    val baseInfo: BaseInfo = BaseInfo()
)

data class GetConfigResponse(
    val ret: Int = 0,
    @SerializedName("typing_ticket")
    val typingTicket: String? = null
)

// === Typing ===

data class SendTypingRequest(
    @SerializedName("typing_ticket")
    val typingTicket: String,
    val status: Int,  // 1=typing, 2=stop
    @SerializedName("ilink_user_id")
    val ilinkUserId: String,
    @SerializedName("base_info")
    val baseInfo: BaseInfo = BaseInfo()
)

// === Constants ===

object MessageType {
    const val NONE = 0
    const val USER = 1
    const val BOT = 2
}

object MessageItemType {
    const val NONE = 0
    const val TEXT = 1
    const val IMAGE = 2
    const val VOICE = 3
    const val FILE = 4
    const val VIDEO = 5
}

object MessageState {
    const val NEW = 0
    const val GENERATING = 1
    const val FINISH = 2
}

const val CHANNEL_VERSION = "2.1.7"
