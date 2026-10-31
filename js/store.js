/* ============================================================
   胶记 · store (localStorage state + persistence)
   ============================================================ */
import { uid, dateKey, at } from "./utils.js";

const KEY = "jiaoji.v1";

export const DEFAULT_TYPES = [
  {
    id: "dtj",
    name: "底涂胶",
    bakes: [
      { temp: 140, minutes: 20 },
      { temp: 140, minutes: 30 },
    ],
    tests: [
      { id: "t_dtj_yl", name: "压流", unit: "mm", min: "", max: "" },
      { id: "t_dtj_xz", name: "旋转粘度", unit: "mPa·s", min: "", max: "" },
      { id: "t_dtj_bh", name: "宾汉粘度", unit: "Pa·s", min: "", max: "" },
      { id: "t_dtj_jq", name: "剪切强度", unit: "MPa", min: "", max: "" },
      { id: "t_dtj_yd", name: "硬度", unit: "Shore A", min: "", max: "" },
    ],
  },
  {
    id: "hfj",
    name: "焊缝胶",
    bakes: [
      { temp: 140, minutes: 30 },
      { temp: 160, minutes: 20 },
    ],
    tests: [
      { id: "t_hfj_yl", name: "压流", unit: "mm", min: "", max: "" },
      { id: "t_hfj_xz", name: "旋转粘度", unit: "mPa·s", min: "", max: "" },
      { id: "t_hfj_jq", name: "剪切强度", unit: "MPa", min: "", max: "" },
      { id: "t_hfj_yd", name: "硬度", unit: "Shore A", min: "", max: "" },
      { id: "t_hfj_gl", name: "固含量", unit: "%", min: "", max: "" },
    ],
  },
  {
    id: "other",
    name: "其他",
    bakes: [],
    tests: [],
  },
];

function defaults() {
  return {
    version: 1,
    settings: {
      preAlertMinutes: 5,
      sound: true,
      vibrate: true,
      keepAwake: true,
      notifyAsked: false,
      customerOrder: [],
    },
    types: structuredClone(DEFAULT_TYPES),
    customers: ["安徽万安"],
    sampling: [], // {id, customer, typeId, dueAt(ms), note, preAlertMinutes|null, status, sampleId}
    samples: [], // {id, customer, typeId, batchNo, sampledAt, note, status, tests:[{id,name,unit,min,max,value,note,skipped}]}
    bakes: [], // {id, oven, sampleId, label, tempC, minutes, startAt, endAt, status, doneAt, sound}
    seq: {}, // batch counters per day-key
  };
}

let state = load();
const listeners = new Set();

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return defaults();
    const data = JSON.parse(raw);
    const d = defaults();
    // shallow merge with defaults for forward compatibility
    return {
      ...d,
      ...data,
      settings: { ...d.settings, ...(data.settings || {}) },
      types: Array.isArray(data.types) && data.types.length ? data.types : d.types,
      customers: Array.isArray(data.customers) ? data.customers : d.customers,
      sampling: Array.isArray(data.sampling) ? data.sampling : [],
      samples: Array.isArray(data.samples) ? data.samples : [],
      bakes: Array.isArray(data.bakes) ? data.bakes : [],
      seq: data.seq && typeof data.seq === "object" ? data.seq : {},
    };
  } catch (err) {
    console.warn("store load failed, using defaults", err);
    return defaults();
  }
}

function persist() {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch (err) {
    console.error("store persist failed", err);
  }
}

export function getState() {
  return state;
}

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

let notifyQueued = false;
export function notify() {
  persist();
  if (notifyQueued) return;
  notifyQueued = true;
  queueMicrotask(() => {
    notifyQueued = false;
    listeners.forEach((fn) => {
      try {
        fn(state);
      } catch (err) {
        console.error(err);
      }
    });
  });
}

export function mutate(fn) {
  fn(state);
  notify();
}

/* ---------- selectors ---------- */

export function getType(id) {
  return state.types.find((t) => t.id === id) || null;
}

export function typeName(id) {
  return getType(id)?.name || "未知类型";
}

export function getSample(id) {
  return state.samples.find((s) => s.id === id) || null;
}

export function getBake(id) {
  return state.bakes.find((b) => b.id === id) || null;
}

