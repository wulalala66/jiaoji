package com.wulalala66.jiaoji

import android.app.ActivityOptions
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
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- 日志（自检页用） ----------------

object AlarmLog {
    private const val PREF = "jiaoji_log"
    private const val KEY = "lines"
    private const val MAX = 120

    fun add(ctx: Context, msg: String) {
        try {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            val lines = (p.getString(KEY, "") ?: "")
                .split("\n").filter { it.isNotBlank() }.toMutableList()
            lines.add("$stamp  $msg")
            while (lines.size > MAX) lines.removeAt(0)
            p.edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (_: Throwable) {
        }
    }

    fun all(ctx: Context): List<String> =
        (ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
            .split("\n").filter { it.isNotBlank() }.reversed()

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

// ---------------- 调度 ----------------

private data class AlarmItem(val at: Long, val title: String, val body: String, val kind: String)

/**
 * 把「待办」翻译成系统闹钟：每个烘烤/取样各有一个提前提醒 + 一个到点闹钟。
 *
 * 用 AlarmManager.setAlarmClock：这是系统里优先级最高的闹钟类型，Doze/省电模式下也准时，
 * 且不受「后台启动前台服务」限制（系统会认为这是用户可见的闹钟）。
 * 同时声明 USE_EXACT_ALARM，安装即拥有精确闹钟能力，无需用户手动授权。
 */
object AlarmScheduler {

    const val ACTION_ALARM = "com.wulalala66.jiaoji.ALARM"

    /**
     * 立刻排一个 10 秒后的「真闹钟」，走和真实到点完全一样的路径：
     * AlarmManager → AlarmReceiver → 前台服务（响铃）+ 全屏响铃页 + 通知。
     * 用来在设置页里当场申请/验证「锁屏显示」——很多 ROM 第一次把界面盖到锁屏上时
     * 会自己弹「允许在锁屏上显示」，点允许就长期生效了。
     */
    fun scheduleTest(ctx: Context, seconds: Int = 10, quiet: Boolean = false) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + seconds * 1000L
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = Uri.parse(if (quiet) "jiaoji://alarm/test:perm" else "jiaoji://alarm/test:lock")
            putExtra("key", if (quiet) "test:perm" else "test:lock")
            putExtra(
                "title",
                if (quiet) "申请锁屏 / 后台弹出界面" else "锁屏响铃测试",
            )
            putExtra(
                "body",
                if (quiet) "系统要是问你「允许在锁屏上显示」或「允许后台弹出界面」，点允许就行"
                else "看到这个页面就说明锁屏响铃没问题，点下面按钮关掉",
            )
            putExtra("kind", if (quiet) "perm" else "test")
        }
        val pi = PendingIntent.getBroadcast(
            ctx, if (quiet) 918_274 else 918_273, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
        AlarmLog.add(ctx, "排定${if (quiet) "权限申请" else "锁屏响铃测试"}：${seconds}s 后")
    }
    private const val PREF = "jiaoji_alarms"
    private const val EXTRA_PREFIX = "jiaoji://alarm/"
    private const val SHOW_OFFSET = 900_000

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } catch (_: Throwable) {
            false
        }
    }

