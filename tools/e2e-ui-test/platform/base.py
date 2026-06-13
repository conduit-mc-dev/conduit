"""
跨平台窗口发现、后台截图、后台点击的抽象接口。

Compose Desktop (Skia 渲染) 的窗口在 CGWindowList / FindWindow 中
可能报告错误的 bounds，因此后台操作必须有降级策略。
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional, Tuple


@dataclass
class WindowHandle:
    """平台原生窗口句柄的跨平台封装。"""
    native_id: Any             # macOS: int (CGWindowID), Windows: int (HWND)
    title: str = ""
    pid: int = 0
    bounds: Tuple[int, int, int, int] = (0, 0, 0, 0)  # 平台报告的 (x, y, w, h)

    # 通过 Vision API 或其他方式获得的实际屏幕位置（缓存）
    # 格式: (screen_x, screen_y, width, height) 或 None
    screen_rect: Optional[Tuple[int, int, int, int]] = field(default=None, repr=False)

    def __bool__(self) -> bool:
        return self.native_id is not None


class PlatformDriver(ABC):
    """
    跨平台驱动接口。每个平台提供一个实现。
    """

    @abstractmethod
    def find_windows(self, title_pattern: str) -> list[WindowHandle]:
        """
        按标题模糊匹配查找窗口。
        返回所有匹配的窗口（通常只有一个主窗口）。
        """

    @abstractmethod
    def screenshot_background(self, handle: WindowHandle, output: Path) -> Optional[Path]:
        """
        后台截图：截取指定窗口内容，不需要窗口在前台。

        Returns:
            截图文件路径，失败返回 None。
        """

    @abstractmethod
    def click_background(self, handle: WindowHandle, x: int, y: int) -> bool:
        """
        后台点击：在窗口的 (x, y) 坐标处发送点击事件。

        Args:
            handle: 窗口句柄。
            x, y: 窗口相对坐标（非屏幕坐标）。

        Returns:
            True 表示点击已发送（不一定成功）。
        """

    @abstractmethod
    def focus_window(self, handle: WindowHandle) -> None:
        """将窗口带到前台。"""

    @abstractmethod
    def get_foreground_window(self) -> Optional[WindowHandle]:
        """获取当前前台窗口。"""

    @abstractmethod
    def restore_foreground(self, handle: Optional[WindowHandle]) -> None:
        """恢复之前保存的前台窗口。"""

    @abstractmethod
    def get_window_rect(self, handle: WindowHandle) -> Tuple[int, int, int, int]:
        """
        获取窗口的屏幕坐标和尺寸。
        Returns: (x, y, width, height)
        """

    @abstractmethod
    def check_permissions(self) -> dict[str, bool]:
        """
        检查平台所需的权限。
        Returns: {"screen_recording": bool, "accessibility": bool, ...}
        """
