package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.components.PinLockGateScreen
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.navigation.AiRoute
import com.example.ui.navigation.CompressSecurityRoute
import com.example.ui.navigation.EditorRoute
import com.example.ui.navigation.FilesRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.ImageToPdfRoute
import com.example.ui.navigation.MergeSplitOrganizeRoute
import com.example.ui.navigation.OcrToolRoute
import com.example.ui.navigation.OfficeConverterRoute
import com.example.ui.navigation.ReaderRoute
import com.example.ui.navigation.ScannerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.ToolsRoute
import com.example.ui.navigation.WebAndNotesToPdfRoute
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.tools.CompressSecurityScreen
import com.example.ui.screens.tools.ImageToPdfScreen
import com.example.ui.screens.tools.MergeSplitOrganizeScreen
import com.example.ui.screens.tools.OcrToolScreen
import com.example.ui.screens.tools.OfficeConverterScreen
import com.example.ui.screens.tools.ScannerScreen
import com.example.ui.screens.tools.WebAndNotesToPdfScreen
import com.example.ui.theme.DocuVerseTheme
import com.example.ui.theme.PrimaryBrandGradient
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.AiViewModel
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.FileFilterOption
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.ReaderViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.ToolsViewModel
import kotlinx.coroutines.delay

private data class BottomNavTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DocuVerseApp
        val container = app.container

        setContent {
            val libraryVm: LibraryViewModel = viewModel(
                factory = LibraryViewModel.Factory(
                    applicationContext,
                    container.documentRepository,
                    container.preferencesRepository,
                    container.vaultSecurityManager
                )
            )
            val toolsVm: ToolsViewModel = viewModel(
                factory = ToolsViewModel.Factory(
                    container.documentRepository,
                    container.pdfEngine,
                    container.officeConverterEngine,
                    container.ocrEngine
                )
            )
            val aiVm: AiViewModel = viewModel(
                factory = AiViewModel.Factory(
                    container.documentRepository,
                    container.pdfEngine,
                    container.aiRepository
                )
            )
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    applicationContext,
                    container.preferencesRepository,
                    container.documentRepository,
                    container.vaultSecurityManager,
                    container.cloudSyncRepository
                )
            )

            val prefs by libraryVm.userPreferences.collectAsStateWithLifecycle()
            val libState by libraryVm.uiState.collectAsStateWithLifecycle()
            val strings = remember(prefs.language) { stringsForLanguage(prefs.language) }

            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(750)
                showSplash = false
            }

            CompositionLocalProvider(LocalAppStrings provides strings) {
                DocuVerseTheme(
                    themeMode = prefs.themeMode,
                    isBengali = prefs.language == AppLanguage.BENGALI
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            !prefs.onboardingCompleted && !showSplash -> {
                                OnboardingScreen(onFinish = { libraryVm.completeOnboarding() })
                            }
                            prefs.isAppLockEnabled && !libState.isAppUnlocked && !showSplash -> {
                                PinLockGateScreen(
                                    title = "Unlock DocuVerse",
                                    biometricAvailable = prefs.biometricEnabled && container.vaultSecurityManager.isBiometricAvailable(),
                                    onVerifyPin = { libraryVm.verifyPin(it) },
                                    onRequestBiometric = {
                                        container.vaultSecurityManager.showBiometricPrompt(
                                            activity = this@MainActivity,
                                            title = "DocuVerse Security",
                                            subtitle = "Authenticate to access your documents",
                                            negativeText = "Use PIN",
                                            onSuccess = { libraryVm.unlockApp() },
                                            onError = {}
                                        )
                                    },
                                    onUnlocked = { libraryVm.unlockApp() }
                                )
                            }
                            else -> {
                                DocuVerseRootShell(
                                    activity = this@MainActivity,
                                    container = container,
                                    libraryVm = libraryVm,
                                    toolsVm = toolsVm,
                                    aiVm = aiVm,
                                    settingsVm = settingsVm,
                                    initialIntent = intent
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut()
                        ) {
                            SplashOverlay()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryBrandGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_art),
                contentDescription = "DocuVerse Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "DocuVerse",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Everything You Need for Documents.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun DocuVerseRootShell(
    activity: FragmentActivity,
    container: com.example.di.AppContainer,
    libraryVm: LibraryViewModel,
    toolsVm: ToolsViewModel,
    aiVm: AiViewModel,
    settingsVm: SettingsViewModel,
    initialIntent: Intent?
) {
    val strings = LocalAppStrings.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val libState by libraryVm.uiState.collectAsStateWithLifecycle()
    val prefs by libraryVm.userPreferences.collectAsStateWithLifecycle()
    val allDocs by libraryVm.allDocuments.collectAsStateWithLifecycle()
    val recentDocs by libraryVm.recentDocuments.collectAsStateWithLifecycle()
    val starredDocs by libraryVm.starredDocuments.collectAsStateWithLifecycle()
    val filteredDocs by libraryVm.filteredDocuments.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsVm.uiState.collectAsStateWithLifecycle()

    val safImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            libraryVm.importUri(uri) { doc ->
                navController.navigate(ReaderRoute(doc.id))
            }
        }
    }

    fun triggerImport() {
        safImporter.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain"
            )
        )
    }

    LaunchedEffect(initialIntent) {
        if (initialIntent?.action == Intent.ACTION_VIEW) {
            initialIntent.data?.let { uri ->
                libraryVm.importUri(uri) { doc ->
                    navController.navigate(ReaderRoute(doc.id))
                }
            }
        }
    }

    LaunchedEffect(libState.statusMessage) {
        libState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            libraryVm.clearStatusMessage()
        }
    }
    LaunchedEffect(settingsState.statusMessage) {
        settingsState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            settingsVm.clearStatus()
        }
    }
    LaunchedEffect(toolsState.statusMessage) {
        toolsState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            toolsVm.clearStatusMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevel = currentDest?.let { d ->
        d.hasRoute<HomeRoute>() ||
            d.hasRoute<FilesRoute>() ||
            d.hasRoute<ToolsRoute>() ||
            d.hasRoute<AiRoute>() ||
            d.hasRoute<SettingsRoute>()
    } ?: true

    val tabs = listOf(
        BottomNavTab(HomeRoute, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavTab(FilesRoute, strings.navFiles, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_files"),
        BottomNavTab(ToolsRoute, strings.navTools, Icons.Filled.Build, Icons.Outlined.Build, "nav_tools"),
        BottomNavTab(AiRoute, strings.navAi, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai"),
        BottomNavTab(SettingsRoute, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isTopLevel) innerPadding else WindowInsets(0).let { innerPadding })
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight().statusBarsPadding()) {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            allDocuments = allDocs,
                            recentDocuments = recentDocs,
                            starredDocuments = starredDocs,
                            storageUsedBytes = libState.storageUsedBytes,
                            searchQuery = libState.searchQuery,
                            themeMode = prefs.themeMode,
                            onSearchChange = { libraryVm.setSearchQuery(it) },
                            onToggleThemeQuick = {
                                val next = if (prefs.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                                settingsVm.setThemeMode(next)
                            },
                            onImportClick = { triggerImport() },
                            onOpenDocument = { doc -> navController.navigate(ReaderRoute(doc.id)) },
                            onEditDocument = { doc -> navController.navigate(EditorRoute(doc.id)) },
                            onToggleStar = { libraryVm.toggleStar(it) },
                            onRenameDocument = { d, title -> libraryVm.renameDocument(d, title) },
                            onShareDocument = { libraryVm.shareDocument(it) },
                            onMoveToVault = { libraryVm.moveToVault(it) },
                            onDeleteDocument = { libraryVm.deleteDocument(it) },
                            onOpenScanner = { navController.navigate(ScannerRoute) },
                            onOpenImageToPdf = { navController.navigate(ImageToPdfRoute) },
                            onOpenMerge = { navController.navigate(MergeSplitOrganizeRoute("merge")) },
                            onOpenOcr = { navController.navigate(OcrToolRoute) },
                            onOpenWebAndNotes = { navController.navigate(WebAndNotesToPdfRoute) },
                            onOpenAiTab = { navigateTopTab(AiRoute) },
                            onOpenVaultFilter = {
                                libraryVm.setFilterOption(FileFilterOption.VAULT)
                                navigateTopTab(FilesRoute)
                            }
                        )
                    }

                    composable<FilesRoute> {
                        FilesScreen(
                            documents = filteredDocs,
                            searchQuery = libState.searchQuery,
                            filterOption = libState.filterOption,
                            sortOption = libState.sortOption,
                            isGridView = libState.isGridView,
                            duplicateGroupsCount = libState.duplicateGroupsCount,
                            isVaultUnlocked = libState.isVaultUnlocked,
                            isPinConfigured = prefs.isAppLockEnabled,
                            biometricAvailable = prefs.biometricEnabled && container.vaultSecurityManager.isBiometricAvailable(),
                            onSearchChange = { libraryVm.setSearchQuery(it) },
                            onFilterChange = { libraryVm.setFilterOption(it) },
                            onSortChange = { libraryVm.setSortOption(it) },
                            onToggleGridView = { libraryVm.toggleGridView() },
                            onImportClick = { triggerImport() },
                            onCleanDuplicates = { libraryVm.removeDuplicatesKeepNewest() },
                            onVerifyPin = { libraryVm.verifyPin(it) },
                            onRequestBiometric = {
                                container.vaultSecurityManager.showBiometricPrompt(
                                    activity = activity,
                                    title = "Private Vault",
                                    subtitle = "Authenticate to unlock your AES-256 vault",
                                    negativeText = "Use PIN",
                                    onSuccess = { libraryVm.unlockVault() },
                                    onError = {}
                                )
                            },
                            onVaultUnlocked = { libraryVm.unlockVault() },
                            onOpenDocument = { doc -> navController.navigate(ReaderRoute(doc.id)) },
                            onEditDocument = { doc -> navController.navigate(EditorRoute(doc.id)) },
                            onToggleStar = { libraryVm.toggleStar(it) },
                            onRenameDocument = { d, title -> libraryVm.renameDocument(d, title) },
                            onShareDocument = { libraryVm.shareDocument(it) },
                            onToggleVault = { doc ->
                                if (doc.isVaulted) libraryVm.restoreFromVault(doc)
                                else libraryVm.moveToVault(doc)
                            },
                            onDeleteDocument = { libraryVm.deleteDocument(it) }
                        )
                    }

                    composable<ToolsRoute> {
                        ToolsScreen(
                            onOpenScanner = { navController.navigate(ScannerRoute) },
                            onOpenImageToPdf = { navController.navigate(ImageToPdfRoute) },
                            onOpenOfficeConverter = { navController.navigate(OfficeConverterRoute) },
                            onOpenWebAndNotes = { navController.navigate(WebAndNotesToPdfRoute) },
                            onOpenMerge = { navController.navigate(MergeSplitOrganizeRoute("merge")) },
                            onOpenSplit = { navController.navigate(MergeSplitOrganizeRoute("split")) },
                            onOpenOrganize = { navController.navigate(MergeSplitOrganizeRoute("organize")) },
                            onOpenCompress = { navController.navigate(CompressSecurityRoute("compress")) },
                            onOpenWatermark = { navController.navigate(CompressSecurityRoute("watermark")) },
                            onOpenProtect = { navController.navigate(CompressSecurityRoute("protect")) },
                            onOpenOcr = { navController.navigate(OcrToolRoute) }
                        )
                    }

                    composable<AiRoute> {
                        AiAssistantScreen(
                            viewModel = aiVm,
                            language = prefs.language,
                            onOpenPageInReader = { docId, page ->
                                navController.navigate(ReaderRoute(docId, page))
                            }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            prefs = prefs,
                            uiState = settingsState,
                            onSetTheme = { settingsVm.setThemeMode(it) },
                            onSetLanguage = { settingsVm.setLanguage(it) },
                            onSetDefaultViewMode = { settingsVm.setDefaultViewMode(it) },
                            onSetDefaultReaderBg = { settingsVm.setDefaultReaderBg(it) },
                            onSetReflowDefaults = { fs, sp, al -> settingsVm.setReflowDefaults(fs, sp, al) },
                            onSetAppPin = { settingsVm.setAppPin(it) },
                            onSetBiometric = { settingsVm.setBiometricEnabled(it) },
                            onClearCache = { settingsVm.clearCache() },
                            onConnectCloud = { p, id -> settingsVm.launchCloudOAuth(p, id) },
                            onDisconnectCloud = { settingsVm.disconnectCloud(it) },
                            onCreateLocalBackup = { settingsVm.createLocalBackupZip() }
                        )
                    }

                    composable<ReaderRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<ReaderRoute>()
                        val readerVm: ReaderViewModel = viewModel(
                            key = "reader_${args.documentId}",
                            factory = ReaderViewModel.Factory(
                                args.documentId,
                                args.initialPage,
                                container.documentRepository,
                                container.pdfEngine,
                                container.preferencesRepository,
                                container.ttsController
                            )
                        )
                        PdfReaderScreen(
                            viewModel = readerVm,
                            onBack = { navController.popBackStack() },
                            onOpenEditor = { docId, page ->
                                navController.navigate(EditorRoute(docId, page))
                            }
                        )
                    }

                    composable<EditorRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<EditorRoute>()
                        val editorVm: EditorViewModel = viewModel(
                            key = "editor_${args.documentId}",
                            factory = EditorViewModel.Factory(
                                args.documentId,
                                args.initialPage,
                                container.documentRepository,
                                container.pdfEngine,
                                container.pdfEditorEngine
                            )
                        )
                        PdfEditorScreen(
                            viewModel = editorVm,
                            onBack = { navController.popBackStack() },
                            onExportedToReader = { saved ->
                                navController.navigate(ReaderRoute(saved.id)) {
                                    popUpTo<EditorRoute> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<ScannerRoute> {
                        ScannerScreen(
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onSavedToReader = { doc ->
                                navController.navigate(ReaderRoute(doc.id)) {
                                    popUpTo<ScannerRoute> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<ImageToPdfRoute> {
                        ImageToPdfScreen(
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onOpenScanner = { navController.navigate(ScannerRoute) },
                            onCreated = { doc ->
                                navController.navigate(ReaderRoute(doc.id)) {
                                    popUpTo<ImageToPdfRoute> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<OfficeConverterRoute> {
                        OfficeConverterScreen(
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onPdfCreated = { doc -> navController.navigate(ReaderRoute(doc.id)) }
                        )
                    }

                    composable<WebAndNotesToPdfRoute> {
                        WebAndNotesToPdfScreen(
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onPdfCreated = { doc -> navController.navigate(ReaderRoute(doc.id)) }
                        )
                    }

                    composable<MergeSplitOrganizeRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<MergeSplitOrganizeRoute>()
                        MergeSplitOrganizeScreen(
                            initialMode = args.mode,
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onDone = { doc -> navController.navigate(ReaderRoute(doc.id)) }
                        )
                    }

                    composable<CompressSecurityRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<CompressSecurityRoute>()
                        CompressSecurityScreen(
                            initialMode = args.mode,
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onDone = { doc -> navController.navigate(ReaderRoute(doc.id)) }
                        )
                    }

                    composable<OcrToolRoute> {
                        OcrToolScreen(
                            viewModel = toolsVm,
                            onBack = { navController.popBackStack() },
                            onSearchablePdfCreated = { doc -> navController.navigate(ReaderRoute(doc.id)) }
                        )
                    }
                }
            }
        }
    }
}
