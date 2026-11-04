package com.wulalala66.jiaoji

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 会话状态放在页面外面（应用级），所以：
 *  - 发出去的问题，返回上一页、切到别的工具，它照样在跑，回来还能看到结果；
 *  - 同一个配方的对话来回切页不丢。
 */
object AiSession {

    class Session {
        val msgs = mutableStateListOf<ChatMsg>()

        /** 正在等回复（含工具执行） */
        var busy by mutableStateOf(false)

        /** 正在流式输出的正文 */
        var stream by mutableStateOf("")

        /** 正在流式输出的思考过程 */
        var think by mutableStateOf("")

        /** 当前在干什么（读配方 / 加原料 / 搜索网页 …） */
        var status by mutableStateOf("")

        var error by mutableStateOf("")

        /** 本轮开始时间，用来算「已思考 N 秒」 */
        var startedAt by mutableStateOf(0L)

        /** 上一轮思考耗时（毫秒） */
        var thinkMs by mutableStateOf(0L)

        internal var job: Job? = null

        val live: Boolean get() = busy || stream.isNotEmpty() || think.isNotEmpty()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val map = HashMap<String, Session>()

    fun of(id: String): Session = map.getOrPut(id) { Session() }

    fun send(id: String, text: String, settings: AiSettings, state: AppState) {
        val s = of(id)
        val clean = text.trim()
        if (clean.isEmpty() || s.busy) return
        s.msgs.add(ChatMsg(role = "user", content = clean))
        run(s, settings, state)
    }

    /** 重新回答：把最后一条 AI 回复（含它调用的工具）丢掉再来一遍 */
    fun regenerate(id: String, settings: AiSettings, state: AppState) {
        val s = of(id)
        if (s.busy) return
        while (s.msgs.isNotEmpty() && s.msgs.last().role != "user") s.msgs.removeAt(s.msgs.size - 1)
        if (s.msgs.isEmpty()) return
        run(s, settings, state)
    }

    /** 改掉第 [index] 条用户消息，从那里重新跑 */
    fun editAndResend(id: String, index: Int, text: String, settings: AiSettings, state: AppState) {
        val s = of(id)
        if (s.busy) return
        val clean = text.trim()
        if (clean.isEmpty() || index !in s.msgs.indices) return
        while (s.msgs.size > index) s.msgs.removeAt(s.msgs.size - 1)
        s.msgs.add(ChatMsg(role = "user", content = clean))
        run(s, settings, state)
    }

    fun stop(id: String) {
        val s = of(id)
        s.job?.cancel()
        s.job = null
        s.busy = false
        s.stream = ""
        s.think = ""
        s.status = ""
    }

    fun clear(id: String) {
        val s = of(id)
        s.job?.cancel()
        s.job = null
        s.msgs.clear()
        s.busy = false
        s.stream = ""
        s.think = ""
        s.status = ""
        s.error = ""
        s.thinkMs = 0L
    }

    fun hasAny(id: String): Boolean = map[id]?.msgs?.isNotEmpty() == true

    private fun run(s: Session, settings: AiSettings, state: AppState) {
        if (!settings.ready) {
            s.error = "还没配置 AI：去「设置」填接口地址、Key，再拉取模型"
            return
        }
        s.busy = true
        s.error = ""
        s.stream = ""
        s.think = ""
        s.status = ""
        s.startedAt = System.currentTimeMillis()
        s.thinkMs = 0L
        s.job = scope.launch {
            var thinkAt = 0L
            try {
                val history = ArrayList<ChatMsg>()
                history.add(Ai.systemMsg(settings))
                history.addAll(Ai.maybeCompress(settings, s.msgs.toList()))
                val result = Ai.runTurn(
                    settings = settings,
                    history = history,
                    state = state,
                    onDelta = { piece -> s.stream += piece },
                    onStatus = { line -> s.status = line },
                    onThink = { piece ->
                        if (thinkAt == 0L) thinkAt = System.currentTimeMillis()
                        s.think += piece
                    },
                )
                s.msgs.addAll(result)
                if (thinkAt > 0L) s.thinkMs = System.currentTimeMillis() - thinkAt
                result.lastOrNull { it.role == "assistant" }?.let { it.thinkMs = s.thinkMs }
            } catch (e: CancellationException) {
                s.error = "已停止"
                throw e
            } catch (e: Throwable) {
                s.error = e.message?.take(300) ?: "请求失败"
            } finally {
                s.stream = ""
                s.think = ""
                s.status = ""
                s.busy = false
                s.job = null
            }
        }
    }
}
