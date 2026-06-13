"""
Claude Vision API 封装 — 通过代理发送截图并解析结果。

Usage:
    from vision_api import identify_elements, compare_screenshots

    # 识别 UI 元素
    elements = await identify_elements("screenshot.png",
        prompt="Find the 'New Server' button and return its center coordinates")
    # => [{"label": "New Server button", "x": 250, "y": 520, "confidence": 0.95}]

    # 对比两张截图
    diff = await compare_screenshots("actual.png", "expected.png")
    # => {"match_score": 0.87, "differences": [...]}
"""

import base64
import json
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests


def _call_vision_api(
    image_b64: str,
    prompt: str,
    system: Optional[str] = None,
    max_tokens: int = 8192,
) -> str:
    """
    调用 Claude Vision API，返回文本响应。

    优先使用 ANTHROPIC_AUTH_TOKEN（代理模式），
    回退到 ANTHROPIC_API_KEY（直连模式）。

    Args:
        image_b64: Base64 编码的图片。
        prompt: 用户提示词。
        system: 系统提示词（可选）。
        max_tokens: 最大输出 token。

    Returns:
        Claude 的文本响应。
    """
    api_key = os.environ.get("ANTHROPIC_AUTH_TOKEN") or os.environ.get("ANTHROPIC_API_KEY", "")
    base_url = os.environ.get("ANTHROPIC_BASE_URL", "http://127.0.0.1:15721").rstrip("/")
    model = os.environ.get("VISION_MODEL", "claude-sonnet-4-20250514")

    url = f"{base_url}/v1/messages"

    messages = [
        {
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": "image/png",
                        "data": image_b64,
                    },
                },
                {"type": "text", "text": prompt},
            ],
        }
    ]

    body: Dict[str, Any] = {
        "model": model,
        "max_tokens": max_tokens,
        "messages": messages,
    }
    if system:
        body["system"] = system

    headers = {
        "Content-Type": "application/json",
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
    }

    resp = requests.post(url, json=body, headers=headers, timeout=60)
    resp.raise_for_status()

    data = resp.json()
    # 某些代理模型（如 mimo-v2.5）会返回 thinking 块，
    # 需要同时支持 text 和 thinking 块的提取。
    # 优先取 text，没有则取 thinking（去掉签名后使用）。
    text_content = ""
    thinking_content = ""
    for block in data.get("content", []):
        if block.get("type") == "text":
            text_content += block["text"]
        elif block.get("type") == "thinking":
            thinking_content += block.get("thinking", "")

    return text_content or thinking_content


def _extract_json(text: str) -> Any:
    """从 Claude 响应中提取 JSON（处理 ```json 包裹的情况）。"""
    # 尝试直接解析
    text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # 尝试从 ```json ... ``` 中提取
    if "```json" in text:
        start = text.index("```json") + 7
        end = text.index("```", start)
        return json.loads(text[start:end].strip())

    # 尝试从 ``` ... ``` 中提取
    if "```" in text:
        start = text.index("```") + 3
        end = text.index("```", start)
        return json.loads(text[start:end].strip())

    # 尝试找第一个 { 或 [
    for i, ch in enumerate(text):
        if ch in "{[":
            # 找匹配的结束符
            close = "}" if ch == "{" else "]"
            depth = 0
            for j in range(i, len(text)):
                if text[j] == ch:
                    depth += 1
                elif text[j] == close:
                    depth -= 1
                    if depth == 0:
                        try:
                            return json.loads(text[i : j + 1])
                        except json.JSONDecodeError:
                            break
    raise ValueError(f"无法从响应中提取 JSON:\n{text}")


