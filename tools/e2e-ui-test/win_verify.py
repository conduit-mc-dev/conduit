#!/usr/bin/env python3
"""
Windows VM 快速验证脚本。
在 VMware 控制台 / RDP 桌面会话中运行（SSH 会话无法截图）。

Usage:
    python tools/e2e-ui-test/win_verify.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))


def main():
    print("=" * 50)
    print("  Conduit MC — Windows E2E Verify")
    print("=" * 50)

    errors = []

    # 1. Platform driver
    print("\n[1/6] Platform detection...")
    try:
        from platform import get_driver
        driver = get_driver()
        print(f"  OK: {type(driver).__name__}")
    except Exception as e:
        print(f"  FAIL: {e}")
        errors.append(("platform", str(e)))
        return errors

    # 2. Permissions
    print("\n[2/6] Permissions...")
    try:
        perms = driver.check_permissions()
        print(f"  OK: {perms}")
    except Exception as e:
        print(f"  FAIL: {e}")
        errors.append(("permissions", str(e)))

    # 3. Window discovery
    print("\n[3/6] Window discovery...")
    try:
        windows = driver.find_windows("Conduit MC")
        if windows:
            for w in windows:
                print(f"  OK: id={w.native_id} title={w.title} bounds={w.bounds}")
        else:
            print("  WARN: No Conduit MC window found (app may not be running)")
            # 也试试 MainKt
            windows2 = driver.find_windows("MainKt")
            if windows2:
                for w in windows2:
                    print(f"  OK: id={w.native_id} title={w.title} bounds={w.bounds}")
    except Exception as e:
        print(f"  FAIL: {e}")
        errors.append(("window_discovery", str(e)))

    # 4. Screenshot
    print("\n[4/6] Screenshot...")
    try:
        from screenshot import capture_screen
        shot = capture_screen()
        from PIL import Image
        img = Image.open(str(shot))
        print(f"  OK: {img.size} ({shot.stat().st_size // 1024} KB)")
    except Exception as e:
        print(f"  FAIL: {e}")
        errors.append(("screenshot", str(e)))

    # 5. Mouse
    print("\n[5/6] Mouse...")
    try:
        from mouse import get_position
        pos = get_position()
        print(f"  OK: position={pos}")
    except Exception as e:
        print(f"  FAIL: {e}")
        errors.append(("mouse", str(e)))

    # 6. Vision API
    print("\n[6/6] Vision API connection...")
    try:
        from vision_api import _call_vision_api
        import base64
        # 用一个小的 1x1 白色图片测试 API 连通性
        from PIL import Image
        import io
        img = Image.new('RGB', (10, 10), 'white')
        buf = io.BytesIO()
        img.save(buf, format='PNG')
        b64 = base64.b64encode(buf.getvalue()).decode()
        result = _call_vision_api(b64, "Say 'OK' in one word")
        print(f"  OK: response={result[:50]}")
    except Exception as e:
        print(f"  FAIL: {e}")
        errors.append(("vision_api", str(e)))

    # Summary
    print("\n" + "=" * 50)
    if not errors:
        print("  ALL TESTS PASSED")
    else:
        print(f"  {len(errors)} FAILURES:")
        for name, err in errors:
            print(f"    - {name}: {err[:80]}")
    print("=" * 50)

    return errors


if __name__ == "__main__":
    errors = main()
    sys.exit(1 if errors else 0)
