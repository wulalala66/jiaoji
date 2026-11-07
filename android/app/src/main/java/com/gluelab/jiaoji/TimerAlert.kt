package com.gluelab.jiaoji

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * 烘烤到点的提醒。
 *
 * 故意不引前台服务和精确闹钟（那是 2.8.0 删掉的那套）：
 * 计时靠「结束时刻」算，App 在后台照样准；进程被系统杀了就没提醒，
 * 但下次打开 App 会立刻显示「已到点」。
 */
object TimerAlert {

    private const val CHANNEL = "jiaoji.timer"
    private const val ID = 7201

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "烘烤计时", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "烘烤时间到了的时候提醒你"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }

    /** 到点：振动 + 通知（没给通知权限就只振动） */
    fun fire(ctx: Context, title: String, body: String) {
        vibrate(ctx)
        runCatching {
            ensureChannel(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return@runCatching
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = open?.let {
                PendingIntent.getActivity(
                    ctx, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 400, 200, 400))
                .apply { pi?.let { setContentIntent(it) } }
                .build()
            nm.notify(ID, n)
        }
    }

    fun cancel(ctx: Context) {
        runCatching {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(ID)
        }
    }

    private fun vibrate(ctx: Context) {
        runCatching {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(VibratorManager::class.java) ?: return
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                @Suppress("DEPRECATION")
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
    }
}
