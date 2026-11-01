package com.wulalala66.jiaoji

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------- 数据模型（与网页版 jiaoji.v1 JSON 同构，可互相导入导出） ----------------

data class Settings(
    var preAlertMinutes: Int = 5,
    var sound: Boolean = true,
    var vibrate: Boolean = true,
    var keepAwake: Boolean = true,
    var notifyAsked: Boolean = false,
)

data class BakePreset(var tempC: Int = 140, var minutes: Int = 30)

data class TestDef(
    var id: String = "",
    var name: String = "",
    var unit: String = "",
    var min: String = "",
    var max: String = "",
)

data class ProductType(
    var id: String = "",
    var name: String = "",
    var bakes: MutableList<BakePreset> = mutableListOf(),
    var tests: MutableList<TestDef> = mutableListOf(),
)

data class SamplingTask(
    var id: String = "",
    var customer: String = "",
    var typeId: String = "",
    var dueAt: Long = 0L,
    var note: String = "",
    var preAlertMinutes: Int? = null,
    var status: String = "pending", // pending | done
    var sampleId: String? = null,
    var dueAlarmed: Boolean = false,
)

data class SampleTest(
    var id: String = "",
    var name: String = "",
    var unit: String = "",
    var min: String = "",
    var max: String = "",
    var value: String = "",
    var note: String = "",
    var skipped: Boolean = false,
)

data class Sample(
    var id: String = "",
    var customer: String = "",
    var typeId: String = "",
    var batchNo: String = "",
    var sampledAt: Long = 0L,
    var note: String = "",
    var status: String = "active", // active | done
    var taskId: String? = null,
    var tests: MutableList<SampleTest> = mutableListOf(),
)

data class Bake(
    var id: String = "",
    var oven: String = "door", // door | window
    var sampleId: String? = null,
    var label: String = "",
    var tempC: Int = 140,
    var startAt: Long = 0L,
    var minutes: Int = 30,
    var endAt: Long = 0L,
    var status: String = "running", // running | due | done
    var doneAt: Long? = null,
    var sound: Boolean = true,
    var preAlert: Int? = null,
)

class AppState(
    var settings: Settings = Settings(),
    var types: MutableList<ProductType> = mutableListOf(),
    var customers: MutableList<String> = mutableListOf(),
    var sampling: MutableList<SamplingTask> = mutableListOf(),
    var samples: MutableList<Sample> = mutableListOf(),
    var bakes: MutableList<Bake> = mutableListOf(),
    var seq: MutableMap<String, Int> = mutableMapOf(),
)

// ---------------- 默认数据 ----------------

object Defaults {
    private fun t(id: String, name: String, unit: String) = TestDef(id = id, name = name, unit = unit)

    fun state(): AppState {
        val base = ProductType(
            id = "base", name = "底涂胶",
            bakes = mutableListOf(BakePreset(140, 20), BakePreset(140, 30)),
            tests = mutableListOf(
                t("press_flow", "压流", "mm"),
                t("rot_vis", "旋转粘度", "mPa·s"),
                t("bingham", "宾汉粘度", "Pa·s"),
                t("shear", "剪切强度", "MPa"),
                t("hardness", "硬度", "Shore A"),
            ),
        )
        val weld = ProductType(
            id = "weld", name = "焊缝胶",
            bakes = mutableListOf(BakePreset(140, 30), BakePreset(160, 20)),
            tests = mutableListOf(
                t("press_flow", "压流", "mm"),
                t("rot_vis", "旋转粘度", "mPa·s"),
                t("shear", "剪切强度", "MPa"),
                t("hardness", "硬度", "Shore A"),
                t("solid", "固含量", "%"),
            ),
        )
        val other = ProductType(id = "other", name = "其他", bakes = mutableListOf(), tests = mutableListOf())
        return AppState(
            types = mutableListOf(base, weld, other),
            customers = mutableListOf("安徽万安"),
        )
    }
}

// ---------------- 工具 ----------------

object Fmt {
    private val hmFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val mdFmt = SimpleDateFormat("MM-dd", Locale.US)
    private val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fullFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun hm(ts: Long): String = hmFmt.format(Date(ts))
    fun md(ts: Long): String = mdFmt.format(Date(ts))
    fun key(ts: Long): String = keyFmt.format(Date(ts))
    fun full(ts: Long): String = fullFmt.format(Date(ts))
    fun stamp(ts: Long): String = stamp.format(Date(ts))

