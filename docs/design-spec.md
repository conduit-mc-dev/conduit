# Desktop 视觉设计规范

> 本文档定义 Desktop 应用的视觉设计规格。
> 导航模型、路由映射、核心约束 → 见 `docs/routing-spec.md`
> 视觉效果确认 → 见 `docs/desktop-mockups/`

---

## 色彩体系

### Surface 色

| Token | 色值 | 用途 |
|-------|------|------|
| Background | `#0d1117` | 页面底色、NavigationRail、InstanceListPanel |
| Surface | `#161b22` | Header、Tab 栏、搜索框背景 |
| Border | `#21262d` | 分隔线、未选中边框、输入框边框 |
| Elevated | `#30363d` | Hover 背景、次操作按钮背景 |

### Text 色

| Token | 色值 | 用途 |
|-------|------|------|
| Primary | `#e6edf3` | 主文字、标题 |
| Secondary | `#8b949e` | 次要信息、卡片行 2 |
| Muted | `#484f58` | 时间戳、灰色圆点、禁用文字 |

### 按钮色

**Header 操作按钮（Style E — 透明底 + 状态色描边）**：仅用于实例详情页 Header 右侧按钮组

| 按钮类型 | 背景 | 文字 | 描边 | 用途 |
|---------|------|------|------|------|
| Start | transparent | `#3fb950` | `rgba(63,185,80,0.35)` | Start |
| Stop | transparent | `#e6edf3` | `rgba(48,54,61,0.6)` | Stop |
| Kill | transparent | `#8b949e` | `rgba(33,38,45,0.6)` | Kill |
| Delete | transparent | `#f85149` | `rgba(248,81,73,0.25)` | Delete |
| Cancel | transparent | `#d29922` | `rgba(210,153,34,0.25)` | Cancel |
| Disabled | transparent | `#484f58` | `rgba(33,38,45,0.4)` | 不可用状态 |

**表单 / 对话框按钮（实底）**：用于 Create/Save/Connect 表单提交、对话框确认

| 按钮类型 | 背景 | 文字 | 用途 |
|---------|------|------|------|
| Primary | `#58a6ff` | `#0d1117` | Create / Save / Connect |
| Secondary | `#21262d` | `#e6edf3` | Cancel / Revert |
| Danger | `#da3633` | `#fff` | Delete / Forget / Discard |

### NavigationRail 色

| Token | 色值 | 用途 |
|-------|------|------|
| 背景 | `#0d1117` | 与 Background 统一（GitHub Dark canvas） |
| 选中背景 | `rgba(88,166,255,0.12)` | 选中项 pill |
| 选中图标/文字 | `#58a6ff` | 选中项颜色 |
| 未选中 | `#6e7681` | 未选中项图标 |
| 边框 | `#21262d` | 右侧分隔线 |

---

## 布局

### 三栏布局

```
┌──────────┬────────────────┬────────────────────────────┐
│ NavRail  │ InstanceList   │ NavHost Content            │
│ (72dp)   │ (220dp)        │ (weight=1f)                │
└──────────┴────────────────┴────────────────────────────┘
```

- **NavigationRail**：始终可见，不因配对状态隐藏
- **InstanceListPanel**：Servers 模式始终显示。未配对时为空态（"No servers" + monitor icon + "Pair Daemon" 链接）
- **NavHost**：始终显示

---

## 组件规格

### NavigationRail

- 宽度：72dp
- 背景：`#0d1117`
- 右侧边框：`#21262d`
- Logo：40x40dp，圆角 12dp，conduit 核心 SVG 图标（品牌色 `#0B3D4E` + `#2BCEE9` + `#E9FBFF`）
- 导航项：56x48dp，圆角 14dp
- 选中态：pill 背景 `rgba(88,166,255,0.12)`，图标 `#58a6ff`，下方文字标签（9px，bold）
- 未选中态：图标 `#6e7681`，无文字标签
- Hover：`rgba(255,255,255,0.05)`
- 导航项：Servers / Launch / Settings

### 搜索栏

- 位置：InstanceListPanel 顶部，固定
- 背景：`#161b22`
- 边框：`#21262d`，圆角 6dp
- 内边距：8px 10px
- 搜索图标：14dp
- placeholder："Search servers..."
- 有值时显示 x 清除按钮（`#484f58`，hover `#e6edf3`）
- 聚焦状态：边框 → `rgba(88,166,255,0.3)`，搜索图标 → `#58a6ff`

### Daemon 分组标签

- 位置：sticky top，始终可见（即使只有 1 个 Daemon）
- 背景：`#0d1117`（随面板背景）
- 内容：StatusDot + 名称（大写，10px，bold）+ 状态文字 + 齿轮图标
- 齿轮菜单（DropdownMenu）：
  - Edit → 打开 DaemonForm 编辑模式
  - Forget → 永久移除（红色，需确认弹窗 S22）

