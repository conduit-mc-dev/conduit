# UI 视觉对齐审计报告

> 审计日期：2026-05-04
> 审计范围：全部 26 个 mockup 页面 vs Kotlin/Compose Desktop 实现
> 审计方法：4 个并行 agent 逐页对比 HTML mockup 与实现代码

---

## 一、跨页面通用问题（影响所有/多个页面）

这些源于共享组件，修复一个地方可以解决大量页面的问题。

### 1. ButtonPrimary 颜色错误 [严重]
- **Mockup**: `#238636`（绿色）用于所有 Start/Stop/Save/Create 按钮
- **实现**: `#58A6FF`（蓝色）
- **影响**: 所有页面的主按钮颜色都不对
- **文件**: `Color.kt:42` `ButtonPrimary`

### 2. ButtonPrimaryText 颜色错误 [严重]
- **Mockup**: `#0D1117`（深色文字，蓝底上的对比更好）
- **实现**: `#FFFFFF`（白色）
- **影响**: 所有主按钮的文字颜色
- **文件**: `Color.kt:43` `ButtonPrimaryText`

### 3. ButtonSecondary 背景色偏差 [中等]
- **Mockup**: `#21262D`（Border 色）
- **实现**: `#30363D`（Elevated 色）
- **影响**: 所有次要/ghost 按钮
- **文件**: `Color.kt:44` `ButtonSecondary`

### 4. 按钮圆角不一致 [中等]
- **Mockup header 按钮**: `border-radius: 6px`
- **Mockup dialog 按钮**: `border-radius: 10px`
- **实现**: 统一 `RoundedCornerShape(8.dp)`
- **文件**: `ActionButton.kt:25`

### 5. 按钮字号不一致 [低]
- **Mockup header 按钮**: `11px`
- **Mockup dialog 按钮**: `13px`
- **实现**: 统一 `bodySmall`（12sp）
- **文件**: `ActionButton.kt`

### 6. TabBar 下划线宽度错误 [中等]
- **Mockup**: 下划线铺满整个 tab 宽度
- **实现**: 固定 `40.dp` 宽的 Box
- **影响**: 所有使用 TabBar 的页面
- **文件**: `TabBar.kt:50`

### 7. TabBar 缺少底部分割线 [低]
- **Mockup**: `border-bottom: 1px solid #21262d`
- **实现**: 无
- **文件**: `TabBar.kt`

### 8. TabBar 项 padding 偏差 [低]
- **Mockup**: `padding: 8px 16px`
- **实现**: `padding(horizontal = 12.dp, vertical = 10.dp)`
- **文件**: `TabBar.kt:37`

### 9. ContentHeader 名称字号偏大 [中等]
- **Mockup**: `18px / weight 800`
- **实现**: `headlineMedium`（~28sp / 800）
- **文件**: `ContentHeader.kt:33`

### 10. Disabled 按钮颜色偏差 [低]
- **Mockup bg**: `#21262d`，**文字**: `#484f58`
- **实现 bg**: `#161B22`，**文字**: `#30363D`
- **文件**: `Color.kt:48-49`

---

## 二、Daemon 生命周期页面

### S01 — No Daemon (Pair 屏幕)
- ❌ Pair 表单标题: "Pair Daemon" vs mockup "Connect to Daemon"
- ❌ 缺少副标题 "Manage remote servers"
- ❌ 表单宽度: 440dp vs 340px
- ❌ 缺少 48x48 monitor 图标容器
- ❌ Address placeholder: "192.168.1.100" vs "192.168.1.100 or domain.com"
- ❌ 多了 Port 标签（mockup 无标签，inline 在地址字段旁）
- ❌ 缺少 Daemon Name 提示文字
- ❌ Pairing code 提示文字过短

### S02 — Paired Empty
- ❌ 内容区域显示 LaunchEmptyScreen（"Ready to Play"），应显示 "No servers yet"
- ❌ 空状态图标尺寸/圆角错误
- ❌ 按钮文字 "+ Create Instance" vs "+ Create Server"
- ❌ 多余的 quick-start chips（仅 Screen-01 有）

