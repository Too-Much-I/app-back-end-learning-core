# LC 운영 기동 조건 보완 — 2026-10-17

## 1. 5줄 결론

1. 사용자 후속 구현 요청으로 test-only 기동 조건에 명시적인 prod 준비 경로를 추가했다.
2. test 승인은 prod에서 재사용하지 않는다. 신규 `BILLING_PAID_EXAM_PRODUCTION_ACTIVATION_APPROVED` 기본값은 false다.
3. prod 프로필은 기존 wire 환경 production과 매핑하며 혼합 프로필·환경 불일치는 거절한다.
4. sealed legacy 목록·owner transaction·필수 index 검사와 JWT/TLS 검증을 유지한다.
5. 코드·격리 테스트 범위이며 운영 DB 설치·설정 ON·배포·공동 E2E 완료가 아니다.

## 2. 반드시 읽어야 하는 내용

운영 승인 설정은 준비 완료를 대신하는 우회 스위치가 아니다. 기존 paid/saga/workload sender/access receiver/owner writer가 모두 true여야 하며, LegacyExamRuntimeConfiguration의 verifySealed와 인덱스 검증이 통과해야 한다. 기존 PaidExamTestActivation 클래스 이름은 참조 변경 범위를 줄이기 위해 유지했다.

구앱 v1 무료 생성에는 `BILLING_LEGACY_EXAM_CREATE_ENABLED=true`도 필요하다. paid OFF만으로 구앱 호환이 되는 구조가 아니며 필수 runtime 없이 무료 fallback하지 않는다. v2는 기존 이용권 검사, v1은 기존 공존 fence를 그대로 사용한다. 활성 paid 작업이 있으면 v1 생성 거절 정책도 불변이다.

## 3. 결정 사항

추가 제품 정책은 없다. 실제 prod 적용은 환경 준비 확인 후 별도 진행한다. 기존 test 설정·키·manifest를 prod에 복사하지 않는다. 새 approval API/collection/worker는 만들지 않았다.

## 4. 위험·미확인

- 운영 manifest/index/owner guard 및 Billing prod 연결은 미검증이다.
- 구앱 로그인·토큰갱신·신규 무료 시험·기존 제출/채점/결과와 신앱 권리 소비는 실제 배포 전후 별도 smoke가 필요하다.
- manifest에 신규 전환 무료 시험을 추가하거나 과거 목록/hash를 재작성하지 않는다.
- 승인 flag가 있어도 실제 JWT key/consumer scope/TLS 설정은 기존 구성 검사 및 운영 대조가 필요하다.

## 5. 구현 및 검증

- PaidExamTestActivation: test/test approval 또는 prod/production approval만 허용. 공통 flag/index/runtime/owner 조건 유지.
- application.yml: production approval 기본 OFF 추가.
- 두 rollout gate 주석을 환경별 준비 검사로 정정.
- 단위 테스트: 환경별 승인·혼합 profile·개별 flag·manifest/owner/index 실패 검증.
- Mongo 테스트: 실제 production sealed 목록·index 정상, hash 오류·owner index 누락·test 목록 혼입 거절 및 사용자 Session 무변경 검증.
- 실행 결과는 WORKLOG/CURRENT_STATE에 기록한다. 실제 외부 서버에 대한 테스트가 아니다.

## 6. 운영 적용 체크리스트

| 구분 | 필수 값/확인 |
| --- | --- |
| Spring profile | prod 단독 |
| 신규 승인 | BILLING_PAID_EXAM_PRODUCTION_ACTIVATION_APPROVED=true, 기본 false |
| 기존 test 승인 | 운영에서는 false 유지; 운영 승인 대체 불가 |
| 업무 | paid-exam/saga/workload sender/access receiver/owner writer ON |
| 신뢰 환경 | workload-jwt/access-events/legacy 모두 production |
| 구앱 호환 | legacy-create ON, 심사 중 최소 앱 버전 임의 상향 금지 |
| 데이터 | 운영 DB exact sealed manifest/hash/count, 필수 index, owner 준비 |
| 인증 | 운영 전용 workload 키·scope·TLS와 기존 사용자 JWT 호환 |
| 배포 확인 | 구앱·신앱 실제 인증 smoke, 오류/지연 시 기존 안전 경계 유지 |

기존 공개 API·DTO·AI/S3/Redis·Billing wire·manifest digest 형식은 변경하지 않는다.
