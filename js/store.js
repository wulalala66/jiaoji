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
    plates: (plates || []).map((pl, i) => ({ id: `pl_${id}_${i}`, fresh: !!pl.fresh, note: pl.note || "" })),
  }));
}

export function defaultState() {
  return { products: defaultProducts(), densityLog: [], timer: null };
}

// ---------------------------------------------------------------- 编解码

const str = (v, fallback = "") => (typeof v === "string" ? v : fallback);

export function decode(raw) {
  const src = typeof raw === "string" ? JSON.parse(raw) : raw;
  if (!src || typeof src !== "object" || Array.isArray(src)) throw new Error("不是有效的备份文件");

  const state = { products: [], densityLog: [], timer: null };

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
          plates.push({ id: str(pl.id) || "pl_" + uid(), fresh: pl.fresh === true, note: str(pl.note).trim() });
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

  if (Array.isArray(src.densityLog)) {
    for (const r of src.densityLog) {
      if (!r || typeof r !== "object") continue;
      state.densityLog.push({
        id: str(r.id) || "d_" + uid(),
        at: num(r.at) ?? 0,
        m1: num(r.m1) ?? 0, m2: num(r.m2) ?? 0, m3: num(r.m3) ?? 0,
        m4: num(r.m4) ?? 0, m5: num(r.m5) ?? 0, m6: num(r.m6) ?? 0,
        specific: num(r.specific),
        solid: num(r.solid),
        foam: num(r.foam),
        note: str(r.note),
      });
    }
    state.densityLog.sort((a, b) => b.at - a.at);
  }

  if (src.timer && typeof src.timer === "object") {
    const minutes = Math.round(num(src.timer.minutes) ?? 0);
    if (minutes > 0) {
      state.timer = {
        productName: str(src.timer.productName),
        tempC: Math.round(num(src.timer.tempC) ?? 0),
        minutes,
        endAt: num(src.timer.endAt) ?? 0,
        pausedLeft: num(src.timer.pausedLeft) ?? -1,
      };
    }
  }

  return state;
}

export function encode(state) {
  return JSON.stringify({
    v: 1,
    products: state.products.map((p) => ({
      id: p.id,
      name: p.name,
      bakes: p.bakes.map((b) => ({ temp: b.temp, minutes: b.minutes })),
      ...(p.plates?.length
        ? { plates: p.plates.map((pl) => ({ id: pl.id, fresh: !!pl.fresh, ...(pl.note ? { note: pl.note } : {}) })) }
        : {}),
    })),
    densityLog: state.densityLog.map((r) => ({
      id: r.id,
      at: r.at,
      m1: r.m1, m2: r.m2, m3: r.m3, m4: r.m4, m5: r.m5, m6: r.m6,
      ...(r.specific !== null && r.specific !== undefined ? { specific: r.specific } : {}),
      ...(r.solid !== null && r.solid !== undefined ? { solid: r.solid } : {}),
      ...(r.foam !== null && r.foam !== undefined ? { foam: r.foam } : {}),
      ...(r.note ? { note: r.note } : {}),
    })),
    ...(state.timer ? { timer: state.timer } : {}),
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
    if (parsed && (Array.isArray(parsed.formulas) || Array.isArray(parsed.samples))) {
      try {
        localStorage.setItem(LEGACY_BACKUP_KEY, raw);
      } catch {
        /* 存不下就算了 */
      }
    }
    return { state: decode(parsed), note: "" };
  } catch {
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

// ---------------------------------------------------------------- 派生

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

export function plateLines(p) {
  return (p?.plates || []).map((pl) => {
    const tag = pl.fresh ? "新" : "旧";
    return pl.note ? `${tag} · ${pl.note}` : tag;
  });
}

/** 计时器剩余毫秒 */
export function timerLeft(t, now = Date.now()) {
  if (!t) return 0;
  return t.pausedLeft >= 0 ? Math.max(0, t.pausedLeft) : Math.max(0, t.endAt - now);
}

// ---------------------------------------------------------------- 写操作

export const actions = {
  // ---- 产品
  addProduct(name, bakes = [], plates = []) {
    const clean = name.trim();
    if (!clean) return null;
    if (state.products.some((p) => p.name === clean)) return null;
    const id = "p_" + uid();
    mutate((s) => {
      s.products.push({
        id,
        name: clean,
        bakes,
        plates: plates.map((pl) => ({ ...pl, id: pl.id || "pl_" + uid() })),
      });
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

  // ---- 计时
  startTimer(productName, tempC, minutes) {
    if (!(minutes > 0)) return;
    mutate((s) => {
      s.timer = {
        productName,
        tempC,
        minutes,
        endAt: Date.now() + minutes * 60000,
        pausedLeft: -1,
      };
    });
  },

  pauseTimer() {
    mutate((s) => {
      if (!s.timer || s.timer.pausedLeft >= 0) return;
      s.timer.pausedLeft = timerLeft(s.timer);
    });
  },

  resumeTimer() {
    mutate((s) => {
      if (!s.timer || s.timer.pausedLeft < 0) return;
      s.timer.endAt = Date.now() + Math.max(0, s.timer.pausedLeft);
      s.timer.pausedLeft = -1;
    });
  },

  stopTimer() {
    mutate((s) => {
      s.timer = null;
    });
  },

  // ---- 比重历史
  addDensityRecord(input, result) {
    const id = "d_" + uid();
    mutate((s) => {
      s.densityLog.unshift({
        id,
        at: Date.now(),
        ...input,
        specific: result.specific,
        solid: result.solid,
        foam: result.foam,
        note: "",
      });
      while (s.densityLog.length > 200) s.densityLog.pop();
    });
    return id;
  },

  removeDensityRecord(id) {
    mutate((s) => {
      s.densityLog = s.densityLog.filter((r) => r.id !== id);
    });
  },

  clearDensityLog() {
    mutate((s) => {
      s.densityLog = [];
    });
  },

  // ---- 备份
  importJson(raw) {
    replaceState(decode(raw));
  },

  exportJson() {
    return encode(state);
  },
};
