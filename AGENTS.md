# 프로젝트 목적

## 2026-10-27 프로모션 기프티콘·이메일·HEIC 테스트 전달 승인

- 사용자 「노션에 수정하면 커밋, 푸시해서 테스트 서버에 올려줘」로 이번 프로모션 입력 교체·HEIC 지원의 Notion 문서 갱신, 커밋·푸시·PR·develop 병합 및 기존 테스트 배포를 승인했다. CI 성공 후 전달하며 main/운영·기능 설정·실DB·타서비스 변경은 제외한다. 기존 인증/중복 정책/비공개 저장은 유지한다.

## 2026-10-27 은행명·결과 기반 이어하기 테스트 전달 승인

- 사용자 「커밋 푸시 머지 허용할테니깐 테스트 서버에도 올려줘」로 bankName 문자열 및 결과 생성 기준 이어하기·첫 세트 복귀 변경의 커밋·푸시·PR·develop 병합과 기존 테스트 배포를 승인했다. 운영·기능 설정·실DB·타서비스 변경은 제외한다. 기존 인증·소유권·삭제 제한 및 part 지정 호환은 유지한다.

## 2026-10-26 최근 풀이 이어하기 테스트 전달 승인

- 사용자 문항 단위 이어하기 방향 승인 및 「수정해서 테스트 서버에 올려줘」와 명시 Git 예외 회신에 따라 이번 변경의 구현·테스트·커밋·푸시·PR·develop 병합·기존 테스트 배포를 허용한다. 운영·타서비스·기능 설정·실DB 강제 변경은 제외한다.
- part 없는 level-only resume와 기존 part 호출 호환, 기존 제출 최초 queuedAt 재사용 및 Part1 명시 완료 활동 시각만 범위에 포함한다. 기존 인증/소유권/삭제·유료 콘텐츠 검사와 AI/음성/모의고사 계약은 유지한다.

## 2026-10-24 이어하기·프로모션 테스트 전달 예외

- 사용자 「커밋 푸시해서 테스트 서버에 올려줘」로 feat/learning-resume-promotion의 두 신규 API 및 관련 테스트·문서 커밋/푸시/PR/develop 병합과 기존 테스트 자동배포를 허용한다. CI 통과 후 전달하며 운영 main 배포는 제외한다.
- 프로모션 기본 OFF 및 개인정보 정책/비공개 저장 준비 gate를 유지한다. 실DB·S3·IAM 변경과 기능 활성화는 이번 전달에 포함하지 않는다. 원래 작업폴더의 미커밋 변경을 포함하지 않는다.

## 2026-10-23 파트별 학습·청크 테스트 전달 승인

- 사용자 요청으로 feat/part-learning-access-chunks 브랜치의 학습 계획 구현·격리 테스트 및 테스트 서버 기능 ON 배포를 허용한다. 전달에 필요한 commit/push/PR/develop 병합과 기존 test workflow 실행을 포함한다. main/prod, Billing/Identity 저장소와 서비스, 실DB 삭제·index 변경·새 IAM/네트워크 권한 확대는 제외한다.
- 첫 세트 전체 무료·나머지 유료 본문/음성 제공에만 part-learning-eligibility 조회, create/submit/retry 미조회 및 직접 채점 호출 위험 수용, 신규 학습300회·실패 재시도 일일 차감 제외를 따른다. 청크 경계 전달·v3 콘텐츠의 기존 v2 미디어 재사용을 구현하며 앱 퍼즐/이어 풀기는 별도다. 이전할 예전 데이터가 없어 진도 이전은 만들지 않는다.
- 원본 작업트리의 미커밋 변경을 보존하고 별도 작업트리를 사용한다. 테스트 활성화 전에 정확한 무료 unit ID/ready manifest와 Billing 테스트 endpoint 준비를 확인한다. 인증·소유권·삭제·탈퇴 및 기존 모의고사/AI 계약을 유지한다.

## 2026-10-24 요약 비동기 보호 쓰기 누락 보완

- 사용자 진단 후 「어 해줘」 승인으로 SummaryDispatchScheduler의 누락된 read-check 경계와 격리 비동기 테스트를 보완한다. 기존 보호 쓰기 제거 전달의 후속으로 commit/push/PR·테스트 확인 후 운영 배포를 진행한다.
- 대상 기존 요약은 기존 인증된 재시도 경로를 사용한다. 인증 우회·토큰 발급·DB 강제 변경·새 관리자 복구 API·타서비스/IAM 변경은 포함하지 않는다. 사용자 앱 재시도가 어려우면 미복구 사실과 추가 수단 필요를 보고한다.

## 2026-10-23 채점 보호 쓰기 제거 승인

- 사용자 브랜치 생성·수정·운영 배포 요청 및 동시 처리 위험 수용에 따라 모의고사 채점/재요청/콜백의 공용·시험별 보호 문서 쓰기를 읽기 검사로 대체한다. 같은 스레드의 저장 직전 검사와 요약 완료 처리에도 적용한다. 신규 시험 생성·Billing 예약·삭제·통합·탈퇴 자체의 writer는 변경하지 않는다.
- 상태 확인 후 lifecycle 변경과 겹친 늦은 저장, 삭제 후 기록 재생성 가능성을 수용했다. 권한·소유권·삭제/탈퇴/환불 상태 조회, 실제 Job/결과 Transaction·멱등성·bounded retry와 외부 계약은 유지한다. 자동 재정리를 보장하지 않는다.
- 이번 변경의 commit/push/PR develop·main 병합 및 기존 workflow 테스트 확인 후 운영 배포를 허용한다. 실DB 정리·기존 fence 삭제·타서비스/IAM 변경은 포함하지 않는다.

## 2026-10-22 Callback write isolation 전달 승인

- 사용자 구현·운영 배포 요청 및 테스트 선검증 순서의 후속 승인으로 이번 callback CORE/AZURE fence와 lifecycle bridge, 관련 테스트/문서의 커밋·푸시·PR·develop/main 병합과 테스트 후 운영 배포를 허용한다. main의 기존 upload URL guard·이벤트 시각 수정도 develop 전달에 포함한다.
- lifecycle ON/isolation OFF 배포 및 구 task drain 확인 후 isolation ON을 적용한다. 기존 인증·삭제·탈퇴·통합·환불 제한과 외부 계약은 유지한다. 일반 Git/배포 금지는 이번 범위에서만 대체하며 Billing/Identity 코드·IAM·Secret·기존 업무 데이터 수동 수정은 제외한다.

## 2026-10-22 AttemptGroup 이벤트 시각 핫픽스 전달 승인

- 사용자 「그거 빨리 수정해서 올려줘」에 따라 신규 AttemptGroup 이벤트의 밀리초 시각 규격 수정, 회귀 테스트, 전용 브랜치 커밋·푸시·PR 및 main 병합과 기존 운영 배포 workflow 실행을 허용한다. 일반 Git/배포 금지의 이번 수정 한정 예외다.
- 기존 공개 API/AI/S3/Redis/feature flag와 이미 저장된 outbox의 eventId·canonical payload·digest는 유지한다. 실패 이벤트 재작성·재전송, 실DB 변경, Billing/Identity 코드 수정 및 IAM 확대는 이 승인에 포함하지 않는다.

## 2026-10-21 main/develop 동기화·Mongo 진단 전달 승인 예외

- 사용자는 이번 작업 한정 AGENTS 예외 추가 요청에 「어 해줘」로 승인했다. main→develop 동기화, 기존 PR54 단어·문장 정합성 검증 유지, 비민감 Mongo 오류·재시도 로그 보강의 커밋·푸시·PR 생성·develop/main PR 병합 및 기존 workflow의 테스트·운영 배포를 허용한다. 일반 Git 금지와 이전 과제의 배포 금지는 이번 전달 범위에서만 대체한다.
- CI 및 테스트 서버 확인 후 운영으로 전달한다. 기존 API·재시도 정책·Sentry 개인정보 정제를 유지하며 Secret/Token/발화 원문을 기록하지 않는다. 별도 기능 활성화, 실DB 수정·삭제, IAM 권한 확대, Billing/Identity/웹 저장소 변경은 허용하지 않는다.

## 2026-10-17 운영 기동 준비 코드 보완 승인

- Billing 측 운영 호환 점검 후 사용자 「어 해줘」로 LC 운영 readiness 코드·격리 테스트 보완을 승인했다. test 승인과 분리된 production 승인 기본 OFF, prod→production 환경 매핑과 기존 sealed manifest/owner/index·JWT/TLS 검증을 유지한다. 아래 test-only 금지는 이 준비 코드 범위에서 대체하며 실제 prod 활성화·DB 설치·배포·commit/push는 수행하지 않는다.

## 2026-10-17 공존 R2 구현·test 전달 승인

- 사용자 「브랜치 파서 하고 끝나면 커밋 푸시 하고 테스트 서버에 배포」로 공존 R2 코드·격리 테스트와 feat 브랜치 commit/push/PR/develop 머지·LC test 배포를 승인했다. main/prod·Billing/Identity·실DB/index/manifest 변경은 제외한다. 사용자 DS_Store는 전달하지 않는다.
- `/api/v1/exams`는 명시 무료 writer, `/api/v2/exams`는 기존 paid saga로 분리한다. OFF 신규 v1은 기존 `409 EXAM_CREATION_PROCESSING`, Retry-After 1로 거절한다. 기존 무료 시험 reader는 OFF와 무관하게 유지한다. 이 승인 범위에서 기존 v1의 전역 paid 분기를 대체한다.

## 2026-10-17 심사 기간 공존 단순화 계획 R2

- 사용자 「지금까지 이야기한 걸로 계획서 수정」 요청을 반영해 공존을 심사 기간 임시 호환으로 한정한다. 기존 버전 검사 재사용, 서버 무료 출처 한 필드와 구경로 신규 생성 ON/OFF 한 설정을 유지하며 별도 정책 revision/관리 UI/장기 왕복 전환은 제외한다.
- 진행 중 Billing 시험/미완료 paid operation의 구경로 신규 생성은 안전 거절하고 정상 paid→무료 교체 기능을 만들지 않는다. 기존 시험의 조회/제출/채점/완료, 삭제/탈퇴/owner·공통 생성 fence, 새경로 무료 fallback 금지는 유지한다. 완료된 Billing 이력만으로 영구 계정 차단을 추가하지 않는다.
- 기준 `docs/codex/LC_BILLING_COEXISTENCE_IMPLEMENTATION_PLAN.md` R2. 이번은 계획/ADR 동기화이며 코드/DB/봉인·서버 설정/배포·commit/push 승인이 아니다. exact 신규 route/field/오류 명칭은 구현 전 대조한다.

