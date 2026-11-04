package com.gluelab.jiaoji

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地滚动日志：崩溃信息、AI 接口错误、导出失败这类「用户看不懂但作者需要」的东西。
 * 只存在本机 SharedPreferences，最多留 [MAX] 条，用户可以在诊断页一键复制。
 */
object AppLog {

    private const val PREF = "jiaoji_log"
    private const val KEY = "lines"
    private const val MAX = 120

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun add(ctx: Context, msg: String) {
        val prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY, "") ?: ""
        val line = "${stamp.format(Date())}  $msg"
        val lines = (old.split('\n').filter { it.isNotBlank() } + line).takeLast(MAX)
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun all(ctx: Context): List<String> =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
