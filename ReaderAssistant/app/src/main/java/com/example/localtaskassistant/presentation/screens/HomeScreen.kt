package com.example.localtaskassistant.presentation.screens

import android.content.Context
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.presentation.*
import com.example.localtaskassistant.presentation.ScreenComponents.button
import com.example.localtaskassistant.presentation.ScreenComponents.label

object HomeScreen {
    fun render(context: Context, state: AssistantUiState, onDemo: () -> Unit) = ScreenComponents.column(context).apply {
        label("今日概览 · 联调骨架", 22f)
        label("待确认 ${state.tasks.count { it.confirmation == Confirmation.DRAFT }} 项")
        label("已确认未完成 ${state.tasks.count { it.confirmation == Confirmation.CONFIRMED && it.lifecycle != Lifecycle.COMPLETED }} 项")
        label("${state.model.message}\n${state.runtime.message}", 14f)
        label("系统提醒、微信 OCR 和跨应用提示卡尚未实现。下方样例不是 AI 识别结果。", 14f)
        button("载入接口联调样例（非 AI 识别）", action = onDemo)
        // TODO(C-01): 今日待办/即将到来分区；使用 B 提供的展示数据，不在 UI 写业务规则。
    }
}
