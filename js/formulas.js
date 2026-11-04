/* ============================================================
   胶记 · 配料（配方列表 + 编辑器 + 打印/导出）
   百分比是唯一真相：克数 = 百分比 × 总量 / 100
   ============================================================ */

import { el, fixed, num, trimNum, debounce, clamp } from "./utils.js";
import { actions, getState, gramsOf, restGrams, restPercent, sumPercent } from "./store.js";
import { confirmDialog, emptyHint, pill, toast } from "./ui.js";
import { exportXlsx, printSheet } from "./export.js";
import { askAboutFormula } from "./ai.js";

const F = (v, d = 1) => fixed(v, d);

// ---------------------------------------------------------------- 列表

export function renderFormulas(root, { navigate }) {
  const state = getState();

  root.appendChild(
    el(
      "header",
      { class: "page-head" },
      el("div", null, el("h1", null, "配料"), el("p", { class: "page-sub" }, "一锅按 100% 算，改总量克数自动跟着变")),
      el(
        "button",
        {
          class: "btn btn-primary btn-sm",
          type: "button",
          onclick: () => navigate("#/f/" + actions.addFormula("新配方")),
        },
        "＋ 新建"
      )
    )
  );

  const list = el("div", { class: "list" });

  if (!state.formulas.length) {
    list.appendChild(emptyHint("⚗️", "还没有配方", "点右上角「新建」，比如「底涂胶 A 料」"));
  }

  for (const f of state.formulas) {
    const sum = sumPercent(f);
    const rest = 100 - sum;
    const done = Math.abs(rest) < 0.05;
    list.appendChild(
      el(
        "button",
        { class: "card list-item", type: "button", onclick: () => navigate("#/f/" + f.id) },
        el(
          "div",
          { class: "list-main" },
          el("div", { class: "list-title" }, f.name || "未命名"),
          el(
            "div",
            { class: "list-sub" },
            `${f.items.length} 种原料 · 合计 ${F(sum, 2)}%`,
            done ? "" : el("span", { class: "warn-text" }, ` · 还差 ${F(rest, 1)}%（${F(restGrams(f), 1)} g）`)
          )
        ),
        el("div", { class: "list-side" }, pill(`${F(f.totalG, 1)} g`))
      )
    );
  }

  root.appendChild(list);
  root.appendChild(
    el("p", { class: "hint foot-hint" }, "输克数或百分比都行，另一边自动换算；剩余量可以一键补到某一行。")
  );
}

// ---------------------------------------------------------------- 编辑器

