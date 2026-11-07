/* ============================================================
   胶记 · 状态与存储
   存储格式与安卓版完全一致（同一个 JSON），两边备份可以互导。
   ============================================================ */

import { num, uid } from "./utils.js";

const KEY = "jiaoji.v1";
const LEGACY_BACKUP_KEY = "jiaoji.legacy-backup";

// ---------------------------------------------------------------- 默认数据

export function defaultProducts() {
  return [
    ["p01", "中亮/中亮VF3", [[150, 25]]],
    ["p02", "焊缝胶", [[140, 30]]],
    ["p03", "折边胶/结构胶", [[180, 20]]],
    ["p04", "奥博/圣歌瑞", [[150, 25]]],
    ["p05", "润东铭", [[160, 20]]],
    ["p06", "润金泽/六安", [[150, 30], [170, 20]]],
    ["p07", "柳州万安", [[160, 20]]],
    ["p08", "安徽万安", [[170, 20], [210, 20]], [
      { fresh: true, note: "2mm 流挂" },
      { fresh: false, note: "" },
      { fresh: false, note: "" },
      { fresh: false, note: "" },
    ]],
    ["p09", "江阴铝板底涂", [[155, 30]]],
    ["p10", "五菱灰色底涂", [[140, 30]]],
    ["p11", "方达发泡底涂", [[160, 20]]],
    ["p12", "珂亦铝板不发泡", [[160, 25], [170, 25]]],
    ["p13", "太仓发泡底涂", [[160, 20]]],
    ["p14", "安徽众源", [[160, 25]]],
    ["p15", "宁波惠轩", [[170, 20]]],
    ["p16", "太仓格盛恩", [[170, 20]]],
  ].map(([id, name, bakes, plates]) => ({
    id,
    name,
    bakes: bakes.map(([temp, minutes]) => ({ temp, minutes })),
    // 电泳板：每块单独记新旧和用途
    plates: (plates || []).map((pl, i) => ({
      id: `pl_${id}_${i}`,
      fresh: !!pl.fresh,
      note: pl.note || "",
    })),
  }));
}

export function defaultFormulas() {
  return [
    {
      id: "f_demo",
      name: "底涂胶 A 料（示例）",
      totalG: 600,
      note: "",
      purpose: "示例：可以改，也可以直接删",
      cure: "140*30",
      items: [
        { id: "fi_demo1", name: "D1NP", percent: 26.33 },
        { id: "fi_demo2", name: "碳酸钙", percent: 18 },
        { id: "fi_demo3", name: "增塑剂", percent: 12.5 },
        { id: "fi_demo4", name: "炭黑", percent: 3.17 },
        { id: "fi_demo5", name: "填料 A", percent: 40 },
      ],
    },
  ];
}

export function defaultAi() {
  return {
    baseUrl: "",
    apiKey: "",
    model: "",
    thinking: "",
    compress: true,
    liveComment: true,
    systemPrompt: "",
  };
}

export function defaultState() {
  return {
    settings: { ai: defaultAi() },
    products: defaultProducts(),
    formulas: defaultFormulas(),
  };
}

// ---------------------------------------------------------------- 编解码

const str = (v, fallback = "") => (typeof v === "string" ? v : fallback);
const positive = (v, fallback) => {
  const n = num(v);
  return n !== null && n > 0 ? n : fallback;
};

/** 把外部 JSON（本机旧数据 / 安卓备份 / 用户导入的文件）整理成内部结构 */
export function decode(raw) {
  const src = typeof raw === "string" ? JSON.parse(raw) : raw;
  if (!src || typeof src !== "object" || Array.isArray(src)) throw new Error("不是有效的备份文件");

  const aiSrc = src?.settings?.ai ?? {};
  const state = {
    settings: {
      ai: {
        baseUrl: str(aiSrc.baseUrl).trim(),
        apiKey: str(aiSrc.apiKey).trim(),
        model: str(aiSrc.model).trim(),
        thinking: ["", "low", "medium", "high"].includes(aiSrc.thinking) ? aiSrc.thinking : "",
        compress: aiSrc.compress !== false,
        liveComment: aiSrc.liveComment !== false,
        systemPrompt: str(aiSrc.systemPrompt),
      },
    },
    products: [],
    formulas: [],
  };

  if (Array.isArray(src.products)) {
    for (const p of src.products) {
      if (!p || typeof p !== "object") continue;
      const name = str(p.name).trim();
      if (!name) continue;
      const bakes = [];
      if (Array.isArray(p.bakes)) {
        for (const b of p.bakes) {
          const temp = num(b?.temp ?? b?.tempC);
          const minutes = num(b?.minutes);
          if (temp > 0 && minutes > 0) bakes.push({ temp: Math.round(temp), minutes: Math.round(minutes) });
        }
      }
      const plates = [];
      if (Array.isArray(p.plates)) {
        for (const pl of p.plates) {
          if (!pl || typeof pl !== "object") continue;
          plates.push({
            id: str(pl.id) || "pl_" + uid(),
            fresh: pl.fresh === true,
            note: str(pl.note).trim(),
          });
        }
      }
      // 3.0.4 的老格式：总数 / 新板数 / 新板要求 —— 自动折成板子列表
      if (!plates.length) {
        const total = Math.min(99, Math.max(0, Math.round(num(p.plateTotal) ?? 0)));
        const fresh = Math.min(total, Math.max(0, Math.round(num(p.plateNew) ?? 0)));
        const spec = str(p.plateSpec).trim();
        for (let i = 0; i < fresh; i++) plates.push({ id: "pl_" + uid(), fresh: true, note: spec });
        for (let i = 0; i < total - fresh; i++) plates.push({ id: "pl_" + uid(), fresh: false, note: "" });
      }
      state.products.push({ id: str(p.id) || "p_" + uid(), name, bakes, plates });
    }
  }
  if (!state.products.length) state.products = defaultProducts();

  if (Array.isArray(src.formulas)) {
    for (const f of src.formulas) {
      if (!f || typeof f !== "object") continue;
      const items = [];
      if (Array.isArray(f.items)) {
        for (const it of f.items) {
          if (!it || typeof it !== "object") continue;
          items.push({
            id: str(it.id) || "fi_" + uid(),
            name: str(it.name),
            percent: Math.min(100, Math.max(0, num(it.percent) ?? 0)),
          });
        }
      }
      state.formulas.push({
        id: str(f.id) || "f_" + uid(),
        name: str(f.name) || "未命名配方",
        totalG: positive(f.totalG, 600),
        note: str(f.note),
        purpose: str(f.purpose),
        cure: str(f.cure),
        items,
      });
    }
  }

  return state;
}

