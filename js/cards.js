/* ============================================================
   胶记 · shared cards: bake timer card, sample rows, helpers
   ============================================================ */
import { getState, getSample, getType } from "./store.js";
import { esc, hmsClock, hm, pad, fmtNum, fmtRange } from "./utils.js";
import { icon } from "./ui.js";
import { nativeAvailable, systemTimer } from "./native.js";

/** wire up the buttons inside bake timer cards (works on any page) */
export function bindBakeCardActions(root, { onChanged } = {}) {
  root.querySelectorAll(".timer-card [data-act]").forEach((btn) => {
    const act = btn.dataset.act;
    const id = btn.dataset.id;
    btn.onclick = async (e) => {
      e.stopPropagation();
      const store = await import("./store.js");
      const forms = await import("./forms.js");
      const b = store.getState().bakes.find((x) => x.id === id);
      if (act === "bake-done") {
        store.markBakeDone(id);
      } else if (act === "bake-snooze") {
        const alarms = await import("./alarms.js");
        alarms.snooze(id, 2);
      } else if (act === "bake-plus") {
        if (b) store.extendBake(id, 5);
      } else if (act === "bake-edit") {
        if (b) forms.openBakeForm({ editBake: b });
      } else if (act === "bake-remove") {
        store.removeBake(id);
      } else if (act === "bake-sys-timer") {
        if (b) systemTimer(Math.max(1, Math.ceil((b.endAt - Date.now()) / 60000)), b.label);
      }
      onChanged?.();
    };
  });
}

export function ovenName(oven) {
  return oven === "door" ? "靠门烘箱" : "靠窗烘箱";
}

export function ovenIcon(oven) {
  return icon(oven === "door" ? "door" : "windowIco");
}

export function verdictOf(test) {
  if (test.skipped) return "na";
  if (test.value == null || test.value === "") return "";
  const n = Number(test.value);
  if (!Number.isFinite(n)) return "";
  const hasMin = test.min !== "" && test.min != null;
  const hasMax = test.max !== "" && test.max != null;
  if (!hasMin && !hasMax) return "recorded";
  if (hasMin && n < Number(test.min)) return "fail";
  if (hasMax && n > Number(test.max)) return "fail";
  return "pass";
}

export function verdictChip(test) {
  const v = verdictOf(test);
  if (!v) return "";
  const map = {
    pass: ["ok", "合格"],
    fail: ["danger", "超差"],
    recorded: ["tint", "已录"],
    na: ["muted", "不做"],
  };
  const [cls, label] = map[v];
  return `<span class="verdict ${v}">${label}</span>`;
}

export function sampleStats(sample) {
  const tests = sample.tests || [];
  const done = tests.filter((t) => verdictOf(t) !== "").length;
  const total = tests.length;
  const bad = tests.filter((t) => verdictOf(t) === "fail").length;
  return { done, total, bad, pct: total ? Math.round((done / total) * 100) : 0 };
}

/** big timer card used on ovens page + linked lists */
export function bakeCardHTML(bake, { compact = false } = {}) {
  const now = Date.now();
  const remain = bake.endAt - now;
  const due = bake.status === "due" || remain <= 0;
  const total = bake.minutes * 60000;
  const pct = Math.min(100, Math.max(0, ((total - Math.max(0, remain)) / total) * 100));
  const sample = bake.sampleId ? getSample(bake.sampleId) : null;
  const clock = due ? "00:00" : hmsClock(remain);
  const cls = due ? "due" : remain < 120000 ? "soon" : "";
  const type = sample ? getType(sample.typeId) : null;

  return `
  <div class="timer-card ${cls}" data-bake="${bake.id}">
    <div class="timer-top">
      <span class="oven-tag ${bake.oven}">${ovenIcon(bake.oven)} ${ovenName(bake.oven)}</span>
      <span class="mono">${bake.tempC}℃ · ${bake.minutes}分</span>
    </div>
    <div class="timer-body">
      <div class="timer-info">
        <div class="timer-name">${esc(bake.label)}</div>
        <div class="timer-sub">${due ? `开始于 ${hm(new Date(bake.startAt))}` : `完成于 ${hm(new Date(bake.endAt))}${sample ? ` · ${esc(sample.customer)} ${type ? type.name : ""}` : ""} · <span class="mono" data-countdown-for="${bake.id}">${hmsClock(Math.max(0, remain))}</span>`}</div>
      </div>
      <div class="timer-clock ${cls}">${clock}</div>
    </div>
    <div class="timer-progress"><i style="width:${pct}%"></i></div>
    ${
      due
        ? `<div class="timer-actions">
            <button class="btn primary" data-act="bake-done" data-id="${bake.id}">${icon("check")} 已取出</button>
            <button class="btn danger" data-act="bake-snooze" data-id="${bake.id}">延后2分</button>
          </div>`
        : `<div class="timer-actions">
            <button class="btn ghost sm" data-act="bake-plus" data-id="${bake.id}">${icon("clock")} 加5分</button>
            <button class="btn ghost sm" data-act="bake-edit" data-id="${bake.id}">${icon("pencil")} 调整</button>
            ${nativeAvailable() ? `<button class="btn ghost sm" data-act="bake-sys-timer" data-id="${bake.id}" title="用系统时钟计时（后台也能响）">${icon("bell")} 系统倒计时</button>` : ""}
            <button class="btn ghost sm" data-act="bake-remove" data-id="${bake.id}">${icon("trash")}</button>
          </div>`
    }
  </div>`;
}

