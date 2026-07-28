#!/usr/bin/env python3
"""Inject turn-scoped worklog requirements before each user prompt."""

from __future__ import annotations

import json
import sys
from typing import Any


MAX_TURN_ID_LENGTH = 512


def emit(payload: dict[str, Any]) -> None:
    json.dump(payload, sys.stdout, ensure_ascii=False)
    sys.stdout.write("\n")


def read_event() -> dict[str, Any] | None:
    try:
        value = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError):
        return None
    return value if isinstance(value, dict) else None


def valid_turn_id(value: Any) -> str | None:
    if not isinstance(value, str) or not value or len(value) > MAX_TURN_ID_LENGTH:
        return None
    if not value.isprintable() or "-->" in value:
        return None
    return value


def main() -> None:
    event = read_event()
    turn_id = valid_turn_id(event.get("turn_id")) if event else None

    if turn_id is None:
        emit(
            {
                "systemMessage": (
                    "UserPromptSubmit Hook이 유효한 turn_id를 받지 못했습니다. "
                    "이번 작업의 WORKLOG marker는 수동으로 확인해야 합니다."
                ),
                "hookSpecificOutput": {
                    "hookEventName": "UserPromptSubmit",
                    "additionalContext": (
                        "작업 종료 전에 docs/codex/WORKLOG.md 끝에 새 항목을 "
                        "append하고 docs/codex/CURRENT_STATE.md를 최신 상태로 "
                        "갱신하세요. Jira 이슈 키가 있으면 두 문서에 기록하고, "
                        "Secret과 Token은 기록하지 마세요."
                    ),
                },
            }
        )
        return

    marker = f"<!-- codex-turn:{turn_id} -->"
    instruction = (
        "이 작업을 종료하기 전에 반드시 다음을 수행하세요.\n"
        "1. docs/codex/WORKLOG.md의 과거 기록을 수정하거나 삭제하지 말고 "
        "파일 끝에 이번 작업의 새 항목을 append하세요.\n"
        "2. docs/codex/CURRENT_STATE.md를 이번 작업 결과에 맞게 갱신하세요.\n"
        "3. 새 WORKLOG 항목에 현재 marker를 정확히 한 번 포함하세요: "
        f"{marker}\n"
        "4. Jira 이슈 키가 있으면 WORKLOG와 CURRENT_STATE에 기록하세요.\n"
        "5. Secret과 Token은 기록하지 마세요."
    )
    emit(
        {
            "hookSpecificOutput": {
                "hookEventName": "UserPromptSubmit",
                "additionalContext": instruction,
            }
        }
    )


if __name__ == "__main__":
    main()
