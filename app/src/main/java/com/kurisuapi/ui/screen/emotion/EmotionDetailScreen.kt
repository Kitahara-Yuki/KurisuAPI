package com.kurisuapi.ui.screen.emotion

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.ui.component.EmotionBar
import com.kurisuapi.ui.theme.*
import com.kurisuapi.ui.viewmodel.EmotionDetailViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionDetailScreen(
    characterId: Long,
    onNavigateBack: () -> Unit,
    viewModel: EmotionDetailViewModel = hiltViewModel()
) {
    val emotion by viewModel.emotion.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("情绪状态", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        val currentEmotion = emotion
        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentEmotion != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(sdp(16.dp)),
                verticalArrangement = Arrangement.spacedBy(sdp(16.dp))
            ) {
                Text("当前情绪", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp)), verticalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                        EmotionBar(label = "开心", value = currentEmotion.happy, color = AppleGreen)
                        EmotionBar(label = "难过", value = currentEmotion.sad, color = AppleBlue)
                        EmotionBar(label = "生气", value = currentEmotion.angry, color = AppleRed)
                        EmotionBar(label = "孤独", value = currentEmotion.lonely, color = AppleIndigo)
                        EmotionBar(label = "好感", value = currentEmotion.affection, color = ApplePink)
                    }
                }

                Text("情绪说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        Text("• 情绪会在每次聊天时动态变化")
                        Text("• 积极的对话会提升开心和好感")
                        Text("• 消极的对话会提升难过和生气")
                        Text("• 长时间不聊天会增加孤独感")
                        Text("• 所有情绪会自然向中性值回归")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无情绪数据", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                    Text("与角色开始聊天后会自动生成", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        }
    }
}
