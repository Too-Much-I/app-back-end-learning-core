# 2026년 8월 저장소별 회고 소재

## 1. 5줄 결론

- Learning Core: 앱 시험 이력·재답변·알림과 채점/결과 안정화, 탈퇴 접근 차단 및 Billing 시험 생성 saga를 진행했다.
- Identity: Firebase 인증 broker, 전화번호 fingerprint, Guest 승격·병합, 탈퇴·외부 계정 cleanup과 이벤트 전달을 구현했다.
- Billing: 신규 저장소를 구성하고 전화번호 기준 무료 응시권, reserve/confirm/cancel/expiry와 AttemptGroup event consumer를 구현했다.
- 웹 백엔드: 8월 커밋에서 블로그 조회수 원자 증가, 시험지 001 전환과 비정형 Part 4 표 데이터 전달을 확인했다.
- 실제 장애·재현된 결함·설계상 방어를 구분하고, 8월 코드 구현을 production 활성화·9월 후속 완성과 동일시하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

조회 기준은 로컬 Git의 모든 ref에 있는 2026-08-01 00:00 KST부터 2026-09-01 00:00 KST 직전의 commit 시점과 WORKLOG다. merge/cherry-pick·중복 기록은 별도 성과로 세지 않는다. WORKLOG 작성일과 코드 반영일이 다르면 둘을 구분한다. 원격 Git fetch, 운영 상태 재검증과 당시 테스트 재실행은 하지 않았다.

| 저장소 | 8월에 확인한 작업 | 회고의 중심 소재 |
|---|---|---|
| app-back-end-learning-core | History/Retry(TMI-61), Expo 완료 알림(TMI-63), Part 4(TMI-77), Summary 복구(TMI-25), 구조화 로그/Sentry, 재시도 점수 수정, 탈퇴 consumer(TMI-109), Billing saga(TMI-116) | 비동기 결과 유효성·세대 구분, CI 동시성 테스트, 집계 기준, 분산 commit 불확실성 |
| identity | 동의 API/quality review, ECS/OIDC·로그/Sentry, Firebase·SocialIdentity·AccountType(TMI-88~94), phone eligibility(TMI-95~96), Guest 승격/merge(TMI-97~98), 탈퇴(TMI-75/103/104/107/108/111), 가입 중단 cleanup(TMI-114) | 인증과 canonical 사용자 분리, 개인정보 fingerprint, 외부 계정과 DB의 비원자성, token 폐기와 downstream deny |
| billing | 무료시험 계약/저장소 신설, eligibility consumer(TMI-110), 최초 reserve(TMI-112), lifecycle(TMI-113), BenefitDefinition(TMI-115), AttemptGroup consumer(TMI-117) | 혜택 정책/지급/소비 분리, same-operation replay, 단일 승자 CAS, inbox와 상태 원자 반영 |
| IdeaProjects/web-back-end | 8/26 블로그 조회수, 8/31 시험지 변경·tableContext Map 전환 | 동시 증가 유실 방지와 유동적인 AI 콘텐츠 스키마 |

### Learning Core

진행한 일:

- 8/4 완료 시험 History와 재답변 회차 조회, Expo 채점 완료 알림 코드 반영. 모범답안의 제출·채점 전 노출을 차단했다.
- 8/5~7 AI 서버 주소 환경변수화, 새 시험마다 신규 Session 생성·기존 활성 Session ABANDONED 처리, Part 4 응답 경로 정리와 tableContext 통일, CI·운영 로그를 보완했다.
- 8/10~11 Sentry의 예상 밖 5xx 단일 수집과 개인정보 정제. Summary 실패·세대별 재생성 작업은 8/11 기록, 8/17 코드 반영이다.
- 8/17 최초 응시만 파트 점수에 포함하도록 수정했고, 8/25 Part 4 결과의 질문 문장 누락을 수정했다.
- 8/28 탈퇴 event consumer·deny gate, 8/29 작업 기록/8/31 반영으로 Billing reserve→Session commit→confirm saga를 구현했다.
- 8월 말 10초 챌린지 프론트/AI 계약·자정/attempt/업로드 정책, 비용·BEP와 AttemptGroup 후속 연동을 설계했다. 챌린지 runtime 구현·Reservation background worker 완성을 8월 성과에 합치지 않는다.

회고 추천:

