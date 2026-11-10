package com.example.localtaskassistant.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<TaskRow>>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: String): TaskRow?
    @Query("SELECT * FROM tasks WHERE lifecycle != 'COMPLETED' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun contextCandidates(limit: Int): List<TaskRow>
    @Query("SELECT * FROM dedup_markers WHERE `key` = :key")
    suspend fun marker(key: String): DedupRow?
    @Query("SELECT * FROM source_events WHERE fingerprint = :fingerprint")
    suspend fun source(fingerprint: String): SourceRow?
    @Insert suspend fun insertTask(task: TaskRow)
    @Update suspend fun updateTask(task: TaskRow)
    @Insert suspend fun insertSource(source: SourceRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(row: TaskSourceRow)
    @Insert suspend fun mark(row: DedupRow)
    @Insert suspend fun history(row: HistoryRow)
    @Insert suspend fun reminder(row: ReminderRow)
    @Insert suspend fun outbox(row: OutboxRow)
    @Query("SELECT * FROM reminders WHERE taskId = :taskId AND status != 'CANCELLED'")
    suspend fun reminders(taskId: String): List<ReminderRow>
    @Query("UPDATE reminders SET status = 'CANCELLED', generation = generation + 1 WHERE taskId = :taskId")
    suspend fun invalidateReminders(taskId: String)
    @Query("DELETE FROM task_history WHERE taskId = :taskId")
    suspend fun clearHistory(taskId: String)
    @Query("DELETE FROM change_proposals WHERE taskId = :taskId")
    suspend fun clearProposals(taskId: String)
    @Query("""UPDATE source_events SET excerpt = NULL WHERE id NOT IN
        (SELECT ts.sourceId FROM task_sources ts INNER JOIN tasks t ON ts.taskId = t.id WHERE t.lifecycle = 'OPEN')""")
    suspend fun clearUnusedExcerpts()
    @Query("UPDATE task_sources SET excerpt = NULL WHERE taskId IN (SELECT id FROM tasks WHERE lifecycle != 'OPEN')")
    suspend fun clearEndedLinkExcerpts()
    @Query("SELECT * FROM schedule_outbox WHERE status = 'PENDING' ORDER BY createdAt")
    suspend fun pendingOutbox(): List<OutboxRow>
    @Query("UPDATE schedule_outbox SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateOutbox(id: String, status: String, error: String?)
}

@Database(entities = [TaskRow::class, SourceRow::class, TaskSourceRow::class, DedupRow::class,
    ProposalRow::class, HistoryRow::class, ReminderRow::class, OutboxRow::class, PromptReceiptRow::class],
    version = 1, exportSchema = true)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun dao(): AssistantDao
    companion object {
        fun create(context: Context): AssistantDatabase = Room.databaseBuilder(
            context.applicationContext, AssistantDatabase::class.java, "assistant-scaffold.db",
        ).build()
        // TODO(B-01): 以后升级必须提供 Migration 和迁移测试，禁止 destructiveMigration 清用户数据。
    }
}
