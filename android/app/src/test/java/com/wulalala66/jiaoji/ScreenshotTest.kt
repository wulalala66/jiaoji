package com.wulalala66.jiaoji

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 把每一屏真的画出来存成 PNG（build/screenshots），CI 里当构建产物传出来，
 * 用来肉眼检查排版。渲染失败不影响构建：出错就写一个 .txt 说明原因。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ctx() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun seed() {
        Repo.I.init(ctx())
        Repo.I.clearAll()
        AppConfig.debounceMs = 0
        Repo.I.state.value.formulas.map { it.id }.forEach { Repo.I.removeFormula(it) }
        val id = Repo.I.addFormula("低成本主机厂底涂胶3#", 600.0)
        Repo.I.addFormulaItem(id, "D1NP", 26.33)
        Repo.I.addFormulaItem(id, "环氧大豆油", 0.50)
        Repo.I.addFormulaItem(id, "D-80", 6.50)
        Repo.I.addFormulaItem(id, "CC-400", 16.67)
        Repo.I.addFormulaItem(id, "NLY-102", 18.33)
        Repo.I.updateFormula(id) {
            it.purpose = "降低粘度"
            it.cure = "140*30"
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val dir = File("build/screenshots").apply { mkdirs() }
        try {
            // 手动走时钟：waitForIdle 在有文本光标/动画时会一直不 idle
            rule.mainClock.autoAdvance = false
            rule.setContent {
                JiaoJiTheme {
                    Surface(color = Bg, modifier = Modifier.fillMaxSize()) { content() }
                }
            }
            rule.mainClock.advanceTimeBy(400L)
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {
            File(dir, "$name.txt").writeText("render failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Test
    fun shotFormulas() {
        val s = Repo.I.state.value
        shot("01-formulas") { FormulasScreen(s, onOpen = {}) }
    }

    @Test
    fun shotEditor() {
        val s = Repo.I.state.value
        val id = s.formulas.first().id
        shot("02-editor") { FormulaEditorScreen(s, id, onBack = {}, onOpenAi = {}) }
    }

    @Test
    fun shotAi() {
        val s = Repo.I.state.value
        val id = s.formulas.first().id
        shot("03-ai") { FormulaAiScreen(s, id, onBack = {}) }
    }

    @Test
    fun shotDensity() {
        shot("04-density") { DensityScreen() }
    }

    @Test
    fun shotProducts() {
        val s = Repo.I.state.value
        shot("05-products") { ProductsScreen(s) }
    }

    @Test
    fun shotSettings() {
        val s = Repo.I.state.value
        shot("06-settings") { SettingsScreen(s, onOpenDiagnostics = {}) }
    }
}
