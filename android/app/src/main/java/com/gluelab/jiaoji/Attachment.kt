package com.gluelab.jiaoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * 发给 AI 的附件。
 *
 * 只做两件事，够用且不引第三方库：
 *  - 图片（含 PDF 渲染出来的页）→ 压成 JPEG 的 base64 data URL，按 OpenAI 的 image_url 格式发；
 *  - 纯文本类（txt/md/csv/json/docx）→ 抽成文本，拼在消息正文里。
 *
 * 尺寸和字数都卡了上限：一张 4000px 的原图 base64 之后能有 8MB，直接顶爆上下文和内存。
 */
data class Attachment(
    /** 显示用的名字 */
    val name: String,
    /** data:image/jpeg;base64,… 或者空（纯文本附件没有图） */
    val dataUrl: String,
    /** 纯文本附件的内容 */
    val text: String = "",
) {
    val isImage: Boolean get() = dataUrl.isNotEmpty()
}

object Attach {

    /** 图片最长边压到这么多像素：再大模型也看不清，只是白烧 token */
    private const val MAX_IMAGE_EDGE = 1568
    private const val JPEG_QUALITY = 85
    /** PDF 最多取几页 */
    private const val MAX_PDF_PAGES = 3
    /** 文本附件最多带多少字 */
    private const val MAX_TEXT = 30_000

    /** 一次最多带几个附件 */
    const val MAX_FILES = 4

    /** 支持的文件类型（给系统的文件选择器看） */
    val MIME_TYPES = arrayOf(
        "image/*",
        "application/pdf",
        "text/*",
        "application/json",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "附件"
    }

    /**
     * 读一个 uri，返回要附加的内容。
     * 失败时抛出带人话的异常，由调用方 toast 出来。
     */
    fun load(ctx: Context, uri: Uri): List<Attachment> {
        val name = displayName(ctx, uri)
        val mime = ctx.contentResolver.getType(uri) ?: guessMime(name)

        return when {
            mime.startsWith("image/") -> listOfNotNull(image(ctx, uri, name))
            mime == "application/pdf" || name.endsWith(".pdf", true) -> pdf(ctx, uri, name)
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                name.endsWith(".docx", true) -> listOfNotNull(docx(ctx, uri, name))
            mime.startsWith("text/") || mime.contains("json") || isTextName(name) ->
                listOfNotNull(textFile(ctx, uri, name))
            else -> throw IllegalArgumentException("暂时读不了这种文件：$name")
        }
    }

    // ---------------------------------------------------------------- 图片

    private fun image(ctx: Context, uri: Uri, name: String): Attachment? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("打不开这张图")
        val bitmap = downscale(bytes) ?: throw IllegalStateException("这张图解不开")
        return Attachment(name = name, dataUrl = toDataUrl(bitmap))
    }

    /** 先只读尺寸算采样率，再按最长边缩到 MAX_IMAGE_EDGE */
    private fun downscale(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_EDGE * 2 || bounds.outHeight / sample > MAX_IMAGE_EDGE * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= MAX_IMAGE_EDGE) return decoded
        val scale = MAX_IMAGE_EDGE.toFloat() / longest
        val out = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (out !== decoded) decoded.recycle()
        return out
    }

    private fun toDataUrl(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    // ---------------------------------------------------------------- PDF

    /**
     * PDF 没有第三方库就没法抽文字，但可以渲染成图片 —— 现在的视觉模型看截图很准，
     * 配料单、检测报告这类扫描件反而比抽文字更还原。
     */
    private fun pdf(ctx: Context, uri: Uri, name: String): List<Attachment> {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("打不开这个 PDF")
        val out = ArrayList<Attachment>()
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Throwable) {
            runCatching { pfd.close() }
            throw IllegalStateException("这个 PDF 打不开（可能加密了）")
        }
        try {
            val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
            for (i in 0 until pages) {
                val page = renderer.openPage(i)
                try {
                    val scale = MAX_IMAGE_EDGE.toFloat() / maxOf(page.width, page.height)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // PDF 页面背景是透明的，不先铺白会渲染成一片黑
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.add(Attachment("$name 第${i + 1}页", toDataUrl(bmp)))
                } finally {
                    runCatching { page.close() }
                }
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        if (out.isEmpty()) throw IllegalStateException("这个 PDF 里没有可读的页面")
        return out
    }

    // ---------------------------------------------------------------- 文本类

    private fun textFile(ctx: Context, uri: Uri, name: String): Attachment? {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("打不开这个文件")
        if (raw.size > 4 * 1024 * 1024) throw IllegalStateException("文件太大了（超过 4MB）")
        val text = String(raw, Charsets.UTF_8)
        if (text.isBlank()) throw IllegalStateException("这个文件是空的")
        return Attachment(name = name, dataUrl = "", text = clamp(text))
    }

    /** docx 就是个 zip，正文在 word/document.xml 里，剥掉标签就够 AI 看了 */
    private fun docx(ctx: Context, uri: Uri, name: String): Attachment? {
        val stream = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("打不开这个文件")
        var xml: String? = null
        ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zip.nextEntry
            }
        }
        val body = xml ?: throw IllegalStateException("这个 docx 里没有正文")
        val text = body
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<w:tab[^>]*/>"), "\t")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (text.isBlank()) throw IllegalStateException("这个 docx 里没有可读的文字")
        return Attachment(name = name, dataUrl = "", text = clamp(text))
    }

    private fun clamp(text: String): String =
        if (text.length > MAX_TEXT) text.take(MAX_TEXT) + "\n…（后面还有 ${text.length - MAX_TEXT} 字，已截断）"
        else text

    private fun isTextName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("txt", "md", "csv", "json", "log", "xml", "yml", "yaml", "ini", "kt", "js", "py")
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }
}
