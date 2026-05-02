# Desktop UI 重设计 Spec

> 创建于 2026-05-03
> 状态：设计已确认
> Mockup 目录：`.superpowers/brainstorm/42236-1777748496/content/`

## 概述

在现有三栏布局基础上进行 Material 3 升级，强化状态视觉，引入 Minecraft 游戏/科技风格。保留现有导航架构（NavController、Routes），改动集中在 UI 层。

## 设计决策总览

| 决策 | 内容 |
|------|------|
| 布局 | 三栏保留：NavigationRail 72px + 实例列表 220px + 弹性内容区 |
| 导航 | M3 NavigationRail 替代手绘 Sidebar |
| 卡片 | 58dp 固定高度，E 方案底部边缘进度条（3px） |
| 分组 | 按 Daemon，sticky 标签，始终显示（不分单/多 Daemon） |
| 尾卡 | New Server 40dp 虚线框 + Pair Daemon 文字链接固定底部 |
| 刷新 | 去掉（WS 实时更新覆盖） |
| 详情 | Tab 子导航（Console/Mods/Players/Config/Files） |
| 按钮 | 无 Restart，每状态最多 3 个按钮 |
| 首次启动 | 默认进 Launch tab，无需配对 |

## 第一栏：NavigationRail

- 宽度：72px
- 组件：M3 `NavigationRail`
- 背景：渐变 `#1a1a2e → #16213e`
- 边框：`rgba(88,166,255,0.12)` 右侧分隔线
- Logo：40×40px，圆角 12px，渐变 `#58a6ff → #a371f7`
- 导航项：56×48px，圆角 14px
- 选中态：pill 背景 `rgba(88,166,255,0.12)`，图标 `#58a6ff`，下方文字标签（9px，bold）
- 未选中态：图标 `#6e7681`，无文字标签
- Hover：`rgba(255,255,255,0.05)`
- 导航项：Servers / Launch / Settings
- 未配对时隐藏

## 第二栏：实例列表面板

- 宽度：220px
- 背景：`#0d1117`
- 边框：`#21262d` 右侧分隔线

### 搜索栏
- 位置：固定顶部，独立一行
- 样式：`#161b22` 背景，`#21262d` 边框，圆角 8px，内边距 8px 10px
- 搜索图标 + placeholder "Search servers..."
- 搜索时有值时显示 × 清除按钮

### Daemon 分组标签
- 位置：sticky top，始终可见（即使只有 1 个 Daemon）
- 背景：`#0d1117`（随面板背景）
- 内容：状态圆点 + 名称（大写，10px，bold）+ 状态文字 + 齿轮图标
- 齿轮菜单（点击展开）：
  - Edit → 打开编辑 Daemon 页面（S19）
  - Disconnect → 断开连接
  - Forget → 永久移除（红色，需确认弹窗 S22）

### 实例卡片
- 高度：58dp（固定）
- 圆角：10dp
- 内边距：10dp top/bottom，14dp left/right
- 选中态边框：`rgba(88,166,255,0.4)`
- 未选中态边框：`#21262d`
- Hover：`#30363d`
- 两行内容：
  - 行 1：名称（12px，selected=bold 700 / unselected=600）+ 状态圆点（右侧）
  - 行 2：版本/玩家信息（10px，`#8b949e` 或状态对应色）
- 底部边缘进度条（E 方案）：
  - 高度：3px
  - 位置：absolute bottom 0
  - 圆角：跟随卡片底部圆角
  - Installing 渐变：`#d29922 → #f0b232`
  - Downloading 渐变：`#58a6ff → #a371f7`
  - Running / Stopped / Crashed：无进度条

### New Server 尾卡
- 高度：40dp
- 样式：1.5px dashed `#21262d`，圆角 10dp
- 内容：+ 图标 + "New Server" 文字
- 位置：每个 Daemon 分组实例列表末尾，随列表滚动
- Hover：`#30363d` 边框，`rgba(255,255,255,0.02)` 背景

### Pair Daemon
- 样式：文字链接
- 位置：面板底部固定（border-top 分隔）
- 内容：+ 图标 + "Pair Daemon"
- 颜色：`#58a6ff`

## 第三栏：内容区

### Header（固定）
- 背景：`#161b22`
- 左侧：实例名（18px，bold 800）+ StatusDot + 状态文字
- 右侧：操作按钮

### 按钮状态矩阵

| 状态 | 主操作（绿色） | 次操作（灰色） | 危险（红色描边） | 取消（黄色） |
|------|---------------|---------------|-----------------|-------------|
| Stopped | Start | — | Delete | — |
| Starting | — | — | — | Cancel |
| Running | Stop | Kill | — | — |
| Stopping | — | Kill | — | — |
| Crashed | Start | Kill | Delete | — |
| Installing | — | — | — | Cancel |
| Downloading | — | — | — | Cancel |

按钮样式：
- 主操作（Primary）：`#238636` 填充，白色文字
- 次操作（Secondary）：`#30363d` 填充，`#e6edf3` 文字
- 危险（Danger）：透明背景，`#f85149` 描边+文字
- 警告（Warning）：`#9e6a03` 填充，白色文字
- 禁用（Disabled）：`#161b22` 背景，`#30363d` 文字
- 布局：主操作最左，Kill 居中，Delete/Cancel 最右

### Tab 栏（固定）
- 背景：`#161b22`
- 选中态：`#58a6ff` 文字 + 2px `#58a6ff` 底部指示线
- 未选中态：`#8b949e` 文字
- Hover：`#e6edf3`
- Tab 列表：Console / Mods (42) / Players / Config / Files
- 计数标签：Mods 和 Players 显示数量
- 无实例选中时整个 Tab 栏隐藏

