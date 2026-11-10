package com.example.localtaskassistant.contract

enum class PromptMode { TIME_BASED, ALWAYS_CARD, ALWAYS_INBOX }
data class UserSettings(
    val capture: CapturePolicy = CapturePolicy(),
    val promptMode: PromptMode = PromptMode.TIME_BASED,
    val cardWindowHours: Int = 24,
    val sourceMaxAgeDays: Int = 7,
    val defaultLeadMinutes: Int = 30,
    val snoozeMinutes: Int = 10,
) {
    init {
        require(cardWindowHours in 1..720 && sourceMaxAgeDays in 1..365)
        require(defaultLeadMinutes in 0..10080 && snoozeMinutes in 1..1440)
        require(capture.allowedPackages.all { it in TargetApps.supported })
    }
}
enum class ActionStatus { APPLIED, ALREADY_APPLIED, STALE, NOT_FOUND, INVALID, NOT_IMPLEMENTED }
data class ActionResult(val status: ActionStatus, val message: String, val taskId: String? = null)

/** 所有修改都交给成员二；C 不得自行读写 DAO 或调用 AlarmManager。 */
sealed interface UserCommand {
    data class Confirm(val task: TaskRef) : UserCommand
    data class Complete(val task: TaskRef) : UserCommand
    data class Edit(val task: TaskRef, val fields: TaskFields) : UserCommand
    data class SetReminder(val task: TaskRef, val enabled: Boolean, val leadMinutes: Int?, val customAtMillis: Long?) : UserCommand
    data class AcceptChange(val proposalId: String, val task: TaskRef, val chosen: TaskFields?) : UserCommand
    data class RejectChange(val proposalId: String, val task: TaskRef) : UserCommand
    data class SplitDraft(val task: TaskRef, val sourceIds: Set<String>) : UserCommand
    data class Snooze(val task: TaskRef, val expectedReminderGeneration: Long) : UserCommand
    data class DeleteAll(val confirmationToken: String) : UserCommand
}
enum class PromptDestination { CARD, INBOX }
data class PromptEvent(val task: TaskRef, val proposalId: String?, val destination: PromptDestination)
data class RuntimeStatus(val message: String = "骨架已启动；本地 AI、OCR 与提醒调度待接入")

data class ReminderPlan(
    val id: String,
    val task: TaskRef,
    val generation: Long,
    val triggerAtMillis: Long,
    val snooze: Boolean = false,
)
sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data class Blocked(val reason: String) : ScheduleResult
}
