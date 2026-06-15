package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.*
import com.kurisuapi.data.repository.*
import com.kurisuapi.domain.engine.EmotionEngine
import com.kurisuapi.domain.engine.RelationshipEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val settingsRepository: SettingsRepository,
    private val emotionEngine: EmotionEngine,
    private val relationshipEngine: RelationshipEngine,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val emotionRepository: EmotionRepository,
    private val relationshipRepository: RelationshipRepository
) : ViewModel() {

    private val activeCharacterId: StateFlow<Long> =
        settingsRepository.observeValue(SettingsRepository.KEY_ACTIVE_CHARACTER)
            .map { it?.toLongOrNull() ?: 0L }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // 修复：原先用一次性 getById，编辑当前活跃角色（ID 不变）后首页卡片不刷新。
    // 改为响应式 observeById，角色资料变更实时反映。
    val activeCharacter: StateFlow<CharacterEntity?> = activeCharacterId
        .flatMapLatest { id ->
            if (id > 0) characterRepository.observeById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val emotion: StateFlow<EmotionStateEntity?> = activeCharacterId
        .flatMapLatest { id ->
            if (id > 0) emotionRepository.getByCharacter(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val relationship: StateFlow<RelationshipEntity?> = activeCharacterId
        .flatMapLatest { id ->
            if (id > 0) relationshipRepository.getByCharacter(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentMessages: StateFlow<List<ChatHistoryEntity>> = activeCharacterId
        .flatMapLatest { id ->
            if (id > 0) chatHistoryRepository.getByCharacter(id).map { it.take(5) }
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _wechatStatus = MutableStateFlow("未连接")
    val wechatStatus: StateFlow<String> = _wechatStatus.asStateFlow()

    private val _aiStatus = MutableStateFlow("就绪")
    val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()

    init {
        // 切换活跃角色时检查是否需要增加孤独感（长时间不聊天）
        viewModelScope.launch {
            activeCharacterId.collect { id ->
                if (id > 0) applyLonelinessDecayIfNeeded(id)
            }
        }
    }

    /**
     * 如果用户长时间未与角色聊天，自动增加孤独感。
     * 规则：距最后一条消息超过 24 小时，每过一天孤独感 +5（上限 80）。
     */
    private suspend fun applyLonelinessDecayIfNeeded(characterId: Long) {
        try {
            val recentMessages = chatHistoryRepository.getRecent(characterId, 1)
            if (recentMessages.isEmpty()) return

            val lastMessageTime = recentMessages.first().timestamp
            val hoursSinceLastChat = (System.currentTimeMillis() - lastMessageTime) / (1000 * 60 * 60)

            if (hoursSinceLastChat >= 24) {
                val emotion = emotionEngine.getEmotion(characterId)
                val daysSince = (hoursSinceLastChat / 24).toInt()
                val lonelinessIncrease = (daysSince * 5).coerceAtMost(80 - emotion.lonely).coerceAtLeast(0)

                if (lonelinessIncrease > 0) {
                    emotionRepository.insertOrUpdate(
                        emotion.copy(
                            lonely = (emotion.lonely + lonelinessIncrease).coerceIn(0, 80),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // 衰减失败不影响主流程
        }
    }

    fun setActiveCharacter(characterId: Long) {
        viewModelScope.launch {
            settingsRepository.setValue(SettingsRepository.KEY_ACTIVE_CHARACTER, characterId.toString())
        }
    }
}
