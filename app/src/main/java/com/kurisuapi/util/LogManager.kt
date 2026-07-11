package com.kurisuapi.util

import android.content.Context
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全量日志管理器：读取系统 logcat，写入本地文件。
 * app 内所有 Log.*、第三方库日志、系统警告，只要是本进程输出的，全部自动捕获。
 *
 * ponytail: 后台线程 + 行级 flush，崩溃不丢日志。
 * 单文件 10MB 上限，超出自动切分。如果日产量高到切分频繁再加 mmap ring buffer。
 */
object LogManager {
    private lateinit var logDir: File
    private var currentDate = ""
    private var writer: FileWriter? = null
    private var currentFileSize = 0L
    private val lock = Any()
    private val initialized = AtomicBoolean(false)

    private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 单文件 10MB
    private const val MAX_READ_SIZE = 2 * 1024 * 1024   // getLogContent 最多读 2MB，防 OOM
    private const val MAX_FILE_AGE_DAYS = 7L

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        logDir = File(context.filesDir, "logs").apply { mkdirs() }
        cleanOldLogs()
        startLogcatReader()
        setupCrashHandler()
    }

    // ---- 文件查询 ----

    /** 列出所有可用日志文件（最近 MAX_FILE_AGE_DAYS 天），按日期倒序 */
    fun listLogFiles(): List<Pair<String, String>> {
        val today = LocalDate.now()
        return (0 until MAX_FILE_AGE_DAYS).mapNotNull { offset ->
            val date = today.minusDays(offset)
            val fileName = "kurisu_$date.log"
            val file = File(logDir, fileName)
            if (file.exists() && file.length() > 0) {
                val label = if (offset == 0L) "今天 ${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
                else "${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
                label to fileName
            } else null
        }
    }

    fun getLogContent(fileName: String? = null): String {
        val target = fileName ?: todayFileName()
        val file = File(logDir, target)
        if (!file.exists()) return "暂无日志"

        val mainText = readTail(file, MAX_READ_SIZE)
        // 收集所有分片文件（_2, _3, _4...）
        val baseName = target.removeSuffix(".log")
        val parts = mutableListOf<String>()
        var idx = 2
        while (true) {
            val part = File(logDir, "${baseName}_${idx}.log")
            if (!part.exists()) break
            parts.add(readTail(part, MAX_READ_SIZE / (idx.coerceAtMost(5))))
            idx++
        }
        return if (parts.isEmpty()) mainText
        else buildString {
            append(mainText)
            parts.forEachIndexed { i, text ->
                append("\n... 文件过大已切分（续${i + 1}）...\n\n")
                append(text)
            }
        }
    }

    fun getLogFile(): File? {
        val file = todayFile()
        return if (file.exists()) file else null
    }

    private fun todayFileName() = "kurisu_${LocalDate.now()}.log"

    // ---- 内部 ----

    private fun startLogcatReader() {
        Thread {
            while (true) {
                var process: java.lang.Process? = null
                try {
                    process = Runtime.getRuntime().exec(
                        arrayOf("logcat", "-v", "threadtime",
                            "KurisuApp:I", "ChatVM:I", "ConversationListVM:I",
                            "WeChatBotService:I", "BootReceiver:I", "DiaryWorker:I",
                            "OemPermHelper:I", "WeChatBridge:I", "ProviderFactory:I",
                            "OpenAICompat:I", "ModelRegistry:I", "AiService:I",
                            "ConversationSummarizer:I", "MemoryExtractor:I",
                            "EncryptedSettings:I", "LogManager:I",
                            "--pid=${Process.myPid()}")
                    )
                    BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                        writeLine(line)
                    }
                } catch (_: Exception) {
                    // logcat 进程被系统终止
                } finally {
                    try { process?.destroy() } catch (_: Exception) { }
                    try { process?.waitFor() } catch (_: Exception) { }
                }
                try {
                    Thread.sleep(5000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.apply {
            isDaemon = true
            name = "LogManager-logcat"
            start()
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 崩溃处理器必须完全防御，不能信任任何状态
            try {
                val crashLog = "FATAL/LogManager: 未捕获异常 thread=${thread.name} ${throwable.stackTraceToString()}"
                // 不经过 writeLine（里面有 synchronized，死锁风险），直接追加写文件
                val today = LocalDate.now().toString()
                val file = File(logDir, "kurisu_$today.log")
                FileWriter(file, true).use { fw ->
                    fw.write(crashLog)
                    fw.write("\n")
                    fw.flush()
                }
            } catch (_: Exception) {
                // 连文件写入都失败了，只能放弃
            }
            try {
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (_: Exception) {
                // 原处理器也挂了，调用 system exit 避免 ANR
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun writeLine(line: String) {
        synchronized(lock) {
            val today = LocalDate.now().toString()
            val dateChanged = today != currentDate
            if (dateChanged) {
                writer?.close()
                currentDate = today
                currentFileSize = 0L
                splitIndex = 0
                writer = FileWriter(File(logDir, "kurisu_$today.log"), true)
            }
            // 用实际文件大小校验，超过上限自动切到下一个分片
            val w = writer ?: return
            val actualSize = File(logDir, currentFileName()).length()
            if (actualSize >= MAX_FILE_SIZE) {
                w.close()
                currentFileSize = 0L
                val nextName = nextSplitName(today)
                writer = FileWriter(File(logDir, nextName), true)
            }
            writer?.apply {
                write(line)
                write("\n")
                flush()
                currentFileSize += line.length + 1
            }
        }
    }

    private fun currentFileName(): String {
        val today = LocalDate.now().toString()
        return splitNameForIndex(today, splitIndex)
    }

    private var splitIndex = 0

    private fun nextSplitName(today: String): String {
        splitIndex++
        return splitNameForIndex(today, splitIndex)
    }

    private fun splitNameForIndex(today: String, index: Int): String {
        return if (index == 0) "kurisu_$today.log" else "kurisu_${today}_${index + 1}.log"
    }

    private fun todayFile() = File(logDir, "kurisu_${LocalDate.now()}.log")

    /** 读取文件尾部最多 maxBytes 字节，避免大文件 OOM */
    private fun readTail(file: File, maxBytes: Int): String {
        val len = file.length()
        if (len <= maxBytes) return file.readText()
        val toSkip = len - maxBytes
        return buildString {
            appendLine("... (已省略前 ${toSkip / 1024}KB，显示最近 ${maxBytes / 1024}KB) ...")
            file.inputStream().use { input ->
                var remaining = toSkip
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped <= 0) break
                    remaining -= skipped
                }
                append(input.bufferedReader().readText())
            }
        }
    }

    private fun cleanOldLogs() {
        val cutoff = LocalDate.now().minusDays(MAX_FILE_AGE_DAYS)
        logDir.listFiles()?.forEach { file ->
            // 从文件名提取日期：kurisu_2025-06-29.log 或 kurisu_2025-06-29_2.log
            val name = file.name.removePrefix("kurisu_").removeSuffix(".log")
            val dateStr = name.substringBefore("_") // "2025-06-29" 或 "2025-06-29_2" → "2025-06-29"
            try {
                if (LocalDate.parse(dateStr).isBefore(cutoff)) file.delete()
            } catch (_: Exception) {
                // 文件名不符合日期格式，跳过
            }
        }
    }
}
