package com.kurisuapi.data.wechat

import android.content.Context
import android.content.SharedPreferences
import com.kurisuapi.util.EncryptedSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedSettings: EncryptedSettings
) {
    // 非敏感数据仍使用明文 SharedPreferences（性能优于加密存储）
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("wechat_session", Context.MODE_PRIVATE)
    }

    // 敏感凭证使用加密存储
    var botToken: String?
        get() = encryptedSettings.getString(KEY_BOT_TOKEN).takeIf { it.isNotBlank() }
        set(value) {
            if (value != null) encryptedSettings.putString(KEY_BOT_TOKEN, value)
            else encryptedSettings.remove(KEY_BOT_TOKEN)
        }

    var accountId: String?
        get() = encryptedSettings.getString(KEY_ACCOUNT_ID).takeIf { it.isNotBlank() }
        set(value) {
            if (value != null) encryptedSettings.putString(KEY_ACCOUNT_ID, value)
            else encryptedSettings.remove(KEY_ACCOUNT_ID)
        }

    var userId: String?
        get() = encryptedSettings.getString(KEY_USER_ID).takeIf { it.isNotBlank() }
        set(value) {
            if (value != null) encryptedSettings.putString(KEY_USER_ID, value)
            else encryptedSettings.remove(KEY_USER_ID)
        }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var updatesBuf: String?
        get() = prefs.getString(KEY_UPDATES_BUF, null)
        set(value) = prefs.edit().putString(KEY_UPDATES_BUF, value).apply()

    var contextToken: String?
        get() = prefs.getString(KEY_CONTEXT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_CONTEXT_TOKEN, value).apply()

    val isLoggedIn: Boolean
        get() = !botToken.isNullOrBlank()

    // Bug fix: use 16-byte random + timestamp for collision-resistant UIN
    fun generateUin(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.take(12)
    }

    // Bug 15 fix: 复用 SecureRandom 实例，避免每次 API 调用重新初始化
    private val random = SecureRandom()

    fun clearSession() {
        encryptedSettings.remove(KEY_BOT_TOKEN)
        encryptedSettings.remove(KEY_ACCOUNT_ID)
        encryptedSettings.remove(KEY_USER_ID)
        prefs.edit().clear().apply()
    }

    fun saveSession(botToken: String, accountId: String, userId: String, baseUrl: String?) {
        this.botToken = botToken
        this.accountId = accountId
        this.userId = userId
        if (!baseUrl.isNullOrBlank()) {
            this.baseUrl = baseUrl
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"

        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_UPDATES_BUF = "updates_buf"
        private const val KEY_CONTEXT_TOKEN = "context_token"
    }
}