1. **빈 피드백을 완료로 판단하던 결함과 선택적 복구(TMI-25).** null/empty Summary를 실패로 분리하고 실제 최초 문항 결과를 완료 근거로 사용한다. 완료 문항을 재채점하지 않고 Summary 또는 누락 문항만 복구한다. 이전 generation의 늦은 Callback은 새 결과를 덮지 못하게 한다.
2. **Callback HTTP 200과 결과 저장은 다르다.** 8/20~21 Summary 미저장을 진단했다. generation 누락/불일치, duplicate, abandon 등은 200 no-op일 수 있다. generation echo가 유력했던 사례와 AI 수정 배포 후에도 추가 확인이 필요했던 사례를 구분한다. 당시 모든 미저장의 단일 원인·최종 복구를 확정한 것으로 쓰지 않는다.
3. **CI에서만 실패한 동시성 테스트.** insert 횟수는 스케줄에 따라 2/3회일 수 있었다. 최종 active Session 하나와 ID 미재사용을 검증하고 DuplicateKey retry는 결정적 별도 테스트로 분리했다. 당시 반복 실행 10/10 및 전체 296개 통과 기록이 있다.
4. **재답변 점수의 중복 집계.** 파트 점수만 모든 회차를 더하고 있었다. 최초 5점/재답변 9점 fixture에서 최초 5점만 집계하도록 회귀 검증했다. 전체 총점까지 중복됐다고 확대하지 않는다.
5. **없는 Mongo 컬렉션의 migration dry-run 실패.** 비동기 getIndexes 예외가 동기 catch를 벗어났다. async/await로 받고 NamespaceNotFound만 빈 index 목록으로 해석했다. 인증·네트워크 오류는 계속 전파한다. 당시 Node 전체 76개 통과 기록이다.
6. **Saga 리뷰에서 발견하고 수정한 영구 정체/오취소(TMI-116).** Session-first replay가 confirm 복구를 막는 문제를 operation-first로 수정했다. unknown commit은 실패 증거가 아니므로 무조건 cancel하지 않는다. 당시 집중 18개·전체 432개 통과 기록이며 운영 사고 복구 실적으로 쓰지 않는다.

근거: [WORKLOG](WORKLOG.md:1307), [Summary 구현](WORKLOG.md:2019), [CI 분석·수정](WORKLOG.md:1657), [집계 수정](WORKLOG.md:2052), [Callback 진단](WORKLOG.md:2309), [Saga 리뷰·수정](WORKLOG.md:4507), [사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md).

### Identity

진행한 일:

- 8/4~11 ECS container·AWS OIDC 검증, 동의 버전/조회 API, LOCAL/GUEST 탈퇴(TMI-75), 로그·Sentry와 임시 smoke 검증을 진행했다.
- 8/13~14 SocialIdentity와 account type 분리, Firebase broker PoC/검증·exchange/signup, PhoneIdentity·versioned HMAC와 인증 기능별 패키지 구성을 구현했다(TMI-88~94).
- 8/14~18 phone eligibility 이벤트 계약/outbox publisher(TMI-95~96), quality review 선택 동의 hotfix, 기존 UUID를 유지하는 Guest MEMBER 승격·auth sync(TMI-97)를 구현했다.
- 8/20 기존 MEMBER로 Guest를 통합하는 canonical merge·source token 거절·UserMerged outbox(TMI-98)를 구현했다. Learning Core 데이터 migration 완성은 후속 작업이다.
- 8/25~28 Firebase 재인증 기반 탈퇴, external cleanup, identity release·재가입 gate, session/mobile 오류 계약, UserWithdrawn outbox/workload publisher, 가입 중단 Firebase cleanup을 구현했다(TMI-103/104/107/108/111/114).

회고 추천:

