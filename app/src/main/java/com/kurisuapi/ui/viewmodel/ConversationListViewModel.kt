package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.kurisuapi.data.entity.ConversationFolderEntity
import com.kurisuapi.data.entity.ConversationSessionEntity
import com.kurisuapi.data.repository.ConversationFolderRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val sessionRepository: ConversationSessionRepository,
    private val folderRepository: ConversationFolderRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: com.kurisuapi.data.repository.MemoryRepository,
    private val indexRepository: com.kurisuapi.data.repository.ConversationIndexRepository
) : ViewModel() {

    companion object {
        const val ARCHIVE_FOLDER_NAME = "已归档"
    }

    private val _characterId = MutableStateFlow(0L)
    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    private val _hideModeLabels = MutableStateFlow(false)

    val hideModeLabels: StateFlow<Boolean> = _hideModeLabels.asStateFlow()

    fun toggleHideModeLabels() {
        val newValue = !_hideModeLabels.value
        _hideModeLabels.value = newValue
        viewModelScope.launch {
            settingsRepository.setHideModeLabels(newValue)
        }
    }

    val sessions: StateFlow<List<ConversationSessionEntity>> = _characterId
        .flatMapLatest { id ->
            if (id > 0) sessionRepository.observeAllByCharacter(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders: StateFlow<List<ConversationFolderEntity>> = _characterId
        .flatMapLatest { id ->
            if (id > 0) folderRepository.observeByCharacter(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    // 追踪当前角色的"已归档"系统文件夹 ID
    private val _archiveFolderId = MutableStateFlow<Long?>(null)
    val archiveFolderId: StateFlow<Long?> = _archiveFolderId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredSessions: StateFlow<List<ConversationSessionEntity>> = combine(
        sessions,
        _selectedFolderId,
        _archiveFolderId
    ) { allSessions, folderId, archiveId ->
        when {
            // 选中了某个文件夹 → 展示该文件夹下的会话
            folderId != null -> allSessions.filter { it.folderId == folderId }
            // "全部"视图 → 排除已归档文件夹中的会话
            archiveId != null -> allSessions.filter { it.folderId != archiveId }
            // 归档文件夹尚未创建 → 全部展示（首次加载过渡状态）
            else -> allSessions
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 用户显式绑定的微信机器人目标会话 ID（来自设置）
    val botSessionId: StateFlow<Long?> = _characterId
        .flatMapLatest { id ->
            if (id > 0) {
                settingsRepository.observeValue(SettingsRepository.KEY_BOT_SESSION_PREFIX + id)
                    .map { it?.toLongOrNull() }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 实际的微信机器人目标：显式绑定优先，否则自动绑定列表第一个未归档会话
    @OptIn(ExperimentalCoroutinesApi::class)
    val effectiveBotSessionId: StateFlow<Long?> = combine(
        botSessionId,
        sessions
    ) { boundId, allSessions ->
        // 验证显式绑定的会话仍存在且未归档
        val bound = boundId?.let { id -> allSessions.firstOrNull { it.id == id && !it.isArchived } }
        bound?.id ?: allSessions.firstOrNull { !it.isArchived }?.id
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun loadCharacter(characterId: Long) {
        _characterId.value = characterId
        viewModelScope.launch {
            // 加载用户偏好：模式标签显示/隐藏
            _hideModeLabels.value = settingsRepository.isHideModeLabels()
            ensureArchiveFolder(characterId)
            // 对旧数据迁移：所有标题为"默认对话"的会话，自动使用第一条用户消息作为标题
            autoTitleLegacySessions(characterId)
            // 对旧数据迁移：文件夹系统引入前已归档的会话，移入"已归档"文件夹
            migrateLegacyArchivedSessions(characterId)
            // 清理过期（超过7天）的软删除会话
            purgeExpiredSessions()
            // 清理孤儿记忆：对应会话已不存在的记忆标记删除
            purgeOrphanedMemories(characterId)
        }
    }

    /** 清理孤儿记忆：sessionId 指向已删除/不存在会话的记忆 */
    private suspend fun purgeOrphanedMemories(characterId: Long) {
        try {
            val validIds = sessionRepository.getAllActiveIds(characterId)
            // Bug fix: 传入 characterId 防止跨角色记忆被误删
            memoryRepository.purgeOrphanedMemories(characterId, validIds)
        } catch (_: Exception) { }
    }

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun createSession(chatMode: String = ConversationSessionEntity.CHAT_MODE_CHAT, onCreated: (Long) -> Unit) {
        val characterId = _characterId.value
        if (characterId <= 0) {
            Log.w("ConversationListVM", "createSession: characterId=$characterId 无效，请先调用 loadCharacter()")
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = ConversationSessionEntity(
                characterId = characterId,
                title = "新对话",
                chatMode = chatMode,
                createdAt = now,
                updatedAt = now
            )
            val sessionId = sessionRepository.insert(session)
            onCreated(sessionId)
        }
    }

    fun createFolder(name: String) {
        val characterId = _characterId.value
        if (characterId <= 0 || name.isBlank()) return

        viewModelScope.launch {
            folderRepository.insert(
                ConversationFolderEntity(
                    characterId = characterId,
                    name = name.trim()
                )
            )
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            sessionRepository.updateTitle(sessionId, newTitle.trim())
        }
    }

    /** 归档会话：标记为已归档 + 移入"已归档"文件夹 + 自动解绑微信 */
    fun archiveSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.archive(sessionId)
            // 移入"已归档"文件夹（若尚未创建则先确保存在）
            val characterId = _characterId.value
            var archiveId = _archiveFolderId.value
            if (archiveId == null) {
                ensureArchiveFolder(characterId)
                archiveId = _archiveFolderId.value
            }
            if (archiveId != null) {
                sessionRepository.updateFolder(sessionId, archiveId)
            }
            // 如果该会话是微信绑定目标，取消绑定
            val boundId = settingsRepository.getBotSessionId(characterId)
            if (boundId == sessionId) {
                settingsRepository.setBotSessionId(characterId, null)
            }
        }
    }

    /** 设置/取消当前角色下微信机器人的目标会话 */
    fun setBotSession(sessionId: Long?) {
        val characterId = _characterId.value
        if (characterId <= 0) return
        viewModelScope.launch {
            settingsRepository.setBotSessionId(characterId, sessionId)
        }
    }

    fun moveSession(sessionId: Long, folderId: Long?) {
        viewModelScope.launch {
            sessionRepository.updateFolder(sessionId, folderId)
        }
    }

    /** 软删除：标记删除并移入回收站，7天后自动清理（同时删除该对话的记忆） */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.softDelete(sessionId)
            memoryRepository.softDeleteBySession(sessionId)
        }
    }

    /** 从回收站还原会话（同时还原该对话的记忆） */
    fun restoreSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.restore(sessionId)
            memoryRepository.restoreBySession(sessionId)
        }
    }

    /** 获取指定会话的已删除记忆列表 */
    suspend fun getDeletedMemories(sessionId: Long): List<com.kurisuapi.data.entity.MemoryEntity> =
        memoryRepository.getDeletedBySession(sessionId)

    /** 永久删除指定会话及其所有关联数据（聊天记录、索引、记忆） */
    fun permanentlyDeleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteById(sessionId)
        }
    }

    /** 永久删除过期（超过7天）的软删除会话及其聊天记录、记忆 */
    fun purgeExpiredSessions() {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            // 先删除过期会话的聊天记录
            val expiredSessions = sessionRepository.observeDeletedByCharacter(_characterId.value)
                .firstOrNull() ?: emptyList()
            for (session in expiredSessions) {
                if (session.deletedAt > 0 && session.deletedAt < cutoff) {
                    chatHistoryRepository.deleteBySession(session.id)
                    indexRepository.deleteBySession(session.id)
                }
            }
            // 再删除过期会话和过期记忆
            sessionRepository.deleteExpired(cutoff)
            memoryRepository.deleteExpired(cutoff)
        }
    }

    /** 已删除的会话列表（回收站） */
    val deletedSessions: StateFlow<List<ConversationSessionEntity>> = _characterId
        .flatMapLatest { id ->
            if (id > 0) sessionRepository.observeDeletedByCharacter(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun renameFolder(folderId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val folder = folderRepository.getById(folderId) ?: return@launch
            if (folder.isSystem) return@launch  // 系统文件夹不可重命名
            folderRepository.update(folder.copy(name = newName.trim()))
        }
    }

    /** 删除文件夹（系统文件夹不可删除） */
    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            val folder = folderRepository.getById(folderId) ?: return@launch
            if (folder.isSystem) return@launch  // 系统文件夹不可删除
            // 将该文件夹下的所有会话移出
            val sessionsInFolder = sessionRepository.getByFolder(folderId)
                .firstOrNull() ?: emptyList()
            for (session in sessionsInFolder) {
                sessionRepository.updateFolder(session.id, null)
            }
            folderRepository.deleteById(folderId)
            // 如果删除的是当前选中的文件夹，重置为"全部"视图
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
    }

    // ===== 私有方法 =====

    /**
     * 对旧版本迁移过来的"默认对话"会话，自动使用第一条用户消息作为标题。
     * 仅处理标题仍为"默认对话"的会话，且只在有聊天记录时才更新。
     */
    /**
     * 将文件夹系统引入前就已归档的旧会话（isArchived=true 但 folderId=null 或未指向归档文件夹）
     * 移入"已归档"文件夹。
     */
    private suspend fun migrateLegacyArchivedSessions(characterId: Long) {
        val archiveId = _archiveFolderId.value ?: return
        val allSessions = sessionRepository.observeAllByCharacter(characterId).firstOrNull() ?: return
        for (session in allSessions) {
            if (session.isArchived && session.folderId != archiveId) {
                sessionRepository.updateFolder(session.id, archiveId)
            }
        }
    }

    private suspend fun autoTitleLegacySessions(characterId: Long) {
        val allSessions = sessionRepository.observeAllByCharacter(characterId).firstOrNull() ?: return
        for (session in allSessions) {
            if (session.title != "默认对话") continue
            // 获取该会话的第一条用户消息作为标题
            val firstUserMsg = chatHistoryRepository.getFirstUserMessageOfSession(session.id) ?: continue
            val newTitle = firstUserMsg.content.take(30)
            if (newTitle.isNotBlank()) {
                sessionRepository.updateTitle(session.id, newTitle)
            }
        }
    }

    /**
     * 确保当前角色存在"已归档"系统文件夹。若不存在则自动创建。
     * 在 loadCharacter 时调用，也在 folders 流更新时同步。
     */
    private suspend fun ensureArchiveFolder(characterId: Long) {
        // 从当前 folders 快照中查找已存在的归档文件夹
        val existing = folderRepository.observeByCharacter(characterId)
            .firstOrNull()
            ?.firstOrNull { it.isSystem && it.name == ARCHIVE_FOLDER_NAME }

        if (existing != null) {
            _archiveFolderId.value = existing.id
            return
        }

        // 不存在则创建
        val newId = folderRepository.insert(
            ConversationFolderEntity(
                characterId = characterId,
                name = ARCHIVE_FOLDER_NAME,
                isSystem = true
            )
        )
        _archiveFolderId.value = newId
    }
}
