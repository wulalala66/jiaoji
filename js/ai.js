/* ============================================================
   胶记 · AI 助手（网页版）
   OpenAI 兼容接口 + function calling，直接读写本地配方。
   安全约定：
     · 只会把请求发到你填的那个接口地址，没有别的出口；
     · 模型返回的一切内容都当纯文本渲染，先转义再造节点；
     · 工具调用只认白名单里的名字，参数一律校验后再落库。
   ============================================================ */

import { el, num, clear } from "./utils.js";
import { actions, getState, gramsOf, restPercent, sumPercent, subscribe } from "./store.js";
import { toast } from "./ui.js";

// ---------------------------------------------------------------- 工具定义

const TOOL_DEFS = [
  fn("list_formulas", "列出所有配方（id、名称、总量、原料数、合计百分比）", {}, []),
  fn("get_formula", "读取一个配方的完整内容：原料、百分比、克数、合计、剩余", { formulaId: p("string", "配方 id") }, ["formulaId"]),
  fn("create_formula", "新建一个空配方，返回新配方 id", { name: p("string", "配方名称"), totalG: p("number", "一锅总量(g)，默认 600") }, ["name"]),
  fn("set_formula_total", "修改配方的一锅总量（克数会跟着重算，百分比不变）", { formulaId: p("string", "配方 id"), totalG: p("number", "新的总量(g)") }, ["formulaId", "totalG"]),
  fn("add_item", "往配方里加一行原料", { formulaId: p("string", "配方 id"), name: p("string", "原料名"), percent: p("number", "百分比") }, ["formulaId", "name", "percent"]),
  fn("update_item", "改一行原料（按名称或 id 定位）", { formulaId: p("string", "配方 id"), item: p("string", "原料名或 id"), name: p("string", "新名称，不改就不填"), percent: p("number", "新百分比，不改就不填") }, ["formulaId", "item"]),
  fn("remove_item", "从配方里删掉一行原料", { formulaId: p("string", "配方 id"), item: p("string", "原料名或 id") }, ["formulaId", "item"]),
  fn("fill_rest", "把剩余百分比补到某一行（不填 item 就补给占比最大的那行）", { formulaId: p("string", "配方 id"), item: p("string", "原料名或 id，可空") }, ["formulaId"]),
  fn("clear_items", "清空配方的所有原料", { formulaId: p("string", "配方 id") }, ["formulaId"]),
  fn("set_formula_meta", "写配方的实验目的 / 固化温度（会出现在打印的配料单上）", { formulaId: p("string", "配方 id"), purpose: p("string", "实验目的"), cure: p("string", "固化温度，如 140*30") }, ["formulaId"]),
  fn("get_products", "查产品条件表：产品名对应的烘烤温度和时间", { query: p("string", "产品名关键词，可空") }, []),
  fn("calc", "算一个数学表达式，支持 + - * / % ^ 和括号", { expression: p("string", "表达式，例如 (600*26.33)/100") }, ["expression"]),
];

function fn(name, description, props, required) {
  return {
    type: "function",
    function: {
      name,
      description,
      parameters: { type: "object", properties: props, required },
    },
  };
}
function p(type, description) {
  return { type, description };
}

const KNOWN = new Set(TOOL_DEFS.map((t) => t.function.name));

/**
 * 「帮我改」按钮发的话：模型经常只在回复里描述改法、不真的调工具，
 * 点这个按钮就是把「说的」变成「做的」。
 */
export const APPLY_PROMPT =
  "别只描述。按你刚才说的，直接用工具改到配方里，改完用一两句话告诉我改了什么、现在合计多少。";

export function toolLabel(name) {
  return (
    {
      list_formulas: "翻配方列表",
      get_formula: "读配方",
      create_formula: "新建配方",
      set_formula_total: "改总量",
      add_item: "加原料",
      update_item: "改原料比例",
      remove_item: "删原料",
      fill_rest: "补齐到 100%",
      clear_items: "清空原料",
      set_formula_meta: "写实验目的",
      get_products: "查产品条件表",
      calc: "算数",
    }[name] || name
  );
}

// ---------------------------------------------------------------- 客户端

