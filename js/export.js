/* ============================================================
   胶记 · 打印配料单 + 导出 Excel
   两条都不依赖第三方库：
     · 打印 → 生成一个 A4 版式的 DOM，走浏览器自己的打印（可另存为 PDF）
     · Excel → 自己拼一个最小 xlsx（ZIP + 单 sheet），Excel/WPS 都能打开
   ============================================================ */

import { el, trimNum, todayText, downloadBlob } from "./utils.js";
import { gramsOf, sumGrams, sumPercent } from "./store.js";
import { toast } from "./ui.js";

// ---------------------------------------------------------------- 打印

export function printSheet(f) {
  if (!f) return;
  const old = document.getElementById("print-root");
  if (old) old.remove();

  const rows = f.items.map((it, i) =>
    el(
      "tr",
      null,
      el("td", { class: "c" }, String(i + 1)),
      el("td", null, it.name || "—"),
      el("td", { class: "r mono" }, trimNum(gramsOf(f, it), 1)),
      el("td", { class: "r mono" }, trimNum(it.percent, 2)),
      el("td"),
      el("td"),
      el("td")
    )
  );

  // 至少补到 12 行，打印出来是一张完整的表，不会半截
  while (rows.length < 12) {
    rows.push(el("tr", null, el("td", { class: "c" }, String(rows.length + 1)), el("td"), el("td"), el("td"), el("td"), el("td"), el("td")));
  }

  const sheet = el(
    "div",
    { class: "print-sheet" },
    el("h1", { class: "print-title" }, "配 料 单"),
    el(
      "div",
      { class: "print-meta" },
      el("span", null, "配方：", el("b", null, f.name || "未命名")),
      el("span", null, "日期：", el("b", null, todayText())),
      el("span", null, "总量：", el("b", null, trimNum(f.totalG, 1) + " g"))
    ),
    el(
      "table",
      { class: "print-table" },
      el(
        "thead",
        null,
        el(
          "tr",
          null,
          el("th", { class: "c", style: { width: "5%" } }, "序"),
          el("th", { style: { width: "28%" } }, "原 材 料"),
          el("th", { class: "r", style: { width: "13%" } }, "用量(g)"),
          el("th", { class: "r", style: { width: "12%" } }, "wt%"),
          el("th", { style: { width: "20%" } }, "检 测 项 目"),
          el("th", { style: { width: "11%" } }, "当日"),
          el("th", { style: { width: "11%" } }, "次日")
        )
      ),
      el("tbody", null, ...rows),
      el(
        "tfoot",
        null,
        el(
          "tr",
          null,
          el("td", { class: "c" }, "合"),
          el("td", null, "计"),
          el("td", { class: "r mono" }, trimNum(sumGrams(f), 1)),
          el("td", { class: "r mono" }, trimNum(sumPercent(f), 2)),
          el("td"),
          el("td"),
          el("td")
        )
      )
    ),
    el(
      "div",
      { class: "print-foot" },
      el("span", null, "实验目的：", el("b", null, f.purpose || "")),
      el("span", null, "固化温度：", el("b", null, f.cure || "")),
      el("span", null, "操作人："),
      el("span", null, "审核：")
    )
  );

  const wrap = el("div", { id: "print-root" }, sheet);
  document.body.appendChild(wrap);
  document.body.classList.add("printing");
  const cleanup = () => {
    document.body.classList.remove("printing");
    wrap.remove();
    window.removeEventListener("afterprint", cleanup);
  };
  window.addEventListener("afterprint", cleanup);
  setTimeout(() => {
    window.print();
    // Safari 有时不触发 afterprint，兜个底
    setTimeout(cleanup, 1500);
  }, 60);
}

// ---------------------------------------------------------------- Excel

export function exportXlsx(f) {
  if (!f) return;
  try {
    const blob = makeXlsx(f);
    const name = (f.name || "配料单").replace(/[\\/:*?"<>|]/g, "_").slice(0, 40);
    downloadBlob(blob, `${name}-${todayText()}.xlsx`);
    toast("已生成 Excel", "ok");
  } catch (e) {
    console.error(e);
    toast("导出失败：" + e.message, "danger");
  }
}

/** 单独抽出来方便测试：返回 xlsx 的 Blob */
export function makeXlsx(f) {
  const rows = buildRows(f);
  const sheetXml = sheetToXml(rows, [20, 18, 10, 12, 16, 10, 10]);
  return zipStore([
    ["[Content_Types].xml", CONTENT_TYPES],
    ["_rels/.rels", ROOT_RELS],
    ["xl/workbook.xml", WORKBOOK],
    ["xl/_rels/workbook.xml.rels", WORKBOOK_RELS],
    ["xl/styles.xml", STYLES],
    ["xl/worksheets/sheet1.xml", sheetXml],
  ]);
}

function buildRows(f) {
  const rows = [];
  const put = (cells, style = 0) => rows.push({ cells, style });

  put(["胶记 · 配料单"], 1);
  put(["配方", f.name || "未命名", "", "日期", todayText()], 0);
  put(["实验目的", f.purpose || "", "", "固化温度", f.cure || ""], 0);
  put(["一锅总量(g)", f.totalG, "", "", ""], 0);
  put([""]);
  put(["原材料", "用量(g)", "wt%", "检测项目", "当日", "次日"], 2);
  for (const it of f.items) {
    put([it.name || "", round1(gramsOf(f, it)), round2(it.percent), "", "", ""], 0);
  }
  put(["合计", round1(sumGrams(f)), round2(sumPercent(f)), "", "", ""], 2);
  return rows;
}

const round1 = (v) => Math.round(v * 10) / 10;
const round2 = (v) => Math.round(v * 100) / 100;

function colName(i) {
  let s = "";
  let n = i;
  do {
    s = String.fromCharCode(65 + (n % 26)) + s;
    n = Math.floor(n / 26) - 1;
  } while (n >= 0);
  return s;
}

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, "");
}

