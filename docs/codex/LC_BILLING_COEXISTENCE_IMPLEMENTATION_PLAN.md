# 심사 기간 구앱 무료·신앱 Billing 공존 구현 계획 R2

- 날짜: 2026-10-17. 심사 기간 한정 호환·기존 버전 검사 재사용·출처 한 필드/생성 스위치 한 개의 사용자 계획 수정 요청 반영.
- 상태: R2 계획 후 사용자가 브랜치 구현·commit/push·LC test 배포를 승인했다. 아래는 승인 당시 계획이며 현재 구현/검증/전달 범위는 [전달 기록](LC_BILLING_COEXISTENCE_DELIVERY.md)을 따른다. 실제 양측 E2E 완료를 뜻하지 않는다.
- 관련: TMI-116/TMI-128/TMI-122/TMI-125/TMI-217/TMI-220, 삭제 TMI-193/TMI-136. 신규 Jira 생성 없음.

## 1. 5줄 결론

1. 기존 `POST /api/v1/exams`는 업데이트 심사 기간 무료 생성 경로로 유지하고 신규 `POST /api/v2/exams`를 Billing 필수 생성 경로로 제안한다.
2. 새 앱과 심사 앱은 같은 새 경로를 사용한다. 버전/헤더를 권한 증거로 삼지 않으며 구경로 직접 호출의 무료 우회 위험은 수용한다.
3. 봉인된 과거 목록은 불변이다. 이후 구경로 시험에는 서버가 명시적 출처를 저장하고 조회·접수·채점·완료까지 지원한다.
4. 양쪽 경로는 동일 사용자 생성/삭제/탈퇴 fence를 사용한다. 새경로 실패를 무료 생성으로 전환하지 않는다.
5. 실제 업데이트 배포 후 기존 버전 검사로 업데이트를 강제하고 구경로 생성 스위치를 OFF로 적용한다. 기존 시험 처리는 유지하며 이번에는 코드·서버·봉인을 변경하지 않는다.

### R1에서 줄인 범위

- 장기 구앱↔신앱 왕복 전환 지원을 제외한다. 정상 흐름은 구앱 무료 사용→업데이트→신앱 Billing 사용이다.
- 무료 출처는 `creationOrigin` 하나만 추가한다. 별도 정책 revision 필드·출처 컬렉션·관리자 화면·동적 설정 서비스는 만들지 않는다.
- 기존 버전 검사를 재사용하며 별도 강제 업데이트 기능을 만들지 않는다. 생성/응답/접수/채점/복구 구성요소도 재사용하고 새 서비스 전체를 복제하지 않는다.
- 진행 중 Billing 시험을 구경로 무료 시험으로 교체하는 정상 전환 기능은 제외한다. 해당 예외 요청은 안전 거절한다. 이는 구경로 일반 무료 이용을 막는 영구 계정 차단이 아니다.

## 2. 반드시 읽을 계약

### 2.1 외부 요청·응답 제안

| 항목 | 구경로 | 새경로 |
| --- | --- | --- |
| 생성 | 기존 POST /api/v1/exams | 제안 POST /api/v2/exams |
| body | 기존 body 없음 유지 | body 없음 |
| 인증/사용자 | 기존 CurrentUserProvider 및 환경별 JWT 정책 유지 | 동일; body/path/query에 userId 없음 |
| Idempotency-Key | 필수화하지 않음. 구클라이언트에 새 보장 부여하지 않음 | 필수 lowercase UUID v4; 기존 paid 검증/120일 계약 |
| 권리 | 전환기간 무료, Billing reserve/소비 없음 | Billing reserve/confirm 필수, paid-first/무료권 판정은 Billing |
| 성공 | 기존 CreateSessionResult/BaseResponse/HTTP 유지 | 동일 성공 DTO/BaseResponse/HTTP 사용 |
| 오류 | 기존 오류 mapper, 공통 생성중/삭제/owner 차단 | 기존 paid 오류 mapper·MEMBER 보강·same-key 복구 유지 |

버전2는 생성 endpoint 한 개만 추가하는 제안이다. 제출/조회/음성/S3/Polling/재채점/Callback URL과 DTO는 v1 그대로 공유한다. 새 앱에서 받은 examId를 기존 API에 사용한다. AI user_id=examId, retryCount, S3/Redis key는 불변이다. 앱 버전·User-Agent·헤더 누락을 보고 무료/paid를 자동 선택하지 않는다.