## 2026-10-17 구앱 무료·신앱 Billing 공존 계획 작성

- 사용자 「좋아 해줘」로 `docs/codex/LC_BILLING_COEXISTENCE_IMPLEMENTATION_PLAN.md`의 공존 구현 계획·새 생성 API 제안 정리를 요청했다. 이번은 문서/ADR 동기화이며 제품 코드·실DB/봉인·설정·배포·commit/push 승인이 아니다.
- 승인 방향: 전환기간 구경로 무료 시험 생성·제출/채점/완료 유지, 신앱/심사 앱 동일 Billing 필수 경로, 양쪽 삭제/탈퇴/owner 차단, 새경로 오류 무료 fallback 금지. 직접 구경로 호출의 무료 우회 위험은 수용하며 종료일은 별도다.
- 기존 manifest bytes/hash/count를 변경하거나 source 누락을 무료로 추론하지 않는다. 서버가 생성 시 기록하는 신규 무료 출처와 공통 reader/lifecycle·생성/삭제 fence를 설계한다. 신규 route/field/flag는 제안이며 기존 runtime guard를 임의 해제하지 않는다. Billing 원장/무료권 소비·복원 근거를 소급 생성하지 않는다.

## 2026-10-16 LC test TLS 코드 전달 승인

- 사용자 「어 해줘」로 TLS 주입 코드의 commit/push/PR/develop 머지 및 기존 test 자동 배포·확인을 승인했다. 기능 OFF와 기존 서버 설정을 유지한다. main/prod·네트워크·DB/index 변경 및 기능 활성화는 제외한다. 사용자 DS_Store는 전달하지 않는다.

## 2026-10-16 LC test 환불 TLS 준비 승인

- 사용자 후속 요청으로 LC test 환불 수신 TLS의 파일 주입 준비·격리 검증을 진행한다. 적용 직전 별도 응답으로 테스트 전용 인증서/PKCS12 생성, `tosunsaeng/test/learning-core/access-events-tls` Secret 저장 및 LC test 실행 역할의 해당1개 읽기 권한 추가를 승인했다. 실제 권한 적용은 exact ARN 확인 후 수행한다.
- 기존 운영/Billing 키·네트워크 공개범위·feature OFF 상태는 변경하지 않는다. 이 승인은 ALB/SG 접근 확대·Billing 발신 변경·실DB/index 설치·paid 활성화나 commit/push 승인이 아니다. 비밀값은 저장소/채팅/로그에 기록하지 않는다.

## 2026-10-16 PR #46 동시성 보완 승인

- 사용자 후속 「어 해줘」로 CI에서 재현된 학습 사용량·환불 중복 동시성의 진단/보완·격리 검증 및 기존 PR 전달/머지/test 배포 재개를 승인했다. 재시도 상한·unknown commit 비재실행·owner/중복 충돌 의미는 유지한다. main 배포·Billing 활성화·실DB/index 설치는 제외한다.

## 2026-10-16 prod/test 정리 전달 승인

- 사용자 후속 「어 해줘」로 현재 환경 정리의 commit/push/PR/develop 머지와 test 자동배포 및 적용 확인을 승인했다. main 배포·기존 별도 서비스 변경·Billing 기능 활성화·DB/index 설치는 제외한다. 사용자 DS_Store는 전달하지 않는다.

## 2026-10-16 prod/test 배포 프로필 정리

- 사용자 prod/test 두 배포 환경 기준 및 후속 「어 해줘」로 LC 코드·배포 설정·격리 테스트 정리를 승인했다. 배포 대상은 main→prod, develop→test이며 서버 test에서 기존 인증/index/트랜잭션 검증을 유지한다. local/test 개발·자동 테스트는 명시 배포 환경 없는 경우 기존 계약을 유지한다. 실제 AWS 설정/리소스·DB·배포·활성화·commit/push는 이번 코드 정리와 분리한다. Billing/Identity wire의 환경 enum과 기존 AWS 이름/도메인은 임의 치환하지 않는다.

## 2026-10-16 legacy 전달 및 테스트 활성화 준비 승인

- 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 legacy runtime/reader/구writer 방어 및 관련 문서의 commit/push/PR/develop 머지와 기존 테스트 자동배포를 승인했다. 사용자 DS_Store는 제외한다. 다음 feat 브랜치에서는 test 환경 한정 활성화 준비 코드와 격리 검증을 구현한다. 실DB/index·실목록 설치/봉인·실키/운영설정·기능 활성화·Billing/Identity 변경은 제외한다. staging/production의 기동차단을 임의 해제하지 않으며 후속 변경 자체 commit/push는 별도 요청 전 수행하지 않는다.

## 2026-10-16 P6.1·삭제 연동 전달 및 legacy 후속 승인

- 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 현재 LC P6.1/삭제 sender·paid 연결 변경의 commit/push/PR/develop 머지 및 기존 테스트 자동배포 트리거를 승인했다. 사용자 DS_Store는 제외한다. 후속 feat 브랜치에서 exact manifest 기반 legacy runtime 및 접수증거 불충분 시 동일 문제 정책의 코드/격리 테스트를 구현한다. 실DB/index·목록 설치/봉인·운영 배포·기능 활성화·Billing/Identity 수정은 제외하며 후속 변경 자체의 commit/push는 별도 요청 전 하지 않는다.

## 2026-10-16 삭제 후 INITIAL·paid 경로 연결 승인

- 개발 잔여 범위 안내 후 사용자 「어 해줘」로 LC 삭제 ACK 기반 새 INITIAL·기존 paid 서비스의 공개 생성/worker 연결과 격리 테스트를 승인했다. 기존 P6.1/삭제 sender 변경을 보존한다. legacy runtime·공동 E2E 전 활성화 차단을 유지하며 실DB/index·Billing/Identity 변경·배포·commit/push는 제외한다.

## 2026-10-16 TMI-217 LC 발신 구현 승인

- 사용자 「어 해줘」로 동결 draft-3>2>1의 LC 삭제 발신·불변자료/204저장·cleanup 독립·전체ACK checkpoint·TTL/120일 보호와 격리 테스트를 승인했다. 기존 P6.1 dirty와 사용자파일을 보존하며 Billing/Identity 변경·실DB/index 적용·배포/활성화·commit/push는 제외한다. 기본OFF·공동runtime/실환경gate를 유지한다.

## 2026-10-14 P6.1 구현 승인

- 전달/머지 요청 후 사용자가 후속 대상을 「LC P6.1: 120일 보관·안전한 정리 준비」로 확정했다. 별도 feat 브랜치에서 최초접수/terminal 보존 경계·미완료 자동복구 상한·읽기 전용 정리 적격성 및 로컬/격리 Mongo 테스트를 구현한다. 실제 purge·실DB/index 변경·운영 설정/배포·Billing/Identity 수정은 제외한다. P6.1 자체 commit/push는 별도 요청 전 수행하지 않는다.

## 2026-10-14 P6·TMI-220 전달 승인

- 사용자 「좋아 커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 현재 LC P6 준비·TMI-220 보완의 커밋/푸시/PR/develop 머지 및 기존 테스트 자동 배포 트리거를 승인했다. 기능 설정/운영 배포/실DB 변경은 제외한다. 후속 대상은 직전 Billing PLAN-012와 LC P6.1 사이의 범위를 확인하고 착수한다. 사용자 DS_Store는 전달 대상에서 제외한다.

## 2026-10-14 TMI-220 LC 보완 구현 승인

- 사용자 「보완 해줘」로 합의된 과거 Session 기접수 예외의 LC 코드·격리 테스트를 승인했다. 신규사용 차단·단일 revoke wire·owner/삭제/terminal 우선은 유지한다. Billing/Identity 수정·공동E2E 완료·실DB/배포/활성화·commit/push는 제외한다. 기존 P6 변경을 보존한다.

## 2026-10-14 P5 전달·P6 준비 구현 승인

- 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 P5/P5.1 커밋·푸시·PR/develop 머지와 기존 테스트 자동배포 및 후속 P6 legacy/index 전환 준비 코드·격리 검증을 승인했다. 실제 inventory/DB/index 적용·기능 활성화·운영 배포·P6.1 최종 purge는 제외한다. TMI-220 기접수/revoke 공동 의미 보완은 별도 gate이며 임의 완화하지 않는다. P6 변경 자체는 별도 요청 전 커밋/푸시하지 않는다.

## 2026-10-14 P5.1 연결 구현 승인

- 후속 「어 구현해줘」로 P5의 Spring ON bean 조립·전용 TLS connector와 격리 연결 테스트를 승인했다. 실제 설정/키 배포·실DB/index 적용·활성화·commit/push는 제외한다. P6 legacy/owner/V07 검증 전 rollout 차단은 유지한다.

## 2026-10-14 P4 전달·P5 구현 승인

- 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 P4/P4.1 커밋·푸시·PR/develop 머지 및 기존 테스트 자동 배포, 후속 P5 역방향 revoke 수신 코드/격리 검증을 승인했다. 기능 설정·운영 배포·실DB·P6 전환·신규 삭제sender/ACK는 제외한다. P5 변경은 별도 요청 전 커밋/푸시하지 않는다.

## 2026-10-14 P4.1 구현 승인

- 후속 사용자 「어 구현해줘」로 생성 중 삭제 미접수·동일 사용자 Transaction fence·공개 오류·삭제된 성공 replay의 P4.1 코드/격리 테스트를 승인했다. 신규 Billing 삭제 sender/ACK 계약·실DB·배포·활성화·commit/push는 제외한다. 기존 P4 변경을 유지한다.

## 2026-10-14 P3 전달·P4 구현 승인

- 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 P3/P3.1·fixtures-2 전달/PR/develop 머지·기존 테스트 자동 배포 및 후속 P4 코드/격리 테스트를 승인했다. P4는 신규 접수 차단과 기접수 채점/파생 Summary 분리이며 P4.1 삭제 공개 연결·실DB·기능 설정/paid 활성화·운영 배포는 제외한다. P4 변경은 별도 요청 전 커밋/푸시하지 않는다.

## 2026-10-14 P3.1 구현 승인

- 사용자 「어 구현해줘」로 기존 P3 브랜치의 MEMBER 영속 대기·HTTP same-key 재개·worker status-only 관측 및 격리 예외 검증을 승인했다. 원인 불명 NEEDS_REVIEW/auth/owner 차단은 해제하지 않는다. 공개 route/자동 scheduler/paid 활성화·실DB·배포·commit/push는 이번 요청 범위가 아니다.

