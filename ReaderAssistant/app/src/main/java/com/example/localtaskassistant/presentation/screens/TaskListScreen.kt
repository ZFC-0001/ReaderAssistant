package com.example.localtaskassistant.presentation.screens

import android.content.Context
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.presentation.*
import com.example.localtaskassistant.presentation.ScreenComponents.label

object TaskListScreen {
    fun render(context: Context, tasks: List<TaskSnapshot>, onOpen: (String) -> Unit) = ScreenComponents.column(context).apply {
        label("正式待办", 22f)
        val confirmed = tasks.filter { it.confirmation == Confirmation.CONFIRMED }
        if (confirmed.isEmpty()) label("暂无已确认任务")
        confirmed.forEach { task -> addView(ScreenComponents.taskCard(context, task) { onOpen(task.id) }) }
        // TODO(C-03): 分组/筛选/空状态；只读取同一任务流，禁止另外维护待办副本。
    }
}
