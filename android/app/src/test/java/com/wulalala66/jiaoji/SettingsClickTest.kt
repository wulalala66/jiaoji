package com.wulalala66.jiaoji

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 设置页与表单的「点了要真的有反应」测试。
 * 之前设置页开关全部无效（updateSettings 把 copy 的返回值丢了），这个文件专门盯这类问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsClickTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setup() {
        Repo.I.init(RuntimeEnvironment.getApplication())
        Repo.I.clearAll()
        Repo.I.updateSettings { it.copy(preAlertMinutes = 5, sound = true, vibrate = true, keepAwake = true) }
        AppConfig.debounceMs = 0
    }

    private fun settings() = Repo.I.state.value.settings

    private fun showSettings() {
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { SettingsScreen(s, onOpenType = {}, onOpenDiagnostics = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun preAlertPlusAndMinusWork() {
        showSettings()
        rule.onNodeWithText("+").performClick()
        rule.waitForIdle()
        assertEquals(6, settings().preAlertMinutes)

        rule.onNodeWithText("−").performClick()
        rule.onNodeWithText("−").performClick()
        rule.waitForIdle()
        assertEquals(4, settings().preAlertMinutes)
    }

    @Test
    fun togglesWorkByTappingLabel() {
        showSettings()
        rule.onNodeWithText("保持屏幕常亮").performClick()
        rule.waitForIdle()
        assertFalse("点文字也应该能切换", settings().keepAwake)

        rule.onNodeWithText("响铃").performClick()
        rule.waitForIdle()
        assertFalse(settings().sound)

        rule.onNodeWithText("振动").performClick()
        rule.waitForIdle()
        assertFalse(settings().vibrate)

        rule.onNodeWithText("保持屏幕常亮").performClick()
        rule.waitForIdle()
        assertTrue(settings().keepAwake)
    }

    @Test
    fun addCustomerFromDialog() {
        showSettings()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("＋ 添加"))
        rule.onNodeWithText("＋ 添加").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("吉利")
        rule.onNodeWithText("确定").performClick()
        rule.waitForIdle()
        assertTrue(Repo.I.state.value.customers.contains("吉利"))
    }

    @Test
    fun diagnosticsCardOpens() {
        var opened = false
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { SettingsScreen(s, onOpenType = {}, onOpenDiagnostics = { opened = true }) }
        }
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("自检与诊断"))
        rule.onNodeWithText("自检与诊断").performClick()
        rule.waitForIdle()
        assertTrue(opened)
    }

    @Test
    fun addTaskFromFormLandsOnPickedDay() {
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { AddTaskScreen(s, onBack = {}, onSaved = {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("明天").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("保存提醒").performClick()
        rule.waitForIdle()
        val t = Repo.I.state.value.sampling.lastOrNull()
        assertEquals("点「明天」后保存，时间应该落在明天", 1, dayOffsetOf(t!!.dueAt))
    }

    @Test
    fun sampleDetailTypingSavesValue() {
        val sampleId = Repo.I.createStandaloneSample("安徽万安", "base")
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { DetailScreen(s, System.currentTimeMillis(), sampleId, onBack = {}, onBake = {}) }
        }
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("3.4")
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        val saved = Repo.I.state.value.sampleOf(sampleId)!!.tests.any { it.value == "3.4" }
        assertTrue("输入的值应该落到第一个测试项", saved)
    }

    @Test
    fun lastValueForFindsPreviousBatch() {
        val first = Repo.I.createStandaloneSample("安徽万安", "base")
        val t0 = Repo.I.state.value.sampleOf(first)!!.tests.first()
        Repo.I.setTestValue(first, t0.id, "3.10")
        val second = Repo.I.createStandaloneSample("安徽万安", "base")
        val prev = Repo.I.state.value.lastValueFor(Repo.I.state.value.sampleOf(second)!!, t0.name)
        assertEquals("3.10", prev)
    }
}
