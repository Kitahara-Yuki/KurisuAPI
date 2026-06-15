package com.kurisuapi.ui.navigation

import androidx.lifecycle.ViewModel
import com.kurisuapi.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    val settingsRepository: SettingsRepository
) : ViewModel()
