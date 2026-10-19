# 미제출 챌린지 재시도 — 구현과 배포 전 확인

## 5줄 결론

1. 미제출 만료는 같은 KST 날짜에 새 attemptId로 다시 시작한다. 실제 제출된 문제는 재응시하지 않는다.
2. 새 시도는 생성부터 1시간이며 만료 ID는 계속 거절한다. 유효한 시도 재호출은 같은 ID를 돌려준다.
3. 만료로 완료 표시·참고 답안·다음 문제를 열지 않는다. 실제 제출 기준 집계는 유지한다.
4. 기존 만료 기록을 삭제하지 않고 내부 attemptSequence로 새 시도를 구분한다. 원본 문서는 null/필드 없음, 재시도는 1부터다.
5. 인덱스 이행과 프론트 적용 확인 전에는 배포하지 않는다. 이 작업은 코드·테스트 준비이며 운영 변경이 아니다.

## 반드시 읽을 내용

- [프론트 계약](../contracts/ten-second-challenge-frontend-api.md)의 2026-10-11 변경을 적용한다. 만료 ID의 410을 받으면 오늘 진행도를 조회하고 같은 날짜의 문제를 새 attempt로 시작한다. 새 ID의 새 녹음·업로드 URL·제출 키를 사용한다.
- 구 인덱스는 userId/challengeDate/questionNumber에 unique를 적용하므로 재시도를 차단한다. 새 인덱스는 여기에 attemptSequence를 추가한다. 신규 collection이나 기존 데이터 삭제는 없다.
- 서버는 기존 시도의 revision을 같은 transaction에서 갱신하고 후속 순번을 insert한다. unique 제약과 transaction retry가 동시 최초 생성·재시작을 한 시도로 수렴시킨다. 이전 시도는 EXPIRED로 유지한다.
- 기존 만료 이후 이미 제출한 후속 문제는 삭제하지 않는다. 다만 미제출 앞 문제는 다시 제출해야 정상 순차 진행 조건을 만족한다. 과거에 공개된 답안을 회수할 수는 없다.

## 사용자 결정/적용 범위

- 같은 날짜의 미제출 만료 재시도와 만료 완료 처리 제거는 사용자 승인됨.
- 운영 DB 인덱스 변경·writer 중단·배포는 아직 수행하지 않았으며 별도 실행 승인이 필요하다.
- 프론트가 기존 만료=submitted 동작에 의존하는지 확인한 뒤 배포 시간을 정한다. 공개 field/enum/URL은 불변이나 의미는 변경된다.

## 위험과 미확인 사항

- 공유 DB에 접근하는 모든 구 Challenge API writer/worker를 drain해야 한다. 신구 코드 혼용은 금지한다.
- 재시도 데이터 생성 후 구버전은 단일 attempt 조회 가정이 깨지므로 단순 이미지 롤백을 하지 않는다. 구 unique index 재생성도 중복 문제 기록 때문에 실패할 수 있다. 사고 시 기능 비활성화 후 forward fix를 우선 검토한다. 임의 기록 삭제는 금지한다.
- 생성된 이전 S3 URL 자체를 취소하지 않지만 deadline에 제한되고 새 attempt는 별도 key를 사용한다. 미제출 audio를 AI로 전송하지 않는다.
- 실제 운영 데이터 dry-run 및 앱 E2E는 별도다. 반복 미제출 시도는 기록량을 늘릴 수 있으므로 운영 관찰이 필요하다.

## 배포 순서

1. 프론트 동작 확인, 테스트 성공 및 rollback 제한을 검토한다.
2. 기존 스크립트 `scripts/mongodb/challenge-10s-prepare.js`를 기본 dry-run으로 실행한다. 기존 연결 주입 방법을 사용하고 접속값을 문서·로그로 출력하지 않는다.
3. DB를 공유하는 Challenge API writer와 worker를 모두 중단/drain하고 필요 시 백업 정책을 확인한다.
4. `CHALLENGE_PREPARE_APPLY=true`, `CHALLENGE_WRITERS_DRAINED=true`, `CHALLENGE_RETRY_INDEX_MIGRATION=true`를 명시해 이행한다. 스크립트는 새 인덱스들을 생성·검증한 뒤 정확히 `challenge_owner_date_question_unique`만 제거한다. 데이터는 변경하지 않는다.
5. 구 unique index가 없는지, 새 unique index가 있는지 다시 dry-run/기동 검증한다. 신규 코드는 구 constraint가 남아 있으면 시작을 거절한다.
6. 신버전만 기동하고 만료→같은 날 재시작→새 ID→제출→1개 집계, 중복 시작/제출, 자정 rollover 및 기존 제출 replay를 확인한다.

## 부록: 코드 근거

- `ChallengeModels.Attempt.attemptSequence`: 원본/후속 시도 구분, 기존 문서 backfill 불필요.
- `ChallengeService.start`: 이전 시도 만료·revision touch·후속 생성 원자 처리, 기존 snapshot 재사용.
- `ChallengeStore.question` 및 `ChallengeService.at`: 최신 순번 조회. `ChallengeViews.detail`: 미제출 답안 비공개.
- `ChallengeStartupValidator` 및 `scripts/mongodb/challenge-10s-prepare.js`: 인덱스 검증/명시적 이행. 프론트/AI 필드, 인증, 시험 도메인 및 S3 key 형식 불변.
- 테스트 실행 결과는 현재 WORKLOG/CURRENT_STATE에 기록한다. 일반 테스트는 외부 인프라를 호출하지 않고 Mongo 통합 테스트는 격리된 로컬 컨테이너를 사용한다.
