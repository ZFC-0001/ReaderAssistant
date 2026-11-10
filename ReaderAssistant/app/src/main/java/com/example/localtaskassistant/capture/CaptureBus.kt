package com.example.localtaskassistant.capture

import com.example.localtaskassistant.contract.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** 成员一入口：应用级单例，不属于 Activity。仅协调器可以消费 contents。 */
class CaptureBus : CaptureGateway {
    private val queue = Channel<CapturedContent>(capacity = 16)
    private val mutableStatus = MutableStateFlow(CaptureStatus())
    private val lastPage = mutableMapOf<String, String>()
    override val contents = queue.receiveAsFlow()
    override val status: StateFlow<CaptureStatus> = mutableStatus.asStateFlow()

    @Synchronized
    override fun updatePolicy(policy: CapturePolicy) {
        if (policy == status.value.policy) return
        lastPage.clear()
        mutableStatus.value = status.value.copy(
            policy = policy,
            session = status.value.session + 1,
            message = if (policy.enabled) "QQ 采集可用；本地 AI 和微信 OCR 待接入" else "识别已暂停",
        )
    }
    override fun isCurrentSession(session: Long) =
        status.value.policy.enabled && status.value.session == session

    @Synchronized
    fun offer(content: CapturedContent) {
        val policy = status.value.policy
        if (!isCurrentSession(content.session) || content.packageName !in policy.allowedPackages) return
        if (lastPage[content.packageName] == content.pageFingerprint) return
        if (queue.trySend(content).isSuccess) {
            lastPage[content.packageName] = content.pageFingerprint
        } else {
            mutableStatus.value = status.value.copy(
                droppedPages = status.value.droppedPages + 1,
                message = "处理队列已满，有页面未接收；请暂停后重试",
            )
            // TODO(A-05): 设计有界背压/补采机制；不能无限保存画面，也不能隐瞒丢页。
        }
    }
    fun connected(value: Boolean) { mutableStatus.update { it.copy(accessibilityConnected = value) } }
    fun diagnostic(message: String) { mutableStatus.update { it.copy(message = message) } }
}
