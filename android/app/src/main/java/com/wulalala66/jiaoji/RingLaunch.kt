package com.wulalala66.jiaoji

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 把响铃界面弄到屏幕上——这件事在国产 ROM 上要靠「多条路一起试」：
 *
 *  1. PendingIntent + MODE_BACKGROUND_ACTIVITY_START_ALLOWED（安卓 14 起官方给的正规豁免）
 *  2. 直接 startActivity：系统文档写明「已授予 SYSTEM_ALERT_WINDOW 的应用可以后台启动 Activity」，
 *     而黑屏能出全屏页恰恰就依赖这个悬浮窗权限
 *  3. 都没起来就隔一会儿再试，一共试三轮
 *  4. 三轮都不行，把同样的界面画成悬浮窗（悬浮窗不经过 Activity 启动限制）
 *
 * 每一步都写进日志（自检页 → 日志），出问题能直接看出是卡在哪一步。
 */
object RingLaunch {

    private val handler = Handler(Looper.getMainLooper())

    fun fire(ctx: Context, key: String, title: String, body: String, kind: String) {
        val app = ctx.applicationContext
        handler.post { attempt(app, key, title, body, kind, 0) }
    }

    private fun attempt(ctx: Context, key: String, title: String, body: String, kind: String, round: Int) {
        if (RingActivity.alive) {
            AlarmLog.add(ctx, "响铃页已在前台，不用再拉（第${round + 1}轮）")
            return
        }

        // 1) 官方豁免：PendingIntent 带着「允许后台启动 Activity」的选项
        try {
            RingNotif.launchRingActivity(ctx, key, title, body, kind)
            AlarmLog.add(ctx, "拉响铃页①PendingIntent 第${round + 1}轮")
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ ①PendingIntent 失败 ${e.javaClass.simpleName}: ${e.message}")
        }

        // 2) 直接启动：有悬浮窗权限时系统允许
        try {
            ctx.startActivity(RingActivity.intent(ctx, key, title, body, kind))
            AlarmLog.add(ctx, "拉响铃页②startActivity 第${round + 1}轮")
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ ②startActivity 失败 ${e.javaClass.simpleName}: ${e.message}")
        }

        val nextDelay = when (round) {
            0 -> 1_500L
            1 -> 3_000L
            else -> 0L
        }
        if (nextDelay > 0) {
            handler.postDelayed({ attempt(ctx, key, title, body, kind, round + 1) }, nextDelay)
        } else {
            handler.postDelayed({
                if (!RingActivity.alive) {
                    AlarmLog.add(ctx, "响铃页三轮都没起来，改用悬浮窗")
                    RingOverlay.show(ctx, key, title, body, kind)
                }
            }, 2_000L)
        }
    }
}