export function encode(state) {
  return JSON.stringify({
    v: 1,
    settings: {
      ai: {
        baseUrl: state.settings.ai.baseUrl,
        apiKey: state.settings.ai.apiKey,
        model: state.settings.ai.model,
        thinking: state.settings.ai.thinking,
        compress: state.settings.ai.compress,
        liveComment: state.settings.ai.liveComment,
        systemPrompt: state.settings.ai.systemPrompt,
      },
    },
    products: state.products.map((p) => ({
      id: p.id,
      name: p.name,
      bakes: p.bakes.map((b) => ({ temp: b.temp, minutes: b.minutes })),
      ...(p.plates?.length
        ? {
            plates: p.plates.map((pl) => ({
              id: pl.id,
              fresh: !!pl.fresh,
              ...(pl.note ? { note: pl.note } : {}),
            })),
          }
        : {}),
    })),
    formulas: state.formulas.map((f) => ({
      id: f.id,
      name: f.name,
      totalG: f.totalG,
      note: f.note,
      ...(f.purpose ? { purpose: f.purpose } : {}),
      ...(f.cure ? { cure: f.cure } : {}),
      items: f.items.map((it) => ({ id: it.id, name: it.name, percent: it.percent })),
    })),
  });
}

// ---------------------------------------------------------------- 状态容器

let state = defaultState();
const listeners = new Set();

function readStored() {
  let raw = null;
  try {
    raw = localStorage.getItem(KEY);
  } catch {
    return { state: defaultState(), note: "浏览器禁用了本地存储，这次的数据关掉页面就没了" };
  }
  if (!raw) return { state: defaultState(), note: "" };

  try {
    const parsed = JSON.parse(raw);
    // 老版本（带取样/烘烤/记录）的数据：留一份原始备份，别直接丢
    if (parsed && (Array.isArray(parsed.samples) || Array.isArray(parsed.sampling))) {
      try {
        localStorage.setItem(LEGACY_BACKUP_KEY, raw);
      } catch {
        /* 存不下就算了，不影响主流程 */
      }
    }
    return { state: decode(parsed), note: "" };
  } catch (e) {
    return { state: defaultState(), note: "本地数据读不出来，已用默认数据启动（原数据没删）" };
  }
}

export function initStore() {
  const r = readStored();
  state = r.state;
  save();
  return r.note;
}

export function getState() {
  return state;
}

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function save() {
  try {
    localStorage.setItem(KEY, encode(state));
  } catch (e) {
    console.warn("保存失败", e);
  }
}

/** 所有写操作都走这里：改完立刻存盘 + 通知界面 */
export function mutate(fn) {
  const next = decode(encode(state));
  fn(next);
  state = next;
  save();
  for (const l of listeners) {
    try {
      l(state);
    } catch (e) {
      console.error(e);
    }
  }
  return state;
}

export function replaceState(next) {
  state = next;
  save();
  for (const l of listeners) l(state);
}

// ---------------------------------------------------------------- 派生计算

export function formulaOf(id) {
  return state.formulas.find((f) => f.id === id) || null;
}

export function sumPercent(f) {
  return f.items.reduce((s, it) => s + (num(it.percent) ?? 0), 0);
}

export function gramsOf(f, item) {
  return ((num(item.percent) ?? 0) * (f.totalG || 0)) / 100;
}

export function sumGrams(f) {
  return f.items.reduce((s, it) => s + gramsOf(f, it), 0);
}

