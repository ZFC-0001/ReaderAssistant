package com.example.readertest

import androidx.room.Entity
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

data class ExtractedTask(
    val content: String,
    val deadline: Long?,      // epoch millis，null 表示"时间待确认"
    val sourcePackage: String,
    val sourceText: String,
    val confidence: Float     // 0.0~1.0
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val deadline: Long?,
    val sourcePackage: String,
    val sourceText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val reminderFired: Boolean = false,
    val version: Int = 1
)

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Query("SELECT * FROM tasks ORDER BY deadline IS NULL, deadline ASC, createdAt DESC")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE completed = 0 AND reminderFired = 0 AND deadline IS NOT NULL")
    suspend fun getPendingReminders(): List<TaskEntity>

    @Query("UPDATE tasks SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("UPDATE tasks SET reminderFired = 1 WHERE id = :id")
    suspend fun setReminderFired(id: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "tasks.db")
                .build().also { INSTANCE = it }
        }
    }
}