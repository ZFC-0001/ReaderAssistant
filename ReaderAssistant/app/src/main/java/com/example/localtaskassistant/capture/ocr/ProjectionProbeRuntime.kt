package com.example.localtaskassistant.capture.ocr

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** 只提供当前窗口是否为微信的布尔判断，不返回节点/文字；服务断开即撤下。 */
internal object WechatProjectionAccess {
    private var owner: Any? = null
    private var checker: (() -> Boolean)? = null
    @Synchronized fun attach(owner: Any, checker: () -> Boolean) { this.owner = owner; this.checker = checker }
    @Synchronized fun detach(owner: Any) { if (this.owner === owner) { this.owner = null; checker = null } }
    fun isWechatForeground(): Boolean {
        val query = synchronized(this) { checker } ?: return false
        return try { query() } catch (_: SecurityException) { false } catch (_: IllegalStateException) { false }
    }
}

internal object ProjectionProbeReports {
    @Volatile var latest: ProjectionProbeSnapshot? = null
        private set
    @Synchronized fun publish(context: Context, session: ProjectionProbeSession) {
        val value = session.snapshot(SystemClock.elapsedRealtime())
        latest = value
        // 仅统计信息落私有文件，绝不写像素、截图、授权Intent或聊天内容。
        val json = JSONObject().put("runId", value.runId).put("phase", value.phase.name)
            .put("endReason", value.endReason?.name ?: JSONObject.NULL)
            .put("frames", value.frames).put("variedFrames", value.variedFrames)
            .put("consecutiveVariedFrames", value.consecutiveVariedFrames)
            .put("scopeSkips", value.scopeSkips).put("width", value.width).put("height", value.height)
            .put("firstFrameLatencyMillis", value.firstFrameLatencyMillis ?: JSONObject.NULL)
            .put("elapsedMillis", value.elapsedMillis)
        try {
            val directory = File(context.filesDir, "wechat-projection-probe")
            if (!directory.exists() && !directory.mkdirs()) return
            val file = AtomicFile(File(directory, "report.json"))
            val output = file.startWrite()
            try {
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (_: IOException) { file.failWrite(output) }
        } catch (_: IOException) { /* 无报告不得冒充通过。 */ }
        catch (_: SecurityException) { /* 不扩大文件访问权限。 */ }
    }
}
