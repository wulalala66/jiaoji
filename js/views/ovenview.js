/* ============================================================
   胶记 · view: Oven (烘箱页)
   ============================================================ */
import { getState, runningBakes, recentDoneBakes } from "../store.js";
import { icon } from "../ui.js";
import { bakeCardHTML, bindBakeCardActions } from "../cards.js";
import { emptyState } from "./today.js";
import { openBakeForm } from "../forms.js";
import { go } from "../main.js";

let currentOven = "door";

export function goToOven(oven = "door") {
  currentOven = oven === "window" ? "window" : "door";
  go("oven");
}

export function renderOven(root) {
  const st = getState();
  const bakes = runningBakes();
  const doorCount = bakes.filter((b) => b.oven === "door").length;
  const winCount = bakes.filter((b) => b.oven === "window").length;
  const finished = recentDoneBakes(6);

  root.innerHTML = `
    <div class="page-head">
      <p class="eyebrow">BAKE TIMERS</p>
      <h1>烘箱</h1>
      <p class="page-sub">两台烘箱，到点自动响铃。取出后点「已取出」。</p>
    </div>

    <div class="oven-switch">
      <button class="oven-switch-btn ${currentOven === "door" ? "on door" : ""}" data-oven="door">
        ${icon("door")} 靠门烘箱
        ${doorCount ? `<span class="os-count">${doorCount}</span>` : ""}
      </button>
      <button class="oven-switch-btn ${currentOven === "window" ? "on window" : ""}" data-oven="window">
        ${icon("windowIco")} 靠窗烘箱
        ${winCount ? `<span class="os-count">${winCount}</span>` : ""}
      </button>
    </div>

    <section class="section" style="margin-top:14px">
      <div class="section-head">
        <h2>计时中</h2>
        <button class="section-link" data-act="add">${icon("plus")} 新建计时</button>
      </div>
      <div id="oven-list"></div>
    </section>

    <section class="section">
      <div class="section-head"><h2>刚出炉</h2></div>
      <div id="oven-finished"></div>
    </section>
  `;

  const list = root.querySelector("#oven-list");
  const mine = bakes.filter((b) => b.oven === currentOven);
  if (!mine.length) {
    list.innerHTML = emptyState({
      icon: currentOven === "door" ? "door" : "windowIco",
      title: `${currentOven === "door" ? "靠门" : "靠窗"}烘箱空闲中`,
      sub: "放入样品后点「新建计时」，或从样品详情页发起",
    });
  } else {
    list.innerHTML = mine.map((b) => bakeCardHTML(b)).join("");
  }

  const finEl = root.querySelector("#oven-finished");
  if (!finished.length) {
    finEl.innerHTML = emptyState({ icon: "clock", title: "暂无完成记录", sub: "今天出炉的样品会显示在这里" });
  } else {
    finEl.innerHTML = `
      <div class="card">
        ${finished
          .map(
            (b) => `
          <div class="finished-row">
            <span class="finished-icon">${icon("flame")}</span>
            <span class="finished-main">${escapeHtml(b.label)} · ${b.tempC}℃×${b.minutes}分</span>
            <span class="finished-time mono">${fmtHM(b.doneAt)}</span>
          </div>`
          )
          .join("")}
      </div>`;
  }

  root.querySelectorAll(".oven-switch-btn").forEach((btn) => {
    btn.onclick = () => {
      currentOven = btn.dataset.oven;
      renderOven(root);
    };
  });
  const addBtn = root.querySelector("[data-act=add]");
  addBtn.onclick = () => openBakeForm({ oven: currentOven });

  bindBakeCardActions(root, { onChanged: () => renderOven(root) });
}

function escapeHtml(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function fmtHM(ts) {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}
