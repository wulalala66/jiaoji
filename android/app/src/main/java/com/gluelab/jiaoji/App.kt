package com.gluelab.jiaoji

import android.app.Application
import android.app.NotificationManager
import android.content.Context

/**
 * 这一版只有四个工具：比重 / 配料 / 条件表 / 设置。
 *
 * onCreate 里做三件事：
 *  1. 初始化存储；
 *  2. 把老版本（≤2.8.x，带取样提醒和烘箱计时）遗留下来的通知清掉，
 *     否则升级后旧通知还挂在通知栏里，点了会跳到一个已经不存在的页面；
 *  3. 崩溃信息写本地日志，用户可以在诊断页一键复制给我。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Repo.I.init(this)

        // 老版本留下的通知（闹钟/计时器）：一律清掉
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll()
        }

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                AppLog.add(this, "✗ 崩溃 ${e.javaClass.simpleName}: ${e.message}")
            }
            prev?.uncaughtException(t, e)
        }
    }
}
