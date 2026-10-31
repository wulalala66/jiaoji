/* ============================================================
   胶记 · native bridge (Android WebView)
   - system-level exact alarms (AlarmManager) so ringing works in
     background / after the app is killed
   - deep link to the system clock countdown (ACTION_SET_TIMER)
   The web build runs with `bridge === null` and falls back to the
   in-page engine.
   ============================================================ */

const bridge = typeof window !== "undefined" ? window.NativeBridge || null : null;

export function nativeAvailable() {
  return !!bridge;
}

export function nativePlatform() {
  try {
    return bridge?.platform?.() || null;
  } catch {
    return null;
  }
}

export function nativeVersion() {
  try {
    return bridge?.version?.() || null;
  } catch {
    return null;
  }
}

/** schedule (or replace) a system alarm. `id` must be unique. */
export function scheduleNative(id, at, title, body) {
  if (!bridge) return false;
  try {
    bridge.scheduleAlarm(String(id), Math.round(at), String(title || "胶记"), String(body || ""));
    return true;
  } catch (err) {
    console.warn("native schedule failed", err);
    return false;
  }
}

export function cancelNative(id) {
  if (!bridge) return false;
  try {
    bridge.cancelAlarm(String(id));
    return true;
  } catch (err) {
    return false;
  }
}

/** open the phone's built-in countdown with the duration prefilled */
export function systemTimer(minutes, label) {
  if (!bridge) return false;
  try {
    bridge.systemTimer(Math.max(1, Math.round(minutes)), String(label || "胶记"));
    return true;
  } catch (err) {
    console.warn("system timer failed", err);
    return false;
  }
}

/** ids the user already dismissed from the system notification */
export function drainNativeAcked() {
  if (!bridge) return [];
  try {
    const raw = bridge.takeAcked();
    const arr = JSON.parse(raw || "[]");
    return Array.isArray(arr) ? arr.map(String) : [];
  } catch (err) {
    return [];
  }
}

/** ask the native layer to request notification permission (Android 13+) */
export function requestNativeNotify() {
  if (!bridge) return false;
  try {
    bridge.requestNotifyPermission?.();
    return true;
  } catch {
    return false;
  }
}
