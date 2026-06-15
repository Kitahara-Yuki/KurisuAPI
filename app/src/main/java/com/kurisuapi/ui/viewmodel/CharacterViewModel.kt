package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.repository.*
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.ConversationFolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val memoryRepository: MemoryRepository,
    private val emotionRepository: EmotionRepository,
    private val relationshipRepository: RelationshipRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val folderRepository: ConversationFolderRepository
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> = characterRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingCharacter = MutableStateFlow<CharacterEntity?>(null)
    val editingCharacter: StateFlow<CharacterEntity?> = _editingCharacter.asStateFlow()

    fun loadCharacter(id: Long) {
        viewModelScope.launch {
            _editingCharacter.value = characterRepository.getById(id)
        }
    }

    fun saveCharacter(
        id: Long?,
        name: String,
        avatar: String,
        gender: String,
        age: Int,
        personality: String,
        appearance: String,
        speakingStyle: String,
        background: String,
        systemPrompt: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (id != null && id > 0) {
                val existing = characterRepository.getById(id)
                if (existing != null) {
                    characterRepository.update(
                        existing.copy(
                            name = name, avatar = avatar, gender = gender, age = age,
                            personality = personality, appearance = appearance,
                            speakingStyle = speakingStyle, background = background,
                            systemPrompt = systemPrompt, updatedAt = now
                        )
                    )
                }
            } else {
                characterRepository.insert(
                    CharacterEntity(
                        name = name, avatar = avatar, gender = gender, age = age,
                        personality = personality, appearance = appearance,
                        speakingStyle = speakingStyle, background = background,
                        systemPrompt = systemPrompt, createdAt = now, updatedAt = now
                    )
                )
            }
            onDone()
        }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch {
            // 1. 清理聊天记录
            chatHistoryRepository.deleteByCharacter(character.id)
            // 2. 清理会话（含聊天记录已在步骤1清理，此处清理会话元数据）
            sessionRepository.deleteByCharacter(character.id)
            // 3. 清理文件夹
            folderRepository.deleteByCharacter(character.id)
            // 4. 清理记忆
            memoryRepository.deleteByCharacter(character.id)
            // 5. 清理情绪状态
            emotionRepository.deleteByCharacter(character.id)
            // 6. 清理关系数据
            relationshipRepository.deleteByCharacter(character.id)
            // 7. 清理用户画像
            userProfileRepository.deleteByCharacter(character.id)
            // 8. 如果删除的是当前活跃角色，清除活跃角色设置
            val activeId = settingsRepository.getActiveCharacterId()
            if (activeId == character.id) {
                settingsRepository.setValue(SettingsRepository.KEY_ACTIVE_CHARACTER, "")
            }
            // 9. 最后删除角色本身
            characterRepository.delete(character)
        }
    }
}
