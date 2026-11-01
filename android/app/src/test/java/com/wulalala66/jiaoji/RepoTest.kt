package com.wulalala66.jiaoji

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
 * 这里挡的是「点了没反应」「刷新后回退」这类问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepoTest {

    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        Repo.I.init(RuntimeEnvironment.getApplication())
        Repo.I.clearAll()
        Repo.I.updateSettings { it.copy(preAlertMinutes = 5, sound = true, vibrate = true, keepAwake = true) }
    }

    private fun state() = Repo.I.state.value
    private fun persisted() = Repo.read(RuntimeEnvironment.getApplication())

    @Test
    fun settingsToggleTakesEffectAndPersists() {
        Repo.I.updateSettings { it.copy(keepAwake = false, sound = false) }
        assertFalse("屏幕常亮应关闭", state().settings.keepAwake)
        assertFalse("响铃应关闭", state().settings.sound)
        assertFalse("应该写进存储", persisted().settings.keepAwake)

        Repo.I.updateSettings { it.copy(keepAwake = true) }
        assertTrue(state().settings.keepAwake)
    }

    @Test
    fun preAlertStepperClamps() {
        Repo.I.updateSettings { it.copy(preAlertMinutes = (it.preAlertMinutes - 1).coerceAtLeast(0)) }
        assertEquals(4, state().settings.preAlertMinutes)
        Repo.I.updateSettings { it.copy(preAlertMinutes = 0) }
        Repo.I.updateSettings { it.copy(preAlertMinutes = (it.preAlertMinutes - 1).coerceAtLeast(0)) }
        assertEquals(0, state().settings.preAlertMinutes)
        Repo.I.updateSettings { it.copy(preAlertMinutes = (it.preAlertMinutes + 1).coerceAtMost(60)) }
        assertEquals(1, state().settings.preAlertMinutes)
    }

    @Test
    fun samplingLifecycle() {
        val id = Repo.I.addSampling("安徽万安", "base", now + 600_000L, "早班")
        assertEquals(1, state().sampling.size)
        assertEquals("pending", state().sampling[0].status)

        Repo.I.ackSampling(id)
        assertTrue(state().sampling[0].dueAlarmed)

        Repo.I.deleteSampling(id)
        assertTrue(state().sampling.isEmpty())
    }

    @Test
    fun confirmSampledCreatesSampleWithTests() {
        val taskId = Repo.I.addSampling("安徽万安", "base", now, "")
        val preview = Repo.I.previewBatchNo("base")
        val sampleId = Repo.I.confirmSampled(taskId, false, "door", 140, 30)
        assertNotNull(sampleId)
        val s = state()
        val sample = s.sampleOf(sampleId!!)
        assertNotNull("应该建档", sample)
        assertEquals("安徽万安", sample!!.customer)
        assertEquals("base", sample.typeId)
        assertTrue("批次号应递增", sample.batchNo.isNotEmpty())
        assertEquals("预览的批次号应该就是实际用的", preview, sample.batchNo)
        assertTrue("应带出默认检测项", sample.tests.isNotEmpty())
        assertEquals("任务应标记完成", "done", s.sampling[0].status)
        assertEquals(sampleId, s.sampling[0].sampleId)
    }

    @Test
    fun confirmSampledWithBakeCreatesTimer() {
        val taskId = Repo.I.addSampling("安徽万安", "weld", now, "")
        val sampleId = Repo.I.confirmSampled(taskId, true, "window", 160, 20)!!
        val bake = state().bakes.firstOrNull { it.sampleId == sampleId }
        assertNotNull("应该同时开一个计时", bake)
        assertEquals("window", bake!!.oven)
        assertEquals(160, bake.tempC)
        assertEquals(20, bake.minutes)
        assertEquals(bake.startAt + 20 * 60_000L, bake.endAt)
    }

    @Test
    fun testValuesAndSkip() {
        val sampleId = Repo.I.createStandaloneSample("奇瑞", "base")
        val s0 = state().sampleOf(sampleId)!!
        val t = s0.tests.first()

        Repo.I.setTestValue(sampleId, t.id, "3.25")
        assertEquals("3.25", state().sampleOf(sampleId)!!.tests.first { it.id == t.id }.value)

        Repo.I.setTestNote(sampleId, t.id, "偏高")
        assertEquals("偏高", state().sampleOf(sampleId)!!.tests.first { it.id == t.id }.note)

        Repo.I.toggleTestSkip(sampleId, t.id)
        val skipped = state().sampleOf(sampleId)!!.tests.first { it.id == t.id }
        assertTrue(skipped.skipped)
        assertEquals("跳过时应清空数值", "", skipped.value)

        Repo.I.setTestValue(sampleId, t.id, "4")
        assertFalse("重新填值应取消跳过", state().sampleOf(sampleId)!!.tests.first { it.id == t.id }.skipped)
    }

    @Test
    fun addAndRemoveTest() {
        val sampleId = Repo.I.createStandaloneSample("奇瑞", "base")
        val before = state().sampleOf(sampleId)!!.tests.size
        Repo.I.addTest(sampleId, "表干时间", "min", "0", "30")
        val after = state().sampleOf(sampleId)!!.tests
        assertEquals(before + 1, after.size)
        val added = after.last()
        assertEquals("表干时间", added.name)
        Repo.I.removeTest(sampleId, added.id)
        assertEquals(before, state().sampleOf(sampleId)!!.tests.size)
    }

    @Test
    fun sampleNoteAndStatus() {
        val sampleId = Repo.I.createStandaloneSample("奇瑞", "base")
        Repo.I.setSampleNote(sampleId, "车间温度偏高")
        assertEquals("车间温度偏高", state().sampleOf(sampleId)!!.note)
        Repo.I.completeSample(sampleId)
        assertEquals("done", state().sampleOf(sampleId)!!.status)
        Repo.I.reopenSample(sampleId)
        assertEquals("active", state().sampleOf(sampleId)!!.status)
        Repo.I.deleteSample(sampleId)
        assertNull(state().sampleOf(sampleId))
    }

    @Test
    fun bakeActions() {
        val id = Repo.I.addBake("door", null, "底涂胶", 140, 30, now - 6 * 60_000L)
        val b0 = state().bakes.first { it.id == id }
        assertEquals("running", b0.status)

        Repo.I.extendBake(id, 5)
        val b1 = state().bakes.first { it.id == id }
        assertEquals(b0.endAt + 5 * 60_000L, b1.endAt)
        assertEquals(35, b1.minutes)

        Repo.I.snoozeBake(id, 2)
        val b2 = state().bakes.first { it.id == id }
        assertEquals(b1.endAt + 2 * 60_000L, b2.endAt)
        assertEquals("running", b2.status)

        Repo.I.markBakeDone(id)
        val b3 = state().bakes.first { it.id == id }
        assertEquals("done", b3.status)
        assertNotNull(b3.doneAt)

        Repo.I.updateBake(id, "window", "焊缝胶", 160, 20, now, null)
        val b4 = state().bakes.first { it.id == id }
        assertEquals("window", b4.oven)
        assertEquals(160, b4.tempC)
        assertEquals(now + 20 * 60_000L, b4.endAt)
        assertEquals("running", b4.status)
        assertNull("改时间后应清掉完成时间", b4.doneAt)

        Repo.I.removeBake(id)
        assertTrue(state().bakes.isEmpty())
    }

    @Test
    fun flipDueBakesOnlyTouchesExpired() {
        val expired = Repo.I.addBake("door", null, "A", 140, 30, now - 40 * 60_000L)
        val running = Repo.I.addBake("window", null, "B", 140, 30, now - 5 * 60_000L)
        Repo.I.flipDueBakes()
        assertEquals("due", state().bakes.first { it.id == expired }.status)
        assertEquals("running", state().bakes.first { it.id == running }.status)
    }

    @Test
    fun customers() {
        Repo.I.addCustomer("长安")
        assertTrue(state().customers.contains("长安"))
        Repo.I.addCustomer("长安")
        assertEquals("重复添加不应产生两条", 1, state().customers.count { it == "长安" })
        Repo.I.addSampling("长安", "base", now, "")
        Repo.I.renameCustomer("长安", "长安汽车")
        assertTrue(state().customers.contains("长安汽车"))
        assertEquals("长安汽车", state().sampling[0].customer)
        Repo.I.removeCustomer("长安汽车")
        assertFalse(state().customers.contains("长安汽车"))
    }

    @Test
    fun typesUpsertAndDelete() {
        val before = state().types.size
        val t = ProductType(id = "type_test", name = "结构胶")
        Repo.I.upsertType(t)
        assertEquals(before + 1, state().types.size)
        assertEquals("结构胶", state().typeOf("type_test")?.name)

        Repo.I.upsertType(t.copy(name = "结构胶2"))
        assertEquals("同名 id 应更新而不是新增", before + 1, state().types.size)
        assertEquals("结构胶2", state().typeOf("type_test")?.name)

        Repo.I.deleteType("type_test")
        assertEquals(before, state().types.size)
        Repo.I.deleteType("other")
        assertNotNull("other 类型不能删", state().typeOf("other"))
    }

    @Test
    fun exportImportRoundTrip() {
        Repo.I.addSampling("安徽万安", "base", now + 60_000L, "早班")
        val sid = Repo.I.createStandaloneSample("奇瑞", "weld")
        Repo.I.setTestValue(sid, state().sampleOf(sid)!!.tests.first().id, "7.5")
        Repo.I.addBake("door", sid, "焊缝胶", 160, 20, now)
        Repo.I.updateSettings { it.copy(preAlertMinutes = 9, keepAwake = false) }

        val json = Repo.I.exportJson()
        Repo.I.clearAll()
        assertTrue(state().samples.isEmpty())

        assertTrue(Repo.I.importJson(json))
        assertEquals(1, state().samples.size)
        assertEquals(1, state().bakes.size)
        assertEquals(1, state().sampling.size)
        assertEquals(9, state().settings.preAlertMinutes)
        assertFalse(state().settings.keepAwake)
        assertEquals("7.5", state().samples[0].tests.first().value)
    }

    @Test
    fun importJsonRejectsGarbage() {
        assertFalse(Repo.I.importJson("{not json"))
        assertEquals(0, state().samples.size)
    }

    @Test
    fun stateSurvivesReload() {
        val sid = Repo.I.createStandaloneSample("奇瑞", "base")
        Repo.I.setTestValue(sid, state().sampleOf(sid)!!.tests.first().id, "1.5")
        Repo.I.reload()
        assertEquals(1, state().samples.size)
        assertEquals("1.5", state().samples[0].tests.first().value)
    }
}
