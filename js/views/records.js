/* ============================================================
   胶记 · view: Records (记录页)
   ============================================================ */
import { getState, customers } from "../store.js";
import { icon, toast } from "../ui.js";
import { typeName, getType, getSample } from "../store.js";
import { esc, dateKey, fmtNum, csvEscape, download, dayLabel } from "../utils.js";
import { openSampleDetail } from "../views/detail.js";
import { emptyState } from "./today.js";

let mode = "today"; // today | week | all
let query = "";
let badOnly = false;

function groupKey(ts) {
  return dateKey(new Date(ts));
}

function verdictOf(t) {
  if (t.skipped) return "na";
  if (t.value == null || t.value === "") return "";
  const n = Number(t.value);
  if (!Number.isFinite(n)) return "";
  const min = t.min === "" || t.min == null ? null : Number(t.min);
  const max = t.max === "" || t.max == null ? null : Number(t.max);
  if (min != null && n < min) return "fail";
  if (max != null && n > max) return "fail";
  return "pass";
}

function sampleBad(s) {
  return (s.tests || []).some((t) => verdictOf(t) === "fail");
}

function bakeStr(s) {
  const st = getState();
  const bs = st.bakes.filter((b) => b.sampleId === s.id);
  if (!bs.length) return "";
  return bs.map((b) => `${b.tempC}℃×${b.minutes}分`).join(" / ");
}

function matchesQuery(s, q) {
  if (!q) return true;
  const st = getState();
  const hay = [
    s.customer,
    typeName(s.typeId),
    s.batchNo,
    s.note || "",
    ...(s.tests || []).map((t) => `${t.name} ${t.value ?? ""}`),
    bakeStr(s),
  ]
    .join(" ")
    .toLowerCase();
  return q
    .toLowerCase()
    .split(/\s+/)
    .every((w) => hay.includes(w));
}

export function renderRecords(root) {
  const st = getState();
  const all = [...st.samples].sort((a, b) => b.sampledAt - a.sampledAt);

  root.innerHTML = `
    <div class="page-head">
      <p class="eyebrow">RECORDS</p>
      <h1>记录</h1>
      <p class="page-sub">全部检测数据与烘烤记录，可搜索、导出。</p>
    </div>

    <div class="seg rec-seg" id="rec-seg">
      <button data-m="today" class="${mode === "today" ? "on" : ""}">今天</button>
      <button data-m="week" class="${mode === "week" ? "on" : ""}">近7天</button>
      <button data-m="all" class="${mode === "all" ? "on" : ""}">全部</button>
    </div>

    <div class="rec-toolbar">
      <div class="search-wrap">
        ${icon("search")}
        <input id="rec-q" placeholder="搜客户 / 批次 / 数据…" value="${esc(query)}" />
      </div>
      <label class="checkline">
        <input type="checkbox" id="rec-bad" ${badOnly ? "checked" : ""} /> 不合格
      </label>
    </div>

    <div class="rec-actions">
      <button class="btn ghost sm" id="rec-csv">${icon("download")} CSV</button>
      <button class="btn ghost sm" id="rec-report">${icon("fileText")} 日报</button>
      <button class="btn ghost sm" id="rec-json">${icon("copy")} 备份</button>
    </div>

    <div id="rec-list"></div>
  `;

  const list = root.querySelector("#rec-list");
  renderList(list, all);

  root.querySelector("#rec-seg").addEventListener("click", (e) => {
    const b = e.target.closest("[data-m]");
    if (!b) return;
    mode = b.dataset.m;
    root.querySelectorAll("#rec-seg button").forEach((x) => x.classList.toggle("on", x === b));
    renderList(list, all);
  });

  const qInput = root.querySelector("#rec-q");
  let deb;
  qInput.addEventListener("input", () => {
    clearTimeout(deb);
    deb = setTimeout(() => {
      query = qInput.value.trim();
      renderList(list, all);
    }, 160);
  });

  root.querySelector("#rec-bad").onchange = (e) => {
    badOnly = e.target.checked;
    renderList(list, all);
  };

  root.querySelector("#rec-csv").onclick = exportCSV;
  root.querySelector("#rec-report").onclick = copyReport;
  root.querySelector("#rec-json").onclick = exportJSON;
}

function filtered(all) {
  const now = Date.now();
  let arr = all;
  if (mode === "today") {
    const k = dateKey(new Date());
    arr = all.filter((s) => groupKey(s.sampledAt) === k);
  } else if (mode === "week") {
    arr = all.filter((s) => now - s.sampledAt <= 7 * 86400000);
  }
  if (badOnly) arr = arr.filter(sampleBad);
  if (query) arr = arr.filter((s) => matchesQuery(s, query));
  return arr;
}

function renderList(el, all) {
  const arr = filtered(all);
  if (!arr.length) {
    el.innerHTML = emptyState({
      icon: "search",
      title: query || badOnly ? "没有符合条件的记录" : "还没有记录",
      sub: query || badOnly ? "换个关键词或关闭筛选" : "取样建档后，记录会出现在这里",
    });
    return;
  }
  const groups = new Map();
  for (const s of arr) {
    const k = groupKey(s.sampledAt);
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(s);
  }
  el.innerHTML = [...groups.entries()]
    .map(([k, items]) => {
      const [y, m, d] = k.split("-").map(Number);
      const dl = dayLabel(new Date(y, m - 1, d));
      const bad = items.filter(sampleBad).length;
      return `
      <div class="rec-group">
        <div class="rec-group-head">
          <span class="rg-label">${esc(dl)}</span>
          <span class="rg-count">${items.length} 批${bad ? ` · <b class="rg-bad">${bad} 批不合格</b>` : ""}</span>
        </div>
        ${items.map((s) => recCardHTML(s)).join("")}
      </div>`;
    })
    .join("");

  el.querySelectorAll("[data-rec]").forEach((card) => {
    card.onclick = () => openSampleDetail(card.dataset.rec);
  });
}