## 2026-10-14 P2 전달 및 P3 구현 승인

- 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」로 P2/P2.1 커밋·푸시·PR/develop 머지와 기존 테스트 자동 배포 트리거, 후속 P3 코드/격리 테스트를 승인했다. 기능 설정·운영 배포·실DB 변경은 제외한다. E06-owner fixture 문구 정정은 추적하며 병행 구현하되 공동 최종 검증·활성화 전 해결한다. 원 fixture는 유지한다.
- P3는 confirm 성공 후 exact 원 Session 신규접수 차단·새 Session 활성·operation 성공 및 status-first 복구/120일 terminal 보존 경계다. P3.1 MEMBER 제한적 재개, P4~P6와 공개 paid 활성화는 완료로 간주하지 않는다.

## 2026-10-14 P2.1 구현 승인

- 사용자 「어 해줘」로 현재 P2 브랜치에서 strict 오류 분류·phone paid/free 응답/영속 검증·관련 테스트를 승인했다. MEMBER 대기 재개/lease·J3 terminal 원자 처리와 공개 saga/worker 활성화는 P3 후속이다. 기존 paid 기동 차단, fixture 원본 및 API/AI/S3/Redis 계약을 유지한다. 이번은 commit/push/실DB/배포 요청이 아니다.

## 2026-10-14 P2 구현 승인

- 사용자 「좋아 브랜치 파서 다음꺼 구현해줘」로 P2 Billing reserve/status reader·restart 요청·source/transition 영속 및 로컬 테스트를 승인했다. P2.1 오류/phone 확장과 P3 lifecycle/worker 연결은 별도 후속이며 paid 활성화 차단을 유지한다. 기존 A/fixture bytes, 공개/AI/S3/Redis 계약과 기존 reader 경로를 보존한다. 이번은 commit/push/배포 요청이 아니다.

## 2026-10-14 TMI-218 전달 승인 예외

- 사용자 커밋·푸시·머지 요청과 「머지까지 해줘」 확인으로 이번 P1 변경의 커밋/푸시/PR/develop 머지 및 기존 테스트 서버 자동 배포 트리거를 허용한다. 일반 Git 금지 규칙의 이번 요청 한정 예외다. 기능 설정/운영 배포는 변경하지 않는다.

## 2026-10-14 TMI-218 P1 구현 승인

- 사용자 「브랜치 내가 변경했던 거처럼 해서 변경해서 구현해줘」로 P1 코드/로컬·격리 Mongo 테스트를 승인했다. 기존 feat/TMI-번호-설명 방식의 브랜치에서 작업한다. 앞선 P0 문서 전용 제한은 이 P1 범위에 한해 대체한다.
- 접수 receipt/완료 marker 공통화, 알림 독립 coverage, 필수집합 snapshot, 단일 mock/startReason·원대상 고정과 동일 key/접수 경합 검증을 구현한다. P2/P3 준비 전 새로운 준비 operation을 기존 Billing wire/worker로 전송하지 않는다.
- P2~P6 전체 구현, Billing/Identity 저장소, 실DB·index 적용·배포·활성화/commit/push는 제외한다. 기존 공개/AI/S3/Redis와 fixture bytes를 유지한다.

## 2026-10-14 LC 유료 시험 계획 승인·P0 문서 준비

- 후속 사용자 「그럼 이거까지만 승인할게 수정해줘」로 세 내부 저장 단순화의 문서 반영을 승인했다: 별도 생성 결과 receipt 없이 기존 operation120일 보관, 별도 inbox 없이 그룹별 단일 revoke 문서, Session 접수 목록 중복 없이 기존 접수 receipt/완료 marker 재사용. 이 절은 설계 동기화이며 제품 코드/실DB 승인이 아니다. MEMBER 관측 축소·NEEDS_REVIEW 자동재개 예외 제거·추가 설정 조합 단순화는 포함하지 않는다. Billing wire/제품 코드는 불변이며 T04 등 저장방식 종속 공동 설명은 별도 보완한다.

- 사용자 계획안 승인과 후속 「어 해줘」는 현재 P0 문서·저장 구조·설정 조합 준비에 적용한다. 이번 작업은 제품 코드 구현/실DB/index 적용/배포/활성화 승인이 아니다. 후속 구현은 승인된 단계·잔여 계약 경계를 확인하고 진행한다.
- 기준: `docs/codex/LC_BILLING_PAID_EXAM_IMPLEMENTATION_PLAN.md`, `docs/codex/LC_BILLING_PAID_EXAM_P0_DESIGN.md`, `docs/adr/ADR-LC-BILLING-PAID-EXAM.md`. A/paid R2/최신 simplified exact fixture 원본 bytes/hash를 변경하지 않는다.
- 신규 시험 선정 기준은 필수 retryCount=0 답안의 durable 전체 접수다. 채점/요약 완료까지 기다리지 않으며 접수 미완료는 동일 mock, 전체 접수 후에는 기존 선정 정책으로 다음 mock을 고른다. 다른 적격 후보가 실제 없을 때만 동일 mock 예외를 적용한다. 같은 요청의 최초 판정을 고정한다.
- 계획상 신규 대기 Session은 사용 불가다. Billing confirm 성공 뒤 exact 이전 신규제출 차단·새 Session 활성·operation 성공을 같은 Transaction으로 마무리한다. 기접수 채점/파생 Summary는 원 접수 증거로 완료하되 삭제/탈퇴/owner deny를 우회하지 않는다.
- phone paid-first/조건부 target 복원은 Billing 책임이며 LC는 exact snapshot을 검증한다. MEMBER 보강은 앱의 기존 purchase-account 호출, worker 새 reserve 금지, NEEDS_REVIEW 제한적 재개는 동일 MEMBER 대기 소진의 영속 원인과 현재 안전 조건 모두 필요하다.
- 생성 중 삭제는 미접수이며 queue를 만들지 않는다. 120일 멱등 보장은 미완료 증거·금융/소비/복원1회·권한 차단 보존과 분리한다. 신규 삭제 route/과거 owner 삭제/자료별 최종 purge는 별도 공동 계약 gate다.
- 본 절은 후속 설계 기준이며 기존 runtime 동작이 이미 바뀌었다는 뜻이 아니다. 공개 성공 DTO/BaseResponse, AI user_id=examId, retryCount/S3/Redis 계약, default-off와 W OFF/P ON drain 및 W OR P index 점검을 유지한다. Billing/Identity 저장소를 수정하지 않는다.

## 2026-10-13 LC–Billing workload JWT B 구현 승인 예외

- 문서 동기화 후 사용자의 「어 해줘」로 LC B 코드·관련 테스트 구현이 승인됐다. **C 재처리 도구, 실제 DB/AWS/배포/활성화는 별도 승인 전 수행하지 않는다.** 격리 Testcontainers DB 검증은 B 테스트 범위다.
- 기준: `docs/contracts/LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md`, `LC-BILLING-WORKLOAD-AUTH-R1.md`, `LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md`, `docs/codex/LC_BILLING_WORKLOAD_JWT_B_IMPLEMENTATION_PLAN.md`, `docs/adr/ADR-LC-BILLING-WORKLOAD-JWT.md`.
- TMI-116/TMI-128/TMI-122 및 AttemptGroup의 기존 SigV4/Lattice 전제는 이 B 전환의 두 Billing 발신 구현 범위에 한해 workload JWT/HTTPS exact-auth gate로 대체한다. 배포·활성화를 뜻하지 않으며 기존 업무·공개 API/AI/S3/Redis/복구·멱등 계약과 default-off를 유지한다. A 규격과 fixtures-1 bytes/hash를 변경하지 않는다.
- B 후속 구현 기준: J/S/H/R/W/P 조합은 수락 표대로22허용/42거절(키·기존 업무/DB 조건 정상 전제). H/R→S 유지, J OFF+(S OR P) 거절, ON 키 검증/OFF 미파싱. **writer OFF/publisher ON은 기존 outbox drain용 허용이며 P→W 의존이나 writer Transaction 필수 의존을 추가하지 않는다.**
- **publisher 단독 ON에서도 필수 outbox index 및 state `_id` 점검이 필요하다. 검증 경계를 W OR P로 준비하고 점검 성공 전에 claim/send하지 않는다.** 기존 key/options 유지, 충돌은 fail-closed하며 임의 index drop/recreate·데이터 정리는 허용하지 않는다. 새 event 생성/DEAD_LETTER 자동 requeue를 drain으로 간주하지 않는다.
- 후속 B에서 두 Billing signer만 교체하며 S3 공용 AWS provider/SDK를 보존한다. Billing 저장소의 수신 보안·ADR/AGENTS는 LC 작업으로 수정하지 않는다. fixture/runtime/실환경 검증 증거를 분리한다.

이 저장소는 기존 토선생 웹 POC 백엔드를 복제하여 만든 앱용 Learning Core 서비스다.

현재 이 저장소의 제품·구현 범위는 앱 전용이다. 기존 웹 백엔드와 웹 프론트는 별도 저장소·별도 배포 서비스이며 이 저장소의 변경 대상으로 취급하지 않는다. 명시적인 요청이 없으면 웹 저장소를 조회하거나 웹 동작을 함께 검증하지 않는다.

기존 POC 저장소:
- Too-Much-I/web-back-end

현재 저장소:
- Too-Much-I/app-back-end-learning-core

기존 POC 저장소와 기존 POC 배포 환경은 절대 수정하지 않는다.
현재 저장소의 코드만 수정한다.

# 기술 환경

- Java 21
- Spring Boot
- Gradle Wrapper
- MongoDB
- Redis
- AWS S3
- Python AI 채점 서버 연동

기본 빌드 및 테스트 명령:
- ./gradlew clean test

# 최우선 호환성 규칙

앱 클라이언트와 Python AI 서버의 기존 계약 호환성을 최우선으로 유지한다.

과거 웹 POC와의 호환성은 이 저장소의 신규 설계 제약이 아니다. 다만 현재 앱이 이미 제공하는 공개 API와 공유 Python AI 계약은 명시적인 요청 없이 변경하지 않는다.

명시적인 요청이 없으면 다음 외부 계약을 변경하지 않는다.

- 기존 공개 API URL
- 기존 HTTP Method
- 기존 Path Parameter
- 기존 Query Parameter
- 기존 Request Body
- 기존 Response DTO
- 기존 BaseResponse 구조
- 기존 retryCount 방식
- 기존 S3 Presigned URL 발급 흐름
- 기존 음성 제출 흐름
- 기존 Polling 흐름
- 기존 AI 요청 구조
- 기존 AI Callback URL
- 기존 AI Callback JSON 구조

