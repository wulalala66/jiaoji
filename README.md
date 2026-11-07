# 胶记 · GlueLab

胶粘剂实验室的随身工具 —— **比重计算 · 产品条件表 · 烘烤计时**。

- **安卓版**：Kotlin + Jetpack Compose，安装包约 1 MB，Android 7.0+，包名 `com.gluelab.jiaoji`
- **网页版**：PWA，同一套数据格式，手机浏览器打开就能用，可加到桌面
- **完全离线**：不联网、无账号、无埋点

**下载安装**：[最新 Release](https://github.com/wulalala66/jiaoji/releases/latest) → 下载 `jiaoji-vX.Y.Z.apk`，手机点开安装即可（首次会提示「未知来源」，允许一次即可）。

> **3.1.0 删掉了「配料」和 AI 助手**，只留每天真正高频的三件事。
> 升级不会丢数据：产品条件表和比重记录都会保留。

## 三个工具

底部导航：**⚖️ 比重 · 📋 条件表 · ⚙️ 设置**（打开 App 默认停在「比重」）

### ⚖️ 比重

- 填 M1~M6 六个称量值，一次给出三个结果：**比重 / 固体成分% / 发泡率%**。
- 公式照抄车间那张 Excel，别自己改：`d1=M3−M1`、`d2=M4−M2`、`d3=M5−M1`、`d4=M6−M2`；`比重 = d1/(d1−d2)`、`固体成分% = d3/d1×100`、`发泡率% = ((d3−d4)/(d1−d2)−1)×100`。
- 只算比重时填 M1~M4 就够，M5/M6 留空按 0 算；填了 M5、M6 才会给出固体成分和发泡率。
- 样例核对：2.9 / 1.82 / 4.42 / 2.26 / 4.38 / 2.26 → 比重 1.4074、固体成分 97.368%、发泡率 −3.704%。
- **记录每次测试**：点「保存这次结果」会把当时的六个称量值和三个结果一起存下来，
  历史按时间倒序排在下面，点一条就能把称量值填回上面重算。最多留 200 条，可单条删除或清空。
- 支持「清空」和「分享结果」（微信/邮件直接发出去）。

### 📋 产品条件表

- 内置 16 条常见产品/客户的烘烤条件（中亮 150℃×25 分、折边胶 180℃×20 分、安徽万安 170℃×20 分 / 210℃×20 分…），一个产品可以有多组条件。
- **点一下 = 开始倒计时**：
  - 一个产品只有一条条件 → 直接按这条开始；
  - **有多条条件 → 先弹一层让你选**（比如安徽万安的 170℃×20 分 / 210℃×20 分）；
  - 计时条显示剩余时间，可以暂停/继续/结束；到点会振动 + 响一声 + 发通知。
- **长按 = 编辑**：改名字、增删烘烤条件、配电泳板。
- 搜索：产品名、客户名、温度都能搜。
- **电泳板**：每条产品记一张板子清单，**每块板单独写新旧和用途**——板子是有各自用途的，不是只有新板才有条件。
  例：安徽万安 4 块 —— 1 块新板写「2mm 流挂」，3 块旧板空着；也可以一块写「测附着力」、一块写「测硬度」。

### ⚙️ 设置

- **数据**：导出备份（JSON）、导入备份、清空。
- **关于**：版本、包名、数据条数。
- 换手机：旧手机「导出备份」→ 文件发到微信 → 新手机打开文件选「用胶记打开」。

## 计时是怎么实现的

- 存的是**结束时刻**而不是「还剩多少秒」：App 切后台、屏幕熄掉、甚至进程被杀再打开，时间都还是对的。
- 到点提醒：振动 + 通知（Android 13+ 首次计时会申请通知权限）。
- 故意没有用前台服务和精确闹钟——那套在 2.8.0 就砍掉了，为了一个 20 分钟的计时不值得常驻一个服务。
  代价是：如果系统把 App 进程杀了，就不会响；下次打开 App 会立刻显示「已到点」。

## 数据与隐私

- 产品条件表、电泳板、比重记录全部存在本机（安卓：SharedPreferences 里一份 JSON；网页：localStorage），没有账号、没有云同步、没有埋点。
- **安卓版不申请联网权限**，装完就是纯离线工具。
- 备份文件是纯 JSON，安卓和网页可以互导。

## 技术与构建

- **App**：Kotlin + Jetpack Compose（Material 3），R8 压缩，单 dex；无 Room、无第三方 SDK、无 WebView。
- **网页**：原生 ES Module + 一个 `app.css`，Vite 只做打包。没有前端框架、没有运行时依赖。
- 构建与发布全部在 GitHub Actions：push 到 main 即
  1. `web`：Vite 构建 → 打包 → ed25519 固定密钥签名 → 部署 GitHub Pages；
  2. `android`：先跑 **Robolectric 测试**（在 JVM 上真实组合每一屏），再 Gradle 构建 release APK，用固定 keystore 签名并自验；
  3. `release`：按 `package.json` 的 version 打 tag → 创建 Release，附 APK、网页产物、签名、SHA256SUMS。

### 验证 Release

```bash
apksigner verify --print-certs jiaoji-vX.Y.Z.apk

printf 'jiaoji-build %s\n' "$(cat signing-key.pub)" > allowed_signers
ssh-keygen -Y verify -f allowed_signers -I jiaoji-build -n jiaoji -s jiaoji-dist.tar.gz.sig < jiaoji-dist.tar.gz
```

## 目录

```
android/app/src/main/java/com/gluelab/jiaoji/
  Models.kt            数据模型 + JSON 编解码（与网页版同构）+ 默认条件表 + 比重公式
  Repo.kt              全局状态（StateFlow）+ 全部写操作 + 备份导入导出
  Root.kt              导航 + 底部栏（比重 / 条件表 / 设置）
  ScreenDensity.kt     比重计算器 + 历史记录
  ScreenProducts.kt    产品条件表 + 烘烤倒计时 + 编辑弹层
  ScreenSettings.kt    设置（数据 / 关于）
  TimerAlert.kt        到点提醒（振动 + 通知）
  Components.kt Theme.kt Share.kt Log.kt App.kt MainActivity.kt

android/app/src/test/  Robolectric 测试（每屏冒烟 + 数据层 + 比重公式）

index.html
css/app.css            唯一一份样式表
js/store.js            状态 + 存储 + 编解码（与安卓同一份 JSON）
js/density.js          比重计算器 + 历史
js/products.js         条件表 + 倒计时 + 编辑
js/settings.js         设置
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
