"""
平台工厂 — 自动检测操作系统并返回对应的 PlatformDriver。

Usage:
    from platform import get_driver
    driver = get_driver()
    windows = driver.find_windows("Conduit MC")
"""

from __future__ import annotations

import sys
from typing import Optional

from .base import PlatformDriver, WindowHandle

_driver: Optional[PlatformDriver] = None


def get_driver() -> PlatformDriver:
    """获取当前平台的驱动实例（单例）。"""
    global _driver
    if _driver is not None:
        return _driver

    if sys.platform == 'darwin':
        from .macos import MacosDriver
        _driver = MacosDriver()
    elif sys.platform == 'win32':
        from .windows import WindowsDriver
        _driver = WindowsDriver()
    else:
        raise OSError(f"不支持的平台: {sys.platform}")

    return _driver


def find_app_window(title: str) -> Optional[WindowHandle]:
    """便捷函数：查找应用窗口，返回第一个匹配。"""
    driver = get_driver()
    windows = driver.find_windows(title)
    return windows[0] if windows else None


__all__ = ['get_driver', 'find_app_window', 'PlatformDriver', 'WindowHandle']
