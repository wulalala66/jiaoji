package com.gluelab.jiaoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 拍照识别手写的 M1~M6。
 *
 * 手写数字识别不可能 100% 准，所以这里只做一件事：把最像「称量值」的 4 或 6 个数
 * 按阅读顺序抠出来。填进去之后让用户自己核对、改错 —— 不追求一步到位。
 */
object DigitOcr {

    /** 识别一张图，返回按顺序排列的称量值（最多 6 个） */
    suspend fun read(ctx: Context, uri: Uri): List<Double> {
        val bitmap = decode(ctx, uri) ?: throw IllegalStateException("这张图解不开")
        val text = recognize(bitmap)
        return parse(text)
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val full = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // 只取画面中间那条横带：用户被要求把数字写满取景框，
        // 上下边缘一般是桌面、纸张边、手 —— 这些噪声对识别的影响比手写本身大。
        val bandTop = (full.height * 0.28f).toInt().coerceIn(0, full.height - 1)
        val bandH = (full.height * 0.44f).toInt().coerceIn(1, full.height - bandTop)
        val cropped = Bitmap.createBitmap(full, 0, bandTop, full.width, bandH)
        if (cropped !== full) full.recycle()
        return cropped
    }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                // 按行拼起来，保持从左到右的顺序
                val sb = StringBuilder()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        sb.append(line.text).append(' ')
                    }
                }
                cont.resume(sb.toString())
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** 从一堆文本里挑出像称量值的数字 */
    fun parse(raw: String): List<Double> {
        // 常见的手写误识别：O/o → 0，l/I/| → 1，全角数字/句号
        val cleaned = raw
            .replace(Regex("[OoＯ〇]"), "0")
            .replace(Regex("[lI|｜]"), "1")
            .replace('，', '.').replace('。', '.').replace('．', '.')
            .replace(Regex("[０-９]")) { (it.value[0].code - '０'.code + '0'.code).toChar().toString() }

        val out = ArrayList<Double>()
        // 先要 x.xx 这种带小数点的，其次才是整数（手写的点经常丢）
        val re = Regex("""(\d{1,3})\s*[.,]\s*(\d{1,2})""")
        re.findAll(cleaned).forEach { m ->
            val v = (m.groupValues[1] + "." + m.groupValues[2]).toDoubleOrNull() ?: return@forEach
            if (v > 0.05 && v < 500) out.add(v)
        }
        if (out.size < 4) {
            // 小数点丢了：连着的一串数字按 3 位一段切（x.xx 最常见的写法）
            Regex("""\d{3,4}""").findAll(cleaned).forEach { m ->
                val s = m.value
                val v = when (s.length) {
                    3 -> (s[0].toString() + "." + s.substring(1)).toDoubleOrNull()
                    4 -> (s.substring(0, 2) + "." + s.substring(2)).toDoubleOrNull()
                    else -> null
                }
                if (v != null && v > 0.05 && v < 500) out.add(v)
            }
        }
        return out.take(6)
    }
}
