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
        Repo.I.clearDensityLog()
        Repo.I.stopTimer()
        Repo.I.importJson(Codec.encode(AppState(products = Defaults.products())))
    }

    private fun state() = Repo.I.state.value
    private fun persisted() = Repo.read(RuntimeEnvironment.getApplication())

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
        assertEquals(0, p.plates.size)
        assertEquals("不用电泳板", p.plateText())
    }

    @Test
    fun duplicateProductNameIsRejected() {
        val a = Repo.I.addProduct("同名客户")
        val b = Repo.I.addProduct("同名客户")
        assertNotNull(a)
        assertNull("同名不该建第二个", b)
    }

    // ---------------- 烘烤计时 ----------------

    @Test
    fun timerCountsDownAndCanPause() {
        val now = 1_000_000L
        Repo.I.startTimer("安徽万安", 170, 20, now)
        val t = state().timer!!
        assertEquals(20 * 60_000L, t.remaining(now))
        assertEquals("安徽万安 · 170℃", t.label())

        Repo.I.pauseTimer(now + 60_000L)
        val p = state().timer!!
        assertTrue("应该处于暂停", p.paused)
        assertEquals("暂停后剩余时间冻住", 19 * 60_000L, p.remaining(now + 600_000L))

        Repo.I.resumeTimer(now + 600_000L)
        val r = state().timer!!
        assertTrue(!r.paused)
        assertEquals("继续后从剩余时间接着走", 19 * 60_000L, r.remaining(now + 600_000L))

        Repo.I.stopTimer()
        assertNull(state().timer)
    }

    @Test
    fun timerSurvivesReload() {
        val now = System.currentTimeMillis()
        Repo.I.startTimer("焊缝胶", 140, 30, now)
        Repo.I.reload()
        val t = state().timer
        assertNotNull("重新读盘后计时还在", t)
        assertEquals("焊缝胶", t!!.productName)
        assertEquals(30, t.minutes)
    }

    @Test
    fun startingAnotherTimerReplacesTheOldOne() {
        Repo.I.startTimer("A", 150, 10)
        Repo.I.startTimer("B", 180, 20)
        assertEquals("B", state().timer?.productName)
    }

    // ---------------- 比重历史 ----------------

    @Test
    fun densityRecordKeepsInputsAndResults() {
        val id = Repo.I.addDensityRecord(2.9, 1.82, 4.42, 2.26, 4.38, 2.26)
        val rec = state().densityLog.first()
        assertEquals(id, rec.id)
        assertEquals(2.9, rec.m1, 1e-9)
        assertEquals(2.26, rec.m6, 1e-9)
        assertEquals(1.4074, rec.specific!!, 1e-4)
        assertEquals(97.368, rec.solid!!, 1e-3)
        assertEquals(-3.704, rec.foam!!, 1e-3)
    }

    @Test
    fun densityLogNewestFirstAndCapped() {
        repeat(3) { Repo.I.addDensityRecord(1.0 + it, 0.5, 2.0, 1.0, 0.0, 0.0) }
        assertEquals(3, state().densityLog.size)
        assertEquals("最新的排最前面", 3.0, state().densityLog.first().m1, 1e-9)

        Repo.I.removeDensityRecord(state().densityLog.first().id)
        assertEquals(2, state().densityLog.size)

        Repo.I.clearDensityLog()
        assertTrue(state().densityLog.isEmpty())
    }

    @Test
    fun densityLogSurvivesExportImport() {
        Repo.I.addDensityRecord(2.9, 1.82, 4.42, 2.26, 4.38, 2.26)
        val json = Repo.I.exportJson()
        Repo.I.clearDensityLog()
        assertTrue(Repo.I.importJson(json))
        assertEquals(1, state().densityLog.size)
        assertEquals(1.4074, state().densityLog.first().specific!!, 1e-4)
    }

    // ---------------- 备份 ----------------

    @Test
    fun importJsonRejectsGarbage() {
        assertFalse(Repo.I.importJson("这不是 json"))
        assertFalse(Repo.I.importJson(""))
    }

    @Test
    fun importLegacyBackupStillWorks() {
        // 老版本（带取样/烘箱/AI/配方）的备份：多出来的字段要能被忽略，条件表照常导入
        val legacy = """
            {"v":1,
             "settings":{"preAlertMinutes":5,"sound":true,"ai":{"baseUrl":"https://x/v1","model":"m"}},
             "types":[],"customers":["老客户"],"ovens":[],
             "products":[{"id":"p1","name":"老产品","bakes":[{"temp":150,"minutes":25}]}],
             "formulas":[{"id":"f1","name":"老配方","totalG":600,"items":[]}],
             "sampling":[],"samples":[],"bakes":[],"seq":{}}
        """.trimIndent()
        assertTrue(Repo.I.importJson(legacy))
        assertEquals("老产品", state().productOf("p1")?.name)
        assertEquals(150, state().productOf("p1")?.bakes?.first()?.tempC)
    }

    @Test
    fun defaultsGiveSomethingToLookAt() {
        val d = Defaults.state()
        assertTrue("条件表应该有内置数据", d.products.size >= 10)
        assertTrue("打开时不该有正在跑的计时", d.timer == null)
        assertTrue("历史一开始是空的", d.densityLog.isEmpty())
    }
}
