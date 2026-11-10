package com.example.localtaskassistant.business

import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.inference.InferencePipeline
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

/** 成员二/集成入口。应用级 scope，无 Activity 轮询；推理与 UI 生命周期解耦。 */
class AssistantCoordinator(
    private val repository: TaskRepository,
    private val settingsStore: SettingsStore,
    private val capture: CaptureGateway,
    private val engine: SemanticEngine,
    private val pipeline: InferencePipeline,
    private val committer: AnalysisCommitter,
    private val scope: CoroutineScope,
    private val clock: Clock,
) : AssistantFacade {
    private val commitMutex = Mutex()
    private val mutableRuntime = MutableStateFlow(RuntimeStatus())
    private val mutablePrompts = MutableSharedFlow<PromptEvent>(extraBufferCapacity = 16)
    private var started = false
    override val tasks = repository.observeTasks().stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val changes = repository.observeChanges().stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val settings = settingsStore.settings
    override val captureStatus = capture.status
    override val runtimeStatus = mutableRuntime.asStateFlow()
    override val modelStatus = engine.status
    override val prompts = mutablePrompts.asSharedFlow()
    override fun observeDetails(taskId: String): Flow<TaskDetails> = tasks.map { list ->
        // TODO(B-01/B-03/B-08): 按任务权限联结来源/历史/提醒；已清理任务不得返回旧原文。
        TaskDetails(list.find { it.id == taskId }, unavailableReason = "来源、历史及提醒详情查询尚未接通")
    }

    fun start() {
        if (started) return
        started = true
        capture.updatePolicy(settings.value.capture)
        scope.launch {
            capture.contents.collect { content ->
                try { process(content) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableRuntime.value = RuntimeStatus("处理失败；未记录原文日志，请重试") }
            }
        }
        // TODO(B-05): 任务过期/来源清理/调度对账的启动与恢复入口，不能仅靠 Activity 存活。
        // TODO(A-05/B-05): 应用 scope 不是后台保活保证；按真机验证设计采集服务的运行生命周期。
    }
    private suspend fun process(content: CapturedContent) {
        if (!capture.isCurrentSession(content.session)) return
        if (content.packageName !in capture.status.value.policy.allowedPackages) return
        if (!TaskRules.sourceIsEligible(content.publishedAt, clock.instant(), settings.value.sourceMaxAgeDays)) return
        val outcome = pipeline.analyze(content) // 模型不占用业务提交锁，暂停操作不必等待模型结束。
        commitMutex.withLock {
            if (!capture.isCurrentSession(content.session)) return@withLock
            if (content.packageName !in capture.status.value.policy.allowedPackages) return@withLock
            if (!TaskRules.sourceIsEligible(content.publishedAt, clock.instant(), settings.value.sourceMaxAgeDays)) return@withLock
            when (outcome) {
                is AnalysisOutcome.Unavailable -> mutableRuntime.value = RuntimeStatus(outcome.reason)
                is AnalysisOutcome.Invalid -> mutableRuntime.value = RuntimeStatus(outcome.reason)
                is AnalysisOutcome.Success -> for (suggestion in outcome.suggestions) {
                    val result = committer.apply(suggestion)
                    mutableRuntime.value = RuntimeStatus(result.message)
                    val id = result.taskId
                    if (result.status == ActionStatus.APPLIED && id != null) {
                        val task = repository.getTask(id) ?: continue
                        val destination = TaskRules.routeNewTask(task.fields.time, settings.value, clock.instant())
                        // TODO(B-06): 先持久化 PromptReceipt，再由平台提示适配器投递并回写结果。
                        // SharedFlow 只是前台联调通道，不代表已有跨应用卡片或退出桌面汇总。
                        mutablePrompts.emit(PromptEvent(TaskRef(id, task.revision), null, destination))
                    }
                }
            }
        }
    }
    override suspend fun dispatch(command: UserCommand): ActionResult = commitMutex.withLock {
        repository.execute(command)
    }
    override suspend fun updateSettings(settings: UserSettings): ActionResult = commitMutex.withLock {
        settingsStore.update(settings)
        capture.updatePolicy(settings.capture)
        ActionResult(ActionStatus.APPLIED, "设置已保存；暂停识别不影响已有任务")
    }
    override suspend fun installDemoFixture(): ActionResult = commitMutex.withLock {
        // 固定来源/片段键，反复点击与重启后点击仍幂等。只是手动数据，不调用模型。
        val now = clock.instant()
        repository.createDraft(
            TaskFields("联调样例：提交实验报告", TaskTime(TimePrecision.DATE_TIME,
                instant = now.plus(Duration.ofHours(2)), expression = "联调样例时间"),
                location = "信息楼302", requirements = "仅用于验证接口，非真实识别结果"),
            SourceEvidence("demo-source-v1", "demo-source-v1", "task-1", "demo.fixture", "接口联调样例",
                now, now, "联调样例：提交实验报告"),
        )
    }
}
