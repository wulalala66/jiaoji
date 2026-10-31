/* ============================================================
   胶记 · forms: quick compose, sampling form, bake form
   ============================================================ */
import { $, $$ } from "./utils.js";
import { getState, customers, addSampling, addBake, addCustomer, updateSampling, removeSampling, markBakeDone, updateBake } from "./store.js";
import { esc, hm, dateKey, at, pad } from "./utils.js";
import { icon, toast, openSheet, confirmDialog, promptDialog } from "./ui.js";
import { parseQuick } from "./parser.js";
import { unlockAudio } from "./alarms.js";

export const WD = ["日", "一", "二", "三", "四", "五", "六"];

function typeOptions(selectedId) {
  return getState().types
    .map((t) => `<option value="${t.id}" ${t.id === selectedId ? "selected" : ""}>${esc(t.name)}</option>`)
    .join("");
}

function customerOptions(selected) {
  return customers()
    .map((c) => `<option value="${esc(c)}" ${c === selected ? "selected" : ""}>${esc(c)}</option>`)
    .join("");
}

/* ---------------- compose menu ---------------- */

export function openCompose() {
  unlockAudio();
  const sheet = openSheet({
    title: "快速添加",
    note: "支持一句话",
    body: `
      <div class="form-grid">
        <div class="form-field">
          <input id="quick-line" class="text-field" placeholder="例如：8点半 安徽万安 底涂胶" autocomplete="off" />
          <div id="quick-parse" class="suggest-list"></div>
          <p class="form-hint" style="margin-top:8px">支持「8点半 / 8:30 / 0830」「安徽万安」「底涂胶 / 焊缝胶」，回车直接创建取样提醒。</p>
        </div>
        <div style="display:flex;gap:8px">
          <button class="btn soft" style="flex:1" data-go="sampling">${icon("clipboard")} 详细取样提醒</button>
          <button class="btn soft" style="flex:1" data-go="bake">${icon("flame")} 烘烤计时</button>
        </div>
      </div>`,
  });

  const input = $("#quick-line", sheet.bodyEl);
  const preview = $("#quick-parse", sheet.bodyEl);
  const render = () => {
    const p = parseQuick(input.value);
    const bits = [
      p.time ? `${hm(new Date(p.time))}` : "— 时间",
      p.customer || "— 客户",
      p.typeId ? getState().types.find((t) => t.id === p.typeId)?.name : "— 类型",
    ];
    preview.innerHTML = bits.map((b) => `<span class="chip ${b.startsWith("—") ? "muted" : "tint"}">${esc(b)}</span>`).join("");
  };
  input.addEventListener("input", render);
  render();
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      const p = parseQuick(input.value);
      if (p.time) {
        addSampling({
          customer: p.customer || "未指定客户",
          typeId: p.typeId || "other",
          dueAt: p.time,
          note: p.note,
        });
        toast("已添加取样提醒");
        sheet.close();
      } else {
        toast("先写个时间，比如「8点半」", "warn");
      }
    }
  });
  sheet.bodyEl.addEventListener("click", (e) => {
    const go = e.target.closest("[data-go]")?.dataset.go;
    if (!go) return;
    const v = input.value;
    sheet.close();
    if (go === "sampling") openSamplingForm({ text: v });
    else openBakeForm({ text: v });
  });
  setTimeout(() => input.focus(), 120);
}

/* ---------------- day/time picker ---------------- */

function pickerHTML(valueTs) {
  const val = valueTs ? new Date(valueTs) : null;
  const today = new Date();
  const days = [];
  for (let i = 0; i < 7; i++) {
    days.push(new Date(today.getFullYear(), today.getMonth(), today.getDate() + i));
  }
  const times = [];
  for (let h = 6; h <= 20; h++) {
    times.push(`${pad(h)}:00`);
    times.push(`${pad(h)}:30`);
  }
  const sel = val ? `${pad(val.getHours())}:${pad(val.getMinutes())}` : "";
  const selDay = val ? dateKey(val) : dateKey(today);
  return `
    <div class="picker-days">
      ${days
        .map(
          (d) => `
        <button type="button" data-day="${dateKey(d)}" class="${dateKey(d) === selDay ? "on" : ""}">
          <span class="wd">${d.getDate() === today.getDate() ? "今天" : WD[d.getDay()]}</span>
          <span class="dm">${d.getMonth() + 1}/${d.getDate()}</span>
        </button>`
        )
        .join("")}
    </div>
    <div class="picker-times">
      ${times.map((t) => `<button type="button" data-time="${t}" class="${t === sel ? "on" : ""}">${t}</button>`).join("")}
    </div>`;
}

