package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AnnotationToolType
import com.example.data.local.DocumentEntity
import com.example.data.pdf.NormalizedPoint
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onExportedToReader: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val activeDragPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var textDialogPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textDialogIsNote by remember { mutableStateOf(false) }
    var textDialogInput by remember { mutableStateOf("") }
    var showSignatureSheet by remember { mutableStateOf(false) }
    var showFormSheet by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.insertImageUri(uri)
        }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val pageBitmap by produceState<Bitmap?>(initialValue = null, state.activeFile, state.currentPage) {
        value = viewModel.renderPageBitmap(state.currentPage, 1080)
    }

    val pageAnnotations = remember(annotations, state.currentPage) {
        annotations.filter { it.pageNumber == state.currentPage }
    }

    val colorChoices = listOf(
        0xFFFACC15L, // Highlighter yellow
        0xFFEF4444L, // Red
        0xFF2563EBL, // Royal Blue
        0xFF10B981L, // Emerald
        0xFF7C3AEDL, // Violet
        0xFF0F172AL  // Ink Black
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "PDF Editor & Annotations",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Page ${state.currentPage + 1}/${state.pageCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.undo() }, enabled = state.canUndo, modifier = Modifier.testTag("editor_undo_btn")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.canRedo, modifier = Modifier.testTag("editor_redo_btn")) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    if (state.formFields.isNotEmpty()) {
                        IconButton(onClick = { showFormSheet = true }, modifier = Modifier.testTag("editor_forms_btn")) {
                            Icon(Icons.Filled.Assignment, contentDescription = "Fill PDF Form")
                        }
                    }
                    Button(
                        onClick = { viewModel.exportCompletedPdf(onExportedToReader) },
                        enabled = !state.isSaving,
                        modifier = Modifier.padding(end = 6.dp).testTag("editor_save_btn")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                    // Color + Thickness + Page Prev/Next
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorChoices.forEach { argb ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (state.selectedColorArgb == argb) 2.5.dp else 1.dp,
                                            color = if (state.selectedColorArgb == argb) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.selectColor(argb) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { viewModel.goToPage(state.currentPage - 1) },
                                enabled = state.currentPage > 0
                            ) { Text("Prev") }
                            TextButton(
                                onClick = { viewModel.goToPage(state.currentPage + 1) },
                                enabled = state.currentPage < state.pageCount - 1
                            ) { Text("Next") }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Size", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = state.strokeWidth,
                            onValueChange = { viewModel.setStrokeWidth(it) },
                            valueRange = 2f..18f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Annotation tools palette
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.isEraserActive,
                                onClick = { viewModel.toggleEraser() },
                                label = { Text("Eraser") },
                                leadingIcon = { Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        val tools = listOf(
                            Triple(AnnotationToolType.HIGHLIGHT, "Highlight", Icons.Filled.BorderColor),
                            Triple(AnnotationToolType.UNDERLINE, "Underline", Icons.Filled.FormatUnderlined),
                            Triple(AnnotationToolType.STRIKETHROUGH, "Strike", Icons.Filled.FormatStrikethrough),
                            Triple(AnnotationToolType.INK, "Pencil", Icons.Filled.Brush),
                            Triple(AnnotationToolType.RECTANGLE, "Box", Icons.Filled.Rectangle),
                            Triple(AnnotationToolType.CIRCLE, "Circle", Icons.Filled.Circle),
                            Triple(AnnotationToolType.ARROW, "Arrow", Icons.Filled.NorthEast),
                            Triple(AnnotationToolType.TEXT_BOX, "Text", Icons.Filled.TextFields),
                            Triple(AnnotationToolType.STICKY_NOTE, "Note", Icons.Filled.StickyNote2),
                            Triple(AnnotationToolType.SIGNATURE, "Sign", Icons.Filled.Draw),
                            Triple(AnnotationToolType.IMAGE, "Image", Icons.Filled.Image)
                        )
                        items(tools) { (tool, label, icon) ->
                            FilterChip(
                                selected = !state.isEraserActive && state.activeTool == tool,
                                onClick = {
                                    when (tool) {
                                        AnnotationToolType.SIGNATURE -> showSignatureSheet = true
                                        AnnotationToolType.IMAGE -> imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                        else -> viewModel.selectTool(tool)
                                    }
                                },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("tool_${tool.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pageBitmap == null) {
                CircularProgressIndicator()
            } else {
                val aspect = pageBitmap!!.width.toFloat() / pageBitmap!!.height.coerceAtLeast(1)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(state.activeTool, state.isEraserActive, state.currentPage) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val ny = (offset.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                if (state.isEraserActive) {
                                    viewModel.eraseNearestAnnotation(nx, ny)
                                } else if (state.activeTool == AnnotationToolType.TEXT_BOX || state.activeTool == AnnotationToolType.STICKY_NOTE) {
                                    textDialogPos = nx to ny
                                    textDialogIsNote = state.activeTool == AnnotationToolType.STICKY_NOTE
                                    textDialogInput = ""
                                }
                            }
                        }
                        .pointerInput(state.activeTool, state.isEraserActive, state.currentPage) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    activeDragPoints.clear()
                                    val nx = (start.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (start.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    if (state.isEraserActive) {
                                        viewModel.eraseNearestAnnotation(nx, ny)
                                    } else {
                                        activeDragPoints.add(NormalizedPoint(nx, ny))
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (change.position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    if (state.isEraserActive) {
                                        viewModel.eraseNearestAnnotation(nx, ny)
                                    } else {
                                        activeDragPoints.add(NormalizedPoint(nx, ny))
                                    }
                                },
                                onDragEnd = {
                                    if (!state.isEraserActive && activeDragPoints.size >= 2) {
                                        viewModel.addStrokeAnnotation(activeDragPoints.toList())
                                    }
                                    activeDragPoints.clear()
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "Editable PDF Page",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    // Vector annotations overlay
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        fun drawAnnotation(
                            tool: AnnotationToolType,
                            color: Color,
                            strokeW: Float,
                            pts: List<NormalizedPoint>
                        ) {
                            if (pts.size < 2) return
                            when (tool) {
                                AnnotationToolType.HIGHLIGHT -> {
                                    val minX = pts.minOf { it.x } * w
                                    val maxX = pts.maxOf { it.x } * w
                                    val avgY = pts.map { it.y }.average().toFloat() * h
                                    val barH = (strokeW * 3.5f).coerceIn(14f, 44f)
                                    drawRect(
                                        color = color.copy(alpha = 0.36f),
                                        topLeft = Offset(minX, avgY - barH / 2f),
                                        size = Size((maxX - minX).coerceAtLeast(12f), barH)
                                    )
                                }
                                AnnotationToolType.UNDERLINE, AnnotationToolType.STRIKETHROUGH -> {
                                    val avgY = pts.map { it.y }.average().toFloat() * h
                                    drawLine(
                                        color = color,
                                        start = Offset(pts.first().x * w, avgY),
                                        end = Offset(pts.last().x * w, avgY),
                                        strokeWidth = strokeW
                                    )
                                }
                                AnnotationToolType.INK -> {
                                    val path = Path().apply {
                                        moveTo(pts.first().x * w, pts.first().y * h)
                                        for (i in 1 until pts.size) lineTo(pts[i].x * w, pts[i].y * h)
                                    }
                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                                AnnotationToolType.RECTANGLE -> {
                                    val x1 = pts.first().x * w
                                    val y1 = pts.first().y * h
                                    val x2 = pts.last().x * w
                                    val y2 = pts.last().y * h
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(minOf(x1, x2), minOf(y1, y2)),
                                        size = Size(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1)),
                                        style = Stroke(width = strokeW)
                                    )
                                }
                                AnnotationToolType.CIRCLE -> {
                                    val cx = ((pts.first().x + pts.last().x) / 2f) * w
                                    val cy = ((pts.first().y + pts.last().y) / 2f) * h
                                    val r = kotlin.math.hypot((pts.last().x - pts.first().x) * w, (pts.last().y - pts.first().y) * h) / 2f
                                    drawCircle(
                                        color = color,
                                        radius = r.coerceAtLeast(8f),
                                        center = Offset(cx, cy),
                                        style = Stroke(width = strokeW)
                                    )
                                }
                                AnnotationToolType.ARROW -> {
                                    val x1 = pts.first().x * w
                                    val y1 = pts.first().y * h
                                    val x2 = pts.last().x * w
                                    val y2 = pts.last().y * h
                                    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeW)
                                    val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                                    val head = 24.0
                                    drawLine(
                                        color,
                                        Offset(x2, y2),
                                        Offset((x2 - head * cos(angle - 0.45)).toFloat(), (y2 - head * sin(angle - 0.45)).toFloat()),
                                        strokeWidth = strokeW
                                    )
                                    drawLine(
                                        color,
                                        Offset(x2, y2),
                                        Offset((x2 - head * cos(angle + 0.45)).toFloat(), (y2 - head * sin(angle + 0.45)).toFloat()),
                                        strokeWidth = strokeW
                                    )
                                }
                                else -> Unit
                            }
                        }

                        pageAnnotations.forEach { ann ->
                            val tool = runCatching { AnnotationToolType.valueOf(ann.toolType) }.getOrNull() ?: return@forEach
                            val pts = runCatching { Json.decodeFromString<List<NormalizedPoint>>(ann.pointsJson) }.getOrDefault(emptyList())
                            drawAnnotation(tool, Color(ann.colorArgb), ann.strokeWidth, pts)
                        }

                        if (activeDragPoints.size >= 2) {
                            drawAnnotation(
                                state.activeTool,
                                Color(state.selectedColorArgb),
                                state.strokeWidth,
                                activeDragPoints
                            )
                        }
                    }

                    // Overlay Text Boxes, Sticky Notes, Signatures, and Inserted Images
                    pageAnnotations.forEach { ann ->
                        val tool = runCatching { AnnotationToolType.valueOf(ann.toolType) }.getOrNull()
                        val ox = (ann.xNorm * canvasSize.width).roundToInt()
                        val oy = (ann.yNorm * canvasSize.height).roundToInt()
                        when (tool) {
                            AnnotationToolType.TEXT_BOX -> {
                                Text(
                                    text = ann.textContent,
                                    color = Color(ann.colorArgb),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset { IntOffset(ox, oy) }
                                )
                            }
                            AnnotationToolType.STICKY_NOTE -> {
                                Card(
                                    modifier = Modifier.offset { IntOffset(ox, oy) }.width(140.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFFEF08A))
                                ) {
                                    Text(
                                        text = ann.textContent,
                                        color = Color(0xFF1E293B),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                            AnnotationToolType.SIGNATURE, AnnotationToolType.IMAGE -> {
                                val bmp = remember(ann.extraPath) { BitmapFactory.decodeFile(ann.extraPath) }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Placed asset",
                                        modifier = Modifier
                                            .offset { IntOffset(ox, oy) }
                                            .size(width = 120.dp, height = 56.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // Text Box / Sticky Note Input Dialog
    textDialogPos?.let { (nx, ny) ->
        AlertDialog(
            onDismissRequest = { textDialogPos = null },
            title = { Text(if (textDialogIsNote) "Add Sticky Note / Comment" else "Add Text Box") },
            text = {
                OutlinedTextField(
                    value = textDialogInput,
                    onValueChange = { textDialogInput = it },
                    label = { Text(if (textDialogIsNote) "Comment text" else "Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (textDialogInput.isNotBlank()) {
                        viewModel.addPositionedAnnotation(
                            tool = if (textDialogIsNote) AnnotationToolType.STICKY_NOTE else AnnotationToolType.TEXT_BOX,
                            xNorm = nx,
                            yNorm = ny,
                            textContent = textDialogInput.trim()
                        )
                    }
                    textDialogPos = null
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { textDialogPos = null }) { Text("Cancel") }
            }
        )
    }

    // Signature Touch Pad & Saved Signatures Sheet
    if (showSignatureSheet) {
        val sigStrokes = remember { mutableStateListOf<List<NormalizedPoint>>() }
        val currentStroke = remember { mutableStateListOf<NormalizedPoint>() }
        var sigCanvasSize by remember { mutableStateOf(IntSize(1, 1)) }
        var sigName by remember { mutableStateOf("My Signature") }

        ModalBottomSheet(onDismissRequest = { showSignatureSheet = false }) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Digital Signature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (savedSignatures.isNotEmpty()) {
                    Text("Saved Signatures (Tap to Place)", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedSignatures) { sig ->
                            OutlinedButton(onClick = {
                                viewModel.addPositionedAnnotation(
                                    tool = AnnotationToolType.SIGNATURE,
                                    xNorm = 0.3f,
                                    yNorm = 0.65f,
                                    extraPath = sig.imagePath,
                                    widthNorm = 0.34f,
                                    heightNorm = 0.12f
                                )
                                showSignatureSheet = false
                            }) {
                                Text(sig.name)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = sigName,
                    onValueChange = { sigName = it },
                    label = { Text("Signature Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .onSizeChanged { sigCanvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    currentStroke.clear()
                                    currentStroke.add(
                                        NormalizedPoint(
                                            (p.x / sigCanvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (p.y / sigCanvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke.add(
                                        NormalizedPoint(
                                            (change.position.x / sigCanvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (change.position.y / sigCanvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) {
                                        sigStrokes.add(currentStroke.toList())
                                    }
                                    currentStroke.clear()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        (sigStrokes + listOf(currentStroke)).forEach { stroke ->
                            if (stroke.size >= 2) {
                                val path = Path().apply {
                                    moveTo(stroke.first().x * size.width, stroke.first().y * size.height)
                                    for (i in 1 until stroke.size) {
                                        lineTo(stroke[i].x * size.width, stroke[i].y * size.height)
                                    }
                                }
                                drawPath(path, Color(0xFF0F172A), style = Stroke(width = 6f, cap = StrokeCap.Round))
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { sigStrokes.clear(); currentStroke.clear() }) { Text("Clear") }
                    Button(onClick = {
                        if (sigStrokes.isNotEmpty()) {
                            viewModel.saveAndPlaceSignature(sigName, sigStrokes.toList())
                            showSignatureSheet = false
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save & Place")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Interactive AcroForm Filler Sheet
    if (showFormSheet) {
        ModalBottomSheet(onDismissRequest = { showFormSheet = false }) {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Interactive PDF Form Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(state.formFields) { field ->
                    OutlinedTextField(
                        value = state.formValues[field.fullyQualifiedName].orEmpty(),
                        onValueChange = { viewModel.updateFormField(field.fullyQualifiedName, it) },
                        label = { Text(field.fullyQualifiedName) },
                        enabled = !field.isReadOnly,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Button(
                        onClick = { showFormSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Apply Form Values (Tap Export to Save PDF)") }
                }
            }
        }
    }
}
