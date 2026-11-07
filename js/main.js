/* ============================================================
   胶记 · 应用外壳：路由 + 底部导航 + 启动
   ============================================================ */

import { el, $, clear } from "./utils.js";
import { initStore } from "./store.js";
import { renderDensity } from "./density.js";
import { renderProducts } from "./products.js";
import { renderSettings } from "./settings.js";
import { closeSheet, toast } from "./ui.js";

const view = $("#view");
const tabbar = $("#tabbar");

let cleanup = null;
let currentRoute = "";

const TABS = [
  { hash: "#/density", icon: "⚖️", label: "比重", match: ["density"] },
  { hash: "#/products", icon: "📋", label: "条件表", match: ["products"] },
  { hash: "#/settings", icon: "⚙️", label: "设置", match: ["settings"] },
];

function navigate(hash) {
  if (location.hash === hash) {
    render();
  } else {
    location.hash = hash;
  }
}

function parse() {
  const raw = location.hash.replace(/^#\/?/, "");
  const parts = raw.split("/").filter(Boolean);
  // 打开就是比重计算器：每天用得最多的一屏
  if (!parts.length) return { name: "density", args: [] };
  return { name: parts[0], args: parts.slice(1) };
}

function render() {
  const route = parse();
  currentRoute = location.hash || "#/density";

  if (typeof cleanup === "function") {
    cleanup();
    cleanup = null;
  }
  closeSheet();

  const root = el("div", { class: "page" });
  clear(view);
  view.appendChild(root);

  try {
    switch (route.name) {
      case "products":
        cleanup = renderProducts(root);
        break;
      case "settings":
        renderSettings(root, { navigate });
        break;
      case "density":
      default:
        renderDensity(root);
        break;
    }
  } catch (e) {
    console.error(e);
    root.appendChild(el("div", { class: "fatal" }, "这一页出错了：" + (e.message || e)));
    root.appendChild(
      el("button", { class: "btn btn-ghost", type: "button", onclick: () => navigate("#/density") }, "回比重页")
    );
  }

  drawTabs(route);
  view.scrollTop = 0;
  window.scrollTo(0, 0);
}

function drawTabs(route) {
  const key = route.name;
  clear(tabbar);
  for (const t of TABS) {
    const on = t.match.includes(key);
    tabbar.appendChild(
      el(
        "button",
        {
          class: `tab${on ? " on" : ""}`,
          type: "button",
          "data-tab": t.hash,
          "aria-current": on ? "page" : null,
          onclick: () => navigate(t.hash),
        },
        el("span", { class: "tab-icon", "aria-hidden": "true" }, t.icon),
        el("span", { class: "tab-label" }, t.label)
      )
    );
  }
}

// ---------------------------------------------------------------- 启动

function boot() {
  const note = initStore();
  if (note) toast(note, "warn", 4200);

  window.addEventListener("hashchange", render);
  if (!location.hash) location.replace("#/density");
  render();

  // 注册 service worker（只在 https / localhost 下有效）
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("./sw.js").catch(() => {});
    });
  }
}

boot();
