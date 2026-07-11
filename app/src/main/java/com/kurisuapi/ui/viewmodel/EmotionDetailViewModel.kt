package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.EmotionStateEntity
import com.kurisuapi.data.repository.EmotionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class EmotionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val emotionRepository: EmotionRepository
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0L

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // 修复：改为响应式 Flow，后台情绪变化能实时刷新（不再是一次性快照）
    val emotion: StateFlow<EmotionStateEntity?> = if (characterId > 0) {
        emotionRepository.getByCharacter(characterId)
            .onEach { _isLoaded.value = true }
            .catch { _isLoaded.value = true }  // 数据库异常时也要结束加载状态，防止永久转圈
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } else {
        _isLoaded.value = true
        MutableStateFlow(null)
    }
}
