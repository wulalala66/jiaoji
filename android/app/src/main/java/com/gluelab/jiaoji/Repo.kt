package com.gluelab.jiaoji

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * 全局状态 + 全部写操作。
 *
 * 状态整体不可变地替换：每次写操作都先深拷贝一份、改完再整体发布，
 * 这样 Compose 拿到的永远是一致快照。
 * 存储：SharedPreferences 里一坨 JSON，与网页版完全同构，两边备份可以互导。
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
                AppLog.add(ctx, "读存储失败，已回退默认数据：${e.javaClass.simpleName} ${e.message}")
                Defaults.state()
            }
        }

        fun write(ctx: Context, s: AppState) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, Codec.encode(s)).apply()
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
        val s = read(context)
        _state.value = s
        write(context, s)
    }

    /** 从磁盘重读（回到前台时用：进程可能被杀过，数据以磁盘为准） */
    fun reload() {
        val c = ctx ?: return
        _state.value = read(c)
    }

    private fun mutate(block: (AppState) -> Unit) {
        val c = ctx ?: return
        val next = Codec.decode(Codec.encode(_state.value))
        block(next)
        _state.value = next
        write(c, next)
    }

    // ---------------- 产品条件表 ----------------

    fun addProduct(
        name: String,
        bakes: List<BakePreset> = emptyList(),
        plates: List<Plate> = emptyList(),
    ): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        var id: String? = null
        mutate { s ->
            if (s.products.any { it.name == n }) return@mutate
            val pid = "p_" + uid()
            s.products.add(
                Product(
                    id = pid,
                    name = n,
                    bakes = bakes.toMutableList(),
                    plates = plates.map { it.copy(id = it.id.ifBlank { "pl_" + uid() }) }.toMutableList(),
                )
            )
            id = pid
        }
        return id
    }

    fun updateProduct(
        id: String,
        name: String,
        bakes: List<BakePreset>,
        plates: List<Plate> = emptyList(),
    ) {
        val n = name.trim()
        if (n.isEmpty()) return
        mutate { s ->
            s.products.firstOrNull { it.id == id }?.apply {
                this.name = n
                this.bakes = bakes.toMutableList()
                this.plates = plates.map { it.copy(id = it.id.ifBlank { "pl_" + uid() }) }.toMutableList()
            }
        }
    }

    fun removeProduct(id: String) {
        mutate { s -> s.products.removeAll { it.id == id } }
    }

    // ---------------- 烘烤计时 ----------------

    /** 点一下条件就开始计时；同一时间只跑一个，新的会顶掉旧的 */
    fun startTimer(productName: String, tempC: Int, minutes: Int, now: Long = System.currentTimeMillis()) {
        if (minutes <= 0) return
        mutate { s ->
            s.timer = BakeTimer(
                productName = productName,
                tempC = tempC,
                minutes = minutes,
                endAt = now + minutes * 60_000L,
                pausedLeft = -1L,
            )
        }
    }

    fun pauseTimer(now: Long = System.currentTimeMillis()) {
        mutate { s ->
            val t = s.timer ?: return@mutate
            if (t.paused) return@mutate
            t.pausedLeft = t.remaining(now)
        }
    }

    fun resumeTimer(now: Long = System.currentTimeMillis()) {
        mutate { s ->
            val t = s.timer ?: return@mutate
            if (!t.paused) return@mutate
            t.endAt = now + t.pausedLeft.coerceAtLeast(0)
            t.pausedLeft = -1L
        }
    }

    fun stopTimer() {
        mutate { s -> s.timer = null }
    }

    // ---------------- 比重历史 ----------------

    /** 保存一次测试。输入和结果一起存，最多留 200 条 */
    fun addDensityRecord(
        m1: Double, m2: Double, m3: Double, m4: Double, m5: Double, m6: Double,
        now: Long = System.currentTimeMillis(),
    ): String {
        val r = DensityCalc.calc(m1, m2, m3, m4, m5, m6)
        val id = "d_" + uid()
        mutate { s ->
            s.densityLog.add(
                0,
                DensityRecord(
                    id = id, at = now,
                    m1 = m1, m2 = m2, m3 = m3, m4 = m4, m5 = m5, m6 = m6,
                    specific = r.specific, solid = r.solid, foam = r.foam,
                )
            )
            while (s.densityLog.size > 200) s.densityLog.removeAt(s.densityLog.size - 1)
        }
        return id
    }

    fun removeDensityRecord(id: String) {
        mutate { s -> s.densityLog.removeAll { it.id == id } }
    }

    fun clearDensityLog() {
        mutate { s -> s.densityLog.clear() }
    }

    // ---------------- 备份 ----------------

    fun exportJson(): String = Codec.encode(_state.value)

    /**
     * 导入备份：整体替换。
     * 老版本的备份里还有 formulas / settings.ai / sampling 这些已经下线的字段，
     * Codec 会直接忽略它们，只取还在用的产品条件表。
     */
    fun importJson(raw: String): Boolean {
        return try {
            val s = Codec.decode(raw)
            mutate { cur ->
                cur.products = s.products
                cur.densityLog = s.densityLog
                cur.timer = s.timer
            }
            true
        } catch (e: Exception) {
            AppLog.add(ctx ?: return false, "导入备份失败：${e.javaClass.simpleName} ${e.message}")
            false
        }
    }
}
