package com.example.localtaskassistant.inference

import com.example.localtaskassistant.contract.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 成员一 A-03/A-04；替换此适配器，不改 B/C 的接口。当前明确返回未接入。 */
class LocalSemanticEngine : SemanticEngine {
    private val mutableStatus = MutableStateFlow(ModelStatus(false, "本地小模型尚未接入"))
    override val status = mutableStatus.asStateFlow()
    override suspend fun analyze(request: AnalysisRequest): AnalysisOutcome {
        // TODO(A-03): 用固定样本评估中文任务/归属/口语时间/关联/取消/冲突，再选运行时和模型。
        // TODO(A-04): 权重/分词器随包；加载/卸载/超时/取消/线程数/内存预算和结构化解码。
        // 禁止把原型规则分数冒充概率，禁止把规则提取冒充模型推理。
        return AnalysisOutcome.Unavailable("本地模型待接入；本次内容没有生成任务")
    }
}
