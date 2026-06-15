package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.MemoryEntity
import com.kurisuapi.data.entity.UserProfileEntity
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.MemoryRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.data.repository.UserProfileRepository
import com.kurisuapi.domain.engine.MemoryExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val memoryExtractor: MemoryExtractor,
    private val sessionRepository: com.kurisuapi.data.repository.ConversationSessionRepository
) : ViewModel() {

    private val _characterId = MutableStateFlow(0L)

    val memories: StateFlow<List<MemoryEntity>> = _characterId
        .flatMapLatest { id -> if (id > 0) memoryRepository.getByCharacter(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = _characterId
        .flatMapLatest { id -> if (id > 0) userProfileRepository.getByCharacter(id) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefining = MutableStateFlow(false)
    val isRefining: StateFlow<Boolean> = _isRefining.asStateFlow()

    private val _sessionTitles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sessionTitles: StateFlow<Map<Long, String>> = _sessionTitles.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshProgress = MutableStateFlow("")
    val refreshProgress: StateFlow<String> = _refreshProgress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setCharacterId(id: Long) {
        _characterId.value = id
        // 旧数据兼容：首次打开记忆页面时自动规范化旧记忆（仅一次）
        if (id > 0) {
            viewModelScope.launch {
                try {
                    if (!settingsRepository.isMemoryNormalized(id)) {
                        val count = memoryExtractor.reprocessAllMemories(id)
                        if (count > 0) {
                            _message.value = "已自动规范化 $count 条旧记忆"
                        }
                        settingsRepository.setMemoryNormalized(id)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun addMemory(content: String, importance: Int) {
        val characterId = _characterId.value
        if (characterId <= 0) return
        viewModelScope.launch {
            memoryRepository.insert(
                MemoryEntity(
                    characterId = characterId,
                    content = content,
                    importance = importance,
                    source = "manual"
                )
            )
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepository.update(memory.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepository.delete(memory)
        }
    }

    /** 手动触发记忆提炼 */
    fun refineMemory() {
        val id = _characterId.value
        if (id <= 0 || _isRefining.value) return
        viewModelScope.launch {
            _isRefining.value = true
            _message.value = null
            try {
                val ok = memoryExtractor.extractAndStore(id)
                if (ok) {
                    // 更新提取进度，避免随后自动提取重复处理同一批消息
                    val total = chatHistoryRepository.countByCharacter(id)
                    settingsRepository.setLastExtractCount(id, total)
                    _message.value = "记忆已更新"
                } else {
                    _message.value = "暂无可提炼的内容"
                }
            } catch (e: Exception) {
                _message.value = "提炼失败：${e.localizedMessage ?: "未知错误"}"
            } finally {
                _isRefining.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 加载当前角色所有对话的标题映射 */
    fun loadSessionTitles() {
        val characterId = _characterId.value
        if (characterId <= 0) return
        viewModelScope.launch {
            val sessions = sessionRepository.getAllOnce(characterId)
            _sessionTitles.value = sessions.associate { it.id to (it.title.ifBlank { "新对话" }) }
        }
    }

    /** 为旧记忆分配会话归属 */
    fun assignToSession(memoryId: Long, sessionId: Long) {
        viewModelScope.launch {
            memoryRepository.assignToSession(memoryId, sessionId)
            loadSessionTitles()
        }
    }

    /** 批量分配记忆到会话 */
    fun batchAssignToSession(memoryIds: List<Long>, sessionId: Long) {
        viewModelScope.launch {
            memoryRepository.batchAssignToSession(memoryIds, sessionId)
            loadSessionTitles()
        }
    }

    /** 批量删除记忆 */
    fun deleteMemories(ids: List<Long>) {
        viewModelScope.launch {
            memoryRepository.deleteByIds(ids)
        }
    }

    /** 全量刷新：对所有旧记忆重新规范化（LLM 优化内容 + 补充重要度） */
    fun refreshAllMemories() {
        val id = _characterId.value
        if (id <= 0 || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshProgress.value = ""
            _message.value = null
            try {
                val count = memoryExtractor.reprocessAllMemories(id) { done, total ->
                    _refreshProgress.value = "正在刷新 $done/$total 条记忆..."
                }
                _message.value = when {
                    count < 0 -> "刷新失败，请稍后重试"
                    count == 0 -> "没有可刷新的记忆"
                    else -> "已刷新 $count 条记忆"
                }
            } catch (e: Exception) {
                _message.value = "刷新失败：${e.localizedMessage ?: "未知错误"}"
            } finally {
                _isRefreshing.value = false
                _refreshProgress.value = ""
                // 刷新后重新加载会话标题，确保记忆能显示所属对话
                loadSessionTitles()
            }
        }
    }
}
