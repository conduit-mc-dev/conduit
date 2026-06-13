"""
截图模块 — 三层降级策略：
  1. 平台原生后台截图（截取指定窗口，不需要前台）
  2. 全屏截图 + Vision API 裁剪到应用窗口
  3. 短暂聚焦 + Pillow 区域截图

macOS 权限要求：系统设置 → 隐私与安全 → 屏幕录制 → 添加终端
"""

from __future__ import annotations

import base64
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional, Tuple

from PIL import Image, ImageGrab

from config import (
    SCREENSHOT_DIR,
    BLACK_PIXEL_THRESHOLD,
    BLACK_RATIO_THRESHOLD,
    FOCUS_RESTORE_DELAY,
)

# 允许 platform 模块被 import
sys.path.insert(0, str(Path(__file__).parent))


class ScreenCaptureError(RuntimeError):
    """截图失败（通常是权限问题）。"""
    pass


# ── 工具函数 ──────────────────────────────────────


def is_mostly_black(path: Path, threshold: float = BLACK_RATIO_THRESHOLD) -> bool:
    """
    检测截图是否大部分为黑色（Skia 渲染失败的典型表现）。
    """
    try:
        img = Image.open(path).convert('L')
        total = img.size[0] * img.size[1]
        # 采样检测（全图太慢）
        sample_size = min(total, 10000)
        step = max(1, total // sample_size)
        pixels = list(img.getdata())
        dark_count = sum(1 for i in range(0, total, step) if pixels[i] < BLACK_PIXEL_THRESHOLD)
        return (dark_count * step) / total > threshold
    except Exception:
        return True


def is_invalid_screenshot(path: Path, min_fraction: float = 0.1) -> bool:
    """
    检测截图是否无效（全黑 或 尺寸过小）。
    Compose Desktop 的 screencapture -l 可能截取到错误的小窗口。
    """
    try:
        img = Image.open(path)
        w, h = img.size

        # 检查 1: 是否大部分为黑色
        if is_mostly_black(path):
            return True

        # 检查 2: 尺寸是否过小（相对于全屏）
        # 获取全屏尺寸（取屏幕最大分辨率估计）
        try:
            screen = ImageGrab.grab()
            screen_w, screen_h = screen.size
            del screen
        except Exception:
            screen_w, screen_h = 3456, 2234  # macOS Retina 默认

        if w < screen_w * min_fraction or h < screen_h * min_fraction:
            return True

        return False
    except Exception:
        return True


def _timestamp() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def _win32_grab_screen() -> Optional[Image.Image]:
    """
    Windows 降级截图：用 ctypes 调用 Win32 GDI API。
    比 ImageGrab.grab() 更底层，在 PsExec/SSH 环境中可能可用。
    """
    if sys.platform != 'win32':
        return None
    try:
        import ctypes
        import ctypes.wintypes

        user32 = ctypes.windll.user32
        gdi32 = ctypes.windll.gdi32

        SM_CXSCREEN, SM_CYSCREEN = 0, 1
        w = user32.GetSystemMetrics(SM_CXSCREEN)
        h = user32.GetSystemMetrics(SM_CYSCREEN)

        hdesktop = user32.GetDC(0)
        hdc = gdi32.CreateCompatibleDC(hdesktop)
        hbmp = gdi32.CreateCompatibleBitmap(hdesktop, w, h)
        gdi32.SelectObject(hdc, hbmp)

        SRCCOPY = 0x00CC0020
        gdi32.BitBlt(hdc, 0, 0, w, h, hdesktop, 0, 0, SRCCOPY)

        # 从 bitmap 创建 PIL Image
        bmp_info = ctypes.wintypes.BITMAPINFOHEADER()
        bmp_info.biSize = ctypes.sizeof(ctypes.wintypes.BITMAPINFOHEADER)
        bmp_info.biWidth = w
        bmp_info.biHeight = -h  # top-down
        bmp_info.biPlanes = 1
        bmp_info.biBitCount = 32
        bmp_info.biCompression = 0  # BI_RGB

        buf = ctypes.create_string_buffer(w * h * 4)
        gdi32.GetDIBits(hdc, hbmp, 0, h, buf, ctypes.byref(bmp_info), 0)

        img = Image.frombuffer('RGBA', (w, h), buf, 'raw', 'BGRA', 0, 1)
        img = img.convert('RGB')

        gdi32.DeleteObject(hbmp)
        gdi32.DeleteDC(hdc)
        user32.ReleaseDC(0, hdesktop)

        # 检查是否全黑
        pixels = list(img.getdata())[:100]
        if all(p == (0, 0, 0) for p in pixels):
            return None  # 全黑 = 无法访问桌面

        return img
    except Exception:
        return None


# ── 基础截图（全屏 / 区域）────────────────────────


def capture_screen(output_path: Optional[Path] = None) -> Path:
    """
    全屏截图。优先 ImageGrab.grab()，Windows 失败时用 Win32 API 降级。
    """
    try:
        img = ImageGrab.grab()
    except Exception as e:
        if sys.platform == 'win32':
            img = _win32_grab_screen()
            if img is None:
                raise ScreenCaptureError(f"全屏截图失败 (ImageGrab + Win32 均失败): {e}")
        else:
            if 'could not create image from display' in str(e).lower() or 'CalledProcessError' in type(e).__name__:
                raise ScreenCaptureError(
                    "屏幕录制权限未授予。\n"
                    "macOS: 系统设置 → 隐私与安全 → 屏幕录制 → 添加终端\n"
                    f"原始错误: {e}"
                )
            raise ScreenCaptureError(f"全屏截图失败: {e}")

    if output_path is None:
        output_path = SCREENSHOT_DIR / f"screen_{_timestamp()}.png"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(str(output_path))
    return output_path


def capture_region(
    x: int, y: int, w: int, h: int, output_path: Optional[Path] = None
) -> Path:
    """
    区域截图。
    """
    try:
        img = ImageGrab.grab(bbox=(x, y, x + w, y + h))
    except Exception as e:
        raise ScreenCaptureError(f"区域截图失败: {e}")

    if output_path is None:
        output_path = SCREENSHOT_DIR / f"region_{_timestamp()}.png"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(str(output_path))
    return output_path


# ── 应用窗口截图（三层降级）────────────────────────


def capture_app_window(
    output_path: Optional[Path] = None,
    handle=None,
    use_vision_fallback: bool = True,
) -> Path:
    """
    截取应用窗口，自动选择最佳策略。

    Args:
        output_path: 输出路径。
        handle: WindowHandle（可选，为 None 时自动查找）。
        use_vision_fallback: 是否启用 Vision API 裁剪降级。

    Returns:
        截图文件路径。
    """
    if output_path is None:
        output_path = SCREENSHOT_DIR / f"app_{_timestamp()}.png"

    # 查找窗口
    if handle is None:
        from platform import get_driver
        from config import APP_WINDOW_TITLE
        driver = get_driver()
        windows = driver.find_windows(APP_WINDOW_TITLE)
        if not windows:
            raise ScreenCaptureError(f"找不到应用窗口 (title: {APP_WINDOW_TITLE})")
        handle = windows[0]

    # ── 策略 1: 平台原生后台截图 ──
    from platform import get_driver
    driver = get_driver()

    bg_path = driver.screenshot_background(handle, output_path)
    if bg_path and bg_path.exists() and not is_invalid_screenshot(bg_path):
        return bg_path

    # ── 策略 2: 全屏截图 + Vision 裁剪 ──
    if use_vision_fallback:
        try:
            from vision_api import identify_elements

            full_screen = capture_screen(output_path.parent / f"full_{_timestamp()}.png")
            elements = identify_elements(
                full_screen,
                prompt=(
                    f"Find the application window titled '{handle.title or 'Conduit MC'}'. "
                    "Return the TOP-LEFT corner and size as JSON: "
                    '{"x": left_edge_pixel, "y": top_edge_pixel, "width": pixel_width, "height": pixel_height} '
                    "where x,y are the top-left corner coordinates in pixels. Only return the JSON object."
                ),
            )

            if elements and isinstance(elements[0], dict):
                e = elements[0]
                # Vision 返回的是左上角坐标 (x, y) 和宽高
                x = int(e.get('x', 0))
                y = int(e.get('y', 0))
                w = int(e.get('width', 0))
                h = int(e.get('height', 0))

                if w > 100 and h > 100:
                    # 缓存实际窗口位置供后续点击使用
                    handle.screen_rect = (x, y, w, h)

                    cropped = Image.open(full_screen).crop((x, y, x + w, y + h))
                    output_path.parent.mkdir(parents=True, exist_ok=True)
                    cropped.save(str(output_path))
                    return output_path

        except Exception:
            pass  # 降级到策略 3

    # ── 策略 3: 短暂聚焦 + 区域截图 ──
    old_fg = driver.get_foreground_window()
    driver.focus_window(handle)
    import time
    time.sleep(FOCUS_RESTORE_DELAY)

    rect = driver.get_window_rect(handle)
    if rect[2] > 100 and rect[3] > 100:
        try:
            path = capture_region(rect[0], rect[1], rect[2], rect[3], output_path)
            driver.restore_foreground(old_fg)
            return path
        except Exception:
            pass

    # 最终降级：全屏截图
    path = capture_screen(output_path)
    driver.restore_foreground(old_fg)
    return path


# ── 工具函数 ──────────────────────────────────────


def image_to_base64(path: Path) -> str:
    """将图片文件编码为 base64 字符串。"""
    with open(path, 'rb') as f:
        return base64.b64encode(f.read()).decode('utf-8')


def get_image_dimensions(path: Path) -> Tuple[int, int]:
    """获取图片的 (width, height)。"""
    with Image.open(path) as img:
        return img.size
