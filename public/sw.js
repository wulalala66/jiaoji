/* ============================================================
   胶记 · service worker
   策略：
     · 导航请求：先走网络（拿到最新页面），失败再退回缓存 —— 离线也能打开；
     · 静态资源：stale-while-revalidate，回头发新版本会自动更新；
     · 跨域请求（AI 接口）一律不碰，绝不缓存对话内容。
   缓存名带版本号：发版后旧缓存会在 activate 里被清掉。
   ============================================================ */

const VERSION = "3.2.6";
const CACHE = `jiaoji-${VERSION}`;
const CORE = ["./", "./index.html", "./manifest.webmanifest", "./icons/icon.svg", "./css/app.css"];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches
      .open(CACHE)
      .then((c) => c.addAll(CORE).catch(() => undefined))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;

  let url;
  try {
    url = new URL(req.url);
  } catch {
    return;
  }
  // AI 接口、任何跨域请求：不拦、不缓存
  if (url.origin !== location.origin) return;

  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put("./index.html", copy));
          return res;
        })
        .catch(() => caches.match("./index.html").then((hit) => hit || Response.error()))
    );
    return;
  }

  e.respondWith(
    caches.match(req).then((hit) => {
      const net = fetch(req)
        .then((res) => {
          if (res.ok && res.type === "basic") {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(req, copy));
          }
          return res;
        })
        .catch(() => hit);
      return hit || net;
    })
  );
});
