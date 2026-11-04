package com.gluelab.jiaoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 配料单：照着实验室那张纸质表格（原材料 / 用量(g) / wt% ｜ 项目 / 当日 / 次日）排版，
 * 能导 A4 PDF（直接打印）和 Excel（xlsx，方便改）。
 */
object FormulaSheet {

    /** 右半边「项目」列的固定顺序，跟纸质表格一致 */
    val TEST_ITEMS = listOf(
        "外观", "压流粘度", "旋转粘度", "宾汉粘度", "剪切强度",
        "比重", "固含", "发泡率", "抗流挂", "附着力",
        "邵氏硬度", "拉伸强度", "断裂伸长率",
    )

    private fun g2(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun p2(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun today(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR) % 100}.${c.get(Calendar.MONTH) + 1}.${c.get(Calendar.DAY_OF_MONTH)}"
    }

    private fun safeName(s: String): String {
        val t = s.ifBlank { "未命名配方" }.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
        return t.take(24)
    }

    private fun exportDir(ctx: Context): File = File(ctx.cacheDir, "exports").apply { mkdirs() }

    private fun stamp(): String = SimpleDateFormat("yyMMdd_HHmm", Locale.US).format(java.util.Date())

    /* ---------------- PDF（A4，直接打印） ---------------- */

    fun pdfFile(ctx: Context, f: Formula): File? = try {
        val file = File(exportDir(ctx), "配料_${safeName(f.name)}_${stamp()}.pdf")
        FileOutputStream(file).use { out ->
            val doc = PdfDocument()
            val perPage = 28
            val items = f.items
            val pageCount = maxOf(1, (items.size + perPage - 1) / perPage)
            for (p in 0 until pageCount) {
                val info = PdfDocument.PageInfo.Builder(595, 842, p + 1).create()
                val page = doc.startPage(info)
                drawPage(page.canvas, f, items.drop(p * perPage).take(perPage), p + 1, pageCount)
                doc.finishPage(page)
            }
            doc.writeTo(out)
            doc.close()
        }
        file
    } catch (_: Throwable) {
        null
    }

