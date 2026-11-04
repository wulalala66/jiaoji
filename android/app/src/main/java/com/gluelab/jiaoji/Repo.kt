package com.gluelab.jiaoji

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * 全局状态 + 全部写操作。
 *
 * 状态整体不可变地替换：每次写操作都先 Codec 深拷贝一份、改完再整体发布，
 * 这样 Compose 拿到的永远是一致快照，不会出现「读到一半被别人改了」。
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

    // ---------------- 设置 ----------------

    /**
     * 改设置。注意 block 必须「返回新的 Settings」（data class copy）——
     * 写成 (Settings) -> Unit 会让 `it.copy(...)` 的返回值被丢掉，
     * 表现就是设置页开关怎么点都没反应。
     */
    fun updateSettings(block: (Settings) -> Settings) {
        mutate { s -> s.settings = block(s.settings) }
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
                if (percent != null) it2.percent = percent.coerceIn(0.0, 100.0)
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
            f.items.firstOrNull { it.id == itemId }?.percent = (100.0 - others).coerceAtLeast(0.0)
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
                purpose = src.purpose,
                cure = src.cure,
                items = src.items.map { FormulaItem(id = "fi_" + uid(), name = it.name, percent = it.percent) }
                    .toMutableList(),
            )
            s.formulas.add(copy)
            newId = copy.id
        }
        return newId
    }

    fun clearFormulas() {
        mutate { s -> s.formulas.clear() }
    }

    // ---------------- 备份 ----------------

    /**
     * 导出备份。**故意把 API Key 清掉**：
     * 备份经常通过微信/邮件转来转去，带着 Key 等于把 Key 发出去了。
     * 导入之后重新填一次 Key 就行。
     */
    fun exportJson(): String {
        val safe = Codec.decode(Codec.encode(_state.value))
        safe.settings.ai.apiKey = ""
        return Codec.encode(safe)
    }

    /**
     * 导入备份：整体替换。
     * 老版本的备份里还有 sampling / samples / bakes 这些已经下线的字段，
     * Codec 会直接忽略它们，只取还在用的配方、条件表和 AI 配置。
     */
    fun importJson(raw: String): Boolean {
        return try {
            val s = Codec.decode(raw)
            mutate { cur ->
                cur.settings = s.settings
                cur.products = s.products
                cur.formulas = s.formulas
            }
            true
        } catch (e: Exception) {
            AppLog.add(ctx ?: return false, "导入备份失败：${e.javaClass.simpleName} ${e.message}")
            false
        }
    }
}
