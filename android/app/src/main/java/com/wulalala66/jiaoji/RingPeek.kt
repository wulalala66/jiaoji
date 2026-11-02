package com.wulalala66.jiaoji

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 一个看不见的 1×1 悬浮窗。
 *
 * 为什么需要它：Android 12 起，后台启动 Activity 的豁免里不再只看「有没有悬浮窗权限」，
 * 而是要求进程**此刻确实有一个可见窗口**。ColorOS 16 对后台启动卡得很死，
 * 于是「闹钟到点自动弹响铃页」经常被静默拦掉——只有声音，屏幕不亮。
 *
 * 到点前后挂一个透明小窗（不接收触摸、不抢焦点、8 秒自动撤），
 * 进程就满足了「有可见窗口」这一条，startActivity 才不会被拦。
 * 注意：它只是窗口，不是界面；响铃页仍然由 RingActivity 显示。
 */
object RingPeek {

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: View? = null
    private val remove = Runnable { hide() }

    fun canOverlay(ctx: Context): Boolean = try {
        Settings.canDrawOverlays(ctx)
    } catch (_: Throwable) {
        false
    }

    private fun type(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /** 挂上透明窗口，8 秒后自动撤 */
    fun show(ctx: Context) {
        if (!canOverlay(ctx)) return
        if (view != null) {
            main.removeCallbacks(remove)
            main.postDelayed(remove, 8_000L)
            return
        }
        try {
            val w = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(ctx)
            val lp = WindowManager.LayoutParams(
                1, 1,
                type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 0
            lp.y = 0
            w.addView(v, lp)
            wm = w
            view = v
            AlarmLog.add(ctx, "挂透明窗口（拿后台启动豁免）")
            main.removeCallbacks(remove)
            main.postDelayed(remove, 8_000L)
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ 透明窗口失败 ${e.javaClass.simpleName}: ${e.message}")
            hide()
        }
    }

    fun hide() {
        main.removeCallbacks(remove)
        val v = view ?: return
        view = null
        try {
            wm?.removeView(v)
        } catch (_: Throwable) {
        }
        wm = null
    }
}
