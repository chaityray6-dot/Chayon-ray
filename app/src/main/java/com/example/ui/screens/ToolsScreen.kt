package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandRoyalBlue
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.ErrorRose
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

private data class ToolHubItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
fun ToolsScreen(
    onOpenScanner: () -> Unit,
    onOpenImageToPdf: () -> Unit,
    onOpenOfficeConverter: () -> Unit,
    onOpenWebAndNotes: () -> Unit,
    onOpenMerge: () -> Unit,
    onOpenSplit: () -> Unit,
    onOpenOrganize: () -> Unit,
    onOpenCompress: () -> Unit,
    onOpenWatermark: () -> Unit,
    onOpenProtect: () -> Unit,
    onOpenOcr: () -> Unit
) {
    val strings = LocalAppStrings.current

    val items = listOf(
        ToolHubItem(strings.toolScanner, "Edge detect, perspective & filters", Icons.Filled.DocumentScanner, BrandRoyalBlue, "tool_card_scanner", onOpenScanner),
        ToolHubItem(strings.toolImageToPdf, "Gallery/Camera, page size & margins", Icons.Filled.Image, BrandViolet, "tool_card_img2pdf", onOpenImageToPdf),
        ToolHubItem(strings.toolOfficeConvert, "PDF ↔ DOCX, XLSX, PPTX, TXT", Icons.Filled.SwapHoriz, SuccessEmerald, "tool_card_office", onOpenOfficeConverter),
        ToolHubItem("${strings.toolWebToPdf} / Notes", "Render URLs or rich notes to PDF", Icons.Filled.Language, BrandIndigo, "tool_card_web2pdf", onOpenWebAndNotes),
        ToolHubItem(strings.toolMerge, "Combine 2+ PDFs into one", Icons.AutoMirrored.Filled.CallMerge, BrandRoyalBlue, "tool_card_merge", onOpenMerge),
        ToolHubItem(strings.toolSplit, "Extract selected pages", Icons.AutoMirrored.Filled.CallSplit, WarningAmber, "tool_card_split", onOpenSplit),
        ToolHubItem(strings.toolOrganize, "Reorder, rotate, duplicate, insert", Icons.Filled.ViewCarousel, BrandViolet, "tool_card_organize", onOpenOrganize),
        ToolHubItem(strings.toolCompress, "Low / Medium / High size preview", Icons.Filled.Compress, SuccessEmerald, "tool_card_compress", onOpenCompress),
        ToolHubItem(strings.toolWatermark, "Text & logo opacity/position", Icons.AutoMirrored.Filled.BrandingWatermark, BrandIndigo, "tool_card_watermark", onOpenWatermark),
        ToolHubItem(strings.toolProtect, "Password lock & decrypt PDFs", Icons.Filled.Lock, ErrorRose, "tool_card_protect", onOpenProtect),
        ToolHubItem(strings.toolOcr, "English & বাংলা text + Searchable PDF", Icons.AutoMirrored.Filled.TextSnippet, Color(0xFF0EA5E9), "tool_card_ocr", onOpenOcr)
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(strings.navTools, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Professional offline PDF creation, conversion, organization & security suite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(items) { tool ->
            Card(
                onClick = tool.onClick,
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().testTag(tool.tag)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(tool.tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(tool.icon, contentDescription = tool.title, tint = tool.tint)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tool.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tool.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
