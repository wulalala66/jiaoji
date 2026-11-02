package com.wulalala66.jiaoji

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.zip.ZipInputStream

/** 配料单导出：Excel 要是合法 xlsx，PDF 的排版绘制不能抛异常 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FormulaSheetTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Repo.I.init(ctx)
        Repo.I.clearAll()
    }

    /** 照着手写那张纸：600 g 一锅，D1NP 26.33% → 158.00 g */
    private fun seed(): Formula {
        val id = Repo.I.addFormula("低成本主机厂底涂胶3#")
        Repo.I.updateFormula(id) {
            it.purpose = "降低粘度"
            it.cure = "140*30"
        }
        Repo.I.setFormulaTotal(id, 600.0)
        Repo.I.addFormulaItem(id, "D1NP", 26.33)
        Repo.I.addFormulaItem(id, "环氧大豆油", 0.50)
        Repo.I.addFormulaItem(id, "CC-400", 16.67)
        Repo.I.addFormulaItem(id, "PCMA-12", 9.33)
        return Repo.I.state.value.formulaOf(id)!!
    }

    @Test
    fun xlsxIsValidPackageAndCarriesTheTable() {
        val f = seed()
        val file = FormulaSheet.xlsxFile(ctx, f)
        assertNotNull(file)
        val f2 = file!!
        assertTrue("文件太小：${f2.length()}", f2.length() > 800)

        val names = mutableListOf<String>()
        var sheet = ""
        ZipInputStream(f2.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                names += e.name
                if (e.name == "xl/worksheets/sheet1.xml") {
                    sheet = zip.readBytes().toString(Charsets.UTF_8)
                }
                e = zip.nextEntry
            }
        }
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("xl/workbook.xml"))
        assertTrue(names.contains("xl/styles.xml"))
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))

        assertTrue("缺标题", sheet.contains("低成本主机厂底涂胶3#"))
        assertTrue("缺实验目的", sheet.contains("实验目的"))
        assertTrue("缺固化温度", sheet.contains("固化温度"))
        assertTrue("缺合计行", sheet.contains("合计"))
        assertTrue("缺原料名", sheet.contains("D1NP"))
        assertTrue("缺检测项目列", sheet.contains("压流粘度"))
        // 26.33% × 600 g = 157.98 g（按模型算，不写死）
        val g = String.format(java.util.Locale.US, "%.2f", f.gramsOf(f.items.first()))
        assertTrue("克数没算对：$g", sheet.contains(g))
        assertTrue("百分比丢了", sheet.contains("26.33"))
        // 标题合并单元格
        assertTrue(sheet.contains("A1:F1"))
    }

    @Test
    fun xlsxTotalsMatchTheFormula() {
        val f = seed()
        val file = FormulaSheet.xlsxFile(ctx, f)!!
        var sheet = ""
        ZipInputStream(file.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        // 合计克数 = 600 * 52.83%
        val expect = String.format(java.util.Locale.US, "%.2f", f.sumGrams())
        assertTrue("合计 $expect 不在表里", sheet.contains(expect))
    }

    @Test
    fun pdfLayoutDrawsWithoutCrash() {
        val f = seed()
        val bmp = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        FormulaSheet.drawPage(canvas, f, f.items, 1, 1)

        var ink = 0
        for (x in 0 until 595 step 3) {
            for (y in 0 until 842 step 3) {
                if (bmp.getPixel(x, y) != Color.WHITE) ink++
            }
        }
        assertTrue("整页都是白的，什么都没画出来", ink > 500)
    }

    @Test
    fun pdfLayoutHandlesEmptyFormula() {
        val id = Repo.I.addFormula("空配方")
        val f = Repo.I.state.value.formulaOf(id)!!
        val bmp = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        FormulaSheet.drawPage(Canvas(bmp), f, f.items, 1, 1)
        assertEquals(0, f.items.size)
    }

    @Test
    fun purposeAndCureSurviveReload() {
        val f = seed()
        Repo.I.reload()
        val again = Repo.I.state.value.formulaOf(f.id)!!
        assertEquals("降低粘度", again.purpose)
        assertEquals("140*30", again.cure)
    }
}
