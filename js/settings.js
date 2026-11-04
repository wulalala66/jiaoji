/* ============================================================
   胶记 · 设置
   AI 配置 / 数据备份 / 关于
   ============================================================ */

import { el, debounce, downloadBlob, todayText } from "./utils.js";
import { actions, getState, legacyBackup } from "./store.js";
import { confirmDialog, copyText, toast } from "./ui.js";
import { fetchModels, normalizeBase } from "./ai.js";

const VERSION = "3.0.1";

export function renderSettings(root, { navigate }) {
  const state = getState();
  const ai = state.settings.ai;

  root.appendChild(
    el(
      "header",
      { class: "page-head" },
      el("div", null, el("h1", null, "设置"), el("p", { class: "page-sub" }, "AI 助手 · 数据 · 关于"))
    )
  );

  // ---------------- AI
  const baseInput = el("input", {
    class: "input",
    type: "text",
    inputmode: "url",
    autocomplete: "off",
    spellcheck: "false",
    placeholder: "https://xxx/v1",
    value: ai.baseUrl,
    oninput: debounce((e) => actions.updateAi({ baseUrl: e.target.value.trim() }), 400),
    onblur: (e) => {
      const v = normalizeBase(e.target.value);
      if (v !== e.target.value) {
        e.target.value = v;
        actions.updateAi({ baseUrl: v });
      }
    },
  });

  const keyInput = el("input", {
    class: "input",
    type: "password",
    autocomplete: "off",
    spellcheck: "false",
    placeholder: "sk-…（只存在这台设备上）",
    value: ai.apiKey,
    oninput: debounce((e) => {
      actions.updateAi({ apiKey: e.target.value.trim() });
      if (!getState().settings.ai.apiKey) sessionStorage.removeItem("jiaoji.key");
    }, 400),
  });

  const keyToggle = el(
    "button",
    {
      class: "btn btn-ghost btn-xs",
      type: "button",
      onclick: () => {
        const show = keyInput.type === "password";
        keyInput.type = show ? "text" : "password";
        keyToggle.textContent = show ? "隐藏" : "显示";
      },
    },
    "显示"
  );

  const modelSelect = el("select", { class: "input" });
  const modelInput = el("input", {
    class: "input",
    type: "text",
    placeholder: "选一个，或直接手填模型名",
    value: ai.model,
    oninput: debounce((e) => actions.updateAi({ model: e.target.value.trim() }), 400),
  });

  const modelHint = el("p", { class: "hint" }, ai.model ? `当前：${ai.model}` : "还没选模型");

  const fillModels = (list) => {
    modelSelect.replaceChildren(el("option", { value: "" }, `— 共 ${list.length} 个模型 —`));
    for (const m of list) modelSelect.appendChild(el("option", { value: m }, m));
    modelSelect.hidden = false;
  };

  modelSelect.hidden = true;
  modelSelect.addEventListener("change", (e) => {
    if (!e.target.value) return;
    modelInput.value = e.target.value;
    actions.updateAi({ model: e.target.value });
    modelHint.textContent = `当前：${e.target.value}`;
  });

  const testOut = el("div", { class: "test-out" });

  const pullModels = async (btn) => {
    const cur = getState().settings.ai;
    if (!cur.baseUrl || !cur.apiKey) {
      toast("先填地址和 Key", "warn");
      return;
    }
    btn.disabled = true;
    const old = btn.textContent;
    btn.textContent = "拉取中…";
    try {
      const list = await fetchModels(cur);
      if (!list.length) {
        toast("地址通了，但没返回模型", "warn");
      } else {
        fillModels(list);
        if (!cur.model) {
          modelInput.value = list[0];
          actions.updateAi({ model: list[0] });
          modelHint.textContent = `当前：${list[0]}`;
        }
        toast(`拿到 ${list.length} 个模型`, "ok");
      }
    } catch (e) {
      testOut.textContent = "✗ " + e.message;
      testOut.className = "test-out bad";
    } finally {
      btn.disabled = false;
      btn.textContent = old;
    }
  };

  const testBtn = el(
    "button",
    { class: "btn btn-soft btn-sm", type: "button", onclick: () => pullModels(testBtn) },
    "拉取模型"
  );

  const chatTestBtn = el(
    "button",
    {
      class: "btn btn-soft btn-sm",
      type: "button",
      onclick: async () => {
        const cur = getState().settings.ai;
        if (!cur.baseUrl || !cur.apiKey || !cur.model) {
          toast("地址 / Key / 模型 都要填", "warn");
          return;
        }
        chatTestBtn.disabled = true;
        testOut.textContent = "测试中…";
        testOut.className = "test-out";
        const started = Date.now();
        try {
          const res = await fetch(normalizeBase(cur.baseUrl) + "/chat/completions", {
            method: "POST",
            headers: { "Content-Type": "application/json", Authorization: "Bearer " + cur.apiKey },
            body: JSON.stringify({
              model: cur.model,
              messages: [
                { role: "system", content: "只回答两个字：收到" },
                { role: "user", content: "在吗" },
              ],
              stream: false,
            }),
          });
          if (!res.ok) throw new Error(`HTTP ${res.status}：${(await res.text()).slice(0, 200)}`);
          const json = await res.json();
          const text = json?.choices?.[0]?.message?.content || "";
          testOut.textContent = `✓ 通了：${cur.model} 回答「${text.slice(0, 20)}」，耗时 ${Date.now() - started}ms`;
          testOut.className = "test-out ok";
        } catch (e) {
          testOut.textContent = "✗ " + (e.message || "失败") + "\n（如果是跨域被拦，用安卓 App 更稳）";
          testOut.className = "test-out bad";
        } finally {
          chatTestBtn.disabled = false;
        }
      },
    },
    "测试对话"
  );

  const thinkingChips = el(
    "div",
    { class: "chips" },
    ...[
      ["", "默认"],
      ["low", "低"],
      ["medium", "中"],
      ["high", "高"],
    ].map(([v, label]) =>
      el(
        "button",
        {
          class: `chip-btn${ai.thinking === v ? " on" : ""}`,
          type: "button",
          onclick: (e) => {
            actions.updateAi({ thinking: v });
            [...e.currentTarget.parentElement.children].forEach((c) => c.classList.remove("on"));
            e.currentTarget.classList.add("on");
          },
        },
        label
      )
    )
  );

  const systemInput = el("textarea", {
    class: "input textarea",
    rows: "5",
    placeholder: "留空就用内置的配料助手提示词",
    value: ai.systemPrompt,
    oninput: debounce((e) => actions.updateAi({ systemPrompt: e.target.value }), 500),
  });

  root.appendChild(
    el(
      "section",
      { class: "card" },
      el("div", { class: "card-title" }, "🤖 AI 助手"),
      el("p", { class: "hint" }, "填一个 OpenAI 兼容接口。Key 只存在这台设备的浏览器里，不会发到别处。"),
      field("接口地址", baseInput),
      el("label", { class: "field" }, el("span", { class: "field-label" }, "API Key"), el("div", { class: "row-gap-tight" }, keyInput, keyToggle)),
      el("label", { class: "field" }, el("span", { class: "field-label" }, "模型"), modelSelect, modelInput),
      modelHint,
      el("div", { class: "row-gap-tight" }, testBtn, chatTestBtn),
      testOut,
      el("div", { class: "field-label" }, "思考档位"),
      thinkingChips,
      el("p", { class: "hint" }, "配料这种活「低」通常就够；想让模型多想一会儿再改配方，调「高」。"),
      switchRow("实时点评", "改完配方自动让 AI 看一眼（防抖 1.5 秒）", ai.liveComment, (v) => actions.updateAi({ liveComment: v })),
      switchRow("上下文压缩", "聊久了自动把前面的对话压成摘要，长对话不炸上下文", ai.compress, (v) => actions.updateAi({ compress: v })),
      el("label", { class: "field" }, el("span", { class: "field-label" }, "系统提示词（进阶）"), systemInput)
    )
  );

  // ---------------- 数据
  const fileInput = el("input", {
    type: "file",
    accept: ".json,application/json",
    hidden: "true",
    onchange: async (e) => {
      const file = e.target.files?.[0];
      e.target.value = "";
      if (!file) return;
      if (file.size > 5 * 1024 * 1024) {
        toast("文件太大了（超过 5MB）", "danger");
        return;
      }
      const text = await file.text();
      let preview;
      try {
        preview = JSON.parse(text);
      } catch {
        toast("这不是一个备份文件", "danger");
        return;
      }
      const nf = Array.isArray(preview.formulas) ? preview.formulas.length : 0;
      const np = Array.isArray(preview.products) ? preview.products.length : 0;
      const ok = await confirmDialog({
        title: "导入备份？",
        message: `会用文件里的数据覆盖当前内容：${nf} 个配方、${np} 条产品条件。当前数据会被替换。`,
        confirm: "导入",
        danger: true,
      });
      if (!ok) return;
      try {
        actions.importJson(text);
        toast("导入成功", "ok");
        navigate("#/formulas");
      } catch (err) {
        toast("导入失败：" + err.message, "danger");
      }
    },
  });

  const legacy = legacyBackup();

  root.appendChild(
    el(
      "section",
      { class: "card" },
      el("div", { class: "card-title" }, "💾 数据"),
      el("p", { class: "hint" }, "配方和条件表都存在这台设备的浏览器里，没有账号、没有云同步。备份不含 API Key。"),
      el(
        "button",
        {
          class: "btn btn-soft btn-block",
          type: "button",
          onclick: () => {
            const blob = new Blob([actions.exportJson()], { type: "application/json" });
            downloadBlob(blob, `胶记备份-${todayText()}.json`);
            toast("已导出", "ok");
          },
        },
        "⬇ 导出备份（JSON）"
      ),
      el("button", { class: "btn btn-soft btn-block", type: "button", onclick: () => fileInput.click() }, "⬆ 导入备份"),
      fileInput,
      legacy
        ? el(
            "button",
            {
              class: "btn btn-ghost btn-block",
              type: "button",
              onclick: () => {
                const blob = new Blob([legacy], { type: "application/json" });
                downloadBlob(blob, `胶记-旧版数据-${todayText()}.json`);
                toast("旧版原始数据已导出", "ok");
              },
            },
            "下载旧版（2.x）原始数据"
          )
        : null,
      el(
        "button",
        {
          class: "btn btn-danger-ghost btn-block",
          type: "button",
          onclick: async () => {
            const ok = await confirmDialog({
              title: "清空所有配方？",
              message: "配方会被全部删掉，产品条件表和 AI 配置保留。这个操作不能撤销。",
              confirm: "清空配方",
              danger: true,
            });
            if (ok) {
              actions.clearFormulas();
              toast("已清空", "ok");
            }
          },
        },
        "清空所有配方"
      )
    )
  );

  // ---------------- 关于
  root.appendChild(
    el(
      "section",
      { class: "card" },
      el("div", { class: "card-title" }, "ℹ️ 关于"),
      kv("版本", VERSION),
      kv("数据", `${state.formulas.length} 个配方 · ${state.products.length} 条产品条件`),
      el(
        "p",
        { class: "hint" },
        "胶记是胶粘剂实验室的随身工具：比重计算、配料单、产品条件表。安卓版和网页版共用同一份数据格式，备份可以互导。"
      ),
      el(
        "button",
        {
          class: "btn btn-ghost btn-block",
          type: "button",
          onclick: async () => {
            const ok = await copyText(`胶记 ${VERSION}\n${navigator.userAgent}`);
            toast(ok ? "已复制" : "复制失败", ok ? "ok" : "warn");
          },
        },
        "复制版本信息"
      )
    )
  );
}

function field(label, control) {
  return el("label", { class: "field" }, el("span", { class: "field-label" }, label), control);
}

function kv(label, value) {
  return el("div", { class: "kv" }, el("span", null, label), el("b", null, value));
}

function switchRow(title, sub, checked, onChange) {
  const input = el("input", {
    type: "checkbox",
    class: "switch-input",
    onchange: (e) => onChange(e.target.checked),
  });
  input.checked = !!checked;
  return el(
    "label",
    { class: "switch-row" },
    el("div", { class: "switch-text" }, el("div", { class: "switch-title" }, title), el("div", { class: "hint" }, sub)),
    el("span", { class: "switch" }, input, el("span", { class: "switch-track" }))
  );
}