/** returns Promise<ts|null> */
export function pickDateTime({ value = null, title = "选择时间" } = {}) {
  return new Promise((resolve) => {
    let day = value ? dateKey(new Date(value)) : dateKey(new Date());
    let time = value ? `${pad(new Date(value).getHours())}:${pad(new Date(value).getMinutes())}` : null;
    let done = false;
    const finish = (v) => {
      if (done) return;
      done = true;
      resolve(v);
    };

    const sheet = openSheet({
      title,
      body: `
        <div class="form-grid">
          <div id="picker-zone">${pickerHTML(value)}</div>
          <div class="time-row">
            <div class="mini-field">
              <label>日期</label>
              <input id="pk-date" class="text-field mono" type="date" value="${day}" />
            </div>
            <div class="mini-field">
              <label>时间</label>
              <input id="pk-time" class="text-field mono" type="time" value="${time || ""}" />
            </div>
          </div>
        </div>`,
      actions: [
        { label: "取消", cls: "ghost", onClick: () => { sheet.close(); finish(null); } },
        { label: "确定", cls: "primary", icon: "check", onClick: () => {
          const d = $("#pk-date", sheet.bodyEl).value;
          const t = $("#pk-time", sheet.bodyEl).value;
          if (!d || !t) { toast("请选择日期和时间", "warn"); return; }
          finish(at(d, t).getTime());
          sheet.close();
        } },
      ],
      onClose: () => finish(null),
    });

    const zone = $("#picker-zone", sheet.bodyEl);
    zone.addEventListener("click", (e) => {
      const dayBtn = e.target.closest("[data-day]");
      const timeBtn = e.target.closest("[data-time]");
      if (dayBtn) {
        day = dayBtn.dataset.day;
        $("#pk-date", sheet.bodyEl).value = day;
      }
      if (timeBtn) {
        time = timeBtn.dataset.time;
        $("#pk-time", sheet.bodyEl).value = time;
      }
      if (dayBtn || timeBtn) {
        const curTime = $("#pk-time", sheet.bodyEl).value || "08:00";
        zone.innerHTML = pickerHTML(at(day, curTime).getTime());
      }
    });
  });
}

/* ---------------- sampling form ---------------- */

