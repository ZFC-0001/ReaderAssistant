package com.example.localtaskassistant.data

import androidx.room.withTransaction
import com.example.localtaskassistant.business.TaskRules
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.data.local.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** B 的唯一写库入口。已接通创建/确认/完成，其余操作明确返回 NOT_IMPLEMENTED。 */
class RoomTaskRepository(
    private val database: AssistantDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
) : TaskRepository {
    private val dao get() = database.dao()
    override fun observeTasks() = dao.observeTasks().map { rows -> rows.map { it.snapshot() } }
    override fun observeChanges(): Flow<List<ChangeProposal>> = flowOf(emptyList())
    // TODO(B-03): ProposalRow 的类型安全序列化/DAO/Flow，禁止把空列表当成已完成的变更功能。
    override suspend fun getTask(id: String) = dao.task(id)?.snapshot()
    override suspend fun findRelated(content: CapturedContent, limit: Int): List<TaskSnapshot> {
        // B 提供数据访问；TODO(A-07): 检索排序/消歧。当前仅给少量未完成记录作接口联调。
        return dao.contextCandidates(limit.coerceIn(1, 24)).map { it.snapshot() }
    }
    override suspend fun createDraft(fields: TaskFields, evidence: SourceEvidence): ActionResult = database.withTransaction {
        val now = clock.instant()
        if (!TaskRules.sourceIsEligible(evidence.publishedAt, now, settings.settings.value.sourceMaxAgeDays))
            return@withTransaction ActionResult(ActionStatus.INVALID, "来源已超过有效期")
        // 一个来源可含多项任务；片段键区分来源里的不同任务。跨来源仍须 B-02 关联。
        val digest = MessageDigest.getInstance("SHA-256").digest(
            (evidence.fingerprint + "\u001f" + evidence.fragmentKey).toByteArray(),
        ).joinToString("") { "%02x".format(it) }
        dao.marker(digest)?.let { return@withTransaction ActionResult(ActionStatus.ALREADY_APPLIED, "该内容已经收录", it.taskId) }
        val id = UUID.randomUUID().toString()
        val task = TaskSnapshot(id, 1, fields, Confirmation.DRAFT,
            if (TaskRules.isExpired(fields.time, now)) Lifecycle.EXPIRED else Lifecycle.OPEN, now, now)
        dao.insertTask(task.row())
        val sourceId = dao.source(evidence.fingerprint)?.id ?: evidence.id.also {
            dao.insertSource(SourceRow(it, evidence.fingerprint, evidence.packageName, evidence.conversation,
                evidence.publishedAt?.toEpochMilli(), evidence.observedAt.toEpochMilli(),
                if (task.lifecycle == Lifecycle.OPEN) evidence.excerpt else null))
        }
        dao.link(TaskSourceRow(id, sourceId, if (task.lifecycle == Lifecycle.OPEN) evidence.excerpt else null))
        dao.mark(DedupRow(digest, id, now.toEpochMilli()))
        ActionResult(ActionStatus.APPLIED, "已保存待确认任务", id)
    }
    override suspend fun execute(command: UserCommand): ActionResult = database.withTransaction {
        when (command) {
            is UserCommand.Confirm -> confirm(command.task)
            is UserCommand.Complete -> complete(command.task)
            // TODO(B-02/B-03/B-04/B-07/B-09): 每个分支单独用例＋事务＋版本门禁；不要在 UI 补逻辑。
            else -> ActionResult(ActionStatus.NOT_IMPLEMENTED, "该业务操作尚未实现，请按 TODO 补充")
        }
    }
    private suspend fun confirm(ref: TaskRef): ActionResult {
        val old = dao.task(ref.id)?.snapshot() ?: return ActionResult(ActionStatus.NOT_FOUND, "任务不存在")
        TaskRules.checkRevision(old, ref.revision)?.let { return it }
        if (old.confirmation == Confirmation.CONFIRMED) return ActionResult(ActionStatus.ALREADY_APPLIED, "已经确认", old.id)
        if (old.lifecycle != Lifecycle.OPEN || TaskRules.isExpired(old.fields.time, clock.instant()))
            return ActionResult(ActionStatus.INVALID, "任务已结束或超时，延期恢复需单独确认", old.id)
        val now = clock.instant()
        val updated = old.copy(confirmation = Confirmation.CONFIRMED, revision = old.revision + 1, updatedAt = now)
        dao.updateTask(updated.row())
        dao.history(HistoryRow(UUID.randomUUID().toString(), old.id, updated.revision, "CONFIRM", null, null, now.toEpochMilli()))
        val reminderAt = TaskRules.defaultReminderAt(updated.fields.time, settings.settings.value, now)
        if (reminderAt != null) {
            val reminderId = UUID.randomUUID().toString()
            dao.reminder(ReminderRow(reminderId, old.id, updated.revision, 1, reminderAt.toEpochMilli(), "DEFAULT", "PENDING_IMPLEMENTATION"))
            dao.outbox(OutboxRow(UUID.randomUUID().toString(), reminderId, 1, "SCHEDULE", "PENDING", null, now.toEpochMilli()))
        }
        return ActionResult(ActionStatus.APPLIED,
            if (reminderAt != null) "已确认；提醒请求已保存，系统调度尚未接入" else "已确认；未安排自动提醒", old.id)
    }
    private suspend fun complete(ref: TaskRef): ActionResult {
        val old = dao.task(ref.id)?.snapshot() ?: return ActionResult(ActionStatus.NOT_FOUND, "任务不存在")
        TaskRules.checkRevision(old, ref.revision)?.let { return it }
        if (old.lifecycle == Lifecycle.COMPLETED) return ActionResult(ActionStatus.ALREADY_APPLIED, "已经完成", old.id)
        if (old.confirmation != Confirmation.CONFIRMED) return ActionResult(ActionStatus.INVALID, "请先确认任务", old.id)
        val now = clock.instant()
        dao.updateTask(old.copy(lifecycle = Lifecycle.COMPLETED, revision = old.revision + 1, updatedAt = now).row())
        for (reminder in dao.reminders(old.id)) {
            dao.outbox(OutboxRow(UUID.randomUUID().toString(), reminder.id, reminder.generation + 1,
                "CANCEL", "PENDING", null, now.toEpochMilli()))
        }
        dao.invalidateReminders(old.id)
        dao.clearHistory(old.id)
        dao.clearProposals(old.id)
        dao.clearEndedLinkExcerpts()
        dao.clearUnusedExcerpts()
        // TODO(B-08): 完成/超时统一 RetentionCoordinator，清理推理缓存；共享源按访问权限裁剪。
        return ActionResult(ActionStatus.APPLIED, "已完成并清理不再需要的详细来源", old.id)
    }
}
