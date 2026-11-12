package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        SignatureEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DocuVerseDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun signatureDao(): SignatureDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: DocuVerseDatabase? = null

        fun getInstance(context: Context): DocuVerseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocuVerseDatabase::class.java,
                    "docuverse.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