export function normalizeBase(raw) {
  let u = String(raw || "").trim();
  if (!u) return "";
  if (!/^https?:\/\//i.test(u)) u = "http://" + u;
  u = u.replace(/\/+$/, "");
  for (const tail of ["/chat/completions", "/completions", "/models"]) {
    if (u.toLowerCase().endsWith(tail)) u = u.slice(0, -tail.length);
  }
  return u.replace(/\/+$/, "");
}

class AiError extends Error {}

async function request(settings, path, init, signal) {
  const base = normalizeBase(settings.baseUrl);
  if (!base) throw new AiError("还没填接口地址");
  const url = base + path;
  let res;
  try {
    res = await fetch(url, {
      ...init,
      signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + settings.apiKey.trim(),
        ...(init?.headers || {}),
      },
    });
  } catch (e) {
    if (e.name === "AbortError") throw e;
    throw new AiError(
      "连不上接口。常见原因：地址写错、对方不允许跨域（CORS）、网络不通。\n" +
        "浏览器里调用第三方接口需要对方允许跨域，如果一直被拦，用安卓 App 更稳。"
    );
  }
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new AiError(httpMessage(res.status, body));
  }
  return res;
}

function httpMessage(code, body) {
  const snippet = (body || "").trim().slice(0, 300) || "（没有返回内容）";
  const hint =
    code === 401 || code === 403
      ? "Key 无效或没有权限"
      : code === 404
      ? "地址不对：接口地址要填到 /v1 为止"
      : code === 400
      ? "请求被拒绝：多半是模型名不对，或这个模型不支持工具调用"
      : code === 413
      ? "内容太长，超出模型上下文"
      : code === 429
      ? "请求太频繁或额度用完了"
      : code >= 500
      ? "接口服务端出错，稍后再试"
      : "接口返回 " + code;
  return `${hint}（HTTP ${code}）\n${snippet}`;
}

export async function fetchModels(settings) {
  const res = await request(settings, "/models", { method: "GET" });
  const body = await res.json().catch(() => null);
  const arr = body?.data || body?.models || (Array.isArray(body) ? body : null);
  if (!arr) throw new AiError("看不懂这个接口返回的内容");
  const out = [];
  for (const m of arr) {
    const id = typeof m === "string" ? m : m?.id || m?.name || "";
    if (id) out.push(String(id));
  }
  return [...new Set(out)].sort();
}

/** 一次流式请求；返回 { content, thinking, toolCalls } */
async function streamOnce(settings, messages, { tools = true, signal, onDelta, onThink }) {
  const body = {
    model: settings.model,
    stream: true,
    messages,
  };
  if (tools) {
    body.tools = TOOL_DEFS;
    body.tool_choice = "auto";
  }
  if (settings.thinking) body.reasoning_effort = settings.thinking;

  let res;
  try {
    res = await request(settings, "/chat/completions", { method: "POST", body: JSON.stringify(body) }, signal);
  } catch (e) {
    // 有的模型/中转不认 reasoning_effort，去掉再试一次
    if (settings.thinking && /reasoning|effort|thinking|400/i.test(e.message || "")) {
      const retry = { ...body };
      delete retry.reasoning_effort;
      res = await request(settings, "/chat/completions", { method: "POST", body: JSON.stringify(retry) }, signal);
    } else throw e;
  }

  const ct = res.headers.get("content-type") || "";
  if (!ct.includes("event-stream")) {
    // 中转忽略了 stream=true，直接给了整包 JSON
    const json = await res.json();
    const msg = json?.choices?.[0]?.message;
    if (!msg) throw new AiError("返回里没有 message");
    const content = msg.content || "";
    if (content) onDelta?.(content);
    const reasoning = msg.reasoning_content || msg.reasoning || msg.thinking || "";
    if (reasoning) onThink?.(reasoning);
    return {
      content,
      thinking: reasoning,
      toolCalls: (msg.tool_calls || []).map((t, i) => ({
        id: t.id || `call_${i}`,
        name: t.function?.name || "",
        args: t.function?.arguments || "{}",
      })),
    };
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let content = "";
  let thinking = "";
  const calls = new Map();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx;
    while ((idx = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, idx).trim();
      buffer = buffer.slice(idx + 1);
      if (!line.startsWith("data:")) continue;
      const payload = line.slice(5).trim();
      if (payload === "[DONE]") {
        buffer = "";
        break;
      }
      let json;
      try {
        json = JSON.parse(payload);
      } catch {
        continue;
      }
      const delta = json?.choices?.[0]?.delta;
      if (!delta) continue;
      if (delta.content) {
        content += delta.content;
        onDelta?.(delta.content);
      }
      const reason = delta.reasoning_content || delta.reasoning || delta.thinking || "";
      if (reason) {
        thinking += reason;
        onThink?.(reason);
      }
      if (Array.isArray(delta.tool_calls)) {
        for (const t of delta.tool_calls) {
          const i = t.index ?? 0;
          const cur = calls.get(i) || { id: "", name: "", args: "" };
          if (t.id) cur.id = t.id;
          if (t.function?.name) cur.name = t.function.name;
          if (t.function?.arguments) cur.args += t.function.arguments;
          calls.set(i, cur);
        }
      }
    }
  }

  return {
    content,
    thinking,
    toolCalls: [...calls.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([i, c]) => ({ id: c.id || `text_${i}`, name: c.name, args: c.args || "{}" }))
      .filter((c) => c.name),
  };
}

