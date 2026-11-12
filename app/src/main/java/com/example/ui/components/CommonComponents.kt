package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DocumentEntity
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandRoyalBlue
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.PrimaryBrandGradient
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun formatShortDate(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
}

@Composable
fun DocuVerseBrandHeader(modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_art),
            contentDescription = "Smart PDF Reader Logo",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DocumentTypeBadge(docType: String, isProtected: Boolean, modifier: Modifier = Modifier) {
    val bgColor = when (docType) {
        "DOCX" -> BrandRoyalBlue
        "XLSX" -> SuccessEmerald
        "PPTX" -> WarningAmber
        "SCANNED_PDF" -> BrandViolet
        else -> BrandIndigo
    }
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isProtected) Icons.Filled.Lock else if (docType == "PDF" || docType == "SCANNED_PDF") Icons.Filled.PictureAsPdf else Icons.Filled.Description,
            contentDescription = docType,
            tint = bgColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListCard(
    doc: DocumentEntity,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onOpen)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}")
            .then(clickModifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onOpen() },
                    modifier = Modifier.testTag("select_doc_${doc.id}")
                )
                Spacer(Modifier.width(6.dp))
            }
            DocumentTypeBadge(docType = doc.docType, isProtected = doc.isPasswordProtected)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${doc.pageCount} ${strings.pagesLabel} • ${formatFileSize(doc.fileSizeBytes)} • ${formatShortDate(doc.modifiedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!selectionMode) {
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("star_doc_${doc.id}")
                ) {
                    Icon(
                        imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (doc.isStarred) "Unstar" else "Star",
                        tint = if (doc.isStarred) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_doc_${doc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Document options"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridCard(
    doc: DocumentEntity,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onOpen)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_grid_card_${doc.id}")
            .then(clickModifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocumentTypeBadge(docType = doc.docType, isProtected = doc.isPasswordProtected)
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onOpen() },
                        modifier = Modifier.testTag("select_grid_doc_${doc.id}")
                    )
                } else {
                    Row {
                        IconButton(onClick = onToggleStar, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Star",
                                tint = if (doc.isStarred) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Options",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = doc.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${doc.pageCount} ${strings.pagesLabel} • ${formatFileSize(doc.fileSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentActionsSheet(
    doc: DocumentEntity,
    onDismiss: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenEditor: () -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    onToggleVault: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember(doc.id) { mutableStateOf(doc.title) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ListItem(
                headlineContent = { Text("Open in PDF Reader") },
                leadingContent = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onOpenReader() }
            )
            ListItem(
                headlineContent = { Text("Edit, Annotate & Sign") },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onOpenEditor() }
            )
            ListItem(
                headlineContent = { Text(strings.share) },
                leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss(); onShare() }
            )
            ListItem(
                headlineContent = { Text(strings.rename) },
                leadingContent = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                modifier = Modifier.clickable { showRenameDialog = true }
            )
            ListItem(
                headlineContent = {
                    Text(if (doc.isVaulted) "Restore from Private Vault" else "Move to Private Vault (AES-256)")
                },
                leadingContent = {
                    Icon(if (doc.isVaulted) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = null)
                },
                modifier = Modifier.clickable { onDismiss(); onToggleVault() }
            )
            ListItem(
                headlineContent = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable { onDismiss(); onDelete() }
            )
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth().testTag("rename_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRename(renameInput)
                    showRenameDialog = false
                    onDismiss()
                }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun PinLockGateScreen(
    title: String,
    biometricAvailable: Boolean,
    onVerifyPin: (String) -> Boolean,
    onRequestBiometric: () -> Unit,
    onUnlocked: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PrimaryBrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Protected with Android Keystore & Salted SHA-256",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it; errorMsg = null },
                label = { Text(strings.enterPin) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = errorMsg != null,
                supportingText = errorMsg?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_lock_input")
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (onVerifyPin(pin)) {
                        onUnlocked()
                    } else {
                        errorMsg = "Incorrect PIN. Please try again."
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("pin_unlock_button")
            ) {
                Text("Unlock")
            }
            if (biometricAvailable) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRequestBiometric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.biometricUnlock)
                }
            }
            if (onCancel != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text(strings.cancel) }
            }
        }
    }
}

@Composable
fun EmptyDocumentsState(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_onboarding_art),
                contentDescription = title,
                modifier = Modifier
                    .height(140.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction, modifier = Modifier.testTag("empty_import_btn")) {
                Text(actionLabel)
            }
        }
    }
}