function sheetToXml(rows, widths) {
  const cols = widths
    .map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`)
    .join("");

  const body = rows
    .map((row, r) => {
      const cells = row.cells
        .map((v, c) => {
          const ref = `${colName(c)}${r + 1}`;
          const style = row.style ? ` s="${row.style}"` : "";
          if (v === "" || v === null || v === undefined) return `<c r="${ref}"${style}/>`;
          if (typeof v === "number" && Number.isFinite(v)) return `<c r="${ref}"${style}><v>${v}</v></c>`;
          return `<c r="${ref}"${style} t="inlineStr"><is><t xml:space="preserve">${esc(v)}</t></is></c>`;
        })
        .join("");
      return `<row r="${r + 1}">${cells}</row>`;
    })
    .join("");

  return (
    `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
    `<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
    `<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>` +
    `<cols>${cols}</cols>` +
    `<sheetData>${body}</sheetData>` +
    `<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>` +
    `<pageSetup paperSize="9" orientation="portrait" fitToWidth="1" fitToHeight="0"/>` +
    `</worksheet>`
  );
}

const CONTENT_TYPES =
  `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
  `<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">` +
  `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>` +
  `<Default Extension="xml" ContentType="application/xml"/>` +
  `<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>` +
  `<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>` +
  `<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>` +
  `</Types>`;

const ROOT_RELS =
  `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
  `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
  `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>` +
  `</Relationships>`;

const WORKBOOK =
  `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
  `<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" ` +
  `xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">` +
  `<sheets><sheet name="配料单" sheetId="1" r:id="rId1"/></sheets></workbook>`;

const WORKBOOK_RELS =
  `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
  `<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">` +
  `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>` +
  `<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>` +
  `</Relationships>`;

const STYLES =
  `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` +
  `<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">` +
  `<fonts count="4">` +
  `<font><sz val="11"/><name val="宋体"/></font>` +
  `<font><b/><sz val="16"/><name val="宋体"/></font>` +
  `<font><b/><sz val="11"/><name val="宋体"/></font>` +
  `<font><sz val="11"/><name val="宋体"/><color rgb="FF666666"/></font>` +
  `</fonts>` +
  `<fills count="3">` +
  `<fill><patternFill patternType="none"/></fill>` +
  `<fill><patternFill patternType="gray125"/></fill>` +
  `<fill><patternFill patternType="solid"><fgColor rgb="FFEAF3EF"/><bgColor indexed="64"/></patternFill></fill>` +
  `</fills>` +
  `<borders count="2">` +
  `<border><left/><right/><top/><bottom/><diagonal/></border>` +
  `<border><left style="thin"/><right style="thin"/><top style="thin"/><bottom style="thin"/><diagonal/></border>` +
  `</borders>` +
  `<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>` +
  `<cellXfs count="4">` +
  `<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>` +
  `<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>` +
  `<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>` +
  `<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>` +
  `</cellXfs>` +
  `<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>` +
  `</styleSheet>`;

// ---------------------------------------------------------------- 最小 ZIP

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(bytes) {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

const enc = new TextEncoder();

/** 打包成 ZIP（store 方式，不压缩 —— xlsx 里的 XML 本来就小，这样最省事也最稳） */
function zipStore(files) {
  const chunks = [];
  const central = [];
  let offset = 0;
  const now = new Date();
  const dosTime = ((now.getHours() << 11) | (now.getMinutes() << 5) | (now.getSeconds() >> 1)) & 0xffff;
  const dosDate = (((now.getFullYear() - 1980) << 9) | ((now.getMonth() + 1) << 5) | now.getDate()) & 0xffff;

  for (const [name, text] of files) {
    const nameBytes = enc.encode(name);
    const data = enc.encode(text);
    const crc = crc32(data);

    const local = new Uint8Array(30 + nameBytes.length);
    const lv = new DataView(local.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(4, 20, true);
    lv.setUint16(6, 0x0800, true); // UTF-8 文件名
    lv.setUint16(8, 0, true); // store
    lv.setUint16(10, dosTime, true);
    lv.setUint16(12, dosDate, true);
    lv.setUint32(14, crc, true);
    lv.setUint32(18, data.length, true);
    lv.setUint32(22, data.length, true);
    lv.setUint16(26, nameBytes.length, true);
    lv.setUint16(28, 0, true);
    local.set(nameBytes, 30);

    chunks.push(local, data);

    const cen = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(cen.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, 0x0800, true);
    cv.setUint16(10, 0, true);
    cv.setUint16(12, dosTime, true);
    cv.setUint16(14, dosDate, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, data.length, true);
    cv.setUint32(24, data.length, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, offset, true);
    cen.set(nameBytes, 46);
    central.push(cen);

    offset += local.length + data.length;
  }

  const centralSize = central.reduce((s, c) => s + c.length, 0);
  const end = new Uint8Array(22);
  const ev = new DataView(end.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(4, 0, true);
  ev.setUint16(6, 0, true);
  ev.setUint16(8, central.length, true);
  ev.setUint16(10, central.length, true);
  ev.setUint32(12, centralSize, true);
  ev.setUint32(16, offset, true);
  ev.setUint16(20, 0, true);

  return new Blob([...chunks, ...central, end], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}