export function openSamplingForm({ text = "", editTask = null } = {}) {
  const pre = text ? parseQuick(text) : null;
  const task = editTask;
  const defTs = task ? task.dueAt : pre?.time || null;

  const sheet = openSheet({
    title: task ? "编辑取样提醒" : "新建取样提醒",
    body: `
      <div class="form-grid">
        <div class="form-field">
          <label>客户 <span class="req">*</span></label>
          <div style="display:flex;gap:8px">
            <select id="sf-customer" class="text-field">${customerOptions(task?.customer || pre?.customer)}</select>
            <button id="sf-add-customer" class="icon-btn" title="新增客户" style="width:46px;height:46px;flex:none">${icon("plus")}</button>
          </div>
        </div>
        <div class="form-field">
          <label>胶类型 <span class="req">*</span></label>
          <select id="sf-type" class="text-field">${typeOptions(task?.typeId || pre?.typeId || "dtj")}</select>
        </div>
        <div class="form-field">
          <label>取样时间 <span class="req">*</span></label>
          <div style="display:flex;gap:8px">
            <input id="sf-due" class="text-field mono" placeholder="点选时间" value="${defTs ? hm(new Date(defTs)) : ""}" readonly style="flex:1;cursor:pointer" />
            <button id="sf-pick" class="btn ghost" style="flex:none">${icon("calendar")} 选择</button>
          </div>
        </div>
        <div class="form-field">
          <label>备注</label>
          <input id="sf-note" class="text-field" placeholder="如：重点关注 / 先做粘度" value="${esc(task?.note || pre?.note || "")}" />
        </div>
        <div class="form-field">
          <label>提前提醒</label>
          <div class="seg" id="sf-pre">
            ${[0, 5, 10, 15]
              .map((m) => {
                const cur = task ? (task.preAlertMinutes == null ? 5 : task.preAlertMinutes) : 5;
                return `<button type="button" data-m="${m}" class="${cur === m ? "on" : ""}">${m === 0 ? "不提醒" : `提前${m}分`}</button>`;
              })
              .join("")}
          </div>
        </div>
      </div>`,
    actions: [
      ...(task ? [{ label: "删除", cls: "danger", icon: "trash", onClick: removeTask }] : []),
      { label: "取消", cls: "ghost", onClick: () => sheet.close() },
      { label: task ? "保存" : "创建提醒", cls: "primary", icon: "check", onClick: submit },
    ],
  });

  let dueTs = defTs;
  let preMin = task ? (task.preAlertMinutes == null ? 5 : task.preAlertMinutes) : 5;

  const dueInput = $("#sf-due", sheet.bodyEl);
  $("#sf-pick", sheet.bodyEl).onclick = async () => {
    const ts = await pickDateTime({ value: dueTs });
    if (ts) {
      dueTs = ts;
      dueInput.value = hm(new Date(ts));
    }
  };
  dueInput.onclick = () => $("#sf-pick", sheet.bodyEl).click();

  $("#sf-pre", sheet.bodyEl).addEventListener("click", (e) => {
    const b = e.target.closest("[data-m]");
    if (!b) return;
    preMin = Number(b.dataset.m);
    $$("#sf-pre button", sheet.bodyEl).forEach((x) => x.classList.toggle("on", x === b));
  });

  $("#sf-add-customer", sheet.bodyEl).onclick = async () => {
    const name = await promptDialog({ title: "新增客户", message: "输入客户 / 厂家名称" });
    if (name) {
      addCustomer(name);
      $("#sf-customer", sheet.bodyEl).innerHTML = customerOptions(name);
    }
  };

  async function removeTask() {
    const ok = await confirmDialog({ title: "删除取样提醒", message: "这条提醒将被移除。", danger: true, okLabel: "删除" });
    if (ok) {
      removeSampling(task.id);
      toast("已删除");
      sheet.close();
    }
  }

  function submit() {
    const customer = $("#sf-customer", sheet.bodyEl).value;
    const typeId = $("#sf-type", sheet.bodyEl).value;
    const note = $("#sf-note", sheet.bodyEl).value.trim();
    if (!dueTs) {
      toast("请选择取样时间", "warn");
      return;
    }
    if (task) {
      updateSampling(task.id, { customer, typeId, dueAt: dueTs, note, preAlertMinutes: preMin, preAlerted: false, dueAlarmed: false });
      toast("已保存");
    } else {
      addSampling({ customer, typeId, dueAt: dueTs, note, preAlertMinutes: preMin });
      toast("已添加取样提醒");
    }
    sheet.close();
  }
}

/* ---------------- bake form ---------------- */

