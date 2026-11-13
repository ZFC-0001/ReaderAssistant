package com.example.localtaskassistant.capture.ocr

internal enum class ProjectionProbePhase { PREPARING, RUNNING, STOPPED, ERROR }
internal enum class ProjectionProbeEnd { USER_STOP, SYSTEM_STOP, LOCKED, TIME_LIMIT, START_FAILED, FRAME_FAILED, SERVICE_DESTROYED, PREVIEW_READY }
internal data class ProjectionProbeSnapshot(
    val runId: String,
    val phase: ProjectionProbePhase,
    val endReason: ProjectionProbeEnd?,
    val frames: Int,
    val variedFrames: Int,
    val consecutiveVariedFrames: Int,
    val scopeSkips: Int,
    val width: Int,
    val height: Int,
    val firstFrameLatencyMillis: Long?,
    val elapsedMillis: Long,
)

/** A-02.1的可独立测试会话：停止后不再接受帧，也不复用上次系统授权。 */
internal class ProjectionProbeSession(private val runId: String, private val startedAt: Long) {
    init { require(runId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }
    private var phase = ProjectionProbePhase.PREPARING
    private var endReason: ProjectionProbeEnd? = null
    private var frames = 0
    private var variedFrames = 0
    private var consecutive = 0
    private var skips = 0
    private var width = 0
    private var height = 0
    private var firstLatency: Long? = null

    @Synchronized fun running() {
        if (phase == ProjectionProbePhase.PREPARING) phase = ProjectionProbePhase.RUNNING
    }
    @Synchronized fun frame(signal: ProjectionPixelSignal, now: Long) {
        if (phase != ProjectionProbePhase.RUNNING) return
        if (firstLatency == null) firstLatency = (now - startedAt).coerceAtLeast(0)
        frames = (frames + 1).coerceAtMost(10_000)
        if (signal.hasVariation) {
            variedFrames = (variedFrames + 1).coerceAtMost(10_000)
            consecutive = (consecutive + 1).coerceAtMost(2)
        } else consecutive = 0
        width = signal.width
        height = signal.height
    }
    @Synchronized fun scopeSkipped() {
        if (phase == ProjectionProbePhase.RUNNING) {
            skips = (skips + 1).coerceAtMost(10_000)
            consecutive = 0
        }
    }
    @Synchronized fun end(reason: ProjectionProbeEnd) {
        if (phase == ProjectionProbePhase.STOPPED || phase == ProjectionProbePhase.ERROR) return
        phase = if (reason in setOf(ProjectionProbeEnd.START_FAILED, ProjectionProbeEnd.FRAME_FAILED))
            ProjectionProbePhase.ERROR else ProjectionProbePhase.STOPPED
        endReason = reason
    }
    @Synchronized fun snapshot(now: Long) = ProjectionProbeSnapshot(runId, phase, endReason,
        frames, variedFrames, consecutive, skips, width, height, firstLatency, (now - startedAt).coerceAtLeast(0))
}

internal object ProjectionScopeGate {
    fun allows(policyEnabled: Boolean, wechatSelected: Boolean, serviceConnected: Boolean,
               interactive: Boolean, locked: Boolean, activePackage: String?): Boolean =
        policyEnabled && wechatSelected && serviceConnected && interactive && !locked && activePackage == "com.tencent.mm"
}
