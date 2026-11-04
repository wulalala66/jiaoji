package com.wulalala66.jiaoji

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 一条对话消息（兼容 OpenAI 的 role / tool_calls / tool_call_id 结构） */
data class ChatMsg(
    val role: String,
    val content: String = "",
    val toolCalls: List<AiCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    /** 这一轮模型的思考过程（reasoning_content / reasoning / thinking 都收） */
    val thinking: String = "",
    /** 这条回复思考了多久（毫秒），只在本地展示用 */
    var thinkMs: Long = 0L,
)

/** 模型发起的一次工具调用 */
data class AiCall(val id: String, val name: String, val args: String)

class AiException(message: String) : Exception(message)

/**
 * 极简的 OpenAI 兼容客户端：
 *  - 拉模型列表 GET {base}/models
 *  - 流式对话 POST {base}/chat/completions（SSE），支持 function calling 循环
 *  - 上下文压缩：太长就把前面的对话压成一段摘要
 *
 * 没有用任何第三方 SDK，全部走 HttpURLConnection，方便离线/小包体。
 */
object Ai {

    private const val CONNECT_TIMEOUT = 20_000
    private const val READ_TIMEOUT = 180_000
    private val main = Handler(Looper.getMainLooper())

    /** 压缩阈值（字符数，粗算 token≈字符/2） */
    private const val COMPRESS_AT = 24_000
    private const val KEEP_TAIL = 6

    // ---------------------------------------------------------------- 模型列表

