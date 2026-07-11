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
import com.kurisuapi.domain.engine.EmbeddingService
import com.kurisuapi.domain.service.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val sessionRepository: com.kurisuapi.data.repository.ConversationSessionRepository,
    private val aiService: AiService,
    private val embeddingService: EmbeddingService
) : ViewModel() {

    private val _characterId = MutableStateFlow(0L)

    val memories: StateFlow<List<MemoryEntity>> = _characterId
        .flatMapLatest { id -> if (id > 0) memoryRepository.getByCharacter(id).catch { emit(emptyList()) } else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = _characterId
        .flatMapLatest { id -> if (id > 0) userProfileRepository.getByCharacter(id).catch { emit(null) } else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _optimizeProgress = MutableStateFlow("")
    val optimizeProgress: StateFlow<String> = _optimizeProgress.asStateFlow()

    private val _sessionTitles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val sessionTitles: StateFlow<Map<Long, String>> = _sessionTitles.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MemoryEntity>?>(null)
    val searchResults: StateFlow<List<MemoryEntity>?> = _searchResults.asStateFlow()

    // 搜索任务的取消句柄，新搜索开始时自动取消上一次未完成的搜索
    private var searchJob: Job? = null

    val isSearching: StateFlow<Boolean> = _searchQuery.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 每角色的处理任务追踪，防止快速切换角色时并发执行 reprocessAllMemories
    private val activeReprocessJobs = mutableMapOf<Long, Job>()

    init {
        // 监听全局活跃角色变化，自动同步（修复从其他页面切换角色后记忆列表不更新的问题）
        viewModelScope.launch {
            settingsRepository.observeValue(SettingsRepository.KEY_ACTIVE_CHARACTER).collect { value ->
                val globalId = value?.toLongOrNull() ?: 0L
                if (globalId > 0 && globalId != _characterId.value) {
                    setCharacterId(globalId)
                }
            }
        }
    }

    fun setCharacterId(id: Long) {
        _characterId.value = id
        // 旧数据兼容：首次打开记忆页面时自动规范化旧记忆（仅一次）
        if (id > 0) {
            // 如果该角色已有处理任务在运行则跳过，防止并发冲突
            if (activeReprocessJobs.containsKey(id)) return
            val job = viewModelScope.launch {
                try {
                    if (!settingsRepository.isMemoryNormalized(id)) {
                        val count = memoryExtractor.reprocessAllMemories(id)
                        if (count > 0) {
                            _message.value = (_message.value?.let { "$it，" } ?: "") + "已自动规范化 $count 条旧记忆"
                        }
                        settingsRepository.setMemoryNormalized(id)
                    }
                } catch (_: Exception) { }
                activeReprocessJobs.remove(id)
            }
            activeReprocessJobs[id] = job
        }
    }

    fun addMemory(content: String, importance: Int) {
        val characterId = _characterId.value
        if (characterId <= 0) return
        viewModelScope.launch {
            // 预计算嵌入向量（失败不阻塞记忆存储）
            val embedding = try {
                aiService.embed(content, characterId)?.let { embeddingService.encodeEmbedding(it) }
            } catch (_: Exception) { null }
            memoryRepository.insert(
                MemoryEntity(
                    characterId = characterId,
                    content = content,
                    importance = importance,
                    source = "manual",
                    embedding = embedding
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
            // 软删除：标记为已删除而非物理删除，与 AI 自动删除行为一致
            memoryRepository.softDeleteById(memory.id)
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
            // 批量软删除
            memoryRepository.softDeleteByIds(ids)
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        val characterId = _characterId.value
        if (characterId <= 0 || query.isBlank()) {
            _searchResults.value = null
            searchJob?.cancel()
            return
        }
        // 取消上一次搜索，防止旧结果覆盖新搜索（快速输入清空时的竞态）
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                _searchResults.value = memoryRepository.search(characterId, query)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = null
    }
}
