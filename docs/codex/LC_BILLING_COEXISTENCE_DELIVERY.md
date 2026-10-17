# 심사 기간 공존 R2 구현·test 전달

## 2026-10-17 실행 결과

- 제품 commit `e817ae8`, PR [#48](https://github.com/Too-Much-I/app-back-end-learning-core/pull/48), develop merge `f2111e556c6b79cad0b377020fe7d5b2f2db0637`.
- [배포 run 39032470211](https://github.com/Too-Much-I/app-back-end-learning-core/actions/runs/39032470211) 성공. 기존 test task42 → 코드 task43 → 공존 flag task44 순서로 적용했다.
- 최종 `tosunsaeng-learning-core-test:44`: `COMPLETED`, running1/pending0. 8080/8444 새 대상 healthy, 공개 health200. v1/v2 무토큰401, OpenAPI의 두 POST와 v2 Idempotency-Key 확인.
- 이미지 `sha256:54db3079ffd23d7f24bf4d53e0e18b222d075750d565114748eda2a72881250d` 유지. task43 대비 task44 변경은 `BILLING_LEGACY_EXAM_CREATE_ENABLED=true` 하나임을 전체 등록 필드 비교로 확인했다. Secret 값 출력/교체·IAM/네트워크/DB 변경 없음.
- 전체 Mongo333개 통과. 실제 사용자 권리 소비·시험 생성·AI 채점 공동 E2E는 수행하지 않았다. 다음 작업은 앱의 v2 고정 연결과 실제 양측 시나리오 확인이다.
- DB 준비 스크립트·운영 준비 문서와 DS_Store는 기존 로컬 변경으로 보존했으며 이 제품 배포에 포함하지 않았다. 예상 밖의 제품 변경 없음.
- 최초 task 등록은 빈 tags 옵션으로 API에서 거절돼 서비스 변경 없이 끝났다. 빈 옵션 제거 후44등록·배포 성공했다. 배포 중 일시 unhealthy는 기동 후 해소됐으며 검증 없이 설정을 완화하지 않았다.
- 이 사후 실행 기록은 작업 브랜치의 문서 전용 추가 commit으로 전달한다. 배포된 제품 코드는 위 merge와 동일하며 문서 기록을 위해 추가 서버 배포하지 않는다.
- 최종 ECS rollout은 COMPLETED지만 ALB 목록에는 이전 target의 잔여 draining 표시가 있었다. 새 대상은 두 포트 모두 healthy이며 잔여 target 항목의 완전 제거를 배포 검증 결과로 주장하지 않는다.

## 5줄 결론

1. 구앱은 기존 `POST /api/v1/exams`, 신앱·심사 앱은 `POST /api/v2/exams`를 사용한다. 생성 외 API는 공유한다.
2. v1은 `BILLING_LEGACY_EXAM_CREATE_ENABLED=true`일 때만 무료 생성하며 Billing을 호출하지 않는다. 기본 false다.
3. v2는 기존 paid saga·필수 UUID v4 key를 사용한다. OFF/오류/권리 부족 시 무료로 fallback하지 않는다.
4. 무료 Session의 `creationOrigin=TRANSITION_FREE_V1` 및 기존 접수 증거를 저장한다. 봉인 목록/fixture/Billing wire는 변경하지 않는다.
5. LC 코드 검증과 실제 양측 결제·채점 E2E는 별도다. 이번 전달은 test만 대상으로 하며 prod/Billing/Identity는 변경하지 않는다.

## 사용자가 알아야 할 동작

- v1 OFF 또는 진행 중 Billing 시험/미완료 operation이 있으면 기존 `409 EXAM_CREATION_PROCESSING`, `Retry-After: 1`이다. v1의 key는 paid replay에 사용하지 않는다.
- 이전 무료 시험이 부분 접수면 같은 문제, 전체 필수 retry0 답안 접수면 다음 선정 정책이다. 채점·Summary 완료를 기다리지 않는다.
- v1 재시작은 같은 사용자 Transaction에서 기존 신규 제출 차단과 새 Session 저장을 처리한다. 기접수 근거와 기존 결과는 유지한다.
- 무료→paid는 로컬 원대상만 operation에 고정한 일반 INITIAL이다. Billing confirm 이후에만 원 신규 제출 차단·새 활성·operation 성공을 함께 반영한다. 복원권을 소급 생성하지 않는다.
- 완료/폐기된 Billing 이력은 영구 계정 차단 사유가 아니다. 진행 중/불명 상태는 active=false여도 무료 교체하지 않는다.
- 삭제·탈퇴·소유권 guard와 같은 Transaction fence를 사용한다. 무료 시험에는 Billing group이 없으므로 별도 Billing 삭제 ACK 대상이 아니다.
- 업데이트 실제 다운로드 가능 확인 후 기존 버전 검사를 적용하고 무료 생성 flag를 OFF로 배포한다. 이미 만든 무료 시험은 공유 API에서 계속 처리한다.

## 코드와 검증 범위

- `TransitionFreeExamService`: 전용 무료 writer, owner/delete/withdrawal/paid guard, 단일 문제 선정·기존 제출 차단.
- `PaidExamRestController`, `ExamServiceImpl`: 생성 route 분리와 성공 DTO/Redis 후처리 재사용.
- `ExamSession`, `LegacyExamRuntime`: 무료 출처 검증; Billing 혼합/미지원/근거 누락은 거절.
- `PaidExamPreparationService`, `PaidReservationProtocol`, `PaidExamLifecycleService`: exact 로컬 무료 원대상 snapshot과 confirm 이후 마무리.
- 단위/HTTP 계약 테스트 및 격리 Mongo의 무료 재시작·전체 접수·paid guard·삭제 선행·동시 생성·무료→paid confirm 경계를 검증한다.

## 배포 및 남은 확인

- 로컬 검증: `clean test` 1375개 중1374통과/기존 import fixture 조건부1 skip, 전체 `mongoIntegrationTest` 성공, Node64개 및 원본 fixture SHA256SUMS 모두 통과. 실제 서비스 E2E와 합산하지 않는다.

- 브랜치 `feat/lc-review-coexistence` → PR/develop → 기존 LC test 자동 배포. main/prod 경로는 사용하지 않는다.
- 기존 test task의 Secret/TLS/봉인/DB/index와 다른 기능 flag를 보존한다. 공존 flag는 코드 배포 후 test에만 적용한다.
- 앱은 v2 고정 사용 및 동일 key 재시도를 적용해야 한다. v2 실패 후 v1 자동 호출은 금지다.
- 실제 Identity 사용자·Billing 업무 ON 상태의 권리 확인 및 AI 채점/Callback을 포함한 공동 E2E는 아직 증명하지 않았다. 기능 간 데이터 계약을 임의 완화하지 않는다.
- 원본 fixture 파일의 bytes/hash와 기존 manifest는 그대로 유지한다. 사용자 DS_Store 및 이전 DB 준비 스크립트는 이번 제품 코드 전달에서 제외한다.
