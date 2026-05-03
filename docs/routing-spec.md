# Desktop 路由规范

本文档定义 Desktop 应用的完整导航模型。以 mockup 编号为索引，聚焦服务器管理（MANAGE 模式）。Launcher/Settings 当前为占位。

## 导航模型概览

应用采用**两轴导航**：

1. **AppMode（左侧 NavigationRail）**：切换三大模式，每次切换清空返回栈
   - Servers（MANAGE）← **默认模式**，当前重点
   - Launch（LAUNCHER）← 占位，始终可访问
   - Settings ← 占位，始终可访问
2. **NavHost（内容区）**：模式内的页面导航，支持返回栈
3. **Tab 子导航**：Instance Detail 内部的 5 个 tab（Console/Mods/Players/Config/Files）由状态驱动，不是独立路由

### 核心约束

- **NavigationRail 始终可见**，不因配对状态隐藏
- **Servers 是默认模式**，应用启动时自动选中
- 未配对时，Servers 内容区显示 DaemonForm（引导用户配对）
- 已配对且有实例时，**自动选中第一个实例**，避免内容区空态
- 模式切换**保留全部状态**：选中的实例、当前 tab、NavHost 返回栈、未保存表单
- 未保存表单离开时需要 S18 未保存警告保护
- Launcher 和 Settings 与配对状态无关，始终可访问

### 三栏布局

```
┌──────────┬────────────────┬────────────────────────────┐
│ NavRail  │ InstanceList   │ NavHost Content            │
│ (72dp)   │ (220dp)        │ (weight=1f)                │
│          │                │                            │
│ [Servers]│ server cards   │ DaemonForm (未配对)        │
│ [Launch] │ daemon header  │ InstanceDetail /           │
│ [Settings]│ gear menu     │ CreateInstance / ...       │
└──────────┴────────────────┴────────────────────────────┘
```

- NavRail：**始终显示**
- InstanceListPanel：Servers 模式**始终显示**（第二栏），未配对时为空态（"No servers"）
- NavHost：始终显示（第三栏）

---

## Mockup → Screen 映射表

### Servers 未配对状态

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-s01 | 无 Daemon（三栏空态） | Servers 模式 + 未配对：NavRail + 空 InstanceListPanel("No servers") + DaemonForm 内容区 | route | partial（DaemonForm 存在，但 InstanceListPanel 空态和三栏布局未对齐） |

> 注：S01 是完整的三栏布局——NavRail + InstanceListPanel 空态 + DaemonForm 作为内容区，不是全屏配对页面。daemon 断连后无限重试（S20），不存在终态"离线"。

### Instance List（Servers 模式已配对）

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-s02 | 配对成功，无实例 | Servers 模式 + 已配对 + 实例列表为空 → 内容区显示"无实例"空态 | route | done |
| screen-s03 | 多 daemon 分组列表 | Servers 模式 + 已配对 + 有实例 → 自动选中第一个实例，内容区显示 Instance Detail | route | done |

> 注：有实例时自动选中第一个，不出现"已配对但无选中"的中间态。侧边栏 InstanceListPanel 始终显示。

### Instance Detail — 实例状态

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-s04 | 实例已停止 | Instance Detail + state == STOPPED | state | done |
| screen-s05 | 实例启动中 | Instance Detail + state == STARTING | state | done |
| screen-s11 | 实例停止中 | Instance Detail + state == STOPPING | state | done |
| screen-s12 | 实例崩溃 | Instance Detail + state == CRASHED | state | done |
| screen-s13 | 安装中 | Instance Detail + state == INITIALIZING | state（替换整个内容区为 InstallProgressScreen） | partial |

> 注：RUNNING 状态没有独立 mockup，复用当前 tab 内容（Console/Mods/Players/Config/Files）。

### Instance Detail — Tab 子导航

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| — | Console tab | Instance Detail + selectedTab == "console"（默认） | state | done |
| screen-s07 | Players tab | Instance Detail + selectedTab == "players" | state | done |
| screen-s08 | Config tab | Instance Detail + selectedTab == "config" | state | done |
| screen-s09 | Mods tab | Instance Detail + selectedTab == "mods" | state | done |
| screen-s10 | Files tab | Instance Detail + selectedTab == "files" | state | done |
| screen-s26 | Mods 搜索空状态 | Mods tab 内搜索无结果 | inline | done |