    fun hms(ms: Long): String {
        val neg = ms < 0
        var s = Math.abs(ms) / 1000
        val h = s / 3600; s -= h * 3600
        val m = s / 60; s -= m * 60
        val body = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
        return if (neg) "-$body" else body
    }

    fun dur(minutes: Int): String {
        if (minutes < 60) return "${minutes}分"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}小时" else "${h}小时${m}分"
    }

    fun ago(ts: Long, now: Long): String {
        val d = now - ts
        if (d < 60_000) return "刚刚"
        if (d < 3_600_000) return "${d / 60_000} 分钟前"
        if (d < 86_400_000) return "${d / 3_600_000} 小时前"
        return "${d / 86_400_000} 天前"
    }

    fun num(v: String): Double? = v.trim().replace(",", ".").toDoubleOrNull()
}

fun startOfDay(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun todayStart(now: Long = System.currentTimeMillis()): Long = startOfDay(now)

fun weekdayCn(ts: Long): String {
    val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    val c = Calendar.getInstance(); c.timeInMillis = ts
    return names[c.get(Calendar.DAY_OF_WEEK) - 1]
}

fun ovenName(oven: String): String = if (oven == "window") "靠窗烘箱" else "靠门烘箱"

// ---------------- JSON 编解码 ----------------

object Codec {

    fun encode(s: AppState): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("settings", JSONObject().apply {
            put("preAlertMinutes", s.settings.preAlertMinutes)
            put("sound", s.settings.sound)
            put("vibrate", s.settings.vibrate)
            put("keepAwake", s.settings.keepAwake)
            put("notifyAsked", s.settings.notifyAsked)
        })
        o.put("types", JSONArray().apply {
            s.types.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("name", t.name)
                    put("bakes", JSONArray().apply {
                        t.bakes.forEach { b -> put(JSONObject().put("temp", b.tempC).put("minutes", b.minutes)) }
                    })
                    put("tests", JSONArray().apply {
                        t.tests.forEach { d ->
                            put(JSONObject().apply {
                                put("id", d.id); put("name", d.name); put("unit", d.unit)
                                put("min", d.min); put("max", d.max)
                            })
                        }
                    })
                })
            }
        })
        o.put("customers", JSONArray().apply { s.customers.forEach { put(it) } })
        o.put("sampling", JSONArray().apply {
            s.sampling.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("customer", t.customer); put("typeId", t.typeId)
                    put("dueAt", t.dueAt); put("note", t.note)
                    if (t.preAlertMinutes != null) put("preAlertMinutes", t.preAlertMinutes) else put("preAlertMinutes", JSONObject.NULL)
                    put("status", t.status)
                    if (t.sampleId != null) put("sampleId", t.sampleId) else put("sampleId", JSONObject.NULL)
                    put("dueAlarmed", t.dueAlarmed)
                })
            }
        })
        o.put("samples", JSONArray().apply {
            s.samples.forEach { sm ->
                put(JSONObject().apply {
                    put("id", sm.id); put("customer", sm.customer); put("typeId", sm.typeId)
                    put("batchNo", sm.batchNo); put("sampledAt", sm.sampledAt); put("note", sm.note)
                    put("status", sm.status)
                    if (sm.taskId != null) put("taskId", sm.taskId) else put("taskId", JSONObject.NULL)
                    put("tests", JSONArray().apply {
                        sm.tests.forEach { d ->
                            put(JSONObject().apply {
                                put("id", d.id); put("name", d.name); put("unit", d.unit)
                                put("min", d.min); put("max", d.max)
                                put("value", d.value); put("note", d.note); put("skipped", d.skipped)
                            })
                        }
                    })
                })
            }
        })
        o.put("bakes", JSONArray().apply {
            s.bakes.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id); put("oven", b.oven)
                    if (b.sampleId != null) put("sampleId", b.sampleId) else put("sampleId", JSONObject.NULL)
                    put("label", b.label); put("tempC", b.tempC)
                    put("startAt", b.startAt); put("minutes", b.minutes); put("endAt", b.endAt)
                    put("status", b.status)
                    if (b.doneAt != null) put("doneAt", b.doneAt) else put("doneAt", JSONObject.NULL)
                    put("sound", b.sound)
                    if (b.preAlert != null) put("preAlert", b.preAlert) else put("preAlert", JSONObject.NULL)
                })
            }
        })
        o.put("seq", JSONObject().apply { s.seq.forEach { (k, v) -> put(k, v) } })
        return o.toString()
    }

    fun decode(raw: String): AppState {
        val s = AppState()
        val o = JSONObject(raw)
        o.optJSONObject("settings")?.let { j ->
            s.settings = Settings(
                preAlertMinutes = j.optInt("preAlertMinutes", 5).coerceIn(0, 120),
                sound = j.optBoolean("sound", true),
                vibrate = j.optBoolean("vibrate", true),
                keepAwake = j.optBoolean("keepAwake", true),
                notifyAsked = j.optBoolean("notifyAsked", false),
            )
        }
        val types = o.optJSONArray("types") ?: JSONArray()
        for (i in 0 until types.length()) {
            val t = types.optJSONObject(i) ?: continue
            val pt = ProductType(
                id = t.optString("id", "type_$i"),
                name = t.optString("name", "未命名"),
            )
            t.optJSONArray("bakes")?.let { bs ->
                for (k in 0 until bs.length()) {
                    val b = bs.optJSONObject(k) ?: continue
                    pt.bakes.add(BakePreset(b.optInt("temp", b.optInt("tempC", 140)), b.optInt("minutes", 30)))
                }
            }
            t.optJSONArray("tests")?.let { ts ->
                for (k in 0 until ts.length()) {
                    val d = ts.optJSONObject(k) ?: continue
                    pt.tests.add(
                        TestDef(
                            id = d.optString("id", "test_$k"),
                            name = d.optString("name", ""),
                            unit = d.optString("unit", ""),
                            min = d.optString("min", ""),
                            max = d.optString("max", ""),
                        )
                    )
                }
            }
            s.types.add(pt)
        }
        if (s.types.isEmpty()) s.types = Defaults.state().types

        o.optJSONArray("customers")?.let { cs ->
            for (i in 0 until cs.length()) {
                val c = cs.optString(i, "").trim()
                if (c.isNotEmpty()) s.customers.add(c)
            }
        }

        o.optJSONArray("sampling")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                s.sampling.add(
                    SamplingTask(
                        id = j.optString("id", "s_$i"),
                        customer = j.optString("customer", ""),
                        typeId = j.optString("typeId", "other"),
                        dueAt = j.optLong("dueAt", 0L),
                        note = j.optString("note", ""),
                        preAlertMinutes = if (j.has("preAlertMinutes") && !j.isNull("preAlertMinutes")) j.optInt("preAlertMinutes") else null,
                        status = j.optString("status", "pending"),
                        sampleId = if (j.has("sampleId") && !j.isNull("sampleId")) j.optString("sampleId") else null,
                        dueAlarmed = j.optBoolean("dueAlarmed", false),
                    )
                )
            }
        }

        o.optJSONArray("samples")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val sm = Sample(
                    id = j.optString("id", "sm_$i"),
                    customer = j.optString("customer", ""),
                    typeId = j.optString("typeId", "other"),
                    batchNo = j.optString("batchNo", ""),
                    sampledAt = j.optLong("sampledAt", 0L),
                    note = j.optString("note", ""),
                    status = j.optString("status", "active"),
                    taskId = if (j.has("taskId") && !j.isNull("taskId")) j.optString("taskId") else null,
                )
                j.optJSONArray("tests")?.let { ts ->
                    for (k in 0 until ts.length()) {
                        val d = ts.optJSONObject(k) ?: continue
                        sm.tests.add(
                            SampleTest(
                                id = d.optString("id", "t_$k"),
                                name = d.optString("name", ""),
                                unit = d.optString("unit", ""),
                                min = d.optString("min", ""),
                                max = d.optString("max", ""),
                                value = d.optString("value", ""),
                                note = d.optString("note", ""),
                                skipped = d.optBoolean("skipped", false),
                            )
                        )
                    }
                }
                s.samples.add(sm)
            }
        }

        o.optJSONArray("bakes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                s.bakes.add(
                    Bake(
                        id = j.optString("id", "b_$i"),
                        oven = if (j.optString("oven", "door") == "window") "window" else "door",
                        sampleId = if (j.has("sampleId") && !j.isNull("sampleId")) j.optString("sampleId") else null,
                        label = j.optString("label", ""),
                        tempC = j.optInt("tempC", 140),
                        startAt = j.optLong("startAt", 0L),
                        minutes = j.optInt("minutes", 30),
                        endAt = j.optLong("endAt", 0L),
                        status = j.optString("status", "running"),
                        doneAt = if (j.has("doneAt") && !j.isNull("doneAt")) j.optLong("doneAt") else null,
                        sound = j.optBoolean("sound", true),
                        preAlert = if (j.has("preAlert") && !j.isNull("preAlert")) j.optInt("preAlert") else null,
                    )
                )
            }
        }

        o.optJSONObject("seq")?.let { j ->
            val keys = j.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                s.seq[k] = j.optInt(k, 0)
            }
        }
        return s
    }
}

