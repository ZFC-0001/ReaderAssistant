package com.example.readertest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskExtractor {

    private val CANCEL_KEYWORDS = listOf("取消", "不用了", "别做了", "暂停", "延期了", "取消了", "推迟", "改天")
    private val ACK_KEYWORDS = listOf("好的", "收到", "ok", "OK", "可以", "行", "知道了", "嗯嗯", "👍")

    fun extract(sourceText: String, pkg: String): List<ExtractedTask> {
        val results = mutableListOf<ExtractedTask>()
        if (sourceText.isBlank()) return results

        val sentences = sourceText.split(" | ").flatMap { it.split(Regex("(?<=[。！？.!?\\n])")) }
            .map { it.trim() }.filter { it.length in 4..200 }

        for (sentence in sentences) {
            if (ACK_KEYWORDS.any { sentence.contains(it) } && sentence.length < 20) continue
            if (CANCEL_KEYWORDS.any { sentence.contains(it) }) continue

            val deadline = parseDeadline(sentence)
            val confidence = computeConfidence(sentence, deadline)
            if (confidence < 0.4f) continue

            results.add(
                ExtractedTask(
                    content = sentence,
                    deadline = deadline,
                    sourcePackage = pkg,
                    sourceText = sentence,
                    confidence = confidence
                )
            )
        }
        return results
    }

    private fun computeConfidence(text: String, deadline: Long?): Float {
        var score = 0.3f
        if (deadline != null) score += 0.4f
        val taskVerbs = listOf("开会", "提交", "交", "完成", "做", "准备", "复习", "上课", "值班", "联系", "回复", "修改", "写", "发", "去")
        if (taskVerbs.any { text.contains(it) }) score += 0.2f
        if (Regex("""\d+[点时]""").containsMatchIn(text)) score += 0.1f
        if (Regex("""[周星][一二三四五六日天]""").containsMatchIn(text)) score += 0.1f
        return score.coerceAtMost(1.0f)
    }

    private fun parseDeadline(text: String): Long? {
        val now = Calendar.getInstance()
        val cal = now.clone() as Calendar

        val minutesMatch = Regex("""(\d{1,3})\s*分钟[之後后]""").find(text)
        val hoursMatch = Regex("""(\d{1,2})\s*(?:个)?小时[之後后]""").find(text)
        if (minutesMatch != null || hoursMatch != null) {
            var offset = 0L
            hoursMatch?.let { offset += it.groupValues[1].toLong() * 3_600_000L }
            minutesMatch?.let { offset += it.groupValues[1].toLong() * 60_000L }
            return now.timeInMillis + offset
        }

        // 相对日
        when {
            text.contains("今天") -> { cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59) }
            text.contains("明天") -> { cal.add(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59) }
            text.contains("后天") -> { cal.add(Calendar.DAY_OF_MONTH, 2); cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59) }
            else -> {
                val weekMatch = Regex("""([周星期])([一二三四五六日天])""").find(text)
                if (weekMatch != null) {
                    val dayMap = mapOf("一" to Calendar.MONDAY, "二" to Calendar.TUESDAY,
                        "三" to Calendar.WEDNESDAY, "四" to Calendar.THURSDAY, "五" to Calendar.FRIDAY,
                        "六" to Calendar.SATURDAY, "日" to Calendar.SUNDAY, "天" to Calendar.SUNDAY)
                    val target = dayMap[weekMatch.groupValues[2]] ?: return null
                    cal.set(Calendar.DAY_OF_WEEK, target)
                    if (cal.before(now)) cal.add(Calendar.WEEK_OF_YEAR, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                } else {
                    val dateMatch = Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(text)
                    if (dateMatch != null) {
                        val m = dateMatch.groupValues[1].toInt()
                        val d = dateMatch.groupValues[2].toInt()
                        cal.set(Calendar.MONTH, m - 1); cal.set(Calendar.DAY_OF_MONTH, d)
                        if (cal.before(now)) cal.add(Calendar.YEAR, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                    } else return null
                }
            }
        }

        // 具体时刻覆盖
        val timeMatch = Regex("""(\d{1,2})[点时:：](\d{1,2})?""").find(text)
        if (timeMatch != null) {
            val h = timeMatch.groupValues[1].toInt().coerceIn(0, 23)
            val min = timeMatch.groupValues[2].toIntOrNull() ?: 0
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
        }

        // "X前" → 截止到当天 23:59（若没有具体时刻）
        if (text.contains("前") && timeMatch == null) {
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        }

        return cal.timeInMillis
    }

    fun formatDeadline(deadline: Long?): String {
        if (deadline == null) return "时间待确认"
        val cal = Calendar.getInstance()
        cal.timeInMillis = deadline
        val now = Calendar.getInstance()
        val diffHours = (deadline - now.timeInMillis) / 3_600_000L
        val fmt = SimpleDateFormat("MM月d日 HH:mm", Locale.getDefault())
        return when {
            diffHours in 0..24 -> "${diffHours}小时后 · ${fmt.format(Date(deadline))}"
            diffHours in 24L..(24 * 7) -> "${diffHours / 24}天后 · ${fmt.format(Date(deadline))}"
            else -> fmt.format(Date(deadline))
        }
    }
}
