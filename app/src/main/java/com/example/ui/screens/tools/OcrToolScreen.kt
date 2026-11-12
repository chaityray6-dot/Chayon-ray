package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.ocr.OcrLanguage
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ToolsViewModel
import kotlinx.coroutines.launch

@Composable
fun OcrToolScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onSearchablePdfCreated: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var ocrLang by remember { mutableStateOf(OcrLanguage.ENGLISH) }
    var outputTitle by remember { mutableStateOf("OCR_Searchable_Document") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.runOcrOnImageUri(uri, ocrLang)
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
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("OCR & Searchable PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text("OCR Language Mode", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OcrLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = ocrLang == lang,
                            onClick = { ocrLang = lang },
                            label = { Text(lang.label) }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("ocr_pick_image_btn")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Extract Text from Image / Photo")
                }
            }

            item {
                Text("Or Run OCR on PDF Page 1", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        OutlinedButton(onClick = { viewModel.runOcrOnPdfPage(doc, 0, ocrLang) }) {
                            Text(doc.title, maxLines = 1)
                        }
                    }
                }
            }

            if (state.isBusy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Running on-device OCR recognition...")
                    }
                }
            }

            state.ocrNote?.let { note ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f))) {
                        Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.ocrExtractedText,
                    onValueChange = { viewModel.updateOcrCorrectedText(it) },
                    label = { Text("OCR Correction & Review Editor") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("ocr_editor_field")
                )
            }

            if (state.ocrExtractedText.isNotBlank()) {
                item {
                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        label = { Text("Export File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.ocrExtractedText))
                                scope.launch { snackbarHostState.showSnackbar("Copied extracted text") }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportOcrAsTxt(outputTitle) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save TXT")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveSearchableOcrPdf(outputTitle, onSearchablePdfCreated) },
                        enabled = state.ocrPreviewBitmap != null && !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("ocr_save_searchable_pdf_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create Searchable PDF")
                    }
                }
            }
        }
    }
}
