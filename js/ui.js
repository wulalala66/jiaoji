/* ============================================================
   胶记 · UI primitives: toast, sheet, confirm, icons
   ============================================================ */
import { $, esc } from "./utils.js";

/* ---------------- icons ---------------- */

const PATHS = {
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  checkCircle: '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  chevronRight: '<path d="m9 18 6-6-6-6"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  arrowLeft: '<path d="m12 19-7-7 7-7M19 12H5"/>',
  clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
  flame: '<path d="M12 2c1.7 4.2 5.4 6.4 5.4 10.5a5.4 5.4 0 0 1-10.8 0c0-2.1 1-3.8 2.2-5.3.5 1.9 1.5 3 2.7 3.6C11.2 7.4 11.2 4.4 12 2Z"/>',
  door: '<rect x="5" y="3" width="14" height="18" rx="2"/><circle cx="15.5" cy="12" r="1"/>',
  windowIco: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M12 4v16M4 12h16"/>',
  flask: '<path d="M10 2v6L4.5 18a2 2 0 0 0 1.8 3h11.4a2 2 0 0 0 1.8-3L14 8V2"/><path d="M8.5 2h7"/><path d="M7 15h10"/>',
  factory: '<path d="M2 20h20"/><path d="M4 20V8l6 4V8l6 4V4h4v16"/><path d="M8 12h.01M12 12h.01M16 12h.01"/>',
  clipboard:
    '<rect x="5" y="4" width="14" height="18" rx="2"/><path d="M9 2h6a1 1 0 0 1 1 1v2H8V3a1 1 0 0 1 1-1Z"/><path d="M9 11h6M9 15h6"/>',
  settings:
    '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.55V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1-1.55 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.55-1H3a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.55-1 1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34h0a1.7 1.7 0 0 0 1-1.55V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.55h0a1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87v0a1.7 1.7 0 0 0 1.55 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.55 1Z"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M10 11v6M14 11v6"/>',
  pencil: '<path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>',
  copy: '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/>',
  alert: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4M12 17h.01"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5"/><path d="M9 2h6"/>',
  play: '<path d="m6 4 14 8-14 8V4Z"/>',
  pause: '<rect x="5" y="4" width="5" height="16" rx="1"/><rect x="14" y="4" width="5" height="16" rx="1"/>',
  rotate: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>',
  volume: '<path d="M11 5 6 9H2v6h4l5 4V5Z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/>',
  vibrate: '<path d="M2 8h2M20 8h2M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z"/><path d="M8 9h8v6H8z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  fileText:
    '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/><path d="M16 13H8M16 17H8M10 9H8"/>',
  skip: '<path d="M5 4l10 8-10 8V4Z"/><path d="M19 5v14"/>',
  save: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
  send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  grip: '<circle cx="9" cy="6" r="1"/><circle cx="9" cy="12" r="1"/><circle cx="9" cy="18" r="1"/><circle cx="15" cy="6" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="15" cy="18" r="1"/>',
  beaker: '<path d="M6 3h12"/><path d="M8 3v7l-4 8a2 2 0 0 0 1.8 3h12.4a2 2 0 0 0 1.8-3l-4-8V3"/>',
  moreHorizontal: '<circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/>',
};

export function icon(name, cls = "") {
  const p = PATHS[name] || PATHS.info;
  return `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${p}</svg>`;
}

/* ---------------- toast ---------------- */

export function toast(msg, kind = "ok", ms = 2400) {
  const wrap = $("#toasts");
  if (!wrap) return;
  const el = document.createElement("div");
  el.className = "toast";
  const ic = kind === "ok" ? "checkCircle" : kind === "warn" ? "alert" : "info";
  el.innerHTML = `<span class="t-ic t-${kind}">${icon(ic)}</span><span>${esc(msg)}</span>`;
  wrap.appendChild(el);
  setTimeout(() => {
    el.classList.add("out");
    setTimeout(() => el.remove(), 240);
  }, ms);
}

/* ---------------- bottom sheet ---------------- */

let sheetEl = null;
let sheetCleanup = null;

