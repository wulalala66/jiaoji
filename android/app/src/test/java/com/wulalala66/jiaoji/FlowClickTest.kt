package com.wulalala66.jiaoji

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 点击链路测试：真的点按钮，确认回调被触发。
 * 之前用户反馈「好多按钮点不了」，这组测试保证关键按钮一定是通的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class FlowClickTest {

    @get:Rule
    val rule = createComposeRule()

    private var sampleId: String = ""
    private var bakeId: String = ""
    private val now = System.currentTimeMillis()

    @Before
    fun seed() {
        Repo.I.init(RuntimeEnvironment.getApplication())
        Repo.I.clearAll()
        val task = Repo.I.addSampling("安徽万安", "base", now + 20 * 60_000L, "早班")
        sampleId = Repo.I.confirmSampled(task, false, "door", 140, 30) ?: ""
        bakeId = Repo.I.addBake("door", sampleId, "底涂胶", 140, 30, now - 6 * 60_000L)
        Repo.I.addBake("window", null, "焊缝胶", 160, 20, now - 26 * 60_000L)
        // 另留一个「还没取样」的任务，用来测「已取样」按钮
        Repo.I.addSampling("奇瑞", "weld", now + 25 * 60_000L, "下午班")
    }

    @Test
    fun tapSampledButton_firesCallback() {
        var hits = 0
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme {
                TodayScreen(s, System.currentTimeMillis(), {}, { hits++ }, {}, {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("已取样").performClick()
        rule.waitForIdle()
        assertEquals(1, hits)
    }

    @Test
    fun tapAddBakeOnOvens_firesCallback() {
        var picked = ""
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme {
                OvensScreen(s, System.currentTimeMillis(), { picked = it }, {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("＋ 在靠门烘箱放一个样品").performClick()
        rule.waitForIdle()
        assertEquals("door", picked)
    }

    @Test
    fun tapSampleCard_firesOpen() {
        var opened: String? = null
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme {
                RecordsScreen(s, System.currentTimeMillis(), { opened = it })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("安徽万安").performClick()
        rule.waitForIdle()
        assertEquals(sampleId, opened)
    }

    @Test
    fun alarmScheduledForBakeEnd() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val pending = AlarmScheduler.pendingMap(ctx)
        assertTrue("应该排定了闹钟", pending.isNotEmpty())
        val at = pending["b:$bakeId"]
        assertNotNull("烘烤到点闹钟必须存在", at)
        val expect = now - 6 * 60_000L + 30 * 60_000L
        assertTrue("闹钟时间应等于出炉时间", abs((at ?: 0L) - expect) < 5_000L)
        assertTrue("提前提醒也应排定", pending.containsKey("bp:$bakeId"))
    }

    @Test
    fun ringDoneMarksBakeDone() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        RingActions.done(ctx, "b:$bakeId")
        val bake = Repo.I.state.value.bakes.firstOrNull { it.id == bakeId }
        assertEquals("done", bake?.status)
    }

    @Test
    fun ringSnoozePushesEndAt() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        val before = Repo.I.state.value.bakes.first { it.id == bakeId }.endAt
        RingActions.snooze(ctx, "b:$bakeId", 2)
        val after = Repo.I.state.value.bakes.first { it.id == bakeId }
        assertEquals(before + 2 * 60_000L, after.endAt)
        assertEquals("running", after.status)
    }

    @Test
    fun settingsScreenOpensDiagnostics() {
        var open = 0
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { SettingsScreen(s, {}, { open++ }) }
        }
        rule.waitForIdle()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("自检与诊断"))
        rule.waitForIdle()
        rule.onNodeWithText("自检与诊断").performClick()
        rule.waitForIdle()
        assertEquals(1, open)
    }
}
