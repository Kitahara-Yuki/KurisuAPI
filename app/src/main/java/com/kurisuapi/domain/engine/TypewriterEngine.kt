package com.kurisuapi.domain.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 打字机动画引擎。
 *
 * 职责：从一段不断增长的文本中逐字推进显示，产生平滑的打字效果。
 * 流结束后继续播完剩余文字，自然退出。
 *
 * 这是一个独立黑盒 — ViewModel 只需调用 [start]、[markStreamComplete]、
 * [awaitFinish]、[reset]，无需关心内部实现。任何 AI 修改 processMessage 时，
 * 只要不动这 4 个调用点，打字机效果就不会被破坏。
 */
class TypewriterEngine(private val scope: CoroutineScope) {

    /** 打字机动画输出文字，逐字增长 */
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    /** 流式接收是否已结束（流结束 ≠ 打字机结束，打字机需继续播完剩余文字） */
    private val _streamComplete = AtomicBoolean(false)

    /** 打字机协程 */
    private var job: Job? = null

    /** 打字机是否正在运行 */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * 启动打字机动画。
     *
     * @param streamSource 获取当前完整流式文本的函数。每次迭代都会调用，
     *                     返回的文本可能随时间增长（流式接收中）。
     */
    fun start(streamSource: () -> String) {
        job?.cancel()
        _streamComplete.set(false)
        _text.value = "" // 清空上一轮残留文本，确保从零开始
        job = scope.launch(Dispatchers.Main) {
            try {
                var pos = _text.value.length
                while (isActive) {
                    val full = streamSource()
                    if (pos < full.length) {
                        pos++
                        _text.value = full.take(pos)
                        val ch = if (pos <= full.length) full[pos - 1] else ' '
                        val speed = when {
                            ch in "。！？\n" -> 60L
                            ch == '.' -> 80L
                            ch in "，" -> 45L
                            full.length < 20 -> 20L
                            else -> 30L
                        }
                        delay(speed)
                    } else if (_streamComplete.get()) {
                        // 流已结束且所有文字已显示完毕 → 自然退出
                        break
                    } else {
                        delay(80)
                    }
                }
            } catch (_: CancellationException) { }
        }
    }

    /** 通知打字机：流式接收已结束，播完剩余文字后可自然退出 */
    fun markStreamComplete() {
        _streamComplete.set(true)
    }

    /**
     * 跳到当前全文末尾（中止动画，立即显示 streamSource 返回的完整文本）。
     * 用于阶段护栏触发时需要切换显示内容等场景。
     */
    fun skipToEnd(streamSource: () -> String) {
        job?.cancel()
        job = null
        _text.value = streamSource()
    }

    /**
     * 重新启动打字机（先跳到当前末尾，再重新开始动画，并标记流结束）。
     * 等价于 skipToEnd + start + markStreamComplete，用于阶段护栏触发生成新回复后，
     * 对新文本重新执行打字机动画。
     */
    fun restart(streamSource: () -> String) {
        // 重置文本为空，从零开始重新动画
        _text.value = ""
        start(streamSource)
    }

    /**
     * 等待打字机播完所有剩余文字后返回。
     *
     * @param timeoutMs 超时毫秒，超时后强制结束
     */
    suspend fun awaitFinish(timeoutMs: Long = 12_000L) {
        try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                while (job?.isActive == true) {
                    delay(60)
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // 超时：强制结束，保留当前文本
            job?.cancel()
            job = null
        }
    }

    /** 完全重置打字机状态（取消协程，清空文本，重置完成标志） */
    fun reset() {
        job?.cancel()
        job = null
        _streamComplete.set(false)
        _text.value = ""
    }
}
