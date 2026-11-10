package com.example.localtaskassistant.contract

enum class SuggestionKind { NEW_TASK, SUPPLEMENT, CHANGE, CANCEL, REOPEN, UNCERTAIN }
data class AnalysisRequest(val content: CapturedContent, val related: List<TaskSnapshot>)
data class TaskSuggestion(
    val kind: SuggestionKind,
    val fields: TaskFields?,
    val target: TaskRef?,
    val evidence: List<SourceEvidence>,
    val uncertainties: List<String> = emptyList(),
)
sealed interface AnalysisOutcome {
    data class Success(val suggestions: List<TaskSuggestion>) : AnalysisOutcome
    /** 模型缺失、OCR 未接入不能被伪装为“没有任务”。 */
    data class Unavailable(val reason: String) : AnalysisOutcome
    data class Invalid(val reason: String) : AnalysisOutcome
}
data class ModelStatus(val ready: Boolean, val message: String)
sealed interface TimeParseResult {
    data class Parsed(val value: TaskTime) : TimeParseResult
    data class NeedsClarification(val original: String, val reason: String) : TimeParseResult
}