export function renderFormulaEditor(root, formulaId, { navigate }) {
  const find = () => getState().formulas.find((f) => f.id === formulaId) || null;
  const f0 = find();
  if (!f0) {
    root.appendChild(emptyHint("🫥", "这条配方已经不在了", "可能刚被删掉"));
    root.appendChild(el("button", { class: "btn btn-ghost", type: "button", onclick: () => navigate("#/formulas") }, "返回列表"));
    return;
  }

  let renderedItemIds = "";

  const head = el(
    "header",
    { class: "page-head" },
    el("button", { class: "icon-btn", type: "button", "aria-label": "返回", onclick: () => navigate("#/formulas") }, "←"),
    el(
      "div",
      { class: "grow" },
      el("h1", { class: "ellipsis" }, f0.name || "未命名"),
      el("p", { class: "page-sub" }, "配料单")
    ),
    el(
      "button",
      { class: "btn btn-soft btn-sm", type: "button", onclick: () => navigate(`#/f/${formulaId}/ai`) },
      "🤖 问 AI"
    )
  );

  const body = el("div", { class: "form-body" });
  root.appendChild(head);
  root.appendChild(body);

  // ---- 基本信息
  const nameInput = el("input", {
    class: "input",
    type: "text",
    value: f0.name,
    placeholder: "配方名称，比如 底涂胶 A 料",
    maxlength: "60",
    oninput: debounce((e) => actions.updateFormula(formulaId, { name: e.target.value }), 300),
  });

  const totalInput = el("input", {
    class: "input num",
    type: "text",
    inputmode: "decimal",
    value: trimNum(f0.totalG, 1),
    oninput: debounce((e) => {
      const v = num(e.target.value);
      if (v !== null && v > 0) {
        actions.updateFormula(formulaId, { totalG: v });
        redrawAll();
      }
    }, 350),
  });

  const purposeInput = el("input", {
    class: "input",
    type: "text",
    value: f0.purpose || "",
    placeholder: "比如 降低粘度",
    maxlength: "60",
    oninput: debounce((e) => actions.updateFormula(formulaId, { purpose: e.target.value }), 350),
  });

  const cureInput = el("input", {
    class: "input",
    type: "text",
    value: f0.cure || "",
    placeholder: "比如 140*30",
    maxlength: "30",
    oninput: debounce((e) => actions.updateFormula(formulaId, { cure: e.target.value }), 350),
  });

  body.appendChild(
    el(
      "section",
      { class: "card" },
      field("配方名称", nameInput),
      el("div", { class: "grid-2" }, field("一锅总量 (g)", totalInput), el("div", { class: "field" }, el("span", { class: "field-label" }, " "))),
      el("div", { class: "grid-2" }, field("实验目的（打印用）", purposeInput), field("固化温度（打印用）", cureInput))
    )
  );

  // ---- 原料
  const itemsCard = el("section", { class: "card" });
  const restBar = el("div", { class: "rest-bar" });
  body.appendChild(el("div", { class: "section-head" }, el("h2", null, "原料"), el("span", { class: "muted" }, "百分比 / 克数 改哪边都行")));
  body.appendChild(itemsCard);
  body.appendChild(restBar);

  function drawItems() {
    const f = find();
    if (!f) return;
    itemsCard.replaceChildren();
    if (!f.items.length) {
      itemsCard.appendChild(emptyHint("🧪", "还没有原料", "点下面「＋ 加一行」开始"));
    }
    f.items.forEach((it) => itemsCard.appendChild(itemRow(f, it)));
  }

  function itemRow(f, item) {
    // 注意：mutate 会把整个 state 换成新对象，所以闭包里不能缓存 f，
    // 每次输入都重新取当前配方，否则改了总量之后算出来的克数是旧的。
    const current = () => getState().formulas.find((x) => x.id === formulaId) || f;

    const pctInput = el("input", {
      class: "input num",
      type: "text",
      inputmode: "decimal",
      value: trimNum(item.percent, 2),
      "aria-label": "百分比",
      oninput: (e) => {
        const p = clamp(num(e.target.value) ?? 0, 0, 100);
        gramsInput.value = trimNum((p * current().totalG) / 100, 1);
        actions.updateItem(formulaId, item.id, { percent: p });
        drawRest();
      },
    });

    const gramsInput = el("input", {
      class: "input num",
      type: "text",
      inputmode: "decimal",
      value: trimNum(gramsOf(f, item), 1),
      "aria-label": "克数",
      oninput: (e) => {
        const g = Math.max(0, num(e.target.value) ?? 0);
        const total = current().totalG;
        const p = total > 0 ? clamp((g / total) * 100, 0, 100) : 0;
        pctInput.value = trimNum(p, 2);
        actions.updateItem(formulaId, item.id, { percent: p });
        drawRest();
      },
    });

    const nameInputEl = el("input", {
      class: "input item-name",
      type: "text",
      value: item.name,
      placeholder: "原料名，比如 D1NP",
      maxlength: "40",
      oninput: debounce((e) => actions.updateItem(formulaId, item.id, { name: e.target.value }), 300),
    });

    return el(
      "div",
      { class: "item-row" },
      el(
        "div",
        { class: "item-line" },
        nameInputEl,
        el(
          "button",
          {
            class: "icon-btn danger",
            type: "button",
            "aria-label": "删掉这行",
            onclick: async () => {
              const ok = await confirmDialog({
                title: "删掉这一行？",
                message: item.name ? `「${item.name}」会从配方里移除` : "这一行会从配方里移除",
                confirm: "删掉",
                danger: true,
              });
              if (ok) {
                actions.removeItem(formulaId, item.id);
                drawItems();
                drawRest();
              }
            },
          },
          "✕"
        )
      ),
      el(
        "div",
        { class: "item-num-line" },
        el("label", { class: "field" }, el("span", { class: "field-label" }, "克数 g"), gramsInput),
        el("label", { class: "field" }, el("span", { class: "field-label" }, "百分比 %"), pctInput),
        el(
          "button",
          {
            class: "btn btn-soft btn-xs fill-btn",
            type: "button",
            title: "把剩余百分比补到这一行",
            onclick: () => {
              actions.fillRest(formulaId, item.id);
              drawItems();
              drawRest();
            },
          },
          "补满"
        )
      )
    );
  }

  function drawRest() {
    const f = find();
    if (!f) return;
    const rest = restPercent(f);
    const done = Math.abs(rest) < 0.05;
    restBar.replaceChildren(
      el(
        "div",
        { class: `rest-pill${done ? " ok" : ""}` },
        done ? `合计 ${F(sumPercent(f), 2)}%　正好 100%` : `合计 ${F(sumPercent(f), 2)}%　还差 ${F(rest, 1)}%（${F(restGrams(f), 1)} g）`
      )
    );
  }

  function redrawAll() {
    const f = find();
    if (!f) return;
    const ids = f.items.map((i) => i.id).join(",");
    if (ids !== renderedItemIds) {
      renderedItemIds = ids;
      drawItems();
    } else {
      // 总量变了：只刷新克数，不动用户正在输入的内容
      const f2 = find();
      itemsCard.querySelectorAll(".item-row").forEach((row, i) => {
        const g = row.querySelectorAll("input.num")[0];
        const it = f2.items[i];
        if (g && it && document.activeElement !== g) g.value = trimNum(gramsOf(f2, it), 1);
      });
    }
    drawRest();
  }

  drawItems();
  drawRest();

  body.appendChild(
    el(
      "button",
      {
        class: "btn btn-ghost btn-block",
        type: "button",
        onclick: () => {
          actions.addItem(formulaId, "", 0);
          drawItems();
          drawRest();
        },
      },
      "＋ 加一行原料"
    )
  );

  // ---- 操作
  body.appendChild(
    el("div", { class: "section-head" }, el("h2", null, "打印 / 导出"))
  );

  body.appendChild(
    el(
      "div",
      { class: "card action-card" },
      el(
        "button",
        { class: "btn btn-soft btn-block", type: "button", onclick: () => printSheet(find()) },
        "🖨 打印 / 存 PDF"
      ),
      el(
        "button",
        { class: "btn btn-soft btn-block", type: "button", onclick: () => exportXlsx(find()) },
        "📊 导出 Excel"
      ),
      el(
        "p",
        { class: "hint" },
        "A4 竖版配料单，左边原料用量、右边检测项目。打印时选「另存为 PDF」就能发微信。"
      )
    )
  );

  body.appendChild(
    el(
      "div",
      { class: "row-gap" },
      el(
        "button",
        {
          class: "btn btn-ghost",
          type: "button",
          onclick: () => {
            const id = actions.duplicateFormula(formulaId);
            toast("已复制一份", "ok");
            navigate("#/f/" + id);
          },
        },
        "复制一份"
      ),
      el(
        "button",
        {
          class: "btn btn-danger-ghost",
          type: "button",
          onclick: async () => {
            const ok = await confirmDialog({
              title: "删掉这个配方？",
              message: `「${find()?.name || "未命名"}」删掉后不能恢复。`,
              confirm: "删掉",
              danger: true,
            });
            if (ok) {
              actions.removeFormula(formulaId);
              toast("已删除", "ok");
              navigate("#/formulas");
            }
          },
        },
        "删除配方"
      )
    )
  );

  // ---- AI 点评卡
  body.appendChild(aiCard(formulaId, navigate));
}

