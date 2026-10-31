/* ============================================================
   胶记 · alarm engine
   ticking, pre-alerts, ringing, notifications, wake lock
   ============================================================ */
import { getState, updateBake, updateSampling, subscribe } from "./store.js";
import { mmss } from "./utils.js";

let started = false;
let tickTimer = null;
let ringing = new Map(); // id -> {kind, label}
let alarmHandler = null; // (alarms[]) => void
let unlockHandler = null;

/* ---------------- audio ---------------- */

let audioCtx = null;

export function unlockAudio() {
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === "suspended") audioCtx.resume();
    // play a silent buffer to fully unlock on iOS
    const buf = audioCtx.createBuffer(1, 1, 22050);
    const src = audioCtx.createBufferSource();
    src.buffer = buf;
    src.connect(audioCtx.destination);
    if (src.start) src.start(0);
    if (navigator.mediaSession) {
      navigator.mediaSession.metadata = null;
    }
  } catch (err) {
    console.warn("audio unlock failed", err);
  }
}

export function onUnlock(fn) {
  unlockHandler = fn;
}

function note(freq, t0, dur, { type = "sine", gain = 0.09 } = {}) {
  if (!audioCtx) return;
  const osc = audioCtx.createOscillator();
  const g = audioCtx.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  g.gain.setValueAtTime(0, t0);
  g.gain.linearRampToValueAtTime(gain, t0 + 0.015);
  g.gain.setValueAtTime(gain, t0 + dur - 0.03);
  g.gain.linearRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(g);
  g.connect(audioCtx.destination);
  osc.start(t0);
  osc.stop(t0 + dur + 0.05);
}

/** gentle chime for pre-alert */
function playChime() {
  if (!audioCtx) return;
  const t = audioCtx.currentTime + 0.02;
  note(880, t, 0.16, { type: "sine", gain: 0.06 });
  note(1174.7, t + 0.14, 0.2, { type: "sine", gain: 0.055 });
}

/** urgent pattern for bake due */
function playAlarm() {
  if (!audioCtx) return;
  const t = audioCtx.currentTime + 0.02;
  // ding-ding-ding … pause … (2s pattern)
  const seq = [880, 1108.7, 880];
  seq.forEach((f, i) => note(f, t + i * 0.22, 0.18, { type: "triangle", gain: 0.1 }));
  seq.forEach((f, i) => note(f, t + 1.1 + i * 0.22, 0.18, { type: "triangle", gain: 0.1 }));
}

export function previewSound(kind = "alarm") {
  unlockAudio();
  if (kind === "chime") playChime();
  else playAlarm();
}

function vibrate(pattern) {
  const s = getState().settings;
  if (!s.vibrate) return;
  try {
    navigator.vibrate?.(pattern);
  } catch {}
}

/* ---------------- notifications ---------------- */

export async function requestNotify() {
  if (!("Notification" in window)) return "unsupported";
  const perm = await Notification.requestPermission();
  getState; // no-op
  import("./store.js").then(({ updateSettings }) => updateSettings({ notifyAsked: true }));
  return perm;
}

export function notifyState() {
  if (!("Notification" in window)) return "unsupported";
  return Notification.permission;
}

function showNotification(title, body, tag) {
  try {
    if ("Notification" in window && Notification.permission === "granted") {
      if (navigator.serviceWorker?.controller) {
        navigator.serviceWorker.controller.postMessage({
          type: "notify",
          title,
          body,
          tag,
        });
      } else {
        new Notification(title, { body, tag });
      }
    }
  } catch (err) {
    console.warn("notify failed", err);
  }
}

/* ---------------- wake lock ---------------- */

let wakeLock = null;

async function ensureWakeLock() {
  const s = getState().settings;
  if (!s.keepAwake || !("wakeLock" in navigator)) return;
  try {
    if (!wakeLock || wakeLock.released) {
      wakeLock = await navigator.wakeLock.request("screen");
      wakeLock.addEventListener("release", () => {
        wakeLock = null;
      });
    }
  } catch (err) {
    // denied or hidden document; retry on visibilitychange
  }
}

function maybeReleaseWakeLock() {
  const s = getState().settings;
  const anyActive = getState().bakes.some((b) => b.status === "running" || b.status === "due");
  if ((!anyActive || !s.keepAwake) && wakeLock && !wakeLock.released) {
    wakeLock.release().catch(() => {});
    wakeLock = null;
  }
}

