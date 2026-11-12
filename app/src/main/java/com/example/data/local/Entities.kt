package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class DocumentType {
    PDF, DOCX, XLSX, PPTX, TXT, SCANNED_PDF
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val mimeType: String = "application/pdf",
    val docType: String = DocumentType.PDF.name,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 1,
    val isStarred: Boolean = false,
    val isVaulted: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val lastOpenedAt: Long = 0L,
    val lastViewedPage: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val contentHash: String = "",
    val folderCategory: String = "All"
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class AnnotationToolType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    INK,
    RECTANGLE,
    CIRCLE,
    ARROW,
    TEXT_BOX,
    STICKY_NOTE,
    SIGNATURE,
    IMAGE
}

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val toolType: String,
    val colorArgb: Long,
    val strokeWidth: Float = 4f,
    val pointsJson: String = "[]",
    val textContent: String = "",
    val xNorm: Float = 0f,
    val yNorm: Float = 0f,
    val widthNorm: Float = 0.25f,
    val heightNorm: Float = 0.08f,
    val extraPath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val strokesJson: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val isUser: Boolean,
    val message: String,
    val pageReferences: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
