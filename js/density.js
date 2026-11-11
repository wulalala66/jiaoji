/* ============================================================
   胶记 · 比重计算器
   公式照抄车间那张 Excel：d1=M3−M1、d2=M4−M2、d3=M5−M1、d4=M6−M2
   比重 = d1/(d1−d2)；固体成分 = d3/d1×100；发泡率 = ((d3−d4)/(d1−d2)−1)×100
   每次测完可以存一条记录（输入 + 结果），下面是历史。
   ============================================================ */

import { el, fixed, num, trimNum, clear } from "./utils.js";
import { actions, getState } from "./store.js";
import { confirmDialog, copyText, toast } from "./ui.js";

/** 六个输入框的值放在模块里，切到别的工具再回来还在 */
const values = { m1: "", m2: "", m3: "", m4: "", m5: "", m6: "" };

export function calc(m1, m2, m3, m4, m5, m6) {
  const d1 = m3 - m1;
  const d2 = m4 - m2;
  const d3 = m5 - m1;
  const d4 = m6 - m2;
  const denom = d1 - d2;
  const okDen = Math.abs(denom) > 1e-9;
  const hasSolid = m5 > 0 && m6 > 0;
  return {
    d1,
    d2,
    d3,
    d4,
    specific: d1 > 0 && okDen ? d1 / denom : null,
    solid: hasSolid && d1 > 0 ? (d3 / d1) * 100 : null,
    foam: hasSolid && okDen ? ((d3 - d4) / denom - 1) * 100 : null,
    hasSolid,
  };
}

export function resultText(r, v) {
  return [
    `比重：${fixed(r.specific, 4)}`,
    `固体成分：${r.solid === null ? "—" : fixed(r.solid, 3) + " %"}`,
    `发泡率：${r.foam === null ? "—" : fixed(r.foam, 3) + " %"}`,
    `M1~M6：${fixed(v.m1, 2)} / ${fixed(v.m2, 2)} / ${fixed(v.m3, 2)} / ${fixed(v.m4, 2)} / ${fixed(v.m5, 2)} / ${fixed(v.m6, 2)}`,
  ].join("\n");
}