// ---------------------------------------------------------------- 工具执行

function findItem(f, key) {
  const k = String(key || "").trim();
  if (!k) return null;
  return (
    f.items.find((it) => it.id === k) ||
    f.items.find((it) => norm(it.name) === norm(k)) ||
    f.items.find((it) => it.name.toLowerCase().includes(k.toLowerCase()) || k.toLowerCase().includes(it.name.toLowerCase())) ||
    null
  );
}

const norm = (s) =>
  String(s || "")
    .toLowerCase()
    .replace(/[\s\u3000]/g, "")
    .replace(/－/g, "-")
    .replace(/（/g, "(")
    .replace(/）/g, ")");

function getFormulaJson(id) {
  const f = getState().formulas.find((x) => x.id === id);
  if (!f) return { error: `没有这个配方：${id}` };
  return {
    id: f.id,
    name: f.name,
    totalG: round(f.totalG),
    sum: round(sumPercent(f)),
    rest: round(restPercent(f)),
    ...(f.purpose ? { purpose: f.purpose } : {}),
    ...(f.cure ? { cure: f.cure } : {}),
    items: f.items.map((it) => ({
      id: it.id,
      name: it.name,
      percent: round(it.percent),
      grams: round(gramsOf(f, it)),
    })),
  };
}

const round = (v) => Math.round(v * 100) / 100;