구경로에 Idempotency-Key가 와도 새경로로 라우팅하지 않는다. v1은 paid operation을 조회하거나 재현하지 않으며 앱은 v2 실패 후 v1로 재시도하지 않는다. 구경로의 기존 무키 반복 생성 위험은 유지하되 동시 생성은 직렬화한다.

### 2.2 출처와 수명

세 부류를 명확히 구분한다: (a) 봉인 목록에 포함된 과거 시험, (b) 전환기간 구경로 신규 무료 시험, (c) Billing snapshot으로 확인된 시험.

- (b)의 내부 필드 후보: Session `creationOrigin=TRANSITION_FREE_V1` 하나. 기존 createdAt/examId/userId를 재사용한다. 클라이언트 입력으로 받지 않으며 Session 생성 Transaction에서만 기록한다. 별도 `creationPolicyRevision` 필드는 추가하지 않는다.
- 이 필드는 EXAM_PASS/SUBSCRIPTION entitlementSource가 아니다. reservation/group/consumption/TrialClaim/복원권을 만들지 않는다. marker와 Billing metadata가 혼재하면 계약 오류다.
- marker 없는 신규 Session을 무료로 추론하지 않는다. source null·앱 시각·임의 생성일만으로 인정하지 않는다. 기존 manifest에 새 시험을 편입하거나 hash/count를 바꾸지 않는다.
- 출처는 Session의 학습 lifecycle과 함께 유지하며 operation 120일 TTL과 분리한다. 별도 origin 컬렉션·영구 key tombstone·중복 manifest는 추가하지 않는다. 알 수 없는 origin은 fail-closed다. 이 값의 의미는 고정하며 이후 의미 변경 시 새 enum 또는 명시적 이관을 검토하는 비용을 수용한다.
- 구경로 생성 종료 후에도 이미 생성된 (b)의 조회·정상 진행·기접수 처리 reader를 끄지 않는다. 삭제/탈퇴/owner/terminal 우선은 유지한다.

### 2.3 생성·교체·복구

- 현재 ExamServiceImpl은 같은 v1 route에서 전역 paid flag로 saga를 선택한다. 생성 진입점만 route별 명시 adapter로 분리하고 기존 Session 생성·응답 조립·접수/채점·paid saga를 필요한 범위에서 재사용한다. 별도 전체 처리 체계를 복제하지 않는다. paid flag OFF는 v2 무료 fallback이 아니라 사용 불가다.
- 구경로 공존 flag 후보 `app.billing.paid-exam.coexistence.legacy-create-enabled` / `BILLING_LEGACY_EXAM_CREATE_ENABLED`, 기본false. 기존 환경설정과 배포 절차로 적용하며 무배포 즉시 토글 기능은 만들지 않는다. OFF는 새 구경로 생성만 중단하며 기존 시험 reader와 v2 경로에는 영향을 주지 않는다. 구경로 OFF 응답의 exact 기존 오류 매핑은 C0에서 정하고 성공/무료 fallback을 반환하지 않는다.
- LegacyExamWriterGuard를 전역 해제하지 않는다. 봉인을 우회할 수 있는 경로는 검증된 공존 전용 writer 하나로 한정하고 이전 Billing saga/prepare/commit 구경로 차단은 유지한다. 봉인 삭제나 paid OFF 전환으로 복귀하지 않는다.
- 사용자 guard touch와 paid activeGuard 확인, 삭제/탈퇴/owner 검증, exact 이전 시험 제출차단·새 무료 Session 저장을 같은 Mongo Transaction에서 수행한다. v2 준비도 같은 guard를 사용한다. 먼저 시작한 paid operation이 미완료면 v1은409이며 무료 Session을 만들지 않는다. v1 먼저 commit이면 v2는 그 Session을 원대상으로 고정한다.
- 신규 (b) 시험도 필수 retry0 durable 접수 근거를 기록한다. 문제 선정은 부분접수→같은 mock, 전체접수→기존 다음 문제 정책, 실제 후보 없음 예외를 재사용한다. 과거 manifest 시험의 증거불충분 예외를 신규 시험의 누락 증거 허용으로 확대하지 않는다.
- (b)→Billing은 원 무료 시험을 operation에 exact 고정하되 Billing restartContext를 만들지 않는 일반 INITIAL이다. 원 consumption이 없으므로 복원 없음. confirm 성공 뒤 원 신규접수 차단·새 활성·operation 성공을 원자 처리한다. 새 필드에는 manifest summary를 위조해 넣지 않고 로컬 출처 snapshot을 구분한다.
- 진행 중 Billing 시험 또는 미완료 paid operation이 있는 상태의 v1 신규 생성은409로 거절한다. 원 시험을 종료하거나 Billing 이벤트/복원/취소를 만들어 무료로 전환하지 않는다. 기존 오류 체계의 exact code·문구는 C0에서 대조한다. 신앱 사용을 안내하되 기존 시험의 읽기/정상 처리를 막지는 않는다.
- 단순 active=false나 요약 대기를 안전한 종료 증거로 보지 않는다. 기존 lifecycle의 영속 상태로 진행 여부를 판정하고 불명 상태는 거절한다. 완료/종료된 과거 Billing 이력만으로 계정을 영구 차단하지 않으며 허용된 v1 생성은 그 과거 그룹·권리·guard를 변경하지 않는다. 다른 기기/직접 API 호출로 거절을 받는 비용을 수용한다.
- same-key paid replay는 최초 Session을 반환하는 기존 계약을 따른다. 이후 Session이 종료됐다고 부활/새 활성화를 하지 않는다. 삭제된 성공 결과 접근 차단은 유지한다.

