# 운영 미연결 기록·삭제 index 읽기 점검

## 삭제 승인 후 사전 재확인

- 사용자 후속 승인으로 사전 읽기 재검사. 결과4/Job15/부모2개 부재 유지, Job PROCESSING11/COMPLETED4다. 실제 실행 프로세스가 없다는 증거는 아직 확보하지 않았다.
- 추가 시각 집계: pendingAt 범위2026-08-05T14:16:02.186Z~2026-09-02T07:22:27.904Z, processingStartedAt 범위2026-08-06T06:48:51.426Z~2026-09-02T07:22:27.904Z. 마지막 발신2026-09-02T07:22:27.904Z, 마지막 완료2026-09-02T07:22:28.916Z.
- 위 시각은 최초 생성/접수 증거가 아니다. 다만 전부8월23일 이전에 처리 종료됐다고 할 수 없다. 기존 createdAt 부재 판단을 유지하면서 처리 시각 증거를 보완한다.
- 복구용 사본의 저장 위치/접근/보존기간과 실행 작업 부재 확인은 미완료. 실제 백업/삭제/DB변경 미실행.

## 2026-10-18 추가 소유권 조사

사용자 승인 후 운영 exact host/DB에서 snapshot 읽기 Transaction으로 재조회했다. 결과만 집계하고 Transaction은 abort했다. 자료·인덱스·서버 변경 없음. 식별자/학습 원문은 출력하지 않았다. 검사기: `/private/tmp/lc-prod-orphan-owner-read.cjs`.

- 미연결 결과4건의 userId는 모두 존재하고 서로 같은 canonical UUID1개다. 개발용 고정 UUID는 아니다. 해당 소유자의 다른 Session과 active=true Session, ownership guard가 존재하며 MERGED guard와 삭제 operation은 없다. 이것은 결과에 남은 소유 정보이며 누락 부모의 원 소유권을 독립 증명하지 않는다. active=true도 현재 제출 권한 증명이 아니다.
- 문항Job15건에는 직접 userId가 없다.4건은 위 결과와 같은 examId로 연결되며 나머지11건의 소유자는 이번 근거로 확인되지 않는다. 부모2개 중 결과 소유정보가 있는 부모는1개, 그 부모의 결과 내 소유자 충돌은0이다.
- 관련 삭제target/생성operation/접수receipt는 여전히0건이다. 결과4건·Job15건 모두 createdAt이 없어120일 이상 경과 여부를 판정할 수 없다. createdOver120Days=0 집계는 최근 생성이라는 뜻이 아니다.
- 현재 자료만으로 부모의 삭제 원인·시각·주체는 확정할 수 없다. 삭제 이력 부재를 삭제가 없었다는 증거로 삼지 않는다. 과거 백업/감사 기록과 exact 부모 연결의 별도 대조가 필요하며 이번에 백업 복원이나 감사 서비스 접근을 수행하지 않았다.
- 결론: 정상 사용자 자료 가능성이 있으므로 자동 삭제/개발잔여물 분류/소유자 추정 복구는 불가.19건 보존과 기존 gate 유지. 별도 격리 정책도 이번 읽기 조사만으로 승인된 것이 아니다.

## test 내부 읽기 후속 — 확인 완료

사용자 후속 요청으로 기존 LC test 사설 네트워크/실행 역할에서 일회성 읽기 전용 Fargate 태스크를 실행했다. 아래 CloudShell 직접 조회 실패를 대체하는 실제 DB 조회 증거다. 운영19건은 이번에 다시 조회하거나 변경하지 않았다.

- 태스크 정의 `lc-test-deletion-index-read-20261017:1`, 실행 ID `1b6d180de01a4dc1a1dd8385aad4fd17`. 공식 mongo:8.0/mongosh, 실행 상한150초, CPU256/memory512, test Mongo Secret1개만 주입, 별도 task role/앱/포트 없음.
- 조회 대상: azure_results, learning_record_deletion_operations/commands/targets, learning_record_billing_continuations의 index metadata만. 개별 문서·URI/비밀값 출력 없음.
- **test Azure cleanup index는 잘못된 `{examId:1,_id:1}`**다. 이름 `idx_deletion_exam_cleanup`, nonunique/non-TTL/nonpartial/nonhidden/nonsparse. 새 코드/검사기는 `{exam_id:1,_id:1}`을 사용하므로 별도 승인된 index 전환이 필요하다. 실제 교체는 미실행.
- 다른 네 collection에서는 삭제 active-owner unique/owner·due·retention/command/target/continuation index 및 기존 expiresAt TTL, `ix_billing_deletion_delivery`를 관측했다. 이번5개 collection 관측을 전체 필수 index/실제 삭제 E2E 통과로 확대하지 않는다.
- 태스크 STOPPED/exit0, 임시 정의 INACTIVE 확인. 기존 test 서비스 taskDefinition·networkConfiguration·loadBalancers 불변, running1/pending0. IAM/방화벽/서비스/DB/index/봉인 변경 없음. 소량의 일회성 Fargate 실행 및 감사 로그만 발생했다.
- 다음은 test의 잘못된 Azure index1개 전환 범위 승인 후 적용, 보완 코드 전달 및 실제 test 검증이다. prod는 앞선 읽기에서 해당 cleanup index가 없었으며 신규 설치 대상이다. 운영19건은 부모 부재 원인/처리 기준 미확정으로 보존한다.

