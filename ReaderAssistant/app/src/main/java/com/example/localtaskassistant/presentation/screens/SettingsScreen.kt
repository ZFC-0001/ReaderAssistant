package com.example.localtaskassistant.presentation.screens

import android.content.Context
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.localtaskassistant.contract.*
import com.example.localtaskassistant.presentation.*
import com.example.localtaskassistant.presentation.ScreenComponents.button
import com.example.localtaskassistant.presentation.ScreenComponents.label

object SettingsScreen {
    fun render(context: Context, state: AssistantUiState, onSave: (UserSettings) -> Unit,
        onOpenAccessibility: () -> Unit) = ScreenComponents.column(context).apply {
        label("设置 · 联调入口", 22f)
        label("无障碍连接：${if (state.capture.accessibilityConnected) "已连接" else "未连接"}")
        label(state.capture.message, 13f)
        val enabled = CheckBox(context).apply { text = "允许识别所选应用当前可读内容"; isChecked = state.settings.capture.enabled }
        val qq = CheckBox(context).apply { text = "QQ（无障碍基础采集）"; isChecked = TargetApps.QQ in state.settings.capture.allowedPackages }
        val wechat = CheckBox(context).apply { text = "微信（OCR 待实现，当前不采集）"; isChecked = TargetApps.WECHAT in state.settings.capture.allowedPackages }
        addView(enabled); addView(qq); addView(wechat)
        val group = RadioGroup(context)
        val choices = PromptMode.values().map { mode ->
            RadioButton(context).apply {
                id = android.view.View.generateViewId()
                text = when (mode) { PromptMode.TIME_BASED -> "按时间区分"; PromptMode.ALWAYS_CARD -> "始终提示卡"; PromptMode.ALWAYS_INBOX -> "始终待确认队列" }
                isChecked = state.settings.promptMode == mode
                group.addView(this)
            } to mode
        }
        addView(group)
        button("保存设置") {
            val apps = buildSet { if (qq.isChecked) add(TargetApps.QQ); if (wechat.isChecked) add(TargetApps.WECHAT) }
            val mode = choices.firstOrNull { it.first.id == group.checkedRadioButtonId }?.second ?: state.settings.promptMode
            onSave(state.settings.copy(capture = CapturePolicy(enabled.isChecked, apps), promptMode = mode))
        }
        button("打开系统无障碍设置", action = onOpenAccessibility)
        label("当前默认：24小时卡片范围；来源7天；提前30分钟；稍后10分钟。提醒和跨应用卡片仍待实现。", 13f)
        // TODO(C-05): 以上数值可编辑、授权引导、隐私说明与清空数据确认；业务由 B 执行。
        // 只有所选应用才允许采正文；应用开关不能自动代替用户的系统授权。
    }
}
