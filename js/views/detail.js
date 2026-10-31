/* ============================================================
   胶记 · view: Sample detail (样品详情 + 数据录入)
   ============================================================ */
import { getState, getType, getSample, setTestValue, addTestToSample, removeTestFromSample, markBakeDone, removeBake, completeSample, reopenSample, updateSample, deleteSample, createStandaloneSample } from "../store.js";
import { $, esc, debounce, fmtNum, fmtRange, dateKey, hmsClock, uid } from "../utils.js";
import { icon, toast, openSheet, confirmDialog } from "../ui.js";
import { verdictOf, sampleStats, bakeCardHTML, verdictChip, bindBakeCardActions } from "../cards.js";
import { openBakeForm } from "../forms.js";
import { go } from "../main.js";

let currentId = null;
const saveLater = debounce((id) => {
  // values are saved on each input; this hook exists for future batching
}, 400);

export function openSampleDetail(id) {
  currentId = id;
  go("sample");
}

export function getOpenSampleId() {
  return currentId;
}

export function renderSampleDetail(root) {
  const s = getSample(currentId);
  if (!s) {
    root.innerHTML = `
      <div class="page-head"><h1>样品不存在</h1></div>
      <button class="btn ghost" id="back-home">${icon("arrowLeft")} 回到今天</button>`;
    root.querySelector("#back-home").onclick = () => go("today");
    return;
  }
  const type = getType(s.typeId);
  const st = sampleStats(s);
  const bakes = getState().bakes.filter((b) => b.sampleId === s.id);
  const bakesSorted = [...bakes].sort((a, b) => b.startAt - a.startAt);

  const d = new Date(s.sampledAt);
  const timeStr = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  const dateStr = `${d.getMonth() + 1}月${d.getDate()}日`;

  root.innerHTML = `
    <div class="detail-hero ${s.status === "done" ? "is-done" : ""}">
      <button class="icon-btn plain" id="dt-back">${icon("arrowLeft")}</button>
      <div class="detail-head">
        <div>
          <div class="eyebrow">SAMPLE</div>
          <h1 class="detail-title">${esc(s.customer)} · ${esc(type?.name || "")}</h1>
          <div class="detail-sub mono">${esc(s.batchNo)} · ${dateStr} ${timeStr}取样</div>
        </div>
        ${s.status === "done" ? `<span class="chip ok">已完检</span>` : ""}
      </div>

      <div class="detail-stats">
        <div class="dstat">
          <div class="dstat-n mono">${st.total ? st.pct : 0}%</div>
          <div class="dstat-l">完成度</div>
        </div>
        <div class="dstat">
          <div class="dstat-n mono">${st.done}/${st.total}</div>
          <div class="dstat-l">已录 / 项数</div>
        </div>
        <div class="dstat">
          <div class="dstat-n mono ${st.bad ? "bad-n" : ""}">${st.bad}</div>
          <div class="dstat-l">不合格</div>
        </div>
      </div>

      <div class="detail-actions">
        ${s.status === "done"
          ? `<button class="btn ghost" id="dt-reopen">${icon("rotate")} 重新打开</button>`
          : `<button class="btn primary" id="dt-complete">${icon("checkCircle")} 完成检测</button>`}
        <button class="btn ghost" id="dt-note">${icon("pencil")} 备注</button>
        <button class="btn danger-ghost" id="dt-del">${icon("trash")}</button>
      </div>
      ${s.note ? `<div class="detail-note">“${esc(s.note)}”</div>` : ""}
    </div>

    <section class="section">
      <div class="section-head">
        <h2>烘烤记录</h2>
        <button class="section-link" id="dt-bake-add">${icon("plus")} 添加烘烤</button>
      </div>
      <div id="dt-bakes"></div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2>检测数据</h2>
        <span class="label-note">标准范围可在设置里配置</span>
      </div>
      <div class="card">
        <div id="dt-tests"></div>
        <div class="divider"></div>
        <button class="add-chip" id="dt-test-add">${icon("plus")} 加检测项</button>
      </div>
    </section>
  `;

  // back / actions
  root.querySelector("#dt-back").onclick = () => go("today");
  root.querySelector("#dt-note").onclick = async () => {
    const note = await promptDialog({ title: "备注", message: "记录特殊情况", value: s.note || "" });
    if (note != null) {
      updateSample(s.id, { note });
      renderSampleDetail(root);
    }
  };
  root.querySelector("#dt-del").onclick = async () => {
    const ok = await confirmDialog({ title: "删除样品", message: `删除 ${s.batchNo} 及其所有数据？无法恢复。`, danger: true, okLabel: "删除" });
    if (ok) {
      deleteSample(s.id);
      toast("已删除");
      go("today");
    }
  };

  const completeBtn = root.querySelector("#dt-complete");
  if (completeBtn)
    completeBtn.onclick = async () => {
      const missing = (s.tests || []).filter((t) => !t.skipped && (t.value == null || t.value === "")).length;
      let msg = "确认所有数据已录入完毕？";
      if (missing) msg = `还有 ${missing} 项未录入，确认完成？`;
      const ok = await confirmDialog({ title: "完成检测", message: msg, okLabel: "完成" });
      if (ok) {
        completeSample(s.id);
        toast("样品已完检 🎉");
        go("today");
      }
    };
  const reopenBtn = root.querySelector("#dt-reopen");
  if (reopenBtn)
    reopenBtn.onclick = () => {
      reopenSample(s.id);
      renderSampleDetail(root);
    };

  // bakes
  const bakesEl = root.querySelector("#dt-bakes");
  if (!bakesSorted.length) {
    bakesEl.innerHTML = emptyStateHTML("样品还没有烘烤记录，点右上角「添加烘烤」");
  } else {
    bakesEl.innerHTML = bakesSorted.map((b) => bakeCardHTML(b)).join("");
  }

  // tests
  const testsEl = root.querySelector("#dt-tests");
  if (!(s.tests || []).length) {
    testsEl.innerHTML = `<p class="form-hint">还没有检测项，点下方「加检测项」</p>`;
  } else {
    testsEl.innerHTML = s.tests
      .map((t, idx) => {
        const v = verdictOf(t);
        const rangeStr = fmtRange(t.min, t.max);
        return `
        <div class="test-row" data-tid="${t.id}">
          <span class="test-index">${idx + 1}</span>
          <div class="test-main">
            <div class="test-spec">
              <span class="test-name">${esc(t.name)}</span>
              ${rangeStr ? `<span class="test-range mono">标准 ${esc(rangeStr)}${t.unit ? ` ${esc(t.unit)}` : ""}</span>` : t.unit ? `<span class="test-range">${esc(t.unit)}</span>` : ""}
            </div>
          </div>
          <div class="input-wrap">
            <input class="test-input mono" type="number" step="any" inputmode="decimal" placeholder="—" value="${t.value ?? ""}" ${t.skipped ? "disabled" : ""} />
            ${t.unit ? `<span class="unit">${esc(t.unit)}</span>` : ""}
          </div>
          <div class="test-verdict">${verdictChip(v)}</div>
          <button class="icon-btn sm" data-skip title="${t.skipped ? "恢复录入" : "跳过此项"}">${icon(t.skipped ? "rotate" : "skip")}</button>
          <button class="icon-btn sm" data-deltest title="删除">${icon("x")}</button>
        </div>`;
      })
      .join("");
  }

  // input events (save immediately, silent)
  testsEl.querySelectorAll(".test-row").forEach((row) => {
    const tid = row.dataset.tid;
    const input = row.querySelector(".test-input");
    input.addEventListener("input", () => {
      const v = input.value;
      setTestValue(s.id, tid, { value: v === "" ? null : Number(v) });
      const test = (getSample(s.id)?.tests || []).find((x) => x.id === tid);
      const chip = row.querySelector(".test-verdict");
      if (chip && test) chip.innerHTML = verdictChip(verdictOf(test));
      const t2 = getSample(s.id);
      const st2 = sampleStats(t2);
      const pctEl = root.querySelector(".dstat-n.mono");
      // update stats quietly
      const dstat = root.querySelectorAll(".dstat-n")[0];
      if (dstat) dstat.textContent = `${st2.total ? st2.pct : 0}%`;
      const dn = root.querySelectorAll(".dstat-n")[1];
      if (dn) dn.textContent = `${st2.done}/${st2.total}`;
      const db = root.querySelectorAll(".dstat-n")[2];
      if (db) {
        db.textContent = String(st2.bad);
        db.classList.toggle("bad-n", st2.bad > 0);
      }
    });
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        const rows = [...testsEl.querySelectorAll(".test-input")];
        const i = rows.indexOf(input);
        if (i >= 0 && i < rows.length - 1) rows[i + 1].focus();
        else input.blur();
      }
    });
    row.querySelector("[data-skip]").onclick = () => {
      const t = (getSample(s.id)?.tests || []).find((x) => x.id === tid);
      if (!t) return;
      setTestValue(s.id, tid, { value: t.value, skipped: !t.skipped });
      renderSampleDetail(root);
    };
    row.querySelector("[data-deltest]").onclick = async () => {
      const ok = await confirmDialog({ title: "删除检测项", message: "这一行的数据将被移除。", danger: true, okLabel: "删除" });
      if (ok) {
        removeTestFromSample(s.id, tid);
        renderSampleDetail(root);
      }
    };
  });

  root.querySelector("#dt-test-add").onclick = () => openAddTestSheet(s.id, () => renderSampleDetail(root));

  root.querySelector("#dt-bake-add").onclick = () => openBakeForm({ sample: s });

  // bake cards actions (shared binder)
  bindBakeCardActions(root, { onChanged: () => renderSampleDetail(root) });
}