### S03 — Multi Daemon
- ❌ Daemon label 位置：status 在 name 右边 4dp，mockup 是 push 到最右
- ❌ Console 输出区域缺少边框容器（mockup 有 bordered box）
- ❌ Console 命令 placeholder: "Type command..." vs "Type a command..."
- ❌ Daemon label 不是 sticky 定位

### S20 — Reconnecting
- ❌ 完全缺少 reconnect banner（旋转圈 + "Reconnecting to..." + 重试计数）
- ❌ 实现是全屏居中状态，应保持 header + tabs + stale content 布局
- ❌ 标题字号/字重错误（18sp/800 vs 16sp/700）
- ❌ 缺少 disabled 按钮组

### S21 — Daemon Offline
- ❌ 图标错误：CloudOff 48dp vs wifi-off 32dp
- ❌ 缺少 64x64 图标容器
- ❌ 标题颜色: TextPrimary vs TextSecondary
- ❌ Retry 按钮样式: Primary(蓝) vs #21262d(灰)
- ❌ Edit 按钮样式/颜色错误
- ❌ 按钮文字: "Retry"/"Edit" vs "Retry Connection"/"Edit Address"

### S22 — Forget Daemon 对话框
- ❌ 缺少背景遮罩（Material3 AlertDialog 在 Desktop 上无 scrim）
- ❌ 对话框宽度: 默认 ~560dp vs 380px
- ❌ 缺少 40x40 红色图标容器
- ❌ 缺少 daemon 参考卡片（名称 + 地址 + 服务器数）
- ❌ 警告文字缺少背景框样式
- ❌ Body 文字内容不完整
- ❌ Forget 按钮: OutlinedButton(红边框) vs 填充红色 `#F85149`

---

## 三、实例状态页面

### S04 — Stopped
- ❌ Delete 按钮: OutlinedButton vs 填充 `#30363d` + 红文字
- ❌ Console 空状态: 无图标，应有 monitor 图标 + "Server is stopped" 文字
- ❌ Console 占位文字差异

### S05 — Starting
- ❌ Header 状态文字: "Starting" vs "Starting..."（缺少省略号）
- ❌ 按钮组: 实现有 Stop(disabled) + Kill(disabled) + Cancel，mockup 只有 Stop(disabled) + Delete(disabled)
- ❌ Console 没有紫色 "Starting..." 结束指示器

### S11 — Stopping
- ❌ Header 状态文字: "Stopping" vs "Stopping..."（缺少省略号）
- ❌ 按钮组多了 Cancel 按钮（mockup 无）
- ❌ Console 没有橙色 "Stopping..." 结束指示器
- ❌ Mod count 未显示在 tab 标签

### S12 — Crashed
- ✅ Crash banner 已实现（颜色、图标匹配）
- ✅ 选中边框红色 tint 正确
- ❌ Header 缺少 crash reason 行内显示
- ❌ Delete 按钮: OutlinedButton vs 填充 `#30363d` + 红文字
- ❌ Console 输入 placeholder: 通用 vs "Server crashed -- restart to continue"

### S13 — Installing
- ❌ InstallProgressScreen 是全屏替代，应保持 tab 布局
- ❌ 进度数据全部硬编码（"0%"、"Installing Server"）
- ❌ 缺少 5 步任务清单
- ❌ 缺少实时任务详情（速度、已下载/总量）
- ❌ Cancel 按钮颜色: Warning(黄) vs `#30363d` + 红文字

### S14 — Downloading
- ❌ InstanceState 枚举没有 DOWNLOADING 状态
- ❌ 所有颜色主题错误（应用蓝色系，实际用琥珀色系）
- ❌ 缺少重试 banner
- ❌ 与 S13 相同的结构性问题

---

## 四、实例标签页

### S07 — Players
- ❌ Stats 布局顺序颠倒（label 在前 vs number 在前）
- ❌ Stats 卡片间距: 32dp vs 12dp
- ❌ 缺少 "ONLINE NOW" 区域标签
- ❌ 玩家缺少连接时间详情
- ❌ 头像: 圆形 vs 圆角方形，32dp vs 36dp
- ❌ 头像样式: 半透明纯色 vs 渐变

