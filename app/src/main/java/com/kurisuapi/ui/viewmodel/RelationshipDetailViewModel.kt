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

    // 修复：改为响应式 Flow，后台关系变化能实时刷新
    val relationship: StateFlow<RelationshipEntity?> = if (characterId > 0) {
        relationshipRepository.getByCharacter(characterId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } else {
        MutableStateFlow(null)
    }
}