    fun fetchModels(settings: AiSettings): List<String> {
        val base = settings.baseUrl.trimEnd('/')
        if (base.isBlank()) throw AiException("还没填 API 地址")
        val conn = open("$base/models", settings, "GET")
        val body = readAll(conn)
        val arr = try {
            JSONObject(body).optJSONArray("data")
                ?: JSONObject(body).optJSONArray("models")
                ?: JSONArray(body)
        } catch (e: Throwable) {
            throw AiException("看不懂这个接口返回的内容：${body.take(200)}")
        }
        val out = ArrayList<String>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val o = arr?.opt(i)
            when (o) {
                is JSONObject -> out.add(o.optString("id").ifBlank { o.optString("name") })
                is String -> out.add(o)
            }
        }
        return out.filter { it.isNotBlank() }.distinct().sorted()
    }

    // ---------------------------------------------------------------- 对话

    /**
     * 跑一轮对话（内部会自动处理工具调用，最多 [maxRounds] 轮）。
     * 返回需要追加到历史里的消息（assistant / tool …），最后一条是给用户看的回答。
     */
    suspend fun runTurn(
        settings: AiSettings,
        history: List<ChatMsg>,
        state: AppState,
        onDelta: (String) -> Unit,
        onStatus: (String) -> Unit,
        onThink: (String) -> Unit = {},
        tools: Boolean = true,
        maxRounds: Int = 6,
    ): List<ChatMsg> = withContext(Dispatchers.IO) {
        val out = ArrayList<ChatMsg>()
        var msgs = history
        var round = 0
        while (round < maxRounds) {
            round++
            var reply = chatOnce(settings, msgs, onDelta, onThink, tools)
            var calls = reply.toolCalls
            if (calls.isEmpty()) {
                // 有些模型/中转不认 tools，会把调用当成 JSON 正文吐出来。
                // 这里把它认出来、剥掉 JSON、当成真正的工具调用执行，避免"只输出一段 JSON 没改到配方"。
                val (clean, fromText) = extractCalls(reply.content)
                if (fromText.isEmpty()) {
                    // 用户明显要改配方、但这一轮一个工具都没调：催它一次，别信"我已经改好了"
                    if (round == 1 && wantsChange(history) && out.none { it.role == "tool" }) {
                        main.post { onStatus("它没动配方，再催一次…") }
                        msgs = msgs + reply + ChatMsg(
                            role = "system",
                            content = "你刚才没有调用任何工具，配方实际上没有变化。请现在直接调用工具修改配方，不要只在回复里描述要改什么。",
                        )
                        continue
                    }
                    out.add(reply)
                    return@withContext out
                }
                main.post { onStatus("按回复里的 JSON 执行工具…") }
                reply = reply.copy(content = clean, toolCalls = fromText)
                calls = fromText
            }
            out.add(reply)
            msgs = msgs + reply
            for (call in calls) {
                val label = AiTools.label(call.name)
                main.post { onStatus("正在$label…") }
                val result = try {
                    AiTools.run(state, call.name, call.args)
                } catch (e: Throwable) {
                    "工具执行失败：${e.message}"
                }
                out.add(ChatMsg(role = "tool", content = result, toolCallId = call.id, name = call.name))
                msgs = msgs + ChatMsg(role = "tool", content = result, toolCallId = call.id, name = call.name)
            }
            main.post { onStatus("") }
        }
        out
    }

    /**
     * 从正文里捞工具调用。兼容这几种写法（中转把 tools 丢了的时候很常见）：
     *   {"name":"add_item","arguments":{"name":"D-80","percent":6.5}}
     *   {"tool":"add_item","args":{...}}
     *   {"tool_calls":[{"id":"call-1","function":{"name":"add_item","arguments":"{...}"}}]}
     * 也支持被 ```json 包起来、或夹在一段解释文字中间。
     * @return 清理掉 JSON 之后的正文 + 认出来的调用（只保留真实存在的工具名）
     */
    fun extractCalls(text: String): Pair<String, List<AiCall>> {
        if (text.isBlank()) return text to emptyList()
        val found = ArrayList<AiCall>()
        val spans = ArrayList<IntRange>()

        fun take(blob: String, start: Int, end: Int) {
            val calls = callsOf(blob)
            if (calls.isEmpty()) return
            found.addAll(calls)
            spans.add(start until end)
        }

        // 1) 整段就是一个 JSON
        runCatching { JSONObject(text.trim()) }.getOrNull()?.let { take(it.toString(), 0, text.length) }
        if (found.isEmpty()) runCatching { JSONArray(text.trim()) }.getOrNull()?.let { take(it.toString(), 0, text.length) }

        // 2) ```json ... ``` 代码块
        if (found.isEmpty()) {
            Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").findAll(text).forEach { m ->
                val blob = m.groupValues[1].trim()
                if (blob.startsWith("{") || blob.startsWith("[")) take(blob, m.range.first, m.range.last + 1)
            }
        }

        // 3) 正文里夹着的第一个/多个平衡括号 JSON
        if (found.isEmpty()) {
            var i = text.indexOfFirst { it == '{' || it == '[' }
            while (i >= 0 && i < text.length) {
                val end = balancedEnd(text, i)
                if (end > i) {
                    take(text.substring(i, end + 1), i, end + 1)
                    if (found.isNotEmpty()) break
                    i = text.indexOfFirst2('{', '[', end + 1)
                } else break
            }
        }

        if (found.isEmpty()) return text to emptyList()
        val sb = StringBuilder()
        var cursor = 0
        spans.sortedBy { it.first }.forEach { r ->
            if (r.first > cursor) sb.append(text, cursor, r.first)
            cursor = maxOf(cursor, r.last + 1)
        }
        if (cursor < text.length) sb.append(text, cursor, text.length)
        return sb.toString().trim() to found
    }

    private fun String.indexOfFirst2(a: Char, b: Char, from: Int): Int {
        for (i in from until length) if (this[i] == a || this[i] == b) return i
        return -1
    }

    private fun balancedEnd(s: String, start: Int): Int {
        val open = s[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (inStr) {
                when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
            } else {
                when (c) {
                    '"' -> inStr = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }
        return -1
    }

    /** 从一段 JSON 里认出工具调用；不认识的工具名直接丢弃 */
    private fun callsOf(blob: String): List<AiCall> {
        val out = ArrayList<AiCall>()
        val root: Any? = runCatching { JSONObject(blob) }.getOrNull() ?: runCatching { JSONArray(blob) }.getOrNull()
        fun one(o: JSONObject) {
            val fn = o.optJSONObject("function")
            val name = o.optString("name").ifBlank { fn?.optString("name") ?: "" }
                .ifBlank { o.optString("tool") }.ifBlank { o.optString("tool_name") }
            if (name.isBlank() || !AiTools.known(name)) return
            val rawArgs = o.opt("arguments") ?: fn?.opt("arguments") ?: o.opt("args")
                ?: o.opt("parameters") ?: o.opt("input") ?: o.opt("params")
            val args = when (rawArgs) {
                null -> "{}"
                is JSONObject -> rawArgs.toString()
                is JSONArray -> rawArgs.toString()
                else -> rawArgs.toString().ifBlank { "{}" }
            }
            val id = o.optString("id").ifBlank { "text-" + out.size }
            out.add(AiCall(id = id, name = name, args = args))
        }
        when (root) {
            is JSONObject -> {
                val arr = root.optJSONArray("tool_calls") ?: root.optJSONArray("calls")
                if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { one(it) }
                else one(root)
            }
            is JSONArray -> for (i in 0 until root.length()) root.optJSONObject(i)?.let { one(it) }
            else -> {}
        }
        return out
    }

    /** 用户这句话是不是在要求改配方 */
    private fun wantsChange(history: List<ChatMsg>): Boolean {
        val last = history.lastOrNull { it.role == "user" }?.content ?: return false
        return Regex("加|改|删|补|调|替换|去掉|增加|减少|换成|降|提|优化|调整|重做|再来").containsMatchIn(last)
    }

    /** 有的模型/中转不认 reasoning_effort，返回 400 时去掉它再试一次 */
    private val REASONING_REJECT = Regex("reasoning_effort|reasoning|effort|thinking", RegexOption.IGNORE_CASE)

    /** 单次请求：把 SSE 的正文增量吐给 onDelta、思考增量吐给 onThink，最后拼成一条 ChatMsg */
    private fun chatOnce(
        settings: AiSettings,
        history: List<ChatMsg>,
        onDelta: (String) -> Unit,
        onThink: (String) -> Unit = {},
        tools: Boolean = true,
    ): ChatMsg {
        if (settings.thinking.isNotBlank()) {
            try {
                return once(settings, history, onDelta, onThink, tools, withEffort = true)
            } catch (e: AiException) {
                val m = e.message ?: ""
                if (!m.contains("400") || !REASONING_REJECT.containsMatchIn(m)) throw e
                main.post { onThink("") }
                return once(settings, history, onDelta, onThink, tools, withEffort = false)
            }
        }
        return once(settings, history, onDelta, onThink, tools, withEffort = false)
    }

    private fun once(
        settings: AiSettings,
        history: List<ChatMsg>,
        onDelta: (String) -> Unit,
        onThink: (String) -> Unit,
        tools: Boolean,
        withEffort: Boolean,
    ): ChatMsg {
        val body = JSONObject().apply {
            put("model", settings.model)
            put("stream", true)
            put("messages", JSONArray().apply { history.forEach { put(it.toJson()) } })
            if (tools) {
                put("tools", AiTools.defs())
                put("tool_choice", "auto")
            }
            if (withEffort) put("reasoning_effort", settings.thinking)
        }
        val conn = open("${settings.baseUrl.trimEnd('/')}/chat/completions", settings, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).readText() } ?: ""
            throw AiException("接口返回 $code：${err.take(400).ifBlank { "没有内容" }}")
        }

        val sb = StringBuilder()
        val think = StringBuilder()
        val calls = LinkedHashMap<Int, Pair<String, StringBuilder>>() // index -> id, name
        val argsBuf = HashMap<Int, StringBuilder>()

        val contentType = conn.contentType ?: ""
        if (!contentType.contains("event-stream", true)) {
            // 有的中转会忽略 stream=true，直接返回整包 JSON
            val text = readAll(conn)
            return parseFull(text, onDelta)
        }

        conn.inputStream.bufferedReader().use { reader ->
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val json = try {
                    JSONObject(payload)
                } catch (_: Throwable) {
                    continue
                }
                val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                val delta = choice.optJSONObject("delta") ?: continue
                val piece = delta.optString("content", "")
                if (piece.isEmpty()) {
                    // 有的网关在拒答时把话放在 refusal 里
                    val refusal = delta.optString("refusal", "")
                    if (refusal.isNotEmpty()) {
                        sb.append(refusal)
                        main.post { onDelta(refusal) }
                    }
                }
                if (piece.isNotEmpty()) {
                    sb.append(piece)
                    main.post { onDelta(piece) }
                }
                // 思考过程：DeepSeek/智谱叫 reasoning_content，OpenAI 系叫 reasoning，个别叫 thinking
                val reason = delta.optString("reasoning_content", "")
                    .ifEmpty { delta.optString("reasoning", "") }
                    .ifEmpty { delta.optString("thinking", "") }
                if (reason.isNotEmpty()) {
                    think.append(reason)
                    main.post { onThink(reason) }
                }
                val tc = delta.optJSONArray("tool_calls")
                if (tc != null) {
                    for (i in 0 until tc.length()) {
                        val t = tc.optJSONObject(i) ?: continue
                        val idx = t.optInt("index", i)
                        val id = t.optString("id", "")
                        val fn = t.optJSONObject("function")
                        val name = fn?.optString("name", "") ?: ""
                        val args = fn?.optString("arguments", "") ?: ""
                        val cur = calls[idx]
                        val newId = if (id.isNotBlank()) id else cur?.first ?: ""
                        val newName = if (name.isNotBlank()) name else cur?.second?.toString() ?: ""
                        calls[idx] = newId to StringBuilder(newName)
                        if (args.isNotEmpty()) argsBuf.getOrPut(idx) { StringBuilder() }.append(args)
                    }
                }
            }
        }
        conn.disconnect()

        val list = calls.entries.sortedBy { it.key }.mapNotNull { (idx, v) ->
            if (v.first.isBlank() || v.second.toString().isBlank()) null
            else AiCall(v.first, v.second.toString(), argsBuf[idx]?.toString() ?: "{}")
        }
        return ChatMsg(role = "assistant", content = sb.toString(), toolCalls = list, thinking = think.toString())
    }

    private fun parseFull(text: String, onDelta: (String) -> Unit): ChatMsg {
        val json = try {
            JSONObject(text)
        } catch (_: Throwable) {
            throw AiException("返回的内容不是 JSON：${text.take(200)}")
        }
        val msg = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw AiException("返回里没有 message：${text.take(200)}")
        val content = msg.optString("content", "")
        if (content.isNotEmpty()) main.post { onDelta(content) }
        val reasoning = msg.optString("reasoning_content", "")
            .ifEmpty { msg.optString("reasoning", "") }
            .ifEmpty { msg.optString("thinking", "") }
        val calls = ArrayList<AiCall>()
        msg.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val fn = t.optJSONObject("function") ?: continue
                calls.add(
                    AiCall(
                        t.optString("id", "call_$i"),
                        fn.optString("name", ""),
                        fn.optString("arguments", "{}"),
                    )
                )
            }
        }
        return ChatMsg(role = "assistant", content = content, toolCalls = calls, thinking = reasoning)
    }

    /** 一次性问答（不走工具、不流式），用于「实时点评」这类小请求 */
    suspend fun quick(settings: AiSettings, system: String, user: String): String =
        withContext(Dispatchers.IO) {
            val msgs = listOf(
                ChatMsg(role = "system", content = system),
                ChatMsg(role = "user", content = user),
            )
            chatOnce(settings.copy(thinking = ""), msgs, {}, {}, tools = false).content.trim()
        }

    // ---------------------------------------------------------------- 上下文压缩

    /** 历史太长就把前面的压成摘要，保留最近几条原样 */
    suspend fun maybeCompress(settings: AiSettings, history: List<ChatMsg>): List<ChatMsg> =
        withContext(Dispatchers.IO) {
            if (!settings.compress) return@withContext history
            val size = history.sumOf { it.content.length + it.toolCalls.sumOf { c -> c.args.length } }
            if (size < COMPRESS_AT || history.size <= KEEP_TAIL + 2) return@withContext history
            val head = history.dropLast(KEEP_TAIL)
            val tail = history.takeLast(KEEP_TAIL)
            val transcript = head.joinToString("\n") { m ->
                val who = when (m.role) {
                    "user" -> "用户"
                    "assistant" -> "助手"
                    "tool" -> "工具(${m.name ?: ""})"
                    else -> m.role
                }
                "$who：${m.content.take(600)}"
            }
            val ask = listOf(
                ChatMsg(role = "system", content = "把下面的对话压缩成要点，保留配方数据、用户的偏好和结论，不要客套话。"),
                ChatMsg(role = "user", content = transcript.take(30_000)),
            )
            val summary = try {
                chatOnce(settings.copy(thinking = ""), ask, {}, {}, tools = false)
            } catch (_: Throwable) {
                return@withContext history
            }
            if (summary.content.isBlank()) return@withContext history
            listOf(ChatMsg(role = "system", content = "（之前对话的摘要）\n" + summary.content)) + tail
        }

    // ---------------------------------------------------------------- HTTP 细节

    private fun open(url: String, settings: AiSettings, method: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
            setRequestProperty("Accept", "text/event-stream, application/json")
            setRequestProperty("User-Agent", "JiaoJi/${BuildConfig.VERSION_NAME} (Android)")
        }
        return conn
    }

    private fun readAll(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.let { BufferedReader(InputStreamReader(it)).readText() } ?: ""
        conn.disconnect()
        return text
    }

    /** 内置的系统提示词（用户可以在设置里覆盖） */
    fun defaultSystemPrompt(): String = """
你是「胶记」里的配料助手，服务对象是胶粘剂实验室的工程师。
说话简短、直接给结论和数字，别客套。回答用中文。

你可以直接读写用户的配料单（工具里带 formulaId 的那种），规则：
- 百分比是唯一准的东西，克数 = 百分比 × 总量 / 100，所以改配方优先改百分比。
- 加料前先看一眼现有配方，别把同名原料加两遍；要加就 add_item，要改就 update_item。
- 用户说「补到 100%」时，把差额加到最合适的那个原料上（fill_rest 也能用）。
- 总量一般是 600g 一锅，用户没说就别乱改总量。
- 需要查资料（供应商牌号、固化条件、行业做法）就用 web_search / fetch_url，别编。
- 算数用 calc，不要心算。
- 每次动完配料单，用一两句话说明「加了什么、现在合计多少、还剩多少」。
- 改配方必须真的调用工具（add_item / update_item / remove_item / fill_rest …）。
  绝对不要把"要改的内容"写成一段 JSON 或表格丢给用户自己改——那就是没改。
- 万一当前接口不支持工具调用，就只输出一个 JSON 对象、不要写别的字，格式：
  {"name":"add_item","arguments":{"formulaId":"…","name":"D-80","percent":6.5}}
  系统会自动执行这个 JSON，再回来问你结果。
""".trimIndent()

    fun systemMsg(settings: AiSettings): ChatMsg {
        val base = settings.systemPrompt.ifBlank { defaultSystemPrompt() }
        return ChatMsg(role = "system", content = base)
    }

    // ---------------------------------------------------------------- 小工具

    fun encode(s: String): String = try {
        URLEncoder.encode(s, "UTF-8")
    } catch (_: Throwable) {
        s
    }

    fun num(v: Double, digits: Int = 2): String =
        String.format(Locale.US, "%.${digits}f", v)
}

/** 把 ChatMsg 转成 OpenAI 需要的 JSON */
fun ChatMsg.toJson(): JSONObject = JSONObject().apply {
    put("role", role)
    if (role == "assistant" && toolCalls.isNotEmpty()) {
        put("content", content.ifBlank { JSONObject.NULL })
        put("tool_calls", JSONArray().apply {
            toolCalls.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("type", "function")
                    put("function", JSONObject().put("name", c.name).put("arguments", c.args.ifBlank { "{}" }))
                })
            }
        })
    } else {
        put("content", content)
    }
    toolCallId?.let { put("tool_call_id", it) }
    name?.let { if (role == "tool") put("name", it) }
}