def identify_elements(
    image_path: Path,
    prompt: str,
    system: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """
    识别截图中的 UI 元素并返回坐标。

    Args:
        image_path: 截图文件路径。
        prompt: 描述要找什么元素。
        system: 可选的系统提示。

    Returns:
        [{"label": str, "x": int, "y": int, "width": int, "height": int}, ...]
    """
    from screenshot import get_image_dimensions, image_to_base64

    img_b64 = image_to_base64(image_path)
    w, h = get_image_dimensions(image_path)

    default_system = (
        f"你是一个 UI 自动化助手。截图尺寸为 {w}x{h} 像素。"
        "请返回 JSON 格式的元素列表，每个元素包含：label（描述）、x（中心 x 坐标）、y（中心 y 坐标）、"
        "width（宽度，像素估计）、height（高度，像素估计）。"
        "只返回 JSON，不要其他文字。"
    )

    raw = _call_vision_api(img_b64, prompt, system=system or default_system)
    result = _extract_json(raw)

    if isinstance(result, list):
        return result
    if isinstance(result, dict) and "elements" in result:
        return result["elements"]
    return [result] if isinstance(result, dict) else []


def compare_screenshots(
    actual_path: Path,
    expected_path: Path,
    criteria: Optional[str] = None,
) -> Dict[str, Any]:
    """
    对比两张截图（实际 vs 设计稿），返回差异分析。

    Args:
        actual_path: 实际应用截图。
        expected_path: 设计稿截图/参考图。
        criteria: 自定义对比标准。

    Returns:
        {
            "match_score": float (0-1),
            "overall_assessment": str,
            "differences": [{"area": str, "expected": str, "actual": str, "severity": str}],
            "layout_match": bool,
            "color_match": bool,
            "element_match": bool,
        }
    """
    from screenshot import image_to_base64

    actual_b64 = image_to_base64(actual_path)
    expected_b64 = image_to_base64(expected_path)

    api_key = os.environ.get("ANTHROPIC_AUTH_TOKEN") or os.environ.get("ANTHROPIC_API_KEY", "")
    base_url = os.environ.get("ANTHROPIC_BASE_URL", "http://127.0.0.1:15721").rstrip("/")
    model = os.environ.get("VISION_MODEL", "claude-sonnet-4-20250514")

    url = f"{base_url}/v1/messages"

    compare_prompt = criteria or (
        "对比这两张 UI 截图。第一张是实际应用截图，第二张是设计稿参考。\n\n"
        "请分析：\n"
        "1. 布局结构是否匹配（导航栏、列表面板、内容区位置）\n"
        "2. 配色方案是否一致（深色主题、强调色）\n"
        "3. UI 元素是否齐全（输入框、按钮、标签、分隔线）\n"
        "4. 间距和比例是否接近\n\n"
        "返回 JSON 格式：\n"
        '{\n'
        '  "match_score": 0.0-1.0 的浮点数,\n'
        '  "overall_assessment": "总体评价",\n'
        '  "differences": [{"area": "区域", "expected": "预期", "actual": "实际", "severity": "critical|major|minor|cosmetic"}],\n'
        '  "layout_match": true/false,\n'
        '  "color_match": true/false,\n'
        '  "element_match": true/false\n'
        '}'
    )

    messages = [
        {
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {"type": "base64", "media_type": "image/png", "data": actual_b64},
                },
                {
                    "type": "image",
                    "source": {"type": "base64", "media_type": "image/png", "data": expected_b64},
                },
                {"type": "text", "text": compare_prompt},
            ],
        }
    ]

    headers = {
        "Content-Type": "application/json",
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
    }

    resp = requests.post(
        url,
        json={"model": model, "max_tokens": 8192, "messages": messages},
        headers=headers,
        timeout=90,
    )
    resp.raise_for_status()

    data = resp.json()
    text = ""
    thinking = ""
    for block in data.get("content", []):
        if block.get("type") == "text":
            text += block["text"]
        elif block.get("type") == "thinking":
            thinking += block.get("thinking", "")
    text = text or thinking

    try:
        return _extract_json(text)
    except (ValueError, json.JSONDecodeError):
        return {
            "match_score": 0.0,
            "overall_assessment": text,
            "differences": [],
            "layout_match": False,
            "color_match": False,
            "element_match": False,
        }


def describe_screenshot(image_path: Path, language: str = "zh") -> str:
    """
    用自然语言描述截图内容（调试用）。
    """
    from screenshot import image_to_base64

    img_b64 = image_to_base64(image_path)
    prompt = (
        f"请用{'中文' if language == 'zh' else 'English'}详细描述这张 UI 截图的内容："
        "布局结构、可见元素、文本内容、按钮状态、颜色方案等。"
    )
    return _call_vision_api(img_b64, prompt)
