# 胶记 · GlueLab

胶粘剂实验室的随身工具 —— **比重计算 · 配料单 · 产品条件表 · 内置 AI 助手**。

- **安卓版**：Kotlin + Jetpack Compose，安装包约 1.3 MB，Android 7.0+，包名 `com.gluelab.jiaoji`
- **网页版**：PWA，同一套数据格式，手机浏览器打开就能用，可加到桌面

**下载安装**：[最新 Release](https://github.com/wulalala66/jiaoji/releases/latest) → 下载 `jiaoji-vX.Y.Z.apk`，手机点开安装即可（首次会提示「未知来源」，允许一次即可）。

> **3.0.0 换了包名**（`com.wulalala66.jiaoji` → `com.gluelab.jiaoji`）。安卓会把新包当成另一个 App，旧版不会覆盖升级。
> 升级步骤：旧版「设置 → 数据 → 导出备份」→ 装新版 → 「导入备份」。导入时会自动忽略旧版里已经下线的取样/烘烤字段。

## 四个工具

底部导航：**⚖️ 比重 · 🧪 配料 · 📋 条件表 · ⚙️ 设置**

### ⚖️ 比重

- 填 M1~M6 六个称量值，一次给出三个结果：**比重 / 固体成分% / 发泡率%**。
- 公式照抄车间那张 Excel，别自己改：`d1=M3−M1`、`d2=M4−M2`、`d3=M5−M1`、`d4=M6−M2`；`比重 = d1/(d1−d2)`、`固体成分% = d3/d1×100`、`发泡率% = ((d3−d4)/(d1−d2)−1)×100`。
- 只算比重时填 M1~M4 就够，M5/M6 留空按 0 算；填了 M5、M6 才会给出固体成分和发泡率。
- 样例核对：2.9 / 1.82 / 4.42 / 2.26 / 4.38 / 2.26 → 比重 1.4074、固体成分 97.368%、发泡率 −3.704%。
- 支持「清空」和「分享结果」（微信/邮件直接发出去）。

### 🧪 配料

- 配方 = 一锅总量（默认 600 g）+ 若干原料的百分比。**百分比是唯一准的东西**，克数 = 百分比 × 总量 / 100，改总量时克数自动跟着变。
- 每行两排：上面原料名，下面「克数」「百分比」两个框，输哪边另一边立刻换算；底部实时显示「还差多少 %（多少 g）」，点「补满」就把剩余量补给这一行凑满 100%。
- 支持复制一份（改副本不动原件）、删除。
- **打印 / 导出**：填好「实验目的」「固化温度」后点「打印 / 存 PDF」生成 A4 配料单（照实验室那张纸质表格：左边 原材料 / 用量(g) / wt%，右边 检测项目 / 当日 / 次日，底下合计），点「导出 Excel」生成 `.xlsx`。
  - 安卓：走系统分享，选「打印」就直接出纸。
  - 网页：走浏览器打印对话框（选「另存为 PDF」），Excel 直接下载。
  - 两边都不依赖第三方库，PDF/xlsx 都是自己算出来的。

### 📋 产品条件表

- 内置 16 条常见产品/客户的烘烤条件（中亮 150℃×25 分、折边胶 180℃×20 分、安徽万安 170℃×20 分 / 210℃×20 分…），一个产品可以有多组条件。
- 支持搜索、增删改；AI 也能查这张表（问「安徽万安的底涂胶多少度烤」直接给答案）。

### ⚙️ 设置

- **AI 助手**：地址 / Key / 模型（可一键拉取模型列表）/ 思考档位 / 实时点评 / 上下文压缩 / 系统提示词 / 测试连接。
- **数据**：导出备份（JSON）、导入备份、清空配方。备份**不含 API Key**，可以放心发微信。
- **诊断**（安卓）：AI 连通性两步自检、错误日志（出问题复制给我就能定位）。

## AI 助手

配料页右上角「🤖 问 AI」进入对话；配方页底部还有一张「AI 助手」卡，点「问一下」就地思考。

**怎么配**：设置 → AI 助手 → 填一个 OpenAI 兼容接口（`https://xxx/v1`）、Key，点「拉取」选模型即可。Key 只存在本机。

**它能做什么**（通过 function calling 直接操作数据，不是空口白话）：

| 工具 | 作用 |
| --- | --- |
| `get_formula` / `list_formulas` | 读当前配方（含每行百分比、克数、合计、剩余） |
| `add_item` / `update_item` / `remove_item` | 加料、改比例、删料（同名自动改成更新，不会加两遍） |
| `fill_rest` | 把剩余百分比补到某一行，凑满 100% |
| `set_formula_total` / `create_formula` / `clear_items` / `set_formula_meta` | 改总量、新建配方、清空、写实验目的与固化温度 |
| `get_products` | 查产品条件表 |
| `web_search` / `fetch_url`（仅安卓） | 联网查资料（牌号、供应商、行业做法），再回答 |
| `calc` | 精确算数，不让模型心算 |

**发图片和文件**（3.0.3 起）

对话页输入框左边有个「＋」：

| 类型 | 怎么处理 |
| --- | --- |
| 拍照 / 相册 | 压到最长边 1568px 的 JPEG，按 OpenAI 的 `image_url`（base64 data URL）发出去 |
| PDF | 安卓版用系统 `PdfRenderer` 把前 3 页渲染成图片发过去 —— 扫描件、检测报告比抽文字更还原 |
| Word（.docx） | 本地解 zip 读 `word/document.xml`，剥掉标签当纯文本发 |
| txt / md / csv / json / log | 直接读文本，最多 3 万字 |

- 一次最多 4 个附件，附件只在内存里，退出就没了，不会存到磁盘或备份里。
- 需要**支持视觉的模型**（gpt-4o、qwen-vl、glm-4v 这类）。用纯文本模型发图片，接口会报错，胶记会把原因翻译出来。
- 网页版支持图片和文本类文件；PDF 需要浏览器端渲染库，网页版没引，会提示改用安卓 App 或截图。
- 网页版还支持**直接粘贴截图**（Ctrl+V / 长按粘贴）。

**上下文压缩**：聊得久了（超过约 2.4 万字符）自动把前面的对话压成一段摘要，保留配方数据和结论，只留最近 6 条原样——长对话不炸上下文，也省钱。

**思考档位**：默认 / 低 / 中 / 高（对应 `reasoning_effort`）。配料这种活「低」通常就够；想让模型多想一会儿再改配方，调「高」。

**健壮性**（做法参考 [RikkaHub](https://github.com/rikkahub/rikkahub) 的 AI 层）：

- **退避重试**：只在网络类故障时重试，1s → 2s → 4s 最多 3 次；接口明确报错（4xx/5xx）不重试。
  只在「一个字都还没吐出来」时重试，不会出现重复的半截回答；点了「停止」也不会被重新拉起。
- **流里的错误会抛出来**：有些网关在 SSE 里塞一条 `{"error":{...}}` 就断开，
  以前会被当成「没有内容」忽略，用户只看到一段空白。
- **错误翻译**：401 / 404 / 429 / 5xx、超时、DNS、证书错误都翻成人话；
  错误体是 `{"error":{"message":"..."}}` 这类 JSON 时会把 message 挖出来，不甩一坨 JSON 给你。
- **工具调用 id 兜底**：有的中转不回 `tool_call id`，以前整条调用会被静默丢弃（模型以为改了、实际没改）。
- **空输出占位**：工具返回空时回 `(no output)`，否则模型会以为没执行过、反复重试。
- **破坏性操作要确认**：`clear_items` 必须带 `confirm=true`，模型得先跟你确认才会真的清空。
- 模型把工具调用写成 JSON 正文吐出来时，自动认出来并真的执行。
- 同一轮里重复调用同一个工具会直接回放结果，不会原地打转。
- 工具调用超过轮数、模型返回空内容，都会明说，不会留一段空白。
- 单个工具结果超过 6000 字自动截断，网页正文不会撑爆上下文。
- 点「停止」时已经吐出来的内容会保留成一条消息，不会凭空消失。

## 数据与隐私

- 配方、条件表、AI 配置全部存在本机（安卓：SharedPreferences 里一份 JSON；网页：localStorage），没有账号、没有云同步、没有埋点。
- AI 请求只发往你自己填的接口地址；聊天记录只存在内存里，退出就没了。
- **备份不含 API Key**：导出的 JSON 会把 Key 清空，发给别人不会泄露。
- 网页版的安全措施：严格的 CSP（脚本只允许本站）、所有动态内容走 `textContent` 渲染（不拼 HTML，天然防 XSS）、AI 回复先转义再做最小样式化、外链强制 `http/https` + `noopener`、导入文件限 5MB 且结构校验。
- 换手机用「设置 → 数据 → 导出备份」搬过去，安卓和网页的备份可以互导。

## 技术与构建

- **App**：Kotlin + Jetpack Compose（Material 3），R8 压缩，单 dex；无 Room、无第三方 SDK、无 WebView。网络层是 `HttpURLConnection` + 手写 SSE 解析。只申请 `INTERNET` / `ACCESS_NETWORK_STATE` 两个权限。
- **网页**：原生 ES Module + 一个 `app.css`，Vite 只做打包。没有前端框架、没有运行时依赖。
- 本地只做开发，**构建与发布全部在 GitHub Actions**：push 到 main 即
  1. `web`：Vite 构建网页版 → 打包 → ed25519 固定密钥签名 → 部署 GitHub Pages；
  2. `android`：先跑 **Robolectric 测试**（在 JVM 上真实组合每一屏、真点按钮、真跑一遍 AI 工具读写），再 Gradle 构建 release APK，用固定 keystore 签名并自验；
  3. `release`：按 `package.json` 的 version 打 tag → 创建 Release，附 APK、网页产物、签名、SHA256SUMS。

### 验证 Release

```bash
# APK 签名（用 Android SDK build-tools 里的 apksigner）
apksigner verify --print-certs jiaoji-vX.Y.Z.apk

# 网页产物签名（ed25519 / SSH 签名格式）
printf 'jiaoji-build %s\n' "$(cat signing-key.pub)" > allowed_signers
ssh-keygen -Y verify -f allowed_signers -I jiaoji-build -n jiaoji -s jiaoji-dist.tar.gz.sig < jiaoji-dist.tar.gz
```

私钥都在 GitHub Secrets（`SIGNING_KEY` 给网页产物、`KEYSTORE_B64` / `KS_PASS` / `KEY_ALIAS` 给 APK），公钥 `signing-key.pub` 随仓库提交；每次构建都用同一把密钥签名。

## 目录

```
android/app/src/main/java/com/gluelab/jiaoji/
  Models.kt            数据模型 + JSON 编解码（与网页版同构）+ 默认条件表 + 比重公式
  Repo.kt              全局状态（StateFlow）+ 全部写操作 + 备份导入导出
  Root.kt              导航 + 底部栏（比重 / 配料 / 条件表 / 设置）
  ScreenDensity.kt     比重计算器
  ScreenFormulas.kt    配料列表 + 编辑器（含 AI 卡片、打印导出）
  ScreenFormulaAi.kt   AI 对话（气泡、工具调用提示、思考过程）
  ScreenProducts.kt    产品条件表
  ScreenSettings.kt    设置（AI 配置 / 数据 / 关于）
  ScreenDiagnostics.kt 诊断（AI 连通性、错误日志）
  Ai.kt                OpenAI 兼容客户端（流式 SSE、工具循环、重试、压缩、拉模型）
  AiSession.kt         会话状态（跑在页面外，切页不中断）
  AiTools.kt           AI 可调用的工具（配方增删改查 / 条件表 / 搜索 / 抓网页 / 计算器）
  FormulaSheet.kt      A4 配料单 PDF + xlsx 导出（无第三方库）
  Components.kt Theme.kt Share.kt Log.kt App.kt MainActivity.kt

android/app/src/test/  Robolectric 测试（每屏冒烟 + AI 工具读写 + 比重公式 + 配料单导出 + 数据层）

index.html
css/app.css            唯一一份样式表
js/store.js            状态 + 存储 + 编解码（与安卓同一份 JSON）
js/density.js          比重计算器
js/formulas.js         配料列表 + 编辑器
js/products.js         产品条件表
js/settings.js         设置
js/ai.js               AI 客户端 + 工具 + 对话页
js/export.js           打印配料单 + 手写 xlsx
js/utils.js ui.js      安全 DOM 构造、格式化、提示条/弹层
js/main.js             路由 + 底部导航

.github/workflows/deploy.yml  构建 + 测试 + 签名 + Release + Pages
```

## 本地开发

```bash
npm ci
npm run dev     # http://localhost:4173，手机同网段可直接访问
npm run build   # 产物在 dist/
```

安卓端需要 JDK 17 + Android SDK 35 + Gradle 8.10.2，`gradle -p android :app:testDebugUnitTest` 跑测试。