function stamp(ms) {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, "0");
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function renderDensity(root) {
  const read = () => ({
    m1: num(values.m1) ?? 0,
    m2: num(values.m2) ?? 0,
    m3: num(values.m3) ?? 0,
    m4: num(values.m4) ?? 0,
    m5: num(values.m5) ?? 0,
    m6: num(values.m6) ?? 0,
  });

  const resultCard = el("section", { class: "card result-card" });
  const saveBtn = el("button", { class: "btn btn-primary btn-block", type: "button" }, "保存这次结果");
  const history = el("div", { class: "list" });

  const current = () => {
    const v = read();
    return { v, r: calc(v.m1, v.m2, v.m3, v.m4, v.m5, v.m6) };
  };

  const fillResult = () => {
    const { v, r } = current();
    resultCard.replaceChildren(
      el(
        "div",
        { class: "result-row" },
        resultBox("比重", fixed(r.specific, 4), r.specific === null),
        resultBox("固体成分", r.solid === null ? "—" : fixed(r.solid, 3) + " %", r.solid === null),
        resultBox("发泡率", r.foam === null ? "—" : fixed(r.foam, 3) + " %", r.foam === null)
      ),
      r.hasSolid ? null : el("p", { class: "hint" }, "填上 M5、M6（固份）才会算固体成分和发泡率；只算比重时前四个值就够了。"),
      el("p", { class: "hint mono" }, `d1 ${fixed(r.d1, 2)}   d2 ${fixed(r.d2, 2)}   d3 ${fixed(r.d3, 2)}   d4 ${fixed(r.d4, 2)}`)
    );
    saveBtn.disabled = r.specific === null;
    saveBtn.textContent = r.specific === null ? "填够 M1~M4 才能保存" : "保存这次结果";
  };

  const inputs = [];

  /** 只收数字：敲 3 2 1 显示 3.21，满三位自动跳下一格（网页没有键盘「下一项」） */
  const fmtDigits = (raw) => {
    const d = String(raw).replace(/\D/g, "").slice(0, 3);
    if (!d) return "";
    return d.length === 1 ? d : d[0] + "." + d.slice(1);
  };

  const field = (key, label, placeholder, index) => {
    const input = el("input", {
      class: "input num",
      type: "text",
      inputmode: "numeric",
      autocomplete: "off",
      placeholder: placeholder || "",
      value: values[key],
      oninput: (e) => {
        const v = fmtDigits(e.target.value);
        values[key] = v;
        if (e.target.value !== v) e.target.value = v;
        fillResult();
        if (v.length >= 4 && index < 5) inputs[index + 1]?.focus();
      },
      onkeydown: (e) => {
        if (e.key === "Enter" && index < 5) {
          e.preventDefault();
          inputs[index + 1]?.focus();
        }
      },
    });
    inputs[index] = input;
    return el("label", { class: "field" }, el("span", { class: "field-label" }, label), input);
  };

  const share = async () => {
    const { v, r } = current();
    const text = resultText(r, v);
    if (navigator.share) {
      try {
        await navigator.share({ title: "比重计算结果", text });
        return;
      } catch {
        /* 用户取消或浏览器不支持 */
      }
    }
    const ok = await copyText(text);
    toast(ok ? "结果已复制" : "复制失败，请手动选中", ok ? "ok" : "warn");
  };

  // ---------------- 历史
  function drawHistory() {
    const log = getState().densityLog;
    clear(history);
    if (!log.length) {
      history.appendChild(
        el("p", { class: "hint" }, "还没有记录。填好称量值后点「保存这次结果」，这里会按时间排下来。")
      );
      return;
    }
    log.forEach((rec) => {
      history.appendChild(
        el(
          "div",
          { class: "card list-item density-rec" },
          el(
            "div",
            { class: "list-main", onclick: () => restore(rec) },
            el("div", { class: "list-sub" }, stamp(rec.at)),
            el(
              "div",
              { class: "rec-values" },
              el("b", null, "比重 " + fixed(rec.specific, 4)),
              el("span", null, "固含 " + (rec.solid === null ? "—" : fixed(rec.solid, 3) + "%")),
              el("span", null, "发泡 " + (rec.foam === null ? "—" : fixed(rec.foam, 3) + "%"))
            ),
            el(
              "div",
              { class: "rec-inputs" },
              `M1~M6  ${fixed(rec.m1, 2)} / ${fixed(rec.m2, 2)} / ${fixed(rec.m3, 2)} / ${fixed(rec.m4, 2)} / ${fixed(rec.m5, 2)} / ${fixed(rec.m6, 2)}`
            )
          ),
          el(
            "button",
            {
              class: "icon-btn danger",
              type: "button",
              "aria-label": "删掉这条",
              onclick: () => {
                actions.removeDensityRecord(rec.id);
                drawHistory();
              },
            },
            "✕"
          )
        )
      );
    });
  }

  /** 点一条记录，把当时的称量值填回上面 */
  function restore(rec) {
    values.m1 = fixed(rec.m1, 2);
    values.m2 = fixed(rec.m2, 2);
    values.m3 = fixed(rec.m3, 2);
    values.m4 = fixed(rec.m4, 2);
    values.m5 = rec.m5 > 0 ? fixed(rec.m5, 2) : "";
    values.m6 = rec.m6 > 0 ? fixed(rec.m6, 2) : "";
    root.querySelectorAll("input.num").forEach((input, i) => {
      input.value = [values.m1, values.m2, values.m3, values.m4, values.m5, values.m6][i] ?? "";
    });
    fillResult();
    window.scrollTo({ top: 0, behavior: "smooth" });
    toast("已填回这次的称量值", "ok");
  }

  saveBtn.addEventListener("click", () => {
    const { v, r } = current();
    if (r.specific === null) return;
    actions.addDensityRecord(v, r);
    drawHistory();
    toast("已保存", "ok");
  });

  root.appendChild(
    el(
      "header",
      { class: "page-head" },
      el("div", null, el("h1", null, "比重计算器"), el("p", { class: "page-sub" }, "六个称量值，出比重 / 固体成分 / 发泡率"))
    )
  );

  root.appendChild(
    el(
      "section",
      { class: "card" },
      el("div", { class: "card-title" }, "称量值"),
      el("div", { class: "grid-2" }, field("m1", "M1", "烘前", 0), field("m2", "M2", "烘前", 1)),
      el("div", { class: "grid-2" }, field("m3", "M3", "烘后", 2), field("m4", "M4", "烘后", 3)),
      el("div", { class: "grid-2" }, field("m5", "M5", "固份，可空", 4), field("m6", "M6", "固份，可空", 5)),
      el(
        "p",
        { class: "hint" },
        "直接敲数字：输 3 2 1 就是 3.21，小数点不用自己打。",
        el("br"),
        "比重 = d1/(d1−d2)；固体成分 = d3/d1×100；发泡率 = ((d3−d4)/(d1−d2)−1)×100"
      )
    )
  );

  root.appendChild(resultCard);
  fillResult();

  root.appendChild(saveBtn);
  root.appendChild(
    el(
      "div",
      { class: "row-gap" },
      el(
        "button",
        {
          class: "btn btn-ghost",
          type: "button",
          onclick: () => {
            Object.keys(values).forEach((k) => (values[k] = ""));
            root.querySelectorAll("input.num").forEach((i) => (i.value = ""));
            fillResult();
          },
        },
        "清空"
      ),
      el("button", { class: "btn btn-primary", type: "button", onclick: share }, "分享结果")
    )
  );

  root.appendChild(
    el(
      "div",
      { class: "section-head" },
      el("h2", null, "历史记录"),
      getState().densityLog.length
        ? el(
            "button",
            {
              class: "chip-btn",
              type: "button",
              onclick: async () => {
                const ok = await confirmDialog({
                  title: "清空历史记录？",
                  message: `${getState().densityLog.length} 条记录会被删掉，不能恢复。`,
                  confirm: "清空",
                  danger: true,
                });
                if (ok) {
                  actions.clearDensityLog();
                  drawHistory();
                }
              },
            },
            "清空"
          )
        : null
    )
  );
  root.appendChild(history);
  drawHistory();
}

function resultBox(label, value, dim) {
  return el(
    "div",
    { class: "result-box" },
    el("div", { class: "result-label" }, label),
    el("div", { class: `result-value${dim ? " dim" : ""}` }, value)
  );
}
