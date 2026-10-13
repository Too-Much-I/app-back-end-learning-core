import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import dotenv_values


ROOT = Path(__file__).resolve().parents[2]
OPENAI_DECISIONS_MODEL = "gpt-6-luna"


@dataclass(frozen=True)
class PracticeSettings:
    data_dir: Path
    checklist_dir: Path
    token: str
    openai_key: str
    stt_model: str
    judge_model: str

    @classmethod
    def load(cls):
        # Read selected settings only; never enable production tracing, Redis or callbacks.
        base = dotenv_values(ROOT / ".env")
        local = dotenv_values(ROOT / ".env.practice.local")
        values = {**local, **os.environ}
        openai_key = values.get("OPENAI_API_KEY") or base.get("OPENAI_API_KEY") or ""
        return cls(
            data_dir=Path(values.get("PRACTICE_DATA_DIR", str(ROOT / ".local/practice-grading"))),
            checklist_dir=Path(values.get("PRACTICE_CHECKLIST_DIR", str(ROOT / "data/practice_checklists/full-bank/runtime"))),
            token=values.get("PRACTICE_PREVIEW_TOKEN", ""),
            openai_key=openai_key,
            stt_model=values.get("PRACTICE_STT_MODEL") or base.get("OPENAI_TRANSCRIBE_MODEL") or "gpt-transcribe",
            judge_model=values.get("PRACTICE_JUDGE_MODEL", OPENAI_DECISIONS_MODEL),
        )

    def missing(self) -> list[str]:
        return [name for name, value in [
            ("OPENAI_API_KEY", self.openai_key),
            ("PRACTICE_JUDGE_MODEL", self.judge_model == OPENAI_DECISIONS_MODEL),
        ] if not value]
