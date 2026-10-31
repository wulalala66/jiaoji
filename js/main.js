/* ============================================================
   胶记 · app shell: tabs, routing, engine start
   ============================================================ */
import { $, esc } from "./utils.js";
import { getState, subscribe } from "./store.js";
import { icon, toast, openSheet, closeSheet, confirmDialog } from "./ui.js";
import { renderToday, emptyState } from "./views/today.js";
import { renderOven, goToOven } from "./views/ovenview.js";
import { renderRecords } from "./views/records.js";
import { renderSettings } from "./views/settings.js";
import { renderSampleDetail, openSampleDetail } from "./views/detail.js";
import { openCompose } from "./forms.js";
import { startEngine, onAlarm, acknowledge, snooze, checkNow } from "./alarms.js";
import { getState as getSt } from "./store.js";

let view = $("#view");
let route = "today";
let renderQueued = false;

/* ---------- routing ---------- */

export function go(r, opts = {}) {
  route = r;
  closeSheet();
  window.scrollTo({ top: 0 });
  render(opts);
}

function updateBadges(st) {
  // badge on oven tab: number of due/running bakes; on records: today samples
  const ovenTab = document.querySelector('.tab[data-tab="oven"]');
  const recTab = document.querySelector('.tab[data-tab="records"]');
  if (!ovenTab || !recTab) return;
  const due = st.bakes.filter((b) => b.status === "due").length;
  const running = st.bakes.filter((b) => b.status === "running").length;
  const badge = (tab, n) => {
    let el = tab.querySelector(".tab-badge");
    if (n > 0) {
      if (!el) {
        el = document.createElement("span");
        el.className = "tab-badge";
        tab.appendChild(el);
      }
      el.textContent = String(n);
    } else if (el) el.remove();
  };
  badge(ovenTab, due > 0 ? due : running);
  const dk = new Date();
  const k = `${dk.getFullYear()}-${String(dk.getMonth() + 1).padStart(2, "0")}-${String(dk.getDate()).padStart(2, "0")}`;
  const todayCount = st.samples.filter((s) => new Date(s.sampledAt).toISOString().slice(0, 10) === new Date(dk - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)).length;
  badge(recTab, 0); // keep records clean; samples visible on Today
}

function render(opts = {}) {
  const st = getState();
  document.querySelectorAll(".tab").forEach((t) => {
    t.classList.toggle("on", t.dataset.tab === route);
  });
  // clear stale listeners: replace node
  const fresh = view.cloneNode(false);
  view.parentNode.replaceChild(fresh, view);
  view = fresh;
  view.tabIndex = -1;

  if (route === "today") renderToday(view);
  else if (route === "oven") renderOven(view);
  else if (route === "records") renderRecords(view);
  else if (route === "settings") renderSettings(view);
  else if (route === "sample") renderSampleDetail(view);

  updateBadges(getState());
}

/* ---------- store → rerender (skip while typing in inputs) ---------- */

subscribe(() => {
  const active = document.activeElement;
  const typing =
    active &&
    (active.tagName === "INPUT" || active.tagName === "TEXTAREA" || active.isContentEditable) &&
    route === "sample";
  if (typing) return; // don't yank the keyboard while entering data
  if (renderQueued) return;
  renderQueued = true;
  requestAnimationFrame(() => {
    renderQueued = false;
    render();
  });
});

/* ---------- tabs ---------- */

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    const r = tab.dataset.tab;
    if (r) go(r);
  });
});

/* ---------- FAB compose ---------- */

$("#fab").addEventListener("click", () => openCompose());

/* ---------- ring banner ---------- */

let ringItems = [];

onAlarm((items) => {
  ringItems = items;
  renderRingBar();
  if (items.length) {
    // auto-refresh view to show due state
    render();
  }
});

function renderRingBar() {
  let bar = $("#ring-bar");
  if (!ringItems.length) {
    if (bar) bar.remove();
    return;
  }
  if (!bar) {
    bar = document.createElement("div");
    bar.id = "ring-bar";
    bar.className = "alarm-bar";
    document.body.appendChild(bar);
  }
  const first = ringItems[0];
  const b = first.kind === "due" ? getState().bakes.find((x) => x.id === first.id) : null;
  const t = first.kind === "sample" || first.kind === "chime" ? getState().sampling.find((x) => x.id === first.id) : null;
  const label = b
    ? `${b.oven === "door" ? "靠门" : "靠窗"}烘箱 · ${b.label}`
    : t
      ? first.kind === "chime"
        ? `快到取样时间 · ${t.customer} ${fmtClockHM(t.dueAt)}`
        : `取样时间到 · ${t.customer}`
      : "提醒";
  bar.innerHTML = `
    <span class="ab-icon">${icon("bell")}</span>
    <div class="ab-main">
      <div class="ab-title">${esc(label)}</div>
      ${ringItems.length > 1 ? `<div class="ab-sub">等 ${ringItems.length} 个提醒在响</div>` : ""}
    </div>
    <button class="btn danger-solid sm" data-rb="ack">${icon("check")} ${b ? "已取出" : "知道了"}</button>
    <button class="btn ghost sm" data-rb="snooze">延后2分</button>
  `;
  bar.querySelector('[data-rb="ack"]').onclick = () => {
    acknowledge(first.id);
  };
  bar.querySelector('[data-rb="snooze"]').onclick = () => {
    snooze(first.id, 2);
    toast("已延后 2 分钟");
  };
}

/* ---------- first run ---------- */

function fmtClockHM(ts) {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function firstRunHint() {
  const st = getState();
  if (!st.firstRun) return;
  setTimeout(() => {
    toast("欢迎使用胶记！先到「设置」确认客户与胶类型 👋");
  }, 600);
}

/* ---------- boot ---------- */

startEngine();
render();
firstRunHint();

// refresh countdown text every second while a timer is active (cheap DOM update)
setInterval(() => {
  const st = getState();
  const hasActive = st.bakes.some((b) => b.status === "running" || b.status === "due");
  const hasTasks = st.sampling.some((t) => t.status === "pending");
  if ((hasActive || hasTasks) && !document.hidden) {
    updateCountdowns();
  }
}, 1000);

function updateCountdowns() {
  // live-update small countdown chips without full re-render
  document.querySelectorAll("[data-countdown-for]").forEach((el) => {
    const id = el.dataset.countdownFor;
    const b = getState().bakes.find((x) => x.id === id);
    if (!b || b.status !== "running") return;
    const left = b.endAt - Date.now();
    el.textContent = clockStr(left);
  });
  document.querySelectorAll("[data-task-count]").forEach((el) => {
    const id = el.dataset.taskCount;
    const t = getState().sampling.find((x) => x.id === id);
    if (!t || t.status !== "pending") return;
    const left = t.dueAt - Date.now();
    el.textContent = left <= 0 ? "到点了" : `剩 ${Math.max(0, Math.floor(left / 60000))}:${String(Math.floor((left % 60000) / 1000)).padStart(2, "0")}`;
  });
}

function clockStr(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  const m = Math.floor(s / 60);
  const sec = s % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    return `${h}:${String(m % 60).padStart(2, "0")}:${String(sec).padStart(2, "0")}`;
  }
  return `${String(m).padStart(2, "0")}:${String(sec).padStart(2, "0")}`;
}
