package com.kurisuapi.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurisuapi.R
import kotlinx.coroutines.delay

/**
 * 牧濑红莉栖彩蛋 — 辉光管全演出（无光晕重叠）。
 */
@Composable
fun SteinsGateEasterEgg(onComplete: () -> Unit) {
    val targetText = "这一切都是命运石之门的选择"
    val nixieOrange = Color(0xFFFF6B35)
    val nixieWarm = Color(0xFFFF8C5A)

    val nixieFont = remember { FontFamily(Font(R.font.nixieone_regular)) }
    val hanchanFont = remember { FontFamily(Font(R.font.hanchan_medium)) }

    // —— 状态 ——
    var bgPhase by remember { mutableStateOf(0f) }
    var worldLinePhase by remember { mutableStateOf(0f) }
    var flickerSpeedMs by remember { mutableStateOf(0L) }
    var stableCount by remember { mutableStateOf(0) }
    var fadePhase by remember { mutableStateOf(-1f) }

    val displayChars = remember {
        val list = mutableStateListOf<Char>()
        repeat(targetText.length) { list.add(' ') }
        list
    }

    val bgAlpha by animateFloatAsState(bgPhase, tween(1500), label = "bgAlpha")
    val worldLineAlpha by animateFloatAsState(worldLinePhase, tween(1000), label = "wlAlpha")

    val randomPool = remember {
        "0123456789" +
        "abcdefghijklmnopqrstuvwxyz" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "!@#$%&*+-/=<>?"
    }

    LaunchedEffect(Unit) {
        bgPhase = 1f
        delay(1200)
        worldLinePhase = 0.35f

        flickerSpeedMs = 50
        delay(1800)

        flickerSpeedMs = 130
        delay(1500)

        flickerSpeedMs = 0
        for (i in targetText.indices) {
            stableCount = i + 1
            displayChars[i] = targetText[i]
            delay(120)
        }

        delay(2500)

        for (i in targetText.indices) {
            fadePhase = i.toFloat() / targetText.length
            delay(120)
        }
        fadePhase = 1f
        delay(400)

        bgPhase = 0f
        worldLinePhase = 0f
        delay(1200)

        onComplete()
    }

    LaunchedEffect(flickerSpeedMs) {
        if (flickerSpeedMs <= 0) return@LaunchedEffect
        while (flickerSpeedMs > 0) {
            for (i in stableCount until targetText.length) {
                displayChars[i] = randomPool.random()
            }
            delay(flickerSpeedMs)
        }
    }

    val bgBrush = remember {
        Brush.radialGradient(
            colors = listOf(Color(0xFF0D0D0D), Color(0xFF16261C), Color(0xFF0D0D0D)),
            center = Offset(0.5f, 0.5f),
            radius = 1.2f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(bgAlpha)
            .background(bgBrush)
    ) {
        // 世界线数字
        if (worldLineAlpha > 0.01f) {
            Text(
                text = "1.048596%",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .alpha(worldLineAlpha),
                color = nixieOrange.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontFamily = nixieFont,
                letterSpacing = 6.sp
            )
        }

        // 主体文字 — 单层，无光晕
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buildAnnotated(
                    targetText, stableCount, fadePhase, displayChars,
                    nixieWarm, Color.White, hanchanFont, nixieFont
                ),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun buildAnnotated(
    targetText: String,
    stableCount: Int,
    fadePhase: Float,
    displayChars: List<Char>,
    kurisuColor: Color,
    normalColor: Color,
    stableFont: FontFamily,
    flickerFont: FontFamily
) = buildAnnotatedString {
    for (i in targetText.indices) {
        val isStable = i < stableCount
        val char = if (isStable) targetText[i] else displayChars[i]
        val isKurisu = i in 5..9

        val charFade = if (fadePhase >= 0f) {
            val start = i.toFloat() / targetText.length
            (1f - ((fadePhase - start) / 0.12f).coerceIn(0f, 1f))
        } else 1f

        val color = (if (isKurisu) kurisuColor else normalColor).copy(alpha = charFade)
        val font = if (isStable) stableFont else flickerFont

        withStyle(SpanStyle(color = color, fontFamily = font)) { append(char) }
    }
}
