# 난이도별 최근 풀이 이어하기 R2

## 2026-10-27 변경 기준 (아래 종전 R2 설명보다 우선)

- 제출 접수 대신 `status=completed`이고 `result`가 있는 제출의 `updatedAt`(LC 결과 저장 시각)을 비교한다. 채점 대기/실패/무음은 제외하며 늦게 생성된 결과도 최근 풀이가 된다.
- 후속 completionPending 정리는 updatedAt을 바꾸지 않는다. 기존 데이터의 updatedAt은 마지막 완료 복구 시각일 수도 있으며 과거 정확한 생성시각을 소급 복원하지 않는다. 신규 컬렉션/인덱스/DB 보정은 없다.
- 후속 승인: `END_OF_PART`는 다음 공개 파트의 첫 세트 첫 문항을 반환한다. 마지막 Part 5까지 완료하면 첫 공개 파트(정상 카탈로그의 Part 1)의 첫 세트 첫 문항으로 순환한다. `NO_HISTORY`도 첫 공개 파트 첫 세트 첫 문항을 반환한다. 이미 완료한 다음 위치도 건너뛰지 않는다.
- 상태 이름은 유지하되 세 상태 모두 유효한 위치를 반환한다. 앱은 READY뿐 아니라 NO_HISTORY/END_OF_PART에서도 CTA를 표시하고 unitId/lessonId로 이동한다.
- Part 1 명시 완료·현재 버전/사용자/난이도·기존 part 호출 호환은 유지한다. 아래는 변경 전 R2 이력이다.

## 5줄 결론

- 단일 CTA는 `GET /api/v1/learning/resume?level=IH`, part를 보내지 않는다.
- 최근 **제출 접수** 문항의 다음 문항으로 이동한다. 채점 완료 순서는 사용하지 않는다.
- 세트 마지막 다음은 같은 파트의 다음 세트 첫 문항이다. 파트 끝에서 자동 다른 파트 이동/순환은 하지 않는다.
- 기존 part 쿼리를 보내면 R1의 unitId/allCompleted 응답을 그대로 유지한다.
- 기존 저장소·인증·삭제를 재사용하며 Billing 조회/새 컬렉션/새 인덱스/화면 진입 API는 추가하지 않는다.

## 프론트 사용

인증 사용자 토큰과 level(IM3/IH/AL)만 전달한다. BaseResponse COMMON_200 안의 result:

```json
{"status":"READY","level":"IH","part":3,"unitId":"unit-3-2","unitNumber":2,"lessonId":"lesson-3-2-3","lessonNumber":3,"questionNumber":7}
```

- `READY`: `IH · Part 3 · 2세트 · 3번 문항 이어하기` CTA. unitId로 기존 상세 API를 연 뒤 lessonId로 이동한다.
- `unitNumber`: 현재 공개 세트의 파트 내 sortOrder/_id 정렬 순번(1부터).
- `lessonNumber`: 해당 세트 lessonIds 내 순번(1부터). 기존 콘텐츠 questionNumber와 구분한다.
- `NO_HISTORY`: 현재 버전의 접수/Part1 완료 이력이 없다. 위치 6필드는 null, CTA 숨김.
- `END_OF_PART`: 최근 풀이가 그 파트 마지막 문항이다. 위치 6필드는 null, CTA 숨김/파트 선택 안내. 전체 파트를 완료했다는 뜻이 아니다.
- 최근 문항 바로 다음을 안내하며 이미 푼 다음 문항을 건너뛰지 않는다. 이전 문항 재학습도 최근 풀이로 반영된다.
- 유료 본문/음성 제공 검사는 기존 상세/음성 API에 남는다. resume 자체는 무차감·무권한조회 메타정보다.

## 최근 풀이 근거

- Part2~5: 인증 사용자·선택 level·현재 contentVersion·현재 공개 문항 중 learning_submissions의 최초 queuedAt. audio 준비/실제 queue 접수 전은 제외한다.
- queuedAt은 기존 queue CAS에서만 설정된다. 새 제출은 갱신되지만 동일 submit 재호출/복구 retry/worker 완료는 순서를 바꾸지 않는다. queued/failed/no_speech/completed도 최초 접수 사실은 동일하다.
- Part1: 제출이 없으므로 기존 완료 PUT에서 learning_completions.lastPracticedAt을 $max로 갱신한다. 최초 completedAt은 보존하고 기존 자료는 이 시각을 fallback으로 사용한다.
- Part1은 기존 계약상 common이므로 모든 선택 난이도에 같은 최근 Part1 이력을 적용한다. 완료 PUT 재호출도 사용자 활동으로 취급하며 별도 새 멱등키를 만들지 않는다.
- 기존 UserMerged 소유권 이전과 삭제 collection 정리를 재사용한다. 완료 중복 merge는 최초 completedAt과 최신 lastPracticedAt을 각각 보존한다.
- 동시 시각은 고정 식별자 순서로 결정한다. 사용자별 기존 인덱스를 사용하며 추가 자료 이전/인덱스 설치 없음. 대규모 사용자 이력에서 정렬 비용은 운영 관측 대상이다.

## 오류·검증·범위

- 400 INVALID_REQUEST: 잘못된 level/part. part 없는 common은 거절.
- 401 인증·403 소유권/기존 삭제·탈퇴 제한 유지.
- 404 LEARNING_NOT_FOUND: 현재 공개 콘텐츠 없음.
- 503 LEARNING_UNAVAILABLE: 누락/중복/잘못 연결된 콘텐츠, 조회 실패. 이력 없음으로 숨기지 않는다.
- 현재 버전 이력만 사용하며 과거 버전을 임의 이전하지 않는다.
- 검증 대상: 동일 세트/다음 세트/다른 최근 파트/파트 끝/무이력/현재버전·사용자·level 격리, 접수 전/채점 후 순서, Part1 재학습, merge, 기존 part 응답/Swagger.
- 테스트 서버 전달만 승인됨. 운영 배포·기능 설정 변경·실제 개인정보 검증은 포함하지 않는다.
