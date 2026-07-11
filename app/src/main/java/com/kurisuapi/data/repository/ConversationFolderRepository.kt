package com.kurisuapi.data.repository

import androidx.room.withTransaction
import com.kurisuapi.data.dao.ConversationFolderDao
import com.kurisuapi.data.dao.ConversationSessionDao
import com.kurisuapi.data.entity.ConversationFolderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationFolderRepository @Inject constructor(
    private val db: com.kurisuapi.data.database.KurisuDatabase,
    private val folderDao: ConversationFolderDao,
    private val sessionDao: ConversationSessionDao
) {
    fun observeByCharacter(characterId: Long): Flow<List<ConversationFolderEntity>> =
        folderDao.observeByCharacter(characterId)

    suspend fun getById(id: Long): ConversationFolderEntity? =
        folderDao.getById(id)

    suspend fun insert(folder: ConversationFolderEntity): Long =
        folderDao.insert(folder)

    suspend fun update(folder: ConversationFolderEntity) =
        folderDao.update(folder)

    /** 删除文件夹，同步清除会话引用（事务保护） */
    suspend fun deleteById(id: Long) = db.withTransaction {
        sessionDao.clearFolderId(id)
        folderDao.deleteById(id)
    }

    suspend fun deleteByCharacter(characterId: Long) =
        folderDao.deleteByCharacter(characterId)
}