export function openSheet({ title = "", note = "", body, actions = null, onClose = null, wide = false }) {
  closeSheet(true);
  sheetEl = document.createElement("div");
  sheetEl.className = "sheet open";
  sheetEl.innerHTML = `
    <div class="sheet-card" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="sheet-grabber"><i></i></div>
      ${title ? `<div class="sheet-head"><h2>${esc(title)}</h2>${note ? `<span class="head-note">${esc(note)}</span>` : `<button class="icon-btn plain" data-close>${icon("x")}</button>`}</div>` : ""}
      <div class="sheet-body"></div>
      ${actions ? `<div class="sheet-actions"></div>` : ""}
    </div>`;
  const bodyEl = $(".sheet-body", sheetEl);
  if (typeof body === "string") bodyEl.innerHTML = body;
  else bodyEl.appendChild(body);

  if (actions) {
    const act = $(".sheet-actions", sheetEl);
    for (const a of actions) {
      const b = document.createElement("button");
      b.className = "btn " + (a.cls || "ghost");
      b.innerHTML = (a.icon ? icon(a.icon) : "") + esc(a.label);
      b.onclick = () => a.onClick?.();
      act.appendChild(b);
    }
  }

  const close = (instant = false) => {
    onClose?.();
    const el = sheetEl;
    if (instant || !el) {
      el?.remove();
    } else {
      el.classList.add("closing");
      setTimeout(() => el.remove(), 200);
    }
    document.body.classList.remove("no-scroll");
    sheetCleanup?.();
    sheetCleanup = null;
    sheetEl = null;
  };

  sheetEl.addEventListener("click", (e) => {
    if (e.target === sheetEl) close();
    if (e.target.closest("[data-close]")) close();
  });
  sheetCleanup = () => {
    onClose = null;
  };
  document.body.classList.add("no-scroll");
  document.body.appendChild(sheetEl);
  return { el: sheetEl, bodyEl, close };
}

export function closeSheet(instant = false) {
  if (sheetEl) {
    sheetCleanup?.();
    if (instant) {
      sheetEl.remove();
    } else {
      sheetEl.classList.add("closing");
      const el = sheetEl;
      setTimeout(() => el.remove(), 200);
    }
    sheetEl = null;
    document.body.classList.remove("no-scroll");
  }
}

/* ---------------- confirm modal ---------------- */

export function confirmDialog({ title = "确认操作", message = "", okLabel = "确认", danger = false }) {
  return new Promise((resolve) => {
    const wrap = document.createElement("div");
    wrap.className = "modal-card";
    wrap.innerHTML = `
      <div class="modal-box">
        <h3>${esc(title)}</h3>
        <p>${esc(message)}</p>
        <div class="modal-actions">
          <button class="btn ghost" data-no>取消</button>
          <button class="btn ${danger ? "danger-solid" : "primary"}" data-yes>${esc(okLabel)}</button>
        </div>
      </div>`;
    wrap.addEventListener("click", (e) => {
      if (e.target === wrap) done(false);
      if (e.target.closest("[data-no]")) done(false);
      if (e.target.closest("[data-yes]")) done(true);
    });
    document.body.appendChild(wrap);
    function done(v) {
      wrap.remove();
      resolve(v);
    }
  });
}

/* ---------------- prompt modal ---------------- */

export function promptDialog({ title = "输入", message = "", placeholder = "", value = "", okLabel = "确定" }) {
  return new Promise((resolve) => {
    const wrap = document.createElement("div");
    wrap.className = "modal-card";
    wrap.innerHTML = `
      <div class="modal-box">
        <h3>${esc(title)}</h3>
        ${message ? `<p>${esc(message)}</p>` : ""}
        <input class="text-field" style="margin-top:12px" placeholder="${esc(placeholder)}" value="${esc(value)}" />
        <div class="modal-actions">
          <button class="btn ghost" data-no>取消</button>
          <button class="btn primary" data-yes>${esc(okLabel)}</button>
        </div>
      </div>`;
    const input = $("input", wrap);
    document.body.appendChild(wrap);
    setTimeout(() => {
      input.focus();
      input.select();
    }, 60);
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") done(input.value);
      if (e.key === "Escape") done(null);
    });
    wrap.addEventListener("click", (e) => {
      if (e.target === wrap) done(null);
      if (e.target.closest("[data-no]")) done(null);
      if (e.target.closest("[data-yes]")) done(input.value);
    });
    function done(v) {
      wrap.remove();
      resolve(v == null ? null : String(v).trim());
    }
  });
}

/* ---------------- misc helpers ---------------- */

export function chip(text, cls = "") {
  return `<span class="chip ${cls}">${text}</span>`;
}

export function emptyState({ icon: ic = "info", title, desc, action }) {
  return `<div class="empty">
    <div class="empty-icon">${icon(ic)}</div>
    <h3>${esc(title)}</h3>
    ${desc ? `<p>${desc}</p>` : ""}
    ${action || ""}
  </div>`;
}
