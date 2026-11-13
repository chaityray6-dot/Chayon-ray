package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.pdf.ScanFilterMode
import com.example.ui.components.formatFileSize
import com.example.ui.viewmodel.ToolsViewModel
import java.util.Locale

@Composable
fun EnhancePdfScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onOpenReader: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val pdfDocs = remember(docs) {
        docs.filter { it.docType == "PDF" || it.docType == "SCANNED_PDF" }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var previewPage by remember { mutableIntStateOf(0) }
    var showOriginal by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(ScanFilterMode.CLEAR_TEXT) }
    var sharpen by remember { mutableFloatStateOf(0.95f) }
    var contrast by remember { mutableFloatStateOf(1.20f) }
    var brightness by remember { mutableFloatStateOf(10f) }
    var outputTitle by remember { mutableStateOf("") }
    var generatedDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    val externalPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importAndEnhanceExternalPdf(uri) { imported ->
                selectedDoc = imported
                previewPage = 0
                outputTitle = "${imported.title}_Clear"
                generatedDoc = null
            }
        }
    }

    LaunchedEffect(selectedDoc?.id, previewPage, filterMode, sharpen, contrast, brightness) {
        selectedDoc?.let { doc ->
            viewModel.loadEnhancePagePreview(
                doc = doc,
                pageIndex = previewPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0)),
                filter = filterMode,
                brightness = brightness,
                contrast = contrast,
                sharpen = sharpen
            )
        }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("enhance_pdf_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Clear Blurry PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { externalPdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("enhance_pick_external_btn")
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import PDF")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            generatedDoc?.let { created ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().testTag("enhance_result_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Clear PDF Ready!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${created.title}.pdf • ${created.pageCount} pages • ${formatFileSize(created.fileSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onShareDocument(created) },
                                    modifier = Modifier.weight(1f).testTag("enhance_share_result_btn")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share PDF")
                                }
                                OutlinedButton(
                                    onClick = { onOpenReader(created) },
                                    modifier = Modifier.weight(1f).testTag("enhance_open_result_btn")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open PDF")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("1. Select a Blurry or Scanned PDF", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                if (pdfDocs.isEmpty()) {
                    Text(
                        "Tap 'Import PDF' above to choose a PDF from your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pdfDocs, key = { it.id }) { doc ->
                            FilterChip(
                                selected = selectedDoc?.id == doc.id,
                                onClick = {
                                    selectedDoc = doc
                                    previewPage = 0
                                    outputTitle = "${doc.title}_Clear"
                                    generatedDoc = null
                                },
                                label = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }

            selectedDoc?.let { doc ->
                item {
                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        label = { Text("Enhanced PDF File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Live Before / After Preview (Page ${previewPage + 1}/${doc.pageCount.coerceAtLeast(1)})", style = MaterialTheme.typography.labelLarge)
                        Row {
                            TextButton(
                                onClick = { previewPage = (previewPage - 1).coerceAtLeast(0) },
                                enabled = previewPage > 0
                            ) { Text("Prev") }
                            TextButton(
                                onClick = { previewPage = (previewPage + 1).coerceAtMost((doc.pageCount - 1).coerceAtLeast(0)) },
                                enabled = previewPage < doc.pageCount - 1
                            ) { Text("Next") }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showOriginal,
                            onClick = { showOriginal = false },
                            label = { Text("Enhanced (Clear)") }
                        )
                        FilterChip(
                            selected = showOriginal,
                            onClick = { showOriginal = true },
                            label = { Text("Original (Before)") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    val previewBmp = if (showOriginal) state.enhancePreviewBefore else state.enhancePreviewAfter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBmp != null) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = if (showOriginal) "Original Blurry Page" else "Sharpened Clear Page",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        if (state.isEnhancingPreview) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }

                item {
                    Text("3. Enhancement Preset", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScanFilterMode.entries) { mode ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { filterMode = mode },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Sharpen / Clarity: ${String.format(Locale.US, "%.2f", sharpen)}", style = MaterialTheme.typography.labelMedium)
                    Slider(value = sharpen, onValueChange = { sharpen = it }, valueRange = 0f..1.5f)
                    Text("Contrast: ${String.format(Locale.US, "%.2f", contrast)}", style = MaterialTheme.typography.labelMedium)
                    Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.7f..1.8f)
                    Text("Brightness: ${brightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                    Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -50f..50f)
                }

                item {
                    state.enhanceProgress?.let { (cur, total) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { (cur.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Enhancing page $cur of $total...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            viewModel.enhanceEntirePdf(
                                doc = doc,
                                outputTitle = outputTitle,
                                filter = filterMode,
                                brightness = brightness,
                                contrast = contrast,
                                sharpen = sharpen,
                                onDone = { saved -> generatedDoc = saved }
                            )
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("enhance_apply_all_btn")
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Enhance All Pages & Save PDF")
                    }
                }
            }
        }
    }
}
