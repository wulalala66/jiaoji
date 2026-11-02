package com.wulalala66.jiaoji

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 常驻通知（锁屏也能看到）：显示最近一个计时还剩多久、是哪个烘箱、哪支样品。
 *
 * 三个作用：
 *  1. 前台服务优先级高，ColorOS 之类不会轻易把进程回收掉，进程内每秒的到点检查才能一直跑；
 *  2. 锁屏界面直接看到倒计时（用系统计时器渲染，不用自己每秒刷新，省电且不会跳）；
 *  3. 下拉状态栏就知道还有几个计时、下一个什么时候到。
 *
 * 设置里可以关掉（「状态栏常驻通知」）。
 */
class KeepAliveService : Service() {

    companion object {
        const val CHANNEL = "jiaoji.keepalive"
        const val ID = 7102

        @Volatile
        var running: Boolean = false
            private set

        fun intent(ctx: Context): Intent = Intent(ctx, KeepAliveService::class.java)

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val old = nm.getNotificationChannel(CHANNEL)
            if (old != null && old.importance >= NotificationManager.IMPORTANCE_DEFAULT) return
            if (old != null) nm.deleteNotificationChannel(CHANNEL)
            // 锁屏上能不能显示跟重要性有关：ColorOS 会把 LOW 优先级的通知从锁屏上藏掉，
            // 所以这里用 DEFAULT；声音还是关掉的（setSilent + 无声渠道）。
            val ch = NotificationChannel(CHANNEL, "倒计时（常驻）", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "锁屏和状态栏常驻一条倒计时，能看是哪个烘箱、还剩多久"
            ch.setShowBadge(false)
            ch.enableVibration(false)
            ch.setSound(null, null)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        running = true
        startSafely(build())
        handler.postDelayed(tick, 30_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        refresh()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        running = false
        super.onDestroy()
    }

    private fun startSafely(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(ID, n)
            }
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ 常驻通知起不来 ${e.javaClass.simpleName}")
            running = false
            stopSelf()
        }
    }

    private fun refresh() {
        val s = Repo.read(this)
        if (!KeepAlive.shouldRun(s)) {
            stopSelf()
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(ID, build())
        } catch (_: Throwable) {
        }
    }

    private fun build(): Notification {
        val s = Repo.read(this)
        val now = System.currentTimeMillis()
        val bakes = s.bakes.filter { it.status != "done" }.sortedBy { it.endAt }
        val tasks = s.sampling.filter { it.status == "pending" }.sortedBy { it.dueAt }

        val bake = bakes.firstOrNull { it.endAt > now }
        val overdue = bakes.firstOrNull { it.endAt <= now }
        val task = tasks.firstOrNull { it.dueAt > now }

        val title: String
        val text: String
        var whenAt = 0L
        var countdown = false

        when {
            bake != null -> {
                title = "${s.ovenName(bake.oven)} · ${bake.label.ifBlank { "样品" }}"
                val extra = bakes.size - 1
                text = buildString {
                    append("${bake.tempC}℃ ${bake.minutes} 分")
                    append(" · ${Fmt.hm(bake.endAt)} 出炉")
                    if (extra > 0) append(" · 另有 $extra 个计时")
                }
                whenAt = bake.endAt
                countdown = true
            }

            overdue != null -> {
                title = "${s.ovenName(overdue.oven)} · 已到点"
                text = "${overdue.label.ifBlank { "样品" }} 该取出来了 · ${Fmt.hm(overdue.endAt)} 到点"
            }

            task != null -> {
                title = "取样 · ${task.customer}"
                val typeName = s.types.firstOrNull { it.id == task.typeId }?.name ?: ""
                text = buildString {
                    append("${Fmt.hm(task.dueAt)} 去取样")
                    if (typeName.isNotBlank()) append(" · $typeName")
                    val extra = tasks.size - 1
                    if (extra > 0) append(" · 另有 $extra 个")
                }
                whenAt = task.dueAt
                countdown = true
            }

            else -> {
                title = "胶记 · 空闲"
                text = "没有在跑的计时，到点会响铃"
            }
        }

        val open = PendingIntent.getActivity(
            this, 7711,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_TAB, if (bakes.isNotEmpty() || overdue != null) "ovens" else "today")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (countdown && whenAt > now) {
            // 交给系统自己走秒：锁屏上也能实时看到 12:34 → 12:33，不用 App 每秒刷新
            b.setUsesChronometer(true)
            b.setChronometerCountDown(true)
            b.setWhen(whenAt)
            b.setShowWhen(true)
        } else {
            b.setShowWhen(false)
        }
        return b.build()
    }
}

object KeepAlive {

    /** 常驻就真的常驻：只要设置里开着就挂一条（没任务时显示「空闲」） */
    fun shouldRun(s: AppState): Boolean = s.settings.keepAlive

    fun sync(ctx: Context, s: AppState) {
        val want = shouldRun(s)
        if (want && !KeepAliveService.running) {
            try {
                KeepAliveService.ensureChannel(ctx)
                ContextCompat.startForegroundService(ctx, KeepAliveService.intent(ctx))
            } catch (e: Throwable) {
                AlarmLog.add(ctx, "✗ 常驻通知启动失败 ${e.javaClass.simpleName}")
            }
        } else if (!want && KeepAliveService.running) {
            try {
                ctx.stopService(KeepAliveService.intent(ctx))
            } catch (_: Throwable) {
            }
        }
    }
}