export function runTool(name, argsJson) {
  if (!KNOWN.has(name)) return { error: `没有这个工具：${name}` };
  let a;
  try {
    a = argsJson && argsJson.trim() ? JSON.parse(argsJson) : {};
  } catch (e) {
    return { error: "参数不是合法 JSON：" + e.message };
  }
  const state = getState();

  try {
    switch (name) {
      case "list_formulas":
        return {
          formulas: state.formulas.map((f) => ({
            id: f.id,
            name: f.name,
            totalG: round(f.totalG),
            items: f.items.length,
            sum: round(sumPercent(f)),
          })),
        };

      case "get_formula":
        return getFormulaJson(String(a.formulaId || ""));

      case "create_formula": {
        const id = actions.addFormula(String(a.name || "新配方"), num(a.totalG) ?? 600);
        return { ok: true, formulaId: id };
      }

      case "set_formula_total": {
        const id = String(a.formulaId || "");
        if (!state.formulas.some((f) => f.id === id)) return { error: `没有这个配方：${id}` };
        const v = num(a.totalG);
        if (v === null || v <= 0) return { error: "总量要大于 0" };
        actions.updateFormula(id, { totalG: v });
        return getFormulaJson(id);
      }

      case "add_item": {
        const id = String(a.formulaId || "");
        const f = state.formulas.find((x) => x.id === id);
        if (!f) return { error: `没有这个配方：${id}` };
        const nm = String(a.name || "").trim();
        const pct = num(a.percent);
        if (!nm) return { error: "原料名不能为空" };
        if (pct === null || pct <= 0) return { error: "百分比要大于 0" };
        const exist = f.items.find((it) => norm(it.name) === norm(nm));
        if (exist) actions.updateItem(id, exist.id, { percent: pct });
        else actions.addItem(id, nm, pct);
        return getFormulaJson(id);
      }

      case "update_item": {
        const id = String(a.formulaId || "");
        const f = state.formulas.find((x) => x.id === id);
        if (!f) return { error: `没有这个配方：${id}` };
        const hit = findItem(f, a.item);
        if (!hit) return { error: `配方里找不到「${a.item}」。现有原料：${f.items.map((i) => i.name).join("、")}` };
        const patch = {};
        if (typeof a.name === "string" && a.name.trim()) patch.name = a.name.trim();
        if (a.percent !== undefined) {
          const v = num(a.percent);
          if (v === null) return { error: "百分比不是数字" };
          patch.percent = v;
        }
        actions.updateItem(id, hit.id, patch);
        return getFormulaJson(id);
      }

      case "remove_item": {
        const id = String(a.formulaId || "");
        const f = state.formulas.find((x) => x.id === id);
        if (!f) return { error: `没有这个配方：${id}` };
        const hit = findItem(f, a.item);
        if (!hit) return { error: `配方里找不到「${a.item}」。现有原料：${f.items.map((i) => i.name).join("、")}` };
        actions.removeItem(id, hit.id);
        return getFormulaJson(id);
      }

      case "fill_rest": {
        const id = String(a.formulaId || "");
        const f = state.formulas.find((x) => x.id === id);
        if (!f) return { error: `没有这个配方：${id}` };
        if (!f.items.length) return { error: "配方里还没有原料，先 add_item" };
        const hit = a.item ? findItem(f, a.item) : [...f.items].sort((x, y) => y.percent - x.percent)[0];
        if (!hit) return { error: `配方里找不到「${a.item}」` };
        const rest = restPercent(f);
        if (rest <= 0.0001) return { error: `合计已经是 ${round(sumPercent(f))}%，没有剩余可补` };
        actions.updateItem(id, hit.id, { percent: hit.percent + rest });
        return getFormulaJson(id);
      }

      case "clear_items": {
        const id = String(a.formulaId || "");
        const f = state.formulas.find((x) => x.id === id);
        if (!f) return { error: `没有这个配方：${id}` };
        f.items.slice().forEach((it) => actions.removeItem(id, it.id));
        return getFormulaJson(id);
      }

      case "set_formula_meta": {
        const id = String(a.formulaId || "");
        if (!state.formulas.some((f) => f.id === id)) return { error: `没有这个配方：${id}` };
        const patch = {};
        if (typeof a.purpose === "string") patch.purpose = a.purpose;
        if (typeof a.cure === "string") patch.cure = a.cure;
        actions.updateFormula(id, patch);
        return getFormulaJson(id);
      }

      case "get_products": {
        const q = String(a.query || "").trim().toLowerCase();
        const hit = state.products.filter((x) => !q || x.name.toLowerCase().includes(q));
        if (!hit.length) return { error: `条件表里没有匹配「${a.query}」的产品` };
        return { products: hit.map((x) => ({ name: x.name, bakes: x.bakes.map((b) => `${b.temp}℃ ${b.minutes}分`) })) };
      }

      case "calc": {
        const v = calcExpr(String(a.expression || ""));
        return { result: String(Math.round(v * 1e6) / 1e6) };
      }

      default:
        return { error: `没有这个工具：${name}` };
    }
  } catch (e) {
    return { error: "工具执行失败：" + (e.message || e) };
  }
}

/** 极简四则运算（不用 eval，避免执行任意代码） */
export function calcExpr(src) {
  const s = String(src).replace(/×/g, "*").replace(/÷/g, "/").replace(/（/g, "(").replace(/）/g, ")");
  let i = 0;
  const ws = () => {
    while (i < s.length && /\s/.test(s[i])) i++;
  };
  function expr() {
    let v = term();
    for (;;) {
      ws();
      if (s[i] === "+") {
        i++;
        v += term();
      } else if (s[i] === "-") {
        i++;
        v -= term();
      } else return v;
    }
  }
  function term() {
    let v = power();
    for (;;) {
      ws();
      if (s[i] === "*") {
        i++;
        v *= power();
      } else if (s[i] === "/") {
        i++;
        const d = power();
        if (d === 0) throw new Error("除数不能是 0");
        v /= d;
      } else if (s[i] === "%") {
        i++;
        const d = power();
        if (d === 0) throw new Error("除数不能是 0");
        v %= d;
      } else return v;
    }
  }
  function power() {
    const base = unary();
    ws();
    if (s[i] === "^") {
      i++;
      return Math.pow(base, power());
    }
    return base;
  }
  function unary() {
    ws();
    if (s[i] === "-" || s[i] === "+") {
      const neg = s[i] === "-";
      i++;
      const v = unary();
      return neg ? -v : v;
    }
    return atom();
  }
  function atom() {
    ws();
    if (s[i] === "(") {
      i++;
      const v = expr();
      ws();
      if (s[i] !== ")") throw new Error("括号没配对");
      i++;
      return v;
    }
    const start = i;
    while (i < s.length && /[0-9.]/.test(s[i])) i++;
    if (start === i) throw new Error("看不懂：" + (s[i] ?? "结尾"));
    const v = Number(s.slice(start, i));
    if (!Number.isFinite(v)) throw new Error("不是数字");
    return v;
  }
  const v = expr();
  ws();
  if (i < s.length) throw new Error("多余的字符：" + s.slice(i));
  return v;
}

