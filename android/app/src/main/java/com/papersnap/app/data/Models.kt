package com.papersnap.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class Paper(
    val arxivId: String,
    val date: String,
    val title: String,
    val titleZh: String,
    val categories: List<String>,
    val authors: List<String>,
    val published: String,
    val pdfUrl: String,
    val abstract: String,
    val summary: String
)

@Entity(tableName = "papers")
data class PaperEntity(
    @PrimaryKey val arxivId: String,
    val date: String,
    val title: String,
    val titleZh: String,
    val categories: String,
    val authors: String,
    val published: String,
    val pdfUrl: String,
    val abstract: String,
    val summary: String,
    val isBookmarked: Boolean = false,
    val isHidden: Boolean = false,
    val fetchedAt: Long = 0L
) {
    fun categoryList(): List<String> = categories.split(",").filter { it.isNotBlank() }
    fun toPaper(): Paper = Paper(
        arxivId = arxivId,
        date = date,
        title = title,
        titleZh = titleZh,
        categories = categoryList(),
        authors = authors.split(",").filter { it.isNotBlank() },
        published = published,
        pdfUrl = pdfUrl,
        abstract = abstract,
        summary = summary
    )
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val arxivId: String,
    val role: String,
    val content: String,
    val createdAt: Long = 0L
)

@Dao
interface PaperDao {
    @Query("SELECT * FROM papers WHERE date = :date ORDER BY fetchedAt DESC")
    fun papersForDate(date: String): Flow<List<PaperEntity>>

    @Query("SELECT * FROM papers WHERE isBookmarked = 1 ORDER BY fetchedAt DESC")
    fun bookmarkedPapers(): Flow<List<PaperEntity>>

    @Query("SELECT * FROM papers")
    fun allPapers(): Flow<List<PaperEntity>>

    @Query("SELECT DISTINCT date FROM papers ORDER BY date DESC")
    fun availableDates(): Flow<List<String>>

    @Query("SELECT * FROM papers WHERE arxivId = :id")
    suspend fun paperById(id: String): PaperEntity?

    @Query("SELECT COUNT(*) FROM papers WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("UPDATE papers SET isBookmarked = :value WHERE arxivId = :id")
    suspend fun setBookmarked(id: String, value: Boolean)

    @Query("UPDATE papers SET isHidden = :value WHERE arxivId = :id")
    suspend fun setHidden(id: String, value: Boolean)

    @Query("UPDATE papers SET isHidden = 0 WHERE date = :date")
    suspend fun restoreHidden(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(papers: List<PaperEntity>)

    @Query("DELETE FROM papers")
    suspend fun clearAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE arxivId = :id ORDER BY createdAt ASC, id ASC")
    fun messagesForPaper(id: String): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Database(
    entities = [PaperEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "papersnap.db"
                ).build().also { instance = it }
            }
    }
}
