package com.kurisuapi.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _userName = MutableStateFlow(SettingsRepository.DEFAULT_USER_NAME)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userAvatar = MutableStateFlow(SettingsRepository.DEFAULT_USER_AVATAR)
    val userAvatar: StateFlow<String> = _userAvatar.asStateFlow()

    private val _userGender = MutableStateFlow(SettingsRepository.DEFAULT_USER_GENDER)
    val userGender: StateFlow<String> = _userGender.asStateFlow()

    private val _userRegion = MutableStateFlow(SettingsRepository.DEFAULT_USER_REGION)
    val userRegion: StateFlow<String> = _userRegion.asStateFlow()

    private val _userBackground = MutableStateFlow(SettingsRepository.DEFAULT_USER_BACKGROUND)
    val userBackground: StateFlow<String> = _userBackground.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            _userName.value = settingsRepository.getUserName()
            _userAvatar.value = settingsRepository.getUserAvatar()
            _userGender.value = settingsRepository.getUserGender()
            _userRegion.value = settingsRepository.getUserRegion()
            _userBackground.value = settingsRepository.getUserBackground()
            _loaded.value = true
        }
    }

    fun setUserName(name: String) {
        _userName.value = name
        viewModelScope.launch {
            settingsRepository.setUserName(name)
            settingsRepository.incrementUserProfileVersion()
        }
    }

    fun setUserGender(gender: String) {
        _userGender.value = gender
        viewModelScope.launch {
            settingsRepository.setUserGender(gender)
            settingsRepository.incrementUserProfileVersion()
        }
    }

    fun setUserRegion(region: String) {
        _userRegion.value = region
        viewModelScope.launch {
            settingsRepository.setUserRegion(region)
            settingsRepository.incrementUserProfileVersion()
        }
    }

    fun setUserBackground(background: String) {
        _userBackground.value = background
        viewModelScope.launch {
            settingsRepository.setUserBackground(background)
            settingsRepository.incrementUserProfileVersion()
        }
    }

    fun saveAvatarFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.filesDir, "avatars")
                    if (!dir.exists()) dir.mkdirs()
                    // 每次使用唯一文件名，确保 StateFlow 能检测到变化并触发 UI 刷新
                    val file = File(dir, "user_avatar_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // 删除旧头像文件，避免堆积
                    dir.listFiles()?.forEach { f ->
                        if (f.name.startsWith("user_avatar_") && f.absolutePath != file.absolutePath) {
                            f.delete()
                        }
                    }
                    file.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            if (path != null) {
                _userAvatar.value = path
                settingsRepository.setUserAvatar(path)
            }
        }
    }

    /** 保存已裁剪好的头像文件路径（UCrop 回调） */
    fun saveAvatarFromPath(path: String) {
        _userAvatar.value = path
        viewModelScope.launch { settingsRepository.setUserAvatar(path) }
    }
}
