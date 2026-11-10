package com.example.localtaskassistant.presentation.screens

import android.content.Context
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.presentation.*
import com.example.localtaskassistant.presentation.ScreenComponents.label

object PendingScreen {
    fun render(context: Context, tasks: List<TaskSnapshot>, onOpen: (String) -> Unit) = ScreenComponents.column(context).apply {
        label("待确认", 22f)
        val pending = tasks.filter { it.confirmation == Confirmation.DRAFT }
        if (pending.isEmpty()) label("暂无待确认任务")
        pending.forEach { task -> addView(ScreenComponents.taskCard(context, task) { onOpen(task.id) }) }
        // TODO(C-02): 待确认变更/冲突选择/超时标签；接入 facade.changes，操作交给 B。
    }
}
