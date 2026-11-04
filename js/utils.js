/* ============================================================
   胶记 · 基础工具：DOM 构造、数字格式化、安全的文本处理
   所有动态内容一律走 textContent / createTextNode，
   这里不提供任何「把字符串当 HTML 塞进去」的入口。
   ============================================================ */

const SVG_NS = "http://www.w3.org/2000/svg";

/** 只允许这几种标签，杜绝拼写错误导致塞进 script */
const SAFE_TAGS = new Set([
  "div", "span", "p", "section", "article", "header", "footer", "nav", "main",
  "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd",
  "button", "label", "input", "textarea", "select", "option", "form",
  "table", "thead", "tbody", "tr", "th", "td", "caption", "colgroup", "col",
  "strong", "b", "em", "i", "small", "code", "pre", "br", "hr", "svg", "path",
  "circle", "rect", "line", "polyline", "polygon", "g", "defs", "use", "img",
  "a", "dialog", "details", "summary", "time", "mark", "kbd", "figure", "figcaption",
]);

const SAFE_ATTRS = new Set([
  "class", "id", "type", "value", "placeholder", "title", "role", "tabindex",
  "disabled", "readonly", "checked", "selected", "multiple", "rows", "cols",
  "min", "max", "step", "maxlength", "inputmode", "autocomplete", "autocapitalize",
  "spellcheck", "for", "name", "href", "target", "rel", "src", "alt", "width",
  "height", "viewBox", "fill", "stroke", "stroke-width", "stroke-linecap",
  "stroke-linejoin", "d", "cx", "cy", "r", "x", "y", "x1", "y1", "x2", "y2",
  "points", "transform", "aria-label", "aria-hidden", "aria-live", "aria-selected",
  "aria-expanded", "aria-controls", "data-id", "data-tab", "data-act", "data-role",
  "contenteditable", "hidden", "draggable", "xmlns", "preserveAspectRatio",
  "download", "open", "colspan", "rowspan",
]);

/**
 * 造一个元素。attrs 里的值一律走 setAttribute / 属性赋值，不解析 HTML。
 * children 里的字符串会变成文本节点 —— 天然转义。
 */
export function el(tag, attrs = {}, ...children) {
  const name = String(tag).toLowerCase();
  if (!SAFE_TAGS.has(name)) throw new Error("unsupported tag: " + name);
  const node = name === "svg" || ["path", "circle", "rect", "line", "polyline", "polygon", "g", "defs", "use"].includes(name)
    ? document.createElementNS(SVG_NS, name)
    : document.createElement(name);

  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k.startsWith("on") && typeof v === "function") {
      node.addEventListener(k.slice(2).toLowerCase(), v);
      continue;
    }
    if (k === "class") { node.setAttribute("class", v); continue; }
    if (k === "text") { node.textContent = String(v); continue; }
    if (k === "value") { node.value = String(v); continue; }
    if (k === "style" && typeof v === "object") {
      for (const [p, val] of Object.entries(v)) node.style.setProperty(p, val);
      continue;
    }
    if (!SAFE_ATTRS.has(k)) continue;
    if (v === true) node.setAttribute(k, "");
    else node.setAttribute(k, String(v));
  }

  append(node, children);
  return node;
}

export function append(node, children) {
  for (const c of children.flat(6)) {
    if (c === null || c === undefined || c === false) continue;
    node.appendChild(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
}

export function $(sel, root = document) {
  return root.querySelector(sel);
}

/** 只有 http/https 才允许当链接，避免 javascript: 之类的伪协议 */
export function safeUrl(raw) {
  const s = String(raw || "").trim();
  if (!s) return "";
  try {
    const u = new URL(s, location.href);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : "";
  } catch {
    return "";
  }
}

// ---------------------------------------------------------------- 数字

/** 把用户输入的「1,5」「 2.0 」读成数字；读不出来返回 null（不是 0） */
export function num(v) {
  if (typeof v === "number") return Number.isFinite(v) ? v : null;
  const s = String(v ?? "").trim().replace(/,/g, ".");
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

export function fixed(v, digits = 1) {
  if (v === null || v === undefined || !Number.isFinite(v)) return "—";
  return v.toFixed(digits);
}

/** 去掉多余的小数零：26.33 → 26.33，40 → 40 */
export function trimNum(v, digits = 2) {
  if (!Number.isFinite(v)) return "0";
  const s = v.toFixed(digits);
  return s.includes(".") ? s.replace(/0+$/, "").replace(/\.$/, "") : s;
}

export function clamp(v, lo, hi) {
  return Math.min(hi, Math.max(lo, v));
}

// ---------------------------------------------------------------- 杂项

export function uid() {
  const a = new Uint8Array(6);
  crypto.getRandomValues(a);
  return Array.from(a, (b) => b.toString(16).padStart(2, "0")).join("");
}

export function debounce(fn, ms) {
  let t = 0;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
}

export function todayText() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = el("a", { href: url, download: filename });
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}
