/* ============================================================
   胶记 · sampling confirm (已取样 → 建档)
   ============================================================ */
import { getState, getType, getSample, createSampleFromTask, addBake } from "../store.js";
import { esc } from "../utils.js";
import { icon, toast, openSheet } from "../ui.js";
import { openSampleDetail } from "../views/detail.js";

/** 点「已取样」后确认建档，可选同步放烘箱 */
export function confirmSampled(taskId) {
  const task = getState().sampling.find((t) => t.id === taskId);
  if (!task) return;
  const type = getType(task.typeId);
  const presets = type?.bakes || [];

  const sheet = openSheet({
    title: "已取样 · 建档",
    note: `批次 ${previewBatch()}`,
    body: `
      <div class="form-grid">
        <div class="prompt-line">${esc(task.customer)} · ${esc(type?.name || "")}</div>
        <div class="form-field">
          <label>批次号</label>
          <input id="cs-batch" class="text-field mono" placeholder="${previewBatch()}" />
        </div>
        <div class="form-field">
          <label>备注</label>
          <input id="cs-note" class="text-field" placeholder="选填" value="${esc(task.note || "")}" />
        </div>
        <label class="checkline"><input type="checkbox" id="cs-bake" ${presets.length ? "checked" : ""} /> 同时放烘箱开始计时</label>
        <div id="cs-bake-zone" class="${presets.length ? "" : "hidden"}">
          <div class="form-field">
            <label>烘箱</label>
            <div class="oven-choice" id="cs-oven">
              <button type="button" class="type-opt on" data-oven="door">${icon("door")} 靠门烘箱</button>
              <button type="button" class="type-opt" data-oven="window">${icon("windowIco")} 靠窗烘箱</button>
            </div>
          </div>
          <div class="form-field">
            <label>烘烤条件</label>
            <div class="bake-presets" id="cs-presets">
              ${presets.map((p, i) => `<button type="button" class="preset-btn ${i === 0 ? "on" : ""}" data-temp="${p.temp}" data-min="${p.minutes}">${icon("flame")} ${p.temp}℃ × ${p.minutes}分</button>`).join("")}
            </div>
          </div>
          <div class="bake-fields">
            <div class="mini-field"><label>温度℃</label><input id="cs-temp" class="text-field mono" type="number" value="${presets[0]?.temp ?? 140}" /></div>
            <div class="mini-field"><label>分钟</label><input id="cs-min" class="text-field mono" type="number" value="${presets[0]?.minutes ?? 30}" /></div>
          </div>
        </div>
      </div>`,
    actions: [
      { label: "取消", cls: "ghost", onClick: () => sheet.close() },
      {
        label: "确认取样",
        cls: "primary",
        icon: "check",
        onClick: () => {
          const batch = sheet.bodyEl.querySelector("#cs-batch").value.trim() || null;
          const note = sheet.bodyEl.querySelector("#cs-note").value.trim();
          const withBake = sheet.bodyEl.querySelector("#cs-bake").checked;
          let oven = "door";
          if (withBake) {
            oven = sheet.bodyEl.querySelector("#cs-oven .type-opt.on")?.dataset.oven || "door";
          }
          const sample = createSampleFromTask(task.id, { batchNo: batch, note });
          if (withBake) {
            const temp = Number(sheet.bodyEl.querySelector("#cs-temp").value) || 140;
            const minutes = Number(sheet.bodyEl.querySelector("#cs-min").value) || 30;
            const ovenSel = sheet.bodyEl.querySelector("#cs-oven .type-opt.on")?.dataset.oven || oven;
            addBake({
              oven: ovenSel,
              sampleId: sample.id,
              label: `${task.customer} ${type?.name || ""} ${sample.batchNo}`.trim(),
              tempC: temp,
              minutes,
              startAt: Date.now(),
              sound: true,
            });
            toast(`${sample.batchNo} 已建档并开始烘烤计时`);
          } else {
            toast(`${sample.batchNo} 已建档`);
          }
          sheet.close();
          openSampleDetail(sample.id);
        },
      },
    ],
  });

  const bakeCheck = sheet.bodyEl.querySelector("#cs-bake");
  bakeCheck.onchange = () => {
    sheet.bodyEl.querySelector("#cs-bake-zone").classList.toggle("hidden", !bakeCheck.checked);
  };
  sheet.bodyEl.querySelector("#cs-oven").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-oven]");
    if (!btn) return;
    sheet.bodyEl.querySelectorAll("#cs-oven .type-opt").forEach((x) => x.classList.toggle("on", x === btn));
  });
  sheet.bodyEl.querySelector("#cs-presets").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-temp]");
    if (!btn) return;
    sheet.bodyEl.querySelectorAll("#cs-presets .preset-btn").forEach((x) => x.classList.toggle("on", x === btn));
    sheet.bodyEl.querySelector("#cs-temp").value = btn.dataset.temp;
    sheet.bodyEl.querySelector("#cs-min").value = btn.dataset.min;
  });
}

function previewBatch() {
  // dry-run preview of next batch number
  const st = getState();
  const today = new Date();
  const k = `${String(today.getMonth() + 1).padStart(2, "0")}${String(today.getDate()).padStart(2, "0")}`;
  const used = st.samples.filter((s) => (s.batchNo || "").startsWith(`${k}-`)).length;
  return `${k}-${String(used + 1).padStart(2, "0")}`;
}
