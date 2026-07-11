package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.repository.*
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.ConversationFolderRepository
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.domain.service.AiService
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val database: com.kurisuapi.data.database.KurisuDatabase,
    private val characterRepository: CharacterRepository,
    private val memoryRepository: MemoryRepository,
    private val emotionRepository: EmotionRepository,
    private val relationshipRepository: RelationshipRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val folderRepository: ConversationFolderRepository,
    private val indexRepository: com.kurisuapi.data.repository.ConversationIndexRepository,
    private val aiService: AiService,
    private val gson: Gson
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

    // === AI 生成角色 ===

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    fun clearGenerateError() { _generateError.value = null }

    /**
     * 根据描述生成角色并直接存入数据库，返回新角色 ID。
     * 这样编辑页直接通过 ID 加载角色，避免跨页面 ViewModel 共享问题。
     */
    fun generateCharacter(description: String, onResult: (Long?) -> Unit) {
        if (_isGenerating.value) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            _isGenerating.value = true
            _generateError.value = null
            try {
                val systemPrompt = """
你是一个专业角色设计师。根据用户的描述，生成一个生动的角色设定。必须输出严格的 JSON。

要求：
- 名字要有创意，符合角色特点，不要用"小明""小红"这种
- 性格描述要具体生动，包含优点和缺点，50-100字
- 外观要包含发型、瞳色、体型、穿着等细节，30-60字
- 说话风格要体现角色的语言习惯，比如口头禅、句尾习惯、用词特点，30-60字
- 背景要有人物经历，让角色有故事感，50-100字
- 系统提示词是给AI的行为指令，要告诉AI如何扮演这个角色，包含行为边界和互动方式，80-150字

输出格式（严格JSON，不要任何其他内容）：
	{"name":"角色名","gender":"男或女","age":年龄数字,"personality":"性格","appearance":"外观","speakingStyle":"说话风格","background":"背景","systemPrompt":"系统提示词"}

用户描述：${description}
""".trimIndent()

                // 把用户描述放入 user 消息，确保每次生成请求都不同（防止缓存返回旧结果）
                val userPrompt = "请根据以下描述生成角色 JSON：\n${description}"
                val response = try {
                    kotlinx.coroutines.withTimeout(40_000L) {
                        aiService.chat(
                            messages = listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = userPrompt)
                            ),
                            modelOverride = null
                        )
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    _generateError.value = "生成超时（40秒），请检查网络后重试"
                    _isGenerating.value = false
                    onResult(null)
                    return@launch
                }

                if (!response.success || response.content.isBlank()) {
                    _generateError.value = "生成失败：${response.errorMessage ?: "AI 未返回内容，请重试"}"
                    _isGenerating.value = false
                    onResult(null)
                    return@launch
                }

                val jsonStr = extractJson(response.content)
                val json = try { gson.fromJson(jsonStr, JsonObject::class.java) }
                    catch (e: Exception) { null }

                if (json == null) {
                    _generateError.value = "生成失败：AI 返回格式异常，请重试"
                    _isGenerating.value = false
                    onResult(null)
                    return@launch
                }

                val now = System.currentTimeMillis()
                val entity = CharacterEntity(
                    name = json.get("name")?.asString?.trim() ?: "未命名角色",
                    gender = json.get("gender")?.asString?.trim() ?: "",
                    age = json.get("age")?.asInt ?: json.get("age")?.asString?.trim()?.toIntOrNull() ?: 0,
                    personality = json.get("personality")?.asString?.trim() ?: "",
                    appearance = json.get("appearance")?.asString?.trim() ?: "",
                    speakingStyle = json.get("speakingStyle")?.asString?.trim() ?: "",
                    background = json.get("background")?.asString?.trim() ?: "",
                    systemPrompt = json.get("systemPrompt")?.asString?.trim() ?: "",
                    exampleDialogues = json.get("exampleDialogues")?.asString?.trim()?.replace("\\n", "\n") ?: "",
                    createdAt = now, updatedAt = now
                )
                val newId = characterRepository.insert(entity)
                _isGenerating.value = false
                onResult(newId)
            } catch (e: Exception) {
                _generateError.value = "生成失败：${e.localizedMessage ?: "未知错误"}"
                _isGenerating.value = false
                onResult(null)
            }
        }
    }

    private fun extractJson(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }

    // === 保存角色 ===

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
        exampleDialogues: String = "",
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
                            systemPrompt = systemPrompt, exampleDialogues = exampleDialogues,
                            updatedAt = now
                        )
                    )
                }
            } else {
                characterRepository.insert(
                    CharacterEntity(
                        name = name, avatar = avatar, gender = gender, age = age,
                        personality = personality, appearance = appearance,
                        speakingStyle = speakingStyle, background = background,
                        systemPrompt = systemPrompt, exampleDialogues = exampleDialogues,
                        createdAt = now, updatedAt = now
                    )
                )
            }
            onDone()
        }
    }

    fun deleteCharacter(character: CharacterEntity) {
        viewModelScope.launch {
            characterRepository.deleteWithCascade(character)
        }
    }
}
