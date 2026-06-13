#!/usr/bin/env python3
"""
Conduit MC — Windows VM 全自动 E2E 验证脚本。
通过 schtasks 在交互式桌面会话中运行，无需人工操作。

流程：
1. 启动 Daemon (如果未运行)
2. 启动 Desktop 应用
3. 等待窗口就绪
4. 截图 + Vision 分析
5. 逐个对比 mockup
6. 输出报告
"""

import sys
import os
import time
import json
import subprocess
from pathlib import Path
from datetime import datetime

# 确保可以 import 同目录模块
SCRIPT_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPT_DIR))

REPORT_DIR = SCRIPT_DIR / "reports"
REPORT_DIR.mkdir(exist_ok=True)


def log(msg):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


def find_gradlew():
    """找到 gradlew 路径。"""
    candidates = [
        SCRIPT_DIR.parent.parent / "gradlew.bat",
        SCRIPT_DIR.parent.parent / "gradlew",
    ]
    for p in candidates:
        if p.exists():
            return str(p)
    return None


def start_daemon():
    """启动 Daemon（如果未运行）。"""
    log("检查 Daemon...")
    try:
        import requests
        r = requests.get("http://127.0.0.1:9147/", timeout=3)
        log("  Daemon 已在运行")
        return True
    except Exception:
        pass

    gradlew = find_gradlew()
    if not gradlew:
        log("  ⚠️  未找到 gradlew，跳过 Daemon 启动")
        return False

    log("  启动 Daemon...")
    subprocess.Popen(
        [gradlew, ":daemon:run", "--quiet"],
        cwd=str(SCRIPT_DIR.parent.parent),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    time.sleep(8)

    try:
        import requests
        r = requests.get("http://127.0.0.1:9147/", timeout=3)
        log("  ✅ Daemon 已启动")
        return True
    except Exception:
        log("  ⚠️  Daemon 启动超时，继续...")
        return False


def start_desktop():
    """启动 Desktop 应用。"""
    log("检查 Desktop 应用...")
    from platform import find_app_window
    h = find_app_window("Conduit MC")
    if h:
        log("  Desktop 已在运行")
        return True

    gradlew = find_gradlew()
    if not gradlew:
        log("  ❌ 未找到 gradlew")
        return False

    log("  启动 Desktop...")
    subprocess.Popen(
        [gradlew, ":desktop:run", "--quiet"],
        cwd=str(SCRIPT_DIR.parent.parent),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    # 等待窗口出现
    for i in range(20):
        time.sleep(2)
        h = find_app_window("Conduit MC")
        if h:
            log(f"  ✅ Desktop 已启动 ({i*2}s)")
            time.sleep(3)  # 额外等待渲染
            return True

    log("  ❌ Desktop 启动超时")
    return False


def run_screenshots_and_compare():
    """截图 + Vision 分析 + mockup 对比。"""
    from platform import get_driver, find_app_window
    from screenshot import capture_app_window
    from vision_api import identify_elements, compare_screenshots, describe_screenshot
    from pathlib import Path

    driver = get_driver()
    h = find_app_window("Conduit MC")
    if not h:
        log("❌ 找不到应用窗口")
        return {}

    driver.focus_window(h)
    time.sleep(1)
    h = find_app_window("Conduit MC")

    results = {}

    # 截图当前状态
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    shot = capture_app_window(REPORT_DIR / f"app_{ts}.png", handle=h)
    log(f"📸 截图: {shot} ({shot.stat().st_size // 1024} KB)")

    # 页面描述
    desc = describe_screenshot(shot)
    log(f"📝 页面描述: {desc[:200]}...")

    # 逐个对比 mockup
    mockups_dir = SCRIPT_DIR.parent.parent / "docs" / "desktop-mockups"
    if not mockups_dir.exists():
        mockups_dir = SCRIPT_DIR / "mockups"  # 备选路径

    mockup_files = sorted(mockups_dir.glob("screen-*.html"))
    log(f"\n🔄 对比 {len(mockup_files)} 个 mockup...")

    # 渲染 mockup 为 PNG（如果还没有）
    rendered_dir = REPORT_DIR / "mockups"
    rendered_dir.mkdir(exist_ok=True)

    # 查找浏览器
    edge_path = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    chrome_path = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
    browser = None
    for p in [chrome_path, edge_path]:
        if Path(p).exists():
            browser = p
            break

    # 也检查常见备选路径
    if not browser:
        for p in [
            r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
            r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        ]:
            if Path(p).exists():
                browser = p
                break

    if browser:
        log(f"  浏览器: {Path(browser).name}")
        for html_path in mockup_files:
            name = html_path.stem
            png_path = rendered_dir / f"{name}.png"
            if png_path.exists() and png_path.stat().st_size > 1000:
                continue
            try:
                subprocess.run(
                    [browser, "--headless", f"--screenshot={png_path}",
                     "--window-size=1280,720", "--hide-scrollbars",
                     f"file://{html_path.resolve()}"],
                    capture_output=True, timeout=10,
                )
            except Exception:
                pass
    else:
        log("  ⚠️  未找到浏览器，跳过 mockup 渲染")

    # 对比每个已渲染的 mockup
    rendered_files = sorted(rendered_dir.glob("screen-*.png"))
    for mockup_png in rendered_files:
        name = mockup_png.stem
        try:
            result = compare_screenshots(shot, mockup_png)
            score = result.get("match_score", 0)
            diffs = result.get("differences", [])
            major_count = len([d for d in diffs if d.get("severity") in ("critical", "major")])
            results[name] = {
                "score": score,
                "major_diffs": major_count,
                "assessment": result.get("overall_assessment", "")[:200],
            }
            status = "✅" if score >= 0.85 else "⚠️" if score >= 0.7 else "❌"
            log(f"  {status} {name}: {score:.0%} ({major_count} major)")
        except Exception as e:
            log(f"  ❌ {name}: {e}")
            results[name] = {"score": 0, "error": str(e)}

    return results


def save_report(results):
    """保存 JSON 报告。"""
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    report = {
        "timestamp": datetime.now().isoformat(),
        "platform": sys.platform,
        "results": results,
        "summary": {
            "total": len(results),
            "passed": len([r for r in results.values() if r.get("score", 0) >= 0.85]),
            "warnings": len([r for r in results.values() if 0.7 <= r.get("score", 0) < 0.85]),
            "failed": len([r for r in results.values() if r.get("score", 0) < 0.7]),
        },
    }
    report_path = REPORT_DIR / f"report_{ts}.json"
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    log(f"\n📄 报告: {report_path}")

    # 打印摘要
    s = report["summary"]
    log(f"\n{'=' * 50}")
    log(f"  总计: {s['total']} | ✅ 通过: {s['passed']} | ⚠️ 警告: {s['warnings']} | ❌ 失败: {s['failed']}")
    log(f"{'=' * 50}")

    return report_path


def main():
    log("=" * 50)
    log("  Conduit MC — Windows E2E Auto Verify")
    log("=" * 50)

    # 1. 启动 Daemon
    start_daemon()

    # 2. 启动 Desktop
    if not start_desktop():
        log("❌ 无法启动 Desktop，终止")
        sys.exit(1)

    # 3. 截图 + 对比
    results = run_screenshots_and_compare()

    # 4. 保存报告
    save_report(results)

    log("\n✅ 全自动验证完成")


if __name__ == "__main__":
    main()
