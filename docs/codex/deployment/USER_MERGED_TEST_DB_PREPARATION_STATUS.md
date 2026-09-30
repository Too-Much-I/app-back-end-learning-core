# UserMerged 테스트 DB 준비 상태

## 결론

- 2026-09-30 최종 복구 확인: test:8/OFF 단일 COMPLETED·1/1/0·ALB healthy·HTTPS200/UP. test:9의 누락 연결 설정 보완은 보존됐으나 동시ON은 Clock 중복 주입으로 실패했다. DB 검증과 Repository14개 등록은 정상, 다음은 Clock 주입 및 전체 앱 기동 회귀 수정/새 이미지다. 실패 태스크 모두 STOPPED, 검사 running 없음. 활성화/회원 통합/채점 E2E는 미완료이며 Identity는 변경하지 않았다.
- 2026-09-30 추가 활성화 시도: test:9에 UserMerged/Challenge 동시ON, workload issuer/JWKS·AI endpoint 및 기존 Secret의 방향별 key 참조를 추가했다. 기존 writer STOPPED 및 0대 상태 개정 전환 후 최종 DB 검증 성공(owner0·필수 인덱스·rollback/데이터 보존 정상). 하지만 전체 앱 기동에서 SummaryDispatchScheduler의 Clock 주입이 userMergedClock/gradingClock 두 bean과 충돌해 실패했다. Repository14개 등록은 정상이다. 정상 OFF test:8 복구 진행 중이며 기능 활성화 성공이 아니다. Identity는 사용자 재확인에 따라 변경하지 않았다.
- 2026-09-30 실제 설정 확인: LC test:8 서비스1/1/0·ALB 정상1/비정상0. UserMerged writer/consumer/source-deny 및 Challenge 모두 OFF이며 UserMerged 전용 workload issuer/JWKS가 아직 없다. Challenge AI endpoint/인증키 참조도 없다. 일반 로그인 issuer/JWKS와 구분한다. Billing/AttemptGroup OFF 유지. DB 준비 이력과 별개로 최신 DB 검증·설정 보완·활성화 후 검증이 필요하며 이번에는 원격 변경하지 않았다.
- 2026-09-30 11:24 KST 업데이트: TMI-187 PR33의 develop 배포36658994728이 ECS 및 최종 Verify health까지 성공했다. 아래 미배포/test:6 복구 기록은 과거 상태다. 실제 활성 flags와 UserMerged 수신·Identity E2E는 이번 조회에서 검증하지 않았으므로 별도 확인이 필요하다.
- 2026-09-30 TMI-187 로컬 수정 완료: exams-only 및 withdrawal 별도 repository registrar를 정리해 Boot 자동 등록으로 통일했다. 단위549·Mongo83·Node105 통과. 실제 자동 구성에서14개 repository 등록 및 Challenge/UserMerged 동시ON 기동·guard transaction/index/probe 검증 성공. 아래 repository 누락은 배포 이미지의 실패 이력이며 새 코드의 원격 배포는 아직 없다. UserMerged flags/Identity E2E 완료로 해석하지 않는다.
- 2026-09-30 최종: test:6/OFF 복구 완료, ECS 단일 COMPLETED·1/1/0·ALB healthy·HTTPS200/UP. DB 준비는 완료됐지만 UserMerged 수신 활성화는 아래 repository 등록 코드 차단점 때문에 미완료다.
- 2026-09-30 최신 결과(TMI-125/TMI-136/TMI-178): 테스트 배포 예외 승인 후 재검증 성공. dry-run `03cd542bbd7543f3850f3542ed3bcb14`, 최종 apply `b549663b8fbd4acd8bd59dab75061cfe` 모두 STOPPED/exit0, owner0·정합성 오류0·누락 인덱스0, exact owner index2·rollback 잔여0·기존 문제/시험 데이터 보존 확인. 다만 flags ON인 test:7은 Repository bean 누락으로 기동 실패하여 정상 OFF test:6 복구 중이다. 수신 활성화/E2E 완료가 아니다.
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

## 후속 결정 및 다음 작업 (2026-09-28)

