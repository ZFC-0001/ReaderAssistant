package com.example.localtaskassistant.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.data.local.AssistantDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.*

/** B 的设备级事务测试；需真机/模拟器，不能用 JVM 测试结果代替它。 */
class RoomTaskRepositoryTest {
    private lateinit var db: AssistantDatabase
    private lateinit var repo: RoomTaskRepository
    private val now = Instant.parse("2026-11-08T08:00:00Z")
    private val preferences = object : SettingsStore {
        override val settings = MutableStateFlow(UserSettings())
        override suspend fun update(settings: UserSettings) { this.settings.value = settings }
    }
    private fun source(fragment: String = "task-1") = SourceEvidence("S1", "stable-source", fragment,
        TargetApps.QQ, "课程群", now, now, "交实验报告")
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            AssistantDatabase::class.java).build()
        repo = RoomTaskRepository(db, preferences, Clock.fixed(now, ZoneOffset.UTC))
    }
    @After fun tearDown() = db.close()
    @Test fun confirmationKeepsIdentityAndRejectsOldCard() = runBlocking {
        val created = repo.createDraft(TaskFields("交实验报告"), source())
        val id = created.taskId!!
        assertEquals(ActionStatus.APPLIED, repo.execute(UserCommand.Confirm(TaskRef(id, 1))).status)
        assertEquals(id, repo.getTask(id)!!.id)
        assertEquals(Confirmation.CONFIRMED, repo.getTask(id)!!.confirmation)
        assertEquals(ActionStatus.STALE, repo.execute(UserCommand.Complete(TaskRef(id, 1))).status)
    }
    @Test fun durableSourceMarkerSurvivesRepositoryRecreation() = runBlocking {
        val first = repo.createDraft(TaskFields("交实验报告"), source())
        val other = RoomTaskRepository(db, preferences, Clock.fixed(now, ZoneOffset.UTC))
        val duplicate = other.createDraft(TaskFields("模型改写过摘要也不能重复创建"), source())
        assertEquals(ActionStatus.ALREADY_APPLIED, duplicate.status)
        assertEquals(first.taskId, duplicate.taskId)
    }
    @Test fun sameSourceCanContainTwoDifferentTasks() = runBlocking {
        val first = repo.createDraft(TaskFields("交实验报告"), source("task-1"))
        val second = repo.createDraft(TaskFields("准备小组汇报"), source("task-2"))
        assertEquals(ActionStatus.APPLIED, second.status)
        assertNotEquals(first.taskId, second.taskId)
    }
}
