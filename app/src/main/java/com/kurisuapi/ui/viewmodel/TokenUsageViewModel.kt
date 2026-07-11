package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.repository.CacheStatsRepository
import com.kurisuapi.domain.service.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 缓存统计数据的轻量容器，只用于弹窗展示。
 */
data class DailyStats(
    val date: String,
    val embedHits: Int,
    val embedMisses: Int,
    val chatL1L2Hits: Int,
    val chatL3Hits: Int,
    val chatMisses: Int
) {
    val totalEmbed: Int get() = embedHits + embedMisses
    val totalChat: Int get() = chatL1L2Hits + chatL3Hits + chatMisses
    val totalHits: Int get() = embedHits + chatL1L2Hits + chatL3Hits
    val totalMisses: Int get() = embedMisses + chatMisses
    val totalRequests: Int get() = totalHits + totalMisses
}

@HiltViewModel
class TokenUsageViewModel @Inject constructor(
    private val aiService: AiService,
    private val cacheStatsRepository: CacheStatsRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(cacheStatsRepository.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedStats = MutableStateFlow<DailyStats?>(null)
    val selectedStats: StateFlow<DailyStats?> = _selectedStats.asStateFlow()

    private val _allDates = MutableStateFlow<List<String>>(emptyList())
    val allDates: StateFlow<List<String>> = _allDates.asStateFlow()

    /** 今日实时统计（内存 + DB 合并，当前运行中的最新数据） */
    private val _todayLive = MutableStateFlow<DailyStats?>(null)
    val todayLive: StateFlow<DailyStats?> = _todayLive.asStateFlow()

    init {
        viewModelScope.launch {
            _allDates.value = cacheStatsRepository.allDates()
            loadDate(cacheStatsRepository.today())
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        viewModelScope.launch { loadDate(date) }
    }

    /** 刷新今日实时数据（从 AiService 内存计数器读取，不查 DB） */
    fun refreshTodayLive() {
        _todayLive.value = DailyStats(
            date = cacheStatsRepository.today(),
            embedHits = aiService.embedCacheHits,
            embedMisses = aiService.embedCacheMisses,
            chatL1L2Hits = aiService.chatCacheHits,
            chatL3Hits = aiService.chatCacheL3Hits,
            chatMisses = aiService.chatCacheMisses
        )
    }

    private suspend fun loadDate(date: String) {
        if (date == cacheStatsRepository.today()) {
            // 今天的统计：内存计数器是实时权威来源（已包含本次运行的所有命中/未命中）。
            // DB 只当作 APP 重启后的兜底：内存计数器为 0 时说明刚启动，用 DB 恢复。
            val memEmbed = aiService.embedCacheHits + aiService.embedCacheMisses
            val db = if (memEmbed == 0 && aiService.chatCacheHits + aiService.chatCacheMisses == 0) {
                cacheStatsRepository.get(date)
            } else null
            _selectedStats.value = DailyStats(
                date = date,
                embedHits = if (db != null) db.embedHits else aiService.embedCacheHits,
                embedMisses = if (db != null) db.embedMisses else aiService.embedCacheMisses,
                chatL1L2Hits = if (db != null) db.chatL1L2Hits else aiService.chatCacheHits,
                chatL3Hits = if (db != null) db.chatL3Hits else aiService.chatCacheL3Hits,
                chatMisses = if (db != null) db.chatMisses else aiService.chatCacheMisses
            )
        } else {
            val db = cacheStatsRepository.get(date)
            _selectedStats.value = if (db != null) {
                DailyStats(
                    date = date,
                    embedHits = db.embedHits,
                    embedMisses = db.embedMisses,
                    chatL1L2Hits = db.chatL1L2Hits,
                    chatL3Hits = db.chatL3Hits,
                    chatMisses = db.chatMisses
                )
            } else {
                DailyStats(date = date, embedHits = 0, embedMisses = 0,
                    chatL1L2Hits = 0, chatL3Hits = 0, chatMisses = 0)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _allDates.value = cacheStatsRepository.allDates()
            loadDate(_selectedDate.value)
        }
    }
}
