package com.gluelab.jiaoji

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * AI 能用的工具（function calling）。
 * 都是 App 内部的真操作 + 两个联网工具（网页搜索 / 取网页），
 * 这样模型能读配方、改配方、查资料、算数，而不是瞎编。
 */
object AiTools {

    private val main = Handler(Looper.getMainLooper())

    /** 给模型看的中文名字（用于「正在…」提示） */
    fun label(name: String): String = when (name) {
        "list_formulas" -> "翻配方列表"
        "get_formula" -> "读配方"
        "create_formula" -> "新建配方"
        "set_formula_total" -> "改总量"
        "add_item" -> "加原料"
        "update_item" -> "改原料比例"
        "remove_item" -> "删原料"
        "fill_rest" -> "补齐到 100%"
        "clear_items" -> "清空原料"
        "set_formula_meta" -> "写实验目的"
        "get_products" -> "查产品条件表"
        "web_search" -> "搜索网页"
        "fetch_url" -> "读网页"
        "calc" -> "算数"
        else -> name
    }

    /** 工具名白名单：正文里捞到的 JSON 只认这些名字，避免瞎执行 */
    private val NAMES = setOf(
        "list_formulas", "get_formula", "create_formula", "set_formula_total",
        "add_item", "update_item", "remove_item", "fill_rest", "clear_items",
        "set_formula_meta", "get_products", "web_search", "fetch_url", "calc",
    )

    fun known(name: String): Boolean = name in NAMES

