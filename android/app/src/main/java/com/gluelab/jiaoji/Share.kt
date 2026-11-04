package com.gluelab.jiaoji

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** 把结果交给系统去发：微信、邮件、打印，用户自己选 */
object Share {

    /** 已经生成好的文件（PDF / xlsx），走系统分享面板 */
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
            AppLog.add(ctx, "分享文件失败：${e.javaClass.simpleName} ${e.message}")
            text(ctx, title, "生成的文件在：${file.absolutePath}")
        }
    }

    /** 纯文本结果（比重计算结果这类） */
    fun text(ctx: Context, subject: String, body: String) {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            ctx.startActivity(Intent.createChooser(i, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            AppLog.add(ctx, "分享文本失败：${e.javaClass.simpleName} ${e.message}")
        }
    }

    /** 备份 JSON：写进缓存目录再分享，微信/邮件/文件管理器都能收 */
    fun json(ctx: Context, fileName: String, content: String) {
        try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeText(content)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "备份数据").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            AppLog.add(ctx, "导出备份失败：${e.javaClass.simpleName} ${e.message}")
            text(ctx, fileName, content)
        }
    }
}
