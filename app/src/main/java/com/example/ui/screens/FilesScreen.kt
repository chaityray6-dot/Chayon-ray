package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.ui.components.DocumentActionsSheet
import com.example.ui.components.DocumentGridCard
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyDocumentsState
import com.example.ui.components.PinLockGateScreen
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.FileFilterOption
import com.example.ui.viewmodel.SortOption

@Composable
fun FilesScreen(
    documents: List<DocumentEntity>,
    searchQuery: String,
    filterOption: FileFilterOption,
    sortOption: SortOption,
    isGridView: Boolean,
    duplicateGroupsCount: Int,
    isVaultUnlocked: Boolean,
    isPinConfigured: Boolean,
    biometricAvailable: Boolean,
    onSearchChange: (String) -> Unit,
    onFilterChange: (FileFilterOption) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onToggleGridView: () -> Unit,
    onImportClick: () -> Unit,
    onCleanDuplicates: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onRequestBiometric: () -> Unit,
    onVaultUnlocked: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onToggleStar: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onToggleVault: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedActionDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    if (filterOption == FileFilterOption.VAULT && !isVaultUnlocked && isPinConfigured) {
        PinLockGateScreen(
            title = strings.unlockVault,
            biometricAvailable = biometricAvailable,
            onVerifyPin = onVerifyPin,
            onRequestBiometric = onRequestBiometric,
            onUnlocked = onVaultUnlocked,
            onCancel = { onFilterChange(FileFilterOption.ALL) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.navFiles,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("files_sort_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort files")
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    SortOption.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label + if (opt == sortOption) " ✓" else "") },
                            onClick = { onSortChange(opt); sortMenuExpanded = false }
                        )
                    }
                }
                IconButton(
                    onClick = onToggleGridView,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("files_view_toggle")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Toggle grid or list view"
                    )
                }
                Button(onClick = onImportClick, modifier = Modifier.testTag("files_import_btn")) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.importDoc)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(strings.searchDocsHint) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("files_search_input")
        )

        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FileFilterOption.entries) { opt ->
                FilterChip(
                    selected = opt == filterOption,
                    onClick = {
                        if (opt == FileFilterOption.VAULT && !isPinConfigured) {
                            onVaultUnlocked()
                        }
                        onFilterChange(opt)
                    },
                    label = { Text(opt.label) },
                    leadingIcon = if (opt == FileFilterOption.VAULT) {
                        { Icon(Icons.Filled.Lock, contentDescription = null) }
                    } else null,
                    modifier = Modifier.testTag("filter_${opt.name.lowercase()}")
                )
            }
        }

        if (duplicateGroupsCount > 0) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = WarningAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$duplicateGroupsCount duplicate document group(s) detected",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(onClick = onCleanDuplicates, modifier = Modifier.testTag("clean_duplicates_btn")) {
                        Text("Remove")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (documents.isEmpty()) {
            EmptyDocumentsState(
                title = if (filterOption == FileFilterOption.VAULT) "Private Vault is empty" else strings.emptyDocsTitle,
                subtitle = if (filterOption == FileFilterOption.VAULT) {
                    "Move sensitive PDFs into the Private Vault from any document's options menu."
                } else strings.emptyDocsSubtitle,
                actionLabel = strings.importDoc,
                onAction = onImportClick
            )
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentGridCard(
                        doc = doc,
                        onOpen = { onOpenDocument(doc) },
                        onToggleStar = { onToggleStar(doc) },
                        onMoreClick = { selectedActionDoc = doc }
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(documents, key = { it.id }) { doc ->
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
            onRename = { newTitle -> onRenameDocument(doc, newTitle) },
            onToggleVault = { onToggleVault(doc) },
            onDelete = { onDeleteDocument(doc) }
        )
    }
}
