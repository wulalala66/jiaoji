package com.wulalala66.jiaoji

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// ---------------- 调度 ----------------

private data class AlarmItem(val at: Long, val title: String, val body: String, val kind: String)

/**
 * 把「待办」翻译成系统闹钟：每个烘烤/取样各有一个提前提醒 + 一个到点闹钟。
 * 系统级 setExactAndAllowWhileIdle：锁屏、后台、App 被杀都能响。
 */
object AlarmScheduler {

    const val ACTION_ALARM = "com.wulalala66.jiaoji.ALARM"
    private const val PREF = "jiaoji_alarms"
    private const val EXTRA_PREFIX = "jiaoji://alarm/"

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun sync(ctx: Context, s: AppState) {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = prefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet()
        val now = System.currentTimeMillis()
        val desired = LinkedHashMap<String, AlarmItem>()

        s.bakes.forEach { b ->
            if (b.status == "done") return@forEach
            val pre = b.preAlert ?: s.settings.preAlertMinutes
            if (pre > 0) {
                val at = b.endAt - pre * 60_000L
                if (at > now) desired["bp:${b.id}"] =
                    AlarmItem(at, "还有 $pre 分钟出炉", "${b.label} · ${b.tempC}℃ · ${ovenName(b.oven)}", "pre")
            }
            if (b.endAt > now) desired["b:${b.id}"] =
                AlarmItem(b.endAt, "烘烤完成 · 该出炉了", "${b.label} · ${b.tempC}℃ ${b.minutes}分钟 · ${ovenName(b.oven)}", "due")
        }

        s.sampling.forEach { t ->
            if (t.status != "pending") return@forEach
            val pre = t.preAlertMinutes ?: s.settings.preAlertMinutes
            if (pre > 0) {
                val at = t.dueAt - pre * 60_000L
                if (at > now) desired["sp:${t.id}"] =
                    AlarmItem(at, "快到取样时间", "${Fmt.hm(t.dueAt)} ${t.customer} · ${s.typeName(t.typeId)}", "pre")
            }
            if (t.dueAt > now && !t.dueAlarmed) desired["s:${t.id}"] =
                AlarmItem(t.dueAt, "取样时间到", "${t.customer} · ${s.typeName(t.typeId)}", "due")
        }

        old.forEach { key -> if (!desired.containsKey(key)) cancel(ctx, key) }
        desired.forEach { (key, item) -> schedule(ctx, key, item) }
        prefs.edit().putStringSet("ids", desired.keys.toMutableSet()).apply()
    }

    private fun schedule(ctx: Context, key: String, item: AlarmItem) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = Uri.parse(EXTRA_PREFIX + key)
            putExtra("key", key)
            putExtra("title", item.title)
            putExtra("body", item.body)
            putExtra("kind", item.kind)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, key.hashCode(), intent, flags)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        }
    }

    fun cancel(ctx: Context, key: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = Uri.parse(EXTRA_PREFIX + key)
        }
        val pi = PendingIntent.getBroadcast(ctx, key.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }
}

// ---------------- 通知 / 铃声 ----------------

object RingNotif {
    const val CHANNEL = "jiaoji.alarm"
    const val ID = 7101
    const val ACTION_DONE = "com.wulalala66.jiaoji.RING_DONE"
    const val ACTION_SNOOZE = "com.wulalala66.jiaoji.RING_SNOOZE"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "闹钟提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "烘烤出炉 / 取样时间提醒"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun build(ctx: Context, key: String, title: String, body: String, kind: String): Notification {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(action: String, label: String): NotificationCompat.Action {
            val i = Intent(ctx, RingActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("jiaoji://ring/$action/$key")
                putExtra("key", key)
            }
            val p = PendingIntent.getBroadcast(
                ctx, (action + key).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(0, label, p)
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
        if (kind == "due") {
            b.addAction(action(ACTION_DONE, "已取出"))
            b.addAction(action(ACTION_SNOOZE, "延后 2 分钟"))
        } else {
            b.addAction(action(ACTION_DONE, "知道了"))
        }
        return b.build()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val title = intent.getStringExtra("title") ?: "提醒"
        val body = intent.getStringExtra("body") ?: ""
        val kind = intent.getStringExtra("kind") ?: "due"
        RingNotif.ensureChannel(context)
        val svc = Intent(context, RingService::class.java).apply {
            putExtra("key", key)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("kind", kind)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc) else context.startService(svc)
        } catch (e: Exception) {
            // 极端情况下（系统限制前台服务）退化为普通通知
            try {
                NotificationManagerCompat.from(context).notify(RingNotif.ID, RingNotif.build(context, key, title, body, kind))
            } catch (ignored: SecurityException) {
            }
        }
    }
}

class RingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val key = intent?.getStringExtra("key") ?: ""
        val title = intent?.getStringExtra("title") ?: "提醒"
        val body = intent?.getStringExtra("body") ?: ""
        val kind = intent?.getStringExtra("kind") ?: "due"
        val s = Repo.read(this)
        startForeground(RingNotif.ID, RingNotif.build(this, key, title, body, kind))

        if (kind == "due" && s.settings.sound) startSound(RingtoneManager.TYPE_ALARM, true)
        else if (kind != "due" && s.settings.sound) startSound(RingtoneManager.TYPE_NOTIFICATION, false)

        if (kind == "due" && s.settings.vibrate) startVibrate()
        return START_NOT_STICKY
    }

    private fun startSound(type: Int, loop: Boolean) {
        releasePlayer()
        try {
            val uri = RingtoneManager.getDefaultUri(type) ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RingService, uri)
                isLooping = loop
                prepare()
                start()
            }
        } catch (e: Exception) {
            releasePlayer()
        }
    }

    private fun startVibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 600, 400, 600, 900)
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (ignored: Exception) {
        }
        try {
            player?.release()
        } catch (ignored: Exception) {
        }
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (ignored: Exception) {
        }
        vibrator = null
        super.onDestroy()
    }
}

class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: ""
        val action = intent.action ?: ""
        context.stopService(Intent(context, RingService::class.java))
        try {
            NotificationManagerCompat.from(context).cancel(RingNotif.ID)
        } catch (ignored: Exception) {
        }
        when (action) {
            RingNotif.ACTION_DONE -> Repo.editStatic(context) { s -> applyDone(s, key) }
            RingNotif.ACTION_SNOOZE -> Repo.editStatic(context) { s -> applySnooze(s, key) }
        }
    }

    private fun applyDone(s: AppState, key: String) {
        val (kind, id) = split(key) ?: return
        when (kind) {
            "b" -> s.bakes.firstOrNull { it.id == id }?.apply {
                status = "done"
                doneAt = System.currentTimeMillis()
            }
            "s" -> s.sampling.firstOrNull { it.id == id }?.apply { dueAlarmed = true }
            else -> Unit
        }
    }

    private fun applySnooze(s: AppState, key: String) {
        val (kind, id) = split(key) ?: return
        when (kind) {
            "b" -> s.bakes.firstOrNull { it.id == id }?.apply {
                endAt += 2 * 60_000L
                minutes += 2
                status = "running"
            }
            "s" -> s.sampling.firstOrNull { it.id == id }?.apply {
                dueAt += 2 * 60_000L
                dueAlarmed = false
            }
            else -> Unit
        }
    }

    private fun split(key: String): Pair<String, String>? {
        val i = key.indexOf(':')
        if (i <= 0) return null
        return key.substring(0, i) to key.substring(i + 1)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = Repo.read(context)
        AlarmScheduler.sync(context, s)
    }
}
