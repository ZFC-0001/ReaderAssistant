package com.example.localtaskassistant.business

import com.example.localtaskassistant.contract.*

/** 成员二 B-08。与版本/恢复提案配合，避免旧清理批次删除新延期证据。 */
interface RetentionCoordinator {
    suspend fun expireAndClean(): ActionResult
    suspend fun deleteAll(confirmationToken: String): ActionResult
}
class PendingRetentionCoordinator : RetentionCoordinator {
    override suspend fun expireAndClean() = ActionResult(ActionStatus.NOT_IMPLEMENTED,
        "TODO(B-08)：超时状态、详细来源、历史和推理缓存的一致清理")
    override suspend fun deleteAll(confirmationToken: String) = ActionResult(ActionStatus.NOT_IMPLEMENTED,
        "TODO(B-09)：暂停并失效旧会话、取消提醒、清库/图片/缓存、恢复默认设置")
}
