"""Backend-only grading intake and worker. Never accepts public app credentials."""
import hashlib
import json
import math
import re
import time
from pathlib import Path
from typing import Literal
from uuid import UUID

from pydantic import Field, model_validator

from app.practice_grading.contracts import Checklist, PracticeError, StrictModel, build_result

MAX_AUDIO_BYTES = 8 * 1024 * 1024
LEASE_SECONDS = 180
TERMINAL = {"completed", "failed", "no_speech"}


class Audio(StrictModel):
    bucket: str = Field(min_length=3, max_length=63)
    key: str = Field(min_length=1, max_length=1024)
    etag: str = Field(pattern=r'^[a-fA-F0-9]{32}(?:-[0-9]+)?$')
    sha256: str = Field(pattern=r'^[a-f0-9]{64}$')


class Evaluation(StrictModel):
    submissionId: UUID
    attempt: int = Field(default=1, strict=True, ge=1, le=3)
    lessonId: str = Field(min_length=1, max_length=200)
    level: Literal["IM3", "IH", "AL"]
    contentVersion: str = Field(min_length=1, max_length=100)
    audio: Audio
    checklist: Checklist

    @model_validator(mode="after")
    def matching_lesson(self):
        if self.lessonId != self.checklist.lesson_id:
            raise ValueError("Checklist lesson does not match")
        if len(self.checklist.version) > 100 or len(self.checklist.question) > 6000:
            raise ValueError("Checklist text is too long")
        return self

    @property
    def job_id(self):
        return f"{self.submissionId}:{self.attempt}"

    @property
    def fingerprint(self):
        payload = self.model_dump(mode="json", exclude={"attempt"})
        return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def public_job(job):
    body = job["payload"]
    return {"id": body["submissionId"], "jobId": job["id"], "attempt": body["attempt"],
            "lessonId": body["lessonId"], "level": body["level"],
            "contentVersion": body["contentVersion"], "status": job["status"],
            "result": job.get("result"), "error": job.get("error")}


def error_value(code, message, retryable=False):
    return {"code": code, "message": message, "retryable": retryable}


def validate_audio(path, seconds):
    from mutagen.mp4 import MP4
    try:
        info = MP4(path).info
        if (not math.isfinite(info.length) or not 3 <= info.length <= seconds + 5
                or info.channels not in (1, 2) or not info.codec.startswith("mp4a")):
            raise ValueError("Unexpected audio format")
    except Exception as exc:
        raise PracticeError("INVALID_AUDIO", "녹음 파일의 형식이나 길이를 확인해 주세요.", 422) from exc