### S08 — Config
- ❌ 未编辑的 value 颜色: TextPrimary vs TextSecondary
- ❌ Edit 按钮缺少 `#21262d` 背景
- ❌ Edit 按钮尺寸: 24dp vs 28px
- ❌ 新 value 字号/字重: 12sp vs 13px/600

### S09 — Mods (已部分修复)
- ✅ Filter 下划线样式、badge、彩色图标、上下文菜单已实现
- ❌ Toolbar 有 Surface 背景 + 全边框，应为透明 + 仅底边框
- ❌ Inactive filter tab 有 1dp Border 下划线，应为透明
- ❌ Filter tabs 有显式 Background，应为透明
- ❌ 图标是通用 Extension icon，mockup 用 emoji per mod
- ❌ Enabled 图标缺少 1px 边框
- ❌ 缺少 "Update available" badge 变体
- ❌ Mod detail 缺少 category 字段

### S10 — Files
- ❌ 文件夹名颜色: TextPrimary vs AccentBlue
- ❌ 缺少文件夹项目数量
- ❌ 文件大小颜色: TextMuted vs TextSecondary
- ❌ Protected badge 无背景样式
- ❌ 缺少每行三点菜单
- ❌ Toolbar 缺少底部分割线

---

## 五、对话框/杂项

### Screen-02 / S16 — Create Instance
- ❌ 标题: "Create New Server" vs "Create Instance" / "Create Server"
- ❌ 缺少副标题
- ❌ Header 图标缺少容器
- ❌ **完全缺少版本快速选择 chips**
- ❌ **完全缺少模组加载器 tabs**（NeoForge/Fabric/Quilt/Forge/Vanilla）
- ❌ 缺少 Max Players 字段
- ❌ 缺少 section 分割线

### S17 — Delete Confirm 对话框
- ❌ 对话框宽度/样式同 S22 问题
- ❌ 缺少图标容器
- ❌ 缺少服务器参考卡片
- ❌ 警告框缺少背景样式
- ❌ 描述文字不完整

### S18 — Unsaved Warning 对话框
- ❌ 按钮顺序不匹配（Discard | Cancel | Save vs 实现布局）
- ❌ Discard 按钮样式: `#21262d` + 红文字 vs OutlinedButton
- ❌ 缺少变更列表卡片

### S19-0 — Gear Menu
- ❌ **完全未实现**：daemon 齿轮图标下拉菜单（Edit/Disconnect/Forget）

### S19 — Edit Daemon
- ❌ 表单宽度: 440dp vs 400px
- ❌ 缺少 daemon 状态指示器
- ❌ Address+Port 应为并排布局

### S26 — Search Empty
- ❌ 缺少搜索无结果的空状态视图
- ❌ 搜索框缺少 focus 高亮边框

### S27-29 — Notifications (Toast)
- ❌ Toast 文字不按类型着色（始终白色 vs 绿/红/黄）
- ❌ 动画方向: slideInVertically vs slideInFromRight
- ❌ 右侧 padding: 24dp vs 16px

---

## 六、优先级排序建议

### P0 — 全局性、高影响（修复后大面积改善）
1. `ButtonPrimary` 颜色 → `#238636`，`ButtonPrimaryText` → `#0D1117`
2. `ButtonSecondary` 背景 → `#21262D`
3. `ContentHeader` 名称字号 → 18sp
4. `TabBar` 下划线宽度 → 动态铺满 + 底边框
5. 按钮圆角 → header 6dp / dialog 10dp（或统一决策）

### P1 — 重要页面差异
6. S02 空状态内容（paired empty 应独立页面）
7. S20 Reconnect banner
8. S21 Offline 按钮样式/图标
9. S13/S14 InstallProgressScreen 重写
10. CreateInstanceScreen 版本选择 + 加载器 tabs
11. 所有对话框自定义样式（宽度、图标容器、参考卡片）

### P2 — 中等差异
12. Disabled 按钮颜色
13. Players tab stats 布局
14. Files tab 文件夹颜色/详情
15. Config tab value 颜色
16. Gear dropdown menu 实现
17. Toast 文字着色

### P3 — 微小差异（polish pass）
18. 按钮字号/间距微调
19. Tab padding
20. Console placeholder 文字
21. 状态文字省略号 ("Starting...")
22. 文件圆角/间距
23. 搜索框 focus 高亮
