"""
macOS 平台实现 — CGWindowList + screencapture + AX API。

依赖：pyobjc-core, pyobjc-framework-Quartz,
      pyobjc-framework-Cocoa, pyobjc-framework-ApplicationServices
"""

from __future__ import annotations

import subprocess
import time
from pathlib import Path
from typing import Optional, Tuple

from .base import PlatformDriver, WindowHandle

# macOS 框架延迟导入（避免非 macOS 环境报错）
_Quartz = None
_AppKit = None
_ApplicationServices = None


def _load_quartz():
    global _Quartz
    if _Quartz is None:
        import Quartz
        _Quartz = Quartz
    return _Quartz


def _load_appkit():
    global _AppKit
    if _AppKit is None:
        import AppKit
        _AppKit = AppKit
    return _AppKit


def _load_ax():
    global _ApplicationServices
    if _ApplicationServices is None:
        from ApplicationServices import (
            AXUIElementCreateApplication,
            AXUIElementCopyAttributeValue,
            AXUIElementCopyElementAtPosition,
            AXUIElementPerformAction,
            AXIsProcessTrusted,
        )
        _ApplicationServices = {
            'AXUIElementCreateApplication': AXUIElementCreateApplication,
            'AXUIElementCopyAttributeValue': AXUIElementCopyAttributeValue,
            'AXUIElementCopyElementAtPosition': AXUIElementCopyElementAtPosition,
            'AXUIElementPerformAction': AXUIElementPerformAction,
            'AXIsProcessTrusted': AXIsProcessTrusted,
        }
    return _ApplicationServices