    // ---------------------------------------------------------------- 工具定义

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject =
        JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", desc)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", props)
                    put("required", JSONArray(required))
                })
            })
        }

    private fun p(type: String, desc: String): JSONObject =
        JSONObject().put("type", type).put("description", desc)

    fun defs(): JSONArray = JSONArray().apply {
        put(fn("list_formulas", "列出所有配方（id、名称、总量、原料数、合计百分比）", JSONObject(), emptyList()))
        put(fn("get_formula", "读取一个配方的完整内容：原料、百分比、克数、合计、剩余", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
        }, listOf("formulaId")))
        put(fn("create_formula", "新建一个空配方，返回新配方 id", JSONObject().apply {
            put("name", p("string", "配方名称"))
            put("totalG", p("number", "一锅总量，单位 g，默认 600"))
        }, listOf("name")))
        put(fn("set_formula_total", "修改配方的一锅总量（克数会跟着重算，百分比不变）", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
            put("totalG", p("number", "新的总量，单位 g"))
        }, listOf("formulaId", "totalG")))
        put(fn("add_item", "往配方里加一行原料", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
            put("name", p("string", "原料名，例如 D1NP"))
            put("percent", p("number", "百分比，例如 26.33"))
        }, listOf("formulaId", "name", "percent")))
        put(fn("update_item", "改一行原料（按名称或 id 定位），可以改名字和百分比", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
            put("item", p("string", "原料名或 item id"))
            put("name", p("string", "新名称，不改就不填"))
            put("percent", p("number", "新的百分比，不改就不填"))
        }, listOf("formulaId", "item")))
        put(fn("remove_item", "从配方里删掉一行原料", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
            put("item", p("string", "原料名或 item id"))
        }, listOf("formulaId", "item")))
        put(fn("fill_rest", "把「剩余百分比」全部补到某一行原料上（不填 item 就补给占比最大的那行）", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
            put("item", p("string", "原料名或 item id，可空"))
        }, listOf("formulaId")))
        put(fn("clear_items", "清空配方的所有原料（只留空配方）", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
        }, listOf("formulaId")))
        put(fn("set_formula_meta", "写配方的实验目的 / 固化温度（会出现在打印的配料单上）", JSONObject().apply {
            put("formulaId", p("string", "配方 id"))
            put("purpose", p("string", "实验目的，例如 降低粘度"))
            put("cure", p("string", "固化温度，例如 140*30"))
        }, listOf("formulaId")))
        put(fn("get_products", "查产品条件表：产品名对应的烘烤温度和时间", JSONObject().apply {
            put("query", p("string", "产品名关键词，可空（空就全返回）"))
        }, emptyList()))
        put(fn("web_search", "用搜索引擎查资料，返回标题、链接和摘要", JSONObject().apply {
            put("query", p("string", "搜索关键词"))
            put("limit", p("integer", "返回条数，默认 5"))
        }, listOf("query")))
        put(fn("fetch_url", "打开一个网页并返回正文纯文本（最多 6000 字）", JSONObject().apply {
            put("url", p("string", "要读的网址"))
        }, listOf("url")))
        put(fn("calc", "算一个数学表达式，支持 + - * / % ^ 和括号", JSONObject().apply {
            put("expression", p("string", "表达式，例如 (600*26.33)/100"))
        }, listOf("expression")))
    }

    // ---------------------------------------------------------------- 执行

    /** 执行一个工具调用，返回给模型看的字符串（尽量短） */
    fun run(name: String, argsJson: String): String {
        fun now() = Repo.I.state.value
        val a = try {
            if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
        } catch (e: Throwable) {
            return "参数不是合法 JSON：${e.message}"
        }
        return when (name) {
            "list_formulas" -> onMain { listFormulas(now()) }
            "get_formula" -> onMain { getFormula(now(), a.optString("formulaId")) }
            "create_formula" -> onMain {
                val id = Repo.I.addFormula(a.optString("name", "新配方"), a.optDouble("totalG", 600.0))
                JSONObject().put("ok", true).put("formulaId", id).toString()
            }
            "set_formula_total" -> onMain {
                val id = a.optString("formulaId")
                if (now().formulaOf(id) == null) return@onMain err("没有这个配方：$id")
                Repo.I.setFormulaTotal(id, a.optDouble("totalG", 600.0))
                getFormula(now(), id)
            }
            "add_item" -> onMain {
                val id = a.optString("formulaId")
                if (now().formulaOf(id) == null) return@onMain err("没有这个配方：$id")
                val nm = a.optString("name").trim()
                if (nm.isBlank()) return@onMain err("原料名不能为空")
                val pct = a.optDouble("percent", 0.0)
                if (pct <= 0) return@onMain err("百分比要大于 0")
                val exist = now().formulaOf(id)?.items?.firstOrNull { sameName(it.name, nm) }
                if (exist != null) {
                    Repo.I.updateFormulaItem(id, exist.id, null, pct)
                    return@onMain getFormula(now(), id)
                }
                Repo.I.addFormulaItem(id, nm, pct)
                getFormula(now(), id)
            }
            "update_item" -> onMain {
                val id = a.optString("formulaId")
                val f = now().formulaOf(id) ?: return@onMain err("没有这个配方：$id")
                val key = a.optString("item")
                val hit = findItem(f, key) ?: return@onMain err(noItem(f, key))
                val nm = if (a.has("name")) a.optString("name").trim() else null
                val pct = if (a.has("percent")) a.optDouble("percent", hit.percent) else null
                Repo.I.updateFormulaItem(id, hit.id, nm, pct)
                getFormula(now(), id)
            }
            "remove_item" -> onMain {
                val id = a.optString("formulaId")
                val f = now().formulaOf(id) ?: return@onMain err("没有这个配方：$id")
                val key = a.optString("item")
                val hit = findItem(f, key) ?: return@onMain err(noItem(f, key))
                Repo.I.removeFormulaItem(id, hit.id)
                getFormula(now(), id)
            }
            "fill_rest" -> onMain {
                val id = a.optString("formulaId")
                val f = now().formulaOf(id) ?: return@onMain err("没有这个配方：$id")
                val key = a.optString("item")
                val hit = if (key.isBlank()) f.items.maxByOrNull { it.percent } else findItem(f, key)
                if (hit == null) return@onMain err("配方里还没有原料，先 add_item")
                val rest = f.restPercent()
                if (rest <= 0.0001) return@onMain err("合计已经是 ${Ai.num(f.sumPercent())}%，没有剩余可补")
                Repo.I.updateFormulaItem(id, hit.id, null, hit.percent + rest)
                getFormula(now(), id)
            }
            "clear_items" -> onMain {
                val id = a.optString("formulaId")
                val f = now().formulaOf(id) ?: return@onMain err("没有这个配方：$id")
                f.items.map { it.id }.forEach { Repo.I.removeFormulaItem(id, it) }
                getFormula(now(), id)
            }
            "set_formula_meta" -> onMain {
                val id = a.optString("formulaId")
                if (now().formulaOf(id) == null) return@onMain err("没有这个配方：$id")
                Repo.I.updateFormula(id) {
                    if (a.has("purpose")) it.purpose = a.optString("purpose")
                    if (a.has("cure")) it.cure = a.optString("cure")
                }
                getFormula(now(), id)
            }
            "get_products" -> onMain { products(now(), a.optString("query")) }
            "web_search" -> webSearch(a.optString("query"), a.optInt("limit", 5))
            "fetch_url" -> fetchUrl(a.optString("url"))
            "calc" -> {
                try {
                    val v = Calc.eval(a.optString("expression"))
                    Ai.num(v, 6).trimEnd('0').trimEnd('.')
                } catch (e: Throwable) {
                    err("算不出来：${e.message}")
                }
            }
            else -> err("没有这个工具：$name")
        }
    }

    // ---------------------------------------------------------------- 配方读写

    private fun listFormulas(s: AppState): String {
        if (s.formulas.isEmpty()) return "{\"formulas\":[]}"
        val arr = JSONArray()
        s.formulas.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("totalG", round(f.totalG))
                put("items", f.items.size)
                put("sum", round(f.sumPercent()))
            })
        }
        return JSONObject().put("formulas", arr).toString()
    }

    private fun getFormula(s: AppState, id: String): String {
        val f = s.formulaOf(id) ?: return err("没有这个配方：$id")
        val items = JSONArray()
        f.items.forEach { it ->
            items.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("percent", round(it.percent))
                put("grams", round(f.gramsOf(it)))
            })
        }
        return JSONObject().apply {
            put("id", f.id)
            put("name", f.name)
            put("totalG", round(f.totalG))
            put("sum", round(f.sumPercent()))
            put("rest", round(f.restPercent()))
            if (f.purpose.isNotBlank()) put("purpose", f.purpose)
            if (f.cure.isNotBlank()) put("cure", f.cure)
            put("items", items)
        }.toString()
    }

    private fun products(s: AppState, query: String): String {
        val q = query.trim()
        val hit = s.products.filter { q.isBlank() || it.name.contains(q, true) }
        if (hit.isEmpty()) return err("条件表里没有匹配「$q」的产品")
        val arr = JSONArray()
        hit.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("bakes", JSONArray().apply {
                    p.bakes.forEach { b -> put("${b.tempC}℃ ${b.minutes}分") }
                })
            })
        }
        return JSONObject().put("products", arr).toString()
    }

    private fun findItem(f: Formula, key: String): FormulaItem? {
        if (key.isBlank()) return null
        f.items.firstOrNull { it.id == key }?.let { return it }
        f.items.firstOrNull { sameName(it.name, key) }?.let { return it }
        return f.items.firstOrNull { it.name.contains(key, true) || key.contains(it.name, true) }
    }

    private fun noItem(f: Formula, key: String): String =
        "配方里找不到「$key」。现有原料：" + f.items.joinToString("、") { it.name }

    private fun sameName(a: String, b: String): Boolean =
        norm(a) == norm(b)

    private fun norm(s: String): String = s.lowercase()
        .replace(" ", "").replace("　", "")
        .replace("－", "-").replace("（", "(").replace("）", ")")

    // ---------------------------------------------------------------- 联网工具

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun webSearch(query: String, limit: Int): String {
        val q = query.trim()
        if (q.isBlank()) return err("搜索词不能为空")
        val n = limit.coerceIn(1, 8)
        val enc = Ai.encode(q)

        // 两个入口都试：lite 更轻，html 更全；任何一个挂了都不影响另一个
        val endpoints = listOf(
            "https://lite.duckduckgo.com/lite/?q=$enc",
            "https://html.duckduckgo.com/html/?q=$enc",
        )
        var lastError = ""
        for (url in endpoints) {
            val html = try {
                get(url)
            } catch (e: Throwable) {
                lastError = e.message ?: e.javaClass.simpleName
                continue
            }
            if (html.isNullOrBlank()) continue
            val results = parseDdg(html, n)
            if (results.isEmpty()) continue
            val arr = JSONArray()
            results.forEach { (title, url2, snippet) ->
                arr.put(JSONObject().apply {
                    put("title", title)
                    put("url", url2)
                    if (snippet.isNotBlank()) put("snippet", snippet)
                })
            }
            return JSONObject().put("query", q).put("results", arr).toString()
        }
        return err(
            if (lastError.isBlank()) "没搜到结果，换个关键词试试"
            else "搜索失败（可能没网或被拦）：$lastError。可以改用 fetch_url 直接读一个已知网址。"
        )
    }

    private fun parseDdg(html: String, limit: Int): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        // 标题链接：lite 版是 class='result-link'，html 版是 class="result__a"
        val linkRe = Regex(
            """<a\s[^>]*href="([^"]+)"[^>]*class=['"](?:result-link|result__a)['"][^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val links = linkRe.findAll(html).toList()
        // 摘要：lite 版在 <td class="result-snippet">，html 版在 <a class="result__snippet">
        val snips = Regex(
            """<(?:td|a)[^>]*class=['"](?:result-snippet|result__snippet)['"][^>]*>(.*?)</(?:td|a)>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(html).map { strip(it.groupValues[1]).replace(Regex("\\s+"), " ").trim() }.toList()

        links.take(limit).forEachIndexed { i, m ->
            val url = unwrap(m.groupValues[1])
            val title = strip(m.groupValues[2]).replace(Regex("\\s+"), " ").trim()
            if (url.isBlank() || title.isBlank()) return@forEachIndexed
            out.add(Triple(title, url, snips.getOrElse(i) { "" }))
        }
        return out
    }

    /** DuckDuckGo 的链接是 //duckduckgo.com/l/?uddg=<编码后的真实地址> */
    private fun unwrap(href: String): String {
        var h = href
        if (h.startsWith("//")) h = "https:$h"
        val idx = h.indexOf("uddg=")
        if (idx >= 0) {
            val enc = h.substring(idx + 5).substringBefore("&")
            return try {
                java.net.URLDecoder.decode(enc, "UTF-8")
            } catch (_: Throwable) {
                h
            }
        }
        return h
    }

    private fun fetchUrl(url: String): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return err("网址要以 http:// 或 https:// 开头")
        }
        // 只让模型读公网页面：内网/本机地址一律拒掉，
        // 否则网页里夹一段提示词就能让它去扫局域网（SSRF）
        val host = runCatching { java.net.URI(u).host ?: "" }.getOrDefault("")
        if (host.isBlank()) return err("网址看不懂：$u")
        if (isPrivateHost(host)) return err("不读内网/本机地址：$host")
        val raw = try {
            get(u)
        } catch (e: Throwable) {
            return err("打不开：${e.message}")
        } ?: return err("打不开这个网址")
        var text = raw
            .replace(Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|tr)>"), "\n")
        text = strip(text)
            .replace(Regex("[ \\t\\u00a0]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (text.length > 6000) text = text.take(6000) + "…（已截断）"
        return if (text.isBlank()) err("这个页面没有可读的正文") else text
    }

    /** 本机 / 内网 / 链路本地地址：不让 AI 去碰 */
    private fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".localhost") || h == "::1") return true
        if (h.contains(':')) return false // 公网 IPv6 不拦
        val m = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(h) ?: return false
        val a = m.groupValues[1].toIntOrNull() ?: return false
        val b = m.groupValues[2].toIntOrNull() ?: return false
        return when {
            a == 0 || a == 10 || a == 127 -> true
            a == 169 && b == 254 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 100 && b in 64..127 -> true
            else -> false
        }
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** 去掉标签 + 解码常见实体 */
    private fun strip(html: String): String = html
        .replace(Regex("(?s)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: ""
        }

    // ---------------------------------------------------------------- 杂项

    private fun err(msg: String) = JSONObject().put("error", msg).toString()

    private fun round(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** Repo 的写操作要跑在主线程（Compose 收集 StateFlow），这里同步等它做完 */
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var out: T? = null
        var e: Throwable? = null
        main.post {
            try {
                out = block()
            } catch (t: Throwable) {
                e = t
            } finally {
                latch.countDown()
            }
        }
        // 超时不能直接强转 null，否则用户看到的是 NullPointerException，找不到原因
        if (!latch.await(8, TimeUnit.SECONDS)) {
            throw IllegalStateException("主线程忙，操作超时（8 秒）")
        }
        e?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }
}