    /** 画一页（单独拆出来是为了能在单元测试里用 Canvas 验证排版不会抛异常） */
    fun drawPage(c: Canvas, f: Formula, rows: List<FormulaItem>, page: Int, pages: Int) {
        val m = 28f
        val w = 595f - m * 2
        val line = Paint().apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.9f; isAntiAlias = true
        }
        val thin = Paint().apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.6f; isAntiAlias = true
        }
        val title = Paint().apply {
            color = Color.BLACK; textSize = 17f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        }
        val head = Paint().apply {
            color = Color.BLACK; textSize = 10.5f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        }
        val body = Paint().apply {
            color = Color.BLACK; textSize = 10f; isAntiAlias = true
        }
        val small = Paint().apply {
            color = Color.BLACK; textSize = 9f; isAntiAlias = true
        }

        fun textLeft(s: String, x: Float, y: Float, p: Paint) = c.drawText(s, x, y, p)
        fun textRight(s: String, right: Float, y: Float, p: Paint) = c.drawText(s, right - p.measureText(s), y, p)
        fun textCenter(s: String, cx: Float, y: Float, p: Paint) = c.drawText(s, cx - p.measureText(s) / 2f, y, p)

        // 标题
        val t = f.name.ifBlank { "配方" }
        textCenter(t, 595f / 2f, m + 14f, title)
        if (pages > 1) {
            textRight("$page/$pages", 595f - m, m + 12f, small)
        }

        // 三格信息条：实验日期 / 实验目的 / 固化温度
        val infoY = m + 22f
        val infoH = 22f
        val cellW = w / 3f
        val labels = listOf("实验日期" to today(), "实验目的" to f.purpose, "固化温度" to f.cure)
        labels.forEachIndexed { i, (k, v) ->
            val x = m + cellW * i
            c.drawRect(x, infoY, x + cellW, infoY + infoH, line)
            c.drawLine(x + 56f, infoY, x + 56f, infoY + infoH, thin)
            textLeft(k, x + 5f, infoY + infoH / 2f + 3.6f, small)
            textLeft(v.ifBlank { " " }, x + 61f, infoY + infoH / 2f + 3.6f, body)
        }

        // 两张表并排：左 = 原材料 / 用量(g) / wt%，右 = 项目 / 当日 / 次日
        val top = infoY + infoH + 10f
        val lw = listOf(178f, 62f, 62f)          // 左表三列
        val rw = listOf(96f, 70f, 70f)           // 右表三列
        val lx = m
        val rx = m + lw.sum()                     // 紧贴左边表
        val headH = 22f
        val rowH = 20f

        val nRows = maxOf(rows.size + 1, TEST_ITEMS.size)   // 至少铺满右表的项目行
        val bottom = top + headH + rowH * nRows

        // 左表
        c.drawRect(lx, top, lx + lw.sum(), bottom, line)
        var y = top
        c.drawLine(lx, y + headH, lx + lw.sum(), y + headH, line)
        run {
            val heads = listOf("原材料", "用量(g)", "wt%")
            var x = lx
            heads.forEachIndexed { i, h ->
                if (i > 0) c.drawLine(x, top, x, bottom, thin)
                textCenter(h, x + lw[i] / 2f, top + headH / 2f + 3.8f, head)
                x += lw[i]
            }
        }
        // 左表正文
        for (i in 0 until nRows) {
            val ry = top + headH + rowH * i
            if (i > 0) c.drawLine(lx, ry, lx + lw.sum(), ry, thin)
            if (i < rows.size) {
                val it = rows[i]
                val ty = ry + rowH / 2f + 3.6f
                textLeft(it.name, lx + 5f, ty, body)
                textRight(g2(f.gramsOf(it)), lx + lw[0] + lw[1] - 5f, ty, body)
                textRight(p2(it.percent), lx + lw.sum() - 5f, ty, body)
            } else if (i == rows.size) {
                // 合计行
                val ty = ry + rowH / 2f + 3.6f
                textCenter("合计", lx + lw[0] / 2f, ty, head)
                textRight(g2(f.sumGrams()), lx + lw[0] + lw[1] - 5f, ty, body)
                textRight(p2(f.sumPercent()), lx + lw.sum() - 5f, ty, body)
            }
        }

        // 右表
        c.drawRect(rx, top, rx + rw.sum(), bottom, line)
        c.drawLine(rx, top + headH, rx + rw.sum(), top + headH, line)
        run {
            val heads = listOf("项目", "当日", "次日")
            var x = rx
            heads.forEachIndexed { i, h ->
                if (i > 0) c.drawLine(x, top, x, bottom, thin)
                textCenter(h, x + rw[i] / 2f, top + headH / 2f + 3.8f, head)
                x += rw[i]
            }
        }
        for (i in 0 until nRows) {
            val ry = top + headH + rowH * i
            if (i > 0) c.drawLine(rx, ry, rx + rw.sum(), ry, thin)
            if (i < TEST_ITEMS.size) {
                textLeft(TEST_ITEMS[i], rx + 5f, ry + rowH / 2f + 3.6f, body)
            }
        }

        // 备注
        var ny = bottom + 16f
        if (f.note.isNotBlank()) {
            textLeft("备注：${f.note}", m, ny, small)
            ny += 14f
        }
        textLeft("制表：胶记 · ${today()}", m, ny, small)
    }

    /* ---------------- Excel（xlsx，无第三方库） ---------------- */

    fun xlsxFile(ctx: Context, f: Formula): File? = try {
        val file = File(exportDir(ctx), "配料_${safeName(f.name)}_${stamp()}.xlsx")
        FileOutputStream(file).use { out ->
            ZipOutputStream(out).use { zip ->
                fun put(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                put("[Content_Types].xml", CONTENT_TYPES)
                put("_rels/.rels", ROOT_RELS)
                put("xl/workbook.xml", WORKBOOK)
                put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
                put("xl/styles.xml", STYLES)
                put("xl/worksheets/sheet1.xml", sheetXml(f))
            }
        }
        file
    } catch (_: Throwable) {
        null
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun cell(ref: String, style: Int, value: String?): String {
        if (value == null) return """<c r="$ref" s="$style"/>"""
        val num = value.toDoubleOrNull()
        return if (num != null && value.isNotBlank()) {
            """<c r="$ref" s="$style"><v>$num</v></c>"""
        } else {
            """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${esc(value)}</t></is></c>"""
        }
    }

    private fun sheetXml(f: Formula): String {
        val sb = StringBuilder()
        val lastRow = maxOf(f.items.size + 1, TEST_ITEMS.size)
        val rows = StringBuilder()
        var r = 0

        // 标题
        r++
        rows.append("""<row r="$r" ht="28" customHeight="1">""")
            .append(cell("A$r", 2, f.name.ifBlank { "配方" }))
            .append("</row>")

        // 信息行
        r++
        rows.append("""<row r="$r" ht="20" customHeight="1">""")
            .append(cell("A$r", 3, "实验日期")).append(cell("B$r", 0, today()))
            .append(cell("C$r", 3, "实验目的")).append(cell("D$r", 0, f.purpose))
            .append(cell("E$r", 3, "固化温度")).append(cell("F$r", 0, f.cure))
            .append("</row>")

        // 空行
        r++
        rows.append("""<row r="$r"/>""")

        // 表头
        r++
        rows.append("""<row r="$r" ht="22" customHeight="1">""")
            .append(cell("A$r", 1, "原材料")).append(cell("B$r", 1, "用量(g)")).append(cell("C$r", 1, "wt%"))
            .append(cell("D$r", 1, "项目")).append(cell("E$r", 1, "当日")).append(cell("F$r", 1, "次日"))
            .append("</row>")

        val firstData = r + 1
        for (i in 0 until lastRow) {
            r++
            rows.append("""<row r="$r" ht="19" customHeight="1">""")
            val it = f.items.getOrNull(i)
            if (it != null) {
                rows.append(cell("A$r", 0, it.name))
                    .append(cell("B$r", 0, g2(f.gramsOf(it))))
                    .append(cell("C$r", 0, p2(it.percent)))
            } else if (i == f.items.size) {
                rows.append(cell("A$r", 3, "合计"))
                    .append(cell("B$r", 0, g2(f.sumGrams())))
                    .append(cell("C$r", 0, p2(f.sumPercent())))
            } else {
                rows.append(cell("A$r", 0, null)).append(cell("B$r", 0, null)).append(cell("C$r", 0, null))
            }
            val item = TEST_ITEMS.getOrNull(i)
            rows.append(cell("D$r", 0, item ?: null))
                .append(cell("E$r", 0, null))
                .append(cell("F$r", 0, null))
            rows.append("</row>")
        }
        val lastData = r

        // 备注
        if (f.note.isNotBlank()) {
            r++
            rows.append("""<row r="$r" ht="20" customHeight="1">""")
                .append(cell("A$r", 0, "备注：${f.note}"))
                .append("</row>")
        }

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="18"/>""")
        sb.append("""<cols>""")
        sb.append("""<col min="1" max="1" width="26" customWidth="1"/>""")
        sb.append("""<col min="2" max="2" width="12" customWidth="1"/>""")
        sb.append("""<col min="3" max="3" width="10" customWidth="1"/>""")
        sb.append("""<col min="4" max="4" width="18" customWidth="1"/>""")
        sb.append("""<col min="5" max="6" width="12" customWidth="1"/>""")
        sb.append("""</cols>""")
        sb.append("<sheetData>").append(rows).append("</sheetData>")
        sb.append("""<mergeCells count="1"><mergeCell ref="A1:F1"/></mergeCells>""")
        sb.append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup paperSize="9" orientation="portrait" fitToWidth="1"/>""")
        sb.append("</worksheet>")
        return sb.toString().also { if (lastData < firstData) Unit }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="配方" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="11"/><name val="宋体"/></font>
<font><b/><sz val="11"/><name val="宋体"/></font>
<font><b/><sz val="16"/><name val="宋体"/></font>
</fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FF000000"/></left><right style="thin"><color rgb="FF000000"/></right><top style="thin"><color rgb="FF000000"/></top><bottom style="thin"><color rgb="FF000000"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="常规" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