API를 더 RESTful하게 만든다는 이유로 기존 계약을 임의로 재설계하지 않는다.

다음과 같은 변경은 현재 작업 범위에 포함하지 않는다.

- upload-url GET API를 POST로 변경
- retryCount를 attemptId 방식으로 변경
- 별도의 시험 완료 API 추가
- 기존 성공 응답 코드를 201 또는 202로 일괄 변경
- 기존 응답 DTO에 새로운 필드 추가
- API 버전 변경

# 사용자 식별 규칙

클라이언트는 실제 userId를 직접 전달하지 않는다.

다음 위치에 userId를 추가하지 않는다.

- Request Body
- Path Parameter
- Query Parameter
- 기존 Response DTO

실제 사용자 ID는 UUID 형식 문자열로 관리한다.

현재 Identity 서버가 없으므로 CurrentUserProvider 추상화를 사용한다.

현재 단계에서는 LegacyCurrentUserProvider 또는 동일한 역할의 구현체가 고정 개발 UUID를 반환한다.

기본 개발 UUID:
- 00000000-0000-0000-0000-000000000001

local/test의 Legacy 모드는 Identity 없이 앱 개발 흐름을 확인할 수 있도록 Authorization 헤더 없이도 동작해야 한다.

Identity 서버 연동 이후에는 JWT의 sub 클레임을 실제 userId로 사용한다.

Identity 연동 이후에도 앱은 userId를 직접 보내지 않고 Authorization 헤더에 Access Token만 전달한다.

현재 작업에서는 다음을 수행하지 않는다.

- JWT 인증 강제
- 모든 API를 authenticated로 변경
- SecurityConfig의 전면 재설계
- Identity 서버 구현

# 시험 세션 규칙

시험 생성 시 다음 관계를 Learning Core의 MongoDB에 저장한다.

- examId -> 실제 userId

ExamSession은 최소한 다음 필드를 가진다.

- examId
- userId
- createdAt

status 또는 mockExamId는 기존 코드 흐름에서 명확히 얻을 수 있고 중복 상태를 만들지 않는 경우에만 추가한다.

시험 생성 API의 기존 Request와 Response는 변경하지 않는다.

외부 Response에 userId를 추가하지 않는다.

# AI 연동 규칙

Python AI 서버로 보내는 user_id에는 실제 사용자 UUID가 아니라 기존과 동일하게 examId를 전달한다.

반드시 다음 규칙을 유지한다.

- AI user_id = examId
- AI user_id != 실제 userId

Python AI 서버의 기존 필드명 user_id를 변경하지 않는다.

AI Callback에서 전달되는 user_id도 examId로 해석한다.

외부 JSON 필드명이 user_id라면 그대로 유지한다.

Java DTO 내부에서는 의미를 명확하게 하기 위해 examId라는 필드명을 사용할 수 있지만, 외부 JSON 계약은 @JsonProperty("user_id") 등으로 그대로 유지한다.

AI Callback 처리 순서는 다음과 같다.

1. Callback의 user_id를 examId로 해석한다.
2. ExamSessionRepository에서 examId로 ExamSession을 조회한다.
3. ExamSession.userId에서 실제 사용자 UUID를 가져온다.
4. ExamResult에 examId와 실제 userId를 함께 저장한다.
5. 기존 Azure, SpeechAce, LLM 채점 결과 저장 흐름을 유지한다.

실제 사용자 UUID를 Python AI 서버에 전송하지 않는다.

# ExamResult 규칙

ExamResult에 실제 userId 필드를 추가한다.

ExamResult에는 최소한 다음 식별 관계가 존재해야 한다.

- examId: 시험 세션 식별자
- userId: 실제 사용자 UUID
- questionNumber
- retryCount

examId와 userId를 서로 바꾸어 저장하지 않는다.

다음 외부 응답에는 userId를 추가하지 않는다.

- 시험 생성 응답
- 시험 상태 응답
- 시험 종합 결과 응답
- 문항별 결과 응답
- 업로드 URL 응답
- 음성 제출 응답
- 문항별 Polling 응답

userId는 내부 DB 저장과 사용자 소유권 검증 용도로만 사용한다.

# 시험 소유권 검증 규칙

사용자용 API에서 examId를 사용할 때 다음 조건을 확인한다.

- ExamSession.userId == CurrentUserProvider.getCurrentUserId()

examId를 입력받는 사용자용 API에는 소유권 검증을 적용한다.

최소 확인 대상은 다음과 같다.

- 시험 전체 상태 조회
- 시험 종합 결과 조회
- 문항별 결과 조회
- S3 업로드 URL 발급
- 음성 제출
- 개별 문항 상태 조회
- 그 밖에 examId를 입력받는 사용자용 공개 API

AI Callback은 사용자용 API가 아니므로 CurrentUserProvider 기반의 사용자 소유권 검증을 적용하지 않는다.

AI Callback에서는 examId로 ExamSession을 조회하여 실제 userId를 찾는다.

# 삭제할 기능

다음 API는 제거 대상이다.

- POST /api/v1/exams/trial
- POST /api/v1/exams/{examId}/terminate

위 두 API에만 사용되는 다음 코드는 제거할 수 있다.

- Controller 메서드
- Service 인터페이스 메서드
- Service 구현 메서드
- Request 또는 Response DTO
- Swagger 또는 OpenAPI 문서
- 테스트
- ErrorCode
- 미사용 import

다른 API에서도 사용하는 공유 코드는 삭제하지 않는다.

# 현재 추가하지 않을 기능

다음 기능은 현재 Learning Core 수정 범위에 포함하지 않는다.

- Identity 서버
- 이메일 회원가입
- 로그인
- 소셜 로그인
- Refresh Token
- JWT 인증 강제
- 스트릭
- 주간 학습 이력
- 취약 단어장
- attemptId 기반 재설계
- 메시지 큐
- Kafka
- SQS
- Redis 구조 전면 개편
- S3 Key 구조 전면 변경
- 관련 없는 대규모 리팩터링

## TMI-14 명시적 예외

- Jira TMI-14에 한하여 “JWT 인증 강제 제외” 규칙의 제한적 예외를 허용한다.
- 이 예외는 staging/prod Startup 검증, Legacy 차단, 인증 모드 설정 검증 및 미사용 HMAC 코드 정리에만 적용한다.
- 기존 JWT 보호 API와 공개 API 범위는 변경하지 않는다.
- local/test Legacy 모드는 유지한다.
- 외부 API와 AI 계약은 변경하지 않는다.
- TMI-14 완료 후에도 다른 작업에는 이 예외가 자동 적용되지 않는다.

## TMI-25 명시적 예외

- Jira TMI-25에 한하여 최우선 호환성 규칙과 “현재 추가하지 않을 기능” 규칙의 제한적 예외를 허용한다.
- 이 예외는 시험 단위 재채점과 AI 채점·Callback 멱등성 구현에만 적용한다.

허용 범위는 다음과 같다.

- 신규 API `POST /api/v1/exams/{examId}/grading/retry` 하나 추가
- 신규 API 전용 Request/Response DTO 추가
- `QuestionGradingJob`과 `SummaryGradingJob` 추가
- 기존 submit API의 외부 계약을 유지하면서 내부 처리를 멱등하게 변경
- 기존 status API의 URL, HTTP Method와 기존 Response 필드를 유지하면서 Job 기반 상태 산정으로 내부 처리 변경
- Feedback, SpeechAce, Azure와 전체 요약 Callback 저장을 멱등하게 변경
- 전체 요약 Trigger를 모든 필수 문항의 최초 응시 `retryCount=0` 완료 기준으로 변경

다음 변경은 이 예외에서도 허용하지 않는다.

- 기존 API URL 또는 HTTP Method 변경
- 기존 Request Parameter 변경
- 기존 Response 필드 삭제 또는 이름 변경
- 기존 `retryCount` 의미 변경
- Python AI 요청과 Callback의 `user_id = examId` 계약 변경
- Redis Key 형식 변경
- S3 Object Key 변경
- 사용자 소유권 검증 변경 또는 신규 시험 단위 retry API의 소유권 검증 누락
- 사용자 새 녹음인 `retryCount>0` 문항을 시험 전체 복구 대상에 포함
- 프론트가 필수 문항 번호 목록을 보내는 구조 추가
- 별도의 외부 전체 요약 retry API 추가

- Jira TMI-25에 명시된 신규 시험 단위 retry API는 Request Body 없음 계약을 유지한다.
- 기존 공개 API·DTO·`BaseResponse`, 음성 제출·Polling과 AI Callback JSON의 나머지 계약은 유지한다.
- 이 예외는 Jira TMI-25에만 적용되며 완료 후 또는 다른 작업에 자동 적용되지 않는다.

## TMI-116 명시적 예외

- Jira TMI-116에 한하여 최우선 호환성 규칙과 “현재 추가하지 않을 기능” 규칙의 제한적 예외를 허용한다.
- 이 예외는 Learning Core 시험 생성의 Billing Reservation saga와 공개 시험 생성 command 멱등성 구현에만 적용한다.

허용 범위는 다음과 같다.

- 기존 `POST /api/v1/exams`에 optional `Idempotency-Key` request header를 추가하고 Billing saga feature flag가 활성화된 환경에서만 필수 lowercase UUID v4로 검증
- 기존 Request Body 없음과 성공 Response DTO·`BaseResponse`를 유지한 same-operation replay
- `ExamCreationOperation`과 ExamSession의 operation·reservation·attemptGroup 내부 metadata 추가
- Billing reserve·confirm·cancel·status internal client와 SigV4 `vpc-lattice-svcs` 전송
- 기존 Session 교체와 새 Session 저장의 Mongo Transaction, confirm 불명 status 복구와 안정적인 공개 오류 mapping
- feature flag 기본값 off, local/test fake client, index migration·startup validation과 관련 테스트

다음 변경은 이 예외에서도 허용하지 않는다.

- 기존 시험 생성 URL, HTTP Method, Request Body와 성공 Response field 변경
- 실제 userId, reservationId, attemptGroupId를 공개 Request/Response에 추가
- 기존 시험 `retryCount`, S3 Object Key, Redis Key와 submit·Polling 흐름 변경
- Python AI request/Callback의 `user_id=examId` 또는 JSON 계약 변경
- Billing Reservation 기능 재구현, 결제·subscription·coupon 기능 추가
- Learning Core AttemptGroup 상태 outbox/publisher, Billing event consumer, UserMerged·owner rebind와 background reconciliation 구현
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포 또는 static AWS credential 추가

