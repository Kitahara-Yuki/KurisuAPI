package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ConversationFolderDao
import com.kurisuapi.data.entity.ConversationFolderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationFolderRepository @Inject constructor(
    private val folderDao: ConversationFolderDao
) {
    fun observeByCharacter(characterId: Long): Flow<List<ConversationFolderEntity>> =
        folderDao.observeByCharacter(characterId)

    suspend fun getById(id: Long): ConversationFolderEntity? =
        folderDao.getById(id)

    suspend fun insert(folder: ConversationFolderEntity): Long =
        folderDao.insert(folder)

    suspend fun update(folder: ConversationFolderEntity) =
        folderDao.update(folder)

    suspend fun deleteById(id: Long) =
        folderDao.deleteById(id)

    suspend fun deleteByCharacter(characterId: Long) =
        folderDao.deleteByCharacter(characterId)
}
