package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.ModelEntity
import com.kurisuapi.data.entity.ProviderEntity
import com.kurisuapi.data.repository.ModelRepository
import com.kurisuapi.data.repository.ProviderRepository
import com.kurisuapi.domain.provider.*
import com.kurisuapi.domain.service.ModelRegistryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository,
    private val providerFactory: ProviderFactory,
    private val modelRegistryService: ModelRegistryService
) : ViewModel() {

    val providers: StateFlow<List<ProviderEntity>> = providerRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingProvider = MutableStateFlow<ProviderEntity?>(null)
    val editingProvider: StateFlow<ProviderEntity?> = _editingProvider.asStateFlow()

    val models: StateFlow<List<ModelEntity>> = _editingProvider
        .filterNotNull()
        .flatMapLatest { modelRepository.getAllByProvider(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 模型搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 搜索过滤后的模型列表 */
    val filteredModels: StateFlow<List<ModelEntity>> = combine(
        models, _searchQuery
    ) { modelList, query ->
        if (query.isBlank()) modelList
        else modelList.filter {
            it.modelId.contains(query, ignoreCase = true) ||
            it.displayName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前模型的弃用警告 */
    private val _deprecationWarning = MutableStateFlow<String?>(null)
    val deprecationWarning: StateFlow<String?> = _deprecationWarning.asStateFlow()

    /** 是否正在同步 */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** 最后同步时间 */
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    init {
        viewModelScope.launch {
            providerRepository.initDefaultProviders()
        }
    }

    fun loadProvider(id: Long) {
        viewModelScope.launch {
            _editingProvider.value = providerRepository.getById(id)
        }
    }

    fun createNewProvider() {
        _editingProvider.value = ProviderEntity(
            name = "",
            type = "openai_compatible",
            baseUrl = ""
        )
    }

    fun saveProvider(
        id: Long?,
        name: String,
        type: String,
        baseUrl: String,
        apiKey: String,
        modelsUrlOverride: String?,
        model: String,
        temperature: Double,
        maxTokens: Int,
        isDefault: Boolean,
        thinkingEnabled: Boolean = true,
        reasoningEffort: String = "high",
        thinkingBudgetTokens: Int = 0,
        contextWindow: Long = 0,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val savedId: Long
            if (id != null && id > 0) {
                savedId = id
                val existing = providerRepository.getById(id)
                if (existing != null) {
                    providerRepository.update(
                        existing.copy(
                            name = name, type = type, baseUrl = baseUrl,
                            apiKey = apiKey, modelsUrlOverride = modelsUrlOverride,
                            model = model, temperature = temperature, maxTokens = maxTokens,
                            isDefault = isDefault, updatedAt = now,
                            thinkingEnabled = thinkingEnabled,
                            reasoningEffort = reasoningEffort,
                            thinkingBudgetTokens = thinkingBudgetTokens,
                            contextWindow = contextWindow
                        )
                    )
                }
            } else {
                // Bug fix: 捕获 insert 返回的新 ID，用于后续 setDefault
                savedId = providerRepository.insert(
                    ProviderEntity(
                        name = name, type = type, baseUrl = baseUrl,
                        apiKey = apiKey, modelsUrlOverride = modelsUrlOverride,
                        model = model, temperature = temperature, maxTokens = maxTokens,
                        isDefault = isDefault,
                        thinkingEnabled = thinkingEnabled,
                        reasoningEffort = reasoningEffort,
                        thinkingBudgetTokens = thinkingBudgetTokens,
                        contextWindow = contextWindow,
                        createdAt = now, updatedAt = now
                    )
                )
            }
            // Bug fix: 使用 savedId（insert 返回值或已有 id）而非查询 getDefault
            if (isDefault && savedId > 0) {
                providerRepository.setDefault(savedId)
            }
            _message.value = "已保存"
            onDone()
        }
    }

    fun deleteProvider(provider: ProviderEntity) {
        viewModelScope.launch {
            // 修复：先清理该 Provider 的所有 model 子行，避免孤儿数据
            modelRepository.deleteAllByProvider(provider.id)
            providerRepository.delete(provider)
        }
    }

    fun testConnection(provider: ProviderEntity) {
        viewModelScope.launch {
            _isTesting.value = true
            _connectionTestResult.value = null

            try {
                val aiProvider = providerFactory.create(provider)
                val result = if (aiProvider is OpenAiCompatibleProvider && !provider.modelsUrlOverride.isNullOrBlank()) {
                    aiProvider.testConnection(provider.apiKey, provider.baseUrl, provider.modelsUrlOverride)
                } else {
                    aiProvider.testConnection(provider.apiKey, provider.baseUrl)
                }
                _connectionTestResult.value = result
            } catch (e: Exception) {
                _connectionTestResult.value = ConnectionTestResult(
                    success = false,
                    latencyMs = 0,
                    errorMessage = "测试连接时出错：${e.localizedMessage ?: "未知错误"}"
                )
            }

            _isTesting.value = false
        }
    }

    fun fetchModels(provider: ProviderEntity) {
        viewModelScope.launch {
            _isFetchingModels.value = true

            try {
                // 使用 ModelRegistryService 同步，自动处理弃用标记
                val result = modelRegistryService.syncProviderModels(provider)

                if (result.success) {
                    // Auto-select first model if provider has no model configured yet
                    if (provider.model.isBlank() && provider.id > 0) {
                        val existingProvider = providerRepository.getById(provider.id)
                        if (existingProvider != null && existingProvider.model.isBlank()) {
                            // 同步后更新 Provider 的 updatedAt 时间戳
                            providerRepository.update(
                                existingProvider.copy(updatedAt = System.currentTimeMillis())
                            )
                        }
                    }

                    _lastSyncTime.value = System.currentTimeMillis()
                    _message.value = if (result.deprecatedCount > 0) {
                        "已同步 ${result.modelCount} 个模型（${result.deprecatedCount} 个即将弃用）"
                    } else {
                        "已同步 ${result.modelCount} 个模型"
                    }

                    // 检查当前模型是否弃用
                    checkCurrentModelDeprecation(provider)
                } else {
                    _message.value = result.message
                }
            } catch (e: Exception) {
                _message.value = "同步模型失败：${e.message ?: "未知错误"}"
            }

            _isFetchingModels.value = false
        }
    }

    /**
     * 一键同步所有 Provider 的模型列表
     */
    fun syncAllModels() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val results = modelRegistryService.syncAllProviders()
                val totalModels = results.values.sumOf { it.modelCount }
                val totalDeprecated = results.values.sumOf { it.deprecatedCount }
                val successCount = results.values.count { it.success }

                _lastSyncTime.value = System.currentTimeMillis()
                _message.value = if (totalDeprecated > 0) {
                    "已同步 $successCount 个 Provider，共 $totalModels 个模型（$totalDeprecated 个即将弃用）"
                } else {
                    "已同步 $successCount 个 Provider，共 $totalModels 个模型"
                }
            } catch (e: Exception) {
                _message.value = "同步失败：${e.message}"
            }
            _isSyncing.value = false
        }
    }

    /**
     * 检查当前使用的模型是否已弃用
     */
    private suspend fun checkCurrentModelDeprecation(provider: ProviderEntity) {
        if (provider.model.isNotBlank()) {
            val warning = modelRegistryService.checkDeprecationWarning(provider.id, provider.model)
            _deprecationWarning.value = warning
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addCustomModel(providerId: Long, modelId: String, displayName: String) {
        viewModelScope.launch {
            modelRepository.insert(
                ModelEntity(
                    providerId = providerId,
                    modelId = modelId,
                    displayName = displayName,
                    isCustom = true,
                    lastFetchedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch {
            modelRepository.delete(model)
        }
    }

    fun setDefaultProvider(id: Long) {
        viewModelScope.launch {
            providerRepository.setDefault(id)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearTestResult() {
        _connectionTestResult.value = null
    }
}