export function activeSampleOfBake(bake) {
  return bake?.sampleId ? getSample(bake.sampleId) : null;
}

export function customers() {
  const fromData = [...state.customers];
  for (const t of state.sampling) if (t.customer && !fromData.includes(t.customer)) fromData.push(t.customer);
  for (const s of state.samples) if (s.customer && !fromData.includes(s.customer)) fromData.push(s.customer);
  const order = state.settings.customerOrder || [];
  fromData.sort((a, b) => {
    const ia = order.indexOf(a);
    const ib = order.indexOf(b);
    if (ia === -1 && ib === -1) return a.localeCompare(b, "zh");
    if (ia === -1) return 1;
    if (ib === -1) return -1;
    return ia - ib;
  });
  return fromData;
}

export function activeBakes() {
  return state.bakes.filter((b) => b.status === "running" || b.status === "due");
}

export function runningBakes(oven) {
  return state.bakes.filter(
    (b) => (oven == null || b.oven === oven) && (b.status === "running" || b.status === "due")
  );
}

export function todaySampling() {
  const today = dateKey();
  return state.sampling
    .filter((t) => dateKey(new Date(t.dueAt)) === today)
    .sort((a, b) => a.dueAt - b.dueAt);
}

export function pendingToday() {
  return todaySampling().filter((t) => t.status === "pending");
}

export function activeSamples() {
  return state.samples.filter((s) => s.status === "active");
}

export function todaySamples() {
  const today = dateKey();
  return state.samples
    .filter((s) => dateKey(new Date(s.sampledAt)) === today)
    .sort((a, b) => b.sampledAt - a.sampledAt);
}

/** next pre-alert/due time across bakes + sampling, for tab hints */
export function nextAlarmTs() {
  const now = Date.now();
  let next = Infinity;
  for (const b of state.bakes) {
    if (b.status !== "running" && b.status !== "due") continue;
    const pre = now + ((b.preAlert ?? state.settings.preAlertMinutes ?? 5) * 60 - 60) * 1000;
    if (b.status === "running" && b.endAt > now) next = Math.min(next, b.endAt);
    if (b.status === "running" && pre > now && pre < b.endAt) next = Math.min(next, pre);
  }
  for (const t of state.sampling) {
    if (t.status !== "pending") continue;
    const pre = t.preAlertMinutes == null ? state.settings.preAlertMinutes : t.preAlertMinutes;
    const preTs = t.dueAt - pre * 60000;
    if (preTs > now) next = Math.min(next, preTs);
    if (t.dueAt > now) next = Math.min(next, t.dueAt);
  }
  return next;
}

/* ---------- batch number ---------- */

export function nextBatchNo(now = new Date()) {
  const key = `${dateKey(now)}T${now.getHours()}`; // per-day per-shift-hour counter is overkill; per day
  const dayKey = dateKey(now);
  const seq = (state.seq[dayKey] || 0) + 1;
  mutate((s) => {
    s.seq[dayKey] = seq;
    // keep only last 60 days
    const keys = Object.keys(s.seq).sort();
    while (keys.length > 60) delete s.seq[keys.shift()];
  });
  return `${String(now.getMonth() + 1).padStart(2, "0")}${String(now.getDate()).padStart(2, "0")}-${String(seq).padStart(2, "0")}`;
}

/* ---------- mutations ---------- */

export function addSampling({ customer, typeId, dueAt, note = "", preAlertMinutes = null }) {
  const task = {
    id: uid(),
    customer,
    typeId,
    dueAt,
    note,
    preAlertMinutes,
    status: "pending",
    sampleId: null,
  };
  mutate((s) => s.sampling.push(task));
  return task;
}

export function updateSampling(id, patch) {
  mutate((s) => {
    const t = s.sampling.find((x) => x.id === id);
    if (t) Object.assign(t, patch);
  });
}

export function removeSampling(id) {
  mutate((s) => {
    s.sampling = s.sampling.filter((x) => x.id !== id);
  });
}