    /** 自检页：当前已排定的闹钟（key -> 时间戳） */
    fun pendingMap(ctx: Context): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        try {
            val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("map", "{}") ?: "{}"
            val o = JSONObject(raw)
            o.keys().forEach { k -> out[k] = o.optLong(k, 0L) }
        } catch (_: Throwable) {
        }
        return out
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
                if (at > now) desired["bp:${b.id}"] = AlarmItem(
                    at, "还有 $pre 分钟出炉",
                    "${b.label} · ${b.tempC}℃ · ${s.ovenName(b.oven)}", "pre"
                )
            }
            if (b.endAt > now) desired["b:${b.id}"] = AlarmItem(
                b.endAt, "烘烤完成 · 该出炉了",
                "${b.label} · ${b.tempC}℃ ${b.minutes}分钟 · ${s.ovenName(b.oven)}", "due"
            )
        }

        s.sampling.forEach { t ->
            if (t.status != "pending") return@forEach
            val pre = t.preAlertMinutes ?: s.settings.preAlertMinutes
            if (pre > 0) {
                val at = t.dueAt - pre * 60_000L
                if (at > now) desired["sp:${t.id}"] = AlarmItem(
                    at, "快到取样时间",
                    "${Fmt.hm(t.dueAt)} ${t.customer} · ${s.typeName(t.typeId)}", "pre"
                )
            }
            if (t.dueAt > now && !t.dueAlarmed) desired["s:${t.id}"] = AlarmItem(
                t.dueAt, "取样时间到",
                "${t.customer} · ${s.typeName(t.typeId)}", "due"
            )
        }

        val prev = LinkedHashMap<String, Long>()
        try {
            val raw = prefs.getString("map", "{}") ?: "{}"
            val o = JSONObject(raw)
            o.keys().forEach { k -> prev[k] = o.optLong(k, 0L) }
        } catch (_: Throwable) {
        }

        old.forEach { key -> if (!desired.containsKey(key)) cancel(ctx, key) }
        // 时间没变就不重复排定，日志也干净
        desired.forEach { (key, item) ->
            if (prev[key] != item.at) schedule(ctx, key, item)
        }

        val map = JSONObject()
        desired.forEach { (k, v) -> map.put(k, v.at) }
        prefs.edit()
            .putStringSet("ids", desired.keys.toMutableSet())
            .putString("map", map.toString())
            .apply()
    }

    private fun schedule(ctx: Context, key: String, item: AlarmItem) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmPendingIntent(ctx, key, item.title, item.body, item.kind, PendingIntent.FLAG_UPDATE_CURRENT)
        val showPi = PendingIntent.getActivity(
            ctx, key.hashCode() + SHOW_OFFSET,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_TAB, "ovens")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 一律先按「闹钟」排：这是系统里最高优先级、Doze 也准时，
        // 并且能让系统认可「这是闹钟」从而允许后台拉起响铃界面。
        // 只有在系统明确拒绝（极少数 ROM 没实现 USE_EXACT_ALARM）时才退化。
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(item.at, showPi), pi)
            AlarmLog.add(ctx, "排定 $key → ${Fmt.stamp(item.at)}（系统闹钟）")
        } catch (e: SecurityException) {
            AlarmLog.add(ctx, "⚠ 闹钟权限被拒，改用非精确闹钟 $key")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            } catch (_: Throwable) {
            }
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ 排定失败 $key ${e.javaClass.simpleName}: ${e.message}")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            } catch (_: Throwable) {
            }
        }
    }

    fun cancel(ctx: Context, key: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmPendingIntent(ctx, key, "", "", "due", PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            am.cancel(pi)
            pi.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun alarmPendingIntent(
        ctx: Context, key: String, title: String, body: String, kind: String, flags: Int
    ): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = Uri.parse(EXTRA_PREFIX + key)
            putExtra("key", key)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("kind", kind)
        }
        return PendingIntent.getBroadcast(
            ctx, key.hashCode(), intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

// ---------------- 通知 / 全屏响铃 ----------------

object RingNotif {
    const val CHANNEL = "jiaoji.alarm"
    const val ID = 7101
    const val ACTION_DONE = "com.wulalala66.jiaoji.RING_DONE"
    const val ACTION_SNOOZE = "com.wulalala66.jiaoji.RING_SNOOZE"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val old = nm.getNotificationChannel(CHANNEL)
        if (old != null && old.importance >= NotificationManager.IMPORTANCE_HIGH) return
        if (old != null) nm.deleteNotificationChannel(CHANNEL)
        val ch = NotificationChannel(CHANNEL, "烘烤 / 取样提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "到点响铃与全屏提醒"
            setSound(null, null)          // 声音由 RingService 自己播，避免两套声音
            enableVibration(false)        // 振动同理
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun build(ctx: Context, key: String, title: String, body: String, kind: String): Notification {
        ensureChannel(ctx)
        val ring = RingActivity.intent(ctx, key, title, body, kind)
        val open = PendingIntent.getActivity(
            ctx, 1, ring,
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
        // 只有「到点」才全屏接管；提前提醒是横幅通知 + 提示音，别打断手上的活
        if (kind == "due" || kind == "test") {
            b.setFullScreenIntent(open, true)
            b.addAction(action(ACTION_DONE, "已取出"))
            b.addAction(action(ACTION_SNOOZE, "延后 2 分钟"))
        } else {
            b.addAction(action(ACTION_DONE, "知道了"))
        }
        return b.build()
    }

    /**
     * 后台直接拉起全屏响铃界面。
     * Android 14 起后台启动 Activity 默认被拦，必须通过 PendingIntent + 显式
     * 声明「允许后台启动」，这也是系统闹钟类 App 的标准做法。
     */
    fun launchRingActivity(ctx: Context, key: String, title: String, body: String, kind: String) {
        val intent = RingActivity.intent(ctx, key, title, body, kind)
        val opts = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        } else {
            null
        }
        val flags = if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        try {
            val pi = PendingIntent.getActivity(ctx, key.hashCode() + 500_000, intent, flags, opts)
            if (Build.VERSION.SDK_INT >= 34) {
                pi.send(ctx, 0, null, null, null, null, opts)
            } else {
                pi.send()
            }
            AlarmLog.add(ctx, "拉起响铃界面 $key")
        } catch (e: Throwable) {
            AlarmLog.add(ctx, "✗ 拉起界面失败 ${e.javaClass.simpleName}: ${e.message}")
            try {
                ctx.startActivity(intent)
            } catch (e2: Throwable) {
                AlarmLog.add(ctx, "✗ startActivity 兜底失败 ${e2.javaClass.simpleName}")
            }
        }
    }
}

// ---------------- 到点处理 ----------------

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val title = intent.getStringExtra("title") ?: "提醒"
        val body = intent.getStringExtra("body") ?: ""
        val kind = intent.getStringExtra("kind") ?: "due"
        AlarmLog.add(context, "⏰ 闹钟触发 $key ($kind)")
        RingNotif.ensureChannel(context)

        // 1) 前台服务：即使界面拉不起来也保证有声音 + 振动（权限申请是静音的，不起服务）
        if (kind != "perm") {
            try {
                ContextCompat.startForegroundService(
                    context, RingService.intent(context, key, title, body, kind)
                )
                AlarmLog.add(context, "前台服务已请求")
            } catch (e: Throwable) {
                AlarmLog.add(context, "✗ 服务启动失败 ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // 2) 全屏响铃界面（锁屏也能亮屏）——到点 / 测试 / 权限申请才拉起，提前提醒只弹通知
        if (kind == "due" || kind == "test" || kind == "perm") {
            RingLaunch.fire(context, key, title, body, kind)
        }

        // 3) 通知兜底（用户可能把界面划掉，通知栏里还能操作）
        try {
            NotificationManagerCompat.from(context)
                .notify(RingNotif.ID, RingNotif.build(context, key, title, body, kind))
        } catch (e: Throwable) {
            AlarmLog.add(context, "✗ 通知失败 ${e.javaClass.simpleName}")
        }
    }
}

class RingService : Service() {

    companion object {
        const val ACTION_START = "com.wulalala66.jiaoji.RING_START"

        fun intent(ctx: Context, key: String, title: String, body: String, kind: String, autoStopMs: Int = 0): Intent =
            Intent(ctx, RingService::class.java).apply {
                action = ACTION_START
                putExtra("key", key)
                putExtra("title", title)
                putExtra("body", body)
                putExtra("kind", kind)
                putExtra("autoStopMs", autoStopMs)
            }

        /** 当前正在响的 key：进程内检查和 AlarmManager 可能同时触发，避免双重响铃 */
        @Volatile
        var ringingKey: String? = null
            private set
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var screenWake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val key = intent.getStringExtra("key") ?: ""
        val title = intent.getStringExtra("title") ?: "提醒"
        val body = intent.getStringExtra("body") ?: ""
        val kind = intent.getStringExtra("kind") ?: "due"
        val autoStop = intent.getIntExtra("autoStopMs", 0)

        try {
            ServiceCompat.startForeground(
                this, RingNotif.ID, RingNotif.build(this, key, title, body, kind),
                if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
            AlarmLog.add(this, "服务前台化成功 $key")
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ startForeground 失败 ${e.javaClass.simpleName}: ${e.message}")
        }

        if (ringingKey == key && player != null) {
            AlarmLog.add(this, "已经在响，忽略重复请求 $key")
            return START_NOT_STICKY
        }
        ringingKey = key

        acquireWake()
        // 界面由 AlarmReceiver 里的 PendingIntent 拉起（2.2.1 起就是这么做的，实测最稳）。
        // 这里只负责声音和振动：服务多拉一次 Activity 反而会把已经在起的响铃页顶掉。
        val s = Repo.read(this)
        val testing = kind == "test"
        if (s.settings.sound || testing) {
            startSound(if (kind == "due" || testing) "alarm" else "chime")
        } else {
            AlarmLog.add(this, "声音开关是关的，跳过铃声")
        }
        if ((kind == "due" || testing) && (s.settings.vibrate || testing)) startVibrate()

        // 提前提醒响两声就停（20 秒），到点铃声一直响到用户确认
        val stopAfter = when {
            autoStop > 0 -> autoStop.toLong()
            kind == "pre" -> 20_000L
            key.startsWith("test") -> 15_000L
            else -> 0L
        }
        if (stopAfter > 0) {
            handler.postDelayed({ stopSelf() }, stopAfter)
        }
        return START_NOT_STICKY
    }

    private fun acquireWake() {
        try {
            if (wake?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jiaoji:ring").apply {
                setReferenceCounted(false)
                acquire(10 * 60_000L)
            }
            AlarmLog.add(this, "持有唤醒锁")
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ 唤醒锁失败 ${e.javaClass.simpleName}")
        }
    }

    /** 把屏幕点着（黑屏时纯 PARTIAL_WAKE_LOCK 只保 CPU，屏幕还是黑的） */
    @Suppress("DEPRECATION")
    private fun acquireScreenWake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (screenWake == null) {
                screenWake = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "jiaoji:screen",
                ).apply { setReferenceCounted(false) }
            }
            if (screenWake?.isHeld != true) {
                screenWake?.acquire(90_000L)
                AlarmLog.add(this, "点亮屏幕")
            }
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ 点亮屏幕失败 ${e.javaClass.simpleName}")
        }
    }

    private fun releaseScreenWake() {
        try {
            if (screenWake?.isHeld == true) screenWake?.release()
        } catch (_: Throwable) {
        }
        screenWake = null
    }

    /** mode = "alarm"（到点）| "chime"（提前提醒） */
    private fun startSound(mode: String) {
        releasePlayer()
        val chime = mode == "chime"
        val candidates = ArrayList<Uri>()
        fun safeAdd(uri: Uri?) {
            if (uri != null && !candidates.contains(uri)) candidates.add(uri)
        }
        if (chime) {
            // 提前提醒：只用 App 自带的提示音，不再借用系统通知音（各家 ROM 的通知音又短又杂）
            safeAdd(Uri.parse("android.resource://$packageName/${R.raw.chime}"))
        } else {
            try {
                safeAdd(RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM))
                safeAdd(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                safeAdd(RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE))
                safeAdd(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            } catch (e: Throwable) {
                AlarmLog.add(this, "读取系统铃声失败 ${e.javaClass.simpleName}")
            }
            // 兜底：内置铃声，保证一定有声音
            safeAdd(Uri.parse("android.resource://$packageName/${R.raw.ring}"))
        }

        bumpVolume(chime)

        for (uri in candidates) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        // 用闹钟流：手机静音/振动模式也能响，车间里不会漏
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                val vol = if (chime) 0.65f else 1f
                mp.setVolume(vol, vol)
                mp.setOnErrorListener { _, what, extra ->
                    AlarmLog.add(this, "✗ 播放器错误 $what/$extra $uri")
                    true
                }
                // 同步 prepare：本地音效/内置资源都很快，失败能立刻换下一个候选
                mp.prepare()
                mp.start()
                player = mp
                AlarmLog.add(this, "▶ 正在播放($mode) $uri")
                return
            } catch (e: Throwable) {
                AlarmLog.add(this, "✗ 音效不可用 ${e.javaClass.simpleName} $uri")
                releasePlayer()
            }
        }
        AlarmLog.add(this, "✗ 没有任何可用音效")
    }

    private fun bumpVolume(soft: Boolean = false) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val ratio = if (soft) 0.55f else 0.8f
            val target = (max * ratio).toInt().coerceAtLeast(1)
            if (cur < target) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
                AlarmLog.add(this, "闹钟音量 $cur → $target（最大 $max）")
            } else {
                AlarmLog.add(this, "闹钟音量 $cur/$max 正常")
            }
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ 音量检查失败 ${e.javaClass.simpleName}")
        }
    }

    private fun startVibrate() {
        try {
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
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ 振动失败 ${e.javaClass.simpleName}")
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (_: Throwable) {
        }
        try {
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ringingKey = null
        RingOverlay.hide()
        releasePlayer()
        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
        vibrator = null
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (_: Throwable) {
        }
        wake = null
        releaseScreenWake()
        super.onDestroy()
    }
}

// ---------------- 响铃后的动作（通知按钮 / 全屏界面共用） ----------------

object RingActions {

    fun done(ctx: Context, key: String) {
        stopRinging(ctx)
        if (key.isBlank()) return
        AlarmLog.add(ctx, "已取出 $key")
        Repo.editStatic(ctx) { s -> applyDone(s, key) }
    }

    fun snooze(ctx: Context, key: String, minutes: Int = 2) {
        stopRinging(ctx)
        if (key.isBlank()) return
        AlarmLog.add(ctx, "延后 $minutes 分钟 $key")
        Repo.editStatic(ctx) { s -> applySnooze(s, key, minutes) }
    }

    fun stopRinging(ctx: Context) {
        RingOverlay.hide()
        try {
            ctx.stopService(Intent(ctx, RingService::class.java))
        } catch (_: Throwable) {
        }
        try {
            NotificationManagerCompat.from(ctx).cancel(RingNotif.ID)
        } catch (_: Throwable) {
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

    private fun applySnooze(s: AppState, key: String, minutes: Int) {
        val (kind, id) = split(key) ?: return
        when (kind) {
            "b" -> s.bakes.firstOrNull { it.id == id }?.apply {
                endAt += minutes * 60_000L
                this.minutes += minutes
                status = "running"
            }
            "s" -> s.sampling.firstOrNull { it.id == id }?.apply {
                dueAt += minutes * 60_000L
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

class RingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: ""
        when (intent.action) {
            RingNotif.ACTION_DONE -> RingActions.done(context, key)
            RingNotif.ACTION_SNOOZE -> RingActions.snooze(context, key, 2)
            else -> RingActions.stopRinging(context)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmLog.add(context, "开机/时间变化，重排闹钟")
        val s = Repo.read(context)
        AlarmScheduler.sync(context, s)
    }
}
