package com.example.ui.screens.tools

import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.DocumentEntity
import com.example.data.pdf.PageSizePreset
import com.example.ui.viewmodel.ToolsViewModel

@Composable
fun ImageToPdfScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onCreated: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var outputTitle by remember { mutableStateOf("Photos_Collection") }
    var pageSize by remember { mutableStateOf(PageSizePreset.A4) }
    var isLandscape by remember { mutableStateOf(false) }
    var marginPt by remember { mutableFloatStateOf(24f) }
    var showPreview by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(25)) { uris ->
        selectedUris.addAll(uris)
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Image to PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f).testTag("img2pdf_pick_btn")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Photos")
                    }
                    OutlinedButton(onClick = onOpenScanner, modifier = Modifier.weight(1f)) {
                        Text("Use Camera")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = outputTitle,
                    onValueChange = { outputTitle = it },
                    label = { Text("PDF File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Page Size", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageSizePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = pageSize == preset,
                            onClick = { pageSize = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Orientation", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isLandscape, onClick = { isLandscape = false }, label = { Text("Portrait") })
                    FilterChip(selected = isLandscape, onClick = { isLandscape = true }, label = { Text("Landscape") })
                }
                Spacer(Modifier.height(6.dp))
                Text("Margins: ${marginPt.toInt()} pt", style = MaterialTheme.typography.labelLarge)
                Slider(value = marginPt, onValueChange = { marginPt = it }, valueRange = 0f..56f)
            }

            itemsIndexed(selectedUris) { idx, uri ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected image ${idx + 1}",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Page ${idx + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val item = selectedUris.removeAt(idx)
                                    selectedUris.add(idx - 1, item)
                                }
                            },
                            enabled = idx > 0
                        ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                        IconButton(
                            onClick = {
                                if (idx < selectedUris.lastIndex) {
                                    val item = selectedUris.removeAt(idx)
                                    selectedUris.add(idx + 1, item)
                                }
                            },
                            enabled = idx < selectedUris.lastIndex
                        ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                        IconButton(onClick = { selectedUris.removeAt(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showPreview = true },
                        enabled = selectedUris.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Preview")
                    }
                    Button(
                        onClick = {
                            viewModel.createPdfFromImages(selectedUris.toList(), outputTitle, pageSize, isLandscape, marginPt, onCreated)
                        },
                        enabled = selectedUris.isNotEmpty() && !state.isBusy,
                        modifier = Modifier.weight(1f).testTag("img2pdf_generate_btn")
                    ) {
                        if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Generate PDF")
                    }
                }
            }
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("PDF Layout Preview") },
            text = {
                Text(
                    "Title: $outputTitle.pdf\n" +
                        "Pages: ${selectedUris.size}\n" +
                        "Page Size: ${pageSize.label} (${if (isLandscape) "Landscape" else "Portrait"})\n" +
                        "Margins: ${marginPt.toInt()} pt"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPreview = false
                    viewModel.createPdfFromImages(selectedUris.toList(), outputTitle, pageSize, isLandscape, marginPt, onCreated)
                }) { Text("Save PDF") }
            },
            dismissButton = {
                TextButton(onClick = { showPreview = false }) { Text("Close") }
            }
        )
    }
}
