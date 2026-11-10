package com.example.localtaskassistant.presentation

import android.content.Context
import android.view.View
import android.widget.*
import com.example.localtaskassistant.contract.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** C 可替换成 Compose 组件；这是能编译联调的 Views 占位，不是最终视觉设计。 */
object ScreenComponents {
    fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 16, 24, 24)
    }
    fun LinearLayout.label(value: String, size: Float = 16f) = addView(TextView(context).apply {
        text = value; textSize = size; setPadding(0, 10, 0, 10)
    })
    fun LinearLayout.button(value: String, enabled: Boolean = true, action: () -> Unit) = addView(Button(context).apply {
        text = value; isEnabled = enabled; setOnClickListener { action() }
    })
    fun timeLabel(time: TaskTime): String = when (time.precision) {
        TimePrecision.NONE -> "无时间，不自动提醒"
        TimePrecision.UNRESOLVED -> "时间待澄清：${time.expression.orEmpty()}"
        TimePrecision.DATE -> "${time.date}（仅日期，不自动提醒）"
        TimePrecision.DATE_TIME -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(time.zone)).format(time.instant)
    }
    fun taskStatus(task: TaskSnapshot) = when (task.lifecycle) {
        Lifecycle.COMPLETED -> "已完成"
        Lifecycle.EXPIRED -> "已超时"
        Lifecycle.OPEN -> if (task.confirmation == Confirmation.DRAFT) "待确认" else "已确认"
    }
    fun taskCard(context: Context, task: TaskSnapshot, onOpen: () -> Unit): View = column(context).apply {
        setBackgroundColor(0xfff1f4f8.toInt())
        label(task.fields.title)
        label("${taskStatus(task)} · ${timeLabel(task.fields.time)}", 13f)
        button("查看详情", action = onOpen)
        // TODO(C-01): 正式卡片视觉与无障碍描述；这里不计算超时，也不更改任务状态。
    }
}
