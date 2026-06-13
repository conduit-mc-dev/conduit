"""
Windows 平台实现 — FindWindowW + PrintWindow + PostMessage。

依赖：pywin32 (win32gui, win32ui)，仅 Windows 需要安装。
"""

from __future__ import annotations

import ctypes
import time
from pathlib import Path
from typing import Optional, Tuple

from .base import PlatformDriver, WindowHandle

# Win32 常量
WM_LBUTTONDOWN = 0x0201
WM_LBUTTONUP = 0x0202
MK_LBUTTON = 0x0001
PW_RENDERFULLCONTENT = 0x00000002


def _get_user32():
    if not hasattr(ctypes, 'windll'):
        raise OSError("此模块仅在 Windows 上可用")
    return ctypes.windll.user32


class WindowsDriver(PlatformDriver):
    """Windows 平台驱动。"""

    def find_windows(self, title_pattern: str) -> list[WindowHandle]:
        user32 = _get_user32()
        results = []
        pattern_lower = title_pattern.lower()

        # 回调函数：枚举所有窗口
        EnumWindows = user32.EnumWindows
        EnumWindowsProc = ctypes.WINFUNCTYPE(
            ctypes.c_bool, ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int)
        )

        def callback(hwnd, lParam):
            if not user32.IsWindowVisible(hwnd):
                return True

            length = user32.GetWindowTextLengthW(hwnd)
            if length == 0:
                return True

            buf = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, buf, length + 1)
            title = buf.value

            if pattern_lower in title.lower():
                rect = ctypes.wintypes.RECT()
                user32.GetWindowRect(hwnd, ctypes.byref(rect))
                results.append(WindowHandle(
                    native_id=hwnd,
                    title=title,
                    pid=_get_window_pid(hwnd),
                    bounds=(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top),
                ))
            return True

        EnumWindows(EnumWindowsProc(callback), 0)
        return results

    def screenshot_background(self, handle: WindowHandle, output: Path) -> Optional[Path]:
        """通过 PrintWindow 后台截图。Compose Desktop 可能返回黑屏。"""
        try:
            import win32gui
            import win32ui
            import win32con
            from PIL import Image

            hwnd = handle.native_id
            left, top, right, bottom = win32gui.GetWindowRect(hwnd)
            width, height = right - left, bottom - top
            if width <= 0 or height <= 0:
                return None

            hwnd_dc = win32gui.GetWindowDC(hwnd)
            mfc_dc = win32ui.CreateDCFromHandle(hwnd_dc)
            save_dc = mfc_dc.CreateCompatibleDC()

            bitmap = win32ui.CreateBitmap()
            bitmap.CreateCompatibleBitmap(mfc_dc, width, height)
            save_dc.SelectObject(bitmap)

            user32 = _get_user32()
            result = user32.PrintWindow(hwnd, save_dc.GetSafeHdc(), PW_RENDERFULLCONTENT)
            if result == 0:
                # 降级尝试不带 flag
                result = user32.PrintWindow(hwnd, save_dc.GetSafeHdc(), 0)

            bmp_info = bitmap.GetInfo()
            bmp_bits = bitmap.GetBitmapBits(True)

            # 清理
            win32gui.DeleteObject(bitmap.GetHandle())
            save_dc.DeleteDC()
            mfc_dc.DeleteDC()
            win32gui.ReleaseDC(hwnd, hwnd_dc)

            if result == 0:
                return None

            img = Image.frombuffer(
                'RGB',
                (bmp_info['bmWidth'], bmp_info['bmHeight']),
                bmp_bits, 'raw', 'BGRX', 0, 1,
            )
            output.parent.mkdir(parents=True, exist_ok=True)
            img.save(str(output))
            return output

        except ImportError:
            # win32gui/win32ui 不可用
            return None
        except Exception:
            return None

    def click_background(self, handle: WindowHandle, x: int, y: int) -> bool:
        """通过 PostMessage 后台点击。Compose Desktop/Skia 可能不响应。"""
        try:
            user32 = _get_user32()
            hwnd = handle.native_id
            lparam = (y << 16) | (x & 0xFFFF)
            user32.PostMessageW(hwnd, WM_LBUTTONDOWN, MK_LBUTTON, lparam)
            time.sleep(0.05)
            user32.PostMessageW(hwnd, WM_LBUTTONUP, 0, lparam)
            return True
        except Exception:
            return False

    def focus_window(self, handle: WindowHandle) -> None:
        """将窗口带到前台。"""
        user32 = _get_user32()
        hwnd = handle.native_id
        # 如果窗口最小化，先恢复
        if user32.IsIconic(hwnd):
            user32.ShowWindow(hwnd, 9)  # SW_RESTORE
        user32.SetForegroundWindow(hwnd)
        time.sleep(0.2)

    def get_foreground_window(self) -> Optional[WindowHandle]:
        """获取当前前台窗口。"""
        user32 = _get_user32()
        hwnd = user32.GetForegroundWindow()
        if not hwnd:
            return None

        length = user32.GetWindowTextLengthW(hwnd)
        buf = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buf, length + 1)

        rect = ctypes.wintypes.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rect))

        return WindowHandle(
            native_id=hwnd,
            title=buf.value,
            pid=_get_window_pid(hwnd),
            bounds=(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top),
        )

    def restore_foreground(self, handle: Optional[WindowHandle]) -> None:
        """恢复之前保存的前台窗口。"""
        if handle:
            self.focus_window(handle)

    def get_window_rect(self, handle: WindowHandle) -> Tuple[int, int, int, int]:
        return handle.bounds

    def check_permissions(self) -> dict[str, bool]:
        """Windows 通常不需要特殊权限。"""
        return {'screen_recording': True, 'accessibility': True}


def _get_window_pid(hwnd: int) -> int:
    """获取窗口所属进程 PID。"""
    user32 = _get_user32()
    pid = ctypes.c_ulong()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    return pid.value
