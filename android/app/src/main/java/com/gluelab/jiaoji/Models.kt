package com.gluelab.jiaoji

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ============================================================
//  数据模型
//  存储格式与网页版共用一份 JSON（v1），两边的备份可以互相导入。
// ============================================================

/** 一条烘烤条件：多少度烤多少分钟 */
data class BakePreset(var tempC: Int = 140, var minutes: Int = 30) {
    fun text(): String = "${tempC}℃ ${minutes}分"
}

/**
 * 一块电泳板。
 *
 * 板子有各自用途：同一批烘烤里，可能一块专门做 2mm 流挂、一块测硬度、
 * 一块测附着力 —— 新旧都可能带要求，所以每块单独记。
 */
data class Plate(
    var id: String = "",
    /** true = 新板 */
    var fresh: Boolean = false,
    /** 这块板子拿来做什么 / 有什么要求，比如「2mm 流挂」；可以空着 */
    var note: String = "",
)

/**
 * 产品 / 客户对应的烘烤条件（车间那张表）。
 * 例：安徽万安 —— 170℃ 20分 / 210℃ 20分；电泳板 4 块，其中 1 块新板做 2mm 流挂。
 */
data class Product(
    var id: String = "",
    var name: String = "",
    var bakes: MutableList<BakePreset> = mutableListOf(),
    var plates: MutableList<Plate> = mutableListOf(),
) {
    val hasPlates: Boolean get() = plates.isNotEmpty()
    val plateNew: Int get() = plates.count { it.fresh }
    val plateOld: Int get() = plates.count { !it.fresh }

    fun plateText(): String {
        if (plates.isEmpty()) return "不用电泳板"
        val parts = ArrayList<String>()
        if (plateNew > 0) parts.add("新 $plateNew")
        if (plateOld > 0) parts.add("旧 $plateOld")
        return "电泳板 ${plates.size} 块（${parts.joinToString(" / ")}）"
    }

    /** 每块板子一行，比如「新 · 2mm 流挂」「旧」 */
    fun plateLines(): List<String> = plates.map { p ->
        val tag = if (p.fresh) "新" else "旧"
        if (p.note.isBlank()) tag else "$tag · ${p.note}"
    }
}

/**
 * 正在跑的烘烤计时。
 *
 * 存的是「结束时刻」而不是「还剩多少秒」：App 切到后台、屏幕熄掉、
 * 甚至进程被杀再打开，时间都还是对的。
 */
data class BakeTimer(
    var productName: String = "",
    var tempC: Int = 0,
    var minutes: Int = 0,
    var endAt: Long = 0L,
    /** 暂停中记剩余毫秒；-1 表示没暂停 */
    var pausedLeft: Long = -1L,
) {
    val paused: Boolean get() = pausedLeft >= 0
    val totalMs: Long get() = minutes * 60_000L

    fun remaining(now: Long): Long =
        if (pausedLeft >= 0) pausedLeft.coerceAtLeast(0) else (endAt - now).coerceAtLeast(0)

    fun label(): String = listOf(productName, if (tempC > 0) "${tempC}℃" else "").filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * 一次比重测试的存档：当时输入的六个称量值 + 当时算出来的三个结果。
 * 结果也存一份，是为了以后万一公式改了，旧记录仍然保持当时的样子。
 */
data class DensityRecord(
    var id: String = "",
    var at: Long = 0L,
    var m1: Double = 0.0,
    var m2: Double = 0.0,
    var m3: Double = 0.0,
    var m4: Double = 0.0,
    var m5: Double = 0.0,
    var m6: Double = 0.0,
    var specific: Double? = null,
    var solid: Double? = null,
    var foam: Double? = null,
    var note: String = "",
) {
    fun density(): Density = Density(m1, m2, m3, m4, m5, m6)
}

class AppState(
    var products: MutableList<Product> = mutableListOf(),
    /** 比重测试历史，最新的在前 */
    var densityLog: MutableList<DensityRecord> = mutableListOf(),
    /** 同一时间只跑一个烘烤计时 */
    var timer: BakeTimer? = null,
)

// ============================================================
//  比重（车间那张 Excel）
// ============================================================

data class Density(
    var m1: Double = 0.0,
    var m2: Double = 0.0,
    var m3: Double = 0.0,
    var m4: Double = 0.0,
    var m5: Double = 0.0,
    var m6: Double = 0.0,
)

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

    fun text(v: Double?, digits: Int = 4): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "—"
        return String.format(Locale.US, "%." + digits + "f", v)
    }
}

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
            plates = mutableListOf(
                Plate("pl_wanan_1", fresh = true, note = "2mm 流挂"),
                Plate("pl_wanan_2", fresh = false, note = ""),
                Plate("pl_wanan_3", fresh = false, note = ""),
                Plate("pl_wanan_4", fresh = false, note = ""),
            ),
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

    fun state(): AppState = AppState(products = products())
}

