"""
Desktop 应用进程管理 — 启动、等待窗口就绪、停止。

Usage:
    from app_manager import launch_app, wait_for_app, kill_app

    launch_app()
    wait_for_app()
    # ... 进行测试 ...
    kill_app()
"""

import subprocess
import time
from pathlib import Path
from typing import Optional

from config import GRADLE_CMD, DESKTOP_RUN_TASK, APP_LAUNCH_WAIT


_process: Optional[subprocess.Popen] = None


def launch_app(wait_time: float = APP_LAUNCH_WAIT) -> subprocess.Popen:
    """
    启动 Desktop 应用（通过 Gradle）。

    Args:
        wait_time: 启动后等待的时间（秒）。

    Returns:
        子进程对象。
    """
    global _process

    print(f"🚀 启动 Desktop 应用: {GRADLE_CMD} {DESKTOP_RUN_TASK}")
    _process = subprocess.Popen(
        [GRADLE_CMD, DESKTOP_RUN_TASK, "--quiet"],
        cwd=str(Path(GRADLE_CMD).parent),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    print(f"⏳ 等待 {wait_time}s 让应用窗口就绪...")
    time.sleep(wait_time)
    return _process


def wait_for_app(timeout: float = 30.0) -> bool:
    """
    等待应用进程启动完成（检查进程是否存活）。

    Args:
        timeout: 最大等待时间。

    Returns:
        True 表示应用正在运行。
    """
    if _process is None:
        return False

    start = time.time()
    while time.time() - start < timeout:
        if _process.poll() is not None:
            # 进程已退出
            stderr = _process.stderr.read().decode() if _process.stderr else ""
            print(f"❌ 应用进程已退出 (code={_process.returncode})")
            if stderr:
                print(f"   stderr: {stderr[:500]}")
            return False
        time.sleep(0.5)

    print("✅ 应用进程正在运行")
    return True


def kill_app() -> None:
    """停止 Desktop 应用。"""
    global _process
    if _process is not None:
        print("🛑 停止 Desktop 应用...")
        _process.terminate()
        try:
            _process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            _process.kill()
            _process.wait()
        print("   已停止")
        _process = None


def is_running() -> bool:
    """检查应用是否正在运行。"""
    if _process is None:
        return False
    return _process.poll() is None
