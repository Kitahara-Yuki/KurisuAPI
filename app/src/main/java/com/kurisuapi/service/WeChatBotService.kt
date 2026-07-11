package com.kurisuapi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kurisuapi.R
import com.kurisuapi.data.wechat.WeChatRepository
import com.kurisuapi.domain.bridge.ConnectionState
import com.kurisuapi.domain.bridge.WeChatBridge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

@AndroidEntryPoint
class WeChatBotService : Service() {

    companion object {
        private const val TAG = "WeChatBotService"
        private const val CHANNEL_ID = "wechat_bot_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.kurisuapi.STOP_BOT_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, WeChatBotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WeChatBotService::class.java))
        }
    }

    @Inject lateinit var weChatBridge: WeChatBridge
    @Inject lateinit var weChatRepository: WeChatRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var stateObserverJob: Job? = null
    private var receiverRegistered: Boolean = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted: Boolean = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                Log.i(TAG, "收到停止广播")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 获取 WakeLock 防止息屏时 CPU 休眠导致轮询中断
        acquireWakeLock()

        // 只首次调用 startForeground，后续重复调用 onStartCommand 不再覆盖通知
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification("微信机器人启动中...", "红莉栖API"))
            foregroundStarted = true
        }

        // Bug 10 fix: 在 onCreate 中注册一次，onStartCommand 被多次调用时避免重复注册
        if (!receiverRegistered) {
            val filter = IntentFilter(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(stopReceiver, filter)
            }
            receiverRegistered = true
        }

        // 检查登录状态并连接（防重：只启动一次连接观察）
        if (stateObserverJob?.isActive != true) {
            stateObserverJob = serviceScope.launch {
                if (weChatRepository.isLoggedIn) {
                    weChatBridge.connect()
                    observeConnectionState()
                } else {
                    Log.w(TAG, "未登录，停止服务")
                    stopSelf()
                }
            }
        } else {
            // 观察者已运行，但通知可能被重置：立即同步当前连接状态到通知
            syncNotificationToCurrentState()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        foregroundStarted = false
        // 释放 WakeLock
        releaseWakeLock()
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
        receiverRegistered = false
        // Bug 3 fix: 用 runBlocking + withTimeout 确保 disconnect 在进程销毁前执行完毕，
        // 不再用 launch 异步方式。disconnect 只是取消协程和更新状态，不涉及 IO，执行极快。
        // timeout 防止极端异常情况下阻塞主线程过久。
        kotlinx.coroutines.runBlocking {
            try {
                kotlinx.coroutines.withTimeout(3000) {
                    weChatBridge.disconnect()
                }
            } catch (_: Exception) {}
        }
        serviceScope.cancel()
        Log.i(TAG, "服务已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun observeConnectionState() {
        weChatBridge.connectionState.collectLatest { state ->
            when (state) {
                ConnectionState.POLLING -> {
                    updateNotification("微信机器人运行中", "正在监听消息...")
                }
                ConnectionState.CONNECTING -> {
                    updateNotification("微信机器人", "正在连接...")
                }
                ConnectionState.DISCONNECTED -> {
                    // 如果不是用户主动断开（token 过期等），停止服务
                    if (!weChatRepository.isLoggedIn) {
                        Log.i(TAG, "登录已失效，停止服务")
                        updateNotification("微信机器人", "登录已过期，请重新扫码")
                        delay(2000)
                        stopSelf()
                    }
                }
                ConnectionState.ERROR -> {
                    updateNotification("微信机器人", "连接异常")
                }
                else -> {}
            }
        }
    }

    /** 当服务已在前台运行时，将通知文本同步到当前连接状态，防止通知停留在"启动中" */
    private fun syncNotificationToCurrentState() {
        val state = weChatBridge.connectionState.value
        when (state) {
            ConnectionState.POLLING -> updateNotification("微信机器人运行中", "正在监听消息...")
            ConnectionState.CONNECTING -> updateNotification("微信机器人", "正在连接...")
            ConnectionState.DISCONNECTED -> {
                if (!weChatRepository.isLoggedIn) {
                    updateNotification("微信机器人", "登录已过期，请重新扫码")
                }
            }
            ConnectionState.ERROR -> updateNotification("微信机器人", "连接异常")
            else -> {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "微信机器人",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "微信机器人后台运行状态"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String, title: String = "微信机器人"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content, title))
    }

    // ── WakeLock 管理 ──

    /** 获取 PARTIAL_WAKE_LOCK，确保息屏时 CPU 不休眠 */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KurisuAPI:WeChatBotWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 最长持锁 10 分钟，超时自动释放
            }
            Log.d(TAG, "WakeLock 已获取")
        } catch (e: Exception) {
            Log.w(TAG, "获取 WakeLock 失败: ${e.message}")
        }
    }

    /** 释放 WakeLock */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock 已释放")
            }
        } catch (e: Exception) {
            Log.w(TAG, "释放 WakeLock 失败: ${e.message}")
        } finally {
            wakeLock = null
        }
    }
}
