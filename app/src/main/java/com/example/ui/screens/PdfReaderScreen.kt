package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfViewMode
import com.example.data.local.ReaderBackgroundMode
import com.example.data.local.ReflowAlignment
import com.example.ui.theme.ReaderDarkBg
import com.example.ui.theme.ReaderDarkText
import com.example.ui.theme.ReaderMintBg
import com.example.ui.theme.ReaderMintText
import com.example.ui.theme.ReaderSepiaBg
import com.example.ui.theme.ReaderSepiaText
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenEditor: (Long, Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSearchBar by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showComfortSheet by remember { mutableStateOf(false) }
    var showTtsSheet by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Apply custom reader brightness to Activity window when adjusted
    DisposableEffect(state.brightness) {
        val window = (context as? Activity)?.window
        val oldBrightness = window?.attributes?.screenBrightness ?: -1f
        if (window != null && state.brightness >= 0f) {
            window.attributes = window.attributes.apply { screenBrightness = state.brightness.coerceIn(0.05f, 1f) }
        }
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            }
        }
    }

    val (bgColor, fgColor) = when (state.backgroundMode) {
        ReaderBackgroundMode.LIGHT -> Color(0xFFF8FAFC) to Color(0xFF0F172A)
        ReaderBackgroundMode.DARK -> ReaderDarkBg to ReaderDarkText
        ReaderBackgroundMode.SEPIA -> ReaderSepiaBg to ReaderSepiaText
        ReaderBackgroundMode.MINT -> ReaderMintBg to ReaderMintText
    }

    if (state.isPasswordPromptRequired) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Password Protected PDF") },
            text = {
                Column {
                    Text("Enter the PDF password to decrypt and read this document.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("PDF Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = state.passwordError != null,
                        supportingText = state.passwordError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().testTag("pdf_password_input")
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.unlockWithPassword(pwd) }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Back") }
            }
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top HUD
            AnimatedVisibility(visible = !state.isFullScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_btn")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.document?.title ?: "PDF Reader",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (state.pageCount > 0) "Page ${state.currentPage + 1} of ${state.pageCount}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showSearchBar = !showSearchBar },
                                modifier = Modifier.testTag("reader_search_btn")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "Search in PDF")
                            }
                            val isBookmarked = bookmarks.any { it.pageNumber == state.currentPage }
                            IconButton(
                                onClick = { viewModel.toggleBookmarkCurrentPage() },
                                modifier = Modifier.testTag("reader_bookmark_btn")
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Bookmark page",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { state.document?.let { onOpenEditor(it.id, state.currentPage) } },
                                modifier = Modifier.testTag("reader_edit_btn")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Annotate & Sign")
                            }
                            IconButton(
                                onClick = { viewModel.toggleFullScreen() },
                                modifier = Modifier.testTag("reader_fullscreen_btn")
                            ) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen")
                            }
                        }

                        if (showSearchBar) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.searchText(it) },
                                    placeholder = { Text("Search text in document...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("reader_search_input")
                                )
                                if (state.searchMatches.isNotEmpty()) {
                                    Text(
                                        "${state.activeMatchIndex + 1}/${state.searchMatches.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(onClick = { viewModel.jumpSearchMatch(-1) }) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous match")
                                    }
                                    IconButton(onClick = { viewModel.jumpSearchMatch(1) }) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next match")
                                    }
                                }
                                IconButton(onClick = { showSearchBar = false; viewModel.searchText("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                                }
                            }
                        }

                        if (state.pageCount > 0) {
                            LinearProgressIndicator(
                                progress = { (state.currentPage + 1f) / state.pageCount.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(3.dp)
                            )
                        }
                    }
                }
            }

            // Main Reading Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(state.isReflowMode) {
                        if (!state.isReflowMode) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = newZoom
                                panOffset = if (newZoom > 1.02f) {
                                    Offset(
                                        (panOffset.x + pan.x).coerceIn(-600f, 600f),
                                        (panOffset.y + pan.y).coerceIn(-800f, 800f)
                                    )
                                } else Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.1f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.1f
                                }
                            }
                        )
                    }
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        text = state.error ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    state.isReflowMode -> {
                        // Text Reflow View (zero horizontal scroll, customizable typography & highlights)
                        val scroll = rememberScrollState()
                        val align = when (state.reflowAlignment) {
                            ReflowAlignment.LEFT -> TextAlign.Start
                            ReflowAlignment.CENTER -> TextAlign.Center
                            ReflowAlignment.JUSTIFY -> TextAlign.Justify
                        }
                        val highlightedText = remember(state.reflowPageText, state.searchQuery) {
                            buildHighlightedAnnotatedString(state.reflowPageText, state.searchQuery)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Reflow • Page ${state.currentPage + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = fgColor.copy(alpha = 0.65f)
                            )
                            Spacer(Modifier.height(12.dp))
                            if (highlightedText.isEmpty()) {
                                Text(
                                    text = "This page appears to be image-only. Use the OCR tool to extract editable text.",
                                    color = fgColor,
                                    fontSize = state.reflowFontSizeSp.sp,
                                    lineHeight = (state.reflowFontSizeSp * state.reflowLineSpacing).sp,
                                    textAlign = align
                                )
                            } else {
                                Text(
                                    text = highlightedText,
                                    color = fgColor,
                                    fontSize = state.reflowFontSizeSp.sp,
                                    lineHeight = (state.reflowFontSizeSp * state.reflowLineSpacing).sp,
                                    textAlign = align
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                )
                        ) {
                            when (state.viewMode) {
                                PdfViewMode.CONTINUOUS_VERTICAL -> {
                                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
                                    LaunchedEffect(state.currentPage) {
                                        if (listState.firstVisibleItemIndex != state.currentPage) {
                                            listState.scrollToItem(state.currentPage)
                                        }
                                    }
                                    LaunchedEffect(listState.firstVisibleItemIndex) {
                                        viewModel.goToPage(listState.firstVisibleItemIndex)
                                    }
                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(state.pageCount) { idx ->
                                            PdfRenderedPageCard(
                                                pageIndex = idx,
                                                backgroundMode = state.backgroundMode,
                                                renderPage = { viewModel.renderPageBitmap(idx, 1080) }
                                            )
                                        }
                                    }
                                }
                                PdfViewMode.SINGLE_PAGE -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(12.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        PdfRenderedPageCard(
                                            pageIndex = state.currentPage,
                                            backgroundMode = state.backgroundMode,
                                            renderPage = { viewModel.renderPageBitmap(state.currentPage, 1200) },
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            OutlinedButton(
                                                onClick = { viewModel.goToPage(state.currentPage - 1) },
                                                enabled = state.currentPage > 0
                                            ) { Text("Prev Page") }
                                            OutlinedButton(
                                                onClick = { viewModel.goToPage(state.currentPage + 1) },
                                                enabled = state.currentPage < state.pageCount - 1
                                            ) { Text("Next Page") }
                                        }
                                    }
                                }
                                PdfViewMode.HORIZONTAL_SWIPE -> {
                                    val pagerState = rememberPagerState(
                                        initialPage = state.currentPage,
                                        pageCount = { state.pageCount.coerceAtLeast(1) }
                                    )
                                    LaunchedEffect(state.currentPage) {
                                        if (pagerState.currentPage != state.currentPage) {
                                            pagerState.scrollToPage(state.currentPage)
                                        }
                                    }
                                    LaunchedEffect(pagerState.currentPage) {
                                        viewModel.goToPage(pagerState.currentPage)
                                    }
                                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { idx ->
                                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                                            PdfRenderedPageCard(
                                                pageIndex = idx,
                                                backgroundMode = state.backgroundMode,
                                                renderPage = { viewModel.renderPageBitmap(idx, 1200) }
                                            )
                                        }
                                    }
                                }
                                PdfViewMode.TWO_PAGE_BOOK -> {
                                    val spreadCount = ((state.pageCount + 1) / 2).coerceAtLeast(1)
                                    val pagerState = rememberPagerState(
                                        initialPage = state.currentPage / 2,
                                        pageCount = { spreadCount }
                                    )
                                    LaunchedEffect(pagerState.currentPage) {
                                        viewModel.goToPage((pagerState.currentPage * 2).coerceAtMost(state.pageCount - 1))
                                    }
                                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { spreadIdx ->
                                        val leftIdx = spreadIdx * 2
                                        val rightIdx = leftIdx + 1
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PdfRenderedPageCard(
                                                pageIndex = leftIdx,
                                                backgroundMode = state.backgroundMode,
                                                renderPage = { viewModel.renderPageBitmap(leftIdx, 720) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (rightIdx < state.pageCount) {
                                                PdfRenderedPageCard(
                                                    pageIndex = rightIdx,
                                                    backgroundMode = state.backgroundMode,
                                                    renderPage = { viewModel.renderPageBitmap(rightIdx, 720) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            } else {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Exit Fullscreen floating button
                if (state.isFullScreen) {
                    IconButton(
                        onClick = { viewModel.toggleFullScreen() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                }

                // Floating search match banner if search active
                if (state.searchMatches.isNotEmpty() && showSearchBar) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        val active = state.searchMatches.getOrNull(state.activeMatchIndex)
                        if (active != null) {
                            Text(
                                text = "Page ${active.pageIndex + 1} (${active.matchCount} match): ${active.snippet}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom Reader Toolbar + Mini TTS Bar
            AnimatedVisibility(visible = !state.isFullScreen) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        if (ttsState.isPlaying || ttsState.isPaused) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TTS ${ttsState.currentParagraphIndex + 1}/${ttsState.paragraphs.size} (${String.format(Locale.US, "%.1fx", ttsState.speechRate)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.ttsController.previousParagraph() }) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev paragraph")
                                }
                                IconButton(onClick = {
                                    if (ttsState.isPlaying) viewModel.ttsController.pause()
                                    else viewModel.ttsController.playOrResume()
                                }) {
                                    Icon(if (ttsState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
                                }
                                IconButton(onClick = { viewModel.ttsController.nextParagraph() }) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next paragraph")
                                }
                                IconButton(onClick = { viewModel.ttsController.stop() }) {
                                    Icon(Icons.Filled.Stop, contentDescription = "Stop TTS")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showTocSheet = true }, modifier = Modifier.testTag("reader_toc_btn")) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents & Bookmarks")
                            }
                            IconButton(onClick = { showThumbnailsSheet = true }, modifier = Modifier.testTag("reader_thumbs_btn")) {
                                Icon(Icons.Filled.GridView, contentDescription = "Page Thumbnails")
                            }
                            TextButton(onClick = { showGoToPageDialog = true }, modifier = Modifier.testTag("reader_goto_btn")) {
                                Text("${state.currentPage + 1}/${state.pageCount.coerceAtLeast(1)}")
                            }
                            IconButton(onClick = { viewModel.toggleReflowMode() }, modifier = Modifier.testTag("reader_reflow_btn")) {
                                Icon(
                                    Icons.Filled.TextFields,
                                    contentDescription = "Text Reflow",
                                    tint = if (state.isReflowMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showComfortSheet = true }, modifier = Modifier.testTag("reader_comfort_btn")) {
                                Icon(Icons.Filled.Brightness6, contentDescription = "View Mode & Comfort")
                            }
                            IconButton(
                                onClick = {
                                    viewModel.loadCurrentPageIntoTts()
                                    showTtsSheet = true
                                },
                                modifier = Modifier.testTag("reader_tts_btn")
                            ) {
                                Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Text-to-Speech")
                            }
                        }
                    }
                }
            }
        }
    }

    // Go To Page Dialog
    if (showGoToPageDialog) {
        var pageInput by remember { mutableStateOf((state.currentPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showGoToPageDialog = false },
            title = { Text("Go to Page (1 – ${state.pageCount})") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.recentlyViewedPages.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Recently Viewed", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.recentlyViewedPages) { p ->
                                AssistChip(
                                    onClick = { viewModel.goToPage(p); showGoToPageDialog = false },
                                    label = { Text("P.${p + 1}") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = (pageInput.toIntOrNull() ?: 1) - 1
                    viewModel.goToPage(p)
                    showGoToPageDialog = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // TOC & Bookmarks Sheet
    if (showTocSheet) {
        ModalBottomSheet(onDismissRequest = { showTocSheet = false }) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Table of Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (state.tocEntries.isEmpty()) {
                    item {
                        Text("No outline embedded in this PDF.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(state.tocEntries) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title, modifier = Modifier.padding(start = (entry.level * 12).dp)) },
                            trailingContent = { Text("P.${entry.pageIndex + 1}") },
                            modifier = Modifier.clickable {
                                viewModel.goToPage(entry.pageIndex)
                                showTocSheet = false
                            }
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(bookmarks) { bm ->
                    ListItem(
                        headlineContent = { Text(bm.label) },
                        leadingContent = { Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Text("P.${bm.pageNumber + 1}") },
                        modifier = Modifier.clickable {
                            viewModel.goToPage(bm.pageNumber)
                            showTocSheet = false
                        }
                    )
                }
            }
        }
    }

    // Page Thumbnails Sheet
    if (showThumbnailsSheet) {
        ModalBottomSheet(onDismissRequest = { showThumbnailsSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Page Thumbnails", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.pageCount) { idx ->
                        val thumb by produceState<Bitmap?>(initialValue = null, idx) {
                            value = viewModel.renderPageBitmap(idx, 260)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (idx == state.currentPage) 2.5.dp else 1.dp,
                                    color = if (idx == state.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.goToPage(idx)
                                    showThumbnailsSheet = false
                                }
                                .padding(6.dp)
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb!!.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Page ${idx + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // View Mode, Comfort Colors, Brightness & Reflow Sheet
    if (showComfortSheet) {
        ModalBottomSheet(onDismissRequest = { showComfortSheet = false }) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reading Mode & Comfort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PdfViewMode.CONTINUOUS_VERTICAL to "Scroll",
                        PdfViewMode.SINGLE_PAGE to "Single",
                        PdfViewMode.TWO_PAGE_BOOK to "Book",
                        PdfViewMode.HORIZONTAL_SWIPE to "Swipe"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.viewMode == mode && !state.isReflowMode,
                            onClick = {
                                if (state.isReflowMode) viewModel.toggleReflowMode()
                                viewModel.setViewMode(mode)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Page Background", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderBackgroundMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.backgroundMode == mode,
                            onClick = { viewModel.setBackgroundMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = if (state.brightness < 0f) 0.7f else state.brightness,
                    onValueChange = { viewModel.setBrightness(it) },
                    valueRange = 0.08f..1f
                )
                if (state.isReflowMode) {
                    Text("Reflow Font Size: ${state.reflowFontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = state.reflowFontSizeSp,
                        onValueChange = { viewModel.updateReflowStyle(it, state.reflowLineSpacing, state.reflowAlignment) },
                        valueRange = 13f..28f
                    )
                    Text("Line Spacing: ${String.format(Locale.US, "%.2fx", state.reflowLineSpacing)}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = state.reflowLineSpacing,
                        onValueChange = { viewModel.updateReflowStyle(state.reflowFontSizeSp, it, state.reflowAlignment) },
                        valueRange = 1.15f..2.1f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReflowAlignment.entries.forEach { al ->
                            FilterChip(
                                selected = state.reflowAlignment == al,
                                onClick = { viewModel.updateReflowStyle(state.reflowFontSizeSp, state.reflowLineSpacing, al) },
                                label = { Text(al.name) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // Text-to-Speech Controls Sheet
    if (showTtsSheet) {
        ModalBottomSheet(onDismissRequest = { showTtsSheet = false }) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Text-to-Speech Reader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ttsState.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { viewModel.ttsController.previousParagraph() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous paragraph")
                    }
                    Button(onClick = {
                        if (ttsState.isPlaying) viewModel.ttsController.pause()
                        else viewModel.ttsController.playOrResume()
                    }) {
                        Icon(if (ttsState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (ttsState.isPlaying) "Pause" else if (ttsState.isPaused) "Resume" else "Play Page")
                    }
                    IconButton(onClick = { viewModel.ttsController.nextParagraph() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next paragraph")
                    }
                    IconButton(onClick = { viewModel.ttsController.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    }
                }
                Text("Speed: ${String.format(Locale.US, "%.2fx", ttsState.speechRate)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = ttsState.speechRate,
                    onValueChange = { viewModel.ttsController.setSpeechRate(it) },
                    valueRange = 0.5f..2.0f
                )
                Text("Language", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Locale.US to "English (US)",
                        Locale.UK to "English (UK)",
                        Locale("bn", "BD") to "বাংলা (BD)"
                    ).forEach { (loc, label) ->
                        FilterChip(
                            selected = ttsState.selectedLanguageTag == loc.toLanguageTag(),
                            onClick = { viewModel.ttsController.setLanguage(loc) },
                            label = { Text(label) }
                        )
                    }
                }
                if (ttsState.availableVoices.isNotEmpty()) {
                    Text("Available Device Voices", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ttsState.availableVoices.take(8)) { voice ->
                            FilterChip(
                                selected = ttsState.selectedVoiceName == voice.name,
                                onClick = { viewModel.ttsController.setVoice(voice) },
                                label = { Text(voice.locale.displayLanguage + " • " + voice.name.takeLast(8)) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PdfRenderedPageCard(
    pageIndex: Int,
    backgroundMode: ReaderBackgroundMode,
    renderPage: suspend () -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pageIndex) {
        value = renderPage()
    }
    val tintOverlay = when (backgroundMode) {
        ReaderBackgroundMode.SEPIA -> Color(0x28D4A359)
        ReaderBackgroundMode.MINT -> Color(0x2084CC9C)
        ReaderBackgroundMode.DARK -> Color(0x30090E1A)
        ReaderBackgroundMode.LIGHT -> Color.Transparent
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.71f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            if (tintOverlay != Color.Transparent) {
                Box(modifier = Modifier.matchParentSize().background(tintOverlay))
            }
        }
    }
}

private fun buildHighlightedAnnotatedString(text: String, query: String) = buildAnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor < text.length) {
        val idx = text.indexOf(q, cursor, ignoreCase = true)
        if (idx < 0) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, idx))
        withStyle(SpanStyle(background = Color(0xFFFDE047), color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, idx + q.length))
        }
        cursor = idx + q.length
    }
}