/** 极简四则运算（支持 + - * / % ^ 与括号，支持负号） */
object Calc {

    fun eval(expr: String): Double {
        val p = Parser(expr.replace("×", "*").replace("÷", "/").replace("（", "(").replace("）", ")"))
        val v = p.expr()
        p.skipSpace()
        if (!p.atEnd()) throw IllegalArgumentException("多余的字符：${p.rest()}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun atEnd() = i >= s.length
        fun rest() = s.substring(i)
        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun expr(): Double {
            var v = term()
            while (true) {
                skipSpace()
                if (atEnd()) return v
                when (s[i]) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        fun term(): Double {
            var v = power()
            while (true) {
                skipSpace()
                if (atEnd()) return v
                when (s[i]) {
                    '*' -> { i++; v *= power() }
                    '/' -> { i++; val d = power(); if (d == 0.0) throw IllegalArgumentException("除数不能是 0"); v /= d }
                    '%' -> { i++; val d = power(); if (d == 0.0) throw IllegalArgumentException("除数不能是 0"); v %= d }
                    else -> return v
                }
            }
        }

        fun power(): Double {
            val base = unary()
            skipSpace()
            if (!atEnd() && s[i] == '^') {
                i++
                return Math.pow(base, power())
            }
            return base
        }

        fun unary(): Double {
            skipSpace()
            if (!atEnd() && (s[i] == '-' || s[i] == '+')) {
                val neg = s[i] == '-'
                i++
                val v = unary()
                return if (neg) -v else v
            }
            return atom()
        }

        fun atom(): Double {
            skipSpace()
            if (atEnd()) throw IllegalArgumentException("表达式不完整")
            if (s[i] == '(') {
                i++
                val v = expr()
                skipSpace()
                if (atEnd() || s[i] != ')') throw IllegalArgumentException("括号没配对")
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("看不懂：${s[i]}")
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("不是数字：${s.substring(start, i)}")
        }
    }
}
