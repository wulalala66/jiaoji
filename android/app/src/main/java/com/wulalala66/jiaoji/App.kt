package com.wulalala66.jiaoji

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** key → 已经响过的那个时间点；endAt / dueAt 变了（延后、改时长）才会再响一次 */
    private val fired = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        Repo.I.init(this)
        RingNotif.ensureChannel(this)
        KeepAliveService.ensureChannel(this)
        KeepAlive.sync(this, Repo.I.state.value)
        scope.launch { watchDueTimes() }
    }

    /**
     * 进程内到点检查。
     *
     * AlarmManager 负责「进程被系统杀掉」的情况；但只要进程还活着（放在后台、屏幕黑着），
     * 这条每秒一次的检查就一定能把到点的那一条抓出来——不依赖闹钟权限、不依赖
     * 「后台弹出界面」，是最后一道保险。
     */
    private suspend fun watchDueTimes() {
        while (scope.isActive) {
            delay(1_000L)
            try {
                checkOnce()
            } catch (_: Throwable) {
            }
        }
    }

    private fun checkOnce() {
        val s = Repo.I.state.value
        KeepAlive.sync(this, s)
        val now = System.currentTimeMillis()
        val grace = 5 * 60_000L

        s.bakes.forEach { b ->
            if (b.status == "done" || b.endAt > now || now - b.endAt > grace) return@forEach
            val key = "b:${b.id}"
            if (fired[key] == b.endAt) return@forEach
            fired[key] = b.endAt
            fire(key, "烘烤完成 · 该出炉了", "${b.label} · ${b.tempC}℃ ${b.minutes}分钟 · ${s.ovenName(b.oven)}")
        }

        s.sampling.forEach { t ->
            if (t.status != "pending" || t.dueAlarmed || t.dueAt > now || now - t.dueAt > grace) return@forEach
            val key = "s:${t.id}"
            if (fired[key] == t.dueAt) return@forEach
            fired[key] = t.dueAt
            fire(key, "取样时间到", "${t.customer} · ${s.typeName(t.typeId)}")
        }
    }

    private fun fire(key: String, title: String, body: String) {
        AlarmLog.add(this, "⏰ 进程内到点 $key")
        try {
            ContextCompat.startForegroundService(this, RingService.intent(this, key, title, body, "due"))
        } catch (e: Throwable) {
            AlarmLog.add(this, "✗ 服务启动失败 ${e.javaClass.simpleName}")
        }
        // 进程在后台时拉不起 Activity，直接把响铃界面画成悬浮窗——黑屏也能看见
        RingOverlay.show(this, key, title, body, "due")
        try {
            NotificationManagerCompat.from(this).notify(RingNotif.ID, RingNotif.build(this, key, title, body, "due"))
        } catch (_: Throwable) {
        }
    }
}
