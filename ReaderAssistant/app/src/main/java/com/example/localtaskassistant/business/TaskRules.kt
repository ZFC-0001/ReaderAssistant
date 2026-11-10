package com.example.localtaskassistant.business

import com.example.localtaskassistant.contract.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 成员二：纯 Kotlin 规则，方便单独测试。UI/模型不得复制这些业务判断。 */
object TaskRules {
    fun sourceIsEligible(publishedAt: Instant?, now: Instant, maxDays: Int): Boolean =
        publishedAt == null || !publishedAt.isBefore(now.minus(Duration.ofDays(maxDays.toLong())))

    fun expiresAt(time: TaskTime): Instant? = when (time.precision) {
        TimePrecision.DATE_TIME -> time.instant
        TimePrecision.DATE -> time.date!!.plusDays(1).atStartOfDay(ZoneId.of(time.zone)).toInstant()
        TimePrecision.NONE, TimePrecision.UNRESOLVED -> null
    }
    fun isExpired(time: TaskTime, now: Instant): Boolean = expiresAt(time)?.let { !now.isBefore(it) } ?: false

    fun routeNewTask(time: TaskTime, settings: UserSettings, now: Instant): PromptDestination =
        when (settings.promptMode) {
            PromptMode.ALWAYS_CARD -> PromptDestination.CARD
            PromptMode.ALWAYS_INBOX -> PromptDestination.INBOX
            PromptMode.TIME_BASED -> {
                val at = time.instant
                if (time.precision == TimePrecision.DATE_TIME && at != null && !at.isBefore(now) &&
                    !at.isAfter(now.plus(Duration.ofHours(settings.cardWindowHours.toLong()))))
                    PromptDestination.CARD else PromptDestination.INBOX
            }
        }
    /** 已确认变更/延期恢复的卡片优先级高于三种新任务模式。 */
    fun routeChange(): PromptDestination = PromptDestination.CARD

    fun defaultReminderAt(time: TaskTime, settings: UserSettings, now: Instant): Instant? {
        if (time.precision != TimePrecision.DATE_TIME) return null
        return time.instant!!.minus(Duration.ofMinutes(settings.defaultLeadMinutes.toLong())).takeIf { it.isAfter(now) }
    }
    fun checkRevision(task: TaskSnapshot, expected: Long): ActionResult? =
        if (task.revision != expected) ActionResult(ActionStatus.STALE, "内容已更新，请查看最新任务", task.id) else null
}
