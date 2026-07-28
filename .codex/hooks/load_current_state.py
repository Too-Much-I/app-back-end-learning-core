#!/usr/bin/env python3
"""Inject the Learning Core CURRENT_STATE at session startup or resume."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


def emit(payload: dict[str, Any]) -> None:
    json.dump(payload, sys.stdout, ensure_ascii=False)
    sys.stdout.write("\n")


def repository_root() -> Path | None:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=Path.cwd(),
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return None

    root = result.stdout.strip()
    if result.returncode != 0 or not root:
        return None
    return Path(root)


def main() -> None:
    root = repository_root()
    if root is None:
        return

    state_path = root / "docs" / "codex" / "CURRENT_STATE.md"
    try:
        current_state = state_path.read_text(encoding="utf-8")
    except OSError:
        return

    emit(
        {
            "hookSpecificOutput": {
                "hookEventName": "SessionStart",
                "additionalContext": (
                    "다음은 Learning Core 저장소의 최신 상태입니다. "
                    "현재 사용자 요청과 함께 작업 기준으로 사용하세요.\n\n"
                    f"{current_state}"
                ),
            }
        }
    )


if __name__ == "__main__":
    main()
