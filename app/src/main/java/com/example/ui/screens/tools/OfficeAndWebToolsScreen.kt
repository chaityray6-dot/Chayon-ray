package com.example.ui.screens.tools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.converter.OfficeFormat
import com.example.data.local.DocumentEntity
import com.example.ui.viewmodel.ToolsViewModel

@Composable
fun OfficeConverterScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onPdfCreated: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    var selectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var targetFormat by remember { mutableStateOf(OfficeFormat.DOCX) }

    val officePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Office_File"
            val format = when {
                mime.contains("word") || name.endsWith(".docx", true) -> OfficeFormat.DOCX
                mime.contains("sheet") || name.endsWith(".xlsx", true) -> OfficeFormat.XLSX
                mime.contains("presentation") || name.endsWith(".pptx", true) -> OfficeFormat.PPTX
                else -> OfficeFormat.TXT
            }
            viewModel.convertOfficeToPdf(uri, name, format, onPdfCreated)
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Office & Text Converter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1. Convert Office / Text to PDF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Supports real OOXML parsing for .docx, .xlsx, .pptx, and .txt files.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                officePicker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                        "text/plain"
                                    )
                                )
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("office_to_pdf_btn")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select DOCX / XLSX / PPTX / TXT")
                        }
                    }
                }
            }

            item {
                Text("2. Export PDF to Office / Text", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfficeFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = targetFormat == fmt,
                            onClick = { targetFormat = fmt },
                            label = { Text(fmt.ext.uppercase()) }
                        )
                    }
                }
            }

            items(docs, key = { it.id }) { doc ->
                Card(
                    onClick = { selectedDoc = doc },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(doc.title, modifier = Modifier.weight(1f), fontWeight = if (selectedDoc?.id == doc.id) FontWeight.Bold else FontWeight.Normal)
                        Button(
                            onClick = { viewModel.convertPdfToOffice(doc, targetFormat) },
                            enabled = !state.isBusy
                        ) {
                            Text("→ ${targetFormat.ext.uppercase()}")
                        }
                    }
                }
            }

            state.lastExportedOfficeFile?.let { file ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exported: ${file.name}", fontWeight = FontWeight.Bold)
                                Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebAndNotesToPdfScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onPdfCreated: (DocumentEntity) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var urlInput by remember { mutableStateOf("https://en.wikipedia.org/wiki/PDF") }
    var activeUrl by remember { mutableStateOf("https://en.wikipedia.org/wiki/PDF") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webPageTitle by remember { mutableStateOf("Webpage_Article") }

    var noteTitle by remember { mutableStateOf("Meeting Notes") }
    var noteBody by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Webpage & Notes to PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Webpage to PDF") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notes to PDF") })
            }

            if (tab == 0) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("HTTPS Webpage URL") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                            modifier = Modifier.weight(1f).testTag("web2pdf_url_input")
                        )
                        Button(onClick = {
                            val clean = if (urlInput.startsWith("http")) urlInput else "https://$urlInput"
                            activeUrl = clean
                            webViewRef?.loadUrl(clean)
                        }) { Text("Load") }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        webPageTitle = view?.title?.take(40) ?: "Webpage_Article"
                                    }
                                }
                                loadUrl(activeUrl)
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val wv = webViewRef ?: return@Button
                            val w = wv.width.coerceAtLeast(800)
                            val totalH = (wv.contentHeight * wv.scale).toInt().coerceAtLeast(1000).coerceAtMost(8000)
                            val fullBmp = Bitmap.createBitmap(w, totalH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(fullBmp)
                            wv.draw(canvas)
                            val pageH = (w * 1.414f).toInt()
                            val slices = mutableListOf<Bitmap>()
                            var y = 0
                            while (y < fullBmp.height) {
                                val sliceH = minOf(pageH, fullBmp.height - y)
                                slices.add(Bitmap.createBitmap(fullBmp, 0, y, w, sliceH))
                                y += sliceH
                            }
                            fullBmp.recycle()
                            viewModel.saveRenderedWebBitmapsToPdf(webPageTitle, slices, onPdfCreated)
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("web2pdf_save_btn")
                    ) {
                        if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Webpage as PDF")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Document Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = { Text("Write or paste your article/notes...") },
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("notes2pdf_body_input")
                    )
                    Button(
                        onClick = { viewModel.createPdfFromText(noteTitle, noteBody, onPdfCreated) },
                        enabled = noteBody.isNotBlank() && !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("notes2pdf_save_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Convert Notes to PDF")
                    }
                }
            }
        }
    }
}