function recCardHTML(s) {
  const type = getType(s.typeId);
  const isWeld = (type?.name || "").includes("焊缝");
  const tests = s.tests || [];
  const doneCount = tests.filter((t) => t.value != null && t.value !== "" || t.skipped).length;
  const bad = tests.some((t) => verdictOf(t) === "fail");
  const pct = tests.length ? Math.round((doneCount / tests.length) * 100) : 0;
  const hm = new Date(s.sampledAt);
  const hhmm = `${String(hm0(hm)).padStart(2, "0")}:${String(hm.getMinutes()).padStart(2, "0")}`;
  return `
  <div class="rec-card" data-rec="${s.id}">
    <div class="rec-head">
      <span class="rec-dot ${bad ? "bad" : isWeld ? "weld" : ""}"></span>
      <div class="rec-main">
        <div class="rec-title">${esc(s.customer)} · ${esc(type?.name || "")}</div>
        <div class="rec-sub mono">${esc(s.batchNo)} · ${hhmm}${bakeStr(s) ? ` · 烘 ${esc(bakeStr(s))}` : ""}</div>
      </div>
      ${bad ? `<span class="chip danger">不合格</span>` : doneCount === tests.length && tests.length ? `<span class="chip ok">已完检</span>` : `<span class="chip muted">${doneCount}/${tests.length}</span>`}
      <span class="chev">${icon("chevronRight")}</span>
    </div>
    <div class="rec-tests">
      ${tests
        .slice(0, 4)
        .map((t) => {
          const v = verdictOf(t);
          return `<span class="rt-chip ${v}">${esc(t.name)} ${t.skipped ? "—" : t.value != null && t.value !== "" ? esc(fmtNum(t.value)) + (t.unit ? esc(t.unit) : "") : "—"}</span>`;
        })
        .join("")}
      ${tests.length > 4 ? `<span class="rt-more">+${tests.length - 4}</span>` : ""}
    </div>
    <div class="rec-bar"><i style="width:${pct}%"></i></div>
  </div>`;
}

function hm0(d) {
  return d.getHours();
}

/* ---------------- exports ---------------- */

function exportCSV() {
  const st = getState();
  const arr = filtered([...st.samples].sort((a, b) => b.sampledAt - a.sampledAt));
  if (!arr.length) {
    toast("没有可导出的记录", "warn");
    return;
  }
  const cols = testColumns(arr);
  const rows = arr.map((s) => {
    const d = new Date(s.sampledAt);
    const base = [
      dateKey(d),
      `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`,
      s.customer,
      typeName(s.typeId),
      s.batchNo,
      bakeStr(s),
    ];
    const vals = cols.map((name) => {
      const t = (s.tests || []).find((x) => x.name === name);
      return t ? (t.skipped ? "—" : t.value ?? "") : "";
    });
    return [...base, ...vals, s.note || ""];
  });
  const header = ["日期", "时间", "客户", "胶类型", "批次号", "烘烤", ...cols, "备注"];
  const csv = [header, ...rows].map((r) => r.map(csvEscape).join(",")).join("\r\n");
  download(`胶记数据_${dateKey(new Date())}.csv`, "\uFEFF" + csv, "text/csv");
  toast(`已导出 ${arr.length} 批记录`);
}

function testColumns(arr) {
  const seen = [];
  for (const s of arr) {
    for (const t of s.tests || []) {
      if (!seen.includes(t.name)) seen.push(t.name);
    }
  }
  return seen;
}

function copyReport() {
  const arr = filtered([...getState().samples].sort((a, b) => a.sampledAt - b.sampledAt));
  if (!arr.length) {
    toast("没有可生成日报的记录", "warn");
    return;
  }
  const lines = [];
  lines.push(`检测日报 · ${dateKey(new Date())}`);
  lines.push("");
  for (const s of arr) {
    const d = new Date(s.sampledAt);
    lines.push(`【${s.customer} · ${typeName(s.typeId)} · ${s.batchNo}】`);
    for (const t of s.tests || []) {
      const v = verdictOf(t);
      const val = t.skipped ? "未测" : t.value != null && t.value !== "" ? `${fmtNum(t.value)}${t.unit || ""}` : "未录";
      const mark = v === "fail" ? " ✗不合格" : v === "pass" ? " ✓" : "";
      lines.push(`  ${t.name}：${val}${t.min != null && t.min !== "" || t.max != null && t.max !== "" ? `（标准 ${t.min ?? "—"}~${t.max ?? "—"}）` : ""}${mark}`);
    }
    lines.push("");
  }
  const text = lines.join("\n");
  navigator.clipboard
    ?.writeText(text)
    .then(() => toast("日报已复制，可直接粘贴发送"))
    .catch(() => {
      download(`检测日报_${dateKey(new Date())}.txt`, text);
      toast("已生成日报文件");
    });
}

function exportJSON() {
  download(`胶记备份_${dateKey(new Date())}.json`, JSON.stringify(getState(), null, 2), "application/json");
  toast("备份已下载");
}
