package com.example.localtaskassistant.capture

import com.example.localtaskassistant.contract.TextBlock

/**
 * A-01.1 的读取结果；包名、字段及含义保持与原 ReadPage 一致。
 * truncated 表示存在未完整读取的内容，而不是“没有任务”。
 * 仅保留顺序文本；消息边界、发送人、源日期仍须真机样本验证。
 */
data class ReadPage(val blocks: List<TextBlock>, val truncated: Boolean)