## 5줄 결론

1. 실제 미연결19건은 부모 시험2개를 참조한다.
2. 결과4건은 부모1개, 문항Job15건은 부모2개를 참조하며 모두 retryCount0이다.
3. Job은 PROCESSING11/COMPLETED4이며 조회한 접수·삭제·생성 증거는 없다. 삭제 완료 잔여물이라고 단정할 수 없다.
4. prod Azure에는 기본_id만 있으므로 잘못된 cleanup index 교체가 아니라 새 exam_id index 설치 대상이다.
5. 이번은 읽기 점검이며 자료/인덱스/봉인/서비스를 변경하지 않았다.

## 반드시 읽어야 하는 내용

삭제·접수 증거가 없다는 사실은 미사용 또는 삭제 승인의 증거가 아니다. PROCESSING11건을 완료/실패로 강제 전환하거나 부모 Session을 재생성하지 않는다. 남은 자료의 처리 정책은 백업·감사 이력 등 추가 근거 확인 후 별도로 결정한다. 현재 데이터만으로 부모가 없어진 원인과 시점은 확정되지 않았다.

## 확인 결과

운영 DB의 snapshot read-only Transaction에서 아래 건수와 연결 근거를 함께 조회했다. 개별 ID/사용자 값·답안/채점 원문은 출력하지 않았다. index metadata는 Transaction 밖 별도 읽기다.

| 대상 | 관측 |
| --- | --- |
| exam_results |4건, 부모1개, retry0 전부 |
| question_grading_jobs |15건, 부모2개, retry0 전부 |
| Job 상태 |PROCESSING11 / COMPLETED4 |
| Job completedAt |4건 있음 |
| Job firstUserSubmittedAt / failedAt |모두 없음 |
| 전체 distinct 부모 |2개 |
| 해당 부모에 연결된 learning_record_deletion_targets |0 |
| exam_creation_operations / exam_submission_receipts |각0 |
| azure_results(exam_id) / speechace_results / exam_summaries |각0 |

완료Job4건과 결과4건의 개별 문항별 동일성/내용은 검사하지 않았다. pendingAt을 최초 접수 증거로 사용하지 않았다. 남아 있는 자료만으로 새 권리/시험/삭제 성공을 추론하지 않는다.

## 인덱스·다음 단계

- prod azure_results: `_id_`만 존재. 삭제 operations/commands/targets는 index 목록 없음. 이번에 DDL을 적용하지 않았다.
- 삭제 index 준비는 현재 검사기의 실제 미연결19건 차단과 writer drain 조건을 유지한다. 조건을 true로 위조하거나 검사기를 완화해 설치하지 않는다.
- 보완된 Azure 코드의 test 전달과 환경별 index 점검은 데이터 처리 결정과 구분한다. 기존 wrong-name/key index가 있다면 자동 교체하지 않는다.
- 관련 TMI-193/TMI-217. 조사 스크립트 `/private/tmp/lc-prod-orphan-evidence.cjs`: exact 운영 host/DB 제한, 최대1000개 미연결 기록 metadata만 메모리에서 집계, 문서 ID 비출력. 이번 Gradle 재실행 없음(제품 코드 변경 없음).

## test 조회 한계

로컬 기존 조회 도구는 AWS_AUTH_UNAVAILABLE로 실패했다. 다른 작업과 분리된 기존 LC CloudShell 세션에서 test Secret을 메모리로만 읽고 exact test host를 검증한 뒤 mongosh로 네 collection의 index 조회를 시도했다. 결과는 TEST_INDEX_READ_FAILED/exit1이며 index를 얻지 못했다. raw exception은 숨겼으므로 정확한 실패 원인을 단정하지 않는다. 해당 조회 프로세스 종료를 확인했고 권한/방화벽/서비스 변경은 없다. test 인덱스 확인 완료를 주장하지 않으며 기존 test 네트워크 내 읽기 점검 등 별도 접근 경로가 필요하다.
