package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.ui.components.CountPill
import com.example.ui.components.DashboardCategory
import com.example.ui.components.DocuVerseBrandHeader
import com.example.ui.components.DocumentActionsSheet
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyDocumentsState
import com.example.ui.components.HomeFabMenuSheet
import com.example.ui.components.RecentDocumentCard
import com.example.ui.components.StorageOverviewCard
import com.example.ui.components.matchesCategory
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandRoyalBlue
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WarningAmber

private data class QuickShortcut(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val tag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    allDocuments: List<DocumentEntity>,
    recentDocuments: List<DocumentEntity>,
    starredDocuments: List<DocumentEntity>,
    storageUsedBytes: Long,
    searchQuery: String,
    themeMode: ThemeMode,
    onSearchChange: (String) -> Unit,
    onToggleThemeQuick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onToggleStar: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onSaveToDevice: (DocumentEntity) -> Unit = {},
    onMoveToVault: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenImageToPdf: () -> Unit,
    onOpenMerge: () -> Unit,
    onOpenOcr: () -> Unit,
    onOpenWebAndNotes: () -> Unit,
    onOpenAiTab: () -> Unit,
    onOpenVaultFilter: () -> Unit,
    onOpenDocumentsSearch: (String) -> Unit = {},
    onImportFolderClick: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var selectedActionDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(DashboardCategory.ALL) }

    val shortcuts = listOf(
        QuickShortcut(strings.toolScanner, Icons.Filled.DocumentScanner, BrandRoyalBlue, "quick_scan", onOpenScanner),
        QuickShortcut(strings.toolImageToPdf, Icons.Filled.Image, BrandViolet, "quick_img_pdf", onOpenImageToPdf),
        QuickShortcut(strings.toolMerge, Icons.AutoMirrored.Filled.CallMerge, BrandIndigo, "quick_merge", onOpenMerge),
        QuickShortcut(strings.toolOcr, Icons.AutoMirrored.Filled.TextSnippet, SuccessEmerald, "quick_ocr", onOpenOcr),
        QuickShortcut(strings.navAi, Icons.Filled.AutoAwesome, WarningAmber, "quick_ai", onOpenAiTab),
        QuickShortcut(strings.privateVault, Icons.Filled.Lock, Color(0xFF0EA5E9), "quick_vault", onOpenVaultFilter)
    )

    val trimmedQuery = searchQuery.trim()
    val displayedDocs = remember(allDocuments, trimmedQuery, selectedCategory) {
        allDocuments.filter { doc ->
            doc.matchesCategory(selectedCategory) &&
                (trimmedQuery.isEmpty() || doc.title.contains(trimmedQuery, ignoreCase = true))
        }
    }

    val displayedRecentDocs = remember(recentDocuments, allDocuments, trimmedQuery, selectedCategory) {
        val baseRecent = recentDocuments.ifEmpty {
            allDocuments.sortedByDescending { maxOf(it.lastOpenedAt, it.modifiedAt) }
        }
        baseRecent.filter { doc ->
            doc.matchesCategory(selectedCategory) &&
                (trimmedQuery.isEmpty() || doc.title.contains(trimmedQuery, ignoreCase = true))
        }.take(10)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFabMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag("home_fab_add")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create or Import Document")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocuVerseBrandHeader(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onOpenDocumentsSearch(searchQuery) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_deep_search_icon")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = strings.documentsSearch
                            )
                        }
                        IconButton(
                            onClick = onToggleThemeQuick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (themeMode == ThemeMode.DARK) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                        Button(
                            onClick = onImportClick,
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_import_button")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.importDoc)
                        }
                    }
                }
            }

            // Material 3 Pill Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(strings.searchDocsHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search documents",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_search_clear")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_field")
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AssistChip(
                        onClick = { onOpenDocumentsSearch(searchQuery) },
                        label = {
                            Text(
                                if (trimmedQuery.isEmpty()) strings.documentsSearch
                                else "Search \"$trimmedQuery\" inside PDFs"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.FindInPage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("home_deep_search_chip")
                    )
                }
            }

            // Storage & Workspace Overview Card
            if (trimmedQuery.isEmpty()) {
                item {
                    StorageOverviewCard(
                        docCount = allDocuments.size,
                        starredCount = starredDocuments.size,
                        storageUsedBytes = storageUsedBytes,
                        storageLabel = strings.storageUsage
                    )
                }

                // Quick Access Tool Shortcuts
                item {
                    Text(
                        text = strings.quickTools,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shortcuts) { item ->
                            ElevatedCard(
                                onClick = item.onClick,
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .width(108.dp)
                                    .testTag(item.tag)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(item.tint.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(item.icon, contentDescription = item.title, tint = item.tint)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Documents Section with Horizontal Scrolling Categories & Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.recentDocs,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            CountPill(count = displayedRecentDocs.size)
                        }
                        if (selectedCategory != DashboardCategory.ALL) {
                            TextButton(
                                onClick = { selectedCategory = DashboardCategory.ALL },
                                modifier = Modifier.testTag("dashboard_reset_category")
                            ) {
                                Text("Show All")
                            }
                        }
                    }

                    // Horizontal scrolling category chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recent_categories_row")
                    ) {
                        items(DashboardCategory.entries) { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag(category.tag)
                            )
                        }
                    }

                    // Horizontal scrolling Recent Documents cards
                    if (displayedRecentDocs.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "No recent ${selectedCategory.label.lowercase()} documents match",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedCategory != DashboardCategory.ALL) {
                                    TextButton(onClick = { selectedCategory = DashboardCategory.ALL }) {
                                        Text("Reset")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recent_documents_row")
                        ) {
                            items(displayedRecentDocs, key = { "recent_${it.id}" }) { doc ->
                                RecentDocumentCard(
                                    doc = doc,
                                    pagesLabel = strings.pagesLabel,
                                    onOpen = { onOpenDocument(doc) },
                                    onToggleStar = { onToggleStar(doc) }
                                )
                            }
                        }
                    }
                }
            }

            // All Files List View Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (trimmedQuery.isBlank() && selectedCategory == DashboardCategory.ALL) {
                                strings.allFiles
                            } else {
                                "${strings.allFiles} • ${selectedCategory.label}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        CountPill(count = displayedDocs.size)
                    }
                    TextButton(
                        onClick = onImportClick,
                        modifier = Modifier.testTag("all_files_import_btn")
                    ) {
                        Text("+ ${strings.importDoc}")
                    }
                }
            }

            // All Files List View
            if (displayedDocs.isEmpty()) {
                item {
                    EmptyDocumentsState(
                        title = if (trimmedQuery.isNotEmpty() || selectedCategory != DashboardCategory.ALL) {
                            "No matching files found"
                        } else {
                            strings.emptyDocsTitle
                        },
                        subtitle = if (trimmedQuery.isNotEmpty() || selectedCategory != DashboardCategory.ALL) {
                            "Try clearing your search or switching back to All categories."
                        } else {
                            strings.emptyDocsSubtitle
                        },
                        actionLabel = if (trimmedQuery.isNotEmpty() || selectedCategory != DashboardCategory.ALL) {
                            "Show All Files"
                        } else {
                            strings.importDoc
                        },
                        onAction = {
                            if (trimmedQuery.isNotEmpty() || selectedCategory != DashboardCategory.ALL) {
                                onSearchChange("")
                                selectedCategory = DashboardCategory.ALL
                            } else {
                                onImportClick()
                            }
                        }
                    )
                }
            } else {
                items(displayedDocs, key = { "all_${it.id}" }) { doc ->
                    DocumentListCard(
                        doc = doc,
                        onOpen = { onOpenDocument(doc) },
                        onToggleStar = { onToggleStar(doc) },
                        onMoreClick = { selectedActionDoc = doc }
                    )
                }
            }
        }
    }

    selectedActionDoc?.let { doc ->
        DocumentActionsSheet(
            doc = doc,
            onDismiss = { selectedActionDoc = null },
            onOpenReader = { onOpenDocument(doc) },
            onOpenEditor = { onEditDocument(doc) },
            onShare = { onShareDocument(doc) },
            onSaveToDevice = { onSaveToDevice(doc) },
            onRename = { newTitle -> onRenameDocument(doc, newTitle) },
            onToggleVault = { onMoveToVault(doc) },
            onDelete = { onDeleteDocument(doc) }
        )
    }

    if (showFabMenu) {
        HomeFabMenuSheet(
            strings = strings,
            onDismiss = { showFabMenu = false },
            onImportClick = onImportClick,
            onImportFolderClick = onImportFolderClick,
            onOpenScanner = onOpenScanner,
            onOpenImageToPdf = onOpenImageToPdf,
            onOpenWebAndNotes = onOpenWebAndNotes
        )
    }
}
