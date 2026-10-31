/* ============================================================
   胶记 · one-line text parser
   "8点半 安徽万安 底涂胶" → {time, customer, typeId}
   ============================================================ */
import { at, escapeRe, DAY_MS } from "./utils.js";
import { getState } from "./store.js";

const NUM_CN = { 零: 0, 一: 1, 两: 2, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 };

/** "8点半" "8:30" "8点45" "0830" "8点半左右" -> minutes or null */
export function parseTime(text, now = new Date()) {
  if (!text) return null;
  const t = text.replace(/\s+/g, "");
  let h = null;
  let m = 0;

  let m0 = t.match(/(\d{1,2})[:：点时](\d{1,2})?分?/);
  if (m0) {
    h = parseInt(m0[1], 10);
    m = m0[2] != null ? parseInt(m0[2], 10) : 0;
  } else if (/半/.test(t)) {
    m0 = t.match(/(\d{1,2})/);
    if (m0) {
      h = parseInt(m0[1], 10);
      m = 30;
    }
  } else if (/[一个两二三四五六七八九十]+刻/.test(t) || /刻/.test(t)) {
    m0 = t.match(/(\d{1,2}|[一个两二三四五六七八九十]+)刻/);
    if (m0) {
      h = parseInt(m0[1], 10) || null;
      if (h == null) {
        // 中文数字（一刻 = x:15）
        const cn = m0[1].match(/[一二两三四五六七八九十]/);
        h = cn ? NUM_CN[cn[0]] : null;
      }
      m = 15;
    }
  } else {
    m0 = t.match(/^(\d{3,4})$/); // "0830"
    if (m0) {
      const v = m0[1];
      h = parseInt(v.slice(0, -2), 10);
      m = parseInt(v.slice(-2), 10);
    } else {
      m0 = t.match(/(\d{1,2})/);
      if (m0 && /点|时/.test(t)) {
        h = parseInt(m0[1], 10);
      } else if (m0 && !/[点时半刻:：]/.test(t)) {
        h = parseInt(m0[1], 10); // bare "8" → 8:00
      }
    }
  }

  // 中文小时 "八点半"
  if (h == null) {
    m0 = t.match(/([零一二两二三四五六七八九十]+)[点时]/);
    if (m0) {
      h = cnToNum(m0[1]);
      const half = /半/.test(t) ? 30 : /刻/.test(t) ? 15 : 0;
      m = half;
    }
  }

  if (h == null || h < 0 || h > 23 || m < 0 || m > 59) return null;
  if (h <= 6 && /下午|傍晚|晚上/.test(t)) h += 12;
  if (/下午|傍晚|晚上/.test(t) && h < 12) h += 12;
  if (/早上|早晨|上午|凌晨/.test(t) && h === 12) h = 0;

  let day = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const cand = at(`${dateKeyLocal(day)}`, `${pad2(h)}:${pad2(m)}`);
  if (cand.getTime() < now.getTime() - 60 * 60000) {
    // more than an hour in the past → assume tomorrow
    day = new Date(day.getTime() + DAY_MS);
    return at(`${dateKeyLocal(day)}`, `${pad2(h)}:${pad2(m)}`).getTime();
  }
  return cand.getTime();
}

function cnToNum(s) {
  if (/^十$/.test(s)) return 10;
  if (/十/.test(s)) {
    const [a, b] = s.split("十");
    return (a ? NUM_CN[a] : 1) * 10 + (b ? NUM_CN[b] : 0);
  }
  return NUM_CN[s] ?? NaN;
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function dateKeyLocal(d) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** find customer by fuzzy inclusion */
export function parseCustomer(text) {
  const list = getState().customers;
  if (!text) return null;
  const t = text.replace(/\s+/g, "");
  for (const c of list) {
    const key = c.replace(/[\s（）()]/g, "");
    if (!key) continue;
    if (t.includes(key)) return c;
    // try progressively shorter prefixes (>=2 chars)
    const core = key.replace(/(股份|有限|公司|科技|集团)$/g, "");
    if (core.length >= 2 && t.includes(core)) return c;
  }
  return null;
}

/** find type by name or common alias */
const ALIAS = {
  底涂: "底涂胶",
  底涂胶: "底涂胶",
  焊缝: "焊缝胶",
  焊缝胶: "焊缝胶",
  点焊: "焊缝胶",
  密封胶: "焊缝胶",
  膏: "其他",
  胶片: "其他",
  其他: "其他",
};

export function parseType(text) {
  if (!text) return null;
  const t = text.replace(/\s+/g, "");
  const types = getState().types;
  // exact / alias match first
  for (const [alias, name] of Object.entries(ALIAS)) {
    if (t.includes(alias)) {
      const type = types.find((x) => x.name === name);
      if (type) return type.id;
    }
  }
  // direct name match against user types
  for (const type of types) {
    if (t.includes(type.name)) return type.id;
    if (type.name.length >= 2) {
      const core = type.name.replace(/胶$/, "");
      if (core.length >= 2 && t.includes(core)) return type.id;
    }
  }
  return null;
}

/** parse an optional "明天/后天/周一…" day modifier; returns dateKey offset days or 0 */
export function parseDayOffset(text, now = new Date()) {
  if (!text) return 0;
  const t = text.replace(/\s+/g, "");
  if (/大后天/.test(t)) return 3;
  if (/后天/.test(t)) return 2;
  if (/明天|明早|明日上午|明早八/.test(t)) return 1;
  if (/今天|今日|今早|早上|上午/.test(t) || true) {
    // weekday names: 周X / 礼拜X / 星期X
    const m = t.match(/(?:周|礼拜|星期)([一二三四五六日天])/);
    if (m) {
      const target = "一二三四五六日天".indexOf(m[1]) % 7;
      const cur = now.getDay();
      let diff = (target - cur + 7) % 7;
      if (diff === 0) diff = 7;
      return diff;
    }
  }
  return 0;
}

/**
 * Full parse: "明早8点半 安徽万安 底涂胶 厚一点"
 */
export function parseQuick(text) {
  const now = new Date();
  const t = (text || "").trim();
  if (!t) return { time: null, customer: null, typeId: null, note: "", dayOffset: 0 };
  const dayOffset = parseDayOffset(t, now);
  const time0 = parseTime(t, now);
  const time = time0 && dayOffset ? time0 + dayOffset * 86400000 : time0;
  const customer = parseCustomer(t);
  const typeId = parseType(t);

  // note = leftover after removing matched pieces
  let leftover = t;
  const strip = (s) => {
    if (!s) return;
    leftover = leftover.replace(new RegExp(escapeRe(s), "g"), " ");
  };
  if (customer) strip(customer);
  if (typeId) {
    const name = getState().types.find((x) => x.id === typeId)?.name;
    strip(name);
    for (const alias of Object.keys(ALIAS)) if (t.includes(alias)) strip(alias);
  }
  leftover = leftover
    .replace(/\d{1,2}[:：点时]\d{0,2}分?/g, " ")
    .replace(/\d{3,4}/g, " ")
    .replace(/[零一二两二三四五六七八九十]+[点时][半刻]?/g, " ")
    .replace(/(今|明|后|大后)天|今早|明早|(早上|上午|下午|晚上|凌晨)|[半刻](?![a-z])|(周|礼拜|星期)[一二三四五六日天]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  return {
    time,
    customer,
    typeId,
    note: leftover,
    dayOffset,
  };
}
