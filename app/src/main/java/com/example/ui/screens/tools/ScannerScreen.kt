package com.example.ui.screens.tools

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.pdf.ScanFilterMode
import com.example.data.pdf.ScanQuad
import com.example.ui.viewmodel.ToolsViewModel
import kotlin.math.hypot

@Composable
fun ScannerScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onSavedToReader: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { viewModel.addScannedBitmap(it) }
            }
        }
    }

    var isCameraMode by remember { mutableStateOf(true) }
    var selectedPageIdx by remember { mutableIntStateOf(0) }
    var docTitle by remember { mutableStateOf("Scanned_Document") }
    var showCropEditor by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scanner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Smart Document Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (state.scannedPages.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.exportScannedPagesToPdf(docTitle, onSavedToReader) },
                            enabled = !state.isBusy,
                            modifier = Modifier.testTag("scanner_export_pdf_btn")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save (${state.scannedPages.size})")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Mode Switch: Camera Capture vs Review & Enhance
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isCameraMode,
                    onClick = { isCameraMode = true },
                    label = { Text("Capture") },
                    leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = !isCameraMode,
                    onClick = { isCameraMode = false },
                    label = { Text("Enhance & Crop (${state.scannedPages.size})") },
                    enabled = state.scannedPages.isNotEmpty()
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        isCameraMode = false
                    },
                    modifier = Modifier.testTag("scanner_gallery_btn")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }

            if (isCameraMode) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp))) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    runCatching {
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageCapture
                                        )
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Visual document framing guide overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val padX = size.width * 0.08f
                            val padY = size.height * 0.1f
                            drawRect(
                                color = Color(0xAA38BDF8),
                                topLeft = Offset(padX, padY),
                                size = androidx.compose.ui.geometry.Size(size.width - padX * 2, size.height - padY * 2),
                                style = Stroke(width = 4f)
                            )
                        }
                        FloatingActionButton(
                            onClick = {
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bmp = imageProxyToBitmap(image)
                                            image.close()
                                            if (bmp != null) {
                                                viewModel.addScannedBitmap(bmp)
                                                isCameraMode = false
                                                selectedPageIdx = state.scannedPages.size
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) = Unit
                                    }
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).testTag("scanner_shutter_btn")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Capture Page", tint = Color.White)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Camera permission is required for live document scanning.", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Grant Camera Permission")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Or tap 'Import' above to scan an existing photo.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                val safeIdx = selectedPageIdx.coerceIn(0, (state.scannedPages.size - 1).coerceAtLeast(0))
                val activePage = state.scannedPages.getOrNull(safeIdx)
                if (activePage == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No scanned pages yet.")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Output PDF Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (showCropEditor) {
                            var quad by remember(activePage.id) { mutableStateOf(activePage.quad) }
                            var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
                            Text("Drag corners to adjust perspective crop", style = MaterialTheme.typography.labelMedium)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(activePage.originalBitmap.width.toFloat() / activePage.originalBitmap.height.coerceAtLeast(1))
                                    .clip(RoundedCornerShape(12.dp))
                                    .onSizeChanged { boxSize = it }
                                    .pointerInput(activePage.id) {
                                        var activeCorner = -1
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                val nx = start.x / boxSize.width.coerceAtLeast(1)
                                                val ny = start.y / boxSize.height.coerceAtLeast(1)
                                                val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
                                                activeCorner = corners.indices.minByOrNull { i ->
                                                    hypot(corners[i].x - nx, corners[i].y - ny)
                                                } ?: -1
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val nx = (change.position.x / boxSize.width.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
                                                val ny = (change.position.y / boxSize.height.coerceAtLeast(1)).coerceIn(0.02f, 0.98f)
                                                quad = when (activeCorner) {
                                                    0 -> quad.copy(topLeft = PointF(nx, ny))
                                                    1 -> quad.copy(topRight = PointF(nx, ny))
                                                    2 -> quad.copy(bottomRight = PointF(nx, ny))
                                                    3 -> quad.copy(bottomLeft = PointF(nx, ny))
                                                    else -> quad
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Image(
                                    bitmap = activePage.originalBitmap.asImageBitmap(),
                                    contentDescription = "Original Scan",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val pts = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).map {
                                        Offset(it.x * size.width, it.y * size.height)
                                    }
                                    val path = Path().apply {
                                        moveTo(pts[0].x, pts[0].y)
                                        lineTo(pts[1].x, pts[1].y)
                                        lineTo(pts[2].x, pts[2].y)
                                        lineTo(pts[3].x, pts[3].y)
                                        close()
                                    }
                                    drawPath(path, Color(0xFF2563EB), style = Stroke(width = 5f))
                                    pts.forEach { p -> drawCircle(Color(0xFF38BDF8), radius = 16f, center = p) }
                                }
                            }
                            Button(
                                onClick = {
                                    viewModel.updateScanPageSettings(safeIdx, quad, activePage.filter, activePage.brightness, activePage.contrast)
                                    showCropEditor = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Apply Perspective Crop") }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = activePage.processedBitmap.asImageBitmap(),
                                    contentDescription = "Enhanced Scan",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showCropEditor = !showCropEditor }) {
                                Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showCropEditor) "Cancel Crop" else "Manual Crop")
                            }
                            OutlinedButton(onClick = { viewModel.removeScanPage(safeIdx) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove Page")
                            }
                        }

                        Text("Enhancement Filter", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ScanFilterMode.entries) { _, mode ->
                                FilterChip(
                                    selected = activePage.filter == mode,
                                    onClick = {
                                        viewModel.updateScanPageSettings(safeIdx, activePage.quad, mode, activePage.brightness, activePage.contrast)
                                    },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        Text("Brightness: ${activePage.brightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = activePage.brightness,
                            onValueChange = { b ->
                                viewModel.updateScanPageSettings(safeIdx, activePage.quad, activePage.filter, b, activePage.contrast)
                            },
                            valueRange = -60f..60f
                        )
                        Text("Contrast: ${String.format(java.util.Locale.US, "%.2f", activePage.contrast)}", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = activePage.contrast,
                            onValueChange = { c ->
                                viewModel.updateScanPageSettings(safeIdx, activePage.quad, activePage.filter, activePage.brightness, c)
                            },
                            valueRange = 0.6f..1.8f
                        )

                        // Scanned page thumbnails strip
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            itemsIndexed(state.scannedPages) { idx, page ->
                                Image(
                                    bitmap = page.processedBitmap.asImageBitmap(),
                                    contentDescription = "Scan page ${idx + 1}",
                                    modifier = Modifier
                                        .size(64.dp, 84.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (idx == safeIdx) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .padding(if (idx == safeIdx) 2.dp else 0.dp)
                                        .clickable { selectedPageIdx = idx },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (state.isBusy) {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}
