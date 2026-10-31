/* ============================================================
   胶记 · utilities
   ============================================================ */
export const $ = (sel, el = document) => el.querySelector(sel);
export const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];

export const DAY_MS = 86400000;

export function pad(n) {
  return String(n).padStart(2, "0");
}

/** local yyyy-mm-dd */
export function dateKey(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** local HH:MM */
export function hm(d) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** HH:MM:SS for logs */
export function hms(d) {
  return `${hm(d)}:${pad(d.getSeconds())}`;
}

/** Date from yyyy-mm-dd + HH:MM (local) */
export function at(dateStr, timeStr) {
  const [y, m, dd] = dateStr.split("-").map(Number);
  const [hh, mm] = timeStr.split(":").map(Number);
  return new Date(y, m - 1, dd, hh, mm, 0, 0);
}

const WD = ["日", "一", "二", "三", "四", "五", "六"];

export function weekday(d) {
  return `周${WD[d.getDay()]}`;
}

/** 今天 · 昨天 · M月D日 周X */
export function dayLabel(d, { short = false } = {}) {
  const today = dateKey();
  const that = dateKey(d);
  if (today === that) return "今天";
  if (today === dateKey(new Date(d.getTime() - DAY_MS))) return "昨天";
  const base = `${d.getMonth() + 1}月${d.getDate()}日`;
  return short ? base : `${base} ${weekday(d)}`;
}

/** duration ms -> "X分Y秒" / "X小时Y分" */
export function durText(ms) {
  const s = Math.max(0, Math.round(ms / 1000));
  if (s < 60) return `${s}秒`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}分${s % 60 ? `${s % 60}秒` : ""}`;
  const h = Math.floor(m / 60);
  return `${h}小时${m % 60 ? `${m % 60}分` : ""}`;
}

export function mmss(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${pad(Math.floor(s / 60))}:${pad(s % 60)}`;
}

export function hmsClock(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : mmss(ms);
}

export function uid() {
  return Math.random().toString(36).slice(2, 8) + Date.now().toString(36).slice(-4);
}

export function esc(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** number formatting for test values */
export function fmtNum(v) {
  if (v == null || v === "") return "";
  const n = Number(v);
  if (!Number.isFinite(n)) return String(v);
  if (Number.isInteger(n)) return String(n);
  return String(Math.round(n * 100) / 100);
}

export function fmtRange(min, max) {
  const f = (x) => (x == null || x === "" ? null : fmtNum(x));
  const a = f(min);
  const b = f(max);
  if (a == null && b == null) return "";
  if (a != null && b != null) return `${a} ~ ${b}`;
  return a != null ? `≥ ${a}` : `≤ ${b}`;
}

export function clamp(n, a, b) {
  return Math.min(b, Math.max(a, n));
}

export function debounce(fn, ms) {
  let t;
  const f = (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
  f.now = (...args) => {
    clearTimeout(t);
    fn(...args);
  };
  return f;
}

export function download(name, content, mime = "text/plain") {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

export function csvEscape(v) {
  const s = String(v ?? "");
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** open file picker, resolve text content */
export function pickFile(accept = ".json,application/json") {
  return new Promise((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = accept;
    input.onchange = () => {
      const file = input.files?.[0];
      if (!file) return resolve(null);
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ""));
      reader.onerror = () => resolve(null);
      reader.readAsText(file);
    };
    input.click();
  });
}
