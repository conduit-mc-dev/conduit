"""
鼠标控制模块 — 三层降级策略：
  1. 平台原生后台点击（AX API / PostMessage）
  2. 短暂聚焦 + pynput 物理点击
  3. 纯 pynput（最终降级）

所有坐标都是屏幕坐标，除非特别说明。
"""

from __future__ import annotations

import sys
import time
from pathlib import Path
from typing import Optional, Tuple

from pynput.mouse import Button, Controller

from config import FOCUS_RESTORE_DELAY

# 允许 platform 模块被 import
sys.path.insert(0, str(Path(__file__).parent))

# 全局 mouse 控制器（前台降级用）
_mouse = Controller()


# ── 基础操作（前台，保留向后兼容）───────────────────


def get_position() -> Tuple[int, int]:
    """获取当前鼠标位置。"""
    pos = _mouse.position
    return (int(pos[0]), int(pos[1]))


def move_to(x: int, y: int, duration: float = 0.1) -> None:
    """移动鼠标到指定屏幕坐标。"""
    _mouse.position = (x, y)
    if duration > 0:
        time.sleep(duration)


def click_at(
    x: int,
    y: int,
    button: Button = Button.left,
    delay_before: float = 0.05,
    delay_after: float = 0.05,
    from_image: bool = False,
) -> None:
    """
    移动到屏幕坐标并点击（前台模式）。

    Args:
        x, y: 屏幕坐标。如果 from_image=True，自动做 Retina 缩放转换。
    """
    if from_image:
        x, y = image_to_screen(x, y)
    if delay_before > 0:
        time.sleep(delay_before)
    _mouse.position = (x, y)
    time.sleep(0.05)
    _mouse.click(button)
    if delay_after > 0:
        time.sleep(delay_after)


def click_and_wait(
    x: int,
    y: int,
    settle: float = 0.5,
    button: Button = Button.left,
    from_image: bool = False,
) -> None:
    """点击并等待 UI 响应。from_image=True 时自动做 Retina 转换。"""
    click_at(x, y, button=button, from_image=from_image)
    time.sleep(settle)


def double_click(x: int, y: int, settle: float = 0.3) -> None:
    """双击指定位置。"""
    _mouse.position = (x, y)
    time.sleep(0.05)
    _mouse.click(Button.left, 2)
    time.sleep(settle)


def scroll_at(x: int, y: int, dy: int = -3, settle: float = 0.3) -> None:
    """在指定位置滚动。"""
    _mouse.position = (x, y)
    time.sleep(0.05)
    _mouse.scroll(0, dy)
    time.sleep(settle)


# ── 窗口坐标点击（三层降级）────────────────────────


def click_at_window(
    handle,
    x: int,
    y: int,
    settle: float = 0.5,
) -> bool:
    """
    在指定窗口的 (x, y) 坐标处点击。
    优先后台操作，失败时降级到短暂聚焦 + pynput。

    Args:
        handle: WindowHandle 对象。
        x, y: 窗口相对坐标（非屏幕坐标）。
        settle: 点击后等待时间。

    Returns:
        True 表示操作完成。
    """
    from platform import get_driver
    driver = get_driver()

    # ── 策略 1: 后台点击 ──
    if driver.click_background(handle, x, y):
        time.sleep(settle)
        return True

    # ── 策略 2: 短暂聚焦 + pynput ──
    old_fg = driver.get_foreground_window()
    driver.focus_window(handle)
    time.sleep(0.2)

    # 计算屏幕坐标
    screen_x, screen_y = _window_to_screen(handle, x, y)
    _mouse.position = (screen_x, screen_y)
    time.sleep(0.05)
    _mouse.click(Button.left)
    time.sleep(settle)

    # 恢复前台窗口
    driver.restore_foreground(old_fg)
    return True


def click_and_wait_at_window(
    handle,
    x: int,
    y: int,
    settle: float = 0.5,
) -> bool:
    """在窗口坐标处点击并等待。"""
    return click_at_window(handle, x, y, settle=settle)


# ── 窗口坐标 → 屏幕坐标转换 ────────────────────────


def get_image_scale() -> float:
    """
    获取 ImageGrab 截图与逻辑屏幕的缩放比。
    Retina Mac 上 ImageGrab 是 2x，逻辑坐标是 1x。
    Vision API 返回的是图片像素坐标，需要除以此比例才是屏幕坐标。
    """
    import sys
    if sys.platform == 'darwin':
        try:
            import Quartz
            logical_w = Quartz.CGDisplayBounds(Quartz.CGMainDisplayID()).size.width
            # ImageGrab.grab() 的实际宽度
            from PIL import ImageGrab
            test = ImageGrab.grab(bbox=(0, 0, 10, 10))
            # 不实际 grab，用已知的 Retina 比例
            # macOS Retina 通常 2x
            return 2.0 if logical_w < 2000 else 1.0
        except Exception:
            return 2.0
    elif sys.platform == 'win32':
        try:
            import ctypes
            user32 = ctypes.windll.user32
            # Windows DPI 缩放
            return user32.GetDpiForSystem() / 96.0
        except Exception:
            return 1.0
    return 1.0


def image_to_screen(ix: int, iy: int, scale: float = 0) -> Tuple[int, int]:
    """
    将 Vision API 返回的图片像素坐标转换为屏幕逻辑坐标。
    pynput 使用逻辑坐标。
    """
    if scale <= 0:
        scale = get_image_scale()
    return (int(ix / scale), int(iy / scale))


def _window_to_screen(handle, wx: int, wy: int) -> Tuple[int, int]:
    """
    将窗口相对坐标转换为屏幕坐标。

    优先使用 Vision 缓存的 screen_rect（更准确），
    回退到平台 API 报告的 bounds。
    """
    # 如果有 Vision 缓存的窗口位置，用它
    if handle.screen_rect:
        return (handle.screen_rect[0] + wx, handle.screen_rect[1] + wy)

    # 否则用平台 API 的 bounds
    from platform import get_driver
    driver = get_driver()
    rect = driver.get_window_rect(handle)
    return (rect[0] + wx, rect[1] + wy)


def screen_to_window(handle, sx: int, sy: int) -> Tuple[int, int]:
    """将屏幕坐标转换为窗口相对坐标。"""
    if handle.screen_rect:
        return (sx - handle.screen_rect[0], sy - handle.screen_rect[1])

    from platform import get_driver
    driver = get_driver()
    rect = driver.get_window_rect(handle)
    return (sx - rect[0], sy - rect[1])