export function createSampleFromTask(taskId, { batchNo, note = "" } = {}) {
  const task = state.sampling.find((t) => t.id === taskId);
  if (!task) return null;
  const type = getType(task.typeId);
  const sample = {
    id: uid(),
    customer: task.customer,
    typeId: task.typeId,
    batchNo: batchNo || nextBatchNo(),
    sampledAt: Date.now(),
    note: note || task.note || "",
    status: "active",
    taskId: task.id,
    tests: (type?.tests || []).map((t) => ({ id: t.id || "t_" + uid(), ...t, value: "", note: "", skipped: false })),
  };
  mutate((s) => {
    s.samples.push(sample);
    const t = s.sampling.find((x) => x.id === taskId);
    if (t) {
      t.status = "done";
      t.sampleId = sample.id;
    }
  });
  return sample;
}

export function createStandaloneSample({ customer, typeId, batchNo, note = "" }) {
  const type = getType(typeId);
  const sample = {
    id: uid(),
    customer,
    typeId,
    batchNo: batchNo || nextBatchNo(),
    sampledAt: Date.now(),
    note,
    status: "active",
    taskId: null,
    tests: (type?.tests || []).map((t) => ({ id: t.id || "t_" + uid(), ...t, value: "", note: "", skipped: false })),
  };
  mutate((s) => s.samples.push(sample));
  return sample;
}

export function updateSample(id, patch) {
  mutate((s) => {
    const sample = s.samples.find((x) => x.id === id);
    if (sample) Object.assign(sample, patch);
  });
}

export function setTestValue(sampleId, testId, patch) {
  mutate((s) => {
    const sample = s.samples.find((x) => x.id === sampleId);
    const t = sample?.tests.find((x) => x.id === testId);
    if (t) Object.assign(t, patch);
  });
}

export function addTestToSample(sampleId, { name, unit = "", min = "", max = "" }) {
  const test = { id: "t_" + uid(), name, unit, min, max, value: "", note: "", skipped: false };
  mutate((s) => {
    const sample = s.samples.find((x) => x.id === sampleId);
    if (sample) sample.tests.push(test);
  });
  return test;
}

export function removeTestFromSample(sampleId, testId) {
  mutate((s) => {
    const sample = s.samples.find((x) => x.id === sampleId);
    if (sample) sample.tests = sample.tests.filter((x) => x.id !== testId);
  });
}

export function updateTypeTest(typeId, testId, patch) {
  mutate((s) => {
    const type = s.types.find((x) => x.id === typeId);
    const t = type?.tests.find((x) => x.id === testId);
    if (t) Object.assign(t, patch);
  });
}

export function addTypeTest(typeId, { name, unit = "", min = "", max = "" }) {
  const test = { id: "t_" + uid(), name, unit, min, max };
  mutate((s) => {
    const type = s.types.find((x) => x.id === typeId);
    if (type) type.tests.push(test);
  });
  return test;
}

export function removeTypeTest(typeId, testId) {
  mutate((s) => {
    const type = s.types.find((x) => x.id === typeId);
    if (type) type.tests = type.tests.filter((x) => x.id !== testId);
  });
}

export function completeSample(id) {
  mutate((s) => {
    const sample = s.samples.find((x) => x.id === id);
    if (sample) sample.status = "done";
  });
}

export function reopenSample(id) {
  mutate((s) => {
    const sample = s.samples.find((x) => x.id === id);
    if (sample) sample.status = "active";
  });
}

export function deleteSample(id) {
  mutate((s) => {
    s.samples = s.samples.filter((x) => x.id !== id);
    s.bakes = s.bakes.filter((b) => b.sampleId !== id);
    s.sampling.forEach((t) => {
      if (t.sampleId === id) {
        t.sampleId = null;
        t.status = "pending";
      }
    });
  });
}

/* ---------- bakes ---------- */

export function addBake({ oven, sampleId = null, label, tempC, minutes, startAt, sound = true }) {
  const bake = {
    id: uid(),
    oven, // 'door' | 'window'
    sampleId,
    label,
    tempC,
    minutes,
    startAt,
    endAt: startAt + minutes * 60000,
    status: "running",
    doneAt: null,
    sound,
    preAlert: null,
  };
  mutate((s) => s.bakes.push(bake));
  return bake;
}