class Service:
    def __init__(self, store, media, queue, *, bucket, prefix, clock=time.time):
        self.store, self.media, self.queue = store, media, queue
        self.bucket, self.prefix, self.clock = bucket, prefix, clock

    def get(self, job_id):
        if not re.fullmatch(r"[0-9a-f-]{36}:[1-3]", job_id):
            raise PracticeError("NOT_FOUND", "제출을 찾을 수 없어요.", 404)
        job = self.store.get(job_id)
        if not job or job["status"] == "deleted":
            raise PracticeError("NOT_FOUND", "제출을 찾을 수 없어요.", 404)
        if job["status"] in ("transcribing", "grading") and job.get("leaseUntil", 0) < self.clock():
            self.store.expire(job_id, int(self.clock()))
            job = self.store.get(job_id)
            if not job or job["status"] == "deleted":
                raise PracticeError("NOT_FOUND", "제출을 찾을 수 없어요.", 404)
        return job

    def submit(self, request):
        if request.audio.bucket != self.bucket or request.audio.key != (
            f"{self.prefix}{request.submissionId}/recording.m4a"
        ):
            raise PracticeError("INVALID_AUDIO_LOCATION", "허용된 학습 녹음 경로가 아닙니다.", 422)
        existing = self.store.get(request.job_id)
        if existing:
            if existing["status"] == "deleted":
                raise PracticeError("SUBMISSION_DELETED", "삭제된 제출입니다.", 409)
            if existing["fingerprint"] != request.fingerprint:
                raise PracticeError("IDEMPOTENCY_CONFLICT", "같은 제출 ID의 내용이 달라요.", 409)
            # Re-send repairs a failed/ambiguous queue send. Atomic worker claim prevents double grading.
            if existing["status"] == "queued":
                self.queue.send(request.job_id, str(request.submissionId))
            return self.get(request.job_id)
        transcript = ""
        if request.attempt > 1:
            previous = self.get(f"{request.submissionId}:{request.attempt - 1}")
            if previous["fingerprint"] != request.fingerprint:
                raise PracticeError("IDEMPOTENCY_CONFLICT", "재시도 내용이 원래 제출과 달라요.", 409)
            if previous["status"] != "failed" or not (previous.get("error") or {}).get("retryable"):
                raise PracticeError("RETRY_NOT_ALLOWED", "이 제출은 재시도할 수 없어요.", 409)
            transcript = previous.get("transcript", "")
        self.media.check(request.audio)
        now = int(self.clock())
        job = {"id": request.job_id, "fingerprint": request.fingerprint,
               "payload": request.model_dump(mode="json"), "status": "queued",
               "transcript": transcript, "createdAt": now, "expiresAt": now + 7 * 86400,
               "result": None, "error": None}
        self.store.create(job)
        saved = self.store.get(request.job_id)
        if saved["status"] == "deleted":
            raise PracticeError("SUBMISSION_DELETED", "삭제된 제출입니다.", 409)
        if saved["fingerprint"] != request.fingerprint:
            raise PracticeError("IDEMPOTENCY_CONFLICT", "같은 제출 ID의 내용이 달라요.", 409)
        self.queue.send(request.job_id, str(request.submissionId))
        return saved

    def purge(self, submission_id):
        if str(UUID(submission_id)) != submission_id:
            raise ValueError("Invalid submission ID")
        self.store.purge(submission_id, int(self.clock()))
        return {"submissionId": submission_id, "purged": True}

    def process(self, job_id, provider_factory, audio_validator=validate_audio):
        try:
            return self._process(job_id, provider_factory, audio_validator)
        except PracticeError as exc:
            # Deleted/expired receipts have no work left. A late SQS delivery must be acknowledged.
            if exc.code == "NOT_FOUND":
                return True
            raise

    def _process(self, job_id, provider_factory, audio_validator):
        job = self.get(job_id)
        if job["status"] in TERMINAL:
            return True
        token = self.store.claim(job_id, int(self.clock()) + LEASE_SECONDS)
        if not token:
            return self.get(job_id)["status"] in TERMINAL
        job = self.store.get(job_id)
        if job["status"] != "transcribing":
            return job["status"] == "deleted" or job["status"] in TERMINAL
        request = Evaluation.model_validate(job["payload"])
        path = Path("/tmp") / f"practice-{request.submissionId}-{request.attempt}.m4a"
        try:
            provider = provider_factory()
            provider.require_ready()
            transcript = job.get("transcript", "")
            if not transcript:
                self.media.download(request.audio, path)
                audio_validator(path, request.checklist.answer_seconds)
                transcript = provider.transcribe(path).strip()
                if not transcript:
                    self.store.update(job_id, lease_token=token, status="no_speech")
                    return True
                if len(transcript) > 12000:
                    raise PracticeError("TRANSCRIPT_TOO_LONG", "녹음한 답변을 확인해 주세요.", 422)
                if not self.store.update(job_id, lease_token=token, status="grading", transcript=transcript):
                    return True
            else:
                if not self.store.update(job_id, lease_token=token, status="grading"):
                    return True
            judgment = provider.judge(request.checklist, transcript, request.level)
            result = build_result(request.checklist, transcript, judgment)
            result["models"] = {"stt": provider.settings.stt_model, "judge": provider.settings.judge_model}
            self.store.update(job_id, lease_token=token, status="completed", result=result, error=None)
        except Exception as exc:
            # Persist only bounded error codes; provider exceptions may contain sensitive data.
            if isinstance(exc, PracticeError):
                error = error_value(exc.code, exc.message, exc.retryable)
            else:
                error = error_value("PROCESSING_FAILED", "답변 처리에 실패했어요. 다시 제출해 주세요.", True)
            if request.attempt == 3:
                error["retryable"] = False
            self.store.update(job_id, lease_token=token, status="failed", error=error)
        finally:
            path.unlink(missing_ok=True)
        return True