// ---------------------------------------------------------------- 会话

const sessions = new Map();

export function sessionOf(formulaId) {
  if (!sessions.has(formulaId)) {
    sessions.set(formulaId, {
      messages: [], // { role, content, toolCalls?, toolCallId?, name? }
      busy: false,
      stream: "",
      think: "",
      status: "",
      error: "",
      startedAt: 0,
      thinkMs: 0,
      controller: null,
      listeners: new Set(),
    });
  }
  return sessions.get(formulaId);
}

function notify(s) {
  for (const fn of s.listeners) fn(s);
}

export function subscribeSession(s, fn) {
  s.listeners.add(fn);
  return () => s.listeners.delete(fn);
}

export function systemPrompt(settings) {
  return (
    settings.systemPrompt ||
    `你是「胶记」里的配料助手，服务对象是胶粘剂实验室的工程师。
说话简短、直接给结论和数字，别客套。回答用中文。

你可以直接读写用户的配料单（工具里带 formulaId 的那种），规则：
- 百分比是唯一准的东西，克数 = 百分比 × 总量 / 100，所以改配方优先改百分比。
- 加料前先看一眼现有配方，别把同名原料加两遍；要加就 add_item，要改就 update_item。
- 用户说「补到 100%」时，把差额加到最合适的那个原料上（fill_rest 也能用）。
- 总量一般是 600g 一锅，用户没说就别乱改总量。
- 算数用 calc，不要心算。
- 每次动完配料单，用一两句话说明「加了什么、现在合计多少、还剩多少」。
- 改配方必须真的调用工具。绝对不要把"要改的内容"写成一段 JSON 或表格丢给用户自己改。
- 这个网页版没有联网搜索工具；需要查资料时，直接说明你查不到，并给出经验判断。`
  );
}

function toApiMessage(m) {
  if (m.role === "assistant" && m.toolCalls?.length) {
    return {
      role: "assistant",
      content: m.content || null,
      tool_calls: m.toolCalls.map((c) => ({
        id: c.id,
        type: "function",
        function: { name: c.name, arguments: c.args || "{}" },
      })),
    };
  }
  if (m.role === "tool") return { role: "tool", content: m.content, tool_call_id: m.toolCallId, name: m.name };
  return { role: m.role, content: m.content };
}

/** 太长就把前面的对话压成摘要 */
async function maybeCompress(settings, messages, signal) {
  if (!settings.compress) return messages;
  const size = messages.reduce((s, m) => s + (m.content?.length || 0), 0);
  if (size < 24000 || messages.length <= 8) return messages;

  const head = messages.slice(0, -6);
  const tail = messages.slice(-6);
  const transcript = head
    .map((m) => `${m.role === "user" ? "用户" : m.role === "assistant" ? "助手" : "工具"}：${(m.content || "").slice(0, 600)}`)
    .join("\n")
    .slice(0, 30000);

  try {
    const r = await streamOnce(
      { ...settings, thinking: "" },
      [
        { role: "system", content: "把下面的对话压缩成要点，保留配方数据、用户的偏好和结论，不要客套话。" },
        { role: "user", content: transcript },
      ],
      { tools: false, signal }
    );
    if (!r.content.trim()) return messages;
    return [{ role: "system", content: "（之前对话的摘要）\n" + r.content.trim() }, ...tail];
  } catch {
    return messages;
  }
}

export async function send(formulaId, text) {
  const s = sessionOf(formulaId);
  const clean = String(text || "").trim();
  if (!clean || s.busy) return;
  s.messages.push({ role: "user", content: clean });
  await run(formulaId);
}

export async function regenerate(formulaId) {
  const s = sessionOf(formulaId);
  if (s.busy) return;
  while (s.messages.length && s.messages[s.messages.length - 1].role !== "user") s.messages.pop();
  if (!s.messages.length) return;
  await run(formulaId);
}

export function stop(formulaId) {
  const s = sessionOf(formulaId);
  s.controller?.abort();
  s.controller = null;
  s.busy = false;
  s.stream = "";
  s.think = "";
  s.status = "";
  notify(s);
}

