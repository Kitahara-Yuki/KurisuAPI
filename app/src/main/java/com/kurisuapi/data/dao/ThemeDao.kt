package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observeAll(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ThemeEntity?

    @Query("SELECT * FROM themes WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ThemeEntity?>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun getById(id: Long): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(theme: ThemeEntity): Long

    @Update
    suspend fun update(theme: ThemeEntity)

    @Query("DELETE FROM themes WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteUserThemeRaw(id: Long)

    /** 删除自定义主题，若删除的是活跃主题则自动回退到系统默认主题 */
    @Transaction
    suspend fun deleteUserTheme(id: Long) {
        val wasActive = getById(id)?.isActive == true
        deleteUserThemeRaw(id)
        if (wasActive) clearActive()  // 清空活跃状态 → UI 层自动使用系统默认色板
    }

    @Query("UPDATE themes SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE themes SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("SELECT COUNT(*) FROM themes")
    suspend fun count(): Int

    /** 原子操作：清除所有活跃状态 → 激活指定主题 */
    @Transaction
    suspend fun applyTheme(id: Long) {
        clearActive()
        setActive(id)
    }

    /** 原子操作：插入新主题 → 清除所有活跃 → 激活新主题 */
    @Transaction
    suspend fun createAndApply(theme: ThemeEntity): Long {
        val id = insert(theme.copy(isActive = false))
        clearActive()
        setActive(id)
        return id
    }
}
