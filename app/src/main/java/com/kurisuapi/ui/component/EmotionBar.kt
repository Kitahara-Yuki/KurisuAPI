package com.kurisuapi.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kurisuapi.util.sdp

@Composable
fun EmotionBar(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val progress = animateFloatAsState(targetValue = (value / 100f).coerceIn(0f, 1f), label = "emotion_$label")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(sdp(48.dp))
        )
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier
                .weight(1f)
                .height(sdp(8.dp))
                .clip(RoundedCornerShape(sdp(4.dp))),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(sdp(36.dp)),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
