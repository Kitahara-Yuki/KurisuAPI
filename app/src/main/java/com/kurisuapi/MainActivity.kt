package com.kurisuapi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.ui.navigation.MainScreen
import com.kurisuapi.ui.navigation.MainScreenViewModel
import com.kurisuapi.ui.theme.KurisuAPITheme
import com.kurisuapi.util.LocalScreenScale

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm = hiltViewModel<MainScreenViewModel>()
            val settingsRepo = vm.settingsRepository
            val themeRepo = vm.themeRepository

            // 首次启动初始化内置主题
            LaunchedEffect(Unit) { themeRepo.initializeIfNeeded() }

            val darkMode by settingsRepo
                .observeValue(SettingsRepository.KEY_THEME_DARK_MODE)
                .collectAsState(initial = null)
            val activeTheme by themeRepo.observeActive()
                .collectAsState(initial = null)

            val isDark = when (darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            KurisuAPITheme(
                darkTheme = isDark,
                activeTheme = activeTheme,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val configWidth = LocalConfiguration.current.screenWidthDp.toFloat()
                        val scale = (configWidth / 360f).coerceIn(0.85f, 1.2f)
                        CompositionLocalProvider(LocalScreenScale provides scale) {
                            MainScreen()
                        }
                    }
                }
            }
        }
    }
}