// ---------------- 选择器 ----------------

/** 相对今天差几天（0=今天，1=明天…） */
fun dayOffsetOf(ts: Long, now: Long = System.currentTimeMillis()): Int =
    ((todayStart(ts) - todayStart(now)) / 86_400_000L).toInt()

fun hourOf(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
fun minuteOf(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.MINUTE)

fun AppState.typeOf(id: String?): ProductType? = types.firstOrNull { it.id == id }
fun AppState.typeName(id: String?): String = typeOf(id)?.name ?: "其他"
fun AppState.sampleOf(id: String?): Sample? = if (id == null) null else samples.firstOrNull { it.id == id }
fun AppState.taskOf(id: String?): SamplingTask? = if (id == null) null else sampling.firstOrNull { it.id == id }

fun AppState.pendingTasks(): List<SamplingTask> =
    sampling.filter { it.status == "pending" }.sortedBy { it.dueAt }

fun AppState.pendingToday(now: Long = System.currentTimeMillis()): List<SamplingTask> {
    val tomorrow = todayStart(now) + 86_400_000L
    // 包含以前没处理的（逾期的）任务：漏了一次取样，它不能从今天的列表里消失
    return sampling.filter { it.status == "pending" && it.dueAt < tomorrow }.sortedBy { it.dueAt }
}

fun AppState.overdueTasks(now: Long = System.currentTimeMillis()): List<SamplingTask> =
    sampling.filter { it.status == "pending" && it.dueAt <= now }.sortedBy { it.dueAt }

fun AppState.activeBakes(): List<Bake> =
    bakes.filter { it.status == "running" || it.status == "due" }.sortedBy { it.endAt }

fun AppState.runningBakes(oven: String? = null): List<Bake> =
    bakes.filter { (oven == null || it.oven == oven) && (it.status == "running" || it.status == "due") }
        .sortedBy { it.endAt }

fun AppState.dueBakes(): List<Bake> = bakes.filter { it.status == "due" }.sortedBy { it.endAt }

fun AppState.recentBakes(since: Long): List<Bake> =
    bakes.filter { it.status == "done" && (it.doneAt ?: 0L) >= since }.sortedByDescending { it.doneAt ?: 0L }

fun AppState.activeSamples(): List<Sample> =
    samples.filter { it.status == "active" }.sortedByDescending { it.sampledAt }

fun AppState.doneSamples(): List<Sample> =
    samples.filter { it.status == "done" }.sortedByDescending { it.sampledAt }

fun AppState.todaySamples(now: Long = System.currentTimeMillis()): List<Sample> {
    val today = todayStart(now)
    return samples.filter { it.sampledAt >= today }.sortedByDescending { it.sampledAt }
}

fun AppState.ovenBusy(oven: String): Boolean = runningBakes(oven).isNotEmpty()

/** 同客户同类型上一批这个测试项的数值，用来做「上批多少」的对比 */
fun AppState.lastValueFor(sample: Sample, testName: String): String? =
    samples.asSequence()
        .filter { it.id != sample.id && it.customer == sample.customer && it.typeId == sample.typeId }
        .sortedByDescending { it.sampledAt }
        .mapNotNull { sm ->
            sm.tests.firstOrNull { it.name == testName && !it.skipped && it.value.isNotBlank() }?.value
        }
        .firstOrNull()

// ---------------- 判定 ----------------

enum class Verdict { PASS, FAIL, EMPTY, SKIP }

fun verdictOf(t: SampleTest): Verdict {
    if (t.skipped) return Verdict.SKIP
    val v = Fmt.num(t.value) ?: return Verdict.EMPTY
    val min = Fmt.num(t.min)
    val max = Fmt.num(t.max)
    if (min != null && v < min) return Verdict.FAIL
    if (max != null && v > max) return Verdict.FAIL
    return Verdict.PASS
}

data class SampleStats(val total: Int, val done: Int, val bad: Int) {
    val pct: Int get() = if (total == 0) 0 else (done * 100 / total)
}

fun statsOf(s: Sample): SampleStats {
    val total = s.tests.size
    val done = s.tests.count { it.skipped || Fmt.num(it.value) != null }
    val bad = s.tests.count { verdictOf(it) == Verdict.FAIL }
    return SampleStats(total, done, bad)
}