/* ---------------- engine ---------------- */

export function onAlarm(fn) {
  alarmHandler = fn;
  emit();
}

function emit() {
  alarmHandler?.([...ringing.values()]);
}

function startRinging(id, kind, label) {
  if (ringing.has(id)) return;
  ringing.set(id, { id, kind, label });
  emit();
  ringOnce(kind);
  vibrate(kind === "due" ? [350, 150, 350, 150, 350] : [180]);
  const s = getState().settings;
  if (kind === "due") {
    showNotification("⏰ 烘烤完成", `${label} 时间到，去把它取出来吧`, "bake-" + id);
  } else {
    showNotification("🔔 快到点了", label, "pre-" + id);
  }
}

export function ringOnce(kind = "due") {
  unlockAudio();
  if (getState().settings.sound) {
    if (kind === "due") playAlarm();
    else playChime();
  }
  vibrate(kind === "due" ? [350, 150, 350] : [180]);
}

/** user tapped "知道了" on one alarm */
export function acknowledge(id) {
  const a = ringing.get(id);
  ringing.delete(id);
  if (!a) return;
  if (a.kind === "due") {
    const b = getState().bakes.find((x) => x.id === id);
    if (b && b.status === "due") markDoneQuiet(id);
  }
  if (a.kind === "sample") {
    // sampling due alarms don't need state change
  }
  emit();
}

function markDoneQuiet(id) {
  updateBake(id, {});
  import("./store.js").then(({ markBakeDone }) => markBakeDone(id));
}

export function snooze(id, minutes = 2) {
  const a = ringing.get(id);
  if (!a) return;
  ringing.delete(id);
  if (a.kind === "due") {
    const b = getState().bakes.find((x) => x.id === id);
    if (b) updateBake(id, { startAt: Date.now() - b.minutes * 60000 + minutes * 60000, status: "running" });
  }
  emit();
}

function tick() {
  const now = Date.now();
  const s = getState();

  for (const b of s.bakes) {
    if (b.status === "running") {
      if (now >= b.endAt) {
        updateBake(b.id, { status: "due" });
        b.status = "due";
        startRinging(b.id, "due", `${b.label} · ${b.tempC}℃ × ${b.minutes}分`);
      } else {
        const preMin = b.preAlert ?? s.settings.preAlertMinutes ?? 5;
        const preTs = b.endAt - preMin * 60000;
        if (preMin > 0 && now >= preTs && now < b.endAt && !b.preAlerted) {
          b.preAlerted = true;
          startRinging(b.id, "chime", `${b.label} 还有约 ${Math.round((b.endAt - now) / 60000)} 分钟完成`);
        }
      }
    }
  }

  for (const t of s.sampling) {
    if (t.status !== "pending") continue;
    const preMin = t.preAlertMinutes == null ? s.settings.preAlertMinutes : t.preAlertMinutes;
    const preTs = t.dueAt - preMin * 60000;
    if (preMin > 0 && now >= preTs && now < t.dueAt && !t.preAlerted) {
      t.preAlerted = true;
      persistPreFlag(t.id);
      startRinging(t.id, "chime", `${t.customer} 取样 · ${fmtClock(t.dueAt)}`);
    } else if (now >= t.dueAt && !t.dueAlarmed) {
      t.dueAlarmed = true;
      persistDueFlag(t.id);
      startRinging(t.id, "sample", `${t.customer} 取样时间已到`);
    }
  }

  maybeReleaseWakeLock();
}

function persistPreFlag(id) {
  updateSampling(id, { preAlerted: true });
}
function persistDueFlag(id) {
  updateSampling(id, { dueAlarmed: true });
}

function fmtClock(ts) {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** force an immediate check (e.g. after adding a task) */
export function checkNow() {
  tick();
}

export function startEngine() {
  if (started) return;
  started = true;
  unlockAudio();
  ensureWakeLock();
  tickTimer = setInterval(tick, 1000);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      unlockAudio();
      ensureWakeLock();
      tick();
    }
  });
  subscribe(() => {
    ensureWakeLock();
  });
  window.addEventListener("beforeunload", () => {
    if (wakeLock && !wakeLock.released) wakeLock.release().catch(() => {});
  });
}

/** banner countdown text helper */
export function alarmList() {
  return [...ringing.values()];
}

export function countdownText(endAt) {
  return mmss(endAt - Date.now());
}
