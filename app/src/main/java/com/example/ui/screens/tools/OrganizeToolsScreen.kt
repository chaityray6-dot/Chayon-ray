package com.example.ui.screens.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.pdf.CompressionLevel
import com.example.data.pdf.WatermarkPosition
import com.example.ui.components.formatFileSize
import com.example.ui.viewmodel.ToolsViewModel

@Composable
fun MergeSplitOrganizeScreen(
    initialMode: String,
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onDone: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(initialMode) }

    val mergeQueue = remember { mutableStateListOf<DocumentEntity>() }
    var selectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var extraInsertDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var splitRangeInput by remember { mutableStateOf("1") }
    var outputTitle by remember { mutableStateOf("DocuVerse_Output") }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Merge, Split & Organize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("merge" to "Merge", "split" to "Split", "organize" to "Organize Pages").forEach { (k, label) ->
                    FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = { Text("Output PDF Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            when (mode) {
                "merge" -> {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            val checked = mergeQueue.any { it.id == doc.id }
                            Card(onClick = {
                                if (checked) mergeQueue.removeAll { it.id == doc.id }
                                else mergeQueue.add(doc)
                            }) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, fontWeight = FontWeight.SemiBold)
                                        Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (checked) {
                                        val idx = mergeQueue.indexOfFirst { it.id == doc.id }
                                        IconButton(onClick = {
                                            if (idx > 0) {
                                                val item = mergeQueue.removeAt(idx)
                                                mergeQueue.add(idx - 1, item)
                                            }
                                        }, enabled = idx > 0) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Up") }
                                        IconButton(onClick = {
                                            if (idx < mergeQueue.lastIndex) {
                                                val item = mergeQueue.removeAt(idx)
                                                mergeQueue.add(idx + 1, item)
                                            }
                                        }, enabled = idx < mergeQueue.lastIndex) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Down") }
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { viewModel.mergePdfs(mergeQueue.toList(), outputTitle, onDone) },
                        enabled = mergeQueue.size >= 2 && !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("merge_execute_btn")
                    ) { Text("Merge ${mergeQueue.size} PDFs") }
                }
                "split" -> {
                    Text("Select a PDF and enter page numbers (e.g. 1, 2, 3 or 1-3):", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = splitRangeInput,
                        onValueChange = { splitRangeInput = it },
                        label = { Text("Pages to extract (1-based)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            Card(onClick = { selectedDoc = doc }) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selectedDoc?.id == doc.id, onCheckedChange = { selectedDoc = doc })
                                    Spacer(Modifier.width(8.dp))
                                    Text("${doc.title} (${doc.pageCount} pages)")
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val doc = selectedDoc ?: return@Button
                            val pages = parsePageRanges(splitRangeInput, doc.pageCount)
                            viewModel.splitPdf(doc, pages, outputTitle, onDone)
                        },
                        enabled = selectedDoc != null && !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("split_execute_btn")
                    ) { Text("Extract Selected Pages") }
                }
                else -> {
                    if (selectedDoc == null) {
                        Text("Pick a PDF to organize its pages:", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(docs, key = { it.id }) { doc ->
                                Card(onClick = {
                                    selectedDoc = doc
                                    viewModel.loadOrganizerPages(doc)
                                }) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text("${doc.title} (${doc.pageCount} pages)", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pages: ${state.organizerPages.size}", fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { selectedDoc = null }) { Text("Change PDF") }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(140.dp),
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(state.organizerPages, key = { _, p -> p.id }) { idx, page ->
                                Card {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        page.thumbnail?.let { bmp ->
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Page ${idx + 1}",
                                                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                        Text("Orig P.${page.originalIndex + 1} (${page.rotationDelta}°)", style = MaterialTheme.typography.labelSmall)
                                        Row {
                                            IconButton(onClick = { viewModel.moveOrganizerPage(idx, idx - 1) }, enabled = idx > 0, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move earlier", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { viewModel.rotateOrganizerPage(idx) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { viewModel.duplicateOrganizerPage(idx) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(onClick = { viewModel.deleteOrganizerPage(idx) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = {
                                selectedDoc?.let { doc ->
                                    viewModel.saveOrganizedPdf(doc, extraInsertDoc, state.organizerPages.lastIndex, outputTitle, onDone)
                                }
                            },
                            enabled = state.organizerPages.isNotEmpty() && !state.isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("organize_save_btn")
                        ) { Text("Save Organized PDF") }
                    }
                }
            }
        }
    }
}

@Composable
fun CompressSecurityScreen(
    initialMode: String,
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onDone: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(initialMode) }
    var selectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    var compressionLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var pdfPassword by remember { mutableStateOf("") }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.28f) }
    var watermarkPosition by remember { mutableStateOf(WatermarkPosition.DIAGONAL) }
    var logoUri by remember { mutableStateOf<Uri?>(null) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        logoUri = uri
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Compress, Protect & Watermark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("compress" to "Compress", "protect" to "Password Lock", "watermark" to "Watermark").forEach { (k, label) ->
                        FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(label) })
                    }
                }
            }

            item {
                Text("1. Select Target PDF", style = MaterialTheme.typography.labelLarge)
            }
            items(docs, key = { it.id }) { doc ->
                Card(onClick = { selectedDoc = doc }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selectedDoc?.id == doc.id, onCheckedChange = { selectedDoc = doc })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(doc.title, fontWeight = FontWeight.SemiBold)
                            Text(formatFileSize(doc.fileSizeBytes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (selectedDoc != null) {
                item {
                    when (mode) {
                        "compress" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Compression Level", style = MaterialTheme.typography.labelLarge)
                                CompressionLevel.entries.forEach { lvl ->
                                    FilterChip(
                                        selected = compressionLevel == lvl,
                                        onClick = { compressionLevel = lvl },
                                        label = { Text(lvl.label) }
                                    )
                                }
                                Button(
                                    onClick = { viewModel.previewCompression(selectedDoc!!, compressionLevel) },
                                    enabled = !state.isBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("compress_preview_btn")
                                ) {
                                    if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Calculate Compressed Size")
                                }
                                if (state.compressionResultBytes > 0L) {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Original Size: ${formatFileSize(state.compressionOriginalBytes)}")
                                            Text("Compressed Size: ${formatFileSize(state.compressionResultBytes)}", fontWeight = FontWeight.Bold)
                                            Button(
                                                onClick = { viewModel.saveCompressedPreview(selectedDoc!!, onDone) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Save Compressed PDF") }
                                        }
                                    }
                                }
                            }
                        }
                        "protect" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = pdfPassword,
                                    onValueChange = { pdfPassword = it },
                                    label = { Text("PDF Encryption Password (AES-128)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("protect_pdf_password_input")
                                )
                                Button(
                                    onClick = { viewModel.protectPdfWithPassword(selectedDoc!!, pdfPassword, onDone) },
                                    enabled = pdfPassword.isNotBlank() && !state.isBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("protect_pdf_apply_btn")
                                ) { Text("Encrypt & Save PDF") }
                            }
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = { watermarkText = it },
                                    label = { Text("Watermark Text") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                Slider(value = watermarkOpacity, onValueChange = { watermarkOpacity = it }, valueRange = 0.08f..0.9f)
                                Text("Position", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    WatermarkPosition.entries.take(3).forEach { pos ->
                                        FilterChip(selected = watermarkPosition == pos, onClick = { watermarkPosition = pos }, label = { Text(pos.label) })
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    WatermarkPosition.entries.drop(3).forEach { pos ->
                                        FilterChip(selected = watermarkPosition == pos, onClick = { watermarkPosition = pos }, label = { Text(pos.label) })
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.applyTextWatermark(selectedDoc!!, watermarkText, watermarkOpacity, watermarkPosition, onDone) },
                                        enabled = watermarkText.isNotBlank() && !state.isBusy,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Apply Text") }
                                    OutlinedButton(
                                        onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Image, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pick Logo")
                                    }
                                }
                                logoUri?.let { uri ->
                                    Button(
                                        onClick = { viewModel.applyImageWatermark(selectedDoc!!, uri, watermarkOpacity, watermarkPosition, onDone) },
                                        enabled = !state.isBusy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Apply Selected Image Watermark") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parsePageRanges(input: String, maxPages: Int): List<Int> {
    val result = linkedSetOf<Int>()
    input.split(",").map { it.trim() }.forEach { part ->
        if (part.contains("-")) {
            val bounds = part.split("-")
            val s = bounds.getOrNull(0)?.toIntOrNull()
            val e = bounds.getOrNull(1)?.toIntOrNull()
            if (s != null && e != null) {
                for (p in minOf(s, e)..maxOf(s, e)) {
                    if (p in 1..maxPages) result.add(p - 1)
                }
            }
        } else {
            val p = part.toIntOrNull()
            if (p != null && p in 1..maxPages) result.add(p - 1)
        }
    }
    return result.toList()
}
