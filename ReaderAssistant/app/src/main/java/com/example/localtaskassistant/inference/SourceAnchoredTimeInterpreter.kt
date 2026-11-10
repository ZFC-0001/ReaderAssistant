package com.example.localtaskassistant.inference

import com.example.localtaskassistant.contract.*
import java.time.Instant

class SourceAnchoredTimeInterpreter : TimeInterpreter {
    override suspend fun parse(expression: String, sourceTime: Instant?): TimeParseResult {
        // TODO(A-08): 模型提取表达，规则解析具体值；优先锚定可靠来源时间。
        // 过去事件不改成下周；只有日期不补 23:59；未知来源时不假定“明天”以现在为基准。
        return TimeParseResult.NeedsClarification(expression, "时间解析器待实现，未猜测日期")
    }
}