### 2.4 심사 종료·업데이트 배포

1. 심사 중에는 준비된 환경에서 구경로 생성 ON, 새 앱·심사 앱은 동일 v2 Billing 검사다.
2. 대상 플랫폼의 실제 업데이트 다운로드 가능 상태를 확인하고 기존 버전 검사 기준을 갱신한다. 심사 통과만으로 아직 설치할 수 없는 앱을 강제하지 않는다. 플랫폼별 배포가 다르면 구앱 지원이 필요한 동안 공통 스위치를 성급히 닫지 않는다.
3. 구경로 생성 스위치를 OFF로 배포/적용하고 신규 v1 거절·v2 정상·기존 무료 시험 처리를 확인한다. 종료 상태를 위한 새 서버 버전 검사나 별도 관리 시스템은 만들지 않는다.
4. 앱 버전 검사만으로 구API 직접 호출이 막힌다고 주장하지 않는다. OFF 스위치가 서버의 신규 생성 차단 수단이다. 기존 무료 출처 reader는 OFF와 무관하게 유지한다.

구경로 종료 때문에 기존 시험을 일괄 종료/삭제하거나 source를 Billing 값으로 변경하지 않는다. 장애 시에도 봉인을 삭제하거나 v2 오류를 v1로 우회하지 않는다. 실제 설정 적용은 별도 실행 요청으로 진행한다.

## 3. 결정·공동 확인 항목

- 사용자 방향 및 이번 수정: 심사 기간 한정 구경로 지원·기존 버전 검사 재사용, 무료 출처 한 필드·생성 스위치 한 개 유지, 장기 왕복 전환 제외. 일반 구경로 우회 위험 수용·무료 시험 전체 lifecycle·양쪽 삭제/탈퇴·새경로 무료 fallback 금지는 유지한다. 실제 종료 적용 시점은 업데이트 배포 확인 후 정한다.
- LC/앱 수락 대상: 신규 생성 route `/api/v2/exams`, 동일 성공 DTO, 앱의 v2 고정 사용·오류 시 same-key 재시도. 기존 endpoint 경로를 덮어쓰지 않는다.
- Billing 공동 확인은 무료→paid의 복원 없는 INITIAL, 무료 시험 삭제 시 불필요한 Billing ACK 대기 없음, 기존 늦은 이벤트가 다른 Session에 영향을 주지 않는 회귀에 한정한다. paid→무료 local closure 신규 wire/이벤트를 이번 공존을 위해 설계하지 않는다.
- 해당 의미가 기존 wire로 표현되지 않는 경우 임의 field/event 추가 없이 별도 보완 계약을 먼저 정한다. 기존 A/R2/exact fixture bytes는 변경하지 않는다.
- 기능 도입 시 test 우선. 현재 production 차단을 문서만으로 해제하지 않는다. 재배포/실DB/index·기능 활성화와 구경로 종료는 별도 요청으로 수행한다.

## 4. 위험과 검증 계획

구경로 무료 사용·반복 생성·추가 채점 비용은 의도적으로 수용한 위험이다. 별도의 앱 버전 인증/신규 보안장치를 만들어 제거하지 않는다. 기존 인증·rate limit·소유권 검사는 유지한다. 계정별 active Session 단일성과 신규/기접수 처리 분리가 핵심 회귀 범위다.

