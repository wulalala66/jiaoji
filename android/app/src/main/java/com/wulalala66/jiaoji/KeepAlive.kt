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
 * 常驻通知：只要还有计时 / 待取样任务，就在状态栏挂一条「胶记在守时」。
 *
 * 两个作用：
 *  1. 前台服务优先级高，ColorOS 之类不会轻易把进程回收掉，进程内每秒的到点检查才能一直跑；
 *  2. 用户下拉状态栏就能看到还有几个计时、下一个什么时候到。
 *
 * 没有任务时会自己停掉，不会一直占着通知栏。
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
            if (nm.getNotificationChannel(CHANNEL) != null) return
            val ch = NotificationChannel(CHANNEL, "守时（常驻）", NotificationManager.IMPORTANCE_LOW)
            ch.description = "还有计时或待取样任务时显示，方便看进度，也让系统别把胶记回收掉"
            ch.setShowBadge(false)
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
        val bakes = s.bakes.filter { it.status != "done" && it.endAt > now }.sortedBy { it.endAt }
        val tasks = s.sampling.filter { it.status == "pending" && it.dueAt > now }.sortedBy { it.dueAt }

        val title = when {
            bakes.isNotEmpty() && tasks.isNotEmpty() -> "${bakes.size} 个计时 · ${tasks.size} 个取样"
            bakes.isNotEmpty() -> "${bakes.size} 个烘箱计时在跑"
            tasks.isNotEmpty() -> "${tasks.size} 个取样提醒"
            else -> "胶记在守时"
        }

        val next = listOfNotNull(
            bakes.firstOrNull()?.let { "最近 ${Fmt.hm(it.endAt)} 出炉" },
            tasks.firstOrNull()?.let { "下次取样 ${Fmt.hm(it.dueAt)}" },
        ).joinToString(" · ")

        val open = PendingIntent.getActivity(
            this, 7711,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(next.ifEmpty { "到点会响铃，别关掉" })
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

object KeepAlive {

    /** 有在跑的计时或今天还没到点的取样，就值得挂常驻通知 */
    fun shouldRun(s: AppState): Boolean {
        if (!s.settings.keepAlive) return false
        val now = System.currentTimeMillis()
        val bake = s.bakes.any { it.status != "done" && it.endAt > now }
        val task = s.sampling.any { it.status == "pending" && it.dueAt > now }
        return bake || task
    }

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
