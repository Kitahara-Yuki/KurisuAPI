package com.kurisuapi.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurisuapi.util.LogManager
import com.kurisuapi.util.sdp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SHARE_LENGTH = 500_000
private const val MAX_DISPLAY_LINES = 3000

private data class HealthItem(
    val label: String,
    val status: HealthStatus,
    val detail: String
)

private enum class HealthStatus { OK, WARN, BAD }

private data class LoadResult(
    val raw: String,
    val health: List<HealthItem>,
    val display: Pair<String, Boolean>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var availableFiles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // (label, fileName)
    var selectedFile by remember { mutableStateOf("") }
    var logContent by remember { mutableStateOf("") }
    var displayLog by remember { mutableStateOf("") }
    var isTruncated by remember { mutableStateOf(false) }
    var healthItems by remember { mutableStateOf<List<HealthItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // 按文件名缓存日志内容，切换文件不重复读
    val contentCache = remember { mutableMapOf<String, String>() }

    fun loadFileList() {
        coroutineScope.launch(Dispatchers.IO) {
            availableFiles = LogManager.listLogFiles()
            if (availableFiles.isNotEmpty() && selectedFile.isEmpty()) {
                selectedFile = availableFiles.first().second
            }
        }
    }

    fun loadData(force: Boolean = false) {
        if (selectedFile.isEmpty()) return
        if (!force && contentCache.containsKey(selectedFile)) {
            logContent = contentCache[selectedFile] ?: ""
            return
        }
        coroutineScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                val raw = LogManager.getLogContent(selectedFile)
                contentCache[selectedFile] = raw
                val healthDeferred = async { runHealthChecks(context, raw) }
                val displayDeferred = async {
                    val allLines = raw.lineSequence().toList()
                    if (allLines.size > MAX_DISPLAY_LINES)
                        allLines.takeLast(MAX_DISPLAY_LINES).joinToString("\n") to true
                    else raw to false
                }
                LoadResult(raw, healthDeferred.await(), displayDeferred.await())
            }
            logContent = result.raw
            healthItems = result.health
            displayLog = result.display.first
            isTruncated = result.display.second
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFileList() }
    LaunchedEffect(selectedFile) { if (selectedFile.isNotEmpty()) loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("软件日志", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadData(force = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = {
                        try {
                            if (logContent.isEmpty()) {
                                Toast.makeText(context, "暂无日志内容可分享", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            // 写入临时文件再通过 FileProvider 分享，避免 Intent 文本大小限制
                            val shareDir = File(context.cacheDir, "logs")
                            shareDir.mkdirs()
                            val shareFile = File(shareDir, "kurisu_log_${java.time.LocalDate.now()}.txt")
                            shareFile.writeText(logContent)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", shareFile
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "分享日志"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "分享")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val copyText = if (logContent.length > MAX_SHARE_LENGTH) {
                        logContent.takeLast(MAX_SHARE_LENGTH)
                    } else logContent
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("日志", copyText))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                text = { Text("一键复制") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = if (isLoading) Alignment.Center else Alignment.TopStart
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(sdp(12.dp))
                ) {
                    // 日期选择器
                    if (availableFiles.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(sdp(6.dp))
                        ) {
                            availableFiles.forEach { (label, fileName) ->
                                val isSelected = fileName == selectedFile
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (fileName != selectedFile) {
                                            selectedFile = fileName
                                        }
                                    },
                                    label = {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.height(sdp(28.dp))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(sdp(12.dp)))
                    }

                    // 健康检查看板
                    if (healthItems.isNotEmpty()) {
                        Text(
                            "健康检查",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = sdp(8.dp))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))
                        ) {
                            healthItems.forEach { item ->
                                HealthCard(item)
                            }
                        }
                        Spacer(modifier = Modifier.height(sdp(16.dp)))
                        Divider()
                        Spacer(modifier = Modifier.height(sdp(12.dp)))
                    }

                    // 日志内容 — 着色
                    if (isTruncated) {
                        Text(
                            "（仅显示最近 $MAX_DISPLAY_LINES 行，共 ${logContent.lines().size} 行。完整日志请点右上角分享）",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = sdp(8.dp))
                        )
                    }
                    SelectionContainer {
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text(
                                text = buildColoredLog(displayLog),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(item: HealthItem) {
    val bg = when (item.status) {
        HealthStatus.OK -> Color(0xFF2E7D32)
        HealthStatus.WARN -> Color(0xFFE65100)
        HealthStatus.BAD -> Color(0xFFC62828)
    }
    val icon = when (item.status) {
        HealthStatus.OK -> Icons.Outlined.CheckCircle
        HealthStatus.WARN -> Icons.Outlined.Warning
        HealthStatus.BAD -> Icons.Outlined.Error
    }

    Card(
        modifier = Modifier.width(sdp(110.dp)),
        colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(sdp(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(sdp(10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = bg, modifier = Modifier.size(sdp(22.dp)))
            Spacer(modifier = Modifier.height(sdp(4.dp)))
            Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = bg)
            Text(item.detail, fontSize = 10.sp, color = bg.copy(alpha = 0.7f))
        }
    }
}

private fun buildColoredLog(log: String) = buildAnnotatedString {
    log.lines().forEach { line ->
        val style = when {
            line.contains("FATAL") || line.contains(" E/") -> SpanStyle(color = Color(0xFFEF5350))
            line.contains(" W/") -> SpanStyle(color = Color(0xFFFFCA28))
            else -> SpanStyle(color = Color(0xFFB0BEC5))
        }
        withStyle(style) { append(line) }
        append("\n")
    }
}

private fun runHealthChecks(context: Context, log: String): List<HealthItem> {
    val items = mutableListOf<HealthItem>()

    // 1. 存储空间
    try {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val free = stat.availableBytes
        val freeMB = free / (1024 * 1024)
        items.add(
            when {
                freeMB < 100 -> HealthItem("存储空间", HealthStatus.BAD, "仅剩 ${freeMB}MB")
                freeMB < 500 -> HealthItem("存储空间", HealthStatus.WARN, "剩余 ${freeMB}MB")
                else -> HealthItem("存储空间", HealthStatus.OK, "${freeMB / 1024}GB 可用")
            }
        )
    } catch (_: Exception) {
        items.add(HealthItem("存储空间", HealthStatus.WARN, "无法检测"))
    }

    // 2. 数据库文件
    try {
        val dbFile = context.getDatabasePath("kurisu_database")
        if (dbFile.exists() && dbFile.length() > 0) {
            val sizeKB = dbFile.length() / 1024
            items.add(HealthItem("数据库", HealthStatus.OK, "${sizeKB}KB"))
        } else {
            items.add(HealthItem("数据库", HealthStatus.BAD, "文件缺失"))
        }
    } catch (_: Exception) {
        items.add(HealthItem("数据库", HealthStatus.WARN, "无法检测"))
    }

    // 3. 日志写入状态
    try {
        val logFile = LogManager.getLogFile()
        if (logFile != null && logFile.length() > 0) {
            val sizeKB = logFile.length() / 1024
            val lastMod = logFile.lastModified()
            val secondsAgo = (System.currentTimeMillis() - lastMod) / 1000
            val detail = if (secondsAgo < 300) "${sizeKB}KB · 活跃" else "${sizeKB}KB · ${secondsAgo / 60}分钟前"
            items.add(HealthItem("日志状态", HealthStatus.OK, detail))
        } else {
            items.add(HealthItem("日志状态", HealthStatus.WARN, "未生成"))
        }
    } catch (_: Exception) {
        items.add(HealthItem("日志状态", HealthStatus.WARN, "无法检测"))
    }

    // 4. 今日异常统计（单遍流式统计，不物化整个列表）
    var errorCount = 0
    var warnCount = 0
    var totalLines = 0
    log.lineSequence().forEach { line ->
        totalLines++
        if (" E/" in line || "FATAL" in line) errorCount++
        if (" W/" in line) warnCount++
    }
    items.add(
        when {
            errorCount > 0 -> HealthItem("今日异常", HealthStatus.BAD, "E:${errorCount} W:${warnCount}")
            warnCount > 5 -> HealthItem("今日异常", HealthStatus.WARN, "W:${warnCount}")
            else -> HealthItem("今日异常", HealthStatus.OK, "无异常")
        }
    )

    // 5. 日志总量
    items.add(
        when {
            totalLines > 100000 -> HealthItem("日志总量", HealthStatus.BAD, "${totalLines}行 · 过大")
            totalLines > 50000 -> HealthItem("日志总量", HealthStatus.WARN, "${totalLines}行")
            else -> HealthItem("日志总量", HealthStatus.OK, "${totalLines}行")
        }
    )

    return items
}
