package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CharacterListViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val activeCharacterId: StateFlow<Long?> = settingsRepository
        .observeValue(SettingsRepository.KEY_ACTIVE_CHARACTER)
        .map { it?.toLongOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActiveCharacter(characterId: Long) {
        viewModelScope.launch {
            settingsRepository.setValue(SettingsRepository.KEY_ACTIVE_CHARACTER, characterId.toString())
        }
    }
}
