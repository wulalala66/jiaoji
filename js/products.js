/* ============================================================
   胶记 · 产品条件表
   查一下就知道某家的胶要烤多少度多少分钟
   ============================================================ */

import { el, debounce, num, clear } from "./utils.js";
import { actions, getState, plateLines, plateText } from "./store.js";
import { confirmDialog, emptyHint, openSheet, pill, toast } from "./ui.js";

export function renderProducts(root) {
  let keyword = "";

  root.appendChild(
    el(
      "header",
      { class: "page-head" },
      el("div", null, el("h1", null, "产品条件表"), el("p", { class: "page-sub" }, "谁家的胶，多少度烤多久")),
      el(
        "button",
        { class: "btn btn-primary btn-sm", type: "button", onclick: () => editProduct(null) },
        "＋ 新增"
      )
    )
  );

  const search = el("input", {
    class: "input search",
    type: "search",
    placeholder: "搜产品 / 客户 / 温度",
    autocomplete: "off",
    oninput: debounce((e) => {
      keyword = e.target.value.trim();
      draw();
    }, 150),
  });
  root.appendChild(search);

  const list = el("div", { class: "list" });
  root.appendChild(list);

  function draw() {
    list.replaceChildren();
    const k = keyword.toLowerCase();
    // 每次重画都取当前状态：增删改之后 mutate 会换掉整个 state 对象
    const items = getState().products.filter(
      (p) =>
        !k ||
        p.name.toLowerCase().includes(k) ||
        p.bakes.some((b) => `${b.temp} ${b.minutes}`.includes(k))
    );
    if (!items.length) {
      list.appendChild(emptyHint("🔍", "没找到", "换个词试试，或者点右上角「新增」"));
      return;
    }
    for (const p of items) {
      list.appendChild(
        el(
          "button",
          { class: "card list-item", type: "button", onclick: () => editProduct(p) },
          el(
            "div",
            { class: "list-main" },
            el("div", { class: "list-title" }, p.name),
            el(
              "div",
              { class: "chips" },
              p.bakes.length
                ? p.bakes.map((b) => pill(`${b.temp}℃ ${b.minutes}分`, "pill-brand"))
                : el("span", { class: "muted" }, "还没填烘烤条件")
            ),
            p.plates?.length
              ? el("div", { class: "chips" }, pill("🧫 " + plateText(p), p.plates.some((x) => x.fresh) ? "pill-warn" : ""))
              : null,
            p.plates?.length && plateLines(p).some((l) => l.includes("·"))
              ? el("div", { class: "plate-notes" }, plateLines(p).filter((l) => l.includes("·")).join(" ｜ "))
              : null
          )
        )
      );
    }
  }

  draw();

  function editProduct(existing) {
    openSheet(existing ? "改产品" : "新增产品", (body, close) => {
      const nameInput = el("input", {
        class: "input",
        type: "text",
        value: existing?.name || "",
        placeholder: "比如 安徽万安",
        maxlength: "40",
      });

      const rows = el("div", { class: "bake-rows" });
      const addRow = (temp = "140", minutes = "30") => {
        const t = el("input", {
          class: "input num",
          type: "text",
          inputmode: "numeric",
          value: temp,
          "aria-label": "烘烤温度，摄氏度",
        });
        const m = el("input", {
          class: "input num",
          type: "text",
          inputmode: "numeric",
          value: minutes,
          "aria-label": "烘烤时间，分钟",
        });
        // 标签在上、输入框在下：手机上框子才不会被挤扁
        const row = el(
          "div",
          { class: "bake-row" },
          el("label", { class: "field" }, el("span", { class: "field-label" }, "温度 ℃"), t),
          el("label", { class: "field" }, el("span", { class: "field-label" }, "时间 分钟"), m),
          el(
            "button",
            {
              class: "icon-btn danger bake-del",
              type: "button",
              "aria-label": "删掉这条",
              onclick: () => {
                if (rows.children.length > 1) row.remove();
              },
            },
            "✕"
          )
        );
        rows.appendChild(row);
      };

      if (existing?.bakes?.length) existing.bakes.forEach((b) => addRow(String(b.temp), String(b.minutes)));
      else addRow();

      // 电泳板：每块一行，可切换新/旧，可写用途
      const plateRows = el("div", { class: "bake-rows" });
      const plates = (existing?.plates || []).map((pl) => ({ ...pl }));
      const drawPlates = () => {
        clear(plateRows);
        plates.forEach((pl, i) => {
          const tag = el(
            "button",
            {
              class: `chip-btn${pl.fresh ? " on" : ""}`,
              type: "button",
              onclick: () => {
                plates[i].fresh = !plates[i].fresh;
                drawPlates();
              },
            },
            pl.fresh ? "新板" : "旧板"
          );
          const note = el("input", {
            class: "input",
            type: "text",
            value: pl.note || "",
            placeholder: "用途 / 要求，可空",
            maxlength: "40",
            oninput: (e) => {
              plates[i].note = e.target.value;
            },
          });
          plateRows.appendChild(
            el(
              "div",
              { class: "bake-row" },
              el("div", { class: "plate-tag" }, tag),
              note,
              el(
                "button",
                {
                  class: "icon-btn danger bake-del",
                  type: "button",
                  "aria-label": "删掉这块板",
                  onclick: () => {
                    plates.splice(i, 1);
                    drawPlates();
                  },
                },
                "✕"
              )
            )
          );
        });
      };
      drawPlates();

      body.appendChild(field("产品 / 客户名", nameInput));
      body.appendChild(el("div", { class: "field-label" }, "烘烤条件（可以加多条）"));
      body.appendChild(rows);
      body.appendChild(
        el("button", { class: "btn btn-ghost btn-block", type: "button", onclick: () => addRow("150", "25") }, "＋ 再加一条条件")
      );

      body.appendChild(el("div", { class: "field-label", style: { "margin-top": "6px" } }, "电泳板（每块单独记用途，不需要就不加）"));
      body.appendChild(plateRows);
      body.appendChild(
        el(
          "button",
          { class: "btn btn-ghost btn-block", type: "button", onclick: () => { plates.push({ fresh: false, note: "" }); drawPlates(); } },
          "＋ 加一块板"
        )
      );
      body.appendChild(
        el(
          "p",
          { class: "hint" },
          "例：安徽万安 4 块 —— 1 块新板写「2mm 流挂」，3 块旧板空着；也可以一块写「测附着力」、一块写「测硬度」。"
        )
      );

      const collectPlates = () => plates.map((pl) => ({ id: pl.id || "", fresh: !!pl.fresh, note: (pl.note || "").trim() }));

      const collect = () => {
        const bakes = [];
        rows.querySelectorAll(".bake-row").forEach((row) => {
          const [t, m] = row.querySelectorAll("input");
          const temp = num(t.value);
          const minutes = num(m.value);
          if (temp >= 30 && temp <= 300 && minutes >= 1 && minutes <= 600) {
            bakes.push({ temp: Math.round(temp), minutes: Math.round(minutes) });
          }
        });
        return bakes;
      };

      body.appendChild(
        el(
          "div",
          { class: "sheet-actions" },
          existing
            ? el(
                "button",
                {
                  class: "btn btn-danger-ghost",
                  type: "button",
                  onclick: async () => {
                    const ok = await confirmDialog({
                      title: "删掉这个产品？",
                      message: `「${existing.name}」和它的烘烤条件会一起删掉。`,
                      confirm: "删掉",
                      danger: true,
                    });
                    if (ok) {
                      actions.removeProduct(existing.id);
                      close();
                      toast("已删除", "ok");
                      draw();
                    }
                  },
                },
                "删除"
              )
            : el("span"),
          el(
            "button",
            {
              class: "btn btn-primary",
              type: "button",
              onclick: () => {
                const name = nameInput.value.trim();
                if (!name) {
                  toast("先填产品名", "warn");
                  nameInput.focus();
                  return;
                }
                if (existing) actions.updateProduct(existing.id, name, collect(), collectPlates());
                else if (!actions.addProduct(name, collect(), collectPlates())) {
                  toast("已经有同名产品了", "warn");
                  return;
                }
                close();
                toast("已保存", "ok");
                draw();
              },
            },
            "保存"
          )
        )
      );
      nameInput.focus();
    });
  }
}

function field(label, control) {
  return el("label", { class: "field" }, el("span", { class: "field-label" }, label), control);
}
