package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ThemeDao
import com.kurisuapi.data.entity.ThemeEntity
import com.kurisuapi.ui.theme.*
import com.kurisuapi.util.toHex
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeRepository @Inject constructor(
    private val themeDao: ThemeDao
) {
    companion object {
        // 7 个内置预设
        private val BUILT_IN_THEMES = listOf(
            ThemeEntity(name = "翡翠绿", seedColorHex = AppleGreen.toHex(), isBuiltIn = true, createdAt = 0),
            ThemeEntity(name = "日落橙", seedColorHex = SunsetOrange.toHex(), isBuiltIn = true, createdAt = 1),
            ThemeEntity(name = "玫瑰红", seedColorHex = AppleRed.toHex(), isBuiltIn = true, createdAt = 2),
            ThemeEntity(name = "樱花粉", seedColorHex = SakuraPink.toHex(), isBuiltIn = true, createdAt = 3),
            ThemeEntity(name = "海洋青", seedColorHex = AppleTeal.toHex(), isBuiltIn = true, createdAt = 4),
            ThemeEntity(name = "星云紫", seedColorHex = AppleIndigo.toHex(), isBuiltIn = true, createdAt = 5),
        )
    }

    /** 首次启动时预填充内置主题，激活第一个 */
    suspend fun initializeIfNeeded() {
        if (themeDao.count() == 0) {
            BUILT_IN_THEMES.forEach { themeDao.insert(it) }
            themeDao.setActive(1)
        }
    }

    /** 获取当前活跃主题，没有则返回 null */
    fun observeActive(): Flow<ThemeEntity?> = themeDao.observeActive()

    /** 获取当前活跃主题（同步） */
    suspend fun getActive(): ThemeEntity? = themeDao.getActive()

    /** 获取所有主题（内置 + 自定义） */
    fun observeAll(): Flow<List<ThemeEntity>> = themeDao.observeAll()

    /** 应用主题（原子操作） */
    suspend fun applyTheme(id: Long) = themeDao.applyTheme(id)

    /** 清除活跃主题（回退到系统默认） */
    suspend fun clearActive() {
        themeDao.clearActive()
    }

    /** 保存新主题并立即应用（原子操作） */
    suspend fun createAndApply(theme: ThemeEntity): Long = themeDao.createAndApply(theme)

    /** 更新主题 */
    suspend fun update(theme: ThemeEntity) = themeDao.update(theme)

    /** 删除用户自建主题 */
    suspend fun delete(id: Long) = themeDao.deleteUserTheme(id)
}
