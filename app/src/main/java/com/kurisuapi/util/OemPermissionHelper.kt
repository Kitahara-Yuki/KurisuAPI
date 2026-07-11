package com.kurisuapi.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

/**
 * 国产手机厂商后台白名单跳转工具。
 * 跳过华为（用户要求不适配），其余品牌按官方 Activity 跳转，失败时降级到系统电池优化页。
 */
object OemPermissionHelper {

    private const val TAG = "OemPermHelper"

    // ── 品牌判断 ──

    private fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val b = Build.BRAND.lowercase(Locale.ROOT)
        return m.contains("xiaomi") || m.contains("redmi") || b.contains("xiaomi") || b.contains("redmi")
    }

    private fun isOppo(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val b = Build.BRAND.lowercase(Locale.ROOT)
        return m.contains("oppo") || m.contains("realme") || b.contains("oppo") || b.contains("realme") ||
                m.contains("oneplus") || b.contains("oneplus")
    }

    private fun isVivo(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val b = Build.BRAND.lowercase(Locale.ROOT)
        return m.contains("vivo") || m.contains("iqoo") || b.contains("vivo") || b.contains("iqoo")
    }

    private fun isSamsung(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return m.contains("samsung") || m.contains("samsng")
    }

    private fun isMeizu(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return m.contains("meizu")
    }

    // ── 电池优化白名单 ──

    /** 是否已加入电池优化白名单 */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 跳转到系统电池优化设置页（标准 Android） */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "无法打开电池优化设置", e)
            false
        }
    }

    // ── 厂商自启动管理 ──

    /**
     * 跳转到厂商自启动管理页面。
     * 成功返回 true，失败返回 false（调用方应降级到 openBatteryOptimizationSettings）。
     */
    fun openAutoStartSettings(context: Context): Boolean {
        return try {
            val intent = when {
                isXiaomi() -> {
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                }
                isOppo() -> {
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                }
                isVivo() -> {
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                }
                isSamsung() -> {
                    // 三星：跳电池优化页
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
                isMeizu() -> {
                    Intent().setComponent(
                        ComponentName(
                            "com.meizu.safe",
                            "com.meizu.safe.security.AppPermissionActivity"
                        )
                    )
                }
                else -> {
                    // 其他品牌：降级到电池优化
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "已跳转自启动管理: ${Build.MANUFACTURER} ${Build.BRAND}")
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "自启动管理页面不存在，降级到电池优化", e)
            openBatteryOptimizationSettings(context)
        } catch (e: SecurityException) {
            Log.w(TAG, "无权限跳转自启动管理", e)
            false
        }
    }

    /** 跳转到应用详情页（通用降级方案） */
    fun openAppDetailSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** 获取当前手机品牌的可读名称 */
    fun getBrandName(): String = when {
        isXiaomi() -> "小米/红米"
        isOppo() -> "OPPO/一加"
        isVivo() -> "vivo"
        isSamsung() -> "三星"
        isMeizu() -> "魅族"
        else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    }
}
