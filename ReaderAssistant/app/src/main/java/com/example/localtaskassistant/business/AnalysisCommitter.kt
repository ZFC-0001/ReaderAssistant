package com.example.localtaskassistant.business

import com.example.localtaskassistant.contract.*

/** B 负责。A 的分析结果只能通过这里进入业务层，模型不得直接写库。 */
class AnalysisCommitter(private val repository: TaskRepository) {
    suspend fun apply(suggestion: TaskSuggestion): ActionResult = when (suggestion.kind) {
        SuggestionKind.NEW_TASK -> {
            val fields = suggestion.fields
            val evidence = suggestion.evidence.firstOrNull()
            if (fields == null || evidence == null || suggestion.target != null)
                ActionResult(ActionStatus.INVALID, "新任务字段或来源不完整")
            else if (suggestion.evidence.size != 1)
                ActionResult(ActionStatus.NOT_IMPLEMENTED, "多来源任务写入待 B-02 实现，未丢弃额外证据后冒充成功")
            else repository.createDraft(fields, evidence)
        }
        // TODO(B-02): 可靠候选关联/补全/合并/拆分，保留 source -> fields 和被合并编号的别名。
        // TODO(B-03): 正式变更/取消 -> 提案；原任务、原提醒不动，重复来源不重复提案。
        // TODO(B-04): EXPIRED -> REOPEN 提案；确认后原 ID 恢复；COMPLETED 不自动恢复。
        else -> ActionResult(ActionStatus.NOT_IMPLEMENTED, "关联、变更或恢复流程待 B-02/B-03/B-04 实现")
    }
}