export function clearSession(formulaId) {
  const s = sessionOf(formulaId);
  s.controller?.abort();
  s.messages = [];
  s.busy = false;
  s.stream = "";
  s.think = "";
  s.status = "";
  s.error = "";
  s.thinkMs = 0;
  notify(s);
}

async function run(formulaId) {
  const s = sessionOf(formulaId);
  const settings = getState().settings.ai;

  if (!settings.baseUrl || !settings.apiKey || !settings.model) {
    s.error = "还没配置 AI：去「设置 → AI 助手」填接口地址、Key，再拉取模型";
    notify(s);
    return;
  }

  s.busy = true;
  s.error = "";
  s.stream = "";
  s.think = "";
  s.status = "";
  s.startedAt = Date.now();
  s.thinkMs = 0;
  s.controller = new AbortController();
  notify(s);

  const signal = s.controller.signal;
  let thinkAt = 0;

  try {
    let history = [
      { role: "system", content: systemPrompt(settings) },
      ...(await maybeCompress(settings, s.messages, signal)),
    ];

    for (let round = 0; round < 6; round++) {
      s.stream = "";
      s.status = round === 0 ? "思考中…" : "接着处理…";
      notify(s);

      const reply = await streamOnce(
        settings,
        history.map(toApiMessage),
        {
          signal,
          onDelta: (piece) => {
            s.stream += piece;
            notify(s);
          },
          onThink: (piece) => {
            if (!thinkAt) thinkAt = Date.now();
            s.think += piece;
            notify(s);
          },
        }
      );

      if (thinkAt) {
        s.thinkMs = Date.now() - thinkAt;
        thinkAt = 0;
      }

      const assistant = {
        role: "assistant",
        content: reply.content,
        toolCalls: reply.toolCalls,
        thinking: reply.thinking,
      };
      s.messages.push(assistant);
      history = [...history, assistant];

      if (!reply.toolCalls.length) {
        s.stream = "";
        break;
      }

      for (const call of reply.toolCalls) {
        s.status = "正在" + toolLabel(call.name) + "…";
        notify(s);
        const result = runTool(call.name, call.args);
        const content = JSON.stringify(result);
        const toolMsg = { role: "tool", content, toolCallId: call.id, name: call.name };
        s.messages.push(toolMsg);
        history = [...history, toolMsg];
      }
      s.stream = "";
      notify(s);

      if (round === 5) {
        s.messages.push({
          role: "assistant",
          content: "这个我来回查了好几次还没定下来（工具调用到上限了）。把要求说得再具体一点，我再改一次。",
        });
      }
    }
  } catch (e) {
    if (e.name === "AbortError") {
      s.error = "已停止";
    } else {
      s.error = e.message || "请求失败";
    }
  } finally {
    s.stream = "";
    s.think = "";
    s.status = "";
    s.busy = false;
    s.controller = null;
    notify(s);
  }
}

/** 从配方页点「问一下」：先切到对话页，再发这条问题 */
export function askAboutFormula(formulaId, text) {
  send(formulaId, text);
}

// ---------------------------------------------------------------- 安全渲染

/**
 * 把模型回复渲染成节点：先按纯文本处理，再只对 **粗体** 和 `代码`
 * 做最小的样式化。任何 HTML 标签都会被当成普通文字显示。
 */
export function renderRich(text) {
  const box = el("div", { class: "md" });
  const lines = String(text || "").split("\n");
  let code = null;

  const flushCode = () => {
    if (code) {
      box.appendChild(el("pre", { class: "md-code" }, code.join("\n")));
      code = null;
    }
  };

  for (const raw of lines) {
    if (/^\s*```/.test(raw)) {
      if (code) flushCode();
      else {
        code = [];
        code.lang = raw.replace(/^\s*```/, "").trim();
      }
      continue;
    }
    if (code) {
      code.push(raw);
      continue;
    }
    if (!raw.trim()) {
      box.appendChild(el("div", { class: "md-gap" }));
      continue;
    }
    const bullet = raw.match(/^\s*[-*·]\s+(.*)$/);
    if (bullet) {
      box.appendChild(el("div", { class: "md-li" }, ...inline(bullet[1])));
      continue;
    }
    const ordered = raw.match(/^\s*(\d+)[.)]\s+(.*)$/);
    if (ordered) {
      box.appendChild(el("div", { class: "md-li" }, el("span", { class: "md-num" }, ordered[1] + "."), ...inline(ordered[2])));
      continue;
    }
    const heading = raw.match(/^(#{1,4})\s+(.*)$/);
    if (heading) {
      box.appendChild(el("div", { class: "md-h" }, ...inline(heading[2])));
      continue;
    }
    box.appendChild(el("div", { class: "md-p" }, ...inline(raw)));
  }
  flushCode();
  return box;
}

