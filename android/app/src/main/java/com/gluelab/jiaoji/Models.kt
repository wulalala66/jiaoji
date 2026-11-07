package com.gluelab.jiaoji

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ============================================================
//  数据模型
//  存储格式与网页版共用一份 JSON（v1），两边的备份文件可以互相导入。
// ============================================================

/**
 * AI 助手的连接配置（OpenAI 兼容接口）。
 * baseUrl 填到 /v1 为止，例如 https://api.openai.com/v1 或自己的中转地址。
 */
data class AiSettings(
    var baseUrl: String = "",
    var apiKey: String = "",
    var model: String = "",
    /** 思考档位："" 关 / low / medium / high */
    var thinking: String = "",
    /** 对话太长时自动压缩成摘要 */
    var compress: Boolean = true,
    /** 改配料时让 AI 顺手点评一句 */
    var liveComment: Boolean = true,
    var systemPrompt: String = "",
) {
    val ready: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class Settings(
    var ai: AiSettings = AiSettings(),
)

/** 一条烘烤条件：多少度烤多少分钟 */
data class BakePreset(var tempC: Int = 140, var minutes: Int = 30) {
    fun text(): String = "${tempC}℃ ${minutes}分"
}

/**
 * 产品 / 客户对应的烘烤条件（车间那张表）。
 *
 * 除了温度时间，还要记「这次烘烤要几块电泳板」：
 * 有的客户要求新板（还要指定流挂），有的新旧都行。
 * 例：安徽万安 4 块 —— 新板 1 块（2mm 流挂），旧板 3 块（旧板不管流挂）。
 */
data class Product(
    var id: String = "",
    var name: String = "",
    var bakes: MutableList<BakePreset> = mutableListOf(),
    /** 这次烘烤要几块电泳板，0 = 不需要 */
    var plateTotal: Int = 0,
    /** 其中必须用新板的块数（0 = 新旧都行） */
    var plateNew: Int = 0,
    /** 新板的要求，比如「2mm 流挂」；旧板不用填 */
    var plateSpec: String = "",
) {
    val plateOld: Int get() = (plateTotal - plateNew).coerceAtLeast(0)
    val hasPlates: Boolean get() = plateTotal > 0

    /** 列表和 AI 都用这一句 */
    fun plateText(): String = when {
        !hasPlates -> "不用电泳板"
        plateNew <= 0 -> "电泳板 $plateTotal 块（新旧都行）"
        else -> buildString {
            append("电泳板 ").append(plateTotal).append(" 块：新 ").append(plateNew)
            if (plateSpec.isNotBlank()) append("（").append(plateSpec).append("）")
            if (plateOld > 0) append(" / 旧 ").append(plateOld)
        }
    }
}

/**
 * 配料：一锅固定总量（比如 600g = 100%），每种原料占多少 %。
 * 百分比是唯一真相 —— 改总量时克数自动跟着变，配方本身不动。
 */
data class FormulaItem(
    var id: String = "",
    var name: String = "",
    var percent: Double = 0.0,
)

data class Formula(
    var id: String = "",
    var name: String = "",
    var totalG: Double = 600.0,
    var items: MutableList<FormulaItem> = mutableListOf(),
    var note: String = "",
    /** 打印用的「实验目的」，比如「降低粘度」 */
    var purpose: String = "",
    /** 打印用的「固化温度」，比如「140*30」 */
    var cure: String = "",
) {
    fun sumPercent(): Double = items.sumOf { it.percent }
    fun gramsOf(it: FormulaItem): Double = it.percent * totalG / 100.0
    fun sumGrams(): Double = items.sumOf { gramsOf(it) }
    fun restPercent(): Double = 100.0 - sumPercent()
    fun restGrams(): Double = totalG - sumGrams()
    /** 合计是否已经凑满 100%（容忍一位小数的舍入误差） */
    fun isComplete(): Boolean = kotlin.math.abs(restPercent()) < 0.05
}

/**
 * 比重用的六个称量值（对应车间那张 Excel 的 M1~M6）：
 *   M1 / M2  烘前（杯 + 样）
 *   M3 / M4  烘后
 *   M5 / M6  固份（可留空，留空按 0 算，这时只算比重不算固含）
 */
data class Density(
    var m1: Double = 0.0,
    var m2: Double = 0.0,
    var m3: Double = 0.0,
    var m4: Double = 0.0,
    var m5: Double = 0.0,
    var m6: Double = 0.0,
) {
    fun toList(): List<Double> = listOf(m1, m2, m3, m4, m5, m6)
    val hasSolid: Boolean get() = m5 > 0.0 && m6 > 0.0
}

data class DensityResult(
    val d1: Double = 0.0,
    val d2: Double = 0.0,
    val d3: Double = 0.0,
    val d4: Double = 0.0,
    val specific: Double? = null, // 比重 = d1 / (d1 - d2)
    val solid: Double? = null,    // 固体成分 % = d3 / d1 * 100
    val foam: Double? = null,     // 发泡率 % = ((d3 - d4) / (d1 - d2) - 1) * 100
)

/** 公式照抄车间的 Excel，别自己改：d1=m3-m1, d2=m4-m2, d3=m5-m1, d4=m6-m2 */
object DensityCalc {
    fun calc(m1: Double, m2: Double, m3: Double, m4: Double, m5: Double, m6: Double): DensityResult {
        val d1 = m3 - m1
        val d2 = m4 - m2
        val d3 = m5 - m1
        val d4 = m6 - m2
        val denom = d1 - d2
        val okDen = kotlin.math.abs(denom) > 1e-6
        val specific = if (d1 > 0.0 && okDen) d1 / denom else null
        val hasSolid = m5 > 0.0 && m6 > 0.0
        val solid = if (hasSolid && d1 > 0.0) d3 / d1 * 100.0 else null
        val foam = if (hasSolid && okDen) ((d3 - d4) / denom - 1.0) * 100.0 else null
        return DensityResult(d1, d2, d3, d4, specific, solid, foam)
    }

    fun calc(d: Density?): DensityResult =
        if (d == null) calc(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        else calc(d.m1, d.m2, d.m3, d.m4, d.m5, d.m6)

    fun text(v: Double?, digits: Int = 4): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        return String.format(Locale.US, "%." + digits + "f", v)
    }
}

class AppState(
    var settings: Settings = Settings(),
    var products: MutableList<Product> = mutableListOf(),
    var formulas: MutableList<Formula> = mutableListOf(),
)

// ============================================================
//  默认数据
// ============================================================

object Defaults {

    fun products(): MutableList<Product> = mutableListOf(
        Product("p01", "中亮/中亮VF3", mutableListOf(BakePreset(150, 25))),
        Product("p02", "焊缝胶", mutableListOf(BakePreset(140, 30))),
        Product("p03", "折边胶/结构胶", mutableListOf(BakePreset(180, 20))),
        Product("p04", "奥博/圣歌瑞", mutableListOf(BakePreset(150, 25))),
        Product("p05", "润东铭", mutableListOf(BakePreset(160, 20))),
        Product("p06", "润金泽/六安", mutableListOf(BakePreset(150, 30), BakePreset(170, 20))),
        Product("p07", "柳州万安", mutableListOf(BakePreset(160, 20))),
        Product(
            "p08", "安徽万安", mutableListOf(BakePreset(170, 20), BakePreset(210, 20)),
            plateTotal = 4, plateNew = 1, plateSpec = "2mm 流挂",
        ),
        Product("p09", "江阴铝板底涂", mutableListOf(BakePreset(155, 30))),
        Product("p10", "五菱灰色底涂", mutableListOf(BakePreset(140, 30))),
        Product("p11", "方达发泡底涂", mutableListOf(BakePreset(160, 20))),
        Product("p12", "珂亦铝板不发泡", mutableListOf(BakePreset(160, 25), BakePreset(170, 25))),
        Product("p13", "太仓发泡底涂", mutableListOf(BakePreset(160, 20))),
        Product("p14", "安徽众源", mutableListOf(BakePreset(160, 25))),
        Product("p15", "宁波惠轩", mutableListOf(BakePreset(170, 20))),
        Product("p16", "太仓格盛恩", mutableListOf(BakePreset(170, 20))),
    )

    /** 第一次打开时给一张示例配料单，比空列表好懂：一眼看出「百分比 + 总量」怎么用 */
    fun formulas(): MutableList<Formula> = mutableListOf(
        Formula(
            id = "f_demo",
            name = "底涂胶 A 料（示例）",
            totalG = 600.0,
            purpose = "示例：可以改，也可以直接删",
            cure = "140*30",
            items = mutableListOf(
                FormulaItem("fi_demo1", "D1NP", 26.33),
                FormulaItem("fi_demo2", "碳酸钙", 18.0),
                FormulaItem("fi_demo3", "增塑剂", 12.5),
                FormulaItem("fi_demo4", "炭黑", 3.17),
                FormulaItem("fi_demo5", "填料 A", 40.0),
            ),
        )
    )

    fun state(): AppState = AppState(
        products = products(),
        formulas = formulas(),
    )
}

// ============================================================
//  JSON 编解码（v1）
// ============================================================

object Codec {

    fun encode(s: AppState): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("settings", JSONObject().apply {
            put("ai", JSONObject().apply {
                put("baseUrl", s.settings.ai.baseUrl)
                put("apiKey", s.settings.ai.apiKey)
                put("model", s.settings.ai.model)
                put("thinking", s.settings.ai.thinking)
                put("compress", s.settings.ai.compress)
                put("liveComment", s.settings.ai.liveComment)
                put("systemPrompt", s.settings.ai.systemPrompt)
            })
        })
        o.put("products", JSONArray().apply {
            s.products.forEach { pd ->
                put(JSONObject().apply {
                    put("id", pd.id)
                    put("name", pd.name)
                    put("bakes", JSONArray().apply {
                        pd.bakes.forEach { b -> put(JSONObject().put("temp", b.tempC).put("minutes", b.minutes)) }
                    })
                    if (pd.plateTotal > 0) {
                        put("plateTotal", pd.plateTotal)
                        put("plateNew", pd.plateNew)
                        if (pd.plateSpec.isNotBlank()) put("plateSpec", pd.plateSpec)
                    }
                })
            }
        })
        o.put("formulas", JSONArray().apply {
            s.formulas.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("totalG", f.totalG)
                    put("note", f.note)
                    if (f.purpose.isNotEmpty()) put("purpose", f.purpose)
                    if (f.cure.isNotEmpty()) put("cure", f.cure)
                    put("items", JSONArray().apply {
                        f.items.forEach { it2 ->
                            put(JSONObject().apply {
                                put("id", it2.id)
                                put("name", it2.name)
                                put("percent", it2.percent)
                            })
                        }
                    })
                })
            }
        })
        return o.toString()
    }

    fun decode(raw: String): AppState {
        val s = AppState()
        val o = JSONObject(raw)

        o.optJSONObject("settings")?.optJSONObject("ai")?.let { a ->
            s.settings = Settings(
                AiSettings(
                    baseUrl = a.optString("baseUrl", "").trim(),
                    apiKey = a.optString("apiKey", "").trim(),
                    model = a.optString("model", "").trim(),
                    thinking = a.optString("thinking", ""),
                    compress = a.optBoolean("compress", true),
                    liveComment = a.optBoolean("liveComment", true),
                    systemPrompt = a.optString("systemPrompt", ""),
                )
            )
        }

        o.optJSONArray("products")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val name = j.optString("name", "").trim()
                if (name.isEmpty()) continue
                val total = j.optInt("plateTotal", 0).coerceIn(0, 99)
                val pd = Product(
                    id = j.optString("id", "p_$i"),
                    name = name,
                    plateTotal = total,
                    // 新板数不能超过总数，老数据/手改的备份都可能出现越界
                    plateNew = j.optInt("plateNew", 0).coerceIn(0, total),
                    plateSpec = j.optString("plateSpec", "").trim(),
                )
                j.optJSONArray("bakes")?.let { bs ->
                    for (k in 0 until bs.length()) {
                        val b = bs.optJSONObject(k) ?: continue
                        val t = b.optInt("temp", b.optInt("tempC", 140))
                        val m = b.optInt("minutes", 30)
                        if (t > 0 && m > 0) pd.bakes.add(BakePreset(t, m))
                    }
                }
                s.products.add(pd)
            }
        }
        if (s.products.isEmpty()) s.products = Defaults.products()

        o.optJSONArray("formulas")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val f = Formula(
                    id = j.optString("id", "f_$i"),
                    name = j.optString("name", "未命名配方"),
                    totalG = j.optDouble("totalG", 600.0).takeIf { it > 0 } ?: 600.0,
                    note = j.optString("note", ""),
                    purpose = j.optString("purpose", ""),
                    cure = j.optString("cure", ""),
                )
                j.optJSONArray("items")?.let { its ->
                    for (k in 0 until its.length()) {
                        val it2 = its.optJSONObject(k) ?: continue
                        f.items.add(
                            FormulaItem(
                                id = it2.optString("id", "fi_$k"),
                                name = it2.optString("name", ""),
                                percent = it2.optDouble("percent", 0.0),
                            )
                        )
                    }
                }
                s.formulas.add(f)
            }
        }
        return s
    }
}

// ============================================================
//  小工具
// ============================================================

object Fmt {
    /** 把用户输入的「1,5」「 2.0 」这类字符串读成数字 */
    fun num(v: String): Double? = v.trim().replace(',', '.').toDoubleOrNull()

    fun g(v: Double): String = String.format(Locale.US, "%.1f", v)

    fun pct(v: Double): String = String.format(Locale.US, "%.2f", v)
}

fun AppState.formulaOf(id: String?): Formula? = if (id == null) null else formulas.firstOrNull { it.id == id }

fun AppState.productOf(id: String?): Product? = if (id == null) null else products.firstOrNull { it.id == id }

fun AppState.productByName(name: String?): Product? =
    if (name.isNullOrBlank()) null else products.firstOrNull { it.name == name }
