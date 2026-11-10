package com.example.localtaskassistant.presentation

import android.content.Context
import android.view.View
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.presentation.ScreenComponents.button
import com.example.localtaskassistant.presentation.ScreenComponents.label

/** C-02 负责内容样式；B-06 负责投递/持久回执，A 配合系统显示能力验证。 */
class PromptCardRenderer {
    fun render(context: Context, task: TaskSnapshot, actualReminderLabel: String,
        onConfirm: (TaskRef) -> Unit): View = ScreenComponents.column(context).apply {
        label(task.fields.title)
        label(ScreenComponents.timeLabel(task.fields.time))
        label(actualReminderLabel)
        button("确认") { onConfirm(TaskRef(task.id, task.revision)) }
        // TODO(C-02): 不确定项/修改入口/变更前后对比；收起后仍在队列，不另建任务。
    }
}