- feature flag가 off이면 기존 무헤더 시험 생성 흐름을 유지한다.
- feature flag를 staging/prod에서 켜기 전에 프론트 header 선배포와 Mongo index·Lattice security·장애 복구 E2E를 완료한다.
- 이 예외는 Jira TMI-116에만 적용되며 완료 후 또는 다른 작업에 자동 적용되지 않는다.

## Reservation 장애 자동 복구 구현 허용 규칙

- 2026-09-08 사용자 승인으로 Learning Core Reservation background reconciliation과 기존 Sentry 경보 연동을 구현 범위에 포함한다.
- TMI-128은 최초 구현 이력이며 같은 계약 안의 후속 수정·테스트에도 적용한다. TMI-116 및 AttemptGroup 절의 제외는 해당 과제 경계이며 이 전용 허용을 취소하지 않는다.
- 기준은 `docs/codex/BILLING_RESERVATION_RECONCILIATION_IMPLEMENTATION_PLAN.md` 승인본이다. 이번 계획 승인/Jira 등록 자체는 runtime 구현 요청이나 production 활성화 승인이 아니다.

허용 범위:

- 이미 저장된 INITIAL/REPLACEMENT/PHONE_REJOIN operation의 status-first 복구와 동일 key confirm/cancel, 로컬 Session/operation 원자 정리를 추가할 수 있다.
- HTTP saga와 worker의 공통 operation lease/version CAS, cleanup intent, 전송 전 durable marker와 bounded scheduler를 추가할 수 있다. Session commit과 cleanup이 같은 문서 write conflict를 만들도록 기존 직접 cancel 경로도 보호한다.
- 외부 HTTP는 Mongo Transaction 밖에서 수행하고 owner guard·Session·operation 변경은 기존 같은 manager의 Transaction 안에 참여시킨다. unknown commit은 fresh majority evidence로 수렴하고 abort된 Transaction을 계속 사용하지 않는다.
- default-off flag, index/dry-run migration·승인 allowlist 편입, durable retry·격리/auth circuit, 최소 로컬7일 terminal 증거 보존과 미해결 무TTL을 추가할 수 있다.
- 기존 Sentry IHub/프로젝트를 재사용하는 worker 전용 reporter와 고정 사유/opaque incident context의 sanitizer 최소 허용 확장, 기존 문서에 pending alert metadata 및 bounded 회수, privacy·중복 제한·metric·runbook과 테스트를 추가할 수 있다. 기존 HTTP 개인정보 정제는 유지한다.

확정 기본값과 안전 경계:

- precommit 정체2분은 cleanup 후보 기준이며 종료 증명이 아니다. poll10초/batch20/instance동시2/lease30초/attempt20초/HTTP최대2회를 기본으로 한다.
- backoff5초→15초→1분→5분→15분 상한+양의 jitter와 Retry-After를 지킨다. 최초 복구부터24시간/200회 중 먼저 도달하면 NEEDS_REVIEW로 격리하며 guard/증거를 유지한다.
- 인증 오류는 전역 circuit을 차단하고15분마다 status-only 단일 probe한다. owner deny는 BLOCKED_OWNER로 중지하며 marker를 해제하지 않는다.
- 신규 검증 데이터만 자동 편입하고 기존 데이터는 dry-run/승인 allowlist를 사용한다. 구버전 HTTP drain·Mongo/Lattice·staging 경합·Sentry 수신 확인 전 worker를 활성화하지 않는다.
- `./gradlew clean test`, `./gradlew mongoIntegrationTest`, migration Node test, `git diff --check`와 원격 성공 유실/lease·commit-cleanup race/privacy 회귀를 검증한다.

금지 범위:

- worker의 새 reserve/continuation discovery/Session 생성, CANCELED·EXPIRED repair-confirm, CONFIRMED cancel, 새 무료권 지급·TrialClaim/consumption 변경, owner rewrite·alias를 금지한다.
- 404·timeout·lease 만료·retry 소진만으로 cancel/terminal/activeGuard 해제를 확정하지 않는다. 기존 결과·outbox payload·Billing ledger를 고치지 않는다.
- 기존 공개 API·Request/Response/BaseResponse·Idempotency-Key 의미·retryCount·AI user_id=examId·S3/Redis 계약, Billing 저장소/유료 결제/Challenge 기능은 변경하지 않는다.
- 신규 관리자 repair API·메시지 큐·실제 AWS/Sentry 운영 설정·배포 변경과 credential/PII/원문 로그 추가를 허용하지 않는다. Sentry 미제안 운영값은 별도 확인한다.

## Phone 재가입 시험 continuation 구현 허용 규칙

- 사용자 결정에 따라 Learning Core의 phone 재가입 시험 continuation discovery와 기존 Billing 시험 생성 saga의 해당 확장은 신규 구현 범위에 포함한다.
- 이 허용은 특정 Jira 한 건에만 묶지 않으며, 동일한 계약 경계 안의 후속 버그 수정·테스트·운영 안정화에도 적용한다.
- Jira TMI-122는 최초 구현 이력이며 허용 범위의 유효기간을 제한하지 않는다.
- 기준 구현 계획은 `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`이며 Billing wire 계약은 Billing PR #8 merge commit `7138810`의 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`를 따른다.

허용 범위는 다음과 같다.

- target userId 소유 ExamSession이 전혀 없을 때 Billing `POST /internal/v1/reservations/continuations/phone`을 조회
- body 없는 204를 기존 INITIAL 흐름으로, strict 200을 `PHONE_REJOIN` REPLACEMENT로 처리
- `ExamCreationOperation`에 nullable continuation reason·ID와 expected AttemptGroup·mockExam snapshot 저장
- 일반 reserve 3-field 계약을 유지하면서 phone continuation에만 `continuationReason`, `continuationId`, `expectedAttemptGroupId`를 모두 포함한 6-field 요청 사용
- reserve·status optional continuation field의 reader-first decode와 operation snapshot strict 검증
- reserve 응답 유실의 status 복구, stale context terminal 수렴과 계약 불일치의 status-first cancel 보상
- 새 target examId·Session을 Billing의 기존 AttemptGroup·mockExamId에 연결하고 Session commit 뒤 기존 confirm 수행
- 별도 default-off phone continuation feature flag와 startup validation
- Billing 동기 호출의 W3C CLIENT span·traceparent 전파, baggage 미전파와 SigV4 최종 서명 순서 검증
- 관련 unit·contract·component·Transaction 회귀 테스트와 문서 갱신

다음 변경은 이 허용 범위에 포함하지 않는다.

- 기존 `POST /api/v1/exams` URL, HTTP Method, Request Body, 성공 Response DTO와 `BaseResponse` 변경
- 실제 userId, continuationId, reservationId와 attemptGroupId를 공개 Request/Response에 추가
- source ExamSession owner 변경 또는 기존 답안·결과·Summary·upload/grading Job·audio 복사
- `TrialOwnerRebindApproved` Learning Core consumer 추가
- Guest `UserMerged` consumer 구현·계약 변경 또는 기존 UserMerged 계획 폐기
- Billing owner rebind·Reservation·AttemptGroup 상태 머신과 Billing 저장소 코드 수정
- 새로운 Grant·Claim·consumption, 결제·subscription·coupon 기능 구현
- 기존 시험 `retryCount`, Redis Key, S3 Object Key, Presigned URL, submit·Polling과 AI `user_id=examId` 계약 변경
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포 또는 static AWS credential 추가

- phone continuation flag는 기본 off이며 creation saga가 켜진 경우에만 활성화할 수 있다.
- Billing 배포·Lattice exact route·staging E2E 전에 production flag를 활성화하지 않는다.
- 새 continuation 유형, owner rebind lifecycle 또는 Billing 권리 정책을 추가할 때는 별도 계약과 명시적 승인이 필요하다.

# UserMerged consumer와 시험 ownership migration 구현 허용 규칙

사용자 결정에 따라 Learning Core의 Guest `UserMerged` consumer, source deny와 시험 ownership migration은 신규 구현 범위에 포함한다. 이 허용은 특정 Jira 한 건에만 묶지 않으며, 동일한 계약 경계 안의 후속 버그 수정·테스트·운영 안정화에도 적용한다.

- Jira `TMI-125`는 최초 구현 이력이며 허용 범위의 유효기간을 제한하지 않는다.
- 구현 계획: `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`
- 계약 결정: `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`
- Identity producer: Identity `TMI-123`의 schema v1 `UserMerged` fan-out 계약을 따른다.
- Phone continuation과 AttemptGroup 절에서 UserMerged를 제외한 문구는 해당 기능의 범위 확장을 금지한 것이며, 이 전용 절의 허용을 취소하지 않는다.

## UserMerged 허용 범위

- exact internal endpoint `POST /internal/v1/events/user-merged`와 최대 4 KiB schema v1 body, 빈 `204`, 계약·인증·경합별 승인된 HTTP status를 추가할 수 있다.
- RS256, 환경별 workload issuer/JWKS, 고정 `aud=learning-core-user-merged`, `sub=identity-service`, `iat=nbf`, 최대 TTL `PT2M`, UUID `jti`, `typ=JWT`, `kid` 계약을 검증하는 전용 workload security를 추가할 수 있다.
- UserMerged exact chain은 `@Order(0)`, 기존 UserWithdrawn exact chain은 `@Order(1)`, 사용자 JWT 또는 local/test Legacy catch-all은 `@Order(2)`로 유지할 수 있다.
- `user_ownership_guards`와 `user_merged_inbox_events`, payload normalization·semantic digest·영구 멱등성과 source token deny gate를 추가할 수 있다.
- source와 target ownership guard를 결정적 순서로 같은 Mongo Transaction에서 touch하고 `exam_sessions`, `exam_results`, `exam_summaries`의 직접 `userId`를 target으로 이전할 수 있다.
- target 활성 시험을 우선하고 source와 target이 모두 활성인 경우 source 활성 시험을 abandon하며, source만 활성인 경우 같은 `examId`를 target에 이전하고 완료·폐기 이력은 합집합으로 보존할 수 있다.
- 모든 user-owned writer, Billing 시험 생성 Transaction, Question/Summary 상태 전이, Callback과 AttemptGroup coordinator·summary completion·reconciler·backfill·outbox 생성이 current owner guard와 같은 Transaction 경계를 사용하도록 수정할 수 있다.
- 서로 다른 feature의 `TransactionTemplate`을 중첩하지 않고 기존 Transaction body에 guard touch가 참여하도록 command 경계를 재구성할 수 있다.
- source 또는 target에 non-terminal `ExamCreationOperation`이 있으면 operation owner를 rewrite하거나 부분 migration하지 않고 `503`과 `Retry-After: 5`로 재시도하게 할 수 있다. terminal operation은 기존 coordination snapshot과 purge 정책을 유지하고 migration을 차단하지 않는다.
- merge 전에 생성된 `attempt_group_event_outbox`의 userId·canonical payload·digest는 수정하지 않고 Billing의 승인된 legacy-source fence로 전달하며, merge 이후 신규 event부터 target owner를 사용하도록 할 수 있다.
- source 또는 target에 기존 `withdrawn_user_access_denies` 충돌이 있으면 mutation 없이 `409`로 fail-closed하고 경보하며, commit winner가 미확정인 일시적 Transaction 경합만 `503`으로 재시도할 수 있다. Learning Core가 withdrawal marker를 삭제하거나 해제하지 않는다.
- direct Transaction을 우선 구현하되 Identity read timeout `PT3S` 아래 초기 gate인 Transaction P99 1초 이하·전체 HTTP 2초 미만을 production 유사 staging에서 검증할 수 있다. 실패하면 timeout 연장이나 hybrid가 아니라 durable inbox + worker 계약을 별도로 개정한다.
- default-off feature flag, startup validator, migration/index dry-run·apply, 구조화 로그·저카디널리티 metric, 전용 `mongoIntegrationTest` replica-set task와 관련 unit·contract·integration·staging E2E를 추가할 수 있다.

