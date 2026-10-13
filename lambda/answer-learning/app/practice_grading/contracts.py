from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Criterion(StrictModel):
    id: str = Field(min_length=1, max_length=80)
    label: str = Field(min_length=1, max_length=120)
    requirement: str = Field(min_length=1, max_length=1800)
    met_feedback: str = Field(min_length=1, max_length=240)
    missing_feedback: str = Field(min_length=1, max_length=240)


class Checklist(StrictModel):
    lesson_id: str
    version: str
    part: Literal[2, 3, 4, 5]
    question: str
    answer_seconds: int = Field(ge=15, le=60)
    source: str = Field(max_length=12000)
    items: list[Criterion] = Field(min_length=1, max_length=8)
    met_threshold: float = Field(default=0.75, ge=0, le=1, allow_inf_nan=False)
    not_met_threshold: float = Field(default=0.25, ge=0, le=1, allow_inf_nan=False)

    @model_validator(mode="after")
    def unique_ids(self):
        if len({item.id for item in self.items}) != len(self.items):
            raise ValueError("Checklist IDs must be unique")
        if self.not_met_threshold >= self.met_threshold:
            raise ValueError("Checklist decision thresholds must not overlap")
        return self


class SubmissionInput(StrictModel):
    submission_id: UUID
    lesson_id: str = Field(min_length=1, max_length=200)
    level: Literal["IM3", "IH", "AL"]


class JudgmentItem(StrictModel):
    id: str
    probability: float = Field(strict=True, ge=0, le=1, allow_inf_nan=False)


class Judgment(StrictModel):
    items: list[JudgmentItem]
    resolved_model: str = ""
    request_id: str = ""
    cost_usd: float | None = Field(default=None, ge=0, allow_inf_nan=False)
    input_tokens: int | None = Field(default=None, strict=True, ge=0)


class PracticeError(Exception):
    def __init__(self, code: str, message: str, status: int = 400, retryable: bool = False):
        super().__init__(message)
        self.code, self.message, self.status, self.retryable = code, message, status, retryable


def build_result(checklist: Checklist, transcript: str, judgment: Judgment) -> dict:
    ids = [item.id for item in judgment.items]
    if len(ids) != len(set(ids)) or set(ids) != {item.id for item in checklist.items}:
        raise PracticeError("INVALID_JUDGMENT", "채점 결과의 항목이 맞지 않아요.", 502, True)
    indexed = {item.id: item for item in judgment.items}
    results = []
    for criterion in checklist.items:
        decision = indexed[criterion.id]
        verdict = ("met" if decision.probability >= checklist.met_threshold else
                   "not_met" if decision.probability <= checklist.not_met_threshold else "uncertain")
        feedback = {
            "met": criterion.met_feedback,
            "not_met": criterion.missing_feedback,
            "uncertain": "인식된 내용만으로는 이 항목을 확인하기 어려워요.",
        }[verdict]
        results.append({"id": criterion.id, "label": criterion.label,
                        "verdict": verdict, "probability": decision.probability, "feedback": feedback})
    return {"checklistVersion": checklist.version, "transcript": transcript,
            "metCount": sum(item["verdict"] == "met" for item in results),
            "totalCount": len(results), "items": results,
            "decisionPolicy": {"metThreshold": checklist.met_threshold,
                               "notMetThreshold": checklist.not_met_threshold},
            "judgeResponse": {"model": judgment.resolved_model,
                              "requestId": judgment.request_id, "costUsd": judgment.cost_usd,
                              "inputTokens": judgment.input_tokens}}