### 实例卡片

- 高度：58dp（固定）
- 圆角：10dp
- 内边距：10dp top/bottom，14dp left/right
- 选中态边框：`rgba(88,166,255,0.4)`
- 未选中态边框：`#21262d`
- Hover：`#30363d`
- 两行内容：
  - 行 1：名称（12px，selected=bold 700 / unselected=600）+ StatusDot（右侧）
  - 行 2：版本/玩家信息（10px，`#8b949e` 或状态对应色）
- 底部边缘进度条：
  - 高度：3dp
  - 位置：absolute bottom 0
  - 颜色：纯状态色（Installing `#d29922`、Downloading `#58a6ff`），无渐变
  - Running / Stopped / Crashed：无进度条

### New Server 尾卡

- 高度：40dp
- 样式：1.5px dashed `#21262d`，圆角 10dp
- 内容：+ 图标 + "New Server" 文字
- 位置：每个 Daemon 分组实例列表末尾，随列表滚动
- Hover：`#30363d` 边框，`rgba(255,255,255,0.02)` 背景

### Pair Daemon 链接

- 样式：文字链接
- 位置：InstanceListPanel 底部固定（border-top 分隔）
- 内容：+ 图标 + "Pair Daemon"
- 颜色：`#58a6ff`

### ActionButton — 按钮状态矩阵

| 实例状态 | 主操作（Primary） | 次操作（Secondary） | 危险（Danger） | 取消（Warning） |
|---------|------------------|--------------------|--------------|-----------------|
| Stopped | Start | — | Delete | — |
| Starting | — | — | — | Cancel |
| Running | Stop | Kill | — | — |
| Stopping | — | Kill | — | — |
| Crashed | Start | Kill | Delete | — |
| Installing | — | — | — | Cancel |
| Downloading | — | — | — | Cancel |

布局：主操作最左，Kill 居中，Delete/Cancel 最右

### Tab 栏

- 背景：`#161b22`
- 选中态：`#58a6ff` 文字 + 2px `#58a6ff` 底部指示线
- 未选中态：`#8b949e` 文字
- Hover：`#e6edf3`
- Tab 列表：Console / Mods (42) / Players / Config / Files
- 计数标签：Mods 和 Players 显示数量
- 无实例选中时整个 Tab 栏隐藏

### StatusDot 组件

- Small（卡片）：6dp
- Medium（标题栏）：8dp
- Large（详情页）：10dp
- 光晕：仅 Running 和 Daemon Online 状态有，`box-shadow 0 0 6dp`，透明度 40%
- 呼吸动画：仅 STARTING / STOPPING / INITIALIZING

### ConduitCard 进度条

- 位置：底部全宽，紧贴卡片边缘
- 高度：3dp
- 颜色：纯状态色（Installing `#d29922`、Downloading `#58a6ff`、Done `#3fb950`）
- 无圆角、无渐变

### Toast 通知

- 位置：右下角，底部 48px
- 自动消失：3 秒
- 可手动关闭：x 按钮
- 入场动画：0.3s slideIn
- 成功：绿色边框 + checkmark 图标
- 错误：红色边框 + cross 图标
- 警告：橙色边框 + warning 图标

---

## 状态视觉系统

### 实例状态色

| 状态 | 颜色 | 动画 | 说明 |
|------|------|------|------|
| Running | `#3fb950` | 光晕 `box-shadow 0 0 6px` | 绿色脉冲 |
| Stopped | `#484f58` | 无 | 灰色静态 |
| Starting | `#a371f7` | 呼吸 1500ms | 紫色呼吸 |
| Stopping | `#f0883e` | 呼吸 1500ms | 深橙呼吸 |
| Crashed | `#f85149` | 无 | 红色静态 |
| Installing | `#d29922` | 呼吸 1500ms | 橙色呼吸 |
| Downloading | `#58a6ff` | 呼吸 1500ms | 蓝色呼吸 |

### Daemon 状态色

| 状态 | 颜色 | 动画 | 说明 |
|------|------|------|------|
| Online | `#3fb950` | 光晕 | WebSocket 已连接 |
| Reconnecting | `#d29922` | 呼吸 1500ms | WS 断开重连中（无限重试，无终态离线） |

### Daemon 状态行为

| 状态 | 实例卡片 | 操作按钮 | New Server | 内容区 |
|------|---------|---------|------------|--------|
| Online | 正常显示 | 正常可用 | 可用 | 正常 |
| Reconnecting | 半透明 0.55 | 全禁用 | 禁用（不可点） | 灰化 + Reconnect Banner |

