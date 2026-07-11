package com.kurisuapi.data.repository

import androidx.room.withTransaction
import com.kurisuapi.data.dao.*
import com.kurisuapi.data.database.KurisuDatabase
import com.kurisuapi.data.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    private val database: KurisuDatabase,
    private val characterDao: CharacterDao,
    private val chatHistoryDao: ChatHistoryDao,
    private val memoryDao: MemoryDao,
    private val emotionStateDao: EmotionStateDao,
    private val relationshipDao: RelationshipDao,
    private val userProfileDao: UserProfileDao,
    private val sessionDao: ConversationSessionDao,
    private val folderDao: ConversationFolderDao,
    private val indexDao: ConversationIndexDao,
    private val proactiveLogDao: ProactiveLogDao,
    private val diaryEntryDao: DiaryEntryDao,
    private val settingsRepository: SettingsRepository
) {
    fun getAll(): Flow<List<CharacterEntity>> = characterDao.getAll()

    suspend fun getAllOnce(): List<CharacterEntity> = characterDao.getAllOnce()

    suspend fun getById(id: Long): CharacterEntity? = characterDao.getById(id)

    fun observeById(id: Long): Flow<CharacterEntity?> = characterDao.observeById(id)

    suspend fun insert(character: CharacterEntity): Long = characterDao.insert(character)

    suspend fun update(character: CharacterEntity) = characterDao.update(character)

    /** 直接删除角色（不清理关联数据，建议使用 deleteWithCascade） */
    @Deprecated("Use deleteWithCascade to avoid orphan data", ReplaceWith("deleteWithCascade(character)"))
    suspend fun delete(character: CharacterEntity) = characterDao.delete(character)

    @Deprecated("Use deleteWithCascade to avoid orphan data", ReplaceWith("deleteWithCascadeById(id)"))
    suspend fun deleteById(id: Long) = characterDao.deleteById(id)

    /** 级联删除角色及其全部关联数据（10 张子表） */
    suspend fun deleteWithCascade(character: CharacterEntity) {
        deleteWithCascadeById(character.id)
    }

    /** 级联删除角色（按 ID）及其全部关联数据，包裹在事务中确保原子性 */
    suspend fun deleteWithCascadeById(id: Long) {
        database.withTransaction {
            // 清理顺序：先子后父，防止外键依赖问题
            chatHistoryDao.deleteByCharacter(id)       // 聊天记录
            memoryDao.deleteByCharacterPermanent(id)   // 记忆（物理删除）
            proactiveLogDao.deleteByCharacter(id)      // 主动消息日志
            diaryEntryDao.deleteByCharacter(id)        // 日记
            indexDao.deleteByCharacter(id)             // 对话索引
            sessionDao.deleteByCharacter(id)           // 会话
            folderDao.deleteByCharacter(id)            // 文件夹
            emotionStateDao.deleteByCharacter(id)      // 情绪状态
            relationshipDao.deleteByCharacter(id)      // 关系数据
            userProfileDao.deleteByCharacter(id)       // 用户画像

            // 如果删除的是当前活跃角色，清除活跃设置
            val activeId = settingsRepository.getActiveCharacterId()
            if (activeId == id) {
                settingsRepository.setValue(SettingsRepository.KEY_ACTIVE_CHARACTER, "")
            }

            // 最后删除角色本身
            characterDao.deleteById(id)
        }
    }
}
