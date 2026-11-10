package com.example.localtaskassistant.business

import com.example.localtaskassistant.contract.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskRulesTest {
    private val now = Instant.parse("2026-11-08T08:00:00Z")
    private val settings = UserSettings()
    private fun at(instant: Instant) = TaskTime(TimePrecision.DATE_TIME, instant = instant)
    @Test fun unknownSourceDateIsNotInventedOrDiscarded() {
        assertTrue(TaskRules.sourceIsEligible(null, now, 7))
    }
    @Test fun sevenDaySourceBoundaryIsInclusive() {
        assertTrue(TaskRules.sourceIsEligible(now.minus(Duration.ofDays(7)), now, 7))
        assertFalse(TaskRules.sourceIsEligible(now.minus(Duration.ofDays(7)).minusMillis(1), now, 7))
    }
    @Test fun twentyFourHourCardBoundaryIsInclusive() {
        assertEquals(PromptDestination.CARD, TaskRules.routeNewTask(at(now.plus(Duration.ofHours(24))), settings, now))
        assertEquals(PromptDestination.INBOX, TaskRules.routeNewTask(at(now.plus(Duration.ofHours(24)).plusMillis(1)), settings, now))
    }
    @Test fun dateOnlyDoesNotBecomeAnExactTimeOrReminder() {
        val date = TaskTime(TimePrecision.DATE, date = LocalDate.of(2026, 11, 8), zone = "Asia/Shanghai")
        assertEquals(PromptDestination.INBOX, TaskRules.routeNewTask(date, settings, now))
        assertNull(TaskRules.defaultReminderAt(date, settings, now))
        assertNull(date.instant)
    }
    @Test fun dateOnlyExpiresAtStartOfFollowingLocalDay() {
        val date = TaskTime(TimePrecision.DATE, date = LocalDate.of(2026, 11, 8), zone = "Asia/Shanghai")
        assertFalse(TaskRules.isExpired(date, Instant.parse("2026-11-08T15:59:59Z")))
        assertTrue(TaskRules.isExpired(date, Instant.parse("2026-11-08T16:00:00Z")))
    }
    @Test fun dateExpiryUsesZoneRulesAcrossDaylightSaving() {
        val date = TaskTime(TimePrecision.DATE, date = LocalDate.of(2026, 11, 1), zone = "America/New_York")
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), TaskRules.expiresAt(date))
    }
    @Test fun noTimeAndUnresolvedTimeNeverExpireAutomatically() {
        assertFalse(TaskRules.isExpired(TaskTime(), now))
        assertFalse(TaskRules.isExpired(TaskTime(TimePrecision.UNRESOLVED, expression = "过两天"), now))
    }
    @Test fun alwaysCardAlsoAppliesToTasksWithoutTime() {
        assertEquals(PromptDestination.CARD, TaskRules.routeNewTask(TaskTime(), settings.copy(promptMode = PromptMode.ALWAYS_CARD), now))
    }
    @Test fun inboxModeDoesNotSuppressConfirmedChangeCards() {
        assertEquals(PromptDestination.INBOX, TaskRules.routeNewTask(at(now.plusSeconds(60)), settings.copy(promptMode = PromptMode.ALWAYS_INBOX), now))
        assertEquals(PromptDestination.CARD, TaskRules.routeChange())
    }
    @Test fun approachingTaskDoesNotScheduleAnAlarmInThePast() {
        assertNull(TaskRules.defaultReminderAt(at(now.plusSeconds(60)), settings, now))
    }
    @Test fun defaultReminderIsThirtyMinutesBeforeTask() {
        assertEquals(now.plusSeconds(5400), TaskRules.defaultReminderAt(at(now.plusSeconds(7200)), settings, now))
    }
    @Test fun invalidDatePrecisionCannotCarryFabricatedTimestamp() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskTime(TimePrecision.DATE, date = LocalDate.of(2026, 11, 8), instant = now)
        }
    }
    @Test fun staleCardIsRejected() {
        val task = TaskSnapshot("T1", 2, TaskFields("交作业"), Confirmation.DRAFT, Lifecycle.OPEN, now, now)
        assertEquals(ActionStatus.STALE, TaskRules.checkRevision(task, 1)?.status)
        assertNull(TaskRules.checkRevision(task, 2))
    }
}