// ============================================================
//  JSON 编解码（v1）
// ============================================================

object Codec {

    fun encode(s: AppState): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("products", JSONArray().apply {
            s.products.forEach { pd ->
                put(JSONObject().apply {
                    put("id", pd.id)
                    put("name", pd.name)
                    put("bakes", JSONArray().apply {
                        pd.bakes.forEach { b -> put(JSONObject().put("temp", b.tempC).put("minutes", b.minutes)) }
                    })
                    if (pd.plates.isNotEmpty()) {
                        put("plates", JSONArray().apply {
                            pd.plates.forEach { pl ->
                                put(JSONObject().apply {
                                    put("id", pl.id)
                                    put("fresh", pl.fresh)
                                    if (pl.note.isNotBlank()) put("note", pl.note)
                                })
                            }
                        })
                    }
                })
            }
        })
        o.put("densityLog", JSONArray().apply {
            s.densityLog.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("at", r.at)
                    put("m1", r.m1); put("m2", r.m2); put("m3", r.m3)
                    put("m4", r.m4); put("m5", r.m5); put("m6", r.m6)
                    r.specific?.let { put("specific", it) }
                    r.solid?.let { put("solid", it) }
                    r.foam?.let { put("foam", it) }
                    if (r.note.isNotBlank()) put("note", r.note)
                })
            }
        })
        s.timer?.let { t ->
            o.put("timer", JSONObject().apply {
                put("productName", t.productName)
                put("tempC", t.tempC)
                put("minutes", t.minutes)
                put("endAt", t.endAt)
                put("pausedLeft", t.pausedLeft)
            })
        }
        return o.toString()
    }

    fun decode(raw: String): AppState {
        val s = AppState()
        val o = JSONObject(raw)

        o.optJSONArray("products")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val name = j.optString("name", "").trim()
                if (name.isEmpty()) continue
                val pd = Product(id = j.optString("id", "p_$i"), name = name)

                j.optJSONArray("bakes")?.let { bs ->
                    for (k in 0 until bs.length()) {
                        val b = bs.optJSONObject(k) ?: continue
                        val t = b.optInt("temp", b.optInt("tempC", 140))
                        val m = b.optInt("minutes", 30)
                        if (t > 0 && m > 0) pd.bakes.add(BakePreset(t, m))
                    }
                }

                j.optJSONArray("plates")?.let { ps ->
                    for (k in 0 until ps.length()) {
                        val po = ps.optJSONObject(k) ?: continue
                        pd.plates.add(
                            Plate(
                                id = po.optString("id", "pl_${i}_$k"),
                                fresh = po.optBoolean("fresh", false),
                                note = po.optString("note", "").trim(),
                            )
                        )
                    }
                }
                // 3.0.4 的老格式：总数 / 新板数 / 新板要求 —— 自动折成板子列表
                if (pd.plates.isEmpty()) {
                    val total = j.optInt("plateTotal", 0).coerceIn(0, 99)
                    val fresh = j.optInt("plateNew", 0).coerceIn(0, total)
                    val spec = j.optString("plateSpec", "").trim()
                    repeat(fresh) { pd.plates.add(Plate("pl_${i}_n$it", fresh = true, note = spec)) }
                    repeat(total - fresh) { pd.plates.add(Plate("pl_${i}_o$it", fresh = false)) }
                }
                s.products.add(pd)
            }
        }
        if (s.products.isEmpty()) s.products = Defaults.products()

        o.optJSONArray("densityLog")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                s.densityLog.add(
                    DensityRecord(
                        id = j.optString("id", "d_$i"),
                        at = j.optLong("at", 0L),
                        m1 = j.optDouble("m1", 0.0), m2 = j.optDouble("m2", 0.0),
                        m3 = j.optDouble("m3", 0.0), m4 = j.optDouble("m4", 0.0),
                        m5 = j.optDouble("m5", 0.0), m6 = j.optDouble("m6", 0.0),
                        specific = if (j.has("specific") && !j.isNull("specific")) j.optDouble("specific") else null,
                        solid = if (j.has("solid") && !j.isNull("solid")) j.optDouble("solid") else null,
                        foam = if (j.has("foam") && !j.isNull("foam")) j.optDouble("foam") else null,
                        note = j.optString("note", ""),
                    )
                )
            }
        }

        o.optJSONObject("timer")?.let { j ->
            val minutes = j.optInt("minutes", 0)
            if (minutes > 0) {
                s.timer = BakeTimer(
                    productName = j.optString("productName", ""),
                    tempC = j.optInt("tempC", 0),
                    minutes = minutes,
                    endAt = j.optLong("endAt", 0L),
                    pausedLeft = j.optLong("pausedLeft", -1L),
                )
            }
        }
        return s
    }
}
