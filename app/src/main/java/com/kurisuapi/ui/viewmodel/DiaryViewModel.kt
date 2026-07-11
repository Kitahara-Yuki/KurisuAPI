package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.entity.DiaryEntryEntity
import com.kurisuapi.data.repository.*
import com.kurisuapi.domain.service.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val diaryRepository: DiaryRepository,
    private val characterRepository: CharacterRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val aiService: AiService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _characterId = MutableStateFlow(0L)

    val character: StateFlow<CharacterEntity?> = _characterId
        .flatMapLatest { id ->
            if (id > 0) characterRepository.observeById(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val entries: StateFlow<List<DiaryEntryEntity>> = _characterId
        .flatMapLatest { id ->
            if (id > 0) diaryRepository.getByCharacter(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _expandedIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedIds: StateFlow<Set<Long>> = _expandedIds.asStateFlow()

    fun loadCharacter(characterId: Long) {
        _characterId.value = characterId
    }

    fun toggleExpanded(id: Long) {
        _expandedIds.update { ids ->
            if (id in ids) ids - id else ids + id
        }
    }

    /** 用户名（用于手动日记标注，页面加载时读取一次） */
    private val _userName = MutableStateFlow(SettingsRepository.DEFAULT_USER_NAME)
    val userName: StateFlow<String> = _userName.asStateFlow()

    init {
        viewModelScope.launch {
            _userName.value = settingsRepository.getUserName()
        }
    }

    fun clearError() { _error.value = null }

    /** 手动生成本天日记 */
    fun generateDiary(dateStr: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            try {
                val characterId = _characterId.value
                if (characterId <= 0) return@launch

                val character = characterRepository.getById(characterId)
                if (character == null) {
                    _error.value = "角色不存在"
                    _isGenerating.value = false
                    return@launch
                }

                // 解析日期范围
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                val startOfDay = date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
                val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

                val messages = chatHistoryRepository.getByCharacterAndDateRange(
                    characterId, startOfDay, endOfDay
                )

                if (messages.isEmpty()) {
                    _error.value = "这一天没有对话记录"
                    _isGenerating.value = false
                    return@launch
                }

                val conversationText = buildString {
                    for (msg in messages) {
                        val role = if (msg.sender == "user") "用户" else character.name
                        appendLine("$role: ${msg.content}")
                        appendLine()
                    }
                }

                val systemPrompt = buildSystemPrompt(character)
                val userPrompt = buildString {
                    appendLine("以下是${dateStr}我和用户的对话记录：")
                    appendLine()
                    appendLine("---")
                    appendLine(conversationText)
                    appendLine("---")
                    appendLine()
                    appendLine("请以第一人称写一篇关于这一天的日记。")
                }

                val response = aiService.chat(
                    messages = listOf(
                        ChatMessage(role = "system", content = systemPrompt),
                        ChatMessage(role = "user", content = userPrompt)
                    ),
                    characterId = characterId
                )

                if (!response.success || response.content.isBlank()) {
                    _error.value = "AI 生成失败：${response.errorMessage ?: "未知错误"}"
                    _isGenerating.value = false
                    return@launch
                }

                val entity = DiaryEntryEntity(
                    characterId = characterId,
                    date = dateStr,
                    content = response.content.trim()
                )
                diaryRepository.save(entity)

                _isGenerating.value = false
            } catch (e: Exception) {
                _error.value = "生成失败：${e.localizedMessage ?: "未知错误"}"
                _isGenerating.value = false
            }
        }
    }

    /** 手动删除一篇日记 */
    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            diaryRepository.delete(id)
        }
    }

    /** 手动写日记（用户自己写内容） */
    fun saveManualDiary(dateStr: String, content: String) {
        viewModelScope.launch {
            val characterId = _characterId.value
            if (characterId <= 0 || content.isBlank()) return@launch
            val entity = DiaryEntryEntity(
                characterId = characterId,
                date = dateStr,
                content = content.trim(),
                isManual = true
            )
            diaryRepository.save(entity)
        }
    }

    private fun buildSystemPrompt(character: CharacterEntity): String = buildString {
        appendLine("你是${character.name}，${character.personality}")
        appendLine()
        appendLine("请以第一人称视角，写一篇私人日记。")
        appendLine()
        appendLine("要求：")
        appendLine("- 用「我」来称呼自己")
        appendLine("- 自然地记录你的感受和印象深刻的事")
        appendLine("- 风格私密自然，像真正的日记")
        appendLine("- 200-400字左右")
        appendLine("- 不要出现「用户说」、「对方说」等第三人称")
        appendLine("- 只输出日记正文，不要加标题或日期")
        if (character.speakingStyle.isNotBlank()) {
            appendLine("- 保持你的说话风格：${character.speakingStyle}")
        }
    }
}