/** add-test sheet: pick from type template or custom */
function openAddTestSheet(sampleId, onDone) {
  const s = getSample(sampleId);
  const type = getType(s.typeId);
  const existing = new Set((s.tests || []).map((t) => t.name));
  const candidates = (type?.tests || []).filter((t) => !existing.has(t.name));
  const sheet = openSheet({
    title: "加检测项",
    body: `
      <div class="form-grid">
        ${candidates.length
          ? `<div class="form-field"><label>常用检测项</label><div class="chips">${candidates
              .map(
                (t, i) =>
                  `<button type="button" class="chip cand-chip" data-i="${i}">${esc(t.name)}${t.unit ? ` · ${esc(t.unit)}` : ""}</button>`
              )
              .join("")}</div></div>`
          : ""}
        <div class="form-field"><label>自定义检测项</label><input id="at-name" class="text-field" placeholder="名称" /></div>
        <div class="bake-fields">
          <div class="mini-field"><label>单位</label><input id="at-unit" class="text-field mono" placeholder="如 MPa" /></div>
          <div class="mini-field"><label>标准下限</label><input id="at-min" class="text-field mono" type="number" step="any" placeholder="选填" /></div>
          <div class="mini-field"><label>标准上限</label><input id="at-max" class="text-field mono" type="number" step="any" placeholder="选填" /></div>
        </div>
      </div>`,
    actions: [
      { label: "取消", cls: "ghost", onClick: () => sheet.close() },
      {
        label: "添加",
        cls: "primary",
        icon: "check",
        onClick: () => {
          const name = sheet.bodyEl.querySelector("#at-name").value.trim();
          if (!name) {
            toast("先填检测项名称", "warn");
            return;
          }
          addTestToSample(sampleId, {
            name,
            unit: sheet.bodyEl.querySelector("#at-unit").value.trim(),
            min: sheet.bodyEl.querySelector("#at-min").value,
            max: sheet.bodyEl.querySelector("#at-max").value,
          });
          sheet.close();
          onDone?.();
        },
      },
    ],
  });
  sheet.bodyEl.querySelectorAll(".cand-chip").forEach((chip) => {
    chip.onclick = () => {
      const t = candidates[Number(chip.dataset.i)];
      addTestToSample(sampleId, { name: t.name, unit: t.unit || "", min: t.min ?? "", max: t.max ?? "" });
      sheet.close();
      onDone?.();
    };
  });
}

