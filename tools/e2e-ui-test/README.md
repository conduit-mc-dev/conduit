# Conduit Desktop — E2E UI 自动化验证

用 Python + Claude Vision 自动验证 Desktop 应用 UI 是否与设计稿一致。

## 原理

```
┌─────────────────────────────────────────────────┐
│  Python (本框架)                                │
│                                                 │
│  1. 启动 Desktop 应用 (subprocess → gradlew)    │
│  2. 全屏截图 (Pillow.ImageGrab)                 │
│  3. 截图 → base64 → Claude Vision API          │
│  4. Vision 返回 UI 元素坐标                     │
│  5. pynput 模拟鼠标点击                         │
│  6. 重复 2-5 导航到目标页面                     │
│  7. 截图 → 与设计稿截图对比                     │
│  8. 输出结构化报告                              │
└─────────────────────────────────────────────────┘
```

**核心思路**：不硬编码坐标，每一步都用 Vision API "看" 屏幕再操作。

## 依赖

- Python 3.13（via Homebrew）
- Pillow（截图）
- pynput（鼠标控制）
- requests（API 调用）
- Claude API 代理（ANTHROPIC_BASE_URL + ANTHROPIC_AUTH_TOKEN）

所有依赖安装在 `.venv` 虚拟环境中。

## 使用

```bash
# 激活 venv
source tools/e2e-ui-test/.venv/bin/activate

# 完整测试（自动启动应用 → 导航 → 截图 → 对比）
python tools/e2e-ui-test/run_test.py

# 应用已在运行时跳过启动
python tools/e2e-ui-test/run_test.py --skip-launch

# 仅截图并分析当前页面
python tools/e2e-ui-test/run_test.py --screenshot-only

# 对比两张已有截图
python tools/e2e-ui-test/run_test.py --compare actual.png expected.png
```

## 文件结构

```
tools/e2e-ui-test/
├── README.md           # 本文件
├── config.py           # 配置（API URL、路径、延时）
├── screenshot.py       # 截图模块（Pillow.ImageGrab）
├── mouse.py            # 鼠标控制模块（pynput）
├── vision_api.py       # Claude Vision API 封装
├── app_manager.py      # 应用进程管理
├── navigate.py         # 导航逻辑（Vision 识别 + 点击）
├── run_test.py         # 主测试入口
├── screenshots/        # 截图输出目录
└── .venv/              # Python 虚拟环境
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `ANTHROPIC_BASE_URL` | API 代理地址 | `http://127.0.0.1:15721` |
| `ANTHROPIC_AUTH_TOKEN` | API 认证 token | — |
| `VISION_MODEL` | Vision 模型 ID | `claude-sonnet-4-20250514` |

## 输出

测试运行后在 `screenshots/test_<timestamp>/` 下生成：

- `create_instance.png` — 实际应用截图
- `mockup_reference.png` — 渲染的设计稿（如可用）
- `report.json` — 结构化测试报告

## 扩展

- 添加更多导航目标：修改 `navigate.py` 中的导航函数
- 添加更多页面对比：在 `run_test.py` 中添加步骤
- 自定义对比标准：`compare_screenshots()` 支持 `criteria` 参数