## UserMerged 금지 범위

- 기존 공개 API URL, HTTP Method, Parameter, Request/Response DTO와 `BaseResponse`를 변경하지 않는다.
- 실제 userId, merge event, guard, inbox, reservationId와 attemptGroupId를 공개 Request/Response에 추가하지 않는다.
- source JWT를 target actor로 alias하거나 source token으로 target 데이터를 조회하지 않는다.
- `examId`, retryCount, Redis Key, S3 Object Key, Presigned URL 발급·submit·Polling과 Python AI request/Callback의 `user_id=examId` 계약을 변경하지 않는다.
- merge 전에 발급된 Presigned PUT URL을 취소하기 위한 새 nonce·generation·Object Key 체계를 이 범위에 추가하지 않는다. v1은 승인된 최대 5분 잔여 capability를 수용한다.
- non-terminal 또는 terminal `ExamCreationOperation`의 userId·reservation snapshot을 target으로 rewrite하지 않는다.
- 기존 AttemptGroup outbox event의 userId·canonical payload·digest·eventId를 rewrite하거나 재생성하지 않는다.
- Billing consumer, owner rebind·Reservation·AttemptGroup 상태 머신과 Billing 저장소 코드를 Learning Core 작업 범위로 수정하지 않는다.
- Identity merge 상태·UserMerged producer를 Learning Core에서 재구현하거나 withdrawal marker를 임의로 해제하지 않는다.
- 새 owner event, phone proof 기반 과거 데이터 이전, TrialOwnerRebindApproved consumer, 결제·subscription·coupon과 Challenge 기능을 이 허용 범위에 포함하지 않는다.
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포 또는 static AWS credential 추가를 수행하지 않는다.

## UserMerged rollout과 테스트

- 2026-09-30 사용자 승인 예외: LC 테스트 서버의 UserMerged 준비를 위한 테스트 DB 점검·guard/index 준비, 구 writer drain, workload issuer/JWKS 및 writer/source-deny/consumer 설정과 테스트 ECS 배포·검증을 허용한다. 위 실제 AWS 배포 금지는 이 범위에 한해 예외다. 운영 서비스·DB, Identity 발급/발행/merge 설정 및 Billing·AttemptGroup·Challenge 활성화는 포함하지 않으며 static credential 추가나 별도 IAM/네트워크 권한 확대를 허용하지 않는다.
- consumer와 source deny feature flag는 기본 off다.
- writer guard 전환과 구버전 instance drain, ACTIVE guard backfill·index 검증, Mongo replica-set Transaction, workload security, Identity retry와 staging 성능 gate를 완료하기 전에는 publisher와 merge 기능을 production에서 활성화하지 않는다.
- direct gate 실패 시 publisher timeout만 늘리거나 direct 처리 중 timeout 뒤 background로 넘기는 hybrid를 사용하지 않는다. durable async 모델의 ack·source deny 시점·SLA·DLQ를 별도 계약으로 확정한다.
- `./gradlew clean test`, `./gradlew mongoIntegrationTest`, migration Node test와 `git diff --check`를 완료한다.

# AttemptGroup 상태 outbox/publisher 구현 허용 규칙

사용자 결정에 따라 Learning Core의 AttemptGroup 상태 판정, durable outbox와 Billing publisher는 신규 구현 범위에 포함한다. 이 허용은 특정 Jira 한 건에만 묶지 않으며, 후속 버그 수정·테스트·운영 안정화에도 동일한 경계를 적용한다.

승인된 구현 계획과 외부 계약은 다음 문서를 따른다.

