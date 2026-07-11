package com.kurisuapi.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kurisuapi.data.entity.ChatHistoryEntity
import com.kurisuapi.ui.theme.LocalActiveTheme
import com.kurisuapi.ui.theme.LocalIsDarkTheme
import com.kurisuapi.util.parseColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.kurisuapi.util.sdp

@Composable
fun ChatBubble(
    message: ChatHistoryEntity,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val isUser = message.sender == "user"
    val theme = LocalActiveTheme.current

    val userBubbleColor = theme?.bubbleUserColorHex?.parseColor()
    val aiBubbleColor = theme?.bubbleAiColorHex?.parseColor()

    val bubbleColor = if (isUser) {
        userBubbleColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        aiBubbleColor
            ?: if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val borderColor = if (isUser) {
        (userBubbleColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isUser) {
        RoundedCornerShape(sdp(18.dp), sdp(18.dp), sdp(6.dp), sdp(18.dp))
    } else {
        RoundedCornerShape(sdp(18.dp), sdp(18.dp), sdp(18.dp), sdp(6.dp))
    }

    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val timeText = remember(message.timestamp) {
        try {
            Instant.ofEpochMilli(message.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(timeFormat)
        } catch (e: Exception) { "" }
    }

    var thinkingExpanded by rememberSaveable { mutableStateOf(false) }
    val hasThinking = !isUser && message.reasoningContent.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // 思考过程（可折叠），仅 AI 消息且包含 reasoningContent 时显示
        if (hasThinking) {
            Box(
                modifier = Modifier
                    .widthIn(max = sdp(280.dp))
                    .padding(bottom = sdp(4.dp))
                    .clip(RoundedCornerShape(sdp(14.dp)))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(sdp(14.dp))
                    )
                    .clickable { thinkingExpanded = !thinkingExpanded }
                    .padding(horizontal = sdp(12.dp), vertical = sdp(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💭 思考过程",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (thinkingExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (thinkingExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(sdp(16.dp))
                    )
                }
            }

            AnimatedVisibility(
                visible = thinkingExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = sdp(280.dp))
                        .padding(bottom = sdp(6.dp))
                        .clip(RoundedCornerShape(sdp(14.dp)))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(sdp(14.dp))
                        )
                        .padding(sdp(12.dp))
                ) {
                    Text(
                        text = message.reasoningContent,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = sdp(280.dp))
                .clip(shape)
                .background(bubbleColor)
                .border(0.5.dp, borderColor, shape)
                .padding(sdp(12.dp))
        ) {
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(sdp(2.dp)))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