export function openBakeForm({ text = "", sample = null, oven = "door", editBake = null } = {}) {
  const pre = text ? parseQuick(text) : null;
  const b = editBake;
  const types = getState().types;
  const selType = sample?.typeId || pre?.typeId || types[0]?.id;
  const type = types.find((t) => t.id === selType);
  const presets = type?.bakes || [];

  const sheet = openSheet({
    title: b ? "调整烘烤" : "新建烘烤计时",
    body: `
      <div class="form-grid">
        ${
          b
            ? `<div class="prompt-line">${esc(b.label)} · 当前 ${b.tempC}℃ × ${b.minutes}分</div>`
            : sample
              ? `<div class="prompt-line">${esc(sample.customer)} · ${esc(type?.name || "")} · 批次 ${esc(sample.batchNo)}</div>`
              : `<div class="form-field">
                  <label>名称 <span class="req">*</span></label>
                  <input id="bf-label" class="text-field" placeholder="如：安徽万安 底涂胶" value="${esc(
                    [pre?.customer, pre?.typeId ? types.find((t) => t.id === pre.typeId)?.name : ""].filter(Boolean).join(" ")
                  )}" />
                </div>
                <div class="form-field">
                  <label>快速预设</label>
                  <div class="bake-presets" id="bf-presets">
                    ${presets.map((p) => `<button type="button" class="preset-btn" data-temp="${p.temp}" data-min="${p.minutes}">${icon("flame")} ${p.temp}℃ × ${p.minutes}分</button>`).join("") || `<span class="form-hint">该类型暂无预设，可直接输入温度和时长</span>`}
                  </div>
                </div>`
        }
        <div class="form-field">
          <label>烘箱</label>
          <div class="oven-choice" id="bf-oven">
            <button type="button" class="type-opt ${(b?.oven || oven) === "door" ? "on" : ""}" data-oven="door">${icon("door")} 靠门烘箱</button>
            <button type="button" class="type-opt ${(b?.oven || oven) === "window" ? "on" : ""}" data-oven="window">${icon("windowIco")} 靠窗烘箱</button>
          </div>
        </div>
        <div class="bake-fields">
          <div class="mini-field">
            <label>温度 ℃</label>
            <input id="bf-temp" class="text-field mono" type="number" inputmode="numeric" value="${b?.tempC ?? presets[0]?.temp ?? 140}" />
          </div>
          <div class="mini-field">
            <label>时长 分钟</label>
            <input id="bf-min" class="text-field mono" type="number" inputmode="numeric" value="${b?.minutes ?? presets[0]?.minutes ?? 30}" />
          </div>
          <div class="mini-field">
            <label>开始时间</label>
            <input id="bf-start" class="text-field mono" type="time" value="${b ? hm(new Date(b.startAt)) : hm(new Date())}" />
          </div>
        </div>
        ${!b ? `<label class="checkline"><input type="checkbox" id="bf-sound" checked /> 到点响铃提醒</label>` : ""}
      </div>`,
    actions: [
      ...(b ? [{ label: "停止计时", cls: "danger", icon: "pause", onClick: stopBake }] : []),
      { label: "取消", cls: "ghost", onClick: () => sheet.close() },
      { label: b ? "保存调整" : "开始计时", cls: "primary", icon: b ? "check" : "play", onClick: submit },
    ],
  });

  let ovenSel = b?.oven || oven;
  $("#bf-oven", sheet.bodyEl).addEventListener("click", (e) => {
    const btn = e.target.closest("[data-oven]");
    if (!btn) return;
    ovenSel = btn.dataset.oven;
    $$("#bf-oven .type-opt", sheet.bodyEl).forEach((x) => x.classList.toggle("on", x === btn));
  });

  $("#bf-presets", sheet.bodyEl)?.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-temp]");
    if (!btn) return;
    $("#bf-temp", sheet.bodyEl).value = btn.dataset.temp;
    $("#bf-min", sheet.bodyEl).value = btn.dataset.min;
  });

  async function stopBake() {
    const ok = await confirmDialog({ title: "结束计时", message: "将记录完成时间并停止提醒。", okLabel: "结束" });
    if (ok) {
      markBakeDone(b.id);
      toast("已结束计时");
      sheet.close();
    }
  }

  function submit() {
    const tempC = Number($("#bf-temp", sheet.bodyEl).value);
    const minutes = Number($("#bf-min", sheet.bodyEl).value);
    const startStr = $("#bf-start", sheet.bodyEl).value;
    if (!tempC || tempC < 30 || tempC > 300) {
      toast("温度需在 30~300℃ 之间", "warn");
      return;
    }
    if (!minutes || minutes <= 0 || minutes > 600) {
      toast("时长需在 1~600 分钟", "warn");
      return;
    }
    if (!startStr) {
      toast("请选择开始时间", "warn");
      return;
    }
    const [h, m] = startStr.split(":").map(Number);
    const start = new Date();
    start.setHours(h, m, 0, 0);
    if (start.getTime() > Date.now() + 60000) start.setTime(start.getTime() - 86400000); // 刚过零点补录昨晚放入的

    if (b) {
      updateBake(b.id, { tempC, minutes, startAt: start.getTime(), oven: ovenSel, preAlerted: false });
      toast("已调整");
    } else {
      const label = $("#bf-label", sheet.bodyEl)?.value.trim();
      if (!label) {
        toast("给这次烘烤起个名字", "warn");
        return;
      }
      const sound = $("#bf-sound", sheet.bodyEl)?.checked ?? true;
      addBake({ oven: ovenSel, sampleId: sample?.id || null, label, tempC, minutes, startAt: start.getTime(), sound });
      toast(`${label} · 开始计时`);
    }
    sheet.close();
  }
}