function aiCard(formulaId, navigate) {
  const ai = getState().settings.ai;
  const f = getState().formulas.find((x) => x.id === formulaId);
  const card = el("section", { class: "card ai-card" });

  if (!ai.baseUrl || !ai.apiKey || !ai.model) {
    card.appendChild(el("div", { class: "ai-title" }, "🤖 AI 助手"));
    card.appendChild(
      el("p", { class: "hint" }, "还没配置。去「设置 → AI 助手」填接口地址和 Key，就能让 AI 读配方、直接改配料。")
    );
    card.appendChild(
      el("button", { class: "btn btn-soft btn-sm", type: "button", onclick: () => navigate("#/settings") }, "去设置")
    );
    return card;
  }

  card.appendChild(
    el(
      "div",
      { class: "ai-head" },
      el("div", { class: "ai-title" }, "🤖 AI 助手"),
      el("button", { class: "btn btn-soft btn-sm", type: "button", onclick: () => navigate(`#/f/${formulaId}/ai`) }, "打开对话")
    )
  );
  card.appendChild(
    el("p", { class: "hint" }, "让 AI 看一眼配比是否合理，或者直接说「D1NP 提到 30%，其余按比例缩小」。")
  );
  card.appendChild(
    el(
      "button",
      {
        class: "btn btn-primary btn-block",
        type: "button",
        onclick: () => {
          navigate(`#/f/${formulaId}/ai`);
          askAboutFormula(
            formulaId,
            `看一下「${f?.name || "这个配方"}」：合计是不是 100%？有没有明显不合理的配比？` +
              `只挑最关键的一两点说；该改的地方直接调用工具改掉，然后告诉我改了什么。`
          );
        },
      },
      "问一下"
    )
  );
  return card;
}

function field(label, control) {
  return el("label", { class: "field" }, el("span", { class: "field-label" }, label), control);
}

