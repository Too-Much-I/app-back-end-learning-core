# 2026년 9월 저장소별 회고 소재 정리

## 1. 5줄 결론

- Learning Core는 AttemptGroup event 전달, phone 재가입 continuation, UserMerged 소유권 이전, 10초 챌린지와 Reservation 자동 복구를 구현했다.
- Identity는 owner event fan-out, JWT 확장, 전체 로그아웃, Refresh 응답 유실 복구, provider 연결/해제, 기존 Guest 복구·가입 재개와 운영 관측을 보완했다.
- Billing은 Guest merge/phone 재가입의 무료권 소유 관계, 중단 시험 continuation과 공개 무료 이용권 조회를 확장했다.
- 실제 출시 후 소재는 팀 기록의 9/2 S3 시험지 음성 누락, 기존 Guest 복구 hotfix, 월말 테스트 배포의 기동·검증 문제다. 본인 역할과 실제 복구 결과는 구분한다.
- 추천 중심은 ‘출시 후 기존 사용자와 기록을 보호하면서 서비스를 확장한 달’이며, 새 기능 구현·테스트 배포·모바일/production E2E 완성을 혼동하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### Learning Core

| 날짜/반영 | 작업 | 회고에 쓸 지점 |
|---|---|---|
| 9/1, TMI-118 | AttemptGroup durable outbox/publisher, Summary Transaction hotfix | 채점 결과와 시험 상태·Billing event를 함께 확정, 실패 event 재전달 |
| 9/3, TMI-122 | phone 재가입 시험 continuation | 과거 학습 기록 복사와 중단 무료시험 권리 승계를 분리 |
| 9/5, TMI-125 | UserMerged consumer·source deny·시험/결과/Summary userId 이전 | 8월 Identity merge 기반을 실제 학습 ownership migration으로 연결 |
| 9/7, TMI-126 | 10초 챌린지 별도 domain·업로드·AI 비동기 채점 | 긴 시험 외 짧은 학습 흐름, attempt/만료/중복·늦은 Callback/no_speech 계약 |
| 9/8 기록·9/9 반영, TMI-128 | Reservation background reconciliation·Sentry 연결 | 사용자가 재요청하지 않아도 이미 저장된 operation을 status-first로 복구 |
| 9/28~30, TMI-178/187 및 공용 Clock | 인덱스 검증·Repository 자동 등록·Clock 충돌 및 테스트 배포 수정 | 개별 기능 테스트와 여러 기능 동시ON/실제 기동 검증의 차이 |

핵심 기술 사례:

- TMI-118: DuplicateKey를 catch해도 Mongo Transaction은 살아나지 않는다. abort된 Transaction 안에서 계속 쓰지 않고 바깥에서 새 Transaction으로 전체 작업을 재시도하도록 hotfix했다. 운영 사고로 단정하지 않고 리뷰 발견 사례로 쓴다.
- TMI-125: commit 성공 여부가 불명확할 때 inbox eventId/digest의 완료 증거를 재조회해 성공/충돌/재시도로 수렴한다. owner guard로 migration과 다른 writer의 경합을 막는다.
- TMI-126: 실제 Mongo·mongosh 검증에서 비동기 사전검사 완료 전에 쉘이 종료되는 결함을 발견했다. Docker API 호환 문제도 실행 한정 설정으로 해결해 통합 테스트를 진행했다.
- TMI-128: timeout/404만으로 ‘예약 없음’ 또는 ‘취소 가능’을 확정하지 않는다. HTTP와 worker가 공통 lease/CAS를 사용하고 불확실한 operation은 근거를 보존해 격리한다.
- TMI-178: Mongo 기본 _id 인덱스의 암묵적 uniqueness를 명시적 isUnique 필드로만 판단하던 검증을 수정했다.
- 테스트 배포: 9/28 유예시간120초→300초 변경 후 같은 이미지가 약174초에 healthy가 되는 것을 확인했다. 최초 health 실패의 모든 원인을 확정한 것으로 쓰지 않는다.
- TMI-187: exams-only Repository scan 등을 정리하고 Boot root 검색으로 14개 Repository를 등록했다. UserMerged/Challenge 동시ON에서 Clock 후보 충돌은 공용 UTC Clock과 기존 이름 alias로 수정했다.