### Tab 内容区（独立滚动）
- 背景：`#0d1117`

#### Console Tab
- 输出区：等宽字体（JetBrains Mono），12px，`#8b949e`，line-height 1.7
- 日志着色：时间戳 `#484f58`，Done `#3fb950`，玩家 `#58a6ff`，警告 `#d29922`，错误 `#f85149`
- 自动滚动到底部
- 命令输入：固定在内容区底部，搜索框样式 + Send 按钮（`#58a6ff`）

#### Players Tab
- 顶部统计卡片：在线数（绿色大字）+ 最大玩家数（灰色大字），并排
- 玩家卡片：彩色头像首字母 + 名称 + 连接时间 + 绿色在线圆点

#### Config Tab
- 工具栏：未保存数量（橙色）+ Revert All + Save
- 搜索过滤框
- 属性列表：每行 key-value，编辑过的行蓝边框 + 删除线旧值 → 蓝色新值
- 未编辑行：灰色值
- 每行右侧铅笔编辑图标

#### Mods Tab
- 工具栏：搜索 + Install from Modrinth + Upload .jar
- 过滤 Tab：All / Enabled / Disabled
- 模组卡片：彩色图标 + 名称 + 类型/版本/来源 + 状态标签
- Disabled 模组：卡片半透明

#### Files Tab
- 面包屑导航 + Upload + New Folder
- 文件/文件夹混排
- 受保护文件：半透明 + 红色 Protected 标签

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
| Reconnecting | `#d29922` | 呼吸 1500ms | WS 断开重连中 |
| Offline | `#484f58` | 无 | 不可达 |

### Daemon 状态行为

| 状态 | 实例卡片 | 操作按钮 | New Server | 内容区 |
|------|---------|---------|------------|--------|
| Online | 正常显示 | 正常可用 | 可用 | 正常 |
| Reconnecting | 半透明 0.55 | 全禁用 | 禁用（不可点） | "Connection lost" |
| Offline | 半透明 0.4 | 全禁用 | 禁用（不可点） | "Daemon Unreachable" + Retry/Edit |

### StatusDot 组件
- Small（卡片）：6dp
- Medium（标题栏）：8dp
- Large（详情页）：10dp
- 光晕：仅 Running 和 Daemon Online 状态有，`box-shadow 0 0 6dp`，透明度 40%

### 动画规范
- 呼吸动画：1500ms，`0.3 ↔ 1.0` alpha，EaseInOut
- Tab 切换：200ms fade
- 导航选中：M3 内置 200ms

## 色彩体系

### Surface 色
- Background：`#0d1117`
- Surface：`#161b22`
- Border：`#21262d`
- Elevated：`#30363d`

### Text 色
- Primary：`#e6edf3`
- Secondary：`#8b949e`
- Muted：`#484f58`

### NavigationRail 色
- 背景渐变：`#1a1a2e → #16213e`
- 选中背景：`rgba(88,166,255,0.12)`
- 选中图标/文字：`#58a6ff`
- 未选中：`#6e7681`
- 边框：`rgba(88,166,255,0.12)`

## 首次启动流程

1. 首次打开 → 默认进 Launch tab（无 NavigationRail 隐藏逻辑）
2. Launch tab 空态：Create Instance + Connect Server 按钮 + 快捷版本芯片
3. 用户点 Servers tab → 三栏布局 + 配对表单（无 Daemon 时）
4. 配对成功 → 列表面板出现 Daemon 标签 + New Server 尾卡

## 配对表单（S01）

- Server Address：IP/域名 + 端口（默认 8080），两个输入框
- Daemon Name：显示名称（如 "Home VPS"）
- Pairing Code：XXXX-XXXX-XXXX 格式
- Connect 按钮
- 提示："Pairing code shown in Daemon console"

## 编辑 Daemon（S19）

- 从齿轮菜单 Edit 打开
- 复用连接表单：Name + Address + Port
- 无配对码字段
- 提示：改地址时先用现有 token 连接，401 才弹配对码

## 弹窗

### 删除实例确认（S17）
- 红色垃圾桶图标 + "Delete Server"
- 服务器信息卡片
- 红色警告："All server data will be permanently deleted"
- Cancel / Delete Server

### 未保存配置离开（S18）
- 橙色感叹号图标 + "Unsaved Changes"
- 变更列表（key: old → new）
- Discard（红色） / Cancel / Save & Leave（蓝色）

### Forget Daemon（S22）
- 红色垃圾桶图标 + "Forget Daemon"
- Daemon 信息卡片
- 红色警告："Server data on the daemon is NOT affected"
- Cancel / Forget Daemon

## Toast 通知

- 位置：右下角，底部 48px
- 自动消失：3 秒
- 可手动关闭：× 按钮
- 入场动画：0.3s slideIn
- 成功：绿色边框 + ✓ 图标
- 错误：红色边框 + ✗ 图标
- 警告：橙色边框 + ⚠ 图标

## 安装/下载进度页（S13/S14）

- 无 Tab 栏（Console 等不可用）
- 主任务卡片：任务名 + 大字百分比 + 粗进度条（8px）+ 速度/大小
- 步骤列表：✓ 已完成（绿色）→ ⟳ 进行中（状态色高亮）→ ○ 待执行（灰色）
- 重试 Banner（S14）：橙色 "Retry 2/3"
- Cancel 按钮

## 实现路径

1. **Design Token 文件** — Color.kt / Shape.kt / Spacing.kt（新）/ Animation.kt（新）
2. **共享组件** — StatusDot / ConduitCard / ConduitProgressBar / NavigationRail
3. **Main.kt 重构** — NavigationRail + InstanceListPanel + Tab 容器
4. **逐屏实现** — 按 S01-S29 顺序，每屏截图对比 mockup
