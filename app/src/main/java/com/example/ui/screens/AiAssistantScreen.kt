package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.SummaryMode
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBrandGradient
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AiViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiViewModel,
    language: AppLanguage,
    onOpenPageInReader: (Long, Int) -> Unit
) {
    val strings = LocalAppStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }
    var questionInput by remember { mutableStateOf("") }

    LaunchedEffect(docs) {
        if (state.selectedDocument == null && docs.isNotEmpty()) {
            viewModel.selectDocument(docs.first())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryBrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(strings.aiTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Required in-app notice when Firebase is disabled / placeholder AI active
        if (state.isPlaceholderMode) {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("ai_firebase_notice")
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(strings.aiNoticeTitle, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(strings.aiNoticeBody, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Selected Document", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(docs, key = { it.id }) { doc ->
                FilterChip(
                    selected = state.selectedDocument?.id == doc.id,
                    onClick = { viewModel.selectDocument(doc) },
                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(doc.title, maxLines = 1) },
                    modifier = Modifier.testTag("ai_doc_chip_${doc.id}")
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        TabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text(strings.aiSummaryTab) },
                modifier = Modifier.testTag("ai_tab_summary")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text(strings.aiChatTab) },
                modifier = Modifier.testTag("ai_tab_chat")
            )
        }

        Spacer(Modifier.height(12.dp))

        if (activeTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.selectedSummaryMode == mode,
                                onClick = { viewModel.generateSummary(mode, language) },
                                label = { Text(if (language == AppLanguage.BENGALI) mode.bnLabel else mode.label) },
                                modifier = Modifier.testTag("summary_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.generateSummary(state.selectedSummaryMode, language) },
                        enabled = state.selectedDocument != null && !state.isGenerating,
                        modifier = Modifier.fillMaxWidth().testTag("ai_generate_summary_btn")
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Generate ${state.selectedSummaryMode.label}")
                    }
                }

                if (state.summaryOutput.isNotBlank()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(state.summaryOutput, style = MaterialTheme.typography.bodyMedium)
                                if (state.summaryCitedPages.isNotEmpty() && state.selectedDocument != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Referenced Pages:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        state.summaryCitedPages.forEach { pageNum ->
                                            AssistChip(
                                                onClick = {
                                                    onOpenPageInReader(state.selectedDocument!!.id, (pageNum - 1).coerceAtLeast(0))
                                                },
                                                label = { Text("Page $pageNum") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Chat With PDF
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val quickPrompts = if (language == AppLanguage.BENGALI) {
                            listOf("মূল বিষয় কী?", "গুরুত্বপূর্ণ তথ্য বের করুন", "প্রথম অনুচ্ছেদ ব্যাখ্যা করুন")
                        } else {
                            listOf("What is the main topic?", "Extract key dates & metrics", "Explain the key findings")
                        }
                        items(quickPrompts) { prompt ->
                            AssistChip(
                                onClick = { viewModel.sendChatMessage(prompt, language) },
                                label = { Text(prompt) }
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear chat")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(chatMessages, key = { it.id }) { msg ->
                        val bg = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bg),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (msg.isUser) "You" else "DocuVerse AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(msg.message, style = MaterialTheme.typography.bodyMedium)
                                if (!msg.isUser && msg.pageReferences.isNotBlank() && state.selectedDocument != null) {
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        msg.pageReferences.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { p ->
                                            AssistChip(
                                                onClick = {
                                                    onOpenPageInReader(state.selectedDocument!!.id, (p - 1).coerceAtLeast(0))
                                                },
                                                label = { Text("Jump to Page $p") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = { Text(strings.aiAskHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("ai_chat_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (questionInput.isNotBlank()) {
                                viewModel.sendChatMessage(questionInput, language)
                                questionInput = ""
                            }
                        },
                        enabled = state.selectedDocument != null && !state.isGenerating,
                        modifier = Modifier.testTag("ai_chat_send_btn")
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}
