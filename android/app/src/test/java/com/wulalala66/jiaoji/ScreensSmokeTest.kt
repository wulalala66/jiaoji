package com.wulalala66.jiaoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 冒烟测试：在 JVM 上（Robolectric）真实组合每一屏，确认不会抛异常。
 * 没有真机也能挡住「一打开就崩」这类问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class ScreensSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private var sampleId: String = ""

    @Before
    fun seed() {
        Repo.I.init(RuntimeEnvironment.getApplication())
        Repo.I.clearAll()
        val now = System.currentTimeMillis()
        val task = Repo.I.addSampling("安徽万安", "base", now + 20 * 60_000L, "早班")
        sampleId = Repo.I.confirmSampled(task, false, "door", 140, 30) ?: ""
        Repo.I.addBake("door", sampleId, "底涂胶", 140, 30, now - 6 * 60_000L)
        Repo.I.addBake("window", null, "焊缝胶", 160, 20, now - 26 * 60_000L)
        val s = Repo.I.state.value
        s.sampleOf(sampleId)?.tests?.firstOrNull()?.let { Repo.I.setTestValue(sampleId, it.id, "3.2") }
    }

    private fun smoke(content: @Composable (AppState, Long) -> Unit) {
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { content(s, System.currentTimeMillis()) }
        }
        rule.waitForIdle()
    }

    @Test
    fun today() = smoke { s, now -> TodayScreen(s, now, {}, {}, {}, {}) }

    @Test
    fun ovens() = smoke { s, now -> OvensScreen(s, now, {}, {}) }

    /** 自定义烘箱（多于两台）也要能正常渲染，首页那一行会变成横向滚动 */
    @Test
    fun ovensWithCustomOvens() {
        Repo.I.addOven("三号炉")
        Repo.I.addOven("四号炉")
        smoke { s, now -> OvensScreen(s, now, {}, {}) }
    }

    @Test
    fun todayWithCustomOvens() {
        Repo.I.addOven("三号炉")
        Repo.I.addOven("四号炉")
        smoke { s, now -> TodayScreen(s, now, {}, {}, {}, {}) }
    }

    @Test
    fun records() = smoke { s, now -> RecordsScreen(s, now, {}) }

    @Test
    fun settings() = smoke { s, _ -> SettingsScreen(s, {}) }

    @Test
    fun detail() = smoke { s, now -> DetailScreen(s, now, sampleId, {}, {}) }

    @Test
    fun addTask() = smoke { s, _ -> AddTaskScreen(s, {}, {}) }

    @Test
    fun bakeFormNew() = smoke { s, _ -> BakeFormScreen(s, null, null, "door", {}, {}) }

    @Test
    fun bakeFormEdit() = smoke { s, _ -> BakeFormScreen(s, s.bakes.firstOrNull()?.id, null, "door", {}, {}) }

    @Test
    fun typeEditorNew() = smoke { s, _ -> TypeEditorScreen(s, null, {}) }

    @Test
    fun typeEditorExisting() = smoke { s, _ -> TypeEditorScreen(s, "base", {}) }

    @Test
    fun sampledDialog() = smoke { s, _ ->
        SampledDialog(s, s.sampling.first(), {}, { _, _, _, _ -> })
    }

    /** 整个 App 外壳（导航栏 + 提醒条）。计时协程不让测试时钟自动推进，避免无限循环。 */
    @Test(timeout = 120_000)
    fun root() {
        rule.mainClock.autoAdvance = false
        rule.setContent { JiaoJiTheme { Root() } }
        rule.waitForIdle()
    }

    @Test
    fun toolsScreen() = smoke { s, _ ->
        ToolsScreen(s, {}, {}, {}, {}, {})
    }

    @Test
    fun densityScreen() = smoke { _, _ -> DensityScreen({}) }

    @Test
    fun productsScreen() = smoke { s, _ -> ProductsScreen(s, {}) }

    @Test
    fun formulasScreen() {
        val id = Repo.I.addFormula("底涂 A 料", 600.0)
        Repo.I.addFormulaItem(id, "树脂", 60.0)
        Repo.I.addFormulaItem(id, "填料", 30.0)
        smoke { s, _ -> FormulasScreen(s, {}, {}) }
    }

    @Test
    fun formulaEditor() {
        val id = Repo.I.addFormula("底涂 A 料", 600.0)
        Repo.I.addFormulaItem(id, "树脂", 60.0)
        smoke { s, _ -> FormulaEditorScreen(s, id, {}) }
    }
}