9/30 마지막 작업 기록은 테스트 LC에 UserMerged/Challenge ON, health·DB 준비·방향별 인증 연결 검사 완료다. 실제 MEMBER 음성 학습/AI 채점·Identity merge/204·ownership/source deny 성능 E2E는 후속으로 남아 있었다. Billing/AttemptGroup/withdrawal flags는 OFF였으며 이번 조사에서 원격 상태를 재조회하지 않았다.

### Identity

| 날짜/반영 | 작업 | 회고에 쓸 지점 |
|---|---|---|
| 9/3, TMI-123 | owner event durable fan-out·Billing SigV4 | UserMerged 등을 여러 서비스에 각각 신뢰성 있게 전달 |
| 9/7~8 | account_type claim, Billing aud/scope(TMI-127) | Guest/MEMBER와 서비스별 접근 계약 연결 |
| 9/9, TMI-129 | Firebase logout-all revoke | 내부 Session 폐기와 외부 인증 서비스의 revoke 연결 |
| 9/10, TMI-130 | Refresh Token 응답 유실 복구 | 서버 rotation 성공 후 앱이 새 응답을 못 받는 상황의 제한된 same-operation replay |
| 9/14, TMI-131 | provider unlink/relink lifecycle | SNS 연결 변경 중 외부/내부 상태 불일치와 재시도 |
| 9/18, TMI-134 | 기존 Guest Session 한시 복구 main hotfix | 같은 userId/기록 유지, 새 사용자 자동 생성으로 우회하지 않기 |
| 9/21, TMI-169 | Guest enrollment 재개/만료 | 중단 가입의 활성 ID 재사용·만료 교체·남은 가입 요건 전달 |
| 9/22, TMI-176 | 요청 로그/JWKS rotation/DB 진단/Sentry release | 코드가 있어도 실제 관측 경로와 키 회전 검증을 별도 확인 |
| 9/30, TMI-188 | Firebase signup/Guest upgrade quality review 선택 동의·공개 정책 조회 | 신규/기존 사용자 동의 누락·유지·철회의 의미 구분 |

주의: TMI-133의 Guest 최초 응답 암호화 복구 계획은 문서상 9/16 미구현 취소다. 관련 PR 제목/merge만 보고 이 기능을 구현한 성과로 넣지 않는다. TMI-134는 다른 한시적 기존 Guest 복구 hotfix이며 main 시점 모델과 사용자 승인 보안 경계를 따른다. 초기401 원인 및 실제 운영 복구 완료는 자료만으로 확정하지 않는다.

### Billing

- 9/1 TMI-117 consume tracing 보완.
- 9/2 작업 기록·9/3 코드 반영 TMI-120: retained trial owner rebind consumer, Guest merge와 phone 재가입 정책·projection.
- 9/3 phone continuation discovery와 명시적 replacement: 새 계정의 과거 학습 답안/결과는 복사하지 않으면서 미완료 무료시험을 같은 consumption·시험지로 새 Session에 연결한다.
- 9/7 무료 이용권 조회 API와 Session 귀속 증빙: 현재 권리/상태를 앱에 전달할 공개 reader와 JWT 검증·정합성 snapshot을 구현했다.
- 기간제 유료 이용권/결제와 학습 기록 삭제는 9월 설계 자료에 있으나 runtime 완성 성과로 포함하지 않는다.

### 그 밖의 저장소와 작업

- 웹 백엔드와 lambda-edge-resize-image: 로컬 전체 ref에서9월 commit 없음. 실제 운영 콘텐츠/설정 변경이 없었다는 의미는 아니다.
- Learning Lab: GradingJob/GradingStatus와 단위 테스트의 소규모 Java 학습 코드가 있다. 9/1 WORKLOG의 별도 학습 프로젝트 방향과 연결할 후보지만 .git이 없어 완료 시점을 확정하지 않는다.
- tosunsaeng-integration-test: 로컬 Git 이력 없음. 현 파일에는 회원 통합/Challenge 검증 도구가 있지만 모두 9월 구현이라고 확정하지 않는다.
- 시스템/AWS 구성도·멘토링/발표 정리, 프론트 계약 인계, 학습 기록 삭제·유료권 설계는 별도 보조 소재다.

