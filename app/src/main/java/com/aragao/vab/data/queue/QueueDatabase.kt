package com.aragao.vab.data.queue

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object QueueStatus {
    const val PENDING = "PENDING"
    const val TRANSCRIBING = "TRANSCRIBING"
    const val DECIDING = "DECIDING"
    const val WAITING_EDITOR = "WAITING_EDITOR"
    const val APPLIED = "APPLIED"
    const val UNDONE = "UNDONE"
    const val FAILED = "FAILED"
}

object QueueInputType {
    const val AUDIO = "AUDIO"
    const val TEXT = "TEXT"
}

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val inputType: String,
    val audioFilePath: String? = null,
    val contentText: String = "",
    val status: String = QueueStatus.PENDING,
    val appliedSummary: String = "",
    val rawDecisionJson: String = "",
    val previousMarkdownSnapshot: String? = null,
    val errorMessage: String? = null
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<QueueItemEntity>>

    @Query(
        "SELECT COUNT(*) FROM queue_items WHERE status IN ('PENDING', 'TRANSCRIBING', 'DECIDING', 'WAITING_EDITOR')"
    )
    fun observeActiveCount(): Flow<Int>

    @Query(
        "SELECT * FROM queue_items WHERE status IN ('PENDING', 'WAITING_EDITOR') ORDER BY createdAtEpochMs ASC"
    )
    suspend fun getProcessableItems(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QueueItemEntity?

    @Insert
    suspend fun insert(item: QueueItemEntity): Long

    @Update
    suspend fun update(item: QueueItemEntity)

    @Query("DELETE FROM queue_items WHERE status IN ('APPLIED', 'UNDONE', 'FAILED')")
    suspend fun clearFinishedHistory()
}

@Database(entities = [QueueItemEntity::class], version = 1, exportSchema = false)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: QueueDatabase? = null

        fun getInstance(context: Context): QueueDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QueueDatabase::class.java,
                    "vab_queue.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