class MacosDriver(PlatformDriver):
    """macOS 平台驱动。"""

    def find_windows(self, title_pattern: str) -> list[WindowHandle]:
        Quartz = _load_quartz()
        AppKit = _load_appkit()

        results = []
        pattern_lower = title_pattern.lower()

        # 方式 1: CGWindowList 按标题匹配
        windows = Quartz.CGWindowListCopyWindowInfo(
            Quartz.kCGWindowListOptionOnScreenOnly | Quartz.kCGWindowListExcludeDesktopElements,
            Quartz.kCGNullWindowID,
        )
        for w in windows:
            name = w.get('kCGWindowName', '')
            owner = w.get('kCGWindowOwnerName', '')
            if (pattern_lower in name.lower() or
                pattern_lower in owner.lower()):
                bounds = w.get('kCGWindowBounds', {})
                handle = WindowHandle(
                    native_id=w['kCGWindowNumber'],
                    title=name,
                    pid=w.get('kCGWindowOwnerPID', 0),
                    bounds=(
                        int(bounds.get('X', 0)),
                        int(bounds.get('Y', 0)),
                        int(bounds.get('Width', 0)),
                        int(bounds.get('Height', 0)),
                    ),
                )
                results.append(handle)

        # 方式 2: 如果 CGWindowList 没找到，按进程名查
        if not results:
            workspace = AppKit.NSWorkspace.sharedWorkspace()
            for app in workspace.runningApplications():
                app_name = app.localizedName() or ''
                if pattern_lower in app_name.lower():
                    pid = app.processIdentifier()
                    # 用 PID 过滤 CGWindowList
                    for w in windows:
                        if w.get('kCGWindowOwnerPID') == pid:
                            bounds = w.get('kCGWindowBounds', {})
                            handle = WindowHandle(
                                native_id=w['kCGWindowNumber'],
                                title=w.get('kCGWindowName', ''),
                                pid=pid,
                                bounds=(
                                    int(bounds.get('X', 0)),
                                    int(bounds.get('Y', 0)),
                                    int(bounds.get('Width', 0)),
                                    int(bounds.get('Height', 0)),
                                ),
                            )
                            results.append(handle)
                    break

        return results

    def screenshot_background(self, handle: WindowHandle, output: Path) -> Optional[Path]:
        """尝试后台截图 (screencapture -l)。Compose Desktop 可能失败。"""
        try:
            output.parent.mkdir(parents=True, exist_ok=True)
            ret = subprocess.run(
                ['screencapture', '-l', str(handle.native_id), '-x', str(output)],
                capture_output=True,
                timeout=5,
            )
            if ret.returncode == 0 and output.exists() and output.stat().st_size > 1000:
                return output
        except Exception:
            pass
        return None

    def click_background(self, handle: WindowHandle, x: int, y: int) -> bool:
        """通过 AX Accessibility API 后台点击。Compose Desktop 可能失败。"""
        try:
            ax = _load_ax()
            AXUIElementCreateApplication = ax['AXUIElementCreateApplication']
            AXUIElementCopyAttributeValue = ax['AXUIElementCopyAttributeValue']
            AXUIElementCopyElementAtPosition = ax['AXUIElementCopyElementAtPosition']
            AXUIElementPerformAction = ax['AXUIElementPerformAction']

            app_element = AXUIElementCreateApplication(handle.pid)

            # 获取窗口列表
            err, windows = AXUIElementCopyAttributeValue(app_element, 'AXWindows', None)
            if not windows or len(windows) == 0:
                return False

            window = windows[0]

            # 获取窗口位置
            err, pos = AXUIElementCopyAttributeValue(window, 'AXPosition', None)
            if err == 0 and pos:
                win_x = pos.x()
                win_y = pos.y()
            else:
                win_x, win_y = 0, 0

            # 在目标坐标处查找元素并执行点击
            err, element = AXUIElementCopyElementAtPosition(
                app_element, x, y, None
            )
            if err == 0 and element:
                AXUIElementPerformAction(element, 'AXPress')
                return True

        except Exception:
            pass
        return False

    def focus_window(self, handle: WindowHandle) -> None:
        """将窗口带到前台。"""
        AppKit = _load_appkit()
        workspace = AppKit.NSWorkspace.sharedWorkspace()

        for app in workspace.runningApplications():
            if app.processIdentifier() == handle.pid:
                app.activateWithOptions_(
                    AppKit.NSApplicationActivateIgnoringOtherApps
                )
                time.sleep(0.2)
                return

    def get_foreground_window(self) -> Optional[WindowHandle]:
        """获取当前最前台的应用窗口。"""
        Quartz = _load_quartz()
        windows = Quartz.CGWindowListCopyWindowInfo(
            Quartz.kCGWindowListOptionOnScreenOnly | Quartz.kCGWindowListExcludeDesktopElements,
            Quartz.kCGNullWindowID,
        )
        # 找 layer=0 的最前面窗口（跳过系统窗口）
        for w in windows:
            if w.get('kCGWindowLayer', 100) == 0:
                bounds = w.get('kCGWindowBounds', {})
                return WindowHandle(
                    native_id=w['kCGWindowNumber'],
                    title=w.get('kCGWindowName', ''),
                    pid=w.get('kCGWindowOwnerPID', 0),
                    bounds=(
                        int(bounds.get('X', 0)),
                        int(bounds.get('Y', 0)),
                        int(bounds.get('Width', 0)),
                        int(bounds.get('Height', 0)),
                    ),
                )
        return None

    def restore_foreground(self, handle: Optional[WindowHandle]) -> None:
        """恢复之前保存的前台窗口。"""
        if handle:
            self.focus_window(handle)

    def get_window_rect(self, handle: WindowHandle) -> Tuple[int, int, int, int]:
        """返回 CGWindowList 报告的窗口位置（可能不准确）。"""
        return handle.bounds

    def check_permissions(self) -> dict[str, bool]:
        """检查 macOS 权限。"""
        result = {}

        # 屏幕录制
        try:
            ret = subprocess.run(
                ['screencapture', '-x', '-t', 'png', '/dev/null'],
                capture_output=True, timeout=3,
            )
            result['screen_recording'] = ret.returncode == 0
        except Exception:
            result['screen_recording'] = False

        # 辅助功能
        ax = _load_ax()
        try:
            result['accessibility'] = ax['AXIsProcessTrusted']()
        except Exception:
            result['accessibility'] = False

        return result
