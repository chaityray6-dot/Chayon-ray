package com.example.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.DocumentEntity
import com.example.data.pdf.ImagePageSpec
import com.example.data.pdf.PageSizePreset
import com.example.ui.components.formatFileSize
import com.example.ui.viewmodel.ToolsViewModel
import kotlin.math.hypot

private data class DraftImageItem(
    val id: Long = System.nanoTime(),
    val uri: Uri,
    val leftNorm: Float = 0f,
    val topNorm: Float = 0f,
    val rightNorm: Float = 1f,
    val bottomNorm: Float = 1f,
    val rotationDegrees: Int = 0
) {
    val isCroppedOrRotated: Boolean
        get() = rotationDegrees % 360 != 0 ||
            leftNorm > 0.01f || topNorm > 0.01f ||
            rightNorm < 0.99f || bottomNorm < 0.99f
}

@Composable
fun ImageToPdfScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onCreated: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems = remember { mutableStateListOf<DraftImageItem>() }
    var outputTitle by remember { mutableStateOf("Photos_Collection") }
    var pageSize by remember { mutableStateOf(PageSizePreset.A4) }
    var isLandscape by remember { mutableStateOf(false) }
    var marginPt by remember { mutableFloatStateOf(24f) }
    var enhanceClearText by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var editingCropIndex by remember { mutableStateOf<Int?>(null) }
    var generatedDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(25)) { uris ->
        selectedItems.addAll(uris.map { DraftImageItem(uri = it) })
        if (uris.isNotEmpty()) generatedDoc = null
    }

    fun generatePdfNow() {
        val specs = selectedItems.map { item ->
            ImagePageSpec(
                uri = item.uri,
                leftNorm = item.leftNorm,
                topNorm = item.topNorm,
                rightNorm = item.rightNorm,
                bottomNorm = item.bottomNorm,
                rotationDegrees = item.rotationDegrees,
                enhanceClearText = enhanceClearText
            )
        }
        viewModel.createPdfFromImageSpecs(
            specs = specs,
            outputTitle = outputTitle,
            pageSize = pageSize,
            landscape = isLandscape,
            marginPt = marginPt,
            onDone = { doc -> generatedDoc = doc }
        )
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
                        modifier = Modifier.fillMaxWidth().testTag("img2pdf_result_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PDF Generated Successfully!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                                    modifier = Modifier.weight(1f).testTag("img2pdf_share_generated_btn")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share PDF")
                                }
                                OutlinedButton(
                                    onClick = { onCreated(created) },
                                    modifier = Modifier.weight(1f).testTag("img2pdf_open_generated_btn")
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Sharpen Blurry Text (HD)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Boost text clarity & contrast for document photos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = enhanceClearText,
                            onCheckedChange = { enhanceClearText = it },
                            modifier = Modifier.testTag("img2pdf_sharpen_switch")
                        )
                    }
                }
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

            itemsIndexed(selectedItems, key = { _, item -> item.id }) { idx, item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = "Selected image ${idx + 1}",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Page ${idx + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (item.isCroppedOrRotated) {
                                Text(
                                    "Cropped • ${item.rotationDegrees}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { editingCropIndex = idx },
                            modifier = Modifier.testTag("img2pdf_crop_btn_$idx")
                        ) {
                            Icon(Icons.Filled.Crop, contentDescription = "Crop or rotate image")
                        }
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val moved = selectedItems.removeAt(idx)
                                    selectedItems.add(idx - 1, moved)
                                }
                            },
                            enabled = idx > 0
                        ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                        IconButton(
                            onClick = {
                                if (idx < selectedItems.lastIndex) {
                                    val moved = selectedItems.removeAt(idx)
                                    selectedItems.add(idx + 1, moved)
                                }
                            },
                            enabled = idx < selectedItems.lastIndex
                        ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                        IconButton(onClick = { selectedItems.removeAt(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showPreview = true },
                        enabled = selectedItems.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Preview")
                    }
                    Button(
                        onClick = { generatePdfNow() },
                        enabled = selectedItems.isNotEmpty() && !state.isBusy,
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

    editingCropIndex?.let { idx ->
        val item = selectedItems.getOrNull(idx)
        if (item != null) {
            ImageCropDialog(
                item = item,
                decodeBitmap = { viewModel.decodeImagePreviewBitmap(item.uri) },
                onDismiss = { editingCropIndex = null },
                onApply = { updated ->
                    if (idx in selectedItems.indices) {
                        selectedItems[idx] = updated
                    }
                    editingCropIndex = null
                }
            )
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("PDF Layout Preview") },
            text = {
                Text(
                    "Title: $outputTitle.pdf\n" +
                        "Pages: ${selectedItems.size}\n" +
                        "Page Size: ${pageSize.label} (${if (isLandscape) "Landscape" else "Portrait"})\n" +
                        "Margins: ${marginPt.toInt()} pt\n" +
                        "Sharpen Blurry Text: ${if (enhanceClearText) "Enabled" else "Off"}"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPreview = false
                    generatePdfNow()
                }) { Text("Generate PDF") }
            },
            dismissButton = {
                TextButton(onClick = { showPreview = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ImageCropDialog(
    item: DraftImageItem,
    decodeBitmap: suspend () -> Bitmap?,
    onDismiss: () -> Unit,
    onApply: (DraftImageItem) -> Unit
) {
    val rawBitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = decodeBitmap()
    }
    var left by remember(item.id) { mutableFloatStateOf(item.leftNorm) }
    var top by remember(item.id) { mutableFloatStateOf(item.topNorm) }
    var right by remember(item.id) { mutableFloatStateOf(item.rightNorm) }
    var bottom by remember(item.id) { mutableFloatStateOf(item.bottomNorm) }
    var rotation by remember(item.id) { mutableIntStateOf(item.rotationDegrees) }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }

    val rotatedBitmap = remember(rawBitmap, rotation) {
        val base = rawBitmap ?: return@remember null
        if (rotation % 360 == 0) base
        else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Crop & Rotate Image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Drag any corner handle to crop the document area.", style = MaterialTheme.typography.bodySmall)

                if (rotatedBitmap == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val aspect = (rotatedBitmap.width.toFloat() / rotatedBitmap.height.coerceAtLeast(1)).coerceAtLeast(0.1f)
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxPreviewH = 300.dp
                        val candidateW = maxWidth
                        val candidateH = candidateW / aspect
                        val (frameW, frameH) = if (candidateH <= maxPreviewH) {
                            candidateW to candidateH
                        } else {
                            (maxPreviewH * aspect).coerceAtMost(maxWidth) to maxPreviewH
                        }
                        Box(
                            modifier = Modifier
                                .size(frameW, frameH)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .onSizeChanged { boxSize = it }
                                .pointerInput(Unit) {
                                var activeCorner = -1
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val nx = start.x / boxSize.width.coerceAtLeast(1)
                                        val ny = start.y / boxSize.height.coerceAtLeast(1)
                                        val corners = listOf(
                                            left to top,
                                            right to top,
                                            right to bottom,
                                            left to bottom
                                        )
                                        activeCorner = corners.indices.minByOrNull { idx ->
                                            hypot(corners[idx].first - nx, corners[idx].second - ny)
                                        } ?: -1
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nx = (change.position.x / boxSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (change.position.y / boxSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        when (activeCorner) {
                                            0 -> {
                                                left = nx.coerceAtMost(right - 0.12f)
                                                top = ny.coerceAtMost(bottom - 0.12f)
                                            }
                                            1 -> {
                                                right = nx.coerceAtLeast(left + 0.12f)
                                                top = ny.coerceAtMost(bottom - 0.12f)
                                            }
                                            2 -> {
                                                right = nx.coerceAtLeast(left + 0.12f)
                                                bottom = ny.coerceAtLeast(top + 0.12f)
                                            }
                                            3 -> {
                                                left = nx.coerceAtMost(right - 0.12f)
                                                bottom = ny.coerceAtLeast(top + 0.12f)
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = rotatedBitmap.asImageBitmap(),
                            contentDescription = "Crop preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lPx = left * size.width
                            val tPx = top * size.height
                            val rPx = right * size.width
                            val bPx = bottom * size.height
                            // Dim outer crop area slightly
                            drawRect(Color.Black.copy(alpha = 0.42f), Offset.Zero, Size(size.width, tPx))
                            drawRect(Color.Black.copy(alpha = 0.42f), Offset(0f, bPx), Size(size.width, size.height - bPx))
                            drawRect(Color.Black.copy(alpha = 0.42f), Offset(0f, tPx), Size(lPx, bPx - tPx))
                            drawRect(Color.Black.copy(alpha = 0.42f), Offset(rPx, tPx), Size(size.width - rPx, bPx - tPx))

                            drawRect(
                                color = Color(0xFF38BDF8),
                                topLeft = Offset(lPx, tPx),
                                size = Size(rPx - lPx, bPx - tPx),
                                style = Stroke(width = 4f)
                            )
                            listOf(
                                Offset(lPx, tPx),
                                Offset(rPx, tPx),
                                Offset(rPx, bPx),
                                Offset(lPx, bPx)
                            ).forEach { corner ->
                                drawCircle(Color(0xFF2563EB), radius = 16f, center = corner)
                                drawCircle(Color.White, radius = 9f, center = corner)
                            }
                        }
                    }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { rotation = (rotation + 90) % 360 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rotate 90°")
                    }
                    OutlinedButton(
                        onClick = { left = 0f; top = 0f; right = 1f; bottom = 1f },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Crop")
                    }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApply(
                                item.copy(
                                    leftNorm = left,
                                    topNorm = top,
                                    rightNorm = right,
                                    bottomNorm = bottom,
                                    rotationDegrees = rotation
                                )
                            )
                        },
                        modifier = Modifier.testTag("img2pdf_apply_crop_btn")
                    ) {
                        Text("Apply Crop")
                    }
                }
            }
        }
    }
}
