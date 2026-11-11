package com.gluelab.jiaoji

import android.content.Context
import android.net.Uri
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 在线识别（可选）。
 *
 * 接口：POST http://ocr.147760166.xyz/ocr，multipart 字段名 data，返回 {"data":{"code":"..."}}。
 * 实测它会吞掉小数点（3.21 返回 "321"），所以文本照样丢给 DigitOcr.parse
 * 走「3 位补点」那条兜底。
 *
 * 只有用户主动点「☁️ 在线识别」才会调用 —— 图片会发到外部服务器，不做默认行为。
 */
object RemoteOcr {

    private const val ENDPOINT = "http://ocr.147760166.xyz/ocr"

    /** 上传图片并取回文本；失败抛异常 */
    fun readText(ctx: Context, uri: Uri, timeoutMs: Int = 30_000): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("读不到这张图")
        if (bytes.size > 8 * 1024 * 1024) throw IllegalStateException("图片太大了")

        val boundary = "----jiaoji" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun line(s: String) = out.write(s.toByteArray())
                line("--$boundary\r\n")
                line("Content-Disposition: form-data; name=\"data\"; filename=\"photo.jpg\"\r\n")
                line("Content-Type: image/jpeg\r\n\r\n")
                out.write(bytes)
                line("\r\n--$boundary--\r\n")
                out.flush()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("接口返回 $code")
            return extractCode(body)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 从 {"status":0,"data":{"code":"..."}} 里抠出文字 */
    fun extractCode(json: String): String {
        val o = runCatching { org.json.JSONObject(json) }.getOrNull()
            ?: throw IllegalStateException("返回不是 JSON")
        if (o.optInt("status", -1) != 0) {
            throw IllegalStateException(o.optString("msg", "接口报错"))
        }
        return o.optJSONObject("data")?.optString("code", "") ?: ""
    }
}
