package com.example.localtaskassistant.contract

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 公共契约，由成员二维护；任何字段变动须三人一起确认。禁止依赖 Android/Room/UI。 */
enum class Confirmation { DRAFT, CONFIRMED }
enum class Lifecycle { OPEN, COMPLETED, EXPIRED }
enum class TimePrecision { NONE, DATE, DATE_TIME, UNRESOLVED }

/** 日期精度与具体时刻分开。DATE 不能补成 23:59，也不能直接安排默认提醒。 */
data class TaskTime(
    val precision: TimePrecision = TimePrecision.NONE,
    val date: LocalDate? = null,
    val instant: Instant? = null,
    val zone: String = ZoneId.systemDefault().id,
    val expression: String? = null,
) {
    init {
        ZoneId.of(zone)
        require(precision != TimePrecision.DATE || (date != null && instant == null))
        require(precision != TimePrecision.DATE_TIME || instant != null)
        require(precision != TimePrecision.NONE || (date == null && instant == null))
        require(precision != TimePrecision.UNRESOLVED || (date == null && instant == null))
    }
}

data class TaskFields(
    val title: String,
    val time: TaskTime = TaskTime(),
    val location: String? = null,
    val requirements: String? = null,
    val possiblyRelevant: Boolean = false,
) { init { require(title.isNotBlank()) } }

/** 编号在确认、更新和恢复前后保持不变。revision 用于拦截旧卡片/旧分析结果。 */
data class TaskSnapshot(
    val id: String,
    val revision: Long,
    val fields: TaskFields,
    val confirmation: Confirmation,
    val lifecycle: Lifecycle,
    val createdAt: Instant,
    val updatedAt: Instant,
)
data class TaskRef(val id: String, val revision: Long)
data class HistorySnapshot(val revision: Long, val kind: String, val before: TaskFields?, val after: TaskFields?)
data class ReminderSnapshot(val plan: ReminderPlan, val state: String, val failureReason: String?)
/** 详情查询接口先固定；未接通的详情必须说明原因，不能把空数组伪装成无来源。 */
data class TaskDetails(
    val task: TaskSnapshot?,
    val sources: List<SourceEvidence> = emptyList(),
    val history: List<HistorySnapshot> = emptyList(),
    val reminders: List<ReminderSnapshot> = emptyList(),
    val unavailableReason: String? = null,
)
enum class ChangeKind { UPDATE, CANCEL, REOPEN }
enum class ProposalStatus { PENDING, ACCEPTED, REJECTED, OBSOLETE }

/** 待确认修改独立保存，不覆盖正式任务。冲突值必须由用户选择。 */
data class ChangeProposal(
    val id: String,
    val task: TaskRef,
    val kind: ChangeKind,
    val proposed: TaskFields?,
    val sourceIds: Set<String>,
    val summary: String,
    val conflicts: List<String> = emptyList(),
    val status: ProposalStatus = ProposalStatus.PENDING,
)

data class SourceEvidence(
    val id: String,
    /** 稳定来源身份；不能包含 observedAt，也不能只用正文前 30 字。 */
    val fingerprint: String,
    /** 同一来源包含多个任务时各用一个稳定片段键；不能使用模型每次生成的摘要。 */
    val fragmentKey: String,
    val packageName: String,
    val conversation: String?,
    val publishedAt: Instant?,
    val observedAt: Instant,
    val excerpt: String,
)