1. **Firebase 인증과 서비스 사용자 식별 분리.** Firebase proof는 인증 근거이고 서비스의 권한·Session은 canonical User와 자체 JWT 계약으로 관리한다. signup에서 관련 identity·Session·outbox와 enrollment consume을 하나의 Transaction에 묶는 이유를 설명한다.
2. **Guest 승격과 기존 MEMBER 병합은 다른 문제다.** 승격은 UUID 유지, 병합은 source/target이 다르다. target은 client userId가 아니라 검증된 기존 identity owner로 결정하며 source Token을 target 권한으로 alias하지 않는다.
3. **전화번호 평문 없이 중복·무료권 자격을 판정한다.** E.164 정규화, versioned HMAC fingerprint와 lookup alias로 key rotation까지 고려했다. HMAC을 단순 암호화·원문 복호화 기능으로 설명하지 않는다.
4. **탈퇴는 DB 한 건 삭제로 끝나지 않는다.** Firebase disable/revoke/provider obligation/delete/부재 확인 뒤 identity release·재가입 gate로 이어진다. timeout만으로 삭제 성공을 확정하지 않고 owner mismatch는 격리한다. 구현·정적 분석에 근거한 설계 사례이며 실제 production 장애 해결 여부는 별도 확인이다.
5. **가입 중단 계정과 cleanup/finalize 경합.** Firebase에는 계정이 생겼으나 signup finalize 전에 중단된 상황을 다룬다. grace·lease/version/generation으로 정상 가입 완료와 cleanup이 동시에 승리하지 않게 한다(TMI-114).
6. **JWT 로컬 검증만으로 탈퇴 직후 차단할 수 없다.** UserWithdrawn outbox와 Learning Core deny consumer를 연결한다. 사용자 JWT와 서비스 전달용 workload JWT는 분리한다. consumer·producer 구현과 실제 전달 E2E를 구분한다.
7. **Sentry 수집과 정제.** 예상 4xx 제외·예상 밖 5xx 단일 event·stack 보존·민감정보 제거가 소재다. Identity 8/11 WORKLOG에는 사용자 수신 확인 후 smoke 제거 기록이 있지만 기존 사례집에는 미확인 표기가 있어, 최종 글은 해당 WORKLOG와 확인 범위를 명시한다. 알림 정책·실제 배포 경로 전체 성공을 뜻하지 않는다.

근거: [Identity WORKLOG](/Users/msde76/identity/docs/codex/WORKLOG.md:605) 및 commit `2743910e`(broker), `37b67334`(fingerprint), `f68d7e8c`(signup), `c33d1258`(upgrade), `b8df69f6`(merge), `1832e278`(release), `43bb3697`(withdrawal publisher), `1c7659ea`(abandoned cleanup). Identity의 `8c4f3ca1`은 TMI-116 제목이지만 문서 2개 변경이므로 Learning Core saga runtime 중복 성과로 세지 않는다.

### Billing

진행한 일:

- 8/24 신규 서비스 기본 구성을 시작하고 유료 결제를 연기한 최소 무료 모의고사 계약을 확정했다.
- 8/26~27 전화번호 eligibility 전달과 VPC Lattice AWS_IAM·SigV4 인증 경계를 설계하고 strict event/inbox/revision consumer를 구현했다(TMI-110).
- 8/28 최초 무료 reserve(TMI-112), confirm/cancel/status/expiry(TMI-113), 혜택 정책을 분리하는 BenefitDefinition(TMI-115)을 구현했다.
- 8/31 Learning Core AttemptGroup 상태 event의 consumer·Session fencing·Transaction·W3C 관측을 구현했다(TMI-117). Learning Core publisher 완성과 production 연결은 후속이다.

회고 추천:

1. **혜택 정의·지급·소비를 분리했다.** eligibility 수신 즉시 무료권을 지급하지 않고 최초 reserve에서 Claim/Grant를 만든다. BenefitDefinition은 정책, Claim은 중복 방지, Grant는 보유 권리, ledger는 변경 이력이다. 유료 결제/구독 완료로 쓰지 않는다.
2. **시험 생성과 차감을 두 서비스에서 안전하게 연결했다.** reserve→Learning Core Session durable commit→confirm이며 confirm은 Summary 완료 시점이 아니다. replacement는 같은 consumption·mockExam을 사용해 추가 차감을 만들지 않는다.
3. **confirm/cancel/expiry 경합의 단일 승자.** RESERVED+version CAS를 먼저 수행하고 hold/ledger/Session 변경을 같은 Transaction으로 묶었다. 여러 worker의 중복 release와 중복 확정을 방지하는 테스트가 있다.
4. **이벤트 전달 중복과 순서 역전을 견딘다.** inbox와 projection을 원자 저장하며 같은 eventId/다른 payload를 conflict로, 구 revision을 stale로 처리한다. AttemptGroup에서는 이전 Session event와 terminal 경쟁을 fencing한다.
5. **unknown commit과 테스트 환경도 검증 대상이다.** replica-set Testcontainers로 rollback·race·commit 재확인을 검증했다. Docker/Testcontainers API 호환 문제에는 test worker API 1.44 설정을 사용했다. 당시 기록상 TMI-110 33개, TMI-113 82개, TMI-117 137개 전체 테스트 통과다.

