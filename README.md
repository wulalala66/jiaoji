# 胶记 · GlueLab

胶粘剂实验室的随身工具 —— **比重计算 · 配料单 · 产品条件表 · 内置 AI 助手**。原生安卓 App（Kotlin + Jetpack Compose），安装包约 1.3 MB，Android 7.0+。

**下载安装**：[最新 Release](https://github.com/wulalala66/jiaoji/releases/latest) → 下载 `jiaoji-vX.Y.Z.apk`，手机点开安装即可（首次会提示「未知来源」，允许一次即可）。

> 2.8.0 起砍掉了「取样提醒 / 烘箱计时 / 检测记录」整套功能，只保留每天真正高频的四个工具。老版本的配方、条件表会自动迁移过来。

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
- 每行两排：上面原料名，下面「克数」「百分比」两个框，输哪边另一边立刻换算；底部实时显示「还差多少 %（多少 g）」，点原料名字就把剩余量补给这一行凑满 100%。
- 支持复制一份（改副本不动原件）、删除。
- **打印 / 导出**：填好「实验目的」「固化温度」后点「打印 / 存 PDF」生成 A4 配料单（照实验室那张纸质表格：左边 原材料 / 用量(g) / wt%，右边 检测项目 / 当日 / 次日，底下合计），点「导出 Excel」生成 `.xlsx`（同一套表格，可在电脑上改）。两个都走系统分享，选「打印」就直接出纸。

### 📋 产品条件表

- 内置 16 条常见产品/客户的烘烤条件（中亮 150℃×25 分、折边胶 180℃×20 分、安徽万安 170℃×20 分 / 210℃×20 分…），一个产品可以有多组条件。
- 支持搜索、增删改；AI 也能查这张表（问「安徽万安的底涂胶多少度烤」直接给答案）。

### ⚙️ 设置

- **AI 助手**：地址 / Key / 模型（可一键拉取模型列表）/ 思考档位 / 实时点评 / 上下文压缩 / 系统提示词 / 测试连接。
- **数据**：导出备份（JSON）、导入备份、清空配方。
- **诊断**：AI 连通性、崩溃日志（出问题复制给我就能定位）。

## AI 助手

配料页右上角「🤖 问 AI」进入对话；配方页顶部还有一张「AI 点评」卡，改完配方会自动让 AI 看一眼（1.5 秒防抖 + 12 秒最小间隔，不刷屏）。

**怎么配**：设置 → AI 助手 → 填一个 OpenAI 兼容接口（`https://xxx/v1`）、Key，点「拉取」选模型即可。Key 只存在本机，不上传任何第三方服务器。

**它能做什么**（通过 function calling 直接操作 App 里的数据，不是空口白话）：

| 工具 | 作用 |
| --- | --- |
| `get_formula` / `list_formulas` | 读当前配方（含每行百分比、克数、合计、剩余） |
| `add_item` / `update_item` / `remove_item` | 加料、改比例、删料（同名自动改成更新，不会加两遍） |
| `fill_rest` | 把剩余百分比补到某一行，凑满 100% |
| `set_formula_total` / `create_formula` / `clear_items` / `set_formula_meta` | 改总量、新建配方、清空、写实验目的与固化温度 |
| `get_products` | 查产品条件表 |
| `web_search` / `fetch_url` | 联网查资料（牌号、供应商、行业做法），再回答 |
| `calc` | 精确算数，不让模型心算 |

**上下文压缩**：聊得久了（超过约 2.4 万字符）自动把前面的对话压成一段摘要，保留配方数据和结论，只留最近 6 条原样——长对话不炸上下文，也省钱。

**思考档位**：默认 / 低 / 中 / 高（对应 `reasoning_effort`）。配料这种活「低」通常就够；想让模型多想一会儿再改配方，调「高」。

## 数据与隐私

- 配方、条件表、AI 配置全部存在手机本地（SharedPreferences 里一份 JSON），没有账号、没有云同步、没有埋点。
- AI 请求只发往你自己填的接口地址；聊天记录只存在内存里，退出 App 就没了。
- 换手机用「设置 → 数据 → 导出备份」搬过去。

## 技术与构建

- **App**：Kotlin + Jetpack Compose（Material 3），R8 压缩，单 dex；无 Room、无第三方 SDK、无 WebView。网络层是 `HttpURLConnection` + 手写 SSE 解析。
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
android/app/src/main/java/com/wulalala66/jiaoji/
  Models.kt            数据模型 + JSON 编解码 + 默认配方/条件表 + 比重公式
  Repo.kt              全局状态（StateFlow）+ 全部写操作
  Root.kt              导航 + 底部栏（比重 / 配料 / 条件表 / 设置）
  ScreenDensity.kt     比重计算器
  ScreenFormulas.kt    配料列表 + 编辑器（含 AI 点评卡、打印导出）
  ScreenFormulaAi.kt   AI 对话（气泡、工具调用提示、上下文压缩）
  ScreenProducts.kt    产品条件表
  ScreenSettings.kt    设置（AI 配置 / 数据 / 关于）
  ScreenDiagnostics.kt 诊断（连通性、崩溃日志）
  Ai.kt                OpenAI 兼容客户端（流式 SSE、工具循环、压缩、拉模型）
  AiTools.kt           AI 可调用的工具（配方增删改查 / 条件表 / 搜索 / 抓网页 / 计算器）
  FormulaSheet.kt      A4 配料单 PDF + xlsx 导出（无第三方库）
  Components.kt Theme.kt Share.kt MainActivity.kt App.kt

android/app/src/test/  Robolectric 测试（每屏冒烟 + AI 工具读写 + 比重公式 + 配料单导出）
index.html css/ js/ public/  网页版（PWA，仍在 Pages 上）
.github/workflows/deploy.yml  构建 + 测试 + 签名 + Release + Pages
```

