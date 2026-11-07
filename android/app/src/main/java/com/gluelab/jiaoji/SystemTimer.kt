package com.gluelab.jiaoji

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock as AC

/**
 * 把倒计时交给系统时钟。
 *
 * 为什么要这么绕：
 *  · AOSP 的标准做法是 `android.intent.action.SET_TIMER`，但 ColorOS 的时钟把接收它的
 *    `HandleApiActivity` 挂上了 `android:permission="com.android.alarm.permission.SET_ALARM"`，
 *    第三方 App 调进去会被 SecurityException 挡掉 —— 这就是「显示没有时钟 App」的真正原因。
 *  · 但同一个时钟里还有一条**没有权限保护**的路：`com.oplus.alarmclock.AlarmClock`
 *    （exported=true），它接的是 `com.oplus.alarmclock.AI_SET_TIMER` ——
 *    这正是 ColorOS 内部把标准 SET_TIMER 转发过去时用的 action。
 *
 * 所以这里按「标准 → 厂商 → 闹钟 → 应用内」四级依次尝试，哪一级成功就用哪一级。
 */
object SystemTimer {

    data class Attempt(val name: String, val intent: Intent)

    /**
     * 按优先级排好的候选。厂商 action 同时带上标准 extra 和 OPPO 自己的 extra，
     * 它读哪个都能拿到值。
     */
    private fun candidates(label: String, minutes: Int): List<Attempt> {
        val seconds = minutes * 60
        val length = seconds

        fun base(action: String) = Intent(action)
            .putExtra(AC.EXTRA_MESSAGE, label)
            .putExtra(AC.EXTRA_LENGTH, length)
            .putExtra(AC.EXTRA_SKIP_UI, true)
            // OPPO/部分厂商的私有字段，多给几个不吃亏
            .putExtra("extra_start", true)
            .putExtra("duration", length * 1000)
            .putExtra("timer_duration", length * 1000)
            .putExtra("EXTRA_TIMER_DURATION", length * 1000)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return listOf(
            Attempt("标准 SET_TIMER", base(AC.ACTION_SET_TIMER)),
            Attempt("OPPO AI_SET_TIMER", base("com.oplus.alarmclock.AI_SET_TIMER")),
            Attempt("ColorOS AI_SET_TIMER", base("com.coloros.alarmclock.AI_SET_TIMER")),
            Attempt("OPPO 时钟主界面", base("com.oplus.alarmclock.AlarmClock")),
            Attempt("ColorOS 时钟主界面", base("com.coloros.alarmclock.AlarmClock")),
        )
    }

    /** 最后一个兜底：标准闹钟（定在 N 分钟后的整点） */
    private fun alarmAttempt(label: String, minutes: Int): Attempt {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis() + minutes * 60_000L
        return Attempt(
            "标准 SET_ALARM",
            Intent(AC.ACTION_SET_ALARM)
                .putExtra(AC.EXTRA_MESSAGE, label)
                .putExtra(AC.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
                .putExtra(AC.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
                .putExtra(AC.EXTRA_SKIP_UI, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * 依次尝试，返回成功的那一级名字；全都失败返回 null。
     */
    fun start(ctx: Context, minutes: Int, label: String): String? {
        if (minutes <= 0) return null
        for (a in candidates(label, minutes) + alarmAttempt(label, minutes)) {
            if (launch(ctx, a.intent)) return a.name
        }
        return null
    }

    private fun launch(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: Throwable) {
        false
    }

    // ---------------------------------------------------------------- 自检

    /** 一条候选在这台机器上能不能用（给设置页的自检用） */
    data class Probe(val name: String, val action: String, val handlers: Int, val error: String = "")

    fun probe(ctx: Context): List<Probe> {
        val out = ArrayList<Probe>()
        for (a in candidates("测试", 5)) {
            val action = a.intent.action ?: ""
            val n = try {
                ctx.packageManager.queryIntentActivities(a.intent, 0).size
            } catch (e: Throwable) {
                -1
            }
            out.add(Probe(a.name, action, n))
        }
        val alarm = alarmAttempt("测试", 5)
        out.add(
            Probe(
                alarm.name,
                alarm.intent.action ?: "",
                try {
                    ctx.packageManager.queryIntentActivities(alarm.intent, 0).size
                } catch (e: Throwable) {
                    -1
                },
            )
        )
        return out
    }

    /** 把自检结果拼成一段能直接复制发出来的文字 */
    fun probeReport(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("机型：").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n')
        sb.append("系统：Android ").append(android.os.Build.VERSION.RELEASE).append('\n')
        probe(ctx).forEach { p ->
            sb.append(if (p.handlers > 0) "✓ " else "✗ ")
                .append(p.name).append("  (").append(p.action).append(")  处理器 ")
                .append(if (p.handlers < 0) "查询失败" else p.handlers.toString()).append(" 个\n")
        }
        return sb.toString()
    }
}
