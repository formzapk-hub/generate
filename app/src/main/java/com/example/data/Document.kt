package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val sourcePath: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0 // 0 = unsynced, 1 = synced
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastModified DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
}

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_to_word_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    suspend fun getDocumentById(id: Long): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insert(document: DocumentEntity): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun update(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun delete(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }

    suspend fun deleteById(id: Long) {
        documentDao.deleteDocumentById(id)
    }
}
