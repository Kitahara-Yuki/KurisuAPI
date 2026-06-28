package com.kurisuapi.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 使用 Android Keystore 加密的 SharedPreferences，专门存储敏感信息（如 API Key）。
 * 使用 EncryptedSharedPreferences + MasterKey 方案，密钥由系统 KeyStore 管理。
 * 当 KeyStore 失效时（OEM 系统升级、锁屏凭据变更等），自动删除损坏文件并重建。
 */
@Singleton
class EncryptedSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Bug fix: lazy initialization to avoid blocking DI thread with Keystore I/O
    private val masterKey: MasterKey by lazy { buildMasterKey() }

    // Bug fix: use a resettable backing field instead of `by lazy`, so retry-rebuild
    // in putString() can update the reference (by lazy only initializes once)
    @Volatile private var _prefs: SharedPreferences? = null

    private val prefs: SharedPreferences
        get() {
            _prefs?.let { return it }
            synchronized(this) {
                _prefs?.let { return it }
                val p = buildPrefs()
                _prefs = p
                return p
            }
        }

    private fun buildMasterKey(): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun buildPrefs(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // KeyStore 失效时删除损坏文件并重建空存储
            android.util.Log.w("EncryptedSettings", "加密存储读取失败，尝试删除损坏文件重建", e)
            context.deleteSharedPreferences(FILE_NAME)
            try {
                EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                android.util.Log.e("EncryptedSettings", "加密存储重建失败，可能是 Keystore 损坏而非文件损坏", e2)
                throw e2
            }
        }
    }

    companion object {
        private const val FILE_NAME = "kurisu_secure_prefs"
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return try {
            prefs.getString(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            // 单次解密失败时返回默认值，避免整个读取流程崩溃
            defaultValue
        }
    }

    fun putString(key: String, value: String) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            // Bug fix: 写入失败时删除损坏文件并重建 prefs，重置 _prefs 引用
            // 使后续 getString 使用新实例而不是已删除文件的旧引用
            try {
                context.deleteSharedPreferences(FILE_NAME)
                synchronized(this) {
                    _prefs = null
                }
                prefs.edit().putString(key, value).apply()
            } catch (e2: Exception) {
                // 两次都失败：记录错误，让调用方知道写入未生效
                android.util.Log.e("EncryptedSettings", "加密写入两次均失败，KeyStore 可能损坏: key=$key", e2)
            }
        }
    }

    fun remove(key: String) {
        try {
            prefs.edit().remove(key).apply()
        } catch (_: Exception) { }
    }
}
