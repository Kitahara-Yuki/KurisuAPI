package com.kurisuapi.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import com.kurisuapi.data.repository.ThemeRepository
import com.kurisuapi.ui.screen.character.CharacterEditScreen
import com.kurisuapi.ui.screen.character.CharacterGenerateScreen
import com.kurisuapi.ui.screen.character.CharacterListScreen
import com.kurisuapi.ui.screen.chat.ChatLogScreen
import com.kurisuapi.ui.screen.chat.ConversationListScreen
import com.kurisuapi.ui.screen.emotion.EmotionDetailScreen
import com.kurisuapi.ui.screen.home.HomeScreen
import com.kurisuapi.ui.screen.diary.DiaryListScreen
import com.kurisuapi.ui.screen.memory.MemoryListScreen
import com.kurisuapi.ui.screen.relationship.RelationshipDetailScreen
import com.kurisuapi.ui.screen.settings.ProviderEditScreen
import com.kurisuapi.ui.screen.settings.ProviderListScreen
import com.kurisuapi.ui.screen.settings.SettingsScreen
import com.kurisuapi.ui.screen.settings.LogViewerScreen
import com.kurisuapi.ui.screen.settings.SystemSettingsScreen
import com.kurisuapi.ui.screen.profile.ProfileEditScreen
import com.kurisuapi.ui.screen.profile.ProfileScreen
import com.kurisuapi.ui.screen.profile.ThemeConfigScreen
import com.kurisuapi.ui.screen.theme.ThemeEditorScreen
import com.kurisuapi.ui.screen.theme.ThemeListScreen
import com.kurisuapi.ui.viewmodel.ProfileViewModel
import com.kurisuapi.ui.screen.wechat.WeChatLoginScreen
import com.kurisuapi.ui.component.EulaDialog
import com.kurisuapi.ui.component.PermissionSetupDialog
import com.kurisuapi.util.sdp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    settingsRepository: SettingsRepository = hiltViewModel<MainScreenViewModel>().settingsRepository,
    themeRepository: ThemeRepository = hiltViewModel<MainScreenViewModel>().themeRepository,
) {
    val navController = rememberNavController()
    val pagerState = rememberPagerState(initialPage = 0) { TabItem.entries.size }
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val profileViewModel: ProfileViewModel = hiltViewModel()

    val showBottomBar = currentRoute == null || currentRoute == "tab_main"
    val showPager = showBottomBar

    val activeCharacterId by settingsRepository.observeValue(SettingsRepository.KEY_ACTIVE_CHARACTER)
        .collectAsState(initial = null)
    val activeCharId = activeCharacterId?.toLongOrNull() ?: 0L

    // 用户协议（首次启动强制同意，不同意则退出）
    var showEula by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val accepted = settingsRepository.getValue(SettingsRepository.KEY_EULA_ACCEPTED)
        if (accepted != "true") {
            showEula = true
        }
    }

    // 首次启动权限引导
    var showPermissionSetup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val shown = settingsRepository.getValue(SettingsRepository.KEY_PERMISSION_SETUP_SHOWN)
        if (shown != "true") {
            showPermissionSetup = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0), // let inner pages handle status bar insets
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sdp(16.dp))
                        .padding(top = sdp(8.dp), bottom = sdp(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // 胶囊型液态玻璃容器
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(sdp(28.dp)),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        val tabPositions = remember { mutableStateMapOf<Int, Float>() }
                        val tabWidths = remember { mutableStateMapOf<Int, Float>() }
                        val tabHeights = remember { mutableStateMapOf<Int, Float>() }

                        val currentPage = pagerState.currentPage
                        val offsetFraction = pagerState.currentPageOffsetFraction
                        val targetX = (tabPositions[currentPage] ?: 0f) +
                            (offsetFraction * (tabWidths[currentPage] ?: 0f))
                        val targetW = tabWidths[currentPage] ?: 0f
                        val targetH = tabHeights[currentPage] ?: 0f

                        // 弹簧物理动画
                        val indicatorX by animateFloatAsState(
                            targetValue = targetX,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f)
                        )
                        val indicatorW by animateFloatAsState(
                            targetValue = targetW,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f)
                        )

                        val density = LocalDensity.current

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = sdp(8.dp), vertical = sdp(6.dp))
                        ) {
                            // 滑动指示器 pill（z轴在文字下方）
                            if (indicatorW > 0f && targetH > 0f) {
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { indicatorX.toDp() },
                                            y = with(density) { 0.dp }
                                        )
                                        .width(with(density) { indicatorW.toDp() })
                                        .height(with(density) { targetH.toDp() })
                                        .clip(RoundedCornerShape(
                                            with(density) { (targetH / 2f).toDp() }
                                        ))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                )
                            }

                            // 标签项（盖在指示器上方）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TabItem.entries.forEach { tab ->
                                    val selected = currentPage == tab.index

                                    val iconColor by animateColorAsState(
                                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                                    )
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (selected) 1.05f else 1.0f,
                                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(tab.index)
                                                }
                                            }
                                            .onGloballyPositioned { coords ->
                                                tabPositions[tab.index] = coords.positionInParent().x
                                                tabWidths[tab.index] = coords.size.width.toFloat()
                                                tabHeights[tab.index] = coords.size.height.toFloat()
                                            }
                                            .padding(vertical = sdp(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(sdp(2.dp))
                                        ) {
                                            Icon(
                                                tab.icon,
                                                contentDescription = tab.title,
                                                modifier = Modifier
                                                    .size(sdp(22.dp))
                                                    .scale(iconScale),
                                                tint = iconColor
                                            )
                                            Text(
                                                tab.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = iconColor
                                            )
                                        }
                                    }
                                }
                            }
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
                        TabItem.DIARY -> {
                            if (activeCharId > 0L) {
                                DiaryListScreen(
                                    characterId = activeCharId,
                                    onNavigateBack = { }
                                )
                            } else {
                                NoCharacterHint()
                            }
                        }
                        TabItem.CHAT_LOG -> {
                            if (activeCharId > 0L) {
                                ConversationListScreen(
                                    characterId = activeCharId,
                                    onNavigateBack = { },
                                    onNavigateToChat = { sessionId ->
                                        navController.navigate(Screen.ChatLogDetail.createRoute(sessionId))
                                    }
                                )
                            } else {
                                NoCharacterHint()
                            }
                        }
                        TabItem.PROFILE -> ProfileScreen(
                            onNavigate = { route -> navController.navigate(route) },
                            viewModel = profileViewModel
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
                composable(Screen.CharacterList.route) {
                    CharacterListScreen(
                        onNavigate = { route -> navController.navigate(route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
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
                composable(Screen.CharacterGenerate.route) {
                    CharacterGenerateScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onGenerated = { newId ->
                            navController.popBackStack()
                            navController.navigate(Screen.CharacterEdit.createRoute(newId))
                        }
                    )
                }
                composable(
                    route = Screen.DiaryList.route,
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    DiaryListScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.MemoryList.route,
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    MemoryListScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.EmotionDetail.route,
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    EmotionDetailScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.RelationshipDetail.route,
                    arguments = listOf(navArgument("characterId") { type = NavType.LongType })
                ) { entry ->
                    val characterId = entry.arguments?.getLong("characterId") ?: 0L
                    RelationshipDetailScreen(
                        characterId = characterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.ConversationList.route,
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
                    route = Screen.ChatLogDetail.route,
                    arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                ) { entry ->
                    val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
                    ChatLogScreen(
                        sessionId = sessionId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.ProfileEdit.route) {
                    ProfileEditScreen(
                        onNavigateBack = { navController.popBackStack() },
                        viewModel = profileViewModel
                    )
                }
                composable(Screen.ThemeConfig.route) {
                    ThemeConfigScreen(
                        settingsRepository = settingsRepository,
                        themeRepository = themeRepository,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCustomThemes = {
                            navController.navigate(Screen.ThemeList.route)
                        }
                    )
                }
                composable(Screen.ThemeList.route) {
                    ThemeListScreen(
                        themeRepository = themeRepository,
                        onNavigateBack = { navController.popBackStack() },
                        onEditTheme = { id ->
                            navController.navigate(Screen.ThemeEdit.createRoute(id))
                        }
                    )
                }
                composable(
                    Screen.ThemeEdit.route,
                    arguments = listOf(navArgument("themeId") { type = NavType.LongType; defaultValue = -1L })
                ) { backStackEntry ->
                    val themeId = backStackEntry.arguments?.getLong("themeId") ?: -1L
                    ThemeEditorScreen(
                        themeId = themeId,
                        themeRepository = themeRepository,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigate = { route -> navController.navigate(route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.SystemSettings.route) {
                    SystemSettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.LogViewer.route) {
                    LogViewerScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.WeChatLogin.route) {
                    WeChatLoginScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.ProviderList.route) {
                    ProviderListScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }
                composable(
                    route = Screen.ProviderEdit.route,
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

    // 用户协议弹窗（在权限引导之前，不同意则退出）
    if (showEula) {
        EulaDialog(
            onAccept = {
                showEula = false
                coroutineScope.launch {
                    settingsRepository.setValue(SettingsRepository.KEY_EULA_ACCEPTED, "true")
                }
            },
            onExit = { /* finishAffinity 已在 EulaDialog 中调用 */ }
        )
    }

    // 首次启动权限引导弹窗
    if (showPermissionSetup) {
        PermissionSetupDialog(
            onDismiss = {
                showPermissionSetup = false
                coroutineScope.launch {
                    settingsRepository.setValue(
                        SettingsRepository.KEY_PERMISSION_SETUP_SHOWN, "true"
                    )
                }
            }
        )
    }
}

@Composable
private fun NoCharacterHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("请先在首页选择角色", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
