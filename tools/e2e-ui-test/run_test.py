#!/usr/bin/env python3
"""
E2E UI 验证 — 主测试流程。

Usage:
    source tools/e2e-ui-test/.venv/bin/activate

    # 完整测试（启动应用 → 导航 → 截图 → 对比）
    python tools/e2e-ui-test/run_test.py

    # 应用已在运行时跳过启动
    python tools/e2e-ui-test/run_test.py --skip-launch

    # 仅截图（后台截图应用窗口）
    python tools/e2e-ui-test/run_test.py --screenshot-only

    # 对比两张已有截图
    python tools/e2e-ui-test/run_test.py --compare actual.png expected.png

    # 批量渲染所有 mockup
    python tools/e2e-ui-test/run_test.py --render-mockups
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from datetime import datetime
from pathlib import Path

# 确保可以 import 同目录模块
sys.path.insert(0, str(Path(__file__).parent))

from config import APP_WINDOW_TITLE, SCREENSHOT_DIR
from screenshot import capture_app_window, capture_screen, ScreenCaptureError
from vision_api import compare_screenshots, describe_screenshot
from navigate import navigate_to_create_instance, render_mockup_reference, render_all_mockups
from app_manager import launch_app, wait_for_app, kill_app


def setup_report_dir() -> Path:
    """创建本次测试的报告目录。"""
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    report_dir = SCREENSHOT_DIR / f"test_{ts}"
    report_dir.mkdir(parents=True, exist_ok=True)
    return report_dir


def run_full_test(skip_launch: bool = False) -> dict:
    """完整 E2E 测试流程。"""
    report_dir = setup_report_dir()
    result = {
        "test_name": "Create Instance UI Verification",
        "timestamp": datetime.now().isoformat(),
        "report_dir": str(report_dir),
        "steps": [],
        "overall": "PENDING",
    }

    try:
        # ── Step 0: 启动应用 ──
        if not skip_launch:
            print("🚀 Step 0: 启动 Desktop 应用")
            launch_app(wait_time=8)
            if not wait_for_app(timeout=15):
                result["overall"] = "FAIL"
                result["error"] = "应用启动失败"
                return result
        else:
            print("⏭️  跳过应用启动（假设已在运行）")

        # ── Step 1: 导航到 Create Instance ──
        print("\n" + "=" * 60)
        print("📋 Step 1: 导航到 Create Instance 页面")
        print("=" * 60)

        create_screenshot = navigate_to_create_instance()
        if create_screenshot is None:
            result["overall"] = "FAIL"
            result["error"] = "导航到 Create Instance 失败"
            return result

        report_screenshot = report_dir / "create_instance.png"
        shutil.copy2(create_screenshot, report_screenshot)
        result["steps"].append({
            "name": "navigate_to_create_instance",
            "status": "PASS",
            "screenshot": str(report_screenshot),
        })

        # ── Step 2: 渲染 Mockup 参考图 ──
        print("\n" + "=" * 60)
        print("📋 Step 2: 准备设计稿参考图")
        print("=" * 60)

        mockup_ref = render_mockup_reference(report_dir / "mockup_reference.png")

        if mockup_ref and mockup_ref.exists():
            # ── Step 3: Vision 对比 ──
            print("\n" + "=" * 60)
            print("📋 Step 3: Claude Vision 对比分析")
            print("=" * 60)

            print("  🔄 对比实际截图 vs 设计稿...")
            diff_result = compare_screenshots(report_screenshot, mockup_ref)

            result["steps"].append({
                "name": "vision_compare",
                "status": "PASS",
                "result": diff_result,
            })

            score = diff_result.get("match_score", 0)
            print(f"\n  📊 匹配分数: {score:.1%}")
            print(f"  📝 总体评价: {diff_result.get('overall_assessment', 'N/A')}")

            diffs = diff_result.get("differences", [])
            if diffs:
                print(f"\n  🔍 差异 ({len(diffs)} 项):")
                for d in diffs:
                    severity = d.get("severity", "unknown")
                    icon = {"critical": "🔴", "major": "🟡", "minor": "🔵", "cosmetic": "⚪"}.get(severity, "❓")
                    print(f"    {icon} [{severity}] {d.get('area', 'N/A')}")

            layout = diff_result.get("layout_match", False)
            color = diff_result.get("color_match", False)
            element = diff_result.get("element_match", False)
            print(f"\n  📐 布局匹配: {'✅' if layout else '❌'}")
            print(f"  🎨 配色匹配: {'✅' if color else '❌'}")
            print(f"  🧩 元素匹配: {'✅' if element else '❌'}")

            if score >= 0.85:
                result["overall"] = "PASS"
            elif score >= 0.6:
                result["overall"] = "WARN"
            else:
                result["overall"] = "FAIL"
        else:
            print("\n⚠️  无设计稿参考图，跳过对比。进行结构分析...")
            analysis = describe_screenshot(report_screenshot)
            result["steps"].append({
                "name": "structure_analysis",
                "status": "INFO",
                "analysis": analysis,
            })
            print(f"\n  📝 页面分析:\n{analysis[:500]}")
            result["overall"] = "INFO"

    except ScreenCaptureError as e:
        result["overall"] = "ERROR"
        result["error"] = str(e)
        print(f"\n❌ 截图错误: {e}")
    except Exception as e:
        result["overall"] = "ERROR"
        result["error"] = str(e)
        import traceback
        traceback.print_exc()

    finally:
        report_path = report_dir / "report.json"
        with open(report_path, "w") as f:
            json.dump(result, f, indent=2, ensure_ascii=False)
        print(f"\n📄 报告已保存: {report_path}")

    return result


def run_screenshot_only() -> None:
    """仅截图应用窗口并描述。"""
    print("📸 后台截图模式")
    try:
        shot = capture_app_window()
        print(f"截图保存: {shot}")
        print("\n🔍 Vision 分析当前页面...")
        desc = describe_screenshot(shot)
        print(f"\n{desc}")
    except ScreenCaptureError as e:
        print(f"❌ 截图失败: {e}")
        print("尝试全屏截图...")
        shot = capture_screen()
        print(f"全屏截图保存: {shot}")


def run_compare_only(actual: str, expected: str) -> None:
    """仅对比两张已有截图。"""
    actual_path = Path(actual)
    expected_path = Path(expected)

    if not actual_path.exists():
        print(f"❌ 文件不存在: {actual_path}")
        sys.exit(1)
    if not expected_path.exists():
        print(f"❌ 文件不存在: {expected_path}")
        sys.exit(1)

    print(f"🔄 对比: {actual_path.name} vs {expected_path.name}")
    result = compare_screenshots(actual_path, expected_path)
    print(json.dumps(result, indent=2, ensure_ascii=False))


def run_render_mockups() -> None:
    """批量渲染所有 mockup。"""
    print("🎨 批量渲染 Mockup...")
    results = render_all_mockups()
    print(f"\n渲染结果:")
    for name, path in sorted(results.items()):
        print(f"  ✅ {name} → {path}")


def main():
    parser = argparse.ArgumentParser(description="Conduit Desktop E2E UI 验证")
    parser.add_argument("--skip-launch", action="store_true", help="跳过应用启动")
    parser.add_argument("--screenshot-only", action="store_true", help="仅截图并分析")
    parser.add_argument("--compare", nargs=2, metavar=("ACTUAL", "EXPECTED"), help="对比两张截图")
    parser.add_argument("--render-mockups", action="store_true", help="批量渲染所有 mockup")

    args = parser.parse_args()

    print("╔══════════════════════════════════════════════════╗")
    print("║  Conduit Desktop — E2E UI Verification          ║")
    print("╚══════════════════════════════════════════════════╝\n")

    if args.screenshot_only:
        run_screenshot_only()
    elif args.compare:
        run_compare_only(*args.compare)
    elif args.render_mockups:
        run_render_mockups()
    else:
        result = run_full_test(skip_launch=args.skip_launch)
        print(f"\n{'=' * 60}")
        print(f"🏁 最终结果: {result['overall']}")
        print(f"{'=' * 60}")

        if result["overall"] == "PASS":
            sys.exit(0)
        elif result["overall"] == "FAIL":
            sys.exit(1)
        else:
            sys.exit(2)


if __name__ == "__main__":
    main()
