package com.example.localtaskassistant.data.local

import androidx.room.*
import com.example.localtaskassistant.contract.*
import java.time.Instant
import java.time.LocalDate

/** 成员二维护。新应用使用新库，不对原 ReaderTest 的 tasks.db 做破坏式迁移。 */
@Entity(tableName = "tasks")
data class TaskRow(
    @PrimaryKey val id: String,
    val revision: Long,
    val title: String,
    val timePrecision: String,
    val dateIso: String?,
    val instantMillis: Long?,
    val zone: String,
    val timeExpression: String?,
    val location: String?,
    val requirements: String?,
    val possiblyRelevant: Boolean,
    val confirmation: String,
    val lifecycle: String,
    val createdAt: Long,
    val updatedAt: Long,
)
fun TaskRow.snapshot() = TaskSnapshot(
    id, revision,
    TaskFields(title, TaskTime(TimePrecision.valueOf(timePrecision), dateIso?.let(LocalDate::parse),
        instantMillis?.let(Instant::ofEpochMilli), zone, timeExpression), location, requirements, possiblyRelevant),
    Confirmation.valueOf(confirmation), Lifecycle.valueOf(lifecycle), Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt),
)
fun TaskSnapshot.row() = TaskRow(id, revision, fields.title, fields.time.precision.name,
    fields.time.date?.toString(), fields.time.instant?.toEpochMilli(), fields.time.zone, fields.time.expression,
    fields.location, fields.requirements, fields.possiblyRelevant, confirmation.name, lifecycle.name,
    createdAt.toEpochMilli(), updatedAt.toEpochMilli())

@Entity(tableName = "source_events", indices = [Index(value = ["fingerprint"], unique = true)])
data class SourceRow(
    @PrimaryKey val id: String,
    val fingerprint: String,
    val packageName: String,
    val conversation: String?,
    val publishedAt: Long?,
    val observedAt: Long,
    val excerpt: String?,
)
@Entity(tableName = "task_sources", primaryKeys = ["taskId", "sourceId"],
    indices = [Index("sourceId")], foreignKeys = [
        ForeignKey(entity = TaskRow::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SourceRow::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ])
data class TaskSourceRow(val taskId: String, val sourceId: String, val excerpt: String?)

/** 一条来源可能含多个任务，因此标记键是“来源事件＋任务片段”，不能只用 sourceId。 */
@Entity(tableName = "dedup_markers")
data class DedupRow(@PrimaryKey val key: String, val taskId: String, val createdAt: Long)

@Entity(tableName = "change_proposals", indices = [Index("taskId")])
data class ProposalRow(
    @PrimaryKey val id: String, val taskId: String, val baseRevision: Long,
    val kind: String, val status: String, val fingerprint: String, val payloadJson: String?, val createdAt: Long,
)
@Entity(tableName = "task_history", indices = [Index("taskId")])
data class HistoryRow(
    @PrimaryKey val id: String, val taskId: String, val revision: Long,
    val kind: String, val beforeJson: String?, val afterJson: String?, val createdAt: Long,
)
@Entity(tableName = "reminders", indices = [Index("taskId")])
data class ReminderRow(
    @PrimaryKey val id: String, val taskId: String, val taskRevision: Long,
    val generation: Long, val triggerAt: Long, val kind: String, val status: String,
)
@Entity(tableName = "schedule_outbox", indices = [Index("reminderId")])
data class OutboxRow(
    @PrimaryKey val id: String, val reminderId: String, val generation: Long,
    val operation: String, val status: String, val lastError: String?, val createdAt: Long,
)
@Entity(tableName = "prompt_receipts")
data class PromptReceiptRow(
    @PrimaryKey val key: String, val taskId: String, val revision: Long,
    val proposalId: String?, val destination: String, val deliveredAt: Long?,
)
