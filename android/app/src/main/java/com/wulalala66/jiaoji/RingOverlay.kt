package com.wulalala66.jiaoji

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮窗响铃页。
 *
 * 为什么需要它：ColorOS / MIUI / vivo 的「后台弹出界面」没有任何 API 能申请，
 * 也没法在代码里替用户授权。但 Android 14 起「应用此刻有可见窗口」是后台启动
 * Activity 的合法豁免之一，而悬浮窗（SYSTEM_ALERT_WINDOW）本身就是一块可见窗口。
 * 所以走两条路：
 *
 *  1. [peek] 在拉起 Activity 之前先挂一个 1×1 的透明悬浮窗，让本进程"有可见窗口"，
 *     这样 startActivity 就不会被 ROM 静默掐掉；
 *  2. 如果 Activity 还是没起来（[RingActivity.alive] 为 false），[show] 直接把整个
 *     响铃界面画在悬浮窗上——完全不经过 Activity 和后台启动限制。
 *
 * 用户只要给过悬浮窗权限，黑屏时也能看到响铃页。
 */
object RingOverlay {

    private val main = Handler(Looper.getMainLooper())
    private var full: View? = null
    private var wm: WindowManager? = null
    private var peek: View? = null

    fun isShown(): Boolean = full != null

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    fun canOverlay(ctx: Context): Boolean = try {
        Settings.canDrawOverlays(ctx)
    } catch (_: Throwable) {
        false
    }

    /** 1×1 透明窗口：只为拿到「有可见窗口」的豁免，6 秒后自己撤掉 */
    fun peek(ctx: Context) {
        if (!canOverlay(ctx) || peek != null) return
        try {
            val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(ctx)
            val lp = WindowManager.LayoutParams(
                1, 1,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            manager.addView(v, lp)
            peek = v
            wm = manager
            AlarmLog.add(ctx, "挂一个透明窗口拿后台启动豁免")
            main.postDelayed({
                try {
                    if (peek === v) {
                        manager.removeView(v)
                        peek = null
                    }
                } catch (_: Throwable) {
                }
            }, 6_000L)
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ 透明窗口失败 ${e.javaClass.simpleName}")
        }
    }

    fun unpeek() {
        val v = peek ?: return
        try {
            wm?.removeView(v)
        } catch (_: Throwable) {
        }
        peek = null
    }

    /** 把整个响铃界面画成悬浮窗（Activity 起不来时的兜底） */
    fun show(ctx: Context, key: String, title: String, body: String, kind: String) {
        if (full != null) return
        if (!canOverlay(ctx)) {
            AlarmLog.add(ctx, "✗ 没有悬浮窗权限，兜底响铃页也弹不出来")
            return
        }
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F4F6F3"))
            setPadding(px(28), px(40), px(28), px(40))
        }
        root.addView(
            TextView(ctx).apply {
                text = title
                textSize = 26f
                setTextColor(Color.parseColor("#16211E"))
                gravity = Gravity.CENTER
            },
        )
        root.addView(
            TextView(ctx).apply {
                text = body
                textSize = 15f
                setTextColor(Color.parseColor("#5B6663"))
                gravity = Gravity.CENTER
                setPadding(0, px(14), 0, px(28))
            },
        )

        val isPerm = kind == "perm"
        val primary = Button(ctx).apply {
            text = if (isPerm) "好，我知道了" else "已取出 / 已处理"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0E7263"))
            setOnClickListener {
                if (!isPerm) RingActions.done(ctx, key)
                hide()
            }
        }
        root.addView(
            primary,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(58)),
        )

        if (!isPerm) {
            root.addView(
                Button(ctx).apply {
                    text = "延后 2 分钟"
                    textSize = 16f
                    setOnClickListener {
                        RingActions.snooze(ctx, key, 2)
                        hide()
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(52)).apply {
                    topMargin = px(10)
                },
            )
            root.addView(
                Button(ctx).apply {
                    text = "只关掉声音"
                    textSize = 14f
                    setOnClickListener {
                        RingActions.stopRinging(ctx)
                        hide()
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(44)).apply {
                    topMargin = px(6)
                },
            )
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 盖住锁屏 + 状态栏：不设这两个，部分 ROM 会把窗口裁到状态栏下面，看起来像「没弹」
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            manager.addView(root, lp)
            full = root
            wm = manager
            AlarmLog.add(ctx, "悬浮窗响铃页已显示 $key")
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ 悬浮窗响铃页失败 ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun hide() {
        val v = full ?: return
        try {
            wm?.removeView(v)
        } catch (_: Throwable) {
        }
        full = null
    }
}
