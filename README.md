# 胶记 · GLUE JOURNAL

胶粘剂实验室工作台 —— **取样提醒 · 烘烤倒计时 · 检测记录**。原生安卓 App（Kotlin + Jetpack Compose），安装包约 1.2 MB，Android 7.0+。

**下载安装**：[最新 Release](https://github.com/wulalala66/jiaoji/releases/latest) → 下载 `jiaoji-vX.Y.Z.apk`，手机点开安装即可（首次会提示「未知来源」，允许一次即可）。

**网页版**（同一套数据模型，桌面/临时用）：https://wulalala66.github.io/jiaoji/

## 为谁设计

胶粘剂实验室的日常：早上车间负责人发来取样时间（如 8 点半取安徽万安的底涂胶），到点取样、回来测数据（压流、旋转粘度、宾汉粘度、剪切强度、硬度……），期间样品还要放进靠门/靠窗两台烘箱，按条件（如 140℃ × 30 分钟）烘烤到点取出来。事情多、时间碎、全靠脑子记容易漏。

## 功能

- **一句话添加**：点 ＋ 输入「8点半 安徽万安 底涂胶」，自动识别时间 / 客户 / 胶类型；也认「明天」「后天」「周X」「8:30」「0830」。晚上输入早上的时间自动算到明天。
- **取样提醒**：系统闹钟到点响（铃声循环 + 振动 + 全屏通知），锁屏、后台、App 被杀都能响；可按任务单独设提前提醒分钟数。
- **一键建档**：取样回来点「已取样」，自动生成批次号（如 1031-01），按胶类型带出检测项模板，可顺手把样品放进烘箱开始计时。
- **双烘箱计时**：靠门（蓝）/ 靠窗（琥珀）两台烘箱各自倒计时，到点变红响铃直到点「已取出」；支持加 5 分钟、延后 2 分钟、改时间、重新放进烘箱。首页顶部有迷你计时条，任何页面到点都会弹提醒。
- **数据录入**：数值即时保存；填了标准上下限自动判定 ✓ / ✗ 不合格；键盘「下一项」直接跳下一格；顶部进度环与汇总，可和上一批对比。
- **记录与导出**：今天 / 近 7 天 / 全部 + 搜索 + 只看不合格；CSV 导出（Excel 友好）、一键复制当日检测日报、JSON 全量备份 / 恢复（可与网页版互导）。
- **可定制**：胶类型（烘烤预设 + 检测项模板）、客户列表自由增删改；提前提醒、铃声、振动、屏幕常亮可调。
- **系统倒计时**：烘烤卡上的「系统倒计时」把时间交给手机自带时钟 App，双保险。

## 提醒机制（重要）

- 提醒走系统 `AlarmManager`（精确闹钟）+ 前台服务播放闹铃，**不依赖 App 在前台**，锁屏 / 清后台 / 重启手机（自动重挂）都会响。
- Android 12+ 需要「闹钟和提醒」权限：设置页会显示状态，点一下跳到系统开关。
- Android 13+ 需要通知权限，首次打开会申请。
- 国产 ROM（小米 / 华为 / OPPO / vivo 等）建议在系统设置里给「胶记」打开 **自启动**、**后台运行不受限制**，否则系统可能压掉闹钟。

## 技术与构建

- **App**：Kotlin + Jetpack Compose（Material 3），R8 压缩，单 dex；无 Room / 无第三方 SDK，数据存 SharedPreferences 里一份 JSON（key `jiaoji.v1`，与网页版完全同构）。
- **网页版**：原生 ES Modules + Vite，无运行时依赖，数据存 localStorage（同一个 JSON 结构）。
- 本地只做开发（`npm run dev` 起静态服务看网页版；App 在 Android Studio 里跑），**构建与发布全部在 GitHub Actions**：push 到 main 即
  1. `web`：Vite 构建 → 打包 dist → ed25519 固定密钥签名 → 部署 GitHub Pages；
  2. `android`：Gradle 8.10.2 + AGP 8.7.2 构建 release APK → 用固定 keystore 通过 `apksigner` 签名并自验；
  3. `release`：递增 tag（v1.0.0 → v1.0.1 …）→ 创建 Release，附 APK、网页产物、签名、SHA256SUMS。

### 验证 Release

```bash
# APK 签名（用 Android SDK build-tools 里的 apksigner）
apksigner verify --print-certs jiaoji-vX.Y.Z.apk

# 网页产物签名（ed25519 / SSH 签名格式）
printf 'jiaoji-build %s\n' "$(cat signing-key.pub)" > allowed_signers
ssh-keygen -Y verify -f allowed_signers -I jiaoji-build -n jiaoji -s jiaoji-dist.tar.gz.sig < jiaoji-dist.tar.gz
```

私钥都在 GitHub Secrets（`SIGNING_KEY` 给网页产物、`KEYSTORE_B64` / `KS_PASS` / `KEY_ALIAS` 给 APK），公钥 `signing-key.pub` 随仓库提交；每次构建都用同一把密钥签名，保证「密钥固定、可校验」。

## 目录

```
android/                         原生 App
  app/src/main/java/com/wulalala66/jiaoji/
    Models.kt                    数据模型 + JSON 编解码 + 默认模板 + 格式化
    Repo.kt                      全局状态（StateFlow）+ 全部写操作
    Alarms.kt                    闹钟调度 / 前台响铃服务 / 通知按钮 / 开机重挂
    QuickParser.kt               「8点半 安徽万安 底涂胶」中文解析
    Root.kt                      导航 + 底部栏 + 全屏提醒条
    ScreenToday.kt               今天（取样待办 / 烘烤 / 已完成）
    ScreenOvens.kt               烘箱（靠门 / 靠窗 + 计时卡）
    ScreenDetail.kt              样品详情（录入 / 判定 / 完成）
    ScreenRecords.kt             记录（筛选 / 搜索 / 导出）
    ScreenSettings.kt            设置（提醒 / 类型 / 客户 / 备份）
    ScreenForms.kt               添加提醒 / 烘烤 / 类型编辑 / 已取样弹窗
    Components.kt Theme.kt Share.kt MainActivity.kt App.kt

index.html  css/  js/  public/   网页版（PWA）
.github/workflows/deploy.yml     构建 + 签名 + Release + Pages
```
