package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isVaulted = 0 ORDER BY modifiedAt DESC")
    fun observeAllVisibleDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isVaulted = 1 ORDER BY modifiedAt DESC")
    fun observeVaultedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isVaulted = 0 AND lastOpenedAt > 0 ORDER BY lastOpenedAt DESC LIMIT 12")
    fun observeRecentlyOpened(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isVaulted = 0 AND isStarred = 1 ORDER BY modifiedAt DESC")
    fun observeStarredDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countAll(): Int

    @Query("SELECT * FROM documents")
    suspend fun getAllSnapshot(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("UPDATE documents SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE documents SET lastOpenedAt = :timestamp, lastViewedPage = :page WHERE id = :id")
    suspend fun updateReadProgress(id: Long, timestamp: Long, page: Int)

    @Query("UPDATE documents SET title = :title, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, modifiedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isVaulted = :vaulted, filePath = :newPath, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateVaultState(id: Long, vaulted: Boolean, newPath: String, modifiedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageNumber ASC")
    fun observeForDocument(documentId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND pageNumber = :pageNumber")
    suspend fun deleteAtPage(documentId: Long, pageNumber: Int)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY createdAt ASC")
    fun observeForDocument(documentId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :documentId")
    suspend fun getSnapshotForDocument(documentId: Long): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM annotations WHERE documentId = :documentId AND pageNumber = :pageNumber")
    suspend fun clearPage(documentId: Long, pageNumber: Int)
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signature: SignatureEntity): Long

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE documentId = :documentId ORDER BY createdAt ASC")
    fun observeForDocument(documentId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE documentId = :documentId")
    suspend fun clearForDocument(documentId: Long)
}
