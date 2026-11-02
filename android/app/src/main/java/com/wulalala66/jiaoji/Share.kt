package com.wulalala66.jiaoji

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Share {
    /** 把已经生成好的文件（PDF / xlsx）发出去，系统会列出微信、邮件、打印等接收方 */
    fun file(ctx: Context, file: File, mime: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            text(ctx, title, "生成的文件在：${file.absolutePath}")
        }
    }

    fun text(ctx: Context, subject: String, body: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        ctx.startActivity(Intent.createChooser(i, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun csv(ctx: Context, fileName: String, content: String) {
        try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeText(content)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "导出 CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            text(ctx, fileName, content)
        }
    }

    fun json(ctx: Context, fileName: String, content: String) {
        try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeText(content)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "备份数据").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            text(ctx, fileName, content)
        }
    }

    private fun cell(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** 每个测试项一列，按出现顺序取并集 */
    fun csvOf(state: AppState, samples: List<Sample>): String {
        val cols = LinkedHashMap<String, String>()
        samples.forEach { s -> s.tests.forEach { t -> cols.putIfAbsent(t.name, t.unit) } }
        val sb = StringBuilder()
        sb.append('\uFEFF')
        val head = mutableListOf("日期", "取样时间", "客户", "类型", "批次")
        cols.forEach { (n, u) -> head.add(if (u.isBlank()) n else "$n($u)") }
        head.addAll(listOf("不合格项", "备注", "状态"))
        sb.append(head.joinToString(",") { cell(it) }).append('\n')
        samples.forEach { s ->
            val row = mutableListOf(
                Fmt.md(s.sampledAt),
                Fmt.hm(s.sampledAt),
                s.customer,
                state.typeName(s.typeId),
                s.batchNo,
            )
            cols.forEach { (n, _) ->
                val t = s.tests.firstOrNull { it.name == n }
                row.add(
                    when {
                        t == null -> ""
                        t.skipped -> "跳过"
                        t.value.isBlank() -> ""
                        else -> t.value
                    }
                )
            }
            val bad = s.tests.filter { verdictOf(it) == Verdict.FAIL }.joinToString("、") { it.name }
            row.add(bad)
            row.add(s.note)
            row.add(if (s.status == "done") "已完成" else "进行中")
            sb.append(row.joinToString(",") { cell(it) }).append('\n')
        }
        return sb.toString()
    }

    fun reportOf(state: AppState, samples: List<Sample>): String {
        val sb = StringBuilder()
        sb.append("检测日报 ").append(Fmt.full(System.currentTimeMillis())).append('\n')
        sb.append("共 ").append(samples.size).append(" 个样品\n")
        val badAll = samples.sumOf { s -> s.tests.count { verdictOf(it) == Verdict.FAIL } }
        if (badAll > 0) sb.append("不合格 ").append(badAll).append(" 项\n")
        sb.append('\n')
        samples.forEach { s ->
            sb.append("【").append(s.customer.ifBlank { "未填客户" }).append(" · ")
                .append(state.typeName(s.typeId)).append(" · ").append(s.batchNo).append("】")
                .append(Fmt.hm(s.sampledAt)).append(" 取样\n")
            if (s.tests.isEmpty()) sb.append("  （无数据）\n")
            s.tests.forEach { t ->
                if (t.skipped) {
                    sb.append("  ").append(t.name).append("：跳过\n")
                } else if (t.value.isNotBlank()) {
                    val v = verdictOf(t)
                    sb.append("  ").append(t.name).append("：").append(t.value)
                    if (t.unit.isNotBlank()) sb.append(' ').append(t.unit)
                    if (t.min.isNotBlank() || t.max.isNotBlank()) {
                        sb.append("（标准 ").append(t.min.ifBlank { "—" }).append('~')
                            .append(t.max.ifBlank { "—" }).append("）")
                    }
                    if (v == Verdict.FAIL) sb.append(" ✗不合格") else if (v == Verdict.PASS) sb.append(" ✓")
                    sb.append('\n')
                }
            }
            if (s.note.isNotBlank()) sb.append("  备注：").append(s.note).append('\n')
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }
}
