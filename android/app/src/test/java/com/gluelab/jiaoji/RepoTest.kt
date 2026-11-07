package com.gluelab.jiaoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 数据层测试：每个写操作都必须真的改到状态，并且能落到存储里。
 * 这里挡的是「点了没反应」「刷新后回退」「导入备份丢数据」这类问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepoTest {

    @Before
    fun setup() {
        Repo.I.init(RuntimeEnvironment.getApplication())
        Repo.I.clearFormulas()
        Repo.I.updateSettings { s -> s.copy(ai = AiSettings()) }
    }

    private fun state() = Repo.I.state.value
    private fun persisted() = Repo.read(RuntimeEnvironment.getApplication())

    // ---------------- 设置 ----------------

    @Test
    fun aiSettingsPersistAcrossReload() {
        Repo.I.updateSettings { s ->
            s.copy(ai = s.ai.copy(baseUrl = "https://api.example.com/v1", model = "gpt-4o-mini", thinking = "low"))
        }
        assertEquals("https://api.example.com/v1", state().settings.ai.baseUrl)
        assertEquals("low", state().settings.ai.thinking)

        Repo.I.reload()
        assertEquals("重新读盘后地址还在", "https://api.example.com/v1", state().settings.ai.baseUrl)
        assertEquals("gpt-4o-mini", persisted().settings.ai.model)
    }

    // ---------------- 产品条件表 ----------------

    @Test
    fun productCrud() {
        val id = Repo.I.addProduct("测试客户", listOf(BakePreset(160, 20), BakePreset(180, 15)))
        assertNotNull("应该建出来", id)
        val p = state().productOf(id)
        assertEquals("测试客户", p?.name)
        assertEquals(2, p?.bakes?.size)

        Repo.I.updateProduct(id!!, "改名了", listOf(BakePreset(170, 25)))
        assertEquals("改名了", state().productOf(id)?.name)
        assertEquals(170, state().productOf(id)?.bakes?.first()?.tempC)

        Repo.I.removeProduct(id)
        assertNull("应该删掉了", state().productOf(id))
    }

    @Test
    fun productPlatesKeepTheirOwnPurpose() {
        val id = Repo.I.addProduct(
            "电泳板客户",
            listOf(BakePreset(160, 20)),
            listOf(
                Plate("pl1", fresh = true, note = "2mm 流挂"),
                Plate("pl2", fresh = false, note = "测附着力"),
                Plate("pl3", fresh = false, note = ""),
                Plate("pl4", fresh = false, note = ""),
            ),
        )!!
        val p = state().productOf(id)!!
        assertEquals(4, p.plates.size)
        assertEquals(1, p.plateNew)
        assertEquals(3, p.plateOld)
        assertEquals("2mm 流挂", p.plates.first { it.fresh }.note)
        // 旧板也可以有自己的用途，不是只有新板才有条件
        assertEquals("测附着力", p.plates.first { !it.fresh }.note)
        assertTrue(p.plateText().contains("4 块"))
        assertTrue(p.plateLines().any { it == "新 · 2mm 流挂" })
        assertTrue(p.plateLines().any { it == "旧 · 测附着力" })
    }

    @Test
    fun productPlatesSurviveExportAndImport() {
        val id = Repo.I.addProduct(
            "电泳板客户",
            emptyList(),
            listOf(Plate("pl1", fresh = true, note = "2mm 流挂"), Plate("pl2", fresh = false, note = "测硬度")),
        )!!
        val json = Repo.I.exportJson()
        Repo.I.removeProduct(id)
        assertNull(state().productOf(id))

        assertTrue(Repo.I.importJson(json))
        val back = state().productOf(id)
        assertNotNull("导入后电泳板不能丢", back)
        assertEquals(2, back!!.plates.size)
        assertEquals("2mm 流挂", back.plates[0].note)
        assertEquals("测硬度", back.plates[1].note)
        assertTrue(back.plates[1].fresh.not())
    }

    @Test
    fun legacyPlateFieldsMigrateToPlateList() {
        // 3.0.4 的老格式：只有总数 / 新板数 / 新板要求
        val legacy = """
            {"v":1,"products":[{"id":"p9","name":"老客户","bakes":[],
             "plateTotal":4,"plateNew":1,"plateSpec":"2mm 流挂"}],"formulas":[]}
        """.trimIndent()
        assertTrue(Repo.I.importJson(legacy))
        val p = state().productOf("p9")!!
        assertEquals("老数据要折成 4 块板", 4, p.plates.size)
        assertEquals(1, p.plateNew)
        assertEquals("2mm 流挂", p.plates.first { it.fresh }.note)
        assertEquals("旧板不该带要求", "", p.plates.first { !it.fresh }.note)
    }

    @Test
    fun productWithoutPlatesSaysSo() {
        val id = Repo.I.addProduct("不用板", emptyList())!!
        val p = state().productOf(id)!!
        assertEquals(0, p.plateTotal)
        assertEquals("不用电泳板", p.plateText())
    }

    @Test
    fun duplicateProductNameIsRejected() {
        val a = Repo.I.addProduct("同名客户")
        val b = Repo.I.addProduct("同名客户")
        assertNotNull(a)
        assertNull("同名不该建第二个", b)
    }

    // ---------------- 配料 ----------------

    @Test
    fun formulaGramsAndPercentFollowTotal() {
        val id = Repo.I.addFormula("测试配方", 600.0)
        Repo.I.addFormulaItem(id, "A", 25.0)
        Repo.I.addFormulaItem(id, "B", 75.0)
        var f = state().formulaOf(id)!!
        assertEquals("合计应为 100%", 100.0, f.sumPercent(), 1e-6)
        assertEquals("25% 的克数", 150.0, f.gramsOf(f.items[0]), 1e-6)
        assertTrue("凑满了", f.isComplete())

        Repo.I.setFormulaTotal(id, 1000.0)
        f = state().formulaOf(id)!!
        assertEquals("改总量不动百分比", 25.0, f.items[0].percent, 1e-6)
        assertEquals("克数跟着变", 250.0, f.gramsOf(f.items[0]), 1e-6)
    }

    @Test
    fun fillRestFillsTheLastGap() {
        val id = Repo.I.addFormula("补差", 600.0)
        Repo.I.addFormulaItem(id, "A", 30.0)
        val b = Repo.I.addFormulaItem(id, "B", 20.0)
        val f0 = state().formulaOf(id)!!
        assertEquals("还差 50%", 50.0, f0.restPercent(), 1e-6)

        Repo.I.fillFormulaRest(id, b)
        val f = state().formulaOf(id)!!
        assertEquals(100.0, f.sumPercent(), 1e-6)
        assertEquals("B 应该变成 70%", 70.0, f.items.first { it.id == b }.percent, 1e-6)
    }

    @Test
    fun formulaItemEditAndRemove() {
        val id = Repo.I.addFormula("增删改", 600.0)
        val a = Repo.I.addFormulaItem(id, "A", 10.0)
        Repo.I.updateFormulaItem(id, a, name = "A2", percent = 12.5)
        val item = state().formulaOf(id)!!.items.first()
        assertEquals("A2", item.name)
        assertEquals(12.5, item.percent, 1e-6)

        Repo.I.updateFormulaItem(id, a, percent = 500.0)
        assertEquals("百分比要夹到 0~100", 100.0, state().formulaOf(id)!!.items.first().percent, 1e-6)

        Repo.I.removeFormulaItem(id, a)
        assertTrue("应该删掉了", state().formulaOf(id)!!.items.isEmpty())
    }

    @Test
    fun formulaDuplicateKeepsMetaAndItems() {
        val id = Repo.I.addFormula("原件", 600.0)
        Repo.I.updateFormula(id) {
            it.purpose = "降低粘度"
            it.cure = "140*30"
        }
        Repo.I.addFormulaItem(id, "D1NP", 26.33)

        val copyId = Repo.I.duplicateFormula(id)
        val copy = state().formulaOf(copyId)!!
        assertEquals("原件 副本", copy.name)
        assertEquals("实验目的要一起复制", "降低粘度", copy.purpose)
        assertEquals("固化温度要一起复制", "140*30", copy.cure)
        assertEquals(1, copy.items.size)
        assertTrue("副本的原料 id 必须是新的", copy.items[0].id != state().formulaOf(id)!!.items[0].id)

        Repo.I.removeFormula(id)
        assertNull(state().formulaOf(id))
        assertNotNull("删原件不能影响副本", state().formulaOf(copyId))
    }

    // ---------------- 备份 ----------------

    @Test
    fun exportImportRoundTripKeepsFormulasAndProducts() {
        val fid = Repo.I.addFormula("导出测试", 750.0)
        Repo.I.addFormulaItem(fid, "D1NP", 26.33)
        Repo.I.updateFormula(fid) { it.cure = "150*25" }
        val pid = Repo.I.addProduct("导出客户", listOf(BakePreset(155, 35)))

        val json = Repo.I.exportJson()

        // 清空后重新导入，配方和条件表都必须回来（这里曾经漏掉过）
        Repo.I.clearFormulas()
        Repo.I.removeProduct(pid!!)
        assertNull(state().formulaOf(fid))
        assertNull(state().productOf(pid))

        assertTrue("导入应该成功", Repo.I.importJson(json))
        val f = state().formulaOf(fid)
        assertNotNull("配方必须被导入", f)
        assertEquals("导出测试", f!!.name)
        assertEquals(750.0, f.totalG, 1e-6)
        assertEquals("150*25", f.cure)
        assertEquals(1, f.items.size)

        val p = state().productOf(pid)
        assertNotNull("条件表必须被导入", p)
        assertEquals(155, p!!.bakes.first().tempC)
    }

    @Test
    fun importJsonRejectsGarbage() {
        assertFalse(Repo.I.importJson("这不是 json"))
        assertFalse(Repo.I.importJson(""))
    }

    @Test
    fun importLegacyBackupStillWorks() {
        // 老版本（带取样/烘箱）的备份：多出来的字段要能被忽略，配方照常导入
        val legacy = """
            {"v":1,
             "settings":{"preAlertMinutes":5,"sound":true,"ai":{"baseUrl":"https://x/v1","model":"m"}},
             "types":[],"customers":["老客户"],"ovens":[],
             "products":[{"id":"p1","name":"老产品","bakes":[{"temp":150,"minutes":25}]}],
             "formulas":[{"id":"f1","name":"老配方","totalG":600,"items":[{"id":"i1","name":"A","percent":100}]}],
             "sampling":[],"samples":[],"bakes":[],"seq":{}}
        """.trimIndent()
        assertTrue(Repo.I.importJson(legacy))
        assertNotNull(state().formulaOf("f1"))
        assertEquals("https://x/v1", state().settings.ai.baseUrl)
        assertEquals("老产品", state().productOf("p1")?.name)
    }

    @Test
    fun stateSurvivesReload() {
        val id = Repo.I.addFormula("重启也要在", 600.0)
        Repo.I.addFormulaItem(id, "A", 42.0)
        Repo.I.reload()
        val f = state().formulaOf(id)
        assertNotNull("重新读盘后配方还在", f)
        assertEquals(42.0, f!!.items.first().percent, 1e-6)
    }

    @Test
    fun defaultsGiveSomethingToLookAt() {
        val d = Defaults.state()
        assertTrue("第一次打开应该有一条示例配方", d.formulas.isNotEmpty())
        assertTrue("条件表应该有内置数据", d.products.size >= 10)
    }
}
