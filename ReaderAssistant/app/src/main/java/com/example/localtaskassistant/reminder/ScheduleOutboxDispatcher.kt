package com.example.localtaskassistant.reminder

import com.example.localtaskassistant.contract.*

/** B：数据库修改与系统闹钟不能一次原子提交，因此通过持久调度命令重试/对账。 */
class ScheduleOutboxDispatcher(private val gateway: ReminderGateway) {
    suspend fun drain(): ScheduleResult {
        // TODO(B-05): 读取待执行命令，检查任务 revision/generation，执行后回写；失败保留原因。
        // TODO(B-05): CANCEL 不得被旧 SCHEDULE 覆盖；启动/重启/权限恢复均触发对账。
        // gateway 未就绪时不能把命令标记完成，也不能在 UI 写“提醒已安排”。
        return gateway.reconcile()
    }
}