function inline(text) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(document.createTextNode(text.slice(last, m.index)));
    const token = m[0];
    if (token.startsWith("**")) out.push(el("strong", null, token.slice(2, -2)));
    else out.push(el("code", { class: "md-inline" }, token.slice(1, -1)));
    last = m.index + token.length;
  }
  if (last < text.length) out.push(document.createTextNode(text.slice(last)));
  return out;
}

// ---------------------------------------------------------------- 对话页

export function renderAiChat(root, formulaId, { navigate }) {
  const s = sessionOf(formulaId);
  const f = getState().formulas.find((x) => x.id === formulaId);

  document.body.classList.add("chat-mode");

  const list = el("div", { class: "chat-list" });
  const jump = el(
    "button",
    {
      class: "jump-bottom",
      type: "button",
      hidden: "true",
      "aria-label": "回到底部",
      onclick: () => {
        pinned = true;
        list.scrollTop = list.scrollHeight;
        jump.hidden = true;
      },
    },
    "↓ 最新"
  );

  const input = el("textarea", {
    class: "chat-input",
    rows: "1",
    placeholder: "说要改什么，比如：D1NP 提到 30%，其余按比例缩小",
    oninput: (e) => {
      e.target.style.height = "auto";
      e.target.style.height = Math.min(120, e.target.scrollHeight) + "px";
    },
    onkeydown: (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        submit();
      }
    },
  });

  const sendBtn = el("button", { class: "btn btn-primary chat-send", type: "button" }, "发送");

  function submit() {
    const text = input.value.trim();
    if (!text) return;
    const cur = getState().settings.ai;
    if (!cur.baseUrl || !cur.apiKey || !cur.model) {
      toast("先去设置里配好 AI", "warn");
      navigate("#/settings");
      return;
    }
    input.value = "";
    input.style.height = "auto";
    pinned = true;
    send(formulaId, text);
  }

  root.appendChild(
    el(
      "header",
      { class: "page-head chat-head" },
      el("button", { class: "icon-btn", type: "button", "aria-label": "返回", onclick: () => navigate(`#/f/${formulaId}`) }, "←"),
      el("div", { class: "grow" }, el("h1", { class: "ellipsis" }, "AI 助手"), el("p", { class: "page-sub ellipsis" }, f?.name || "配方")),
      el(
        "button",
        {
          class: "btn btn-ghost btn-sm",
          type: "button",
          onclick: () => {
            if (!s.messages.length) return;
            clearSession(formulaId);
          },
        },
        "清空"
      )
    )
  );

  root.appendChild(el("div", { class: "chat-stage" }, list, jump));
  root.appendChild(el("div", { class: "chat-composer" }, input, sendBtn));

  // ---------------------------------------------------------------- 滚动
  // 贴底才跟随：用户往上翻历史时不被新 token 拽回去，
  // 这是主流对话 App（ChatGPT / Claude / LobeChat）的通用做法。
  let pinned = true;
  const NEAR = 72;

  list.addEventListener(
    "scroll",
    () => {
      const gap = list.scrollHeight - list.scrollTop - list.clientHeight;
      pinned = gap < NEAR;
      jump.hidden = pinned || !s.busy;
    },
    { passive: true }
  );

  const scrollToBottom = () => {
    if (!pinned) return;
    list.scrollTop = list.scrollHeight;
  };

  // ---------------------------------------------------------------- 渲染

  let signature = "";
  let streamNode = null;
  let statusNode = null;

  const bubble = (cls, ...kids) => el("div", { class: `bubble ${cls}` }, ...kids);

  function toolLine(names) {
    return el(
      "div",
      { class: "tool-line" },
      names.map((n) => el("span", { class: "tool-pill" }, "🔧 " + toolLabel(n)))
    );
  }

  function fullRender() {
    clear(list);

    if (!s.messages.length && !s.busy) {
      list.appendChild(
        el(
          "div",
          { class: "chat-intro" },
          el("div", { class: "chat-intro-emoji" }, "🤖"),
          el("p", null, "我能直接读你正在看的这张配料单，也能改它。"),
          el(
            "div",
            { class: "chat-samples" },
            ...[
              "合计到 100% 了吗？没到就补上",
              "D1NP 提到 30%，其余按比例缩小",
              "帮我看看这个配比有什么问题",
            ].map((t) => el("button", { class: "chip-btn", type: "button", onclick: () => send(formulaId, t) }, t))
          )
        )
      );
    }

    s.messages.forEach((m, index) => {
      if (m.role === "user") {
        list.appendChild(bubble("bubble-user", m.content));
        return;
      }
      if (m.role === "assistant") {
        if (m.toolCalls?.length) list.appendChild(toolLine(m.toolCalls.map((c) => c.name)));
        if (m.thinking) {
          list.appendChild(
            el(
              "details",
              { class: "think-card" },
              el("summary", null, "思考过程 · " + m.thinking.length + " 字"),
              el("div", { class: "think-body" }, m.thinking)
            )
          );
        }
        if (m.content) {
          list.appendChild(bubble("bubble-ai", renderRich(m.content)));
          const isLast = index === s.messages.length - 1;
          const acts = el(
            "div",
            { class: "bubble-acts" },
            el("button", { class: "chip-btn", type: "button", onclick: () => copyText(m.content).then((ok) => toast(ok ? "已复制" : "复制失败", ok ? "ok" : "warn")) }, "复制"),
            // 模型只"说"没"做"的时候，给它一个一键落地的按钮
            !m.toolCalls?.length
              ? el("button", { class: "chip-btn on", type: "button", onclick: () => send(formulaId, APPLY_PROMPT) }, "帮我改")
              : null,
            isLast && !s.busy ? el("button", { class: "chip-btn", type: "button", onclick: () => regenerate(formulaId) }, "重新生成") : null
          );
          list.appendChild(acts);
        }
        return;
      }
      if (m.role === "tool") {
        list.appendChild(
          el("div", { class: "tool-line" }, el("span", { class: "tool-pill" }, "✅ " + (m.name ? toolLabel(m.name) : "完成")))
        );
      }
    });

    if (s.error && !s.busy) {
      list.appendChild(
        el(
          "div",
          { class: "chat-error" },
          el("div", null, s.error),
          el("button", { class: "btn btn-soft btn-sm", type: "button", onclick: () => regenerate(formulaId) }, "重试")
        )
      );
    }

    // 流式占位：先把节点建好，之后只改里面的文字，不整段重建
    if (s.busy) {
      const details = el(
        "details",
        { class: "think-card live", hidden: "true" },
        el("summary", null, "思考中…"),
        el("div", { class: "think-body" })
      );
      list.appendChild(details);

      statusNode = el("div", { class: "tool-line" }, el("span", { class: "tool-pill live" }, "⏳ 思考中…"));
      list.appendChild(statusNode);

      streamNode = bubble("bubble-ai");
      streamNode.hidden = true;
      list.appendChild(streamNode);

      list.appendChild(el("div", { class: "typing" }, el("span"), el("span"), el("span")));
    } else {
      streamNode = null;
      statusNode = null;
    }
  }

  function updateLive() {
    if (!s.busy) return;
    const details = list.querySelector(".think-card.live");
    if (details) {
      const body = details.querySelector(".think-body");
      if (s.think) {
        details.hidden = false;
        if (body.textContent !== s.think) body.textContent = s.think;
      } else {
        details.hidden = true;
      }
    }
    if (statusNode) {
      const pill = statusNode.querySelector(".tool-pill");
      const text = "⏳ " + (s.status || "思考中…");
      if (pill.textContent !== text) pill.textContent = text;
    }
    if (streamNode) {
      if (s.stream) {
        streamNode.replaceChildren(renderRich(s.stream));
        list.querySelector(".typing")?.remove();
      }
      streamNode.hidden = !s.stream;
    }
  }

  function draw() {
    const next = [s.messages.length, s.busy, s.error, s.status].join("|");
    if (next !== signature) {
      signature = next;
      fullRender();
    }
    updateLive();

    sendBtn.textContent = s.busy ? "停止" : "发送";
    sendBtn.classList.toggle("btn-danger", s.busy);
    sendBtn.classList.toggle("btn-primary", !s.busy);
    sendBtn.onclick = s.busy ? () => stop(formulaId) : () => submit();

    jump.hidden = pinned || !s.busy;
    requestAnimationFrame(scrollToBottom);
  }

  draw();
  const off = subscribeSession(s, draw);

  return () => {
    off();
    document.body.classList.remove("chat-mode");
  };
}
