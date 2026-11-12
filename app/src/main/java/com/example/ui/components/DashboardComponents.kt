package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentType
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.WarningAmber

enum class DashboardCategory(
    val label: String,
    val icon: ImageVector,
    val tag: String
) {
    ALL("All", Icons.Filled.Apps, "dashboard_cat_all"),
    PDF("PDF", Icons.Filled.PictureAsPdf, "dashboard_cat_pdf"),
    SCANNED("Scanned", Icons.Filled.DocumentScanner, "dashboard_cat_scanned"),
    WORD("Word", Icons.Filled.Description, "dashboard_cat_word"),
    SHEETS("Sheets", Icons.Filled.TableChart, "dashboard_cat_sheets"),
    SLIDES("Slides", Icons.Filled.Slideshow, "dashboard_cat_slides"),
    STARRED("Starred", Icons.Filled.Star, "dashboard_cat_starred")
}

fun DocumentEntity.matchesCategory(category: DashboardCategory): Boolean = when (category) {
    DashboardCategory.ALL -> true
    DashboardCategory.PDF -> docType == DocumentType.PDF.name
    DashboardCategory.SCANNED -> docType == DocumentType.SCANNED_PDF.name
    DashboardCategory.WORD -> docType == DocumentType.DOCX.name || docType == DocumentType.TXT.name
    DashboardCategory.SHEETS -> docType == DocumentType.XLSX.name
    DashboardCategory.SLIDES -> docType == DocumentType.PPTX.name
    DashboardCategory.STARRED -> isStarred
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFabMenuSheet(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onImportClick: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenImageToPdf: () -> Unit,
    onOpenWebAndNotes: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text(
                text = "Create or Import",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Import PDF, Word, Excel, PPT, TXT") },
                leadingContent = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onImportClick() }
            )
            ListItem(
                headlineContent = { Text(strings.toolScanner) },
                leadingContent = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onOpenScanner() }
            )
            ListItem(
                headlineContent = { Text(strings.toolImageToPdf) },
                leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onOpenImageToPdf() }
            )
            ListItem(
                headlineContent = { Text("${strings.toolWebToPdf} & ${strings.toolNotesToPdf}") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onOpenWebAndNotes() }
            )
        }
    }
}

@Composable
fun StorageOverviewCard(
    docCount: Int,
    starredCount: Int,
    storageUsedBytes: Long,
    storageLabel: String
) {
    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xD90F172A), Color(0x991E3A8A))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = storageLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$docCount docs • $starredCount starred • ${formatFileSize(storageUsedBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE0E7FF)
                    )
                }
                val quotaBytes = (512L * 1024L * 1024L).toFloat()
                val progress = (storageUsedBytes / quotaBytes).coerceIn(0.03f, 1f)
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape),
                        color = Color(0xFF38BDF8),
                        trackColor = Color.White.copy(alpha = 0.24f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Offline-first workspace • AES-256 Keystore ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

@Composable
fun CountPill(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun RecentDocumentCard(
    doc: DocumentEntity,
    pagesLabel: String,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit
) {
    val totalPages = doc.pageCount.coerceAtLeast(1)
    val currentPage = (doc.lastViewedPage + 1).coerceIn(1, totalPages)
    val readingProgress = (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0.08f, 1f)

    ElevatedCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(216.dp)
            .testTag("recent_doc_${doc.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentTypeBadge(
                        docType = doc.docType,
                        isProtected = doc.isPasswordProtected,
                        modifier = Modifier.size(38.dp)
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (doc.docType == DocumentType.SCANNED_PDF.name) "SCAN" else doc.docType,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recent_star_${doc.id}")
                ) {
                    Icon(
                        imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (doc.isStarred) "Unstar" else "Star",
                        tint = if (doc.isStarred) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = doc.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { readingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Page $currentPage/$totalPages $pagesLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatShortDate(maxOf(doc.lastOpenedAt, doc.modifiedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