/** mini tile progress for today page */
export function ovenTileHTML(oven) {
  const s = getState();
  const list = s.bakes
    .filter((b) => b.oven === oven && (b.status === "running" || b.status === "due"))
    .sort((a, b) => a.endAt - b.endAt);
  const b = list[0];
  const others = list.length - 1;

  if (!b) {
    return `
    <a class="oven-tile empty ${oven}" href="#/ovens">
      <div class="oven-tile-head">${ovenIcon(oven)} ${ovenName(oven)}</div>
      <div class="oven-tile-main">空闲中</div>
      <div class="oven-tile-sub">没有进行中的烘烤</div>
    </a>`;
  }

  const now = Date.now();
  const remain = b.endAt - now;
  const due = b.status === "due" || remain <= 0;
  const total = b.minutes * 60000;
  const pct = Math.min(100, Math.max(0, ((total - Math.max(0, remain)) / total) * 100));
  const clock = due ? "00:00" : hmsClock(remain);

  return `
  <a class="oven-tile ${oven} ${due ? "due" : ""}" href="#/ovens">
    <div class="oven-tile-head">${ovenIcon(oven)} ${ovenName(oven)}
      <svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m9 18 6-6-6-6"/></svg>
    </div>
    <div class="oven-tile-main">${clock}<span class="unit">${due ? "请取出" : "剩余"}</span></div>
    <div class="oven-tile-sub">${esc(b.label)} · ${b.tempC}℃${others > 0 ? ` · 还有${others}个` : ""}</div>
    <div class="mini-bar"><i style="width:${pct}%"></i></div>
  </a>`;
}

export function batchNoLabel(sample) {
  return sample?.batchNo ? `批次 ${sample.batchNo}` : "";
}

export function sampledTimeLabel(sample) {
  return sample ? `${pad(new Date(sample.sampledAt).getHours())}:${pad(new Date(sample.sampledAt).getMinutes())}` : "";
}

/** compact row card for a sample (used on Today page) */
export function sampleRowHTML(sample) {
  const type = getType(sample.typeId);
  const isWeld = (type?.name || "").includes("焊缝");
  const st = sampleStats(sample);
  const initial = (sample.customer || "?").slice(0, 1);
  const bakes = getState().bakes.filter((b) => b.sampleId === sample.id && b.status !== "done");
  const bakeNote = bakes.length
    ? `<div class="sample-bake-note">${ovenIcon(bakes[0].oven)} ${bakes[0].tempC}℃·<span class="mono" data-countdown-for="${bakes[0].id}">${hmsClock(Math.max(0, bakes[0].endAt - Date.now()))}</span></div>`
    : "";
  return `
  <div class="sample-row ${isWeld ? "weld" : ""}" data-sample="${sample.id}">
    <span class="sample-avatar">${esc(initial)}</span>
    <div class="sample-main">
      <div class="sample-title">${esc(sample.customer)} · ${esc(type?.name || "")}</div>
      <div class="sample-sub mono">${esc(sample.batchNo)}${st.total ? ` · ${st.done}/${st.total}${st.bad ? ` · <b class=\"bad-txt\">${st.bad} 项不合格</b>` : ""}` : ""}</div>
      ${bakeNote}
    </div>
    <div class="sample-side">
      <div class="sample-progress-mini"><i style="width:${st.pct}%"></i></div>
      <span class="chev-r">${chevronSvg()}</span>
    </div>
  </div>`;
}

function chevronSvg() {
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m9 18 6-6-6-6"/></svg>';
}
