# LC 운영 인덱스·TLS 네트워크 사전 준비

## 후속 읽기 진단 정정 — Azure 오탐 확인

- 앞선1,410건은 검사기 기준 관측이며 실제 부모 미연결 총수로 사용하면 안 된다. `AzureResult.examId`는 `@Field("exam_id")`로 저장된다. 검사기는 raw `examId`로 lookup해 Azure1,391건 전부를 오탐했다. 올바른 `exam_id`로 운영 재조회한 결과1,391건 모두 부모 Session이 존재했다.
- `exam_results`는 부모 있음15,040/없음4, `question_grading_jobs`는 있음15,101/없음15다. 따라서 확인된 실제 부모 미연결은 두 collection 합계19건이다. 부모 부재 원인·삭제 가능성은 미확정이며 자료를 삭제/복원하지 않았다. 개별 집계는 고정 snapshot이 아니다.
- owner 검사 invalidOwnerCount1은 과거 고정 개발 ID 한 값이다. Session2건이 해당 ID를 사용하고 나머지1,716건은 검사기 canonical UUID 조건을 만족한다. 결과15,044건·Summary1,171건도 canonical이다. 기존 개발 ID를 임의 사용자로 rewrite하거나 런타임 인증을 완화하지 않는다.
- 검사기뿐 아니라 `DeletionWorker.cleanMongo/verify`가 collection명+Document/raw 쿼리로 모든 시험 하위를 `examId` 조회하고, `DeletionPersistenceFence`도 직렬화된 Document에서 `examId`를 읽는다. Azure에서는 정리 누락/검증 오판 또는 저장 거절 위험이 있다. 이는 코드 정적 확인이며 실제 삭제를 실행해 재현한 결과는 아니다.
- 다음 보완은 Azure의 실제 저장 필드를 검사기·cleanup index·worker 정리/검증·persistence fence에 일관되게 적용하고 회귀 테스트하는 것이다. DB 필드 rename이나 기존 데이터를 삭제하는 해결책은 아니다. 이 읽기 진단 턴에서는 제품 코드/DDL/설정/배포를 변경하지 않았다. 실제 미연결19건은 별도 원인 확인 대상으로 남긴다.

근거: `src/main/java/web/tosunsaeng/domain/exams/domain/entity/AzureResult.java`, `scripts/mongodb/learning-record-deletion-prepare.js`, `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/application/DeletionWorker.java`, `src/main/java/web/tosunsaeng/domain/learningrecorddeletion/infrastructure/DeletionPersistenceFence.java`. 아래 초기 관측은 이 정정과 함께 읽는다.

## 5줄 결론

1. 사용자 요청대로 기존 시험 목록 최종 봉인은 수행하지 않았다.
2. 운영 DB에 생성/saga·복구·outbox·환불 인덱스18개를 설치하고 실제 정의를 재검증했다.
3. 이 중 TTL2개는 자동 영구 삭제 영향을 설명하고 별도 승인받았다. 현재 대상 업무 자료0건을 확인했다.
4. 별도 승인된 ALB→LC prod8444 SG 규칙과 HTTPS/IP 대상 그룹을 만들었다. 서비스/전달 규칙 연결은 하지 않았다.
5. 삭제 준비 검사에서 부모 연결 불일치1,410건 등이 나와 삭제 관련 DDL은 보류했다. 전부 준비 완료 상태는 아니다.

## 반드시 읽어야 하는 내용

운영 task는 `tosunsaeng-learning-core-prod:7`, running1/pending0, 기존8080 연결 그대로다. 기능 ON·배포·ALB 전달 규칙 변경 없음. 기존 Session·답안·결과·owner·manifest 문서는 수정/삭제하지 않았다. 대상 그룹 생성은 실제8444 listener/TLS 검증 완료가 아니다.

## 적용 결과

- 비TTL16개: saga6, recovery6, outbox2, revoke2.
- TTL2개: `exam_creation_operations.ttl_exam_creation_purge` (`purgeAt`, expireAfterSeconds0, date partial), `attempt_group_event_outbox.ttl_attempt_group_outbox` (`expiresAt`, expireAfterSeconds0).
- `attempt_group_publisher_state` 빈 collection 생성. 기존 이름/옵션 충돌과 다른 TTL을 사전 검사했고 drop/recreate 없음.
- 최종18개 대조 missing=[] 확인. 새 operation/outbox/revoke/control/state는0건, Session Billing 연결 필드에 string값이 없는 조건을 적용 직전 재확인했다.
- 운영 URI는 환경변수로만 전달했으며 명령행/출력에 포함하지 않았다. 일회성 도구 `/private/tmp/lc-prod-index-prep.cjs`: exact prod host/DB 제한, 기본 inspect, 별도 TTL 승인, 업무 쓰기/봉인 경로 없음. selftest18개 정의의 정상/hidden/collation 및 manifest 제외 조건 통과.

