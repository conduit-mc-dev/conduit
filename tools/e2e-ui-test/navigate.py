"""
导航模块 — 通过 Claude Vision 识别 + 平台驱动点击，
自动导航到指定页面。

支持后台操作：优先后台截图+后台点击，失败时降级。
坐标系统：
  - Vision 返回的坐标相对于应用窗口内容区域
  - click_at_window 接受窗口相对坐标
"""

from __future__ import annotations

import time
from pathlib import Path
from typing import Optional

from config import (
    CLICK_SETTLE_TIME,
    NAV_SETTLE_TIME,
    MOCKUP_S16,
    MOCKUP_DIR,
    APP_WINDOW_TITLE,
    SCREENSHOT_DIR,
)
from screenshot import capture_app_window, capture_screen
from vision_api import identify_elements, describe_screenshot


def _get_app_handle():
    """获取应用窗口 handle（延迟初始化）。"""
    from platform import find_app_window
    handle = find_app_window(APP_WINDOW_TITLE)
    if not handle:
        raise RuntimeError(f"找不到应用窗口: {APP_WINDOW_TITLE}")
    return handle


def find_and_click(
    description: str,
    handle=None,
    screenshot_path: Optional[Path] = None,
    settle: float = CLICK_SETTLE_TIME,
    max_retries: int = 3,
) -> bool:
    """
    通用操作：截图 → 找元素 → 点击。

    Args:
        description: 要点击的元素的自然语言描述。
        handle: WindowHandle，为 None 时自动查找。
        settle: 点击后等待时间。
        max_retries: 最大重试次数。

    Returns:
        True 表示成功找到并点击。
    """
    from mouse import click_at_window

    if handle is None:
        handle = _get_app_handle()

    for attempt in range(1, max_retries + 1):
        print(f"  📸 截图... (尝试 {attempt}/{max_retries})")
        shot = capture_app_window(screenshot_path, handle=handle)

        print(f"  🔍 Vision 识别: {description}")
        elements = identify_elements(
            shot,
            prompt=f"找到 '{description}' 的位置，返回它的中心坐标（相对于截图）。",
        )

        if not elements:
            print(f"  ⚠️  未找到 '{description}'")
            if attempt < max_retries:
                time.sleep(1)
            continue

        best = elements[0]
        x, y = int(best.get('x', 0)), int(best.get('y', 0))
        label = best.get('label', description)
        print(f"  🎯 找到: {label} @ ({x}, {y})")

        # 如果截图是裁剪过的（app window），坐标已经是窗口相对坐标
        # 如果截图是全屏的，需要减去窗口偏移
        if handle.screen_rect:
            # 截图是裁剪过的，坐标直接可用
            win_x, win_y = x, y
        else:
            # 截图可能是全屏的，Vision 坐标是相对于全屏的
            # 需要转换为窗口相对坐标
            from mouse import screen_to_window
            win_x, win_y = screen_to_window(handle, x, y)

        click_at_window(handle, win_x, win_y, settle=settle)
        return True

    print(f"  ❌ 未能找到 '{description}'（{max_retries} 次尝试后放弃）")
    return False


def navigate_to_create_instance() -> Optional[Path]:
    """
    自动导航到 Create Instance 页面。
    """
    print("\n📋 导航到 Create Instance 页面...")
    print("=" * 50)

    handle = _get_app_handle()

    # Step 1: 截取当前状态
    print("\n[Step 1] 检查当前页面状态...")
    initial = capture_app_window(Path("screenshots/step1_initial.png"), handle=handle)
    desc = describe_screenshot(initial)
    print(f"  📝 当前页面: {desc[:200]}...")

    # Step 2: 找到并点击 "New Server" 按钮
    print("\n[Step 2] 查找并点击 New Server 按钮...")
    found = find_and_click(
        "New Server button or plus (+) button in the server list panel",
        handle=handle,
        screenshot_path=Path("screenshots/step2_before_click.png"),
        settle=NAV_SETTLE_TIME,
    )
    if not found:
        print("  ❌ 导航失败：无法找到 New Server 按钮")
        return None

    # Step 3: 确认到达 Create Instance 页面
    print("\n[Step 3] 确认到达 Create Instance 页面...")
    time.sleep(NAV_SETTLE_TIME)
    create_page = capture_app_window(
        Path("screenshots/step3_create_instance.png"), handle=handle
    )

    elements = identify_elements(
        create_page,
        prompt=(
            "这是不是 'Create Server' 或 'Create Instance' 页面？"
            "返回 JSON: {\"is_create_page\": true/false}"
        ),
    )

    if elements and isinstance(elements[0], dict):
        is_create = elements[0].get('is_create_page', False)
        if is_create:
            print("  ✅ 已到达 Create Instance 页面")

    print(f"\n📸 Create Instance 截图已保存: {create_page}")
    return create_page


def render_mockup_reference(output_path: Optional[Path] = None) -> Optional[Path]:
    """
    渲染 mockup HTML 为 PNG 参考图（使用 Edge/Chrome headless）。
    """
    if not MOCKUP_S16.exists():
        print(f"  ❌ Mockup 文件不存在: {MOCKUP_S16}")
        return None

    if output_path is None:
        output_path = Path("screenshots/mockup_s16_reference.png")

    import subprocess

    # 查找浏览器
    edge_path = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
    chrome_path = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    browser = None
    for p in [chrome_path, edge_path]:
        from pathlib import Path as _P
        if _P(p).exists():
            browser = p
            break

    if not browser:
        print("  ⚠️  未找到 Chrome 或 Edge 浏览器")
        return None

    browser_name = Path(browser).name
    try:
        ret = subprocess.run(
            [
                browser,
                "--headless",
                f"--screenshot={output_path.resolve()}",
                "--window-size=1280,720",
                "--hide-scrollbars",
                f"file://{MOCKUP_S16.resolve()}",
            ],
            capture_output=True,
            timeout=15,
        )
        if output_path.exists() and output_path.stat().st_size > 1000:
            print(f"  ✅ Mockup 已渲染 ({browser_name} headless): {output_path}")
            return output_path
        else:
            print(f"  ⚠️  {browser_name} headless 截图失败")
    except Exception as e:
        print(f"  ⚠️  {browser_name} headless 失败: {e}")

    return None


def render_all_mockups(output_dir: Optional[Path] = None) -> dict[str, Path]:
    """
    批量渲染所有 screen mockup HTML 为 PNG。

    Returns:
        {mockup_name: png_path} 字典。
    """
    import subprocess

    edge_path = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
    chrome_path = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    browser = None
    for p in [chrome_path, edge_path]:
        if Path(p).exists():
            browser = p
            break

    if not browser:
        print("  ⚠️  未找到浏览器")
        return {}

    if output_dir is None:
        output_dir = SCREENSHOT_DIR / "mockups"
    output_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    mockups = sorted(MOCKUP_DIR.glob("screen-*.html"))

    print(f"  渲染 {len(mockups)} 个 mockup...")
    for html_path in mockups:
        name = html_path.stem
        out = output_dir / f"{name}.png"
        if out.exists() and out.stat().st_size > 1000:
            results[name] = out
            continue

        try:
            subprocess.run(
                [browser, "--headless", f"--screenshot={out.resolve()}",
                 "--window-size=1280,720", "--hide-scrollbars",
                 f"file://{html_path.resolve()}"],
                capture_output=True, timeout=10,
            )
            if out.exists() and out.stat().st_size > 1000:
                results[name] = out
        except Exception:
            pass

    print(f"  ✅ 渲染完成: {len(results)}/{len(mockups)}")
    return results