> Tab 切换通过 `selectTab()` 状态变更实现，不改变 URL/route。

### Instance Detail — 操作与对话框

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-s17 | 删除确认对话框 | 点击 Delete 按钮 | dialog | done |
| screen-s18 | 未保存更改警告 | 配置 tab 修改后试图离开 | dialog | **missing** |
| screen-s22 | Forget daemon 确认 | 侧边栏 gear → Forget | dialog | **missing**（直接执行，无确认） |

### 创建实例

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-02 / screen-s16 | 创建实例表单 | Instance List → "+ Create Server" 或 Launcher → "+ Create Instance" | route | done |

### Daemon 管理

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-s19-0 | Gear 下拉菜单 | 侧边栏 daemon label 点击齿轮图标，含 Edit 和 Forget 两项 | inline（DropdownMenu） | partial（当前有 Disconnect，需移除） |
| screen-s01 / screen-s19 | Daemon 表单（配对 / 编辑） | 未配对时作为内容区显示；或 Gear → Edit；或 S20 重连 banner → Edit | route | partial（当前是两个独立 screen，需合并） |
| screen-s20 | 重连横幅 | daemon 连接状态 == RECONNECTING，banner 含"Edit"按钮可快速跳转 DaemonForm | state（Instance Detail 内 banner + 灰化内容） | partial（缺 Edit 按钮） |

> 注：S01（配对）和 S19（编辑）合并为同一个 DaemonForm screen——配对码字段仅在新建/re-pair 时显示。
> S21（Daemon 离线）不适用——WebSocket 无限重试，无终态。重连期间用户可通过 banner 上的 Edit 按钮修改 daemon 连接信息。

### 全局

| Mockup | Screen 概念 | 触发条件 | 导航类型 | 状态 |
|--------|------------|---------|---------|------|
| screen-s27-29 | 通知 / Toast | 各种操作结果反馈 | inline（Toast 组件） | done |
| button-matrix | 按钮组件参考 | — | N/A（设计参考，非 screen） | N/A |

### 占位（非当前重点）

| Screen 概念 | 导航类型 | 状态 |
|------------|---------|------|
| Launcher（游戏启动） | route | placeholder（复用 LaunchEmptyScreen） |
| Settings | route | placeholder（"coming soon" 文字） |

---

## 导航流

### 应用入口

```
应用启动 → NavigationRail 始终显示 → 默认 Servers 模式
├── 未配对 → 三栏布局：NavRail + InstanceListPanel 空态("No servers") + DaemonForm（screen-s01）
│   ├── 配对成功 + 有实例 → 自动选中第一个实例，内容区显示 Instance Detail
│   ├── 配对成功 + 无实例 → 内容区显示"无实例"空态（screen-s02）
│   └── 配对失败 → 留在 DaemonForm，显示错误
└── 已配对 + 有实例 → 自动选中第一个实例，内容区显示 Instance Detail
└── 已配对 + 无实例 → 内容区显示"无实例"空态（screen-s02）
```

### Servers 模式导航流