## TLS 사전 준비

| 대상 | 적용/관측 |
| --- | --- |
| SG | `sg-0372f877eda5ef96f`, TCP8444 소스 `sg-0e5462fa65a22ded8`만 허용 |
| 신규 rule | `sgr-0c61f041e2cd196f8` |
| 대상 그룹 | `arn:aws:elasticloadbalancing:ap-northeast-2:889384901776:targetgroup/lc-prod-access-events-8444/1041faca66350508` |
| 전송 | HTTPS8444 / IP target / VPC `vpc-04068339482839ff1` |
| health | 기존 test 패턴과 같은 HTTP8080 `/actuator/health`,200; 실제 TLS 검증 대체 아님 |
| 연결 상태 | LoadBalancerArns=[], ECS 기존8080만. 아직 트래픽을 전달하지 않음 |

기존8080 규칙을 보존하고 public CIDR 허용을 추가하지 않았다. Billing prod 서비스가 새로 존재함을 이번에 확인했다(앞선 MISSING 관측과 시점 다름). Billing 운영 두 private subnet의 NAT는 `13.124.57.130`으로 확인됐으나 ALB source-IP 규칙을 아직 적용하지 않았다. 같은 NAT 서비스의 네트워크 접근과 workload JWT 권한은 별개다.

## 삭제·owner 사전 검증 차단

기존 `learning-record-deletion-prepare.js`의 inspect와 `user-merged-prepare.js` inventory를 읽기 전용 실행했다. 여러 집계는 고정 snapshot이 아니다. 식별자/사용자 원문은 출력하지 않았다.

| 검사 | 관측 |
| --- | --- |
| 삭제 인덱스 |24개 미설치, 이름/옵션 충돌0, active owner 중복0 |
| 부모 연결 없는 exam_results |4 |
| 부모 연결 없는 question_grading_jobs |15 |
| 부모 연결 없는 azure_results |1,391 |
| 나머지 검사 대상 하위 collection |0 |
| 삭제 검사 owner 불일치 |0 |
| owner inventory invalidOwnerCount |1 (문서 수가 아닌 검사기 집계값) |
| 기존 MERGED guard |1 (이미 수행된 merge일 수 있으므로 오류로 단정하지 않음) |
| owner 조회 index |missing/errors 모두0, guard/inbox 기본_id 존재 |
| legacy entries / rollout state count |각0, 최종 봉인 미실행 |

부모 연결 누락은 현 스키마의 examId→Session._id 기준이다. 역사적 형식/정리 이력 확인 없이 불필요한 데이터라고 단정하거나 삭제하지 않는다. 기존 삭제 도구는 위 정합성 검사와 writer drain을 요구하므로 이를 우회해 적용하지 않았다. owner backfill/guard 재작성도 하지 않았다.

## 다음 단계와 범위

1. 위 과거 연결/owner 형식의 읽기 원인 분석 후 보존/변환/격리 등 필요한 조치를 별도 확정한다. 무조건 삭제 승인으로 해석하지 않는다.
2. 삭제 준비24개(그중 TTL3개)는 정합성·writer 경계 검증과 별도 TTL 승인 후 적용한다.
3. Billing 운영 공개키/scope/origin 인계 대조, 향후 태스크 Secret 주입·8444 포트/대상 그룹 및 ALB exact 전달/차단 규칙 연결, 실제 인증/TLS 검증이 남았다. 현재 대상 그룹만으로 연결 완료를 주장하지 않는다.
4. 목록 봉인은 사용자 제외 범위로 유지한다. 이번 준비로 실제 배포/활성화를 승인한 것으로 보지 않는다.

제품/API/AI/S3/Redis 코드는 불변, Gradle 미실행. 대신 도구 selftest·실DB18개 사후 읽기·AWS SG/TG/service 재조회·git diff --check를 수행했다. 관련 TMI-116/TMI-128/TMI-217/TMI-220/TMI-193. 사용자/이전 dirty 파일 보존, commit/push/merge 없음.
