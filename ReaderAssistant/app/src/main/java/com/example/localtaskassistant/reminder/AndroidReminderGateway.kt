package com.example.localtaskassistant.reminder

import com.example.localtaskassistant.contract.*

/** B-05/B-07：接口已接好，尚未注册系统闹钟，绝不能返回假成功。 */
class AndroidReminderGateway : ReminderGateway {
    override suspend fun schedule(plan: ReminderPlan): ScheduleResult {
        // TODO(B-05): 权限/通知渠道/AlarmManager；状态写库由 outbox 执行器负责。
        // PendingIntent 的 action/data/requestCode 等身份在 schedule 与 cancel 必须完全一致。
        // 缺精确提醒权限须展示降级状态，不能假称准点；WorkManager 仅作非准点对账。
        return ScheduleResult.Blocked("系统提醒调度尚未实现")
    }
    override suspend fun cancel(reminderId: String, generation: Long) =
        ScheduleResult.Blocked("提醒取消适配器尚未实现")
    override suspend fun reconcile() = ScheduleResult.Blocked("提醒对账尚未实现")
}