근거: [eligibility](/Users/msde76/billing/docs/codex/WORKLOG.md:875), [reserve](/Users/msde76/billing/docs/codex/WORKLOG.md:1020), [lifecycle](/Users/msde76/billing/docs/codex/WORKLOG.md:1221), [AttemptGroup consumer](/Users/msde76/billing/docs/codex/WORKLOG.md:2063).

### 웹 백엔드

- 8/26 `1794e34`: 공개 블로그 상세 API에서만 조회수를 Mongo `$inc`/findAndModify로 증가시킨다. 기존 viewCount 누락은 0으로 읽고 updatedAt은 바꾸지 않는다. 당시 문서·테스트에 동시 40회 정확히 40 누적 검증이 있다. 고유 방문자 집계나 IP 중복 제거로 설명하지 않는다.
- 8/31 `a884c5a`: 시험 생성·문항 조회·AI 요청의 지정 시험지를 004에서 001로 변경했다. 성능 최적화나 시험지 무작위 배정으로 쓰지 않는다.
- 8/31 `89c8f9d`: 고정 TableContext DTO 대신 Map으로 Mongo의 비정형 표를 보존한다. 앱 Part 4 작업과 동일한 콘텐츠 문제를 다뤘지만 별도 저장소 변경이다.

추천 소재는 **동시 조회수 증가 유실 방지**와 **AI 콘텐츠의 유동적 스키마를 고정 DTO에 맞추면서 데이터가 빠지는 문제**다. 이번에는 Git diff와 과거 검증 기록만 조회했으며 웹 저장소·배포를 수정하지 않았다.

## 3. 사용자가 결정해야 하는 사항

회고 본문은 Learning Core의 채점 복구·CI flakiness·saga 리뷰, Identity의 Guest 승격/병합·탈퇴 lifecycle, Billing의 무료권/예약 경합, 웹의 조회수 원자 증가 중 실제 본인 판단과 고민이 기억나는 사례를 중심으로 구성하는 것을 권장한다. 이번 조사에서는 추가 승인이나 제품 결정을 요구하지 않는다.

## 4. 주요 위험과 미확인 사항

- 모든 저장소는 현재 로컬에서 찾은 관련 저장소 범위다. 앱 프론트·Python AI repository는 로컬 확인 범위에 없어 직접 Git 기반 개인 기여를 확정하지 않았다. 기존 팀원 회고의 AI 품질·앱 출시 수치는 팀 성과 출처로만 활용한다.
- `identity-quality-review-hotfix`는 Identity와 같은 이력이므로 별도 성과로 세지 않는다.
- `lambda-edge-resize-image`는 8월 commit 없음(마지막 로컬 commit 6/25). `toseonsaeng-learning-lab`와 `tosunsaeng-integration-test`는 로컬 .git이 없어 8월 이력 확인 불가다. Learning Lab 파일은 9월 시점이지만 생성 시점을 Git으로 확정할 수 없다.
- 9/2 S3 시험지 음성 누락 장애, Learning Core UserMerged 실제 migration·AttemptGroup publisher·Reservation background reconciliation·챌린지 runtime/배포는 8월 성과로 소급하지 않는다.
- production flag 활성화·운영 복구·정량 성능/비용 절감률은 commit·단위 테스트만으로 확인되지 않는다.

## 5. 현재 작업과 직접 관련된 설명

이번 작업은 기록 조사와 회고 소재 정리다. Learning Core의 이 문서·WORKLOG EOF·CURRENT_STATE만 수정한다. 기존 dirty 문서와 배포 상태는 보존하고 runtime/API/AI/S3/Redis/DB/외부 서비스·Git commit/push·Jira는 변경하지 않는다. 코드 변경이 없어 Gradle 테스트는 실행하지 않고 diff whitespace와 turn marker를 검증한다. Secret/Token은 기록하지 않는다.

## 6. 부록: 추가 상세 근거

- [기존 troubleshooting 사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md): 실제 구현 결함과 진단/설계 후보를 구분한 자료.
- [팀원 회고 보강 자료](TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md): 앱 출시·AI repair·콘텐츠·GPU 실험은 팀 성과와 담당자 범위로 구분한다.
- 월 경계는 한국 시간 기준이다. 초기 Git 조회에는 다음 달 00:00 경계가 포함될 수 있으나 보고에는 8월 날짜 commit만 사용했다.
