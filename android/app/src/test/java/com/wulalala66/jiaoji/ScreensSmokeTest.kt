package com.wulalala66.jiaoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 每个界面都渲染一遍，防止改代码时崩在组合阶段。
 * Robolectric 跑真实的 Compose 组合，不需要设备。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class ScreensSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun seed() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Repo.I.init(ctx)
        Repo.I.clearAll()
        AppConfig.debounceMs = 0
    }

    private fun smoke(content: @Composable (AppState) -> Unit) {
        rule.setContent {
            val s by Repo.I.state.collectAsState()
            JiaoJiTheme { content(s) }
        }
        rule.waitForIdle()
    }

    @Test(timeout = 120_000)
    fun formulasList() {
        smoke { s -> FormulasScreen(s, onOpen = {}) }
    }

    @Test(timeout = 120_000)
    fun formulaEditorWithAiCard() {
        val id = Repo.I.state.value.formulas.first().id
        smoke { s -> FormulaEditorScreen(s, id, onBack = {}, onOpenAi = {}) }
    }

    @Test(timeout = 120_000)
    fun formulaAiNotConfigured() {
        val id = Repo.I.state.value.formulas.first().id
        smoke { s -> FormulaAiScreen(s, id, onBack = {}) }
    }

    @Test(timeout = 120_000)
    fun density() {
        smoke { DensityScreen() }
    }

    @Test(timeout = 120_000)
    fun products() {
        smoke { s -> ProductsScreen(s) }
    }

    @Test(timeout = 120_000)
    fun settings() {
        smoke { s -> SettingsScreen(s, onOpenDiagnostics = {}) }
    }

    @Test(timeout = 120_000)
    fun root() {
        smoke { Root() }
    }
}
