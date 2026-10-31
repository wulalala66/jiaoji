/* 胶记 service worker: runtime cache, network-first for freshness */
const CACHE = "jiaoji-v1";
const CORE = ["./", "./index.html", "./manifest.webmanifest", "./icons/icon.svg"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CORE)).then(() => self.skipWaiting()));
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
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;

  // SPA shell
  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put("./index.html", copy));
          return res;
        })
        .catch(() => caches.match("./index.html"))
    );
    return;
  }

  // assets: stale-while-revalidate
  e.respondWith(
    caches.match(req).then((hit) => {
      const net = fetch(req)
        .then((res) => {
          if (res.ok) {
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

/* alarm relay: page asks SW to show a notification even when tab hidden */
self.addEventListener("message", (e) => {
  const data = e.data || {};
  if (data.type === "notify") {
    self.registration.showNotification(data.title || "胶记提醒", {
      body: data.body || "",
      tag: data.tag || "jiaoji",
      renotify: true,
      requireInteraction: true,
      vibrate: data.vibrate !== false ? [300, 150, 300, 150, 300] : undefined,
      icon: "./icons/icon-192.png",
      badge: "./icons/icon-192.png",
      data: { url: "./" },
    });
  }
});

self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      for (const c of list) {
        if ("focus" in c) return c.focus();
      }
      return self.clients.openWindow("./");
    })
  );
});
