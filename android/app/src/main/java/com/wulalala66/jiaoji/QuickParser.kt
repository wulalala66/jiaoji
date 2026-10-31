package com.wulalala66.jiaoji

import java.util.Calendar

data class ParsedTask(
    val timeMs: Long? = null,
    val customer: String? = null,
    val typeId: String? = null,
    val note: String = "",
    val dayOffset: Int = 0,
    val matchedTime: String = "",
    val matchedCustomer: String = "",
    val matchedType: String = "",
) {
    val hasTime: Boolean get() = timeMs != null
    val hasCustomer: Boolean get() = !customer.isNullOrBlank()
    val hasType: Boolean get() = !typeId.isNullOrBlank()
    val ready: Boolean get() = hasTime && hasCustomer && hasType
}

/**
 * 一句话快速添加：「8点半 安徽万安 底涂胶」「明天下午2点 焊缝胶 东风」
 * 解析出时间 / 客户 / 类型，剩下的文字作为备注。
 */
object QuickParser {

    private val cnDigits = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '壹' to 1, '二' to 2, '两' to 2, '贰' to 2,
        '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    private val typeAlias: List<Pair<String, String>> = listOf(
        "底涂" to "base", "底凃" to "base", "底漆" to "base", "base" to "base",
        "焊缝" to "weld", "焊封" to "weld", "weld" to "weld",
        "点焊" to "spot", "密封" to "seal", "结构" to "struct",
    )

    private fun cnToInt(s: String): Int? {
        if (s.isEmpty()) return null
        if (s.length == 1) return cnDigits[s[0]]
        val ten = s.indexOf('十')
        if (ten >= 0) {
            val hi = if (ten == 0) 1 else cnDigits[s[0]] ?: return null
            val lo = if (ten == s.length - 1) 0 else cnDigits[s[ten + 1]] ?: return null
            return hi * 10 + lo
        }
        return s.toIntOrNull()
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val c = when (ch) {
                in '０'..'９' -> ('0' + (ch - '０'))
                '：' -> ':'
                '．', '。' -> '.'
                '　' -> ' '
                else -> ch
            }
            sb.append(c)
        }
        return sb.toString()
    }

    private class TimeHit(val hour: Int, val minute: Int, val text: String)

    private fun findTime(t: String): TimeHit? {
        Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})").find(t)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return@let
            val m = it.groupValues[2].toIntOrNull() ?: return@let
            if (h in 0..23 && m in 0..59) return TimeHit(h, m, it.value)
        }
        Regex("(\\d{1,2})\\s*[点時时]\\s*(半|一刻|三刻|(\\d{1,2})\\s*分?)?").find(t)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return@let
            val minute = when {
                it.groupValues[2] == "半" -> 30
                it.groupValues[2] == "一刻" -> 15
                it.groupValues[2] == "三刻" -> 45
                else -> it.groupValues[3].trim().removeSuffix("分").toIntOrNull() ?: 0
            }
            if (h in 0..23 && minute in 0..59) return TimeHit(h, minute, it.value)
        }
        Regex("([零〇一壹二两贰三四五六七八九]{1,3})\\s*[点時时]\\s*(半|一刻|三刻|([零〇一壹二两贰三四五六七八九]{1,3})\\s*分?)?").find(t)?.let {
            val h = cnToInt(it.groupValues[1]) ?: return@let
            val minute = when {
                it.groupValues[2] == "半" -> 30
                it.groupValues[2] == "一刻" -> 15
                it.groupValues[2] == "三刻" -> 45
                else -> cnToInt(it.groupValues[3].trim().removeSuffix("分")) ?: 0
            }
            if (h in 0..23 && minute in 0..59) return TimeHit(h, minute, it.value)
        }
        Regex("\\b(\\d{2})(\\d{2})\\b").find(t)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return@let
            val m = it.groupValues[2].toIntOrNull() ?: return@let
            if (h in 0..23 && m in 0..59) return TimeHit(h, m, it.value)
        }
        return null
    }

    private fun dayOffsetOf(t: String, now: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        if (t.contains("大后天")) return 3
        if (t.contains("后天")) return 2
        if (t.contains("明天") || t.contains("明日")) return 1
        if (t.contains("今天") || t.contains("今日") || t.contains("今晚")) return 0
        val m = Regex("(?:周|星期|礼拜)([一二三四五六日天])").find(t)
        if (m != null) {
            val map = mapOf("一" to 2, "二" to 3, "三" to 4, "四" to 5, "五" to 6, "六" to 7, "日" to 1, "天" to 1)
            val target = map[m.groupValues[1]] ?: return 0
            val cur = c.get(Calendar.DAY_OF_WEEK)
            var diff = (target - cur + 7) % 7
            if (diff == 0) diff = 7
            return diff
        }
        return 0
    }

    private fun isPmContext(t: String): Boolean =
        listOf("下午", "晚上", "傍晚", "夜里", "夜间", "傍晚").any { t.contains(it) }

    private fun isAmContext(t: String): Boolean =
        listOf("上午", "早上", "早晨", "凌晨", "清早").any { t.contains(it) }

    fun parse(raw: String, customers: List<String>, types: List<ProductType>, now: Long = System.currentTimeMillis()): ParsedTask {
        val text = normalize(raw).trim()
        if (text.isEmpty()) return ParsedTask()

        var noteText = text
        val hit = findTime(text)
        var timeMs: Long? = null
        var offset = dayOffsetOf(text, now)

        if (hit != null) {
            var h = hit.hour
            if (isPmContext(text) && h < 12) h += 12
            if (isAmContext(text) && h == 12) h = 0
            val c = Calendar.getInstance()
            c.timeInMillis = now
            c.set(Calendar.HOUR_OF_DAY, h.coerceIn(0, 23))
            c.set(Calendar.MINUTE, hit.minute.coerceIn(0, 59))
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.DAY_OF_YEAR, offset)
            var ts = c.timeInMillis
            if (offset == 0 && ts < now - 3_600_000L) {
                // 说的时间已过去 1 小时以上 → 理解为次日
                ts += 86_400_000L
                offset = 1
            }
            timeMs = ts
            noteText = noteText.replace(hit.text, " ")
        }

        var customer: String? = null
        var matchedCustomer = ""
        for (c in customers.sortedByDescending { it.length }) {
            if (c.isNotBlank() && text.contains(c)) {
                customer = c
                matchedCustomer = c
                noteText = noteText.replace(c, " ")
                break
            }
        }

        var typeId: String? = null
        var matchedType = ""
        for ((alias, id) in typeAlias.sortedByDescending { it.first.length }) {
            if (text.contains(alias) && types.any { it.id == id }) {
                typeId = id
                matchedType = alias
                noteText = noteText.replace(alias, " ")
                break
            }
        }
        if (typeId == null) {
            for (tp in types.sortedByDescending { it.name.length }) {
                if (tp.name.length >= 2 && text.contains(tp.name)) {
                    typeId = tp.id
                    matchedType = tp.name
                    noteText = noteText.replace(tp.name, " ")
                    break
                }
            }
        }

        noteText = noteText
            .replace(Regex("(今天|今日|今晚|明天|明日|后天|大后天)"), " ")
            .replace(Regex("(?:周|星期|礼拜)[一二三四五六日天]"), " ")
            .replace(Regex("(上午|下午|早上|早晨|晚上|傍晚|夜里|凌晨|清早)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '，', '、', '-', '。')

        return ParsedTask(
            timeMs = timeMs,
            customer = customer,
            typeId = typeId,
            note = noteText,
            dayOffset = offset,
            matchedTime = hit?.text ?: "",
            matchedCustomer = matchedCustomer,
            matchedType = matchedType,
        )
    }
}
