package com.example.localtaskassistant.contract

import java.time.Instant

object TargetApps {
    const val QQ = "com.tencent.mobileqq"
    const val WECHAT = "com.tencent.mm"
    val supported = setOf(QQ, WECHAT)
}
enum class CaptureMethod { ACCESSIBILITY, LOCAL_OCR }
data class TextBlock(val text: String, val order: Int, val sender: String? = null)

/** publishedAt 是来源发送/发布时间；未知保留 null，绝不回填 observedAt。 */
data class CapturedContent(
    val captureId: String,
    val session: Long,
    val packageName: String,
    val method: CaptureMethod,
    val blocks: List<TextBlock>,
    val observedAt: Instant,
    val publishedAt: Instant? = null,
    val conversation: String? = null,
    val pageFingerprint: String,
    val truncated: Boolean = false,
)
data class CapturePolicy(
    val enabled: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
)
data class CaptureStatus(
    val policy: CapturePolicy = CapturePolicy(),
    val session: Long = 0,
    val accessibilityConnected: Boolean = false,
    val message: String = "识别已暂停；微信 OCR 尚未实现",
    val droppedPages: Long = 0,
)
/** 截图数据只在内存中处理；A 负责 finally 释放 buffer，不保存持续录像。 */
data class OcrFrame(val bytes: ByteArray, val width: Int, val height: Int)
