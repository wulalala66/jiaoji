package com.wulalala66.jiaoji

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

/**
 * 全局状态 + 全部写操作。状态整体不可变地替换（深拷贝），UI 用 StateFlow 订阅。
 * 存储：SharedPreferences 里一坨 JSON，与网页版 `jiaoji.v1` 完全同构。
 */
class Repo private constructor() {

    companion object {
        val I = Repo()
        const val PREFS = "jiaoji"
        const val KEY = "jiaoji.v1"

        fun read(ctx: Context): AppState {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return try {
                if (raw.isNullOrBlank()) Defaults.state() else Codec.decode(raw)
            } catch (e: Exception) {
                Defaults.state()
            }
        }

        fun write(ctx: Context, s: AppState) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, Codec.encode(s)).apply()
        }

        /** 后台（通知按钮 / 闹钟）直接改库：App 进程没起来也能生效 */
        fun editStatic(ctx: Context, block: (AppState) -> Unit) {
            val s = read(ctx)
            block(s)
            write(ctx, s)
            AlarmScheduler.sync(ctx, s)
            I.reload()
        }

        fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(10)
    }

    private var ctx: Context? = null
    private var loaded = false
    private val _state = MutableStateFlow(Defaults.state())
    val state: StateFlow<AppState> get() = _state

    fun init(context: Context) {
        ctx = context.applicationContext
        if (loaded) return
        loaded = true
        _state.value = read(context)
        write(context, _state.value)
        AlarmScheduler.sync(context, _state.value)
    }

    fun reload() {
        val c = ctx ?: return
        _state.value = read(c)
    }

    private fun alarmSig(s: AppState): String {
        val sb = StringBuilder().append(s.settings.preAlertMinutes).append(';')
        s.bakes.forEach { b ->
            if (b.status == "running" || b.status == "due") sb.append(b.id).append(b.endAt).append(b.preAlert).append('|')
        }
        s.sampling.forEach { t ->
            if (t.status == "pending") sb.append(t.id).append(t.dueAt).append(t.preAlertMinutes).append('|')
        }
        return sb.toString()
    }

    private fun mutate(block: (AppState) -> Unit) {
        val c = ctx ?: return
        val old = _state.value
        val before = alarmSig(old)
        val next = Codec.decode(Codec.encode(old))
        block(next)
        _state.value = next
        write(c, next)
        if (alarmSig(next) != before) AlarmScheduler.sync(c, next)
    }

    // ---------------- 取样任务 ----------------

    fun addSampling(
        customer: String,
        typeId: String,
        dueAt: Long,
        note: String = "",
        preAlertMinutes: Int? = null,
    ): String {
        val id = "task_" + uid()
        mutate { s ->
            s.sampling.add(
                SamplingTask(
                    id = id, customer = customer, typeId = typeId,
                    dueAt = dueAt, note = note, preAlertMinutes = preAlertMinutes,
                )
            )
        }
        return id
    }

    fun updateSampling(id: String, block: (SamplingTask) -> Unit) {
        mutate { s -> s.sampling.firstOrNull { it.id == id }?.let(block) }
    }

    fun deleteSampling(id: String) {
        mutate { s -> s.sampling.removeAll { it.id == id } }
    }

    fun ackSampling(id: String) {
        mutate { s -> s.sampling.firstOrNull { it.id == id }?.apply { dueAlarmed = true } }
    }

    /** 已取样：建档（批次号自动递增），可选同时开一个烘烤计时 */
    fun confirmSampled(
        taskId: String,
        withBake: Boolean = false,
        oven: String = "door",
        tempC: Int = 140,
        minutes: Int = 30,
    ): String? {
        var newId: String? = null
        mutate { s ->
            val task = s.sampling.firstOrNull { it.id == taskId } ?: return@mutate
            val existing = task.sampleId
            if (existing != null && s.samples.any { it.id == existing }) {
                newId = existing
                task.status = "done"
                return@mutate
            }
            val sample = makeSample(s, task.customer, task.typeId, task.id)
            s.samples.add(sample)
            task.status = "done"
            task.sampleId = sample.id
            task.dueAlarmed = true
            // 烘箱已经被别的样品占着就不硬塞，样品照样建档
            if (withBake && s.bakeInOven(oven) == null) {
                s.bakes.add(
                    makeBake(oven, sample.id, sampleTitle(s, sample), tempC, minutes, System.currentTimeMillis())
                )
            }
            newId = sample.id
        }
        return newId
    }

    fun createStandaloneSample(customer: String, typeId: String): String {
        var id = ""
        mutate { s ->
            val sample = makeSample(s, customer, typeId, null)
            s.samples.add(sample)
            id = sample.id
        }
        return id
    }

    /** 预览批次号（不写入） */
    fun previewBatchNo(typeId: String): String {
        val s = _state.value
        val key = Fmt.key(System.currentTimeMillis()).replace("-", "").takeLast(4)
        val n = (s.seq[key] ?: 0) + 1
        return "$key-" + String.format(Locale.US, "%02d", n)
    }

    private fun nextBatchNo(s: AppState, now: Long): String {
        val key = Fmt.key(now).replace("-", "").takeLast(4)
        val n = (s.seq[key] ?: 0) + 1
        s.seq[key] = n
        return "$key-" + String.format(Locale.US, "%02d", n)
    }

    private fun makeSample(s: AppState, customer: String, typeId: String, taskId: String?): Sample {
        val now = System.currentTimeMillis()
        val type = s.typeOf(typeId)
        val tests = (type?.tests ?: emptyList()).map {
            SampleTest(
                id = it.id, name = it.name, unit = it.unit, min = it.min, max = it.max,
                kind = it.kind,
                dens = if (it.kind == "density") Density() else null,
            )
        }.toMutableList()
        return Sample(
            id = "sm_" + uid(),
            customer = customer,
            typeId = typeId,
            batchNo = nextBatchNo(s, now),
            sampledAt = now,
            status = "active",
            taskId = taskId,
            tests = tests,
        )
    }

    private fun sampleTitle(s: AppState, sample: Sample): String =
        "${sample.customer} · ${s.typeName(sample.typeId)}"

    // ---------------- 样品 / 测试 ----------------

    fun setTestValue(sampleId: String, testId: String, value: String) {
        mutate { s ->
            s.sampleOf(sampleId)?.tests?.firstOrNull { it.id == testId }?.let {
                it.value = value
                if (value.isNotBlank()) it.skipped = false
            }
        }
    }

    /** 比重：存下六个称量值，并把算出来的比重写进 value（记录/日报/CSV 都用 value） */
    fun setTestDensity(sampleId: String, testId: String, dens: Density) {
        mutate { s ->
            s.sampleOf(sampleId)?.tests?.firstOrNull { it.id == testId }?.let {
                it.dens = dens
                it.value = DensityCalc.specificText(dens)
                if (it.value.isNotBlank()) it.skipped = false
            }
        }
    }

    fun setTestNote(sampleId: String, testId: String, note: String) {
        mutate { s ->
            s.sampleOf(sampleId)?.tests?.firstOrNull { it.id == testId }?.note = note
        }
    }

    fun toggleTestSkip(sampleId: String, testId: String) {
        mutate { s ->
            s.sampleOf(sampleId)?.tests?.firstOrNull { it.id == testId }?.let {
                it.skipped = !it.skipped
                if (it.skipped) it.value = ""
            }
        }
    }

    fun addTest(
        sampleId: String,
        name: String,
        unit: String,
        min: String = "",
        max: String = "",
        kind: String = "plain",
    ) {
        if (name.isBlank()) return
        mutate { s ->
            s.sampleOf(sampleId)?.tests?.add(
                SampleTest(
                    id = "t_" + uid(), name = name.trim(), unit = unit.trim(), min = min, max = max,
                    kind = kind,
                    dens = if (kind == "density") Density() else null,
                )
            )
        }
    }

    fun removeTest(sampleId: String, testId: String) {
        mutate { s -> s.sampleOf(sampleId)?.tests?.removeAll { it.id == testId } }
    }

    fun setSampleNote(sampleId: String, note: String) {
        mutate { s -> s.sampleOf(sampleId)?.note = note }
    }

    fun completeSample(sampleId: String) {
        mutate { s ->
            s.sampleOf(sampleId)?.apply { status = "done" }
        }
    }

    fun reopenSample(sampleId: String) {
        mutate { s -> s.sampleOf(sampleId)?.apply { status = "active" } }
    }

    fun deleteSample(sampleId: String) {
        mutate { s ->
            s.samples.removeAll { it.id == sampleId }
            s.bakes.removeAll { it.sampleId == sampleId }
            s.sampling.forEach { if (it.sampleId == sampleId) it.sampleId = null }
        }
    }

    // ---------------- 烘烤 ----------------

    fun makeBake(
        oven: String,
        sampleId: String?,
        label: String,
        tempC: Int,
        minutes: Int,
        startAt: Long,
    ): Bake {
        val end = startAt + minutes * 60_000L
        return Bake(
            id = "bake_" + uid(),
            oven = oven,
            sampleId = sampleId,
            label = label,
            tempC = tempC,
            startAt = startAt,
            minutes = minutes,
            endAt = end,
            status = if (end <= System.currentTimeMillis()) "due" else "running",
            sound = true,
        )
    }

    /**
     * 放一个样品进烘箱。一个烘箱同时只能放一个（物理上就是这样），
     * 已经被占用时返回 null，调用方负责提示。
     */
    fun addBake(oven: String, sampleId: String?, label: String, tempC: Int, minutes: Int, startAt: Long): String? {
        if (I.state.value.bakeInOven(oven) != null) return null
        val b = makeBake(oven, sampleId, label, tempC, minutes, startAt)
        mutate { s -> s.bakes.add(b) }
        return b.id
    }

    fun markBakeDone(id: String) {
        mutate { s ->
            s.bakes.firstOrNull { it.id == id }?.apply {
                status = "done"
                doneAt = System.currentTimeMillis()
            }
        }
    }

    fun snoozeBake(id: String, minutes: Int = 2) {
        mutate { s ->
            s.bakes.firstOrNull { it.id == id }?.apply {
                endAt += minutes * 60_000L
                this.minutes += minutes
                status = "running"
            }
        }
    }

    fun extendBake(id: String, minutes: Int = 5) {
        mutate { s ->
            s.bakes.firstOrNull { it.id == id }?.apply {
                endAt += minutes * 60_000L
                this.minutes += minutes
                if (status == "due" && endAt > System.currentTimeMillis()) status = "running"
            }
        }
    }

    fun removeBake(id: String) {
        mutate { s -> s.bakes.removeAll { it.id == id } }
    }

    fun updateBake(
        id: String,
        oven: String = "door",
        label: String,
        tempC: Int,
        minutes: Int,
        startAt: Long,
        sampleId: String?,
    ) {
        mutate { s ->
            s.bakes.firstOrNull { it.id == id }?.apply {
                // 目标烘箱已经被别的计时占着就保持原烘箱
                val busyByOther = s.bakes.any {
                    it.id != id && it.oven == oven && (it.status == "running" || it.status == "due")
                }
                if (!busyByOther) this.oven = oven
                this.label = label
                this.tempC = tempC
                this.minutes = minutes
                this.startAt = startAt
                this.endAt = startAt + minutes * 60_000L
                this.sampleId = sampleId
                this.status = if (endAt <= System.currentTimeMillis()) "due" else "running"
                this.doneAt = null
            }
        }
    }

    /** 每秒调用：到点的烘烤标记为 due（响铃由系统闹钟负责，这里只同步 UI 状态） */
    fun flipDueBakes() {
        val now = System.currentTimeMillis()
        if (_state.value.bakes.none { it.status == "running" && it.endAt <= now }) return
        mutate { s ->
            s.bakes.forEach { if (it.status == "running" && it.endAt <= now) it.status = "due" }
        }
    }

    // ---------------- 设置 ----------------

    /**
     * 改设置。注意 block 必须「返回新的 Settings」（data class copy）——
     * 之前写成 (Settings) -> Unit 会让 `it.copy(...)` 的返回值被丢掉，
     * 表现就是设置页开关怎么点都没反应。
     */
    fun updateSettings(block: (Settings) -> Settings) {
        mutate { s -> s.settings = block(s.settings) }
    }

    fun addOven(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        var newId: String? = null
        mutate { s ->
            if (s.ovens.any { it.name == n }) return@mutate
            val id = "oven_" + uid()
            s.ovens.add(Oven(id = id, name = n, colorIdx = s.ovens.size))
            newId = id
        }
        return newId
    }

    fun renameOven(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        mutate { s -> s.ovens.firstOrNull { it.id == id }?.name = n }
    }

    /** 只有空着、且不是最后一个烘箱才能删 */
    fun removeOven(id: String): Boolean {
        if (I.state.value.ovens.size <= 1) return false
        if (I.state.value.bakeInOven(id) != null) return false
        mutate { s ->
            s.ovens.removeAll { it.id == id }
            // 这台烘箱的历史计时一起清掉，否则会留下找不到烘箱的记录
            s.bakes.removeAll { it.oven == id }
        }
        return true
    }

    // ---------------- 产品条件表 ----------------

    fun addProduct(name: String, bakes: List<BakePreset> = emptyList()): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        var id: String? = null
        mutate { s ->
            if (s.products.any { it.name == n }) return@mutate
            val pid = "p_" + uid()
            s.products.add(Product(id = pid, name = n, bakes = bakes.toMutableList()))
            id = pid
        }
        return id
    }

    fun updateProduct(id: String, name: String, bakes: List<BakePreset>) {
        val n = name.trim()
        if (n.isEmpty()) return
        mutate { s ->
            s.products.firstOrNull { it.id == id }?.apply {
                this.name = n
                this.bakes = bakes.toMutableList()
            }
        }
    }

    fun removeProduct(id: String) {
        mutate { s -> s.products.removeAll { it.id == id } }
    }

    // ---------------- 配料 / 配方 ----------------

    fun addFormula(name: String = "", totalG: Double = 600.0): String {
        var id = ""
        mutate { s ->
            val f = Formula(
                id = "f_" + uid(),
                name = name.ifBlank { "配方 " + (s.formulas.size + 1) },
                totalG = if (totalG > 0) totalG else 600.0,
            )
            s.formulas.add(f)
            id = f.id
        }
        return id
    }

    fun updateFormula(id: String, block: (Formula) -> Unit) {
        mutate { s -> s.formulas.firstOrNull { it.id == id }?.let(block) }
    }

    fun setFormulaTotal(id: String, totalG: Double) {
        updateFormula(id) { it.totalG = if (totalG > 0) totalG else it.totalG }
    }

    fun addFormulaItem(id: String, name: String = "", percent: Double = 0.0): String {
        var itemId = ""
        updateFormula(id) { f ->
            val it2 = FormulaItem(id = "fi_" + uid(), name = name, percent = percent)
            f.items.add(it2)
            itemId = it2.id
        }
        return itemId
    }

    fun updateFormulaItem(id: String, itemId: String, name: String? = null, percent: Double? = null) {
        updateFormula(id) { f ->
            f.items.firstOrNull { it.id == itemId }?.let { it2 ->
                if (name != null) it2.name = name
                if (percent != null) it2.percent = percent
            }
        }
    }

    fun removeFormulaItem(id: String, itemId: String) {
        updateFormula(id) { f -> f.items.removeAll { it.id == itemId } }
    }

    /** 把剩下的量补给某一行（凑满 100%） */
    fun fillFormulaRest(id: String, itemId: String) {
        updateFormula(id) { f ->
            val others = f.items.filter { it.id != itemId }.sumOf { it.percent }
            f.items.firstOrNull { it.id == itemId }?.percent =
                (100.0 - others).coerceAtLeast(0.0)
        }
    }

    fun removeFormula(id: String) {
        mutate { s -> s.formulas.removeAll { it.id == id } }
    }

    fun duplicateFormula(id: String): String {
        var newId = ""
        mutate { s ->
            val src = s.formulas.firstOrNull { it.id == id } ?: return@mutate
            val copy = Formula(
                id = "f_" + uid(),
                name = src.name + " 副本",
                totalG = src.totalG,
                note = src.note,
                items = src.items.map { FormulaItem(id = "fi_" + uid(), name = it.name, percent = it.percent) }
                    .toMutableList(),
            )
            s.formulas.add(copy)
            newId = copy.id
        }
        return newId
    }

    fun addCustomer(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        mutate { s -> if (!s.customers.contains(n)) s.customers.add(n) }
    }

    fun renameCustomer(old: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) return
        mutate { s ->
            val i = s.customers.indexOf(old)
            if (i >= 0) s.customers[i] = n else s.customers.add(n)
            s.sampling.forEach { if (it.customer == old) it.customer = n }
            s.samples.forEach { if (it.customer == old) it.customer = n }
        }
    }

    fun removeCustomer(name: String) {
        mutate { s -> s.customers.removeAll { it == name } }
    }

    fun upsertType(type: ProductType) {
        mutate { s ->
            val i = s.types.indexOfFirst { it.id == type.id }
            if (i >= 0) s.types[i] = type else s.types.add(type)
        }
    }

    fun deleteType(id: String) {
        if (id == "other") return
        mutate { s -> s.types.removeAll { it.id == id } }
    }

    fun exportJson(): String = Codec.encode(_state.value)

    fun importJson(raw: String): Boolean {
        return try {
            val s = Codec.decode(raw)
            mutate { cur ->
                cur.settings = s.settings
                cur.types = s.types
                cur.customers = s.customers
                cur.sampling = s.sampling
                cur.samples = s.samples
                cur.bakes = s.bakes
                cur.seq = s.seq
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearAll() {
        mutate { s ->
            s.sampling.clear(); s.samples.clear(); s.bakes.clear(); s.seq.clear()
        }
    }
}
