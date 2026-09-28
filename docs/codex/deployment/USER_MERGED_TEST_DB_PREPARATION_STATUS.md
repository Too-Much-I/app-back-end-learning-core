# UserMerged 테스트 DB 준비 상태

## 결론

- 관련 TMI-126/TMI-125. 2026-09-28 사용자 승인으로 테스트 DB 준비·검증을 완료했다.
- 대상은 `tosunsaeng-test/Cluster0/to-teacher-learning-core-test`뿐이다. Identity DB·운영 DB는 변경하지 않는다.
- 원본 준비 스크립트 Node 테스트7개 통과, 실제 DB dry-run·apply/verify 성공. 기존 owner0·정합성 오류0·owner 인덱스2개 생성, 롤백·기존 데이터 보존 확인.
- 테스트 LC 쓰기 태스크 실제 종료 후 적용했고 기존 test:3/OFF로1대 복구 완료했다. ECS1/1/0·COMPLETED·ALB healthy, HTTPS health200·미인증 Challenge401 확인. Billing·UserMerged·Challenge flag는 켜지 않았다.
- **추가 코드 차단점:** 기본 `_id_` 인덱스에 대한 startup validator의 `isUnique()` 요구는 실제 Mongo metadata와 맞지 않는다. DB 준비만으로 활성화 가능한 상태가 아니다. Identity workload 설정/E2E도 별도로 남는다.

## 준비 범위

| 대상 | 준비/검증 |
| --- | --- |
| user_ownership_guards | 컬렉션·기본 `_id_` 인덱스, 기존 owner ACTIVE backfill |
| user_merged_inbox_events | 컬렉션·기본 `_id_` 인덱스, TTL 없음 |
| exam_results | `idx_exam_results_user`, `{userId:1}`, nonunique |
| exam_summaries | `idx_exam_summaries_user`, `{userId:1}`, nonunique |
| user_merged_transaction_probe | 빈 컬렉션 준비, 합성 canary insert→abort→잔존0 검증 |

기존 owner0이면 backfill 문서도0이다. DB 준비를 위해 Identity 사용자나 가짜 학습 이력을 생성하지 않는다. source/target 실제 테스트 계정 생성은 Identity 이벤트 연동 E2E 때 수행한다.

## 실행 및 증거

- 기존 `scripts/mongodb/user-merged-prepare.js` 업로드 SHA256: `c4bddd944f7fca550c5471c54d50271552fe83048154ac95a08d63789f5766bf`.
- 기존 LC test execution role와 MongoDB Secret 참조를 사용하는 private-subnet 일회성 mongosh7.0 Fargate task를 사용한다. 별도 credential 조회·권한 확대 없음.
- 최초 `--eval` 호출은 마지막 module export가 완료값이 되어 비동기 검사 완료를 보장하지 못했다. exit0만으로 성공 판정하지 않았다. task `7757933d35b048799becaa6c7ab07d1d`는 유효한 dry-run 증거가 아니다.
- 실행용 wrapper는 원본 함수들을 유지하고 `runMongoMigration()` Promise가 마지막 완료값이 되도록 호출 부분을 조정했다. 오류 원문은 출력하지 않는다. 저장소 migration 파일은 변경하지 않았다.
- 유효 dry-run task `ddc74f3b13a6480388c763c77b1503fa`, definition `tosunsaeng-lc-test-user-merged-prepare:2`: STOPPED/exit0 및 DRY-RUN 완료 보고 확인. owner0, invalid0, active duplicate0, orphan0, mismatch0, existing merged0, withdrawal0, nonterminal operation0, missing indexes2.
- 적용 definition `:3`에는 exact 이름/옵션의 인덱스 검증, canary rollback 및 기존 시험 컬렉션 건수·Challenge 문제 전체 EJSON 전후 동일성 검증을 추가했다. 기본 apply=false이며 실제 태스크 drain 확인 후에만 apply/drained override를 사용한다.
- 기존 LC task c81f7327176940e0846e722223496a63의 STOPPED 및 service0/0/0 확인 후 적용을 시도했다. `:3` task 6ed3d7f4532844fa87be59c7f3bc07a2는 컬렉션 생성 전 실패했다. read-only diagnostic task 6c36814abe794987bf775c7cc0a266aa에서 mongosh의 `database.listCollections`는 함수가 아니고 `getCollectionInfos`는 함수임을 확인했고, 기존8컬렉션만 존재해 변경 전 실패임을 확인했다.
- 실행용 `:4`는 원본의 두 컬렉션 목록 조회를 mongosh `getCollectionInfos()`로 바꿨다. 변경 범위는 실행 API 호환성과 완료 대기·안전한 오류 출력뿐이며 inventory/backfill/index 정책은 유지한다. 저장소 원본 스크립트에 이 호환성 수정과 실행 통합 테스트 반영은 후속 작업이다.
- 최종 apply task `ea3292b71c644c3395c20195451f34c8`, definition `:4`: STOPPED/exit0, "applied and verified successfully" 확인. 기본 컬렉션2개와 인덱스 대상 컬렉션2개, probe 컬렉션1개를 준비했다. 기존 owner0으로 guard backfill0, 테스트 계정 생성0이다.
- 실측 보고: `transactionRollback=true`, `catalogUnchanged=true`, `ownerCountsUnchanged=true`, `probeDocuments=0`, `exactOwnerIndexes=2`. Challenge 문제 전체 EJSON이 작업 전후 동일하며 기존 시험 컬렉션 건수도 동일하다. 계정·학습 데이터 삭제 없음. canary는 insert 후 abort해 영구 저장되지 않았다.

