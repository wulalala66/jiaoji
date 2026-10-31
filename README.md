# 胶记 · GLUE JOURNAL

胶粘剂实验室工作台 —— 取样提醒 · 烘烤计时 · 检测记录。纯前端 PWA，无需安装，手机浏览器打开即用。

**在线使用**：https://wulalala66.github.io/jiaoji/

## 为谁设计

胶粘剂实验室的日常：早上车间负责人发来取样时间（如 8 点半取安徽万安的底涂胶），到点取样、回来测数据（压流、旋转粘度、宾汉粘度、剪切强度、硬度……），期间样品还要放进靠门/靠窗两台烘箱，按条件（如 140℃ × 30 分钟）烘烤到点取出来。事情多、时间碎、全靠脑子记容易漏。

## 功能

- **一句话添加**：点 ＋ 输入「8点半 安徽万安 底涂胶」，自动识别时间 / 客户 / 胶类型；也支持「明天」「后天」「周X」「8:30」「0830」等写法。晚上输入早上的时间自动算到明天。
- **取样提醒**：到点弹横幅响铃（每 4 秒重复，直到确认），可按任务单独设置提前提醒分钟数。
- **一键建档**：取样回来点「已取样」，自动生成批次号（如 1031-01），按胶类型带出检测项模板，可顺手把样品放进烘箱开始计时。
- **双烘箱计时**：靠门（蓝）/ 靠窗（琥珀）两台烘箱各自倒计时，到点变红响铃直到点「已取出」；支持加 5 分钟、调整时间、重新放进烘箱。首页顶部也有迷你计时条，任何页面到点都会弹提醒。
- **数据录入**：数值即时保存；填了标准上下限自动判定 ✓/✗不合格；Enter 跳下一项；顶部进度环与汇总。
- **记录与导出**：今天 / 近 7 天 / 全部 + 搜索 + 只看不合格；CSV 导出（Excel 友好）；一键复制当日检测日报；JSON 全量备份 / 恢复。
- **可定制**：胶类型（烘烤预设 + 检测项模板）、客户列表都可自由增删改；提醒方式（铃声/振动/屏幕常亮/系统通知）可调。

## 提醒边界（重要）

- 网页保持后台运行时提醒有效（配合「屏幕常亮」）；**页面被完全关闭则无法响铃**，页内已作提示。
- 系统通知在 HTTPS 下可用（GitHub Pages 满足）；局域网 HTTP 下自动降级为页内响铃。
- iOS Safari 需先「添加到主屏幕」并在设置里允许通知，铃声在首次点击后自动解锁。

## 技术与构建

- 纯原生 ES Modules + Vite 构建，无运行时依赖；数据全部存于本机 localStorage。
- 本地开发：`npm run dev`（零依赖静态服务，端口 4173，打印局域网地址）。
- 构建/发布全部在 GitHub Actions：push 到 main 即自动 **Vite 构建 → 递增打 tag（v1.0.0 → v1.0.1 …）→ 创建 Release** 并上传 dist 产物（tar.gz / zip）、**ed25519 固定密钥签名**（`.sig`，SSH 签名格式）、SHA256SUMS 与公钥，同时部署 GitHub Pages。

### 验证 Release 签名

```bash
# 1. 从 Release 下载 jiaoji-dist.tar.gz、jiaoji-dist.tar.gz.sig、signing-key.pub
printf 'jiaoji-build %s\n' "$(cat signing-key.pub)" > allowed_signers
ssh-keygen -Y verify -f allowed_signers -I jiaoji-build -n jiaoji -s jiaoji-dist.tar.gz.sig < jiaoji-dist.tar.gz
# 输出 Good "jiaoji" signature ... 即为原厂构建，未被篡改
```

签名私钥存于 GitHub Secret `SIGNING_KEY`（openssh 格式 ed25519），公钥 `signing-key.pub` 随仓库提交；每次构建用同一把私钥签名，构建后还会用仓库内公钥回验，保证「密钥固定、可校验」。

## 目录

```
index.html            入口（PWA manifest / SW 注册）
css/                  样式（base 设计系统 / 页面 / 记录设置 / 弹层）
js/                   utils · store(localStorage) · parser(中文解析)
                      alarms(响铃引擎) · ui · cards · forms · main
js/views/             today · ovenview · records · settings · detail · sampling
public/               manifest · sw.js · icons
.github/workflows/    构建 + Release(签名) + Pages 部署
```
