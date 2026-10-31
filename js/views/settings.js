/* ============================================================
   胶记 · view: Settings (设置页)
   ============================================================ */
import { getState, customers, updateSettings, addCustomer, renameCustomer, deleteCustomer, addType, updateType, deleteType, addTypeBake, removeTypeBake, importJSON, clearAll } from "../store.js";
import { uid } from "../utils.js";
import { icon, toast, confirmDialog, promptDialog, openSheet } from "../ui.js";
import { esc, pickFile, download } from "../utils.js";
import { previewSound, unlockAudio } from "../alarms.js";
import { emptyState } from "./today.js";

export function renderSettings(root) {
  const st = getState();
  const s = st.settings;

  root.innerHTML = `
    <div class="page-head">
      <p class="eyebrow">SETTINGS</p>
      <h1>设置</h1>
      <p class="page-sub">提醒方式、产品预设、数据管理。</p>
    </div>

    <div class="set-section">
      <div class="set-title">提醒</div>
      <div class="set-group">
        <div class="set-row">
          <div class="set-main">
            <div class="set-label">提前提醒</div>
            <div class="set-sub">取样前 X 分钟响铃</div>
          </div>
          <div class="stepper" id="st-pre">
            <button data-d="-5">−</button>
            <span class="mono">${s.preAlertMinutes ?? 5}分</span>
            <button data-d="5">＋</button>
          </div>
        </div>
        <div class="set-row">
          <div class="set-main">
            <div class="set-label">铃声</div>
            <div class="set-sub">到点提醒的声音</div>
          </div>
          <div class="set-side">
            <button class="btn ghost sm" id="set-ring-preview">${icon("volume")} 试听</button>
            <label class="switch"><input type="checkbox" id="set-sound" ${s.sound ? "checked" : ""} /><i></i></label>
          </div>
        </div>
        <div class="set-row">
          <div class="set-main">
            <div class="set-label">振动</div>
            <div class="set-sub">响铃时一并振动（部分安卓）</div>
          </div>
          <label class="switch"><input type="checkbox" id="set-vibrate" ${s.vibrate ? "checked" : ""} /><i></i></label>
        </div>
        <div class="set-row">
          <div class="set-main">
            <div class="set-label">屏幕常亮</div>
            <div class="set-sub">有计时器时屏幕不会熄灭</div>
          </div>
          <label class="switch"><input type="checkbox" id="set-keepawake" ${s.keepAwake ? "checked" : ""} /><i></i></label>
        </div>
        <div class="set-row">
          <div class="set-main">
            <div class="set-label">系统通知</div>
            <div class="set-sub" id="notify-state">${notifStateText()}</div>
          </div>
          <button class="btn ghost sm" id="set-notify">${icon("bell")} 开启</button>
        </div>
      </div>
      <p class="set-note">注意：网页在完全关闭后无法响铃。请保持页面在后台运行（可锁定屏幕但别划掉）。</p>
    </div>

    <div class="set-section">
      <div class="set-title">客户</div>
      <div class="set-group" id="cust-list"></div>
      <button class="btn soft block" id="cust-add" style="margin-top:8px">${icon("plus")} 添加客户</button>
    </div>

    <div class="set-section">
      <div class="set-title">胶类型与检测项</div>
      <div class="set-group" id="type-list"></div>
      <button class="btn soft block" id="type-add" style="margin-top:8px">${icon("plus")} 添加胶类型</button>
    </div>

    <div class="set-section">
      <div class="set-title">数据</div>
      <div class="set-group">
        <button class="set-row as-btn" id="data-export">
          <div class="set-main">
            <div class="set-label">导出备份 (JSON)</div>
            <div class="set-sub">包含全部样品、记录与设置</div>
          </div>
          <span class="set-chev">${icon("chevronRight")}</span>
        </button>
        <button class="set-row as-btn" id="data-import">
          <div class="set-main">
            <div class="set-label">导入备份</div>
            <div class="set-sub">从备份文件恢复（覆盖当前数据）</div>
          </div>
          <span class="set-chev">${icon("chevronRight")}</span>
        </button>
        <button class="set-row as-btn danger-line" id="data-clear">
          <div class="set-main">
            <div class="set-label">清空全部数据</div>
            <div class="set-sub">恢复到初始状态，不可撤销</div>
          </div>
          <span class="set-chev">${icon("trash")}</span>
        </button>
      </div>
    </div>

    <div class="about-block">
      <div class="about-name">胶记 <span class="mono">v1.0</span></div>
      <div class="about-sub">取样 · 烘烤 · 检测，胶粘剂实验室的工作台。数据保存在本设备浏览器中。</div>
    </div>
  `;

  renderCustomers(root);
  renderTypes(root);

  // settings bindings
  root.querySelector("#st-pre").addEventListener("click", (e) => {
    const b = e.target.closest("[data-d]");
    if (!b) return;
    const cur = getState().settings.preAlertMinutes ?? 5;
    const next = Math.min(60, Math.max(0, cur + Number(b.dataset.d)));
    updateSettings({ preAlertMinutes: next });
    root.querySelector("#st-pre .mono").textContent = `${next}分`;
  });

  root.querySelector("#set-sound").onchange = (e) => {
    updateSettings({ sound: e.target.checked });
    if (e.target.checked) previewSound("alarm");
  };
  root.querySelector("#set-ring-preview").onclick = () => {
    unlockAudio();
    previewSound("alarm");
  };
  root.querySelector("#set-vibrate").onchange = (e) => updateSettings({ vibrate: e.target.checked });
  root.querySelector("#set-keepawake").onchange = (e) => updateSettings({ keepAwake: e.target.checked });
  root.querySelector("#set-notify").onclick = requestNotify;

  // data
  root.querySelector("#data-export").onclick = () => {
    download(`胶记备份_${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(getState(), null, 2), "application/json");
    toast("备份已下载");
  };
  root.querySelector("#data-import").onclick = async () => {
    const text = await pickFile(".json,application/json");
    if (!text) return;
    try {
      importJSON(text);
      toast("导入成功");
      renderSettings(root);
    } catch (err) {
      toast(`导入失败：${err.message}`, "bad");
    }
  };
  root.querySelector("#data-clear").onclick = async () => {
    const ok = await confirmDialog({
      title: "清空全部数据？",
      message: "所有样品、记录、计时器与设置都会被删除，且无法恢复。",
      okLabel: "全部清空",
      danger: true,
    });
    if (ok) {
      clearAll();
      toast("已清空");
      renderSettings(root);
    }
  };
}

function notifStateText() {
  if (!("Notification" in window)) return "此浏览器不支持";
  if (Notification.permission === "granted") return "已开启";
  if (Notification.permission === "denied") return "已被浏览器拒绝，请在设置中手动允许";
  return "未开启";
}

async function requestNotify() {
  if (!("Notification" in window)) {
    toast("此浏览器不支持系统通知", "warn");
    return;
  }
  if (Notification.permission === "granted") {
    toast("通知已经开启啦");
    return;
  }
  const p = await Notification.requestPermission();
  updateSettings({ notifyAsked: true });
  const el = document.querySelector("#notify-state");
  if (el) el.textContent = notifStateText();
  if (p === "granted") toast("通知已开启，页面在后台也会提醒");
  else if (p === "denied") toast("浏览器拒绝了通知权限", "warn");
}

function renderCustomers(root) {
  const list = customers();
  const el = root.querySelector("#cust-list");

  root.querySelector("#cust-add").onclick = async () => {
    const name = await promptDialog({ title: "添加客户", message: "输入客户 / 厂家名称" });
    if (name) {
      addCustomer(name);
      renderCustomers(root);
    }
  };

  if (!list.length) {
    el.innerHTML = `<div class="set-row"><div class="set-main"><div class="set-sub">还没有客户，点下方添加</div></div></div>`;
    return;
  }
  el.innerHTML = list
    .map(
      (c) => `
      <div class="set-row" data-cust="${esc(c)}">
        <div class="set-main">
          <div class="set-label">${esc(c)}</div>
        </div>
        <div class="set-side">
          <button class="icon-btn sm" data-cust-edit title="重命名">${icon("pencil")}</button>
          <button class="icon-btn sm" data-cust-del title="删除">${icon("trash")}</button>
        </div>
      </div>`
    )
    .join("");

  el.querySelectorAll("[data-cust]").forEach((row) => {
    row.querySelector("[data-cust-edit]").onclick = async () => {
      const old = row.dataset.cust;
      const name = await promptDialog({ title: "重命名客户", message: old, value: old });
      if (name && name !== old) {
        renameCustomer(old, name);
        renderCustomers(root);
      }
    };
    row.querySelector("[data-cust-del]").onclick = async () => {
      const old = row.dataset.cust;
      const ok = await confirmDialog({ title: "删除客户", message: `「${old}」将从列表移除（历史记录保留）。`, danger: true, okLabel: "删除" });
      if (ok) {
        deleteCustomer(old);
        renderCustomers(root);
      }
    };
  });
}

function renderTypes(root) {
  const st = getState();
  const el = root.querySelector("#type-list");
  el.innerHTML = st.types
    .map((t) => {
      const tests = (t.tests || []).map((x) => `<span class="chip line">${esc(x.name)}</span>`).join("");
      const bakes = (t.bakes || []).map((b) => `<span class="chip tint">${b.temp}℃×${b.minutes}分</span>`).join("");
      return `
      <div class="set-row type-row" data-type="${t.id}">
        <div class="set-main">
          <div class="set-label">${esc(t.name)}${t.id === "other" ? '<span class="chip muted" style="margin-left:6px">内置</span>' : ""}</div>
          <div class="type-presets">
            <div class="le-badges">${bakes || '<span class="form-hint">无烘烤预设</span>'}</div>
            <div class="le-badges">${tests || '<span class="form-hint">无检测项</span>'}</div>
          </div>
        </div>
        <div class="set-side">
          <button class="icon-btn sm" data-type-edit title="编辑">${icon("pencil")}</button>
          ${t.id === "other" ? "" : `<button class="icon-btn sm" data-type-del title="删除">${icon("trash")}</button>`}
        </div>
      </div>`;
    })
    .join("");

  el.querySelectorAll("[data-type]").forEach((row) => {
    row.querySelector("[data-type-edit]").onclick = () => {
      const t = getState().types.find((x) => x.id === row.dataset.type);
      openTypeEditor(t, () => renderTypes(root));
    };
    const del = row.querySelector("[data-type-del]");
    if (del)
      del.onclick = async () => {
        const t = getState().types.find((x) => x.id === row.dataset.type);
        const ok = await confirmDialog({ title: "删除类型", message: `删除「${t.name}」及其预设？历史记录保留。`, danger: true, okLabel: "删除" });
        if (ok) {
          deleteType(t.id);
          renderTypes(root);
        }
      };
  });

  root.querySelector("#type-add").onclick = () => openTypeEditor(null, () => renderTypes(root));
}

function openTypeEditor(type, onDone) {
  const isNew = !type;
  const t = type || { id: null, name: "", bakes: [], tests: [] };

  const sheet = openSheet({
    title: isNew ? "添加胶类型" : `编辑「${t.name}」`,
    body: `
      <div class="form-grid">
        <div class="form-field">
          <label>类型名称 <span class="req">*</span></label>
          <input id="te-name" class="text-field" placeholder="如：底涂胶" value="${esc(t.name)}" />
        </div>
        <div class="form-field">
          <label>烘烤预设</label>
          <div id="te-bakes"></div>
          <button class="add-chip" id="te-bake-add" style="margin-top:8px">${icon("plus")} 加预设</button>
        </div>
        <div class="form-field">
          <label>检测项</label>
          <div id="te-tests"></div>
          <button class="add-chip" id="te-test-add" style="margin-top:8px">${icon("plus")} 加检测项</button>
        </div>
      </div>`,
    actions: [
      { label: "取消", cls: "ghost", onClick: () => sheet.close() },
      {
        label: isNew ? "添加" : "保存",
        cls: "primary",
        icon: "check",
        onClick: () => {
          const name = sheet.bodyEl.querySelector("#te-name").value.trim();
          if (!name) {
            toast("先填类型名称", "warn");
            return;
          }
          const bakes = collectBakes();
          const tests = collectTests();
          if (isNew) addType({ name, bakes, tests });
          else updateType(t.id, { name, bakes, tests });
          toast("已保存");
          sheet.close();
          onDone?.();
        },
      },
    ],
  });

  const bakesEl = sheet.bodyEl.querySelector("#te-bakes");
  const testsEl = sheet.bodyEl.querySelector("#te-tests");
  let bakes = (t.bakes || []).map((b) => ({ ...b }));
  let tests = (t.tests || []).map((x) => ({ ...x }));

  const renderBakes = () => {
    bakesEl.innerHTML = bakes
      .map(
        (b, i) => `
      <div class="bake-fields" data-i="${i}">
        <div class="mini-field"><label>温度℃</label><input type="number" class="text-field mono" data-k="temp" value="${b.temp}" /></div>
        <div class="mini-field"><label>分钟</label><input type="number" class="text-field mono" data-k="minutes" value="${b.minutes}" /></div>
        <button class="icon-btn sm" data-del title="删除预设">${icon("x")}</button>
      </div>`
      )
      .join("");
    bakesEl.querySelectorAll("[data-del]").forEach((btn, idx) => {
      btn.onclick = () => {
        bakes.splice(idx, 1);
        renderBakes();
      };
    });
  };
  const renderTests = () => {
    testsEl.innerHTML = tests
      .map(
        (x, i) => `
      <div class="te-test-row" data-i="${i}" data-tid="${esc(x.id || "")}">
        <input class="text-field" data-k="name" placeholder="检测项名称" value="${esc(x.name)}" />
        <input class="text-field mono" data-k="unit" placeholder="单位" value="${esc(x.unit || "")}" style="flex:none;width:86px" />
        <button class="icon-btn sm" data-del title="删除">${icon("x")}</button>
      </div>`
      )
      .join("");
    testsEl.querySelectorAll("[data-del]").forEach((btn, idx) => {
      btn.onclick = () => {
        tests.splice(idx, 1);
        renderTests();
      };
    });
  };

  const collectBakes = () =>
    [...bakesEl.querySelectorAll("[data-i]")].map((row) => ({
      temp: Number(row.querySelector('[data-k="temp"]').value) || 140,
      minutes: Number(row.querySelector('[data-k="minutes"]').value) || 30,
    }));
  const collectTests = () =>
    [...testsEl.querySelectorAll("[data-i]")]
      .map((row, i) => ({
        id: row.dataset.tid || "t_" + uid(),
        name: row.querySelector('[data-k="name"]').value.trim(),
        unit: row.querySelector('[data-k="unit"]').value.trim(),
      }))
      .filter((x) => x.name);

  renderBakes();
  renderTests();
  sheet.bodyEl.querySelector("#te-bake-add").onclick = () => {
    bakes.push({ temp: 140, minutes: 30 });
    renderBakes();
  };
  sheet.bodyEl.querySelector("#te-test-add").onclick = () => {
    tests.push({ name: "", unit: "" });
    renderTests();
  };
}