### 动画规范

- 呼吸动画：1500ms，`0.3 ↔ 1.0` alpha，EaseInOut
- Tab 切换：200ms fade
- 导航选中：M3 内置 200ms

---

## Tab 内容区视觉

### Console Tab

- 背景：`#0d1117`
- 输出区：等宽字体（JetBrains Mono），12px，`#8b949e`，line-height 1.7
- 日志着色：时间戳 `#484f58`，Done `#3fb950`，玩家 `#58a6ff`，警告 `#d29922`，错误 `#f85149`
- 自动滚动到底部
- 命令输入：固定在内容区底部，搜索框样式 + Send 按钮（`#58a6ff`）

### Players Tab

- 顶部统计卡片：在线数（绿色大字）+ 最大玩家数（灰色大字），并排
- 玩家卡片：彩色头像首字母 + 名称 + 连接时间 + 绿色在线圆点

### Config Tab

- 工具栏：未保存数量（橙色）+ Revert All + Save
- 搜索过滤框
- 属性列表：每行 key-value，编辑过的行蓝边框 + 删除线旧值 → 蓝色新值
- 未编辑行：灰色值
- 每行右侧铅笔编辑图标

### Mods Tab

- 工具栏：搜索 + Install from Modrinth + Upload .jar
- 过滤 Tab：All / Enabled / Disabled
- 模组卡片：彩色图标 + 名称 + 类型/版本/来源 + 状态标签
- Disabled 模组：卡片半透明

### Files Tab

- 面包屑导航 + Upload + New Folder
- 文件/文件夹混排
- 受保护文件：半透明 + 红色 Protected 标签

---

## 页面级视觉

### DaemonForm（配对 / 编辑）

S01（配对）和 S19（编辑）合并为单一 DaemonForm。配对码字段仅在新建 / re-pair 时显示。

**配对模式**（未配对时 / re-pair after 401）：
- Server Address：IP/域名 + 端口（默认 8080），两个输入框
- Daemon Name：显示名称（如 "Home VPS"）
- Pairing Code：XXXX-XXXX-XXXX 格式
- Connect 按钮
- 提示："Pairing code shown in Daemon console"

**编辑模式**（Gear → Edit / S20 banner → Edit）：
- Name + Address + Port
- 无配对码字段
- 提示：改地址时先用现有 token 连接，401 才弹配对码

### 首次启动

默认进 Servers 模式。未配对时三栏布局：NavRail + InstanceListPanel 空态（"No servers"）+ DaemonForm 作为内容区引导配对。

### Reconnect Banner (S20)

- 触发：daemon 连接状态 == RECONNECTING
- 样式：`rgba(210,153,34,0.1)` 底色 + `rgba(210,153,34,0.4)` 边框
- 圆角：6dp
- 内容：重连提示文字 + **Edit 按钮**（跳转 DaemonForm 编辑模式，快速修改 daemon 连接信息）
- 内容区灰化（半透明 0.55）

### Crash Banner (S12)

- 样式：`rgba(248,81,73,0.1)` 底色 + `rgba(248,81,73,0.3)` 边框
- 圆角：6dp
- 内边距：10px 16px

### 弹窗

**删除实例确认（S17）**：
- 红色垃圾桶图标 + "Delete Server"
- 服务器信息卡片
- 红色警告："All server data will be permanently deleted"
- Cancel / Delete Server

**未保存配置离开（S18）**：
- 橙色感叹号图标 + "Unsaved Changes"
- 变更列表（key: old → new）
- Discard（红色） / Cancel / Save & Leave（蓝色）

**Forget Daemon（S22）**：
- 红色垃圾桶图标 + "Forget Daemon"
- Daemon 信息卡片
- 红色警告："Server data on the daemon is NOT affected"
- Cancel / Forget Daemon

### 安装/下载进度页（S13/S14）

- 无 Tab 栏（Console 等不可用）
- 主任务卡片：任务名 + 大字百分比 + 粗进度条（8dp）+ 速度/大小
- 步骤列表：checkmark 已完成（绿色）→ 进行中（状态色高亮）→ 待执行（灰色）
- 重试 Banner（S14）：橙色 "Retry 2/3"
- Cancel 按钮

---

## GitHub Dark 对齐规范

所有组件对齐 GitHub Dark 设计语言。StatusDot 保留自定义设计（带 glow）。

### 圆角

- 卡片：10dp
- 按钮 / Alert / 搜索框：6dp
- 输入框：6dp

### 字号

- bodySmall：12sp（按钮文字、卡片信息）
- labelSmall：12sp（状态文字）
- bodyMedium：14sp（正文段落）
- titleSmall：13sp（卡片标题）
