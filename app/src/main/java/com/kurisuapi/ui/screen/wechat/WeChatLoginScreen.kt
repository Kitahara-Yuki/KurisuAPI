package com.kurisuapi.ui.screen.wechat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.domain.bridge.ConnectionState
import com.kurisuapi.ui.viewmodel.WeChatViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeChatLoginScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeChatViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val qrCodeData by viewModel.qrCodeData.collectAsState()
    val loginStatus by viewModel.loginStatus.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接微信", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(sdp(24.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sdp(24.dp))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (connectionState) {
                        ConnectionState.POLLING, ConnectionState.LOGGED_IN ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ConnectionState.ERROR ->
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        else ->
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    }
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(sdp(16.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.DISCONNECTED -> "未连接"
                            ConnectionState.CONNECTING -> "连接中..."
                            ConnectionState.WAITING_SCAN -> "等待扫码"
                            ConnectionState.SCANED -> "已扫码"
                            ConnectionState.LOGGED_IN -> "已登录"
                            ConnectionState.POLLING -> "已连接"
                            ConnectionState.ERROR -> "连接错误"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (loginStatus != null) {
                        Spacer(modifier = Modifier.height(sdp(4.dp)))
                        Text(
                            text = loginStatus ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(sdp(12.dp)).fillMaxWidth()) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) { Text("关闭") }
                    }
                }
            }

            when (connectionState) {
                ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                    Text(
                        text = "使用微信扫码连接\nAI将通过微信与你聊天",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = { viewModel.startLogin() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("获取二维码") }
                }
                ConnectionState.WAITING_SCAN, ConnectionState.SCANED -> {
                    qrCodeData?.let { qrData ->
                        QrCodeFromText(text = qrData.qrcodeImgContent, modifier = Modifier.size(sdp(256.dp)))
                    }
                    if (connectionState == ConnectionState.SCANED) {
                        Text("已扫码，请在手机上确认登录",
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.startLogin() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(sdp(8.dp)))
                        Text("刷新二维码")
                    }
                }
                ConnectionState.LOGGED_IN, ConnectionState.POLLING -> {
                    Text("微信已连接！", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "AI正在通过微信接收和回复消息",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(sdp(16.dp)))
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("断开连接") }
                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                    TextButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
                }
                else -> { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun QrCodeFromText(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        try {
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width; val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) for (x in 0 until width)
                pixels[y * width + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        Card(modifier = modifier, shape = MaterialTheme.shapes.medium) {
            Image(bitmap = bitmap, contentDescription = "微信登录二维码",
                modifier = Modifier.fillMaxSize().padding(sdp(16.dp)), contentScale = ContentScale.Fit)
        }
    } else {
        QrCodeErrorPlaceholder(modifier)
    }
}

@Composable
private fun QrCodeErrorPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("二维码加载失败，请点击下方刷新", color = MaterialTheme.colorScheme.error)
    }
}
