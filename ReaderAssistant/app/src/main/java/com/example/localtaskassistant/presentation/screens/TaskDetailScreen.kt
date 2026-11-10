package com.example.localtaskassistant.presentation.screens

import android.content.Context
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.presentation.*
import com.example.localtaskassistant.presentation.ScreenComponents.button
import com.example.localtaskassistant.presentation.ScreenComponents.label

object TaskDetailScreen {
    fun render(context: Context, task: TaskSnapshot?, onCommand: (UserCommand) -> Unit) = ScreenComponents.column(context).apply {
        label("任务详情", 22f)
        if (task == null) { label("任务不存在或正在加载"); return@apply }
        label(task.fields.title)
        label(ScreenComponents.taskStatus(task))
        label(ScreenComponents.timeLabel(task.fields.time))
        label("地点：${task.fields.location ?: "未确认"}\n要求：${task.fields.requirements ?: "未确认"}")
        label("联调编号：${task.id}\n版本：${task.revision}", 12f)
        label("提醒：调度适配器未接入，不代表已安排", 13f)
        val ref = TaskRef(task.id, task.revision)
        if (task.confirmation == Confirmation.DRAFT)
            button("确认保存", task.lifecycle == Lifecycle.OPEN) { onCommand(UserCommand.Confirm(ref)) }
        if (task.confirmation == Confirmation.CONFIRMED && task.lifecycle != Lifecycle.COMPLETED)
            button("标记完成") { onCommand(UserCommand.Complete(ref)) }
        // TODO(C-04): 字段编辑、来源、变更差异、错误合并拆分；对应 B-02/03/04 先完成接口。
        // TODO(C-04): 已结束任务只展示保留摘要，不显示已清理详情或伪造历史。
        // 提醒设置/稍后按钮须接入真实提醒状态后启用，不传猜测的 generation。
    }
}