function emptyStateHTML(text) {
  return `<div class="mini-empty">${esc(text)}</div>`;
}

export function openNewSampleSheet() {
  const st = getState();
  const sheet = openSheet({
    title: "新建样品",
    note: "未到取样时间的补录",
    body: `
      <div class="form-grid">
        <div class="form-field">
          <label>客户</label>
          <select id="ns-customer" class="text-field">${st.settings.customerOrder
            .map((c) => `<option>${esc(c)}</option>`)
            .join("")}</select>
        </div>
        <div class="form-field">
          <label>胶类型</label>
          <select id="ns-type" class="text-field">${st.types.map((t) => `<option value="${t.id}">${esc(t.name)}</option>`).join("")}</select>
        </div>
        <div class="bake-fields">
          <div class="mini-field">
            <label>批次号</label>
            <input id="ns-batch" class="text-field mono" placeholder="留空自动" />
          </div>
          <div class="mini-field">
            <label>取样时间</label>
            <input id="ns-time" class="text-field mono" type="time" value="${new Date().toTimeString().slice(0, 5)}" />
          </div>
        </div>
        <div class="form-field">
          <label>备注</label>
          <input id="ns-note" class="text-field" placeholder="选填" />
        </div>
      </div>`,
    actions: [
      { label: "取消", cls: "ghost", onClick: () => sheet.close() },
      {
        label: "建档",
        cls: "primary",
        icon: "check",
        onClick: () => {
          const customer = sheet.bodyEl.querySelector("#ns-customer").value;
          const typeId = sheet.bodyEl.querySelector("#ns-type").value;
          const batch = sheet.bodyEl.querySelector("#ns-batch").value.trim() || null;
          const timeStr = sheet.bodyEl.querySelector("#ns-time").value || "08:00";
          const note = sheet.bodyEl.querySelector("#ns-note").value.trim();
          const [h, m] = timeStr.split(":").map(Number);
          const d = new Date();
          d.setHours(h, m, 0, 0);
          const id = createStandaloneSample({ customer, typeId, batchNo: batch, sampledAt: d.getTime(), note });
          sheet.close();
          openSampleDetail(id);
        },
      },
    ],
  });
}
