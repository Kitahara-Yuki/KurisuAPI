package com.kurisuapi.ui.screen.relationship

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
import com.kurisuapi.domain.engine.RelationshipEngine
import com.kurisuapi.ui.viewmodel.RelationshipDetailViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipDetailScreen(
    characterId: Long,
    onNavigateBack: () -> Unit,
    viewModel: RelationshipDetailViewModel = hiltViewModel()
) {
    val relationship by viewModel.relationship.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关系状态", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        val currentRelationship = relationship
        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentRelationship != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(sdp(16.dp)),
                verticalArrangement = Arrangement.spacedBy(sdp(16.dp))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(sdp(24.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("当前关系", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currentRelationship.level,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(sdp(8.dp)))
                        Text("关系值: ${currentRelationship.score}", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(sdp(12.dp)))
                        LinearProgressIndicator(
                            progress = { (currentRelationship.score / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(sdp(8.dp)),
                        )
                    }
                }

                Text("关系等级", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        RelationshipEngine.LEVELS.forEach { (level, threshold) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = sdp(4.dp)),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = level,
                                    fontWeight = if (currentRelationship.level == level) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (currentRelationship.score >= threshold) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "${threshold}+",
                                    color = if (currentRelationship.score >= threshold) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                Text("说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        Text("• 每次聊天都会增加关系值")
                        Text("• 深入的对话增加更多关系值")
                        Text("• 高等级时增长速度减缓")
                        Text("• 关系等级影响AI回复的亲密程度")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无关系数据", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                    Text("与角色开始聊天后会自动生成", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
        }
    }
}
