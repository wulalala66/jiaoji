package com.gluelab.jiaoji

/**
 * 把 OCR 返回的文本清洗成 M1~M6。
 *
 * 实测在线接口会把小数点吞掉（3.21 → "321"），所以这里的核心是：
 * 先按 x.xx 抓，抓不到就按 3~4 位补点。
 */
object DigitOcr {

    fun parse(raw: String): List<Double> {
        val cleaned = raw
            .replace(Regex("[OoＯ〇]"), "0")
            .replace(Regex("[lI|｜]"), "1")
            .replace('，', '.').replace('。', '.').replace('．', '.')
            .replace(Regex("[０-９]")) { (it.value[0].code - '０'.code + '0'.code).toChar().toString() }

        val out = ArrayList<Double>()

        // 带小数点的：最可靠
        Regex("""(\d{1,3})\s*[.,]\s*(\d{1,2})""").findAll(cleaned).forEach { m ->
            val v = (m.groupValues[1] + "." + m.groupValues[2]).toDoubleOrNull() ?: return@forEach
            if (v > 0.05 && v < 500) out.add(v)
        }

        // 小数点丢了：3 位 → x.xx，4 位 → xx.xx
        if (out.size < 4) {
            Regex("""\d{3,4}""").findAll(cleaned).forEach { m ->
                val t = m.value
                val v = when (t.length) {
                    3 -> (t[0].toString() + "." + t.substring(1)).toDoubleOrNull()
                    4 -> (t.substring(0, 2) + "." + t.substring(2)).toDoubleOrNull()
                    else -> null
                }
                if (v != null && v > 0.05 && v < 500) out.add(v)
            }
        }
        return out.take(6)
    }
}
