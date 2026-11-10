package com.example.localtaskassistant.contract

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** A 的采集出口。流必须有界；暂停/撤销后旧 session 不得继续提交分析结果。 */
interface CaptureGateway {
    val contents: Flow<CapturedContent>
    val status: StateFlow<CaptureStatus>
    fun updatePolicy(policy: CapturePolicy)
    fun isCurrentSession(session: Long): Boolean
}
interface TextRecognizer { suspend fun recognize(frame: OcrFrame): Result<List<TextBlock>> }
interface SemanticEngine {
    val status: StateFlow<ModelStatus>
    suspend fun analyze(request: AnalysisRequest): AnalysisOutcome
}
interface TaskContextProvider {
    suspend fun findRelated(content: CapturedContent, limit: Int = 12): List<TaskSnapshot>
}
interface TimeInterpreter {
    /** 不知道来源时间时，禁止默默用读取时间解析“明天”。 */
    suspend fun parse(expression: String, sourceTime: Instant?): TimeParseResult
}

/** B 的持久化边界。写入要有事务、来源幂等和 expectedRevision 校验。 */
interface TaskRepository : TaskContextProvider {
    fun observeTasks(): Flow<List<TaskSnapshot>>
    fun observeChanges(): Flow<List<ChangeProposal>>
    suspend fun getTask(id: String): TaskSnapshot?
    suspend fun createDraft(fields: TaskFields, evidence: SourceEvidence): ActionResult
    suspend fun execute(command: UserCommand): ActionResult
}
interface SettingsStore {
    val settings: StateFlow<UserSettings>
    suspend fun update(settings: UserSettings)
}
interface ReminderGateway {
    suspend fun schedule(plan: ReminderPlan): ScheduleResult
    suspend fun cancel(reminderId: String, generation: Long): ScheduleResult
    suspend fun reconcile(): ScheduleResult
}
/** B 实现，C 只依赖此接口。注入同一实例，禁止每个页面创建自己的业务状态。 */
interface AssistantFacade {
    val tasks: StateFlow<List<TaskSnapshot>>
    val changes: StateFlow<List<ChangeProposal>>
    val settings: StateFlow<UserSettings>
    val captureStatus: StateFlow<CaptureStatus>
    val runtimeStatus: StateFlow<RuntimeStatus>
    val modelStatus: StateFlow<ModelStatus>
    val prompts: Flow<PromptEvent>
    fun observeDetails(taskId: String): Flow<TaskDetails>
    suspend fun dispatch(command: UserCommand): ActionResult
    suspend fun updateSettings(settings: UserSettings): ActionResult
    /** 明确标注为手工接口联调样例；不能作为模型识别效果演示。 */
    suspend fun installDemoFixture(): ActionResult
}