- 구현 계획: `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`
- 상태·과금 결정: `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`
- Billing event 계약: Billing 저장소 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`
- trace 전달 계약: Billing 저장소 `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`

TMI-116에서 AttemptGroup outbox/publisher를 제외한 것은 해당 Jira의 구현 범위를 제한한 것이다. 이 절의 후속 영구 허용 범위를 취소하거나 금지하는 의미로 해석하지 않는다.

## AttemptGroup 허용 범위

- 모든 필수 `retryCount=0` submit과 durable Question Job을 기준으로 `GRADING` 상태를 판정한다.
- 필수 retry 0 feedback, 유효 점수와 Summary가 모두 조회 가능할 때만 `COMPLETED`를 판정한다.
- 자동 복구 불가능한 최종 실패를 승인된 failureCode와 `RETAKE_AVAILABLE`로 판정한다.
- ExamSession의 local AttemptGroup projection·terminal metadata와 outbox event를 같은 Mongo Transaction/CAS로 저장한다.
- 같은 Session에는 `COMPLETED` 또는 `RETAKE_AVAILABLE` terminal event 하나만 허용한다.
- Summary Callback의 외부 계약을 유지하면서 Summary·Job·Session terminal·outbox의 로컬 DB 단계를 Transaction으로 재구성할 수 있다.
- RETAKE_AVAILABLE Session을 같은 Billing consumption·attemptGroupId·mockExamId의 REPLACEMENT 생성 saga에 연결하기 위한 내부 metadata와 검증을 추가할 수 있다.
- outbox collection, index, lease claim, retry, retention, dead-letter, auth circuit과 제한된 reconciliation scheduler를 추가할 수 있다.
- Billing `POST /internal/v1/attempt-group-events` client와 SigV4 `vpc-lattice-svcs` 전송을 추가할 수 있다.
- W3C trace context 최소 metadata 저장, publish attempt별 새 span, 구조화 로그와 저카디널리티 metric을 추가할 수 있다.
- writer와 publisher feature flag, startup validator, local/test fake client와 관련 테스트를 추가할 수 있다.

이 절의 reconciliation은 submit·Callback과 local 상태 전이 뒤 누락된 AttemptGroup outbox를 복구하는 범위로 제한한다. Billing Reservation 생성 saga 전체의 background reconciliation, 결제 보상 command와 owner rebind까지 자동으로 허용하지 않는다.

## AttemptGroup 확정 정책

- GRADING deadline은 `PT30M`이다.
- 완료 evidence가 있으면 `COMPLETED`가 항상 우선한다.
- 자동 복구 불가 정합성 위반은 `RESULT_INTEGRITY_VIOLATION`으로 종료한다.
- retry가 소진되고 active dispatch·Callback completion claim이 없으면 `REQUIRED_RESULTS_UNAVAILABLE` 또는 `SUMMARY_UNAVAILABLE`로 종료한다.
- 그 밖의 정체는 deadline 뒤 `GRADING_DEADLINE_EXCEEDED`로 종료한다.
- 신규 Billing-linked Session의 Summary 완료 source는 결정적 `exam_summaries` 문서만 사용한다. `exam_results.totalScore` legacy fallback은 자동 terminal 판정에 사용하지 않는다.
- 401·403은 event를 `BLOCKED_AUTH`로 보존하고 전역 circuit을 차단한 뒤 15분마다 한 event만 half-open probe한다.
- 기존 Billing-linked Session은 전체 자동 backfill하지 않는다. inventory/dry-run 뒤 명시적으로 승인된 allowlist만 backfill한다.
- publisher 기본값은 poll 1초, batch 20, lease 30초이며 설정으로 조절할 수 있다.

## AttemptGroup event와 trace 규칙

- event target은 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`만 사용한다.
- RETAKE_AVAILABLE failureCode는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`만 사용한다.
- retry마다 같은 eventId와 canonical payload를 유지한다.
- `DELIVERED`는 30일, `DEAD_LETTER`는 90일 보존하고 PENDING·IN_FLIGHT·BLOCKED_AUTH에는 TTL을 두지 않는다.
- outbox에는 검증된 `traceId`, `parentSpanId`, `traceFlags`만 저장한다. raw `traceparent`, raw `tracestate`와 baggage를 저장하지 않는다.
- 각 publish attempt는 저장된 context를 parent로 새 span을 만들며 같은 traceId와 서로 다른 spanId를 사용한다.
- publish span의 `traceparent`를 inject한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행하고 서명 뒤 request를 변경하지 않는다.
- trace context 문제는 새 fallback trace와 counter로 수렴시키며 업무 event 전달을 막거나 DEAD_LETTER로 보내지 않는다.

## AttemptGroup 금지 범위

- 기존 공개 API URL, HTTP Method, Parameter, Request/Response DTO와 `BaseResponse`를 변경하지 않는다.
- 실제 userId, reservationId, attemptGroupId와 내부 outbox 상태를 공개 Request/Response에 추가하지 않는다.
- 기존 시험 `retryCount`, Redis Key, S3 Object Key, Presigned URL, 음성 submit과 Polling 계약을 변경하지 않는다.
- Python AI request/Callback의 `user_id=examId`, URL과 JSON 계약을 변경하지 않는다.
- Billing consumer, Billing AttemptGroup 상태 머신과 Billing 저장소 코드를 Learning Core 작업 범위로 수정하지 않는다.
- UserMerged·owner rebind, 결제·subscription·coupon, entitlement 환불/보상과 Challenge 기능을 이 허용 범위에 포함하지 않는다.
- 실제 AWS Lattice/IAM/SG/ECS 리소스 생성·배포와 static AWS credential 추가를 수행하지 않는다.
- 사용자·Session·AttemptGroup 식별자, event payload·digest, 인증 header, provider/AI 원문을 일반 log·span attribute·metric tag에 기록하지 않는다.

## AttemptGroup rollout과 테스트

- writer와 publisher feature flag는 모두 기본 off다.
- publisher를 먼저 idle 활성화하고 인증·연결을 확인한 뒤 writer를 활성화한다.
- Mongo replica-set Transaction, index migration, Billing consumer 배포, Lattice security와 failure-injection E2E 전에는 production writer를 활성화하지 않는다.
- 상태 판정, terminal race, Transaction rollback·unknown commit, lease 경쟁, retry/status 분류, auth circuit, retention, W3C trace와 privacy 테스트를 추가한다.
- 실제 MongoDB, Redis, S3, Billing, AWS credential과 trace backend에 의존하지 않는 Mock·fixture 또는 격리된 테스트 구성을 사용한다.
- 구현 완료 후 `./gradlew clean test`를 실행하고 공개 API·AI·S3·Redis 계약 불변을 확인한다.

# 10초 챌린지 구현 허용 규칙

사용자 결정에 따라 10초 챌린지는 Learning Core의 신규 구현 범위에 포함한다.

승인된 계약은 다음 문서를 따른다.

- 프론트 API: `docs/contracts/ten-second-challenge-frontend-api.md` v1
- Learning Core–AI API: `docs/contracts/ten-second-challenge-ai-api.md` v1
- 상세 결정: `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`
- 구현 계획 및 2026-09-07 사용자 승인 보완: `docs/codex/TEN_SECOND_CHALLENGE_IMPLEMENTATION_PLAN.md`

위 계약과 다른 URL, HTTP Method, Request/Response field, enum, timeout, retry 또는 Callback 구조를 임의로 추가·변경하지 않는다. 계약 변경이 필요하면 구현보다 먼저 관련 문서와 프론트·AI 합의를 갱신한다.

## Challenge 범위와 격리

- 10초 챌린지는 기존 시험과 별도 domain·aggregate로 구현한다.
- 기존 `ExamSession`, `ExamResult`, `QuestionGradingJob`, `SummaryGradingJob`과 시험 `retryCount`를 Challenge에 재사용하지 않는다.
- 기존 시험 공개 API·DTO·`BaseResponse`, Redis key, S3 object key와 Python 시험 AI/Callback 계약을 변경하지 않는다.
- Challenge 신규 공개 응답도 기존 Learning Core `BaseResponse` 구조를 사용한다.
- feature flag 기본값은 off로 두고 catalog·index·AI·인증·staging E2E가 준비된 뒤 환경별로 활성화한다.
- 스트릭, 주간 학습 이력, 경제적 reward, 결제·credit와 랭킹은 Challenge 구현 범위에 포함하지 않는다.

## Challenge 사용자와 공개 API

- 10초 챌린지는 Identity의 `MEMBER`만 이용한다. Guest 요청은 `403`으로 거절한다.
- 2026-09-07 승인: 서명 검증된 Identity JWT의 `account_type=MEMBER`를 사용한다. GUEST·claim 누락·알 수 없는 값은 403이며 Legacy로 우회하지 않는다. Identity claim 발급 구현은 별도 의존 작업이다.
- 실제 userId는 인증된 Identity에서 가져오며 Request Body, Path, Query와 Response에 추가하지 않는다.
- 모든 사용자용 attempt·날짜·결과 API는 인증 사용자 소유권을 검증한다.
- AI Callback에는 사용자용 CurrentUserProvider 소유권 검증을 적용하지 않고 service credential과 Job 식별자로 검증한다.
- 프론트 endpoint, field, 상태와 오류 code는 프론트 v1 계약을 그대로 구현한다.
- MVP에서는 사용자 audio 재생과 `audioUrl`을 제공하지 않는다.

## Challenge 콘텐츠와 날짜

- 기존 MongoDB 연결에서 `challenge_10s_questions` collection을 사용한다.
- `questions[].korean`을 공개 `promptKo`와 AI `prompt_ko`로 매핑한다.
- `questions[].difficulty`는 해석하지 않고 프론트에 정수 그대로 전달하며 AI 요청에는 포함하지 않는다.
- `questions[].referenceAnswer`는 문제 조회, attempt 생성과 upload-url 응답에서 노출하지 않는다.
- ChallengeAttempt 생성 시 `dayNumber`, `questionId`, `questionNumber`, `korean`, `referenceAnswer`, `difficulty`를 snapshot한다.
- 제출 또는 만료 terminal 이후의 참고 답안은 catalog 재조회가 아니라 attempt snapshot을 사용한다.
- `question.referenceAnswer`와 `aiResult.referenceAnswer`는 동일한 attempt snapshot 하나에서 조립한다.
- no-speech에서도 `gradingStatus=completed`, non-null `aiResult`, non-blank `aiResult.referenceAnswer`를 유지하고 transcript·verdict·correctedAnswer·feedback만 null로 반환한다.
- `app.challenge.enabled=true`로 처음 성공 기동한 KST 날짜를 `challenge_10s_catalog_state` singleton에 원자적으로 한 번만 저장하고 dayNumber 1로 사용한다.
- 재시작·재배포·scale-out으로 기준일을 초기화하지 않는다.
- dayNumber는 순환하지 않으며 콘텐츠가 없으면 다른 날 문제로 fallback하지 않고 `CHALLENGE_CONTENT_NOT_FOUND`로 fail-closed한다.

## Challenge attempt와 S3

- 녹음 시작 직전에 ChallengeAttempt를 먼저 생성하고 녹음 완료 후 같은 attemptId로 Presigned PUT URL을 별도 발급한다.
- attempt 제출 유효시간은 생성 시점부터 1시간이며 생성 당시 challengeDate에 귀속한다.
- 자정을 지나도 deadline 전에는 기존 attempt 제출을 허용하지만 이전 날짜의 새 attempt는 만들지 않는다.
- attemptId 기반 server-generated S3 object key를 생성하고 프론트가 key나 URL을 지정하게 하지 않는다.
- 같은 attempt의 upload URL 재발급은 동일 object key를 사용하고 새 응시로 계산하지 않는다.
- 사용자 승인 Challenge key는 `temp/challenges/{attemptId}/q_{questionNumber}.m4a`다. retryCount·실제 userId·날짜·추가 prefix를 붙이지 않는다. 기존 시험 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav`는 유지하며 temp lifecycle·권한은 운영 활성화 전에 검증한다.
- 2026-09-07 사용자 결정: 같은 key의 유효 PUT 덮어쓰기를 허용하고 마지막 성공 업로드를 현재 음성으로 유지한다. version pin·별도 음성 보관본은 만들지 않는다. bucket versioning 변경·과거 객체 삭제를 허용한 것은 아니다. 같은 AI Job에 다른 audio 전송은 기존 AI 멱등 계약과 충돌하므로 별도 합의 없이 변경하지 않는다.
- 미제출 EXPIRED 결과의 submittedAt과 gradedAt은 null로 반환한다. 만료 시각을 실제 제출 시각으로 기록하지 않는다.
- 사용자 확인: 재녹음은 answer 제출 전만 허용하고 같은 attempt·deadline·key로 최종 PUT 완료 뒤 제출한다. 최초 AI 전송 bytes의 digest를 HTTP 전에 durable CAS로 선택하고 재시작/재전송에도 유지한다. 다른 bytes를 같은 Job에 보내거나 새 generation으로 우회하지 않는다. 복구 불가면 채점 실패로 종료하되 이미 완료된 결과·제출·풀이 수·참고 답안을 보존한다. version pin·음성 보관본은 추가하지 않는다.
- 미제출 attempt 만료는 요청 경계 lazy CAS와 bounded scheduler로 수렴한다. 이미 성공한 submit replay를 deadline 경과로 만료 처리하지 않는다.
- 2026-09-07 사용자 결정: `solvedQuestionCount`는 실제 audio 제출이 접수된 내부 SUBMITTED만 세고 미제출 EXPIRED는 제외한다. `participated`는 실제 제출 수가 1 이상일 때만 true다. AI pending·no-speech·failed는 접수 사실을 바꾸지 않는다. 만료의 다음 문제·참고 답안 접근과 공개 submitted projection은 유지하며 진행 종료 목록을 실제 풀이 수로 집계하지 않는다.
- audio는 M4A/AAC-LC·`audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB 계약을 따른다.
- Challenge용 신규 S3 key를 설계할 수 있지만 기존 시험 S3 key 형식은 변경하지 않는다.

## Challenge AI와 Callback

- 기존 시험 `/evaluations`와 Feedback Callback을 재사용하지 않고 Challenge 전용 v1 endpoint를 사용한다.
- Learning Core가 S3 audio를 내려받아 multipart `audio_file`로 AI에 전달한다.
- AI 요청에는 `attempt_id`, 결정적 `job_id`, `grading_attempt`, `question_id`, `question_number`, `prompt_ko`, `reference_answer`, `audio_file`만 계약대로 전달한다.
- 실제 userId, 사용자 Access Token, difficulty, dayNumber, challengeDate, S3 위치와 credential을 AI에 전달하지 않는다.
- request와 Callback 모두 `attemptId`, `jobId`, `gradingAttempt`를 검증하고 이전 generation Callback은 상태를 덮어쓰지 않는 성공 no-op으로 처리한다.
- AI가 referenceAnswer를 Callback으로 echo하게 하지 않는다. 프론트 `aiResult.referenceAnswer`는 Learning Core attempt snapshot에서 조립한다.
- Callback deadline은 120초, 최대 AI generation은 3회다.
- 후속 사용자 승인: 각 Job 최초 dispatch부터 202 접수 전 transport 총 예산은 5분(연결/응답/backoff 포함)이며 재시작·lease 회수·재전송으로 초기화하지 않는다. 예산 소진 또는 마지막 generation timeout으로 최종 failed가 확정되면 늦은 유효 Callback은 결과를 바꾸지 않고 204 no-op한다. 기존 인증·payload conflict 검증은 유지하고 예산 회피용 새 generation을 만들지 않는다. 상세는 AI v1 및 구현 계획 C.2를 따른다.
- transcript와 corrected answer는 각각 최대 1000자, feedback 각 항목은 최대 500자, Callback JSON은 UTF-8 기준 최대 16 KiB다.
- 방향별 service credential은 서로 분리해 환경변수·secret store로 주입하고 실제 값을 코드·문서·테스트·로그에 기록하지 않는다.
- 사용자 audio, transcript 전체, prompt·reference answer 전체와 AI provider 원문을 로그에 기록하지 않는다.
- Callback 전용 exact security chain은 일반 JWT/Legacy catch-all보다 먼저 항상 등록하고 feature OFF이면 deny-all한다. 방향별 opaque Bearer 인증 계약은 유지한다.
- Learning Core는 S3 존재·metadata MIME·2 MiB를 검증하고 AI는 실제 media profile을 검사한다. 식별자는 구조화 로그에만 허용하며 metric tag에는 고정 enum만 사용한다.
- 2026-09-07 승인한 history 범위는 요청 월과 `[contentBaseDate, KST 오늘]`의 교집합이다. `CHALLENGE_DATE_CLOSED`는 제거하고 날짜 불일치/미제출 만료의 기존 전용 오류로 구분한다.

## Challenge 테스트

- catalog validator, one-time baseDate 초기화, 비순환 day resolver와 missing content fail-closed를 테스트한다.
- MEMBER 허용, Guest 거절과 사용자 attempt 소유권을 테스트한다.
- attempt 멱등 생성, 1시간 deadline, 자정 rollover, 동일 S3 key URL 재발급과 submit 멱등성을 테스트한다.
- referenceAnswer 조기 노출 금지, attempt snapshot 재현성과 정상/no-speech/processing/failed DTO를 테스트한다.
- AI request idempotency, duplicate·stale·conflict Callback, timeout·최대 generation과 payload 제한을 contract test로 검증한다.
- 실제 MongoDB, Redis, S3, AI provider와 secret에 의존하지 않고 Mock·fixture를 사용한다.
- 구현 완료 후 `./gradlew clean test`를 실행한다.

# Redis 및 S3 규칙

## TMI-193 학습 기록 독립 삭제 구현 허용

- 2026-10-03 사용자 구현 요청으로 `docs/codex/LEARNING_RECORD_DELETION_IMPLEMENTATION_PLAN.md`의 Learning Core 구현·로컬 테스트를 허용한다. 상위 이슈는 TMI-136이다.
- 신규 삭제 command/status API, 전용 operation/command/target 및 최소 Billing coordination 증거, 기존 writer/read/Callback의 삭제 fence, bounded Mongo/S3/Redis cleanup과 비식별 일별 집계를 추가할 수 있다.
- 기존 공개 API/DTO/BaseResponse, retryCount, AI `user_id=examId`, Callback JSON과 S3/Redis key 계약은 유지한다. 신규 삭제 전용 API/DTO/오류만 추가한다.
- 삭제는 Identity 계정·Token, Billing 소비·무료 기회·outbox를 삭제하거나 복원하지 않는다. 집계에는 개인·시험 식별자를 넣지 않는다. 안전 checkpoint 뒤 cleanup은 sealed target ID만 사용한다.
- 기존 UserMerged·AttemptGroup·Challenge의 제외 문구는 해당 과제 범위이며 이 삭제 전용 구현을 금지하지 않는다. 다른 저장소 수정, 운영 데이터 삭제, AWS/IAM 변경, 배포와 feature 활성화는 허용하지 않는다.
- 모든 flag는 기본 OFF다. 전체 fence·coordination·cleanup 연결 전에는 활성화를 거절한다. Billing 실제 fixture·모바일 계약·S3 권한/보존과 staging E2E는 출시 전 별도 검증한다.

기존 Redis 상태 및 Lock 흐름을 임의로 변경하지 않는다.

application.yml에 app.redis.key-prefix 설정이 있더라도 명시적인 구현 요청이 있기 전에는 Redis 키 구조를 전면 변경하지 않는다.

기존 S3 Presigned URL 및 Object Key 구조를 임의로 변경하지 않는다.

별도의 S3 버킷을 사용하고 있으므로 app.s3.key-prefix를 적용하기 위해 기존 Object Key 계약을 자동으로 변경하지 않는다.

# 보안 및 설정 규칙

실제 Secret이나 자격증명을 저장소에 추가하지 않는다.

다음 값을 코드, 테스트, 문서, 로그에 작성하지 않는다.

- 실제 AWS Access Key
- 실제 AWS Secret Key
- 실제 MongoDB URI와 비밀번호
- 실제 JWT Secret
- 사용자 음성 데이터
- 사용자 발화 전체 Transcript
- Refresh Token

application.yml에서는 환경변수 참조 방식을 유지한다.

application-test.yml에는 가짜 테스트 값만 사용한다.

기존 POC용 GitHub Actions 배포 Workflow를 복원하지 않는다.

새로운 배포 Workflow는 명시적으로 요청받기 전에는 추가하지 않는다.

기존 POC EC2, 컨테이너 또는 배포 경로를 대상으로 설정하지 않는다.

# 테스트 규칙

변경한 비즈니스 로직에는 테스트를 추가한다.

테스트에서는 실제 운영 인프라를 호출하지 않는다.

다음 외부 의존성은 가능한 경우 Mock으로 처리한다.

- MongoDB Repository
- Redis
- AWS S3
- Python AI 서버
- Sentry

모든 구현 작업 후 다음 명령을 실행한다.

- ./gradlew clean test

기존 공개 API의 Request와 Response 구조가 바뀌지 않았는지 확인한다.

컴파일 경고를 현재 작업과 무관하게 일괄 수정하지 않는다.

# 코드 변경 규칙

기존 패키지 구조와 코드 스타일을 우선 따른다.

관련 없는 파일을 포맷하거나 리팩터링하지 않는다.

명시적인 요청 없이 새로운 운영 의존성을 추가하지 않는다.

작업 범위 밖의 버그를 발견하면 임의로 수정하지 말고 최종 보고에 기록한다.

기존 API 호환성에 영향을 줄 수 있는 변경은 구현 전에 명확히 보고한다.

# Git 규칙

Codex는 다음 명령을 직접 수행하지 않는다.

- git commit
- git push
- git push --force
- git reset --hard
- 기존 POC 저장소 수정
- 기존 POC 배포 실행

Codex는 파일 수정과 테스트까지만 수행한다.

커밋과 push는 사용자가 직접 수행한다.

# 작업 기록 규칙

- 모든 Codex 구현·분석·리뷰 작업은 종료 전에 `docs/codex/WORKLOG.md` 끝에 새 항목을 append한다.
- 모든 Codex 작업은 종료 전에 `docs/codex/CURRENT_STATE.md`를 최신 상태로 갱신한다.
- Jira 이슈 키가 있으면 WORKLOG와 CURRENT_STATE에 기록한다.
- WORKLOG의 과거 항목은 수정하거나 삭제하지 않는다.
- 코드 변경이 없는 분석 작업도 WORKLOG와 CURRENT_STATE에 기록한다.
- Secret과 Token은 WORKLOG와 CURRENT_STATE에 기록하지 않는다.
- Git commit과 push는 사용자가 직접 수행한다.

# 계획·조사 문서 작성 규칙

계획, 조사, 분석, 리뷰 문서는 상세 내용을 빠짐없이 확인하되 사용자가 중요한 내용을 먼저 읽을 수 있도록 다음 계층으로 작성한다.

1. 5줄 결론
2. 사용자가 반드시 읽어야 하는 내용
3. 사용자가 결정해야 하는 사항
4. 주요 위험과 미확인 사항
5. 현재 작업과 직접 관련된 설명
6. 상세 조사 근거와 전체 표를 담은 부록

- 각 결론에는 가능한 경우 관련 코드, 설정, 테스트 또는 계약 문서의 파일 근거를 연결한다.
- 확인된 구현 사실, 문서상 계획, Codex의 분석·추론을 명확히 구분한다.
- 상세 조사 결과를 삭제하거나 축약해서 잃지 말고 긴 목록, 비교표와 보조 근거는 부록으로 이동한다.
- 정확한 field, enum, timeout, retry, 보안·과금 조건과 금지 규칙은 요약만으로 대체하지 않고 관련 계약 원문을 연결한다.

# 작업 완료 보고 규칙

각 작업이 끝나면 다음 내용을 보고한다.

1. 변경한 파일
2. 변경한 동작
3. 유지한 외부 API 계약
4. 실행한 테스트
5. 테스트 결과
6. 남아 있는 위험 요소
7. 배포 전에 확인할 사항
8. 실제 diff에서 예상 밖으로 변경된 파일이나 범위가 있는지 여부
9. 다음 작업 전에 확인할 사항

- 구현 작업 완료 보고에서는 위 항목을 생략하지 않는다.
- 예상 밖의 변경이 없으면 없다고 명시하고, 있으면 사용자 변경과 이번 작업 변경을 구분해 설명한다.
- 테스트를 실행하지 않았다면 그 이유와 대신 수행한 검증을 기록한다.

# 코드 리뷰 우선순위

리뷰할 때 다음 문제를 우선 확인한다.

1. 기존 공개 API URL, Method, Parameter, Response가 변경되었는가
2. BaseResponse 구조가 변경되었는가
3. Python AI의 user_id가 examId가 아닌 실제 userId로 변경되었는가
4. Callback 외부 JSON 구조가 변경되었는가
5. 클라이언트 Request 또는 Response에 실제 userId가 추가되었는가
6. 시험 생성 시 ExamSession 저장이 누락되었는가
7. ExamResult.userId 저장이 누락되었는가
8. examId와 실제 userId가 뒤바뀌었는가
9. 사용자용 examId API의 소유권 검증이 누락되었는가
10. AI Callback에 사용자용 소유권 검증을 잘못 적용했는가
11. 테스트가 실제 AWS, Atlas, Redis 또는 Sentry에 의존하는가
12. Secret 또는 개인정보가 코드나 로그에 추가되었는가
13. trial과 terminate 외의 API가 삭제되거나 변경되었는가
14. 관련 없는 대규모 리팩터링이 포함되었는가
15. Challenge가 기존 Exam domain·retryCount·AI Callback을 재사용하거나 변경했는가
16. Challenge 사용자 API에서 MEMBER 검증 또는 attempt 소유권 검증이 누락됐는가
17. referenceAnswer가 제출 전 노출되거나 catalog 재조회로 snapshot과 달라질 수 있는가
18. no-speech 결과에서 `aiResult.referenceAnswer`가 사라지거나 `aiResult` 전체가 null이 되는가
19. difficulty·실제 userId·S3 위치가 Challenge AI 요청에 포함됐는가
20. stale·duplicate Callback이 현재 Challenge grading 결과를 덮어쓸 수 있는가
21. 재배포·scale-out으로 contentBaseDate가 초기화되거나 dayNumber가 순환하는가
22. Challenge 결과에 `audioUrl` 또는 사용자 음성·transcript 전체 로그가 노출되는가
