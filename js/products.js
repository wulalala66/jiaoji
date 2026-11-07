/* ============================================================
   胶记 · 产品条件表
   点产品 = 按这条条件开始倒计时（多条条件先让你选）
   长按产品 = 编辑
   ============================================================ */

import { el, debounce, num, clear, fixed } from "./utils.js";
import { actions, getState, plateLines, plateText, timerLeft } from "./store.js";
import { confirmDialog, emptyHint, openSheet, pill, toast } from "./ui.js";

let ticker = null;

function fmtLeft(ms) {
  const total = Math.ceil(ms / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const p = (n) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${p(m)}:${p(s)}` : `${p(m)}:${p(s)}`;
}

/**
 * 安卓浏览器里用 intent:// 直接调系统时钟的 SET_TIMER。
 *
 * 参数类型前缀：i.=int，S.=String，b.=boolean（大小写有讲究）。
 * 桌面浏览器和 iOS 不支持，直接返回 false。
 */
export function trySystemTimer(minutes, label) {
  const ua = typeof navigator === "undefined" ? "" : navigator.userAgent || "";
  if (!/Android/i.test(ua)) return false;
  const url =
    "intent://#Intent;action=android.intent.action.SET_TIMER;" +
    `i.android.intent.extra.alarm.LENGTH=${minutes * 60};` +
    `S.android.intent.extra.alarm.MESSAGE=${encodeURIComponent(label)};` +
    "b.android.intent.extra.alarm.SKIP_UI=true;end";
  try {
    window.location.href = url;
    return true;
  } catch {
    return false;
  }
}

/** 生成 .ics：没有系统时钟 API 时，交给系统日历 */
export function buildIcs(title, startAt, minutes) {
  const fmt = (ms) => new Date(ms).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
  const safe = String(title).replace(/[\n,;]/g, " ");
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//GlueLab//JiaoJi//CN",
    "CALSCALE:GREGORIAN",
    "BEGIN:VEVENT",
    `UID:${Date.now()}@jiaoji`,
    `DTSTAMP:${fmt(Date.now())}`,
    `DTSTART:${fmt(startAt)}`,
    `DTEND:${fmt(startAt + minutes * 60000)}`,
    `SUMMARY:${safe}`,
    `DESCRIPTION:烘烤计时 ${minutes} 分钟`,
    "BEGIN:VALARM",
    "TRIGGER:-PT0M",
    "ACTION:DISPLAY",
    `DESCRIPTION:${safe}`,
    "END:VALARM",
    "END:VEVENT",
    "END:VCALENDAR",
  ].join("\r\n");
}

export function renderProducts(root) {
  let keyword = "";
  let firedFor = null;

  root.appendChild(
    el(
      "header",
      { class: "page-head" },
      el("div", null, el("h1", null, "产品条件表"), el("p", { class: "page-sub" }, "点条件开始计时 · 长按产品编辑")),
      el("button", { class: "btn btn-primary btn-sm", type: "button", onclick: () => editProduct(null) }, "＋ 新增")
    )
  );

  const search = el("input", {
    class: "input search",
    type: "search",
    placeholder: "搜产品 / 客户 / 温度",
    autocomplete: "off",
    oninput: debounce((e) => {
      keyword = e.target.value.trim();
      draw();
    }, 150),
  });
  root.appendChild(search);

  const timerBox = el("div", { class: "timer-host" });
  root.appendChild(timerBox);

  const list = el("div", { class: "list" });
  root.appendChild(list);
  root.appendChild(el("p", { class: "hint foot-hint" }, /Android/i.test(typeof navigator === "undefined" ? "" : navigator.userAgent || "")
      ? "点一下条件 → 交给系统时钟计时；长按产品可以改。"
      : "点一下条件开始页面内计时（关掉页面就不响了，可用「加到日历」）；长按产品可以改。"));

  // ---------------------------------------------------------------- 计时条
  function drawTimer() {
    const t = getState().timer;
    clear(timerBox);
    if (ticker) {
      clearInterval(ticker);
      ticker = null;
    }
    if (!t) return;

    const label = el("div", { class: "timer-label" }, [t.productName, t.tempC ? `${t.tempC}℃` : ""].filter(Boolean).join(" · "));
    const clock = el("div", { class: "timer-clock" });
    const stateText = el("div", { class: "timer-state" });
    const pauseBtn = el("button", { class: "btn btn-soft btn-sm", type: "button" });
    const stopBtn = el("button", { class: "btn btn-ghost btn-sm", type: "button" }, "结束");

    const panel = el(
      "section",
      { class: "card timer-card" },
      el("div", { class: "timer-head" }, label, el("span", { class: "timer-total" }, `共 ${t.minutes} 分`)),
      clock,
      stateText,
      el(
        "div",
        { class: "timer-actions" },
        pauseBtn,
        stopBtn,
        el(
          "button",
          {
            class: "btn btn-ghost btn-sm",
            type: "button",
            title: "关掉页面就不响了，交给系统日历更稳",
            onclick: () => {
              const blob = new Blob([buildIcs(`${t.productName} 烘烤 ${t.minutes} 分钟`, Date.now() + t.minutes * 60000, t.minutes)], {
                type: "text/calendar;charset=utf-8",
              });
              const url = URL.createObjectURL(blob);
              const a = el("a", { href: url, download: "烘烤计时.ics" });
              document.body.appendChild(a);
              a.click();
              a.remove();
              setTimeout(() => URL.revokeObjectURL(url), 3000);
              toast("已导出 .ics，用系统日历打开就会到点提醒", "ok", 3600);
            },
          },
          "加到日历"
        )
      )
    );
    timerBox.appendChild(panel);

    const paint = () => {
      const cur = getState().timer;
      if (!cur) {
        drawTimer();
        return;
      }
      const left = timerLeft(cur);
      const done = left <= 0 && cur.pausedLeft < 0;
      clock.textContent = done ? "到点了，出炉！" : fmtLeft(left);
      panel.classList.toggle("done", done);
      panel.classList.toggle("paused", cur.pausedLeft >= 0);
      stateText.textContent = done ? "" : cur.pausedLeft >= 0 ? "已暂停" : "";
      pauseBtn.textContent = cur.pausedLeft >= 0 ? "继续" : "暂停";
      pauseBtn.hidden = done;
      stopBtn.textContent = done ? "知道了" : "结束";
      if (done && firedFor !== cur.endAt) {
        firedFor = cur.endAt;
        alertDone(cur);
      }
    };

    pauseBtn.onclick = () => {
      const cur = getState().timer;
      if (!cur) return;
      if (cur.pausedLeft >= 0) actions.resumeTimer();
      else actions.pauseTimer();
      paint();
    };
    stopBtn.onclick = async () => {
      const cur = getState().timer;
      if (!cur) return;
      if (timerLeft(cur) > 0) {
        const ok = await confirmDialog({
          title: "结束这次计时？",
          message: `还剩 ${fmtLeft(timerLeft(cur))}，结束了就不提醒了。`,
          confirm: "结束",
          danger: true,
        });
        if (!ok) return;
      }
      actions.stopTimer();
      drawTimer();
    };

    paint();
    ticker = setInterval(paint, 500);
  }

  /** 到点：振动 + 通知 + 声音（能用就用，不能用就算了） */
  function alertDone(t) {
    try {
      navigator.vibrate?.([400, 200, 400, 200, 400]);
    } catch {
      /* 不支持就算了 */
    }
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.25, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.9);
      osc.start();
      osc.stop(ctx.currentTime + 0.9);
    } catch {
      /* 浏览器不让响就算了 */
    }
    if ("Notification" in window && Notification.permission === "granted") {
      try {
        new Notification("烘烤到点了", {
          body: [t.productName, t.tempC ? `${t.tempC}℃` : "", `${t.minutes} 分`].filter(Boolean).join(" · "),
          tag: "jiaoji-timer",
        });
      } catch {
        /* 忽略 */
      }
    }
    toast("⏰ 烘烤到点了，出炉！", "ok", 6000);
  }

  // ---------------------------------------------------------------- 列表
  function start(p, b) {
    const label = `${p.name} ${b.temp}℃`;
    // 安卓浏览器：和安卓 App 一样，直接交给系统时钟
    if (trySystemTimer(b.minutes, label)) {
      actions.stopTimer();
      drawTimer();
      toast(`已交给系统时钟：${b.minutes} 分钟`, "ok", 3200);
      return;
    }
    // 其它浏览器没有系统时钟接口，用页面内计时
    if ("Notification" in window && Notification.permission === "default") {
      Notification.requestPermission().catch(() => {});
    }
    actions.startTimer(p.name, b.temp, b.minutes);
    firedFor = null;
    drawTimer();
    toast(`开始计时：${label} ${b.minutes}分（关掉页面就不响了，可以点「加到日历」）`, "ok", 3600);
  }

  function tapProduct(p) {
    if (!p.bakes.length) {
      toast(`「${p.name}」还没有烘烤条件，长按可以编辑`, "warn");
      return;
    }
    if (p.bakes.length === 1) {
      start(p, p.bakes[0]);
      return;
    }
    openSheet("选一个烘烤条件", (body, close) => {
      body.appendChild(
        el("p", { class: "hint" }, `「${p.name}」有 ${p.bakes.length} 条条件，点哪条就按哪条计时。`)
      );
      p.bakes.forEach((b) => {
        body.appendChild(
          el(
            "button",
            {
              class: "btn btn-soft btn-block bake-choice",
              type: "button",
              onclick: () => {
                close();
                start(p, b);
              },
            },
            `${b.temp}℃ ${b.minutes}分`,
            el("span", { class: "bake-choice-go" }, "开始计时 →")
          )
        );
      });
    });
  }

  /** 长按（500ms）触发编辑；期间手指移动超过 10px 就取消 */
  function withLongPress(node, onLong) {
    let timer = 0;
    let sx = 0;
    let sy = 0;
    const cancel = () => {
      clearTimeout(timer);
      timer = 0;
    };
    node.addEventListener("pointerdown", (e) => {
      if (e.pointerType === "mouse" && e.button !== 0) return;
      sx = e.clientX;
      sy = e.clientY;
      cancel();
      timer = setTimeout(() => {
        timer = 0;
        onLong();
      }, 500);
    });
    node.addEventListener("pointermove", (e) => {
      if (!timer) return;
      if (Math.abs(e.clientX - sx) > 10 || Math.abs(e.clientY - sy) > 10) cancel();
    });
    node.addEventListener("pointerup", cancel);
    node.addEventListener("pointercancel", cancel);
    node.addEventListener("pointerleave", cancel);
    node.addEventListener("contextmenu", (e) => e.preventDefault());
  }

  function draw() {
    list.replaceChildren();
    const k = keyword.toLowerCase();
    const items = getState().products.filter(
      (p) =>
        !k ||
        p.name.toLowerCase().includes(k) ||
        p.bakes.some((b) => `${b.temp} ${b.minutes}`.includes(k))
    );
    if (!items.length) {
      list.appendChild(emptyHint("🔍", "没找到", "换个词试试，或者点右上角「新增」"));
      return;
    }
    for (const p of items) {
      const card = el(
        "button",
        { class: "card list-item product-card", type: "button" },
        el(
          "div",
          { class: "list-main" },
          el("div", { class: "list-title" }, p.name),
          el(
            "div",
            { class: "chips" },
            p.bakes.length
              ? p.bakes.map((b) => pill(`${b.temp}℃ ${b.minutes}分`, "pill-brand"))
              : el("span", { class: "muted" }, "还没填烘烤条件（长按编辑）")
          ),
          p.plates?.length
            ? el("div", { class: "chips" }, pill("🧫 " + plateText(p), p.plates.some((x) => x.fresh) ? "pill-warn" : ""))
            : null,
          p.plates?.length && plateLines(p).some((l) => l.includes("·"))
            ? el("div", { class: "plate-notes" }, plateLines(p).filter((l) => l.includes("·")).join(" ｜ "))
            : null
        )
      );
      card.addEventListener("click", (e) => {
        if (card.dataset.longpressed === "1") {
          card.dataset.longpressed = "0";
          e.preventDefault();
          return;
        }
        tapProduct(p);
      });
      withLongPress(card, () => {
        card.dataset.longpressed = "1";
        editProduct(p);
      });
      list.appendChild(card);
    }
  }

  // ---------------------------------------------------------------- 编辑
  function editProduct(existing) {
    openSheet(existing ? "改产品" : "新增产品", (body, close) => {
      const nameInput = el("input", {
        class: "input",
        type: "text",
        value: existing?.name || "",
        placeholder: "比如 安徽万安",
        maxlength: "40",
      });

      const rows = el("div", { class: "bake-rows" });
      const addRow = (temp = "140", minutes = "30") => {
        const t = el("input", { class: "input num", type: "text", inputmode: "numeric", value: temp, "aria-label": "烘烤温度，摄氏度" });
        const m = el("input", { class: "input num", type: "text", inputmode: "numeric", value: minutes, "aria-label": "烘烤时间，分钟" });
        const row = el(
          "div",
          { class: "bake-row" },
          el("label", { class: "field" }, el("span", { class: "field-label" }, "温度 ℃"), t),
          el("label", { class: "field" }, el("span", { class: "field-label" }, "时间 分钟"), m),
          el("button", {
            class: "icon-btn danger bake-del",
            type: "button",
            "aria-label": "删掉这条",
            onclick: () => {
              if (rows.children.length > 1) row.remove();
            },
          }, "✕")
        );
        rows.appendChild(row);
      };
      if (existing?.bakes?.length) existing.bakes.forEach((b) => addRow(String(b.temp), String(b.minutes)));
      else addRow();

      const plateRows = el("div", { class: "bake-rows" });
      const plates = (existing?.plates || []).map((pl) => ({ ...pl }));
      const drawPlates = () => {
        clear(plateRows);
        plates.forEach((pl, i) => {
          const tag = el(
            "button",
            {
              class: `chip-btn${pl.fresh ? " on" : ""}`,
              type: "button",
              onclick: () => {
                plates[i].fresh = !plates[i].fresh;
                drawPlates();
              },
            },
            pl.fresh ? "新板" : "旧板"
          );
          const note = el("input", {
            class: "input",
            type: "text",
            value: pl.note || "",
            placeholder: "用途 / 要求，可空",
            maxlength: "40",
            oninput: (e) => {
              plates[i].note = e.target.value;
            },
          });
          plateRows.appendChild(
            el(
              "div",
              { class: "bake-row" },
              el("div", { class: "plate-tag" }, tag),
              note,
              el("button", {
                class: "icon-btn danger bake-del",
                type: "button",
                "aria-label": "删掉这块板",
                onclick: () => {
                  plates.splice(i, 1);
                  drawPlates();
                },
              }, "✕")
            )
          );
        });
      };
      drawPlates();

      body.appendChild(field("产品 / 客户名", nameInput));
      body.appendChild(el("div", { class: "field-label" }, "烘烤条件（可以加多条）"));
      body.appendChild(rows);
      body.appendChild(
        el("button", { class: "btn btn-ghost btn-block", type: "button", onclick: () => addRow("150", "25") }, "＋ 再加一条条件")
      );

      body.appendChild(el("div", { class: "field-label", style: { "margin-top": "6px" } }, "电泳板（每块单独记用途，不需要就不加）"));
      body.appendChild(plateRows);
      body.appendChild(
        el("button", { class: "btn btn-ghost btn-block", type: "button", onclick: () => { plates.push({ fresh: false, note: "" }); drawPlates(); } }, "＋ 加一块板")
      );
      body.appendChild(
        el("p", { class: "hint" }, "例：安徽万安 4 块 —— 1 块新板写「2mm 流挂」，3 块旧板空着。")
      );

      const collectBakes = () => {
        const out = [];
        rows.querySelectorAll(".bake-row").forEach((row) => {
          const [t, m] = row.querySelectorAll("input");
          const temp = num(t.value);
          const minutes = num(m.value);
          if (temp >= 30 && temp <= 300 && minutes >= 1 && minutes <= 600) {
            out.push({ temp: Math.round(temp), minutes: Math.round(minutes) });
          }
        });
        return out;
      };
      const collectPlates = () => plates.map((pl) => ({ id: pl.id || "", fresh: !!pl.fresh, note: (pl.note || "").trim() }));

      body.appendChild(
        el(
          "div",
          { class: "sheet-actions" },
          existing
            ? el("button", {
                class: "btn btn-danger-ghost",
                type: "button",
                onclick: async () => {
                  const ok = await confirmDialog({
                    title: "删掉这个产品？",
                    message: `「${existing.name}」和它的烘烤条件会一起删掉。`,
                    confirm: "删掉",
                    danger: true,
                  });
                  if (ok) {
                    actions.removeProduct(existing.id);
                    close();
                    toast("已删除", "ok");
                    draw();
                  }
                },
              }, "删除")
            : el("span"),
          el(
            "button",
            {
              class: "btn btn-primary",
              type: "button",
              onclick: () => {
                const name = nameInput.value.trim();
                if (!name) {
                  toast("先填产品名", "warn");
                  nameInput.focus();
                  return;
                }
                if (existing) actions.updateProduct(existing.id, name, collectBakes(), collectPlates());
                else if (!actions.addProduct(name, collectBakes(), collectPlates())) {
                  toast("已经有同名产品了", "warn");
                  return;
                }
                close();
                toast("已保存", "ok");
                draw();
              },
            },
            "保存"
          )
        )
      );
      nameInput.focus();
    });
  }

  draw();
  drawTimer();

  // 页面切走时停掉 ticker
  return () => {
    if (ticker) clearInterval(ticker);
    ticker = null;
  };
}

function field(label, control) {
  return el("label", { class: "field" }, el("span", { class: "field-label" }, label), control);
}