## 3. 사용자가 결정해야 하는 사항

9월 본문을 쓰기 전에 실제로 기억나는 사건과 본인 담당 범위를 확인한다. 특히 아래 세 소재가 좋다.

1. 출시 후 중단 사용자 패턴을 발견한 S3 시험지 음성 누락: 발견/재현/복구 중 본인 역할과 처리 결과.
2. 기존 Guest 복구: 사용자가 어떤 증상을 겪었고, 왜 임시 hotfix를 선택했는지와 실제 복구 여부.
3. 10초 챌린지와 월말 배포: 짧은 학습을 추가한 이유, 기능 조합에서 생긴 기동 실패와 당시 대응 경험.

8월과 이어지는 공통 주제는 ‘첫 사용자에게서 시작해 기존 기록과 계정을 보호하고, 실패 뒤 돌아올 수 있는 흐름을 만든 과정’이다. 현재는 소재 조사이며 회고 본문/감정/사용자 반응을 새로 만들지 않는다.

## 4. 주요 위험과 미확인 사항

- 9/2 S3 장애는 팀원 회고에 따르면 신규 사용자들이 Q4 이후 멈췄고 계정 초기화 후 Q5 음성 누락을 재현했다. 콘텐츠 정리 중 파일을 복사 대신 이동하여 시험지 음성이 없어졌고, 기존 계정은 다른 시험지로 정상 테스트돼 놓쳤다. 정확한 복구 방법·본인 역할·전체 응시 재검증은 확인 필요다.
- 구현 commit·Jira 완료는 production 활성화나 모바일/AI E2E 완료와 다르다. 미구현 취소한 설계와 운영 기대 결과를 과장하지 않는다.
- 로컬 모든 ref에는 merge/cherry-pick/stash와 문서 commit이 포함된다. 중복 성과로 집계하지 않는다. 원격 fetch·운영 조회 없음.
- 앱 프론트/Python AI 원본 Git은 이번 조사에서 직접 확인하지 않았다.

## 5. 현재 작업과 직접 관련된 설명

9월 Git/WORKLOG·계약/사례집 기반의 기록 정리다. 이 문서와 Learning Core WORKLOG EOF/CURRENT_STATE만 수정하며 8월 회고 초안과 기존 dirty 문서/배포 상태를 보존한다. 코드/공개 API/AI/S3/Redis/DB/원격/Jira/commit/push 변경 없음. 코드 변경이 없어 Gradle 테스트 미실행, git diff --check로 문서 검증. 당시 테스트 기록과 이번 실행을 구분한다. Secret/Token 비기록.

## 6. 부록: 상세 근거

- [Learning Core WORKLOG](WORKLOG.md:6185), [Reservation worker](WORKLOG.md:9719), [Repository 등록](WORKLOG.md:12651), [Clock](WORKLOG.md:12802).
- [Troubleshooting 사례집](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md:166), [팀 S3 장애](TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md:331).
- [Challenge rollout](TEN_SECOND_CHALLENGE_ROLLOUT.md), [9월 테스트 배포 상태](deployment/USER_MERGED_TEST_DB_PREPARATION_STATUS.md).
- [Identity WORKLOG](/Users/msde76/identity/docs/codex/WORKLOG.md), [Refresh 복구 계획](/Users/msde76/identity/docs/contracts/refresh-token-response-recovery-stage-9-plan.md), [Guest 재개 계획](/Users/msde76/identity/docs/contracts/firebase-guest-enrollment-resume-plan.md).
- TMI-134의 hotfix 문서는 현 branch에서 없으므로 Identity commit2ccfb2fb의 docs/contracts/guest-session-recovery-hotfix-TMI-134.md를 읽었다.
- [Billing WORKLOG](/Users/msde76/billing/docs/codex/WORKLOG.md:2436).
