package com.kurisuapi.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.ui.screen.character.CharacterEditScreen
import com.kurisuapi.ui.screen.character.CharacterListScreen
import com.kurisuapi.ui.screen.chat.ChatLogScreen
import com.kurisuapi.ui.screen.chat.ConversationListScreen
import com.kurisuapi.ui.screen.emotion.EmotionDetailScreen
import com.kurisuapi.ui.screen.home.HomeScreen
import com.kurisuapi.ui.screen.memory.MemoryListScreen
import com.kurisuapi.ui.screen.relationship.RelationshipDetailScreen
import com.kurisuapi.ui.screen.settings.ProviderEditScreen
import com.kurisuapi.ui.screen.settings.ProviderListScreen
import com.kurisuapi.ui.screen.settings.SettingsScreen
import com.kurisuapi.ui.screen.settings.SystemSettingsScreen
import com.kurisuapi.ui.screen.wechat.WeChatLoginScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    settingsRepository: SettingsRepository = hiltViewModel<MainScreenViewModel>().settingsRepository
) {
    val navController = rememberNavController()
    val pagerState = rememberPagerState(initialPage = 0) { TabItem.entries.size }
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute == null || currentRoute == "tab_main"
    val showPager = showBottomBar

    val activeCharacterId by settingsRepository.observeValue(SettingsRepository.KEY_ACTIVE_CHARACTER)
        .collectAsState(initial = null)
    val activeCharId = activeCharacterId?.toLongOrNull() ?: 0L

    Scaffold(
        contentWindowInsets = WindowInsets(0), // let inner pages handle status bar insets
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 0.dp
                    ) {
                        TabItem.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = pagerState.currentPage == tab.index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(tab.index)
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                label = { Text(tab.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (showPager) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { page ->
                    when (TabItem.fromIndex(page)) {
                        TabItem.HOME -> HomeScreen(
                            onNavigate = { route -> navController.navigate(route) }
                        )
                        TabItem.CHARACTER -> CharacterListScreen(
                            onNavigate = { route -> navController.navigate(route) },
                            onNavigateBack = { }
                        )
                        TabItem.MEMORY -> MemoryListScreen(
                            characterId = activeCharId,
                            onNavigateBack = { }
                        )
                        TabItem.CHAT_LOG -> ConversationListScreen(
                            characterId = activeCharId,
                            onNavigateBack = { },
                            onNavigateToChat = { sessionId ->
                                navController.navigate(Screen.ChatLogDetail.createRoute(sessionId))
                            }
                        )
                        TabItem.SETTINGS -> SettingsScreen(
                            onNavigate = { route -> navController.navigate(route) }
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = "tab_main",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("tab_main") { }
                composable(
                    route = Screen.CharacterEdit.route,
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: -1L
                    CharacterEditScreen(
                        onNavigateBack = { navController.popBackStack() },
                        characterId = if (characterId == -1L) null else characterId
                    )
                }
                composable(
                    route = "memory_list/{characterId}",
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    MemoryListScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "emotion_detail/{characterId}",
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    EmotionDetailScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "relationship_detail/{characterId}",
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    RelationshipDetailScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "conversation_list/{characterId}",
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    ConversationListScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToChat = { sessionId ->
                            navController.navigate(Screen.ChatLogDetail.createRoute(sessionId))
                        }
                    )
                }
                composable(
                    route = "chat_log/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                ) { entry ->
                    val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                    ChatLogScreen(
                        sessionId = sessionId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.SystemSettings.route) {
                    SystemSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.WeChatLogin.route) {
                    WeChatLoginScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("provider_list") {
                    ProviderListScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }
                composable(
                    route = "provider_edit/{providerId}",
                    arguments = listOf(navArgument("providerId") { type = NavType.LongType })
                ) { entry ->
                    val providerId = entry.arguments?.getLong("providerId") ?: 0L
                    ProviderEditScreen(
                        providerId = providerId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