export function updateBake(id, patch) {
  mutate((s) => {
    const b = s.bakes.find((x) => x.id === id);
    if (!b) return;
    Object.assign(b, patch);
    if (patch.startAt != null || patch.minutes != null) {
      b.endAt = b.startAt + b.minutes * 60000;
      if (b.status === "due" && b.endAt > Date.now()) b.status = "running";
    }
  });
}

export function markBakeDone(id) {
  mutate((s) => {
    const b = s.bakes.find((x) => x.id === id);
    if (b) {
      b.status = "done";
      b.doneAt = Date.now();
    }
  });
}

/** add minutes to a running bake ("加5分") */
export function extendBake(id, minutes = 5) {
  mutate((s) => {
    const b = s.bakes.find((x) => x.id === id);
    if (!b) return;
    b.minutes = (b.minutes || 0) + minutes;
    b.endAt = b.endAt + minutes * 60000;
    if (b.status === "due" && b.endAt > Date.now()) b.status = "running";
  });
}

export function removeBake(id) {
  mutate((s) => {
    s.bakes = s.bakes.filter((x) => x.id !== id);
  });
}

/* ---------- customers & types ---------- */

export function addCustomer(name) {
  const n = name.trim();
  if (!n) return;
  mutate((s) => {
    if (!s.customers.includes(n)) s.customers.push(n);
  });
}

export function renameCustomer(oldName, newName) {
  const n = newName.trim();
  if (!n || n === oldName) return;
  mutate((s) => {
    const swap = (arr) => arr.forEach((x) => {
      if (x.customer === oldName) x.customer = n;
    });
    swap(s.sampling);
    swap(s.samples);
    s.customers = s.customers.map((c) => (c === oldName ? n : c));
  });
}

export function deleteCustomer(name) {
  mutate((s) => {
    s.customers = s.customers.filter((c) => c !== name);
  });
}

export function addType({ name, bakes = [], tests = [] }) {
  const type = { id: "tp_" + uid(), name: name.trim(), bakes, tests };
  mutate((s) => s.types.push(type));
  return type;
}

export function updateType(id, patch) {
  mutate((s) => {
    const t = s.types.find((x) => x.id === id);
    if (t) Object.assign(t, patch);
  });
}

export function deleteType(id) {
  if (id === "other") return;
  mutate((s) => {
    s.types = s.types.filter((x) => x.id !== id);
  });
}

export function addTypeBake(typeId, { temp, minutes }) {
  mutate((s) => {
    const t = s.types.find((x) => x.id === typeId);
    if (t) t.bakes.push({ temp, minutes });
  });
}

export function removeTypeBake(typeId, index) {
  mutate((s) => {
    const t = s.types.find((x) => x.id === typeId);
    if (t) t.bakes.splice(index, 1);
  });
}

/* ---------- settings ---------- */

export function updateSettings(patch) {
  mutate((s) => Object.assign(s.settings, patch));
}

/* ---------- import / export / clear ---------- */

export function exportJSON() {
  return JSON.stringify({ app: "jiaoji", exportedAt: new Date().toISOString(), data: state }, null, 2);
}

export function importJSON(text) {
  const parsed = JSON.parse(text);
  const data = parsed?.data && typeof parsed.data === "object" ? parsed.data : parsed;
  if (!data || typeof data !== "object") throw new Error("格式不正确");
  const d = defaults();
  state = {
    ...d,
    ...data,
    settings: { ...d.settings, ...(data.settings || {}) },
    types: Array.isArray(data.types) && data.types.length ? data.types : d.types,
    customers: Array.isArray(data.customers) ? data.customers : d.customers,
    sampling: Array.isArray(data.sampling) ? data.sampling : [],
    samples: Array.isArray(data.samples) ? data.samples : [],
    bakes: Array.isArray(data.bakes) ? data.bakes : [],
    seq: data.seq && typeof data.seq === "object" ? data.seq : {},
  };
  notify();
}

export function clearAll() {
  state = defaults();
  notify();
}

/* time helper used by parsers */
export { at };

/** recently finished bakes (for "刚出炉" list), newest first */
export function recentDoneBakes(limit = 6) {
  return state.bakes
    .filter((b) => b.status === "done" && b.doneAt && Date.now() - b.doneAt < 36 * 3600000)
    .sort((a, b) => b.doneAt - a.doneAt)
    .slice(0, limit);
}