```
S01 无 Daemon（screen-s01）— 未配对时
├── NavRail: 三个 tab 始终显示，Servers 高亮
├── InstanceListPanel: 空态（"No servers" + monitor icon + "Pair Daemon" 链接）
├── 内容区: DaemonForm（配对模式，含配对码字段）
│   ├── Connect → 成功：Instance List（screen-s02 或 s03）
│   └── Connect → 失败：留在 DaemonForm，显示错误
└── InstanceListPanel 底部 "Pair Daemon" → 内容区显示 DaemonForm

Instance List (screen-s02 / s03) — 已配对时的 Servers 内容
├── 有实例 → 自动选中第一个，内容区显示 Instance Detail
├── 无实例 → 内容区显示"无实例"空态（screen-s02）
├── 点击 server card → 切换选中，内容区更新为该实例的 Instance Detail
├── 点击侧边栏 "New Server" 尾卡 → Create Instance (screen-02/s16)
├── 点击内容区 "+ Create Server" → Create Instance (screen-02/s16)
├── 点击 daemon gear → Dropdown Menu (screen-s19-0)
│   ├── Edit → DaemonForm（编辑模式，无配对码字段）
│   └── Forget → 移除 daemon + 清除 session
│       ├── 还有其他 daemon → 切换到下一个 daemon，自动选中其实例
│       └── 无其他 daemon → 内容区回到 DaemonForm（配对模式）
└── 点击 "Pair Daemon" → DaemonForm (screen-s01，配对模式)
    返回栈：完全清空（popUpTo(0) inclusive）

Instance Detail (screen-s04~s14)
├── 是 Servers 模式的默认内容（有实例时自动选中第一个）
├── 无返回按钮——通过侧边栏 server card 切换实例
├── Tab 切换 → Console / Players / Config / Mods / Files（状态驱动，同 route）
├── 状态变化 → 自动切换显示（STOPPED → STARTING → RUNNING → ...）
├── 点击 Start/Stop/Kill/Delete → 状态变更 + 对话框
├── daemon 断连 → 显示 Reconnect Banner (screen-s20) + 灰化内容 + "Edit" 快速入口

Create Instance (screen-02/s16)
├── 覆盖在 Instance Detail 之上的子页面，底部有 Cancel + Create 按钮
├── Create → 成功：返回 Instance Detail，新实例出现在侧边栏列表并自动选中
├── Create → 失败：留在当前页面，显示错误提示
└── Cancel → 返回 Instance Detail（popBackStack）

DaemonForm — 编辑模式（Gear → Edit 或 S20 banner → Edit）
├── 覆盖在 Instance Detail 之上的子页面，底部有 Cancel + Save 按钮
├── 字段：Name、Address、Port（无配对码）
├── Save → 成功：返回上一个页面，daemon 信息更新
├── Save → 失败（如 token 失效）：提示需要重新配对，显示配对码字段
├── Cancel → 返回上一个页面（popBackStack）

DaemonForm — 配对模式（S01 未配对时 / re-pair）
├── 字段：Name、Address、Port、Pairing Code
├── 底部按钮：Connect
├── Connect → 成功：进入 Servers 模式，自动选中实例
└── Connect → 失败：留在当前页面，显示错误
```

### NavigationRail 模式切换

```
任何页面 → 点击 NavigationRail 图标
├── Servers → 恢复之前的 NavHost 状态（选中实例、tab、返回栈）
├── Launch → Launcher placeholder
└── Settings → Settings placeholder
```

> 模式切换保留全部状态（selectedInstanceId、selectedTab、NavHost 返回栈）。不清空返回栈。
> 未保存表单离开时需触发 S18 未保存警告。

---

## 已知 Gap

1. **Forget 确认对话框缺失**（S22）：当前直接执行 forget 操作，无二次确认
2. **未保存更改警告缺失**（S18）：Config tab 离开时无保存提示
3. **Instance List 路由语义模糊**：S02（空状态）和 S03（有实例）共用同一 route，由数据决定显示内容
4. **NavigationRail 可见性未对齐**：当前实现 `!isPaired` 时隐藏 NavigationRail，规范要求始终显示
5. **PairScreen + EditDaemonScreen 需合并**：当前是两个独立 screen，规范要求合并为 DaemonForm（配对码按需显示）
6. **InstanceListPanel 空态未对齐**：S01 要求未配对时也显示 InstanceListPanel（"No servers" 空态），当前未配对时整个第二栏隐藏
7. **Gear menu Disconnect 需移除**：只保留 Edit 和 Forget
8. **Reconnect Banner 缺 Edit 按钮**：S20 重连横幅应提供"Edit"快速入口跳转编辑 daemon
9. **模式切换状态保留未对齐**：当前实现每次切换模式清空 selectedInstanceId 和返回栈，规范要求全部保留
10. **自动选中未实现**：已配对且有实例时，进入 Servers 模式应自动选中第一个实例并显示 Instance Detail；当前实现显示 PairedEmptyScreen（"No servers yet"），语义错误
