package com.example.localtaskassistant.bootstrap

import android.content.Context
import com.example.localtaskassistant.business.*
import com.example.localtaskassistant.capture.CaptureBus
import com.example.localtaskassistant.contract.AssistantFacade
import com.example.localtaskassistant.data.*
import com.example.localtaskassistant.data.local.AssistantDatabase
import com.example.localtaskassistant.inference.*
import com.example.localtaskassistant.reminder.*
import kotlinx.coroutines.*
import java.time.Clock

/** B 维护装配；手工构造器注入便于第一轮联调，后续可替换 Hilt，不能到处创建单例。 */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val capture = CaptureBus()
    private val settings = LocalSettingsStore(context)
    private val database = AssistantDatabase.create(context)
    private val clock = Clock.systemUTC()
    private val repository = RoomTaskRepository(database, settings, clock)
    private val engine = LocalSemanticEngine()
    private val pipeline = InferencePipeline(repository, engine)
    private val coordinator = AssistantCoordinator(repository, settings, capture, engine, pipeline,
        AnalysisCommitter(repository), scope, clock)
    val outbox = ScheduleOutboxDispatcher(AndroidReminderGateway())
    val facade: AssistantFacade = coordinator
    fun start() = coordinator.start()
    // TODO(B-10): 替换成 Hilt 时保持接口及单例作用域；不把 Activity 注入 Application 生命周期。
}
