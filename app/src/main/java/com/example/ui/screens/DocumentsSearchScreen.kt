package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.data.repository.DocumentContentSearchResult
import com.example.ui.components.CountPill
import com.example.ui.components.DocumentActionsSheet
import com.example.ui.components.DocumentListCard
import com.example.ui.components.DocumentTypeBadge
import com.example.ui.i18n.LocalAppStrings

private enum class SearchScopeFilter(val label: String, val tag: String) {
    ALL("All", "search_filter_all"),
    TITLES("Titles", "search_filter_titles"),
    CONTENT("Inside Text", "search_filter_content"),
    PDF("PDF", "search_filter_pdf"),
    OFFICE("Office", "search_filter_office"),
    STARRED("Starred", "search_filter_starred")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentsSearchScreen(
    initialQuery: String,
    query: String,
    allDocuments: List<DocumentEntity>,
    contentResults: List<DocumentContentSearchResult>,
    isSearchingContent: Boolean,
    recentSearches: List<String>,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onBack: () -> Unit,
    onOpenDocumentPage: (Long, Int) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onToggleStar: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onMoveToVault: (DocumentEntity) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var scopeFilter by remember { mutableStateOf(SearchScopeFilter.ALL) }
    var selectedActionDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && query.isBlank()) {
            onQueryChange(initialQuery)
        }
        runCatching { focusRequester.requestFocus() }
    }

    val trimmed = query.trim()

    fun DocumentEntity.matchesTypeFilter(): Boolean = when (scopeFilter) {
        SearchScopeFilter.PDF -> docType == "PDF" || docType == "SCANNED_PDF"
        SearchScopeFilter.OFFICE -> docType in listOf("DOCX", "XLSX", "PPTX", "TXT")
        SearchScopeFilter.STARRED -> isStarred
        else -> true
    }

    val titleMatches = remember(allDocuments, trimmed, scopeFilter) {
        if (scopeFilter == SearchScopeFilter.CONTENT) emptyList()
        else if (trimmed.isEmpty()) allDocuments.filter { it.matchesTypeFilter() }.take(8)
        else allDocuments.filter { doc ->
            doc.matchesTypeFilter() &&
                (doc.title.contains(trimmed, ignoreCase = true) ||
                    doc.docType.contains(trimmed, ignoreCase = true))
        }
    }

    val filteredContentMatches = remember(contentResults, scopeFilter, trimmed) {
        if (trimmed.length < 2 || scopeFilter == SearchScopeFilter.TITLES) emptyList()
        else contentResults.filter { it.document.matchesTypeFilter() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.documentsSearch, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("search_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search input field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(strings.searchInsideDocsHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search icon", tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("deep_search_clear_btn")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSubmitSearch(query)
                        keyboardController?.hide()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester)
                    .testTag("deep_search_input")
            )

            Spacer(Modifier.height(10.dp))

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("search_scope_filters")
            ) {
                items(SearchScopeFilter.entries) { filter ->
                    FilterChip(
                        selected = scopeFilter == filter,
                        onClick = { scopeFilter = filter },
                        label = { Text(filter.label) },
                        leadingIcon = if (filter == SearchScopeFilter.STARRED) {
                            { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(filter.tag)
                    )
                }
            }

            if (isSearchingContent) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag("search_loading_bar")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Recent search history when query is empty
                if (trimmed.isEmpty() && recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.recentSearches,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onClearRecentSearches) {
                                Text("Clear")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recentSearches.forEach { term ->
                                AssistChip(
                                    onClick = { onSubmitSearch(term) },
                                    label = { Text(term) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }

                // Prompt hint for short queries
                if (trimmed.length == 1) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Type at least 2 characters to also search text inside all PDFs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Title matches section
                if (titleMatches.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (trimmed.isEmpty()) "Suggested Documents" else strings.matchingTitles,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            CountPill(count = titleMatches.size)
                        }
                    }
                    items(titleMatches, key = { "title_${it.id}" }) { doc ->
                        DocumentListCard(
                            doc = doc,
                            onOpen = { onOpenDocumentPage(doc.id, doc.lastViewedPage) },
                            onToggleStar = { onToggleStar(doc) },
                            onMoreClick = { selectedActionDoc = doc }
                        )
                    }
                }

                // Full-text inside document matches section
                if (trimmed.length >= 2 && scopeFilter != SearchScopeFilter.TITLES) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.FindInPage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.insideContentMatches,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            CountPill(count = filteredContentMatches.size)
                        }
                    }

                    if (!isSearchingContent && filteredContentMatches.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No text matches found inside your PDFs for \"$trimmed\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredContentMatches, key = { "content_${it.document.id}" }) { result ->
                            ContentMatchResultCard(
                                result = result,
                                query = trimmed,
                                onOpenPage = { pageIndex -> onOpenDocumentPage(result.document.id, pageIndex) }
                            )
                        }
                    }
                }

                // Overall empty state when searching by title only and nothing matched
                if (trimmed.isNotEmpty() && titleMatches.isEmpty() && filteredContentMatches.isEmpty() && !isSearchingContent) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No documents matched \"$trimmed\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedActionDoc?.let { doc ->
        DocumentActionsSheet(
            doc = doc,
            onDismiss = { selectedActionDoc = null },
            onOpenReader = { onOpenDocumentPage(doc.id, doc.lastViewedPage) },
            onOpenEditor = { onEditDocument(doc) },
            onShare = { onShareDocument(doc) },
            onRename = { newTitle -> onRenameDocument(doc, newTitle) },
            onToggleVault = { onMoveToVault(doc) },
            onDelete = { onDeleteDocument(doc) }
        )
    }
}

@Composable
private fun ContentMatchResultCard(
    result: DocumentContentSearchResult,
    query: String,
    onOpenPage: (Int) -> Unit
) {
    val doc = result.document
    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("content_match_doc_${doc.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPage(result.matches.first().pageIndex) }
            ) {
                DocumentTypeBadge(
                    docType = doc.docType,
                    isProtected = doc.isPasswordProtected,
                    modifier = Modifier.size(38.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${result.matches.sumOf { it.matchCount }} match(es) across ${result.matches.size} page(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            result.matches.take(4).forEachIndexed { idx, match ->
                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPage(match.pageIndex) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "P.${match.pageIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = highlightSnippet(match.snippet, query, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun highlightSnippet(
    snippet: String,
    query: String,
    highlightColor: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) {
        append(snippet)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor < snippet.length) {
        val idx = snippet.indexOf(q, cursor, ignoreCase = true)
        if (idx < 0) {
            append(snippet.substring(cursor))
            break
        }
        if (idx > cursor) append(snippet.substring(cursor, idx))
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(snippet.substring(idx, idx + q.length))
        }
        cursor = idx + q.length
    }
}
