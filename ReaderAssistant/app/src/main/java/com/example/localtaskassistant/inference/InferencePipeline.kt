package com.example.localtaskassistant.inference

import com.example.localtaskassistant.contract.*

/** A 负责模型输入、检索策略、结构校验；B 提供查询接口，不向 A 暴露 DAO。 */
class InferencePipeline(
    private val context: TaskContextProvider,
    private val engine: SemanticEngine,
) {
    suspend fun analyze(content: CapturedContent): AnalysisOutcome {
        if (content.truncated) return AnalysisOutcome.Invalid("页面读取被截断，需补采后分析")
        // TODO(A-07): 轻量噪声过滤；不能过滤“取消/延期/推迟”，也不能丢掉缺时间的补充句。
        val related = context.findRelated(content, 12)
        val result = engine.analyze(AnalysisRequest(content, related))
        if (result !is AnalysisOutcome.Success) return result
        val known = related.associateBy { it.id }
        for (suggestion in result.suggestions) {
            val target = suggestion.target
            if (target != null && known[target.id]?.revision != target.revision)
                return AnalysisOutcome.Invalid("模型引用了未提供或过时的任务")
            if (suggestion.kind == SuggestionKind.NEW_TASK && target != null)
                return AnalysisOutcome.Invalid("新任务不能同时指定旧任务")
            if (suggestion.kind !in setOf(SuggestionKind.NEW_TASK, SuggestionKind.UNCERTAIN) && target == null)
                return AnalysisOutcome.Invalid("补充或变更建议未指定关联任务")
            if (suggestion.kind != SuggestionKind.UNCERTAIN && suggestion.evidence.isEmpty())
                return AnalysisOutcome.Invalid("任务建议缺少来源依据")
            val pageText = content.blocks.joinToString("\n") { it.text }
            if (suggestion.evidence.any { it.packageName != content.packageName ||
                    it.publishedAt != content.publishedAt || it.observedAt != content.observedAt ||
                    it.fingerprint.isBlank() || it.fragmentKey.isBlank() ||
                    it.excerpt.isBlank() || !pageText.contains(it.excerpt) })
                return AnalysisOutcome.Invalid("来源证据不在当前输入中")
        }
        // TODO(A-08): 时间/身份/来源日期可靠性、无证据字段、关联歧义和冲突的完整校验。
        return result
    }
}
