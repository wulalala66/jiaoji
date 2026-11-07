package com.gluelab.jiaoji

import android.provider.AlarmClock
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 把倒计时交给系统时钟 App。
 *
 * 为什么这么做：
 *  · `android.intent.action.SET_TIMER` 是 Android 的公开标准动作，OPPO/华为/小米/三星
 *    自带的时钟都实现了它，不需要任何权限，也不用跟厂商谈合作；
 *  · 系统时钟到点会自己响铃 + 震动 + 在锁屏上显示，比我们自己在后台守着可靠得多
 *    （我们的进程被系统杀掉就不会响，系统时钟不会）。
 *
 * 拿不到时钟 App 的机型（极少数）再退一步用 SET_ALARM。
 */
object SystemTimer {

    /**
     * 调起系统时钟的「计时器」。
     * @return true = 成功交给系统了；false = 这台机器上没有能接这个动作的 App
     */
    fun start(ctx: Context, minutes: Int, label: String): Boolean {
        if (minutes <= 0) return false
        val seconds = minutes * 60
        if (tryIntent(ctx, timerIntent(label, seconds))) return true
        // 少数机型只认「闹钟」，用 SET_ALARM 兜底（会定在 N 分钟后的整点时刻）
        val at = System.currentTimeMillis() + seconds * 1000L
        if (tryIntent(ctx, alarmIntent(label, at))) return true
        return false
    }

    private fun timerIntent(label: String, seconds: Int): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            // true = 尽量让时钟 App 直接开始计时，不用再按一下「开始」
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun alarmIntent(label: String, atMillis: Long): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_HOUR, 0)
            .putExtra(AlarmClock.EXTRA_MINUTES, 0)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .apply { putExtra("android.intent.extra.alarm.TIME_IN_MILLIS", atMillis) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tryIntent(ctx: Context, intent: Intent): Boolean {
        // 不先 resolveActivity 就 startActivity，抛异常再兜底 —— 少一次跨进程查询
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 生成一个 .ics 日历事件（兜底方案，网页版也用它）。
     * 手机自带日历能直接打开，到点也会提醒。
     */
    fun ics(title: String, startAt: Long, minutes: Int): String {
        fun fmt(ms: Long): String {
            val f = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return f.format(java.util.Date(ms))
        }
        val end = startAt + minutes * 60_000L
        val safe = title.replace("\n", " ").replace(",", "\\,")
        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//GlueLab//JiaoJi//CN\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("BEGIN:VEVENT\r\n")
            append("UID:").append(System.currentTimeMillis()).append("@jiaoji\r\n")
            append("DTSTAMP:").append(fmt(System.currentTimeMillis())).append("\r\n")
            append("DTSTART:").append(fmt(startAt)).append("\r\n")
            append("DTEND:").append(fmt(end)).append("\r\n")
            append("SUMMARY:").append(safe).append("\r\n")
            append("DESCRIPTION:烘烤计时 ").append(minutes).append(" 分钟\r\n")
            append("BEGIN:VALARM\r\n")
            append("TRIGGER:-PT0M\r\n")
            append("ACTION:DISPLAY\r\n")
            append("DESCRIPTION:").append(safe).append("\r\n")
            append("END:VALARM\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
    }

    /** 打开系统日历（兜底用） */
    fun openCalendar(ctx: Context, ics: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("data:text/calendar;charset=utf8," + Uri.encode(ics)),
                    "text/calendar",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 这个机型支不支持调起系统计时器（给 UI 决定要不要显示按钮） */
    fun available(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return false
        return try {
            ctx.packageManager.resolveActivity(timerIntent("x", 60), 0) != null
        } catch (_: Throwable) {
            false
        }
    }
}
