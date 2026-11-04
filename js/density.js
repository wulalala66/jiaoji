/* ============================================================
   胶记 · 比重计算器
   公式照抄车间那张 Excel：d1=M3−M1、d2=M4−M2、d3=M5−M1、d4=M6−M2
   比重 = d1/(d1−d2)；固体成分 = d3/d1×100；发泡率 = ((d3−d4)/(d1−d2)−1)×100
   ============================================================ */

import { el, fixed, num } from "./utils.js";
import { copyText, toast } from "./ui.js";

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
  const fillResult = () => {
    const v = read();
    const r = calc(v.m1, v.m2, v.m3, v.m4, v.m5, v.m6);
    resultCard.replaceChildren(
      el(
        "div",
        { class: "result-row" },
        resultBox("比重", fixed(r.specific, 4), r.specific === null),
        resultBox("固体成分", r.solid === null ? "—" : fixed(r.solid, 3) + " %", r.solid === null),
        resultBox("发泡率", r.foam === null ? "—" : fixed(r.foam, 3) + " %", r.foam === null)
      ),
      r.hasSolid
        ? null
        : el(
            "p",
            { class: "hint" },
            "填上 M5、M6（固份）才会算固体成分和发泡率；只算比重时前四个值就够了。"
          ),
      el("p", { class: "hint mono" }, `d1 ${fixed(r.d1, 2)}   d2 ${fixed(r.d2, 2)}   d3 ${fixed(r.d3, 2)}   d4 ${fixed(r.d4, 2)}`)
    );
    return r;
  };

  const field = (key, label, placeholder) =>
    el(
      "label",
      { class: "field" },
      el("span", { class: "field-label" }, label),
      el("input", {
        class: "input num",
        type: "text",
        inputmode: "decimal",
        autocomplete: "off",
        placeholder: placeholder || "",
        value: values[key],
        oninput: (e) => {
          values[key] = e.target.value;
          fillResult();
        },
      })
    );

  const share = async () => {
    const v = read();
    const r = calc(v.m1, v.m2, v.m3, v.m4, v.m5, v.m6);
    const text = resultText(r, v);
    if (navigator.share) {
      try {
        await navigator.share({ title: "比重计算结果", text });
        return;
      } catch {
        /* 用户取消或浏览器不支持，落到复制 */
      }
    }
    const ok = await copyText(text);
    toast(ok ? "结果已复制" : "复制失败，请手动选中", ok ? "ok" : "warn");
  };

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
      el("div", { class: "grid-2" }, field("m1", "M1", "烘前"), field("m2", "M2", "烘前")),
      el("div", { class: "grid-2" }, field("m3", "M3", "烘后"), field("m4", "M4", "烘后")),
      el("div", { class: "grid-2" }, field("m5", "M5", "固份，可空"), field("m6", "M6", "固份，可空")),
      el(
        "p",
        { class: "hint" },
        "d1=M3−M1　d2=M4−M2　d3=M5−M1　d4=M6−M2",
        el("br"),
        "比重 = d1/(d1−d2)；固体成分 = d3/d1×100；发泡率 = ((d3−d4)/(d1−d2)−1)×100"
      )
    )
  );

  root.appendChild(resultCard);
  fillResult();

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
}

function resultBox(label, value, dim) {
  return el(
    "div",
    { class: "result-box" },
    el("div", { class: "result-label" }, label),
    el("div", { class: `result-value${dim ? " dim" : ""}` }, value)
  );
}