- 사용자 결정: Identity의 **기존 UserMerged 전용 저장·발행 경로**를 사용한다. 신규 OwnerEvent publisher를 같은 통합 이벤트에 병행 활성화하지 않는다. Identity 구현/배포 변경은 이번 결정 기록에서 수행하지 않았다.
- TMI-178 기본 `_id_` 판정 수정·회귀 테스트 및 수정 이미지 test:6 배포는 완료됐다. 테스트 서비스 grace300초·COMPLETED·HTTPS200/UP 확인 이력이 있다. 위 수정 필요 문구는 당시 이력이며 현재 남은 작업은 실제 UserMerged flags ON 상태의 검증이다.
- 다음 LC 작업: 테스트 DB 최신 inventory 및 ACTIVE guard backfill 필요 여부 재확인, 구 writer 태스크 drain과 guard 전환 절차 이행, workload issuer/JWKS 설정·현재 kid 확인, writer/source-deny/consumer 활성화 및 기동/인증 검증. consumer는 writer와 source-deny가 모두 켜져야 한다. Identity 통합·publisher는 LC 준비 확인 전 OFF 유지한다.
- 이후 Identity 작업: 별도 workload issuer 명시 및 발급 활성화, 기존 UserMerged publisher에 LC exact endpoint 연결, 테스트 회원 통합 활성화. MEMBER 로그인 토큰을 workload 토큰 대신 보내지 않는다.
- E2E: 테스트 Guest 기록 생성→MEMBER 통합→204·target 기록 소유권 확인·source 기존 토큰 거절, 동일 eventId 재전송의 중복 이전 방지, 일시 장애 재시도 및 양방향 토큰 오용 거절 검증. 실제 테스트 계정/이벤트 전송은 별도 실행 단계다.
- Billing/AttemptGroup 관련 flags OFF 유지. 이 consumer의 시험 기록 이전과 Challenge 기록 승계를 혼동하지 않으며 챌린지 활성화/AI 채점 E2E는 별도 후속이다.

## 2026-09-30 테스트 활성화 시도와 신규 기동 차단점

- 구 writer `1428e0149b864feaade08659f49edbdf` STOPPED, service0/0/0 확인 뒤 최종 apply를 실행했다. ALB deregistration delay300초 및 태스크 STOPPING 동안에는 안전 assertion으로 apply 실행을 막았고, 실제 종료 후에만 실행했다. guard/inbox `_id_` metadata 및 exact owner indexes2, `transactionRollback=true`, `catalogUnchanged=true`, `ownerCountsUnchanged=true`, `probeDocuments=0` 확인.
- 공개 JWKS에서 kid `tosunsaeng-identity-test-rsa-1`, RSA/RS256/sig 확인. 실제 workload 토큰 발급/전송은 수행하지 않았다.
- test:6을 복제한 test:7은 기존 digest `sha256:f13ff0711504794753ea79404f70d02f2729ce2d460a7e92917bf4387ad26f72` 유지, UserMerged 세 flag와 workload issuer/JWKS 5개 값만 변경했다. Billing/AttemptGroup/Challenge OFF 유지, 권한·네트워크·Secret 변경 없음.
- 신규 `8fe7738493c34af4900201032db86c74` exit1: `mergedUserAccessGateFilter`에 필요한 `UserOwnershipGuardRepository` bean이 없다. `TosunsaengApplication`의 Mongo repository scan이 exams 패키지만 지정하며 UserMerged용 별도 등록이 없다. DB 권한/인덱스 문제나 ALB grace 부족으로 분류하지 않는다.
- 추가 코드 점검: consumer는 `UserMergedInboxRepository`와 `WithdrawnUserAccessDenyRepository`도 필수다. 후자는 withdrawal flags만으로 등록되므로 UserMerged consumer ON/withdrawal OFF 조합도 함께 보완해야 한다. withdrawal 기능 자체를 켜서 우회하지 않는다.
- 기존 security 테스트는 guard repository를 mock하고 Mongo 통합 테스트는 repository factory에서 직접 생성하므로 실제 feature 조합의 repository 등록 누락을 검출하지 못한다. 다음 수정은 명시적인 조건부 repository 등록과 실제 configuration 조합 회귀 테스트다. 이번에는 runtime 코드를 수정하지 않았다.
- 배포 시 desired0→1과 revision 전환을 동시에 요청하자 기존 revision6 태스크 `710fb2ecc26a485a8bad2703430ade47`도 일시 생성됐다. 다음 활성화 시에는 desired0 상태에서 신규 revision으로 먼저 전환·이전 deployment 정리를 확인하고, writer 부재 상태의 최종 inventory 후 scale-up을 분리해야 한다. 실제 통합 이벤트는 전송하지 않았다.
- 실패 반복을 막기 위해 desired0으로 내린 뒤 정상 test:6/OFF 복구를 요청했다. template도 OFF로 유지한다. 첫 merge 이후 deny-unaware rollback 금지 규칙은 유지하며 이번 실패는 consumer 기동 전이다.
- 로컬 전체 단위 테스트548개 및 migration Node7개 통과. 이번 턴 Mongo 통합 suite는 재실행하지 않았으며 실제 테스트 DB의 준비/rollback canary로 배포 전 조건을 확인했다. 인증·204·멱등성/소유권/source deny·성능 E2E는 새 이미지 배포 후 남는다.
