package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.RelationshipEntity
import com.kurisuapi.data.repository.RelationshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class RelationshipDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val relationshipRepository: RelationshipRepository
) : ViewModel() {

    private val characterId: Long = savedStateHandle.get<Long>("characterId") ?: 0L

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // 修复：改为响应式 Flow，后台关系变化能实时刷新
    val relationship: StateFlow<RelationshipEntity?> = if (characterId > 0) {
        relationshipRepository.getByCharacter(characterId)
            .onEach { _isLoaded.value = true }
            .catch { _isLoaded.value = true }  // 数据库异常时也要结束加载状态，防止永久转圈
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } else {
        _isLoaded.value = true
        MutableStateFlow(null)
    }
}
