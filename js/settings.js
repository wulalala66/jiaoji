/* ============================================================
   胶记 · 设置
   数据备份 + 关于。联网/AI 那套已经下线，这一页只留必须的。
   ============================================================ */

import { el, downloadBlob, todayText } from "./utils.js";
import { actions, getState } from "./store.js";
import { confirmDialog, copyText, toast } from "./ui.js";

const VERSION = "3.3.1";

export function renderSettings(root) {
  const state = getState();

  root.appendChild(
    el(
      "header",
      { class: "page-head" },
      el("div", null, el("h1", null, "设置"), el("p", { class: "page-sub" }, "数据 · 关于"))
    )
  );

  // ---------------- 数据
  const fileInput = el("input", {
    type: "file",
    accept: ".json,application/json",
    hidden: "true",
    onchange: async (e) => {
      const file = e.target.files?.[0];
      e.target.value = "";
      if (!file) return;
      if (file.size > 5 * 1024 * 1024) {
        toast("文件太大了（超过 5MB）", "danger");
        return;
      }
      const text = await file.text();
      let preview;
      try {
        preview = JSON.parse(text);
      } catch {
        toast("这不是一个备份文件", "danger");
        return;
      }
      const np = Array.isArray(preview.products) ? preview.products.length : 0;
      const nd = Array.isArray(preview.densityLog) ? preview.densityLog.length : 0;
      const ok = await confirmDialog({
        title: "导入备份？",
        message: `会用文件里的数据覆盖当前内容：${np} 条产品条件、${nd} 条比重记录。`,
        confirm: "导入",
        danger: true,
      });
      if (!ok) return;
      try {
        actions.importJson(text);
        toast("导入成功", "ok");
        location.hash = "#/products";
        location.reload();
      } catch (err) {
        toast("导入失败：" + err.message, "danger");
      }
    },
  });

  root.appendChild(
    el(
      "section",
      { class: "card" },
      el("div", { class: "card-title" }, "💾 数据"),
      el("p", { class: "hint" }, "产品条件表、电泳板、比重记录都存在这台设备的浏览器里，没有账号、没有云同步。"),
      el(
        "button",
        {
          class: "btn btn-soft btn-block",
          type: "button",
          onclick: () => {
            const blob = new Blob([actions.exportJson()], { type: "application/json" });
            downloadBlob(blob, `胶记备份-${todayText()}.json`);
            toast("已导出", "ok");
          },
        },
        "⬇ 导出备份（JSON）"
      ),
      el("button", { class: "btn btn-soft btn-block", type: "button", onclick: () => fileInput.click() }, "⬆ 导入备份"),
      fileInput
    )
  );

  // ---------------- 关于
  root.appendChild(
    el(
      "section",
      { class: "card" },
      el("div", { class: "card-title" }, "ℹ️ 关于"),
      kv("版本", VERSION),
      kv("数据", `${state.products.length} 条产品条件 · ${state.densityLog.length} 条比重记录`),
      el("p", { class: "hint" }, "胶记 · 胶粘剂实验室的随身工具：比重计算、产品条件表与烘烤计时。"),
      el(
        "button",
        {
          class: "btn btn-ghost btn-block",
          type: "button",
          onclick: async () => {
            const ok = await copyText(`胶记 ${VERSION}\n${navigator.userAgent}`);
            toast(ok ? "已复制" : "复制失败", ok ? "ok" : "warn");
          },
        },
        "复制版本信息"
      )
    )
  );
}

function kv(label, value) {
  return el("div", { class: "kv" }, el("span", null, label), el("b", null, value));
}