## 활성화 전 주의

- 기존 writer OFF 서비스로 복구한 뒤 새 학습 데이터가 생기면 UserMerged 활성화 직전에 inventory/backfill을 재검증해야 한다.
- 첫 merge를 처리한 뒤에는 source deny/guard/inbox를 모르는 버전으로 돌아가지 않는다.
- 실제 guard/inbox `_id_` metadata는 `{v:2,key:{_id:1},name:"_id_"}`이며 `unique` 필드를 생략한다. Mongo 자체 기본 `_id` 고유성은 정상이다.
- 로컬 Spring Data MongoDB4.4.2의 `IndexInfo.indexInfoOf`에 이 metadata를 넣어 `isUnique()==false`를 재현했다. `UserMergedIndexValidator.requireIdIndex`는 이를 true로 요구하므로 올바른 DB도 거절한다. 기본 인덱스를 바꾸거나 검증 전체를 끄지 말고 `_id_`의 암묵적 고유성을 올바르게 판정하도록 코드 및 회귀 테스트를 수정해야 한다. 이 작업에서는 Java runtime을 수정하지 않았다.

## Identity 인계 수신: 활성화 예정값 (2026-09-28)

- 후속 사용자 확인: Identity 테스트의 회원 통합·workload 발급·이벤트 발행은 현재 모두 OFF다. LC 수신 준비 완료 후 Identity 전송 설정을 적용하고 E2E를 진행한다. 사용자 확인에 근거하며 이 턴에 AWS 상태를 직접 재조회한 것은 아니다.
- 사용자 전달 Identity 코드 확인 결과이며 실제 AWS 활성 상태/발급·전송 성공을 LC에서 재검증한 증거는 아니다. MEMBER 로그인 테스트와 기존 Guest의 회원 통합 테스트는 별도다. 기존 Challenge403의 직접 원인은 Challenge OFF였고 UserMerged 인증정보를 일반 로그인 인증정보로 사용하지 않는다.
- 예정값: `USER_MERGED_WORKLOAD_ISSUER=https://identity-test.to-teacher.com`, `USER_MERGED_WORKLOAD_JWK_SET_URI=https://identity-test.to-teacher.com/.well-known/jwks.json`. Identity는 별도 `WORKLOAD_JWT_ISSUER`에 값을 명시해야 한다. 실제 task template/환경에는 아직 적용하지 않았다.
- Identity는 로그인/workload 토큰을 별도로 발급하되 서명 키와 JWKS를 공유한다고 전달했다. 예상 공개 kid는 `tosunsaeng-identity-test-rsa-1`이며 활성화 전에 현재 JWKS와 대조한다. 이전의 별도 issuer 설명과 달리 이 테스트 제안은 issuer URL 값도 동일하다. LC 구현은 workload 전용 audience 및 subject와 TTL/header 검증으로 목적을 분리하며 양방향 토큰 오용 거절 E2E가 필요하다.
- LC 계약은 RS256, audience에 learning-core-user-merged, sub=identity-service, iat=nbf, exp-iat 최대2분, canonical UUID jti, typ=JWT, 비어 있지 않은 kid다. 실제 토큰/비밀키는 문서에 기록하지 않는다.
- Identity 전송 목적지는 `POST https://api-test.to-teacher.com/internal/v1/events/user-merged`. LC는 schemaVersion1의 eventId/sourceUserId/targetUserId/occurredAt body를 처리한다. 기존 UserMerged publisher와 신규 OwnerEvent publisher 중 단일 경로를 확정해야 하며, 신규 경로의 envelope를 같은 것으로 가정하거나 둘 다 활성화하지 않는다.
- 키 교체 시 공유 서명 키의 이전 공개키는 workload2분뿐 아니라 실제 사용자 토큰 최대 TTL, 검증 여유시간, JWKS 캐시를 고려해 유지한다. 구체적 기간은 Identity 운영값 확인 전 확정하지 않는다.
- 남은 순서는 _id 검증 코드 수정·회귀 테스트/새 이미지 준비, DB inventory 재확인, LC workload 설정·consumer/source deny 검증, 단일 Identity publisher 활성화와 Guest→MEMBER canary다. 수정·활성화·이벤트 전송은 이번 인계 수신 작업에서 수행하지 않았다.
