package com.example.localtaskassistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // TODO(B-07): goAsync + try/finally finish；先查 reminderId/generation/taskRevision。
        // 校验完成/取消/过时广播；核验通知权限与渠道后才发送，禁止提前标记成功并吞异常。
        // SNOOZE 可以在超时后提醒摘要，但不能恢复任务；已完成任务的旧动作必须失效。
        // 通知完成/稍后操作走业务接口，不绕过 revision/generation 门禁。
        // 当前未实现，无通知发出；不把收到广播伪装成通知实际显示。
    }
    companion object { const val ACTION = "com.example.localtaskassistant.REMINDER" }
}
