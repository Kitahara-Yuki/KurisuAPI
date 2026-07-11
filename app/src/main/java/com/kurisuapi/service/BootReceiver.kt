package com.kurisuapi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kurisuapi.data.wechat.WeChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备开机后自动启动微信机器人服务（如果已登录）。
 * Android 8+ 系统在 onReceive 返回后会对后台服务启动施加限制，
 * 因此使用 goAsync + 短暂延迟来确保服务成功启动。
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject lateinit var weChatRepository: WeChatRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "收到开机广播")

        // Android 8+ 需要用 goAsync 延长生命周期
        val pending = goAsync()

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                // 等待系统初始化完成，避免过早启动被系统拦截
                delay(5000L)

                if (weChatRepository.isLoggedIn) {
                    Log.i(TAG, "已登录，自动启动微信服务")
                    // 短暂等待确保 Hilt 完全初始化
                    delay(2000L)
                    WeChatBotService.start(context)
                } else {
                    Log.d(TAG, "未登录，跳过自启动")
                }
            } catch (e: Exception) {
                Log.e(TAG, "自启动失败: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
