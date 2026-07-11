package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "relationships",
    indices = [Index(value = ["characterId"], unique = true)]
)
data class RelationshipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    // ── 三轴模型 (Sternberg) ──
    val intimacy: Int = 0,       // 亲密 0-100
    val trust: Int = 0,          // 信任 0-100
    val attraction: Int = 0,     // 吸引 0-100
    // ── Knapp 五阶段 ──
    val stage: String = "初识",  // 初识/探索/深入/融合/羁绊
    // ── 向后兼容 ──
    val level: String = "初识",  // 与 stage 同步
    val score: Int = 0,          // 三轴均分
    // ── 内部追踪 ──
    val rawComposite: Int = 0,           // 三轴原始总分
    val lastInteractionTime: Long = 0,   // 上次互动时间戳
    val dailyGains: String = "",         // JSON: 每日增益追踪
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Knapp 关系发展阶段 (0-4) */
        val STAGES = listOf("初识", "探索", "深入", "融合", "羁绊")

        /** 各阶段升级所需的最低加权综合分 */
        val STAGE_THRESHOLDS = mapOf(
            "初识" to 0,
            "探索" to 40,
            "深入" to 100,
            "融合" to 180,
            "羁绊" to 260
        )

        /** 根据综合分判定阶段 */
        fun stageForComposite(composite: Int): String {
            return when {
                composite >= (STAGE_THRESHOLDS["羁绊"] ?: 260) -> "羁绊"
                composite >= (STAGE_THRESHOLDS["融合"] ?: 180) -> "融合"
                composite >= (STAGE_THRESHOLDS["深入"] ?: 100) -> "深入"
                composite >= (STAGE_THRESHOLDS["探索"] ?: 40) -> "探索"
                else -> "初识"
            }
        }
    }
}
