"""
E2E UI 测试框架的配置。
"""

import os
from pathlib import Path

# ── 项目根目录 ──────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent

# ── Claude API 配置 ──────────────────────────────────
ANTHROPIC_BASE_URL = os.environ.get("ANTHROPIC_BASE_URL", "http://127.0.0.1:15721")
ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_AUTH_TOKEN", os.environ.get("ANTHROPIC_API_KEY", ""))

# Vision 模型
VISION_MODEL = os.environ.get("VISION_MODEL", "claude-sonnet-4-20250514")

# ── Desktop 应用 ──────────────────────────────────────
GRADLE_CMD = str(PROJECT_ROOT / "gradlew")
DESKTOP_RUN_TASK = ":desktop:run"

# 查找窗口用的标题关键字（支持 macOS/Windows）
APP_WINDOW_TITLE = os.environ.get("APP_WINDOW_TITLE", "Conduit MC")

# ── 截图目录 ──────────────────────────────────────
SCREENSHOT_DIR = Path(__file__).parent / "screenshots"
SCREENSHOT_DIR.mkdir(exist_ok=True)

# ── Mockup 路径 ──────────────────────────────────────
MOCKUP_S16 = PROJECT_ROOT / "docs" / "desktop-mockups" / "screen-s16-new-instance.html"
MOCKUP_DIR = PROJECT_ROOT / "docs" / "desktop-mockups"

# ── 延迟（秒）──────────────────────────────────────
APP_LAUNCH_WAIT = 8       # 等待应用窗口出现
CLICK_SETTLE_TIME = 0.5   # 点击后等待 UI 响应
NAV_SETTLE_TIME = 1.0     # 导航后等待页面渲染
FOCUS_RESTORE_DELAY = 0.3  # 聚焦恢复等待时间

# ── 降级策略 ──────────────────────────────────────────
# 黑屏检测阈值：像素亮度低于此值视为"暗"
BLACK_PIXEL_THRESHOLD = 15
# 黑屏比例超过此值视为截图失败
BLACK_RATIO_THRESHOLD = 0.98
# Vision 定位容差（像素）
VISION_LOCATE_TOLERANCE = 30
