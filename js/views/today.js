/* ============================================================
   胶记 · view: Today
   ============================================================ */
import { $ } from "../utils.js";
import { getState, pendingToday, todaySamples, runningBakes, getSample } from "../store.js";
import { icon } from "../ui.js";
import { ovenTileHTML, sampleRowHTML } from "../cards.js";
import { openBakeForm, openSamplingForm } from "../forms.js";
import { openSampleDetail, openNewSampleSheet } from "./detail.js";
import { confirmSampled } from "./sampling.js";
import { goToOven } from "./ovenview.js";

const WD = ["日", "一", "二", "三", "四", "五", "六"];

function esc(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function hm(d) {
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function todayLabel(d) {
  return `${d.getMonth() + 1}月${d.getDate()}日 周${WD[d.getDay()]}`;
}

function heroGreeting() {
  const h = new Date().getHours();
  if (h < 9) return "早上好";
  if (h < 12) return "上午好";
  if (h < 14) return "中午好";
  if (h < 18) return "下午好";
  return "晚上好";
}

function typeNameOf(typeId) {
  const t = getState().types.find((x) => x.id === typeId);
  return t ? t.name : "";
}

function heroSubHTML(st, tasks, samples) {
  const now = Date.now();
  const dueBakes = runningBakes().filter((b) => b.status === "due");
  const dueTasks = tasks.filter((t) => t.dueAt <= now);
  if (dueBakes.length) {
    const b = dueBakes[0];
    return `<span class="alert-text">${icon("bell")} ${b.oven === "door" ? "靠门" : "靠窗"}烘箱到点了${dueBakes.length > 1 ? `（共 ${dueBakes.length} 个）` : ""}</span> — 请取出样品`;
  }
  if (dueTasks.length) {
    const t = dueTasks[0];
    return `<span class="alert-text">${icon("clock")} 到点了：${esc(t.customer)}</span> — 取样后点「已取样」`;
  }
  if (st.firstRun) {
    return `先在「设置」里确认客户与胶类型。每天流程：看到取样提醒 → 取样建档 → 放烘箱计时 → 录数据。`;
  }
  if (tasks.length) {
    const t = tasks[0];
    return `下一件事：<b>${esc(t.customer)}</b> ${hm(new Date(t.dueAt))} 取${typeNameOf(t.typeId)}的样。`;
  }
  if (samples.length) {
    return `取样都安排完了。继续跟进 <b>${samples.length}</b> 批样品的检测数据。`;
  }
  return `今天还没有安排。点右下角 <b>＋</b> 添加取样提醒，或直接开始一个烘烤计时。`;
}

export function renderToday(root) {
  const st = getState();
  const bakes = runningBakes();
  const tasks = pendingToday();
  const samples = todaySamples();

  root.innerHTML = `
    <div class="today-hero">
      <div class="brand-line">
        <div class="logo-mark">${icon("drop")}</div>
        <div>
          <div class="brand-name">胶记</div>
          <div class="brand-en">GLUE JOURNAL</div>
        </div>
        <span class="hero-date">${todayLabel(new Date())}</span>
      </div>
      <h1 class="hero-title">${heroGreeting()}，<span class="accent">今天</span>要取哪些样？</h1>
      <p class="hero-sub">${heroSubHTML(st, tasks, samples)}</p>
    </div>

    <section class="section">
      <div class="section-head">
        <h2>烘箱</h2>
        <span class="label-note">${bakes.length ? `${bakes.length} 个计时中` : "空闲"}</span>
      </div>
      <div class="oven-grid">
        ${ovenTileHTML("door")}
        ${ovenTileHTML("window")}
      </div>
      <div class="quick-actions">
        <button class="btn soft" data-act="bake-new-door">${icon("flame")} 放入靠门烘箱</button>
        <button class="btn soft" data-act="bake-new-window">${icon("flame")} 放入靠窗烘箱</button>
        <button class="btn ghost" data-act="bake-new">${icon("plus")} 其他计时</button>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2>今日取样</h2>
        <span class="label-note">${tasks.length ? `${tasks.length} 条待取样` : "暂无安排"}</span>
        <button class="section-link" data-act="add-sampling">${icon("plus")} 添加</button>
      </div>
      <div id="today-tasks"></div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2>今日样品</h2>
        <span class="label-note">${samples.length ? `${samples.length} 批` : ""}</span>
        <button class="section-link" data-act="new-sample">${icon("plus")} 新建</button>
      </div>
      <div id="today-samples"></div>
    </section>

    <section class="section">
      <div class="section-head"><h2>今日完成</h2></div>
      <div id="today-finished"></div>
    </section>
  `;

  renderTasks($("#today-tasks", root), tasks);
  renderSamples($("#today-samples", root), samples);
  renderFinished($("#today-finished", root), st);

  root.querySelectorAll("[data-act]").forEach((el) => {
    el.onclick = () => {
      const act = el.dataset.act;
      if (act === "bake-new-door") openBakeForm({ oven: "door" });
      else if (act === "bake-new-window") openBakeForm({ oven: "window" });
      else if (act === "bake-new") openBakeForm({});
      else if (act === "add-sampling") openSamplingForm({});
      else if (act === "new-sample") openNewSampleSheet();
    };
  });

  root.querySelectorAll("[data-oven-tile]").forEach((el) => {
    el.onclick = () => goToOven(el.dataset.ovenTile);
  });
}

function renderTasks(el, tasks) {
  if (!tasks.length) {
    el.innerHTML = emptyState({
      icon: "clipboard",
      title: "暂无取样安排",
      sub: "负责人发时间后，点「添加」安排",
    });
    return;
  }
  const now = Date.now();
  const sorted = [...tasks].sort((a, b) => a.dueAt - b.dueAt);
  el.innerHTML = sorted
    .map((t) => {
      const left = t.dueAt - now;
      const late = left < 0;
      const soon = left > 0 && left < 10 * 60000;
      const typeName = typeNameOf(t.typeId);
      const isWeld = typeName.includes("焊缝");
      return `
      <div class="task-card ${late ? "overdue" : soon ? "soon" : ""}">
        <div class="task-time">
          <div class="tt-clock">${hm(new Date(t.dueAt))}</div>
          <div class="tt-count mono">${late ? "待取样" : fmtCountdown(left)}</div>
        </div>
        <div class="task-main">
          <div class="task-title">${esc(t.customer)}</div>
          <div class="task-meta">
            <span class="type-chip ${isWeld ? "weld" : "other"}">${esc(typeName)}</span>
            ${t.note ? `<span class="task-note">“${esc(t.note)}”</span>` : ""}
          </div>
        </div>
        <div class="task-actions">
          <button class="btn primary sm" data-sampled="${t.id}">${icon("check")} 已取样</button>
          <button class="icon-btn sm" data-task-edit="${t.id}" title="编辑">${icon("pencil")}</button>
        </div>
      </div>`;
    })
    .join("");

  el.querySelectorAll("[data-sampled]").forEach((btn) => {
    btn.onclick = () => confirmSampled(btn.dataset.sampled);
  });
  el.querySelectorAll("[data-task-edit]").forEach((btn) => {
    btn.onclick = () => {
      const t = getState().sampling.find((x) => x.id === btn.dataset.taskEdit);
      if (t) openSamplingForm({ editTask: t });
    };
  });
}

function fmtCountdown(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  if (s >= 3600) {
    const h = Math.floor(s / 3600);
    return `剩 ${h}:${String(Math.floor((s % 3600) / 60)).padStart(2, "0")}`;
  }
  return `剩 ${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

function renderSamples(el, samples) {
  if (!samples.length) {
    el.innerHTML = emptyState({
      icon: "flask",
      title: "今天还没有样品",
      sub: "到点取样后点「已取样」自动建档，或点「新建」手动建",
    });
    return;
  }
  el.innerHTML = samples.map((s) => sampleRowHTML(s)).join("");
  el.querySelectorAll("[data-sample]").forEach((row) => {
    row.onclick = () => openSampleDetail(row.dataset.sample);
  });
}

function renderFinished(el, st) {
  const done = st.sampling.filter((t) => t.status === "done" && sameDay(t.doneAt));
  const doneBakes = st.bakes.filter((b) => b.status === "done" && sameDay(b.doneAt));
  const rows = [
    ...done.map((t) => ({
      icon: "checkCircle",
      main: `${t.customer} · ${typeNameOf(t.typeId)} 取样`,
      time: t.doneAt,
    })),
    ...doneBakes.map((b) => ({
      icon: "flame",
      main: `${b.label} · ${b.tempC}℃×${b.minutes}分 烘烤完成`,
      time: b.doneAt,
    })),
  ].sort((a, b) => b.time - a.time);

  if (!rows.length) {
    el.innerHTML = emptyState({
      icon: "checkCircle",
      title: "还没有完成事项",
      sub: "完成的取样和烘烤会出现在这里",
    });
    return;
  }
  el.innerHTML = `
    <div class="card">
      ${rows
        .map(
          (r) => `
        <div class="finished-row">
          <span class="finished-icon">${icon(r.icon)}</span>
          <span class="finished-main">${esc(r.main)}</span>
          <span class="finished-time mono">${hm(new Date(r.time))}</span>
        </div>`
        )
        .join("")}
    </div>`;
}

function sameDay(ts) {
  const d = new Date(ts);
  const n = new Date();
  return d.getFullYear() === n.getFullYear() && d.getMonth() === n.getMonth() && d.getDate() === n.getDate();
}

export function emptyState({ icon: ic = "info", title, sub }) {
  return `
    <div class="empty-state">
      <div class="empty-ico">${icon(ic)}</div>
      <div class="empty-title">${esc(title)}</div>
      <div class="empty-sub">${esc(sub || "")}</div>
    </div>`;
}
