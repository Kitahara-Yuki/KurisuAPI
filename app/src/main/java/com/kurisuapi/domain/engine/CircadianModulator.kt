package com.kurisuapi.domain.engine

import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 昼夜节律调节器 — 根据当前时段返回情绪基线偏移和能量系数。
 *
 * 来源：CHI 2026 CTEM 论文 + MATE 情感架构 — 不往提示词塞时间，
 * 而是让角色的情绪基线和行为节奏随真实时段自然变化。
 */
@Singleton
class CircadianModulator @Inject constructor() {

    data class CircadianShift(
        val happyShift: Int,       // 加到 happy 基线（53 → 更开心，47 → 更慵懒）
        val lonelyShift: Int,      // 加到 lonely 基线（22 → 更粘人，18 → 更独立）
        val energyLevel: Float,    // 回复速度系数：1.0 正常，0.7 慢，1.1 快
        val timeLabel: String      // "清晨" "上午" "中午" "下午" "傍晚" "深夜"
    )

    /**
     * @param hour 当前小时（0-23），通常传入 [LocalTime.now().hour]
     * @param enabled 用户是否开启昼夜节律，关闭时返回全中性值
     */
    fun getShift(hour: Int, enabled: Boolean = true): CircadianShift {
        if (!enabled) return CircadianShift(happyShift = 0, lonelyShift = 0, energyLevel = 1.0f, timeLabel = "")
        return when (hour) {
            in 5..7   -> CircadianShift(happyShift = -3, lonelyShift = 3,  energyLevel = 0.8f, timeLabel = "清晨")
            in 8..11  -> CircadianShift(happyShift =  0, lonelyShift = -2, energyLevel = 1.0f, timeLabel = "上午")
            in 12..14 -> CircadianShift(happyShift =  2, lonelyShift = -3, energyLevel = 1.0f, timeLabel = "中午")
            in 15..17 -> CircadianShift(happyShift =  3, lonelyShift = -2, energyLevel = 1.1f, timeLabel = "下午")
            in 18..21 -> CircadianShift(happyShift =  2, lonelyShift = -5, energyLevel = 0.9f, timeLabel = "傍晚")
            else      -> CircadianShift(happyShift = -3, lonelyShift = 5,  energyLevel = 0.7f, timeLabel = "深夜")
        }
    }
}
