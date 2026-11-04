package com.wulalala66.jiaoji

import android.app.Application
import android.content.Intent

/**
 * 这一版把「烘箱 / 取样 / 闹钟」整套下线，只留比重、配料、条件表、设置。
 * onCreate 里做三件事：
 *  1. 初始化存储；
 *  2. 把上一版排定的闹钟和常驻通知全部取消，避免升级后还冒出来响铃页；
 *  3. 崩溃信息写日志，方便用户从诊断页复制给我。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Repo.I.init(this)
        RingNotif.ensureChannel(this)

        // 老版本留下的闹钟/前台服务：一律清掉
        try {
            AlarmScheduler.cancelAll(this)
            stopService(Intent(this, RingService::class.java))
            stopService(Intent(this, KeepAliveService::class.java))
            KeepAliveService.running = false
        } catch (_: Throwable) {
        }

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                AlarmLog.add(this, "✗ 崩溃 ${e.javaClass.simpleName}: ${e.message}")
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}