| 사례 | 필수 기대 |
| --- | --- |
| v1 무키/유키, v2 무키/정상키 | v1 호환·Billing 무호출; v2 strict key/권리 검사 |
| v2 timeout/401/403/계약오류/권리없음 | 무료 생성0, 기존 오류·guard·복구 유지 |
| manifest/명시무료/paid/근거없음/혼합metadata | 앞 세 부류만 정확 분류; 나머지 거절 |
| 구앱 신규 시험 전체 경로 | 업로드/접수/Callback/재시도/조회/요약/완료 정상 |
| 부분접수/전체접수·채점대기 | 동일/다음 mock; 요약 완료를 기다리지 않음 |
| v1-v1 및 v1-v2 양순서 경합 | 공통 fence, active 단일성, paid 미완료 우회 없음 |
| 무료→paid/paid 성공 replay | 원대상 exact·새 권리 오귀속/원시험 부활/복원 없음 |
| 진행 중 paid→v1·미완료/불명 상태 |409·새 무료 Session 없음·기존 Session/권리/그룹 무변경 |
| 완료/종료된 Billing 이력 뒤 v1 | 영구 계정 차단 없음·과거 그룹 무변경·공통 fence 유지 |
| 교체 뒤 기존 Callback/Summary | 영속 접수만 처리, 신규녹음/부분접수 전체완료 금지 |
| 삭제/탈퇴/UserMerged와 v1/v2 생성 경합 | deny·owner 우선, 생성중 삭제 미접수 유지 |
| 무료 시험 삭제/환불 이벤트 | 무관 Billing ACK/그룹차단 없음, 다른 시험 보호 |
| 공존 flag OFF·재시작·rollback | 신규 v1 중단, 기존 reader 유지, 봉인 삭제 없음 |
| 업데이트 후 기존 무료 시험 계속 진행 | 같은 examId로 공유 API 이용·조회/제출/채점/완료 지원 |

## 5. 구현 순서와 완료 기준

1. C0 계약/계획 수락: route·단일 field·단일 flag 및 OFF/진행 중 paid의 v1 거절 오류 매핑 대조. 앱/Billing 전달 문구 준비.
2. C1 출처 모델·공통 reader/policy·신규 무료 시험 receipt/coverage 처리. 기존 manifest/paid 검증을 완화하지 않는 단위 테스트.
3. C2 route/service 분리·전용 무료 writer·공통 owner/creation/delete fence. 격리 Mongo에서 생성/접수/삭제 경합 검증.
4. C3 무료→paid 준비/closure snapshot 및 기존 replay·복구 연결. 진행 중 paid의 v1 거절을 검증하고 역방향 정상 전환은 구현하지 않는다. 기존 worker 새 reserve 금지 유지.
5. C4 `clean test`, `mongoIntegrationTest`, fixture hash 및 Node/diff 검사, 앱 호환/양측 경합 테스트. 실패 수/미실행 항목 별도 기록.
6. 별도 배포 승인 후 test에서 신규 앱/구앱 시나리오 검증. reader-first 코드 전달 후 공존 flag ON. 심사/실제 배포 확인→기존 버전 검사 적용→공존 flag OFF 순서로 종료하며 기존 시험 처리는 유지한다. 봉인을 제거하거나 구버전 이미지로 내려 해결하지 않는다.

## 6. 코드 근거

- `ExamRestController`/`ExamServiceImpl.createExamSession`: 현재 단일 route·전역 paid 분기. 성공 DTO/공통 후처리 재사용 대상.
- `LegacyExamWriterGuard`/`LegacyExamRuntime`: 현재 봉인 뒤 구writer 차단, manifest 외 paid snapshot 필수. 명시 공존 출처를 추가해야 하는 지점.
- `PaidExamPreparationService`: group 없는 원 시험을 requireLegacy로만 판정하므로 신규 무료 provenance 분기와 operation snapshot 확장이 필요하다.
- `ExamSessionManager`, `ExamReadService`, `ExamGradingService`, `ExamAcceptedWorkPolicy`: 생성/조회/채점 전체 경로를 함께 검증한다. 단순 생성 guard 제거만으로 완료할 수 없다.

이 문서는 실제 구현/테스트 통과·새 fixture 제작·공동 최종 수락·실환경 활성화 증거가 아니다.
