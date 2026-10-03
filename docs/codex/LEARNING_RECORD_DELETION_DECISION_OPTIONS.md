# 학습 기록 독립 삭제 기능 결정 선택지

- 작성일: 2026-09-21
- 상태: 제품 정책 유지, 2026-10-03 삭제 API·fence·worker·일별 집계 구현/로컬 검증 추가. 실제 rollout 검증 및 Billing 증거 retention 연결 전 기본 OFF 유지(IMPLEMENTATION_PROGRESS 참조)
- Jira: [TMI-193](https://to-teacher.atlassian.net/browse/TMI-193), 상위 TMI-136
- 대상: 로그아웃·회원 탈퇴와 독립적으로 현재 사용자의 Learning Core 학습 기록을 삭제하는 기능

## 권장 기본안

1. 모의고사와 10초 Challenge의 사용자 학습 기록을 모두 삭제한다.
2. 1차 버전은 개별 시험 삭제가 아니라 전체 학습 기록 삭제만 제공한다.
3. 삭제 요청 즉시 조회 노출과 새 학습 write를 일시 차단한다. cutoff·target inventory, Callback fence와 Billing coordination이 안전하게 확정되면 물리 정리 중에도 새 학습을 다시 허용하고 durable deletion job이 MongoDB·S3·Redis를 계속 정리한다.
4. 진행 중 채점과 Billing 연동 상태는 강제 삭제하지 않고 terminal/reconciliation 증거를 먼저 확정한 뒤 정리한다.
5. 사용자가 생성한 학습 내용과 음성은 삭제하되 결제 사용 이력, 무료 기회 사용 여부, 보안 deny marker, event inbox와 최소 멱등성·감사 증거는 승인된 보존기간 동안 유지한다.
6. 기록 삭제는 credit·무료 기회·응시권을 복원하지 않는다.
7. 되돌리기는 지원하지 않고 이중 확인과 멱등 요청을 사용한다.

### 2026-09-22 추가 확정

- 안전 checkpoint 후에는 같은 날 Challenge 재참여를 허용한다. 반복 삭제에 따른 반복 재참여도 가능하며 별도 당일 제한 증거는 남기지 않는다. Billing credit·무료 시험 기회 복원은 여전히 금지한다.
- 진행 중 다른 key의 추가 삭제는 409로 명시적으로 거절하고 기존 삭제 완료 후 재요청하게 한다. 같은 key replay와 새 학습 허용은 그대로 유지한다. cleanup 지연 시 추가 삭제 대기도 길어질 수 있다. 이전 cutoff alias나 후속 삭제 예약은 사용하지 않는다.
- 통계는 발생일별 건수·주월 합계와 terminal 결과 구성비로 한정한다. 동일 시작 집단의 완료율은 제외한다. 활성화일부터 정확한 집계를 보장하고 과거는 근거가 있는 지표만 검증 후 반영하며, 복원 불가를 0으로 표시하지 않는다. 하루 최대 18개 조합은 유지한다.

추가 제품 선택은 확정됐으며 기술 경합 보완은 구현 계획 §5~10·§15를 따른다. 아래 기존 선택지 표는 의사결정 근거이며, 실제 운영/계약 검증과 Jira 생성·runtime 구현·배포는 아직 수행하지 않았다.

## 1. 삭제 대상 기능 범위

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 모의고사만 삭제 | 범위가 작고 현재 핵심 학습 이력에 집중 가능 | Challenge 이력이 남아 “학습 기록 전체 삭제”라는 문구와 어긋남 |
| B. 모의고사와 Challenge 모두 삭제 **(권장)** | 사용자 기대와 가장 일치하고 개인정보 설명이 단순함 | 두 aggregate와 worker·S3 정리를 모두 설계해야 함 |
| C. 화면에서 항목별 선택 | 사용자 제어권이 큼 | API·UI·삭제 경합·테스트 조합이 크게 늘어남 |

권장 UI 문구는 범위가 명확한 “모든 학습 기록 삭제”다. 일부만 지우려면 별도 기능으로 분리한다.

## 2. 삭제 단위

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 전체 기록만 삭제 **(권장 1차)** | 멱등성과 UX가 단순하고 누락 가능성이 낮음 | 잘못 삭제했을 때 영향 범위가 큼 |
| B. 시험 한 건/Challenge 날짜 한 건 삭제 | 세밀한 관리 가능 | Summary·Job·S3·Billing event 연관관계 처리와 화면이 복잡함 |
| C. 기간별 삭제 | 개인정보 관리 UX가 좋음 | 날짜 기준, timezone, 부분 Summary와 AttemptGroup 정책이 추가됨 |

## 3. 진행 중 시험·채점이 있을 때

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 모든 작업이 끝날 때까지 삭제 거절 | 구현이 가장 단순하고 데이터 경합이 적음 | 장애로 작업이 정체되면 사용자가 영구적으로 삭제하지 못할 수 있음 |
| B. 즉시 물리 삭제 | 빠르게 완료된 것처럼 보임 | 늦은 AI Callback이 결과를 다시 만들거나 Billing/S3 상태가 남을 수 있음 |
| C. 즉시 deletion fence 후 비동기 수렴 **(권장)** | 사용자는 즉시 기록을 볼 수 없고, 늦은 Callback·worker 재생성을 차단하면서 안전하게 정리 가능 | deletion request 상태와 background worker가 필요함 |

확정 흐름은 `REQUESTED → FENCED/INVENTORYING → 안전 checkpoint → 물리 정리 → COMPLETED`다. 안전 checkpoint 전에는 새 시험 생성·submit·retry·Challenge answer를 거절한다. 이후에는 최초에 sealed한 old target만 삭제하고 새 기록은 정상 허용한다. 단순 정리 지연은 non-blocking `CLEANUP_DELAYED`, target·ownership·Billing·commit outcome이 불명확한 경우만 blocking `NEEDS_REVIEW`로 둔다.

## 4. 실행 방식

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. HTTP 요청에서 MongoDB·S3·Redis 동기 삭제 | API가 단순해 보임 | 외부 저장소를 하나의 Transaction으로 묶을 수 없고 timeout·부분 삭제 위험이 큼 |
| B. MongoDB durable job + worker **(권장)** | 재시도·장애 복구·진행 상태·멱등성을 구현할 수 있음 | scheduler, lease, 상태 조회와 운영 metric이 필요함 |
| C. 메시지 큐 | 확장성과 분리가 좋음 | 현재 범위에 새 운영 인프라가 필요하고 과함 |

MongoDB Transaction에는 deletion request/fence와 DB 상태 전이만 넣고, S3 실제 삭제와 Redis 정리는 commit 후 worker가 수행한다.

## 5. 삭제 데이터와 보존 데이터

### 삭제 대상 권장안

- `exam_sessions`, `exam_results`, `exam_summaries`
- Question/Summary grading job과 Azure/SpeechAce 결과
- Challenge attempt, grading job과 사용자 응답 데이터
- 시험·Challenge 사용자 제출 S3 음성
- 사용자별 Redis 학습 상태 projection

삭제 완료 뒤 원본 `ExamSession`을 hidden 상태로 남기는 방식은 사용하지 않는다. 진행 중 Billing/AttemptGroup 정리를 위해 삭제 operation 동안 일시적으로 숨겨 둘 수는 있지만, `COMPLETED` 전에 학습 내용이 없는 별도 최소 coordination tombstone으로 필요한 증거를 옮기고 원본 Session 문서는 제거한다. 로그인·RefreshSession은 Identity 영역이며 이 삭제 대상이 아니다.

### 즉시 함께 삭제하면 안 되는 데이터

- Billing reservation/consumption, TrialClaim과 무료 기회 사용 증거
- AttemptGroup 전달을 위한 미확정 outbox와 이미 발행된 canonical event 증거
- UserMerged/UserWithdrawn inbox·deny marker와 ownership guard
- 삭제 명령의 최소 tombstone, 멱등성 key와 보안 감사 증거
- 사용자 비소유 MockExam·Question·Challenge catalog

| 보존 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 관련 데이터를 전부 즉시 삭제 | 설명이 단순함 | credit 중복 지급, event 재처리, 보안 우회와 감사 불능 위험 |
| B. 사용자 생성 콘텐츠는 삭제하고 최소 거래·보안 증거 유지 **(권장)** | 개인정보와 시스템 무결성을 함께 지킬 수 있음 | 개인정보 처리방침에 보존 항목·기간·목적을 명시해야 함 |
| C. 모든 DB 기록을 익명화 | 분석·정산 자료 유지 가능 | 진짜 비식별 여부를 보장하기 어렵고 구현 범위가 큼 |

비식별 집계 metric은 개인에게 재연결할 수 없는 경우만 유지한다. 운영 로그와 backup은 실시간 삭제 대상과 별도로 보존기간 만료 정책을 공개해야 한다.

## 6. credit·무료 기회 처리

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 기록 삭제 시 credit/무료 기회 복원 | 사용자에게 관대함 | 삭제 반복으로 무한 응시가 가능하고 Billing ledger 정합성이 깨짐 |
| B. 복원하지 않음 **(권장)** | 남용 방지와 Billing/TrialClaim 계약 유지 | 사용자가 삭제 후에도 사용권이 돌아오지 않는 이유를 UI에 명시해야 함 |
| C. 고객센터 승인 시에만 보상 | 예외 대응 가능 | 운영 절차와 관리자 감사 기능이 필요함 |

UI 확인 문구에 “학습 기록 삭제는 사용한 이용권·credit·무료 응시 기회를 복원하지 않습니다”를 포함한다.

## 7. 사용자의 삭제 체감 시점

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 물리 삭제 완료 후 화면에서 숨김 | 상태가 정확함 | S3 재시도 동안 기록이 계속 보임 |
| B. 요청 즉시 숨기고 뒤에서 물리 삭제 **(권장)** | 개인정보 UX가 좋고 진행 중 재노출을 막음 | 삭제 실패/장기 지연 상태와 운영 대응이 필요함 |
| C. 유예기간 동안 soft delete 후 purge | 실수 복구 가능 | “삭제 완료” 의미가 모호하고 보존기간이 늘어남 |

확정안은 되돌리기 없는 즉시 숨김과 물리 삭제 목표 24시간이다. 새 학습은 cutoff·target inventory, late Callback fence와 Billing OPEN tombstone이 안전하게 확정될 때까지만 차단한다. 그 뒤 S3·Redis·old orphan·backup 정리가 지연되면 기록은 계속 숨긴 채 `CLEANUP_DELAYED`로 새 학습을 허용한다. 신규 데이터 오삭제·추가 차감 위험이 있는 경우만 `NEEDS_REVIEW`로 차단한다.

## 8. 확인·인증·멱등성

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. Access Token만 있으면 즉시 삭제 | 구현이 단순함 | 오조작과 탈취 Token 피해가 큼 |
| B. 앱 이중 확인 + 멱등성 key **(권장 최소)** | UX와 서버 중복 요청을 함께 방어 | 앱·서버 계약 추가 필요 |
| C. 최근 재인증까지 강제 | 보안이 가장 강함 | Identity recent-auth 계약과 앱 인증 화면이 필요함 |

현재 단계 권장 최소는 정상 JWT·현재 사용자 확인, 위험 문구가 있는 이중 확인, 한 번의 active deletion만 허용하는 서버 멱등성이다. Identity가 recent-auth 증거를 제공하면 C로 강화할 수 있다.

### B 선택 시 늘어나는 범위

이중 확인은 앱 UX이고 멱등성은 Learning Core command 안전장치다. 이 선택만으로 Identity 인증 계약을 변경하거나 새 Token·claim·비밀번호/MFA 확인을 추가하지 않는다.

앱 범위:

- 설정 화면에 “모든 학습 기록 삭제” 진입점 추가
- 1차 확인에서 삭제 대상·미복원 항목 안내
- 2차 확인에서 되돌릴 수 없음과 credit·무료 기회 미복원을 다시 확인
- 최종 확인 시 UUID idempotency key를 한 번 생성하고 timeout·재시도에는 같은 key 재사용
- 중복 탭 방지, 삭제 진행/완료/실패 상태 표시와 접수 확인 시 이전 로컬 학습 cache 무효화; 완료 시 새 학습 cache 보존
- 로그아웃하거나 Access/Refresh Token을 삭제하지 않음

여기서 앱은 Learning Core 서버가 아니라 사용자가 설치하는 모바일 클라이언트를 뜻한다. 예상 화면 흐름은 `설정 → 학습 기록 삭제 → 삭제 범위 안내 → 최종 확인 → 삭제 진행/완료`다. 최종 확인 전에는 서버 요청을 보내지 않는다. 최종 확인 시 앱이 한 번 만든 key를 삭제 operation이 terminal 상태가 될 때까지 로컬에 보존하고, 네트워크 timeout이나 앱 재실행 뒤 재조회·재전송에도 같은 key를 사용한다. 접수가 확인되면 이전 cache와 늦은 응답을 차단하고 완료 시에는 해당 삭제 세대의 cache만 정리한다. checkpoint 후 새 학습 cache, 로그인 Token과 계정 정보는 유지한다. 다른 기기의 오프라인 cache 즉시 제거를 보장하지 않으며 온라인 복귀 시 status부터 동기화한다.

앱은 `confirmed=true` 같은 값을 보내지 않는다. 두 번의 확인은 실수 방지 화면이고, 서버에는 기존 Authorization header와 삭제 command용 idempotency key만 전달한다. 서버가 삭제 API를 제공해도 앱에 별도 메뉴·호출 코드가 없으면 사용자는 이 기능을 실행할 수 없으므로 backend와 mobile app의 배포가 함께 필요하다.

Learning Core 범위:

- 기존 JWT 검증 결과의 `sub`를 사용하고 Request에 userId를 받지 않음
- 신규 삭제 command에서 `Idempotency-Key` 형식과 필수 여부 검증
- userId·idempotency key·상태를 가진 durable deletion operation과 unique/active 제약 추가
- 같은 key 재전송은 새 삭제를 만들지 않고 기존 operation 결과 반환
- 응답 유실·같은 key 동시 요청은 한 작업으로 수렴. 다른 key의 active deletion 요청은 `409 LEARNING_RECORD_DELETION_ALREADY_ACTIVE`로 거절하고 alias·후속 예약을 만들지 않음
- 진행 상태 조회 또는 동일 command replay로 공개 `processing/cleanup_delayed/completed/needs_review`와 `canStartLearning` 제공
- key 원문과 사용자 식별자를 일반 로그·metric tag에 남기지 않음

Identity 범위:

- 변경 없음
- 신규 로그인, Firebase 재인증, 최근 인증 시각 claim, RefreshSession 폐기와 Token 재발급 없음

서버는 앱에서 확인 창을 실제로 두 번 눌렀는지 신뢰성 있게 증명할 수 없다. `confirmed=true` 같은 body 필드는 보안 증거가 아니므로 추가하지 않는다. 탈취 Token까지 방어하려면 B가 아니라 C의 recent-auth 계약이 필요하다.

삭제 worker, MongoDB·S3·Redis 정리와 늦은 Callback 차단은 A/B/C 공통 기반이다. 따라서 B가 A보다 추가하는 핵심은 앱 확인 화면과 idempotency command 계약이며, 삭제 엔진의 대부분은 어느 선택에서도 필요하다.

## 9. 신규 API 형태

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. `DELETE /api/v1/learning-records` + 상태 조회 **(권장)** | userId를 받지 않고 의도가 명확함 | 비동기 삭제 status 계약을 추가해야 함 |
| B. `POST /api/v1/learning-record-deletions` | command resource와 비동기 상태 표현이 자연스러움 | 프론트에서 “삭제인데 POST”가 낯설 수 있음 |
| C. Identity 회원 API 아래에 추가 | 프로필 기능과 한곳에 보임 | 실제 데이터 소유 서비스가 Learning Core라 서비스 경계가 흐려짐 |

기존 공개 API를 바꾸지 않고 신규 API만 추가한다. Request에 userId를 받지 않고 JWT `sub`를 사용한다. 신규 응답 DTO에는 내부 collection, S3 key, Billing 식별자나 실패 원문을 노출하지 않는다.

## 10. 삭제 후 제품·품질 통계

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 집계 통계까지 모두 제거 | 삭제 의미가 가장 단순함 | 전체 응시량과 채점 품질 추세를 잃음 |
| B. 재식별 불가능한 서비스 집계만 유지 **(확정)** | 사용자 기록을 지우면서 운영·품질 개선 지표 유지 | 집계 차원·최소 cohort와 생성 시점 설계 필요 |
| C. 사용자별 누적 횟수 유지 | 개인화와 lifetime 통계가 쉬움 | 개인 학습 이력이 남아 “모든 학습 기록 삭제”와 충돌 |

확정안은 별도 MongoDB collection `learning_activity_daily_aggregates`에 KST 일자·시험 유형별 저카디널리티 count를 유지하는 것이다. 예시는 `exam_started_total`, `exam_completed_total`, 채점 성공·실패와 재시도 건수다. 주·월 통계는 일별 문서를 합산하고 v1에서 주·월 중복 문서를 따로 저장하지 않는다.

문서는 `bucketDate`, `metric`, `examType`, `outcome`, `count`, 생성·수정 시각과 version만 가진다. 값이 없는 dimension은 `ALL` 고정값을 사용하고 `(bucketDate, metric, examType, outcome)`을 unique key로 둔다. 집계 기준 timezone은 `Asia/Seoul`로 고정한다.

v1 metric 조합은 모의고사 흐름 4개, 문항·Summary 7개, Challenge 7개로 하루 최대 18개다. 발생하지 않은 조합은 문서를 만들지 않는다. 실패·시스템 재시도의 상세 원인은 이 collection에서 나누지 않고 `outcome=ALL`로 합치며 기존 Micrometer 운영 metric에서만 확인한다. 문항 제출은 `INITIAL|USER_RETRY`, Challenge 완료는 `SCORED|NO_SPEECH`만 구분한다.

발생일별 건수와 주·월 합계를 제공하고 중복 저장하지 않는다. 시험 완료/(완료+재응시 가능)는 해당 기간 terminal 결과 구성비이지 시작 대비 완료율이 아니다. 분모 0이면 계산 불가로 표시한다. 날짜·모집단이 다른 완료/시작·제출/시작 비율과 Job 완료/실패의 단순 성공률, 동일 시작일 cohort 완료율은 v1에서 제공하지 않는다. 18개 조합 기준 연간 최대 6,570개 문서다.

live 집계는 수집 시작 시각부터 보장하며 최초 부분 일자·지표별 coverage를 운영 manifest로 관리한다. 과거는 실제 event 발생일과 중복 제거 증거가 남은 항목만 검증하여 반영한다. 미수집/복원 불가 구간은 0이 아니며 완전 수집 구간의 sparse 문서 부재만 0으로 해석한다. 현재 상태나 덮어쓴 완료 시각으로 과거 일별 retry를 추정하지 않는다. Job 재개 후에도 최초 집계 marker를 유지하고 새 durable retry 결정만 세며 상세 구현은 계획 §7.4를 따른다.

집계에는 userId, examId, attemptId, 정확한 응시 시각, 원문 음성·답안·transcript·피드백과 희소한 dimension 조합을 넣지 않는다. 특정 사용자의 누적 응시 횟수, 평균 점수, 최근 응시일과 개인별 counter는 삭제한다. 삭제된 사용자의 과거 기여분은 개인에게 다시 연결할 수 없는 집계 count 안에만 남고 사용자 삭제 때 차감하지 않는다.

Billing consumption과 TrialClaim은 거래·중복 지급 방지 목적으로 별도 보존할 수 있지만, 이를 일반 분석이나 개인화용 사용자별 응시 counter로 재사용하지 않는다. lifetime unique user처럼 영구 식별 또는 재연결이 필요한 지표는 이 확정안에 포함하지 않고 별도 privacy 설계를 거친다.

## 11. 잔여 운영 결정 확정

| 항목 | 확정 권장안 |
| --- | --- |
| Billing OPEN | 원본 ExamSession 대신 최소 coordination tombstone을 사용해 같은 attemptGroup/consumption으로 replacement하고 추가 차감하지 않음 |
| retention | 완료 command·Callback tombstone·일반 로그·암호화 backup 30일, security audit 90일, OPEN/non-terminal coordination 무TTL 후 terminal+30일 |
| S3 IAM | bucket 전체 권한 금지, `temp/*` prefix의 List/Delete만 허용. versioning 활성 시 version list/delete 또는 검증된 lifecycle 적용 |
| 모바일 | 공개 `processing|cleanup_delayed|completed|needs_review`와 `canStartLearning`; 정리 지연은 새 학습 가능 문구, 위험 상태만 일시 제한·고객지원 문구 사용. 내부 stage·식별자·실패 원문 비노출 |
| 운영 복구 | `CLEANUP_DELAYED`는 새 학습 허용, `NEEDS_REVIEW`는 fail-closed. 같은 operation을 복구하고 강제 완료·새 operation 생성 금지 |
| Jira | 상위 이슈 아래 backend core, Billing/AttemptGroup, storage/IAM, Challenge/AI, aggregate/privacy, mobile, test/runbook·rollout 하위 이슈 구성 |

Jira key는 아직 생성되지 않았다. 위 구성은 구현 범위 확정이며 실제 이슈 생성·담당자 지정은 별도 수행한다.

## 확정 결과

1. 모의고사와 Challenge 전체 학습 기록을 삭제한다.
2. 1차 버전은 전체 삭제만 제공한다.
3. 즉시 read hide/write fence 후 durable deletion worker로 비동기 수렴한다.
4. 안전 checkpoint까지만 새 학습을 차단하고 이후에는 physical cleanup과 신규 학습을 분리한다. 단순 정리 지연은 `CLEANUP_DELAYED`로 허용하고 데이터 손상·중복 차감 위험이 있는 `NEEDS_REVIEW`만 차단한다.
5. 삭제로 credit·무료 기회·응시권을 복원하지 않으며 앱 확인 문구에 이를 명시한다.
6. 물리 삭제 목표는 24시간이다. 위험 없는 반복 실패는 `CLEANUP_DELAYED`, target·ownership·Billing·commit 증거가 불명확한 실패만 `NEEDS_REVIEW`로 격리한다. 두 상태 모두 old record hide를 유지한다.
7. 앱 이중 확인+멱등성 key를 사용하며 Identity recent-auth, 신규 claim과 Token 계약은 추가하지 않는다.
8. live 사용자 콘텐츠는 제거한다. 완료 command·Callback tombstone·일반 로그·암호화 backup은 30일, 보안 감사는 90일 보존하고, Billing coordination은 OPEN/non-terminal 동안 무TTL·terminal 후 30일로 한다. Billing ledger는 별도 거래·법정 정책을 따른다.
9. 서비스 전체·기간별 비식별 응시/품질 집계는 유지하되 사용자별 누적 횟수·평균 점수·최근 응시일은 삭제한다. Billing 소비 증거는 거래 목적으로만 제한한다.
   - KST 일별 집계는 MongoDB `learning_activity_daily_aggregates`에 저장하고 주·월 수치는 이를 합산한다.
   - v1은 하루 최대 18개 고정 조합만 허용하며 실패·시스템 재시도의 상세 원인은 저장하지 않는다.
10. 삭제 완료 뒤 원본 `ExamSession`은 남기지 않는다. Billing OPEN 승계에는 별도 최소 coordination tombstone만 사용하고 로그인 Session은 변경하지 않는다.
11. S3 권한은 `temp/*`에 한정하고 bucket versioning 활성 시 과거 version까지 제거할 수 있는 권한 또는 lifecycle를 production gate로 검증한다.
12. 운영자는 동일 deletion operation을 복구하며 DB 강제 완료·fence 강제 해제·새 operation 생성으로 우회하지 않는다.
13. Jira는 상위 이슈와 담당별 하위 이슈 구조로 등록한다. 현재는 key 미생성 상태다.
14. 안전 checkpoint 전에 기존 Challenge slot을 해제하고 같은 날 새 attempt로 재참여를 허용한다. 반복 삭제로 재참여할 수 있음도 수용한다.
15. 진행 중 다른 key의 추가 삭제는 명시적으로 거절하고 완료 뒤 새 확인·새 key로 다시 요청한다. 정리 지연은 새 학습과 별개로 추가 삭제를 지연시킬 수 있다.
16. 통계는 발생 건수·terminal 결과 구성비로 한정하고 수집 시작일부터 보장한다. 검증 가능한 과거만 반영하며 미수집을 0으로 표시하지 않는다.

상세 구현 경계와 남은 운영 확인은 `docs/codex/LEARNING_RECORD_DELETION_IMPLEMENTATION_PLAN.md`를 따른다.
