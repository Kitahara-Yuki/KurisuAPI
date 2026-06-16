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

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _optimizeProgress = MutableStateFlow("")
    val optimizeProgress: StateFlow<String> = _optimizeProgress.asStateFlow()

    private val _sessionTitles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sessionTitles: StateFlow<Map<Long, String>> = _sessionTitles.asStateFlow()

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
    fun optimizeMemories() {
        val id = _characterId.value
        if (id <= 0 || _isOptimizing.value) return
        viewModelScope.launch {
            _isOptimizing.value = true
            _optimizeProgress.value = "正在提取新记忆..."
            _message.value = null
            try {
                // 第一步：从最近聊天中提取新记忆
                val extracted = memoryExtractor.extractAndStore(id)
                if (extracted) {
                    val total = chatHistoryRepository.countByCharacter(id)
                    settingsRepository.setLastExtractCount(id, total)
                }
                // 第二步：优化已有记忆
                _optimizeProgress.value = "正在优化已有记忆..."
                val count = memoryExtractor.reprocessAllMemories(id) { done, total ->
                    _optimizeProgress.value = "正在优化记忆 $done/$total..."
                }
                // 汇总结果
                val parts = mutableListOf<String>()
                if (extracted) parts.add("新记忆已提取")
                _message.value = when {
                    count > 0 -> (parts + "已优化 $count 条记忆").joinToString("，")
                    extracted -> "新记忆已提取，无需优化"
                    else -> "暂无可提取的内容"
                }
                loadSessionTitles()
            } catch (e: Exception) {
                _message.value = "优化失败：${e.localizedMessage ?: "未知错误"}"
            } finally {
                _isOptimizing.value = false
                _optimizeProgress.value = ""
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun loadSessionTitles() {
        val characterId = _characterId.value
        if (characterId <= 0) return
        viewModelScope.launch {
            val sessions = sessionRepository.getAllOnce(characterId)
            _sessionTitles.value = sessions.associate { it.id to (it.title.ifBlank { "新对话" }) }
        }
    }

    fun assignToSession(memoryId: Long, sessionId: Long) {
        viewModelScope.launch {
            memoryRepository.assignToSession(memoryId, sessionId)
            loadSessionTitles()
        }
    }

    fun batchAssignToSession(memoryIds: List<Long>, sessionId: Long) {
        viewModelScope.launch {
            memoryRepository.batchAssignToSession(memoryIds, sessionId)
            loadSessionTitles()
        }
    }

    fun deleteMemories(ids: List<Long>) {
        viewModelScope.launch {
            memoryRepository.deleteByIds(ids)
        }
    }
}