export function restPercent(f) {
  return 100 - sumPercent(f);
}

export function restGrams(f) {
  return (f.totalG || 0) - sumGrams(f);
}

/** 电泳板的一句话说明，列表和 AI 共用 */
export function plateText(p) {
  const plates = p?.plates || [];
  if (!plates.length) return "不用电泳板";
  const fresh = plates.filter((x) => x.fresh).length;
  const old = plates.length - fresh;
  const parts = [];
  if (fresh) parts.push(`新 ${fresh}`);
  if (old) parts.push(`旧 ${old}`);
  return `电泳板 ${plates.length} 块（${parts.join(" / ")}）`;
}

/** 每块板子一行：「新 · 2mm 流挂」「旧」 */
export function plateLines(p) {
  return (p?.plates || []).map((pl) => {
    const tag = pl.fresh ? "新" : "旧";
    return pl.note ? `${tag} · ${pl.note}` : tag;
  });
}

export function isComplete(f) {
  return Math.abs(restPercent(f)) < 0.05;
}

// ---------------------------------------------------------------- 写操作

export const actions = {
  addFormula(name = "新配方", totalG = 600) {
    const id = "f_" + uid();
    mutate((s) => {
      s.formulas.push({
        id,
        name: name || `配方 ${s.formulas.length + 1}`,
        totalG: totalG > 0 ? totalG : 600,
        note: "",
        purpose: "",
        cure: "",
        items: [],
      });
    });
    return id;
  },

  updateFormula(id, patch) {
    mutate((s) => {
      const f = s.formulas.find((x) => x.id === id);
      if (f) Object.assign(f, patch);
    });
  },

  removeFormula(id) {
    mutate((s) => {
      s.formulas = s.formulas.filter((f) => f.id !== id);
    });
  },

  duplicateFormula(id) {
    const newId = "f_" + uid();
    mutate((s) => {
      const src = s.formulas.find((f) => f.id === id);
      if (!src) return;
      s.formulas.push({
        ...src,
        id: newId,
        name: src.name + " 副本",
        items: src.items.map((it) => ({ ...it, id: "fi_" + uid() })),
      });
    });
    return newId;
  },

  addItem(formulaId, name = "", percent = 0) {
    const id = "fi_" + uid();
    mutate((s) => {
      const f = s.formulas.find((x) => x.id === formulaId);
      if (f) f.items.push({ id, name, percent });
    });
    return id;
  },

  updateItem(formulaId, itemId, patch) {
    mutate((s) => {
      const f = s.formulas.find((x) => x.id === formulaId);
      const it = f?.items.find((x) => x.id === itemId);
      if (!it) return;
      if (typeof patch.name === "string") it.name = patch.name;
      if (patch.percent !== undefined && patch.percent !== null) {
        it.percent = Math.min(100, Math.max(0, num(patch.percent) ?? 0));
      }
    });
  },

  removeItem(formulaId, itemId) {
    mutate((s) => {
      const f = s.formulas.find((x) => x.id === formulaId);
      if (f) f.items = f.items.filter((x) => x.id !== itemId);
    });
  },

  fillRest(formulaId, itemId) {
    mutate((s) => {
      const f = s.formulas.find((x) => x.id === formulaId);
      if (!f) return;
      const others = f.items.filter((x) => x.id !== itemId).reduce((a, b) => a + b.percent, 0);
      const it = f.items.find((x) => x.id === itemId);
      if (it) it.percent = Math.max(0, Math.round((100 - others) * 100) / 100);
    });
  },

  addProduct(name, bakes = [], plates = []) {
    const clean = name.trim();
    if (!clean) return null;
    if (state.products.some((p) => p.name === clean)) return null;
    const id = "p_" + uid();
    mutate((s) => {
      s.products.push({ id, name: clean, bakes, plates: plates.map((pl) => ({ ...pl, id: pl.id || "pl_" + uid() })) });
    });
    return id;
  },

  updateProduct(id, name, bakes, plates = []) {
    const clean = name.trim();
    if (!clean) return;
    mutate((s) => {
      const p = s.products.find((x) => x.id === id);
      if (!p) return;
      p.name = clean;
      p.bakes = bakes;
      p.plates = plates.map((pl) => ({ ...pl, id: pl.id || "pl_" + uid() }));
    });
  },

  removeProduct(id) {
    mutate((s) => {
      s.products = s.products.filter((p) => p.id !== id);
    });
  },

  updateAi(patch) {
    mutate((s) => {
      Object.assign(s.settings.ai, patch);
    });
  },

  importJson(raw) {
    const next = decode(raw);
    replaceState(next);
    return next;
  },

  exportJson() {
    return encode(state);
  },

  clearFormulas() {
    mutate((s) => {
      s.formulas = [];
    });
  },

  resetAll() {
    replaceState(defaultState());
  },
};

export function legacyBackup() {
  try {
    return localStorage.getItem(LEGACY_BACKUP_KEY);
  } catch {
    return null;
  }
}
