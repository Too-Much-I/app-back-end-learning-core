# LC 운영 삭제 저장소 준비 읽기 점검

## 2026-10-18 후속 범위 축소 — 실제 건수 대조

- 05:37:59 UTC 운영 host/DB exact 검사 후 countDocuments만 수행: learning_submissions 0, learning_completions 0, learning_record_deletion_targets 0. 개별 식별자/내용/URI 미출력. 동시점 snapshot 및 최종 봉인 증거는 아니다.
- LearningLambdaClient 생성자는 URL을 검증/호출하지 않는다. 실제 LEARNING_SUBMISSION 삭제 경로에서만 learning bucket과 purge URL이 필요하다. 따라서 적용 직전 inventory 0 및 effective 답안학습 writer OFF가 확인되고 유지되는 경우, 이 전환에 새 Lambda/bucket 준비를 필수로 추가하지 않는다. 기능 OFF 실환경 재확인은 남았으며 데이터가 생기면 이 조건을 재평가한다. 삭제 대상 skip이나 storageVerified 자동 승인은 아니다.
- S3/Redis 실호출 준비 증거는 여전히 별도다. 권한 시뮬레이션을 실제 저장소 삭제 성공으로 확대하지 않는다.
- Billing 최신 회신 §7은 exact 승인 target 전용 일회성 검증을 제안한다. 현재 BillingDeletionSender.tick()은 전체 PENDING에서 선택하므로 운영 사전검증에 그대로 호출하지 않는다. 실제 삭제 target도 현재 0건이다. 정상 생성된 검증 Session의 삭제 승인, exact 대상 실행 도구, Billing 업무/route/OPEN 준비 없이는 real204/ACK 증거를 만들 수 없다.
- 최종 task 초안에 위 관측과 미완료 조건을 추가했다. ECS 등록/기동 gate 완화/실제 삭제/배포 없음. 기존 격리 테스트 재실행 없음.

## 후속 승인 적용 완료

- 사용자 「어」 승인 후 현재 prod:8의 task role/시험 bucket이 직전 조회 대상과 같음을 재확인하고 `tosunsaeng-learning-core-prod-task-role`에 inline `ProductionDeletionS3`를 추가했다.
- 해당 bucket GetBucketVersioning, s3:prefix가 temp/ 또는 temp/*인 ListBucket, 해당 bucket temp/*의 DeleteObject만 허용한다. 기존 ProductionRuntimeS3 정책 readback 동일 확인. 버전 삭제/다른 bucket/범위 밖 prefix 권한은 추가하지 않았다.
- 정책 시뮬레이션: versioning/list_temp/delete_temp allowed, list_outside/delete_outside/delete_version implicitDeny. 정책 전체 readback 일치 확인. 증빙 `/private/tmp/lc-prod-s3-deletion-permission-20261018.png`.
- 실제 object 삭제·DB 변경·배포 없음. IAM은 실행 중 역할에도 적용되는 권한 변경이다. 실호출/Redis/답안학습 원격purge 검증을 대신하지 않으므로 storageVerified는 여전히 미확정이다. 아래 권한 부재 기록은 적용 전 이력이다.

관련 TMI-193/TMI-217. 기존 LC 전용 CloudShell에서 ECS/S3/IAM metadata만 조회했다. 파일 목록/원문·Secret 값은 읽지 않았고 실제 삭제 요청이나 권한 변경은 하지 않았다.

## 확인 결과

- 실행 task는 `tosunsaeng-learning-core-prod:8`, task role은 `tosunsaeng-learning-core-prod-task-role`이다.
- 시험 S3 bucket과 REDIS_HOST 설정은 존재한다. 답안학습 `ANSWER_LEARNING_S3_BUCKET`/`ANSWER_LEARNING_LAMBDA_URL`은 environment/Secret 참조에 없고 SPRING_APPLICATION_JSON의 관련 설정도 없다. command의 해당 이름 참조도 없다. 실제 컨테이너 내부의 모든 설정 공급 경로를 검사한 것은 아니다.
- 시험 bucket GetBucketVersioning 응답은 Status 부재(UNVERSIONED)다. 이는 CloudShell 주체의 조회 성공이며 LC task 역할의 성공 증거가 아니다.
- task role의 inline policy는 ProductionRuntimeS3 하나, attached managed policy는0개. inline Allow Action은 GetObject/PutObject뿐이다.
- 역할 정책 시뮬레이션에서 bucket의 GetBucketVersioning/ListBucket/ListBucketVersions와 가짜 `temp/lc-preflight-not-created/q_1_r0.wav` object의 DeleteObject/DeleteObjectVersion 모두 implicitDeny. 실제 파일은 생성·조회·삭제하지 않았다. 시뮬레이션은 bucket policy/SCP/endpoint 등 모든 실효 권한과 실호출을 대신하지 않는다.

## 필요한 보완 — 미적용

1. 시험 bucket의 GetBucketVersioning, `temp/` 범위로 제한한 ListBucket, `temp/*` DeleteObject 권한을 LC task 역할에 검토·승인 후 추가한다. 기존 Get/Put은 유지한다. challenge 경로 `temp/challenges/`도 포함된다. 현재 unversioned이므로 버전 삭제 권한을 자동 추가하지 않는다. 향후 versioning 변경 시 재검토한다.
2. 답안학습 대상 데이터/기능 범위를 확인하고 실제 운영 bucket·Lambda URL·호출 권한을 별도로 확정한다. 임의 URL/다른 서비스 키를 넣거나 설정 누락을 근거로 삭제대상을 건너뛰지 않는다.
3. Redis 연결/삭제 권한과 실제 파일 삭제는 승인된 격리 probe 자료로 확인한다. 실제 사용자 기록으로 시험하지 않는다.
4. 위 근거가 없으면 storageVerified를 true로 기록하지 않는다. 이번 발견은 신규 코드 결함 판정이 아니라 운영 역할/설정 준비 미완료다.

## 삭제 사전검증 분리 검토

격리 테스트는 sender를 직접 조립해 ACK저장을 검증하지만, 이를 운영 기동 gate 우회 수단으로 쓰지 않는다. 현재 전체 앱 sender bean은 billingDeliveryVerified에 의존한다. 제한된 전용 검증 실행방식·지정Session/event·양측 OPEN/권한·증거 기록 절차의 공동 합의가 선행돼야 하며 아직 해당 운영 도구는 구현/실행하지 않았다.
