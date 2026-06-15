package com.kurisuapi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.kurisuapi.ui.navigation.MainScreen
import com.kurisuapi.ui.theme.KurisuAPITheme
import com.kurisuapi.util.LocalScreenScale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KurisuAPITheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        // 以 360dp 为基准宽度计算缩放比例，限制在 0.85 ~ 1.2 之间
                        val screenWidthDp = maxWidth
                        val density = LocalDensity.current
                        val widthInDp = screenWidthDp.value
                        val scale = (widthInDp / 360f).coerceIn(0.85f, 1.2f)

                        CompositionLocalProvider(LocalScreenScale provides scale) {
                            MainScreen()
                        }
                    }
                }
            }
        }
    }
}
