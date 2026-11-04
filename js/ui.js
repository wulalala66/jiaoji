/* ============================================================
   胶记 · 交互组件：提示条、底部弹层、确认框
   ============================================================ */

import { el, $, clear } from "./utils.js";

let toastBox = null;

export function toast(message, kind = "info", ms = 2400) {
  if (!toastBox) toastBox = $("#toasts");
  if (!toastBox) return;
  const node = el("div", { class: `toast toast-${kind}` }, message);
  toastBox.appendChild(node);
  requestAnimationFrame(() => node.classList.add("in"));
  setTimeout(() => {
    node.classList.remove("in");
    setTimeout(() => node.remove(), 220);
  }, ms);
}

let sheetCloser = null;

/** 底部弹层：返回一个 close() */
export function openSheet(title, build) {
  closeSheet();
  const body = el("div", { class: "sheet-body" });
  const panel = el(
    "div",
    { class: "sheet-panel", role: "dialog", "aria-label": title },
    el("div", { class: "sheet-grab" }),
    title ? el("div", { class: "sheet-title" }, title) : null,
    body
  );
  const backdrop = el("div", { class: "sheet-backdrop" }, panel);
  backdrop.addEventListener("click", (e) => {
    if (e.target === backdrop) closeSheet();
  });

  const onKey = (e) => {
    if (e.key === "Escape") closeSheet();
  };
  document.addEventListener("keydown", onKey);
  document.body.appendChild(backdrop);
  document.body.classList.add("no-scroll");
  requestAnimationFrame(() => backdrop.classList.add("in"));

  sheetCloser = () => {
    document.removeEventListener("keydown", onKey);
    backdrop.classList.remove("in");
    document.body.classList.remove("no-scroll");
    setTimeout(() => backdrop.remove(), 200);
    sheetCloser = null;
  };

  build(body, closeSheet);
  return closeSheet;
}

export function closeSheet() {
  if (sheetCloser) sheetCloser();
}

/** 确认框：返回 Promise<boolean>，不用浏览器原生 confirm（样式不统一、移动端还挡输入法） */
export function confirmDialog({ title, message, confirm = "确定", cancel = "取消", danger = false }) {
  return new Promise((resolve) => {
    const close = (value) => {
      document.removeEventListener("keydown", onKey);
      wrap.classList.remove("in");
      setTimeout(() => wrap.remove(), 160);
      resolve(value);
    };
    const onKey = (e) => {
      if (e.key === "Escape") close(false);
      if (e.key === "Enter") close(true);
    };
    const wrap = el(
      "div",
      { class: "dialog-backdrop" },
      el(
        "div",
        { class: "dialog", role: "alertdialog", "aria-label": title },
        el("div", { class: "dialog-title" }, title),
        message ? el("div", { class: "dialog-msg" }, message) : null,
        el(
          "div",
          { class: "dialog-actions" },
          el("button", { class: "btn btn-ghost", type: "button", onclick: () => close(false) }, cancel),
          el(
            "button",
            { class: `btn ${danger ? "btn-danger" : "btn-primary"}`, type: "button", onclick: () => close(true) },
            confirm
          )
        )
      )
    );
    wrap.addEventListener("click", (e) => {
      if (e.target === wrap) close(false);
    });
    document.addEventListener("keydown", onKey);
    document.body.appendChild(wrap);
    requestAnimationFrame(() => wrap.classList.add("in"));
  });
}

/** 空状态占位 */
export function emptyHint(emoji, title, sub) {
  return el(
    "div",
    { class: "empty" },
    el("div", { class: "empty-emoji" }, emoji),
    el("div", { class: "empty-title" }, title),
    sub ? el("div", { class: "empty-sub" }, sub) : null
  );
}

export function pill(text, kind = "") {
  return el("span", { class: `pill ${kind}` }, text);
}

/** 复制到剪贴板：老浏览器（没有 clipboard API / 非安全上下文）走兜底 */
export async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* 落到下面的兜底 */
  }
  try {
    const ta = el("textarea", { style: { position: "fixed", top: "-1000px", opacity: "0" } });
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand("copy");
    ta.remove();
    return ok;
  } catch {
    return false;
  }
}

export { clear };
