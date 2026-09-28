# Learning Core Current State

## Last updated

- 2026-09-28 — TMI-178 구현 완료(미배포): UserMergedIndexValidator가 기본 `_id_`의 암묵적 고유성을 인정하고 이름·단일 `_id`·ASC 형태로 판정하도록 수정했다. owner 인덱스 검증/API/AI 계약은 불변. 실제 BSON 단위 회귀 5개와 Mongo7 replica-set 메타데이터 통합 테스트 추가. 전체 단위548개·migration Node7개 통과, Mongo 통합 테스트도 실행 한정 JAVA_TOOL_OPTIONS=-Dapi.version=1.44로 Docker29 API 불일치를 해소한 후 통과했다. 커밋/푸시/배포·Identity/Billing 활성화 없음. 다음은 사용자 커밋 및 테스트 배포 후 LC 기동/Identity 인증·E2E 검증이다.
- 2026-09-28 — TMI-136(sns 로그인) 에픽 아래 TMI-178 `[Learning Core] UserMerged 기본 _id_ 인덱스 판정 오류 수정 및 회귀 테스트`를 등록했다. Mongo 기본 인덱스의 unique 생략 오판정 수정, 실제 BSON 기반 단위/replica-set 통합 테스트와 완료 기준을 명시했다. 이번 작업은 이슈 등록이며 코드 수정·테스트 실행·배포·기능 활성화는 미실행이다.
- 2026-09-28 — TMI-126/TMI-125 회원 통합은 LC 수신 준비 후 Identity workload 발급·단일 publisher·merge 기능의 단계적 활성화가 필요함을 설명했다. 일반 SNS 로그인 및 무제한 네트워크 공개와 구분, Billing OFF 유지. 실제 설정·배포 변경 없이 기록만 갱신했다.
- 2026-09-28 — TMI-126/TMI-125 사용자 확인: Identity 테스트 회원 통합/workload 발급/이벤트 발행은 현재 OFF다(이번 직접 AWS 조회 아님). 예정 issuer https://identity-test.to-teacher.com 및 해당 /.well-known/jwks.json 사용, LC 수신 준비 후 Identity 전송 설정·E2E 진행 순서 확정. LC _id 판정 수정·회귀 테스트/수신 인증 검증과 단일 publisher 경로 확인이 남는다. 현재 턴 표식 WORKLOG EOF 추가, 기록 외 설정·코드·배포 변경 없음.
- 2026-09-28 — TMI-126/TMI-125 Identity 인계 수신: 테스트 workload issuer는 identity-test.to-teacher.com을 별도 설정하고 JWKS/서명 키는 로그인과 공유 가능하다는 코드 확인 결과를 받았다. LC aud/sub/TTL/header 조건과 호환되나 실제 AWS 활성/전송 미확인, kid 재확인 필요. MEMBER 로그인과 Guest merge 검증 분리, UserMerged/OwnerEvent publisher 단일 경로 결정 및 사용자 토큰 TTL까지 반영한 키 overlap 필요. 예정값만 준비 문서에 반영, 코드·AWS·배포·이벤트 전송 없음. _id startup 판정 수정은 여전히 선행조건이다.
- 2026-09-28 — TMI-126/TMI-125 테스트 DB 준비 턴 표식을 WORKLOG EOF에 보완했다. DB 준비·보존/롤백 검증 및 LC 정상 복구 완료, 임시 작업5개 모두 STOPPED 확인. UserMerged/Challenge OFF 유지, 인덱스 판정 코드와 Identity workload 연결은 후속이다. 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126/TMI-125 DB 준비 후 최종 복구 완료: LC test:3/OFF1/1/0·COMPLETED·ALB healthy, HTTPS health200·미인증401. DB 준비 검증 완료와 UserMerged 활성화 미완료를 구분한다. 기본 _id 인덱스 판정 코드 수정 및 Identity workload 준비가 남는다. 운영/Identity 변경·계정 생성 없음.
- 2026-09-28 — TMI-126/TMI-125 사용자 승인으로 UserMerged 테스트 DB 준비 완료: 컬렉션5개(guard/inbox/result/summary/probe), owner 인덱스2개, owner0/backfill0. 실제 dry-run/apply 성공·rollback probe 잔존0·기존 Challenge 전체 EJSON/시험 문서 건수 동일 검증. 운영/Identity 불변, 테스트 계정 불필요로 미생성. 기존 test:3/OFF 서비스1대 복구 후 ALB 최종 확인 중. 실행 wrapper의 mongosh 조회/async 호환성 보완이 필요했고 원본 스크립트는 미수정이다. 추가 blocker: 기본 _id metadata unique 생략→Spring IndexInfo.isUnique=false를 로컬 재현, UserMergedIndexValidator의 true 요구로 정상 DB도 거절하므로 활성화 전 코드 수정 필요. 세부 deployment/USER_MERGED_TEST_DB_PREPARATION_STATUS.md.
- 2026-09-28 — TMI-126/TMI-125 UserMerged 활성화 준비 조사 턴 표식을 WORKLOG EOF에 보완했다. 체크리스트 안내·준비 스크립트 테스트7개 통과, Identity workload 정보 및 실제 DB 준비 검증 대기 상태다. 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126 UserMerged 활성화 준비사항 조사: 기존 user-merged-prepare.js 전체 확인 및 Node7개 통과. guard/inbox 컬렉션·기본 _id 인덱스, exam_results/summary userId 인덱스2개, 기존 owner ACTIVE backfill/정합성 inventory, transaction probe 준비가 필요하다. 테스트 workload issuer/JWKS와 Identity→LC exact endpoint publisher·재시도·접근제한은 미확인(사용자 로그인 issuer/JWKS와 구분). 이번 원격 상태 재조회/DB apply/배포 없음. UserMerged 구현은 ExamSession/Result/Summary만 이전하며 Challenge 기록 이전은 포함하지 않으므로 게스트 챌린지 승계 완료로 안내하면 안 된다.
- 2026-09-28 — TMI-126 Billing OFF·SNS 로그인/UserMerged 활성화 결정의 실제 현재 턴 표식을 WORKLOG EOF에 정정 기록했다. 사전 DB·인증·연결 검증 후 적용하는 방향이며 실제 설정·배포 변경은 아직 없다. 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126 사용자 결정: 테스트에서 Billing creation saga/phone continuation/reconciliation 및 AttemptGroup writer/publisher OFF 유지, Identity SNS 로그인/JWT 사용과 LC UserMerged writer/consumer/source-deny 활성화를 목표로 한다. 무관한 기능 전체 ON 승인이 아니다. 기존 executor 누락은 writer 활성화로 해소 가능하나 UserMerged DB 준비·구버전 drain·workload issuer/JWKS·Identity producer/연동 검증 후 적용해야 한다. 현재는 결정 기록만 반영했고 실제 flags/Identity 설정/배포는 변경하지 않았다. Billing OFF는 모의고사 API 차단이 아닌 기존 생성 경로임을 재안내한다.
- 2026-09-28 — TMI-126 SNS 로그인/UserMerged/Billing 설정 경계 설명의 현재 턴 표식을 WORKLOG EOF에 보완했다. 설정 활성화 여부는 미확정이며 코드·AWS·배포는 변경하지 않았다. UserMerged 준비 후 활성화 대안과 Challenge 독립 기동 수정안을 구분해 안내 완료.
- 2026-09-28 — TMI-126 설정만으로 오류 해소 가능성 검토: Identity SNS 로그인/JWT 사용과 LC UserMerged는 별개다. UserMerged writer ON이면 누락 executor가 생성되어 해당 주입 오류는 해소 가능하나 guard/inbox·owner index·Transaction 검증이 추가되며 정상 배포 보장은 아니다. consumer ON은 writer/source-deny ON 및 workload issuer/JWKS와 Identity producer 준비가 필요하다. Billing saga OFF는 시험 생성 차단이 아니라 기존 startNew 경로이므로 전화번호1회 제한 비활성화와 무료 시험 제공 중단을 혼동하면 안 된다. 조회/설명/기록만 수행, 코드·AWS 변경 없음.
- 2026-09-28 — TMI-126 Challenge 기동 오류 진단의 현재 턴 표식을 WORKLOG EOF에 보완했다. 수정 위치·회귀 테스트 안내 완료, runtime 수정/재배포는 미실행이며 별도 ELB health 원인 미확정 상태를 유지한다. 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126 사용자 진단 요청: 기존 배포 로그와 현재 소스로 Challenge ON/UserMerged 전체 OFF의 필수 bean 주입 충돌을 재확인했다. ChallengeConfiguration:20은 executor 필수, UserMergedConfiguration:42는 세 flag OR 조건이다. 권장 수정은 Challenge 범위의 optional provider와 writer flag 명시 검증, ChallengeTransactions의 항상-트랜잭션/조건부 guard 유지다. writer ON인데 executor가 없으면 fail-closed해야 한다. 기존 Mongo 통합 테스트는 executor mock/직접 생성으로 wiring을 우회하므로 실제 configuration context 조합 테스트가 필요하다. runtime·AWS 변경/테스트 실행 없음, 별도 ELB health 실패 원인은 아직 미확정이다.
- 2026-09-28 — TMI-126 최종 복구 확인: LC test:3/OFF는 마지막 성공 deployment로 명시적 ROLLBACK 후1/1/0·COMPLETED, ALB healthy(실패 교체 대상 draining) 및 HTTPS200 확인. 중간 동일 revision 교체 task도 ELB health 실패했으므로 새 이미지 배포 시 별도 원인 검증 필요(시작 로그만으로 정상 판정 금지). AI test:2 정상, 문제/인덱스 준비 완료, Challenge 활성화는 구성 코드 수정 전 보류. WORKLOG EOF 보완, whitespace/표식1회 확인.
- 2026-09-28 — TMI-126 문제 100일/300개 전체 JSON 동일성 확인 및 migration dry-run/apply 성공(6컬렉션/9인덱스, 일회성 task 모두 STOPPED/exit0). AI test:2 gpt-6-luna 배포1/1/0·COMPLETED·ALB healthy, reasoning none/출력10000/전사 유지. LC test:4 Challenge ON은 UserOwnedTransactionExecutor bean 부재로 exit1: UserMerged flags가 모두 OFF일 때 구성 미생성인데 Challenge는 필수 주입한다. 정상 test:3/OFF로 복구 요청 후 running1/HTTPS health200·미인증401 확인, 롤아웃 최종 수렴 확인 중. 운영 LC:20 불변. AI Secret 읽기 승인 적용, 값 미노출. 시작일·실제 채점 E2E 미완료, 별도 코드 수정/사용자 push 필요.
- 2026-09-28 — TMI-126 사용자 요청으로 운영 문제 컬렉션만 테스트 LC DB에100일/300문제 복사 완료(UI100문서 확인), 원본 불변. 숫자3필드는 명시적 BSON int로 입력했다. index migration·전체 비교·Challenge 활성화 준비 중. LC 실행 역할의 AI Secret 읽기 추가 승인 완료. 추가 요청 gpt-6-luna는 공식 문서 Responses/none 호환 확인, Task template 수정 및 실제 재배포 준비 중이다.
- 2026-09-28 — TMI-126 AI 테스트 배포 완료 턴 표식을 WORKLOG EOF에 보완했다. 배포·연결 검증 완료 상태를 유지하며 기록 외 추가 변경 없음. LC 활성화와 실제 채점 E2E는 후속이다.
- 2026-09-28 — TMI-126 AI 테스트 배포 완료: tosunsaeng-ai-learning-test:1, service1/task1 내 API·worker,0.5vCPU/1GiB,1/1/0·COMPLETED·ALB healthy. 기존 ALB ai-test host+NAT source 제한/나머지403, 전용 S3/IAM/SG/로그7일 생성. ECR digest cf2ea719…003ca35. 동일 VPC Redis PING/S3 PutGetDelete/HTTPS200/미인증401 검증 성공, 임시 probe STOPPED. 기존 LC-test/운영 모두 정상, LC Challenge OFF·문제 복사/실제 채점/Callback E2E는 후속. bootstrap 문서 및 Task JSON 추가, 비밀값 미기록.
- 2026-09-28 — TMI-126 ai-test.to-teacher.com ACM dac51c42-a317-4b94-8f78-dff6fb7ba1ac ISSUED 확인. AI Docker Python3.12 테스트133개 통과(경고1개). CloudShell 소스 업로드는 Chrome 파일 URL 접근 권한에 막혀 사용자 설정 대기. ECR push/ECS 서비스 및 ALB rule/IAM/SG/S3 생성 미실행, 기존 서비스 불변. 서비스 CNAME 조회는 dig 미설치로 미완료.
- 2026-09-28 — TMI-126 사용자가 기존 ALB의 출발지 제한 HTTPS 재사용 및 AI 배포를 승인했다. API/worker 동일 task 구성, 기존 운영 불변, 테스트 전용 IAM/S3/SG/인증서·DNS 및 이미지 전달을 준비 중이다. 배포 성공은 아직 미확인이다.
- 2026-09-28 — TMI-126 ALB 재사용 검토 턴 표식을 WORKLOG EOF에 보완했다. 신규 ALB는 생성하지 않았으며 공유 공개 ALB의 제한적 재사용에 필요한 보안 결정 대기 상태를 유지한다. 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126 ALB 신규 생성 필수 표현 정정: 기존 LC-test는 공유 ALB TG 연결, 기존 AI service는 loadBalancers=[]/serviceConnectConfiguration=null 확인. 공유 공개 ALB 재사용은 기술적으로 가능하지만 AI private 경로와 다르므로 source-IP 제한 등 별도 보안 결정 필요. 신규 ALB 생성/권한 변경 없음.
- 2026-09-28 — TMI-126 AWS inventory 턴 표식을 WORKLOG EOF에 보완했다. 내부 HTTPS ALB 추가 비용 및 AI 전용 S3/IAM/SG 생성 승인 대기이며 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126 사용자 방향별 인증키 등록 완료 보고. AWS describe-secret로 AI Secret ARN/수정시간만 확인(값 미조회). 기존 AI learning ECR 있음, 신규 AI test service/전용 역할/임시 S3 없음. ALB는 internet-facing 한 개, Cloud Map HTTP namespace 있음. 비공개 HTTPS 경로 추가 비용 및 IAM/SG 접근 범위 승인 전 원격 변경/배포 보류. 로컬 이미지 빌드 완료 상태 유지.
- 2026-09-28 — TMI-126 AI 공용 linux/amd64 이미지 app-ai-learning-test:2391a94 로컬 빌드 exit0 완료. AWS push/배포 및 runtime 테스트는 미실행, 방향별 인증값 사용자 등록 대기. 이번 턴 표식 WORKLOG EOF 보완 및 비밀값 미기록.
- 2026-09-28 — TMI-126 사용자 승인으로 AI API/worker 동일 ECS task의 컨테이너2개 구성을 확정했다. provider Secret 이름/키와 원본 Cluster0/to-teacher-app/challenge_10s_questions 위치를 전달받았다(값 미조회). main2391a94 재확인, amd64 공통 이미지 로컬 빌드 진행 중. 서버 간 방향별 credential은 사용자 생성·등록 대기. Redis AWS 주소/격리, private HTTPS, IAM/S3/자원 규모 확정 및 배포·문제 복사는 아직 미완료. 업로드 실효2MiB와 TRACE_CONTENT 미지원/원문 차단을 확인했으며 LC Challenge OFF 유지.
- 2026-09-28 — TMI-126 AI API/worker는 별도 서비스가 필수는 아니며 테스트용 ECS task1개 내 컨테이너2개 배치를 비용 절감 대안으로 설명했다. 자원·IAM role·장애/배포/확장 경계 공유 및 sizing 검증 필요. 아직 구성 확정/원격 변경 없음.
- 2026-09-28 — TMI-126 사용자 지정 app-ai-learning main2391a94를 임시 clone하여 ECS 배포 구성 확인. 동일 이미지의 API8000/worker 분리, LC callback 경로 일치, private HTTPS·OpenAI Secret·양방향 credential 필요. 운영 문제는 문제만 테스트 복사 제안(원본 DB/컬렉션 미확정, 복사 없음). 규모/비용·키 저장 위치 사용자 확인 대기, pytest 미설치로 테스트 미실행. AWS/AI 코드/배포 변경 없이 LC Challenge OFF 유지. 상세 deployment/AI_LEARNING_TEST_PREPARATION.md.
- 2026-09-28 — TMI-126 Challenge 활성화 선행조건 점검 턴 표식을 WORKLOG EOF에 보완했다. AI endpoint·양방향 인증정보 참조 준비 확인 대기이며 Challenge OFF/기존 정상 서비스 유지, 기록 외 변경 없음.
- 2026-09-28 — TMI-126 Challenge 활성화·재배포 요청은 선행조건 미충족으로 미실행. test:3에 AI endpoint·양방향 credential 환경변수/Secret 참조가 없으며 활성화 시 startup validator가 LC 기동을 막는다. 현재 정상 서비스/OFF 유지, AI 주소·Secret 참조 준비 확인 및 challenge DB migration/catalog 준비가 필요하다. 조회·기록만 수행, 비밀값 출력 없음.
- 2026-09-28 — TMI-126 Challenge403 진단: 실행 test:3/RUNNING1의 CHALLENGE_ENABLED=false, task flag override 없음 확인. 배포 commit의 ChallengeController.service() enabled gate가 정상 MEMBER도403 COMMON403으로 거절한다. 사용자 보고로 Identity 재발급/MEMBER/LC audience 검증 완료, 해당 요청 토큰/trace 직접 재현은 미실행. 조회만 수행하고 활성화·배포 변경 없음.
- 2026-09-28 — TMI-126 LC 테스트 서버 주소 https://api-test.to-teacher.com 및 Swagger 경로를 안내했다. 기존 검증 결과 기반 안내이며 이번 원격 재조회·설정 변경 없음.
- 2026-09-28 — TMI-126 Swagger 확인 턴 표식을 WORKLOG EOF에 보완했다. Swagger/OpenAPI200·미인증 일반 API401 확인 상태를 유지하며 기록 외 변경 없음.
- 2026-09-28 — TMI-126 Swagger 허용 요청 확인: 테스트 swagger-ui/index.html200 및 v3/api-docs200, 미인증 일반 API401. 기존 permitAll로 이미 접근 가능해 코드/보안/배포 변경 없이 URL 안내. 실제 API 실행은 JWT 필요.
- 2026-09-28 — TMI-126 정상 기동 턴 표식을 WORKLOG EOF에 보완했다. 테스트 test:3 1대 실행, HTTPS200/UP·미인증401·ALB healthy 검증 완료 상태를 유지한다. 실제 MEMBER·문제 seed·S3/AI E2E는 후속이며 기록 외 변경 없음.
- 2026-09-28 — TMI-126 최종 ALB 확인: healthy 대상1개/이전 대상 draining1개, ECS running1/pending0. LC 테스트 서버 HTTPS health200/UP와 미인증401 검증 완료. 챌린지/AI는 OFF, 데이터 seed와 실제 MEMBER·S3/AI E2E는 미완료.
- 2026-09-28 — TMI-126 사용자 credential 수정 후 DB 권한 오류 해소. 신규 LC DB/빈 exam_sessions·mock_exams 및 필수 unique 인덱스2개 생성(정의는 validator/migration과 대조, 기존 데이터 변경 없음). test:3 재기동 후 1/1/0·rollout COMPLETED, HTTPS200/UP·미인증 API401 확인. ALB 상태 수렴 확인 중, 기존 main:20 1대 유지. 문제 seed/비필수 완료이력 인덱스/실제 MEMBER·S3·AI 검증은 후속. 코드·API 변경 및 Secret 원문 조회 없음.
- 2026-09-28 — TMI-126 LC 접속 문자열 안내 턴 표식을 WORKLOG EOF에 보완했다. 사용자 Secret 저장 완료 대기 및 테스트 서버 중지 상태를 유지한다. 기록 외 변경 없음.
- 2026-09-28 — TMI-126 LC 접속 문자열의 사용자명을 LC 전용 계정으로 바꾸고 해당 계정 비밀번호를 사용하도록 안내했다. Secret 키·호스트·옵션 유지, 특수문자 percent-encoding 주의. 사용자 저장 대기이며 실제 credential 수정·재배포 없음.
- 2026-09-28 — TMI-126 MongoDB 권한 거절 원인 확정: Atlas LC 전용 사용자는 LC DB 전체 readWrite/Cluster0로 정상이나 LC Secret URI는 Identity 전용 사용자를 참조했다. CloudShell에서 메모리 내 파싱으로 사용자명·호스트만 대조, 비밀번호/URI 전체 출력·저장 없음. 사용자에게 LC 전용 credential로 Secret 수정 인계, 권한 확대·credential 변경·재배포는 미실행. 테스트 중지 유지.
- 2026-09-28 — TMI-126 MongoDB 권한 조회 턴 표식을 WORKLOG EOF에 보완했다. Atlas 로그인 인계 대기이며 DB 사용자·역할 확인과 권한 변경은 미실행이다. 테스트 서버 중지 유지, 기록 외 변경 없음.
- 2026-09-28 — TMI-126 MongoDB 권한 진단 요청으로 tosunsaeng-test Database Users에 접근했으나 로그인 만료로 사용자 인계했다. 실제 역할 조회는 미완료, LC test:3 중지 유지. 로그인 후 LC DB 사용자·허용 DB·역할을 확인하며 현재 권한 변경 없음.
- 2026-09-28 — TMI-126 MongoDB JSON 키 선택 수정 턴 표식을 WORKLOG EOF에 보완했다. test:3은 0대로 중지됐으며 URI 형식 오류 해결 후 발견된 listIndexes 권한 거절에 대해 DB 사용자·역할 확인 대기 상태를 유지한다. 기록 외 추가 변경 없음.
- 2026-09-28 — TMI-126 JSON 키 선택 수정 후 종료 확인: test:3 desired/running/pending=0/0/0. DB listIndexes 권한 거절 원인 확인 전 중지 완료.
- 2026-09-28 — TMI-126 사용자 확인(JSON 키 MONGODB_URI)에 따라 test:3과 로컬 template에 `:MONGODB_URI::` 선택자를 적용했다. Secret 원문 미조회·미변경, 동일 이미지 유지. 기동 시 URI 형식 오류는 해결됐으나 AtlasError8000: to-teacher-learning-core-test.exam_sessions listIndexes 권한 거절로 종료. desired0 복귀 요청, URI의 LC 전용 사용자와 해당 DB readWrite 역할 확인 필요. IAM/DB 권한 확대·검증 우회 없음.
- 2026-09-28 — TMI-126 테스트 서비스 생성 턴 표식을 WORKLOG EOF에 보완했다. 인프라·HTTPS·이미지 배포 완료, MongoDB URI 형식 오류로 정상 기동 미완료이며 테스트는 0대로 중지 상태다. Secret 저장 형태/JSON 키 이름 확인 대기를 유지하고 추가 원격 변경은 하지 않았다.
- 2026-09-28 — TMI-126 종료 확인: 테스트 test:2 desired/running/pending=0/0/0, 기존 운영 LC:20=1/1/0. 테스트 재시작 중단 완료, MongoDB Secret 저장 형식 확인 대기.
- 2026-09-28 — TMI-126 승인 후 LC-test SG/TG/로그7일/ECS service 생성, 사용자 DNS 등록 후 api-test 인증서 ISSUED·ALB 연결 완료. develop run 36366557693 attempt2의 테스트·OIDC·image push·test:2 배포 성공, digest 88444cb679d0caf41e1d6e816441a9c2278bb35c762cb0a5eda218a9cd79b41b 확인 후 1대 기동. MongoDB URI 형식 IllegalArgumentException으로 exit1, health 실패하여 desired0 복귀 요청. Secret 원문 미조회로 저장 형식 확인은 사용자 후속 필요. 정상 기동·MEMBER 인증·DB/Redis/S3 앱 연결 검증 미완료. 기존 운영 불변, 세부사항 deployment/LEARNING_CORE_TEST_BOOTSTRAP_STATUS.md 참조.
- 2026-09-28 — TMI-126 테스트 ECS 서비스 사전 확인의 작업 표식을 WORKLOG EOF에 보완했다. 신규 서비스·SG 접근·HTTPS 공개·Fargate 비용 승인 대기를 유지하며 기록 외 원격 변경이나 배포는 없다.
- 2026-09-28 — TMI-126 서비스 구성 사전 실조회: develop b2cd2b6의 Actions run 36366557693은 당시 AWS_TEST_ROLE_ARN 미설정으로 초기 실패했고 이미지 push 전이었다. 테스트 ECS service/SG 및 api-test ACM 인증서 부재 확인. 기존 LC/Identity-test는 동일 private subnet 두 개와 기존 ALB를 사용한다. desiredCount=0의 테스트 service bootstrap→workflow 이미지 push/digest 배포→승인된 1대 기동 방안을 제안하며 신규 SG 접근·비용·HTTPS 공개 범위 승인 대기다. 이번 턴 리소스 생성/배포/재실행 없음.
- 2026-09-28 — TMI-126 사용자 승인 후 tosunsaeng-github-learning-core-test-deploy-role 생성 및 LearningCoreTestDeploy inline policy 부여, GitHub AWS_TEST_ROLE_ARN 등록 완료. ID 포함 exact develop sub/aud trust 재조회와 정책 저장 확인. ECS UpdateService 단일 ARN별 simulation에서 테스트 allowed/기존 운영 implicitDeny 확인(실제 OIDC 배포 성공과는 구분). 기존 main 역할/변수 불변. 최초 테스트 서비스·HTTPS bootstrap과 실제 workflow AssumeRole 검증은 후속이며 이번 턴 배포/재실행 없음.
- 2026-09-28 — TMI-126 운영/테스트 Task Definition 구분 설명의 현재 turn 표식을 WORKLOG EOF에 보완했다. 서비스·family별 revision 선택 설명만 완료했으며 테스트 배포 역할 생성은 승인 대기다. 기록 외 변경 없음.
- 2026-09-28 — TMI-126 사용자 질문에 현재 workflow의 Task Definition 선택 방식을 설명했다. 브랜치별 service/family를 먼저 정하고 해당 service가 참조하는 revision을 조회한 뒤 이미지 digest만 바꾼 새 revision을 등록한다. revision 숫자 자체로 운영/테스트를 구별하지 않으며 현재 AWS revision 재조회나 권한 생성은 하지 않았다. 테스트 배포 역할 생성 승인은 계속 대기다.
- 2026-09-28 — TMI-126 AWS/GitHub 실조회: GitHub OIDC provider와 LC main deploy/test execution/test task 역할 존재, 테스트 deploy 역할은 LC 역할 목록에 없음. GitHub 변수는 AWS_ROLE_ARN만 있고 AWS_TEST_ROLE_ARN은 없음. main trust의 sub는 조직/저장소 ID가 포함된 형식으로 확인되어 배포 문서 수정. 새 tosunsaeng-github-learning-core-test-deploy-role 생성·권한 부여·GitHub 변수 등록은 실행 직전 권한 범위 승인 대기이며 아직 미실행이다. 비밀값 조회/원격 변경 없음.
- 2026-09-28 — TMI-126 다음 배포 작업 안내의 현재 turn 표식을 WORKLOG EOF에 보완했다. 다음 단계는 테스트 전용 GitHub OIDC 배포 역할 및 AWS_TEST_ROLE_ARN 준비이며 실제 설정/배포는 미실행이다. 기록 외 변경 없음.
- 2026-09-28 — TMI-126 후속 다음 작업 안내: 테스트 배포 전용 OIDC 역할/GitHub AWS_TEST_ROLE_ARN부터 준비하고, 최신 LC 이미지 최초 push·테스트 ECS/SG/로그·HTTPS bootstrap 후 develop 자동 배포를 검증한다. 현재 workflow는 기존 서비스 갱신용이므로 service 미존재 상태에서는 최초 생성하지 않는다. 문서 기반 안내만 수행, 원격 상태 재확인/변경 없음.
- 2026-09-28 — TMI-126 후속 사용자 승인으로 deploy-staging.yml에 main/develop 분기를 구현했다. main 기존 service/health/AWS_ROLE_ARN/staging 태그 유지, develop은 test service/health/AWS_TEST_ROLE_ARN/test-SHA·test 태그·별도 concurrency 사용. 다른 ref 및 테스트 역할 미설정/운영 역할 재사용은 거절한다. 현재 task family/container 검증 후 image digest만 교체하며 초기 service 없으면 중단한다. GitHub/IAM/최초 테스트 인프라 설정은 별도 필요, 원격 실행·commit/push 없음. routing 10 tests, clean test 543개, YAML/inline bash/bash -n/diff 검사 통과. 전체 Mongo integration은 이번 변경에서 로컬 미실행(기존 CI gate 유지). 사용자 기존 변경 보존.
- 2026-09-26 — Atlas 읽기 전용 집계: 앱 출시 이후 누적 계정 223개, 앱 완료 410건, 웹 완료 284건, 통합 694건. 추석 9/24~26 현재까지 신규 앱 계정 33개·앱 완료 64건·웹 완료 11건. 반복 응시 포함, 오늘 부분일, 계정 수와 실인원 구분 및 웹 시험일 귀속 한계를 명시했다. DB·코드·인프라 변경 없음.

- 2026-09-24 — 사용자에게 Azure raw 로컬 백업 디렉터리의 절대 경로를 전달했다. 기존 백업·검증·삭제 완료 상태는 유지되며 파일·DB·코드 변경은 없다.
- 2026-09-24 — 어제까지의 Azure raw 결과 4,627건 로컬 백업·검증·DB 삭제 완료 상태를 hook의 현재 turn 표식으로 WORKLOG EOF에 보완했다. 백업과 DB의 기존 완료 상태는 유지되며 추가 데이터·코드·인프라 변경은 없다.
- 2026-09-24 — 별도 Jira 없이 사용자 승인에 따라 Azure raw 신규 저장은 유지하고, 한국시간 2026-09-23 23:59:59까지의 `azure_results` 4,627건을 로컬 Documents의 접근 제한 디렉터리에 Canonical Extended JSON Lines로 백업했다. 문서/ID 파일 SHA-256, 4,627개 고유 ID, 전체 EJSON 파싱을 오프라인 검증한 뒤 백업 ID와 DB 대상이 정확히 일치할 때만 `deleteMany`를 실행했다. 삭제 4,627건·대상 잔존 0건, 오늘 데이터 126건 보존을 확인했다. Azure collection 논리 크기는 약 196MB에서 5MB, 해당 DB 전체 논리 dataSize는 약 237MB에서 44MB로 감소했다. Atlas 클러스터 카드의 441.40MB/512MB 표시는 삭제 후 30초 재조회에도 아직 갱신되지 않아 metric 지연 또는 WiredTiger 할당 공간 영향으로 기록하며, collection storageSize 약 39MB는 재사용 가능 공간을 포함한다. 백업에는 발화·인식 데이터가 포함될 수 있어 Git 밖에 권한 600으로 보관하며 DB 삭제분은 해당 백업으로만 복구 가능하다. 앱 코드·Callback 계약·신규 저장 동작은 변경하지 않았다.
- 2026-09-24 — 별도 Jira 없이 Azure raw 결과 정리 판단을 보완했다. 현재 실데이터 키 불일치로 앱 응답의 `azureFeedback`이 사실상 null/미포함이므로 `azure_results`를 외부 archive로 복사하는 작업은 앱 동작에 영향을 주지 않는다. 검증된 archive 후 DB 제거도 현재 앱 영향은 낮지만, raw 자료의 장애 분석·재처리·감사 용도 상실 가능성과 계속되는 Callback 신규 저장을 고려해 count·checksum·격리 restore 검증, 대표 API/Callback 확인 및 삭제 직전 최종 승인을 gate로 유지한다. archive·삭제·코드 변경은 아직 미실행이다.
- 2026-09-24 — 별도 Jira 없이 Atlas Free 클러스터를 읽기 전용으로 확인했다. UI 사용량은 441.40MB/512MB(약 86%, 잔여 약 70.6MB)이며 `to-teacher-app.azure_results`는 약 4,753건·논리 크기 196.34MB다. 최근 7일 48.42MB, 30일 190.99MB 증가 기준 Azure 데이터만으로 약 10.2~11.1일 뒤 한도 도달 가능성이 있어 다른 collection 증가를 고려하면 7~10일 내 조치가 안전하다. 실데이터 4,753건은 모두 `raw_data.azure_result`이고 변환 코드가 찾는 `raw_data.azure_speech_result`는 0건이어서 현재 저장 데이터의 `azureFeedback`은 사실상 null/미포함이다. `azure_result`가 collection의 약 194.51MB를 차지하므로 검증된 archive 후 제거 시 클러스터 사용량은 단순 추정 약 245.1MB(47.9%)까지 감소할 수 있다. 삭제·archive·코드 변경은 미실행이며, 신규 raw 저장 중단/보존 정책 구현과 저장소 밖 암호화 archive·checksum·복원 검증 뒤 삭제 직전 사용자 최종 승인이 필요하다.
- 2026-09-23 — TMI-126 배포 잔여 작업 설명의 현재 turn 표식을 WORKLOG EOF에 보완했다. 다음 단계는 LC 이미지 ECR 업로드/digest 확정과 인증서/DNS 준비이며 실제 배포·연결 검증은 미완료다. 기록 외 변경 없음.
- 2026-09-23 — TMI-126 배포 잔여 작업을 인계서/현재 ECS 초안 기준으로 정리했다. 다음은 LC 이미지 ECR push/digest 확정과 api-test 인증서/DNS 준비이며, 사양·비용 승인, subnet/NAT·전용 SG·로그/target group, ECS 등록/기동, Mongo·Redis DB2·S3·MEMBER 인증 실검증이 남는다. AI/Challenge OFF 범위 유지, 잔여 임시 SG 삭제는 별도 후속이며 기동 선행조건은 아니다. 이번 턴은 상태 설명/기록만, AWS 변경·새 테스트 없음.
- 2026-09-23 — TMI-126 임시 CloudShell 접근 정리 작업의 현재 turn 표식을 WORKLOG EOF에 보완했다. 환경/캐시측 SG 삭제 완료, 남은 CloudShell측 SG는 빈 규칙 상태로 관리 ENI 해제 대기다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 사용자 삭제 승인 후 lc-test-redis-check CloudShell 환경을 삭제하고 임시 SG 상호 TCP6379 규칙을 제거했다. 캐시측 sg-0f42223f236bb40ee는 분리/삭제 성공, 캐시는 available 및 원래 sg-0f8528393d749b3a9만 active 확인. CloudShell측 sg-0f83bb7d8c5b6b124는 관리 ENI eni-0aa843cd598fd3f0d가 in-use로 남아 DeleteSecurityGroup DependencyViolation, 최종 조회 inbound/outbound 모두 빈 상태다. 관리 ENI 강제 삭제는 하지 않았다. AWS 연결 해제 후 남은 SG 삭제 필요. Redis 데이터/기존 서비스/배포 초안 불변, 문서만 변경하여 Gradle 생략·whitespace 검증.
- 2026-09-23 — TMI-126 DB2 연결 점검 작업의 현재 turn 표식을 WORKLOG EOF에 보완했다. DB2 PONG/키0개 확인 완료, LC task 연결은 미배포로 후속이며 임시 CloudShell 환경/SG 삭제는 최종 승인 대기다. 기록 보완 외 변경 없음.
- 2026-09-23 — TMI-126 VPC CloudShell에서 valkey-cli -n 2 PING=PONG, DBSIZE=0을 확인했다(키/값 읽기·쓰기 없음). ECS LC-test service는 MISSING이므로 실제 LC task 경로 연결 검증은 아직 불가하다. 임시 CloudShell SG sg-0f83bb7d8c5b6b124는 CloudShell ENI, 캐시 SG sg-0f42223f236bb40ee는 캐시 ENI에 연결 중이며 두 SG는 상호 TCP6379 경로만 구성한다. 캐시 원래 SG sg-0f8528393d749b3a9는 유지 중이다. 임시 환경/SG 제거는 대상 명시 후 최종 승인 대기, 아직 외부 변경 없음. 문서만 변경해 Gradle 생략, whitespace 검증.
- 2026-09-23 — TMI-126 사용자 승인으로 LC-test 배포 JSON의 기존 Redis endpoint/6379와 SPRING_DATA_REDIS_DATABASE=2 반영 완료(계획상 배정, 실제 ECS 배포 아님). Identity :23/test :3의 Redis 환경/secret 이름과 env file 없음 추가 확인. 확인한 ECS 설정에서 DB2 충돌은 없으나 숨은 runtime/외부 consumer 검증은 제한적이다. 배포 전 실제 DB2 선택·SG 연결 검증 필요. 신규 캐시/기존 서비스 변경 없음, 임시 SG 정리 미실행. JSON/whitespace 검증, runtime 코드 불변으로 Gradle 생략.
- 2026-09-23 — TMI-126 일반 CloudShell에서 현재 ECS 정의 대조: LC :20은 Redis HOST/PORT만 있고 DB override·Redis secret·env file은 없다(기본 DB0 예상, 이미지 내부/런타임 override 미검증). AI :13의 API와 worker1~4는 REDIS_URL 경로 /1이다. VPC 셸 ECS 조회는 connect timeout, 후속 메모리 조회는 성공하여 21.03MiB/384MiB를 확인했다. DB2를 LC-test 후보로 권장하되 전체 예약·런타임 검증 전 확정 배정하지 않았다. template/클라우드 변경 없음, 점검 SG 정리 후속. 문서만 변경해 Gradle 미실행, whitespace 검사.
- 2026-09-23 — TMI-126 CloudShell 캐시 메타데이터 조회 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. db1 키 개수 및 메모리 한도 확인 완료, 기존 consumer DB 설정 대조와 테스트 DB 번호 확정은 미완료다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 사용자 CloudShell 실행 완료 후 lc-test-redis-check의 INFO keyspace 성공을 확인했다. 조회 시 db1만 keys=1127/expires=1127로 표시됐고 INFO memory의 maxmemory=384MiB, volatile-lru를 확인했다. 이는 다른 DB 미사용/미예약 증거가 아니므로 DB2는 후보일 뿐 배정/환경 반영하지 않았다. VPC CloudShell의 ECS describe-services는 응답이 없어 consumer 설정 대조가 미완료이며 중단 시도 후 프롬프트 복귀도 확정하지 못했다. 추가 캐시 write/권한 변경 없음, 접속용 SG 정리는 남는다.
- 2026-09-23 — TMI-126 승인된 CloudShell 접근 구성·입력 handoff의 현재 turn 표식을 WORKLOG 끝에 보완했다. 접속용 SG 구성은 완료됐으나 새 VPC 환경 생성/INFO keyspace 실행은 사용자 버튼 클릭 대기이며 DB 번호는 미확정이다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 사용자 승인 후 AWS 캐시→CloudShell 연결을 제출했다. 콘솔은 새 SG 2개 생성/규칙 설정/캐시 연결 성공100%를 보고했다: CloudShell sg-0f83bb7d8c5b6b124, 캐시 sg-0f42223f236bb40ee. 마지막 캐시 표시는 Modifying이며 기존 SG 자체 수정 없이 새 SG 연결이 수행됐다. CloudShell 새 VPC 환경 폼 lc-test-redis-check 및 INFO keyspace 명령을 입력했으나 iframe 클릭/키보드 오류가 지속되어 생성 및 실행은 미제출이다. 사용자 버튼 클릭 handoff가 필요하며 실제 DB/메모리 조회·DB 번호 배정은 미완료. 점검 후 신규 접근 정리 필요성을 인계한다.
- 2026-09-23 — TMI-126 Valkey 부하 확인·CloudShell 접속 승인 대기 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. 새 SG/6379 접근 추가는 아직 미실행이며 DB 번호도 미확정이다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 기존 캐시 재사용 후속: CloudWatch 확대 그래프에서 최근3시간/5분 평균 DB 메모리 약5.51~5.60%, 엔진 CPU 약0.70~0.73%를 확인했다(장기 peak/테스트 부하 보장은 아님). DB 번호 확인을 위한 CloudShell 연결 모달은 새 SG 생성과 TCP6379 접근 규칙 추가를 안내하므로 최종 연결을 누르지 않고 권한 확대 승인을 요청한다. 기존 SG 미수정 안내를 확인했으나 실제 변경은 아직 없다. DB 번호/consumer 점유·실연결은 미확정이며 Redis template placeholder 유지.
- 2026-09-23 — TMI-126 기존 Valkey 구성 실조회 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. 논리 DB 분리 지원은 확인했으나 미사용 번호·실제 부하 검증은 미완료이며 AWS 설정 변경은 없다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 기존 Valkey 재사용 읽기 전용 확인: tosunsaeng-staging-redis는 cluster mode disabled, 단일 cache.t4g.micro, default.valkey9의 databases=16이다. VPC는 LC 예정 VPC와 동일하며 캐시 SG의 TCP6379 inbound는 AI/기존 LC SG 2개로 한정된다. 전송 암호화 OFF·저장 암호화 ON, 사용자 그룹 없음, Multi-AZ/자동 failover OFF다. 미사용 DB 번호·실제 용량/부하는 미검증(CloudWatch 그래프 로딩 지속), DB 분리는 보안/자원 격리가 아니다. 신규 생성/기존 설정/배포 초안 접속값 변경 없이 조건부 재사용 판단만 기록한다.
- 2026-09-23 — TMI-126 기존 Valkey 재사용 검토의 현재 turn 표식을 WORKLOG 끝에 보완했다. 신규 캐시는 생성하지 않았으며 DB 번호 분리 지원·미사용 번호·용량 확인 전 연결 설정을 변경하지 않는다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 사용자 비용 우려에 기존 staging Valkey 재사용 가능성을 검토했다. 신규 캐시는 아직 생성하지 않았다. LC는 Spring Redis 자동 설정을 사용하고 별도 connection factory/flushAll/flushDb 구현은 검색되지 않았다. cluster mode disabled 및 미사용 DB 번호 검증 후 SPRING_DATA_REDIS_DATABASE로 논리 DB 분리가 가능하나 부하/장애/관리 명령은 공유된다. 실제 엔진 설정·사용 중 DB·용량·TLS/ACL·접속 권한은 미검증이므로 연결 변경 없이 조건부 권장안만 설명한다. IAM 역할 자체는 별도 사용료가 없다는 점도 구분한다.
- 2026-09-23 — TMI-126 IAM 역할 2개 생성 완료 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. 역할·인라인 정책 연결 및 배포 초안 ARN 반영 완료 상태를 유지하며 실제 ECS 연결 검증은 후속이다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 사용자 승인 후 tosunsaeng-learning-core-test-execution-role 및 tosunsaeng-learning-core-test-task-role을 생성했다. 콘솔 성공 알림, 각각 ARN 및 인라인 정책 LearningCoreTestExecution/LearningCoreTestAudio 1개 연결을 확인했다. 계정/서울 ECS 제한 trust와 승인된 ECR/로그/LC Mongo Secret·테스트 S3 Get/temp Put만 적용했다. 기존 역할 불변. ECS 초안 ARN과 IAM 초안 상태를 갱신했다. 편집기 JSON 입력 오류는 저장 전에 수정했으며 실제 ECS assume/DB/S3 접근 검증은 미실행이다. Redis·로그 그룹·이미지 push·TLS·배포가 남는다.
- 2026-09-23 — TMI-126 IAM 설명 및 Identity-test 실행 역할 확인 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. LC 역할 생성/권한 부여는 여전히 승인 대기이며 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 IAM 필요성 질문에 Identity-test:3 ECS 설정을 읽기 전용 확인했다. 실행 역할 tosunsaeng-identity-test-execution-role이 연결되어 있고 task 역할은 없다. 역할 최초 생성 시점/주체와 세부 policy는 이번 조회로 확정하지 않는다. LC는 기동용 실행 역할 외 S3 호출용 task 역할이 필요함을 설명한다. AWS 변경·비밀값 조회 없음, 코드 테스트는 설명/기록만으로 생략한다.
- 2026-09-23 — TMI-126 IAM 최소권한 초안·Valkey inventory 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. AWS 역할 생성/권한 부여는 사용자 승인 대기이며 기존 staging 캐시는 변경하지 않았다. 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 IAM learning 검색은 기존 deploy/task 역할 2개만 반환했다. 테스트 전용 역할 생성 화면 및 로컬 learning-core-test.iam-proposal.json을 준비했으나 AWS 권한 부여는 승인 대기다. ECS trust는 계정/서울로 제한, 실행 역할은 LC ECR pull·전용 로그 stream·LC Mongo Secret 한 개, task 역할은 테스트 버킷 Get와 temp/* Put만 제안한다. 서울 Valkey 목록에서 기존 tosunsaeng-staging-redis Available/cache.t4g.micro를 발견했다. Redis OSS 0개는 전체 Redis 부재가 아니며 기존 캐시 재사용/수정은 하지 않았다. 독립 테스트 Redis 비용/구성은 미확정이다.
- 2026-09-23 — TMI-126 S3 생성 완료 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. tosunsaeng-test-audio 생성 및 ECS 초안 반영 완료 상태를 유지한다. 실제 LC 접근권한/연결·Redis·배포는 후속이며 기록 외 추가 변경 없음.
- 2026-09-23 — TMI-126 사용자 생성 승인 후 서울 S3 tosunsaeng-test-audio 생성 성공 메시지와 객체 0개를 확인했다. ACL 비활성/퍼블릭 전체 차단/SSE-S3/versioning 비활성으로 생성했고 별도 lifecycle·권한은 추가하지 않았다. ECS 초안 AWS_S3_BUCKET_NAME에 반영했으며 실제 LC 연결/배포는 미실행이다. IAM 최소권한·Redis·이미지 push·TLS 준비가 남는다. 문서/JSON 검사만 수행, runtime 코드 테스트는 변경 없어 생략한다.
- 2026-09-23 — TMI-126 S3 이름 단순화 작업의 현재 turn 표식을 WORKLOG 끝에 보완했다. tosunsaeng-test-audio 생성 폼 입력만 완료했으며 생성/이름 가용성 검증은 승인 대기다. 기록 보완 외 변경 없음.
- 2026-09-23 — TMI-126 사용자 제안에 따라 S3 생성 폼 이름을 tosunsaeng-test-audio로 변경했다. 계정 ID 접미사는 전역 이름 충돌 완화용이며 필수는 아님을 설명했다. 생성 제출 전이므로 이름 가용성은 미확인, 기존 보안 설정/리소스 불변이며 생성 승인을 기다린다. 코드 변경 없이 문서 검사만 수행한다.
- 2026-09-23 — TMI-126 LC 다음 인프라 준비: S3 목록에 LC 테스트 전용 버킷이 없고 서울 ElastiCache Redis OSS 목록은 0개임을 확인했다(Valkey/자체 Redis는 미확인). 서울 S3 생성 폼에 tosunsaeng-learning-core-test-audio-889384901776을 입력하고 ACL OFF·퍼블릭 전체 차단·SSE-S3 기본 설정으로 생성 승인 대기한다. 아직 생성 제출·IAM/네트워크 변경·비용 리소스 배포는 없으며 ECS 초안의 버킷 placeholder도 유지한다. Redis 구성/비용과 IAM 최소권한은 후속 확인한다.
- 2026-09-23 — TMI-126 LC Mongo Secret 확인·ECS 참조 반영의 현재 turn 표식을 WORKLOG EOF에 보완했다. 실제 DB 연결 및 실행 권한·인프라·배포 검증은 후속으로 유지한다. 기록 보완 외 변경 없음.
- 2026-09-23 — TMI-126 사용자 완료 통보 후 AWS 목록 새로고침/상세 metadata에서 tosunsaeng/test/learning-core/mongodb 생성과 ARN을 확인하여 ECS 초안의 MONGODB_URI secret 참조에 반영했다. 비밀값은 열지 않았고 URI 형식/인증/DB 연결은 미검증이다. 실행 역할 read 권한·NAT·Redis/S3·image push·TLS 및 배포가 후속이다. 외부 변경 없이 인계서/JSON/상태/로그만 갱신했다.
- 2026-09-23 — TMI-126 비밀번호 분리 안내의 현재 turn 표식을 WORKLOG EOF에 보완했다. Identity 설정 유지·LC 전용 비밀번호 권장과 사용자 직접 입력 원칙을 유지하며, 기록 외 변경 없음.
- 2026-09-23 — TMI-126 사용자에게 동일 비밀번호여도 MongoDB 사용자명별 인증/권한은 구분되지만 credential 재사용 위험이 있어 LC 전용 비밀번호를 권장한다고 안내했다. 기존 Identity 비밀번호는 유지하고 필요 시 사용자가 LC 계정만 변경한 뒤 LC Secret URI에 반영해야 한다. 실제 비밀번호 재사용 여부는 미확인, 비밀 열람/변경·외부 설정 변경 없음.
- 2026-09-23 — TMI-126 사용자에게 MongoDB 접속 URI 확인 경로(tosunsaeng-test → Cluster0 → Connect → Drivers)를 안내했다. LC 사용자명/비밀번호 치환 및 Secret 일반 텍스트 저장 단계는 사용자 진행 대기다. 비밀값 열람 없이 안내와 기록만 수행했고 DB 연결/Secret 저장 성공을 주장하지 않는다.
- 2026-09-23 — TMI-126 LC 계정 확인·Secret 입력 인계의 현재 turn 표식을 WORKLOG EOF에 보완했다. 사용자 Secret 저장 완료 대기이며 DB 초기화/연결 검증은 후속이다. 기록 보완 외 설정·코드 변경 없음.
- 2026-09-23 — TMI-126 사용자의 생성 완료 후 Atlas 목록에서 to-teacher-learning-core-test 계정/readWrite@동명DB/클러스터1개 제한을 확인했다. Identity 계정은 별도로 유지된다. ECS 초안의 DB 이름을 해당 이름으로 확정했으나 실제 DB collection 초기화/접속은 아직 미검증이다. AWS 새 Secret의 다른 유형/일반 텍스트 입력 화면을 열어 사용자 handoff했다. 목표 이름 tosunsaeng/test/learning-core/mongodb, 값은 LC 계정의 Atlas URI 단일 문자열이며 비밀 입력/저장은 사용자 진행 대기다. ARN 미확정 유지, 외부 생성 제출/권한 변경 없음.
- 2026-09-23 — TMI-126 사용자 LC DB/계정/secret 생성 요청에 따라 tosunsaeng-test Atlas 새 사용자 폼을 준비했다. 사용자명·대상 DB는 to-teacher-learning-core-test, Specific Privileges readWrite(해당 DB 전체 collection), Cluster0만 접근하도록 미저장 폼에 설정했다. 비밀번호는 비워 두고 입력·생성 제출은 사용자 handoff 대기다. 아직 계정/DB/Secret 생성 완료가 아니며 기존 Identity/IP/권한은 변경하지 않았다. 비밀값 열람/기록 없음. 사용자 완료 후 계정 metadata 검증과 별도 AWS secret 저장이 남는다.
- 2026-09-23 — TMI-126 tosunsaeng-test 재확인의 현재 turn 표식을 WORKLOG EOF에 보완했다. Identity DB 전용 권한·NAT IP 제한 확인과 LC 별도 계정 권장안을 유지하며, 기록 외 설정/코드 변경은 없다.
- 2026-09-23 — TMI-126 Atlas 대상 정정: 사용자 지정 tosunsaeng-test/Cluster0에서 재조회했다. DB 계정 to-teacher-test는 readWrite@to-teacher-identity-test만 있고 IP는 AWS staging NAT - test services 설명의 /32 하나만 Active이며 현재 사용자 IP는 미허용이다. 용량은0B/512MB 표시다. 이전 Project0의 전체IP/관리자/84%/DB목록 관찰은 테스트 판단 근거에서 제외한다. LC 전용 DB/계정/secret과 실제 ECS outbound NAT 대조가 필요하며 Identity 권한·IP·secret은 변경하지 않았다. 읽기 전용 확인과 문서 갱신만 수행.
- 2026-09-23 — TMI-126 Atlas 읽기 전용 확인의 현재 turn 표식을 WORKLOG EOF에 추가했다. LC 테스트 DB/최소권한 계정 확정과 실제 연결 검증은 후속으로 유지한다. 기록 보완 외 설정·코드 변경 없음.
- 2026-09-23 — TMI-126 Atlas 로그인 후 Project0/Cluster0 읽기 전용 점검: IP 0.0.0.0/0 Active, DB 계정1개 atlasAdmin/All Resources로 좁은 IP/DB 권한 제한 상태는 아니다. 단 AWS secret 계정과의 동일성 및 ECS 연결은 미검증이다. DB 목록에 명시적인 LC 테스트 DB는 없고 기존 app/identity 등만 보인다(첫 write 전 DB·다른 project 가능성 유지). 용량430.09/512MB(84%). collection 문서·비밀값 조회나 설정 변경 없이 인계서와 로그만 갱신했다. 테스트 DB/전용 최소권한 계정 확인이 다음 단계다.
- 2026-09-23 — TMI-126 사용자가 MongoDB 접속 제한 확인을 위해 직접 로그인하겠다고 요청하여 인앱 MongoDB Atlas 로그인 화면을 열고 유지했다. 현재 제한 여부는 미확인이며 Secret 이름만으로 네트워크/DB 계정 권한을 추정하지 않는다. 로그인 후 테스트 cluster의 Network Access·DB 계정 범위·LC DB 구분을 읽기 전용 확인할 예정이다. 로그인 입력과 권한/허용 IP 변경은 하지 않았다.
- 2026-09-23 — TMI-126 MongoDB 후보 메타데이터 확인 작업의 현재 turn 표식을 WORKLOG EOF에 보완했다. LC DB 이름·권한은 사용자 확인 대기이며 연결/배포 및 비밀값 열람은 하지 않았다. 기록 보완 외 변경 없음.
- 2026-09-23 — TMI-126 Secrets Manager 이름/설명 목록을 읽기 전용 확인했다. mongodb 명칭은 tosunsaeng/test/identity/mongodb 하나이며 설명은 없다. 사용자 지칭 후보는 찾았으나 LC 테스트 DB/권한 용도는 미확인이라 task 참조를 연결하지 않았다. 값/URI/비밀번호 열람·AWS 변경 없음. 같은 cluster 재사용과 Identity DB/계정 공유를 구분하여 사용자 확인이 필요하다. 문서만 갱신, 코드 테스트 미실행.
- 2026-09-23 — TMI-126 후속: 사용자가 테스트 DB 준비 완료를 알렸다. DB 신규 생성은 진행하지 않고 DB 이름·테스트 전용 권한/secret 참조·실연결 확인을 기다린다. Redis/S3 준비 여부는 미확인이다. 로컬 LC 이미지/등록 초안 준비 완료, AWS 배포 미실행 상태는 유지한다.
- 2026-09-23 — TMI-126 LC 단독 준비 실행: develop16eb5de로 clean test bootJar 성공(543 tests/실패0), 로컬 learning-core-test:16eb5de Docker build 성공 및 linux/amd64·user=app 확인. docs/codex/deployment/learning-core-test.task-definition.template.json에 테스트 JWT/Challenge OFF·운영 AI 오호출 방지 loopback·별도 secret/data 참조를 준비하고 JSON/설정 assertion 통과. 0.5vCPU/1GiB·task1은 승인 전 제안이다. 콘솔에서 LC staging 이미지는 8/25 afa686c이며 현재 develop과 다름, Identity-test 인증서는 identity-test 단일 도메인임을 확인했다. 테스트 데이터 리소스 준비 여부를 질문했다. AWS 생성/등록/push/배포/DNS/IAM/SG/secret 변경 없음. 기존 API/runtime/workflow 불변, 기존 dirty 파일 보존. 인증서·테스트 DB/Redis/S3·권한/사양 확인과 ECR push 경로가 남는다.
- 2026-09-23 — TMI-126 LC 단독 준비 결정의 현재 turn 표식을 WORKLOG EOF에 추가했다. api-test.to-teacher.com, Challenge OFF, AI 준비 보류 결정과 인계서 내용은 유지한다. 기록 보완 외 코드·AWS·배포 변경 없음.
- 2026-09-23 — TMI-126 사용자 결정: LC 테스트 주소 api-test.to-teacher.com 확정, AI 이미지 미준비·키 미수령으로 LC 단독 기동 준비만 진행하고 AI build/배포/연동은 보류한다. CHALLENGE_ENABLED=false의 AI 설정 검증 skip과 secret 없는 LC Docker build 구조를 코드로 확인하고 인계서에 LC 환경값·별도 DB/Redis/S3·8080/health·DNS/TLS 준비 범위를 정리했다. 실제 DNS/인증서/이미지/리소스 생성·배포는 하지 않았고 코드/API/workflow도 불변이다. Challenge OFF는 LC 기본 인프라 의존성을 없애지 않으며 Day1/채점 E2E/기존 리뷰2건은 후속이다. 문서만 변경해 Gradle 테스트 미실행, diff whitespace 검사 수행.
- 2026-09-23 — TMI-126: 사용자 로그인 후 인앱 AWS 콘솔 읽기 전용 inventory 완료 범위를 인계서에 반영했다. 서울 기존 cluster 서비스4개 중 Identity-test는 task :3/running1/healthy1이며 기존 ALB 연결·VPC/subnet/SG/public IP OFF를 확인했다. LC-test/Challenge AI API·worker 서비스는 해당 cluster에 없다. ECR ai-learning-worker는 8/31 이미지7개로 현재 Challenge 승인 commit과의 대응은 미확인이다. 콘솔 접근 blocker는 해소됐으나 CLI는 미인증이다. 테스트 도메인·AI 이미지·DB/catalog·Redis/S3·사양/비용 확인 후 생성 단계로 진행한다. AWS/secret/코드/배포 변경 없음, 실제 MEMBER/모바일 E2E 및 기존 리뷰2건 보완은 미완료. 문서3개만 갱신하고 코드 테스트는 미실행이다.
- 2026-09-23 — 사용자 요청으로 https://isb.etslearning.co.kr/ 를 인앱 브라우저에 열고 다음 턴에도 유지하도록 표시했다. Innovation Sandbox on AWS 홈이 보이며 사용자의 로그인/접근 준비 완료 안내를 기다린다. lease 요청·계정 선택·AWS 리소스 변경은 하지 않았다. 관련 TMI-126, 신규 Jira 없음. Chrome 미승인 접근을 재시도하지 않고 사용자가 새로 지정한 사이트를 열었다.
- 2026-09-23 — TMI-126 후속 인계: 사용자는 로그인된 AWS 콘솔 경로를 선택했으나 컴퓨터 사용 도구의 Google Chrome 접근이 승인되지 않아 inventory를 진행하지 못했다. 우회 접근/외부 변경은 하지 않았다. Identity 담당 전달값은 issuer https://identity-test.to-teacher.com(끝 slash 없음), JWKS /.well-known/jwks.json, 공개 kid tosunsaeng-identity-test-rsa-1/RS256, commit 88ff5bedc1ee661ccd38a8a1d2c4dbf3b9f03f2d, task tosunsaeng-identity-test:3이다. 담당자는 health/JWKS/transaction 로그와 자동 테스트958개를 확인했으나 실제 가입/로그인/재발급 MEMBER token 검증은 미완료라고 명시했다. 인계서에 설정값과 검증 한계를 반영했고 환경 주입/리소스 생성은 미실행이다. 다음 blocker는 Chrome 접근 승인 또는 별도 승인된 AWS 접근 확보다. 신규 Jira 없음.
- 2026-09-23 — 사용자가 TMI-126 관련 LC 테스트 서버·Challenge AI API/worker·서버 간 연결 준비를 요청했다. AWS CLI는 설치돼 있지만 sts get-caller-identity가 NoCredentials로 실패하고 configure list-profiles도 비어 있어 계정/리소스 inventory를 진행하지 못했다. AWS SSO/CLI 로그인 후 profile명 또는 로그인된 콘솔 이용 여부를 질문했다. 비밀값 직접 공유는 요청하지 않았다. CHALLENGE_TEST_DEPLOYMENT_HANDOFF에 Identity 배포 통보와 접근 blocker/실행 순서를 갱신했다. 계정·대상·네트워크·비용 확인 전 실제 리소스 생성·secret 발급/조회·DB 작업·이미지 배포·workflow 변경은 하지 않았다. 기존 코드 리뷰 2건도 미수정이며 활성화 전 검증 과제로 유지한다. 신규 Jira 없음, 관련 기존 TMI-126.
- 2026-09-23 — TMI-126 테스트 오픈 준비: 사용자가 Identity 테스트 서버를 https://identity-test.to-teacher.com 에 배포했다고 알렸다. 이는 사용자 제공 배포 상태이며 이번 작업에서 HTTPS/JWKS/실제 MEMBER 발급을 직접 검증하지 않았다. 다음 인계는 정확한 issuer·JWKS URL·공개 kid/배포 commit, aud에 tosunsaeng-learning-core 포함·account_type=MEMBER·UUID sub 및 로그인/재발급 검증 결과다. URL만으로 issuer/JWKS 경로를 확정하지 않는다. LC/AI 테스트 주소·서비스/DB/S3/Redis·방향별 secret 참조·catalog/index·KST Day1과 실제 E2E 준비가 남는다. 이전 리뷰의 S3 body 전체 timeout과 순차 dispatch의 Callback timeout 지연 2건은 아직 수정하지 않았다. 학습 기록 삭제는 계획 유지. 신규 Jira 없음(기존 TMI-126), 코드/환경/배포 변경 없이 상태와 작업 기록만 갱신했다.
- 2026-09-22 — 사용자 요청으로 로컬 develop@16eb5de의 Challenge 테스트 오픈 관련 코드(인증·제출·채점·S3·scheduler·계약)를 리뷰했다. 로컬 origin/develop도 동일하나 원격 fetch는 하지 않았다. TMI-126 관련 수정 필요 2건을 임시 로컬 진단 테스트로 확인했다: [P1] ChallengeAudioStorage.read의 apiCallTimeout 10초가 반환 stream의 readNBytes 전체 시간을 제한하지 않아 다운로드 정체가 dispatch tick을 붙잡을 수 있음(실제 SDK+로컬 HTTP에서 11초 초과 후에도 성공 읽기 재현); [P2] worker.tick의 최대20개 순차 dispatch와 같은 tick의 Callback timeout 처리로 14초 접수×20개에서 첫 Job deadline 134초가 지났는데 280초까지 WAITING_CALLBACK 유지(모의 시계 재현). 전체 기존 단위 테스트 543개는 재실행 통과했고 임시 재현 테스트 2개도 문제 존재를 확인했다. 코드·배포 설정은 수정하지 않고 CURRENT_STATE/WORKLOG만 갱신했다. 실제 Identity/AI/AWS E2E는 미검증이며 테스트 오픈 전 두 경로 보완을 권장한다. 신규 Jira 없음, 관련 기존 이력 TMI-126.
- 2026-09-22 — TMI-126 관련 별도 테스트 서버 오픈 준비로 전환했다. 사용자는 별도 테스트 환경을 명시했고 Identity는 인증서 준비 대기 중으로 아직 배포되지 않았다고 확인했다. 학습 기록 삭제/일별 집계는 계획으로만 유지한다. 로컬 develop@16eb5de의 인증·Challenge·migration·배포 workflow를 확인하고 CHALLENGE_TEST_DEPLOYMENT_HANDOFF에 환경/연결값·미확인 사항·Day1·기존 서비스 보호 경계를 갱신했다. 전체 단위 543개, 초기 선택 테스트 104개, migration Node 7개 통과. Challenge Mongo 통합은 최초 Docker API 1.32 호환 실패 후 기존 문서의 테스트 프로세스 한정 JAVA_TOOL_OPTIONS=-Dapi.version=1.44로 29개 통과했다. 실제 Identity/JWKS·AI·AWS·모바일 E2E는 미실행이다. 기존 deploy-staging workflow는 현재 LC service 갱신용이므로 신규 테스트 배포에 사용하지 않는다. 실제 리소스·주소·사양/비용·활성 KST 날짜·secret 참조와 Identity 배포 인계가 필요하다. 신규 Jira key 없음(TMI-126은 기존 구현 이력). runtime/API/의존성·배포 workflow·AWS 변경 없이 문서 3개만 갱신했다.
- 2026-09-22 — 보완된 삭제 계획을 사용자 관점으로 다시 설명했다. 삭제 접수/즉시 old hide·초기 차단 → 안전 확인 → 새 학습 허용과 old target 정리 → 완료 순서, 삭제/보존 데이터, Billing OPEN의 같은 소비 승계와 무환급, 당일 Challenge 재참여, active 중 추가 삭제 거절, 정리 지연과 위험 격리, 일별 18개 조합·발생 건수·coverage를 구분했다. 원본 음성/답안 삭제 후 해당 원본을 품질 개선에 재사용하지 않으며 집계는 추세 파악용이라는 한계를 명시했다. visualize 지침에 따라 정적 흐름은 Mermaid로 설명하고 별도 파일은 만들지 않는다. Jira key 없음. 이번 변경은 CURRENT_STATE/WORKLOG뿐이며 계획·runtime·기존 외부 계약은 변경하지 않았다. 문서 검사 외 Gradle 테스트는 코드 변경이 없어 생략한다.
- 2026-09-22 — 삭제 계획 보완 작업의 현재 turn 기록 표식을 WORKLOG EOF에 추가하여 종료 hook 누락을 보완했다. 승인된 세 가지 정책과 리뷰 8건의 문서 반영 상태는 유지하며 추가 제품·runtime 변경은 없다. Jira key 없음. 문서 whitespace 검사는 통과했고 코드 변경이 없어 Gradle 테스트는 생략했다. Billing/앱/운영 검증 및 실제 구현은 여전히 후속 과제다.
- 2026-09-22 — 사용자가 세 가지 권장안을 승인하여 삭제 결정서·구현 계획에 반영했다: safe checkpoint 뒤 당일 Challenge 재참여 허용(반복 삭제 시 반복 참여 포함), active deletion 중 다른 key 추가 삭제는 명시적 409 후 완료 뒤 재요청(같은 key replay·신규 학습 유지), 통계는 발생 건수·terminal 결과 구성비와 수집 시작일부터의 보장/검증 가능한 과거만 반영(하루 18개 조합 유지, 미수집은 0 아님). 이전 리뷰 8건의 문서 보완도 반영했다: deletion-only guard, PREPARING_RESTART의 Challenge slot 선해제, DRAINING/SEALED 분리, alias 제거, Billing tombstone 일회 claim/transfer와 unknown commit 복구, Job 최초 counted marker/retry sequence·coverage, 전환율 제외, 접수 시 old cache 무효화/완료 시 new cache 보존. 테스트 계획·migration·rollout 체크와 코드 근거를 갱신했다. Jira key 없음/생성하지 않음. runtime·기존 공개 API/AI 계약·DB·AWS·모바일·배포는 변경하지 않았다. Billing fixture·IAM/retention·앱 합의·privacy/coverage·부하·staging 검증은 미완료이며 구현과 production 활성화는 별도 요청/승인 대상이다.
- 2026-09-22 — 삭제 계획 리뷰 후 사용자 결정이 필요한 제품 정책을 세 묶음으로 분리했다(아직 미확정): ① 삭제 안전 checkpoint 뒤 당일 Challenge 재참여 허용 권장(삭제 반복 시 재참여도 가능하다는 정책 영향 포함), ② 기존 삭제가 진행 중이면 다른 key의 추가 삭제는 명시적으로 거절하고 완료 후 재요청 권장(같은 key replay·신규 학습은 유지, cleanup 지연 시 재삭제도 지연), ③ 통계 v1은 발생일별 건수·terminal 결과 구성비로 한정하고 수집 시작일부터 보장하며 과거는 근거가 있는 지표만 별도 검증 후 반영 권장. guard·Callback drain/fence·Billing 일회 승계·집계 중복 방지·cache 경합은 제품 선택이 아닌 기술 명세 보완으로 분류했다. Billing fixture, IAM·retention 실제 설정, 개인정보 안내·희소 집계 표시 기준, 앱 합의와 Jira 등록은 별도 구현/출시 확인 사항이다. 본 기능 Jira key 없음. 계획서·runtime은 변경하지 않고 상태/작업 기록만 갱신했다.
- 2026-09-22 — 학습 기록 삭제 계획을 현 코드와 대조해 리뷰했다. 구현 전 보완이 필요하다: deletion-only 활성 시 guard touch 누락(§6.1/6.2), safe checkpoint 후에도 남는 Challenge 당일 unique slot, GRADING drain과 target Callback 무조건 no-op 충돌, 신규 학습 뒤 두 번째 삭제가 old operation alias로 흡수되는 계약, Billing tombstone의 일회 승계/소비·우선순위·경합 프로토콜 누락. 통계는 Job 재개에 대한 최초 집계 marker와 복원 가능한 backfill 범위가 필요하고 일별 event count의 비율을 동일 집단 전환율로 표시하면 안 된다. 모바일은 접수 시 old cache 제거와 completion 시 신규 cache 보존 규칙이 필요하다. 하루 18개 조합 계산은 맞지만 DB 성능은 부하 검증 전 확정할 수 없다. 신규 Jira 없음. 계획서·runtime 수정 없이 리뷰와 상태/작업 기록만 수행했다.
- 2026-09-22 — 최종 학습 기록 삭제 계획을 확정된 비식별 일별 통계 v1까지 포함해 사용자 관점으로 다시 설명했다. 설명 범위는 앱 이중 확인·멱등 key, 즉시 old record hide와 초기 write block, sealed inventory·Callback/Billing fence의 safe checkpoint, 신규 학습 재허용, Mongo/S3/Redis cleanup, `CLEANUP_DELAYED`/`NEEDS_REVIEW`, 삭제·보존 데이터, 30일/90일 retention, `learning_activity_daily_aggregates`의 하루 최대 18개 조합과 정확히 한 번 `$inc`, rollout 순서다. 별도 Jira는 없으며 설명·기록 외 runtime·DB·AWS·모바일·배포 변경은 없다.
- 2026-09-22 — 사용자가 비식별 일별 통계 v1 권장안을 확정했다. `learning_activity_daily_aggregates`는 시험 4개, 문항·Summary 7개, Challenge 7개의 하루 최대 18개 고정 metric/outcome 조합만 허용한다. 실패·시스템 재시도의 상세 원인은 aggregate에서 나누지 않고 `ALL`로 합치며 기존 Micrometer에서만 관측한다. 문항 제출은 `INITIAL|USER_RETRY`, Challenge 완료는 `SCORED|NO_SPEECH`만 구분한다. 완료·실패·재시도·제출·만료율과 주·월 추세는 일별 count에서 계산하고 중복 저장하지 않는다. 18개 기준 연 최대 6,570개이며 0건 조합은 만들지 않는다. 정상 11문항 시험은 약 25회의 transactional aggregate `$inc`가 추가된다. 결정서·구현계획과 테스트 기준만 갱신했으며 runtime·DB·Jira·배포 변경은 없다.
- 2026-09-22 — 일별 aggregate의 저장량과 write 부하를 산정했다. 실패·시스템 재시도는 v1에서 상세 원인별 문서를 만들지 않고 `ALL` 한 건으로 합치면 KST 하루 최대 18개 조합이며, RETAKE_AVAILABLE의 4개 원인을 분리해도 최대 21개다. 0건 조합은 문서를 만들지 않는 sparse upsert를 사용하므로 실제 문서 수는 더 적다. 18개 기준 연 6,570개로 저장량은 작다. write는 새 문서 생성이 아니라 durable event당 같은 일별 문서에 `$inc` 1회이며, 11문항 시험 정상 완료 한 건은 시작·제출 11·문항 완료 11·Summary 완료·시험 완료로 약 25회다. 현재 규모에서는 낮은 부하이나 장래 높은 동시성에서는 hot document contention을 측정하고 필요 시 시간 bucket/shard 또는 durable batching을 별도 검토한다. 이는 설명·권장안이며 계획서 metric enum은 아직 변경하지 않았다.
- 2026-09-22 — `learning_activity_daily_aggregates`에 추가할 v1 통계를 검토했다. 권장안은 모의고사 시작·완료·재응시 가능, 문항 제출·사용자 재답변·채점 완료·최종 실패·시스템 재시도, Summary 완료·최종 실패, Challenge 시작·제출·완료·만료·채점 최종 실패·시스템 재시도·no-speech를 KST 일별 count로 저장하는 것이다. 완료율·실패율·재시도율·만료율은 count에서 계산하고 별도 저장하지 않는다. 일별 고유 사용자, 개인별 횟수, 평균·분포 점수, 정확한 처리시간과 자유문장·음성 특성은 v1에서 제외한다. 고정 outcome만 허용하고 provider 원문 오류는 저장하지 않는다. 이는 권장 목록 검토이며 아직 결정·구현 계획서의 metric enum을 추가 확정하거나 runtime·Jira를 변경하지 않았다.
- 2026-09-22 — 사용자가 비식별 서비스 통계를 별도 MongoDB collection에 일자별로 저장하는 안을 확정했다. collection은 `learning_activity_daily_aggregates`, 기준일은 `Asia/Seoul`의 `YYYY-MM-DD`이며 `(bucketDate, metric, examType, outcome)`을 unique key로 사용한다. v1은 일별 문서만 저장하고 주·월 수치는 합산하며 userId·examId·attemptId·개별 event ID·정확한 응시 시각·점수·음성 특성은 저장하지 않는다. 실제 domain insert/CAS winner와 같은 Transaction에서 정확히 한 번 `$inc`하고 replay/CAS loser는 증가시키지 않는다. 기존 데이터는 cutoff를 고정한 shadow backfill·대조 후 live increment로 전환한다. 이 collection은 사용자 삭제와 TTL 대상이 아니다. 계획 문서만 갱신했으며 runtime·DB·Jira·배포 변경은 없다.
- 2026-09-22 — 계획서의 `2026-09-21 모의고사 완료 120건` 같은 수치는 현재 존재하는 저장 데이터가 아니라 향후 별도 비식별 일별 aggregate 저장소에 남길 예시임을 확인했다. 계획에는 `bucketStart`, `metric`, `examType`/고정 outcome, `count` 구조만 있고 실제 Mongo collection 이름은 아직 미확정이다. 현재 코드에는 Challenge·Billing 등의 Micrometer 운영 metric과 raw ExamSession 집계 query는 있지만 이 제품 통계용 durable aggregate collection은 없다. 구현 시 MongoDB의 별도 daily aggregate collection을 source of truth로 두고 userId·examId·attemptId·정확한 시각을 저장하지 않으며, 관측 metric backend는 선택적 mirror로만 사용하는 방향이 적절하다. Jira·runtime·DB 변경은 없다.
- 2026-09-22 — 학습 기록 삭제의 위험 상황 재시도 경계를 설명했다. 일반 일시 오류는 durable worker가 5초→15초→1분→5분→15분 상한+jitter로 자동 재시도하고, Billing/unknown commit은 status-first·majority re-read로 증거를 재확인한다. 그러나 target·신규 데이터·AttemptGroup·commit·추가 차감 위험이 해소되지 않아 `NEEDS_REVIEW`가 되면 위험한 delete/reserve/cancel의 blind 자동 재시도는 멈춘다. 기록 hide와 신규 학습 block을 유지한 채 운영자가 원인을 제거·검증하고 승인된 내부 절차로 같은 deletion operation을 resume한다. `CLEANUP_DELAYED`의 old target cleanup은 신규 학습을 허용한 채 자동 재시도를 계속한다. Jira는 생성되지 않았고 runtime·외부 시스템 변경은 없다.
- 2026-09-21 — 확정된 학습 기록 삭제 계획을 사용자 관점에서 다시 설명했다. 이중 확인·멱등 key → 즉시 old record 숨김과 초기 write block → sealed inventory·Callback/Billing fence의 안전 checkpoint → 신규 학습 재허용 → old Mongo/S3/Redis 물리 정리 흐름과 삭제·보존 데이터, 상태별 앱 동작을 구분했다. 단순 정리 지연은 `CLEANUP_DELAYED`, 신규 데이터나 추가 차감 위험이 있는 경우만 `NEEDS_REVIEW`다. 설명과 기록만 수행했으며 runtime·Jira·외부 시스템은 변경하지 않았다.
- 2026-09-21 — 학습 기록 삭제 잔여 권장안 확정 작업의 종료 기록을 현재 turn과 동기화했다. 안전 checkpoint 뒤 단순 물리 정리 지연은 `CLEANUP_DELAYED`와 `canStartLearning=true`, target·ownership·Billing·commit 불확실성은 `NEEDS_REVIEW`와 false로 유지한다. sealed old target만 삭제하고 이후 신규 학습 기록은 보존한다. retention·S3 IAM·모바일 문구·운영 복구·Jira 구성은 권장안으로 확정됐으며 실제 Jira key는 아직 없다. 계획 문서와 상태 기록만 변경했고 runtime·DB·AWS·모바일·배포는 변경하지 않았다.
- 2026-09-21 — 사용자가 “단순 정리 지연에는 신규 학습을 허용하고 나머지는 권장안”으로 확정했다. 삭제 계획은 안전 checkpoint 전까지만 write를 차단하고, sealed inventory·old record hide·late Callback fence·Billing OPEN tombstone이 확정되면 `canStartLearning=true`로 전환한다. 이후 worker는 sealed old target만 삭제하며 userId 전체 삭제를 금지한다. S3/Redis/old orphan 정리 지연은 `CLEANUP_DELAYED`, target·ownership·Billing·commit 불확실성만 `NEEDS_REVIEW`로 분리한다. 보존은 command/Callback tombstone·일반 로그·암호화 backup 30일, 보안 감사 90일, OPEN/non-terminal coordination 무TTL 후 terminal+30일이다. S3 IAM은 `temp/*` List/Delete로 제한하고 versioning 삭제를 production gate로 검증한다. 모바일 4상태·`canStartLearning`·고정 문구와 동일 operation 복구 runbook, 상위 Jira+담당별 하위 이슈 구조를 계획서에 확정했다. 실제 Billing fixture·S3 환경·개인정보 고지·Jira key/담당자는 운영 확인으로 남고 runtime·AWS·Jira·배포는 변경하지 않았다.
- 2026-09-21 — 학습 기록 삭제 구현계획서 작성 단계 확인의 종료 기록을 보완했다. 계획서 권장 운영 기본값은 요청 즉시 숨김, 물리 삭제 완료 전 신규 학습 차단, 24시간 정리 목표, 반복 실패 `NEEDS_REVIEW` 격리·숨김 유지, backup/log의 기존 보존기간 만료, 계정·로그인 유지와 credit·무료 기회 미복원이다. 이는 계획 입력값이며 아직 구현·Jira·운영 활성화는 아니다.
- 2026-09-21 — 사용자가 학습 기록 독립 삭제 권장 방향과 모바일 앱 역할 설명을 수용해 구현계획서 작성 단계로 진행 가능하다고 정리했다. 계획서 기준은 모의고사+Challenge 전체, 1차 전체 삭제, 즉시 숨김/write fence+durable worker, 사용자 콘텐츠·음성 삭제/거래·보안 최소 증거 보존, credit·무료 기회 미복원, 이중 확인+멱등성, Identity 무변경이다. 구현 전 계획서에서 물리 삭제 SLA, 완료 전 새 학습 차단, 장기 실패 상태와 backup/log 보존 고지를 명시해야 하며 아직 계획서 작성·Jira·runtime 구현은 시작하지 않았다.
- 2026-09-21 — 학습 기록 삭제 권장안의 모바일 앱 수정 범위 설명을 현재 turn 종료 기록으로 확정했다. 모바일 앱은 설정 진입점·2단계 확인·UUID idempotency key의 terminal까지 보존/재사용·진행 상태·완료 후 로컬 cache 제거를 담당하고, Learning Core는 삭제 API와 실제 정리를 담당하며 Identity는 변경하지 않는다. 로그인 Token과 계정은 유지되고 아직 모바일/서버 runtime 구현이나 Jira 등록은 시작하지 않았다.
- 2026-09-21 — 학습 기록 삭제 권장안에서 말한 “앱 수정”의 의미를 명확히 했다. 이는 Learning Core 서버가 아니라 모바일 앱의 설정 메뉴·2단계 확인·최종 확인 시 UUID idempotency key 생성/terminal까지 보존·timeout/앱 재실행 시 같은 key 재사용·진행/완료 표시·완료 후 로컬 학습 cache 제거를 뜻한다. 로그인 Token·계정 정보는 유지하고 `confirmed=true` body는 보내지 않는다. 서버 API와 모바일 호출 UI가 모두 있어야 사용자가 기능을 실행할 수 있으며 아직 양쪽 runtime 구현은 시작하지 않았다.
- 2026-09-21 — 학습 기록 삭제 확인 방식 중 “앱 이중 확인+멱등성 key” 선택 시 범위를 명확히 했다. 앱은 2단계 위험 안내·중복 탭 방지·한 번 생성한 UUID key의 timeout 재사용·진행 상태/로컬 cache 처리를 추가하고, Learning Core는 기존 JWT `sub` 기반 신규 삭제 command·durable operation·unique/active 제약·동시/응답 유실 replay 수렴을 추가한다. Identity 로그인·Firebase 재인증·새 claim/Token·RefreshSession은 변경하지 않는다. 이중 확인은 UX이며 `confirmed=true`는 보안 증거가 아니고, 탈취 Token 방어가 필요하면 별도 recent-auth 선택이 필요하다. 별도 Jira나 runtime 구현 없이 결정 문서만 보완했다.
- 2026-09-21 — 독립 학습 기록 삭제 결정 선택지 정리 작업의 종료 기록을 보완했다. 권장 조합은 모의고사+Challenge 전체, 1차 전체 삭제만, 요청 즉시 숨김·write fence 후 Mongo durable job 비동기 정리, 사용자 콘텐츠·S3·Redis 삭제와 거래/보안/멱등성 최소 증거 분리 보존, credit·무료 기회 미복원, 이중 확인+멱등성, Learning Core 전용 삭제 API다. 이는 구현 전 제안이며 사용자 최종 승인·Jira·runtime 변경은 아직 없다.
- 2026-09-21 — 로그아웃·회원 탈퇴와 독립된 학습 기록 삭제 기능의 제품·기술 선택지를 정리했다. 권장안은 모의고사+Challenge 전체 기록, 1차 전체 삭제만, 요청 즉시 deletion fence/조회 숨김 후 Mongo durable job으로 DB·S3·Redis 비동기 수렴, 진행 중 Billing/채점 증거의 terminal 확인, 사용자 생성 콘텐츠 삭제와 최소 거래·보안·멱등성 증거 분리 보존, credit·무료 기회 미복원, 이중 확인과 idempotency다. 아직 사용자 확정이나 runtime 구현은 아니며 별도 Jira 키도 없다. 세부 장단점은 `docs/codex/LEARNING_RECORD_DELETION_DECISION_OPTIONS.md`에 기록했다.
- 2026-09-21 — 로그아웃·회원 탈퇴와 독립된 “현재 사용자의 학습 기록만 삭제” 기능 존재 여부를 `develop` / `16eb5de`에서 확인했다. 현재 Exam·Challenge 사용자용 Controller에는 `DELETE` endpoint가 없고, Repository/application 코드에도 학습 aggregate를 일괄 삭제하는 command가 없다. Identity의 로그아웃은 RefreshSession 폐기, 회원 탈퇴는 Identity lifecycle이며, Learning Core `UserWithdrawn` consumer는 inbox와 잔여 Access Token deny marker만 저장하고 시험·Challenge·S3·Redis 데이터를 삭제하지 않는다. 따라서 이 기능은 현재 미구현이다. 별도 Jira 없이 읽기 전용 분석과 기록만 수행했으며 구현 전 삭제 범위·진행 중 채점/결제 상태·S3/Redis·법적/멱등성 보존 정책을 별도 계약으로 확정해야 한다.
- 2026-09-21 — 모의고사 문제·Part 3 안내 음원 URL 발급 시점을 현재 `develop` / `16eb5de` 코드로 확인했다. `POST /api/v1/exams`에서 Session assignment로 `examId`를 얻은 뒤 같은 요청 안에서 모든 문항의 `audioUrl`을 발급하고, Part 3 문항에는 `questions/{mockExamId}/part3_intro.wav`의 `guideAudioUrl`을 추가해 생성 응답에 포함한다. 두 Presigned GET URL은 서명 시점부터 60분 유효하며, 결과 조회용 사용자 답변·AI 전달용 URL의 5분 정책과 다르다. 동일 생성 API가 다시 응답을 조립하면 URL도 새로 서명된다. 별도 Jira 없이 읽기 전용 코드 확인과 기록만 수행했고 앱 코드·API·S3·배포는 변경하지 않았다.
- 2026-09-21 — TMI-126 사용자 확정안을 테스트 배포 인계 문서에 반영했다. 기존 to-teacher-firebase 재사용, Google/가상 번호 동일 UID link, 별도 테스트 Identity DB/키/issuer 및 fingerprint·복구 암호화 키, Access PT30M/Refresh P14D/reissue recovery ON PT2M 기준이다. 실제 활성화/검증 완료가 아니라 목표값이며 도메인/JWKS/kid/DB·secret 참조/배포 commit/약관 버전은 미확정이다. Firebase는 공유 사용자 영역이므로 운영 의존성 및 사용자 삭제·미완료 가입 정리 등 부수 작업 범위를 확인해야 한다. 기록/인계 문서 외 코드·외부 설정 변경 없음.
- 2026-09-21 — TMI-126 Identity 담당자 확정 항목을 Firebase 프로젝트/provider·테스트 전화번호·클라이언트 등록, 테스트 HTTPS/issuer/JWKS, JWT 키/kid/실제 TTL, 배포 commit·기능 ON/OFF(재발급 복구 포함), 별도 DB/session 및 가입 내부 보안 설정, 프론트 가입/재발급 계약으로 정리했다. 기존 audience 배열/account_type 계약은 유지하며 AI 전용 JWT는 추가하지 않는다. 비밀값이 아닌 설정명/참조·검증 결과를 인계받는 단계이고 인프라 공동 결정과 Identity 내부 결정을 구분한다. 실제 설정 확정·배포 없이 설명/기록만 수행.
- 2026-09-21 — TMI-126 남은 테스트 배포값 확정 안내를 현재 turn 기록에 연결했다. 도메인/내부 HTTPS·데이터 리소스·Identity 설정·배포 사양/비용·최초 활성일 확정 후 인프라 준비와 E2E를 진행하는 단계이며, 이번에는 실제 리소스 생성·배포 없이 설명과 기록만 수행했다.
- 2026-09-21 — TMI-126 테스트 배포의 다음 단계는 남은 배포값 확정으로 정리했다. 운영 유지·기존 cluster 재사용·테스트 Identity/LC 및 app-ai-learning API/worker 분리·정상 MEMBER 인증·게스트 예외 미추가는 합의 방향이다. 테스트 도메인/private HTTPS 경로, DB/S3/Redis 격리 상세, Firebase 프로젝트와 재발급 복구 ON/OFF, 배포 revision/사양/비용·운영 기간·Day1 활성 시각은 확정/인계가 필요하다. 값 확정만으로 배포 완료가 아니며 이후 인프라 준비·배포·E2E가 남는다. 기록 외 변경 없음.
- 2026-09-21 — TMI-126 새 app-ai-learning 배포에 따른 Identity 추가 인계 범위를 정리했다. AI는 LC와만 기존 전용 service credential로 통신하므로 Identity의 사용자 JWT audience/scope/발급 API나 AI용 workload JWT 추가는 불필요하다. Identity에는 신규 AI 분리 배포 사실과 기존 MEMBER 인증 계약 유지, 테스트 주소/issuer/JWKS/TTL/revision/Firebase 및 refresh recovery 활성 상태 인계 요청만 전달하면 된다. 실제 전송·코드·배포 변경 없음.
- 2026-09-21 — TMI-126 신규 app-ai-learning API/worker 테스트 배포 인계 결과를 현재 turn 기록에 연결했다. 배포 인계 문서를 작성했지만 AWS 생성·이미지 빌드·배포는 미실행이며 테스트 리소스/연결값·실행 사양 확정이 남아 있다.
- 2026-09-21 — TMI-126 사용자 지정 새 AI 저장소 Too-Much-I/app-ai-learning을 GitHub에서 읽기 전용 확인했다. 조회 main 2391a944010f816016c9263e507a2850c5b5c07a 기준 Docker/FastAPI API:8000와 python -m app.jobs.worker를 별도 ECS service/task로 배포하며 동일 이미지·테스트 Redis·AI 임시 S3를 공유하는 구조다. 기존 모의고사 AI는 복제/변경하지 않는다. docs/codex/CHALLENGE_TEST_DEPLOYMENT_HANDOFF.md에 명령/환경변수 매핑·private HTTPS·권한·데이터 분리·준비값/배포 순서를 정리했다. 실제 AWS 생성·이미지 build/push·AI 코드/배포 변경 없음. LC callback URL·Redis/S3·secret ARN·네트워크·사양/비용이 미확정이며 이 문서는 배포 완료 증빙이 아니다.
- 2026-09-21 — TMI-126 Identity 답신을 로컬 LC develop 16eb5de 및 Identity develop 8c624ffe 문서와 대조했다(원격 최신/실배포 확인 아님). 신규 가입은 exchange→ENROLLMENT_REQUIRED/enrollmentId→동일 Firebase UID phone link→강제 갱신→signup이며 기존 회원은 exchange다. account_type 누락은 LC develop의 JWT 계약 사본 문제이고 Identity develop에는 명시돼 있다. LC audience contains 검증·JWT 설정명/PT60S 기본값·MEMBER 인가·Billing 비의존 Challenge와 KST Day1 고정을 확인했다. 실제 환경값/상호 토큰 거절/E2E는 배포 후 검증 필요. 재발급 복구 ON 시 절대 만료 헤더 처리, enrollment/recent-auth 제한과 브라우저 테스트 CORS를 추가 인계사항으로 제안했다. 기록 외 변경 없음.
- 2026-09-21 — TMI-126 Identity/LC 공동 인계 설명을 현재 turn 기록에 연결했다. 정상 MEMBER 발급·재발급, JWT 설정 대조, 테스트 서비스/데이터/키/큐/콜백 분리와 인증→Challenge E2E 순서를 안내했다. 실제 설정값 확정·배포·외부 변경은 미실행이다.
- 2026-09-21 — TMI-126 정상 MEMBER 기반 Challenge 테스트의 Identity/LC/프론트/AI 인계 범위를 정리했다. 테스트 Identity의 검증된 develop revision·Firebase project/provider/전화번호 증빙·signup/exchange/reissue·MEMBER claim, LC의 jwt mode/issuer/JWKS/audience 설정을 맞추고 운영과 DB/키/refresh session/S3/queue/credential/콜백/배포를 분리하는 제안이다. 사용자 JWT는 LC가 로컬 검증하며 매 요청 Identity 호출이나 AI에 사용자 토큰 전달을 추가하지 않는다. 기본 JWT 계약 문서의 account_type 누락은 Challenge 계약과 정합화가 필요하다. 실제 테스트 주소/활성화/인프라는 미확정이며 정상 로그인·재발급부터 검증한 뒤 Challenge를 활성화한다. 기록 외 변경 없음.
- 2026-09-21 — TMI-126 정상 MEMBER 인증 경로 우선 검토 결과를 현재 turn 기록에 연결했다. Identity가 MEMBER 토큰을 정상 발급하면 LC 게스트 예외는 추가하지 않는다. 실제 인증 성공/배포 활성화는 미확인이며 코드·운영 설정 변경 없음.
- 2026-09-21 — TMI-126 사용자가 Google 로그인→전화번호 테스트 연결→MEMBER 발급→LC 회원 API 호출 경로를 제안했다. 해당 흐름으로 Identity가 정상 account_type=MEMBER Access Token을 발급하고 테스트 LC가 issuer/audience/JWKS를 검증할 수 있다면 게스트 allowlist/기간/인가 예외 구현은 불필요하다. 예외는 미구현 상태이며 정상 회원 인증 검증을 우선한다. Firebase 테스트 번호 설정과 Identity 가입/교환 지원의 실제 배포 활성화는 아직 확인하지 않았다. 테스트 LC/AI 및 데이터 격리 준비는 별도로 유지한다.
- 2026-09-21 — TMI-126 테스트 인가의 잔여 결정사항을 정리했다. 권장안은 개발자 전용 계정 allowlist(사용자별 분리), 배포 시 고정한 최초 7일 종료 시각과 명시적 연장, 분리된 테스트 서비스/데이터, 회원 인증/탈퇴/병합 E2E를 제외한 Challenge 흐름 검증이다. JWT 만료는 별도로 유지하고 재설치 등 sub 변경 시 재등록한다. 실제 계정 식별자·종료 시각·테스트 주소/AI callback 및 TLS 값은 배포 전 확정하며 아직 운영 변경/구현 승인은 아니다. 7일은 신규 제안으로 미확정이다.
- 2026-09-21 — TMI-126 사용자가 기존 Identity JWT에는 account_type이 없음을 확인했다. 테스트 인가 예외는 claim 부재를 지원하되 정상 JWT 검증 후 승인된 issuer/sub, 별도 테스트 배포, default-off flag, 허용 기한을 모두 만족하는 계정만 통과시키는 방향이다. claim 부재 자체로 GUEST/MEMBER를 추정하지 않고 null/빈 값/알 수 없는 값은 별개로 거절한다. 운영의 MEMBER 필수 정책과 소유권/deny 검증을 유지하며 Identity 토큰 변경 없이 테스트 가능하도록 제안했다. runtime/배포 변경 없이 설명·기록만 수행.
- 2026-09-21 — TMI-126 테스트 전용 인가의 코드·설정·문서·배포·회귀 검증 범위를 사용자에게 설명하고 현재 turn 기록에 연결했다. 구현 및 AWS 변경은 미실행이며 기존 MEMBER 정책과 JWT/소유권 보호를 유지하는 제한적 테스트 예외를 제안한 상태다.
- 2026-09-21 — TMI-126 테스트 전용 게스트 허용의 수정 범위를 검토했다. ChallengeController의 회원 인가를 전용 정책으로 분리하고 ChallengeProperties/application.yml에 default-off·테스트 계정 allowlist·허용 기한을 제안한다. 설정 검증은 Challenge 활성 여부와 무관하게 실행하고 운영 staging 명칭만으로 테스트를 판단하지 않는다. 테스트 배포 구분·별도 DB/S3/AI 연결·workflow 대상 제한, 기존 JWT/소유권/deny/Callback 유지, 인가/기동 회귀 및 AGENTS/계약/rollout의 테스트 한정 예외 문서화가 필요하다. 기존 게스트 JWT의 account_type 유무는 확인 후 명시적 호환 범위를 정하며 임의 claim 값은 허용하지 않는다. 구현 없이 설명·기록만 수행.
- 2026-09-21 — TMI-126 Firebase 없는 Challenge 단독 테스트 대안 안내를 현재 turn 기록에 연결했다. 테스트 서버 전용 게스트 계정 allowlist와 기본 OFF 인가 예외는 아직 제안이며 구현/활성화하지 않았다. 기존 게스트 JWT와 소유권 검증을 유지하고 MEMBER/Firebase E2E는 후속으로 분리한다.
- 2026-09-21 — TMI-126 사용자가 Firebase 설정을 기다리지 않고 Challenge만 먼저 테스트하기를 요청했다. ChallengeController/Properties 확인 결과 현재 MEMBER 검사는 고정이며 게스트 테스트 허용 설정은 없다. 대안으로 별도 테스트 LC에서만 기본 OFF 설정과 명시적 테스트 계정 allowlist를 사용해 정상 서명된 기존 게스트 JWT의 sub에 한정하여 회원 조건을 예외 허용하는 방식을 제안한다. JWT 검증·소유권·탈퇴/병합 보호·AI 인증은 유지하며 운영 staging을 테스트로 오인하지 않는 배포 경계가 필요하다. 제품 MEMBER 정책/계약은 유지하고 테스트 한정 예외 문서와 회귀 검증을 승인 후 추가해야 한다. 이번은 제안만으로 코드·권한·AWS 변경 없음.
- 2026-09-21 — TMI-126 테스트 인증 안내를 현재 turn 기록에 연결했다. 사용자 확인에 근거한 develop MEMBER 지원과 실제 배포 미확인을 구분하며, 테스트 Identity 배포·Firebase 인증 연결은 제안 단계다. 운영 및 MEMBER 제한 변경 없이 다음 실행 승인 대기.
- 2026-09-21 — TMI-126 인증 전제 확인: 사용자 제공 검토 결과에 따르면 Identity develop에는 Firebase signup/exchange/guest upgrade/merge와 MEMBER claim 발급이 구현됐고 main 기반 hotfix에는 없다. Firebase 기본 OFF 및 실제 배포 활성화 여부는 미확인이다. 앞선 로컬 관찰을 develop 기능 부재로 해석하지 않는다. 권장안은 운영 유지, 별도 테스트 Identity에 검증한 develop revision과 필요한 Firebase 설정을 배포하고 테스트 LC의 issuer/JWKS를 맞추는 것이다. 프론트는 개발용 최소 Firebase 인증 또는 정상 발급 토큰 입력이 필요하며 MEMBER 제한은 유지한다. 이번은 사용자 제공 사실 기반 제안으로 신규 검증·구현·배포 없음.
- 2026-09-21 — TMI-126 인증 테스트 제안에 사용자가 “그건 없다”고 정정했다. 부재 대상이 Identity MEMBER 발급 기능인지 프론트 임시 토큰 입력 기능인지 확정되지 않아 확인 질문 단계다. 기존 제안을 실행 가능한 확정안으로 취급하지 않고 인증 완화·계정 생성·코드/배포 변경을 진행하지 않는다.
- 2026-09-21 — TMI-126 프론트 로그인 UI 미구현 상태의 Challenge 테스트 인증을 검토했다. ChallengeController는 유효한 JwtAuthenticationToken의 account_type=MEMBER를 요구하므로 기존 GUEST 토큰으로는 불가하다. 권장안은 정상 테스트 MEMBER 계정 발급 토큰을 개발 빌드의 임시 입력/인증 저장소에 주입하고 기존 Bearer 요청을 사용하는 것으로, 회원 제한 완화나 DB 직접 변경은 하지 않는다. 로컬 Identity에 signup/login/reissue API는 있지만 현재 checkout의 JWT 발급 코드에는 account_type이 없어 최신 배포본의 MEMBER 발급 지원 확인이 선행돼야 한다. 배포 상태/실제 토큰 미확인, 외부 변경 없음.
- 2026-09-21 — TMI-126 테스트 환경의 cluster 재사용은 기존 tosunsaeng-staging-cluster 안에 테스트 Learning Core/AI 서비스를 추가하는 의미로 설명했다. 기존 운영 서비스는 유지하고 신규 서비스의 task·배포·설정·데이터 연결을 분리한다. Fargate cluster는 단일 서버가 아닌 관리 단위이며 공유만으로 데이터/권한 격리가 보장되지는 않는다. 설명 및 기록만 수행, AWS 리소스 생성·배포·코드·Jira 변경 없음.
- 2026-09-21 — 사용자 제공 Innovation Sandbox 포털과 AWS 콘솔을 UI로 읽기 전용 점검했다. 기존 로그인으로 접근됐고 활성 lease의 계정은 기존 운영 계정과 동일하다. 서울 리전 ECS는 tosunsaeng-staging-cluster 하나, Identity/Learning Core/AI 서비스 각 1/1이며 task definition은 각각23/20/13이다. LC·AI는 같은 VPC/2개 subnet, public IP OFF다. LC는 tosunsaeng-staging-alb의 별도 target group:8080에 정상 연결돼 있다. ALB HTTP80→HTTPS443, host 규칙은 identity-staging/api-staging 및 default404다. 기존 인증서 SAN은 이 두 운영 주소만 포함한다.
- TMI-126 테스트 권장안: 기존 cluster/VPC/ALB 기반을 재사용하되 LC-test·AI-test 별도 service/task definition·target group·정확한 신규 host 규칙/인증서, 테스트 DB/user·S3·Redis/AI queue·credentials·callback을 분리한다. AI 현재 Service Connect는 tosunsaeng-staging namespace, tosunsaeng-ai:8000 내부 HTTP/TLS OFF·ALB 연결 없음이며 ECS 서비스 수준 Lattice 연결도 없다. Challenge staging/prod의 HTTPS endpoint 검증 때문에 운영 HTTP URL을 그대로 복사할 수 없다. 테스트 AI에 HTTPS 도달 경로를 구성하고 운영 discovery/alias/callback을 보존한다. Identity는 인증만 확인하는 초기 E2E라면 승인된 테스트 MEMBER 계정의 기존 발급 흐름 재사용을 검토하되 가입/탈퇴/merge 시험은 별도 Identity 환경이 필요하다.
- 실제 AWS 생성/변경·새 lease·배포·Secret 열람은 하지 않았다. DNS 관리 권한/새 인증서 검증·task CPU/메모리/권한·DB/Redis/S3 실제 준비·AI Challenge 구현 및 계정 만료의 정확한 정책은 추가 확인 대상이다. 공유 ALB/네트워크는 완전 물리 격리가 아니며 추가 task·트래픽/AI 호출 비용이 발생한다. 사용자에게 확인 사실과 제안/미확인을 분리해 설명하며 CURRENT_STATE/WORKLOG만 갱신한다. 관련 TMI-126, 비활성 유지 후보 TMI-116·118·128. 신규 Jira/코드/테스트 실행 없음.
- 2026-09-21 — 사용자가 운영 활성화가 아니라 별도 테스트 서버에서 프론트·AI 통합 검증을 원하는 것으로 정정했다. 운영 모의고사/서비스/DB/AI callback 설정은 유지한다. 같은 ECS cluster/VPC를 재사용하더라도 테스트용 별도 service/task definition·접속 주소·Mongo DB/user·S3 bucket/권한·Redis/AI queue·방향별 credential을 분리하는 구성을 권장한다. 신규 cluster/ALB/NAT 생성은 필수 조건이 아니며 기존 routing/권한·용량·비용은 확인 전이다. 서버의 JWT/HTTPS를 유지하고 Spring local/test Legacy profile로 우회하지 않는다.
- TMI-126 AI 계약은 callback URL이 요청별 지정이 아니라 AI 배포 설정으로 고정된다. 운영 AI callback 주소를 테스트로 변경하지 않으며, 별도 AI 테스트 배포/queue 또는 기존 테스트 endpoint 제공 가능 여부가 다음 확인점이다. Challenge 기준일/콘텐츠는 테스트 DB에서 별도 준비하고 운영 데이터를 복사하지 않는다. 기존 운영 대상 main 배포 workflow는 테스트 배포에 그대로 실행하지 않는다. 이번은 구성 제안·기록만으로 신규 인프라/workflow/배포/DB/코드/Jira 변경이나 테스트 실행은 없고 실제 리소스 생성은 대상·비용·AI 준비를 확인한 뒤 별도 승인 범위로 남긴다. 관련 TMI-126 및 TMI-128.
- 2026-09-21 — 10초 챌린지 우선 배포 가능성을 로컬 코드/rollout/workflow로 점검했다. 사용자 의도는 기존 모의고사 유지·신규 기능만 OFF로 확정됐다. Challenge는 별도 flag/aggregate/worker/AI Callback이며 Billing 없이 활성화 가능하다. 기존 Billing/AttemptGroup 등 미활성 기능은 OFF를 유지하되 이미 활성인 기능이나 backlog는 실환경 확인 없이 끄지 않는다. JWT/MEMBER 인가와 기존 탈퇴·병합 접근 차단은 유지한다. Billing saga OFF는 모의고사 비활성화가 아니라 기존 startNew 경로이며 이번 요청에는 모의고사 차단 코드가 필요 없다.
- TMI-126 rollout 전 Mongo 6개 collection/9개 index·replica-set·catalog/현재 기준일, Identity account_type=MEMBER 실제 발급, AI HTTPS endpoint·방향별 별도 credential 및 callback, S3 temp/challenges 권한/lifecycle·모바일 E2E 확인이 필요하다. 최초 활성화의 catalog 초기화가 KST 기준일을 영속화하며 이후 기동 실패/재배포로 자동 복원하지 않는다. 테스트와 실제 서비스의 기준일 metadata를 분리한다. 기존 deploy-staging workflow는 main push/manual로 현재 ECS task definition의 환경변수/secret 참조를 계승하고 이미지만 교체하므로 ECS 설정 준비가 별도 필요하다. 기록상 staging은 실제 앱 운영 환경이다. 실제 ECS/DB/AI 설정·배포/flag를 조회하거나 변경하지 않았으며 코드 변경·테스트 실행·Jira 생성/전환 없음. 관련 TMI-126, TMI-116·118·128, TMI-109·125.
- 2026-09-18 — 웹 누적 완료 응시를 기존 7/27 시작·종합 요약 존재·반복 포함 기준으로 Atlas 읽기 전용 재집계. 일반 시험 요약 213개에서 7/27 이전 13개 제외한 200건(9/18 약 15시 KST 조회까지), 9/18 응시일 귀속 6건. 명시적 비일반 테스트 요약 89개 제외, 일반 시험 ID 중복·날짜 파싱 누락 없음. 웹 요약 시각 부재로 시험 ID의 응시 시각을 KST 날짜로 귀속하는 기존 한계 유지. 이전 9/12 조회 150건보다 50건 증가. DB·코드·API·배포·Jira 변경 없음.

- 2026-09-18 — 8/23 출시 이후 사용자 지표를 Identity와 Learning Core DB 읽기 전용 대조로 정리. 동일 신규 게스트 계정 157개 기준 시험 생성 115, 문항 결과 103, 11문항·요약 완료 78, 2회 이상 완료 51(65.4%), 완료 268건. 다른 날 완료 35, 재답변 결과 보유 16계정·76건, 7일 관찰 가능 47개 중 다음 날~7일 재완료 25개. 집단 밖 활동을 합친 80계정·271건과 분리하고, 신규 유입·최초 완료·전환 분모 및 계정/사람·무료/유료 의미를 명시한 Markdown 작성. 수치 합계·비율·문항 정합성 확인. DB·앱 코드·API·배포·Jira 변경 없음.

- 2026-09-18 — 사용자 확인한 8/23 앱 출시·유료 판매 전·기간제 가격을 기준으로 MongoDB Atlas를 읽기 전용 집계하고 5년 결제액 추산 문서/XLSX 작성. 8/23~9/17 신규 완료 계정 78개, 완료 계정 79개·268건, 반복 52개·다른 날 이용 36개. 9/18 부분 일자 3건 추가. 연환산 약 1,100개·전환 5%·평균 3만원·연 1회 구매 가정은 연 165만원, 5년차 신규 3만개·전환 10% 목표는 9천만원이며 검증된 매출 전망과 구분. known test/demo 제외의 한계·웹 중복·판매/확장 수요 미검증 명시. 수식 대조·입력 민감도·전 시트 렌더 검증 완료. 원본 발표·DB·앱 코드·API·운영 변경 및 Jira 없음.

- 2026-09-16 — staging이 실제 앱 운영 환경임을 반영해 24시간 유지 기준으로 비용 검토. 사용자 동의에 따라 Container Insights를 OFF로 적용하고 콘솔 성공·서비스 3개 각 1/1 실행 확인. 기본 ECS CPU 기반 자동 확장 경보 4개 유지, 상세 지표 비용 약 $41.42/30일 절감 예상. 주간 화면 평균 CPU/메모리는 Identity 0.17%/9.119%, Learning Core 1.125%/15.62%, AI 2.705%/51.49%. Identity 0.5 vCPU/1 GiB와 Learning Core 0.5 vCPU/2 GiB는 검증 후보이며 최대값·기동/부하 테스트 미확인으로 미배포, AI 1 vCPU/2 GiB 유지 권고. 양 프라이빗 서브넷의 동일 NAT 기본 경로·S3 Gateway endpoint 확인. NAT 유지 권고, 공인 IP 전환은 Atlas IP 허용 정책 확인 대기. CPU·메모리·라우팅·앱 코드·API·Jira 변경 없음.

- 2026-09-16 — AWS 9월 비용 CSV와 서울 리전 콘솔을 읽기 전용 대조해 하루 약 $10의 원인을 분석. 9/1~14 완전 일자 평균은 $10.12/day($303.59/30일), 최근 완전 일자 9/14는 $10.52. ECS 3개 24시간 실행, Enhanced Container Insights, NAT Gateway, EC2 2대, 공인 IPv4 5개 상당, ALB와 ElastiCache의 고정비가 주원인. Cost Explorer는 현재 SSO 권한 거부로 CSV를 금액 근거로 사용. AWS 리소스·앱 코드·DB·외부 계약·Jira 변경 없음.

- 2026-09-16 — 보안 설명에 인증/시험 소유권의 단계별 역할과 파일·작업별 5분 임시 URL 접근 제한을 보충. 설명 및 작업 기록만 갱신. Jira 및 페이지·앱 코드·DB·운영 변경 없음.

- 2026-09-16 — 보안 설명을 인증 유효성 검증과 시험 소유권 대조, 특정 음성 파일의 5분 임시 접근 범위로 보완. 설명 및 기록만 갱신. Jira 및 페이지·앱 코드·DB·운영 변경 없음.

- 2026-09-16 — 보안 적용 내용을 사용자 인증/시험 소유권과 음성 5분 임시 접근 URL의 두 항목으로 짧게 요약. 설명 및 기록만 갱신. Jira 및 페이지·앱 코드·DB·운영 변경 없음.

- 2026-09-16 — 보안 단독 페이지 JWT 인증 설명 구체화 완료를 현재 작업 WORKLOG EOF에 연결. 검증 항목과 시험 소유권 문구 및 브라우저 검수 결과 유지. 이번 후속은 기록만 갱신, Jira 및 원본 자료·앱 코드·DB·운영 추가 변경 없음.

- 2026-09-16 — 보안 단독 HTML의 JWT 설명을 서명·만료·발급자·대상 서비스 유효성 검증으로 구체화하고 인증 사용자/시험 소유자 일치 확인을 명시. 브라우저 표시 검증, 원본 덱·앱 코드·DB·운영 유지. Jira 없음.

- 2026-09-16 — index_v7 부록 디자인에 맞춘 보안 단독 1페이지 완료. 사용자 정정에 따라 발표자료 합본 대신 security-appendix/toseonsaeng-security-onepage.html 제공. JWT/시험 소유권과 음성 5분 임시 URL 2개 항목, 원본 글꼴·색상 재사용. 1페이지/원본 해시 불변과 브라우저 배치 확인. 원본 덱·앱 코드·DB·운영 유지. Jira 없음.

- 2026-09-16 — 보안 발표 페이지는 사용자 선택에 따라 내 기록만 접근(JWT/시험 소유권)과 음성 접근 시간 제한(5분 임시 URL) 두 항목으로 정리. 접근 주체와 유효 시간 중심 문구 제안, Sentry 항목 제외. Jira 및 원본 자료·앱 코드·DB·운영 변경 없음.

- 2026-09-16 — 발표 보안 페이지를 음성/평가 결과 보호 중심으로 제안. 코드 확인된 JWT/시험 소유권, 답변 음성 5분 임시 접근 URL, Sentry 민감정보 정제를 3개 항목으로 구성. 운영 배포 상태는 미확인. 자료·앱 코드·DB·운영 변경 없음. Jira 없음.

- 2026-09-14 — 노션 붙여넣기용 본문 27장 대본 제공 완료를 현재 작업 WORKLOG EOF에 연결. Markdown 파일과 설명 약 16분/시연 3분 유지. 이번 후속은 작업 기록만 갱신, Jira 및 원본 자료·앱 코드·DB·운영 변경 없음.

- 2026-09-14 — index_v3 대본을 노션에 붙여넣을 수 있는 27개 페이지 제목/문단 Markdown으로 정리. 설명 약 16분 + 시연 3분 유지, 별도 MD 및 복사 가능한 본문 제공. 노션 직접 수정·원본 덱·앱 코드·DB·운영 변경 없음. Jira 없음.

- 2026-09-14 — 최종 index_v3 본문 27장 발표 대본 완료. 사용자 확정 설명 15~17분 + 시연 3분에 맞춰 설명 16분/전체 19분 배분, 시연 11장 대본 제외. script-index-v3에 시간 포함 대본·본문 TXT·시간표 생성. 발화 4,276자, 27장/총 1,140초/원본 해시 불변 검증. 설문 59명과 실적/목표·설계/검증 지표 구분 유지. 시연 장표의 02:00과 시간표 3분 차이 메모. 원본 덱·앱 코드·DB·운영 변경 없음. Jira 없음.

- 2026-09-14 — 문제 정의 3장을 첫 번째 장의 좌우 분할 디자인으로 통일한 v3 완료. 왼쪽에 출처/문제, 오른쪽에 근거/결론 배치. 수치를 오른쪽 끝에 두고 본문 폭·간격·결론 높이를 조정해 추가 내용 없이 여백 균형 개선. 3장 브라우저 검수, 원본 해시 불변 확인. 산출물 problem-definition-3slides/toseonsaeng-problem-definition-v3.html. v2·원본 덱·앱 코드·DB·운영 유지. Jira 없음.

- 2026-09-14 — 문제 정의 3장 구성 재설계 완료. YBM 인터뷰 분할형, 토스미 테스트 비교형, 커뮤니티/설문 수치형으로 구분하고 근거 출처·관찰·도출 문제를 명시. 원본 4~6장 근거 재대조와 3장 브라우저 검수 완료. 산출물 problem-definition-3slides/toseonsaeng-problem-definition-v2.html 및 기존 별도 HTML 갱신. 원본 덱·앱 코드·DB·운영 변경 없음. Jira 없음.

- 2026-09-14 — 발표 문제 정의 3페이지 HTML 제작 및 간소화 완료. YBM·경쟁사·커뮤니티/설문별 핵심 문제 2~3개와 결론 한 줄로 정리하고 세부 수치/조건은 대본에 유지. 원본 폰트·색상 유지, 3장 화면 확인 및 원본 해시 불변 검증. 산출물 problem-definition-3slides/toseonsaeng-problem-definition.html. 원본 덱·DB·앱 코드·운영 변경 없음. Jira 없음.

- 2026-09-14 — 사용자 요청으로 시연용 시험 1번 문항에 첫 재응시 retryCount=1, 3/3점 결과 1건 추가. 기존 0회차 2.5점 유지, 전사/피드백은 기존 결과 재사용. 두 문서 전체 대조로 저장 검증. 종합 160점·세션·Job·S3 음성·앱 코드 변경 없음. 실제 재녹음/채점은 수행하지 않은 시연용 기록. Jira 없음.

- 2026-09-14 — 사용자 제공 문제를 신속하고 일관된 실력 진단의 어려움과 감점 근거/목표별 연습 방향 부족이라는 발표용 두 문장으로 통합. 신규 조사·원본 자료·DB·앱 코드 변경 없음. Jira 없음.

- 2026-09-14 — 답변 음성 재생 경로 분석 완료를 현재 작업 WORKLOG EOF에 연결. MongoDB 피드백 복사에 S3 녹음은 포함되지 않았고 대상 시험 경로를 조회하는 코드 확인. 실제 S3 파일 상태와 직접 실패 원인은 미확정. 기록만 갱신, Jira 및 DB·음성·앱 코드·운영 변경 없음.

- 2026-09-14 — 시연용 답변 음성 문의에 대해 코드상 재생 URL이 대상 examId의 S3 temp 경로로 생성됨을 확인. 직전 MongoDB 결과 복사에는 음성이 포함되지 않아 원본 녹음이 연결되지 않는다. 실제 S3 객체 존재/무음/권한/만료는 미확인. 이번 작업은 설명·기록만 수행하며 음성·DB·앱 코드 변경 없음. Jira 없음.

- 2026-09-14 — 시연용 문항 결과 11건·파트 피드백 5개 복사 완료를 현재 작업 WORKLOG EOF에 연결. 대상 식별자 매핑 및 기존 160점/종합 요약 보존, 저장 결과 전체 대조 검증 유지. 이번 후속은 기록만 갱신하며 DB·앱 코드 추가 변경 없음. Jira 없음.

- 2026-09-14 — 사용자 승인으로 동일 mock_exam_004의 원본 시험 문항 결과 11건을 시연용 시험에 복사하고 요약의 5개 partFeedback만 교체. 대상 식별자·소유 관계로 매핑, 추가 선택에 따라 기존 160점과 나머지 종합 요약 유지. 결과 11건 전체 필드 및 요약 변경 범위 재조회 검증 완료. 원본·세션·Job·음성·앱 코드 변경 없음. Jira 없음.

- 2026-09-14 — 지정 시험 조회 완료를 현재 작업 WORKLOG EOF에 연결. 요청 계정 소유 완료 세션·요약 각 1건, 문항 결과 0건 및 요약 내부 설명 불일치 확인 유지. 원인 미확정. 이번 후속은 기록만 갱신하며 DB·앱 코드·운영 변경 없음. Jira 없음.

- 2026-09-14 — 로그인 후 지정 시험의 Atlas 실데이터 확인 완료: 요청 계정 소유 COMPLETED 세션 1건, exam_results 0건, exam_summaries 1건. 요약 점수/summary와 overallFeedback의 무발화·0점 안내도 상충. 원인 미확정, 읽기 전용 조회이며 DB·앱 코드·운영 변경 없음. Jira 없음.

- 2026-09-14 — 지정 계정의 단일 시험 요약·문항 결과 유무 조회는 Atlas 로그아웃으로 미완료. 로그인 화면 유지, 사용자 로그인 후 읽기 전용 확인 필요. exam_summaries/exam_results 및 legacy 요약 필드 구조만 확인했으며 실제 데이터 존재를 단정하지 않음. Jira 없음. DB·앱 코드·운영 변경 없음.

- 2026-09-12 — 설명 17분·시연 3분의 총 20분 대본 완료를 현재 작업의 WORKLOG EOF 기록에 연결. 본문 32장, 설명 4,042자와 총 1,200초 검증 결과 유지. 이번 후속은 기록만 갱신하며 Jira 이슈·산출물·앱 코드·DB·운영 추가 변경 없음.

- 2026-09-12 — 사용자 정정에 따라 설명 17분 + 시연 3분 = 총 20분 대본과 시간표를 script-20min에 별도 작성. 본문 32장·시연 원문 유지, 기술/성장 설명 보충, 공백 제외 4,042자. 총 1,200초·시연 180초·설명 1,020초 검증. 기존 17분 파일·원본 덱·앱 코드·DB·운영 변경 없음. Jira 없음.

- 2026-09-12 — index_v2 본문 32장을 시연 3분 포함 총 17분 대본으로 별도 편집. 설명 14분·약 3,371자(공백 제외), 시연 6단계 조작 안내와 슬라이드별 누적 시간표 제공. 숫자 발화 표기·주요 사실 한계 유지. 32장·총 1,020초·원본 해시 불변 검증, 실제 속도는 리허설 확인 필요. Jira 없음. 원본 덱·기존 추출본·앱 코드·DB·운영 변경 없음.

- 2026-09-12 — Downloads/index_v2.html의 본문 1~32번 발표자 notes를 원문 유지하여 index_v2_본문_발표대본.txt로 추출. 부록 9개 제외. section/metadata 순서·구간 대조, 대본 포함 및 원본 해시 불변 확인. Jira 없음. 원본 HTML·앱 코드·DB·운영 변경 없음.

- 2026-09-12 — 현재 앱 기능 문구 수정 완료를 현재 작업의 WORKLOG EOF 기록에 연결. 모의고사 응시·음성 답변 녹음/제출·AI 채점/요약 피드백 세 줄 및 접근성 설명 검증 결과 유지. 이번 후속은 기록만 갱신, Jira 이슈 및 앱 코드·DB·운영 변경 없음.

- 2026-09-12 — 단독 성장 그래프의 현재 앱 기능을 모의고사 응시 / 음성 답변 녹음·제출 / AI 채점·요약 피드백으로 간소화. 백엔드·테스트 설명 제거, 표시 및 접근성 문구 동기화 확인. 수치·일정·기존 PPT·앱 코드·DB·운영 변경 없음. Jira 없음.

- 2026-09-12 — 단독 성장 그래프에 9/23 Android 출시 예정·10/1 챌린지/보상/추천·10/17 취약 학습·11/1 맞춤 코칭을 날짜 아래 추가. 현재 핵심 기능 운영·iOS 출시·Android 심사 상태 및 TMI-126 기록 기준 챌린지 백엔드 구현/로컬 테스트 완료와 프론트·AI 연동/운영 검증 예정 구분. 8개 수치 불변·SVG/문구 정적 검증 완료, 브라우저 정책 제한으로 화면 미검증. 기존 PPT·DB·앱 코드·운영/Jira 변경 없음.

- 2026-09-12 — 완료 응시 성장 계획을 기존 덱과 분리한 16:9 단독 HTML(completion-growth-slide/completion-growth.html)로 제작. 실제 341건·11/30 목표 10,000건과 앞당긴 고도화 일정 표시. 8개 값의 CSV 대조 및 내장 글꼴·단독 파일 구성 검증 완료. 브라우저 URL 보안 정책으로 시각 미리보기 검증은 미완료. 원본 덱·DB·앱 코드 변경 없음. Jira 없음.

- 2026-09-12 — 최신 CSV를 읽어 7/27~11/30 전체 성장 일정표 12행을 대화로 제공했다. 실제 조회 시점 341건, 앞당긴 9/23·10/1·10/17·11/1 단계 및 최종 11/30 10,000건 목표 유지. 기존 산출물·DB 재집계나 변경 없음. Jira 이슈 키 없음. 기록만 갱신.

- 2026-09-12 — 통합 완료 응시 건수 일정표의 미래 단계를 9/23·10/1·10/17·11/1로 1~2주 앞당겼다. 목표 740·1,400·2,800·5,300건 및 11/30 10,000건, 실제 실적 341건은 유지. CSV/MD와 시나리오 설명 갱신, 실제 행·목표 불변 검증. Android 일정은 심사 통과 조건부. Jira 이슈 키 없음. DB·코드·운영 변경 없음.

- 2026-09-12 — 웹·앱 통합 완료 응시 건수 작업 완료를 현재 식별자의 WORKLOG EOF 항목에 연결했다. 웹 150건+앱 191건=341건 및 요약 생성 기준·사용자 중복 제거 없음·웹 응시일 귀속 한계를 유지한다. 통합 CSV/MD 산출물과 11/30 10,000건 목표 시나리오 유지. Jira 이슈 키 없음. 이번 후속은 기록만 갱신하며 DB·코드·운영 추가 변경 없음.

- 2026-09-12 — 사용자 결정에 따라 지표를 웹·앱 통합 누적 완료 응시 건수(요약 생성, 사용자 중복 제거 없음)로 교체했다. 웹 7/27 이후 150건 + 앱 8/23 이후 191건 = 조회 시점 341건. 명시적 웹 테스트 제외. 웹 날짜는 요약 생성 시간이 없어 응시일 기준, 앱은 완료 시각 기준이며 열람 지표는 아니다. 11/30 목표는 10,000건. 통합 일정표·일별 CSV와 설명 MD 생성 및 합계 검증. Jira 이슈 키 없음. DB·코드·운영 변경 없음.

- 2026-09-12 — DB 실적 기반 응시자 성장표 완료를 현재 작업의 WORKLOG EOF 항목에 연결했다. 앱 출시 이후 완료 응시자 55명과 웹·앱 통합 집계 불가 한계, 11/30 1만 명 목표 시나리오 및 최근 추세 약 326명의 구분을 유지한다. 기존 CSV/MD 산출물 유지. Jira 이슈 키 없음. 이번 후속은 기록 문서만 갱신하며 DB·코드·운영 추가 변경 없음.

- 2026-09-12 — Atlas 실제 집계로 응시자 성장표 갱신. iOS 8/23 출시 이후 완료 이력이 있는 고유 계정 55명(8/31 17명, 9/6 38명), 전체 완료 계정 68명은 출시 전 기록 포함. 고정 개발 계정 제외, 기타 테스트 계정 미식별. 웹 결과에는 사용자 식별자가 없어 통합 합산 불가. Android 9월 출시 가정. 11/30 1만 명은 일 6.81% 누적 성장이 필요한 목표 시나리오이며 최근 7일 단순 추세는 약 326명. 표·일별 실제 지표 CSV 및 설명 MD 저장. Jira 이슈 키 없음. DB·코드·운영 변경 없음.

- 2026-09-12 — 성장 일정표의 11/30 목표를 사용자 결정에 따라 누적 응시자 10,000명으로 구체화했다. 중복 없는 모의고사 1회 이상 완료자를 임시 집계 기준으로 제안하며 완료 기준은 추가 확정 전이다. 9/30 1,000 → 10/15 2,500 → 10/31 5,000 → 11/15 7,500 → 11/30 10,000명과 기능·유입 활동은 초안. 기존 PoC 완료자 82명은 과거 기간 실적으로 별도 표시. 실제 출시일·현재 실적 답변 대기. Jira 이슈 키 없음. 코드·운영 변경 없음.

- 2026-09-12 — 11월 30일 사용자 10,000명 달성을 위한 출시·고도화·유입 활동 일정표 초안 제안. 중간 목표는 9/30 1,000 → 10/15 2,500 → 10/31 5,000 → 11/15 7,500명이며 기능 배치와 함께 잠정 제안이다. 사용자 지표 정의·실제 출시일·현재 실적·확정 고도화 일정 답변 대기. 웹 PoC 663명은 방문자 지표로 별도 취급. Jira 이슈 키 없음. PPT·코드·운영 변경 없음.

- 2026-09-12 — 사용자 요청에 따라 `index_v01_발표대본.txt`를 본문 1~25번 대본만 포함하도록 갱신했다. 부록 11개 제외, 본문 문구 및 원본 HTML 유지. 25개 슬라이드 확인. Jira 이슈 키 없음. 앱 코드·운영 변경 없음.

- 2026-09-12 — 로컬 `index_v01.html`의 발표자 노트를 슬라이드 순서대로 `index_v01_발표대본.txt`에 추출했다. 본문 25장·부록 11장 전체 36장, 대본 원문 유지. section/metadata 번호·노트 포함 및 입력 파일 해시 불변 확인. Jira 이슈 키 없음. 원본 자료·앱 코드·운영 변경 없음.

- 2026-09-11 — `메모 반영본 0911`의 SEO/GEO 조치 내용을 요약+6개 번호 항목으로 재작성하고 핵심 제목 굵기·항목 간격을 적용했다. 수정 구간 화면 및 DOCX 내용·서식 검증, 표 18개·그림 21개 보존 확인. 원본 탭·댓글 편집 없음. Jira 이슈 키 없음. 앱 코드·운영 변경 없음.

- 2026-09-11 — 중간보고서 원본을 복제한 새 문서 탭 `메모 반영본 0911`에 본문 연결 메모 10개 반영 완료. 핵심 요구·시장 수치·배포 상태와 B2C 활용방안을 정리하고 BM/체험 프로모션/리텐션을 분리했다. 복제 기준 표 19개·그림 24개 및 media 해시 보존, 원본/새 탭의 수정 분리와 저장 상태 확인. 원본·댓글은 직접 수정하지 않았고 공동 편집자의 원본 변경은 유지. 관련 Jira 없음. 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-11 — 사용자 제공 모의고사/채점·피드백 단계표를 단일 범주의 기획·분석·설계·개발·테스트·완성 6단계로 통합해 제공. 문구 편집이며 신규 사실 검증·원본 문서·Jira·앱 코드·운영 변경 없음. Jira 이슈 키 없음.

- 2026-09-11 — 제공된 `ex_ba4228d776_0905_1806` 전체 로그를 대조한 결과 Q1~Q10은 각각 `dispatchAttempt=1` 전송·Callback·Job 완료됐지만 마지막 Q11 Job `question:ex_ba4228d776_0905_1806:11:0`은 2026-09-05T18:24:13Z AI HTTP 500으로 실패한 뒤 attempt 2/3, 성공 전송, Callback, 완료 이력이 없다. 따라서 이 시험도 최종 Summary·완료에 도달한 것으로 볼 수 없다. 앞 사례와 달리 첨부 범위에는 Session `ABANDONED` 로그가 없어 이후 상태는 단정하지 않지만, 당시에는 Q11 `FAILED/QUESTION_DISPATCH_FAILED`가 남은 미완료 시험이다. `grading/retry` 호출 증거도 없다. 관련 구현 이력은 `TMI-25`이며 코드·Jira·AWS는 변경하지 않았다.

- 2026-09-11 — 제공된 `ex_a78a067692_0906_1321` 전체 로그를 대조한 결과 Q5 Job `question:ex_a78a067692_0906_1321:5:0`은 2026-09-06T13:28:32Z `dispatchAttempt=1` AI HTTP 500 실패 이후 재전송 성공·Callback·완료 이력이 없다. Q6~Q11의 attempt 1 성공은 각각 별도 Job이며 Q5 복구 증거가 아니다. 2026-09-07T04:13:26Z 새 시험 생성으로 해당 Session이 `ABANDONED/new_session_started` 처리돼 이 시험은 완성되지 않았다. `GRADING_MAX_DISPATCH_ATTEMPTS=3`은 자동 retry 횟수가 아니라 `POST /api/v1/exams/{examId}/grading/retry` 호출 시 허용되는 상한이고, 현재 코드에는 Question Job 자동 retry scheduler가 없다. 관련 구현 이력은 `TMI-25`이며 코드·Jira·AWS는 변경하지 않았다.

- 2026-09-11 — 문항 채점 재전송 이력 검색 기준을 확인했다. 최초 전송과 재전송은 별도 HTTP 요청이므로 `traceId`와 `requestId`가 달라질 수 있지만 결정적 Question Job ID와 AI `Idempotency-Key`는 `question:{examId}:{questionNumber}:{retryCount}`로 유지되고 `dispatchAttempt`만 기본 최대 3까지 증가한다. 따라서 전체 재전송 이력은 Learning Core에서 `jobId`, AI에서 동일 `Idempotency-Key` 또는 `user_id=examId`로 검색하고, `traceId`는 개별 요청 내부 추적에만 사용한다. 현재 로그 pattern은 `requestId`만 기본 출력하므로 traceId 단독 검색은 신뢰할 수 없다. 관련 구현 이력은 `TMI-25`이며 코드·Jira·AWS는 변경하지 않았다.

- 2026-09-11 — 2026-09-05/06 staging의 두 `QUESTION_DISPATCH_FAILED` 로그를 코드 기준으로 진단했다. 두 요청 모두 S3 다운로드 이후 AI `/evaluations` POST 단계에서 각각 약 107ms·124ms 만에 `HttpServerErrorException.InternalServerError`, 즉 AI 서버의 HTTP 500 응답을 받았다. 따라서 Learning Core 연결 실패·S3 다운로드 실패·read timeout으로 볼 근거는 없고 AI 서비스 내부 예외가 직접 원인이다. 현재 Learning Core는 AI 500 응답 body를 의도적으로 기록하지 않아 세부 원인은 같은 UTC 시각의 AI task 로그 대조 없이는 확정할 수 없다. Job은 `FAILED/QUESTION_DISPATCH_FAILED`가 되고 `POST /api/v1/exams/{examId}/grading/retry`가 기본 최대 3 dispatch attempt 안에서 복구한다. 관련 구현 이력은 `TMI-25`이며 코드·Jira·AWS·배포는 변경하지 않았다.

- 2026-09-11 — 대시보드 기간 표시 완료를 현재 작업 식별자의 WORKLOG EOF 항목에 연결했다. `2026.07.27~현재 (2026.09.11 확인)`과 한 열 배치·124개 집계의 최종 시각 확인 결과 유지. 관련 TMI-4는 최초 생성일 조회 근거이며 변경 없음. 이번 후속은 기록 문서만 갱신했다.

- 2026-09-11 — TMI 대시보드 10001에 전체 기간 `2026.07.27~현재 (2026.09.11 확인)` 표시. 최초 TMI-4 생성일을 오름차순 검색으로 확인했고, 제목 가독성을 위해 한 열로 변경했다. 전체 124개·날짜 제한 없는 필터 및 공유 권한 유지. TMI-4 조회 근거, 이슈 수정 없음. 최종 화면 검증 완료.

- 2026-09-11 — TMI 전체 기간 대시보드 생성 완료를 현재 작업 식별자의 WORKLOG EOF 기록에 연결했다. 필터 10002·대시보드 10001·상태 파이 차트 10004, 전체 124개(완료 102·진행 중 3·해야 할 일 19) 및 나만 보기 상태 유지. 특정 Jira 이슈 키 없음. 이번 보완은 기록 문서만 갱신하며 Jira 추가 변경 없음.

- 2026-09-11 — 사용자 요청으로 TMI 전체 기간 필터 10002와 상태 파이 차트 대시보드 10001 생성 완료. 기본 비공개·나만 권한, 날짜 제한 없는 전역 JQL 기반 전체 124개(완료 102·해야 할 일 19·진행 중 3) 표시 확인. 기존 이슈/요약 화면 유지. 특정 Jira 이슈 키 없음, 앱 코드·계약·운영 변경 없음.

- 2026-09-11 — Jira TMI 요약 화면의 최근 7일 카드와 완료 최근 2주 제한을 실제 UI에서 확인. 전체 기간은 날짜 제한 없는 목록/JQL 및 저장 필터 대시보드로 안내. 전체 목록 113개 중 50개 표시 확인. 특정 Jira 이슈 키 없음, Jira·앱 코드·외부 계약·운영 변경 없음.

- 2026-09-11 — 사용자 요청으로 당근 색감 수정본 HTML과 글꼴 5개를 Downloads/toseonsaeng-daangn-colors-v3/에 저장 완료. 원본 대비 SHA-256 일치 확인, 내용·디자인 변경 없음. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-11 — 당근 HTML 원래 색감 복원본 `toseonsaeng-daangn-colors-v3.html` 생성. 흰색·중립 회색·초록색 등 최초 팔레트를 복원하고 주황색만 현재 템플릿 계열 유지. Jua/Gothic A1과 30장 내용·구성 보존. 폰트/본문 동일성 및 팔레트 정적 검증 통과, 시각 검수 미완료. 이전 파일 보존. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-11 — 당근 HTML 글꼴 미반영 피드백에 따라 `toseonsaeng-daangn-fonts-v2.html` 새 파일 생성. Jua/Gothic A1 버전별 CSS 이름과 명시적 제목 지정, 독립 TTF + 내장 fallback 적용. 이전 글꼴 데이터는 정상이었으며 표시 실패 원인은 미확정. 30장 전체 본문·노트·스크립트 동일성 확인, 브라우저 정책 차단으로 시각 검수 미완료. 이전 산출물 보존. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-11 — 당근 HTML의 글꼴·색감을 사용자 deck-template.html에 맞춰 수정. Jua 제목/대형 수치와 Gothic A1 본문을 HTML에 내장하고 크림·남색·오렌지 팔레트 적용. 30장 구성·내용·이미지·노트 유지, 수정 전 파일 백업. 데이터/폰트/색상 정적 확인 완료. file:// 브라우저 보안 차단으로 새 글꼴 적용 후 시각 검수 미완료. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-10 — Daangn HTML 시안 제작·30장 검증 완료 상태를 현재 작업 식별자의 WORKLOG EOF 항목에 연결했다. 기존 산출물과 검증 결과 유지. Jira 없음. 기록 문서 외 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-10 — 토선생 Daangn 스타일 독립 HTML 완료. 본문 22장·19분 30초와 부록 8장, 원본 내용·수치·발표 노트를 유지하고 대화형 고민·단계별 학습·목록형 이용권·표본 비율 중심으로 새롭게 구성했다. 30장 시각/넘침 검사와 표지 이미지 가림 방지, 발표·갤러리·노트·계산기 확인 완료. 기존 보고서/HTML 보존. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-10 — 토선생 Musinsa 스타일 독립 HTML 완료. 본문 22장·19분 30초와 부록 8장, 내용·수치·발표 노트를 보존하고 흑백 대비·굵은 제목·순위 목록형 설문·화보형 서비스 화면·이용권 가격표 등으로 새롭게 구성했다. 30장 시각/넘침 검사와 발표·갤러리·노트·계산기 확인 완료. 기존 보고서/HTML 보존. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-10 — 토선생 Samsung Pay 스타일 독립 HTML 완료. 본문 22장·19분 30초와 부록 8장, 원본 내용·수치·노트를 유지하고 다크 캔버스·카드 스택·큰 지표·36개 응답 분포·비례 막대 등 구성을 새로 제작했다. 30장 시각/넘침 검사와 발표·갤러리·노트·계산기 확인 완료. 기존 보고서/HTML 보존. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-10 — 토선생 Ant Design 독립 HTML 발표자료 완료. 본문 22장·19분 30초와 부록 8장, 내용·수치·발표 노트를 보존하고 표·설명 목록·단계·타임라인·만족도 도넛·시나리오 비교로 구성을 재설계했다. 30장 시각/넘침 검수와 발표·갤러리·노트·계산기 검증 완료. 기존 보고서/HTML 보존. Jira 없음, 앱 코드·외부 계약·운영 변경 없음.

- 2026-09-10 — Cloudflare HTML v2 구성 전면 재설계 완료. `toseonsaeng-cloudflare-v2/toseonsaeng-cloudflare-v2.html`, 본문 22장+부록 8장/19분 30초 유지. 조사 대시보드·사용자 차트·AI/코드 흐름도·월별 일정 행렬·손익 계산식·실험 보드로 원본 및 Nintendo v2와 다른 본문 구조 적용. 30장 시각/배치·발표/계산 기능 검증, 기존 버전 보존. 관련 Jira 없음, runtime·계약·운영 변경 없음.
- 2026-09-10 — 색상 변경만으로 디자인이 유사하다는 피드백에 따라 Nintendo HTML v2 페이지 구성을 전면 재설계했다. `toseonsaeng-nintendo-v2/toseonsaeng-nintendo-v2.html`, 본문 22장+부록 8장/19분 30초와 노트·수치·근거 유지. 질문·단계 흐름·앱 전시·점 도표·전환 막대·넓은 시연·세로 요금 등으로 본문 22장 모두 재구성. 30장 시각/배치/발표 기능 검증 완료. 기존 버전 보존. 관련 Jira 없음, runtime·계약·운영 변경 없음.
- 2026-09-10 — Nintendo HTML 30장 제작 완료 기록을 현재 turn 식별자와 연결했다. 산출물과 기존 버전 보존·검증 결과는 아래 Nintendo 항목과 동일하며 이번 보완은 종료 기록만 갱신했다. 관련 Jira 없음.
- 2026-09-10 — Nintendo 가이드 기반 토선생 HTML 별도 버전 완료. `toseonsaeng-nintendo/toseonsaeng-nintendo.html`, 기존 30장 내용/이미지를 유지하고 레드 헤더·둥근 타일·흰 여백 적용. 30장 시각 검토·넘침 검사·발표/노트/갤러리·계산기 확인 완료. 기존 듀오링고/Cloudflare 파일 hash 불변. 관련 Jira 없음, runtime·계약·운영 변경 없음.
- 2026-09-10 — 첨부 Cloudflare 가이드 기반 토선생 HTML 발표자료 별도 버전 완료. `toseonsaeng-cloudflare/toseonsaeng-cloudflare.html`, 기존 내용/이미지 그대로 본문 22장+부록 8장. 오렌지·블루·얇은 테두리와 다크 표지/기술/마무리를 적용했다. 30장 시각 검토·겹침 검사·발표/노트/갤러리·계산기 확인 완료. 기존 듀오링고 파일 hash 불변 확인. 관련 Jira 없음, runtime·계약·운영 변경 없음.
- 2026-09-10 — 듀오링고 가이드·첨부 실제 내용 기반 토선생 HTML 발표자료 제작 완료. `toseonsaeng-duo/toseonsaeng-presentation.html` 단일 파일, 본문 22장/19분 30초와 부록 8장. 30장 시각 검토·넘침 보정·발표 모드·갤러리·노트·손익 계산 검증 완료. 시연 영상 미첨부(11페이지 파일 선택), 검색 비중 49.9%/42.9% 집계 기준 확인 필요. 기존 보고서/PPT/HTML 보존. 관련 Jira 없음, runtime·계약·운영 변경 없음.
- 2026-09-10 — 첨부 당근 디자인 가이드 기반 HTML 발표 시안 6개 레이아웃 제작 완료. `daangn-html/daangn-presentation.html` 단일 파일에 표지·목차·본문/이미지·3열 비교·빈 표·마무리를 구성하고 실제 내용 대신 자리표시자만 포함했다. 전체 보기·자리표시자 토글·발표 모드·키보드 이동을 브라우저에서 확인했다. 첨부 일러스트 원문과 기존 보고서/PPT를 보존했다. 관련 Jira 없음, runtime·외부 계약·운영 변경 없음.
- 2026-09-10 — 내용 없는 PPT 디자인 시안 5종 제작 완료. 오빠두 갤러리의 Toss·Claude·Linear·Inflearn·29CM 색상/서체 인상을 참고해 각각 표지·본문/이미지·빈 표 3장으로 구성했다. visualization 작업 폴더 ppt-designs/output에 편집 가능한 최종 PPTX 5개, 비교 이미지, HTML 미리보기, ZIP을 제공한다. 실제 발표 내용 없이 자리표시자만 포함하며 기존 보고서/PPT는 보존했다. 최종 파일 검사와 15장 렌더링/시각 검토 완료. 관련 Jira 없음, runtime·계약·운영 설정 변경 없음.
- 2026-09-10 — 중간보고서 축약본을 같은 Google Docs의 새 문서 탭 `중간보고서 축약본 26~27페이지`(t.ihn64vmsok1x)에 작성했다. PDF 기준 33→27페이지이며 원본 텍스트·이미지 보존을 대조했다. 표 19개·그림 22개와 핵심 근거를 유지하고 중복 서술·문단 간격·목차를 정리했다. 관련 Jira 없음. 멘토 의견 작성 및 출력 환경 변경 뒤 페이지 재확인이 필요하다. 저장소에는 작업 기록만 추가했으며 runtime·외부 계약·운영 설정 변경은 없다.
- 2026-09-09 — 토선생 중간발표 디자인 분석: 오빠두 갤러리 242개 브랜드 목록과 첨부 PDF 37페이지를 확인해 Toss·Inflearn·Claude·Apple HIG·Duolingo·Daangn·Linear·Stripe·Notion·Google Material Design 10개를 추천했다. 최우선은 Toss의 정보 위계/여백에 기존 딥그린·오렌지·토끼 캐릭터를 적용하는 방향이며 Inflearn/Claude가 대안이다. 추천 순위는 발표 적합성 판단이다. 관련 Jira 없음. 원본 PDF/PPT·runtime·외부 계약·운영 설정은 변경하지 않았으며 PPT 제작은 미수행이다. 코드 없는 분석으로 Gradle은 실행하지 않고 PDF 렌더링/시각 검토와 문서 whitespace·append/marker 검증을 수행했다.
- 2026-09-09 — TMI-128 종료 후 다음 작업을 검토했다. 로컬 현재 branch=develop, HEAD=16eb5de(PR #31 merge), 구현 commit89ab584를 확인했다. 체크리스트의 Reservation reconciliation 미착수 표시는 오래된 상태다. 무료시험·Challenge Learning Core 기반 이후 즉시 우선순위는 default-off 기능의 migration/인증/Lattice/AI·모바일 staging 통합 검증과 Sentry 실제 수신 확인이다. 다음 신규 기능 개발 트랙은 기간제 유료 이용권 연동 계약 검토이며 Billing이 결제·권리 판정을 소유하고 Learning Core는 승인된 시험 실행 경계를 반영한다. 타 저장소 현재 구현·원격 CI·배포는 이번에 새로 확인하지 않았으므로 Identity reader token/기간제 미구현 기록은 착수 전 재확인한다. 신규 계획서/Jira/runtime 작업은 시작하지 않았다.
- 2026-09-09 — 사용자 요청으로 TMI-128 `[Learning Core] Billing Reservation 장애 자동 복구 및 Sentry 경보 구현`을 해야 할 일에서 완료로 전환하고 Jira 재조회로 확인했다. 종료 댓글10082에 구현·5분 조기 경보·이전 검증 결과와 운영 활성화 별도 경계를 기록했다. 기본 OFF, 신규 index/drain/staging E2E·경보 재시도 운영값·Sentry 실제 수신 확인은 남는다. 이번 작업은 Jira/기록만 변경했고 코드·merge·배포 상태는 새로 검증하거나 변경하지 않았다. 테스트는 재실행하지 않았다.
- 2026-09-08 — 문항별 채점과11문항 종료 후 채점 대조 실험을 완료했다. 같은 앱 HEADcb5f6ee+기존 dirty 코드, 동일 submit/접수·모의 AI 자원에서 AI 실행 시작 gate만 변경했다. 가정 응시618초를20배 압축(30.9초), 문항 연산0.6~2.2초, worker4/16·단일/6시험/중단 조건을 각3회 실행했다. 24실행·60흐름(완주48/중단12)·588submit, 완주48개 모두 완료·제출/Callback 오류0. worker4 중첩6시험의 종료후 대기 p95 중앙값21.896→3.424초(84.4%감소), 단일5.444→2.843초(47.8%감소), worker16단일2.801→2.821초로 단축 없음. 대기 queue 최대56→7개, 중단6시험/군의 모의 문항 연산 시작은 문항별30/종료후0이다. [결과·조건·보고서 문구](GRADING_START_COMPARISON_RESULTS.md).
- 실행기/모델/단위 테스트/집계기4개와 앱 전용 비교 빌드 task·README·결과/원시JSON·기록만 추가/갱신했다. `clean test`543개, Node 모델6개, smoke/main 및24행 집계·gate/중복/시간/동시성 assert 통과, 소스hash5개 일치·프로세스 종료 확인. 기존 build 로그/테스트 보고서는 clean으로 재생성됐고 이전 측정JSON은 보존했다. 제품runtime·외부 API/AI/S3/Redis/Billing 계약·운영/웹/AI 원본 변경 없음. 관련 TMI-25는 배경이며 신규 Jira 변경 없음. 압축 시간에 HTTP/DB비용은 비례 축소되지 않아 실제 운영 시간/비용 개선율로 환산하지 않으며 실제 앱 시간표·AI동시성·중단율 검증이 남는다.
- 2026-09-08 — 보고서 5개 항목 재작성 완료를 현재 turn 식별자로 WORKLOG EOF에 연결했다. 문제·구조 변경 조치·기존 실험 결과·남은 검증을 구분한 최종 문구를 대화로 제공했다. 관련 TMI-25·TMI-128 배경이며 문서 기록 외 신규 구현·실험·운영/Jira 변경은 없다.
- 2026-09-08 — 보고서용 실험 성과 5개 항목 전체를 구조 변경 중심으로 재작성했다. ‘조치’는 비동기 접수 분리·Job 기반 중복 방지·필수 문항 완료 gate·선택 재시도·Reservation 상태 우선 복구/동시성 보호 구현을 설명하고, 실험 조건·기존 측정 수치는 ‘결과’에 배치한다. 신규 실험이나 제품 변경은 없으며 운영 지표/비용 절감/복구율과의 구분 및 Reservation 기본 OFF를 유지한다. 관련 TMI-25·TMI-128 배경, Jira 변경 없음.
- 2026-09-08 — 동기/비동기 ‘조치’ 문구 수정 완료를 현재 turn 식별자로 WORKLOG 끝에 연결했다. 설명·기록만 변경했으며 신규 실험·runtime 구현·배포·Jira 변경은 없다. 관련 TMI-25 배경과 과거 배포 시점 미확인 경계를 유지한다.
- 2026-09-08 — 동기/비동기 보고서의 ‘조치’를 실험 절차가 아닌 기존 구조 변경 중심으로 다시 작성했다. 채점·Callback 완료 후 응답하던 구조에서 접수 응답을 먼저 반환하고 채점 결과는 Callback/Polling으로 전달하는 분리를 설명한다. 접수 HTTP까지 완전 non-blocking이라는 주장은 하지 않는다. 과거 배포 시점은 여전히 미확인이고 신규 구현·실험·Jira 변경은 없다. 관련 TMI-25는 배경이다.
- 2026-09-08 — 문항별 즉시 채점과 11문항 종료 후 채점의 비교를 권장했다. 동기/비동기와 별개인 채점 시작 시점 비교로, 두 군 모두 비동기 접수·동일 업로드 시점/채점 로직/AI 자원/요약 조건을 유지하고 실제 응시 간격을 반영하는 제안이다. 주지표는 마지막 제출→최종 결과 p50/p95이며 종료 시 완료 문항 수·AI 대기/동시 실행·오류·중도 포기 시 선행 채점 수를 함께 측정한다. 기존 실험의 30ms 제출은 이 효과를 측정하지 않았다. 신규 실험은 미실행이며 대기 시간 단축은 아직 가설이다. 관련 TMI-25는 배경이고 신규 Jira/변경 없음. 설명과 기록만 수행했다.
- 2026-09-08 — 기존 실험 결과를 소마 보고서용 5개 항목(비동기 접수, 중복 AI 전송 방지, 완료 상태 정합성, 실패 문항 선택 재시도, Reservation 복구 안전성)으로 대화에서 정리했다. 각 항목은 문제·조치·결과·남은 것과 근거 링크를 포함한다. 근거는 [동기/비동기](AI_SYNC_ASYNC_COMPARISON_RESULTS.md), [웹/앱 비교](POC_APP_COMPARISON_RESULTS.md), [로컬 복구 검증](LOCAL_RECOVERY_MEASUREMENT_RESULTS.md)이며 신규 실험은 실행하지 않았다. 운영 성과와 재현 실험, HTTP 전송과 과금, 접수와 최종 결과 시간, 테스트 통과와 운영 복구율을 구분했다. 관련 구현 배경 TMI-25·TMI-128, Jira 변경 없음. 이번 파일 변경은 CURRENT_STATE·WORKLOG 기록뿐이며 runtime·계약·운영 설정은 유지한다.
- 2026-09-08 — AI 응답 지연의 동기/비동기 대조 실험 완료. GitHub 읽기 전용으로 AI fd43a983c9b9의 평가+기본 동기 Callback 후 응답, 1134e1c5f454의 Redis queue 등록/202 경로 추가를 확인했다. 실제 배포 날짜·환경은 미확인이다. 같은 웹89c8f9d와2초 모의채점/64worker로 HTTP 응답 순서만 대조한12개 실행(3회반복),48시험·528submit을 기록했다. 낮은 부하 submit p95 중앙값2071.37→62.15ms, 최종결과2630.65→2624.90ms. 중첩 부하 submit오류198/198→0/198, worker p9520→2, 관찰내 시험완료15/18→18/18. [결과](AI_SYNC_ASYNC_COMPARISON_RESULTS.md), [과거 소스·재현 한계](AI_SYNC_ASYNC_HISTORICAL_EVIDENCE.md).
- 이번 실험은 과거 배포 전체가 아닌 소스 기반 HTTP 순서 재현이며 당시 Python serial lock/event loop/AI worker수·실제모델 latency는 재현하지 않았다. 동기군의 제출실패와 결과소실은 구분하며3시험은15초 관찰내 미완료다. 실행기/집계기·문서/JSON·기록만 수정/추가, 제품runtime/웹·AI원본/외부계약/운영설정 유지, 기존dirty 보존. smoke/main exit0·Node syntax·집계 assert·링크/diff 검증과 실험프로세스 종료 확인. Gradle은이번에 재실행하지 않았다. 관련 TMI-25는 기존 구현 설명 배경이고 실험전용/당시사건 Jira는 미확인, Jira 변경 없음.
- 2026-09-08 — 사용자 요청으로 웹 POC89c8f9d와 앱cb5f6ee+기존 dirty 코드의 공통 모의고사 HTTP 흐름을 실제 Controller/Service·격리 Mongo/Redis·모의 AI로 비교했다. 5조건×2버전×3회=30개 실행, 시험1,272개 최종 완료(별도 warm-up 제외), HTTP/Callback 오류와 deadline 초과0. 중복 조건120시험/버전에서 AI 문항 전송2,640→1,320회, Summary240→120회; 순서 역전12시험/버전의 조기 완료 표시12→0건. 정상15시험/s의 submit p95 중앙값55.16→61.43ms, worker 평균 중앙값7.82→9.41로 앱 속도/최대 처리량 향상은 입증하지 못했다. [결과·한계·원시 자료](POC_APP_COMPARISON_RESULTS.md). 관련 TMI-25, Jira 변경 없음.
- 비교 실행기/집계기·결과/원시JSON·작업 기록만 추가/갱신하고 application runtime·기존 테스트·웹 원본·외부 계약·운영 설정을 유지했다. 로컬 웹 status clean 확인. 일반 test543개 재실행 모두 통과, Node syntax/30행 집계 assert/diff 검증 통과. JVM CPU hard limit·production 전체 wiring·Identity/Billing/JWT·실제 AI 성능은 제외했으며 다음 측정에는 별도 SLO·독립 자원·긴 warm-up이 필요하다. 실험 Java/Node 잔존 프로세스 없음 확인, runtime 배포 불필요. 기존 미측정/방향 제안 기록은 이전 시점 이력이다.
- 2026-09-08 — POC 대비 앱 종합 안정성 비교 권장 설명을 현재 turn 식별자로 WORKLOG EOF에 연결했다. 관련 TMI-25, 비교 기준 확정 대기이며 아직 실험·개선율 측정은 수행하지 않았다. 종료 기록 문서만 갱신하고 runtime·외부 계약·운영·Jira는 유지했다.
- 2026-09-08 — 보고서용으로 웹 POC 대비 앱 백엔드의 종합 안정성·처리 능력 비교를 권장했다. 공통 모의고사 흐름에서 완료 시험 처리량·실패/timeout·최종 결과 완료 p95를 주지표로, API 응답과 요청 스레드를 보조지표로 삼는다. 동일 총자원/AI 처리조건에서 부하를 단계적으로 올리고 반복 측정하는 제안이며 실제 개선은 미검증이다. 설명과 기록만 수행했고 웹 조회·실험 실행은 하지 않았다. 관련 TMI-25, Jira 변경 없음.
- 2026-09-08 — 웹 POC/앱 성능 비교 설명의 종료 기록을 현재 turn 식별자로 WORKLOG EOF에 연결했다. 관련 TMI-25, 기준 버전·실험 조건 확정 대기 상태를 유지한다. 이번 동기화는 문서만 변경하며 코드·외부 계약·운영·Jira 변경이나 테스트 재실행은 없다.
- 2026-09-08 — 웹 POC와 앱 백엔드 비교는 제품 발전의 종합 비교로 적합하되 비동기화 단독 효과를 입증하지는 않는다고 설명했다. 웹 기준 버전의 실제 동기 처리 여부를 먼저 확인하고, 동일 시험 workload·자원·AI stub 지연 아래 요청 접수와 최종 결과 완료 시간을 분리해 측정하는 방향을 권장했다. 이번에는 웹 저장소 조회·실험·runtime 변경 없이 작업 기록만 갱신했다. 관련 TMI-25, 신규 Jira 변경 없음.
- 2026-09-08 — 보고서용 로컬 정량 검증(TMI-25·128): 11문항 fixture 4개(실패 0/1/3/11)에서 완료 문항 재전송과 즉시 재요청 추가 전송 각각 0회 확인. 일반 test 전체 543개 및 Reservation 격리 Mongo 통합 테스트 대상 31개를 이번에 재실행해 모두 통과했다. 31개는 복구·격리·안전장치 검증이며 운영 복구율이 아니다. API p95/스레드 전후 비교·실제 복구 시간은 미측정이다. [실행 결과·근거·후속 측정 조건](LOCAL_RECOVERY_MEASUREMENT_RESULTS.md). 테스트 4개 case와 결과/기록 문서만 보강했고 runtime·외부 계약·운영 설정·Jira는 변경하지 않았다. 기존 dirty 변경을 보존했다.
- 2026-09-08 — TMI-128 생성 차단 5분 조기 Sentry 경보 사용자 승인·구현·로컬 회귀 검증 완료. 운영 활성화는 별도다.
- 2026-09-05 최종 상품 정책을 재확정했다. credit 상품·잔액·grant·소비·충전 API는 제품에서 완전히 제거하며 첫 구매 2배도 후속 후보가 아니라 완전히 제거한다. 유료 상품은 Billing 검증 `CAPTURED` 시점부터 24·72·168·336·720시간 사용하는 5종 비자동갱신 무제한 이용권만 유지하고, 검증 phone당 무료시험 `FREE_EXAM_ONCE`는 별도 권리로 유지한다. 출석 연장·연속 로그인·추천인·coupon은 이후로 미루되, 다시 도입하려면 폐기된 credit 계약을 재사용하지 않고 새 계약을 확정해야 한다. 관련 기반 Jira는 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`이며 유료 기간제 이용권 구현 전용 Jira는 아직 없고 Jira는 변경하지 않았다.
- 2026-09-07

## Current branch

- `develop` — 2026-09-09 로컬 HEAD `16eb5de`, PR #31 병합 확인

## Latest development scope update — 2026-09-08

- TMI-128: 사용자가 **5분 이상 지속 시 경보**를 승인해 구현했다. 최초 operation.createdAt 기준 5분·activeGuard 유지·검증 편입된 non-terminal READY/IN_FLIGHT를 대상으로, 업무 lease가 비어 있는 다음 poll에서 `creation_blocked_5m` incident를 원자 등록한다. operation당 조기 incident는 한 번만 생성하며 `recovery.earlyAlert.*`에 독립 보존해 재시작·SDK 실패·후속 격리에서 덮어쓰지 않는다. 후속 격리·auth circuit 경보와는 별도 lane이고 이미 격리 경보가 있거나 전역 인증 차단 중이면 신규 조기 incident를 억제한다. poll당 SDK capture는 최대 3건이다. 아래의 5분 미승인 기록은 이전 시점 이력이다.
- 이번 조기 경보는 업무 state·activeGuard·복구 retry 일정/횟수·24시간/200회 격리 기준을 바꾸지 않는다. 5분은 후보 기준이며 poll/lease/대기열/SDK 지연이 추가될 수 있다. SDK_ACCEPTED는 실제 Sentry 전달/담당자 수신 완료가 아니고 crash 경계 중복 접수는 가능하다. 자동 차단 해제·새 예약 우회·운영 repair API는 추가하지 않았다.
- 최신 전체 검증: `./gradlew clean test` **539개**, `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest` **71개**, `node --test scripts/mongodb/*.test.js` **105개** 통과(실패/오류/skip 0). Docker API compatibility 값은 격리 테스트 프로세스에만 적용했다. 4분59초/5분 경계·다중 poller·재시작·격리 병존·SDK 실패/소진·OFF/완료/legacy/auth 제외·batch/lease·privacy 및 인덱스를 검증했다. `git diff --check`도 통과했다.
- 활성화 전 신규 `reservation_recovery_early_due`/`reservation_recovery_early_pending` index와 기존 drain/staging gate, 미확정 alert-max-age/alert-max-attempts 운영값, Sentry Alert Rule·수신자·실제 수신을 확인한다. 기본 OFF이며 공개 API/AI/Billing/S3/Redis/Challenge 계약은 그대로다. [TMI-128 RUNBOOK](BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md)과 계획서 §5.6을 갱신했다. 기존 사용자/선행 구현 dirty 변경을 보존했으며 Billing/Identity·운영 DB/AWS/Sentry 설정·Jira·commit/push/배포는 변경하지 않았다.
- TMI-128 Sentry 운영 대응 방향의 종료 기록을 현재 turn 식별자로 WORKLOG에 연결했다. 기존 차단/운영자 확인 방식을 유지하며, 5분 정체 조기 경보는 아직 제안 상태다. 이번 동기화는 문서만 변경하고 코드·운영 설정·Jira는 변경하지 않았다.
- TMI-128 장기 차단 대응은 사용자 의견에 따라 현재의 안전 차단·Sentry 경보·운영자 확인 방향을 유지한다. 직전 제안한 자동 재개/안전 재시작/예외 이용은 채택하지 않았다. 일반 통신 장애는 최초 오류가 아니라 retry 소진 시 경보 대상이 되므로, 빠른 운영 대응을 위한 정체 조기 경보는 별도 제안/승인 대상으로 구분했다. runtime·Sentry 운영 설정은 변경하지 않았다.
- TMI-128 장기 차단의 가용성 대안을 검토했다. 일시 장애/재시도 소진만 제한된 background probe·요청 시 기존 operation 상태 재확인으로 회복시키는 안을 우선 제안하고, 불명 operation의 안전 재시작은 Billing의 late reserve/confirm fence와 계약 확장이 필요하다고 구분했다. 장애 중 예외 이용은 별도 비용/권리 정책 승인 대상이다. 모두 제안 상태이며 runtime·현재 승인 정책은 변경하지 않았다.
- TMI-128 신규 예약 차단의 해제 조건을 확인·설명했다. activeGuard는 로컬 terminal 확정 시 해제되며 24시간/200회는 자동 복구 예산일 뿐 차단 자동 해제 기한이 아니다. NEEDS_REVIEW/미해결 격리는 guard를 보존하므로 고정된 차단 상한이 없고 운영 해결이 필요할 수 있다. 설명/문서 기록만 갱신하고 runtime·운영 정책은 변경하지 않았다.
- TMI-128 새 시험 예약 설명의 종료 기록을 현재 turn 식별자로 WORKLOG에 연결했다. 새 예약/동일 key 복구/REPLACEMENT 구분 설명 완료 상태를 유지하며 문서 외 코드·운영·Jira 변경은 없다.
- TMI-128 관련 새 시험 시작과 동일 요청 재전송을 구분했다. 새 key는 새로운 예약 확보·Session 저장·confirm 흐름이고, 같은 key는 기존 요청 복구다. 기존 미해결 guard가 남으면 새 생성을 막으며, 허용된 REPLACEMENT는 기존 group/consumption을 재사용한다. 설명/기록만 수행했고 코드와 운영 상태는 변경하지 않았다.
- TMI-128의 Session 생성/예약 취소 경합을 추가 설명했다. 단순 조회는 그 순간의 상태만 보여주며 이미 진행 중인 생성 요청을 중단시키지 않는다. 지연된 요청과 worker가 겹치는 예와 공통 lease·operation Transaction fence가 생성/cleanup 중 한쪽만 확정시키는 역할을 설명했다. 이번 턴은 설명/문서 기록만 수행하며 runtime과 운영 상태는 그대로다.
- TMI-128 코드 설명 작업의 종료 기록을 현재 turn 식별자로 WORKLOG에 동기화했다. 설명 완료 상태와 기본 OFF·운영 검증 대기를 유지한다. 이번 동기화는 문서 기록만 변경하며 runtime·Jira·운영 설정 변경이나 테스트 재실행은 없다.
- TMI-128 구현 설명 요청으로 실제 코드를 다시 확인했다. operation의 업무 상태와 recovery metadata, scheduler·공유 lease/CAS, Session 저장 전 cleanup/저장 후 confirm, unknown commit·retry/격리, Sentry pending과 운영 활성화 경계를 코드 발췌와 함께 설명했다. 이번 턴은 설명/기록만 수행하며 runtime·외부 계약·운영 설정은 변경하지 않고 테스트를 재실행하지 않았다.
- 사용자 구현 요청에 따라 TMI-128 Reservation background reconciliation을 현재 feature branch에 구현했다. 공유 HTTP/worker lease/version fence·dispatch marker/cleanup intent, status-first confirm/cancel, fresh majority unknown-commit 수렴, owner/retry/auth 격리, bounded scheduler·startup/index 검증, Sentry pending 회수/privacy, legacy dry-run/allowlist script와 RUNBOOK을 추가했다. worker는 새 reserve/discovery/Session을 생성하지 않는다.
- 최종 로컬 검증: `./gradlew clean test` **538개**, `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest` **64개**, `node --test scripts/mongodb/*.test.js` **104개** 모두 통과(실패/skip 0). Mongo는 격리 Docker replica-set이며 Docker API compatibility 값은 테스트 프로세스에만 적용했다. `git diff --check`도 통과했다.
- 기본 OFF이며 기존 공개 API/AI/S3/Redis/Challenge 계약은 유지한다. 운영 활성화 전 구버전 HTTP drain, 기존/new Mongo index·legacy allowlist, Lattice/Billing/모바일 staging E2E와 Sentry 실제 수신 검증이 필요하다. Sentry `alert-max-age`/`alert-max-attempts`는 미승인 기본값을 만들지 않고 ON 시 명시 설정을 요구한다. [TMI-128 RUNBOOK](BILLING_RESERVATION_RECONCILIATION_RUNBOOK.md) 참조.
- 이번 작업에서 Billing/Identity 저장소, AWS/운영 DB/Sentry 설정, commit/push/배포와 Jira 종료는 수행하지 않았다. 기존 dirty AGENTS·범위/체크리스트/포트폴리오/미추적 문서는 사용자 변경으로 보존했다. 아래의 runtime 미착수/승인 대기 설명은 이전 작업 시점의 이력이다.

- 사용자 권장안 승인으로 Reservation 자동 복구 계획을 확정하고 Jira [TMI-128](https://to-teacher.atlassian.net/browse/TMI-128) `[Learning Core] Billing Reservation 장애 자동 복구 및 Sentry 경보 구현`을 생성했다. 재조회로 해야 할 일 상태와 본문을 확인했다. poll10초/batch20/동시2/lease30초/attempt20초/HTTP2회, precommit정체2분, 24시간/200회 retry, auth15분 probe, legacy allowlist, 최소 로컬7일 보존과 Sentry 재사용·pending 경보 설계를 확정했다. AGENTS에 독립 허용 절을 추가했다. runtime 미착수·운영 활성화 미승인이며 Billing fixture/404·status 의미와 미제안 Sentry 운영값/실제 수신 검증은 남는다. 아래 승인 대기 기록은 이전 시점 이력이다.
- Reservation 계획 보완의 종료 기록을 현재 turn marker로 동기화했다. Sentry 재사용·무료권/그룹 재시작 구분·polling 부하 제한 반영과 문서 링크21개/whitespace 검증이 완료됐다. 관련 TMI-116·118·122·125, Billing TMI-113의 계약은 유지하며 신규 구현·정책 승인·Jira 전환은 없다.
- 사용자 요청으로 기존 Reservation 자동 복구 계획서를 보완했다. operation은 현재 진행표이며 poll은 due 후보 조회라는 설명, INITIAL held→available 해제와 REPLACEMENT consumption 유지/새 Session의 구분, Sentry 기존 IHub 재사용·worker 전용 reporter·sanitizer 최소 허용·durable pending 경보·중복 제한 및 수신 검증을 추가했다. 관련 기반은 TMI-116·118·122·125와 Billing TMI-113이며 TMI-126은 범위 밖이다. 권장 수치·격리 정책은 승인 대기이고 신규 Jira·AGENTS·runtime·Sentry 운영 설정은 변경하지 않았다.
- 종료 훅이 제공한 현재 turn 식별자로 예약 해제/AttemptGroup 재시작 설명의 완료 기록을 WORKLOG 끝에 추가했다. TMI-116·118·122 및 Billing TMI-113 관련 설명 완료 상태를 유지하며 신규 구현·정책 승인·Jira 변경은 없다.
- 예약 해제와 AttemptGroup REPLACEMENT 재시작을 구분해 설명했다. INITIAL confirm 전 취소/만료는 held 사용권 복원이고, confirm 후 미완료 시험의 재시작은 기존 consumption/group/mock을 유지하며 새 operation/examId를 생성하는 흐름이다. Session 저장만으로 confirm 완료를 의미하지 않으며 GRADING은 기존 채점 복구 우선, RETAKE_AVAILABLE에서 재시작한다. TMI-116·118·122 기반 계약 설명만 수행했고 코드·계획·Jira·운영 설정은 변경하지 않았다.
- Reservation 경보는 기존 Sentry 재사용이 가능하다고 안내했다. 현재 reporter는 HTTP 예외 중심이고 sanitizer가 message/임의 tag/fingerprint를 제거하므로 worker 전용 보고·고정 사유 allowlist·중복 제한·실제 Alert Rule/수신 검증이 필요하다. Billing 로컬 ReservationLifecycleService에서 INITIAL cancel/expire가 같은 Transaction의 releaseInitial로 held→available을 복원하고 REPLACEMENT는 추가 수량 변경 없이 끝남을 확인했다. TrialClaim 보존과 무료권 신규 지급을 구분하며, TMI-116·113 기반 동작 설명만 수행했다. 신규 경보 구현·계획 승인·Sentry 운영 설정 변경은 없다.
- Reservation 복구 계획에서 추가로 알아둘 사용자·운영 영향(미확정 격리 시 새 시험/merge 차단, 취소·만료 예약 부활 금지, 기존 데이터 승인 편입, 인증 장애 circuit, 복구 기록 보존과 안전한 활성화)을 안내했다. TMI-116·118·122·125 기반 계획의 권장 수치·정책은 여전히 승인 대기이며, Billing 404/오래된 status 의미 확인도 남는다. 설명만 수행했고 계획·AGENTS·Jira·runtime·운영 설정은 변경하지 않았다.
- Reservation operation은 단순 append 로그가 아니라 시험 생성 요청 하나의 현재 진행 상태·멱등성·복구 판단을 저장하는 기존 DB 문서임을 설명했다. 제안 poll10초는 instance당 분당 약6회 후보 조회 주기이며 전체 재처리나 전체 DB 작업 횟수가 아니다. index 기반 due/non-terminal 조회, batch20·동시2·backoff로 부하를 제한하되 다중 instance와 DB 여유 용량·explain/staging 관측으로 검증해야 한다. TMI-116 기반 자동 복구 계획은 승인 대기이며 설정·코드·운영 DB를 변경하지 않았다.
- MongoDB Atlas 저장공간 67% 경고와 관련해 `sample_mflix` 삭제 가능성을 검토했다. Learning Core와 Identity 저장소 전체를 정적 검색한 결과 `sample_mflix` 참조는 없으며 기본 애플리케이션 DB는 각각 `to-teacher-app`, `to-teacher-identity`다. 따라서 Atlas에서 사용자가 샘플 데이터 적재로 생성한 `sample_mflix`가 맞고 다른 도구·분석 작업에서도 사용하지 않는다면 삭제 후보로 판단한다. 반면 `local`은 MongoDB가 replica set·oplog 등 내부 용도로 사용하는 시스템 DB이므로 삭제하지 않는다. 실제 Atlas DB 크기·연결 중인 외부 client·backup은 조회하지 않았고 DB 삭제도 수행하지 않았다. 관련 Jira는 없다.
- Reservation 자동 복구 계획을 사용자 관점으로 설명했다. Reservation=시험 사용권 임시 확보, Session=실제 시험 기록, operation=생성 요청 진행 기록을 구분하고, 사용자 재요청 없이 저장 후 확정·저장 전 예약 정리·불명확 결과 격리를 수행하는 권장안을 안내했다. lease/CAS와 원격 늦은 성공, 5분 예약 만료와 24시간 복구 예산의 차이 및 격리 시 새 시험/merge 차단 가능성을 설명했다. TMI-116·118·122·125 기반의 계획은 여전히 승인 대기이며 신규 Jira·구현·TMI-126 계약 변경은 없다.
- Reservation 자동 복구 계획서 `docs/codex/BILLING_RESERVATION_RECONCILIATION_IMPLEMENTATION_PLAN.md`를 작성했다. TMI-116·118·122·125의 기존 코드와 Billing ADR-001/서비스 계약을 근거로 Session 저장 후 confirm 복구, 저장 전 정체 예약 cleanup, HTTP/worker 공유 lease·cleanup-intent CAS, unknown commit/404 격리, owner guard와 auth circuit, migration·테스트/rollout을 제안했다. 2분 정체·24시간/200회·기존 allowlist·최소 로컬 7일 보존은 승인 대기 권장값이다. 신규 Jira·AGENTS 허용 확장·runtime 구현·배포는 하지 않았고 TMI-126 Challenge는 범위 밖이다. 다음은 계획 정책 승인 후 독립 허용 절/Jira 작성이다.
- Reservation background reconciliation만으로 1차 전체 코드 개발이 완료되는 것은 아니다. 최신 1차 범위에는 기간제 유료 이용권도 포함돼 있다. 기존 무료시험·Challenge Learning Core 범위에서는 해당 복구가 문서상 마지막 주요 신규 코드 항목이지만, 전체 범위에는 Identity Billing reader audience/scope, Billing 기간제 결제 runtime, Learning Core 유료 권리 연동과 프론트 구매/복원·조회 연동이 남는다. 이번 판정은 최신 체크리스트·상태 기록을 대조한 것이며 타 저장소·AI/프론트 구현을 새로 검증한 결과는 아니다. 관련 완료 기반은 TMI-116·118·122·125·126이고 새 Jira나 구현은 추가하지 않았다.
- 세 저장소의 현재 `develop`을 재대조했다. Learning Core는 `cb5f6ee`, Identity는 `fe9c7f6`, Billing은 `eb0ae14`다. 이번 점검에서는 원격·Jira·배포 상태를 변경하지 않았다.
- Identity: 기존 SNS/phone/Guest·탈퇴 lifecycle과 `TMI-123` owner event fan-out에 더해 PR #39에서 사용자 Access Token `account_type=MEMBER|GUEST` 발급이 병합됐다. 전체 640개 테스트 성공 기록이 있다. Billing 무료 reader용 audience·`billing:read` scope 확장은 계획만 있고 Jira·runtime은 아직 없다.
- Billing 무료시험: `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`의 eligibility·Claim/Grant/ledger·Reservation·AttemptGroup·owner rebind/phone continuation 기반이 병합됐다. 추가로 Jira 없는 `PLAN-007`의 `GET /api/v1/entitlements` 공개 reader, read-only snapshot·owner epoch·JWT/scope 경계가 PR #9에 병합됐고 전체 236개 테스트가 성공했다. reader/public connector는 기본 OFF이며 Identity audience/scope·ALB/SG/JWKS·legacy coverage·프론트 연동이 남는다.
- Learning Core 기존 시험: `TMI-109`, `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`의 withdrawal deny, Billing 생성 saga, AttemptGroup outbox, phone continuation, UserMerged ownership migration이 병합됐다. `TMI-125` 후속 PR #29에서 UUID strict decode·orphan/owner mismatch migration blocker·unknown commit 수렴·CI/staging `mongoIntegrationTest` gate도 완료됐다.
- 10초 챌린지: `TMI-126` 구현 commit `4050ee3`과 PR #30 merge `cb5f6ee`가 반영됐다. 별도 Challenge domain, 7개 공개 API, MEMBER 인가, catalog/baseDate·attempt/deadline·고정 S3 key·원자 submit/Job·AI multipart/lease/retry/strict Callback·결과/history를 구현했다. 일반 Java 529개, Mongo replica-set 40개, Node migration 90개가 실패·skip 없이 성공했다. 마지막 기록상 Jira 상태 전환은 수행하지 않았다.
- 유료 기간제: 최신 Billing 계약은 `PREMIUM_1D/3D/7D/14D/28D`, 24/72/168/336/672시간, 양 Store consumable one-time과 RevenueCat adapter다. Billing ADR-004·fixed-term 계약은 상세화됐지만 별도 결제 PLAN·Jira·runtime은 아직 없다. Billing payment/store transaction/fixed-term entitlement·public purchase API·RevenueCat webhook/API reconciliation, Learning Core paid evaluator와 모바일 구매·동기화가 큰 신규 구현 공백이다.
- 추가 기능 공백은 Identity Billing reader token, Billing Reservation background reconciliation과 기간제 결제 runtime이다. 코드 완료 영역도 실제 Mongo migration, Lattice/IAM/SG, workload issuer/JWKS, AI/Callback TLS·credential, Challenge catalog/index·S3 lifecycle, 모바일 E2E와 canary/feature flag rollout 전에는 production 완료로 보지 않는다.
- 진행 체크리스트는 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 2026-09-08 기준으로 갱신했다. `docs/codex/FIRST_RELEASE_FIXED_TERM_PASS_SCOPE.md`에는 2026-09-05의 30일·직접 Store 연동 초안보다 Billing 최신 28일·RevenueCat 계약이 우선한다는 상태 공지를 추가했다.

## Latest portfolio investigation — 2026-09-07

- 2026-09-08 보고서용 정량 지표를 재확인했다. 실제 로컬 JUnit XML 합계는 일반 Java539/Mongo통합71, 각 failures/errors/skipped0이며 Node105 성공은 최신 TMI-128 WORKLOG 실행 기록이다. 이전 538/64/104는 조기 경보 보강 전 시점이다. CI 문제 테스트10회 반복10/10 성공 기록, 팀원 회고의 8월31일 웹+앱 전체시험100회·앱17명/30회·콘텐츠100세트를 구분했다. 테스트 통과를 coverage/운영 무장애로 바꾸지 않는다.
- 인프라 월79만원→약49만원/59만원 비교는 staging월40시간·ALB/NAT 제거/유지 가정에 따른 과거 견적이며 실제 청구 절감이 아니다. p95/처리량/스레드 점유·운영 복구성공률·실제 AI 비용 개선율은 조사 자료에서 미확인이다. 후속 측정은 동일 조건의 전후 부하·복구 시나리오·호출 수 집계가 적합하되 이번에 실행하지 않았다. 관련 TMI-25·77·128, Jira 변경 없음. CURRENT_STATE/WORKLOG만 변경하고 동시 진행 runtime·문서를 보존했다.
- 2026-09-08 소마 개인 개발·성장 보고서용 소재 10개를 사용자 가치·문제/개선 서사·본인 기여 근거·설명 가능성을 기준으로 추천 순위화했다. 공식 평가 기준이나 점수는 아니다. 순서는 AI 채점 완료 비동기 분리, 선택적 재채점/실패 복구, 구조화 로그/Sentry, 시험 생성 Reservation Saga/자동 복구, 웹 POC 재사용과 서비스 책임 분리, Part 4 원본 표/API 통일, 문항/종합 결과 저장소 분리·최신 조회, 최초 시험 점수 집계 수정, CI 동시성 테스트 개선, 신규 사용자 S3 음성 장애다.
- 근거는 기존 포트폴리오·EV/TS 사례집·팀원 회고 대조 및 TMI-128 구현 기록/활성화 안내다. 관련 TMI-10 후속 보완·25·31·61·77·116·128이며 Jira 변경은 없다. TMI-128은 로컬 구현·검증과 운영 활성화를 구분하고 동시 작업 중인 조기 경보 세부 정책은 이번 순위의 성과 근거에서 제외했다. S3 장애의 개인 기여와 초기 동기 채점 스레드 고갈의 당시 로그는 추가 확인 대상으로 남겼다. 이번에는 CURRENT_STATE/WORKLOG만 변경하며 다른 작업의 runtime·문서 변경을 보존한다.
- 사용자 요청에 따라 이미 개발한 뒤 왜 수정했는지 조사했다. `TOSUNSAENG_DESIGN_EVOLUTION_CASEBOOK.md`에 18개 소재를 이전 방식→계기→이유→수정→대가·검증으로 정리하고 포트폴리오·TS 사례집에 연결했다. EV-01~15는 구현 변경/확장, EV-16~17은 구현 전 계약 보정 후 구현, EV-18은 상품 계획 변경이다. 실제 변경·결정·추론·미확인을 구분하고 TS와 사건 수를 합산하지 않는다.
- 대표 흐름은 세션 재사용→새 시작/폐기→Billing command replay, 문항/종합 결과 저장소 분리, Part 4 이미지/고정 DTO→원본 Map·세 API 통일이다. 추가로 완료 evidence·Summary generation·로그/Sentry·AWS 인증·운영 JWT·AI URL·outbox·phone continuation·History, Challenge 집계/총 예산과 credit 폐기 결정을 연결했다. 관련 구현 Jira는 TMI-10(완료 후 보완), TMI-14·25·31·61·77·116·118·122·126이며 기간제 전용 신규 Jira는 이번 조사에서 생성·확정하지 않았다. Jira 변경 없음.
- 이번 조사도 문서 전용이며 핵심 커밋 전후 diff와 현재 코드·테스트·WORKLOG를 대조했다. 새 시험 정책의 직접 UX 동기·기간제 상품 선택의 사업적 이유·실제 배포/전후 수치는 추가 확인 대상이다. 과거 재시작 문서 정정을 runtime 변경으로 표현하지 않았다. Gradle 재실행·API 변경·운영 DB/AWS·commit/push·배포는 수행하지 않는다.
- 설계 진화 보강 뒤 포트폴리오 관련 4개 문서의 로컬 파일·행 참조 274개를 확인했고 오류는 없었다. git diff --check와 신규 EV 문서 whitespace 검사도 통과했다. 이번 변경은 EV 신규 문서·포트폴리오/TS 연결·CURRENT_STATE·WORKLOG의 5개 문서이며 기존 팀원 회고 보강 문서는 그대로 보존했다.
- 팀원 JINI의 토선생 회고 3편 본문을 읽고 현재 Learning Core 코드와 대조했다. `TOSUNSAENG_TEAM_RETROSPECTIVE_BACKEND_SUPPLEMENT.md`에 6계층 구조로 운영·협업·비용·콘텐츠 등 9개 소재를 추가했다. 기존 사례집은 S3 음성 이동·신규 사용자 Q5 중단(TS-23), 약 10분 AI 피드백 대기 후 이탈(TS-24)을 포함해 24개 후보이며 모두 독립 운영 장애나 개인 해결 성과라는 뜻은 아니다. 포트폴리오 목록에도 선택 안내를 연결했다.
- 회고 사실·현재 구현·추론·미구현 제안을 구분했다. AI field repair/STT 개선은 팀원 성과, 백엔드 Job 복구·Challenge no_speech는 현재 코드 근거로 분리했다. 과거 장애의 직접 조치·본인 기여·복구 성공과 운영 수치는 추가 확인 대상이다. 웹+앱 완료 100회, 앱 17명/30회, 콘텐츠 100세트를 구분했다. 이번 작업은 문서만 변경하며 Gradle 재실행·웹 코드 조회·AWS/DB 변경·Jira 변경·배포를 하지 않는다.
- 회고 보강 후 세 산출물의 로컬 파일·행 참조 192개 검증과 git diff --check를 통과했다. 외부 계약·애플리케이션은 변경하지 않았으며 기존 dirty 파일·과거 기록을 보존했다. 다음은 본인 기여·당시 복구 성공 확인과 본문 소재 선택이다.
- 종료 훅의 새 turn 식별자로 WORKLOG EOF 기록을 추가했다. 트러블슈팅 사례집 16개 상세 사례·6개 추가 진단, 로컬 참조 149개 검증과 기존 포트폴리오 연결은 완료 상태다. 이번 동기화는 상태·작업 기록 문서만 갱신한다.
- 사용자 요청에 따라 앱 관련 채팅 4개 작업의 선택된 페이지, 세 서버 WORKLOG와 Learning Core Git diff·현재 코드·테스트를 대조했다. `TOSUNSAENG_TROUBLESHOOTING_CASEBOOK.md`에 수정·검증 근거가 있는 16개 상세 사례와 외부 연동 진단 6개를 추가하고 `TOSUNSAENG_PORTFOLIO_CONTENT_INVENTORY.md`에 연결했다.
- 추천 사례는 빈 종합 피드백 복구(TMI-25), Mongo Summary 트랜잭션 hotfix(TMI-118), CI 동시성 flaky 테스트다. 재답변 점수 누적, CI SENTRY_RELEASE 설정 충돌, TMI-61 NamespaceNotFound, Part 4 text 누락, 모범답안 사전 노출, Sentry stack·정제, TMI-125 unknown commit·검증 누락, TMI-31 migration 경합, TMI-126 Docker API·mongosh 조기 종료도 근거와 연결했다. 관련 TMI-122·Identity TMI-123·103·104·107은 연동 배경 또는 추가 진단 후보이며 Jira 변경은 없다.
- 최신 코드 기준은 `develop@cb5f6ee`다. 09-05 Docker 부재 이후 09-07 Java 529개·Mongo 40개·Node 90개 성공 기록과 Challenge 구현 상태를 포트폴리오에 보정했다. 이번 문서 조사에서는 Gradle 테스트를 재실행하지 않았으며 과거 검증 결과를 현재 실행 결과로 표현하지 않았다.
- 실제 발생·리뷰 발견·원인 진단·후속 미확인을 구분했다. 문서 링크·행 참조와 diff를 검증하며 애플리케이션·외부 계약·Jira·운영 인프라·Git 이력은 변경하지 않는다. 기존 WORKLOG 중복 항목과 다른 작업의 변경은 보존한다.

## Latest implementation — TMI-126 (2026-09-07)

- 다음 우선 작업은 TMI-126의 staging 연동·활성화 준비 검증이다. 이전 확인에서 PR #30 develop 병합과 CI 성공을 확인했지만 실제 Identity claim 배포, AI/Callback 인증·TLS, Mongo catalog/index, S3 권한/lifecycle 및 모바일 E2E는 별도 검증 대상이다. staging과 production의 기준일 metadata를 분리하고 최초 enabled 기동이 Day 1을 결정함에 유의한다. 이번 안내에서는 배포·기능 활성화·Jira 변경을 하지 않았다. 새 개발 항목인 Billing Reservation background reconciliation은 기존 AttemptGroup outbox 복구와 구분되는 후속 범위다.
- Learning Core 10초 챌린지 로컬 구현·검증 완료. `domain/challenge` 전용 코드, 7개 공개 API, MEMBER 인가/기존 deny gate, KST catalog singleton, snapshot·1시간 attempt, 만료/실제 제출 집계, 고정 S3 key, 원자 submit receipt/Job, AI multipart/lease/retry·strict Callback·결과/history를 추가했다. 기본 OFF이며 운영 배포·활성화는 하지 않았다.
- S3 key는 `temp/challenges/{attemptId}/q_{questionNumber}.m4a`. version pin·보관본 없이 같은 key를 사용하며 최초 AI audio digest를 durable CAS로 선택한다. 변경 음성·5분 전송 예산 소진·최종 timeout은 실패로 종료하고 늦은 유효 Callback은 결과를 뒤집지 않는다.
- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew clean test mongoIntegrationTest` 성공: 일반 Java 529개(Challenge 33), Mongo 40개(Challenge 29), 실패·skip 0. Testcontainers Mongo 7.0.14의 commit/rollback·경합·unknown commit·실제 mongosh dry-run/apply까지 검증했다. Node migration 전체 90개(Challenge 7)도 성공했다.
- 기존 시험 API·DTO·AI user_id=examId·S3/Redis key·BaseResponse·보안 chain 0/1/2를 유지했다. Challenge Callback은 -1 exact chain이고 worker는 기존 시험 scheduler를 대체하지 않는 private lifecycle executor다.
- 배포 전 [rollout 안내](TEN_SECOND_CHALLENGE_ROLLOUT.md)의 Identity claim 선배포/구형 Token drain, catalog/index 적용, 방향별 credential/TLS, temp prefix IAM/lifecycle, 모바일·프론트/AI staging E2E, 개인정보 보존/삭제·경보 확인이 필요하다.
- 이전부터 변경돼 있던 AGENTS·계약/계획·release/portfolio 문서를 보존했다. 이번 범위 밖 runtime 변경, commit·push·Jira 상태 전환·운영 DB/S3/IAM 변경은 없다. 작업 브랜치는 사용자가 준비한 위 브랜치를 그대로 사용했다.

## 이전 작업 기록

아래 누적 기록의 “구현 미착수” 등은 각 분석 시점의 상태이며 최신 구현 상태는 위 TMI-126 절을 따른다.
- 2026-09-07 사용자 승인으로 TMI-126 Challenge S3 key를 temp/challenges/{attemptId}/q_{questionNumber}.m4a로 확정했다. 계획·결정서·프론트 계약·AGENTS·Jira에 반영했고 동일 attempt 재녹음/URL 재발급은 같은 key를 사용한다. 기존 시험 key 및 프론트/AI 전송 계약은 유지한다. runtime 구현은 미착수이며 temp lifecycle·prefix 권한은 운영 활성화 전 확인한다. 실제 S3/IAM 변경은 하지 않았다.
- 2026-09-07 TMI-126 S3 key를 모의고사와 비교했다. 실제 시험 업로드는 temp/{examId}/q_{questionNumber}_r{retryCount}.wav이고 Challenge는 attempt 기반 .m4a 고정 key 규칙만 있으며 runtime/prefix는 미구현이다. temp/challenges/{attemptId}/q_{questionNumber}.m4a를 제안하되 기존 temp lifecycle/권한 확인이 필요하다고 안내했다. 이번에는 기록만 갱신했으며 경로 확정·계약·Jira·코드·S3 변경은 하지 않았다.
- 2026-09-07 TMI-126 전체 확정 사항을 종합 안내했다. MEMBER/콘텐츠·날짜/attempt·재녹음·S3/submit·집계·만료·AI/Callback·재시도 종료·API 호환·테스트/운영 범위를 최신 계획 기준으로 정리했다. 논의한 기능 및 예외 기준은 모두 확정이며 남은 것은 runtime 구현·프론트/AI 적용 확인·통합/운영 검증이다. 이번에는 기록만 갱신하고 코드·계약·Jira를 변경하지 않았다.
- 2026-09-07 TMI-126 프론트/AI 계약의 누적 diff를 대조했다. URL·Method·요청/응답 field 이름·JSON 구조·인증·S3 PUT·AI multipart/Callback 전송 방식은 유지한다. 다만 실제 제출 기준 풀이 수·EXPIRED submittedAt null·history 날짜 범위·DATE_CLOSED 제거/415 보완 및 AI 총5분/late Callback 정책은 의미·허용값·오류 처리 변경이다. 프론트 null 타입/집계 표시와 AI 기존 204 처리 등을 확인해야 하므로 상대 코드 수정 불필요를 보장하지 않는다. 기록만 갱신했으며 runtime과 Jira는 변경하지 않았다.
- 2026-09-07 TMI-126 최근 정책 확정의 통신 호환성을 설명했다. 프론트/AI URL·Method·field·enum·인증·S3 PUT·AI multipart/Callback JSON은 유지하며 실제 제출 집계·만료 submittedAt null·접수 전5분·최종 실패 후204 no-op은 동작/허용값 변경으로 구분했다. 실제 프론트/AI 적용 확인은 남아 있고 이번에는 기록만 갱신했으며 코드·계약·Jira를 변경하지 않았다.
- 2026-09-07 TMI-126 AI 전송 예산·최종 실패 후 late Callback 정책 확정의 종료 기록을 동기화했다. 논의한 구현 기준은 확정됐고 runtime 구현·양 팀 적용·통합/운영 검증은 남아 있다. 지정 turn marker를 WORKLOG 새 항목에 기록했다.
- 2026-09-07 사용자가 TMI-126의 마지막 두 AI 예외 권장안을 승인했다. 각 Job 최초 dispatch부터 202 전 총 5분(연결/응답/backoff 포함), 재시작/lease 회수/재전송 시 deadline 불변, 소진 시 최종 실패와 예산 회피 generation 금지를 확정했다. 최종 실패 뒤 유효 late Callback은 204 no-op으로 실패를 유지하며 기존 인증/payload conflict 검증과 CAS 승자 보존은 유지한다. 계획·AI/프론트 계약·결정서·AGENTS와 Jira 최상단 확정 절을 동기화했다. 제품/예외 구현 기준은 확정됐고 구현 미착수·양 팀 적용/통합/운영 검증은 남아 있다.
- 2026-09-07 TMI-126 최종 구현 기준을 요약했다. 제출 전 재녹음·동일 attempt/deadline·최종 업로드 후 제출, 변경 음성 재전송 방어와 실제 제출만 집계 등 제품 기준은 확정이다. 접수 전 총 재시도 한도 및 최종 timeout 뒤 late Callback은 문서상 미확정으로 구분해 안내했다. 기록만 갱신했으며 계획·계약·Jira·runtime은 변경하지 않았고 구현은 미착수다.
- 2026-09-07 TMI-126 제출 전 재녹음 및 변경 음성 방어 승인 작업의 종료 기록을 동기화했다. 계획/계약/Jira 반영 완료·구현 미착수 상태이며, 별도 AI 재시도 종료 정책과 적용 검증은 남아 있다. 지정 turn marker를 WORKLOG 새 항목에 기록했다.
- 2026-09-07 사용자가 TMI-126 프론트 재녹음은 answer 제출 전만 가능하다고 확인하고 변경 음성 재전송 방어 권장안을 승인했다. 같은 attempt/key/deadline으로 최종 PUT 뒤 한 번 제출하며 최초 AI 전송 bytes의 durable digest와 다른 음성은 재전송하지 않는다. 복구 불가 시 채점 실패로 종료하되 완료 결과·제출·풀이 수·참고 답안은 유지한다. version pin·보관본 없이 기존 AI 409 계약을 보존한다. 계획/계약/AGENTS 및 Jira를 동기화했고 구현은 미착수다. 실제 프론트 업로드 순서와 AI fixture 검증은 남아 있으며 별도 재시도 총 한도·최종 late Callback 수치를 추가 승인으로 추정하지 않았다.
- 2026-09-07 TMI-126 관련 프론트 재녹음 동작을 제출 전/후로 구분해 설명했다. S3 업로드만 끝난 제출 전에는 동일 attempt·deadline·key로 재녹음 업로드를 마친 뒤 answer를 한 번 접수하면 기존 AI 계약과 충돌하지 않는다. 제출 접수 이후에는 이미 Job이 생성돼 덮어쓰기만으로 채점 대상을 바꿀 수 없으며 같은 요청 replay도 새 채점을 만들지 않는다. 프론트가 재녹음을 허용하는 시점과 answer 호출 시점은 아직 확인되지 않았다. 이번에는 기록만 갱신하고 정책·코드·Jira를 변경하지 않았다.
- 2026-09-07 TMI-126 최종 결정 현황 설명의 종료 기록을 동기화했다. 제품 정책은 확정돼 있으나 AI 접수 전 재전송 총 한도·최종 late Callback·덮어쓰기 후 동일 Job audio 변경 처리의 세 예외는 미확정이다. 권장안 승인을 추정하지 않고 구현 미착수 상태를 유지하며 지정 turn marker를 WORKLOG에 기록했다.
- 2026-09-07 TMI-126 최종 결정 현황을 재대조했다. MEMBER·하루 3문제·비순환 기준일·1시간 attempt·실제 제출만 집계·음성 덮어쓰기·만료 submittedAt null은 확정이다. AI 접수 전 재전송 총 한도, 최종 timeout 뒤 늦은 결과, 덮어쓰기 후 동일 Job audio 변경 처리의 세 예외 경계는 아직 확정되지 않았다. 기존 AI 수치와 미정 예외를 구분해 안내하며 계약·Jira·구현을 변경하지 않았다. 기반 구현은 가능하고 운영 활성화 증빙은 별도다.
- 2026-09-07 TMI-126 음성 덮어쓰기·만료 시각 확정과 AI 수치 확인의 종료 기록을 동기화했다. 문서/Jira 반영 완료·구현 미착수 상태이며, 같은 Job audio 변경의 409 충돌과 transport 총 한도·최종 late Callback·프론트 null 처리 확인은 남아 있다. 지정 turn marker를 WORKLOG에 기록했다.
- 2026-09-07 TMI-126 후속 결정으로 같은 upload key에 마지막 성공 PUT이 현재 음성으로 남도록 덮어쓰기를 허용하고 version pin·별도 보관본 계획을 제외했다. terminal 새 URL 발급 금지는 유지하며 기존 bucket versioning/과거 객체는 변경하지 않는다. EXPIRED submittedAt=null도 확정했다. 기존 AI 수치(연결 3초·접수 응답 15초·Callback 대기 120초·최대 generation 3회, AI→LC 전달 5초 시작/최대 간격 10분/최대 10회)를 재확인했다. 접수 전 transport 총 한도·마지막 generation late Callback은 미정이며, 덮어쓰기 후 같은 Job의 바뀐 audio 재전송은 기존 409 계약과 충돌해 추가 대조가 필요하다. 계획/계약/AGENTS와 Jira 본문을 동기화했고 구현은 미착수다.
- 2026-09-07 TMI-126 실제 제출 기준 집계 변경의 종료 기록을 동기화했다. 풀이 수·참여 여부에서 미제출 만료를 제외하는 문서/Jira 반영은 완료됐고 구현은 미착수다. 프론트 적용 확인을 유지하며 지정 turn marker를 WORKLOG 새 항목에 기록했다.
- 2026-09-07 사용자 요청으로 TMI-126 풀이 수를 실제 audio 제출 접수만 세도록 확정·변경했다. history/results의 solvedQuestionCount는 내부 SUBMITTED만 집계하고 EXPIRED를 제외하며 participated는 실제 제출 수>0이다. AI pending/no-speech/failed는 제출이 접수됐다면 포함한다. 만료의 참고 답안·다음 문제 접근·공개 submitted 및 dailyStatus/completedQuestionNumbers의 진행 종료 의미는 유지한다. 프론트 계약·결정서·계획서·AGENTS와 Jira 본문/검증 기준을 동기화했으며 Jira는 해야 할 일이다. runtime 구현은 아직 시작하지 않았고 프론트의 실제 집계 표시 적용 확인은 남아 있다.
- 2026-09-07 TMI-126의 만료 풀이 수 정책을 설명했다. 현재 프론트 v1은 생성한 attempt를 1시간 내 제출하지 않아 만료돼도 종료된 응시로 계산해 solvedQuestionCount에 포함하며, 시작하지 않은 문제는 자동으로 세지 않는다. 실제 제출 수와 종료된 응시 수를 구분할 필요가 있음을 안내하되 이번에는 설명만 하고 계약·계획·코드·Jira를 변경하지 않았다.
- 2026-09-07 TMI-126 상세 계획을 사용자 흐름과 5단계 구현 순서 중심으로 설명했다. MEMBER 인가·자동 Day 1·1시간 attempt·제출/AI 분리·참고 답안/결과·이력, 원자 저장/replay·음성 고정·Callback fencing의 목적을 정리했다. S3 artifact, AI retry/late Callback, EXPIRED 제출 시각 fixture는 여전히 확인 대상이다. 이번에는 계획·계약·코드·Jira를 변경하지 않았으며 구현은 미착수다. 설명 기록만 갱신하고 문서 형식을 검증한다.
- 2026-09-07 TMI-126 상세 구현 계획 작성의 종료 기록을 동기화했다. 계획서 보완은 완료됐고 runtime 구현은 미착수다. S3 artifact 방식·AI retry/late Callback·만료 결과 timestamp와 운영 활성화 확인사항을 유지한다. 지정 turn marker를 WORKLOG 새 항목에 기록했으며 이번 종료 동기화는 문서 두 파일만 변경했다.
- 2026-09-07 사용자 요청으로 TMI-126 구현 계획서를 상세화했다. 기존 5단계 계획을 보존하면서 예정 클래스 책임·6개 collection/index·공개 상태 projection·submit receipt/unknown commit 수렴·음성 version pin/private snapshot 대안·AI Job/Callback 상태표·replica-set 검증과 운영 drain 체크리스트를 추가했다. 기존 UserOwnedTransactionExecutor가 writer OFF에서 Transaction을 시작하지 않으므로 Challenge 자체 원자성 경계가 필요함을 명시했다. transport budget 수치·마지막 generation late Callback·EXPIRED submittedAt null은 확정 계약과 구분해 확인 대상으로 두었다. 구현·Jira 변경·배포는 수행하지 않았으며 계획서와 CURRENT_STATE/WORKLOG만 변경했다. 기존 사용자 변경과 프론트/AI 계약을 보존했고 문서 검증 git diff --check는 통과했다.
- 2026-09-07 사용자 요청으로 [TMI-126](https://to-teacher.atlassian.net/browse/TMI-126) `[Learning Core] 10초 챌린지 API 및 비동기 AI 채점 구현`을 작업 유형·해야 할 일 상태로 생성하고 재조회했다. 승인된 MEMBER 인가·catalog/기준일·1시간 attempt·S3/내구성 submit·독립 AI Job/Callback·결과/history, 통합 테스트와 운영 활성화 조건을 포함한다. Identity 코드는 선행 검증 완료지만 운영 배포는 미확인으로 구분했다. 계획·계약 문서에 키를 반영했으며 runtime 구현은 아직 시작하지 않았다. 제출 audio 고정과 transport retry 상태표는 구현 전 기술 확인 대상으로 남긴다. 기존 사용자 변경과 외부 계약을 유지했고 이번에는 Jira·문서만 변경했다.
- 2026-09-07 Identity account_type 선행 구현을 재검증했다. 로컬 develop과 origin/develop 참조는 PR #39 merge commit `fe9c7f6`이며 구현 commit `6b34f44`를 포함한다. issuer는 UserAccountType 필수 인자를 받아 문자열 MEMBER/GUEST를 발급하고 null을 거절한다. 사용자 발급 경로 7곳·현재 DB 유형 refresh·승격/merge와 구형 Token 호환 테스트를 확인했으며 `./gradlew clean test --no-daemon`을 다시 실행해 123 suites·640 tests, 실패/오류/skip 0으로 통과했다. 실제 원격 최신 상태·운영 배포·구버전 발급 instance 종료 시각과 운영 TTL/skew는 미확인이다. 다음 Learning Core 작업은 승인된 Challenge 계획을 바탕으로 전용 Jira 범위를 등록하고 MEMBER gate·catalog/baseDate·1시간 attempt·S3/submit 기반을 구현하는 것이다. AI Job·Callback·결과/history와 통합 검증을 이어 붙인다. 이번에는 구현을 시작하지 않았고 전용 Jira도 생성하지 않았다.
- 2026-09-07 Identity 인계 문안 작성의 종료 훅 기록을 완료했다. account_type 발급 경로·테스트·선배포 순서의 전달 문안은 준비됐고 실제 Identity 구현·전달·배포와 전용 Jira 생성은 아직 수행하지 않았다.
- 2026-09-07 Identity 작업자에게 전달할 account_type JWT 인계 내용을 정리했다. 현재 AccessTokenIssuer 인터페이스와 발급 호출 7곳을 재확인했으며 MEMBER/GUEST enum, 신뢰된 User 유형 전달, refresh 시 현재 유형 재평가, upgrade/merge 성공 후 유형 반영, 사용자 JWT에만 claim 추가, 기존 wire/decoder 호환과 테스트·선배포 조건을 명시했다. 답변은 전달용 문안이며 외부 발송과 Identity 코드·Jira 변경은 하지 않았다. 전용 Jira는 아직 없다.
- 2026-09-07 종료 훅 기록을 동기화했다. Identity account_type 발급 변경이 다음 선행 작업이며 구현과 전용 Jira 생성은 아직 수행하지 않았다. Learning Core 기반 개발은 병행 가능하고 운영 활성화 순서는 기존 승인 계획을 따른다.
- 2026-09-07 Challenge의 다음 선행 작업은 Identity의 사용자 Access Token에 현재 계정 유형 `account_type=MEMBER|GUEST`를 발급하는 변경으로 정리했다. signup/login/exchange/refresh/Guest upgrade/merge 모든 발급 경로와 회귀 테스트가 대상이다. Learning Core catalog·attempt·Job 개발은 병행 가능하지만 실제 MEMBER 제한 활성화는 Identity 전체 발급 전환과 구형 Token TTL+skew 경과 후 수행한다. 이번 설명에서는 코드와 Jira를 변경하지 않았으며 전용 Jira는 아직 없다.
- 2026-09-07 사용자가 10초 챌린지 선행 쟁점의 권장안을 승인해 프론트·AI v1 계약, 상세 결정서, release plan의 오래된 문구와 AGENTS.md를 갱신하고 `docs/codex/TEN_SECOND_CHALLENGE_IMPLEMENTATION_PLAN.md`를 작성했다. 확정안은 Identity JWT `account_type`, lazy CAS+bounded scheduler 만료, opaque Bearer Callback exact chain·OFF deny-all, Learning Core metadata/AI binary 검증 분리, ID metric tag 금지, DATE_CLOSED 제거, baseDate 이전 history 제외다. 프론트는 calendar 누락 날짜·오류 UX, AI는 media 검증과 metric tag를 대조해야 하므로 외부 코드 수정이 전혀 없다고 보장하지 않는다. endpoint·Request/Response field는 유지하지만 history 범위와 오류 의미는 보완됐으며 상대 팀 전달·적용 확인이 남아 있다. Identity 발급과 runtime은 구현하지 않았다. Challenge 전용 Jira 미생성, AI 문서 관련 이력 TMI-102·TMI-105·TMI-106은 기존 참조만 유지한다. 실제 코드는 건드리지 않았고 기존 포트폴리오 문서 변경은 보존했다.
- 2026-09-07 10초 챌린지 구현 전 발견 사항의 선택지를 정리했다. 권장 패키지는 (1) Identity Access Token에 `account_type=MEMBER|GUEST` claim을 추가하고 Learning Core가 Challenge endpoint에서 `MEMBER`를 검증, (2) 요청 경계의 CAS lazy expiration을 correctness 기준으로 두고 bounded scheduler를 eventual cleanup으로 병행, (3) opaque AI service Bearer 계약을 유지하며 항상 등록되는 Challenge Callback exact SecurityFilterChain을 일반 JWT/Legacy catch-all보다 앞에 두고 feature OFF 시 deny-all, (4) Learning Core는 S3 존재·MIME·2 MiB만 검증하고 실제 M4A/AAC profile은 decode 주체인 AI가 검증하도록 공개 415/failed 의미를 정정, (5) 확정값과 충돌하는 문구·미사용 `CHALLENGE_DATE_CLOSED`·metric 고카디널리티 표현을 정리하고 history는 baseDate 이전 날짜를 제외하는 안이다. 사용자의 최종 선택 전에는 계약·코드·Jira·배포를 변경하지 않는다.
- 2026-09-07 10초 챌린지 v1 계약과 현재 `develop@88b46c6`, Identity Access Token 발급 구조, Learning Core 보안·S3 기반을 구현 전 재검토했다. Challenge domain 자체는 시작할 수 있지만 현재 Identity Access Token은 `sub`와 공통 `scope`만 발급하고 Guest와 MEMBER 모두 같은 scope를 사용하므로 Learning Core가 계약의 MEMBER 전용 `403`을 판정할 근거가 없다. 구현 전에 Identity–Learning Core 간 계정 유형 claim 또는 MEMBER 전용 scope를 확정하고 Identity 선배포·기존 Token 만료 뒤 Challenge flag를 여는 rollout이 필요하다. 또한 제출 없이 deadline이 지난 attempt를 누가 언제 EXPIRED로 CAS 전이하는지, opaque service credential Callback exact chain을 기존 SecurityFilterChain 0/1/2보다 앞에서 어떻게 격리하는지, S3 HEAD로 확인 가능한 MIME·크기와 실제 M4A/AAC profile 검증의 책임을 계획에 고정해야 한다. 프론트 문서의 2 MiB `임시값` 표현과 결정서의 audio profile `추가 고정 필요` 표현은 이미 확정된 v1과 충돌하며, AI 문서의 식별자를 metric에 허용하는 표현은 고카디널리티 tag 금지로 정정해야 한다. 별도 Challenge 구현 계획서와 Jira는 아직 없고 이번 검토에서 코드·계약·Jira·배포는 변경하지 않았다.
- 2026-09-07 TMI-125 후속 production safety 수정이 PR #29 merge commit `88b46c6`으로 `develop`과 `origin/develop`에 반영된 상태를 확인했다. 10초 챌린지는 프론트·AI v1 계약과 `AGENTS.md` 구현 허용 범위가 확정됐지만 runtime 코드와 Learning Core 전용 Jira는 아직 없다. 따라서 현재 `develop` 기준 계약·계획을 짧게 재검토한 뒤 전용 Jira와 feature branch를 만들고, catalog validator·최초 활성 KST 기준일·비순환 dayNumber·MEMBER 전용 1시간 ChallengeAttempt·snapshot/소유권·today/question/attempt/upload-url 기반부터 구현할 수 있다. 이후 AI Job·Callback·timeout/최대 3회 generation·결과/history·migration/index·staging E2E를 연결한다. TMI-125의 Docker replica-set CI, Mongo migration, workload/JWKS와 staging 성능 검증은 production 활성화 gate로 남지만 Challenge 개발 착수를 막지는 않는다. 이번 판단에서 애플리케이션·Jira·배포 상태는 변경하지 않았다.
- 2026-09-06 포트폴리오 소재 문서에서 구조화 로그와 AI 오류·지연 재시도를 각각 독립 문제 해결 사례로 보강했다. 요청별 UUID `requestId`와 MDC·비동기 TaskDecorator, `event/outcome/reason/stage/durationMs` key=value 규격, Sentry 개인정보 정제의 문제·구현·검증·한계를 기록했다. 또한 문항 영속 Job, PENDING/PROCESSING timeout, 최대 dispatch 횟수, 시험 단위 선택적 복구 API, Summary `generationAttempt`와 stale Callback no-op을 정리했다. 현재 Question AI 접수는 blocking이고 문항 재전송은 완전 자동 worker가 아니라 API 기반 복구임을 명시했으며 애플리케이션 코드와 외부 계약은 변경하지 않았다.
- 2026-09-06 토선생 프로젝트의 포트폴리오 후보를 현재 세 서버 코드·문서·Git 이력과 대조해 `docs/codex/TOSUNSAENG_PORTFOLIO_CONTENT_INVENTORY.md`에 정리했다. 대표 서사는 웹 POC 활용 후 Identity·Learning Core·Billing·AI 책임 분리, 동기 채점의 Thread 고갈 경험에서 Job·Callback·Polling·재처리 workflow로 전환한 과정, 중첩·가변 AI 결과에 맞춘 MongoDB document 모델이다. 추가로 Presigned S3, Redis 상태 projection, Billing Reservation Saga, Idempotency-Key, AttemptGroup Transactional Outbox, UserMerged ownership migration, withdrawal deny, JWT/SigV4, 시험지 migration, 관측·개인정보 정제, rollout과 테스트까지 구현/조건부/계획 상태로 구분했다. 현재 JUnit XML은 기본 Java 496개 성공을 기록하지만 `mongoIntegrationTest`는 로컬 Docker 부재 실패 기록이 있어 production 완료로 과장하지 않도록 명시했다. 별도 Jira 변경과 애플리케이션·AWS 변경은 없다.
- 2026-09-05 종료 훅 기준으로 TMI-125 후속 production safety 구현 상태를 동기화했다. 로컬 구현, Java 496개·Node 7개 성공, integration source compile과 workflow/diff 검증은 완료됐고, 실제 replica-set 실행만 Docker daemon 부재로 CI gate에 남아 있다. Jira `TMI-125`는 구현 결과 댓글 `10048`과 함께 `진행 중`을 유지하며 production UserMerged flag도 OFF다.
- 2026-09-05 `feat/TMI-125-user-merged-ownership-migration` 작업 트리에서 TMI-125 후속 production safety 보강을 구현했다. Billing 성공 응답과 Saga의 `attemptGroupId` lowercase UUID v4 이중 검증, migration orphan Result/Summary·Session owner mismatch count-only blocker, unknown commit 전용 분류와 eventId+digest inbox 204/409/503 수렴, Spring Transaction wrapper 빈 503 mapping을 추가했다. replica-set 테스트는 4개에서 11개로 확장해 rollback·non-terminal operation·동시 duplicate·commit 응답 유실·source writer fence와 Feedback/Summary/Azure/SpeechAce Callback 양쪽 선형화 순서를 검증하도록 했고 PR verify/staging workflow에 Node·Mongo integration gate를 추가했다. `./gradlew clean test --no-daemon` 496개와 Node 7개, integration source compile은 성공했다. 실제 `mongoIntegrationTest`는 로컬 Docker daemon 부재로 initialization error이며 CI Docker 성공 전 Jira 완료와 production flag 활성화를 보류한다. Jira `TMI-125`에는 구현·검증 결과를 댓글 `10048`로 기록했다.
- 2026-09-05 종료 훅 기준으로 Jira `TMI-125` 후속 수정 계획 반영 상태를 동기화했다. 이슈는 `진행 중`이고 상세 계획은 Jira 댓글 `10047`에 기록돼 있으며, 애플리케이션 구현과 production flag 활성화는 아직 수행하지 않았다. 이번 동기화에서 Jira·코드·배포 상태를 추가로 변경하지 않았다.
- 2026-09-05 Jira `TMI-125`에 후속 production safety 수정 계획을 상세 댓글로 반영했다. Jira는 `진행 중`을 유지하며 댓글 ID는 `10047`이다. 댓글에는 `attemptGroupId` UUID v4 이중 검증, migration orphan/owner mismatch blocker, unknown commit의 inbox 기반 204/409/503 수렴, replica-set 경합 테스트, PR·staging 필수 CI와 production flag OFF 경계를 기록했다. 이 처리에서 Jira 설명·상태, 애플리케이션 코드와 배포 설정은 변경하지 않았다.
- 2026-09-05 TMI-125 후속 production safety 수정 계획을 `docs/codex/TMI-125_FOLLOWUP_PRODUCTION_SAFETY_FIX_PLAN.md`로 작성했다. 범위는 phone continuation discovery와 Billing 성공 응답의 `attemptGroupId` lowercase UUID v4 이중 검증, migration의 orphan Result/Summary·Session owner mismatch count-only blocker, unknown commit의 eventId+digest inbox 재조회에 따른 204/409/503 수렴, 실제 replica-set rollback·operation·동시 duplicate·writer/Callback 경합 테스트와 PR/deploy CI gate다. 추가 제품·wire 결정은 없으며 코드 구현과 production 활성화는 아직 수행하지 않았다.
- 2026-09-05 종료 훅 기준으로 Jira `TMI-125` 재개 상태를 동기화했다. 이슈는 후속 production blocker 4건 수정을 위해 `진행 중`이며, 수정 범위와 완료 재판정 조건은 Jira 댓글과 WORKLOG에 기록돼 있다. 이번 동기화에서 애플리케이션·Jira·배포 상태를 추가로 변경하지 않았다.
- 2026-09-05 후속 결함 4건을 수정하기 위해 Jira `TMI-125`를 `완료`에서 `진행 중`으로 다시 열었다. Jira 댓글에 phone continuation `attemptGroupId` UUID v4 strict 검증, migration orphan/owner mismatch preflight, unknown commit inbox 수렴·503 매핑, replica-set rollback·duplicate·writer/Callback 경합 테스트 보강을 완료 조건으로 기록했다. 수정과 전체 검증 전까지 production UserMerged feature flag는 OFF로 유지한다.
- 2026-09-05 완료된 `TMI-125` 후속 리뷰에서 production 활성화 전 수정해야 할 결함 4건을 확인했다. (1) 일반 reserve 응답은 Saga가 `attemptGroupId` lowercase UUID v4를 검사하지만 phone continuation discovery는 client와 Saga 모두 opaque text만 허용해 잘못된 group이 `PREPARED` operation에 먼저 저장될 수 있다. (2) `user-merged-prepare.js`에는 orphan Result/Summary와 참조 Session owner 불일치 검사가 없다. (3) `UnknownTransactionCommitResult`는 blind retry를 막지만 inbox 재조회 수렴이 없고 `TransactionSystemException` 등 non-DataAccess wrapper가 internal advice를 벗어나 500이 될 수 있다. (4) replica-set 테스트 4개는 후반 단계 failure rollback, non-terminal operation HTTP 503, 동시 duplicate, source/target writer·Callback 경합과 unknown commit을 증명하지 않는다. 네 건 모두 유효하며 TMI-125 production flag 활성화 blocker다. 애플리케이션 코드는 수정하지 않았고 별도 follow-up bug 또는 TMI-125 재개 후 함께 보완해야 한다.
- 2026-09-05 10초 챌린지를 제외한 1차 잔여 기능을 세 저장소 기준으로 재점검했다. SNS/Identity lifecycle, 검증 phone당 무료시험, Billing Reservation·AttemptGroup, phone continuation, Identity `TMI-123` fan-out과 Learning Core `TMI-125` UserMerged ownership migration의 큰 서버 기능은 구현·병합됐다. 그러나 새로 확정한 `UNLIMITED_1D/3D/7D/14D/30D` 비자동갱신 기간제 이용권은 전용 Jira와 runtime이 없어 Billing 상품·Apple/Google 검증·payment/transaction/entitlement 원장·공개 구매/복원 API·notification/refund/reconciliation, Learning Core evaluator, 모바일 구매 UX 전체가 신규 개발로 남는다. 별도로 Learning Core staging workflow의 `mongoIntegrationTest` gate, UserMerged migration orphan/owner preflight, phone continuation `attemptGroupId` lowercase UUID v4 strict decode 3건의 production 안전성 보완 코드가 필요하다. 무료시험 계열은 대형 신규 기능보다 Docker replica-set 테스트, Mongo migration, Lattice/IAM/workload, multi-instance·response-loss·rollback과 모바일 staging E2E가 주 잔여 범위다. 진행표는 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 2026-09-05 기준으로 갱신했다.
- 2026-09-05 Jira `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`을 `완료`로 전환했고 Resolution도 `완료`임을 재조회했다. 구현은 PR #28 merge commit `8c8208b`로 `develop`과 `origin/develop`에 반영됐으며 Jira 처리 전 작업 트리는 clean이었다. production feature flag 활성화와 Docker replica-set `mongoIntegrationTest`, Mongo migration·workload/staging E2E는 Jira 완료와 별개의 배포 gate로 남아 있다.
- 2026-09-05 종료 훅 기준으로 1차 업데이트 범위를 다시 동기화했다. 유료 상품은 Billing server 검증 `CAPTURED` 시점부터 24·72·168·336·720시간 사용하는 비자동갱신 무제한 이용권이며, 무료시험 1회·Identity/SNS/phone·시험 생성 saga·AttemptGroup 완료 전 재시작·owner lifecycle·Apple/Google 구매 검증·복원·환불과 운영 E2E를 1차 묶음으로 본다. credit·자동 갱신·출석·추천·coupon·첫 구매 배수·plan 변경·웹 PG는 제외한다. 10초 Challenge는 기존 진행표상 1차지만 runtime이 없어 같은 배포에 유지할지 1.1로 분리할지 결정이 필요하다. 관련 기반 Jira는 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`이며 결제 전용 신규 Jira는 아직 없고 Jira 상태는 변경하지 않았다.
- 2026-09-05 사용자가 유료 상품을 자동 갱신 없이 결제한 기간만 사용하는 1일·3일·7일·14일·30일 무제한 이용권으로 확정해 `docs/codex/FIRST_RELEASE_FIXED_TERM_PASS_SCOPE.md`를 작성했다. Billing 서버 검증 `CAPTURED` 시각부터 각각 24·72·168·336·720시간을 적용하고, 활성 기간에는 시험 횟수 차감 없이 새 AttemptGroup을 허용하며 만료 전에 시작한 시험은 완료 전 추가 결제 없는 처음부터 재시작을 보장한다. 전체 1차 묶음은 Identity/SNS·필수 phone, 무료시험, 기간제 결제, 시험 생성·완료, owner lifecycle와 배포 gate로 정리했다. 10초 Challenge는 기존 진행표상 1차지만 runtime이 없어 같은 배포에 유지하면 별도 일정이 추가되며 1.1 분리를 검토해야 한다. credit·자동 갱신·출석 연장·추천·coupon·첫 구매 배수·plan 변경은 제외한다. 관련 기반 Jira는 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`이며 신규 결제 Jira는 아직 없고 Jira 상태는 변경하지 않았다.
- 2026-09-05 종료 훅 기록을 동기화했다. 유료 상품 방향은 credit이 아닌 1일·3일·7일·14일·30일 무제한 기간형이며, 구매한 기간만 사용하는 이용권인지 자동 갱신 구독인지는 구현 전 최종 확정이 필요하다. 현재 기간 구성에는 기간제 이용권을 권장하고 무료시험 `FREE_EXAM_ONCE`는 별도로 유지한다. 관련 기반 Jira는 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`이며 Jira 상태는 변경하지 않았다.
- 2026-09-05 사용자가 유료 상품을 credit이 아니라 1일·3일·7일·14일·30일 무제한 기간형으로 변경한다고 결정해 `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`를 전면 갱신했다. 현재 기간 조합에는 자동 갱신보다 구매한 기간만 사용하는 기간제 이용권을 권장하며, true auto-renew 여부는 구현 전 최종 확정이 필요하다. 기간형 모델은 `SubscriptionEntitlement`의 plan·activatedAt·expiresAt·status와 server-time authorization을 사용하고, 유효 기간 중 새 시험을 횟수 차감 없이 허용하며 만료 전에 연 AttemptGroup은 완료 전 재시작을 보장한다. 5종 기간제 MVP는 약 4~6주/+50~80%, 모두 자동 갱신이면 약 6~10주 이상/+80~140%의 거친 추가 범위로 재산정했다. 관련 기반 Jira는 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`이며 Jira 상태는 변경하지 않았다.
- 2026-09-05 유료 인앱결제를 1차 업데이트에 포함할 때의 범위를 `docs/codex/FIRST_RELEASE_PAYMENT_SCOPE_IMPACT.md`에 분석했다. 현재 Billing은 무료 TrialClaim·entitlement ledger·Reservation·AttemptGroup·owner rebind 기반은 있지만 Apple/Google 거래 검증 adapter, server notification, payment/order/refund 원장과 앱용 구매·복원 공개 API가 없다. 최소 credit 결제 MVP도 현재 남은 출시 작업 대비 약 40~70%, 1 backend+1 mobile 병렬 기준 3~5주가 추가될 수 있고, 3일 pass·첫 구매 2배·출석·추천·coupon까지 모두 포함하면 약 80~150%, 6~10주 이상으로 커질 수 있다고 추정했다. 결제가 필수라면 credit 구매·서버 검증·notification·복원·미사용 전액 환불의 폐쇄 루프만 1차에 두고 프로모션은 후속으로 분리하는 안을 권장한다. 관련 기반 이력은 Billing `TMI-110`, `TMI-112`, `TMI-113`, `TMI-115`, `TMI-117`, `TMI-120`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, `TMI-125`이며 Jira 상태는 변경하지 않았다.
- 2026-09-04 `TMI-125` 로컬 구현 다음 작업을 재판정했다. 즉시 필요한 마무리는 사용자 주도의 TMI-125 commit/PR/`develop` 병합과 Docker CI의 `mongoIntegrationTest` 통과이며, 다음 신규 기능 개발은 아직 runtime 코드가 전혀 없는 Learning Core 10초 챌린지 백엔드다. 첫 구현 단위는 catalog validator·KST 최초 활성일 baseDate·비순환 day resolver, MEMBER 전용 ChallengeAttempt·1시간 deadline·소유권, attempt 기반 고정 S3 key와 today/question/attempt/upload/submit API foundation으로 권장한다. 이후 AI dispatch/Callback, timeout·최대 3 generation, 결과·history API와 운영 migration/E2E를 연결한다. Challenge 전용 Jira는 아직 없으며 v1 프론트·AI 계약은 이미 승인돼 추가 제품 결정 없이 계획·Jira 작성이 가능하다. 기존 Billing 장애 reconciliation은 별도 운영 안정화 후속 작업으로 유지한다.
- 2026-09-04 `TMI-125` 구현 결과를 요청 흐름 기준으로 재확인하고 설명했다. Identity의 전용 workload JWT가 UserMerged endpoint를 통과하면 event 정규화·digest 멱등 판정, withdrawal/non-terminal operation 선검사, canonical guard touch, active Session 충돌 해소, Result·Summary·Session owner 이전, source `MERGED`, inbox `PROCESSED`가 한 Mongo Transaction으로 처리된다. 기존 사용자 쓰기도 같은 ownership guard에 참여해 merge와 동시에 성공하지 못하며, merge 이후 source JWT는 공개 API에서 거절된다. feature flag는 기본 OFF이고 Mongo migration·Docker replica-set 통합 테스트·환경별 issuer/JWKS·staging E2E 전에는 production 활성화하지 않는다. 애플리케이션 코드는 추가 변경하지 않았다.
- 2026-09-04 `TMI-125` UserMerged consumer와 ownership migration의 로컬 구현을 완료했다. exact `POST /internal/v1/events/user-merged`, 전용 RS256 workload JWT(`aud=learning-core-user-merged`, `sub=identity-service`)와 SecurityFilterChain Order 0/1/2, 4 KiB body/status 계약, source token deny gate를 추가했다. `user_ownership_guards`와 `user_merged_inbox_events`를 기반으로 withdrawal·non-terminal creation operation을 선판정하고 source/target guard를 canonical 순서로 touch한 뒤 활성 Session target 우선, Result·Summary·Session owner 이전, source `MERGED`, inbox `PROCESSED`를 한 Mongo Transaction으로 처리한다. 기존 Session·Billing saga·Question/Summary Job·AI Callback·AttemptGroup writer를 current owner guard Transaction에 연결하고 S3/AI/Redis 외부 처리는 commit 밖에 유지했다. dry-run 기본 migration과 필수 index/startup probe, Testcontainers replica-set 전용 `mongoIntegrationTest`를 추가했다. 최종 `./gradlew clean test`는 Java 483개 failures/errors/skipped 0으로 성공했고 Node migration 6개와 `git diff --check`도 성공했다. 현재 host에는 Docker daemon이 없어 필수 replica-set 통합 테스트는 실행 단계에서 실패했으며, CI/Docker 환경에서 4개 통합 테스트를 반드시 실행해야 한다. feature flag는 모두 기본 OFF이고 실제 issuer/JWKS·rotation, migration dry-run/apply, 구버전 writer drain, Identity retry·staging E2E, P99 1초/HTTP 2초 gate 전에는 production consumer를 활성화하지 않는다.
- 2026-09-04 `TMI-125` UserMerged 계획의 착수 상태를 다시 확인했다. 구현 계획서와 계약 결정서 모두 Jira를 이미 `TMI-125`로 연결하고 있으며 C12~C18 승인으로 구현 전 추가 제품 선택은 없다. 따라서 Jira를 새로 만들지 말고 기존 `TMI-125`에서 구현을 시작하면 된다. 환경별 issuer/JWKS·rotation, Mongo topology/index, 전용 replica-set 통합 테스트, staging 성능·E2E와 rollout은 구현 또는 production 활성화 gate로 남아 있지만 새 Jira 생성 전제나 코드 착수 차단 사안은 아니다. 애플리케이션과 Jira 상태는 변경하지 않았다.
- 2026-09-04 `TMI-125` UserMerged Transaction의 12단계 처리 순서를 설명했다. inbox digest로 duplicate/conflict를 먼저 판정하고, withdrawal marker는 영구 충돌 `409`, non-terminal creation operation은 일시 전제조건 `503`으로 mutation 전에 종료한다. source/target UUID를 정렬해 같은 순서로 두 guard를 touch한 뒤 active Session 충돌 정책, Result·Summary·Session owner 이전, source guard `MERGED`, inbox `PROCESSED`를 하나의 Mongo Transaction으로 commit한다. 애플리케이션과 외부 계약은 변경하지 않았다.
- 2026-09-04 `TMI-125`의 ownership guard와 revision 개념을 설명했다. guard는 사용자별 `ACTIVE/MERGED` 상태를 가진 접근 제어·동시성 문서이고, revision은 guard를 쓰는 작업마다 증가하는 버전 번호다. 순수 조회는 상태만 확인하고 revision을 변경하지 않으며, 사용자 데이터 쓰기와 UserMerged는 같은 Mongo Transaction에서 guard revision을 update하여 동시 실행 시 write conflict와 재시도로 한쪽 순서에 수렴시킨다. 애플리케이션과 외부 계약은 변경하지 않았다.
- 2026-09-04 `TMI-125`의 ownership guard와 Transaction 경계를 설명했다. 순수 조회는 guard를 읽은 순간 `ACTIVE`인지 확인만 하고 revision을 변경하지 않으므로 merge commit 전에 확인을 끝낸 in-flight 조회는 완료될 수 있지만, merge commit 뒤 확인한 source 조회는 `MERGED`로 거절된다. 모든 source/target 쓰기는 기존 업무 Mongo Transaction 안에서 guard revision을 함께 touch하여 merge와 같은 문서를 수정하게 하고, write conflict와 재시도로 write 누락 없이 한쪽 순서로 수렴시킨다. Billing saga·Summary completion·AttemptGroup coordinator처럼 이미 Transaction을 연 command에서는 별도 `TransactionTemplate`을 중첩하지 않고 그 body에 guard touch를 참여시킨다. 애플리케이션 구현과 계약은 변경하지 않았다.
- 2026-09-04 사용자가 `TMI-125` 신규 권장안 C12~C18을 승인해 `AGENTS.md`, `USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`, `USER_MERGED_CONTRACT_DECISIONS.md`를 구현 기준으로 갱신했다. source/target non-terminal `ExamCreationOperation`은 mutation 없이 `503 + Retry-After: 5`, terminal operation snapshot은 rewrite하지 않는다. 기존 AttemptGroup outbox eventId·userId·canonical payload·digest는 불변으로 Billing legacy-source fence에 전달하고 merge 이후 새 event만 target owner를 사용한다. security chain은 UserMerged 0/UserWithdrawn 1/일반 JWT·Legacy 2, active withdrawal marker는 `409`, 미확정 Transaction 경합만 `503`으로 고정했다. Identity connect/read `PT1S/PT3S` 아래 direct Transaction P99 1초·전체 HTTP 2초 미만 초기 gate와 전용 Testcontainers replica-set `mongoIntegrationTest` CI task를 확정했다. UserMerged 구현은 Jira 단건 예외가 아닌 경계 제한형 영구 허용으로 추가했으며, 사전 검토 문서는 역사적 snapshot임을 표시했다. 애플리케이션 구현과 production 활성화는 수행하지 않았다.
- 위 TMI-125 계획 갱신 결과를 현재 turn 기록 ID로 WORKLOG EOF에 동기화했다. 구현 전 추가 제품 선택은 없으며 실제 코드 구현, Gradle test와 production 활성화는 아직 수행하지 않았다.
- TMI-125의 “구현 착수 기준 승인, production 활성화 승인은 아님”은 추가 설계 결정을 요구한다는 뜻이 아니다. 승인된 계획대로 코드·테스트·migration·설정을 구현할 수 있지만, 실제 사용자 merge event를 운영에서 보내고 처리하는 feature flag는 merge·배포, Mongo/index, workload JWT 운영값, staging E2E·성능 gate와 rollback/관측 준비를 확인한 뒤 별도 go-live 단계에서 켠다는 의미다.
- 2026-09-04 `TMI-125` 최신 코드 재검토 기준으로 기존 C1~C11 제품·보안 방향은 이미 확정됐고, 구현 전 남은 선택을 신규 경합·운영 항목으로 분리했다. 권장 조합은 non-terminal `ExamCreationOperation` 존재 시 `503` 재시도, 기존 AttemptGroup outbox 불변 유지, UserMerged 0/UserWithdrawn 1/일반 catch-all 2의 exact chain, durable withdrawal marker 충돌 `409` fail-closed와 일시 경합만 `503`, Identity read timeout `PT3S` 아래 direct Transaction P99 1초·전체 HTTP 2초 초기 gate 및 실패 시 async 계약 개정, 전용 `mongoIntegrationTest` replica-set task, Jira 단건 예외가 아닌 경계 제한형 UserMerged 영구 허용이다. 애플리케이션과 계획 본문은 아직 변경하지 않았다.
- 2026-09-04 `TMI-125` 계획 검토에서 언급한 `SecurityFilterChain` 순서 문제를 설명했다. Spring Security는 낮은 `@Order`부터 chain을 검사하고 첫 번째로 matcher가 맞는 chain 하나만 적용하므로, UserMerged workload endpoint가 일반 사용자/Legacy chain보다 먼저 선택되고 UserWithdrawn endpoint와 서로 다른 전용 audience 검증기를 사용하도록 순서를 명시해야 한다. 현재 UserWithdrawn exact chain은 `@Order(1)`, 사용자 JWT/Legacy catch-all chain은 `@Order(2)`이므로 최소 변경 권장안은 UserMerged exact chain을 `@Order(0)`으로 추가하고 기존 순서는 유지하는 것이다. 같은 `@Order(1)`을 사용한다고 즉시 오작동하는 것은 아니지만 bean 정렬 의존성이 생겨 향후 matcher 변경 시 잘못된 chain 선택 위험이 있으므로 피한다. `/internal/v1/events/user-merged`는 `learning-core-user-merged`, `/internal/v1/events/withdrawn`은 `learning-core-user-withdrawn` audience를 각각 검증한다. signature·issuer·audience 등 token 자체가 잘못되면 401, token 검증은 성공했지만 `sub`가 `identity-service`가 아니면 authorization 403으로 분리한다. 코드·계획·Jira는 변경하지 않았다.
- 2026-09-04 `TMI-125` UserMerged consumer 구현 계획을 Identity `TMI-123` merge 결과, Billing `TMI-120` owner rebind 계약과 현재 Learning Core 코드에 다시 대조했다. 기존 source/target guard·direct Transaction·204/409/503·Session/Result/Summary migration 방향은 유지 가능하지만 지금 상태로는 구현 착수 전 계획 개정이 필요하다. 작성 뒤 추가된 `exam_creation_operations.userId`와 `attempt_group_event_outbox.userId/canonicalPayload`가 ownership inventory에서 누락됐고 Billing saga·AttemptGroup writer/Transaction 경로도 guard 전환 목록에 빠져 있다. active source/target creation operation은 owner를 rewrite하지 않고 terminal까지 503 재시도하는 정책을 권장하며, 기존 AttemptGroup outbox payload/digest/userId는 불변으로 유지하고 Billing의 legacy-source fence로 전달을 수렴시켜야 한다. workload 계약은 RS256, `aud=learning-core-user-merged`, `sub=identity-service`, `iat=nbf`, TTL `PT2M`, UUID `jti`, `typ=JWT`, `kid`로 이미 확정됐고 `service` claim은 존재하지 않는다. Identity 신규 owner-eventvent read timeout은 `PT3S`이므로 계획의 5초/P99 기준도 재산정해야 한다. 현재 `SecurityConfig`의 UserWithdrawn internal chain이 이미 `@Order(1)`이므로 UserMerged chain의 동일 order 추가를 피하고 두 internal endpoint의 선택·401/403을 함께 회귀 테스트해야 한다. `AGENTS.md`에는 아직 Guest UserMerged 구현이 명시적으로 제외돼 있어 구현 전 영구 허용 절을 추가해야 한다. 코드와 계획 본문·Jira는 변경하지 않았으며 feature flag는 계속 OFF다.
- 2026-09-04 Identity `TMI-123`의 완료 상태를 최종 확인했다. Identity `develop`과 `origin/develop`은 PR #38 merge commit `fa9843e`로 일치하고 후속 구현 commit `1110b8a`를 포함한다. Jira `TMI-123`은 상태와 Resolution 모두 `완료`이며, exact `/internal/v1/events/user-merged`, `learning-core-user-merged` workload audience, Billing·Learning Core 독립 delivery와 HTTP 415 계약 오류 분류가 병합됐다. 병합 전 Identity 전체 테스트 630개가 실패·오류·건너뜀 없이 통과했다. 다음 개발 작업은 현재 `해야 할 일` 상태인 Learning Core `TMI-125` UserMerged consumer 및 ownership migration이며, 실제 publisher retry를 수행하는 동일 eventId·payload 검증과 workload 운영값·Mongo replica-set/staging E2E는 후속 검증으로 남아 있다.
- 2026-09-03 로그인 내용을 제외한 프론트 1차 업데이트 인계서 `docs/codex/FRONTEND_NON_LOGIN_UPDATE_GUIDE.md`를 작성했다. 시험 생성의 lowercase UUID v4 `Idempotency-Key` 생성·보관·same-key transport retry, 오류별 key 유지/폐기, 최초 1회 권리 확정과 `OPEN`·`GRADING`·`RETAKE_AVAILABLE`·`COMPLETED` 경계, 추가 차감 없는 처음부터 재시작, 음성 업로드·submit·polling, grading retry·Summary 완료 gate, Part 4 `tableContext`, phone 재가입 continuation과 Billing 내부 API 금지를 정리했다. TMI-116·TMI-118·TMI-120·TMI-122 기반 코드는 있으나 관련 flag는 기본 off이고 결제 공개 API·Challenge runtime·Guest UserMerged 종단 연결은 후속임을 구분했다. Jira 상태는 변경하지 않았다.
- 2026-09-03 프론트엔드 로그인 인계 문서 `docs/codex/FRONTEND_LOGIN_INTEGRATION_GUIDE.md`를 작성했다. Firebase ID Token은 Identity 교환·가입에만 사용하고 Identity Access Token만 Learning Core Bearer로 보내는 경계, 기존 MEMBER exchange, 신규 가입의 동일 Firebase UID phone link·force refresh, Guest 생성·upgrade·merge, Refresh Token rotation single-flight, logout과 주요 오류 UX를 정리했다. Firebase·provider flag는 기본 off이고 Guest merge는 Identity `TMI-123` fan-out, Learning Core UserMerged consumer와 cross-service staging E2E 전에는 production에 노출하지 않는다고 명시했다. 관련 이력은 Identity `TMI-109`, `TMI-111`, `TMI-114`, `TMI-123`, Learning Core `TMI-116`, `TMI-118`, `TMI-122`, Billing `TMI-120`이며 Jira 상태는 변경하지 않았다.
- 2026-09-03 중간 발표용 개발 측면 예상 문제점을 `기능 범위 확대로 인한 일정 지연`과 `학습 콘텐츠 품질 편차` 두 항목으로 재정리했다. 해결 방향은 1차 출시 필수 기능 우선순위·단계별 개발·범위 변경 관리와, 콘텐츠 생성 기준·자동 유효성 검사·수동 검수·품질 모니터링을 통한 검증된 콘텐츠 배포다. 별도 Jira 이슈가 없으며 코드·AWS·외부 계약은 변경하지 않았다.
- 2026-09-03 선행 Identity `TMI-123`의 Learning Core UserMerged 후속 구현을 검토했다. exact endpoint `/internal/v1/events/user-merged`, typed `USER_MERGED` audience `learning-core-user-merged`, `USER_WITHDRAWN` 회귀, 신규·legacy publisher, 415 계약 오류와 Billing/Learning Core 독립 재시도는 코드·계약과 일치했고 Identity `./gradlew clean test` 전체 630개와 `git diff --check`가 성공했다. 원 TMI-123은 PR #37 merge commit `391b55f`로 Identity `develop`에 병합됐지만 이번 후속 변경은 현재 삭제된 원격을 가리키는 로컬 feature branch의 미커밋·미추적 상태여서 아직 develop에 반영되지 않았다. 차단급 코드 결함은 없으나 실제 publisher retry의 동일 eventId·payload를 검증한다는 테스트가 동일 객체 직렬화 비교에 그쳐 후속 PR 전 보강을 권장한다. Jira `TMI-123`은 여전히 `해야 할 일`이며 Learning Core `TMI-125` 구현·Mongo/staging 검증은 남아 있다.
- 2026-09-03 `TMI-125` 착수를 위해 Identity 팀에 전달할 `TMI-123` 후속 요청을 정리했다. Identity 책임은 UserMerged의 BILLING·LEARNING_CORE 독립 fan-out 완성·병합, Learning Core exact endpoint를 `/internal/v1/events/user-merged`로 통일, UserMerged 전용 audience `learning-core-user-merged`를 임의 audience 없이 발급 가능한 고정 allowlist/별도 profile로 추가하고 RS256·workload issuer/JWKS·`sub=identity-service`·TTL `PT2M` 계약을 유지하는 것이다. TrialOwnerRebindApproved는 Billing-only이고, Billing은 SigV4·Learning Core UserMerged는 Bearer workload JWT 경계를 유지한다. 실제 Mongo topology·Transaction·P99 검증은 Identity 요청이 아니라 Learning Core·인프라 책임으로 분리했다. 코드·Jira·AWS·DB는 변경하지 않았다.
- 2026-09-03 종료 훅 요구에 따라 `TMI-125` 선행 조건 검토 기록을 동기화했다. 결론은 Identity `TMI-123`이 아직 미커밋·미병합 상태이고, 양쪽의 UserMerged endpoint 경로와 workload JWT 전용 audience를 먼저 통일해야 하며, 실제 staging/prod Mongo Transaction topology·rollback·동시성·P99 증빙이 필요하다는 것이다. 애플리케이션 코드·Jira·AWS·DB는 변경하지 않았다.
- 2026-09-03 `TMI-125` 착수 전 선행 확인사항을 Learning Core·Identity 로컬 코드와 계약에서 대조했다. Identity `TMI-123`은 현재 Jira `해야 할 일`, 로컬 `feat/TMI-123-owner-event-fanout-sigv4`의 미커밋 작업 상태라 완료·병합·배포 증거가 아직 없다. 로컬 구현은 `UserMerged`를 BILLING과 LEARNING_CORE에 독립 delivery로 fan-out하는 구조를 갖췄지만 Learning Core endpoint를 `/internal/v1/owners/merge/events`로 요구해 TMI-125 계획의 `/internal/v1/events/user-merged`와 불일치한다. 또한 기존 Identity workload JWT provider는 audience를 `learning-core-user-withdrawn` 하나로 고정하므로 UserMerged 전용 audience를 그대로 발급할 수 없다. 구현 전 exact endpoint, `learning-core-user-merged` 같은 전용 audience, RS256·workload issuer/JWKS·`sub=identity-service`·TTL `PT2M`·skew `PT30S`와 multi-key rotation을 양쪽 계약으로 확정해야 한다. Learning Core에는 Billing·AttemptGroup·UserWithdrawn용 MongoTransactionManager와 startup rollback probe가 이미 있어 기반은 존재하지만, 실제 staging/prod Mongo가 replica set 또는 sharded transaction을 지원하는지, 필수 index와 replica-set 통합 테스트, direct migration P99가 Identity의 현재 3초 read timeout 안에서 충분한 여유를 갖는지는 아직 운영 증빙이 필요하다. 이번 확인은 코드·Jira·AWS·DB를 변경하지 않았다.
- 2026-09-03 Jira `TMI-125` `[Learning Core] UserMerged consumer 및 ownership migration 구현`을 `작업` 유형, `해야 할 일` 상태로 생성했다. 이슈 설명에는 Identity `TMI-123` 선행 의존성, internal event endpoint, inbox 멱등성, source/target ownership guard, Session·Result·Summary owner 이전, 활성 시험 target 우선 정책, 모든 writer·Callback Transaction 전환, workload JWT 보안, 완료 조건·통합 테스트·배포 gate와 제외 범위를 기록했다. UserMerged 계획서와 계약 결정서의 Jira 메타데이터도 `TMI-125`로 갱신했다. 애플리케이션 코드·설정·테스트, AWS와 DB는 변경하지 않았다.
- 2026-09-03 Learning Core `UserMerged` consumer의 목적과 구현 경계를 현재 확정 문서 기준으로 다시 설명했다. Identity가 Guest source 계정을 최종 MEMBER target 계정으로 병합한 뒤 보내는 schema v1 event를 받아, 한 Mongo Transaction에서 source의 `exam_sessions`, `exam_results`, `exam_summaries` 직접 `userId`를 target으로 이전하고 활성 시험 충돌 정책, source `MERGED` deny guard와 inbox 멱등 처리를 함께 확정하는 작업이다. `examId` 기반 Job·Redis·S3 객체는 이동하지 않으며 공개 API·AI `user_id=examId` 계약도 유지한다. 구현 전 Identity `TMI-123` 발행·fan-out 완료, workload JWT 운영값, Mongo Transaction/성능 gate와 별도 Learning Core Jira가 필요하다. 이번 설명에서는 코드·Jira·AWS·DB를 변경하지 않았다.
- 2026-09-03 AttemptGroup 상태표에 누락됐던 `OPEN`을 추가해 미제출 중단과 채점 실패 경계를 명확히 했다. 필수 retry 0 제출이 모두 접수되지 않은 시험은 `GRADING`이나 `RETAKE_AVAILABLE`로 넘어가지 않고 `OPEN`에 남는다. 사용자의 다음 시작 요청은 `OPEN → OPEN` Session 교체로 처리해 기존 Session을 `ABANDONED_RESTARTED`로 닫고 추가 entitlement 차감 없이 새 examId로 처음부터 시작한다. 모든 필수 제출과 durable Question Job 확인 뒤에만 `GRADING`으로 전환하며, 이후 채점·Summary 복구 최종 실패가 `RETAKE_AVAILABLE`의 주 경로다. 자동 복구 불가능한 결과 정합성 위반은 안전을 위해 발견 시점과 무관하게 즉시 `RETAKE_AVAILABLE`이 될 수 있다. 관련 구현은 `TMI-116`, `TMI-118`이며 Jira 상태는 변경하지 않았다.
- 2026-09-03 세 앱 서버의 현재 `develop`, 작업 트리와 Jira·테스트 기록을 다시 대조해 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 갱신했다. Learning Core `TMI-118` AttemptGroup outbox/publisher는 PR #25·#26과 444개 테스트·Jira 완료, Billing `TMI-120` owner rebind/phone continuation은 PR #6~#8 병합, Learning Core `TMI-122` phone continuation은 PR #27과 457개 테스트로 `develop`에 반영됐다. Identity `TMI-123` SigV4·owner event fan-out은 Jira `해야 할 일`이며 현재 작업 트리에서 구현 진행 중이지만 테스트 완료·commit·병합 기록은 아직 없다. 무료시험 핵심 양방향 코드는 대부분 갖춰졌으나 Learning Core `UserMerged`, Billing replica-set Testcontainers, 실제 Lattice/IAM/SG·Mongo migration·staging E2E와 Challenge backend가 남아 production release는 계속 차단한다. 이번 점검은 애플리케이션·Jira·AWS를 변경하지 않았다.
- 2026-09-03 종료 훅 기록을 동기화했다. 미완료 시험 정책의 사용자 표현은 “최초 응시에서 1회만 차감하고, 완료할 때까지 추가 차감 없이 처음부터 재시작”으로 유지한다. 이는 새 무료 응시권을 반복 발급하는 것이 아니라 `OPEN` 또는 `RETAKE_AVAILABLE`인 동일 consumption·AttemptGroup·mockExamId에 새 examId의 Session을 연결하는 의미다. 관련 구현 이력은 `TMI-116`, `TMI-118`이며 Jira 상태는 변경하지 않았다.
- 2026-09-03 시험 재시작 정책의 사용자 표현을 “무제한 무료 새 시험”이 아니라 “최초 응시에서 1회만 차감하고, 완료할 때까지 추가 차감 없이 처음부터 재시작”으로 명확히 했다. 새로운 무료 응시권을 반복 지급하는 것이 아니라 동일 consumption·AttemptGroup·mockExamId 안에서 새 examId의 Session으로 교체하는 구조다. `OPEN`과 `RETAKE_AVAILABLE`에서 적용하고, `GRADING`은 기존 채점·Summary 복구를 우선하며, `COMPLETED` 이후에만 다음 entitlement를 차감한다. 관련 구현 이력은 `TMI-116`, `TMI-118`이며 Jira 상태는 변경하지 않았다.
- 2026-09-03 Firebase SNS 흐름의 phone credential 표현을 정정했다. 신규 MEMBER 가입과 Guest→MEMBER 승격에서는 primary Google·Apple 등으로 인증한 동일 Firebase UID에 verified phone credential을 반드시 link해야 하며, 없거나 미검증이면 Identity가 `FIREBASE_PHONE_VERIFICATION_REQUIRED`로 거절한다. phone은 무료시험 phone당 1회와 ACTIVE MEMBER 중복 방지 proof에 필수지만 phone-only 로그인 수단이나 자동 merge key는 아니다. 이미 가입 시 인증을 완료한 MEMBER는 매 로그인마다 SMS 인증을 반복하지 않고 phone 변경·재가입 등 새 proof가 필요한 흐름에서만 다시 인증한다. 신규 Jira 키는 없으며 Jira 상태는 변경하지 않았다.
- 2026-09-03 앱 기능을 인증·Guest·SNS·회원 lifecycle·무료시험·시험 생성·이어풀기 없는 restart·문제 제공·S3 제출·AI 채점·Summary·채점 복구·AttemptGroup·시스템 failure replacement·phone 재가입·결제·Challenge로 나누고 각 기능의 사용자 동작, 서비스 책임, 상태 전이, 실패 처리와 현재 활성화 상태를 `docs/codex/APP_FEATURE_LOGIC_OVERVIEW.md`에 정리했다. 핵심 시험 runtime과 TMI-116·TMI-118·TMI-122 기반은 구현·develop 병합됐지만 관련 flag는 기본 off이며, 결제 공개 API·Guest UserMerged 전체 owner 이전·Challenge runtime은 후속이다. 이번 작업에서 Jira 상태는 변경하지 않았다.
- 2026-09-03 현재 앱 흐름을 구현 코드와 계약 기준으로 재정리했다. 사용자는 Identity의 LOCAL·Guest 또는 Firebase SNS 흐름으로 Access/Refresh Token을 받고, 앱은 Identity Access Token만 Bearer로 Learning Core에 전달한다. 모의고사는 새 lowercase UUID v4 Idempotency-Key로 생성하며 Billing saga flag가 켜진 환경에서는 reserve → ExamSession durable commit → confirm으로 권리를 확정하고, 같은 transport retry는 동일 key로 같은 결과에 수렴한다. 기존 진행 시험은 이어풀기하지 않고 새 시험 시작 시 ABANDONED 처리한다. 각 문항은 prompt 조회 → Presigned URL 발급 → S3 raw audio PUT → body 없는 submit → 문항/시험 polling → Summary·문항 결과·이력 조회 순서다. Part 4 표는 tableContext JSON으로 전달한다. 최초 retryCount=0 채점 증거가 모이면 AttemptGroup GRADING/COMPLETED 또는 시스템 복구 불가 시 RETAKE_AVAILABLE event를 Billing에 durable outbox로 전달한다. phone 재가입 사용자의 target Session이 0건이면 별도 flag 아래 Billing continuation을 조회해 과거 답안 복사 없이 기존 AttemptGroup·mockExam에 새 examId를 연결할 수 있다. Billing creation/phone continuation/AttemptGroup writer·publisher/UserWithdrawn consumer flag는 기본 off이며 production 활성화 전 Lattice/IAM/Mongo/staging E2E가 필요하다. 결제 상품·스토어 구매/복원과 10초 챌린지 API, Guest UserMerged owner rebind는 현재 사용자 호출 가능한 완성 흐름이 아니다. 관련 구현 Jira는 TMI-116·TMI-118·TMI-122이며 이번 분석에서 Jira 상태는 변경하지 않았다.
- 2026-09-03 Jira `TMI-122` phone 재가입 시험 continuation을 구현했다. `AGENTS.md`는 단건 예외 대신 확정 capability와 동일 경계의 후속 안정화를 영구 허용하도록 바꿨다. target user의 ExamSession이 0건이고 별도 flag가 켜진 경우에만 Billing discovery를 호출하며, 204는 기존 INITIAL, strict 200은 Billing 기존 AttemptGroup·mockExamId를 snapshot한 새 PHONE_REJOIN REPLACEMENT로 처리한다. operation에 continuation reason/id와 expected group/mock을 불변 저장하고, 일반 reserve 3필드와 phone reserve 6필드를 분리했으며 reserve/status echo 검증, 응답 유실 status 복구와 계약 불일치 status-first cancel fencing을 추가했다. Billing HTTP client는 호출별 CLIENT span의 traceparent를 서명 전 주입하고 SigV4를 마지막에 수행하며 식별자 없는 구조화 로그·저카디널리티 metric을 남긴다. flag 기본값은 off이고 phone flag 단독 활성화는 startup에서 거절한다. 공개 API·BaseResponse·AI·S3·Redis 계약과 source 시험 데이터는 변경하지 않았다. 관련 테스트와 전체 `./gradlew clean test`는 성공했고, production 활성화 전 Billing 배포·Lattice exact route·staging E2E가 남아 있다.
- 2026-09-03 사용자와 TMI-122 범위를 Jira 단건 예외가 아니라 영구 허용 규칙으로 둘지 검토했다. 모든 Billing·owner rebind 변경을 포괄적으로 여는 것은 결제·권리·데이터 소유권 경계를 무력화하므로 권장하지 않지만, 확정 계약을 따르는 `phone 재가입 시험 continuation` capability와 그 후속 버그 수정·테스트·운영 안정화만 영구 허용하는 방식은 Jira 예외 누적보다 적절하다. 권장 구조는 현재 TMI-122 절을 `Phone 재가입 시험 continuation 허용 규칙`으로 바꾸고 TMI-122를 최초 구현 이력으로만 기록하며, 공개 API·source 데이터 비이전·Billing 저장소 비수정·default-off·SigV4/Lattice·staging gate 금지선은 유지하는 것이다. 이번 검토에서는 `AGENTS.md`를 추가 수정하지 않았고 구현도 계속 일시 중단 상태다.
- 2026-09-03 사용자가 `AGENTS.md`의 기존 범위가 phone 재가입 owner continuation을 허용하지 않는다는 의미를 질문해 TMI-116·AttemptGroup 범위와 신규 TMI-122 예외의 관계를 설명했다. 기존 TMI-116 예외는 reserve·confirm·cancel·status 기반 최초 Billing saga 구현에만 적용되고 다른 Jira로 자동 확장되지 않으며, AttemptGroup 영구 허용도 UserMerged·owner rebind와 Billing Reservation saga 확장을 명시적으로 제외한다. 이는 제품 기능 금지가 아니라 Codex가 별도 승인 없이 작업 범위를 넓히지 못하게 하는 저장소 작업 거버넌스다. 사용자가 계획·Jira `TMI-122`·구현을 승인했으므로 직전 중단된 구현 턴에서 `AGENTS.md`에 phone continuation만 허용하는 제한적 예외를 추가했으며 애플리케이션 코드는 아직 변경하지 않았다.
- 2026-09-03 Jira `TMI-122` `[Learning Core] phone 재가입 시험 continuation 연동`을 `작업` 유형, `해야 할 일` 상태로 생성했다. 목표·구현 범위·완료 조건·필수 테스트·배포 순서와 제외 범위를 `PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md` 기준으로 기록했고, Billing `TMI-120`이 `TMI-122`를 선행 차단하는 Blocks 관계를 연결해 확인했다. 계획서의 Learning Core Jira 메타데이터와 Phase 0 체크리스트도 `TMI-122`로 갱신했다. `TMI-120`의 Jira 상태는 현재 `해야 할 일`이지만 Billing PR #8 merge commit `7138810`의 코드 병합 사실은 별도로 확인돼 있으며 이번 작업에서 상태를 변경하지 않았다. 애플리케이션 코드·설정·테스트·외부 계약은 변경하지 않았다.
- 2026-09-03 Billing phone continuation 구현이 PR #8 merge commit `7138810`으로 Billing `develop`과 `origin/develop`에 일치하고 작업 트리가 clean인 것을 확인했다. 상류 구현 commit은 `b61ebb9`, Jira는 `TMI-120`이다. 확정된 Billing wire를 기준으로 `docs/codex/PHONE_REJOIN_CONTINUATION_IMPLEMENTATION_PLAN.md`를 작성했다. 계획은 phone 재가입과 Guest `UserMerged`를 분리하고, target Session 0건일 때만 discovery, 204 INITIAL/200 PHONE_REJOIN REPLACEMENT, operation 불변 snapshot, 3-field/6-field reserve, reserve 응답 유실 status 복구, contract mismatch status-first cancel, 204 전용 decoder, default-off flag, 새 client span traceparent 후 SigV4 최종 서명과 reader-first rollout을 확정한다. Learning Core 전용 Jira와 `AGENTS.md` 허용 범위 추가 후 구현해야 하며 애플리케이션 코드는 아직 변경하지 않았다.
- 2026-09-03 Billing `TMI-120`의 phone 재가입 continuation 인계안을 Learning Core 현재 시험 생성 saga와 대조했다. 과거 Session·답안·결과를 이전하지 않고 Billing이 승인한 기존 AttemptGroup·mockExamId에 새 target `examId`를 연결하는 방향, 204 시 기존 INITIAL 유지, PHONE_REJOIN 세 field exact echo, 응답·status strict 검증, SigV4/Lattice와 reader-first rollout은 타당하다. 구현 전에는 이 작업이 Guest `UserMerged` consumer를 대체하지 않는 별도 phone lifecycle임을 명시하고, continuation 조회 조건을 `같은 operation의 최초 준비이며 target ExamSession이 전혀 없음`으로 고정하며, 조회 결과를 outbox가 아닌 `ExamCreationOperation`에 reserve 전에 영속화해야 한다. 또한 204 empty-body 전용 decoder, 별도 default-off flag, status의 continuation field 검증과 계약 불일치 시 untrusted reservationId 직접 cancel이 아닌 operation status 재조회 기반 보상을 계획에 추가해야 한다. 현재 시험 생성용 SigV4 client에는 trace context 주입이 없으므로 `traceparent 전파 유지`가 아니라 W3C header를 서명 전에 새로 inject하고 SigV4를 마지막 변경 단계로 두는 신규 구현으로 명시해야 한다. 코드·외부 계약·Billing 작업 트리는 변경하지 않았다.
- 2026-09-02 1차 업데이트 간결 구성도의 토선생 앱 도형을 사용자가 제공한 토끼 PNG의 축소본을 base64로 내장한 이미지로 교체했다. AWS 리소스는 기존 공식 AWS4 아이콘을 유지하고 Atlas는 DB 실린더, 외부 AI Provider는 별도 외부 서비스 도형으로 구분했다. 아이콘과 텍스트를 독립 도형으로 분리하고 주요 8개 연결을 라벨 경계끼리 연결해 화살표가 글자 위를 통과하지 않도록 수정했다. 68,167 byte PNG가 draw.io 내부에 포함되어 원본 Desktop 경로 없이도 열린다. XML, base64 PNG, ID·레이어와 `git diff --check`를 검증했으며 애플리케이션 코드·AWS 리소스·외부 계약은 변경하지 않았다.
- 2026-09-02 종료 기록 동기화: 1차 업데이트 간결 구성도의 AWS4 아이콘 적용 결과를 재확인했다. ALB·Fargate 4개·S3·ElastiCache의 7개 AWS 아이콘, 16개 도형·10개 연결선과 후면 edge layer가 유지된다. 이번 종료 동기화에서는 구성도·애플리케이션 코드·AWS 리소스·외부 계약을 추가 변경하지 않았다.
- 2026-09-02 1차 업데이트 간결 구성도의 AWS 리소스에 diagrams.net AWS4 Architecture 아이콘을 적용했다. ALB는 Application Load Balancer 아이콘, Identity·Learning Core·AI·Billing은 각각 Fargate 아이콘, 음성 저장소는 S3 아이콘, Valkey는 ElastiCache 아이콘으로 표시했다. 앱과 MongoDB Atlas·외부 AI Provider처럼 AWS 관리 리소스가 아닌 요소는 일반 도형을 유지해 AWS 경계를 구분했다. 기존 16개 도형·10개 연결선과 후면 edge layer, 서비스 연결 관계는 유지했으며 코드·AWS 리소스·외부 계약은 변경하지 않았다.
- 2026-09-02 1차 업데이트 AWS 구성도를 발표용으로 한 단계 더 단순화했다. VPC/Subnet/NAT, ECR, GitHub Actions와 운영·관측 세부 도형을 제거하고 토선생 앱→가비아 DNS·ALB→Identity/Learning Core의 사용자 진입, ECS Fargate의 Identity·Learning Core·AI·Billing 태스크 각 1개, S3·Valkey·MongoDB Atlas와 외부 AI Provider만 남겼다. Learning Core↔AI의 Service Connect와 Learning Core↔Billing의 VPC Lattice·SigV4는 연결선 라벨로 유지했다. 도형은 25개에서 16개, 연결선은 15개에서 10개로 줄었고 모든 연결선은 후면 `edge-layer`에 있다. 애플리케이션 코드·AWS 리소스·외부 계약은 변경하지 않았다.
- 2026-09-02 간결 AWS 구성도를 현재 실배포가 아니라 1차 업데이트 완료 목표로 수정했다. Identity·Learning Core·Billing·AI를 ECS Fargate 서비스별 태스크 1개로 통일하고, AI는 Worker 없는 단일 FastAPI 서버로 표시했다. Learning Core↔AI는 Service Connect, Learning Core→VPC Lattice↔Billing은 SigV4/AWS_IAM 기반 비공개 통신으로 구분했으며 Billing을 ALB 공개 경로에 연결하지 않았다. Valkey의 AI Job Queue 표기를 제거하고 Learning 상태·Lock만 남겼으며 MongoDB Atlas와 ECR에 Billing을 반영했다. 현재 콘솔은 staging 접두어, Billing 미배포, AI Worker 4개 상태라는 차이는 하단 주의 문구로 보존했다. 애플리케이션 코드·AWS 리소스·외부 계약은 변경하지 않았다.
- 2026-09-02 Jira `TMI-118` Summary Transaction hotfix가 commit `4781723`, PR #26 merge commit `4f9e74c`로 현재 로컬·원격 `develop`에 반영된 것을 확인했다. 따라서 다음 즉시 작업은 AttemptGroup rollout을 막고 있는 실제 경계 통합 검증이다. 격리된 replica-set Mongo에서 commit/rollback·duplicate·unknown commit·terminal race와 multi-instance lease reclaim/stale fencing을 검증하고, fake signer/HTTP component test로 traceparent 주입 후 SigV4 최종 서명과 금지 데이터 부재를 확인한 뒤 Learning Core/Billing staging에서 동일 traceId·서로 다른 spanId·baggage 미전파 E2E를 수행한다. 이 gate 이후 다음 제품 기능은 Billing `UserMerged` retained subject owner rebind ADR·계획서·신규 Jira다. 코드·Jira·DB·AWS는 변경하지 않았고 기존 미추적 draw.io 2개를 보존했다.
- 2026-09-01 Jira `TMI-118` hotfix의 사용자 commit·push 명령을 현재 변경 파일 기준으로 정리했다. `git add .` 대신 애플리케이션 2개, 신규 테스트 2개와 필수 CURRENT_STATE·WORKLOG만 명시적으로 stage하고 `fix(TMI-118): retry aborted summary transactions` 메시지로 commit한 뒤 현재 브랜치 `codex/fix-tmi-118-summary-transaction`을 origin에 upstream 설정해 push하도록 안내한다. 명령 안내만 수행했고 Git commit·push는 실행하지 않았다.
- 2026-09-01 종료 훅 동기화: Jira `TMI-118` Summary Transaction hotfix는 새 브랜치 `codex/fix-tmi-118-summary-transaction`에서 완료됐다. abort된 Transaction 밖의 전체-unit 재시도, Summary Job false rollback, nested coordinator 예외 전파와 회귀 테스트 5개를 적용했고 `./gradlew clean test` 전체 444개 및 `git diff --check`가 성공했다. 공개 API·AI·S3·Redis·Billing 계약은 유지했으며 commit·push·PR·merge·배포는 수행하지 않았다.
- 2026-09-01 Jira `TMI-118` Summary Transaction P1 hotfix를 새 브랜치 `codex/fix-tmi-118-summary-transaction`에서 구현했다. Summary deterministic ID를 Transaction 안에서 먼저 조회·identity 검증하고 없을 때만 insert하며, duplicate/optimistic/TransientTransactionError/UnknownTransactionCommitResult는 abort된 Transaction 내부에서 삼키지 않고 rollback 뒤 최대 3회의 새 Transaction으로 Summary+Job+Session+outbox 전체 단위를 재시도한다. Summary Job 완료가 false면 Transaction을 rollback-only로 만들어 Summary 단독 commit을 막았다. coordinator에는 outer Transaction 전용 `reconcileWithinTransaction()`을 분리해 동시성 예외가 바깥 재시도 경계까지 전파되도록 했다. 중복 rollback 후 전체 재시도, false rollback, unknown commit, identity conflict와 nested duplicate 전파 테스트 5개를 추가했고 `./gradlew clean test` 전체 444개가 성공했다. 공개 API·BaseResponse·AI·S3·Redis·Billing wire 계약은 유지했다. 실제 replica-set failure injection, multi-instance lease, SigV4와 cross-service trace/privacy 통합 테스트는 별도 잔여 gate다.
- 2026-09-01 Jira `TMI-118` 사후 리뷰의 P1 두 건을 사용자에게 쉽게 설명했다. Mongo Transaction을 한 묶음의 결제 봉투로 보면 duplicate key가 발생한 순간 봉투 전체에 폐기 표시가 붙으며 Java catch는 알림만 숨길 뿐 봉투를 되살리지 못한다. 1번은 Summary duplicate를 catch한 뒤 이미 폐기된 같은 봉투에서 Job 완료·Session·outbox를 계속 변경하려는 결함이다. 2번은 coordinator가 새 Transaction처럼 보이지만 Summary의 outer Transaction에 참여하므로 outbox duplicate/optimistic conflict가 같은 봉투를 rollback-only로 만들고, coordinator가 예외를 삼키면 바깥 로직이 성공처럼 계속되다가 최종 commit에서 전체 rollback되는 결함이다. 해결은 예외를 Transaction 밖까지 보내 rollback을 끝낸 뒤 새 Transaction으로 Summary+Job+Session+outbox 전체를 재시도하는 것이다. 설명·기록만 수행했고 코드는 수정하지 않았다.
- 2026-09-01 Jira `TMI-118` 병합 코드의 사후 리뷰에서 P1 Transaction 결함을 확인했다. `AttemptGroupSummaryCompletionService.persistAndComplete()`는 Mongo Transaction callback 안에서 결정적 Summary `insert()`의 `DuplicateKeyException`을 삼킨 뒤 같은 Transaction으로 Summary Job 완료와 coordinator reconcile을 계속한다. Mongo duplicate key는 해당 Transaction을 abort하므로 이후 작업 또는 commit이 실패하며, 응답 유실·rolling deploy·동시 저장 재처리가 안정적으로 수렴하지 않는다. `AttemptGroupStateCoordinator.reconcile()`도 독립 호출에서는 execute 바깥 catch가 안전하지만 Summary의 바깥 Transaction에 참여할 때 duplicate/optimistic 예외를 내부에서 잡으면 outer가 rollback-only인 채 성공처럼 진행할 수 있어 전체 transaction 단위 재시도가 필요하다. 현재 AttemptGroup 테스트는 TransactionOperations·store·client·tracer mock 중심이고 Summary completion 전용 테스트와 replica-set/Testcontainers 의존성이 없어 실제 commit/rollback·duplicate/unknown commit·terminal race·lease reclaim·SigV4/trace/privacy 통합 검증이 없다. Billing owner rebind보다 TMI-118 hotfix와 replica-set integration suite를 먼저 진행해야 하며, 수정은 수행하지 않았다.
- 2026-09-01 종료 훅 동기화: Jira `TMI-116`은 사용자 요청에 따라 `완료`로 전환됐고 Jira status category `done`을 확인했다. PR #24 develop 병합과 432개 테스트 성공 근거는 유지되며, 이번 동기화에서는 기록 문서 외 애플리케이션·DB·AWS·Git 이력과 외부 계약을 변경하지 않았다.
- 2026-09-01 사용자 요청에 따라 Jira `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`을 `해야 할 일`에서 `완료`로 전환했고 Jira 응답에서 status category `done`을 확인했다. TMI-116 구현은 PR #24로 develop에 병합돼 있고 P1/P2 보완 후 전체 432개 테스트 성공 기록이 있다. 이번 작업에서는 Jira 상태와 기록 문서만 변경했으며 애플리케이션 코드·DB·AWS·Git 이력과 외부 계약은 변경하지 않았다.
- 2026-09-01 Jira `TMI-118` 완료 이후 다음 개발 우선순위를 재검토했다. 프로젝트 전체의 다음 1순위는 Billing의 탈퇴·재가입 `UserMerged` retained subject owner rebind다. Billing의 TrialClaim·entitlement·Reservation·AttemptGroup 소유권을 source Guest에서 최종 Member로 멱등 이전해야 무료 권리·사용 이력 단절과 중복 무료 지급을 막을 수 있다. Jira JQL 조회에서 관련 전용 이슈는 확인되지 않았으므로 먼저 Billing 계획서와 신규 Jira를 만들고 구현하는 순서가 맞다. 그 다음은 Identity→Billing SigV4/Lattice 정렬과 TMI-116 staging E2E, Learning Core UserMerged consumer, Challenge backend다. 관리상 Jira `TMI-116`은 구현·병합 기록과 달리 현재 `해야 할 일` 상태이므로 별도 상태 정리가 필요하다. 이번 검토에서는 코드·Jira·DB·AWS를 변경하지 않고 기록 문서만 갱신했다.
- 2026-09-01 종료 훅 동기화: Jira `TMI-118` 구현은 PR #25 merge commit `c00d872`를 통해 현재 로컬 `develop`과 `origin/develop`에 모두 반영돼 있고, 구현 commit `63d0f7d`의 ancestor 포함 관계와 현재 worktree의 AttemptGroup 소스 존재를 재확인했다. Jira 상태는 `완료`이며 애플리케이션 코드·DB·AWS·Git 이력은 변경하지 않았다.
- 2026-09-01 Jira `TMI-118` 로컬 반영을 재확인했다. 현재 `HEAD`, 로컬 `develop`, `origin/develop`은 모두 PR #25 merge commit `c00d872`를 가리키고 구현 commit `63d0f7d`은 develop의 ancestor다. `src/main/java/web/tosunsaeng/domain/exams/attemptgroup` 소스도 현재 worktree에 존재하므로 feature branch에만 있는 상태가 아니라 로컬 develop에 실제 반영됐다. Jira는 `완료` 상태이며 이번 확인에서는 애플리케이션 코드·DB·AWS·Git 이력을 변경하지 않고 기록 문서만 갱신했다.
- 2026-09-01 Jira `TMI-118`의 backfill 의미를 설명했다. 이번 backfill은 AttemptGroup outbox 기능이 생기기 전에 생성된 기존 CONFIRMED Billing-linked ExamSession 중 projection 상태가 없는 데이터만 대상으로, 먼저 dry-run으로 현재 시험 증거를 판정하고 운영자가 승인한 Session에 `attemptGroupProjectionStatus=OPEN`, projection version 0을 초기화한 뒤 coordinator가 GRADING·COMPLETED·RETAKE_AVAILABLE 상태와 outbox event를 생성하도록 연결하는 일회성 데이터 이관이다. 신규 Session은 정상 writer 흐름이 자동 처리하므로 대상이 아니며, 현재 local profile Atlas에는 대상이 0개여서 실행할 backfill이 없다. 설명과 문서만 갱신했고 애플리케이션 코드·DB·외부 계약은 변경하지 않았다.
- 2026-09-01 Jira `TMI-118` 추가 작업 여부 최종 확인: local profile Atlas의 backfill 후보와 CONFIRMED Billing-linked Session이 모두 0개이므로 현재 연결 DB를 위한 추가 제품 코드, 세션별 allowlist와 backfill apply는 필요 없다. staging/production이 별도 DB라면 각 환경 inventory를 별도로 확인해야 하며, Billing TMI-117 consumer 배포, Mongo Transaction·index, Lattice IAM, publisher idle 선활성, writer canary와 상태·오류 E2E는 배포·운영 단계의 필수 잔여 작업이다. 이번 확인은 문서만 동기화했고 애플리케이션 코드·DB·AWS·Jira·Git 상태와 외부 계약을 변경하지 않았다. 직전 `./gradlew clean test` 439개 성공 상태를 유지하며 문서 diff는 `git diff --check`로 검증한다.
- 2026-09-01 Jira `TMI-118`의 추가 작업 필요 여부를 정리했다. 현재 local profile Atlas에는 CONFIRMED Billing-linked Session이 0개이므로 이 DB를 위한 backfill runner·allowlist·apply 추가 작업은 필요 없다. 다만 이 결과만으로 staging/production rollout이 끝난 것은 아니며, 별도 DB를 사용하면 각 환경 inventory count, Billing TMI-117 consumer 배포·활성, Mongo transaction/index, Lattice IAM, publisher 선활성 후 writer canary와 상태·오류 E2E가 남아 있다. 따라서 추가 애플리케이션 기능 개발은 현재 필수가 아니지만 배포·운영 검증은 필수다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 종료 훅 동기화: Jira `TMI-118` backfill inventory를 `.env.docker.local` local profile의 Atlas 연결에서 읽기 전용으로 실행한 결과 후보 0개, CONFIRMED Billing-linked Session 전체 0개였다. 현재 연결 대상에는 backfill apply가 필요 없으며 DB 문서는 변경하지 않았다. staging/production이 별도 연결이면 writer 활성화 전 각 환경을 별도로 조회해야 한다. URI·credential·userId·Session ID와 Secret/Token은 출력·기록하지 않았고 애플리케이션 코드는 변경하지 않았다.
- 2026-09-01 사용자 승인으로 `.env.docker.local`의 local profile이 가리키는 Atlas DB에 TMI-118 backfill inventory를 읽기 전용 실행했다. Secret·URI·사용자·Session ID는 출력하지 않고 집계만 조회했으며 `CONFIRMED + nonblank attemptGroupId + projection null/missing` 후보는 0개였다. 추가 분해 조회에서도 CONFIRMED Billing-linked Session 전체가 0개여서 현재 이 연결 대상에는 backfill이 필요하지 않다. 이 결과는 local profile 연결에만 해당하며 staging/production이 별도 URI를 사용한다면 각 환경은 writer 활성화 전에 별도로 count 0을 확인해야 한다. DB 변경, backfill apply와 애플리케이션 코드는 수행·변경하지 않았고 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118` rollout 전 기존 backfill 후보 조회 시점을 설명했다. 지금 필요한 작업은 apply가 아니라 환경별 read-only inventory count다. `BILLING_CREATION_SAGA_ENABLED`가 한 번이라도 true였던 staging/prod에서는 writer 활성화 전에 `exam_sessions`의 `entitlementState=CONFIRMED`, nonblank attemptGroupId, projection null/missing 조건을 조회해야 한다. count 0이면 backfill 없이 신규 writer cutover만 진행하고, count가 있으면 writer 활성화 전 dry-run report·분류·canary batch 계획을 확정한다. Billing saga가 해당 환경에서 한 번도 활성화되지 않았다면 후보가 생길 수 없지만 count 0을 확인해 기록하는 것을 권장한다. 조회는 DB 상태를 변경하지 않으며 애플리케이션 코드는 변경하지 않았다. 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118` 기존 Session backfill은 운영자가 Mongo 문서를 하나씩 수동 확인하는 방식이 아님을 설명했다. 먼저 repository가 `CONFIRMED + attemptGroupId 존재 + projection null/missing` 후보 전체를 inventory하고, 후보 ID 집합을 batch dry-run해 결과를 `completed`, `gradingReady`, failureCode와 추가 조사 대상으로 분류한 뒤 승인된 그룹을 한 번에 allowlist apply하는 방식이 권장된다. 데이터가 적으면 개별 확인하고, 많으면 완전한 strict evidence 대상은 batch 승인하며 failure/integrity 후보만 개별 조사한다. 현재 내부 service는 후보 조회·명시적 ID 집합 dry-run/apply까지만 있고 보고서·CLI/admin runner는 없으므로 실제 기존 후보가 존재하면 TMI-118 staging rollout 전에 일회성 운영 runner 또는 command를 추가해야 한다. 기존 후보가 없으면 backfill 자체를 실행하지 않는다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 439개 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118`의 기존 Billing-linked Session allowlist backfill을 사용자에게 설명했다. writer 활성화 전에 만들어져 `attemptGroupProjectionStatus`가 없는 CONFIRMED Session은 reconciler가 자동 처리하지 않는다. 운영자가 명시한 Session ID 집합만 `AttemptGroupBackfillService.dryRun`으로 현재 evidence 기준 GRADING 준비·COMPLETED·failureCode 후보를 읽기 전용 확인하고, 승인 후 `apply`가 Transaction에서 projection을 OPEN/version 0으로 초기화한 뒤 coordinator를 실행해 상태+outbox를 생성한다. 이는 legacy Summary·feedback 불완전성으로 잘못 COMPLETED/RETAKE 처리하거나 대량 event를 한꺼번에 전송하는 위험을 줄인다. 현재 서비스는 공개·admin API나 자동 runner가 없는 내부 실행 경계이므로 실제 staging backfill 실행 방법과 allowlist는 별도 운영 절차로 확정해야 한다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 전체 439개 테스트 성공 상태는 그대로다.
- 2026-09-01 Jira `TMI-118`의 `AttemptGroupOutboxStore`가 Mongo `findAndModify`와 CAS로 수행하는 작업을 사용자에게 설명했다. `findAndModify`는 PENDING 또는 lease 만료 IN_FLIGHT event 하나를 찾는 것과 새 leaseToken·owner·until·attemptCount를 기록하는 것을 단일 원자 연산으로 묶어 여러 ECS Task의 동시 claim을 막는다. 이후 성공, retry, DEAD_LETTER와 BLOCKED_AUTH 갱신은 `_id + status=IN_FLIGHT + leaseToken` 조건의 update로 처리해 현재 처리권을 보유한 worker만 상태를 바꾸도록 한다. 만료 lease는 다른 worker가 회수하며, 잘못된 trace context도 동일 leaseToken CAS를 통과한 worker가 한 번만 fallback context로 교체한다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 439개 전체 테스트 성공 상태는 유지된다.
- 2026-09-01 Jira `TMI-118` publisher의 lease 의미를 사용자에게 설명했다. lease는 여러 Learning Core ECS Task 중 한 Task가 outbox event를 일정 시간 독점 처리하도록 Mongo에 `leaseOwner`, random `leaseToken`, `leaseUntil`을 기록하는 시간제 처리권이다. 정상 완료 시 동일 token 보유자만 DELIVERED/retry/dead-letter/auth 상태를 갱신하고, Task가 중간 종료되면 30초 기본 lease 만료 뒤 다른 Task가 event를 회수한다. 따라서 영구 lock 없이 동시 중복 처리를 줄이고 장애 복구가 가능하며, 아주 드문 HTTP 성공 후 local 갱신 실패에 따른 재전송은 Billing의 eventId/digest 멱등성이 최종 방어한다. 애플리케이션 코드는 변경하지 않았고 공개 계약과 직전 전체 439개 테스트 성공 상태는 그대로다.
- 2026-09-01 Jira `TMI-118` 구현 파일을 역할별로 재검토하고 사용자 설명용 구조를 정리했다. 핵심 흐름은 `ExamServiceImpl` submit/Callback trigger → `AttemptGroupEvidenceEvaluator` evidence 판정 → `AttemptGroupStateCoordinator`의 Session+outbox Transaction → `AttemptGroupOutboxPublisher` lease claim → W3C publish span/traceparent → `SigV4AttemptGroupEventClient`의 Billing 전송이다. 상태·payload·outbox domain, coordinator/reconciler/Summary transaction/backfill application, Mongo lease/auth circuit/index/config/trace/SigV4 infrastructure, RETAKE replacement를 위한 기존 Exam/Billing 수정 파일과 테스트 책임을 대조했다. 애플리케이션 코드는 변경하지 않았고 공개 API·AI·S3·Redis 계약과 이전 `./gradlew clean test` 439개 성공 상태는 그대로다. 이번 설명 작업에서는 문서만 갱신했으며 신규 테스트는 실행하지 않았다.
- 2026-09-01 Jira `TMI-118` Learning Core AttemptGroup durable outbox/publisher를 로컬 구현했다. Billing-linked·CONFIRMED Session만 writer가 관리하며 필수 retry 0 제출 시 GRADING, strict `exam_summaries` evidence 시 COMPLETED, retry 소진·정합성 위반·PT30M deadline 시 고정 failureCode의 RETAKE_AVAILABLE로 전이한다. Session projection+outbox는 Mongo Transaction/optimistic CAS와 Session별 GRADING/TERMINAL unique slot으로 묶었다. Summary Callback의 Summary insert·Job 완료·terminal+outbox도 writer 대상에서 같은 Transaction으로 수렴한다. lease publisher는 같은 eventId/payload를 유지하고 재시도별 새 W3C CLIENT span·traceparent 주입 뒤 SigV4 `vpc-lattice-svcs` 서명, HTTP 분류, DELIVERED 30일·DEAD_LETTER 90일, 401/403 BLOCKED_AUTH 전역 circuit·15분 단일 probe를 구현했다. RETAKE_AVAILABLE Session은 다음 시험 생성 operation에 source Session/group/mockExam을 snapshot해 exact Billing REPLACEMENT만 허용한다. writer/publisher 기본값은 off이며 기존 linked Session은 자동 backfill하지 않고 명시적 allowlist dry-run/apply만 제공한다. 공개 API·BaseResponse·AI·S3·Redis 계약은 변경하지 않았고 `./gradlew clean test` 439개가 성공했다. staging에서는 Billing consumer image/flag, Mongo replica-set·index, Lattice IAM과 INITIAL/REPLACEMENT·401/403·timeout failure-injection E2E가 남아 있으므로 아직 production 활성화 상태가 아니다.
- 2026-08-31 세 앱 서버 현재 `develop`과 최근 Jira·테스트 기록을 기준으로 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`를 최신화했다. Identity `TMI-114` 가입 중단 Firebase cleanup은 PR #36·600 tests·Jira 완료, Billing `TMI-115` BenefitDefinition과 `TMI-117` AttemptGroup consumer는 각각 PR #3·#4에 병합됐고 `TMI-117`은 137 tests·Jira 완료다. Learning Core `TMI-116` Billing Reservation 시험 생성 saga는 PR #24로 병합되고 P1/P2 보완 후 전체 432 tests가 성공했지만 feature flag 기본 off이며 Jira 상태 전환, 실제 Mongo migration·Lattice/IAM/SG·staging E2E가 남아 있다. 무료시험의 가장 큰 코드 공백은 Learning Core AttemptGroup durable outbox/publisher와 Billing owner rebind이고 Challenge backend도 미구현이므로 production release는 계속 차단한다. 애플리케이션·Jira·외부 계약은 이번 점검에서 변경하지 않았다.
- 2026-08-31 앱 문제 응답의 Part 4 표 처리 현황을 분석했다. MongoDB `table_context`는 `Map<String,Object>`로 읽어 시험 생성·문항 prompt·문항 결과 응답의 `tableContext` JSON에 가공 없이 전달하며 서버가 고정 schema, HTML 또는 Markdown으로 렌더링하지 않는다. `table_image_url`은 내부 entity에만 남고 공개 응답에서 제외된다. Part 4 tableContext가 null이면 catalog configuration error이며 빈 object는 허용한다. AI 채점 요청에는 table_context와 table_image_url을 보내지 않는다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 현재 저장소 기준 1차 업데이트 체크리스트 작성·검증 결과를 현재 turn 기록으로 WORKLOG 끝에 추가했다. Identity `TMI-109`·`TMI-111`과 Billing `TMI-110`·`TMI-112`·`TMI-113` 완료, Learning Core Billing saga·Challenge backend·모바일/workload/staging E2E 잔여 판정은 동일하다. 애플리케이션·Jira·외부 계약은 변경하지 않았다.
- 2026-08-28 현재 저장소 기준 1차 업데이트 진행 상태를 `docs/codex/FIRST_UPDATE_PROGRESS_CHECKLIST.md`로 다시 정리했다. Identity `TMI-109`·`TMI-111`과 Billing `TMI-110`·`TMI-112`·`TMI-113`은 구현·병합 기록에 따라 완료로 반영했다. Billing에는 TrialClaim, `FREE_EXAM_ONCE` grant/ledger와 Reservation reserve/confirm/cancel/status/expiry 기반이 있으나 Learning Core Billing client·필수 `Idempotency-Key`·reserve/commit/confirm saga는 아직 없다. Challenge는 프론트·AI v1 계약과 콘텐츠가 준비됐지만 Learning Core backend·AI 양방향 구현은 미착수다. 따라서 실제 모바일 SNS, workload/Lattice, replica set·multi-instance, 무료시험·Challenge staging E2E와 canary가 끝나기 전 production 출시는 차단한다. 신규 Jira와 애플리케이션 코드는 변경하지 않았다.
- 2026-08-28 전체 프론트 API 인계서를 Identity·Learning Core·Billing의 모든 `@RestController`와 Security 설정에 다시 대조했다. Identity 앱 API 17개와 Learning Core 앱 API 11개는 누락 없이 유지된다. Billing은 공개 앱 API가 0개지만 `TMI-110` eligibility consumer, `TMI-112` TrialClaim·FREE_EXAM_ONCE initial reserve, `TMI-113` confirm/cancel/status·expiry lifecycle이 구현된 상태여서 기존 “Reservation 미구현” 설명을 정정했다. Learning Core Billing saga·필수 `Idempotency-Key`, AttemptGroup event·owner rebind·Lattice staging E2E는 여전히 남아 있다. Billing 내부 Reservation endpoint 4개를 프론트 호출 금지 표에 추가하고 Challenge는 `TMI-102`·`TMI-105`·`TMI-106` 관련 승인된 v1 계약·API 미구현 상태로 통일했다. 기존 시험 upload URL의 5분 signature/`expiresIn=60` 불일치와 `.wav` key 대비 Content-Type·codec 미고정 위험을 명시했다. 애플리케이션·Jira는 변경하지 않았다.
- 2026-08-28 월 서버 고정비 300,000원과 사용자가 제공한 AI 실측 합계 275.28원/모의고사로 무제한 이용권 BEP를 재계산했다. 단기권 집중 사용 4·8·14·21·28회, VAT 10%와 IAP 15% 기준 상품 단독 BEP는 24시간 52건, 3일 25건, 7일 17건, 2주 10건, 4주 7건이다. IAP 30% 민감도는 65·31·21·12·9건이며 무료 시험·추천·쿠폰·환불·광고비는 제외한다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 고정비 300,000원·AI 실측 275.28원 기준 BEP 결과와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 서버 비용 최종 관리 기준을 월 300,000원으로 표로 고정했다. Production AWS 전체 비용 242,200원, staging 테스트 24,200원, 환율·청구 지연·Task 중복·로그·전송량 변동 대응 33,600원이며 완료 모의고사당 AI API 250원은 별도 변동비다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 월 300,000원 서버 예산표와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 다른 AWS 비용을 모두 포함한 실제 전체 비용을 `$1.26/day`가 아니라 `$5.49/day`로 재정정했다. 조정 사양의 compute 감소율 6.46%와 고정비 유지 조건에서 production 전체 비용은 `$5.135~5.490/day`, `$154.06~164.70/30일`, 환율 1,400원·VAT 포함 약 237,300~253,600원이다. compute 비중 70% 기준은 약 242,200원이며 staging 10% 24,200원과 변동 완충액 33,600원을 합쳐 월 운영 예산을 300,000원으로 수정했다. 과거 월 7만원 예산은 폐기하며 신규 Jira 키는 없다.
- 2026-08-28 조정 사양의 월 7만원 운영 예산을 표로 분해했다. Production 기준 예상액 약 55,600원, staging 테스트 여유 약 5,600원, 환율·청구 지연·사용량 변동 완충액 약 8,800원으로 합계 70,000원이다. 완충액은 확정 서비스 비용이 아니라 변동 흡수용 예산이며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 `$1.26/day`가 Fargate뿐 아니라 다른 AWS 항목을 모두 포함한 전체 비용이라고 확정했다. 따라서 ALB·NAT·로그 등을 별도 가산한 월 19만~29만원 시나리오는 폐기한다. 조정 사양의 compute 정상 단가 감소율은 6.46%지만 고정 인프라는 줄지 않으므로 전체 비용은 이보다 적게 감소한다. compute 비중 0~100% 경계에서 조정 후 전체 비용은 `$1.179~1.260/day`, `$35.36~37.80/30일`, 환율 1,400원·VAT 포함 약 54,500~58,200원이다. 운영 예산은 청구 지연·환율·staging 테스트 여유를 포함해 월 7만원으로 유지한다. 신규 Jira 키는 없다.
- 2026-08-28 조정 사양 기준 서버비를 관측 `$1.26/day`의 범위에 따라 재계산했다. `$1.26`이 AWS 전체 비용이면 production 약 `$35.36/월`, staging 10% 여유 포함 VAT 기준 약 6만원이며 운영 예산은 월 7만원이다. `$1.26`이 Fargate compute만이면 Mongo 무료·예비비 0·Valkey 최소 가정에서 staging 네트워크 시간제 생성 시 약 19만원, staging ALB·NAT 상시 유지 시 약 29만원이다. Cost Explorer에서 필터 없는 Service별 합계로 어느 시나리오인지 확인해야 하며 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 조정 사양 전체 서버비 재계산 결과와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 조직 계정에서 실제 비용이 `$1.26/day`로 정확히 표시된다고 확인했다. 향후 Identity `1 vCPU/2GB`, Learning Core `1 vCPU/2GB`, Billing `1 vCPU/1GB`, AI `1 vCPU/2GB`로 조정하면 서울 정상 단가 자원비가 현재 구성의 93.54%가 된다. 동일한 조직 정산 효과 유지 가정에서 약 `$1.179/day`, `$35.36/30일`, 환율 1,400원 기준 VAT 포함 약 54,500원이다. 혜택이 사라진 정상 단가 compute는 `$162.07/month`, 환율·VAT 적용 약 249,600원이며 네트워크·로그는 별도다. AWS Organizations 자체는 자동 할인 근거가 아니므로 credit·Savings Plans·private pricing·cost type을 확인해야 한다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 조직 계정 실제 단가 기반 4서비스 축소 비용 추정과 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS 리소스·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 무제한 이용권 BEP의 사용량 가정을 단기권 집중 사용 패턴으로 보정했다. 기준 평균 응시는 24시간 4회, 3일 8회, 7일 14회, 2주 21회, 4주 28회다. VAT 10%와 IAP 15%, 월 고정비 380,000원, 완료 시험당 AI 250원 기준 BEP는 각각 64건·30건·21건·12건·9건이다. 24시간권 3~5회 시 62~67건, 3일권 6~10회 시 29~32건이며 출시 후 실제 cohort의 completed exam 평균·p95로 교체해야 한다. 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: 단기권 집중 사용 BEP 보정 결과와 검증 상태를 WORKLOG 끝에 기록했다. 애플리케이션·AWS·Jira·외부 계약은 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 production 관측 비용을 `$12.6/day`가 아니라 `$1.26`으로 재정정했다. `$1.26×30=$37.80`, 환율 1,400원 기준 VAT 전 약 52,920원이지만, Identity `1 vCPU/3GB`, Learning Core `1 vCPU/3GB`, AI `2 vCPU/4GB` Task가 각 1개씩 24시간 실행되면 Fargate compute만 `$5.696/day`, `$173.26/month`이므로 `$1.26`은 완전한 하루 총비용과 양립하지 않는다. 당일 부분 누적·필터·간헐 실행·credit/net cost 여부를 확인하기 전에는 월 고정비로 사용하지 않는다. 신규 Jira 키는 없다.
- 2026-08-28 사용자가 제공한 토스트 앱 화면에서 무제한 멤버십 가격을 24시간 9,000원, 3일 19,000원, 7일 29,000원, 2주 49,000원, 4주 69,000원으로 확인했다. 월 고정비 380,000원, 모의고사 완료 1회당 AI 변동비 250원, 구매자당 하루 평균 1회 응시, 한국 표시가격 VAT 10%와 IAP 15% 차감 가정에서 상품 단독 판매 월 BEP는 각각 57건·28건·19건·12건·9건이다. IAP 30%이면 70건·34건·23건·14건·11건이다. 사용자가 말한 한 달은 첨부 화면상 4주(28일)로 계산했으며 실제 혼합 판매 BEP는 상품별 판매수×공헌이익 합계가 38만원 이상인 지점이다. 무료시험·추천·쿠폰·환불·광고비는 제외했고 실제 구매자당 시험 수와 Apple/Google 15% 적용 자격을 확인해야 한다. 상세 근거는 `docs/codex/SUBSCRIPTION_BEP_ESTIMATE.md`에 기록했으며 신규 Jira 키는 없다.
- 2026-08-28 사용자가 비용 산정에서 AI Task를 `2 vCPU/4GB`에서 `1 vCPU/2GB`로 낮추고 MongoDB 비용과 기타 예비비를 `$0`으로 정했다. 서울 Fargate 단가 기준 AI production 24시간 비용은 `$41.45/month`, Identity·Learning Core·Billing을 포함한 production Fargate는 `$93.26`, 동일 크기 staging 월 40시간은 `$5.11`이다. staging ALB·NAT도 테스트 때만 IaC로 생성·제거하면 Valkey 두 환경 최소 `$12`를 포함한 전체가 약 `$184.14`, 환율 1,400원과 VAT 10% 가정 약 28.4만원으로 운영 예산은 월 29만~30만원이다. staging ECS만 끄고 ALB·NAT를 유지하면 약 `$249.83`, 약 38.5만원으로 월 39만~40만원이다. 수정안 예상 범위는 월 29만~40만원이다. 현재 실제 AI Task Definition은 `2 vCPU/4GB`이고 API+worker 4개가 함께 있으므로 `1 vCPU/2GB` 적용 전 staging CPU throttling·peak RSS/OOM·queue backlog·p95 부하 검증이 필요하다. 외부 AI provider 호출료는 제외하며 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 동기화: production 24시간·staging 테스트 시 운영 비용 재산정 결과는 동일하다. staging 월 40시간 기준 ALB·NAT까지 필요시에만 IaC로 생성하면 약 `$317.80/month`, 환율 1,400원과 VAT 10% 가정 약 49만원이고, staging ECS만 끈 채 ALB·NAT를 유지하면 약 `$383.49/month`, 약 59만원이다. 현실적인 안전 예산은 월 50만~60만원이며 신규 Jira 키는 없다. 애플리케이션·인프라와 외부 계약은 변경하지 않았다.
- 2026-08-28 사용자가 production만 24시간 운영하고 staging은 테스트할 때만 사용한다고 확정해 월 비용을 재산정했다. staging 월 40시간을 가정하면 네 서비스의 staging Fargate는 약 `$7.38`이고 production Fargate는 `$134.71`이다. staging ALB·NAT까지 테스트 때 IaC로 생성·제거하면 전체 약 `$317.80/month`, 환율 1,400원과 VAT 10% 가정 약 49만원으로 안전 예산은 월 50만~55만원이다. staging Task만 0으로 내리고 ALB·NAT·Atlas Flex·Valkey를 유지하면 약 `$383.49/month`, 약 59만원이므로 현실적인 예산 범위는 월 50만~60만원이다. ALB와 NAT는 ECS Task를 꺼도 삭제하지 않으면 계속 과금되며, staging 네트워크의 자동 생성·삭제는 IaC와 데이터 초기화가 전제다. 상세 계산은 `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`에 반영했고 신규 Jira 키는 없다.
- 2026-08-28 staging+production 월 인프라 비용을 서울 리전 공식 단가로 추정했다. 확인 단가는 Fargate Linux/x86 `vCPU $0.04656/hour`, memory `$0.00511/GB-hour`, ALB `$0.0225/hour + $0.008/LCU-hour`, public IPv4 `$0.005/address-hour`, NAT Gateway `$0.059/hour + $0.059/GB`, Atlas M10 시작 `$56.94/month`, Atlas Flex 최저 `$8/month`, ElastiCache Serverless for Valkey 시작 `$6/month`이다. 확인된 AI Task `2 vCPU/4GB`와 Identity/Learning Core `0.5 vCPU/1GB`, Billing `0.25 vCPU/0.5GB` 가정으로 환경별 Task 한 개를 24시간 운영하면 기준 합계는 약 `$515.82/month`, 환율 1,400원과 VAT 10% 가정 약 79만원이며 안전 예산은 월 80만~90만원이다. staging 시간제 운영·NAT 대체 시 약 59만원, production 2 Task/AZ·Atlas M30의 보수적 HA안은 약 164만원이다. 외부 AI/provider 호출료, IAP 수수료, Atlas backup/egress와 대량 S3/로그는 제외했다. 상세 근거는 `docs/codex/MONTHLY_INFRA_COST_ESTIMATE.md`에 기록했다. 신규 Jira 키는 없다.
- 2026-08-28 사용자가 제공한 실제 Mongo document에 맞춰 10초 챌린지 계획·계약·프론트 인계 문서를 갱신했다. 콘텐츠는 Learning Core가 이미 사용하는 `to-teacher-app` cluster의 `challenge_10s_questions` collection에 `dayNumber`별 정확히 세 문제로 저장된다. `questions[].korean → promptKo`, `referenceAnswer → 제출 또는 만료 terminal 이후 공개`로 매핑하고 `_id`, `dayNumber`, `questionId`, `difficulty`는 프론트 비노출로 고정했다. 프론트 명세는 Draft v0.8로 올리고 실제 day 1 문항 예시를 반영했다. 계획에는 기존 cluster/connection 재사용, dayNumber unique와 questionId 중복 검증, catalog fail-closed, published content append-only, attempt 문제 snapshot을 추가했다. 남은 필수 결정은 `contentBaseDate`와 dayNumber=1 대응 KST 날짜, 콘텐츠 소진 후 순환 여부, difficulty scale이다. 관련 기존 Jira는 TMI-102·TMI-105·TMI-106이며 Learning Core Challenge backend 구현 Jira는 아직 없다. 애플리케이션과 Jira는 변경하지 않았다.
- 2026-08-28 Jira `TMI-109`·`TMI-111`의 UserWithdrawn workload JWT 계약안을 현재 Learning Core와 Identity 코드에 대조 검토했다. RS256, 별도 workload issuer, 전용 audience `learning-core-user-withdrawn`, Identity 기존 RSA/JWKS 재사용, PT2M TTL·PT30S skew, 내부 로컬 발급·요청별 새 token·HTTPS/no-redirect 방향은 타당하다. Learning Core는 현재 RS256, issuer, audience 포함 여부, 설정된 단일 principal claim/value, timestamp, `exp-iat` 최대 수명을 검증한다. 따라서 제안의 `service=identity`만 실제 principal allowlist이고 `sub=identity-service`, `jti`, `kid 필수`는 현재 별도 validator로 강제되지 않음을 계약에 명시해야 한다. 권장안은 principal을 표준 `sub=identity-service` 하나로 통일해 `principal-claim=sub`으로 설정하거나, `service`를 유지한다면 `sub`까지 두 validator로 모두 강제하는 것이다. 미래 `iat`를 막기 위해 `nbf=iat`을 필수 claim으로 추가하거나 별도 future-iat validator가 필요하다. Identity는 기존 RS256 JwtEncoder와 단일 RSAKey JWKS를 제공하지만 workload credential provider 구현체는 아직 없고 JWKS 다중 키 rotation도 미지원이므로 TMI-111과 production activation 전에 구현·E2E가 필요하다. 코드·Jira는 변경하지 않았다.
- 2026-08-28 프론트가 Identity·Learning Core와 1차 업데이트 예정 API를 한곳에서 확인할 수 있도록 `docs/contracts/FRONTEND_API_HANDOFF.md`를 추가했다. 현재 구현된 Identity 17개와 Learning Core 11개 앱 API를 공개/Bearer 인증으로 구분하고 요청·응답·상태·S3 upload/polling 흐름을 정리했다. 무료 모의고사 1회·결제 권한과 10초 챌린지는 구현 API와 섞지 않고 계획/Draft로 표시했으며 AI callback, withdrawal·eligibility workload endpoint와 JWKS는 프론트 호출 금지로 분리했다. TMI-102·TMI-105·TMI-106·TMI-109·TMI-110·TMI-111 관련 현재 경계를 반영했으며 애플리케이션과 Jira는 변경하지 않았다. 현재 Learning Core upload URL의 실제 5분 signature와 응답 `expiresIn=60` 불일치가 프론트 연동 주의점으로 남아 있다.
- 2026-08-28 종료 훅 요구에 따라 10초 챌린지 attempt 제출 유효시간 1시간과 Draft v0.7 갱신 결과를 현재 turn marker로 재동기화했다. attempt deadline은 생성 시각+1시간이고 Presigned URL은 짧게 발급해 deadline 전 같은 key로 재발급한다. 계약 문서만 변경했으며 애플리케이션·Jira는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 사용자 확정에 따라 10초 챌린지 attempt 제출 유효시간을 생성 시점부터 5분에서 1시간으로 변경하고 프론트 명세를 Draft v0.7로 갱신했다. `submissionDeadlineAt=attemptCreatedAt+1시간`이며 23:59:50 KST에 생성한 attempt는 00:59:50까지 원래 challengeDate로 upload-url 발급·S3 업로드·answer 제출이 가능하다. Presigned URL 자체는 예시 기준 5분처럼 짧게 유지하고 attempt deadline 전 같은 object key로 재발급한다. 1시간 만료의 공개 `submitted` projection·history 풀이 수·참고 답안 정책은 기존 만료 규칙을 그대로 유지한다. 프론트 계약, 상태 결정서와 출시 계획을 동기화했고 애플리케이션·Jira는 변경하지 않았다. 신규 Jira 키는 없다.
- 2026-08-28 사용자 요청에 따라 현재 10초 챌린지 프론트 계약 문서 `docs/contracts/ten-second-challenge-frontend-api.md`의 위치와 버전을 확인했다. 문서는 Draft v0.6이며 녹음 시작 attempt 생성과 녹음 후 `POST /api/v1/challenges/attempts/{attemptId}/upload-url` 발급 분리, 자정 rollover 보호를 반영한 구현 전 합의용 명세다. 문서 내용·애플리케이션·Jira는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 요구에 따라 10초 챌린지 Draft v0.6의 attempt·S3 upload-url 분리 계약 확정 기록을 현재 turn marker로 재동기화했다. `POST /today/questions/{questionNumber}/attempt`는 녹음 시작 시 날짜·deadline·내부 object key를 고정하고, 녹음 후 `POST /attempts/{attemptId}/upload-url`이 동일 key의 Presigned URL을 발급·재발급한다. 자정 후에도 기존 attempt의 저장된 날짜와 deadline을 사용한다. 애플리케이션 구현·Jira 변경은 수행하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 사용자의 승인에 따라 10초 챌린지 attempt 시작과 S3 Presigned URL 발급 분리안을 구현 기준 Draft 계약으로 반영했다. 프론트 명세를 Draft v0.6으로 올리고 호출 순서를 `문제 조회 → 녹음 시작 attempt 생성 → 최대 10초 녹음 → POST /api/v1/challenges/attempts/{attemptId}/upload-url → S3 PUT → answer 제출`로 고정했다. attempt 응답에서는 upload 객체를 제거하고 `attemptId`, `challengeDate`, `questionNumber`, `submissionDeadlineAt`만 반환한다. S3 object key는 attempt 생성 시 attemptId 기반으로 내부 고정하며, upload-url은 소유권·상태·deadline을 검증하고 동일 key에 대해서만 재발급한다. 자정 이후에도 기존 attempt의 저장된 challengeDate와 deadline을 사용한다. 결정서와 1차 출시 계획도 같은 내용으로 동기화했다. Challenge API는 아직 구현·배포되지 않아 애플리케이션 코드는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지의 attempt 생성과 S3 Presigned URL 발급을 분리하는 계약 대안을 검토했다. 권장 흐름은 `문제 조회 → 녹음 시작 직전 attempt 생성 → 최대 10초 녹음 → attemptId로 upload-url 발급 → S3 PUT → answer 제출`이다. attempt의 server-side `createdAt`, `challengeDate`, `submissionDeadlineAt`이 자정 경계의 authoritative start가 되므로 별도 임시 session이 필요 없고, 자정 전 생성된 attempt는 deadline까지 자정 이후에도 같은 날짜 문제로 URL 발급·제출할 수 있다. object key는 attemptId 기반으로 attempt 생성 시 결정해 저장하거나 결정적으로 계산하고, upload-url 재발급은 동일 key에 대해 멱등 처리한다. 내부 상태는 CREATED → UPLOAD_READY/UPLOADING → SUBMITTED 또는 EXPIRED로 관리하되 공개 `attemptStatus`는 기존 Draft처럼 제출 전 `not_started`, terminal 후 `submitted` projection을 유지할 수 있다. 다만 attempt 생성 즉시 문제당 1회를 점유하므로 사용자가 녹음을 취소하거나 앱을 종료했을 때 deadline 후 EXPIRED 처리와 참고 답안·풀이 수 정책을 제품적으로 확정해야 한다. 이 대안은 프론트 호출 순서와 draft challenge API를 변경하지만 아직 배포된 API가 아니며, 이번 작업에서는 코드·계약 문서·Jira를 변경하지 않았다. 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지의 backend-only rollover를 임시 recording session 기반으로 구체화했다. 서버가 문제 조회 또는 기존 녹음 직전 요청에서 `ChallengeRecordingSession(userId, challengeDate, questionNumber, startedAt, expiresAt)`을 server clock으로 생성하고, 자정 후 attempt 생성 시 session의 KST `startedAt` 날짜가 요청 `challengeDate`와 같고 session이 유효한 경우에만 이전 날짜 attempt를 허용하는 방식이 더 안전하다. client가 보낸 시작 시각은 조작 가능하므로 근거로 사용하지 않는다. attempt와 session consume은 Mongo 단일 Transaction으로 처리하고 `(userId, challengeDate, questionNumber)` attempt unique를 유지하며 TTL 삭제 지연과 무관하게 `expiresAt`을 직접 비교해야 한다. 제출 deadline은 늦은 attempt 생성 시각이 아니라 `session.startedAt + 허용시간`을 기준으로 해야 자정 후 유효시간이 부당하게 연장되지 않는다. 프론트 변경 없이 문제 GET에서 session을 만들면 실제 녹음 시작이 아니라 문제 조회 시각이라는 한계가 있고, 정확한 녹음 시작이 필요하면 녹음 직전 start 호출이라는 최소 프론트 변경이 필요하다. 코드·계약·Jira는 변경하지 않았으며 신규 Jira 키는 없다.
- 2026-08-28 종료 훅 요구에 따라 10초 챌린지 backend-only 자정 rollover 검토 기록을 현재 turn marker로 재동기화했다. 결론은 동일하다. 프론트 attempt 요청이 기존 `X-Challenge-Date`를 보내면 서버는 자정 후 제한된 creation grace와 server-side question view/recording lease를 사용해 이전 날짜 attempt 생성을 허용할 수 있다. 요청 날짜로 ChallengeDefinition·unique key를 고정하고 creation grace와 제출 deadline을 분리해야 한다. 날짜 식별자가 전혀 없으면 정확한 backend-only 해결은 불가능하다. 코드·공개 계약·Jira는 변경하지 않았고 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지에서 프론트의 `녹음 → attempt 생성 → 업로드/제출` 순서를 유지하는 backend-only rollover 대안을 검토했다. attempt 요청이 기존 계약대로 캐시된 `X-Challenge-Date`를 보내면, 서버는 현재 KST 날짜와 무조건 같아야 한다는 규칙 대신 요청 날짜가 오늘이거나 직전 날짜이고 자정 후 제한된 creation grace 안인 경우를 허용할 수 있다. 늦게 생성한 attempt도 요청 날짜의 ChallengeDefinition과 `(userId, challengeDate, questionNumber)` unique key에 귀속하고 기존 순차 진행·1회 제한을 검증한다. 권장 안전장치는 question 조회 시 사용자·날짜·문항별 짧은 server-side view/recording lease를 남기고, 직전 날짜 attempt는 자정 전에 발급된 lease가 있을 때만 허용하는 방식이다. 그러면 프론트 payload 변경 없이 자정 전 문제를 실제 조회한 사용자만 이전 날짜 attempt를 만들 수 있다. `X-Challenge-Date`나 동등한 기존 날짜 식별자가 전혀 없다면 백엔드는 녹음이 어느 날짜 문제인지 판별할 수 없어 정확한 backend-only 해결은 불가능하다. creation grace와 submission deadline의 정확한 duration은 구현 전 확정해야 하며 이번 작업에서는 분석·기록만 수행했다. 신규 Jira 키는 없다.
- 2026-08-28 10초 챌린지의 자정 경계와 프론트 attempt 생성 순서를 재검토했다. 확정된 Draft 계약의 호출 순서는 `오늘 진행도 → 문제 → attempt 생성/Presigned URL 발급 → 최대 10초 녹음 → S3 PUT → answer 제출`이다. 프론트가 녹음을 먼저 끝내고 제출 직전에 attempt를 생성하면, 녹음 중 KST 날짜가 바뀔 때 이전 `X-Challenge-Date`가 현재 server 날짜와 달라 `409 CHALLENGE_DATE_CHANGED`로 새 attempt 생성이 거절되고 해당 녹음을 기존 날짜 문제에 연결할 수 없다. 따라서 녹음 버튼 처리에서 recorder 시작 전에 attempt를 생성·로컬 보관해야 한다. 자정 전에 생성된 attempt는 생성 당시 challengeDate에 고정되고 `submissionDeadlineAt=attemptCreatedAt+5분`까지 자정 이후에도 제출을 허용하며, answer 처리는 현재 날짜가 아닌 attempt의 저장된 날짜를 사용해야 한다. 이번 작업에서는 계약 분석과 기록만 수행했고 코드·Jira는 변경하지 않았다. 관련 신규 Jira 키는 없다.
- 2026-08-28 TMI-109 PR [#23](https://github.com/Too-Much-I/app-back-end-learning-core/pull/23)이 base `develop`에 merge commit `4baa4f20b7b179290dd743325ef7b251a408da47`로 병합된 것을 원격 fetch와 GitHub 재조회로 확인했다. PR 상태는 `MERGED`, CodeRabbit check는 `SUCCESS`이며 병합 커밋에 withdrawal 운영 코드·테스트·설정·runbook이 포함돼 있고 diff check가 통과한다. 로컬 `develop`도 원격과 같은 커밋으로 fast-forward했다. 구현 시 실행한 전체 402개 테스트는 failures/errors/skipped 0개였다. Jira `TMI-109`를 transition 41로 `완료` 처리하고 재조회에서 status와 resolution이 모두 `완료`임을 확인했다. `TMI-109 blocks TMI-111` 관계는 유지되며 `TMI-111`은 `해야 할 일`이다. 운영 feature flag 활성화 전 replica set·TTL index·workload 인증값과 staging E2E 검증은 계속 필요하다.
- 2026-08-28 Jira `TMI-109`의 production 보완 구현을 완료했다. `consumer-enabled`와 `deny-gate-enabled`를 분리하고 consumer만 켠 위험 조합은 startup에서 차단한다. gate-only rollback에서는 deny marker repository와 TTL 검증을 유지하며 inbox consumer는 내릴 수 있다. 동일 userId의 동시 event 충돌은 250ms/10ms bounded recheck로 다른 `sourceEventId`를 확인하면 409, 동일 source이나 승자를 확정하지 못하면 503으로 수렴한다. staging/prod에는 실제 Mongo Transaction write·rollback·잔존 0건을 검증하는 startup probe를 추가했고 공유 semantic digest golden vector와 식별자 없는 delivery-lag metric, TTL/index 설정 runbook을 보강했다. `./gradlew clean test --no-daemon` 전체 402개 테스트가 failures/errors/skipped 0개로 통과했고 `git diff --check`도 통과했다. 운영 활성화 전 workload 인증값·Access Token/retention/skew 값 승인, replica set과 정확한 TTL index 준비, 실제 rollback·동시성·다중 instance·staging E2E, 후속 Identity `TMI-111` publisher 연동이 필요하다. Jira 상태·댓글과 Git commit·push는 변경하지 않았다.
- 2026-08-27 TMI-109 구현 내용 설명 turn의 종료 훅 기록을 동기화했다. Identity 탈퇴 event 수신부터 validation·digest, inbox/marker Mongo Transaction, duplicate/conflict 수렴, JWT deny gate, 분리 flag, TTL·startup probe·관측·staging E2E와 Learning Core 선배포 순서를 설명했다. Jira `TMI-109 blocks TMI-111` 관계는 유지되며 이번 turn에서는 애플리케이션·Jira를 변경하지 않았다.
- 2026-08-27 사용자에게 TMI-109 구현 범위를 설명했다. 구현은 Identity의 `UserWithdrawn` v1 event를 workload 전용 endpoint에서 검증·멱등 소비해 eventId inbox와 userId deny marker를 단일 Mongo Transaction으로 저장하는 consumer 축과, 정상 사용자 JWT 인증 뒤 active marker를 확인해 old Access Token을 application 진입 전에 차단하는 deny gate 축으로 구성된다. 남은 production 보완은 consumer/gate flag 분리, marker unique race의 204·409·503 수렴, startup Transaction capability probe, 공유 digest golden vector, TTL·관측, replica set·workload auth·multi-instance staging E2E다. 이번 설명에서는 코드·Jira를 변경하지 않았다.
- 2026-08-27 TMI-109 dependency 교정 완료 turn의 종료 훅 기록을 동기화했다. Jira 관계는 최종적으로 `TMI-109 blocks TMI-111`이며 API와 TMI-109 화면에서 재검증됐다. 잘못된 기존 link만 제거하고 올바른 link를 재생성했으며 이슈 본문·상태·댓글과 애플리케이션 구현은 변경하지 않았다.
- 2026-08-27 사용자 확인 후 Jira dependency를 교정했다. 기존 반대 방향 `TMI-111 blocks TMI-109` link 한 건을 해제하고 `TMI-109 blocks TMI-111` link 한 건을 생성했다. TMI-109 API에는 outward issue TMI-111, TMI-111 API에는 inward issue TMI-109가 각각 한 건만 존재하며 TMI-109 화면도 `차단: TMI-111`로 표시됨을 재검증했다. 두 issue의 본문·상태·댓글은 변경하지 않았고 애플리케이션 구현도 시작하지 않았다.
- 2026-08-27 현재 확정된 1차 업데이트 범위인 SNS 로그인·검증 전화번호당 무료 모의고사 1회·10초 챌린지의 진행 상태를 Identity·Billing·Learning Core 코드와 Jira에 대조했다. Identity의 Firebase broker·PhoneIdentity·signup·eligibility publisher·Guest merge와 탈퇴 lifecycle `TMI-90`~`TMI-98`, `TMI-103`, `TMI-104`, `TMI-107`, `TMI-108`은 Jira 완료다. Billing eligibility consumer `TMI-110`도 완료돼 replica-set 테스트 33개 기록이 있지만 TrialClaim·FREE_EXAM_ONCE ledger·Reservation/reconciliation·UserMerged consumer와 실제 Lattice/SigV4 연동은 없다. Learning Core `TMI-109`는 초안 코드와 과거 전체 389개 테스트 기록이 있으나 현재 feature branch의 미커밋·미추적 상태이고 계획에서 추가한 분리 flag·marker race 409·startup Transaction probe는 미구현이며 Jira도 해야 할 일이다. Identity producer `TMI-111`도 해야 할 일이며 Jira dependency는 현재 올바른 `TMI-109 blocks TMI-111`로 교정돼 있다. 기존 시험·채점 기반은 준비됐지만 Billing reserve/confirm saga, UserMerged consumer, AttemptGroup/R3, Challenge domain/API/S3·AI job은 미구현이다. Challenge 관련 Jira는 문제 생성 `TMI-105` 완료, UI `TMI-102`와 채점 agent `TMI-106` 진행 중이나 Learning Core backend Jira는 없다. 따라서 Phase 1 서버 기반은 대부분 완료됐지만 무료시험 vertical slice, Challenge backend, 모바일·workload·staging production E2E와 rollout은 production blocker로 남아 있다. 이번 상태 점검에서는 Jira와 애플리케이션 코드를 변경하지 않았다.
- 2026-08-27 TMI-109 Jira dependency 교정 준비와 구현 계획 설명 turn의 종료 기록을 동기화했다. 현재 실제 관계는 `TMI-111 blocks TMI-109`이며, 목표는 기존 link 한 건을 해제하고 `TMI-109 blocks TMI-111`로 재생성하는 것이다. cloud link 삭제·생성의 실행 직전 확인을 요청한 상태라 Jira는 아직 변경하지 않았고 애플리케이션 구현도 시작하지 않았다. 구현 계획은 운영 계약 확정 → wire/digest golden vector → Mongo Transaction·race → security/gate → 분리 flag·TTL·capability probe·관측 → 전체/staging E2E → Learning Core 선활성화 후 TMI-111 publisher 활성화 순서다.
- 2026-08-27 사용자가 Jira dependency 방향 수정을 요청해 TMI-109 화면을 확인했다. 연결된 업무 항목이 실제로 `다음에 의해 차단됨: TMI-111`로 표시되어, 현재는 `TMI-111 blocks TMI-109`인 것이 확정됐다. 목표는 기존 link만 해제하고 `TMI-109 blocks TMI-111`로 다시 연결하는 것이며 두 issue의 내용·상태는 변경하지 않는다. 브라우저에서 cloud link 삭제와 새 link 생성은 실행 직전 확인이 필요한 외부 변경이라 사용자 확인을 기다리고 있다. 애플리케이션 구현은 시작하지 않았다.
- 2026-08-27 수정된 Jira `TMI-109` 계획서를 Jira `TMI-109`·후속 `TMI-111`, Identity Stage 5 계약과 현재 Learning Core 초안 코드에 다시 대조했다. 이전 검토의 단일 flag rollback 문제는 `consumer-enabled`/`deny-gate-enabled` 분리와 금지 조합·smoke test로, 같은 userId·다른 eventId race 오분류는 inbox 이후 marker/sourceEventId bounded 재조회와 409 acceptance로, replica set startup 검증 누락은 canary Transaction abort·잔존 0건 readiness probe로 해소됐다. 계획서는 구현 진행 가능한 상태다. 다만 실제 Jira link는 문서의 `TMI-109 blocks TMI-111`과 반대로 현재 `TMI-111 blocks TMI-109` 방향이므로 rollout 전 링크 방향을 교정해야 한다. 애플리케이션 코드·계획서·Jira는 이번 재검토에서 변경하지 않았다.
- 2026-08-27 Jira `TMI-109` 계획서를 조건부 승인 검토에 맞춰 갱신했다. 단일 flag를 목표 계약상 `consumer-enabled`와 `deny-gate-enabled`로 분리하고 consumer→gate 의존, consumer-only rollback과 gate 유지 조건을 고정했다. 동시 같은 userId·다른 eventId marker unique loser는 eventId inbox 다음 userId marker를 bounded 재조회해 다른 `sourceEventId`가 확정되면 409, winner 미가시성만 503으로 처리하도록 명시했다. staging/prod consumer startup은 전용 canary를 실제 Mongo Transaction으로 write·abort한 뒤 잔존 0건을 확인하고 실패 시 readiness 전에 중단하도록 구체화했다. 후속 Identity producer/outbox/backfill Jira `TMI-111`을 High 작업으로 생성했고 `TMI-109 blocks TMI-111` 링크를 재조회로 확인했다. 애플리케이션 코드는 변경하지 않았고 Jira 상태 전환·댓글과 Git commit·push는 수행하지 않았다.
- 2026-08-27 Jira `TMI-109` 계획서 외부 검토 4건을 코드와 Jira에 독립 재대조했고 모두 유효하다고 확인했다. 권장 보완은 `consumer-enabled`와 `deny-gate-enabled`를 분리하되 consumer 활성은 gate 활성에 종속시키고, rollback 시 consumer/workload endpoint만 내려도 기존 marker gate와 repository는 유지하는 것이다. 동시 같은 userId·다른 eventId의 marker unique loser는 inbox 확인 뒤 userId marker를 bounded 재조회해 다른 `sourceEventId`가 보이면 409, winner가 아직 보이지 않을 때만 503으로 분류해야 한다. replica set fail-fast는 consumer 활성 startup에서 실제 canary Transaction write 후 abort와 잔존 0건을 확인하는 방식으로 구체화하는 것을 권장한다. Jira read-only 재조회 결과 TMI-109 link는 0건이고 별도 UserWithdrawn producer/outbox/publisher 이슈도 검색되지 않았다. 계획서·애플리케이션·Jira는 변경하지 않았다.
- 2026-08-27 Jira `TMI-109` 계획서를 Jira 본문, Identity Stage 5 기준 문서, 현재 Learning Core 초안 구현에 대조 검토했다. 전체 범위와 wire 계약은 대체로 일치하지만 production 진행 전 보완할 핵심 항목이 있다. 단일 `app.user-withdrawn.enabled`가 consumer endpoint와 deny gate를 함께 제거하므로 계획서의 "endpoint만 비활성화하고 기존 gate 유지" rollback을 실행할 수 없고, 같은 userId·다른 eventId의 동시 insert가 marker unique 충돌을 내면 현재 loser는 계약상 409가 아니라 inbox 재조회 실패 후 503으로 끝날 수 있다. 또한 replica set 미지원 환경의 startup fail-fast를 완료 조건으로 두었지만 이를 구현하는 단계가 불명확하며, Jira의 후속 Identity producer 이슈 blocks 링크도 현재 없다. 공유 digest golden vector, 실제 replica set·multi-instance·workload auth E2E 등 기존 production gate는 계속 유효하다. 애플리케이션 코드와 계획서, Jira 필드·댓글·상태는 변경하지 않았다.
- 2026-08-27 Jira `TMI-109`의 Learning Core 구현 계획을 Identity Stage 5 계획과 현재 초안 코드에 대조해 `docs/codex/TMI-109_USER_WITHDRAWN_CONSUMER_IMPLEMENTATION_PLAN.md`로 작성했다. v1 wire·digest, inbox/marker Transaction, user JWT deny gate, workload chain, TTL·관측·rollback을 확정 범위로 정리했다. 현재 초안에는 핵심 코드와 단위/MVC 테스트가 있지만 Identity 공유 digest golden vector, 실제 replica set rollback·동시성, 다중 instance 가시성, production workload 인증 방식과 TTL 운영값, staging E2E가 남아 있으므로 이를 production 완료 gate로 분리했다. 애플리케이션 코드와 Jira 상태·댓글, Git commit·push는 변경하지 않았다.
- 2026-08-27 Jira `TMI-109`의 Learning Core consumer를 구현했다. 기능은 기본 비활성이며, 활성화하면 workload JWT 전용 `POST /internal/v1/events/withdrawn`이 v1 event를 검증하고 eventId inbox와 userId deny marker를 단일 Mongo Transaction으로 저장한다. 기존 사용자 JWT 검증 뒤 active marker가 있으면 `401 ACCOUNT_WITHDRAWN`, marker 조회 장애면 fail-closed `503 WITHDRAWAL_DENY_GATE_UNAVAILABLE`을 반환한다. marker는 Access Token 최대 수명과 verifier clock skew까지만 유지하고 inbox는 별도 TTL로 보존한다. consumer·보안·설정 테스트와 전체 389개 테스트가 성공했다. 실제 workload profile·TTL 값 승인, replica set Transaction과 staging E2E 전에는 production에서 활성화하지 않는다. Jira 댓글·상태와 Git commit·push는 변경하지 않았다.
- 2026-08-26 사용자가 Billing workload 인증 C3-D를 최종 승인했다. Learning Core의 기존 사용자 inbound Load Balancer와 Identity 사용자 JWT 검증은 유지하고, Billing outbound만 Learning Core ECS task role credential로 VPC Lattice 요청을 SigV4 서명한다. Identity workload token client는 만들지 않으며 reserve/confirm/cancel/status client와 same-key retry를 후속 구현해야 한다. 코드·외부 API·현재 Git/Jira 상태는 변경하지 않았다.
- 2026-08-26 Billing workload 인증을 기존 서비스와 맞추기 위해 인증·outbound 구현을 읽기 전용 대조했다. Learning Core의 실제 인증은 Identity RS256 사용자 JWT를 issuer·JWKS·audience·시간·UUID sub로 로컬 검증하는 방식이다. Python AI outbound는 Authorization 없이 `Idempotency-Key`만 전송하고 Identity workload event consumer는 아직 없으므로 재사용 가능한 server-to-server 인증은 없다. Billing에는 사용자 token을 전달하지 않고 Identity-issued workload 전용 JWT를 발급받아 캐시하는 client가 새로 필요하다. 코드·외부 API·현재 Git/Jira 상태는 변경하지 않았다.
- 2026-08-25 Part 4 PR 준비 상태 확인 turn의 marker를 종료 hook 요구값으로 보완했다. `origin/develop`은 `514fb49`이고 `origin/main`보다 1커밋 앞서며 PR에는 converter와 테스트 2개만 포함된다. 남은 절차는 develop→main PR, CI, review와 merge다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 `text` 수정의 PR 준비 상태를 확인했다. `develop`과 `origin/develop`은 동일 커밋 `514fb49`이며 `origin/main`보다 정확히 1커밋 앞서 있어 commit·push가 완료됐다. main 대비 diff는 `ExamConverter.java`와 회귀 테스트 2개만 포함하며 3 files, 10 insertions, 3 deletions이고 `git diff --check origin/main...develop`이 성공했다. 따라서 남은 Git 작업은 `develop → main` PR 생성·검토·병합뿐이다. working tree의 AGENTS/README/작업 문서 변경은 커밋에 포함되지 않았으며 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 선택적 stage turn의 기록 marker를 종료 hook 요구값으로 보완했다. staged 대상은 converter 1개와 테스트 2개뿐이며 다른 문서는 stage되지 않았다. 직전 전체 352개 테스트와 staged/unstaged diff check는 성공했고 commit·push·main merge는 사용자가 수행해야 한다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 사용자의 요청에 따라 Part 4 결과 상세 `text` 수정 커밋 대상만 stage했다. staged 파일은 `ExamConverter.java`, `ExamOwnershipServiceTest.java`, `ExamReadApiContractTest.java` 세 개이며 staged diff는 10 insertions/3 deletions다. AGENTS, README, WORKLOG/CURRENT_STATE와 다른 미추적 문서는 stage하지 않아 커밋에 섞이지 않는다. 직전 전체 352개 테스트와 staged `git diff --check`가 성공했다. 저장소 규칙에 따라 Codex는 commit·push·main merge를 수행하지 않으며 사용자가 커밋·push 후 develop→main PR을 병합해야 한다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 `text` 수정의 main 반영 전 Git 상태 확인 turn marker를 종료 hook 요구값으로 보완했다. 현재 수정은 `develop` checkout의 미커밋 working tree에 있으며 develop/main/origin-main은 모두 `98730c9`다. 관련 파일만 선택적으로 stage/commit한 뒤 main PR로 병합해야 하고 다른 미커밋 문서를 함께 반영하면 안 된다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 결과 상세 `text` 수정의 Git 상태를 확인했다. 현재 checkout은 `develop`이고 `develop`, 로컬 `main`, `origin/main`은 모두 `98730c9`를 가리키지만 수정 파일은 아직 미커밋 working tree 상태라 어느 브랜치 이력에도 포함되지 않았다. 즉 즉시 main 반영에는 관련 운영 코드 1개와 테스트 2개만 선택적으로 stage/commit한 뒤 PR로 main에 merge해야 한다. 작업 트리에 AGENTS/README와 다수 문서 변경이 함께 있으므로 `git add .` 또는 전체 commit은 피해야 한다. Codex는 규칙상 commit·push를 수행하지 않는다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 세 문항 제공 경로 대조 turn의 작업 기록 marker를 종료 hook 요구값으로 보완했다. 결론은 동일하다. 시험 생성과 prompt는 기존부터 Part 4 `text`를 제공했고 결과 상세의 Part 4 전용 converter만 누락돼 운영 코드 한 곳 수정이 충분하며, 세 경로의 계약 테스트와 전체 352개 테스트가 성공했다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 문항 원문을 제공하는 세 경로를 대조했다. `POST /api/v1/exams`는 `result.questions[].text`, `GET /{examId}/questions/{questionNumber}/prompt`는 `result.text`, 결과 상세 `GET /{examId}/questions?questionNumber=&retryCount=`는 `result.question.questionInfo.text`를 사용한다. 시험 생성과 prompt는 이미 공통 `ExamConverter.toQuestionDTO()`가 Part 4 원본 `Question.question`을 `text`로 매핑하고 있었고, 결과 상세만 Part 4 전용 `toQuestionInfoDTO()`가 누락해 이번 운영 코드 한 곳 수정이 충분하다. 생성·prompt·결과 상세의 Part 4 text 계약 테스트를 모두 명시적으로 확인·보강했다. 핵심 테스트와 전체 352개 테스트가 모두 성공했으며 실패·오류·건너뜀은 0개다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Part 4 문항 상세 결과의 `result.question.questionInfo`가 표 `tableContext`만 제공하고 질문 문장을 누락하던 원인을 수정했다. `ExamConverter.toQuestionInfoDTO`의 Part 4 전용 최소 변환이 원본 `Question.question`을 제외하고 있었으며, 기존 공통 응답 필드 `text`에 이를 매핑했다. 앱 필드 경로는 `result.question.questionInfo.text`다. Part 4의 `tableContext`와 기존 필드, URL·Method·query parameter·`BaseResponse`는 유지하고 `tableImageUrl` 등 기존 비노출 필드는 추가하지 않았다. 핵심 테스트와 `./gradlew clean test --no-daemon`이 성공했으며 전체 352개 테스트의 실패·오류·건너뜀은 0개다. 별도 Jira 키는 제공되지 않았다.
- 2026-08-25 Challenge 녹음·업로드 canonical 형식은 `.m4a` 확장자의 M4A 컨테이너와 AAC 코덱으로 확정했다. S3 PUT과 object metadata의 `Content-Type`은 `audio/mp4`, server-generated S3 key 확장자는 `.m4a`를 사용한다. sample rate·channel·최대 파일 크기와 AI 서버의 직접 처리 또는 내부 변환 방식은 아직 미확정이며 Jira 키는 없다.
- 2026-08-25 Challenge AI 자동 재시도 소진 시 결과 조회 응답을 확정했다. 조회 요청 자체는 성공이므로 HTTP 200과 기존 `BaseResponse(isSuccess=true, code=COMMON_200)`를 유지하고, 문제에는 `attemptStatus=submitted`, `gradingStatus=failed`, `gradedAt=null`, `aiResult=null`을 반환한다. prompt·submittedAt·referenceAnswer는 유지하며 내부 예외명·AI 원문·재시도 횟수·failureReason은 공개하지 않는다. 프론트는 polling을 중단하고 피드백 생성 실패 안내를 표시한다. Jira 키는 없다.
- 2026-08-25 Challenge의 `attemptStatus=submitted`는 사용자 audio 접수 완료를 뜻하며 AI 채점 완료를 뜻하지 않는다. submitted 문제는 Callback 전이나 최종 AI 실패 후에도 결과 API에서 항상 HTTP 200으로 조회 가능해야 한다. Callback 전에는 참고 답안·제출 정보와 `gradingStatus=pending|processing`, `aiResult=null`을 반환하고, 최종 실패 시에도 `gradingStatus=failed`와 참고 답안을 유지한다. 프론트 polling 중단·앱 종료는 서버 Job을 취소하지 않으며 재진입 시 재조회한다. submitted 문제 조회가 404라면 정상 대기 상태가 아니라 서버 정합성 오류다. 프론트 polling 상한과 서버 timeout·최대 retry·최종 failed 전환 시간은 미확정이며 Jira 키는 없다.
- 2026-08-25 혼동을 일으킨 “`timed_out` 제거 후 결과 내용을 `feedbackType`과 `gradingStatus`로 표현” 문구를 바로잡았다. 공개 `attemptStatus`는 화면 이동 기준인 `not_started|submitted`만 사용하고, 10초 녹음 종료는 정상 제출이다. 별도의 공개 `feedbackType` enum은 두지 않으며 AI 준비 상태는 `gradingStatus`, 실제 결과는 nullable `aiResult`·`transcript`와 안내 문구로 표현한다. Jira 키는 없다.
- 2026-08-25 자정 직전 프론트 캐시 경합을 방지하기 위해 오늘 진행도 응답에 server 기준 `challengeDateExpiresAt`과 `expiresInSeconds`를 주는 안을 권장했다. 앱은 server TTL로 timer를 시작하고 만료·foreground 복귀 시 재조회하며, question·attempt 요청에 `X-Challenge-Date`를 보내 server가 현재 KST 날짜와 최종 비교한다. 불일치하면 mutation 없이 `409 CHALLENGE_DATE_CHANGED`와 최신 날짜 정보를 반환한다. client timer만으로는 요청 중 rollover 경합을 막을 수 없어 server 검증이 필수다. 이 계약은 사용자 최종 승인 전이며 Jira 키는 없다.
- 2026-08-25 Challenge history는 cursor pagination을 제거하고 `yearMonth=YYYY-MM` 월별 조회로 변경했다. 응답은 KST 날짜마다 `participated`와 공개 `attemptStatus=submitted` 문제 수 `solvedQuestionCount`만 제공한다. 정상 audio 제출·무음·5분 만료 terminal은 프론트에서 구분하지 않고 풀이 수에 포함하며 아직 terminal이 아닌 공개 `not_started`만 제외한다. 특정 날짜 결과는 `GET /api/v1/challenges/{challengeDate}/results?questionNumber={optional}`로 통합하고 번호가 없으면 풀이 수만, 번호가 있으면 날짜 전체 풀이 수와 해당 문제 단일 상세만 반환한다. Jira 키는 없다.
- 2026-08-25 프론트 공개 문제 상태는 화면 이동에 필요한 `attemptStatus=not_started|submitted` 두 값만 두는 것으로 단순화했다. 서버 내부의 CREATED/UPLOADING은 공개 `not_started`, 정상 제출·무음·5분 만료 terminal은 공개 `submitted`로 projection한다. 내부 상태는 Presigned URL 재발급·멱등 submit·deadline 처리에 필요하므로 제거하지 않는다. history에서도 timeout/expired count를 별도 노출하지 않는다. Jira 키는 없다.
- 2026-08-25 history의 cursor는 페이지 경계를 위한 토큰이지만 최신 결정에서는 월별 최대 31건만 반환하므로 제거했다. Jira 키는 없다.
- 2026-08-25 `10초 종료=timed_out=피드백 없음`은 사용자가 실제 발화했는데도 답안·피드백을 잃는 UX이므로 채택하지 않는다. 앱은 10초에 녹음을 자동 종료하고 녹음된 audio를 정상 `submitted`로 올리며, 서버는 제출 접수 즉시 참고 영어 문장을 반환하고 다음 문제를 연다. AI feedback은 비동기로 갱신하고 무음과 5분 미제출도 별도의 공개 결과 타입 없이 `gradingStatus`, nullable AI 결과와 안내 문구로 표현한다. AI 실패에도 참고 답안과 제출 상태는 유지한다. Jira 키는 없다.
- 2026-08-24 자정 직전 ChallengeAttempt는 생성 당시 challengeDate에 귀속하고 `submissionDeadlineAt=attemptCreatedAt+5분`까지 제출을 허용하는 것으로 확정됐다. 23:59:50 생성은 00:04:50까지 이전 날짜 제출로 처리하며, 자정 이후 이전 날짜의 새 attempt 생성은 금지하고 deadline 이후 제출은 `CHALLENGE_ATTEMPT_EXPIRED`로 거절한다. 이 5분은 10초 녹음 검증이 아니라 S3·네트워크 복구 시간이다. Jira 키는 없다.
- 2026-08-24 10초 챌린지는 세 문제를 모두 푸는 것이 필수가 아니며 월별 history에서 실제 audio 제출이 한 문제 이상인 날짜를 참여로 표시하고 풀이 문제 수를 함께 노출한다. Jira 키는 없다.
- 2026-08-24 10초 챌린지는 녹음 길이를 최대 10초로 제한하고, 1→2→3 순차 진행하며, 같은 KST 날짜에 모든 사용자가 동일한 3문제를 푸는 것으로 확정됐다. 세 문제 완료 여부와 무관하게 일부 참여 날짜도 history에 포함한다. Jira 키는 없다.
- 2026-08-25 프론트 전달용 10초 챌린지 API를 Draft v0.5로 갱신했다. 공개 `attemptStatus`는 `not_started|submitted`만 제공하고, 월별 참여·풀이 수, 날짜 count/detail 분리와 server 기준 날짜 rollover 보호를 반영했다. timeout UX와 M4A/AAC·`audio/mp4` 형식은 확정됐고 API는 아직 구현·배포되지 않았다. rollover 보호 최종 승인, sample rate·channel·최대 파일 크기와 AI 결과 필드는 미확정이다. 기존 시험 API·AI·S3 계약은 변경하지 않았고 Jira 키는 없다.
- 2026-08-24 10초 챌린지 콘텐츠가 한국어 문장을 보고 영어 문장을 만들어 최대 10초 길이로 직접 발음한 audio를 S3에 올리는 방식으로 확정됐다. 문제 DTO는 한국어 prompt를 제공하고 참고 영어 문장은 결과 전까지 숨긴다. AI는 S3 audio를 인식해 transcript와 의미·문법·발음의 간단한 feedback을 생성하는 challenge 전용 비동기 계약을 사용한다. server는 audio duration을 검증하지 않고 attempt·presigned upload·object 검증·terminal·AI Job을 관리한다. audio canonical format과 최종 feedback 필드는 미확정이며 Jira 키는 없다.
- 2026-08-24 사용자가 녹음 시간은 프론트가 측정하고 영어 발화 녹음 audio를 S3에 직접 업로드한다고 확인했다. 최신 UX 권장안에서는 server가 문제당 단일 attempt, presigned upload URL, exact server-generated object key, submitted/expired terminal과 AI Job을 관리한다. 녹음 길이는 최대 10초이며 `.m4a` M4A/AAC와 `audio/mp4`는 확정됐고 sample rate·channel·최대 크기는 추가 확정이 필요하다. Jira 키는 없다.
- 2026-08-24 프론트가 요청한 10초 챌린지 API를 검토했다. 오늘 진행도, 개별 문제, 답안 제출, 날짜별 이력, 특정 날짜·문제 결과는 필요하며 하루 3문제·문제당 최대 1회·한국어 prompt 기반 영어 발화 audio·AI 간단 피드백 요구를 반영했다. 녹음 길이는 client가 최대 10초로 제한하고 server는 audio duration을 검증하지 않으며, server는 attempt·S3 upload·terminal 상태를 관리한다. 일일 상태와 문제 attempt 상태, AI grading 상태를 분리하고 answer submit은 UUID idempotency, AI 피드백은 challenge 전용 비동기 Job/Callback으로 처리한다. 순차 진행·공통 문제·M4A/AAC와 `audio/mp4`는 확정됐고 sample rate·channel·최대 크기와 feedback 필드는 미확정이며 상세 내용은 `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`에 기록했다. Jira 키는 없다.
- 2026-08-24 결제·credit·pass 구현을 후속으로 미루고 1차 우선 범위를 SNS 로그인, 검증된 전화번호당 무료 모의고사 1회, 10초 챌린지로 변경하는 계획을 정리했다. Identity에는 Firebase exchange/signup·Guest flow, SocialIdentity·PhoneIdentity·eligibility publisher 기반이 이미 있으나 production flag는 꺼져 있고 탈퇴 lifecycle 1~3, 실제 모바일 Google/Apple/Phone과 staging E2E가 남아 있다. 무료 1회는 결제 없이도 TrialClaim unique와 reserve/confirm 원장이 필요하며 기존 결정대로 최소 Billing/Entitlement가 소유한다. 10초 챌린지는 Learning Core 신규 domain이며 MEMBER·KST 일 3문제·한국어 prompt 기반 영어 발화 audio·문제당 별도 attempt·경제적 reward 없음의 MVP를 권장한다. 상세 계획은 `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`에 기록했고 Jira 키는 없다.
- 2026-08-24 Learning Core와 Identity의 Sentry 수집·메일 알림 경계를 정적 확인했다. Learning Core는 DSN이 주입된 환경에서 ControllerAdvice의 예상 밖 500과 servlet/filter까지 빠져나간 unhandled Runtime/Servlet 예외만 명시적으로 수집하고, validation·JSON parse·인증·비즈니스 4xx와 단순 ERROR 로그·grading/AI/Callback 운영 실패 로그는 자동 Sentry event로 보내지 않는다. Identity는 `SENTRY_ENABLED=true`와 DSN이 함께 주입돼야 하며 GlobalExceptionHandler의 예상 밖 500만 명시 capture하고 expected 4xx와 ERROR 로그는 제외한다. 두 서비스 모두 tracing/log integration은 꺼져 있다. Sentry event 수신과 이메일 발송은 별개이며 저장소에는 실제 Alert Rule·수신자·임계값이 없으므로 현재 이메일 조건은 Sentry 프로젝트의 Alerts/Notifications에서 확인해야 한다. Jira 키는 없다.
- 2026-08-24 Billing 계약의 단일 기준과 이후 작업기록 위치를 `/Users/msde76/billing/docs/codex`로 이전했다. Billing의 `CONTRACT_DECISIONS.md`에 기존 확정사항과 C1~C13 선택지·장단점·권장 승인 순서를 기록했으며, 1차 구현 전 C1~C8 승인이 필요하다. Learning Core 종료 훅 호환성을 위해 이 상태 요약만 남기며 앞으로 Billing 계약 본문은 Learning Core 문서에 추가하지 않는다. Learning Core 애플리케이션·외부 API·DTO·AI·Redis·S3 계약은 변경하지 않았고 Jira 키는 없다.
- 2026-08-24 신규 `/Users/msde76/billing` 프로젝트의 기본 설정을 완료했다. 로컬 Git 저장소를 `develop` 브랜치로 초기화하고 빈 GitHub 저장소 `Too-Much-I/app-back-end-billing`을 `origin`으로 연결했다. Spring Boot 3.4.2·Java 21과 Web, Validation, MongoDB, Security Resource Server, Actuator, Lombok, Testcontainers 기반으로 맞췄고 애플리케이션 이름 `app-back-end-billing`, 기본 포트 8082, 환경변수 Mongo 설정을 적용했다. 인증 계약 구현 전에는 health 외 요청을 fail-closed로 차단한다. Billing에 `AGENTS.md`, `.codex/hooks`, `docs/codex/CURRENT_STATE.md`, `WORKLOG.md`를 추가했으며 `./gradlew clean test`가 성공했다. 결제 API·도메인·스토어 검증과 Learning Core workload 연동은 구현하지 않았고 Jira 키는 없다. Learning Core 애플리케이션·외부 계약은 변경하지 않았다.
- 2026-08-24 `/Users/msde76/billing`에 생성된 Billing skeleton을 사용자 요청대로 읽기 전용 점검했다. Java 21, Gradle Groovy, group/package `web.tosunsaeng`/`web.tosunsaeng.billing`, 기본 Application·context test와 wrapper는 정상 생성됐지만 Spring Boot는 기존 두 서비스의 3.4.2가 아닌 4.1.1, Gradle wrapper는 9.5.1이다. `build.gradle`에는 `spring-boot-starter`와 test starter만 있어 Web, Validation, MongoDB, Security, OAuth2 Resource Server, Actuator, Lombok 및 transaction 통합 테스트 의존성이 없다. root project와 application name은 `billing`이고 아직 Git repository가 아니다. Billing 파일은 수정하지 않았고 빌드 산출물 생성을 피하기 위해 Gradle 테스트도 실행하지 않았다. Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-24 신규 앱 Billing/Entitlement 서버의 Spring Initializr 권장 구성을 확정했다. 기존 Identity·Learning Core와 맞춰 Gradle Groovy, Java 21, Spring Boot 3.4.2 계열, group `web.tosunsaeng`, artifact/name `app-back-end-billing`, package `web.tosunsaeng.billing`, Jar를 사용한다. 초기 의존성은 Spring Web, Validation, Spring Data MongoDB, Spring Security, OAuth2 Resource Server, Actuator, Lombok이며 Testcontainers는 Mongo transaction/unique concurrency 검증을 위해 수동 추가한다. Redis, JPA/SQL, Kafka/SQS, AWS SDK, OAuth2 Client, Apple/Google adapter와 Sentry/OpenAPI는 실제 계약·운영 필요에 따라 후속 추가하고, 결제·entitlement·reservation 원장은 MongoDB를 단일 진실 공급원으로 둔다. Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-21 종료 훅 요구에 따라 Summary generation wire field 확인 작업의 WORKLOG marker를 보완했다. 외부 JSON은 root-level `generation_attempt`, Java 내부는 `generationAttempt`라는 결론과 관련 기존 Jira `TMI-25` 상태는 동일하다.
- 2026-08-21 Summary generation 외부 wire 이름은 camelCase가 아니라 root-level snake_case `generation_attempt`로 확정돼 있다. Java DTO 내부만 `generationAttempt`이며 `@JsonProperty("generation_attempt")`로 역직렬화한다. AI 요청과 Callback 모두 숫자 값을 최상위 `generation_attempt`로 보내야 하고 `generationAttempt`나 nested metadata는 계약이 아니다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 2026-08-21 사용자가 AI 문항 Callback은 동일한 `FEEDBACK_CALLBACK_URL`로 현재 앱 Backend에 정상 저장된다고 확인했다. 따라서 Callback 목적지 문제는 우선순위에서 제외하고 Summary JSON 계약 차이를 확인한다. 문항과 Summary는 같은 `/api/v1/exams/callback/feedback` endpoint를 사용하지만 Summary는 root-level snake_case `generation_attempt`가 현재 Job generation과 같아야 하고, `suggested_total_score`로 Summary로 분류되며, non-empty `part_feedback`가 필요하다. 현재 두 Job에 completion claim과 failureReason이 모두 없으므로 가장 유력한 원인은 실제 worker Callback에서 `generation_attempt`가 누락·다른 이름·중첩·불일치한 경우다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 2026-08-21 종료 훅 요구에 따라 두 Summary Job `PROCESSING/dispatchAttempt=2` 추가 진단의 WORKLOG turn marker를 보완했다. 진단 결론과 관련 기존 Jira `TMI-25`, 신규 Jira 없음 상태는 변경되지 않았다.
- 2026-08-21 두 Summary Job(`ex_e855ed97a6_0821_0429`, `ex_0ff1b425ab_0821_0412`) 모두 generation 1·`PROCESSING`·`dispatchAttempt=2`이고 `completionClaimedGeneration`, `failureReason`, `completedAt`이 없다. 유효한 current-generation Callback이 현재 앱 Backend의 저장 경로에 진입했다면 completion claim이 먼저 기록되므로, 두 시험의 동일 패턴은 우연한 경합이나 empty `part_feedback`보다 AI Callback URL이 다른 Backend를 가리키거나 payload의 `generation_attempt`가 누락/불일치하는 시스템 문제를 우선 지시한다. 첫 시험은 04:34:22 Callback 200 이후에도 완료되지 않아 04:41:08에 재전송됐다. 다음 확인은 `exam_sessions` abandoned 여부, `exam_summaries` 부재, AI worker의 실제 Callback target host와 안전한 metadata `user_id/generation_attempt/part_feedback key count`, ECS worker image digest가 echo 수정 commit을 포함하는지다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다.
- 2026-08-21 `ex_e855ed97a6_0821_0429` Summary 요청은 04:34:14.642 UTC에 generation 1로 AI에 전송됐고 AI가 04:34:22.293 UTC에 Backend Callback HTTP 200을 기록했으므로 네트워크 미도착보다 Backend 수신 후 no-op/실패 분기가 유력하다. Callback controller는 stale/missing generation, duplicate, abandoned Session, completion claim 상실과 empty `part_feedback` 처리 뒤에도 200을 반환한다. 정상 저장이면 같은 examId의 `요약 채점 콜백 저장 완료` INFO와 Summary Job 완료 INFO가 있어야 한다. 오늘 10:22 KST에 web-ai generation echo 수정이 이미 배포됐으므로 누락을 단정하지 않고 `summary_grading_jobs` generation/status/failureReason, `exam_summaries` 존재, `exam_sessions` 상태와 Backend INFO/DEBUG/WARN을 함께 확인해야 한다. 관련 기존 Jira는 `TMI-25`이며 신규 Jira 키는 없다. 제공된 AWS Task 링크는 브라우저 세션이 로그아웃 상태라 직접 조회하지 못했다.
- 2026-08-21 `GET /`의 `COMMON401`·`InsufficientAuthenticationException` 로그를 진단했다. JWT 모드에서 공개 경로는 Callback, Swagger, `/actuator/health`이고 루트 `/`는 `authenticated()` 대상이므로 Bearer 인증 없이 접근하면 Security가 Controller/404 처리 전에 정상적으로 401을 반환한다. 단발이면 브라우저·외부 probe일 가능성이 높고 30~60초 주기 반복이면 ALB Target Group health check path가 `/`로 설정됐는지 확인해 `/actuator/health`로 수정해야 한다. 배포 workflow의 검증 URL은 이미 `/actuator/health`다. `/`를 단순히 공개하는 코드는 추가하지 않았으며 관련 신규 Jira 키는 없다.
- 2026-08-21 사용자가 현재 저장소는 앱 전용이며 웹과 앱 서버가 분리되어 있으므로 앞으로 웹 저장소·웹 동작을 함께 고려하지 말고 앱만 대상으로 하라고 확정했다. 저장소 검색 결과 실제 웹 백엔드·웹 프론트 코드는 없고, 웹 관련 항목은 복제 출처를 설명하는 문서, 과거 작업 기록, 레거시 Java package namespace `web.tosunsaeng`, 별도 공유 Python AI 저장소명 `web-ai`뿐이다. `AGENTS.md`와 `README.md`를 앱 전용 범위로 명확히 했으며, 웹 호환성을 신규 설계 제약에서 제외하되 현재 앱 공개 API와 공유 Python AI `user_id=examId`·Callback 계약 보호는 유지한다. 관련 기존 Jira는 `TMI-14`, `TMI-25`, `TMI-31`, Identity `TMI-90`, `TMI-95`, `TMI-98`이며 신규 Jira 키는 없다.
- 2026-08-21 reservation 5분 TTL은 시험 시간 제한이나 confirmed consumption 만료가 아니라 `RESERVED` 상태에만 적용된다고 명확히 했다. 정상 시작은 Session durable commit 직후, client에 시험을 노출하기 전에 `RESERVED → CONFIRMED/CONSUMED`로 전이하므로 시험 중 5분이 지나도 credits가 `AVAILABLE`로 돌아가지 않는다. confirm되지 않은 Session은 사용할 수 있게 반환하지 않으며, TTL 만료는 Session commit 전에 프로세스가 중단되는 orphan hold 복구에만 쓰인다. 관련 기존 Jira는 Identity `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-21 시험 시작의 `5분 reserve → Session 저장 → confirm` 의미를 명확히 했다. 5분은 사용자 대기 시간이 아니라 Billing reservation TTL이며 정상 요청은 세 단계를 수초 안에 처리한다. reserve는 10 credits/free entitlement/pass 사용 권리를 동시 요청이 재사용하지 못하게 임시 hold할 뿐 영구 소비하지 않고, Learning Core가 `ExamSession`과 operation/reservation 관계를 MongoDB에 durable commit한 뒤 confirm에서 최초 AttemptGroup 소비를 확정한다. Session 저장 실패는 cancel/TTL 만료로 hold를 반환하고, confirm 응답 유실은 같은 operation의 상태 조회·재시도로 수렴해 새 Session·이중 차감을 만들지 않는다. 관련 기존 Jira는 Identity `TMI-95`, `TMI-96`, `TMI-98`이며 Billing/Learning Core 후속 Jira 키는 아직 없다.
- 2026-08-21 `web-ai` deploy role의 최종 IAM 정책 전체본을 확정했다. 기존 ECR authorization/repository push, exact ECS Service describe/update, Task Definition register/tag와 execution role PassRole 제한을 유지하고, AWS authorization 특성에 따라 `ecs:DescribeTaskDefinition`, `ecs:ListTasks`, `ecs:DescribeTasks`는 resource `*`의 read-only statement로 허용한다. 이를 통해 현재 Task Definition 다운로드와 배포 후 running Task image/digest 검증의 두 AccessDenied를 해결한다. 정책 저장 후 GitHub Actions failed jobs를 rerun하며 Role과 `AWS_ROLE_ARN`은 변경하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 재실행한 `web-ai` workflow가 새 Task Definition `tosunsaeng-ai:6`과 새 ECR digest까지 생성한 뒤 배포 후 Task 검증 단계의 `ecs:ListTasks` AccessDenied로 실패했다. 배포·register 단계는 통과했고 실패는 running Task image/digest 확인을 위한 read-only 검증이다. deploy role 정책에 `ecs:ListTasks`와 후속 검증에서 필요한 `ecs:DescribeTasks`를 resource `*`로 추가한다. 기존 ECR push, exact Service update, execution role PassRole 제한은 유지한다. 정책 저장 후 failed jobs만 rerun하며 이미 같은 digest/revision이 배포된 상태일 수 있으므로 ECS Service events와 revision 6 상태를 함께 확인한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 Phase 0 계약 확정을 위한 선택 영역을 정리했다. 이미 확정된 것은 Billing/Entitlement 단일 서비스, Apple/Google 결제, 검증 phone당 무료 1회, 10 credits/시험, 5분 reserve→Session commit→confirm, 이어풀기 제외와 R3 무료 replacement다. 추가 결정이 필요한 핵심은 (1) 공개 `Idempotency-Key` 의무 수준과 replay 응답, (2) entitlement 자동 선택 여부, (3) confirm 불명 시 외부 응답과 Session 노출, (4) Billing 오류 mapping, (5) AttemptGroup·consumption의 서비스별 소유권과 완료 증거, (6) replacement 동시성·authorization, (7) PhoneEligibility/UserMerged 전달 방식, (8) workload 인증, (9) store 거래 검증·notification 멱등 원천, (10) reconciliation과 rollout gate다. 권장 기본 조합은 기존 공개 body/response를 유지하며 신규 앱만 UUID v4 key 필수, 완료 replay는 동일 200·처리 중 409, 서버 자동 entitlement 선택, confirm 전 Session 비노출·503, 안정적 오류 mapping, Learning Core가 AttemptGroup 학습 상태를 소유하고 Billing이 consumption을 소유, consumer별 direct HTTPS delivery와 workload JWT, server-side store 검증+notification inbox, 양쪽 operation 조회 기반 reconciliation이다. 관련 기존 Jira는 `TMI-90`, `TMI-95`, `TMI-98`, `TMI-14`, `TMI-25`, `TMI-31`이며 후속 Jira 키는 아직 없다.
- 2026-08-21 전체 앱 계획의 최우선 다음 단계는 코드 구현보다 Phase 0 계약·Jira 동결이다. 가장 먼저 Learning Core→Billing의 `reserve/confirm/cancel/status/reconcile`, 5분 reservation, Session commit 불명 복구, `Idempotency-Key`, 공개 오류 mapping과 R3 `AttemptGroup` 상태 계약을 확정하고, Identity `PhoneEligibilityBinding`·`UserMerged` multi-consumer fan-out, Billing 서비스, Learning Core Billing 연동, Client IAP, staging E2E를 별도 Jira로 분리해야 한다. 이후 feature flag OFF로 Identity fan-out, Billing 원장/TrialClaim/Reservation, Learning Core contract client·Session metadata를 병렬 구현한다. 관련 기존 Jira는 Identity `TMI-90`, `TMI-95`, `TMI-98`, Learning Core 기반 `TMI-14`, `TMI-25`, `TMI-31`이며 Billing/Learning Core 후속 Jira 키는 아직 제공되지 않았다.
- 2026-08-21 사용자가 현재 `tosunsaeng-web-ai-github-deploy-policy` 전체 JSON을 제공하고 AccessDenied 수정본 전체를 요청했다. `ReadAiTaskDefinitions` statement의 `ecs:DescribeTaskDefinition` resource만 Task Definition ARN에서 `*`로 변경한 완전한 정책을 제공하며, ECR repository `tosunsaeng-ai`, exact Service `tosunsaeng-staging-cluster/tosunsaeng-ai-service`, Task Definition tag ARN과 기존 execution role PassRole 제한은 유지한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 첫 `web-ai` ECS GitHub Actions run에서 OIDC assume은 성공했지만 현재 Task Definition 다운로드 중 `ecs:DescribeTaskDefinition` AccessDenied가 발생했다. 오류가 action resource를 `*`로 평가하므로 앞서 ARN으로 제한해 안내한 `DescribeTaskDefinition` statement가 매칭되지 않은 것이 원인이다. 기존 `tosunsaeng-web-ai-github-deploy-role`이나 GitHub variable은 다시 만들지 않고 연결 정책에서 `ecs:DescribeTaskDefinition`의 `Resource`만 `*`로 수정한다. ECR repository, exact ECS Service update와 execution role PassRole 범위는 그대로 유지한다. 정책 저장 후 실패한 workflow를 rerun하면 되며 후속 denied action이 있으면 해당 API의 AWS authorization model에 맞춰 최소 범위로 보완한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·workflow는 변경하지 않았다.
- 2026-08-21 사용자가 `web-ai`에 deploy 코드가 없다고 확인했다. 별도 `deploy.sh`나 애플리케이션 배포 모듈은 필요 없지만 GitHub Actions 자동 배포를 위해 `.github/workflows/deploy-ecs.yml` 같은 workflow 파일은 반드시 새로 생성해야 한다. 이 YAML 자체가 checkout/test, OIDC assume, ECR build/push, 현재 Task Definition 조회, 다섯 container image render, `tosunsaeng-ai-service` deploy/stability 검증을 수행하는 배포 코드다. Dockerfile은 image build에 필요하고 기존 offline test/Compose 검증 명령은 새 workflow에 옮긴다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 `web-ai` 파일·AWS·GitHub를 변경하지 않았다.
- 2026-08-21 사용자가 `tosunsaeng-web-ai-github-deploy-role` 생성과 `Too-Much-I/web-ai` GitHub variable `AWS_ROLE_ARN` 등록을 완료했다고 확인했다. 남은 작업은 AI 팀원이 `web-ai/.github/workflows/deploy.yml`을 수정하는 것이다. 기존 offline test·Compose validation은 유지하고 Docker Hub/EC2 SSH 단계를 제거하며 OIDC permission, ECR `tosunsaeng-ai` 단일 build/push, 현재 `tosunsaeng-ai-service` Task Definition 조회, `ai-api`와 worker 4개에 동일 digest 순차 render, Service 단일 deploy와 stability 검증을 추가한다. public `HEALTH_URL` curl은 제외하고 기존 `/ready` ECS health check를 사용하며 Task Definition의 command/environment/secrets/mount/resources는 변경하지 않는다. trust가 `web-ai/main` branch subject이므로 실제 배포 검증은 main merge/push 또는 main 대상 workflow_dispatch에서 이루어진다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 workflow·AWS·GitHub를 추가 변경하지 않았다.
- 2026-08-21 `tosunsaeng-web-ai-github-deploy-role` 생성 절차를 현재 AI ECS 실값으로 확정했다. 기존 GitHub OIDC provider `token.actions.githubusercontent.com`과 audience `sts.amazonaws.com`을 재사용하고 trust subject를 `repo:Too-Much-I/web-ai:ref:refs/heads/main`으로 제한한다. Role 권한은 ECR authorization, repository `tosunsaeng-ai` push, ECS describe/register/tag, 정확한 service `tosunsaeng-staging-cluster/tosunsaeng-ai-service` update, 기존 execution role `tosunsaeng-ecs-execution-role`에 대한 `iam:PassRole`만 허용한다. AI `taskRoleArn`은 null이므로 PassRole 대상에 추가하지 않는다. 생성된 role ARN은 GitHub repository variable `AWS_ROLE_ARN`으로 등록하고 static AWS key는 만들지 않는다. workflow에 GitHub Environment를 추가하면 OIDC subject가 달라지므로 현재 branch trust와 함께 사용하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 IAM·GitHub·workflow는 변경하지 않았다.
- 2026-08-21 사용자가 제공한 `tosunsaeng-ai:5` Task Definition JSON을 민감값 없이 구조적으로 확인했다. `taskRoleArn`은 null이므로 AI Task Role은 생성하지 않는다. `executionRoleArn`은 기존 `arn:aws:iam::889384901776:role/tosunsaeng-ecs-execution-role`이며 현재 ECR image pull, CloudWatch logging과 세 가지 API credential의 ECS secret injection이 정상 작동하므로 새 Execution Role도 생성하지 않고 재사용한다. GitHub deploy role의 `iam:PassRole` resource에는 이 execution role ARN 하나만 넣는다. Fargate/awsvpc Task는 family `tosunsaeng-ai`, revision 5, CPU 2048, memory 4096이며 다섯 essential container가 동일 image digest를 사용한다. `ai-api`에는 localhost port 8000 `/ready` health check가 interval 30s, timeout 5s, retries 3, startPeriod 60s로 실제 등록되어 있고 worker에는 health check가 없다. public `HEALTH_URL`은 불필요하다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았고 Secret 값은 조회·기록하지 않았다.
- 2026-08-21 사용자가 AI Task Role과 Task Execution Role을 새로 만들려 했으나, 이미 정상 실행 중인 `tosunsaeng-ai-service`의 현재 Task Definition에서 `taskRoleArn`과 `executionRoleArn`을 먼저 확인·재사용해야 한다고 정리했다. Execution Role은 ECR pull·CloudWatch Logs·ECS secret 주입을 담당하고 기존 실행 Task에 거의 확실히 존재한다. Task Role은 AI 애플리케이션이 AWS API를 직접 호출할 때만 필요하며 null이면 새로 만들 필요가 없다. 새 역할이 실제로 필요한 경우 둘 다 `ecs-tasks.amazonaws.com` trust를 사용하고, execution role에는 `AmazonECSTaskExecutionRolePolicy`와 현재 Task Definition이 참조하는 Secret/KMS에만 최소 권한을, task role에는 애플리케이션이 실제 호출하는 AWS resource 권한만 부여한다. 새 역할 적용은 Task Definition revision과 Service 배포 검증이 필요한 별도 변경이며 GitHub deploy role의 `iam:PassRole`은 최종 사용 role ARN에만 제한한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 지금까지 확인한 실제 AI ECS 값을 반영해 `web-ai` GitHub Actions 전환 절차를 통합 정리했다. 확정값은 account/region 기반 ECR repository `tosunsaeng-ai`, cluster `tosunsaeng-staging-cluster`, service `tosunsaeng-ai-service`, containers `ai-api`, `ai-worker-1`~`ai-worker-4`다. 기존 offline test·Compose validation은 유지하고 Docker Hub·EC2 SSH 단계를 GitHub OIDC→ECR SHA push→현재 Task Definition 조회→다섯 container 순차 render→Service 단일 deploy/stability 검증으로 교체한다. public AI DNS가 없으므로 `HEALTH_URL` curl은 제외한다. AWS OIDC provider는 기존 backend 설정을 재사용할 수 있지만 `web-ai` repo/main subject trust, AI ECR/ECS 권한과 현재 Task Definition의 execution/task role에 제한된 `iam:PassRole`을 가진 deploy role이 필요하다. 실제 role ARN 두 개와 GitHub `AWS_ROLE_ARN` 값은 AWS에서 확인해야 하며 Secret·Token으로 문서화하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 workflow·AWS·GitHub는 변경하지 않았다.
- 2026-08-21 사용자가 `ai-api`와 `ai-worker-1`~`ai-worker-4`가 모두 동일한 `tosunsaeng-ai` ECR repository와 동일 current image digest를 사용한다고 확인했다. 따라서 AI GitHub Actions image 전략은 하나의 Docker build, `tosunsaeng-ai:${GITHUB_SHA}` ECR push, 동일 image URI를 다섯 container에 순차 render, 최종 Task Definition 하나를 `tosunsaeng-ai-service`에 한 번 deploy하는 것으로 확정됐다. 기존 Task Definition의 API/worker별 command, environment, secret, mount, resource 설정은 render action이 보존한다. public health URL은 사용하지 않고 `ai-api` container readiness와 ECS service stability를 사용한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 현재 AI container image URI를 제공해 ECR repository를 확정했다. registry 뒤 `/`와 digest `@sha256:` 사이 값에 따라 `ECR_REPOSITORY=tosunsaeng-ai`다. 제공된 digest는 현재 배포 image의 immutable 식별자이며 workflow repository 값에는 포함하지 않는다. `ai-worker-1`~`ai-worker-4`도 같은 repository/digest인지 Task Definition에서 대조한 뒤 다섯 container를 동일 새 SHA image URI로 render한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았고 image digest 외 Secret·Token은 기록하지 않았다.
- 2026-08-21 사용자가 AI workflow의 `ECR_REPOSITORY` 확인 위치를 질문했다. ECS `tosunsaeng-ai-service`가 사용하는 Task Definition revision에서 `ai-api` container의 `Image URI`를 확인하고, registry hostname 다음 `/`부터 tag `:` 또는 digest `@sha256:` 전까지를 repository name으로 사용한다. 예를 들어 `<account>.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-web-ai:<tag>`이면 `ECR_REPOSITORY=tosunsaeng-web-ai`다. worker 4개의 Image URI도 같은 repository인지 함께 확인해야 하며, 다르면 하나의 image로 일괄 render하지 않는다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 `tosunsaeng-ai-service`의 단일 Task에 `ai-api`, `ai-worker-1`~`ai-worker-4` 다섯 컨테이너가 있고 `ai-api` 로그에서 localhost `/ready` 200 응답을 확인했다고 제공했다. 자동 배포는 service를 한 번만 갱신하되, 동일 ECR image를 사용하는 것이 확인되면 `amazon-ecs-render-task-definition`을 `ai-api`→worker 1~4 순서로 체인해 다섯 container image를 모두 같은 immutable SHA URI로 교체하고 마지막 rendered Task Definition만 deploy해야 한다. `CONTAINER_NAME=ai-api` 한 개만 render하면 worker가 구버전 image로 남을 수 있다. localhost readiness가 이미 동작하므로 public DNS가 없는 현재 구성에서는 `HEALTH_URL` curl을 제거하고 ECS container health와 `wait-for-service-stability`를 사용한다. Task Definition에서 다섯 container의 current image repository 동일성과 `healthCheck` 등록 상태는 최종 확인이 필요하다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 공통 cluster `tosunsaeng-staging-cluster`와 AI ECS Service `tosunsaeng-ai-service`를 확정하고 workflow의 `CONTAINER_NAME`, `HEALTH_URL` 확인 위치를 질문했다. `CONTAINER_NAME`은 Service가 참조하는 Task Definition revision의 `Container definitions` 또는 실행 Task의 `Containers`에서 `Name`을 확인한다. `HEALTH_URL`은 Task Definition 값이 아니라 public ALB listener/target group health path와 Route 53 record가 있을 때만 조합한다. 공개 조회에서 `ai.to-teacher.com`은 DNS 해석되지 않아 현재 health URL로 사용할 수 없다. Learning Core 기본 `AI_SERVER_URL=http://tosunsaeng-ai:8000`을 고려하면 Service Connect/Cloud Map 내부 연결일 가능성이 높으며, 이 경우 workflow는 public curl 대신 container `healthCheck`와 ECS service stability/healthy task 확인을 사용해야 한다. AWS Console은 여전히 로그아웃 상태라 실제 container name과 service networking은 확정하지 못했다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 `web-ai`가 Learning Core와 같은 `tosunsaeng-staging-cluster`에 배포됐다는 사용자 확인을 바탕으로 자동 배포에 필요한 나머지 실값 조회를 시도했다. 로컬 Learning Core workflow에서 region `ap-northeast-2`, cluster, GitHub OIDC와 현재 task definition을 조회해 ECS Service를 갱신하는 패턴은 확인했다. 그러나 로컬 AWS CLI에는 credential이 없고 GitHub CLI 인증은 만료됐으며, in-app AWS Console과 비공개 GitHub 저장소도 로그아웃 상태라 AI ECS Service/Task Definition/container/ECR/deploy role 권한의 실제 값은 확인하지 못했다. AWS 로그인 탭을 사용자 handoff로 열어두었으며 로그인 뒤 읽기 전용으로 AI Service→Task Definition→container/image/ECR→deployment config→IAM role→GitHub workflow/variables 순으로 대조할 수 있다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. AWS·GitHub·코드는 변경하지 않았다.
- 2026-08-21 사용자가 `web-ai`도 이미 Learning Core와 유사하게 ECS에 정상 배포되어 있다고 정정했다. 따라서 Redis/EFS/ALB/ECS Service를 신규 설계·생성하는 작업은 현재 범위가 아니다. 필요한 변경은 기존 `web-ai` workflow의 Docker Hub·EC2 SSH 재기동 단계를 제거하고, GitHub OIDC로 AWS deploy role을 assume한 뒤 ECR에 immutable SHA image를 push하고 현재 AI ECS Service의 Task Definition image를 render·register·deploy하여 service stability와 health를 확인하는 자동화다. AI가 API/worker 단일 service면 한 번, 별도 service면 동일 image로 각 service를 순차 갱신한다. 관련 기존 Jira는 `TMI-25`이며 신규 배포 Jira 키는 제공되지 않았다. 실제 AWS·GitHub Actions·`web-ai` 코드는 변경하지 않았다.
- 2026-08-21 `web-ai`의 기존 EC2 Docker Compose·Docker Hub·SSH 배포를 ECS/ECR·GitHub OIDC 방식으로 전환하는 설계를 정리했다. ECS에서는 cluster 자체가 아니라 API·worker 별 Task Definition revision과 Service를 갱신한다. 현재 AI 작업이 Redis에 로컬 업로드 경로를 전달하므로 workflow만 바꾸면 분리된 Fargate task가 파일을 공유하지 못한다. 1차 전환은 ElastiCache Redis와 EFS access point를 공용으로 사용하고 `/app/data` 전체를 덮지 않는 별도 mount path를 평가 관련 환경변수에 연결한다. 이후 API 1개와 worker 4개를 별도 ECS Service로 구성하고 동일 immutable ECR SHA image를 GitHub OIDC deploy role로 배포한다. execution role, application task role, GitHub deploy role을 분리하고 staging(`develop`)·production(`main`)의 cluster/service/secret/role을 격리한다. 관련 Summary 멱등·generation 범위는 기존 `TMI-25`이며 신규 인프라 Jira 키는 제공되지 않았다. 실제 `web-ai`, AWS, GitHub Actions와 애플리케이션 외부 계약은 변경하지 않았다.
- 2026-08-21 전체 앱 흐름을 현재 구현과 확정 계획으로 나눠 재정리했다. 현재 Learning Core는 JWT/Legacy 사용자 식별, 순환 시험지 배정, S3 직접 업로드, `QuestionGradingJob`/`SummaryGradingJob` 기반 비동기 AI 채점·멱등 Callback, Polling·결과·이력·재답변 조회와 시험 단위 채점 복구를 구현한 상태다. 1차 앱의 목표 흐름은 Identity 로그인·verified phone(`TMI-90`, `TMI-95`, `TMI-98`) → Billing/Entitlement의 무료 1회·인앱결제·사용권 reserve/confirm → Learning Core Session/AttemptGroup → 채점 완료 또는 무료 replacement → 결과 조회이며, Learning Core의 기존 인증·채점 기반 작업에는 `TMI-14`, `TMI-25`, 시험 배정에는 `TMI-31`이 반영돼 있다. Billing 연동, `AttemptGroup`/R3, Learning Core·Billing의 `UserMerged` consumer, Identity multi-consumer fan-out과 staging/prod 배포 자동화는 아직 계획·후속 구현 범위이고 Billing/Learning Core 후속 Jira 키는 제공되지 않았다. 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI/Callback `user_id=examId` 계약은 유지한다.
- 사용자가 Billing/Entitlement를 새로운 하나의 배포 서비스로 시작하고, 10 credits=시험 1회, 5천원=5 credits, 1만원=10 credits, 3만원=3일 무제한+3일 출석 시 하루 연장, 5만원=100 credits, 첫 구매 2배, 주간 연속 로그인 `0,1,1,1,2,2,3`, 추천 code 10 credits, coupon별 credits와 검증된 휴대전화 번호당 무료 1회를 제품 기본안으로 정했다. 관련 Identity Jira는 `TMI-95`, `TMI-98`이며 Billing/Learning Core 후속 키는 미제공이다.
- `docs/codex/BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 immutable grant ledger, unlimited/free entitlement, 5분 reservation, Session commit-confirm saga, PG webhook 멱등 상태, Billing `UserMerged` consumer와 TrialClaim 보존 권장안을 기록했다.
- 결제 채널은 Apple In-App Purchase와 Google Play Billing만 사용하는 것으로 확정했다. 웹 checkout·웹 PG는 현재 범위에서 제외하고 향후 별도 제품 결정으로만 추가한다. Billing은 두 store provider adapter와 단일 주문·entitlement 원장을 사용하며, 배포 국가별 store 정책·product ID·상품 유형·실제 가격 구간은 출시 전에 확정해야 한다.
- 첫 구매 2배는 verified phone 기준 첫 credit 상품에 적용하고 `CREDIT_100`은 총 200 credits를 지급한다. unlimited pass를 먼저 구매해도 자격을 소진하지 않으며 merge·탈퇴·환불로 자격을 다시 열지 않는다.
- 3만원 pass는 구매 후 30일 안의 첫 reserve부터 72시간, 서로 다른 KST 3일의 Billing check-in 완료 시 24시간 한 번 연장으로 확정했다. 재구매 pass는 별도 보존하고 활성 pass 종료 뒤 활성화하며 미활성·미사용 pass만 환불한다.
- 추천 보상은 입력자와 추천인에게 각각 10 credits를 지급하되 입력자의 verified phone과 첫 유료 인앱결제 `CAPTURED` 후 한 번만 지급한다. phone당 code 입력 1회, self-referral 금지와 abuse 보류, 첫 결제 환불 시 revoke/debt 규칙을 적용한다.
- 아직 확정할 product 세부사항은 7일 streak 반복/reset과 paid/promotional 만료, 부분 사용 후 환불·chargeback, optional `Idempotency-Key`, coupon stacking/한도/만료, TrialClaim 법무 보존과 번호 재할당 정책이다.
- 확정 계약의 코드 영향 검토 결과, Learning Core `UserMerged` 전용 계획은 그대로 유지하되 시험 생성의 Billing reserve→Session commit→confirm/cancel/reconcile를 다루는 별도 구현 계획·Jira가 필요하다. 현재 `startNew()`은 기존 활성 Session을 abandon하고 새 Session을 즉시 insert하므로 operation 멱등성과 reservation metadata·durable reconciliation을 함께 설계해야 한다.
- Identity의 PhoneEligibilityBinding publisher는 Billing consumer를 이미 전제하므로 상품·인앱결제·추천을 위한 payload 확장은 필요 없다. 다만 현재 `UserMerged` publisher는 단일 endpoint/audience와 event당 delivery status 하나만 지원하므로 Learning Core와 Billing의 독립 delivery를 위해 consumer별 delivery state fan-out 또는 승인된 durable broker가 필요하며 direct HTTPS consumer별 delivery를 권장한다.
- 권장 실행 순서는 Identity→Billing→Learning Core의 완전 직렬 개발이 아니다. Phase 0에서 phone binding, multi-consumer `UserMerged`, reserve/confirm/cancel, store transaction과 idempotency/error 계약 및 Jira를 동결하고, Identity fan-out·Billing foundation·Learning Core 계획/consumer를 feature OFF로 병렬 구현한다. staging에서는 consumer endpoint와 보안을 먼저 배포한 뒤 phone eligibility, 무료시험 E2E, `UserMerged` fan-out, store sandbox, Billing enforcement 순으로 검증하고 signup/Guest merge production flag를 마지막에 연다.
- `Idempotency-Key`와 Billing 오류 계약의 선택지를 `BILLING_ENTITLEMENT_CONTRACT_DECISIONS.md`에 추가했다. 권장 패키지는 공개 API header optional·신규 앱 UUID v4 필수, user/operation scope, Session 수명 동안 mapping과 terminal command 7일, 완료 결과 재사용·processing 409다. 오류는 pass-through하지 않고 사용권 부족 402, eligibility/payment/exam processing 409, rate limit 429, Billing 장애·confirm 불명 503과 `Retry-After`로 mapping하며 같은 key 재시도와 reconciliation을 사용한다. 아직 사용자 승인은 받지 않았다.
- 사용자는 이어풀기를 제외하고 완료 전 무제한 무료 replacement(R3)를 확정했다. 최초 시작에서 entitlement를 한 번 소비해 OPEN AttemptGroup을 만들고, restart마다 기존 Session을 `ABANDONED_RESTARTED`로 종료한 뒤 새 key·새 examId를 같은 group/consumption과 동일 mockExamId에 연결한다. 서로 다른 restart는 다른 key를 쓰되 같은 restart의 response loss 재전송은 같은 key로 같은 Session에 수렴하므로 R3에서도 idempotency는 필요하다.
- 차감은 Billing 5분 reserve→Learning Core Session commit→Billing confirm에서 최초 한 번 확정한다. submit 미완료 장애는 OPEN group 무료 replacement, 모든 필수 최초 submit 접수 뒤에는 GRADING에서 기존 채점 복구, 필수 피드백·유효 점수·Summary가 조회 가능할 때만 COMPLETED다. grading retry/reconciliation이 최종 실패하면 RETAKE_AVAILABLE로 전환해 같은 consumption으로 무료 새 Session을 열며, retake조차 제공할 수 없는 장기 장애에는 원 credits/free entitlement를 멱등 복원한다. 재화 자동 순서는 unlimited→free once→promotional→paid를 권장하며 아직 사용자 승인은 받지 않았다.
- `Idempotency-Key`는 결제·AttemptGroup 식별자가 아니라 한 번의 Session create/restart command 식별자다. 같은 사용자 동작의 timeout·response loss 재전송은 같은 key로 같은 Session을 반환하고, 사용자가 의도한 다음 restart는 새 key·새 Session을 만든다. R3 무료 replacement에서도 key가 없으면 한 번의 restart 재전송이 여러 Session과 Billing authorization·Redis/S3/Job을 만들 수 있으므로 유지한다.
- 배포 환경은 현재 `tosunsaeng-staging-cluster`를 업데이트 전 통합 검증용으로 유지하고 신규 `tosunsaeng-prod-cluster`를 실제 사용자용으로 추가하는 방향으로 정했다. 초기에는 같은 AWS account·VPC를 사용할 수 있지만 ECS service/task definition/target group/log, 도메인과 workload credential, task role/secret, MongoDB·Redis·S3 mutable data boundary, Apple/Google sandbox·production 설정을 환경별로 격리한다. staging에서 검증한 동일 immutable image digest만 production으로 승격하며 production signup/merge/Billing flag는 마지막에 연다. 제공된 AWS Console 링크는 로그인 화면으로 전환되어 기존 cluster의 실제 service·capacity provider·network inventory는 확인하지 못했다.
- 브랜치별 자동 배포는 `develop` merge/push→`tosunsaeng-staging-cluster`, protected `main` PR merge→`tosunsaeng-prod-cluster`로 구성한다. 현재 `deploy-staging.yml`은 `main`에서 staging을 배포하므로 trigger 변경이 필요하고, production은 별도 workflow·GitHub Environment·OIDC role·cluster/service/health URL과 환경별 secret/variable을 사용해야 한다. 기본 자동화는 각 환경 test/build/deploy이고, 더 강한 배포 동일성이 필요하면 staging 검증 ECR image digest를 production에 그대로 승격한다. Billing·인프라 후속 Jira 키는 아직 없다.
- 1차 업데이트의 SNS 로그인·결제·검증된 휴대전화 번호당 무료 모의고사 1회 범위를 Identity의 기존 `TMI-90`, `TMI-95`, `TMI-98` 계약과 대조했다. Billing/Learning Core 후속 Jira 키는 제공되지 않았다.
- 결제와 무료 1회를 1차 production 범위에 포함한다면 Billing 서버는 릴리스 이후 작업이 아니라 선행·병렬 의존성이다. 초기에는 결제와 Entitlement를 별도 서비스 둘로 나누지 않고 Billing/Entitlement 하나가 payment, TrialClaim, Entitlement, reserve/confirm/cancel/reconcile와 merge 이전을 소유하는 방향을 권장한다.
- 무료 정책은 userId당 1회가 아니라 검증된 휴대전화 번호당 1회다. Identity는 consumer-scoped eligibility binding만 생산하고 Billing/Entitlement가 TrialClaim unique와 사용권 원장을 소유하며, Learning Core는 시험 생성 전 reserve하고 성공 후 confirm한다.
- 검증 번호당 1회는 실제 자연인당 1회를 완전히 보장하지 않는다. 여러 번호와 번호 재할당까지 막아야 한다면 KYC·abuse·보존 정책이 추가되므로 1차 제품 요구사항에서 의미를 명시해야 한다.
- 현재 `UserMerged` 최종 계획의 애플리케이션 코드 변경 주 대상은 Learning Core다. 그러나 production 연동에는 Identity의 불변식/status 계약·workload/publisher 설정 또는 보완, 인프라 TLS/network/Mongo, staging E2E가 필요하며 Billing의 entitlement merge consumer는 별도 범위다.
- 상세 책임 경계와 1차 업데이트 병렬 track을 `docs/codex/FIRST_RELEASE_BILLING_BOUNDARY_REVIEW.md`에 기록했다.
- 확정된 `UserMerged` C1~C11 계약을 실제 구현 단위로 내린 최종 계획을 `docs/codex/USER_MERGED_CONSUMER_IMPLEMENTATION_PLAN.md`에 추가했다. 별도 Jira 이슈 키는 제공되지 않았다.
- 구현 순서는 외부 credential·Mongo 지원 확인, Transaction/guard foundation, 기존 writer·Callback 전환, migration/backfill/index, workload security/internal endpoint, inbox/ownership migration, replica-set 동시성 테스트, staging E2E/P99, 제한 rollout이다. endpoint부터 먼저 구현하지 않는다.
- 모든 user-owned Mongo command는 guard와 business mutation을 같은 Transaction에서 commit하고 S3 network·AI·Redis는 commit 후 실행한다. PUT Presigned URL의 로컬 서명만 guard와 직렬화해 merge 뒤 신규 capability 발급 경합을 막는다.
- direct consumer는 source/target guard, 활성 시험 정책, `exam_sessions`/`exam_results`/`exam_summaries` owner rewrite, source MERGED deny와 inbox PROCESSED를 하나의 Transaction으로 처리한다. Callback은 Session current owner를 다시 읽고 guard/result/Job을 같은 Transaction에 둔다.
- production 유사 staging의 direct Transaction 성능 gate 실패 시 timeout 연장이나 hybrid 처리 없이 C2-B durable inbox + worker 계약으로 개정한다. 첫 processed merge 뒤에는 guard-unaware 구버전으로 rollback하지 않는 runbook을 필수로 한다.
- 사용자가 `UserMerged` 계약 결정 가이드의 C1~C11 권장 기본 패키지를 승인해 구현 방향과 C4-A+C6-A 위험 수용 정책을 확정했다. 별도 Jira 이슈 키는 제공되지 않았다.
- target 활성 시험 우선·source-only 활성 이전, 모든 history 합집합 보존, merge 전 발급된 S3 PUT URL의 최대 5분 잔여 capability 수용이 제품 정책으로 확정됐다. Learning Core API의 source actor 권한은 merge commit 즉시 폐기하되 S3 capability 자체는 즉시 취소되지 않는 범위를 명시한다.
- source/target 양쪽 guard, Callback 전체 Transaction, 상충 event fail-closed, 권장 HTTP status 표, 인프라 TLS/network 제한과 publisher OFF 상태의 단계적 writer 전환을 구현 기준으로 확정했다.
- direct Transaction은 조건부 확정이다. Mongo 지원과 production 유사 staging 성능 기준을 통과하면 유지하고, 실패하면 timeout 연장이나 hybrid 대신 durable inbox + worker로 계약을 개정한다.
- 아직 운영 활성화 승인은 아니다. C1 실제 issuer/JWKS/audience/principal/TTL/rotation, Identity 인계서 반영, Mongo/P99, TLS/network와 staging E2E가 남아 있다. 상세 확정 상태는 `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`에 기록했다.
- `UserMerged` 선행 계약을 실제로 확정하기 위한 선택지·장단점·권장 조합·승인 절차를 `docs/codex/USER_MERGED_CONTRACT_DECISIONS.md`에 정리했다. 별도 Jira 이슈 키는 제공되지 않았다.
- 필수 기술 불변식은 source/target 양쪽 guard, Callback의 Session/guard/result/Job 단일 Transaction, 상충 event fail-closed다. 제품·보안이 직접 선택할 핵심은 활성 시험 처리와 merge 전 발급된 S3 PUT URL의 최대 5분 잔여 capability 수용 여부다.
- 권장 기본안은 target 활성 시험 우선·source-only 활성 이전·모든 history 합집합 보존·5분 잔여 위험 명시적 수용이다. source 유래 S3 write 가능성도 0이어야 한다면 source 활성을 항상 abandon하거나 revocable/nonce 업로드를 별도 설계해야 한다.
- direct Transaction은 아직 무조건 확정하지 않고 production 유사 staging에서 예시 P99 2초 이하 등 공동 승인 기준을 통과할 때만 채택한다. 실패하면 timeout 연장이나 hybrid가 아니라 durable inbox + worker 계약으로 개정한다.
- 현재 확인 시점 HEAD는 `98730c9`이며 작업 시작 시 worktree는 clean이었다.
- Identity Service의 `UserMerged` schema version 1 인계서를 현재 Learning Core 코드와 대조 검토했다. 애플리케이션·설정·테스트 코드는 변경하지 않았으며 별도 Jira 이슈 키는 제공되지 않았다.
- 검토 결론은 “구현 가능, 선행 결정 필요”다. workload credential의 모든 TBD, source/target 양쪽 guard 획득, 활성 시험 충돌 정책, Callback stale-owner 경합, 기존 Presigned PUT URL의 최대 5분 잔여 권한, Mongo Transaction/P99 검증이 endpoint 구현 전 차단 사항이다.
- 현재 직접 userId를 가진 컬렉션은 `exam_sessions`, `exam_results`, `exam_summaries`다. Question/Summary Job, Azure/SpeechAce 결과, Redis와 S3는 `examId` 간접 귀속이므로 rewrite하지 않고 Session ownership을 유지하는 방향이 맞다.
- 기존 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, AI·Callback `user_id=examId` 계약은 변경할 필요가 없으며 internal endpoint는 사용자 JWT와 분리된 workload 전용 SecurityFilterChain을 사용해야 한다.
- 상세 inventory, 차단 사항과 권장 구현 순서는 `docs/codex/USER_MERGED_CONSUMER_REVIEW.md`에 기록했다. 이번 검토에서는 테스트를 실행하지 않았고 Git/Jira 쓰기 작업도 수행하지 않았다.
- 확인 시점 HEAD는 `52634a9`이며 이번 분석에서는 애플리케이션·설정·테스트 코드를 변경하지 않았다.
- 기존 사용자 작업인 Actuator 의존성·Health 설정을 보존한 상태에서 AWS S3 자격 증명 구성을 Default Credentials Provider Chain으로 전환한 미커밋 변경이 작업 트리에 있다.
- `S3Config`의 프로젝트 전용 Access Key/Secret Key 주입과 static credential 생성을 제거했다. `S3Client`와 `S3Presigner`는 공유 `DefaultCredentialsProvider`를 사용하며 기존 Region·Bucket property, Object Key와 Presigned URL 동작은 유지한다.
- `application.yml`과 test profile에서 static credentials 설정을 제거했고 credential 없는 `.env.example`, 로컬 profile/Docker/ECS Task Role 문서를 추가했다. AWS SDK는 BOM `2.29.52`로 `s3`, `sso`, `ssooidc`, `sts`를 함께 관리해 일반 Profile, 현대식 `sso_session`, Assume Role와 Web Identity 경로를 지원한다.
- Spring Cloud AWS 자동 구성과 S3 Health Indicator는 없다. Credential 환경변수와 Profile mount 없이 새 linux/amd64 이미지를 실행해 `/actuator/health` HTTP 200·`UP`을 확인했고 검증 컨테이너는 SIGTERM으로 종료했다.
- S3 credential 관련 테스트는 classpath 5개, S3 Bean 7개, 설정 계약 4개, Health 1개로 총 17개다. 최신 `./gradlew clean test`는 XML 기준 전체 Java 248개, failures/errors/skipped 0개다.
- 운영 코드·설정에서 프로젝트 전용 AWS key 이름, `StaticCredentialsProvider`, `AwsBasicCredentials`, credentials property 잔여가 없고 실제 AWS Key 패턴도 발견되지 않았다. 설명과 금지 계약 테스트의 문자열만 의도적으로 남아 있다.
- Gradle `runtimeClasspath`의 AWS SDK 모듈은 transitive `auth`·`profiles`를 포함해 모두 `2.29.52`로 해석되며 혼합 버전이 없다. 필요한 SSO OIDC와 STS 클래스도 classpath 테스트로 확인했다.
- native Linux 문서는 host UID/GID 실행, image app group `999` 추가, `HOME=/app`과 read-only Profile mount를 사용한다. 현재 Docker Desktop에서 owner-only 빈 모의 Profile·SSO cache 접근, Java `user.home=/app`, non-root, JAR 읽기와 `/tmp` 쓰기를 확인했지만 실제 native Linux host 검증은 남아 있다.
- 최종 MEDIUM finding 후 README의 로컬 Docker SSO 정책을 보완했다. macOS와 native Linux 모두 각 개발 세션 전에 host에서 `aws sso login`과 stdout을 숨긴 credential 검증을 수행하고, 컨테이너는 host `.aws`를 read-only로 읽기만 한다. 컨테이너 내부 token cache 갱신은 보장하지 않으며 만료 시 host 재로그인 후 컨테이너를 재시작한다. 일반 Shared Credentials Profile의 만료 정책과 SSO Profile 절차를 구분했고 ECS Task Role 흐름에는 영향이 없다.
- `.dockerignore`는 root·중첩 `.aws`를 제외하며 새 이미지에도 `/app/.aws`가 없다. 실제 AWS Profile/SSO를 사용한 S3 Smoke Test와 ECS Task Role 실환경 검증은 수행하지 않았다.
- 이번 credential 후속 작업에 별도 Jira 이슈 키는 없으며 Codex는 commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았다.
- 기존 순차·순환 선택, 진행 중 세션 재사용, Summary 저장 후 완료와 전 과정 `mockExamId` 전파 구조는 유지했다.
- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c` 기준 최종 리뷰의 HIGH 1건과 MEDIUM 4건을 수정했다. summarized legacy Session 판정/backfill, 운영 partial unique index 시작 검증, 명시적 migration DB 선택, 완료 횟수 aggregation/index, 중복 `mockExamId` 차단이 현재 작업 트리에 반영됐다.
- 같은 merge base 기준 재리뷰에서 확인한 HIGH 1건과 MEDIUM 1건을 최소 범위로 수정했다. 분리 이전 `exam_results.totalScore != null` 종합 결과도 legacy 완료 증거로 인정하며, migration은 inactive/empty 등 배정 제외 MockExam을 먼저 분류한 뒤 assignable 시험에만 sequence를 강제한다.
- 필수 partial unique index fail-closed, 명시적 migration DB 선택, 완료 횟수 Mongo aggregation/index, 중복 `mockExamId` runtime/migration 차단과 외부 API·AI·Redis·S3 계약은 그대로 유지했다.
- 2026-07-30 사용자 지정 최종 명령 `git diff --check`, migration `node --check`, `./gradlew clean test`를 다시 실행해 모두 성공했다. 애플리케이션·테스트·migration 코드는 이 재검증에서 변경하지 않았다.
- 같은 merge base 기준 독립 최종 리뷰에서 P1 live migration stale activation, P2 Java `Integer` 범위를 넘는 sequence 허용, P3 기존 WORKLOG 항목 수정 3건을 확인했다. 리뷰 대상 코드는 수정하지 않았다.
- AGENTS.md와 Jira TMI-31을 다시 대조한 사용자 지정 11개 항목 최종 리뷰에서도 위 HIGH 1건, MEDIUM 1건, LOW 1건을 재확인했다. 정상 runtime의 legacy 증거 판정·단일 Session 집계·조건부 backfill·순환 선택·활성 재사용·운영 인덱스 fail-closed·DB 선택·제외 catalog·ID 고유성·`mockExamId` 전파와 외부 계약에는 별도 finding이 없었다.
- 후속 수정에서 HIGH를 해소했다. apply는 `TMI31_LEGACY_WRITER_STOPPED=true`를 필수로 요구하고, legacy 활성화 직전에 최신 Session과 `exam_summaries`, `exam_results.totalScore != null`을 재조회한다. 새 완료 증거는 `active=false`/`completedAt`으로 조건부 보정하며 apply 후 active/완료 증거/사용자 중복/필수 인덱스를 현재 DB 상태로 교차검증해 불일치 시 실패한다.
- Runtime은 `active=true`이면서 `cycleNumber`가 없는 legacy 의심 Session에만 완료 증거 방어 조회를 추가했다. 신규 `cycleNumber`가 있는 active Session은 빠른 재사용 경로를 유지한다.
- 후속 수정에서 MEDIUM을 해소했다. migration의 명시 sequence와 ID suffix는 공통 Java `Integer` 범위 `1..2147483647`만 허용하고 오류 유형을 구분한다. Runtime catalog는 suffix overflow와 repository mapping overflow를 민감한 BSON 내용 없는 설정 오류로 처리한다.
- LOW의 과거 WORKLOG branch/HEAD 한 줄은 main 원문 `feat/TMI-25-grading-retry-idempotency` / `fb354b6`로 복원했고 정정 경위는 새 append 항목에만 기록했다.
- 최종 검증은 `git diff --check`, 두 migration 파일 `node --check`, Node 49개, `./gradlew clean test` Java 205개 모두 성공했고 failures/errors/skipped는 0개다. 공개 Controller/DTO diff는 없고 실제 AWS Access Key·자격증명 포함 Mongo URI·private key 패턴도 발견되지 않았다.
- 이번 merge base 최종 재리뷰는 tracked diff와 신규 미추적 application·migration·테스트 파일을 다시 독립 검토했으며, 수정 가치가 확실한 신규 finding을 확인하지 않았다. 공개 API·DTO·`BaseResponse`, 소유권, Redis/S3 Key, `retryCount`, AI/Callback `user_id=examId` 계약도 그대로다.
- 이번 환경에서 migration 두 파일 `node --check`, Node 49개와 whitespace/Secret 정적 검사는 성공했다. 정확한 `./gradlew clean test`와 writable offline Gradle home 재시도는 각각 sandbox의 사용자 Gradle lock 쓰기 제한과 file-lock UDP socket 제한으로 task 실행 전에 중단됐고, 현재 소스보다 최신인 기존 XML은 Java 205개와 failures/errors/skipped 0개를 기록한다.
- 추가 HIGH/MEDIUM/LOW 해소 여부 최종 리뷰에서도 신규 severity finding은 확인하지 않았다. 초기 snapshot 뒤 완료 증거는 legacy 활성화 직전 실DB 재조회에서 차단되고, 성공 종료 전 active/완료 증거/사용자 중복/필수 인덱스 교차검증이 남은 불일치를 실패 처리한다. apply는 실제 writer 종료를 전제로 `TMI31_LEGACY_WRITER_STOPPED=true`를 필수 요구하며 이 승인값을 거짓으로 설정하는 운영 위반은 자동 프로세스 탐지 대상이 아니다.
- assignable sequence와 ID suffix는 `1..2147483647` 범위로 제한되고 Runtime mapping/suffix overflow도 안전한 catalog 오류로 실패한다. WORKLOG는 main 대비 기존 행 삭제·수정 없이 append-only 상태이며 공개 API·DTO·`BaseResponse`, `retryCount`, Redis/S3 Key, Callback JSON과 AI `user_id=examId` 계약도 유지된다.
- 이번 targeted review에서 `git diff --check`, migration `node --check`, Node 49개가 성공했고 현재 source의 기존 Java XML은 205개·failures/errors/skipped 0개다. 애플리케이션·migration·테스트 파일은 수정하지 않았다.
- 문항별 피드백 응답 흐름을 코드 기준으로 재확인했다. AI Callback은 결과를 Mongo에 멱등 저장하고 `BaseResponse<Void>`만 반환하며, 프론트는 문항 상태를 폴링한 뒤 `GET /api/v1/exams/{examId}/questions`에서 `QuestionResult`를 받는다. 상세 응답은 요청 회차의 최신 AI 결과, Azure 결과, 5분 제출 음성 URL과 Session `mockExamId`의 문제 정보를 결합한다. 채점 전 상세 조회도 가능해 빈 feedback/누락된 nullable 결과 필드가 반환될 수 있으므로 UI는 `COMPLETED` 뒤 조회하는 흐름이 안전하다.
- 사용자 요청으로 문항 상세의 `question` 객체에 additive `retryScores` 배열을 추가했다. 각 원소는 `retryCount`와 `score`를 가지며 동일 examId·questionNumber의 점수 있는 최신 결과를 retry 오름차순으로 반환한다. legacy null retry는 0으로 병합하고 동일 retry 중복은 `_id` 최신 문서만 사용하며, 최신 score가 null이면 과거 점수로 fallback하지 않고 해당 retry를 제외한다.
- 사용자 확정에 따라 문항 상세 `question.retryFeedbackScores`를 구현했다. 기존 `feedback`은 요청한 현재 retry를 유지하고, 새 배열은 동일 examId·questionNumber의 최초 응시 `retryCount=0` 세부 점수만 한 건 반환한다. legacy null retry도 0으로 병합하고 중복 0회차는 `_id` 최신 문서 하나만 사용하며, 최초 피드백이 없으면 빈 배열을 반환한다.
- 프론트 전달용 문항 상세 응답 계약을 현재 Controller·DTO 기준으로 재확인했다. HTTP 200 `BaseResponse.result.question`에 현재 retry `feedback`, 총점 이력 `retryScores`, 최초 응시 비교값 `retryFeedbackScores`와 음성·Azure·문제 정보가 들어간다. null인 question 필드는 생략될 수 있고 최초 피드백이 없으면 `retryFeedbackScores=[]`이다. 애플리케이션·테스트 코드는 이 정리 작업에서 수정하지 않았다.
- 변경 후 집중 테스트와 `./gradlew clean test`가 성공했다. XML 기준 Java 207개, failures/errors/skipped 0개이며 `git diff --check`도 통과했다. 문항 상세 기존 필드와 URL·Method·Query, `BaseResponse`, 소유권, AI user_id, retryCount, Redis·S3·grading 계약은 유지했다.
- TMI-31은 사용자 요청으로 Jira `완료`(ID `10003`, resolution `완료` ID `10000`)로 전환했고 재조회로 확인했다. 실제 Atlas backup/dry-run/apply·index build·aggregation explain 및 Redis·S3·Python AI staging E2E는 수행하지 않았다.

## Current question prompt API

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`를 additive로 추가했다. JWT 모드에서는 기존 SecurityFilterChain이 Bearer 인증을 요구하며 `JwtCurrentUserProvider`가 검증된 JWT `sub` UUID를 실제 사용자 ID로 사용한다. local/test legacy 인증 정책은 변경하지 않았다.
- 서비스는 `ExamSession`을 조회하고 현재 사용자와 `ExamSession.userId`를 비교한 뒤에만 `ExamSession.mockExamId`의 MockExam을 조회한다. legacy null/blank `mockExamId`는 기존 `mock_exam_003` fallback을 유지하고 다른 사용자의 시험은 `COMMON403`으로 차단한다.
- 응답은 세션 생성에서 사용하던 `QuestionDTO`를 그대로 재사용한다. Part, 문항 번호, text/reference/intro, image/table, 준비·답변 시간과 문제 음성 URL을 반환하며 Part 3은 기존 안내 음성 URL도 제공한다. 내부 examId·userId·mockExamId와 문제지 내부 Mongo ID는 응답에 추가하지 않았다.
- 문제 음성 Key와 60분 만료 정책은 기존 `questions/{mockExamId}/q_{questionNumber}.wav`를 유지하고 Part 3 안내 음성도 기존 Key를 유지한다. retryCount, 사용자 제출 S3 Key, AI·Callback, Redis와 채점 계약은 변경하지 않았다.
- URL 계약·Part별 매핑·선택된 시험지·문항 없음·소유권 선검증·JWT 401/403/200 테스트를 추가했다. 집중 테스트 55개와 `./gradlew clean test` 전체 Java 245개가 failures/errors/skipped 0개로 성공했다.
- Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경을 수행하지 않았고 Secret, Token, 실제 URI와 Presigned URL을 기록하지 않았다.

## Current question result model-answer audio change

- 이번 작업에 별도 Jira 이슈 키는 제공되지 않았으며 Jira 댓글·필드·상태를 변경하지 않았다. Git commit·push·PR 생성도 수행하지 않았다.
- 기존 문항 단건 `GET /api/v1/exams/{examId}/questions`와 필수 `questionNumber`, 선택·기본값 0인 `retryCount`, 별도 `/summary` 계약을 유지하면서 `result.question.modelAnswer`를 additive 선택 필드로 추가했다.
- 최종 `ModelAnswerResponse`는 `audioUrl`과 `spokenWordSequence` 두 필드만 가진다. 응답 DTO·Builder·JSON·OpenAPI·README 예시에 모범답안 문장 필드는 없고 null placeholder도 직렬화하지 않는다.
- `modelAnswer`는 소유권 확인 후 요청한 canonical retryCount의 `ExamResult`가 존재하고, 원본 문제의 `partNumber=1`이면서 `questionNumber=1` 또는 `2`이며 해당 시험지 메타데이터가 있을 때만 조립한다. 기존 상태 정책은 matching 결과 문서를 채점 완료 증거로 본다. 제출 전·처리 중·실패·존재하지 않는 회차와 다른 문항에서는 `PartResultDTO`의 `NON_NULL` 정책으로 필드 자체를 생략한다.
- MongoDB `model_answer` 컬렉션과 데이터는 조회·수정·삭제하지 않았다. 문항 단건 응답 경로는 해당 컬렉션이나 모범답안 텍스트에 의존하지 않는다.
- `mock_exam_004`의 q1 55개·q2 53개 모범답안 단어 시퀀스를 classpath 내부 메타데이터로 추가했다. 내부 record에서 기존 응답용 `SpokenWordDTO`로 명시 매핑해 index·segmentIndex·wordIndex, Long offset·duration, Double 발음 점수와 errorType을 그대로 유지하며 사용자 녹음 시퀀스와 분리한다.
- 완료 결과가 확인된 뒤에만 `ExamSession.mockExamId`를 사용해 `{mockExamId}/part1_a{questionNumber}.wav`를 결정하고 기존 `S3Presigner`로 60분 Presigned GET URL을 생성한다. 결과가 없으면 model-answer catalog 조회, 사용자·모범답안 S3 Key 조립과 Presign을 모두 생략한다. HeadObject, 새 credential provider, S3 Key DB 저장은 추가하지 않았다.
- `mock_exam_004` 외 시험지는 다른 시험의 시퀀스를 재사용하지 않는다. 해당 시험지의 q1/q2 모범답안 제공이 필요하면 올바른 시퀀스 메타데이터 파일을 먼저 추가해야 한다.
- 제출 전, 존재하지 않는 retry와 처리 중 결과 없음의 조기 차단 테스트 3개를 추가했다. 완료 Q1·Q2, 다른 Part, 타 사용자 403, feedback·retryScores·retryFeedbackScores 회귀를 포함한 집중 테스트 44개와 전체 Java 248개가 failures/errors/skipped 0개로 성공했고 `git diff --check`도 성공했다. 실제 AWS, MongoDB, Redis, Python AI와 Sentry는 호출하지 않았다.
- 기존 feedback·azureFeedback·완료 결과의 사용자 audioUrl·사용자 spokenWordSequence·questionInfo/referenceText/audioUrl, JWT·Guest JWT·소유권, Redis·채점 멱등성, AI/Callback `user_id=examId`, Default Credentials와 Health 계약은 변경하지 않았다. 결과가 없는 회차는 사용자 음성 및 모범답안 Presign을 모두 생략한다. 기존 미커밋 AWS/Actuator 작업도 보존했으며 S3 인증·Docker 파일은 수정하지 않았다.

## Current grading client-source implementation

- 이번 구현에 별도 Jira 이슈 키는 없다. Git commit·push·PR 생성과 Jira 댓글·필드·상태 변경도 수행하지 않았다.
- 앱 Learning Core와 기존 웹 POC 백엔드가 별도라는 확정에 따라 공개 submit API나 `submitQuestion` 시그니처에는 source를 추가하지 않았다. 앱 전용 `GradingDispatchService.dispatchQuestion`이 Python AI multipart에 `client_source=app`을 고정 추가한다.
- 최초 submit, 시험 단위 retry와 stale recovery가 모두 같은 `dispatchQuestion` 경로를 사용하므로 `QuestionGradingJob`·`QuestionDispatchClaim`에 중복 source 필드를 저장하지 않아도 매 AI 문항 요청에 동일하게 전달된다.
- 기존 AI `user_id=examId`, mockExam/part/question/retry/audio, 결정적 Job ID와 `Idempotency-Key`를 유지한다. 전체 요약 AI Body와 Callback JSON에는 `client_source`를 추가하지 않았고, source를 JWT 인증·시험 소유권 판단에 사용하지 않는다.
- `GradingDispatchServiceTest`가 문항 multipart의 정확한 `client_source=app`과 Summary Body의 source 미포함을 함께 검증한다. 집중 테스트와 `./gradlew clean test`가 성공했고 XML 기준 Java 229개, failures/errors/skipped 0개이며 실제 AWS·Python AI는 호출하지 않았다.
- Python AI는 신규 multipart 필드 `client_source`를 읽고 값 `app`을 처리해야 한다. 기존 웹 POC 요청의 필드 누락을 `web`으로 해석하는 정책은 Python 측에서 별도로 반영·검증해야 한다.

## Current grading service responsibility note

- `ExamGradingService`는 채점 자체를 계산하는 서비스가 아니라 Question/Summary Job 상태를 관리하는 orchestration 계층이다. 기존 결과 확인, 결정적 Job 생성, optimistic claim, retry 가능 여부·시도 한도 판단, 실패/완료 전이, 전체 상태 계산과 Redis projection, Summary 시작 조건을 담당한다.
- `GradingDispatchService`는 이미 claim된 immutable 요청을 외부로 운반하는 integration 계층이다. S3 GET Presigned URL 생성과 음성 다운로드, Python AI용 Question multipart·Summary JSON·`Idempotency-Key` 조립, HTTP POST만 담당하고 Mongo Job 상태나 retry 정책을 결정하지 않는다.
- 호출 방향은 Controller → `ExamServiceImpl`의 소유권 확인 → `ExamGradingService`의 상태·정책 결정 → `GradingDispatchService`의 외부 I/O → Python AI다. 전송 실패가 발생하면 Dispatch 서비스는 예외를 올리고 Grading 서비스가 claim attempt와 Job 상태를 안전하게 실패 전이한다.
- `client_source=app`은 앱 전용 백엔드의 AI wire metadata이므로 `GradingDispatchService`에 위치한다. 이 값이 retry 정책·Job identity를 바꾸지 않으므로 `ExamGradingService`나 Job Entity에 저장하지 않는다.
- 이번 설명 작업에 별도 Jira 이슈 키는 없으며 애플리케이션·테스트 코드는 수정하지 않았다. Git commit·push·PR 생성 및 Jira 댓글·필드·상태 변경도 수행하지 않았다.

## Current Jira issue

- [`TMI-31`](https://to-teacher.atlassian.net/browse/TMI-31) — [Learning Core] 사용자별 모의고사 순차 배정 및 순환 제공
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 라벨: 없음
- 사용자별 활성 MockExam 완료 횟수와 sequence 기반 순차·순환 선택, 진행 중 활성 ExamSession 재사용, 사용자당 활성 세션 하나, 선택된 `mockExamId`의 S3·문항 조회·AI grading retry·summary 전 과정 전파, legacy null의 `mock_exam_003` fallback과 Summary Callback 기반 완료 처리를 다룬다.
- 생성 Payload에는 프로젝트, 이슈 유형, 승인된 제목·Markdown 설명과 우선순위만 포함했다. 담당자·라벨·스프린트·에픽·상위 항목·상태 전환은 설정하지 않았다.
- 생성 후 상세 재조회로 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 확인했다.

## Latest independent TMI-31 review

- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c` 기준 tracked 변경과 신규 미추적 production·migration·test 파일을 함께 검토했다.
- 순차·순환 선택, 활성 Session 재사용·동시 insert 충돌 복구, legacy 완료 증거와 조건부 backfill, Summary 성공 후 완료, 선택된 `mockExamId`의 S3·Job·AI·retry·조회 전파, staging/prod 필수 인덱스 검증과 migration fail-closed 경로에서 신규 actionable finding은 확인하지 않았다.
- Controller·Request/Response DTO·`BaseResponse`에는 diff가 없고 사용자 소유권, 실제 userId 비노출, Redis/S3 Key, `retryCount`, Callback JSON과 Python AI `user_id=examId` 계약이 유지된다.
- Node syntax와 migration 49개 테스트, tracked/untracked whitespace 및 Secret 패턴 검사는 성공했다. fresh Gradle은 sandbox 제약으로 task 시작 전에 실패했으며, 2026-07-30 16:02에 현재 소스로 생성된 XML은 Java 205개, failures/errors/skipped 0개다.
- 실제 Atlas migration/index, 다중 인스턴스 동시성, Redis·S3·Python AI staging E2E는 이번 리뷰 범위에서 실행하지 않았다. 애플리케이션·migration·테스트 코드는 수정하지 않았고 Jira와 Git commit/push도 변경하지 않았다.

## Previous independent TMI-31 review

- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`의 tracked diff와 신규 미추적 파일을 함께 재검토했다.
- P1: `TMI31_APPLY=true`가 기존 main Callback과 겹치면 plan snapshot 뒤 Summary가 저장된 Session도 stale `activateIncompleteLegacy` 목록에서 `active=true`가 된다. 기존 Callback은 Session 완료 필드를 쓰지 않고 신규 Manager는 true-active 후보의 evidence를 확인하지 않으므로 구버전 writer quiescence 또는 activation/index 전 재검증이 필요하다.
- P2: migration은 explicit sequence와 ID suffix를 JavaScript integer 범위로만 검사해 Java Entity의 `Integer.MAX_VALUE`를 넘는 값을 저장할 수 있다. APPLY 전에 두 경로 모두 signed 32-bit 상한을 검증해야 한다.
- P3: 기존 WORKLOG의 `019fac7a-...` branch 기록 한 줄을 append가 아니라 수정했다. append-only 규칙에 따라 원문을 복원하고 정정은 새 항목으로 남겨야 한다.
- 외부 공개 API·DTO·`BaseResponse`, 소유권, Redis/S3 Key, `retryCount`, AI/Callback `user_id=examId` 계약은 리뷰 중 변경하지 않았다.
- tracked/untracked whitespace 검사, migration 두 파일 `node --check`와 Node 테스트 25개는 성공했다. fresh Gradle은 sandbox lock/socket 제한으로 task 시작 전에 실패했으며, 현재 소스보다 최신인 기존 XML은 Java 200개와 failures/errors/skipped 0개를 기록한다.
- 후속 사용자 지정 최종 리뷰는 AGENTS.md와 Atlassian MCP의 TMI-31 설명을 다시 읽고 요청된 11개 경로를 추적했으며, 위 세 finding 외 추가 HIGH/MEDIUM/LOW finding을 확인하지 않았다. Jira 쓰기와 application·migration·테스트 코드 수정은 수행하지 않았다.

## Previous TMI-31 code review and resolution

- merge base `b71b54bb4ff871a8e082cd6d94a34007c84b062c`의 tracked diff와 신규 미추적 파일을 함께 검토했다.
- HIGH 수정: `ExamCompletionEvidenceService`가 `exam_summaries`와 `exam_results.totalScore != null`을 동일한 완료 증거로 조회한다. legacy null active/null completedAt Session에 증거가 있으면 재사용하지 않고, 가장 이른 명시 시각·실제 BSON ObjectId 시각·Session createdAt 순으로 완료 시각을 산정해 조건부 원자 backfill한다. 시각을 얻지 못해도 활성으로 재사용하지 않는다.
- MEDIUM 수정: migration catalog 검사는 `INACTIVE`, `EMPTY_QUESTIONS`, `MISSING_ID`, `INVALID_ACTIVE`를 먼저 분류하고 assignable 문서에만 `deriveSequence`와 sequence 중복 검증을 적용한다. `mockExamId` 중복 검증은 전체 catalog 범위를 유지한다.
- migration도 Summary와 legacy totalScore 증거를 합쳐 Session당 한 번만 완료 처리하고, 가장 이른 신뢰 가능한 완료 시각과 evidence overlap·duplicate·orphan·unresolved 통계를 dry-run에 출력한다. 배정 제외 문서는 별도 목록에 표시하며 sequence/active 보정 대상에서 제외한다.
- 공개 API·DTO·`BaseResponse`, 소유권, Redis/S3 Key, `retryCount`, AI/Callback `user_id=examId` 계약은 변경하지 않았다.
- `git diff --check main --`, migration `node --check`, Node 테스트 25개와 `./gradlew clean test`가 성공했다. XML 기준 Java 테스트 200개, 실패·오류·건너뜀 0개다.

## TMI-31 implementation state

- `MockExam`에 `sequence`, `active`를 추가했다. `active=null`은 활성, `sequence=null`은 `mockExamId` 끝 숫자를 임시 sequence로 해석하며 활성·비어 있지 않은 시험지를 숫자 sequence 오름차순으로 반환한다. 유효하지 않은 sequence, 활성 sequence 중복, 전체 catalog의 null/blank/whitespace/중복 `mockExamId`는 `EXAM_5001` 설정 오류로 안전하게 실패하고 빈 시험지는 배정에서 제외한다. 단건 조회도 `List` 결과가 2개 이상이면 임의 선택하지 않는다.
- `ExamSession`에 `mockExamId`, `cycleNumber`, `active`, `completedAt`을 추가했다. 신규 세션은 사용자별 완료 횟수가 최소인 활성 시험 중 sequence가 가장 작은 시험을 선택하고 `cycleNumber=completionCount+1`, `active=true`, `completedAt=null`로 Mongo `insert`한다.
- `POST /api/v1/exams`는 현재 사용자의 재사용 가능 세션을 먼저 조회한다. `active=true`는 재사용하고 `active=false` 또는 완료 시각이 있는 세션은 제외한다. null active+null completedAt 후보는 결정적/legacy `ExamSummary` 또는 분리 이전 `exam_results.totalScore != null` 증거를 확인해 완료면 조건부 원자 backfill하고, 증거가 없는 실제 진행 중 legacy Session만 같은 `examId`로 재사용한다. 문제·가이드 Presigned GET URL은 매 호출 새로 발급하고 Redis 누락은 기존 Key/TTL로 복구한다.
- 완료 횟수는 전체 `ExamSession` Entity 목록을 읽지 않고 Mongo aggregation으로 현재 `userId`, `completedAt != null`만 `mockExamId`별 집계한다. null `mockExamId`는 `mock_exam_003`으로 그룹화한다.
- 동시 신규 생성은 `active=true` 문서에 대한 사용자별 Mongo partial unique index `uniq_exam_sessions_active_user`를 전제로 한다. 두 요청이 동시에 insert하면 한 요청만 성공하고 loser는 `DuplicateKeyException`을 500으로 노출하지 않고 승자 활성 세션을 재조회한다. staging/prod는 시작 시 이름·키·unique·partial 정의를 검증해 누락/불일치 시 fail-closed하고 local은 경고한다.
- Summary Callback은 `ExamSummary`가 신규 또는 멱등 성공으로 확인된 뒤에만 `completedAt is null` 조건 원자 update로 세션 완료 시각을 기존 UTC `Clock`에서 설정하고 `active=false`로 바꾼다. 저장 예외 전에는 완료하지 않고 중복 Callback은 최초 전이 이후 no-op이다. 문항 결과/Job 완료만으로는 세션을 완료하지 않는다.
- 신규 `QuestionGradingJob`과 `SummaryGradingJob`은 최소 필드 `mockExamId`를 저장한다. 세션의 선택값이 문제 조회, `questions/{mockExamId}/q_N.wav`, `part3_intro.wav`, 문항 AI multipart, 시험 retry 예상 문항, Summary AI JSON과 문항 상세 조회까지 전파된다. 기존 Job의 값이 없으면 세션을 조회하고 세션도 없거나 값이 null/blank이면 legacy `mock_exam_003`만 fallback한다.
- AI outbound `user_id`와 Callback `user_id`는 계속 `examId`다. Callback 저장 시 외부 `mock_exam_id`보다 세션의 canonical `mockExamId`를 사용하며 실제 사용자 UUID를 AI 서버로 보내거나 외부 응답에 추가하지 않는다.
- `scripts/mongodb/tmi-31-migrate-exam-assignment.js`와 실행 문서를 추가했다. Node entrypoint가 `MONGODB_DATABASE`를 필수 검증하고 URI의 DB와 무관하게 `getSiblingDB`로 명시 선택한다. 기본은 dry-run이며 `TMI31_APPLY=true`일 때만 Summary/legacy totalScore 증거에 따른 완료 Session backfill, assignable MockExam/Session 보정과 active unique·완료 집계·`mock_exam_id` unique 세 인덱스를 생성한다. 완료 증거·시각 충돌, 중복 ID, 여러 활성 후보와 인덱스 충돌은 쓰기 전에 중단한다.
- `POST /api/v1/exams`의 URL·Method·Request Body 없음, `CreateSessionResult` 세 필드와 `BaseResponse`를 포함한 기존 공개 API/DTO, `retryCount`, Redis Key/TTL, 제출 S3 Key, Callback JSON 계약은 변경하지 않았다.

## Previous TMI-31 finding resolution

- HIGH 수정: null/missing `active`와 null `completedAt`인 legacy 후보는 Summary 증거를 조회한다. Summary가 있으면 재사용하지 않고 ObjectId 또는 Summary Job 완료 시각을 사용해 `active=false`, `completedAt`을 기존 값이 여전히 없는 경우에만 원자 보정한다. 중복 Summary는 임의 선택하지 않고 안전하게 실패한다.
- MEDIUM 수정: `ExamAssignmentIndexValidator`가 `uniq_exam_sessions_active_user`와 `uniq_mock_exams_mock_exam_id`를 정확한 이름·순서 있는 키·unique·partial 기준으로 검증한다. staging/prod는 실패 시 기동하지 않으며 test profile은 실제 Mongo를 검사하지 않는다. 완료 집계 인덱스는 정확성 필수와 분리해 경고한다.
- MEDIUM 수정: migration은 `MONGODB_DATABASE` 누락·공백·시스템 DB를 거부하고 URI와 별개로 해당 DB를 선택하며 DB/collection/예정 변경 수를 dry-run과 apply 직전에 표시한다. URI와 자격증명은 출력하지 않는다.
- MEDIUM 수정: 완료 횟수 집계를 Mongo aggregation으로 옮기고 `idx_exam_sessions_user_completed_mock_exam`을 migration apply 대상으로 추가했다. 현재 사용자·완료 Session만 집계하며 legacy null 시험 ID fallback을 유지한다.
- MEDIUM 수정: runtime 전체 catalog와 단건 조회에서 중복/null/blank/공백 `mockExamId`를 거부하고, migration은 실제 저장 필드 `mock_exam_id`의 중복 metadata와 인덱스 충돌을 보고한 뒤 문제가 없을 때만 `uniq_mock_exams_mock_exam_id`를 생성한다.
- 추가 HIGH 수정: `ExamCompletionEvidenceService`가 Summary와 `exam_results.totalScore != null` projection을 합쳐 가장 이른 완료 시각을 결정한다. Manager는 완료 증거가 있는 legacy Session을 재사용하지 않고 조건부 원자 backfill한 뒤 기존 `completedAt` aggregation으로 한 번만 집계한다. `totalScore=null` 문항 결과는 증거에서 제외한다.
- 추가 MEDIUM 수정: migration은 assignable 여부를 sequence 해석 전에 판정한다. sequence uniqueness는 assignable 시험에만 적용하고, 전체 catalog `mockExamId` uniqueness는 그대로 유지하며 제외 문서를 임의 활성화·수정하지 않는다.
- Jira TMI-31 설명과 완료 조건은 Atlassian MCP로 읽기 전용 재조회했고 Jira 쓰기 API는 호출하지 않았다.
- migration Node 테스트 25개와 현재 소스의 `./gradlew clean test`가 성공했다. XML 기준 Java 테스트 200개, 실패·오류·건너뜀 0개이며 기존 `ExamServiceImpl` unchecked 경고만 남았다.

## TMI-31 application package map

- `ExamService`는 Controller가 의존하는 시험 유스케이스 계약이고 `ExamServiceImpl`은 사용자 소유권, S3 URL, 세션 생성·재사용, 제출·상태·결과 조회와 세 종류 Callback 저장을 조율하는 API 파사드다.
- `ExamSessionManager`는 활성 세션 재사용, 사용자별 완료 횟수·sequence 기반 신규 배정, 동시 insert 충돌 복구와 Summary 성공 뒤 세션 완료를 담당한다. `MockExamCatalogService`는 활성·비어 있지 않은 문제지와 유효한 숫자 sequence로 배정 catalog를 만든다.
- `ExamGradingService`는 Question/Summary Job 생성·완료·retry, S3 제출 존재 확인, optimistic claim, Mongo 결과 기반 전체/문항 상태 계산과 Redis projection을 담당하는 채점 상태 오케스트레이터다.
- `SummaryDispatchScheduler`는 bounded executor에서 Summary Job을 원자 claim하고 비동기 AI 전송·실패 전이를 수행하며, `GradingDispatchService`는 S3 음성 로드와 Python AI multipart/JSON HTTP 계약을 실제로 실행한다.
- `QuestionDispatchClaim`과 `SummaryDispatchClaim`은 claim 시점의 attempt·시간·`examId`·`mockExamId`를 고정하는 immutable 전송 스냅샷이고, `GradingKeys`는 결정적 Job/결과 ID, 기존 S3 제출 Key, retry 0 정규화와 `mock_exam_003` legacy fallback을 중앙화한다.
- 2026-07-29 역할 분석에서는 application 패키지 10개 파일과 Controller 호출 관계를 읽기 전용으로 확인했다. 애플리케이션·테스트 코드는 수정하지 않았고 기존 TMI-31 외부 계약과 직전 169개 전체 테스트 성공 상태를 유지한다.

## Previous completed Jira issue — TMI-25

- [`TMI-25`](https://to-teacher.atlassian.net/browse/TMI-25) — [Learning Core] 시험 단위 재채점 및 AI 채점·Callback 멱등성 보장
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 라벨: 없음
- 사용자가 승인한 제목과 Markdown 설명을 그대로 사용해 생성했다. 설명에는 기존 문항 submit·전체 상태 API 유지, 신규 시험 단위 retry API, retryCount 0 복구 규칙, 문항·요약 Job, Callback 멱등성, Redis 전체 상태, AI `Idempotency-Key`, 완료 조건과 범위 제외가 포함된다.
- 생성 Payload에는 프로젝트, 이슈 유형, 제목, 설명, 우선순위만 포함했다. 담당자·라벨·상위 항목·스프린트·에픽·상태 전환은 설정하지 않았다.
- 생성 후 상세 재조회로 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 확인했다.
- 2026-07-28 전환 전 읽기 전용 조회에서 상태 `해야 할 일`과 사용 가능한 전환 `해야 할 일`(ID `11`)·`검토 중`(`31`)·`진행 중`(`21`)·`완료`(`41`)를 확인했다.
- 사용자 요청에 따라 전환 직전 상태와 `진행 중` 전환 ID `21`의 가용성을 다시 확인한 뒤 해당 전환만 적용했다. 전환 Payload에는 다른 필드·댓글·업데이트를 포함하지 않았고 다른 Jira 이슈를 호출하지 않았다.
- 전환 후 상세 재조회에서 현재 상태 `진행 중`(상태 ID `10001`)을 확인했다.
- 구현 전 정적 분석에서 동일 submit·네 종류 Callback·11번 요약 Trigger의 중복 가능성, Redis 단일 상태와 고정 progress, Job·Clock·S3Client Bean·Mongo `@Version`·원자 claim 부재, legacy Unique Index 충돌 위험을 확인했다. 애플리케이션 구현과 Jira 변경은 수행하지 않았다.
- 사용자가 TMI-25에 한해 API 변경 금지 규칙의 제한 예외를 승인했고 `AGENTS.md`에 전용 예외를 기록했다. 신규 시험 단위 retry API·전용 DTO·Question/Summary Job·submit/Callback 멱등성·Job 기반 status 내부 처리·전체 필수 retry 0 문항 완료 요약 Trigger만 허용되며 다른 작업에는 자동 적용되지 않는다.
- 승인된 범위의 구현과 리뷰 finding 회귀 수정을 완료했다. 사용자의 종료 요청에 따라 Atlassian MCP로 전환 직전 `진행 중`과 `완료` 전환 ID `41`을 재확인한 뒤 해당 전환만 적용했다.
- 후속 상세 조회에서 상태 `완료`와 resolution `완료`를 확인했다. 댓글·다른 필드·다른 Jira 이슈는 변경하지 않았다.
- `./gradlew clean test`는 142개 테스트 모두 성공했고 `git diff --check`, 외부 API·AI/Redis/S3 계약 검색과 Secret 패턴 검색도 통과했다.

## Latest Jira creation

- 프로젝트 `TMI`에 `[Learning Core] 사용자별 모의고사 순차 배정 및 순환 제공` 제목의 `작업` 이슈를 `TMI-31`로 생성했다.
- Atlassian 메타데이터에서 프로젝트 ID `10000`, 이슈 유형 ID `10003`, 설명 필드와 우선순위 `High`(ID `2`) 지원을 확인했고 동일 제목 검색 결과는 없었다.
- 설명은 사용자별 활성 MockExam 완료 횟수와 sequence 기반 순차·순환 선택, 진행 중 활성 ExamSession 재사용, 사용자당 활성 세션 하나, 선택된 `mockExamId`의 S3·문항 조회·AI grading retry·summary 전 과정 전파, legacy null의 `mock_exam_003` fallback과 Summary Callback 기반 완료 처리를 포함한다.
- 프로젝트, 이슈 유형, 승인된 제목·Markdown 설명과 `High`만 전송했다. 담당자·라벨·스프린트·에픽·상위 항목·상태 전환은 설정하지 않았고 기본 상태 `해야 할 일`을 유지했다.

## Latest TMI-25 regression fixes

- Question/Summary dispatch는 immutable claim에 `jobId`, `dispatchAttempt`, `claimedAt`을 고정한다. HTTP 실패는 Mongo `_id + status=PROCESSING + dispatchAttempt=claimedAttempt` 조건 update만 사용하며 0건이면 이전 attempt의 늦은 실패로 무시한다.
- Feedback Callback은 결과 저장과 Question Job 완료·복구 후 모든 필수 retry 0 완료를 확인하고 Summary PENDING만 확보한다. bounded 전용 executor에 task를 넘기고 실제 Summary HTTP는 worker가 `@Version` claim에 성공한 경우에만 실행한다.
- Callback gate `ensureSummaryStartedIfReady`는 기존 FAILED 또는 stale PROCESSING Summary를 재시도하지 않는다. `retrySummaryIfEligible` 경로만 FAILED·stale PENDING/PROCESSING과 max attempts를 판정해 recovery task를 제출한다.
- AI HTTP connect/read timeout 기본값은 각각 `PT3S`/`PT30S`, Summary worker/queue 기본값은 `2`/`100`이며 모두 `app.grading` 타입 안전 설정이다. queue rejection은 Job을 변경하지 않아 PENDING 복구가 가능하다.
- submit은 Job insert 전에 retry 0의 `0/null/missing` compatible Feedback 결과를 확인하고 COMPLETED Job을 지연 복구한다. 기존 non-COMPLETED Job보다 결과를 우선해 COMPLETED로 보정하며 AI를 재호출하지 않는다.
- Azure retry 0 조회는 결정적 ID, 정확한 0, 명시적 BSON null, 필드 누락 순서다. retryCount>0은 정확한 회차만 조회하며 ObjectId와 문자열 ID를 한 정렬에서 시간순으로 비교하지 않는다.
- 실제 attempt 1 HTTP를 timeout 경계 너머까지 대기시켜 attempt 2를 claim한 뒤 attempt 1 실패를 도착시키는 Question/Summary 동시성 테스트, 중복 scheduler task 단일 dispatch, queue rejection, Callback/retry gate 분리, legacy submit 복구와 Azure null/missing 조회 테스트가 통과했다. 자체 재리뷰에서 남은 HIGH/MEDIUM finding은 확인하지 않았다.

## Previous TMI-25 code review state

- 2026-07-29에 사용자 요청으로 merge base `bc15c504b4130e011cbb476d71a37e98e1d8a862` 기준 전체 diff와 미커밋 회귀 수정까지 다시 재검증했다. 리뷰 대상 애플리케이션·테스트 코드는 수정하지 않았고 Git/Jira 쓰기 작업도 수행하지 않았다.
- P1: 시험 retry가 여러 Question의 S3 GET과 AI POST를 요청 스레드에서 직렬 실행하므로 downstream timeout 시 단일 요청이 수분간 지속되고 Tomcat 스레드 풀이 고갈될 수 있다.
- P2: 세션이 생성될 때 제공한 문항 집합을 고정하지 않고 매 status/retry/Callback gate에서 현재 `mock_exam_003`을 다시 읽어, 시험지 변경 시 진행 중 세션의 완료 기준이 바뀐다.
- P2: retry 0 Azure의 legacy BSON null·필드 누락 fallback 쿼리에 최신순 정렬이 없어 pre-idempotency 중복 문서 중 임의 결과를 반환할 수 있다.
- P2: staging/prod localhost 차단이 축약형 IPv6만 열거해 `[0:0:0:0:0:0:0:1]`, IPv4-mapped IPv6 같은 loopback 표기를 허용한다.
- P2: E2E의 단일 logout은 Refresh 재사용 탐지가 이미 폐기한 Token을 사용해 logout이 no-op이어도 통과한다.
- P2: E2E의 `logout-all`은 활성 Session을 하나만 만들어 단일 logout 구현도 통과할 수 있다.
- 정적 검증인 `git diff --check bc15c504b4130e011cbb476d71a37e98e1d8a862`와 E2E `bash -n`은 성공했다. `./gradlew clean test --no-daemon`은 사용자 Gradle home lock 쓰기 제한으로, cache를 `/tmp`에 복제한 offline 재시도는 sandbox의 file-lock contention socket 제한으로 시작되지 않았다. 기존 XML 결과는 현재 소스로 컴파일된 142개 테스트와 실패·오류·건너뜀 0개를 기록한다.

## Previous completed Jira issue

- [`TMI-14`](https://to-teacher.atlassian.net/browse/TMI-14) — [Learning Core] 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리
- 프로젝트: `TMI` (ID `10000`)
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 설정됨 (개인 식별 정보는 기록하지 않으며 이번 작업에서는 변경하지 않음)
- 타입 안전 `AuthMode`, staging/prod Startup Validator, Legacy profile 격리와 미사용 HMAC/JJWT/`JWT_SECRET_KEY` 제거를 구현했고, 사용자가 PR 병합과 테스트 성공을 확인했다.
- 전환 직전 상태는 `진행 중`(상태 ID `10001`)이었고 `완료` 전환 ID `41`이 사용 가능했다.
- 사용자 요청에 따라 TMI-14에 전환 ID `41`만 전송했다. 전환 Payload에 다른 필드·업데이트·댓글을 포함하지 않았고 다른 Jira 이슈를 수정하지 않았다.
- 후속 상세 조회에서 상태 `완료`와 resolution `완료`를 확인했다. resolution은 완료 전환 워크플로가 자동으로 설정했으며 별도 필드 수정으로 지정하지 않았다.
- Jira 완료 댓글은 등록하지 않았다.

## Earlier completed Jira issue

- [`TMI-11`](https://to-teacher.atlassian.net/browse/TMI-11) — [Integration] Identity·Learning Core E2E 인증 테스트 및 JWT 계약 확정
- 이슈 유형: `작업` (ID `10003`)
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 우선순위: `High` (ID `2`)
- 담당자: 미지정
- 생성 시각: `2026-07-28T12:30:27.701+0900`
- Identity 회원가입·로그인부터 Learning Core 시험 소유권, 실패 Token, Refresh Token Rotation·로그아웃, 공개 AI Callback, Python AI `user_id = examId` 계약까지 실제 두 서버 E2E로 검증하는 작업이다.
- 완료 댓글 ID `10002`에 구현 파일, 자동화 범위, JWT 계약, 정적·Gradle 테스트 결과, 실제 서버 E2E 미실행과 수동 DB 확인 잔여 항목을 기록했다.
- 사용자 요청에 따라 완료 전환 ID `41`을 실행하고 상태와 resolution을 재조회해 확인했다.
- 로컬 구현과 정적 검증, 두 저장소 전체 테스트는 완료했다. 실제 Identity 8081과 Learning Core 8080이 실행 중이지 않아 두 서버 E2E 실행과 직접 `ExamSession.userId` 확인은 후속 운영 검증으로 남아 있다.

## Related completed Jira issue

- `TMI-10` — [Learning Core] Identity JWKS 기반 JWT 인증 연동
- Jira 상태: `완료` (상태 ID `10003`, resolution `완료` ID `10000`)
- 2026-07-28 테스트 결과와 PR #8을 Jira 댓글 ID `10001`로 기록했다.
- 완료 전환 ID `41` 실행 후 상태와 resolution을 재조회해 확인했다.

## Completed

- 기존 웹 POC 백엔드에서 앱용 Learning Core 분리
- trial API와 terminate API 제거
- `ExamSession`에 `examId`, 실제 `userId`, `createdAt` 저장
- `CurrentUserProvider` 추상화와 `LegacyCurrentUserProvider` 유지
- `ExamResult.userId` 저장과 시험 소유권 검증 유지
- Feedback Callback의 `examId -> ExamSession -> 실제 userId` 매핑 유지
- Spring Security OAuth2 Resource Server 의존성 추가
- `APP_AUTH_MODE` 기반 Legacy/JWT 조건부 보안 구성 추가
- 기본값 `legacy`에서 기존 전체 `permitAll` 웹 흐름 유지
- `jwt` 모드에서 Identity JWKS를 명시적으로 사용하는 RS256 검증 구성
- issuer, audience, exp, nbf, UUID subject 검증 구성
- `JwtCurrentUserProvider`에서 JWT `sub`를 실제 사용자 UUID로 변환
- JWT 모드의 사용자용 API 인증 강제와 Callback·Swagger·OpenAPI·health 공개 경로 구성
- Security 계층의 401/403을 기존 `BaseResponse` JSON 구조로 반환
- 테스트용 JWKS endpoint와 합성 RSA 키를 이용한 JWT Resource Server 통합 테스트 추가
- PR [#8](https://github.com/Too-Much-I/app-back-end-learning-core/pull/8) merge 완료 및 CodeRabbit 체크 성공 확인
- Jira `TMI-10` 테스트·PR 댓글 등록과 완료 처리
- AI 문항 피드백을 요청한 `examId + questionNumber + retryCount` 범위의 최신 `_id` 문서로 조회하도록 보완
- 종합 피드백을 문항별 `exam_results`와 분리된 `exam_summaries` 컬렉션에 저장하도록 보완
- 종합 피드백 조회 시 `exam_summaries`의 최신 `_id` 문서를 우선하고, 분리 전 `exam_results`의 최신 종합 문서를 fallback하도록 보완
- PR #9로 최신 피드백 조회·종합 피드백 저장소 분리 변경을 `main`에 merge
- Identity·Learning Core E2E 인증 통합 테스트 후속 Jira Payload 초안과 지원 필드 검증 완료
- Learning Core 운영 JWT 모드 강제 및 Legacy/HMAC 인증 정리 후속 Jira Payload 초안과 지원 필드 검증 완료
- Jira `TMI-14` 생성과 승인된 제목·설명·유형·우선순위·기본 상태 재조회 검증 완료
- Jira `TMI-14` 생성 turn의 전용 Stop Hook marker 기록 완료
- Jira `TMI-14` 현재 상태와 가능한 전환의 읽기 전용 조회 완료
- Jira `TMI-14`를 다른 필드 변경 없이 `진행 중`으로 전환하고 재조회 검증 완료
- Jira `TMI-11` 생성과 제목·설명·유형·상태·우선순위 재조회 검증 완료
- Jira `TMI-11` 작업 결과 댓글 ID `10002` 등록과 완료 처리
- `scripts/e2e/auth-integration-test.sh`에 실제 두 서버용 JWT 인증 E2E 자동화 추가
- `scripts/e2e/README.md`에 실행 전제, 환경변수, 정리 정책, 수동 DB 검증과 운영 실행 금지 안내 추가
- `docs/contracts/identity-learning-jwt.md`에 RS256·`kid`·Claim·JWKS·사용자 식별·AI·로그아웃 계약 확정
- Jira TMI-14 제한 예외를 `AGENTS.md`에 명시하고 다른 작업·경로·외부 계약으로 확대되지 않음을 재검토
- `AuthMode.LEGACY`·`AuthMode.JWT`와 `AuthProperties`를 추가하고 소문자 `legacy`·`jwt`만 허용하도록 설정 바인딩 검증
- staging/prod에서 JWT 모드와 비로컬 issuer·JWKS URL·audience를 강제하고 설정 형식만 검사하는 `AuthStartupValidator` 추가
- Legacy Provider와 Legacy SecurityFilterChain을 `local`·`test` profile로 제한하고 staging/prod 강제 등록 탐지 추가
- 미사용 `JwtAuthenticationFilter`, `JwtTokenProvider`, JJWT 의존성, `jwt.secret`, `JWT_SECRET_KEY` 설정 제거
- 기존 OAuth2 Resource Server, RS256·issuer·audience·timestamp·UUID subject 검증과 보호·공개 경로 유지
- README와 JWT 계약·E2E 실행 문서에 local/test Legacy와 staging/prod JWT 환경 규칙 반영
- 사용자가 TMI-14 PR 병합과 테스트 성공을 확인한 뒤 Jira `TMI-14`를 다른 필드·댓글 변경 없이 `완료`로 전환하고 상태와 자동 resolution을 재조회해 검증 완료
- Learning Core 시험 단위 재채점과 AI 채점·Callback 멱등성 보장 Jira Payload 초안 작성 및 TMI 생성 필드·`High` 지원 여부 검증 완료. Jira 이슈는 생성하지 않음
- 승인된 채점 복구·멱등성 Payload로 Jira `TMI-25`를 `작업`, `High`, 기본 상태 `해야 할 일`로 생성하고 제목·설명·미지정 담당자·빈 라벨을 재조회해 검증 완료
- Jira `TMI-25` 구현 전 제출·Callback·요약·상태·소유권·MockExam·S3·Mongo·테스트 구조 정적 분석과 Question/Summary Job·retry·Callback 멱등성·legacy 호환 설계 완료
- Jira `TMI-25`에만 적용되는 신규 retry API와 채점·Callback 멱등성 구현의 제한적 호환성 예외를 `AGENTS.md`에 명시
- Jira `TMI-25`의 시험 단위 retry API, Question/Summary Job, submit·Callback·요약 dispatch 멱등성, Job 기반 전체 상태 산정과 legacy 결과 지연 복구 구현 완료
- Question/Summary AI 요청에 안정적인 `Idempotency-Key`를 추가하고 S3 `HeadObject` 기반 누락/복구 분기, 양수 timeout·최소 attempt 설정 검증과 UTC `Clock` Bean 추가
- TMI-25 집중·회귀 테스트와 전체 `./gradlew clean test` 126개 성공, 외부 인프라 호출 없음

## TMI-25 implementation state

- 신규 `POST /api/v1/exams/{examId}/grading/retry`는 Request Body 없이 기존 `BaseResponse`로 `examId`, `overallStatus`, retried/waiting/missing 문항 번호와 `summaryAction`을 반환하며 기존 소유권 검증을 먼저 수행한다.
- `QuestionGradingJob`의 결정적 `_id`는 `question:{examId}:{questionNumber}:{retryCount}`, `SummaryGradingJob`은 `summary:{examId}:v1`이다. 두 문서 모두 상태·dispatch 횟수·필수 시각·실패 정보와 Mongo `@Version`을 가진다.
- submit은 결정적 Job `insert`에 성공한 최초 요청만 `PROCESSING`으로 optimistic claim하고 AI를 호출한다. PENDING/PROCESSING/COMPLETED 및 기존 FAILED Job의 동일 submit은 새 요청을 만들지 않는다.
- 시험 retry는 `mock_exam_003`의 `MockExam.questions`에서 예상 문항을 읽고 retryCount 0만 처리한다. fresh PENDING/PROCESSING은 대기하고 stale PENDING/PROCESSING 또는 시도 한도 미만 FAILED만 optimistic claim 후 재전송한다.
- Job이 없으면 기존 S3 Key에 `HeadObject`를 수행한다. 404만 미제출로 분류하고 객체가 있으면 PENDING Job을 복구해 dispatch하며 403·timeout·인프라 오류는 미제출로 오인하지 않는다.
- Feedback·SpeechAce·Azure·전체 요약 결과는 논리 키의 legacy 존재 여부를 먼저 확인하고 결정적 `_id`로 `insert`한다. 동시 중복의 `DuplicateKeyException`은 멱등 성공으로 처리하며 기존 결과에 Unique Index나 자동 마이그레이션을 적용하지 않는다.
- Feedback Callback은 Question Job을 COMPLETED로 전이하거나 legacy 시험의 누락 Job을 복구한 뒤 매번 요약 gate를 확인한다. 11번 특별 Trigger는 제거했고 모든 필수 retryCount 0 결과 또는 COMPLETED Job이 있어야 Summary Job을 한 번 claim한다.
- 시험 retry는 문항 작업이 남지 않았을 때만 Summary Job을 처리한다. 요약 fresh PROCESSING은 `WAITING`, stale PROCESSING/FAILED는 재전송, 완료는 `ALREADY_COMPLETED`, Job 없음은 생성·dispatch한다.
- 전체 상태는 retryCount 0 결과와 Question/Summary Job을 일괄 조회해 산정하고 기존 `exam:status:{examId}`와 1시간 TTL에 캐시한다. 기존 status DTO와 `progressPercent=60`은 프론트 계약을 위해 유지한다.
- AI multipart/JSON Body와 `user_id = examId`는 유지하고 Header만 `question:{examId}:{questionNumber}:{retryCount}` 또는 `summary:{examId}:v1`로 추가했다.
- 설정 기본값은 pending `PT1M`, processing `PT3M`, max dispatch attempts `3`이며 Duration 양수와 attempt 1 이상을 검증한다. 신규 시간 로직은 UTC `Clock` Bean만 사용한다.

### Approved limited exception

- TMI-25에 한해 `POST /api/v1/exams/{examId}/grading/retry`, 해당 API 전용 DTO, Question/Summary Job, 기존 submit·Callback의 멱등 내부 처리와 모든 필수 retry 0 문항 완료 기반 요약 Trigger를 구현할 수 있다.
- 기존 status API는 URL·Method·기존 필드를 유지하면서 Job 기반으로 내부 상태 산정을 변경할 수 있다.
- 기존 API URL·Method·Request Parameter·Response 필드, retryCount 의미, AI `user_id = examId`, Redis/S3 Key, 소유권 검증은 변경할 수 없다.
- retryCount>0 사용자 새 녹음의 시험 전체 복구, 프론트 문항 목록 전달, 별도 외부 summary retry API는 허용되지 않는다.
- 이 예외는 TMI-25 전용이며 다른 Jira나 후속 작업에 승계되지 않는다.

## Authentication modes

### Legacy

- `APP_AUTH_MODE=legacy` 또는 모드 설정 누락은 `AuthMode.LEGACY`로 해석되지만 `local`·`test` profile에서만 활성화된다.
- active profile이 없거나 staging/prod에서 Legacy 모드를 선택하면 시작에 실패한다.
- `LegacyCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- JWT Resource Server와 `JwtDecoder`는 등록하지 않는다.
- Identity 서버와 Authorization 헤더 없이 기존 API를 호출할 수 있다.

### JWT

- `APP_AUTH_MODE=jwt`일 때만 활성화된다.
- `JwtCurrentUserProvider`만 `CurrentUserProvider`로 등록한다.
- `IDENTITY_JWK_SET_URI`를 직접 사용하므로 OIDC discovery를 전제하지 않는다.
- RS256 서명, issuer, audience, exp, nbf, UUID subject를 검증한다.
- JWT `sub`를 정규화된 UUID 문자열로 반환해 `ExamSession.userId`와 소유권 검증에 사용한다.

## TMI-14 Startup validation

- `APP_AUTH_MODE`는 소문자 `legacy`와 `jwt`만 허용한다. 빈 값, 대문자 표기, 오타와 지원하지 않는 값은 안전한 오류로 시작 실패하며 Legacy로 fallback하지 않는다.
- JWT 모드는 모든 profile에서 issuer·JWKS URL·audience의 존재와 HTTP(S) URI 형식을 검증한다.
- 공통 설정에는 Identity 기본값을 두지 않고 `application-local.yml`에서만 localhost issuer·JWKS URL과 개발 audience 기본값을 제공하므로 staging/prod 누락이 local 기본값으로 숨지 않는다.
- staging/prod는 JWT 모드만 허용하고 localhost·loopback Identity URL과 placeholder audience를 거부한다.
- 검증 단계에서 Identity 또는 JWKS endpoint로 네트워크 요청을 보내지 않는다.
- staging/prod에서 `LegacyCurrentUserProvider` 또는 `legacySecurityFilterChain`이 강제로 등록되면 시작 실패한다.
- local/test에서는 명시적으로 Legacy를 사용할 수 있고 Identity 연결 없이 기존 웹 호환 흐름이 동작한다.

## Public paths in JWT mode

- `/api/v1/exams/callback/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs`와 `/v3/api-docs/**`
- `/actuator/health`와 하위 경로. 현재 Actuator 의존성과 Health 설정이 있어 endpoint가 생성되며 상세 정보는 노출하지 않는다.

## Protected paths in JWT mode

- 위 공개 경로를 제외한 모든 요청은 authenticated다.
- 시험 생성, 상태·종합·문항 결과 조회, 업로드 URL, 음성 제출, 문항 Polling 등 기존 사용자용 시험 API가 포함된다.
- `examId`를 받는 사용자용 API의 기존 `ExamSession.userId == CurrentUserProvider.getCurrentUserId()` 소유권 검증을 유지한다.

## TMI-11 E2E automation

- `IDENTITY_BASE_URL` 기본값은 `http://localhost:8081`, `LEARNING_CORE_BASE_URL` 기본값은 `http://localhost:8080`이다.
- 스크립트는 Identity health·JWKS, 두 사용자 회원가입/로그인, JWT Claim, Learning Core 401/403·시험 소유권, 잘못된 Token, Refresh Rotation·재사용 탐지, 단일·전체 로그아웃, 공개 Feedback Callback을 단계별 검증한다.
- Access/Refresh Token과 Token 또는 URL을 포함할 수 있는 전체 응답은 출력하지 않고, 실패 시 단계·HTTP 상태·최상위 안전 필드만 출력한다.
- 임시 파일은 제한된 권한의 임시 디렉터리에 두고 `trap`으로 삭제한다.
- 만료·잘못된 issuer·잘못된 audience Token은 공개 API로 안전하게 생성하지 않고 기존 `JwtSecurityIntegrationTest` 검증을 사용한다.
- 사용자·시험 삭제 API가 없으므로 계정과 시험 문서는 자동 삭제하지 않는다. 기본 모드에서는 남은 Refresh Session만 로그아웃하며 직접 DB 검증은 수동 항목이다.

## Latest feedback lookup assessment

- Azure retry 0 문항 피드백은 신규 결정적 ID, 정확한 `retryCount=0`, legacy BSON null, legacy 필드 누락 순서로 조회한다. retryCount>0은 결정적 ID와 정확한 회차만 사용한다.
- Azure의 null과 missing은 별도 Mongo 쿼리로 구분하며 ObjectId와 결정적 문자열 `_id`를 한 정렬에서 시간순으로 간주하지 않는다.
- AI 문항 피드백인 `ExamResult`도 `examId + questionNumber + retryCount` 조건에 `OrderByIdDesc`를 적용한 Repository 단건 조회로 최신 문서를 선택한다.
- 0회차 조회는 기존 `retryCount=null` 문서를 0으로 해석하던 호환성을 유지하기 위해 `retryCount in [0, null]` 조건을 사용한다.
- 문항 피드백 API는 클라이언트가 전달한 `retryCount` 회차를 조회하며 가장 큰 retryCount를 자동 선택하지 않는다.
- 신규 종합 피드백은 같은 MongoDB 연결의 별도 `exam_summaries` 컬렉션에 저장하고 `examId + OrderByIdDesc`로 최신 문서를 조회한다.
- `exam_summaries`가 비어 있으면 분리 전 `exam_results`에서 `totalScore != null`인 최신 `_id` 문서를 조회해 기존 데이터를 계속 제공한다.

## Important contracts

- JWT `sub`는 실제 `userId`다.
- Access Token은 RS256이며 Header의 `kid`가 Identity JWKS Public Key를 선택한다.
- JWT issuer는 환경별 Identity 설정값이고 `scope`는 공백 구분 문자열이다.
- JWT audience는 `tosunsaeng-learning-core`다.
- Python AI 요청의 `user_id`는 계속 `examId`다.
- AI Callback의 `user_id`도 `examId`로 해석한다.
- 실제 `userId`를 Python AI 서버로 보내지 않는다.
- 클라이언트 Request Body, Path, Query, Response DTO에 `userId`를 추가하지 않는다.
- 기존 API URL·Method·Parameter·DTO·`BaseResponse`·`retryCount` 계약을 유지한다.
- TMI-25에서 허용된 신규 API는 `POST /api/v1/exams/{examId}/grading/retry` 하나뿐이며 Request Body와 별도 summary retry API가 없다.
- 기존 status 응답 필드와 `progressPercent=60`, Redis `exam:status:{examId}`·1시간 TTL을 유지한다.
- 기존 Redis Key·TTL, S3 Presigned URL·Object Key, 음성 제출·Polling 흐름을 유지한다.
- AI Body 계약은 그대로 두고 `Idempotency-Key` Header만 Question/Summary 논리 키로 추가한다.
- 종합 피드백 저장소 분리는 MongoDB 연결·database 설정을 추가하지 않고 컬렉션만 `exam_summaries`로 분리했다.
- 운영 앱에서는 Legacy 모드를 금지한다.
- `logout`과 `logout-all`은 Refresh Session을 폐기하지만 기존 Access Token의 즉시 무효화를 보장하지 않는다.

## Test status

- 사용자 지정 최종 재검증에서 `git diff --check`와 `node --check scripts/mongodb/tmi-31-migrate-exam-assignment.js`가 종료 코드 0, `./gradlew clean test`가 `BUILD SUCCESSFUL`로 완료됐다.
- TMI-31 최신 finding 수정 후 정확한 `./gradlew clean test`가 성공했다: Java 200개 테스트, 실패·오류·건너뜀 0개.
- migration은 `node --check`와 `node --test scripts/mongodb/tmi-31-migrate-exam-assignment.test.js` 25개가 성공했다. DB 필수 선택, 환경 DB 우선, 시스템 DB 거부, Summary와 legacy totalScore 완료 증거/backfill, assignable 선판정, 제외 catalog, 중복 Summary/ID, 인덱스 필드와 URI 비출력을 검증했다.
- TMI-31 집중 테스트에서 완료 이력별 sequence 선택과 cycle 증가, 비활성·빈 시험 제외, legacy sequence fallback, 중복·해석 불가 sequence 실패와 다른 사용자 이력 격리를 확인했다.
- 진행 중 세션 재사용 시 같은 `examId`와 새 Presigned URL, Redis 누락 복구, 동시 insert unique 충돌 시 승자 세션 재조회와 활성 세션 1개 유지를 검증했다.
- Summary 저장 성공 뒤 원자적 세션 완료, 중복 Callback no-op, 저장 실패·문항 완료만으로는 미완료임을 검증했다.
- 선택된 `mockExamId`가 문제 조회·S3 문제 음성·Question/Summary Job·문항/요약 AI 요청·시험 retry 예상 문항·상세 결과 조회까지 전파되고 legacy Session/Job은 세션 또는 `mock_exam_003`으로 fallback함을 확인했다.
- `POST /api/v1/exams` Request Body 없음, 기존 `CreateSessionResult`·`BaseResponse`, AI `user_id=examId` 계약이 유지되는 회귀 테스트가 성공했다.
- migration 스크립트는 기본 dry-run이고 명시적 `TMI31_APPLY=true`에서만 write 함수를 실행하도록 검증했다. 이 환경에는 `mongosh`와 실제 DB가 없어 실제 staging dry-run/apply는 수행하지 않았다.
- TMI-25 finding 집중 테스트가 성공했다. 실제 Atlas·S3·Redis·Python AI 서버는 호출하지 않고 Repository, S3Client, RestTemplate과 executor 경계를 Mockito/단위 테스트로 검증했다.
- Question/Summary attempt 1 HTTP를 timeout까지 대기시킨 뒤 attempt 2를 claim하고 attempt 1 실패를 늦게 도착시켜 최신 PROCESSING/attempt 2, null `failedAt`·`failureReason`을 확인했다.
- Callback Summary gate의 FAILED/stale PROCESSING 비재시도, grading retry의 recovery scheduling, 중복 task 단일 HTTP, queue rejection PENDING 유지, HTTP timeout의 claimedAttempt 조건 실패 전이를 확인했다.
- legacy Feedback `retryCount=null/0`과 Job 부재·기존 FAILED Job submit 복구, Azure null/missing retry 0 조회와 retry 1 격리, executor 크기·queue와 connect/read timeout 설정을 확인했다.
- TMI-25 집중 테스트에서 최초·반복·동시 submit, 상태/timeout/attempt별 시험 retry, S3 HeadObject 404·403, retryCount>0 제외와 concurrent claim을 검증했다.
- 네 Callback의 결정적 ID·중복 1개 저장, legacy null retry 결과와 누락 Job 복구, 11번 단독 요약 금지, 전체 필수 문항 완료 후 요약 1회와 요약 timeout/FAILED retry를 검증했다.
- AI `user_id = examId`, 기존 multipart/summary Body, 안정적인 두 `Idempotency-Key`, 신규 API의 Request Body 없음·기존 BaseResponse, status `progressPercent=60`, 소유권 검증을 확인했다.
- AuthMode의 legacy/JWT 변환, 누락 시 local Legacy 기본값, 빈 값·대문자·오타 실패 검증 성공
- local/test Legacy 성공, profile 없는 Legacy와 staging/prod Legacy 실패, staging/prod 정상 JWT 설정 성공 검증
- staging/prod issuer·JWKS URL·audience 누락, URI 형식 오류, localhost·loopback, placeholder audience 실패 검증
- local/test Legacy Provider 등록과 staging/prod 미등록, 강제 Legacy Provider·FilterChain 등록 실패 검증
- Legacy/JWT 모드별 `CurrentUserProvider`, `SecurityFilterChain`, `JwtDecoder` 단일 등록 검증
- HMAC 두 클래스 부재, JJWT 의존성과 `JWT_SECRET_KEY`·`jwt.secret` 활성 설정 부재 검증
- 기본 Legacy 모드, 무인증 Legacy API 접근, Legacy/JWT 빈 상호 배타 등록 검증 성공
- 테스트용 JWKS HTTP endpoint를 통한 유효 RS256 Token 시험 생성과 `ExamSession.userId` 저장 검증 성공
- 동일 사용자 접근 성공, 다른 사용자 접근 BaseResponse 403 검증 성공
- Token 없음, 잘못된 서명, 만료, 미래 nbf, 잘못된 issuer·audience, UUID가 아닌 sub의 BaseResponse 401 검증 성공
- AI Callback 무인증 접근과 Callback `user_id = examId` 흐름 검증 성공
- 기존 테스트에서 AI multipart/summary 요청의 `user_id = examId`, 외부 userId 미노출, Callback 실제 userId 매핑 검증 성공
- `git diff --check` 성공
- 실제 Identity 프로세스·Atlas·AWS·Redis·Python AI 서버는 테스트에서 호출하지 않았다.
- Jira 완료 처리 작업에서는 애플리케이션 코드를 변경하지 않아 테스트를 다시 실행하지 않았고, 직전 구현 작업의 53개 전체 성공 결과를 댓글에 기록했다.
- 최신 문항 피드백 조회 여부 분석 작업에서는 코드를 변경하지 않아 테스트를 실행하지 않았다.
- 같은 문항·retryCount의 구·신규 `ExamResult`가 함께 있을 때 최신 결과를 응답하는 테스트와 0회차 null 호환 조회 검증 성공
- 종합 Callback이 `ExamSummary`만 저장하고 `ExamResult`에는 저장하지 않는지, `ExamSession.userId` 매핑과 Redis 완료 상태가 유지되는지 검증 성공
- 최신 `exam_summaries` 조회와 새 컬렉션이 비어 있을 때 최신 legacy `exam_results` 종합 문서 fallback 검증 성공
- Atlassian MCP에서 TMI 프로젝트, `작업` 유형, 설명 필드와 `High` 우선순위 지원 여부를 읽기 전용으로 확인했다. 애플리케이션 코드를 변경하지 않아 이번 초안 작업에서는 Gradle 테스트를 다시 실행하지 않았다.
- Atlassian MCP에서 운영 JWT 보안 정리용 TMI 생성 권한과 `작업`·설명·`High` 필드를 재검증하고 동일 제목 중복이 없음을 확인한 뒤 `TMI-14`를 생성했다. 생성 후 승인된 제목·설명, `작업`, `High`, 기본 상태 `해야 할 일`, 담당자 미지정과 빈 라벨을 재조회했다. 초안 및 생성 turn의 필수 marker가 각각 정확히 한 번 존재하고 `git diff --check`가 성공했다. 애플리케이션 코드는 변경하지 않아 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-14`의 현재 상태 `해야 할 일`과 사용 가능한 전환 `해야 할 일(11)`·`진행 중(21)`·`검토 중(31)`·`완료(41)`를 직접 조회했다. Jira 변경 API와 애플리케이션 코드는 호출·수정하지 않아 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-14`의 `진행 중` 전환 ID `21`을 실행하고 상태 ID `10001`을 후속 재조회했다. 전환 Payload에는 다른 필드·댓글·업데이트가 없었고 다른 Jira 이슈를 수정하지 않았다. 애플리케이션 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.
- 사용자 확인 기준 TMI-14 PR 병합과 테스트가 성공했다. Atlassian MCP로 `진행 중` 상태와 사용 가능한 `완료` 전환 ID `41`을 확인한 뒤 TMI-14에 해당 전환만 실행했고, 후속 조회에서 상태 `완료`(ID `10003`)와 워크플로가 자동 설정한 resolution `완료`(ID `10000`)를 확인했다. 애플리케이션 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다.
- TMI-14 구현 전 `AGENTS.md`, CURRENT_STATE와 Jira 설명·완료 조건을 대조했다. “JWT 인증 강제” 금지와 staging/prod JWT 모드 강제 요구의 충돌로 구현을 시작하지 않았고, 코드 변경이 없어 인증 모드 테스트와 Gradle 테스트를 실행하지 않았다.
- Atlassian MCP로 `TMI-11`을 생성한 뒤 제목·설명·프로젝트·이슈 유형·상태·우선순위를 재조회해 승인된 Payload 반영을 확인했다. 애플리케이션 코드는 변경하지 않았다.
- Stop Hook 보완 기록의 필수 marker 단일 존재와 `git diff --check`를 검증했다.
- TMI-11 정적 검증: `bash -n scripts/e2e/auth-integration-test.sh`, JWKS/Claim jq filter 샘플, 비대화형 비밀번호 누락 오류, `git diff --check` 성공
- ShellCheck는 로컬에 설치돼 있지 않아 자동 설치하거나 실행하지 않았다.
- Learning Core `./gradlew clean test` 성공: 56개, 실패·오류·건너뜀 0개. 기존 `ExamServiceImpl` unchecked 경고만 남았다.
- Identity 저장소 `./gradlew clean test` 성공: 138개, 실패·오류·건너뜀 0개. Identity 소스와 추적 파일은 변경하지 않았다.
- 기본 8081/8080 포트 모두 연결되지 않아 실제 E2E 스크립트는 실행하지 않았다.
- 이번 Jira Payload 초안 작업에서는 Atlassian MCP로 TMI 생성 권한, `작업` 유형, 설명과 `High` 우선순위 지원 및 동일 제목 후보 부재를 읽기 전용으로 확인했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- Atlassian MCP로 `TMI-25`를 생성한 뒤 제목·설명·프로젝트·유형·우선순위·기본 상태·담당자·라벨을 재조회했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- Atlassian MCP로 `TMI-25`의 현재 상태와 사용 가능한 전환을 읽기 전용으로 조회했다. 현재 상태는 `해야 할 일`이고 전환 `11`·`31`·`21`·`41`이 모두 사용 가능했다. Jira 변경 API와 애플리케이션 코드를 호출·수정하지 않아 `./gradlew clean test`는 실행하지 않았다.
- Atlassian MCP로 TMI-25의 전환 직전 상태와 `진행 중` 전환 ID `21`을 재확인한 뒤 전환 ID만 적용하고, 후속 조회에서 상태 ID `10001`을 확인했다. 애플리케이션 코드 변경이 없어 `./gradlew clean test`는 실행하지 않았다.
- TMI-25 구현 전 분석에서는 관련 소스·설정·테스트와 Jira 설명·완료 조건을 정적으로 확인하고 `git diff --check`를 실행했다. 애플리케이션 구현 변경이 없어 `./gradlew clean test`는 실행하지 않았다.

## HMAC cleanup

- 저장소 전수 검색에서 `JwtAuthenticationFilter`와 `JwtTokenProvider`는 Bean·FilterChain·비즈니스 코드에서 사용되지 않고 서로만 참조함을 확인했다.
- 두 HMAC 클래스와 전용 JJWT API·runtime 의존성을 삭제했다.
- `application.yml`과 테스트 설정에서 `jwt.secret`·`JWT_SECRET_KEY`를 제거했으며 공유 HMAC Secret은 더 이상 필요하지 않다.
- 활성 런타임 소스·설정·빌드에서 HMAC 클래스, `addFilterBefore`, JJWT와 공유 Secret 잔여 사용처가 없음을 확인했다.
- JWT 인증 책임은 기존 Identity JWKS 기반 OAuth2 Resource Server에만 남아 있다.

## Known risks

- 운영 배포 전에 `scripts/mongodb/tmi-31-migrate-exam-assignment.js`를 먼저 dry-run으로 실행해 중복 sequence, 여러 legacy 활성 세션과 호환되지 않는 기존 인덱스를 해소한 뒤 `TMI31_APPLY=true`로 사용자당 활성 세션 partial unique index를 설치해야 한다. 인덱스가 없으면 다중 인스턴스 동시 요청의 단일 활성 세션 보장이 완성되지 않는다.
- legacy `active` 누락/null이면서 `completedAt`도 없는 세션이 사용자당 여러 개면 런타임은 최신 세션을 선택하고 경고하지만 migration apply는 운영자 조정 전 중단한다. 자동 데이터 migration은 의도적으로 없다.
- 활성 세션 재사용 시 해당 `MockExam`이 삭제됐거나 문제가 비어 있으면 안전하게 설정 오류로 실패한다. 진행 중 시험의 문항 구성을 운영 중 변경하지 않는 정책이 필요하다.
- TMI-31 테스트는 실제 Atlas·Redis·S3·Python AI 서버를 호출하지 않았다. partial unique index 충돌, Presigned URL과 AI multipart/JSON의 실제 인프라 연동은 staging smoke test가 필요하다.
- Summary 문서 insert와 ExamSession 완료 update는 서로 다른 Mongo 연산이므로 둘 사이 프로세스 중단 window가 남는다. Summary가 저장된 Callback은 멱등 재전달되면 세션 완료가 복구되므로 Python AI/인프라의 Callback 재시도 정책을 staging에서 확인해야 한다.
- Jira `TMI-10`은 사용자 요청에 따라 완료 처리됐지만 실제 Identity와 Learning Core 로컬 E2E는 수행하지 않았다. 테스트용 JWKS 경계 통합 검증으로 대체한 상태다.
- 배포 환경에서 JWT 모드를 활성화하기 전에 실제 Identity issuer·JWKS 주소와 audience 설정을 확인해야 한다.
- Nimbus 기본 clock skew가 적용되므로 exp와 nbf 경계에는 표준 허용 오차가 있다.
- JWKS key rotation과 Identity 장애 시 캐시 동작은 실제 환경에서 별도 점검이 필요하다.
- AI Callback은 의도대로 공개 상태이며 서비스 간 인증은 범위 밖이다.
- `APP_AUTH_MODE`는 소문자 `legacy` 또는 `jwt`만 허용되며 잘못된 값은 시작 실패한다.
- 최신 저장 순서는 MongoDB가 자동 생성하는 `_id` 내림차순을 기준으로 판단한다.
- 기존 `exam_results`의 종합 문서는 삭제·이관하지 않고 읽기 fallback으로 유지한다.
- 물리적으로 별도 MongoDB database나 클러스터를 요구한다면 별도 연결 설정과 운영 값이 추가로 필요하다. 현재 구현은 같은 database 내 컬렉션 분리다.
- 데이터가 커지면 `exam_summaries`의 `examId + _id` 조회용 복합 인덱스를 운영 환경에서 검토해야 한다.
- TMI-11 스크립트 구현은 완료했지만 실제 Identity 8081과 JWT 모드 Learning Core 8080이 기동되지 않아 실서버 E2E 결과는 아직 없다.
- Jira 완료 조건의 `ExamSession.userId == JWT sub` 직접 DB 비교는 MongoDB 자격증명을 스크립트에 넣지 않기 위해 수동 검증으로 남겼다. 소유권 200/403 시나리오는 API 경계에서 간접 검증한다.
- AI Callback은 사용자 JWT 없이 공개 상태이며 서비스 간 인증은 아직 없다.
- 사용자·시험 삭제 API가 없어 로컬 E2E 계정과 시험 문서는 테스트 DB에 남으며 운영자 정리가 필요하다.
- Startup Validator는 설정 형식과 로컬 URL 사용 여부만 확인하며 실제 staging/prod Identity·JWKS 네트워크 도달성은 배포 전 별도 확인이 필요하다.
- staging/prod 전체 애플리케이션을 실제 운영 인프라 설정으로 기동하는 smoke test는 수행하지 않았고 외부 호출 없는 ApplicationContext 검증으로 대체했다.
- Learning Core가 안정적인 `Idempotency-Key`를 보내더라도 Python AI가 그 키를 실제 처리하기 전까지는 AI 서버 내부 중복 실행까지 단독으로 보장할 수 없다.
- DB Job claim과 외부 AI HTTP 요청은 단일 트랜잭션이 아니므로 Python AI가 멱등 키를 처리하기 전까지 crash window의 정확히 한 번 실행은 보장할 수 없다.
- S3 `HeadObject`는 404만 미제출로 분류한다. 운영 IAM에 대상 버킷 객체 조회 권한이 없으면 403이 API 오류로 전파되므로 배포 전 권한을 확인해야 한다.
- 기존 결과의 ObjectId와 신규 결정적 문자열 `_id`가 혼재하면 `_id DESC`가 생성 시간순이 아닐 수 있으며, legacy 중복은 현재 파트 점수와 풀이 문항 수를 부풀릴 수 있다.
- 기존 결과의 중복은 삭제하지 않고 논리 존재 확인으로 신규 중복만 막는다. 운영 중복 정리가 필요하면 별도 검토·백업 후 명시적 일회성 스크립트로 수행해야 한다.
- AI `RestTemplate`은 connect/read timeout 기본값 `PT3S`/`PT30S`를 갖지만, 문항 음성을 계속 전체 `byte[]`로 읽으므로 시험 단위 다문항 복구의 메모리 사용과 timeout 적정값을 운영 부하에서 확인해야 한다.

## Next

- 운영 데이터 백업 후 TMI-31 migration을 먼저 dry-run하고 보고된 sequence·legacy 활성 세션 문제를 조정한 뒤 명시적 apply로 필드 보정과 `uniq_exam_sessions_active_user` 인덱스를 설치한다.
- staging에서 같은 사용자 동시 `POST /api/v1/exams`, 활성 세션 재사용, 순차·순환 배정, Summary Callback 완료 전이와 선택된 `mockExamId`의 S3·Python AI 전파를 실제 MongoDB·Redis·S3·AI 연동으로 smoke test한다.
- Jira `TMI-31`은 사용자 요청에 따라 `완료`로 전환했다. 완료 댓글은 등록하지 않았고 다른 Jira 필드는 변경하지 않았다.
- Identity를 8081, Learning Core를 JWT 모드 8080으로 기동한 뒤 `scripts/e2e/auth-integration-test.sh`를 실행한다.
- 실제 E2E 성공 후 출력된 수동 확인 식별자로 `exam_sessions.userId`와 JWT `sub`를 폐기 가능한 로컬 DB에서 비교한다.
- 배포 환경에서 `APP_AUTH_MODE=jwt` 전환 전 issuer·JWKS·audience와 네트워크 접근성을 확인한다.
- 실제 배포 전에 staging/prod에 `APP_AUTH_MODE=jwt`, 환경별 issuer·JWKS URL·audience와 나머지 인프라 설정을 주입해 smoke test한다.
- Jira `TMI-14`는 완료됐으며 완료 댓글은 등록하지 않았다. 다시 열기나 댓글 등록은 사용자가 명시적으로 요청하는 경우에만 수행한다.
- Jira `TMI-10`은 완료됐으므로 후속 위험은 별도 Jira 이슈로 추적한다.
- 물리적으로 다른 MongoDB database가 필요한지 확인하고, 필요하면 별도 MongoTemplate·자격증명·배포 환경변수 범위를 정의한다.
- 운영 데이터 규모에 따라 `exam_summaries` 조회 인덱스와 legacy 종합 문서 이관·보존 정책을 결정한다.
- Jira `TMI-11`은 완료 처리됐으며 실제 서버 E2E나 수동 DB 검증에서 문제가 발견되면 이슈를 다시 열거나 별도 후속 이슈로 추적한다.
- Jira `TMI-25`는 `완료` 상태와 resolution `완료`로 닫혔으며 완료 댓글은 등록하지 않았다. 다시 열기나 댓글 등록은 사용자가 명시적으로 요청하는 경우에만 수행한다.
- Python AI가 두 `Idempotency-Key`를 실제 저장·중복 반환하도록 하는 후속 작업을 별도 이슈로 분리한다.
- 배포 전 staging에서 S3 HeadObject 권한, Mongo 신규 컬렉션 생성 권한과 AI Header 전달을 smoke test한다.
- 사용자가 변경분을 검토한 뒤 commit과 push를 수행한다.

## Current review against main (2026-07-31)

- 브랜치 `chore/add-actuator-health`의 HEAD·main과 사용자 지정 merge base는 모두 `b70d03f38afc239849086fef6549bc3af47c89f6`다. 해당 기준 tracked diff와 신규 미추적 운영·리소스·테스트 파일을 함께 리뷰했으며, 수정 가치가 확실한 correctness finding은 확인하지 않았다.
- Actuator Health, AWS Default Credentials, 선택적 모범답안 음성 응답, AI `client_source=app`, 신규 문항 prompt API를 점검했다. 기존 공개 API·`BaseResponse`, retryCount, Redis/S3 기존 계약, Callback JSON, AI `user_id=examId`와 사용자 소유권/비노출 규칙은 유지된다.
- `git diff --check`와 catalog JSON·민감 패턴 정적 검증은 성공했다. 정확한 `./gradlew clean test`는 sandbox의 Gradle lock 쓰기 제한, writable offline 재시도는 file-lock UDP socket 제한으로 task 시작 전에 중단됐다. 현재 source 이후 생성된 기존 XML은 Java 245개, failures/errors/skipped 0개다.
- 리뷰 대상 애플리케이션·설정·테스트 코드는 수정하지 않았고 Codex 기록 파일만 갱신했다. 별도 Jira 이슈 키는 없으며 commit·push·PR 생성과 Jira 변경을 수행하지 않았다.
- 실제 AWS Profile/SSO·native Linux Docker·ECS Task Role/Bucket IAM 및 Python AI `client_source` 수신은 배포 전 별도 smoke test가 필요하다.

## Latest AWS credentials final review against main (2026-07-31)

- HEAD·main `b70d03f38afc239849086fef6549bc3af47c89f6` 기준 tracked 변경과 신규 미추적 파일을 함께 재검토했다. 이번 리뷰에 별도 Jira 이슈 키는 없고 Git·Jira 쓰기 작업을 수행하지 않았다.
- 이전 finding의 직접 수정은 확인됐다. AWS SDK BOM `2.29.52` 아래 `s3`·`sso`·`ssooidc`·`sts`와 transitive `auth`·`profiles`가 모두 같은 버전이고, SSO OIDC·STS factory 및 ECS Container Credentials provider가 runtime에 있다. S3Client와 S3Presigner는 공유 Default Provider를 사용하며 Bean/Health 생성 시 credential을 조회하지 않는다.
- native Linux host UID/GID, supplementary app group `999`, `HOME=/app` 방식은 현재 amd64 image의 `/app`·JAR·`/tmp` 권한과 일치한다. Dockerfile의 non-root `app`, `.aws`·`.env`·key build-context 제외와 image 내 `.aws` 부재도 유지된다.
- 남은 MEDIUM finding은 read-only SSO token cache다. AWS SDK SSO OIDC provider가 만료 임박 token을 갱신한 뒤 cache에 저장하므로, README의 전체 `.aws:ro` 방식은 host가 먼저 cache를 갱신하지 않은 장시간 local Docker 실행에서 S3 credential 해석이 실패할 수 있다. host-side SSO 재로그인 운영 절차를 명시하거나 host 원본을 read-only로 유지하는 안전한 container-owned 임시 cache 방식을 검토해야 한다.
- fresh `./gradlew clean test --no-daemon`은 Java 245개, failures/errors/skipped 0개로 성공했다. runtime dependency report·insight, `git diff --check main --`, 민감 패턴 검증도 성공했다. 실제 AWS Profile/SSO, native Linux host와 ECS Task Role smoke test는 수행하지 않았다.
- 기존 S3 Region·Bucket·Object Key·Presigned URL, 시험 API·DTO·`BaseResponse`, JWT·Guest JWT, retryCount, Redis, grading retry·멱등성, Callback JSON과 AI `user_id=examId`에는 별도 회귀를 확인하지 않았다.

## Latest frontend question-feedback contract analysis (2026-08-04)

- 현재 미커밋 작업 트리 기준 프론트 문항별 상세 조회는 `GET /api/v1/exams/{examId}/questions?questionNumber={questionNumber}&retryCount={retryCount}`이며 HTTP 200 `BaseResponse.result.question`에 요청 회차의 최신 `feedback`, 회차별 `retryScores`, 최초 응시 retry 0의 `retryFeedbackScores`, 사용자 음성·Azure·문제 정보를 결합한다. 이번 분석에 별도 Jira 이슈 키는 없다.
- 텍스트 기준 답안은 `question.feedback.correctedAnswer`로 보내며 AI Callback의 `corrected_answer`가 아니라 Session 시험지 원본 `Question.corrected_answer`를 매 조회 시 사용한다. AI가 생성한 회차별 추천 답안은 별도 `question.feedback.recommendedAnswer`이며 Callback의 `recommended_answer`가 camelCase 응답으로 변환된다.
- 음성 모범답안 `question.modelAnswer`는 텍스트를 포함하지 않고 `audioUrl`, `spokenWordSequence`만 가진다. Part 1 Question 1·2이면서 해당 Session 시험지 metadata가 있을 때만 제공하고, 그 외에는 필드 자체를 생략한다. 현재 metadata는 `mock_exam_004` q1·q2만 있다.
- `modelAnswer.audioUrl`은 `{mockExamId}/part1_a{questionNumber}.wav`의 60분 Presigned GET URL이다. 사용자 녹음인 `question.audioUrl`·`question.spokenWordSequence` 및 출제 음성인 `question.questionInfo.audioUrl`과 분리되고 retryCount에 따라 바뀌지 않는다.
- 응답의 일반 필드는 camelCase이고 `azureFeedback` 내부만 snake_case다. PartResult의 null 선택 필드는 생략될 수 있으므로 프론트는 `modelAnswer`, 점수, transcript, Azure와 사용자 단어 시퀀스의 존재 여부를 확인해야 한다.
- 애플리케이션·테스트 코드는 수정하지 않았다. 관련 집중 테스트 3개 클래스, 총 12개가 failures/errors/skipped 0개로 성공했으며 전체 `./gradlew clean test`는 분석 작업이라 다시 실행하지 않았다. 실제 배포 버전과 `mock_exam_004` 외 시험지의 음성 metadata는 별도 확인 사항이다.

## Latest code review against main (2026-08-04)

- 브랜치 `chore/add-actuator-health`의 HEAD·main과 사용자 지정 merge base는 `b70d03f38afc239849086fef6549bc3af47c89f6`다. tracked diff와 신규 미추적 파일을 함께 재리뷰했으며 별도 Jira 이슈 키는 없다.
- 이전 HIGH finding을 해결했다. 요청한 canonical retryCount의 `ExamResult`가 없으면 `getDownloadUrl`과 `buildModelAnswer`를 호출하지 않으며 model-answer catalog와 `S3Presigner`도 사용하지 않는다. matching 결과가 있는 완료 회차에만 Part 1 문항 1·2의 모범답안을 조립한다.
- 이전 로컬 Docker SSO MEDIUM은 해소됐다. macOS와 native Linux 모두 host 사전 SSO 로그인과 stdout을 숨긴 credential 검증, `.aws` read-only mount, 만료 시 host 재로그인 후 컨테이너 재시작을 안내한다. 컨테이너 내부 token cache 자동 갱신을 보장하지 않고 ECS는 Profile 없이 Task Role을 사용한다.
- 기존 공개 API·`BaseResponse`, retryCount, 사용자 소유권·userId 비노출, 완료 결과의 사용자 음성 URL, Redis/S3 기존 계약, JWT·Guest, grading, Callback JSON과 AI `user_id=examId`에 별도 회귀를 확인하지 않았다.
- fresh `./gradlew clean test`는 Java 248개, failures/errors/skipped 0개로 성공했다. 신규 집중 테스트 3개를 포함한 관련 서비스·소유권 테스트 44개도 성공했고 `git diff --check`가 통과했다.
- 실제 AWS Profile/SSO·native Linux Docker·ECS Task Role/Bucket IAM 및 Python AI `client_source=app` 수신은 배포 전 별도 smoke test가 필요하다.

## Latest modelAnswer HIGH finding narrow review (2026-08-04)

- `ExamServiceImpl.getExamQuestion`, 직접 관련된 modelAnswer 테스트와 Presigned URL 생성 경로만 재검토했으며 HIGH·MEDIUM finding은 없다. 별도 Jira 이슈 키는 없다.
- 요청 문항·canonical retry 결과가 없으면 `buildModelAnswer`, model-answer catalog 조회와 Presigned GET URL 생성을 모두 생략한다. 제출 전·처리 중·존재하지 않는 retry에는 `modelAnswer`가 없고 완료된 Part 1 문항 1·2에는 기존대로 제공된다.
- 다른 사용자 시험은 소유권 검사에서 403으로 선차단되어 모범답안 조회와 Presigner가 실행되지 않는다.
- 집중 테스트 `ExamQuestionModelAnswerTest` 8개와 `ExamOwnershipServiceTest` 36개, 총 44개가 failures/errors/skipped 0개로 성공했다. 애플리케이션·테스트 파일과 Git·Jira 상태는 변경하지 않았다.

## Latest completed-history and retry-attempt APIs (2026-08-04)

- 관련 Jira 이슈 [`TMI-61`](https://to-teacher.atlassian.net/browse/TMI-61)을 `TMI` 프로젝트의 `작업` 타입으로 생성했다. 설명에는 JWT `sub` 식별, `completedAt` 완료 기준, 신규 Summary 우선·Legacy fallback, Job 우선 Retries, `dispatchAttempt`·상세 피드백 비노출, 소유권·호환성 및 테스트 완료 조건을 기록했다.
- `GET /api/v1/exams/history`를 추가했다. JWT 모드에서는 기존 Resource Server가 Bearer 인증을 요구하고 `JwtCurrentUserProvider`가 검증된 JWT `sub` UUID를 실제 사용자 ID로 사용한다. 요청·응답에 `userId`나 `mockExamId`를 추가하지 않았고 local/test Legacy Guest 정책은 유지했다.
- History 완료 판정은 `ExamSession.userId = current user`와 `completedAt` 존재 여부만 사용한다. `active=false`만으로 완료를 판정하지 않으며 active가 null인 Legacy 완료 Session도 포함한다. 결과는 `completedAt DESC`, 동일 시각에는 `examId DESC`다.
- History는 `totalCount`와 `histories`를 반환한다. 각 항목은 `examId`, MockExam `title`, `cycleNumber`, `completedAt`, `totalScore`, `levelEstimate`, `summaryAvailable`만 포함한다. 완료 이력이 없으면 200과 `histories=[]`다.
- Session 목록 뒤 MockExam 제목, 신규 ExamSummary 후보, Legacy `exam_results.totalScore != null` 후보를 각각 batch 조회한다. Mongo `_id DESC`의 첫 문서를 최신으로 사용하고 신규 Summary를 우선한다. Summary가 전혀 없는 완료 시험은 점수·레벨 null과 `summaryAvailable=false`이며 해당 examId만 로그에 남긴다.
- `GET /api/v1/exams/{examId}/retries`를 추가했다. Session 존재와 JWT `sub` 소유권을 먼저 확인해 기존 `EXAM_4004`/`COMMON403`을 유지한다. `question_grading_jobs`의 `questionNumber`, 사용자 `retryCount`, 기존 Job status가 1차 기준이고 `dispatchAttempt`는 읽거나 응답 회차로 사용하지 않는다.
- 문항별 Legacy `exam_results.retryCount`를 합치고 null retryCount는 기존 canonical 정책대로 0으로 해석한다. 동일 question/retry Key는 Job 상태가 우선하며 결과만 있는 회차는 `COMPLETED`다. 실제 retryCount 1 이상이 있는 문항만 반환하고, 저장된 0회차는 함께 제공하되 없는 0회차는 생성하지 않는다. 문항과 회차는 각각 오름차순이며 상세 score·feedback·Transcript·URL·failureReason을 반환하지 않는다. 재답변 문항이 없으면 200과 `questions=[]`다.
- 운영 자동 인덱스 생성에 의존하지 않도록 별도 기본 dry-run/idempotent 스크립트를 추가했다. 대상은 `exam_sessions {userId:1, completedAt:-1, _id:-1}`, `question_grading_jobs {examId:1, questionNumber:1, retryCount:1}`, `exam_results {examId:1, questionNumber:1, retryCount:1}`이며 기존 호환 인덱스는 중복 생성하지 않고 충돌 정의는 쓰기 전에 차단한다. 실제 MongoDB에는 적용하지 않았다.
- 새 Java 테스트 18개와 Node migration 테스트 7개, 총 25개를 추가했다. 신규 집중 Java 테스트 37개가 성공했고 `./gradlew clean test` 전체 Java 266개가 failures/errors/skipped 0으로 성공했다. MongoDB 스크립트 전체 Node 56개도 성공했으며 `git diff --check`가 통과했다.
- 기존 시험 생성·문항 단건·Summary·status·submit·grading retry와 Controller mapping, `BaseResponse`, retryCount/dispatchAttempt 의미, JWT·Guest, Redis, S3, AI/Callback `user_id=examId`, modelAnswer의 `audioUrl`·`spokenWordSequence`, Health 계약을 변경하지 않았다. Secret, Token, 실제 URI·Credential·Presigned URL을 코드·로그·문서에 기록하지 않았다.
- 남은 운영 확인은 실제 데이터 규모의 query explain, 별도 인덱스 스크립트 dry-run/apply, 혼합 BSON `_id` 타입을 가진 중복 Summary의 최신 정렬 결과와 staging Bearer smoke test다. Git commit·push·PR 생성은 수행하지 않았다.

## Latest Jira issue creation (2026-08-04)

- [`TMI-61`](https://to-teacher.atlassian.net/browse/TMI-61) — `[Learning Core] 완료 시험 이력 및 재답변 회차 조회 API`를 생성했다.
- 프로젝트는 `TMI`(ID `10000`), 이슈 유형은 `작업`(ID `10003`)이다. 생성 후 재조회에서 기본 상태 `해야 할 일`(ID `10000`), 기본 우선순위 `Medium`(ID `3`), 담당자 미지정, 빈 라벨을 확인했다.
- 설명에는 JWT `sub` 기반 사용자 식별, `ExamSession.completedAt` 완료 기준, 신규/Legacy Summary batch 결합과 신규 우선 fallback, `question_grading_jobs` 우선 및 `exam_results` Legacy fallback, 사용자 `retryCount`·Job 상태 제공과 `dispatchAttempt`·상세 피드백 비노출을 기록했다.
- 보안·호환성 및 Java·MongoDB 스크립트 테스트 완료 조건도 기록했다. 제공된 Jira/PR 완료 댓글 초안은 이번 이슈 생성 요청 범위에서 등록하지 않았고 상태 전환·담당자·라벨·댓글은 변경하지 않았다.
- 애플리케이션·테스트·migration 구현은 수정하지 않았다. 문서 기록만 갱신했으며 이번 turn에서는 Gradle·Node 테스트를 다시 실행하지 않았다. 직전 구현 검증 결과인 Java 266개와 MongoDB 스크립트 56개 성공 상태를 인용했을 뿐 재실행 결과로 기록하지 않는다.

## Latest TMI-61 History/Retries scoped review (2026-08-04)

- Jira `TMI-61`의 `GET /api/v1/exams/history`, `GET /api/v1/exams/{examId}/retries`와 Controller, `ExamReadService`, 신규 DTO, 관련 Repository, MongoDB read-index 스크립트 및 관련 테스트만 검토했다.
- 리뷰 결과는 HIGH 없음, MEDIUM 1건이다. `ExamSummaryRepository.findHistoryCandidatesByExamIdIn`은 `exam_summaries`를 `examId IN (...)`으로 조회하고 `{examId:1, _id:-1}` 정렬하지만 `create-exam-read-indexes.js`에는 `exam_summaries` 인덱스가 없다. 데이터가 증가하면 사용자 History 요청마다 전역 collection scan과 blocking sort가 발생할 수 있으므로 해당 query shape를 지원하는 인덱스를 스크립트·테스트·문서에 추가하고 실제 `explain`으로 검증해야 한다.
- 확인 항목 1~10의 기능 동작은 모두 충족한다. completedAt/current user 필터, `completedAt DESC`·`examId DESC`, 고정 개수 batch 조회, Summary 없음 허용, 타 사용자 Retries 403, dispatchAttempt 비사용, Job/Legacy 회차 dedupe, retry 1 이상 없는 문항 제외, 200 빈 배열, 기존 문항 단건·Summary mapping/DTO 계약 유지가 확인됐다.
- 관련 Java 테스트 6개 클래스 40개와 `create-exam-read-indexes.test.js` Node 7개가 모두 failures/errors/skipped 0개로 성공했고 `git diff --check`도 통과했다. 첫 Gradle 시도는 sandbox의 사용자 Gradle cache lock 권한으로 task 시작 전에 중단됐고 승인된 동일 명령 재실행은 성공했다.
- 실제 MongoDB query `explain`과 인덱스 dry-run/apply는 수행하지 않았다. 애플리케이션·테스트·인덱스 스크립트는 수정하지 않았고 필수 Codex 작업 기록 문서만 갱신했다.

## Latest Stop Hook record reconciliation (2026-08-04)

- Stop Hook이 요구한 현재 turn 기록을 추가했다. Jira `TMI-61` History/Retries 지정 범위 리뷰 결과는 HIGH 없음, MEDIUM 1건으로 동일하며, MEDIUM은 `exam_summaries` History batch query용 인덱스가 read-index 스크립트에서 누락된 문제다.
- 기능 확인 1~10, 관련 Java 40개·Node 7개 성공, `git diff --check` 성공과 실제 MongoDB `explain`·dry-run/apply 미실행 상태는 변경되지 않았다.
- 애플리케이션·테스트·인덱스 스크립트와 Jira는 변경하지 않았고 Stop Hook 기록을 위한 Codex 문서만 갱신했다. Secret과 Token은 기록하지 않았다.

## Latest TMI-61 Summary batch index MEDIUM fix (2026-08-04)

- targeted review의 MEDIUM finding을 최소 범위로 수정했다. `create-exam-read-indexes.js`의 선언형 계획에 `exam_summaries`용 `idx_exam_summaries_exam_id_latest`, Key `{examId:1, _id:-1}`를 추가해 `ExamSummaryRepository.findHistoryCandidatesByExamIdIn`의 `examId IN (...)`과 `{examId:1, _id:-1}` 정렬을 지원한다.
- 기본 dry-run, `EXAM_READ_INDEXES_APPLY=true` 명시 apply, apply 전 전체 충돌 검사, apply 후 재검증과 운영 자동 적용 금지 정책을 유지했다. 인덱스만 계획·생성하며 `exam_summaries` 문서는 조회·수정하지 않는다.
- 같은 이름·정확히 같은 Key와 다른 이름·같은 Key는 idempotent하게 재생성하지 않는다. 다른 이름의 더 긴 `{examId:1, _id:-1, ...}` 인덱스는 필수 ordered prefix와 옵션이 호환되면 재사용하고, 확정 이름의 다른 정의·역방향·필드 순서 불일치·짧은 Key와 unique/sparse/partial/collation 옵션은 호환으로 보지 않는다.
- Node 테스트는 Summary 계획·확정 이름·정확한 Key, dry-run 무쓰기, apply 생성, 동일/다른 이름 idempotency, 긴 prefix, 동일 이름 충돌, 역방향·재정렬·짧은 Key와 기존 세 인덱스 회귀를 실제 MongoDB 없이 검증한다. 전체 MongoDB 스크립트 테스트 63개가 성공했다.
- 요청한 Java 집중 테스트 `*ExamRead*`, `*JwtSecurityIntegrationTest*`, `*LegacySecurityIntegrationTest*` 총 37개와 `git diff --check`가 성공했다. Java 운영 코드, Controller, Repository, DTO와 공개 API·인증·소유권·retryCount·dispatchAttempt·modelAnswer 계약은 변경하지 않았다.
- 실제 Staging/운영 DB apply와 `explain("executionStats")`는 수행하지 않았다. README에 apply 후 IXSCAN, 선택 인덱스, COLLSCAN·blocking SORT 부재와 `totalDocsExamined`를 확인하는 쿼리를 기록했다. 전체 `clean test`는 Java 운영 코드가 바뀌지 않아 PR 직전 통합 검증으로 남겼다.
- Git commit·push·PR 생성 및 Jira `TMI-61` 댓글·필드·상태 변경은 수행하지 않았다. Secret과 Token은 기록하지 않았다.

## Latest TMI-61 Summary index narrow review (2026-08-04)

- `ExamSummaryRepository`, `create-exam-read-indexes.js`, `create-exam-read-indexes.test.js`만 재검토했다. 결과는 HIGH 없음, MEDIUM 1건이다.
- MEDIUM: `hasIncompatibleOptions`가 `hidden:true`를 검사하지 않아 정확한 `{examId:1, _id:-1}` 또는 호환 prefix 인덱스가 hidden이어도 compatible로 처리한다. apply와 최종 검증은 새 usable 인덱스를 만들지 않고 성공할 수 있지만 MongoDB Query Planner는 hidden 인덱스를 사용하지 않으므로 History query가 COLLSCAN/blocking SORT로 남을 수 있다.
- Key `{examId:1, _id:-1}`와 이름 `idx_exam_summaries_exam_id_latest`는 Repository의 `examId IN (...)`, `{examId:1, _id:-1}` 정렬에 맞게 존재한다. 기본 dry-run, 명시 apply, exact/different-name idempotency, 확정 이름 충돌 무쓰기와 기존 세 인덱스 계획도 유지된다.
- Node 테스트 14개와 `git diff --check`가 성공했다. 별도 Node probe에서 다른 이름의 `{examId:1, _id:-1, hidden:true}`가 오류 없이 compatible로 분류되고 Summary 인덱스 생성 계획에서 제외되는 것을 재현했다. 실제 MongoDB 연결·apply·explain은 수행하지 않았다.
- 리뷰 대상 코드는 수정하지 않았고 필수 Codex 기록만 갱신했다. Jira `TMI-61`, Git commit·push·PR, Secret과 Token에는 변경이 없다.

## Latest TMI-61 hidden index MEDIUM fix (2026-08-04)

- targeted review의 hidden 인덱스 MEDIUM finding 하나만 수정했다. `hasIncompatibleOptions`가 `hidden:true`를 비호환으로 판정하며 `hidden:false` 또는 hidden 필드가 없는 visible 인덱스는 기존대로 호환 가능하다.
- 다른 이름의 exact `{examId:1, _id:-1}` 또는 compatible prefix가 hidden이면 재사용하지 않고 visible 목표 인덱스를 생성 계획에 남긴다. 동일 이름의 hidden 인덱스는 컬렉션·이름·예상 Key·실제 Key·`hidden=true`와 자동 drop/unhide 미수행 사실을 포함한 명시적 충돌로 apply 전에 전체 쓰기를 차단한다.
- 스크립트는 `dropIndex`나 `collMod`를 호출하지 않고 기존 인덱스를 수정하지 않는다. 기본 dry-run, 명시 apply, visible 인덱스 idempotency, unique/sparse/partial/collation 충돌과 기존 네 컬렉션 인덱스 계획은 유지된다.
- read-index Node 테스트는 19개로 늘어 exact/prefix hidden 배제, same-name hidden 무쓰기 충돌, create/drop/collMod 0회, hidden false/필드 누락 visible 호환, 다른 이름 visible 중복 방지와 기존 옵션·계획 회귀를 실제 MongoDB 없이 검증한다. 전체 MongoDB 스크립트 테스트 68개와 `git diff --check`가 성공했다.
- Java·Repository·Controller·DTO와 공개 API 계약은 변경하지 않아 Java 테스트는 다시 실행하지 않았다. 실제 DB apply와 `explain("executionStats")`, Git commit·push·PR 및 Jira `TMI-61` 댓글·필드·상태 변경은 수행하지 않았고 Secret과 Token을 기록하지 않았다.

## Latest TMI-61 hidden index targeted review (2026-08-04)

- `create-exam-read-indexes.js`의 hidden 호환 판정, 동일 이름 hidden 충돌 무쓰기, visible 인덱스 idempotency를 재검토했으며 HIGH·MEDIUM finding은 없다.
- `hidden:true` exact/prefix 인덱스는 호환에서 제외된다. 동일 이름 hidden은 apply 전에 충돌하고 `createIndex`, `dropIndex`, `collMod`를 호출하지 않으며, `hidden:false` 또는 hidden 필드가 없는 visible exact/prefix 인덱스는 기존대로 중복 생성하지 않는다.
- 직접 관련된 Node 테스트 19개가 failures/errors/skipped 0으로 성공했다. 실제 MongoDB 연결·apply·explain은 수행하지 않았고 리뷰 대상 코드·테스트, Git 및 Jira 상태는 변경하지 않았다.

## Latest TMI-61 missing-namespace dry-run fix (2026-08-04)

- 아직 없는 `exam_summaries`의 비동기 인덱스 조회가 `NamespaceNotFound`로 dry-run을 중단하던 문제를 수정했다. 조회 helper와 호출부는 `async/await`를 사용하며 `code === 26` 또는 `codeName === "NamespaceNotFound"`만 빈 인덱스 목록으로 정규화한다.
- 누락 컬렉션의 인덱스는 dry-run 생성 예정에 포함되지만 `createCollection`, `createIndex`, `dropIndex`, `collMod` 쓰기는 발생하지 않는다. apply에서는 기존 `createIndex` 흐름을 유지하며 별도 문서 insert/delete를 사용하지 않는다.
- 인증·네트워크·권한·명령·알 수 없는 MongoDB 오류는 숨기지 않고 전파한다. visible idempotency, hidden 비호환, 동일 이름 충돌 선차단과 자동 drop/unhide 금지 정책도 유지된다.
- Node 테스트 8개를 추가했고 전체 MongoDB 스크립트 테스트 76개와 `git diff --check`가 성공했다. 실제 MongoDB 연결·apply·explain, Git commit·push·PR과 Jira `TMI-61` 쓰기 작업은 수행하지 않았다.

## Latest AWS Secrets Manager configuration inventory (2026-08-04)

- 현재 tracked Learning Core 설정 기준으로 `MONGODB_URI`는 자격증명을 포함하므로 AWS Secrets Manager 필수 대상이고, `SENTRY_DSN`은 보호 저장 권장 대상이다. 실제 값이나 실행 환경 Secret은 조회하지 않았다.
- 현재 checkout에는 Expo Access Token과 Redis password 설정이 없다. 해당 인증 기능이 배포될 때만 Provider Access Token 또는 Redis AUTH 값을 Secrets Manager 대상으로 추가해야 하며, 먼저 애플리케이션 설정 바인딩을 확인해야 한다.
- MongoDB database 이름, Redis host/port, AWS Region·S3 Bucket, Identity issuer·JWKS URL·audience, profile/auth mode와 각종 prefix·timeout·thread·port·sampling 값은 비밀값이 아닌 일반 구성이다.
- AWS 장기 Access Key/Secret Key는 Secrets Manager 주입 대상이 아니라 ECS Task Role로 대체한다. Learning Core에는 Identity RSA Private Key나 공유 JWT Secret을 저장하지 않는다.
- 별도 Jira 이슈 키는 없고 코드·테스트 변경이나 AWS/Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest AI endpoint configuration check (2026-08-05)

- 현재 `main`의 AI 채점 endpoint는 `GradingDispatchService` 정적 상수로 고정되어 있고 환경변수 또는 Spring property로 처리되지 않는다. 문항과 Summary 전송이 같은 고정 주소를 사용한다.
- 환경변수로 조정 가능한 AI 관련 값은 연결 timeout과 읽기 timeout뿐이며 `.env.example`에는 AI 주소 항목이 없다.
- 현황 확인만 수행해 코드·설정·테스트를 변경하거나 테스트를 재실행하지 않았다. 별도 Jira 이슈 키와 Git/Jira 쓰기 작업은 없고 실제 Secret·Token·실행 환경값은 조회하지 않았다.

## Latest AI server URL environment configuration (2026-08-05)

- AI 서버 주소는 더 이상 `GradingDispatchService` 상수로 고정되지 않는다. `app.grading.ai-server-url`이 `AI_SERVER_URL`을 읽고 기본값과 `.env.example` 예시는 `http://tosunsaeng-ai:8000`이다.
- 환경변수는 base URL 계약이며 서비스가 기존 `/evaluations`를 한 번만 붙인다. `GradingProperties`는 URI와 HTTP(S) base URL 조건을 기동 시 검증한다.
- 문항·Summary의 AI 요청 body·header, `user_id=examId`, `mock_exam_id`, `client_source`, `Idempotency-Key`와 endpoint path는 유지했다.
- 관련 집중 테스트와 전체 `./gradlew clean test` Java 267개가 failures/errors/skipped 0으로 성공했고 `git diff --check`도 성공했다. 실제 AI 호출, Git commit·push·PR 및 Jira 쓰기는 수행하지 않았다.
- Stop Hook 요구에 따라 현재 turn marker를 포함한 append-only WORKLOG 보완 기록을 추가했으며 구현·검증 결과에는 변경이 없다.

## Latest exam-session audio URL inspection (2026-08-06)

- `POST /api/v1/exams`가 반환하는 각 문제의 `audioUrl`은 `questions/{mockExamId}/q_{questionNumber}.wav`를 대상으로 생성한 60분 S3 Presigned GET URL이다.
- Part 3 문항에는 `questions/{mockExamId}/part3_intro.wav`의 60분 `guideAudioUrl`도 포함된다. 실제 URL 문자열은 실행 환경의 Bucket·Region과 서명에 따라 달라진다.
- 사용자 녹음용 Presigned PUT URL은 세션 생성과 분리되어 기존 upload-url API에서 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav` Key로 발급된다.
- 별도 Jira 이슈 키와 애플리케이션·테스트 변경은 없다. 실제 S3·Secret·Credential·Token·Presigned URL 접근 또는 발급, Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest exam-session issuance logging inspection (2026-08-06)

- `POST /api/v1/exams`에서 `ExamSessionManager`가 새 문서를 insert해 `assignment.created() == true`인 경우에만 `ExamServiceImpl`이 세션 생성 완료 INFO 로그를 출력한다.
- 진행 중 활성 세션 재사용 또는 동시 생성 충돌 후 기존 세션 선택은 `created=false`이므로 현재 세션 발행 로그가 없다. 별도 HTTP access log 설정도 확인되지 않았다.
- 기본 Spring 로깅에서는 신규 생성 INFO 로그가 보이지만 배포 환경이 INFO를 차단하도록 별도 override하면 수집되지 않을 수 있다.
- 별도 Jira 이슈 키 및 애플리케이션·테스트 변경은 없다. Secret·Token·Credential 접근과 Git/Jira 쓰기 작업도 수행하지 않았다.

## Latest ECS logging diagnosis (2026-08-06)

- Learning Core 이미지는 커스텀 파일 appender 없이 `java -jar /app/app.jar`로 실행되므로 Spring 기본 기동·INFO 로그는 컨테이너 stdout/stderr에 기록된다.
- CloudWatch에 기동 로그까지 전혀 없다면 ECS Container Definition의 `awslogs` `logConfiguration`, 정확한 Region·Log Group·최신 stream, Task Execution Role의 CloudWatch Logs 권한과 Task Definition revision을 우선 확인해야 한다. 저장소에는 실제 Task Definition/IaC가 없다.
- 현재 로컬 AWS CLI에는 자격 증명이 없어 ECS와 CloudWatch의 실제 설정을 읽기 전용으로도 확인하지 못했다. AWS 쓰기는 수행하지 않았다.
- 세션 생성 로그만 없는 경우에는 진행 중 세션 재사용 분기와 미활성 HTTP access log가 원인이 될 수 있다. Task Definition의 `LOGGING_LEVEL_ROOT=OFF` 또는 외부 `LOGGING_CONFIG` override도 실제 배포 설정에서 확인이 필요하다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 Secret·Token·Credential 값은 조회하거나 기록하지 않았다.

## Latest grading-after-upload diagnosis (2026-08-06)

- Presigned PUT은 앱에서 S3로 직접 수행되며 Learning Core에 업로드 완료 이벤트가 전달되지 않는다. 채점은 동일 식별자와 회차의 별도 `POST /api/v1/exams/{examId}/questions/{questionNumber}/submit` 호출로만 시작된다.
- submit은 결정적 Question Job을 PROCESSING으로 claim한 뒤 S3 객체를 Presigned GET으로 다운로드하고 `AI_SERVER_URL`의 `/evaluations`에 multipart POST한다. Put 성공만으로 S3 Get 권한·Key 일치와 AI 연결은 검증되지 않는다.
- ECS에서 `AI_SERVER_URL` 미주입 시 기본값 `http://tosunsaeng-ai:8000`을 사용한다. 해당 이름이 ECS Service Connect/Cloud Map 또는 실제 Task 통신 경로로 해석되지 않으면 dispatch가 실패한다. Task Role의 `s3:GetObject` 누락도 같은 증상을 만든다.
- dispatch 예외는 Job의 `failureReason=QUESTION_DISPATCH_FAILED`와 API `EXAM_4001`로 정규화되지만 원래 예외를 로그로 남기지 않는다. submit·S3 fetch·AI outbound에도 정상 흐름 로그가 없어 현재 관측성만으로 S3와 AI 실패를 구분할 수 없다.
- submit 응답이 없으면 앱 호출 누락, 401/403이면 인증·소유권, 500 `EXAM_4001`이면 S3 GET/AI 연결, 200 `PROCESSING` 뒤 정체면 AI 처리·Callback을 우선 확인한다. 실패 원인 수정 후 동일 submit은 기존 Job을 자동 재전송하지 않으므로 grading retry 경로를 사용해야 한다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 AWS·MongoDB·AI·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest AI grading outbound logging inspection (2026-08-06)

- 문항 submit, S3 음성 재다운로드, AI `/evaluations` 요청 직전과 성공 응답에는 현재 INFO 로그가 없다. `GradingDispatchService`에는 logger가 선언되어 있지 않다.
- outbound RuntimeException은 Question Job을 `FAILED`와 `QUESTION_DISPATCH_FAILED`로 기록하고 `EXAM_4001`로 변환되지만 원래 예외와 S3/AI 실패 단계는 로그에 남지 않는다.
- AI Feedback Callback이 실제 도달했을 때만 Controller가 exam·문항·회차 식별 정보를 INFO로 기록한다. outbound와 inbound 관측성은 비대칭이다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest FAILED Question Job resubmission behavior (2026-08-06)

- 동일 `examId + questionNumber + retryCount` submit은 결정적 Job ID를 재사용한다. 기존 Job insert가 Duplicate Key이면 기존 상태를 반환하고 dispatch하지 않으므로 FAILED Job의 같은 submit 재호출은 HTTP 200과 `result.status=FAILED`가 된다.
- 최초 dispatch 실패가 발생한 원 요청은 Job을 `FAILED/QUESTION_DISPATCH_FAILED`로 전이하고 500 `EXAM_4001`을 반환한다. 이후 같은 submit부터는 중복 Job 상태 조회 경로다.
- 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`는 최초 응시 `retryCount=0` FAILED Job만 즉시 재시도하며 dispatchAttempt가 설정된 최대 횟수 미만일 때 AI에 다시 보낸다. 기본 최대 횟수는 3회다.
- `retryCount>0` 사용자 재답변 Job은 이 시험 단위 복구 API 대상이 아니다. 새로운 retryCount submit은 새로운 답변 Job이며 기존 FAILED Job 재전송과는 다르다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest question submit payload inspection (2026-08-06)

- 앱 submit은 Body 없이 `POST /api/v1/exams/{examId}/questions/{questionNumber}/submit?retryCount={retryCount}`를 호출한다. JWT 모드에서는 Bearer 인증이 필요하며 retryCount 기본값은 0이다.
- Learning Core는 `temp/{examId}/q_{questionNumber}_r{retryCount}.wav`의 음성을 읽고 AI multipart에 `user_id=examId`, Session `mock_exam_id`, 파생 `part_number`, `question_number`, canonical `retry_count`, `client_source=app`, `audio_file`을 보낸다. 실제 사용자 ID는 보내지 않는다.
- AI endpoint는 `${AI_SERVER_URL}/evaluations`, multipart 파일명은 `q_{questionNumber}_r{retryCount}.webm`, `Idempotency-Key`는 `question:{examId}:{questionNumber}:{retryCount}`다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest AI request recognition diagnosis (2026-08-06)

- `AI_SERVER_URL`은 base URL이어야 하며 Learning Core가 `/evaluations`를 붙인다. 배포 값에 이미 해당 path가 있으면 `/evaluations/evaluations`로 전송될 수 있고 현재 URI 검증은 이 오설정을 차단하지 않는다.
- 기존 웹 POC 대비 앱 문항 요청의 주요 wire 차이는 `client_source=app`, Session의 실제 `mock_exam_id`, `Idempotency-Key`와 환경변수 endpoint다. 웹 요청만 정상이라면 AI의 이 값 처리 여부를 우선 대조해야 한다.
- S3 Key `.wav`의 bytes를 multipart 파일명 `.webm`으로 보내는 기존 동작도 AI가 확장자·Content-Type을 엄격히 검증하는 경우 확인 대상이다.
- submit이 200 `PROCESSING`이면 AI HTTP endpoint가 2xx를 반환했으므로 AI 내부 분기·Callback 문제이고, 500 `EXAM_4001` 또는 FAILED이면 path, multipart validation, AI 4xx/5xx 또는 전송 문제다. Learning Core는 현재 원본 AI 응답 오류를 로그에 남기지 않는다.
- Python 채점 서버 소스와 실제 ECS/AI 요청·응답은 확인하지 못했다. 별도 Jira 이슈 키와 코드·테스트 변경은 없으며 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업도 수행하지 않았다.

## Latest question grading diagnostic logging (2026-08-06)

- 별도 Jira 이슈 키 없이 Question submit과 AI outbound 진단 로그를 추가했다. submit job/exam/question/retry, 신규·기존 Job과 status/dispatchAttempt, AI 호출 전 job/fileKey/attempt와 성공·실패를 기록한다.
- `GradingDispatchService`는 실제 `${AI_SERVER_URL}/evaluations` URI, jobId, fileKey, audio byte size와 반환 HTTP status를 INFO로 기록하며 최초 submit과 grading retry outbound 모두 추적 가능하다.
- dispatch 실패는 jobId·예외 타입·안전한 메시지를 ERROR로 기록한다. Presigned URL·서명·Token 가능성이 있는 URI 및 민감 값은 치환하고 메시지를 단일 행 500자로 제한하며 원본 Throwable stacktrace는 로그에 출력하지 않는다.
- 기존 Job idempotency, FAILED/retry 상태 전이, S3·AI payload와 `Idempotency-Key`, 공개 API·DTO·응답은 변경하지 않았다.
- 로그 내용과 Presigned URL 비노출 집중 테스트 및 전체 `./gradlew clean test`가 성공했다. Java 268개, failures/errors/skipped 0개이며 `git diff --check`도 성공했다. 실제 AI·AWS·MongoDB·ECS 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest question prompt API inspection (2026-08-06)

- 특정 문제만 조회하는 API는 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`다. JWT sub 사용자와 ExamSession 소유권을 확인한 뒤 Session의 실제 시험지에서 해당 문항을 반환한다.
- `QuestionDTO`는 part, questionNumber, 문제·참조·Part 안내 문구, image/table, 준비·답변 시간과 60분 문제 audioUrl을 포함하며 Part 3은 guideAudioUrl도 제공한다.
- 채점 상태·점수·피드백·retryCount·사용자 녹음은 포함하지 않아 기존 결과 단건 `GET /api/v1/exams/{examId}/questions`와 구분된다. 세션 생성 API는 전체 문제 목록을 반환한다.
- 별도 Jira 이슈 키와 코드·테스트 변경은 없다. 실제 외부 시스템·Secret·Token·Credential 접근 및 Git/Jira 쓰기 작업은 수행하지 않았다.

## Latest always-new exam session lifecycle (2026-08-06)

- `POST /api/v1/exams`는 더 이상 진행 중 Session을 재사용하지 않는다. 같은 사용자의 기존 `IN_PROGRESS` Session을 조건부로 모두 `ABANDONED`, `active=false`로 전이한 뒤 매번 새 examId와 `IN_PROGRESS` status의 Session을 insert하고 새 Redis 상태를 PENDING으로 초기화한다.
- 내부 Session 상태는 `IN_PROGRESS`, `COMPLETED`, `ABANDONED`이며 status 없는 기존 문서는 completedAt/active와 완료 증거를 이용한 legacy 호환 처리를 유지한다. 완료 처리는 completedAt, active=false, status=COMPLETED를 함께 기록하고 ABANDONED Session을 완료로 되돌리지 않는다.
- 다중 ECS 동시 시작은 기존 필수 `uniq_exam_sessions_active_user` partial unique 인덱스와 조건부 ABANDON, Duplicate Key 재시도로 직렬화한다. 동시 테스트에서 서로 다른 신규 ID가 생성되고 최종 활성 Session이 한 개임을 확인했다.
- ABANDONED 시험의 Feedback/Summary·SpeechAce·Azure Callback은 저장 및 Job/Session 완료 없이 성공 no-op이며, Question/Summary dispatch도 Session 상태를 재검사해 AI 재전송을 막는다. 시험 단위 grading retry는 IN_PROGRESS만 허용하고 ABANDONED/COMPLETED를 각각 `EXAM_4007`/`EXAM_4008`로 차단한다.
- 기존 사용자 재답변은 완료 시험에서도 같은 examId와 증가한 retryCount로 유지된다. 새 시험의 최초 submit은 retryCount=0이고 과거 Job·결과·오디오를 상속하지 않는다. 공개 API·DTO·BaseResponse, AI `user_id=examId`, Callback JSON, S3 Key와 기존 submit 멱등성은 변경하지 않았다.
- `git diff --check`와 `./gradlew clean test`가 성공했고 XML 기준 전체 Java 272개, failures/errors/skipped 0개다. 실제 MongoDB·Redis·S3·AI 호출, 운영 변경, Git commit·push·PR 및 Jira 쓰기는 수행하지 않았다.

## Latest Question submit state-transition inspection (2026-08-06)

- 최초 Question Job은 실제로 PENDING으로 insert되지만 같은 submit 요청이 즉시 optimistic-lock claim하여 PROCESSING과 dispatchAttempt=1로 저장한 뒤 AI HTTP 호출을 수행한다.
- 현재 의미에서 PENDING은 아직 claim되지 않은 대기 상태이고 PROCESSING은 outbound 호출 시작부터 AI Callback 완료 전까지의 상태다. 정상 submit 응답은 AI endpoint의 2xx 이후 PROCESSING이므로 일반 클라이언트는 짧은 PENDING 구간을 관찰하지 못한다.
- 클라이언트에 PENDING을 먼저 반환하려면 응답만 바꾸는 것이 아니라 초기 AI 전송을 별도 원자적 claim Worker로 분리해야 한다. 코드·테스트 변경과 실제 외부 호출은 수행하지 않았다.

## Latest grading-status semantics clarification (2026-08-06)

- 현재 PENDING은 Learning Core 내부에서 아직 dispatch claim되지 않은 상태이고, PROCESSING은 AI 전송 claim부터 최종 Callback까지의 전체 상태다.
- 따라서 PROCESSING은 AI가 실제 모델 계산을 시작했다는 뜻이 아니라 요청 전송 중, AI 내부 대기 또는 결과 Callback 대기를 모두 포함할 수 있다.
- AI 실제 대기와 계산 중을 정확히 구분하려면 AI가 accepted/started 상태를 제공하는 추가 계약이 필요하다. 이번 확인에서는 코드·테스트·외부 시스템을 변경하지 않았다.

## Latest grading-retry eligibility clarification (2026-08-06)

- 기존 grading retry는 FAILED Job을 즉시, PENDING은 `GRADING_PENDING_TIMEOUT` 이후, PROCESSING은 `GRADING_PROCESSING_TIMEOUT` 이후 재시도 대상으로 삼는다. 기본값은 각각 1분과 3분이며 최대 dispatch 시도 기본값은 3이다.
- 프론트의 복구 조건에는 장기 PROCESSING도 포함하는 것이 현재 상태 의미와 일치한다. 동일 submit 재호출은 기존 Job 상태만 반환하므로 실제 재전송에는 시험 단위 grading retry API를 사용하고, 최종 eligibility는 백엔드 응답을 기준으로 해야 한다.
- 이번 확인에서는 코드·테스트·외부 시스템을 변경하지 않았다.

## Latest frontend retry guidance for long PROCESSING (2026-08-06)

- 프론트 복구 UI는 FAILED뿐 아니라 backend timeout을 넘긴 PENDING과 PROCESSING도 대상으로 삼아야 한다. 현재 기본 timeout은 PENDING 1분, PROCESSING 3분이고 최대 dispatch 시도는 3회다.
- 재전송은 동일 submit 호출이 아니라 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`를 사용한다. 최종 eligibility와 동시 claim은 백엔드가 판정하며, 장기 PROCESSING 재전송에 대비해 AI의 동일 Idempotency-Key 처리 보장이 필요하다.
- 코드·테스트·외부 시스템 변경은 수행하지 않았다.

## Latest Part 4 question table image response (2026-08-06)

- 문항 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 Part 4 `questionInfo`는 이제 `part`, `questionNumber`, `tableImageUrl`만 반환한다. MongoDB `table_image_url`은 `Question.tableImageUrl`로 명시 매핑되며 저장된 URL을 가공 없이 전달한다.
- Part 4 단건 응답에서는 기존 text, referenceText, partIntroText, audioUrl, guideAudioUrl, imageUrl, tableContext, prepTimeSec, speakTimeSec를 노출하지 않는다. DB·내부 `tableContext`, 세션·prompt 변환, Part 1·2·3·5·6·7과 Summary API는 유지한다.
- Part 4 URL이 null·빈 문자열·공백이면 기존 카탈로그 설정 오류 `EXAM_5001`로 처리하며 임의 URL이나 Presigned URL을 생성하지 않는다.
- Part 4 AI submit multipart와 `Idempotency-Key`는 변경하지 않았다. 집중 테스트와 전체 `./gradlew clean test`가 성공했고 Java 286개, failures/errors/skipped 0개이며 `git diff --check`도 성공했다. 실제 MongoDB·S3·AI, Git 및 Jira 쓰기 작업은 수행하지 않았다.

## Latest Part 4 delivery-path clarification (2026-08-06)

- URL-only Part 4 변경은 채점 결과 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 `questionInfo`에 적용된다. 이 응답은 part, questionNumber, tableImageUrl만 포함한다.
- 초기 문제를 전달하는 `POST /api/v1/exams`와 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 아직 기존 공통 변환을 사용해 tableContext를 전달하며 tableImageUrl은 채우지 않는다. 프론트가 어느 API로 문제를 렌더링하는지에 따라 후속 범위 확인이 필요하다.
- 현재 문항 번호 규칙에서 Part 4는 Question 8~10이다. 이번 확인은 읽기 전용이며 코드·테스트·외부 시스템과 Git·Jira를 변경하지 않았다.

## Latest deployed Part 4 response diagnosis (2026-08-06)

- 배포 후 관찰된 Part 4의 text·audioUrl·tableContext 배열은 `POST /api/v1/exams` 세션 생성 응답이다. 이 경로는 `createExamSession` → `toQuestionPrompt` → 기존 `toQuestionDTO`를 사용하므로 현재 동작과 일치한다.
- tableImageUrl-only 변경은 채점 결과 단건 `GET /api/v1/exams/{examId}/questions?questionNumber={number}&retryCount={optional}`의 `questionInfo` 변환에만 적용되어 있다. 세션 생성과 문제 prompt API에는 아직 적용되지 않았다.
- 실제 시험 문제 표시 경로도 이미지 URL 방식으로 바꾸려면 세션 생성 및 prompt의 Part 4 변환까지 후속 변경해야 한다. 사용자 제공 Presigned URL과 임시 자격·서명 값은 문서에 기록하지 않았으며 이번 진단에서는 코드·테스트·외부 시스템을 변경하지 않았다.

## Latest POST exam Part 4 table image response (2026-08-06)

- `POST /api/v1/exams`의 `result.questions`에서 Part 4는 기존 text·audioUrl을 유지하면서 DB `table_image_url`의 원본 값을 camelCase `tableImageUrl`로 반환하고 tableContext는 JSON에 노출하지 않는다.
- 세션 생성 전용 변환만 추가했으므로 Part 1·2·3·5·6·7, 기존 채점 결과 단건 `GET /api/v1/exams/{examId}/questions`, Summary·AI 계약은 유지된다. 내부 `Question.tableContext`와 Mongo 매핑도 보존한다.
- 별도 prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 이번 명시 범위 밖이라 기존 tableContext 변환을 유지한다.
- 집중 테스트와 전체 `./gradlew clean test`가 성공했다. Java 295개, failures/errors/skipped 0개이며 `git diff --check`도 성공했다. 실제 외부 시스템과 Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest Codex record synchronization (2026-08-06)

- 현재 turn hook 요구에 따라 Part 4 시험 시작 응답 구현 상태를 WORKLOG 끝에 append하고 CURRENT_STATE를 동기화했다. 구현 및 검증 결과는 직전 상태와 동일하며 애플리케이션·테스트 코드는 추가 변경하지 않았다.
- 별도 Jira 이슈 키가 없고 Secret·Token·Credential 및 사용자 제공 임시 URL 값은 기록하지 않았다. Git·Jira 쓰기 작업과 외부 시스템 호출도 수행하지 않았다.

## Latest GitHub Actions concurrency-test diagnosis (2026-08-07)

- `ExamSessionManagerTest.concurrentStartsLeaveExactlyOneActiveSessionAndNeverReuseExamId`의 CI 실패는 최종 세션 불변식이 아니라 `insert()` 정확히 3회라는 Mockito 검증에서 발생한다.
- latch 해제 후 snapshot을 읽는 현재 테스트에서는 스케줄에 따라 두 initial lookup이 모두 빈 목록을 보아 Duplicate Key와 3회 insert가 발생하거나, 두 번째 lookup이 첫 insert를 보아 정상 abandon 후 총 2회 insert로 완료될 수 있다. 둘 다 최종 active Session 1개와 서로 다른 신규 examId를 만족한다.
- 권장 방향은 snapshot을 latch 대기 전에 고정해 collision을 결정적으로 만들거나, 최종 동시성 불변식 테스트와 Duplicate Key 재시도 테스트를 분리하는 것이다. 이번 진단에서는 코드·테스트·외부 시스템 및 Git·Jira를 변경하지 않았다.

## Latest GitHub Actions concurrency-test resolution (2026-08-07)

- `ExamSessionManagerTest`의 동시 시작 테스트에서 스케줄링 의존적인 `insert()` 정확히 3회 검증을 제거하고, 기존 최종 불변식 검증은 유지했다.
- Duplicate Key retry는 별도 `duplicateKeyDuringSessionCreationRetries` 테스트로 분리했다. 첫 insert 예외, recursive retry, concurrent Session abandon, 두 번째 insert 성공과 정상 Assignment 반환을 Mockito로 결정적으로 검증한다.
- flaky 대상 테스트는 `--rerun-tasks`로 10회 반복해 10/10 성공했다. 전체 `./gradlew test`도 성공했고 Java 296개, failures/errors/skipped 0개다. `git diff --check`가 성공했으며 production 코드는 변경하지 않았다.
- 실제 외부 시스템과 Git·Jira 쓰기 작업은 수행하지 않았고 Secret·Token·Credential을 기록하지 않았다.

## Latest History response inspection (2026-08-07)

- 현재 `GET /api/v1/exams/history`는 JWT sub 사용자의 completedAt이 있는 Session 전체를 completedAt DESC, examId DESC로 반환한다. 결과는 totalCount와 histories이며 항목 필드는 examId, title, cycleNumber, completedAt, totalScore, levelEstimate, summaryAvailable이다.
- Controller가 page·size 또는 Pageable을 받지 않으므로 `?page=0&size=20`은 무시되며 실제 pagination metadata도 없다.
- 현재 main 소스·테스트·Git 이력에 retriedQuestionCount 필드는 없고 History 경로는 Question Job/문항 Result를 조회하지 않는다. 별도 retries API의 questions 크기는 retryCount 1 이상이 존재하는 서로 다른 문항 수지만 History에는 결합되지 않는다.
- 배포 응답에 retriedQuestionCount가 있다면 실행 이미지/커밋 또는 클라이언트·중간 계층 확인이 필요하다. 이번 확인에서는 코드·테스트·외부 시스템과 Git·Jira를 변경하지 않았다.

## Latest TMI-61 History retriedQuestionCount implementation (2026-08-07)

- `GET /api/v1/exams/history`의 각 history 항목에 primitive int `retriedQuestionCount`를 추가했다. 값은 해당 examId에서 `retryCount >= 1`이 존재하는 서로 다른 questionNumber 수이며 없으면 0이다.
- 완료 History examId 전체를 기준으로 QuestionGradingJob과 Legacy ExamResult 후보를 각각 batch 조회하고 `(examId, questionNumber)` Set으로 합친다. 여러 retry 회차와 Job/Legacy 중복은 한 문항으로 계산하며 dispatchAttempt는 사용하지 않는다. 기존 read 인덱스를 재사용하고 N+1 조회를 만들지 않았다.
- JWT sub, completedAt 완료 기준, History 정렬, 신규 Summary 우선·Legacy fallback, 기존 Retries·문항 단건·Summary API와 page·size 미지원 상태는 유지된다.
- 집중 테스트를 이번 turn에 재실행해 성공했고, 전체 `./gradlew clean test`도 Java 297개, failures/errors/skipped 0개로 성공했다. `git diff --check`가 성공했으며 실제 DB apply·explain, Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest TMI-61 History response contract clarification (2026-08-07)

- 현재 `GET /api/v1/exams/history`의 `result`는 `totalCount` 및 `histories`로 구성된다. `exams` 배열과 page·size·totalElements·totalPages·hasNext 메타데이터는 없다.
- History 항목의 현재 필드는 `examId`, `title`, `cycleNumber`, `completedAt`, `totalScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`다. `status`, `maxScore`, `startedAt`은 반환하지 않는다.
- `page`/`size` query parameter는 Controller에 바인딩되지 않아 무시되며 완료 이력 전체가 반환된다. `completedAt`은 `LocalDateTime`이므로 `Z`가 붙지 않는다.
- `retriedQuestionCount`는 `retryCount >= 1`이 존재하는 고유 `questionNumber` 수이며 Job/Legacy 중복과 다중 회차는 한 문항으로 계산한다. 이번 turn에서 애플리케이션·테스트 코드, Git·Jira는 변경하지 않았다.

## Latest TMI-61 History status/maxScore/startedAt implementation (2026-08-07)

- `GET /api/v1/exams/history`의 `histories` 항목에 `status`, `maxScore`, `startedAt`이 additive로 추가됐다. 기존 응답 필드와 `totalCount`/`histories` 구조는 그대로다.
- `status`는 `ExamSession.effectiveStatus()`를 사용해 Legacy 완료 세션도 `COMPLETED`로 보정하고, `startedAt`은 `ExamSession.createdAt`을 가공 없이 반환한다. Legacy 문서에 createdAt이 없으면 `startedAt` 또한 null이다.
- `maxScore`는 현재 모의고사 채점 계약의 고정 만점 200이다. 이 추가로 Repository·MongoDB 문서·인덱스는 변경하지 않았다.
- JWT sub, completedAt 완료 판정, completedAt DESC/examId DESC 정렬, Summary fallback, `retriedQuestionCount`, page·size 미지원은 유지된다.
- 집중 테스트와 `./gradlew clean test`가 성공했고 tests/failures/errors/skipped는 297/0/0/0이다. turn 종료 기록까지 반영했으며 `git diff --check`도 성공했고 Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest TMI-61 History response shape (2026-08-07)

- 현재 성공 응답은 BaseResponse의 `isSuccess`, `code`, `message`, `result`를 사용하고, `result`는 `totalCount`와 `histories`로 구성된다.
- `histories` 항목은 `examId`, `title`, `status`, `cycleNumber`, `startedAt`, `completedAt`, `totalScore`, `maxScore`, `levelEstimate`, `summaryAvailable`, `retriedQuestionCount`를 반환한다.
- `status`는 `ExamSession.effectiveStatus()`, `startedAt`은 `ExamSession.createdAt`, `maxScore`는 200이다. Legacy createdAt 누락 세션은 `startedAt: null`이고 Summary가 없으면 `totalScore`/`levelEstimate`는 null, `summaryAvailable`는 false다.
- page·size는 현재 바인딩하지 않으며 pagination metadata도 없다. 응답 구조 안내 turn 종료 기록까지 반영했고, 코드·테스트·Git·Jira를 변경하지 않았다.

## Latest Part 4 tableImageUrl response-path audit (2026-08-07)

- `Question.tableImageUrl`은 MongoDB `table_image_url`에 명시적으로 매핑된다. URL은 재작성·presign·기본값 생성 없이 저장값을 사용한다.
- `POST /api/v1/exams` 시험 시작 응답은 Part 4 `questions[]` 항목에 `tableImageUrl`을 반환하고 `tableContext`는 제외한다.
- `GET /api/v1/exams/{examId}/questions?questionNumber=...&retryCount=...` 채점 결과 문항 단건의 Part 4 `questionInfo`는 `part`, `questionNumber`, `tableImageUrl`만 반환한다.
- 다만 `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`는 아직 `toQuestionDTO()` 경로라 `tableContext`를 매핑하고 `tableImageUrl`을 매핑하지 않는다. 이번 turn은 읽기 전용 확인으로 코드·테스트·Git·Jira를 변경하지 않았다.

## Latest TMI-61 Retries response-shape audit (2026-08-07)

- `GET /api/v1/exams/{examId}/retries`의 `result`는 `examId`, `questions`고, 문항 항목은 `partNumber`, `questionNumber`, `totalAttemptCount`, `latestRetryCount`, `attempts`를 반환한다.
- 각 `attempts[]`는 `retryCount`, `status` 두 필드만 반환한다. `score`, `completedAt`, 피드백·음성·Transcript는 노출하지 않는다. 상태는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`다.
- Job과 Legacy Result를 `(questionNumber,retryCount)`로 합치고 Job status를 우선하며, Legacy-only 회차는 `COMPLETED`다. 실제 저장된 회차만 retryCount 오름차순으로 반환한다.
- `retryCount >= 1`이 하나도 없는 문항은 제외하고 저장된 0회차는 포함하지만 없는 0회차를 생성하지 않는다. 응답 비교 turn 종료 기록까지 반영했고 코드·테스트·Git·Jira를 변경하지 않았다.

## Latest TMI-61 Retries score/completedAt implementation (2026-08-07)

- `GET /api/v1/exams/{examId}/retries`의 각 `attempts[]`는 `retryCount`, `status`, `score`, `completedAt`을 반환한다. `score`는 Double, `completedAt`은 UTC `Instant`이므로 JSON에서 `Z` suffix ISO-8601 문자열이다.
- `score`는 `ExamResult.score`, `completedAt`은 `QuestionGradingJob.completedAt`에서 가져온다. Legacy Result-only 회차는 `completedAt=null`, Job-only 회차는 `score=null`이다.
- Job/Result가 겹치면 Job status·completedAt과 Result score를 함께 보존한다. 기존 dedupe, Job 상태 우선, Legacy-only `COMPLETED`, 정렬, 소유권, 빈 결과 계약은 유지된다.
- Repository는 Result `score`와 Job `completedAt`만 추가 projection하고 피드백·Transcript·음성 URL·`dispatchAttempt`·내부 userId는 노출하지 않는다. MongoDB 문서·인덱스 변경은 없다.
- 집중 테스트와 `./gradlew clean test`가 성공했고 tests/failures/errors/skipped는 298/0/0/0이다. `git diff --check`도 성공했으며 Git·Jira 쓰기 작업은 수행하지 않았다.

## Latest TMI-77 Part 4 table_context implementation (2026-08-07)

- Jira `TMI-77` `[Learning Core] Part 4 table_context 원본 응답 통일`을 생성하고 구현한 뒤 사용자 요청에 따라 상태와 resolution을 `완료`로 전환했다. Jira 댓글·기타 필드는 변경하지 않았고 Git commit·push·PR은 생성하지 않았다.
- `Question.tableContext`와 응답 `QuestionDTO.tableContext`는 `Map<String, Object>`다. Mongo 최상위 `table_context`만 API `tableContext`로 연결하며 내부 임의 키, 중첩 객체·배열, null과 snake_case는 이름 변경이나 고정 구조 생성 없이 보존한다.
- 시험 시작 `POST /api/v1/exams`, 채점 결과 문항 단건 `GET /api/v1/exams/{examId}/questions`, 문제 prompt `GET /api/v1/exams/{examId}/questions/{questionNumber}/prompt`의 Part 4가 동일한 원본 Map을 반환한다. 응답 DTO에는 `tableImageUrl`이 없으며 Mongo 내부 `table_image_url` 필드와 기존 데이터는 유지한다.
- Part 4 `table_context=null`은 기존 catalog configuration 오류, 빈 Map은 정상 빈 객체 응답이다. Part 1·2·3·5·6·7, BaseResponse와 URL·파라미터, AI 요청·Callback, Summary, JWT 소유권은 유지된다.
- 실제 `MappingMongoConverter`, 세 API JSON, null/empty, 다른 Part, AI dispatch, 문항 경로, JWT 집중 테스트와 전체 `./gradlew clean test`가 성공했다. 전체 tests/failures/errors/skipped는 `303/0/0/0`, `git diff --check`도 성공했다. 실제 MongoDB와 외부 인프라는 호출하거나 수정하지 않았다.
- 배포 전 프론트가 기존 Part 4 `tableImageUrl` 대신 비정형 `tableContext`와 DB 내부 키 이름을 그대로 처리하는지 확인해야 한다. 모든 운영 Part 4 문서에 `table_context`가 존재하는지도 별도 읽기 전용 점검이 필요하다.
- Jira `TMI-77` 완료 처리 turn의 WORKLOG 기록까지 반영됐다. 종료 처리 이후 애플리케이션·테스트 코드는 추가 변경하지 않았다.
- 운영 로그 후속 검토 결과, 세 API의 Part 4 성공/누락 로그는 아직 구현되지 않았다. 구현한다면 Service 경계에서 operation, examId, questionNumber, fieldCount만 INFO/WARN으로 남기고 tableContext 원문·키·값과 URL·Token은 금지하는 방향이다.

## Latest operations logging analysis (2026-08-07)

- 운영 로그 추가는 권장하지만, 현재 가장 큰 공백은 Summary dispatch, 시험 단위 retry 결과, Job 상태 전환과 submit-to-callback 지연 시간이다. 폴링 요청별 INFO 로그는 대량 중복을 만들므로 상태 전환만 기록해야 한다.
- 신규 로그는 안정적인 `event`/`outcome`과 `jobId`, `examId`, question/retry/attempt, `durationMs`, 제한된 reason만 사용한다. 실제 `userId`, 음성·Transcript·Callback/Table Context 원문, Presigned URL, Token·Secret은 기록하지 않는다.
- 기존 `ExamSessionManager`의 `userId`·abandoned ID 목록, `GradingDispatchService`의 `fileKey`, `GlobalExceptionAdvice`의 `printStackTrace()`·예외 원문은 새 로그 추가 전에 정리할 후보다. 현재 저장소에는 Sentry error와 Actuator health 외의 로그 집계·알림 구성이 확인되지 않아 배포 환경의 수집기·대시보드·알림 연결이 별도로 필요하다.
- 이번 검토에서는 애플리케이션·테스트 코드와 외부 API·AI/Callback·Redis/S3 계약을 변경하지 않았다. Jira `TMI-77`은 완료 상태로 유지하며 신규 Jira 작업이나 Jira/Git 쓰기는 수행하지 않았다.

## Latest AI communication logging cleanup (2026-08-07)

- 최초 Question AI 전송의 기본 로그는 기존 약 6줄에서 `event=grading.question.dispatch` 성공 한 줄로 줄었다. 실패는 Mongo Job 실패 전이가 실제 반영된 경우에만 ERROR이며, 오래된 attempt 실패는 DEBUG no-op이다. 로그에는 `jobId`, `examId`, question/retry/attempt와 `durationMs`만 남기고 URI, S3 key, 오디오 크기와 예외 메시지는 제외한다.
- Feedback Controller 수신 로그와 adapter 내부 POST 단계 로그는 제거했다. 핵심 Feedback/Summary 저장은 INFO 한 줄, 중복 및 SpeechAce·Azure 보조 Callback은 DEBUG다. Summary dispatch 성공/실패, executor rejection과 시험 단위 retry 집계는 별도 단일 이벤트로 추적한다.
- 시험 생성 로그는 실제 `userId`와 abandoned ID 목록 없이 생성 결과 한 줄만 남긴다. 전역 JSON 파싱 오류도 `printStackTrace()`와 원문 로그 대신 예외 타입만 포함한 WARN 한 줄로 정리했다.
- 로그 전용·동시성 집중 테스트와 전체 `./gradlew clean test`가 성공했다. 전체 tests/failures/errors/skipped는 `303/0/0/0`이고 `git diff --check`도 성공했다. 외부 API·DTO·BaseResponse, AI `user_id=examId`와 Callback JSON, retryCount, Redis/S3 계약은 그대로다.
- 관련 Jira `TMI-25`와 `TMI-77`은 완료 상태를 유지하며 Jira 쓰기 작업은 없었다. 로그 집계·대시보드·알림 연결과 request/trace correlation은 아직 별도 운영 과제로 남는다.

## Latest monitoring logging plan (2026-08-07)

- 이번 계획 작업에는 별도 Jira 이슈 키가 없다. 현재 워킹 트리의 기존 구조화 로그와 미커밋 로그 정리 변경을 보존했으며 애플리케이션·테스트 코드는 수정하지 않았다.
- 현재 정상 흐름은 세션 생성, Question/Summary dispatch, 시험 retry 집계와 핵심 Callback 저장 로그로 일부 연결된다. 다음 구현 우선순위는 세션 폐기·완료, Question/Summary Job 완료와 최대 attempt, Summary Trigger 결정, Callback 거절, 안전한 인증/비즈니스/5xx 오류 경계다.
- Polling 성공을 요청마다 INFO로 기록하지 않고 상태 전이와 장기 PROCESSING만 관측한다. 내부 requestId/MDC는 동기 요청을 연결하고, submit과 별도 Callback 요청은 외부 계약 변경 없이 `examId`·`jobId`로 연결한다.
- 외부 I/O 장애는 Presigned URL·S3 Key·예외 메시지를 남기지 않은 채 S3 다운로드와 AI POST 단계, 결과, attempt, 소요 시간만 구분한다. INFO/WARN/ERROR 기준과 안정적인 key-value 이벤트명을 먼저 고정하고 Sentry의 중복 오류 이벤트도 방지한다.
- 실제 사용자 ID, Token·Secret, 음성·Transcript, Callback/Feedback/Azure/SpeechAce/Table Context 원문과 내부 키·값은 로그 금지 대상이다. 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, retryCount, Redis와 S3 계약은 그대로 유지한다.
- 구현 시 로그 캡처 단위 테스트, 중복·동시성 회귀, 민감값 부재 검사를 추가한 뒤 `./gradlew clean test`와 `git diff --check`를 실행한다. CloudWatch 등 실제 수집 대상과 retention이 저장소에 없으므로 배포 전 확정하고 장기 PROCESSING, dispatch 실패, queue rejection, max attempts, completion race와 5xx 알림을 연결해야 한다.

## Latest monitoring logging implementation (2026-08-07)

- 별도 Jira 이슈 키 없이 운영 모니터링 로그 계획을 구현했다. 요청 필터가 내부 UUID `requestId`를 MDC에 설정·정리하고 Summary executor의 TaskDecorator가 MDC를 작업 스레드에 복사·복원한다. requestId를 외부 응답 헤더나 DTO에는 추가하지 않았다.
- 시험 세션 폐기·충돌 재시도·완료, Question/Summary Job 완료·최대 attempt, Summary Trigger 판단·예약, Callback 분류 거절, 소유권 거절과 조회 데이터 누락을 구조화 이벤트로 관측한다. 일반 요청과 정상 Polling은 DEBUG이며, 중요한 상태 전이는 정확히 한 번 기록되도록 멱등·동시성 경로를 보존했다.
- 외부 dispatch는 `s3_download`와 `ai_post` stage 및 `durationMs`로 실패 위치를 구분한다. 로그와 Sentry에는 실제 userId, Authorization/JWT, Secret·Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext payload, 예외 메시지를 넣지 않는다. 잘못된 JSON은 Sentry 대상에서 제외하고 예상하지 못한 5xx만 예외 타입 tag를 포함한 안전한 단일 메시지 이벤트로 전송한다.
- 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, retryCount, Redis Key/TTL과 S3 Object Key는 유지했다. 실제 외부 인프라는 호출하지 않았다.
- 집중 테스트와 `./gradlew clean test --no-daemon`이 성공했다. 전체 tests/failures/errors/skipped는 `316/0/0/0`이며 `git diff --check`도 성공했다.
- CloudWatch 로그 그룹·retention·metric filter·dashboard·alarm은 저장소 외부 운영 설정으로 남아 있다. Callback이 아예 도착하지 않는 경우와 장기 `PROCESSING` Job은 로그만으로 직접 검출할 수 없어 별도 metric/watchdog 결정이 필요하다.

## Latest monitoring log language recommendation (2026-08-08)

- 운영자가 빠르게 읽을 수 있도록 로그의 고정 설명 문장은 한글화하는 방향을 권장한다. 다만 자동 검색·집계·알림 계약인 `event`, `outcome`, `reason`, `stage`, 상태값과 필드명은 영문으로 유지한다.
- 권장 형식은 `문항 채점 작업 완료 event=grading.question.job.completed outcome=success ...`와 같은 혼합형이다. 기존 event code를 유지하면 향후 CloudWatch metric filter와 dashboard를 언어에 의존하지 않고 구성할 수 있다.
- 한글 설명에는 예외 메시지나 payload를 삽입하지 않는다. 실제 userId, Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext 원문을 남기지 않는 기존 로그 보안 원칙도 유지한다.
- 이번 검토에서는 운영 코드와 테스트를 변경하거나 실행하지 않았다. 한글화 구현 여부와 적용 범위는 사용자 확인 후 결정한다.

## Latest monitoring log Korean descriptions (2026-08-08)

- 운영 로그의 사람이 읽는 고정 설명을 한글로 변경했다. 적용 범위는 HTTP 요청·인증·전역 예외, 시험 세션·소유권·이력, Question/Summary 채점 Job·Trigger·dispatch, Callback, S3/AI 단계, MongoDB 인덱스와 MockExam 카탈로그다.
- 자동 검색·집계·알림 계약인 `event`, `outcome`, `reason`, `stage`, 상태값과 구조화 필드명은 영문으로 유지했다. HEAD 대비 현재 `event` 코드 목록의 정적 비교 결과가 동일하다.
- 실제 userId, Authorization/JWT, Secret·Token, URL·S3 Key, 음성·Transcript, Callback/채점/tableContext payload와 예외 메시지를 로그에 넣지 않는 보안 원칙은 유지한다. 외부 API·AI/Callback·Redis/S3 계약도 변경하지 않았다.
- 한글 설명과 기존 event code의 동시 출력을 대표 로그 캡처 테스트로 검증했다. 집중 테스트와 전체 `./gradlew clean test --no-daemon`이 성공했으며 tests/failures/errors/skipped는 `316/0/0/0`, `git diff --check`도 성공했다.
- CloudWatch 등 수집 환경은 UTF-8 출력을 기준으로 확인해야 한다. metric filter·dashboard·alarm은 한글 문장보다 유지된 `event` 코드와 영문 구조화 필드를 기준으로 구성한다.

## Latest Sentry DSN readiness audit (2026-08-10)

- Sentry Spring Boot starter가 이미 포함되어 있고 `application.yml`은 실제 값을 저장하지 않은 채 `SENTRY_DSN` 환경변수를 참조한다. DSN 자체를 코드 변경이나 채팅으로 전달할 필요가 없다.
- 기본 설정은 `send-default-pii=false`, active profile 기반 environment, trace sampling 0이며 테스트는 비운영 DSN과 sampling 0을 사용한다. 전역 예외 처리의 예상하지 못한 5xx는 안전한 고정 메시지와 예외 타입 tag로 명시적으로 수집한다.
- 적용 단계는 배포 환경 Secret에 `SENTRY_DSN`을 등록하고 애플리케이션을 재시작한 뒤 의도적으로 발생시킨 비민감 테스트 오류가 해당 environment로 수집되는지 확인하는 것이다. tracing이 필요할 때만 `SENTRY_TRACES_SAMPLE_RATE`를 별도로 결정한다.
- 이번 확인에서는 운영 코드·테스트·외부 시스템을 변경하지 않았고 실제 DSN이나 Secret을 조회·기록하지 않았다.

## Latest Sentry production-readiness assessment (2026-08-10)

- 현재 설정은 DSN 환경변수, `send-default-pii=false`, ERROR 자동 수집, trace sampling 0과 4xx WARN 제외를 사용하므로 초기 오류 수집과 노이즈 제어 관점에서는 적절하다.
- 운영 분석의 최우선 공백은 예상하지 못한 5xx가 안전한 `captureMessage`로만 수집되어 예외 타입 tag는 있지만 원본 스택트레이스가 없다는 점이다. 예외 메시지·payload를 제거하면서 스택 프레임을 보존하는 안전한 Sentry event sanitizing 설계와 테스트가 필요하다.
- 배포 추적을 위해 `SENTRY_RELEASE`, 환경 오분류 방지를 위해 명시적인 `SENTRY_ENVIRONMENT`를 배포 Secret/환경변수로 주입하는 것을 권장한다. tracing은 성능 관측 요구가 생길 때 별도 sampling 정책을 정한다.
- DSN 주입 후 staging의 비민감 5xx 테스트로 수집, environment/release, 중복 이벤트, stack trace, PII 부재를 확인하고 Sentry 프로젝트의 Alert Rule도 별도로 구성해야 한다.
- 이번 검토에서는 코드·테스트·외부 시스템을 변경하지 않았고 실제 자격정보를 조회·기록하지 않았다.

## Latest Sentry production hardening plan (2026-08-10)

- 상세 계획서는 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`에 있다. 별도 Jira 이슈 키는 없다.
- P0 순서는 `SENTRY_ENVIRONMENT`·`SENTRY_RELEASE` 명시, `BeforeSendCallback` sanitizer, `IHub` 기반 reporter와 안전한 `captureException`, 인메모리 transport 중복·PII 테스트, staging smoke 검증이다.
- sanitizer는 exception value와 자유 형식 message, request data/query/cookie/header, user와 비허용 breadcrumb/extra를 제거하고 예외 type·module·mechanism·stack frame 및 제한된 분류 tag만 보존한다.
- 기본 order 1의 자동 `SentryExceptionResolver`, 명시적 reporter와 Logback ERROR 경로가 겹칠 수 있으므로 5xx 1건, 4xx 0건, grading ERROR 의도 건수를 통합 테스트로 고정한다.
- SDK 8.x 업데이트와 Sentry Alert Rule은 capture 동작을 현재 7.14.0에서 먼저 고정한 후 별도 P1 단계로 진행한다. tracing은 초기 0을 유지하고 실제 DSN은 배포 Secret으로만 주입한다.
- 계획 작성만 완료했으며 운영·테스트 코드와 외부 시스템은 변경하지 않았다. 구현 전 배포 환경변수 주입 방식, release 규칙, grading ERROR 수집 정책, Alert 임계값과 SDK 업데이트 순서를 확정해야 한다.

## Latest Sentry production hardening plan revision (2026-08-11)

- 상세 계획서는 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`에 있다. 별도 Jira 이슈 키는 없고 아직 애플리케이션 구현 전이다.
- 초기 운영 역할은 `Sentry=조사가 필요한 예외`, `CloudWatch=구조화 운영 로그`로 확정했다. `sentry.logging.enabled=false`로 Sentry LogbackAppender를 끄고, 예상하지 못한 Controller 5xx만 명시적 `captureException` 1건과 안전한 CloudWatch ERROR 1건으로 남긴다. grading·AI dispatch·Callback ERROR는 초기 Sentry event 0건이다.
- `BeforeSendCallback`은 fail-closed로 동작한다. event·exception message뿐 아니라 request·transaction·user·breadcrumb·비허용 tag/extra/context, stack local·절대 경로·source context·register·lock, mechanism 자유 형식 map과 SDK unknown field를 제거하고 실패하면 원본 대신 event를 drop한다.
- `BeforeSendCallback`이 attachment와 tracing transaction까지 정제하지는 않으므로 초기 reporter의 attachment를 금지하고 envelope item 0건을 검사한다. tracing은 0을 유지하며 활성화 전 별도 transaction sanitizer 검토가 필요하다.
- Sentry에는 저카디널리티 오류 분류와 형식 검증된 `correlation.request_id` context만 허용한다. examId·jobId·questionNumber·retryCount는 CloudWatch에서 조회하며 release는 CI source context와 ECS runtime 모두 `app-back-end-learning-core@<git-sha>`로 맞춘다.
- SDK 7.14.0에서 sanitizer·호출별 local scope reporter·자동 resolver·recording transport의 정확히 1회 동작을 먼저 고정한다. 모든 위치의 가짜 민감 marker가 최종 직렬화 JSON에 없는지, 일반 ERROR의 Sentry event가 0건인지, 연속 capture 간 metadata가 누출되지 않는지와 reporter 실패에도 기존 응답 계약이 유지되는지를 테스트하고 8.x 업그레이드는 별도 이슈로 분리한다.
- Spring MVC 6.2.2의 기본 exception resolver composite order 0과 Sentry resolver order 1을 확인했다. Advice가 처리한 예외는 명시적 reporter만, 앞선 resolver가 처리하지 못한 예외는 자동 resolver만 타는 현재 전제를 통합 테스트의 최종 event 건수로 고정한다.
- 실제 DSN·Secret·Token은 기록하지 않고 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key를 유지한다. 이번 보정은 문서만 변경했으며 배포 workflow·Sentry 프로젝트·애플리케이션·테스트 코드는 변경하지 않았다.
- 추적 문서 정적 검증으로 tracked 변경과 신규 계획서 모두 whitespace 오류가 없음을 확인했다. 문서 전용 작업이라 Gradle 테스트는 실행하지 않았다.

## Latest Sentry production hardening implementation (2026-08-11)

- 별도 Jira 이슈 키 없이 `docs/codex/SENTRY_PRODUCTION_HARDENING_PLAN.md`의 P0 애플리케이션 구현과 자동 검증을 완료했다. 실제 배포 환경과 외부 Sentry 프로젝트는 변경하지 않았다.
- 설정은 `SENTRY_ENVIRONMENT`·`SENTRY_RELEASE` 환경변수를 지원하고 request body 비수집, resolver order 1, `sentry.logging.enabled=false`, trace sampling 기본 0을 명시한다. 따라서 일반 grading·AI dispatch·Callback ERROR는 CloudWatch에 남고 Logback을 통해 Sentry Issue로 자동 승격되지 않는다.
- 예상하지 못한 ControllerAdvice 5xx는 `UnexpectedExceptionReporter`를 통해 `captureException` 정확히 1건과 원문 없는 한글 CloudWatch ERROR 정확히 1건을 남긴다. 4xx와 JSON 파싱·비즈니스 오류는 reporter를 호출하지 않으며 reporter 실패도 기존 응답을 바꾸지 않는다.
- `SentryEventSanitizer`는 fail-closed `BeforeSendCallback`으로 event/exception message, request/user/breadcrumb, transaction/fingerprint, 비허용 tag·context·extra, module/dist, stack local·절대 경로·source context·register·lock·주소, mechanism 자유 형식 map과 unknown field를 제거한다. 예외 type과 애플리케이션 stack class/method/file/line, environment/release, 안전한 분류와 UUID requestId context만 남긴다.
- reporter의 local scope는 parent request/user/breadcrumb/attachment뿐 아니라 session·propagation baggage·replay ID까지 초기화한다. 기본 Sentry resolver도 `SanitizedSentryExceptionResolver`로 교체해 같은 격리 reporter를 사용하고, `UnhandledExceptionCaptureFilter`는 하위 필터의 `ServletException`·`RuntimeException`을 1회 보고하며 request attribute로 resolver 중복을 막는다. `IOException`은 연결 종료 노이즈 가능성 때문에 자동 Issue로 보내지 않는다.
- 인메모리 recording transport에서 연속 두 capture의 scope 누출 없음, event·envelope 전체의 가짜 민감 marker 부재, attachment/session item 부재, stack 보존, handled/unhandled 정확히 1건, sanitizer 실패 시 0건, Logback initializer 부재를 검증했다. 실제 외부 Sentry는 호출하지 않았다.
- 최종 `./gradlew clean test --no-daemon`은 tests/failures/errors/skipped `332/0/0/0`, `git diff --check`와 민감정보 패턴 검사는 성공했다. 기존 `ExamServiceImpl` unchecked 경고만 남았으며 이번 범위와 무관하다.
- 공개 API URL·Method·Request/Response DTO·`BaseResponse`, 실제 userId 비노출, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL과 S3 Object Key는 변경하지 않았다. 기존 500 응답 body가 내부 예외 메시지를 담을 수 있는 위험도 호환성 때문에 이번 작업에서는 변경하지 않았다.
- 운영 전 남은 작업은 실제 배포 환경의 DSN·environment·`app-back-end-learning-core@<git-sha>` release 주입, CI/ECS 값 일치 확인, staging smoke, Sentry IP 저장 방지와 Alert Rule 설정이다. SDK 7.14.0에서 8.x 업그레이드와 tracing 활성화는 별도 작업으로 유지한다.

## Latest Sentry deployment environment variables (2026-08-11)

- 별도 Jira 이슈 키 없이 현재 `application.yml`, Dockerfile과 배포 파일 존재 여부를 기준으로 Sentry 환경변수를 정리했다. 저장소에는 ECS Task Definition이나 GitHub Actions 배포 Workflow가 없으므로 실제 환경변수 주입은 아직 저장소 밖 배포 설정에서 수행해야 한다.
- staging/prod 런타임 필수 항목은 보호 저장소에서 주입할 `SENTRY_DSN`, 명시적인 `SENTRY_ENVIRONMENT=staging|prod`, 배포 산출물의 전체 Git SHA를 사용한 `SENTRY_RELEASE=app-back-end-learning-core@<git-sha>`다. 같은 배포의 CI release 값과 ECS runtime 값을 반드시 일치시킨다.
- `SENTRY_TRACES_SAMPLE_RATE`는 선택 항목이며 현재는 미설정 또는 `0.0`으로 유지한다. tracing을 활성화하기 전에는 transaction 데이터 정제와 sampling 비용 정책을 별도로 검토해야 한다.
- 기존 `SPRING_PROFILES_ACTIVE`는 Sentry 전용 신규 값은 아니지만 staging/prod에서 각각 명시해야 한다. `SENTRY_ENVIRONMENT`가 없으면 Spring profile로 fallback하지만 운영 오분류 방지를 위해 두 값을 모두 명시한다.
- `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, `SENTRY_PROJECT`는 현재 런타임 이벤트 전송에 필요하지 않으며 추가하지 않는다. 향후 CI에서 Sentry release 생성이나 source context 업로드를 도입할 때만 별도 최소 권한 CI Secret으로 검토한다.
- 실제 DSN·Secret·Token은 조회하거나 기록하지 않았다. 애플리케이션·테스트·배포 파일과 외부 API·AI/Callback·Redis/S3 계약은 변경하지 않았고, 문서 갱신 외 코드 변경이 없어 Gradle 테스트는 다시 실행하지 않았다.

## Latest empty Summary feedback recovery plan (2026-08-11)

- 별도 신규 Jira 이슈 키는 제공되지 않았으며, 기존 `TMI-25`에서 구현한 시험 단위 `POST /api/v1/exams/{examId}/grading/retry`와 Question/Summary Job 흐름을 활용하는 후속 계획이다. 상세 내용은 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`에 있다.
- 현재 Summary Callback은 `partFeedback` null/empty를 검사하지 않아 빈 객체도 `ExamSummary` 저장, Summary Job 완료와 ExamSession 완료로 이어질 수 있다. 계획은 빈/null Map이면 Summary를 저장하지 않고 Job을 `FAILED`, reason=`FEEDBACK_GENERATION_FAILED`로 만들며 Session을 `IN_PROGRESS`로 유지한다.
- 프론트의 주 오류 수신 경계는 기존 `GET /api/v1/exams/{examId}/status`로 권장했다. 기존 `BaseResponse` 구조에서 exact code `FEEDBACK_GENERATION_FAILED`, message `피드백 생성에 실패했습니다.`를 non-2xx로 반환하고, Summary 조회도 같은 상태에서 동일 오류를 반환한다. AI Callback은 실패 상태를 영속화한 뒤 200 delivery acknowledgement를 반환하는 안을 권장한다.
- Summary 시작 근거는 배정된 MockExam의 모든 필수 문항에 대한 실제 최초 `ExamResult` 존재로 강화한다. 현재 문제지는 카탈로그 계약 테스트로 1~11번을 고정하되 별도 하드코딩 목록이나 프론트 입력을 추가하지 않는다. `QuestionGradingJob=COMPLETED`만 있고 결과가 없는 문항은 `QUESTION_RESULT_MISSING` 복구 대상으로 취급한다.
- 프론트 retry가 들어오면 실패한 Summary Job을 다음 generation의 `PENDING`으로 먼저 재무장한다. 모든 결과가 이미 있으면 Question dispatch 없이 Summary만 예약하고, 누락 문항이 있으면 해당 문항만 복구한 뒤 마지막 valid Callback에서 PENDING Summary를 한 번 예약한다.
- 반복 재생성을 위해 내부 `generationAttempt`와 기존 `dispatchAttempt` 분리를 권장했다. Summary document/job ID와 JSON 계약은 유지하되 generation 2 이상 멱등 키를 구분해야 실제 AI 재생성이 보장되므로 Python AI의 `Idempotency-Key` 캐시 정책 확인이 구현 전 필수다. Callback JSON에 generation이 없어 stale Callback 완전 구분은 불가능하므로 valid Summary와 COMPLETED가 항상 우선하도록 단조성을 보장한다.
- 기존에 이미 빈 Summary가 저장되고 Session이 COMPLETED인 데이터는 새 Callback 검증만으로 복구되지 않는다. 배포 전 읽기 전용 집계 후 별도 승인된 복구 runbook 범위를 정하며, 계획 작업에서 운영 데이터나 완료 Session을 변경하지 않았다.
- 이번 turn은 계획·상태·작업 기록 문서만 변경했다. 애플리케이션·테스트·외부 시스템은 변경하지 않았고 Gradle 테스트도 실행하지 않았다. 실제 Secret·Token과 Callback payload를 조회하거나 기록하지 않았다.

## Latest Summary generation fencing plan revision (2026-08-11)

- 별도 신규 Jira 이슈 키는 제공되지 않았고, 기존 `TMI-25`의 시험 단위 grading retry를 활용하는 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`에 사용자가 확정한 `generationAttempt` 계약과 동시성 보강안을 반영했다.
- Question AI Request/Callback은 그대로 유지한다. Summary에만 `generation_attempt`를 추가하며 Learning Core가 generation을 생성·증가시키고 Python AI는 요청 값을 Callback에 그대로 echo한다. Callback 값이 누락되거나 현재 `SummaryGradingJob.generationAttempt`와 다르면 empty/valid 여부와 관계없이 stale no-op한다.
- `generationAttempt`는 `FAILED/FEEDBACK_GENERATION_FAILED` 상태에서 사용자가 기존 grading retry API를 호출할 때만 1 증가한다. 새 generation은 `PENDING`, `dispatchAttempt=0`으로 시작하고 동일 generation의 transport timeout·전송 실패·retry는 generation을 유지한 채 dispatch attempt만 증가한다. legacy Mongo Job의 누락 필드는 generation 1로 해석한다.
- Summary Scheduler, claim, dispatch 전 검증과 완료·실패 갱신에 generation fencing을 적용한다. 이전 generation worker는 새 generation의 status·dispatch attempt를 바꾸지 않고, 전송 직전 stale이면 외부 요청도 보내지 않는다. 이미 전송된 이전 요청의 Callback은 Callback generation fence로 차단한다.
- Callback의 최초 generation 조회와 실제 Summary 저장 사이 경합도 막기 위해 저장 직전 `jobId + generation + status + version` completion claim 또는 Mongo transaction을 요구한다. 전체 COMPLETED 판정은 유효 Summary 저장 결과까지 확인해 Job만 완료된 중간 상태를 외부 완료로 노출하지 않는다.
- 실제 최초 `ExamResult`가 없고 Question Job만 COMPLETED이면 `QUESTION_RESULT_MISSING`으로 분류한다. Job을 version/recovery-cycle 조건으로 PENDING re-open하고 `dispatchAttempt=0`으로 초기화해 새 복구 사이클의 max attempt 정책을 적용하며, Question wire 계약은 변경하지 않는다.
- Summary Idempotency-Key는 generation 1에서 기존 Job ID, generation 2 이상에서 `:generation:<n>` suffix를 사용한다. 같은 generation transport retry는 같은 키를 쓴다. 프론트는 기존 status/Summary 조회의 HTTP 500 `FEEDBACK_GENERATION_FAILED` exact code로 기존 grading retry를 호출하고 다른 5xx/FAILED 동작은 유지한다.
- 배포는 Python AI의 generation echo를 먼저 적용하되 전환 기간 동안 필드 없는 구버전 Summary 요청을 generation 1로 처리해 echo한 뒤 Learning Core를 배포한다. staging에서 generation별 키 독립 처리, 구버전/in-flight Callback, stale worker를 확인하고 기존 empty Summary는 별도 읽기 전용 집계와 승인된 복구 runbook으로 분리한다.
- 이번 turn은 계획·상태·작업 기록 문서만 변경했다. 애플리케이션·테스트·외부 시스템을 변경하지 않았고 Gradle 테스트를 실행하지 않았다. 실제 Secret·Token·Callback payload를 조회하거나 기록하지 않았으며 공개 프론트 API·DTO·Redis/S3 계약도 변경하지 않았다.

## Latest Summary generation recovery implementation (2026-08-11)

- Jira `TMI-25` 후속 범위로 `docs/codex/FEEDBACK_GENERATION_RECOVERY_PLAN.md`의 Summary 재생성과 Question 결과 누락 복구를 구현했다. 신규 공개 API는 추가하지 않았고 기존 `POST /api/v1/exams/{examId}/grading/retry`의 body 없음 및 `GradingRetryResult` 계약을 유지한다.
- `SummaryGradingJob`은 legacy 누락 값을 1로 해석하는 `generationAttempt`와 completion claim을 가진다. `FAILED/FEEDBACK_GENERATION_FAILED`에서 사용자가 retry할 때만 Mongo 조건부 update로 다음 generation을 열고 `dispatchAttempt=0`, `PENDING`으로 재무장한다. transport retry는 같은 generation에서 dispatch attempt만 증가한다.
- Summary Request와 Callback에만 `generation_attempt`를 추가했다. generation 1은 기존 `summary:<examId>:v1`, generation 2 이상은 `summary:<examId>:v1:generation:<n>` Idempotency-Key를 사용한다. Scheduler task와 dispatch claim은 generation을 캡처하고 claim 전·AI 전송 직전·실패 갱신에서 현재 generation을 확인한다.
- Summary Callback은 generation 누락·불일치를 empty/valid 모두 stale no-op한다. 현재 generation의 null/empty `partFeedback`은 Summary를 저장하거나 Session을 완료하지 않고 Job을 `FAILED/FEEDBACK_GENERATION_FAILED`로 만든다. valid Callback은 generation 조건의 completion claim을 먼저 획득한 뒤 결정적 Summary 저장, Job COMPLETED, Session COMPLETED, 기존 Redis status projection 순으로 수렴한다.
- 실제 최초 `ExamResult`만 Summary 준비 근거로 사용한다. 결과 없이 `QuestionGradingJob=COMPLETED`이면 `QUESTION_RESULT_MISSING`으로 보고 원자적으로 PENDING re-open하며 `dispatchAttempt=0`과 새 내부 recovery cycle로 해당 문항만 다시 보낸다. stale 이전 cycle의 전송 실패는 새 cycle 상태를 변경하지 않는다.
- 기존 status와 Summary 조회는 유효 Summary가 없는 현재 Summary Job의 `FAILED/FEEDBACK_GENERATION_FAILED`를 HTTP 500, code `FEEDBACK_GENERATION_FAILED`, message `피드백 생성에 실패했습니다.`의 기존 `BaseResponse`로 반환한다. 다른 5xx/FAILED 처리와 공개 프론트 DTO는 변경하지 않았다.
- 공개 API URL·Method·기존 Request/Response DTO·`BaseResponse`, `user_id=examId`, `retryCount`, Question AI Request/Callback, Redis Key/TTL, S3 Object Key와 소유권 검증을 유지했다. 실제 외부 Python AI, MongoDB, Redis, S3, Sentry와 배포 환경은 호출하거나 변경하지 않았다.
- 최종 `./gradlew clean test`는 tests/failures/errors/skipped `351/0/0/0`으로 성공했고 `git diff --check`도 성공했다. 기존 `ExamServiceImpl` unchecked 경고는 이번 범위와 무관하게 유지했다.
- 배포 전 Python AI의 generation echo와 generation별 Idempotency-Key 독립 처리를 먼저 적용해야 한다. generation 없는 in-flight 구버전 Callback 가능성과 기존 empty/null Summary 데이터는 staging 확인 및 별도 승인된 읽기 전용 집계·복구 runbook 대상으로 남아 있다.

## Latest daily work summary for 2026-08-11 through 2026-08-13 (2026-08-14)

- 기존 작업 기록의 연속된 주제와 진행 순서를 날짜별로 재분류했다. 원래 WORKLOG 항목은 변경하거나 삭제하지 않았으며, 상세 정정 요약은 WORKLOG의 2026-08-14 항목에 있다.
- **2026-08-11:** 별도 Jira 이슈 없이 Sentry 운영 보완 정책 확정, fail-closed event sanitizing, 예상하지 못한 5xx 단일 수집, scope 격리와 recording transport 검증을 구현했다. 전체 테스트 결과는 `332/0/0/0`이다.
- **2026-08-12:** Jira `TMI-25` 후속 범위로 빈 Summary feedback 실패 처리, 사용자 retry 기반 `generationAttempt`, generation fencing과 누락 Question 결과 복구 계획을 확정했다. 계획·상태 문서만 변경한 단계다.
- **2026-08-13:** Jira `TMI-25` 후속 Summary 재생성과 Question 결과 누락 복구를 구현하고 stale worker/Callback 및 동시 retry 회귀 테스트를 보강했다. 전체 테스트 결과는 `351/0/0/0`이다.
- 공개 API·DTO·`BaseResponse`, `retryCount`, AI/Callback `user_id=examId`, Redis Key/TTL, S3 Object Key와 시험 소유권 검증 계약은 유지됐다.
- 남은 배포 확인 사항은 Python AI의 `generation_attempt` echo와 generation별 Idempotency-Key 독립 처리, in-flight legacy Callback, 기존 empty/null Summary 데이터의 읽기 전용 집계 및 승인된 복구 runbook이다.
- 이번 날짜별 정리는 문서만 변경했으며 Gradle 테스트는 다시 실행하지 않았다. 외부 시스템·Jira 상태·Git commit·push는 변경하지 않았다.

## Latest in-chat blog draft delivery (2026-08-14)

- 사용자의 정정에 따라 2026-08-11부터 2026-08-13까지의 작업 내용을 저장소 문서가 아닌 대화창에서 사용할 블로그 글 초안으로 제공한다.
- 구성은 8월 11일 Sentry 운영 보완, 8월 12일 Jira `TMI-25` 후속 Summary 복구 설계, 8월 13일 Summary generation 및 누락 Question 결과 복구 구현·검증 순서다.
- 애플리케이션 동작과 공개 API·DTO·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount`, Redis Key/TTL, S3 Object Key 및 소유권 검증 계약은 변경하지 않았다.
- 이번 turn은 응답 작성과 작업 기록 갱신만 수행했으며 Gradle 테스트는 실행하지 않았다. 외부 시스템·Jira 상태·Git commit·push도 변경하지 않았다.

## Latest part score retry inclusion analysis (2026-08-17)

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/summary`의 `partScores` 산정 경로를 확인했다.
- 현재 `ExamServiceImpl.getExamSummary()`는 시험의 모든 `ExamResult` 중 `questionNumber`와 `score`가 있는 모든 문서를 파트별로 합산하며, `retryCount`를 필터링하지 않는다. 따라서 최초 응시와 재시도 점수가 모두 `partScores`에 더해지는 현상이 맞다.
- `totalSolvedQuestions`는 `retryCount == 0`만 카운트하고, `totalScore`는 종합 문서에 저장된 값을 사용하므로 위 영향은 `partScores`에 한정된다.
- 코드를 수정하지 않았고 공개 API·DTO·`BaseResponse`, AI/Callback, `retryCount`, Redis/S3 계약을 변경하지 않았다. 정적 분석과 문서 기록만 수행해 Gradle 테스트는 실행하지 않았다.
- 후속 수정 전에 최초 응시만 사용할지, 문항별 최신 재시도나 최고 점수를 사용할지 산정 정책을 확정해야 한다.

## Latest initial-attempt-only part score implementation (2026-08-17)

- 별도 Jira 이슈 키 없이 `GET /api/v1/exams/{examId}/summary`의 `partScores`를 `retryCount == 0`인 최초 응시 결과만 파트별로 합산하도록 변경했다.
- `retryCount>0` 재시도 결과와 `retryCount=null` legacy 결과는 `partScores`에서 제외된다. `totalScore`와 `totalSolvedQuestions` 로직은 변경하지 않았다.
- 회귀 테스트로 최초 응시 점수만 합산되고 재시도와 null 회차가 제외되는 것을 검증했다. 최종 `./gradlew clean test`는 tests/failures/errors/skipped `352/0/0/0`, `git diff --check`는 성공했다.
- 공개 API URL·Method·DTO 필드·`BaseResponse`, AI/Callback `user_id=examId`, `retryCount` 의미, Redis/S3와 소유권 검증 계약은 유지했다.
- 배포 전 실제 데이터에 `retryCount=null`인 최초 응시 문서가 존재하는지 확인할 수 있다. 명시적 요청은 `retryCount=0`만 포함하는 것이므로 현재 구현은 null을 최초 응시로 간주하지 않는다.

### Completion record sync (2026-08-17)

- 종료 hook에서 요구한 WORKLOG 보완 항목을 추가했다. 현재 구현·테스트 결과·외부 계약·legacy `retryCount=null` 위험은 위 최신 상태와 동일하며, 이 보완으로 코드나 외부 시스템은 추가 변경되지 않았다.

## Latest initial-attempt part score implementation plan (2026-08-17)

- 별도 Jira 이슈 키 없이 이미 반영된 `partScores` 최초 응시 집계의 구현 계획을 정리했다.
- 외부 API·DTO·`BaseResponse`는 유지하고 `getExamSummary()`의 점수 합산 스트림에서 `retryCount == 0`만 통과시키는 최소 변경이다.
- 최초 응시·재시도·null 회차를 함께 사용하는 회귀 테스트와 전체 Gradle 테스트로 검증하며, legacy null 문서 존재 여부를 배포 전 확인 사항으로 유지한다.
- 이번 계획 정리 turn에서는 작업 기록 문서 외의 코드나 외부 시스템을 추가 변경하지 않았다.

## Latest initial-attempt part score implementation confirmation (2026-08-17)

- 별도 Jira 이슈 키 없이 사용자의 구현 요청에 따라 현재 작업 트리의 구현·회귀 테스트·전체 테스트 상태를 확정했다.
- `partScores`는 `retryCount == 0`인 최초 응시만 합산하고 재시도와 null 회차는 제외한다. 공개 API 구조와 기타 외부 계약은 유지된다.
- `./gradlew clean test`는 tests/failures/errors/skipped `352/0/0/0`, `git diff --check`는 성공했다. 기존 unchecked 경고 외에 추가 문제는 없다.
- 이미 요청한 애플리케이션·테스트 변경이 작업 트리에 존재했으므로 이번 확인에서는 코드를 추가 변경하지 않았다. legacy `retryCount=null` 최초 응시 문서는 집계에서 제외되는 상태다.

## Latest staging GitHub Actions test failure fix (2026-08-17)

- 별도 Jira 이슈 키 없이 GitHub Actions run `32034974696`의 job·step·실패 로그를 읽기 전용으로 확인했다. `checkout@v4`·`setup-java@v4` 경고는 실패 원인이 아니었고, job 전역 `SENTRY_RELEASE` 오버라이드로 `TosunsaengApplicationTests` 1건이 실패한 것이 직접 원인이었다.
- `Run tests` step의 `SENTRY_RELEASE`를 `app-back-end-learning-core@test`로 격리했고, checkout·setup-java를 Node.js 24 기반 v5 action으로 올렸다. 배포 후속 step의 commit SHA release와 AWS·ECR·ECS·health check 흐름은 유지된다.
- CI 동일 테스트 release 환경의 `./gradlew clean test --no-daemon`은 tests/failures/errors/skipped `352/0/0/0`으로 성공했다. YAML parse와 `git diff --check`도 성공했고 `actionlint`는 미설치로 실행하지 못했다.
- 사용자가 commit·push한 뒤 실제 GitHub Actions에서 v5 action 초기화부터 ECS health check까지 전체 배포를 재검증해야 한다. Codex는 workflow run을 재실행하거나 Git commit·push·배포 변경을 수행하지 않았다.

## Latest deployed-main Callback persistence diagnosis (2026-08-20)

- 로컬 `main`과 `develop`은 동일한 `98730c9`이며, 별도 신규 Jira 키 없이 기존 `TMI-25` Summary generation fencing과 운영 Callback 미저장 현상을 정적으로 대조했다.
- 가장 유력한 원인은 Python AI Summary Callback의 `generation_attempt` 누락 또는 현재 `summary_grading_jobs.generationAttempt`와의 불일치다. 2026-08-17 `a2c4fb6` 이후 이 Callback은 stale no-op 처리되어 `exam_summaries` insert 없이 HTTP 성공 응답을 반환한다.
- 운영 확인 순서는 Callback의 `user_id`, `generation_attempt`, `part_feedback` 존재 여부, 같은 examId의 `summary_grading_jobs` generation/status, ExamSession abandoned 여부, `exam_summaries`와 legacy `exam_results` 중복 결과 존재 여부다. 실제 payload 본문이나 민감정보는 로그에 남기지 않는다.
- `missing_generation`, `generation_mismatch`, abandoned, duplicate와 completion-claim-lost 경로는 DEBUG라 기본 INFO 운영 로그에 보이지 않을 수 있다. 성공 저장이면 `요약 채점 콜백 저장 완료 event=grading.callback outcome=stored callbackType=summary` INFO가 있어야 한다.
- 코드·외부 API·AI `user_id=examId`, Callback의 나머지 JSON, Redis/S3와 소유권 계약은 변경하지 않았다. 실제 운영 DB·로그·외부 시스템은 조회하지 않았고 Gradle 테스트도 실행하지 않았다.

## Latest Callback log versus Mongo result-count diagnosis (2026-08-20)

- 제공된 운영 로그에는 `ex_da814c87a9_0820_1425`와 `ex_3871c98953_0820_1412` 두 시험이 섞여 있다. 별도 신규 Jira 키는 없으며 기존 채점 멱등 관련 키는 `TMI-25`다.
- 최신 `ex_da...1425`에는 문항 1~5 저장 로그만 있고, 문항 9~11 및 Summary 예약 로그는 이전 `ex_387...1412`에 속한다. 최신 examId Mongo 조회에서 1~5만 보이는 현상은 이 로그와 모순되지 않는다.
- 이전 시험의 Summary 예약은 `completedQuestionCount=11 expectedQuestionCount=11`이며, 이는 해당 examId의 `exam_results`에서 최초 응시 결과 11개를 확인한 뒤에만 기록된다. 저장 INFO도 Mongo insert 정상 반환 뒤에만 출력된다.
- 운영 확인 대상은 두 examId별 `exam_results`, 결정적 feedback `_id`, 연결된 `exam_sessions`, 그리고 배포 앱의 `MONGODB_DATABASE`와 Atlas에서 조회 중인 database/cluster 일치 여부다. 실제 운영 DB와 payload는 이번 분석에서 조회하지 않았다.
- 애플리케이션·설정·테스트 코드는 변경하지 않았고 Gradle 테스트를 실행하지 않았다. 공개 API·AI Callback·Redis/S3 계약과 외부 시스템·Jira·Git 상태도 변경하지 않았다.

## Latest Summary-only persistence confirmation (2026-08-20)

- 문항 결과 부족처럼 보인 현상은 서로 다른 examId 혼동으로 해소됐고, 남은 Summary 미저장은 기존 `TMI-25` generation fencing과 가장 잘 일치한다. 별도 신규 Jira 키는 없다.
- 제공 로그의 Summary 요청은 generation 1이지만 저장 완료 로그가 없다. Python Callback에 `generation_attempt=1`이 없으면 main은 `missing_generation` stale no-op으로 HTTP 성공만 반환한다.
- 최종 확정에는 Callback 필드와 `summary_grading_jobs` status/generation/failureReason, `exam_summaries` 존재 여부를 함께 확인해야 한다. 실제 운영 payload·DB는 이번 turn에서 조회하지 않았다.
- 코드·설정·테스트와 공개 API·AI/Callback·Redis/S3 계약은 변경하지 않았고 Gradle 테스트도 실행하지 않았다.

## Latest web-ai deployment and generation propagation diagnosis (2026-08-20)

- `Too-Much-I/web-ai` 최신 main `ef060d2`의 GitHub Actions 배포 run `32333161432`는 성공했으므로 단순 서버 업데이트 누락으로 보이지 않는다. 별도 신규 Jira 키는 없으며 Learning Core 관련 기존 키는 `TMI-25`다.
- web-ai 최신 코드가 Summary 요청의 `generation_attempt`를 JSON parsing, Redis payload, sync/worker 처리, Summary response와 backend Callback까지 보존하지 않는다. 최신 이미지를 재배포하는 것만으로는 해결되지 않는다.
- AI 저장소에서 `generation_attempt` end-to-end echo와 sync/Redis callback 계약 테스트를 구현한 뒤 main에 병합하면 기존 workflow가 테스트, Docker image push와 EC2 `ai-server`·`ai-worker` 교체를 자동 수행한다.
- 외부 저장소·Actions는 읽기 전용으로만 확인했고 commit·push·PR·배포 재실행과 Learning Core 애플리케이션 변경은 수행하지 않았다.

### Completion record sync (2026-08-20)

- 종료 훅에 맞춰 `web-ai` 배포 안내 turn의 WORKLOG marker를 보완했다. 최신 main은 이미 배포됐고, 해결에는 AI 코드의 `generation_attempt` end-to-end echo 구현과 테스트 후 main 병합이 필요하다는 상태는 동일하다.
- 별도 신규 Jira 키는 없고 관련 기존 범위는 `TMI-25`다. 외부 저장소·Actions·EC2와 Learning Core 코드는 추가 변경하지 않았으며 Secret·Token도 다루지 않았다.

## Latest legacy Summary timing confirmation (2026-08-20)

- `ex_d9b6268627_0817_1308`의 suffix는 UTC 기준 2026-08-17 13:08, 한국 시간 22:08의 시험 생성 시각이다. 별도 신규 Jira 키는 없고 관련 기존 범위는 `TMI-25`다.
- generation fencing이 포함된 첫 workflow는 13:24 UTC에 실패했고, 실제 성공 배포 `98730c9`는 13:51 UTC, 한국 시간 22:51에 완료됐다. 해당 시험은 성공 배포 약 43분 전에 생성되어 구버전 Callback 저장 로직을 사용할 수 있었다.
- Summary 문서에는 저장 시각이 없으므로 정확한 Callback 저장 시각은 `exam_sessions.completedAt` 또는 CloudWatch 저장 완료 로그로 확인해야 한다. 코드·외부 시스템·Git/Jira 상태는 변경하지 않았다.

### Legacy Summary timing completion record sync (2026-08-20)

- 종료 훅에 맞춰 legacy Summary 시각 대조 turn의 WORKLOG marker를 보완했다. `0817_1308` 시험 생성은 generation fencing 성공 배포보다 약 43분 빠르다는 결론은 동일하다.
- 정확한 Summary 저장 시각은 `exam_sessions.completedAt` 또는 CloudWatch 로그 확인이 필요하며, 별도 신규 Jira 키는 없고 관련 기존 범위는 `TMI-25`다. 외부 시스템과 코드는 추가 변경하지 않았다.

## Latest web-ai AWS deployment confirmation (2026-08-21)

- 별도 신규 Jira 키 없이 기존 `TMI-25` Summary generation 연동과 관련된 `web-ai` 배포 상태를 확인했다.
- 최신 main `883c45c`의 `Echo generation attempt in feedback callbacks` GitHub Actions run `32435961886`이 한국 시간 2026-08-21 10:22:59에 성공했다.
- offline test·Compose 검증·Docker image push·EC2 SSH deploy가 모두 성공했으며 `ai-server`와 `ai-worker` 교체까지 완료됐다. 추가 수동 AWS 업로드는 필요 없다.
- 새 시험으로 Summary 저장과 Job COMPLETED를 확인하고, 기존 누락 시험은 필요하면 기존 grading retry API로 복구한다. 외부 저장소·Actions는 읽기 전용으로만 확인했고 코드·배포·Git/Jira 상태는 변경하지 않았다.

## Billing 구현 시작 분석 (2026-08-25)

- 별도 Billing 저장소는 Spring Boot·MongoDB·Security health-only 골격만 있고 도메인 entity, API, transaction과 reconciliation은 아직 없다.
- Identity의 phone eligibility schema v1 producer와 publisher는 구현됐지만 Billing의 event inbox·revision high-water·current binding consumer와 staging E2E가 없다.
- Learning Core 시험 생성은 현재 Billing reserve 없이 기존 진행 Session을 abandon하고 새 `ExamSession`을 즉시 insert한다. Billing 연동 시 기존 공개 시험 생성 body/성공 DTO를 유지하면서 내부 경계를 `reserve → Session durable commit → confirm`과 동일-operation 복구로 변경해야 한다.
- Billing의 우선 구현 범위는 phone binding consumer, 무료 1회 TrialClaim·entitlement ledger, 멱등 Reservation API, 5분 만료·reconciliation과 workload 인증이다. Apple/Google 결제, paid credit, pass, coupon과 환불은 후속이다.
- 구현 전 Billing 사용자 JWT audience, Learning Core workload credential, internal endpoint/DTO, idempotency와 오류 mapping, confirm 불명 복구, AttemptGroup 완료 증거와 TrialClaim 보존 기간을 확정해야 한다.
- 별도 신규 Jira 키는 없다. Learning Core 코드는 이번 분석에서 변경하지 않았으며 Billing API 계약이 동결되기 전 임의 연동을 시작하지 않는다.

## Billing AGENTS.md 최신화 동기화 (2026-08-27)

- 별도 Billing 저장소의 `AGENTS.md`에 현재 무료 최소 Entitlement 범위, TrialClaim 3년 보존, eligibility event API·멱등성·Mongo Transaction, VPC Lattice AWS_IAM·ECS task role·SigV4와 코드 리뷰 우선순위를 반영했다.
- Billing은 명시적 요청 없이 Identity와 Learning Core를 읽기 전용 계약 확인 대상으로만 사용하며 다른 저장소 코드를 함께 변경하지 않는다.
- Learning Core 애플리케이션과 공개 API·DTO·BaseResponse·AI/Callback·S3·Redis 계약은 이번 작업에서 변경하지 않았다.
- 별도 신규 Jira 키는 없다. Billing Reservation API가 확정되기 전에는 Learning Core outbound client와 시험 생성 saga를 임의 구현하지 않는다.

## Billing AGENTS.md 전용 계약 정교화 동기화 (2026-08-27)

- Billing `AGENTS.md`는 무료 MVP 전체와 현재 PLAN-001 event consumer 범위를 분리하고, internal DTO·204/error mapping, 인증된 service userId body 예외와 explicit Mongo index·보존 규칙을 반영했다.
- PLAN-001에는 eligibility inbox와 current projection만 포함하며 TrialClaim·grant·Reservation은 후속 vertical slice다.
- Learning Core 애플리케이션과 외부 API 계약은 변경하지 않았다. 별도 신규 Jira 키는 없다.
- Billing Reservation API가 동결되기 전 Learning Core outbound 연동을 임의 구현하지 않는다.

## Billing 서비스 간 통합 계약서 동기화 (2026-08-27)

- Billing 저장소에 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`가 추가됐다.
- 문서는 Identity eligibility event와 Learning Core reserve·confirm·cancel·status·AttemptGroup event의 인증, 멱등성, 재시도·장애 수렴과 배포 체크리스트를 한 흐름으로 설명한다.
- 세부 wire·Mongo는 Billing ADR-001, Lattice·SigV4는 ADR-002가 최종 기준이다.
- Learning Core 애플리케이션과 외부 계약은 변경하지 않았고 별도 신규 Jira 키는 없다.

## Billing 통합 계약 외부 검토 확인 (2026-08-27)

- 첨부 검토 4건을 Billing 계약과 Identity·Learning Core 실제 코드에 대조했다.
- Billing ADR-001 inbox field/index·disposition 불일치, Identity Bearer publisher와 Billing SigV4 목표 차이, Billing 필수 Idempotency-Key와 Learning Core controller 미구현은 사실이다.
- Identity가 `Retry-After`를 읽지 않는 지적은 맞지만 Billing eligibility endpoint의 409는 구체 계약상 EVENT_ID_CONFLICT 전용이므로 COMMAND_PROCESSING과 error body code를 구분할 필요는 없다.
- Learning Core 과거 계획 문서는 시험 생성 header를 optional로 기록하지만 Billing 최신 승인 계약은 필수 UUID v4다. Reservation saga 구현 전에 앱과 Learning Core 공개 header를 함께 전환해야 한다.
- Jira 키는 없다. 계약·애플리케이션 코드는 수정하지 않았다.

## Billing 필수 Idempotency-Key 계약 문서 정렬 (2026-08-27)

- Billing 최신 승인에 맞춰 Learning Core Billing 계약 검토 문서의 optional header를 필수 lowercase UUID v4 `Idempotency-Key` 목표로 보정했다.
- `POST /api/v1/exams`의 Request Body 없음과 기존 성공 Response DTO는 유지하며 header 없는 요청은 목표 구현에서 `INVALID_IDEMPOTENCY_KEY`로 거절한다.
- 같은 key는 응답 유실 transport retry에만 재사용하고 앱 종료 후 의도적 restart는 새 key·새 examId와 `ABANDONED_RESTARTED`를 사용한다.
- 실제 `ExamRestController`와 `ExamSession`·Billing client는 아직 이 계약을 구현하지 않았다. 앱 선배포·구버전 호환 gate와 Reservation API 준비 후 별도 구현한다.
- 관련 Identity transport 기존 Jira는 `TMI-95`이며 Jira는 변경하지 않았다. Learning Core 애플리케이션·외부 runtime 계약은 이번 문서 업데이트에서 변경하지 않았다.

## 10초 챌린지 자동 Day 1·difficulty 계약 확정 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira 자체는 조회하거나 변경하지 않았다. Learning Core Challenge backend 구현 Jira는 아직 없다.
- 프론트 상세 계약과 전체 API 인계서를 Draft v0.9로 갱신했다. Challenge API는 아직 구현·배포되지 않은 계획 상태다.
- `app.challenge.enabled=true`인 배포가 처음 성공 기동한 KST 날짜를 Mongo `challenge_10s_catalog_state`의 `_id="active:v1"` singleton에 원자 `setOnInsert`로 한 번만 저장하고 그날을 dayNumber 1로 사용한다. disabled 배포는 날짜를 만들지 않으며, 재시작·재배포·ECS scale-out은 기준일을 초기화하지 않는다.
- 이후 `dayNumber = daysBetween(contentBaseDate, challengeDate) + 1`로 계산하며 순환, modulo, random 또는 이전 날짜 fallback을 하지 않는다. 해당 dayNumber 콘텐츠가 없으면 `404 CHALLENGE_CONTENT_NOT_FOUND`로 fail-closed하고 운영 알림 대상으로 삼는다.
- Mongo의 `difficulty`는 정수인지 확인하되 scale이나 범위를 해석하지 않는다. 문제 조회, 제출 terminal 응답과 상세 결과 DTO에는 저장된 정수를 그대로 반환하고, attempt·upload-url 응답과 AI 요청·grading job payload에는 넣지 않는다. 과거 결과 안정성을 위해 attempt snapshot에는 저장한다.
- 변경 범위는 `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, `docs/codex/TEN_SECOND_CHALLENGE_API_CONTRACT_DECISIONS.md`, `docs/codex/REVISED_RELEASE_PLAN_SOCIAL_FREE_TRIAL_CHALLENGE.md`와 Codex 기록 문서다. 애플리케이션·테스트 코드, Mongo 데이터, Jira와 배포는 변경하지 않았다.
- 기존 시험 API·DTO·`BaseResponse`, S3·Redis, Python AI/Callback의 `user_id=examId` 계약은 유지했다. 문서 작업이므로 Gradle 테스트는 실행하지 않았다.
- 구현 전에 Challenge backend Jira를 만들고 metadata initializer·비순환 resolver·catalog validation·attempt snapshot·공개 DTO·AI payload exclusion 테스트를 구현해야 한다. sample rate·channel·최대 파일 크기와 AI 결과 세부 계약은 여전히 확정이 필요하다.

## 10초 챌린지 Learning Core–AI 계약 미확정 항목 검토 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend와 AI 연동 전용 Jira는 아직 없다.
- 현재 문서는 시험 Callback을 재사용하지 않는 challenge 전용 versioned 비동기 계약, 실제 userId·difficulty 제외, attemptId·문제 식별값·한국어 prompt·audio 전달, 결정적 Job과 callback 멱등성까지만 방향이 정해져 있다. 실제 endpoint, 인증과 request/callback JSON 또는 multipart schema는 아직 동결되지 않았다.
- 구현 전 필수 확정 대상은 contract version, AI 요청 endpoint·전송 방식, audio 규격과 최대 크기, request 필수/nullable field, callback endpoint·인증, 결과 field와 enum/null 의미, no-speech·unsupported audio 처리, `attemptId + gradingAttempt` stale callback fencing, idempotency key, timeout·retry·최종 실패 전환, HTTP 오류 분류와 payload 제한이다.
- 권장 MVP는 숫자 점수 없이 transcript, `correct|needs_improvement` verdict, correctedAnswer, 짧은 meaning·grammar·pronunciation feedback만 제공한다. 내부 `no_speech`는 정상 terminal 결과로 저장하되 공개 `feedbackType`은 추가하지 않고, 시스템 실패와 구분한다.
- 기존 Summary의 `generation_attempt` 누락 문제를 반복하지 않도록 outbound와 callback 모두 `attemptId`, 결정적 `jobId`, 양의 정수 `gradingAttempt`를 필수로 두고 현재 attempt generation과 불일치하는 callback은 성공 no-op으로 처리하는 방향을 권장한다.
- 분석·기록만 수행했으며 애플리케이션·테스트 코드, 기존 프론트 Draft v0.9, Mongo 데이터, Jira와 배포는 변경하지 않았다. 기존 시험 AI/Callback `user_id=examId` 계약에도 영향을 주지 않는다.

## 10초 챌린지 promptKo 의미 확인 (2026-08-28)

- `promptKo`는 사용자가 보고 영어로 말해야 하는 한국어 문제 문장이며 Mongo `challenge_10s_questions.questions[].korean`을 공개 API 필드로 매핑한 값이다.
- `referenceAnswer`는 같은 문제의 참고 영어 답안으로 `promptKo`와 구분하며, 문제 조회 단계에는 숨기고 제출 또는 만료 terminal 이후에만 프론트에 공개한다.
- AI 계약에서 snake_case를 사용한다면 동일 값을 `prompt_ko`로 전달하는 제안이며, 실제 wire field명은 AI 계약 v1 동결 시 최종 확정한다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira와 애플리케이션·계약 문서는 변경하지 않았다.

## 10초 챌린지 Learning Core–AI 계약서 Draft v0.1 작성 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 신규 `docs/contracts/ten-second-challenge-ai-api.md` Draft v0.1을 작성했다. 기존 시험 `/evaluations`·Feedback Callback과 분리한 `POST /v1/challenges/evaluations`, `POST /internal/v1/challenges/grading/callback` 권장 계약이다.
- Learning Core가 S3 audio를 내려받아 multipart로 AI에 전달한다. 필수 field는 `attempt_id`, 결정적 `job_id`, `grading_attempt`, `question_id`, `question_number`, Mongo `korean`과 같은 `prompt_ko`, `reference_answer`, `audio_file`이며 실제 userId·difficulty·날짜·S3 위치는 제외한다.
- audio 허용 profile은 M4A/AAC-LC, `audio/mp4`, 16/44.1/48 kHz, mono/stereo, 최대 2 MiB이며 AI가 내부에서 16 kHz mono PCM으로 정규화하는 권장안이다.
- 결과는 숫자 점수 없이 `completed|no_speech|failed`, transcript, `correct|needs_improvement`, corrected answer와 짧은 meaning·grammar·pronunciation feedback을 사용한다. no-speech는 시스템 실패가 아닌 completed terminal로 projection한다.
- 방향별로 분리된 service Bearer credential, private service discovery·TLS, request `Idempotency-Key`, callback UUID와 `attemptId/jobId/gradingAttempt` fencing, 204 duplicate·stale no-op, 오류별 retry, 120초 deadline·최대 3 generation 권장값을 문서화했다.
- 결정서와 개정 출시 계획에 AI 계약 문서 링크·Draft 상태·담당 팀 승인 및 contract test gate를 반영했다. 기존 프론트 Draft v0.9와 애플리케이션·테스트 코드는 변경하지 않았다.
- 네 개의 JSON 예시는 Ruby JSON parser로 검증했고 `git diff --check`가 통과했다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.
- AI 팀과 실제 모바일 audio fixture로 profile을 검증하고, service credential/TLS 운영 경로, 120초 deadline·최대 3 generation, 프론트 `aiResult` projection을 승인한 뒤 v1로 동결해야 한다.

### AI 계약서 작업 종료 기록 동기화 (2026-08-28)

- 종료 훅의 현재 turn 기록 요구에 맞춰 WORKLOG 완료 항목을 추가했다. 신규 AI 계약서 Draft v0.1, 결정서·출시 계획 동기화, JSON 예시 및 `git diff --check` 검증 결과는 위 최신 상태와 동일하다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira, 애플리케이션 코드, Mongo 데이터와 배포는 추가로 변경하지 않았다.

## 10초 챌린지 AI 계약 v1 승인 반영·잔여 결정 검토 (2026-08-28)

- AI 팀이 `docs/contracts/ten-second-challenge-ai-api.md` 내용대로 구현하기로 합의해 문서 상태를 Draft v0.1에서 승인된 v1·미구현으로 변경했다. 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira 자체는 변경하지 않았다.
- 승인 범위는 challenge 전용 request/Callback endpoint, multipart audio, M4A/AAC profile·2 MiB, 방향별 service credential, `attemptId/jobId/gradingAttempt` fencing, 결과 schema, 120초 Callback deadline, 최대 3 generation과 retry/error 규칙이다.
- AI 계약서의 담당 팀 endpoint/field 승인 checklist를 완료 처리하고, 결정서·출시 계획의 Draft 표현을 v1 승인과 구현·contract test 잔여 상태로 동기화했다. 프론트 계약에는 서버 timeout·generation이 v1로 확정됐음을 반영했다.
- 추가 확정 대상은 프론트 `aiResult`와 no-speech null 표현, MEMBER/Guest 범위, 날짜 rollover 최종 승인, foreground polling 상한, audio 재생 제공 여부, AI text field와 Callback 전체 payload 상한이다.
- 권장 잔여안은 no-speech를 `gradingStatus=completed`와 null 하위 field를 가진 `aiResult` 객체로 표현, MEMBER 전용, 기존 rollover안 승인, foreground polling 60초, MVP audio 재생 제외, transcript/corrected answer 각 1000자·feedback 각 500자·Callback JSON 16 KiB 상한이다. 이 값들은 아직 사용자 승인 전이다.
- 구현·운영 준비로 실제 모바일 audio fixture, 양방향 credential 주입·rotation, private routing·TLS/security group, contract test·retry/DLQ와 staging latency 검증이 남아 있다. 이는 wire schema 재결정과 구분한다.
- 애플리케이션·테스트 코드, Mongo 데이터, 배포와 기존 시험 AI/Callback 계약은 변경하지 않았다. 문서 변경만 수행해 Gradle 테스트는 실행하지 않았고 `git diff --check`가 통과했다.

## 10초 챌린지 프론트 v1 잔여 계약 확정 (2026-08-28)

- 관련 기존 Jira는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이며 Jira를 조회하거나 변경하지 않았다. Learning Core Challenge backend·AI 연동 전용 Jira는 아직 없다.
- 프론트 계약을 v1 승인·미구현 상태로 올리고 `aiResult.referenceAnswer`를 추가했다. 이 값은 AI 생성값이나 Callback echo가 아니라 Mongo `challenge_10s_questions.questions[].referenceAnswer`를 attempt 생성 시 snapshot한 Learning Core 값이다.
- 정상 AI 완료와 no-speech 모두 non-null `aiResult`와 non-blank `aiResult.referenceAnswer`를 반환한다. no-speech는 `gradingStatus=completed`이고 transcript·verdict·correctedAnswer·feedback만 null이다. processing·최종 failed에서는 기존처럼 `aiResult=null`이고 top-level `question.referenceAnswer`는 유지한다.
- `question.referenceAnswer`는 제출 직후부터 이용하는 기존 field이고 `aiResult.referenceAnswer`는 완료 결과 component용 동일 snapshot이다. 두 값이 다르면 attempt snapshot을 authoritative 값으로 처리한다.
- MEMBER 전용·Guest `403`, 기존 KST rollover 보호, foreground polling 60초(처음 20초 2초 간격·이후 5초 간격), MVP 사용자 audio 재생과 `audioUrl` 제외를 확정했다.
- AI text 상한은 transcript·corrected answer 각 1000자, feedback 각 500자, Callback JSON 전체 UTF-8 16 KiB로 확정했다. 초과 Callback은 `413 CALLBACK_PAYLOAD_TOO_LARGE`다.
- `docs/contracts/ten-second-challenge-frontend-api.md`, `docs/contracts/FRONTEND_API_HANDOFF.md`, AI v1 계약, 결정서와 출시 계획을 동기화했다. 애플리케이션·테스트 코드와 Mongo 데이터는 변경하지 않았다.
- 프론트·AI 계약의 모든 JSON code block parser 검증과 `git diff --check`가 통과했다. 문서 작업이라 Gradle 테스트는 실행하지 않았다.
- 실제 구현에서는 attempt snapshot 조립, no-speech projection, MEMBER authorization, 60초 polling 타입·UX, audioUrl 비노출, text/payload validation을 contract test로 검증해야 한다.

## 10초 챌린지 구현 착수 가능성 점검 (2026-08-28)

- 프론트·AI v1 계약, 콘텐츠 저장 구조, attempt/upload-url 분리, 날짜·상태·멱등성·retry 계약은 구현 가능한 수준으로 동결됐다. 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 Jira를 조회하거나 변경하지 않았다.
- 현재 직접적인 착수 blocker는 repository `AGENTS.md`의 “현재 추가하지 않을 기능” 목록에 10초 챌린지가 포함돼 있고 `TMI-14`, `TMI-25` 외 별도 예외가 없다는 점이다. Learning Core Challenge backend 전용 Jira도 아직 없다.
- 구현 전 필수 순서는 Learning Core backend Jira 생성, 해당 키에 한정한 AGENTS 명시적 예외 추가, 현재 계약 문서 변경의 사용자 commit·push, 전용 branch 생성이다. Codex는 commit·push를 수행하지 않는다.
- 실제 secret 값 없이 방향별 service credential 환경변수·Secrets Manager 주입, private routing·TLS/security group과 feature flag 기본 off를 구현 범위에 포함해야 한다. 실제 credential 생성·운영 주입은 배포 준비 단계다.
- 구현은 catalog/state 기반 Day resolver, ChallengeAttempt·snapshot·upload/submit, MEMBER authorization, GradingJob·AI dispatch/callback fencing, 결과/history API, migration/index·contract/integration test 순서의 vertical slice로 진행할 수 있다.
- 구현 전 분석만 수행했으며 애플리케이션·테스트 코드, AGENTS.md, Jira, Git branch·commit·push와 배포는 변경하지 않았다. 출시 계획의 상태 문구만 계약 승인 상태에 맞춰 갱신했다.

## AGENTS.md 10초 챌린지 구현 범위 승인 반영 (2026-08-28)

- 사용자가 10초 챌린지를 Learning Core에 추가할 기능으로 명시 승인해 `AGENTS.md`의 “현재 추가하지 않을 기능” 목록에서 10초 챌린지를 제거했다.
- Jira 신규 키는 없으며 관련 기존 이슈는 Challenge UI `TMI-102`, 문제 생성 `TMI-105`, 채점 agent `TMI-106`이다. Jira를 조회하거나 변경하지 않았다.
- `AGENTS.md`에 프론트·AI v1 계약을 authoritative source로 지정하고 Challenge domain 격리, MEMBER·소유권, 콘텐츠/date resolver, attempt/S3, AI/Callback, secret·로그와 테스트 규칙을 추가했다.
- 기존 ExamSession·ExamResult·시험 Job/retryCount·시험 API/AI 계약을 Challenge가 재사용하거나 변경하지 않도록 경계를 고정했다.
- DB referenceAnswer attempt snapshot, 정상/no-speech `aiResult.referenceAnswer`, difficulty AI 제외, 비순환 Day 1, 1시간 attempt, M4A/AAC, AI generation fencing·payload 제한과 audioUrl 비노출을 구현 규칙으로 반영했다.
- 코드 리뷰 우선순위에 Challenge의 MEMBER/소유권, referenceAnswer 조기 노출·snapshot, no-speech, AI payload, stale Callback, baseDate와 audio 개인정보 검사를 추가했다.
- 이전 구현 착수 blocker였던 AGENTS 범위 제한은 해소됐다. Learning Core backend Jira 생성은 추적을 위해 권장하지만 저장소 규칙상 구현 허용의 선행 blocker는 아니다.
- 애플리케이션·테스트 코드와 배포는 변경하지 않았다. 문서 변경이라 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 검증한다.

### AGENTS 범위 승인 종료 기록 동기화 (2026-08-28)

- 종료 훅의 현재 turn 기록 요구에 맞춰 WORKLOG 완료 항목을 추가했다. 10초 챌린지 제외 해제, 전용 구현·테스트·리뷰 규칙 추가와 저장소 범위 blocker 해소 상태는 위 최신 기록과 동일하다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 신규 Jira, 애플리케이션 코드, secret, 배포는 추가로 변경하지 않았다.

## Production 실제 비용 관측 재정정 (2026-08-28)

- 현재 보고된 비용은 `$12.6/day`가 아니라 `$1.26`이다.
- 서울 Fargate 기준 Task 각 1개 compute는 약 `$5.696/day`이므로 `$1.26`은 세 Task가 24시간 실행된 하루 전체 비용일 수 없다.
- 다음 확인 항목은 ECS 서비스별 desired/running/deployment Task 수와 Cost Explorer의 Fargate vCPU·GB hours, NAT Gateway, ALB/LCU, public IPv4, CloudWatch usage type이다.
- `$1.26`의 기간·필터·cost type·credit 여부를 확인하기 전에는 단순 월 환산 `$37.80`, 약 52,920원을 고정비로 사용하지 않는다. 애플리케이션과 AWS 리소스 및 외부 계약은 변경하지 않았다.

## AGENTS 10초 챌린지 범위 승인 최종 상태 (2026-08-28)

- 10초 챌린지는 더 이상 `AGENTS.md` 제외 기능이 아니며 승인된 프론트·AI v1 계약에 따라 Learning Core에서 구현할 수 있다.
- 관련 기존 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 신규 Jira와 애플리케이션 코드는 아직 없다. Secret·Token과 배포 상태는 변경하지 않았다.

## Challenge 제외 Learning Core·Billing 잔여 구현 재확인 (2026-08-28)

- 사용자가 제시한 `UserMerged` Learning Core consumer, Billing Reservation client·시험 생성 saga, 공개 시험 생성 `Idempotency-Key`·same-operation replay, AttemptGroup 상태 outbox/publisher·R3 replacement 연결, Billing 장애 reconciliation은 Challenge를 제외한 Learning Core 기존 시험의 실제 잔여 구현 항목이 맞다.
- Learning Core `POST /api/v1/exams`는 현재 header 없이 `createExamSession()`을 호출해 Session을 즉시 생성하며 Billing client, reservationId·operationId metadata, UserMerged와 AttemptGroup/reconciliation 코드가 없다. 채점 dispatch에 사용되는 내부 `Idempotency-Key`는 공개 시험 생성 operation key와 별개다.
- Billing에는 `TMI-112`, `TMI-113` 범위의 TrialClaim·무료 grant/ledger와 reserve/confirm/cancel/status·expiry, AttemptGroup/AttemptSession 기반이 구현돼 있다. 따라서 “Billing Reservation 전체 미구현”이 아니라 Learning Core 호출·saga와 양방향 수렴이 미구현이다.
- AttemptGroup 종단 연결은 양쪽 작업이다. Learning Core의 상태 event outbox/publisher와 Billing의 event consumer가 모두 없으며, Billing의 REPLACEMENT 판정 기반만 존재해 현재는 GRADING/COMPLETED/RETAKE_AVAILABLE 실제 수렴이 불가능하다.
- 전체 1차 출시 기준에는 위 목록 외에 Billing owner rebind, Identity `TMI-114` 포함 여부와 구현, 실제 모바일 SNS·phone link, workload/Lattice/IAM/SG·replica-set/multi-instance·response-loss·rollback·canary E2E가 남아 있다.
- 관련 완료 Jira는 Learning Core `TMI-109`, Identity `TMI-111`, Billing `TMI-110`·`TMI-112`·`TMI-113`이며 후속 Identity 계획은 `TMI-114`다. Jira는 조회하거나 변경하지 않았다.
- 코드와 문서를 읽기 전용으로 대조했으며 애플리케이션·테스트 코드와 외부 시스템은 변경하지 않았다. 분석 작업이라 Gradle 테스트는 실행하지 않았다.

## 다음 작업 확정: Billing AttemptGroup 상태 event consumer (2026-08-28)

- 다음 vertical slice는 Billing `POST /internal/v1/attempt-group-events` consumer다. `AttemptGroup`·`AttemptSession`과 reserve/confirm 기반은 이미 있지만 Learning Core 상태 event를 받는 입구가 없어 group 상태가 실제 채점 진행·완료·최종 실패로 수렴하지 않는다.
- schema v1 strict validation, 16 KiB 상한, canonical digest inbox, active Session fencing, version CAS와 Mongo Transaction을 한 단위로 구현한다. 같은 eventId·같은 digest는 `204` no-op, 다른 digest는 `409`, stale Session은 inbox에 `STALE`로 기록하고 `204`로 종료한다.
- 권장 전이는 유효한 현재 Session의 terminal evidence가 도착하면 `OPEN`에서도 `COMPLETED` 또는 `RETAKE_AVAILABLE`로 전진 수렴시키고, `COMPLETED`는 terminal로 보호하는 방식이다. missing group/session은 retryable, owner mismatch는 계약 위반 격리, failureCode는 저 cardinality allowlist로 제한한다.
- 이 slice에는 Learning Core publisher/outbox, Billing owner rebind, 공개 시험 생성 Idempotency-Key·Reservation saga, reconciliation과 AWS/Lattice 배포를 포함하지 않는다. consumer가 완료된 뒤 owner rebind와 Learning Core outbound 쪽을 순서대로 연결한다.
- 이번 턴은 구현 전 계획 설명만 수행했으며 애플리케이션·외부 계약·Jira·배포를 변경하지 않았다.

## Billing PLAN-005 AttemptGroup 상태 event consumer 계획서 작성 (2026-08-28)

- Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`에 endpoint, schema v1 strict decode·16 KiB, digest/inbox, active Session fencing, 상태 전이, Transaction·CAS, security, 오류와 테스트·production gate를 구현 단계별로 작성했다.
- terminal evidence는 `GRADING` 누락 시 `OPEN`에서도 직접 전진한다. terminal 뒤 역행과 이전 Session event는 `STALE` 204, missing group/session은 `503 ATTEMPT_PROJECTION_NOT_READY`, 구조적 owner/session 충돌은 `409 EVENT_TARGET_CONFLICT`로 고정했다.
- Billing 내부 owner 검증은 `AttemptGroup.subjectRefId`를 userId로 직접 비교하지 않고 active·unexpired `BillingSubjectLink`를 통해 수행하도록 계획했다. retention 뒤 link는 복원하지 않고 stale 처리한다.
- RETAKE failureCode 초안 allowlist는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`이다. provider 원문·자유 형식 사유는 금지한다.
- 기존 Billing PLAN-004가 있어 번호는 PLAN-005를 사용했다. PLAN-004/TMI-115는 기술적 선행 조건이 아니며 상태를 변경하지 않았다. PLAN-005는 사용자 승인 대기·Jira 미생성 상태다.
- Learning Core publisher/outbox, UserMerged, Reservation saga·reconciliation, 실제 Lattice/AWS는 후속이며 이번 턴에는 애플리케이션·계약·Jira를 변경하지 않았다.

## Billing PLAN-005 계획서 종료 상태 (2026-08-28)

- PLAN-005 상세 계획서는 작성 완료·사용자 승인 대기 상태다. Jira는 미생성이며 애플리케이션 구현은 시작하지 않았다.
- 최종 계획은 `BillingSubjectLink` 기반 owner 검증, terminal 전진·역행 차단, duplicate/stale 204, missing projection retryable 503, target conflict non-retryable 409와 failureCode allowlist를 포함한다.
- 문서 외 런타임 계약은 아직 변경하지 않았다. 승인 후 Jira 생성과 Phase 0 ADR·서비스 통합 계약 보정이 다음 단계다.

## 범위 정정: Learning Core Billing Reservation·시험 생성 saga 계획 (2026-08-28)

- 사용자가 수정 대상은 Billing이 아니라 Learning Core라고 정정했다. 이전 Billing PLAN-005는 철회·삭제했고 Billing 애플리케이션이나 Jira를 변경하지 않았다.
- 신규 `docs/codex/BILLING_RESERVATION_SAGA_IMPLEMENTATION_PLAN.md`가 현재 활성 초안이다. 공개 시험 생성의 필수 `Idempotency-Key`, `ExamCreationOperation`, Billing reserve→ExamSession durable commit→confirm, cancel/status 복구와 same-operation replay를 Learning Core 구현 범위로 둔다.
- `ExamSession`에 `creationOperationId`, `billingReservationId`, `billingReservationKind`, `attemptGroupId`, entitlement confirmation 상태를 내부 저장하되 기존 성공 DTO에는 노출하지 않는다.
- 현재 `attemptGroupId` mapping이 없으므로 AttemptGroup 상태 outbox/publisher보다 이 saga가 먼저다. saga 완료 후 같은 mapping으로 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` event를 발행한다.
- Billing flag는 기본 off로 계획했다. flag off에서는 기존 무헤더 흐름을 유지하고, 프론트 header 선배포·staging 검증 뒤 flag on에서 lowercase UUID v4를 필수화한다.
- 이번 턴은 계획 문서만 변경했으며 Java/config/migration·runtime API·Jira·AWS는 변경하지 않았다. 계획은 사용자 승인 대기·Jira 미생성 상태다.
- 재확인 결과 수정 대상은 계속 Learning Core이며 Billing은 reserve/confirm/cancel/status 계약의 호출 대상일 뿐이다. Billing consumer나 Billing 런타임 구현은 현재 활성 작업이 아니다.
- Billing Reservation 기반은 `TMI-112`·`TMI-113`으로 이미 존재하므로 현재 saga를 위해 Billing에 같은 기능을 다시 만들 필요는 없다. 다만 전체 AttemptGroup 수렴에는 후속 Billing 상태 event consumer가 필요하며 owner rebind와 Billing 측 reconciliation도 별도 Billing 작업으로 남아 있다. 현재 순서는 Learning Core saga와 durable group mapping이 먼저다.

## Billing 변경 필요성 최종 결론 (2026-08-28)

- 현재 Learning Core 시험 생성 saga를 연결하는 데 필요한 Billing Reservation endpoint 기반은 이미 구현돼 있으므로 Billing을 먼저 수정하지 않는다.
- 우선순위는 Learning Core saga와 `ExamSession.attemptGroupId` mapping이다. 이후 Learning Core 상태 outbox/publisher에 대응하는 Billing consumer, owner rebind와 Billing reconciliation을 Billing 저장소의 별도 후속 작업으로 구현한다.
- 현재 턴에는 런타임 코드·외부 계약·Jira·AWS를 변경하지 않았다.

## Learning Core 시험 생성 saga 계획 설명 상태 (2026-08-28)

- 활성 계획서는 기존 Billing Reservation 기반을 Learning Core의 `POST /api/v1/exams`에 연결해 한 번의 사용자 시작 동작이 하나의 operation·Session·사용권 소비로 수렴하게 하는 구현 계획이다.
- 정상 순서는 operation 준비→Billing reserve→confirming Session local commit→Billing confirm→`IN_PROGRESS` finalize이며, commit 실패는 cancel/expiry, confirm 응답 불명은 Billing status와 same-key replay로 복구한다.
- 외부 성공 DTO와 `BaseResponse`, 실제 userId 비노출, retryCount, S3·Redis와 AI `user_id=examId` 계약은 유지한다. feature flag는 기본 off이고 AttemptGroup publisher/consumer와 owner rebind·background reconciliation은 후속이다.
- 이번 턴에는 계획을 설명하고 기록만 갱신했으며 애플리케이션·외부 계약·Jira·AWS를 변경하지 않았다.

## TMI-116 Learning Core 시험 생성 saga Jira (2026-08-28)

- Jira `TMI-116` `[Learning Core] Billing Reservation 시험 생성 saga 구현`을 생성했다. 재조회 상태는 `해야 할 일`, 담당자는 미지정이다.
- 이슈에는 Learning Core `POST /api/v1/exams`의 필수 `Idempotency-Key`, Billing reserve→Session commit→confirm, same-operation replay, 실패 복구, internal mapping, SigV4/Lattice, feature flag 기본 off와 회귀·production gate를 포함했다.
- 선행 완료 이슈는 Billing `TMI-112`·`TMI-113`이다. Billing Reservation 재구현과 AttemptGroup publisher/consumer, owner rebind·background reconciliation은 `TMI-116`에서 제외한 후속 범위다.
- 계획서는 Jira 생성·구현 대기 상태로 갱신했으며 애플리케이션·외부 계약·AWS는 변경하지 않았다.

## TMI-116 Billing Reservation 시험 생성 saga 구현 완료 (2026-08-29)

- 브랜치 `feat/TMI-116-billing-reservation-exam-saga`에서 Learning Core의 `POST /api/v1/exams`에 feature flag 기반 Billing Reservation saga를 구현했다. flag는 기본 off이며 on일 때만 `Idempotency-Key` lowercase UUID v4를 필수 검증한다.
- `ExamCreationOperation`의 `PREPARED → RESERVED → SESSION_COMMITTED → SUCCEEDED` 및 cancel·expiry·terminal 상태를 영속화하고, 같은 사용자·operation replay가 고정 `sessionId`와 `mockExamId`로 수렴하도록 했다. operation TTL 뒤에는 `(userId, creationOperationId)` Session mapping으로 장기 replay한다.
- Billing reserve·confirm·cancel·status SigV4 client를 추가했다. service는 `vpc-lattice-svcs`, region은 `ap-northeast-2`, redirect는 금지하며 strict JSON·16 KiB response 상한과 `Retry-After` mapping을 적용했다. confirm의 `sessionCommittedAt`은 Billing strict decoder에 맞춰 UTC 밀리초 3자리로 직렬화한다.
- 기존 Session abandon과 새 `ENTITLEMENT_CONFIRMING` Session insert를 Mongo Transaction으로 묶고 confirm 뒤에만 `IN_PROGRESS`로 전환한다. commit 실패 cancel, cancel 불명 `CANCEL_PENDING`, confirm 불명 status 조회, duplicate/stale local 전이를 복구하며 same-key 동시 commit은 공유 reservation을 취소하지 않는다.
- `ExamSession`에 operation·reservation·reservation kind·attemptGroup·entitlement metadata를 내부 저장하지만 기존 성공 DTO·`BaseResponse`, 실제 userId 비노출, 시험 retryCount·S3·Redis·AI request/Callback과 `user_id=examId` 계약은 유지했다.
- Mongo index dry-run/apply script, staging/prod index validator와 Transaction capability probe, 설정 startup validator, 프론트 인계 계약과 환경변수 예시를 추가했다. 실제 AWS/Lattice/IAM/SG와 운영 DB migration은 실행하지 않았다.
- 검증 결과 `./gradlew clean test`는 424 tests, failures 0, errors 0, skipped 0으로 성공했다. `node --check scripts/mongodb/tmi-116-migrate-billing-exam-saga.js`와 `git diff --check`도 성공했다.
- 애플리케이션 구현과 로컬 회귀 검증은 완료됐지만 Jira `TMI-116` 상태는 변경하지 않았다. 활성화 전 프론트 header 선배포, 실제 replica-set migration·failure injection, Lattice 권한·경로와 INITIAL/REPLACEMENT staging E2E가 남아 있다.

## TMI-116 코드 증가량 설명 (2026-08-29)

- 이번 변경은 단순 Billing HTTP 호출 하나가 아니라 분산 saga의 정상 흐름, 응답 유실·process crash·동시 요청 복구, Mongo Transaction·영속 operation, SigV4 전송, startup fail-closed와 migration 및 회귀 테스트까지 한 번에 포함해 파일 수와 코드량이 커졌다.
- 핵심 비즈니스 코드는 `BillingExamCreationSaga`, `BillingExamCreationTransactionService`, `ExamCreationOperation`과 기존 시험 생성 연결부다. 나머지 큰 비중은 SigV4/strict contract client, 설정·index·transaction startup 검증, migration과 테스트다.
- 작업 트리 전체 diff에는 이번 TMI-116과 무관하게 이전부터 존재하던 10초 챌린지·비용 관련 문서 변경도 함께 표시되므로 전체 변경량을 전부 이번 구현 코드로 보면 안 된다.
- 이번 설명 턴에는 애플리케이션 코드를 추가 수정하지 않았고 Jira·AWS·Git commit/push도 변경하지 않았다.
- 종료 훅 재검증에서도 결론은 동일하다. 신규 운영 코드의 대부분은 saga 복구와 SigV4·Mongo 운영 안전장치이며, 기존 10초 챌린지·비용 문서 변경은 TMI-116 구현량과 분리해서 본다.
- 상세 설명 기준으로 기존 직접 Session 생성과 달리 Billing·Mongo 두 시스템에는 단일 Transaction을 걸 수 없어, `ExamCreationOperation`을 복구 지점으로 사용하는 saga 상태 머신이 필요했다. 정상 흐름 외에도 reserve/commit/confirm 각 경계의 응답 유실·rollback·동시 요청을 같은 operation으로 수렴시키는 코드가 핵심 증가분이다.
- 종료 훅 기준 상세 설명 기록도 완료했으며, 애플리케이션 구현·Jira·AWS와 외부 계약 상태에는 추가 변경이 없다.

## TMI-116 Saga와 SigV4 client 역할 구분 (2026-08-31)

- `BillingExamCreationSaga`는 시험 생성 use case의 업무 순서와 복구 정책을 담당한다. 영속 operation 상태를 읽고 reserve, local Session commit, confirm, status/cancel reconciliation 중 다음 행동을 결정한다.
- `SigV4BillingReservationClient`는 saga가 요청한 reserve·confirm·cancel·status를 Billing HTTP 계약으로 변환하고 VPC Lattice SigV4 서명, timeout, strict response decode와 오류 mapping을 수행하는 infrastructure adapter다.
- Saga는 HTTP 서명 방법을 모르고 `BillingReservationClient` port만 사용하며, SigV4 client는 시험 Session 상태나 어떤 복구 단계를 선택할지 결정하지 않는다. 이번 설명에서 애플리케이션·계약·Jira `TMI-116` 상태는 변경하지 않았다.

## Billing VPC Lattice AWS_IAM 선택 근거 재확인 (2026-08-31)

- Billing은 모바일 앱이 직접 호출하지 않는 내부 workload API이고 Identity·Learning Core는 이미 ECS에서 실행되므로, ECS application task role의 자동 회전 임시 credential을 서비스 신원으로 사용하는 VPC Lattice `AWS_IAM`+SigV4를 선택했다.
- Lattice auth policy가 IAM principal뿐 아니라 HTTP Method·Path를 함께 제한해 Identity와 Learning Core 권한을 route별로 분리하고, production/staging role과 service network를 교차 차단할 수 있다. Billing은 public/internal ALB 없이 Lattice target으로만 두고 SG로 task 직접 우회 경로를 막는 계약이다.
- 이 선택은 별도 workload JWT issuer·JWKS·client-credentials·client secret rotation과 token cache를 새로 만드는 비용을 피하며, 사용자 Access Token이나 caller header를 서비스 인증으로 오용하지 않는다. 대가는 Lattice 비용·AWS 종속성·SigV4 client 및 IAM/SG 운영 복잡도다.
- 이는 승인된 설계 근거 재확인이며 Jira `TMI-116`, 애플리케이션 코드와 실제 AWS 배포 상태는 변경하지 않았다. 실제 Lattice/IAM/SG staging 연결과 negative/E2E 검증은 여전히 운영 gate다.

## TMI-116 이후 다음 작업 재확인 (2026-08-31)

- 다음 애플리케이션 vertical slice는 `ExamSession.attemptGroupId`를 사용한 AttemptGroup 상태 event 파이프라인이다. Learning Core가 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`을 durable outbox에 기록해 publisher가 Billing으로 전달하고, Billing consumer가 event inbox·active Session fencing·상태 전이를 처리해야 한다.
- 현재 코드 재확인 결과 Learning Core outbox/publisher와 Billing `POST /internal/v1/attempt-group-events` consumer가 모두 없다. 안전한 배포 순서는 event 계약 동결 → Billing consumer 선배포 → Learning Core outbox/publisher 선배포 → staging E2E → consumer 후 publisher 활성화다.
- 현재 저장소에서의 다음 구현 대상은 Learning Core outbox/publisher지만, Billing consumer가 준비되기 전 publisher를 활성화하지 않는다. TMI-116 자체의 프론트 key·Mongo migration·Lattice/IAM/SG·staging saga E2E는 별도 운영 활성화 gate로 먼저 또는 병행해야 한다.
- 이번 설명에서는 Jira `TMI-116`, 애플리케이션 코드, Billing 저장소와 AWS를 변경하지 않았다. 신규 Jira는 아직 만들지 않았다.
- 종료 훅 기준으로도 다음 개발 대상은 AttemptGroup 상태 consumer/outbox/publisher이며, consumer-first 활성화와 TMI-116 staging gate 병행 원칙을 유지한다.

## TMI-116 독립 리뷰 P1/P2 검증 (2026-08-31)

- 리뷰 1은 유효하다. `BillingExamCreationSaga.start()`가 operation보다 Session을 먼저 조회하고 `ENTITLEMENT_CONFIRMING`이면 `EXAM_CREATION_PROCESSING`을 즉시 반환해, 기존 operation이 `SESSION_COMMITTED`여도 confirm/status reconciliation에 다시 진입하지 못한다. operation을 먼저 drive하고 terminal command purge 뒤에만 Session durable replay를 fallback해야 한다.
- 리뷰 2도 유효하다. commit 단계는 `DuplicateKeyException`과 `OptimisticLockingFailureException`만 동시성으로 분류하며 그 밖의 Mongo transient/unknown Transaction 예외에서 한 번 re-read한 operation이 아직 `RESERVED`면 shared reservation을 cancel할 수 있다. transient/unknown 결과는 cancel하지 않고 operation과 `(userId, creationOperationId)` Session 재조회 및 same-key retry로 수렴해야 한다.
- 리뷰 3도 유효하다. 현재 ObjectMapper는 unknown·duplicate·trailing token은 막지만 scalar coercion과 enum ordinal, creator field missing/null을 완전히 차단하지 않는다. confirm 성공 검증도 `attemptGroupStatus=OPEN`과 non-null `confirmedAt`을 요구하지 않는다. global coercion 차단과 endpoint/status별 필수·조건부 semantic validation이 필요하다.
- PR 범위 지적도 유효하다. 작업 트리에 TMI-116과 무관한 10초 챌린지·비용 문서가 섞여 있고 `FRONTEND_API_HANDOFF.md`는 Idempotency-Key 부분은 관련되지만 전체 파일 포함 의도를 확인해야 한다. 사용자 변경을 되돌리지 말고 selective staging/별도 commit으로 분리해야 한다.
- 이번 턴은 Jira `TMI-116` 리뷰 진단만 수행했으며 아직 코드를 수정하지 않았다. 현재 상태에서는 PR 전 세 finding 수정과 회귀 테스트가 필요하다.

## ENTITLEMENT_CONFIRMING·SESSION_COMMITTED 관계 설명 (2026-08-31)

- `ENTITLEMENT_CONFIRMING`은 Session 생성 충돌이 아니라 Billing reserve 뒤 Learning Core의 local Session commit은 성공했지만 Billing confirm은 아직 확정되지 않은 정상 중간 상태다.
- `commitReservedSession()`의 단일 Mongo Transaction이 새 Session을 `ENTITLEMENT_CONFIRMING`·`entitlementState=CONFIRMING`으로 insert하고 같은 Transaction에서 operation을 `RESERVED → SESSION_COMMITTED`로 저장한다. 따라서 Transaction이 정상 동작하면 두 상태는 함께 나타나며 rollback이면 둘 다 나타나지 않는다.
- 이후 Billing confirm과 local finalize가 성공하면 두 번째 Transaction에서 Session은 `IN_PROGRESS`·`CONFIRMED`, operation은 `SUCCEEDED`가 된다. confirm/status 실패 시 정상적으로 `ENTITLEMENT_CONFIRMING + SESSION_COMMITTED` 쌍이 남고 same-key retry가 이를 복구해야 한다.
- 이 설명은 Jira `TMI-116` P1 finding의 근거를 명확히 한 것이며 Java·테스트·Jira 상태는 변경하지 않았다.

## TMI-116 P1/P2 리뷰 finding 구현 완료 (2026-08-31)

- `BillingExamCreationSaga.start()`는 동일 `(userId, operationId)` operation을 Session보다 먼저 복구한다. operation이 존재하면 `SESSION_COMMITTED` confirm/status reconciliation을 우선 실행하고, operation이 없을 때만 Session을 장기 durable replay fallback으로 사용한다.
- Session commit 예외는 관측 결과를 `ADVANCED`, `SESSION_VISIBLE`, `NOT_VISIBLE`로 나눈다. Mongo transient/unknown 결과에서는 reservation을 취소하지 않고, operation이 이미 `SESSION_COMMITTED` 또는 `SUCCEEDED`이면 다음 상태로 진행하며 그 밖에는 same-key `EXAM_CREATION_PROCESSING` 재시도를 반환한다. 명시적인 local `IllegalStateException`에서 operation·Session이 모두 보이지 않을 때만 기존 cancel 보상 흐름을 유지한다.
- Billing 성공 응답은 scalar·숫자 enum coercion을 차단하고 reserve/confirm/cancel/status별 필수 문자열·enum·timestamp를 검증한다. Saga도 confirm의 `attemptGroupStatus=OPEN`·`confirmedAt`, status의 reservation kind·attempt group·session·mock exam·terminal timestamp, cancel timestamp를 fail-closed로 검증한다.
- 공개 시험 생성 API URL·Method·Request·성공 Response·`BaseResponse`와 기존 AI·S3·Redis 계약은 변경하지 않았다. Billing 저장소와 AWS도 변경하지 않았다.
- 회귀 테스트를 추가했고 `./gradlew clean test` 전체 432개가 통과했다. 남은 운영 gate는 실제 replica set에서의 failure injection, Lattice/IAM/SG 연결과 staging E2E이며 Jira `TMI-116` 상태와 Git commit/push는 변경하지 않았다.

## TMI-116 로컬 develop 반영 확인 (2026-08-31)

- 현재 checkout은 로컬 `develop`이며 `HEAD`, `develop`, `origin/develop`이 모두 PR `#24` merge commit `d95d18b42a47383c2237fdb7eae536b7495136fb`를 가리킨다.
- merge commit에는 TMI-116 최신 feature commit `c3e3c8296316b1e49014413eb3dc32efaad76aba`가 포함돼 있으므로 원격뿐 아니라 현재 로컬 코드에도 TMI-116 P1/P2 보완이 반영됐다.
- 애플리케이션 코드·Jira `TMI-116` 상태·AWS·Git commit/push는 변경하지 않았고, 코드 변경이 없어 테스트를 재실행하지 않았다.

## 멘토링용 저장소 구조 조사 준비 상태 (2026-08-31)

- 별도 Jira 이슈 키 없이 앱 Learning Core의 구조·기능·정보 체계·네이밍·컨벤션·기술 진화 방향을 분석하는 멘토링 산출물의 사전 구성을 정리했다.
- 예정 산출물은 한국어 중심의 draw.io 4종(컨셉맵, 아키텍처, Feature Map, IA)과 Markdown 표·문서 3종(네이밍 사전, 컨벤션과 code smell, 진화수렴·디팩토 비교)이다.
- 분석 범위는 현재 저장소의 구현 코드, 테스트, 설정, 계약·운영 문서와 migration script다. 기존 웹 POC는 제외하고, 실행 중인 기능·feature flag 기능·문서상 계획을 시각적으로 구분한다.
- 현재 확인된 주요 축은 시험·채점, Billing Reservation 시험 생성 saga, 회원 탈퇴 이벤트, 인증·보안·관측성, MongoDB·Redis·S3·Python AI·Billing 외부 연동이다. 10초 챌린지는 승인 계약 문서는 있으나 실제 구현 여부를 전수 조사한 뒤 별도 상태로 표시해야 한다.
- 아직 실제 draw.io와 본 조사 문서는 생성하지 않았다. 애플리케이션 코드와 외부 계약은 변경하지 않았고 기록 문서만 갱신했으므로 테스트는 실행하지 않았다.

## 멘토링 구조 조사 범위 확장 결정 (2026-08-31)

- 별도 Jira 이슈 없이 구조 조사 범위를 웹을 제외한 앱 서버 전체인 Learning Core·Identity·Billing으로 확장하는 방향을 권고했다.
- 조사 자체는 세 저장소를 같은 시점의 하나의 시스템으로 수행한다. 산출물은 통합 시스템 관점과 서버별 내부 관점을 분리해, 서비스 경계가 사라지거나 한 장의 복잡한 도식으로 뭉개지지 않게 구성한다.
- 컨셉맵·시스템 아키텍처·Feature Map·IA는 공통 상위 관점을 제공하고 필요하면 서버별 상세 페이지를 둔다. 네이밍 사전과 컨벤션은 공통 기준, 저장소별 용례·편차, 서비스 간 계약 용어를 비교할 수 있게 작성한다.
- 기존 웹 POC와 웹 프론트·백엔드는 계속 조사·변경 범위에서 제외한다. 아직 실제 전수 조사와 draw.io·본 문서 작성은 시작하지 않았으며 애플리케이션 코드와 외부 계약은 변경하지 않았다.

## 웹 제외 앱 서버 통합 구조 조사 완료 (2026-08-31)

- 별도 신규 Jira 없이 Learning Core `develop@d95d18b`, Identity `feat/TMI-116-billing-reservation-exam-saga@8c4f3ca`, Billing `develop@39e424d`를 기준으로 세 앱 서버의 코드·테스트·설정·계약·ADR·현재 상태를 통합 조사했다. 관련 구현 문맥은 `TMI-115`·`TMI-116`이며 Jira 상태는 변경하지 않았다.
- `docs/architecture/app-server-mentoring.drawio`에 통합 컨셉맵, 시스템 아키텍처, Feature Map, 앱 IA, 세 서비스 상세와 서비스 간 Gap을 포함한 8개 페이지를 작성했다.
- `APP_SERVER_SYSTEM_OVERVIEW.md`, `APP_SERVER_NAMING_DICTIONARY.md`, `APP_SERVER_CONVENTIONS_AND_CODE_SMELLS.md`, `APP_SERVER_EVOLUTION_CONVERGENCE_REVIEW.md`에 기능 상태, 식별자·suffix 정의, 실제 컨벤션과 위반, 진화수렴 방향·fitness function을 기록했다.
- 서비스 분리와 data ownership은 대체로 적절하다. 현재 우선순위는 Identity→Billing SigV4 transport 정렬, Billing AttemptGroup consumer와 Learning Core outbox/publisher, saga 운영 gate, AI Callback 인증·strict boundary, Learning Core 내부 capability 분리다.
- Billing saga는 코드가 구현됐지만 기본 off와 staging gate가 남는다. 10초 챌린지와 AttemptGroup 상태 파이프라인은 계약·계획은 있으나 현재 실행 코드가 없음을 별도 상태로 표시했다.
- 공개 API·AI·S3·Redis·JWT·Billing wire 계약과 애플리케이션 코드는 변경하지 않았다. draw.io XML 8개 페이지와 whitespace를 검증했으며 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## 멘토링 draw.io 문서 사용 안내 (2026-08-31)

- 첨부된 `<mxfile>` 텍스트는 `docs/architecture/app-server-mentoring.drawio`와 동일한 완전한 8페이지 draw.io 문서다.
- diagrams.net에서 `.drawio` 파일을 직접 열면 하단 페이지 탭으로 통합 컨셉맵, 시스템 아키텍처, Feature Map, 앱 IA와 서비스별 상세 페이지를 이동하며 편집할 수 있다.
- 이번 안내에서는 애플리케이션 코드와 외부 계약을 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 현재 앱 서버 개발 방식 정리 (2026-08-31)

- 별도 Jira 이슈 없이 현재 세 앱 서버의 개발 방식을 계약 우선의 점진적·진화적 개발로 설명했다.
- 서비스는 Identity·Learning Core·Billing의 business capability와 data ownership으로 나누고, 기능은 vertical slice로 구현하면서 멱등 command, durable state, saga, outbox/inbox, feature flag와 운영 gate를 함께 설계한다.
- 현재 방향은 무조건 최신 기술을 도입하기보다 기존 앱·AI 계약을 보호하고 장애·중복·재시작에도 같은 결과로 수렴하게 만드는 실용적 구조다.
- 다음 성숙 단계의 주요 과제는 Learning Core 내부 모듈화, 미완성 event pipeline 종결, cross-service E2E와 tracing·문서 freshness 강화다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트는 실행하지 않았다.

## AI 활용 개발 방식 정리 (2026-08-31)

- 별도 Jira 이슈 없이 사용자의 AI 활용 방식을 repository-grounded·artifact-driven·human-in-the-loop 개발로 정리했다.
- AI가 코드 작성 전에 저장소 전체를 조사해 컨셉맵, 아키텍처, Feature Map, IA, 네이밍·컨벤션·기술 비교 문서로 외재화하고, 사용자가 이를 다시 읽고 직접 옮겨 그리며 이해와 판단을 형성하는 방식이다.
- AI는 조사자·지도 제작자·리뷰어·구현 보조자이고, 사용자는 범위·제품 의도·계약·우선순위와 최종 승인권을 유지한다.
- 주의점은 AI 산출물의 근거 확인, 문서와 코드의 동기화, AI에게 이해 자체를 외주화하지 않는 것이다. 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## AI 개발 루프의 스킬 도입 방향 (2026-08-31)

- 별도 Jira 이슈 없이 공식 OpenAI Codex 스킬 문서를 기준으로 현재 AI 활용 방식에 스킬을 적용하는 방향을 정리했다.
- 반복되는 저장소 조사·멘토링 산출물 생성, 외부 계약 검증, vertical slice 계획, 출시 준비 검토는 스킬화 가치가 높다.
- 상시 프로젝트 제약은 `AGENTS.md`, 정확한 계약은 `docs/contracts`, 조건부 절차는 Skill, 결정적 검사는 script로 분리한다. 모든 지식을 하나의 스킬에 복제하지 않는다.
- 첫 도입은 `repo-cartographer`와 `contract-guardian` 두 개를 작게 만들고 실제 사용 결과로 trigger·입출력·template을 보정한 뒤 나머지를 확장하는 것을 권장한다.
- 이번 작업에서는 스킬 생성이나 설치, 애플리케이션 코드와 외부 계약 변경을 수행하지 않았고 Gradle 테스트를 실행하지 않았다.

## 토선생 개발 병행 학습 방향 (2026-08-31)

- 별도 Jira 이슈 없이 AI를 활용하면서 직접 코딩 역량을 키우기 위한 프로젝트 내 학습 루프를 정리했다.
- 실제 기능마다 일부 작은 핵심 로직과 테스트를 사용자가 먼저 작성하고, AI는 질문·힌트·리뷰·실패 사례 생성에 우선 사용한다.
- 권장 루프는 코드 읽기와 결과 예측 → 30~60분 직접 구현 → AI 리뷰 → 수정 이유 설명 → 다음 날 무자료 재구현 → 주간 회고다.
- 학습 주제는 Java/Spring 일반론을 넓게 훑기보다 현재 변경과 맞닿은 테스트, 상태 전이, 멱등성, Spring 경계, Mongo transaction·index 등에서 주당 하나씩 선택한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## AI 구현·별도 학습 이중 트랙 방향 (2026-08-31)

- 별도 Jira 이슈 없이 현재 출시 속도를 유지하기 위해 production 구현은 AI에게 적극 맡기고, 학습은 최근 구현 코드에서 작은 개념을 추출해 별도로 수행하는 방식으로 조정했다.
- 학습은 프로젝트와 무관한 병렬 강의가 아니라 이번 주 코드의 테스트·상태 전이·Spring·Mongo·멱등성 중 하나를 toy example, 무자료 재구현, 코드 설명으로 연습한다.
- 권장 최소 리듬은 구현 후 5분 후보 기록, 주 2~3회 20~40분 직접 연습, 주 1회 60분 코드 해부이며 production 일정과 학습 실패를 분리한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 코딩 외 최소 학습 범위 (2026-08-31)

- 별도 Jira 이슈 없이 직접 코딩 외에 추가로 유지해야 할 최소 학습 범위를 정리했다.
- 요구사항·계약·아키텍처·문서화는 현재 AI 협업 과정에서 이미 반복 학습되고 있으므로 별도 커리큘럼보다 현행 산출물 검토를 유지한다.
- 별도로 강화할 핵심은 디버깅과 운영 관찰이다. 기능별 정상·실패 요청 직접 실행, 주 1회 end-to-end 요청 추적, 월 1회 장애 시나리오 분석을 권장한다.
- 기반 이론은 전 범위를 미리 공부하기보다 실제 코드와 장애에서 등장한 HTTP, transaction, index, JWT, timeout 등을 just-in-time으로 학습한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 긴 개발 문서의 읽기 원칙 (2026-08-31)

- 별도 Jira 이슈 없이 긴 AI 산출물을 모두 정독하는 대신 정독·훑기·필요 시 조회로 계층화하는 방식을 정리했다.
- 현재 변경의 계약, 결정, 위험, 검증 방법은 정독하고 배경 설명과 대안 비교는 훑으며 네이밍 사전·전체 inventory·과거 WORKLOG는 검색 가능한 reference로 사용한다.
- 앞으로 AI 문서는 결론, 반드시 읽을 항목, 결정 필요 사항, 위험, 상세 근거의 순서로 작성하도록 요청하는 것이 적절하다.
- 사용자가 현재 변경의 목적, 보호 계약, 변경 범위, 실패 방식, 검증 방법을 설명할 수 있는지를 정독 완료 기준으로 삼는다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.

## 10초 챌린지 AI Callback 목적지 확인 (2026-08-31)

- 승인된 v1 계약상 AI는 `POST {LEARNING_CORE_INTERNAL_BASE_URL}/internal/v1/challenges/grading/callback`으로 결과를 전달한다.
- 필수 header는 AI→Learning Core 전용 `Authorization: Bearer <AI_TO_LEARNING_CORE_CREDENTIAL>`, `X-Challenge-Contract-Version: v1`, `Content-Type: application/json`이다. 사용자 Access Token과 기존 시험 Feedback Callback, `BaseResponse`는 사용하지 않는다.
- `LEARNING_CORE_INTERNAL_BASE_URL`의 실제 host는 같은 ECS cluster의 private Service Connect 또는 동등한 private discovery 주소를 배포 설정으로 주입해야 하며 요청 Body가 Callback URL을 지정하지 않는다.
- 현재 `develop`에는 Challenge Callback Controller가 아직 구현·배포되지 않았다. 따라서 경로 계약은 확정됐지만 실제 호출 가능한 base URL과 credential secret 주입은 Challenge backend 배포 전에 환경별로 확정해야 한다.
- 관련 계약 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 이번 확인에서 Jira 상태·애플리케이션 코드·외부 계약은 변경하지 않았다.

## 10초 챌린지 사용자 Token과 서비스 인증 구분 (2026-08-31)

- 앱의 사용자 Access Token은 앱→Learning Core 공개 Challenge API에서만 사용하고 Learning Core가 AI 요청에 전달하지 않는다.
- Learning Core→AI 평가 요청은 별도 전용 Bearer credential, AI→Learning Core Callback은 그와 다른 방향별 전용 Bearer credential로 인증한다.
- AI→Learning Core credential은 AI ECS task 환경에 secret store로 주입하므로 AI가 사용자 Token을 알거나 Callback마다 전달받을 필요가 없다.
- AI 요청과 Callback에는 계약상 `attempt_id`, `job_id`, `grading_attempt` 등 Challenge 작업 식별자만 사용하며 실제 userId와 사용자 Access Token은 포함하지 않는다.
- 관련 계약 Jira는 `TMI-102`, `TMI-105`, `TMI-106`이며 이번 설명에서 코드·계약·Jira 상태는 변경하지 않았다.
- 종료 훅 기준으로도 사용자 Access Token 비전달, 방향별 service credential의 ECS secret 주입 원칙을 유지하며 실제 credential 값은 기록하지 않았다.

## Billing TMI-117 완료 확인과 Learning Core 후속 작업 (2026-08-31)

- Billing `develop@37a3e1d`에 PR `#4`의 TMI-117 feature commit `96a5727`이 merge됐고 Jira `TMI-117`은 완료 상태다. `POST /internal/v1/attempt-group-events`, strict schema v1 decoder, inbox 멱등성, active Session fencing, Mongo Transaction/CAS, 보안·관측성·replica-set 테스트가 구현돼 있다.
- Billing 기록상 `./gradlew clean test` 전체 137개가 성공했으며 merge diff의 `git diff --check`도 통과했다. 현재 Learning Core에는 AttemptGroup outbox/writer/publisher가 아직 없다.
- 다음 개발 대상은 Learning Core의 `AttemptGroupStatusChanged` durable outbox와 lease 기반 SigV4 publisher다. `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` 판정과 outbox 생성을 local state transition과 같은 Mongo Transaction/CAS로 묶고 Session당 terminal event 하나만 허용해야 한다.
- Billing consumer 코드는 준비됐으므로 Learning Core 개발은 시작할 수 있다. 다만 Billing consumer가 staging에 먼저 배포·활성화되고 Lattice/IAM/SG route가 검증되기 전에는 Learning Core publisher를 활성화하지 않는다.
- 현재 TMI-116 예외 범위는 AttemptGroup outbox/publisher를 명시적으로 제외하므로 TMI-117을 재사용하지 않고 Learning Core 전용 PLAN·신규 Jira와 해당 작업의 명시적 허용 범위를 먼저 확정해야 한다.
- 이번 확인에서 Learning Core/Billing 애플리케이션 코드, Jira, AWS와 Git commit/push는 변경하지 않았다.

## AttemptGroup outbox DELIVERED·DEAD_LETTER 보존 근거 (2026-08-31)

- `DELIVERED` 30일은 Learning Core 발행과 Billing 수신을 eventId·traceId로 대조하고, 응답 유실·중복·상태 불일치 사고를 조사할 수 있는 짧은 운영 증거 기간이다. 정상 전달된 문서를 영구 보존하지 않아 저장 비용과 userId/sessionId 보유 기간을 제한한다.
- `DEAD_LETTER` 90일은 계약 오류·관계 충돌·인증 장애처럼 자동 재시도가 중단된 미해결 event를 조사·수정하고 같은 eventId/payload로 수동 replay할 시간을 더 길게 제공한다. 해결되지 않은 event를 정상 전달 문서보다 먼저 삭제하면 Billing projection 복구 근거를 잃는다.
- Billing inbox의 보존 기간이 120일이므로 30일·90일은 그보다 짧다. 이 기간 안의 replay는 Billing의 same eventId/digest 멱등성 창 안에서 안전하게 수렴한다.
- `PENDING`은 아직 전달되지 않은 업무 event이므로 TTL 삭제하지 않는다. 보존 기간은 authorization이나 업무 상태의 근거가 아니라 운영 기본값이며, on-call 대응 SLA·감사·개인정보 정책에 따라 계약 변경 절차로 조정할 수 있다.
- 관련 작업은 Billing `TMI-117`과 후속 Learning Core outbox/publisher이며 이번 설명에서 코드·Jira·계약값은 변경하지 않았다.

## 세 앱 서버 문서 계층·완료 보고 규칙 (2026-08-31)

- 별도 Jira 없이 Learning Core·Identity·Billing `AGENTS.md`에 공통 문서 가독성 및 구현 완료 보고 규칙을 반영했다.
- 계획·조사·분석·리뷰 문서는 5줄 결론 → 반드시 읽을 내용 → 사용자 결정 → 위험·미확인 → 현재 작업 설명 → 상세 근거 부록 순서로 작성하고 파일 근거와 구현 사실·계획·추론을 구분한다.
- 구현 완료 후에는 변경 파일·동작, 유지/변경 외부 계약, 테스트와 결과, 남은 위험, 배포 전 확인, 예상 밖 diff, 다음 확인 사항을 빠짐없이 보고한다.
- 애플리케이션 코드와 외부 계약은 변경하지 않았다. 규칙·기록 문서 변경만 있어 Gradle 테스트는 실행하지 않고 세 저장소 `git diff --check`로 검증한다.

## IntelliJ 학습 프로젝트 구성 방향 (2026-09-01)

- 별도 Jira 없이 토선생 학습용 프로젝트는 Java 21+Gradle+JUnit의 가벼운 `java-lab`으로 시작하는 방향을 권고했다.
- 상태 전이·검증·멱등성·테스트는 Spring 없이 연습하고, Spring MVC·DI·Validation·Transaction·Repository가 학습 대상일 때만 `spring-lab`을 별도로 추가한다.
- production 저장소에 연습 코드를 섞지 않고 MongoDB·Redis·AWS 같은 운영 의존성도 처음부터 추가하지 않는다.
- 실제 학습 프로젝트는 아직 생성하지 않았으며 Learning Core 애플리케이션과 외부 계약은 변경하지 않았다.

## AttemptGroup 분산 trace·구조화 관측 계약 검토 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 없다. 제안한 W3C Trace Context, baggage 제외, event payload·digest와 trace metadata 분리, publish attempt별 새 span, trace 장애 시 업무 전달 계속 원칙은 타당하다.
- outbox에는 raw inbound header 대신 검증된 `traceId`, parent `spanId`, `traceFlags`만 transport metadata로 저장하는 것을 v1 기본안으로 권장한다. `tracestate`는 실제 backend 요구가 생기기 전에는 저장하지 않고, 필요해지면 W3C propagator 검증·크기 제한·로그 금지를 적용한다.
- 모든 재시도에서도 동일 traceId가 필수라면 각 publish attempt를 저장된 origin context의 자식인 sibling span으로 생성해야 한다. OpenTelemetry link만 사용하면 새 traceId가 될 수 있으므로 `parent 또는 link`라는 선택지는 계약에서 제거하거나 traceId 연속성 요구를 완화해야 한다.
- Billing은 현재 inbound W3C traceId를 이어받지만 production code에서 `attempt_group_event_consume`라는 별도 span을 만들지는 않는다. 정확한 span 이름이 계약이면 HTTP server span 아래 별도 internal span 또는 ObservationConvention을 추가해야 하며, 로그의 `operation` 값과 span name을 구분해야 한다.
- 401/403은 개별 event의 영구 payload 실패가 아니라 전역 인증 설정 장애이므로 즉시 DEAD_LETTER 처리하지 않고 `BLOCKED_AUTH` 또는 PENDING+긴 backoff와 publisher circuit/alert로 격리한 뒤 같은 eventId로 재개하는 안을 권장한다. `invalid_trace_context`는 delivery outcome이 아니라 missing/invalid 고정 counter로 기록한다.
- SigV4와 tracing의 header mutation 순서를 계획에 고정해야 한다. 최종 publish/client span context를 inject한 뒤 SigV4 서명하고 이후 `traceparent`를 변경하지 않거나, trace header를 서명 대상에서 명시적으로 제외해야 한다. 자동 HTTP client instrumentation이 context를 재주입해 서명을 깨거나 예상 span 계층을 바꾸지 않는 contract test가 필요하다.
- Learning Core에는 아직 tracing bridge와 AttemptGroup publisher가 없고 현재 TMI-116 예외는 이 범위를 명시적으로 제외한다. 구현 전 신규 Learning Core Jira·PLAN·AGENTS 예외를 만들고, 이번 검토 보정사항과 필수 테스트를 반영해야 한다.
- 이번 작업은 분석 및 기록 문서 갱신만 수행했다. 애플리케이션 코드·공개 API·event JSON·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았고 Gradle 테스트는 실행하지 않았다.

## AttemptGroup publish 재시도 span 관계 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 하나의 `traceId`는 사건 전체의 폴더 번호, 각 `spanId`는 origin·publish 시도·Billing 처리 같은 개별 작업 번호로 설명했다.
- 최초 publish와 재시도를 모두 저장된 origin span의 자식으로 만들면 publish attempt들이 sibling이 되고 같은 traceId 안에서 서로 다른 spanId를 가진다. 재시도를 직전 실패 attempt의 자식으로 만들지 않아 독립된 재시도라는 의미도 유지한다.
- OpenTelemetry link는 다른 trace에서 원본 trace를 참조할 수 있게 하는 연결일 뿐 같은 traceId를 상속시키지 않는다. 따라서 “모든 재시도와 Billing 처리를 같은 traceId로 검색”하는 현재 목표에는 parent 관계가 필요하다.
- origin context가 없을 때도 이후 재시도까지 같은 traceId가 필요하다면 첫 전송 전에 fallback delivery root context를 한 번 생성·보존하고 각 attempt를 그 자식으로 만들어야 한다. 그렇지 않으면 missing-context 재시도마다 새 trace가 생길 수 있다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## AttemptGroup 서비스 경계 span 설명 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 설명은 서비스 경계마다 기존 spanId를 변경하는 것이 아니라 같은 traceId를 상속한 새 spanId를 생성한다는 것이다.
- Billing의 W3C trace 연결은 현재 동작하지만 `attempt_group_event_consume`은 로그 operation일 뿐 production span name으로 고정되지 않았다. 명확한 업무 단계가 필요하면 HTTP server span 아래 별도 internal span을 추가한다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup 서비스 경계 span과 Billing span name 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 새로운 서버나 처리 단계로 넘어갈 때 기존 spanId를 수정하는 것이 아니라 같은 traceId 아래 새로운 span과 새로운 spanId를 생성한다는 의미로 정리했다.
- 현재 Billing은 inbound `traceparent`를 통해 Learning Core와 같은 traceId를 이어받고 새로운 HTTP server spanId도 자동 생성하므로 분산 trace 연결 자체는 동작한다.
- 다만 Billing trace 화면의 실제 span name은 Spring HTTP server 관측 이름일 수 있고 로그의 `operation=attempt_group_event_consume` 값이 자동으로 span name이 되지는 않는다.
- 업무 처리 단계를 명확히 보이게 하려면 Billing HTTP server span 아래 `attempt_group_event_consume` internal span을 하나 더 생성하는 안을 권장한다. 그러면 HTTP 수신과 실제 event 처리 시간이 분리된다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## AttemptGroup outbox trace metadata 저장 방식 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 동일한 사건은 하나의 `traceId`로 묶고 origin·publish attempt·Billing consume은 서로 다른 `spanId`로 구분한다는 이해를 재확인했다.
- W3C `traceparent`는 version, traceId, 현재 spanId와 flags를 한 문자열로 포장한 전송용 header다. outbox에는 외부에서 받은 문자열을 그대로 저장하지 않고 propagator가 검증·분해한 `traceId`, parent `spanId`, `traceFlags`만 transport metadata로 보존한다.
- publisher는 저장 metadata로 parent context를 복원한 뒤 새 publish spanId를 만들고, 같은 traceId와 새 spanId가 담긴 새로운 `traceparent`를 HTTP 요청에 inject한다. 원본 header를 replay하면 publish span이 trace에서 사라지는 문제가 생긴다.
- trace metadata는 event JSON·canonical digest·idempotency key와 분리하고 baggage는 저장하지 않는다. v1에서는 필요성이 확인되지 않은 `tracestate`도 생략하는 권장안을 유지한다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## AttemptGroup Billing 업무 span과 인증 장애 처리 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 3번은 로그 내용을 더 길게 만드는 작업이 아니라 Billing HTTP server span 아래 `attempt_group_event_consume` 업무 span을 추가해 trace timeline에서 HTTP 수신과 decode·DB 반영 시간 및 실패 위치를 구분하는 관측성 보완이다.
- 4번은 400·409·422처럼 event 자체가 잘못된 영구 오류와 401·403처럼 IAM·SigV4·route 등 publisher 전역 인증 설정이 잘못된 장애를 분리하는 정책이다.
- 401·403에서는 개별 event를 DEAD_LETTER로 보내지 않고 `auth_failure`를 기록·경보하며 publisher circuit을 열고 event를 `BLOCKED_AUTH` 또는 장기 backoff PENDING으로 보존한다. 인증 복구 후 같은 eventId로 재개한다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup span 추가와 오류 분류 의미 재확인 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 3번은 로그 line을 append하는 것이 아니라 기존 Billing HTTP server span 아래 `attempt_group_event_consume` internal span 하나를 추가하는 것이다. 구조화 로그 필드는 늘리지 않는다.
- 시간 분리는 Learning Core publish/client span, Billing HTTP server span과 Billing consume span을 함께 비교해 판단한다. consume span은 decode·멱등성 판단·Mongo 반영이라는 업무 처리 구간을 나타낸다.
- 4번은 오류 이름만 자세히 나누는 것이 아니라 HTTP category별 상태 전이와 후속 행동을 고정한다. network·408·425·429·5xx는 retry, 400·409·422는 DEAD_LETTER, 401·403은 전역 auth 차단·경보·복구 후 재개로 처리한다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup span·오류 분류 설명 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 이해는 3번이 로그 추가가 아닌 Billing consume internal span 추가이고, 4번이 오류별 명칭뿐 아니라 retry·dead-letter·auth-block 후속 동작까지 구분하는 정책이라는 것이다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup trace header와 SigV4 순서 설명 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 5번은 publisher가 새 span을 만든 뒤 최종 `traceparent`를 요청에 붙이고, 그 완성된 요청을 SigV4로 서명한 다음 header를 변경하지 않고 전송하도록 순서를 고정하는 것이다.
- SigV4 서명 뒤 자동 tracing instrumentation이 `traceparent`를 새로 쓰면 Billing이 받은 요청과 서명 대상이 달라져 401·403 인증 실패가 날 수 있다. 따라서 이 client의 trace inject 소유자는 하나로 제한한다.
- 재시도마다 새 publish spanId와 시각이 생기므로 eventId·payload는 유지하되 `traceparent`와 SigV4 서명은 매번 새로 생성한다. 서명된 HTTP 요청 자체를 재사용하지 않는다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup SigV4 순서 설명 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 원칙은 publish span과 `traceparent`를 먼저 확정하고 SigV4로 서명한 뒤 전송 전 header를 변경하지 않는 것이다. 재시도는 같은 eventId·payload를 유지하면서 새 span과 새 서명을 생성한다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup SigV4 최종 단계 확인 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- SigV4는 URI·method·body·일반 header와 `traceparent`가 모두 확정된 뒤 전송 직전의 마지막 논리적 변경 단계에서 수행한다.
- 서명 후 SDK signed request를 실제 HTTP request로 변환하는 작업은 가능하지만 서명된 header·body·path를 변경하거나 자동 tracing이 `traceparent`를 다시 inject해서는 안 된다.
- 설명과 기록 문서만 갱신했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup SigV4 마지막 서명 원칙 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- 최종 확인은 URL·method·body·header와 `traceparent`를 먼저 확정하고 SigV4 서명을 마지막 논리적 변경 단계로 수행한 뒤 요청을 변경하지 않고 전송한다는 것이다.
- 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았으며 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup 분산 trace 계약 최종 확정 판단 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다. 지금까지 검토한 trace propagation·span·오류 분류·SigV4 원칙은 권장안 기준으로 계약을 동결할 수 있는 상태다.
- 동일 traceId와 단계별 새 spanId, origin 공통 parent의 retry sibling, 검증된 `traceId`·`parentSpanId`·`traceFlags` outbox metadata, baggage·raw header·v1 tracestate 제외를 확정안으로 둔다.
- Learning Core는 publish attempt별 `attempt_group_outbox_publish` span, Billing은 HTTP server span 아래 `attempt_group_event_consume` internal span을 사용한다. missing/invalid context는 최초 fallback trace anchor를 CAS로 한 번 보존하고 counter 후 delivery를 계속한다.
- publisher outcome은 `delivered`, `retry_scheduled`, `dead_letter`, `auth_failure`, `lease_lost`로 고정하고 `temporary_failure`는 제거한다. network·408·425·429·5xx는 retry, 400·409·422는 dead-letter, 401·403은 `BLOCKED_AUTH`·전역 circuit·alert·복구 후 재개다.
- 최종 요청에 trace header를 inject한 뒤 SigV4를 마지막 논리적 변경 단계에서 수행하며, 재시도마다 같은 eventId·payload와 새 span·새 서명을 사용한다. 구현 전 Learning Core 신규 Jira·PLAN·AGENTS 명시적 예외가 필요하다.
- 이번 판단은 분석과 기록 갱신만 수행했으며 애플리케이션·외부 계약·Billing 코드·AWS·Jira·Git commit/push는 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup 분산 trace 계약 동결 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- trace context 저장·재시도 span·Billing consume span·오류별 상태와 행동·SigV4 최종 서명 순서를 포함한 관측 계약을 권장안으로 동결했다.
- 다음 단계는 Learning Core 신규 Jira·PLAN과 AGENTS 명시적 예외 작성이며 이번 작업에서는 애플리케이션 코드·외부 계약·AWS·Jira·Git commit/push를 변경하지 않았다.

## AttemptGroup trace Billing 전달 범위 확인 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Billing 보완 또는 Learning Core 후속 Jira는 아직 없다.
- Billing은 W3C-only propagator, inbound traceId 연속성, 고정 구조화 로그와 저카디널리티 metric이 이미 구현돼 있어 event JSON·endpoint·status 계약 변경은 필요 없다.
- Billing 필수 보완은 production HTTP server span 아래 `attempt_group_event_consume` internal span을 실제 decode·service 처리 범위에 생성하고, 현재 테스트의 수동 span이 아니라 실제 controller 요청에서 같은 traceId·서로 다른 spanId·정확한 span name을 검증하는 것이다.
- 401·403의 `BLOCKED_AUTH`·circuit·재개와 trace inject 후 SigV4 최종 서명은 Learning Core publisher 책임이다. Billing은 인증 실패 status를 안정적으로 반환하고 raw trace header·payload·식별자·credential을 log/span/metric에 남기지 않는 기존 경계를 유지한다.
- 선택 보완으로 missing·invalid를 함께 기록하는 metric 이름의 의미를 명확히 하고 baggage 미전파와 inner span 예외 종료·민감정보 비기록 테스트를 추가할 수 있다. exporter/backend·dashboard·alert는 별도 운영 범위다.
- 이번 확인은 읽기 전용 분석과 기록 갱신만 수행했으며 Billing·Learning Core 애플리케이션, 외부 계약, AWS, Jira와 Git commit/push를 변경하지 않았다. 코드 변경이 없어 Gradle 테스트를 실행하지 않았다.

## AttemptGroup trace Billing 전달 종료 상태 (2026-08-31)

- 관련 완료 이슈는 Billing `TMI-117`이며 Billing 보완과 Learning Core 후속 Jira는 아직 없다.
- Billing 전달 필수사항은 production `attempt_group_event_consume` internal span과 실제 Controller 기반 trace/span contract test이며 endpoint·event payload·status 계약 변경은 없다.
- Learning Core 전용 outbox·retry·auth-block·SigV4 책임과 Billing 보완 범위를 분리해 전달했으며 이번 작업에서는 애플리케이션 코드·AWS·Jira·Git commit/push를 변경하지 않았다.

## Billing AttemptGroup consume span 구현 확인과 Learning Core 다음 작업 (2026-09-01)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core outbox/publisher 후속 Jira는 아직 없다.
- Billing 로컬 `develop@37a3e1d`의 미커밋 변경에서 production Controller가 strict decode와 service 처리를 `attempt_group_event_consume` span으로 감싸고, Micrometer helper가 정상·RuntimeException·Error 경로에서 span을 종료·오류 기록하는 구현을 확인했다.
- embedded Tomcat 통합 테스트는 same inbound traceId, HTTP SERVER와 INTERNAL consume의 서로 다른 spanId·descendant 관계, 정확한 span name, decoder/service scope, baggage 미전파, 정상·409 예외 종료와 금지 attribute 부재를 검증한다.
- Billing 현재 작업 트리에서 `./gradlew clean test`를 재실행해 138개 성공, 실패·오류·skip 0을 확인했고 `git diff --check`도 통과했다. 다만 변경은 아직 commit·push·PR·merge되지 않아 배포 가능한 저장소 상태는 아니다.
- 다음 즉시 순서는 Billing trace 보완 commit·PR·merge·consumer 배포 후 Learning Core 신규 Jira·PLAN·AGENTS 예외를 확정하는 것이다. 그 다음 Exam 상태 전이와 같은 Mongo Transaction/CAS에서 `GRADING` 또는 terminal outbox를 저장하고 lease publisher가 W3C span·SigV4로 Billing에 전달하도록 구현한다.
- 구현 계획에서는 GRADING의 정확한 local trigger, COMPLETED evidence 세 boolean의 source of truth, RETAKE_AVAILABLE failureCode mapping과 Session당 terminal event 하나의 불변식을 코드 경계에 매핑해야 한다. publisher feature flag는 기본 off로 유지한다.
- 이번 작업은 Billing 읽기 전용 검토와 Learning Core 기록 갱신만 수행했다. Billing·Learning Core 애플리케이션, 공개 API·AI·S3·Redis·event wire, AWS, Jira와 Git commit/push는 변경하지 않았다.

## Billing consume span 검증 종료 상태 (2026-09-01)

- 관련 완료 이슈는 Billing `TMI-117`이며 Learning Core 후속 Jira는 아직 없다.
- Billing 로컬 구현과 전체 138개 테스트 성공을 확인했으나 변경은 미커밋이므로 commit·PR·merge·배포가 선행돼야 한다.
- 이후 Learning Core 신규 Jira·PLAN·AGENTS 예외를 만들고 Exam 상태와 outbox 동시 저장 및 lease·W3C·SigV4 publisher를 구현하는 순서로 확정했다.

## Billing trace 보완 merge 확인과 Learning Core outbox 계획 (2026-09-01)

- Billing `develop`과 `origin/develop`은 PR #5 merge commit `a34766e`로 일치하고 작업 트리는 clean이다. `b1f6fbd`의 production `attempt_group_event_consume` span, 실제 HTTP trace/span·baggage·privacy·오류 테스트가 병합돼 Learning Core publisher의 consumer-first 선행 조건을 충족했다.
- `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`를 추가했다. 모든 필수 retry 0 submit의 GRADING, strict 결과·점수·Summary evidence의 COMPLETED, 최종 복구 실패의 RETAKE_AVAILABLE을 ExamSession 상태와 outbox의 동일 Mongo Transaction/CAS로 만들고 Session당 terminal event 하나를 보장하는 계획이다.
- publisher는 lease 기반 multi-instance claim, same eventId/canonical payload retry, DELIVERED 30일·DEAD_LETTER 90일·미전달/BLOCKED_AUTH 무TTL, W3C same trace/different span, trace inject 뒤 SigV4 최종 서명을 사용한다.
- 구현 전 신규 Learning Core Jira와 현재 TMI-116 제외 범위를 해소하는 `AGENTS.md` 명시적 예외가 필요하다. 사용자 확정이 필요한 핵심값은 권장 `GRADING` deadline `PT30M`이고, 신규 Billing-linked Session의 Summary source는 `exam_summaries` only를 권장한다.
- 이번 작업은 Billing 읽기 전용 merge 확인과 Learning Core 계획·상태·작업 기록 문서만 변경했다. 애플리케이션, 공개 API·AI·S3·Redis·Billing event wire, AWS, Jira와 Git commit/push는 변경하지 않았다.

## AttemptGroup 구현 전 선택지 정리 (2026-09-01)

- 관련 선행 이슈는 Billing `TMI-117`이며 Learning Core 신규 구현 Jira는 아직 없다.
- 구현 전 필수 선택을 GRADING deadline, 최종 실패 확정 방식, Summary 완료 source로 구분했다. 권장 조합은 `PT30M`, 완료 evidence 우선 뒤 retry 소진·정합성 오류를 즉시 terminal 처리하고 deadline을 정체 safety net으로 사용하는 단계적 확정, 신규 Billing-linked Session의 `exam_summaries` only다.
- 운영 활성화 전 선택은 401/403 인증 복구와 기존 linked Session backfill이다. 권장안은 `BLOCKED_AUTH`·전역 circuit 뒤 15분마다 한 event만 half-open probe하고, 기존 Session은 전체 자동 스캔 대신 inventory/dry-run 후 allowlist backfill하는 방식이다.
- poll 1초, batch 20, lease 30초와 writer/publisher 기본 off는 설정으로 조절 가능한 기술 기본값이므로 별도 제품 결정 없이 권장값으로 둘 수 있다.
- 선택지·장단점은 `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md` 3절에 반영했다. 애플리케이션, 외부 계약, AWS, Jira와 Git commit/push는 변경하지 않았다.

## AttemptGroup 정책 확정과 AGENTS 영구 허용 (2026-09-01)

- 관련 선행 이슈는 Billing `TMI-117`이며 Learning Core 구현 Jira는 아직 생성되지 않았다.
- 사용자가 `1B·2C·3A·4A·5C`를 승인했다. GRADING deadline은 `PT30M`, 완료 evidence 우선·retry 소진과 정합성 오류 즉시 종료·deadline safety net의 단계적 실패 확정, 신규 Billing-linked `exam_summaries` only, 401/403의 15분 단일 half-open, inventory/dry-run 후 allowlist backfill이 확정값이다.
- 특정 Jira에만 묶인 예외를 반복하지 않고 `AGENTS.md`에 AttemptGroup 상태 판정·durable outbox·lease publisher·제한된 reconciliation·RETAKE replacement 연결을 영구 허용하는 규칙을 추가했다. 신규 Jira는 범위 허가가 아니라 작업 추적과 완료 관리 목적으로 생성하면 된다.
- 영구 허용은 Learning Core 내부 구현에만 적용한다. 공개 API·AI·S3·Redis 계약, Billing consumer/저장소, UserMerged·owner rebind·결제 보상, 실제 AWS 리소스 생성·배포는 범위 밖이다.
- 계획서의 정책 상태, Phase 0과 완료 조건을 영구 허용 기준으로 갱신했다. 애플리케이션·AWS·Jira·Git commit/push와 Secret/Token은 변경하지 않았다.

## TMI-118 AttemptGroup outbox/publisher Jira 생성 (2026-09-01)

- Learning Core 후속 구현 Jira `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`을 `작업` 유형, 상태 `해야 할 일`로 생성했다.
- 이슈에는 GRADING/COMPLETED/RETAKE_AVAILABLE 판정, `PT30M` 단계적 실패 확정, strict `exam_summaries` evidence, Session당 terminal 하나, Mongo Transaction/CAS, lease·retry·retention·BLOCKED_AUTH, W3C trace와 최종 SigV4, RETAKE replacement 연결과 전체 완료 조건을 기록했다.
- 선행 이슈 `TMI-116`과 `TMI-117`, 공개 API·AI·S3·Redis 계약 불변, Billing·결제·인프라 제외 범위와 production 활성화 gate를 명시했다.
- 계획서의 Jira 상태와 Phase 0·완료 체크리스트를 `TMI-118` 기준으로 갱신했다. 애플리케이션 구현, AWS 리소스와 Git commit/push는 수행하지 않았다.

## TMI-118 Jira 생성 종료 기록 동기화 (2026-09-01)

- `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`은 `작업` 유형과 `해야 할 일` 상태로 생성 완료됐다.
- 확정 정책, 구현·제외 범위, 완료 조건과 production 활성화 제한은 Jira와 `docs/codex/ATTEMPT_GROUP_OUTBOX_PUBLISHER_IMPLEMENTATION_PLAN.md`에 동기화돼 있다.
- 이번 종료 동기화는 CURRENT_STATE와 WORKLOG marker 보완만 수행했으며 애플리케이션·AWS·Jira 내용·Git commit/push와 Secret/Token은 변경하지 않았다.

## 시스템 구성도 표현 도구 선택 (2026-09-01)

- 시스템 전체의 서비스·저장소·외부 연동·신뢰 경계는 편집성과 자유 배치가 좋은 draw.io를 원본으로 사용하는 방향을 권고했다.
- Mermaid는 별도의 요청 sequence·상태 전이·간단한 흐름처럼 코드와 함께 버전 관리할 도식에 사용한다.
- 생성 이미지는 발표용 삽화에는 사용할 수 있지만 정확한 label, 연결 관계, 편집과 diff가 필요한 시스템 구성도의 source of truth로 사용하지 않는다.
- 동일 도식을 여러 형식으로 중복 유지하지 않는다. 이번 작업에서 실제 도식과 애플리케이션·외부 계약은 변경하지 않았다.

## 토선생 앱 시스템 구성도 draw.io (2026-09-01)

- 별도 Jira 없이 `docs/architecture/tosunsaeng-app-system-configuration.drawio` 한 페이지 시스템 구성도를 추가했다.
- 앱, Identity, Learning Core, Billing, Python AI, 서비스별 MongoDB, Redis와 S3를 배치하고 공개 HTTPS, JWT/JWKS, SigV4·VPC Lattice, Presigned PUT, AI 요청·Callback을 표현했다.
- Billing 내부 전용 경계, `examId→userId`, `AI user_id=examId`, 실제 userId 비전송, 서비스별 데이터 소유권과 rollout gate를 명시했다.
- draw.io XML, 1개 diagram, 29개 vertex, 14개 edge와 모든 source/target 참조를 검증했다. 애플리케이션과 외부 계약은 변경하지 않았다.

## 시스템 구성도 연결선 정리 (2026-09-01)

- 사용자 PNG에서 서비스 내부 도형을 관통하던 연결선을 확인하고 `tosunsaeng-app-system-configuration.drawio`의 주요 9개 edge에 고정 entry/exit와 경유점을 지정했다.
- 앱·Identity·Learning Core·Billing 연결은 상단·서비스 하단 통로로, AI 요청은 데이터 저장소 사이 빈 통로로, Callback은 우측 외곽 통로로 분리했다.
- 긴 연결선 label을 축약하고 offset을 적용했다. XML, 14개 edge, 9개 수동 route와 source/target 참조를 검증했으며 시스템 의미와 외부 계약은 변경하지 않았다.
- 종료 훅 기준 WORKLOG marker와 현재 상태를 동기화했다. diagrams.net 육안 확인 외 남은 애플리케이션·배포 작업은 없다.

## 시스템 구성도 관점 검토 (2026-09-01)

- 현재 draw.io는 일반 제품 시스템 구성도보다 `앱 백엔드 기술 구성도` 성격이 강하며 백엔드 멘토링에는 적합하다고 판단했다.
- 제품·비개발 독자를 위해서는 사용자, 앱 기능, 로그인→시험→녹음→AI 채점→결과 흐름과 세 서비스의 역할만 보이는 상위 페이지가 별도로 필요하다.
- 권고 구조는 1페이지 `전체 시스템 구성도`, 2페이지 기존 `백엔드 기술 구성도`다. 실제로 확인되지 않은 프론트 인프라는 추정해 넣지 않는다.
- 이번 검토에서 draw.io, 애플리케이션과 외부 계약은 변경하지 않았다.

## 전체 시스템·AWS 구성도 (2026-09-01)

- 별도 Jira 없이 `tosunsaeng-app-system-configuration.drawio`를 2페이지로 확장했다. 첫 페이지는 전체 시스템·AWS 구성도, 둘째 페이지는 기존 백엔드 기술 구성도다.
- AWS 페이지는 현재 ALB+Identity/Learning Core ECS, GitHub Actions OIDC→ECR→ECS 배포와 S3를 실선으로 표시한다.
- 아직 미배포인 Billing VPC Lattice AWS_IAM, private Fargate·no ALB/public IP와 환경별 role/SG/Secret/database 분리 목표는 주황 점선으로 표시한다.
- 실제 ALB listener/target/DNS, subnet·SG·ARN, DB/Redis/NAT network path와 Secret 서비스는 read-only AWS inventory 필요 항목으로 남겼다.
- XML, 2개 페이지, AWS 페이지 vertex 25개·edge 16개와 source/target 참조를 검증했다. 애플리케이션과 외부 계약은 변경하지 않았다.
- AWS 페이지의 16개 연결선 중 14개에 고정 경유점을 두어 Identity→Lattice, Learning Core↔AI, 앱→S3와 데이터 의존성 선이 다른 서비스·managed resource 도형 위를 지나지 않게 분리했다.

## 1차 업데이트 완료 기준 시스템·AWS 구성도 (2026-09-01)

- `tosunsaeng-app-system-configuration.drawio` 첫 페이지는 현재/목표 비교가 아니라 1차 업데이트의 모든 release gate와 production canary가 완료된 시점의 단일 스냅샷이다.
- 앱 기능은 SNS/Phone 로그인, 무료 모의고사, 기존 시험·AI 결과와 10초 챌린지를 포함한다. Identity는 Guest/MEMBER·JWT/JWKS·merge/withdrawal, Learning Core는 시험·무료 응시 saga·AI 채점·Challenge·AttemptGroup publisher를 담당한다.
- AWS production 구조는 public ALB 뒤 Identity/Learning Core, private Billing Fargate, VPC Lattice `AWS_IAM`·SigV4, ECR 3개 서비스 image, S3 audio와 서비스별 MongoDB·Redis를 표시한다.
- 기존 둘째 페이지 백엔드 기술 구성도와 첫 페이지의 고정 routing은 보존했다. XML 2페이지, 첫 페이지 vertex 25개·edge 16개, 누락 연결 참조 0개와 과도기 표기 제거를 검증했다.
- 이 변경은 별도 Jira가 아니며 애플리케이션·외부 계약·AWS resource·배포·Git commit/push를 변경하지 않았다. 정확한 AWS resource ID는 inventory 없이 추정하지 않았다.

## 앱 프론트·AI 근거 기반 전체 제품 구성도 (2026-09-01)

- 최신 구성도는 3페이지다: `1. 제품·사용자 흐름`, `2. AWS·배포`, `3. 백엔드 기술 상세`. 첫 페이지가 기본 설명용이며 사용자 가치와 앱 화면·기능을 서비스보다 먼저 보여준다.
- 앱 근거는 `Too-Much-I/app-front-end@4e6c5957`이다. Expo 57 React Native, 홈/모의고사/피드백, Guest 인증 복구, 11문항 녹음·업로드·Polling, WebView/native bridge, 재답변, 10초 챌린지, Amplitude·Clarity·Sentry와 EAS 전달 흐름을 반영했다.
- AI 근거는 `Too-Much-I/web-ai@ee9db665`이다. AI는 AWS 외부가 아니라 ECS의 FastAPI API·Redis queue·4 worker이며 Q1 Azure, Q2~Q11 STT+Azure+LLM/VLM, checklist score, 한국어 피드백·요약 Callback을 수행한다.
- `1차 업데이트 완료 기준`이므로 프론트의 현재 Challenge 개발 mock은 제거되고 실제 API가 연결된 상태, AI 저장소에 현재 없는 Challenge 전용 평가·Callback과 SNS/Phone login은 구현·E2E가 끝난 상태로 표현했다. 이는 현재 코드 사실이 아니라 완료 조건에 따른 계획 상태다.
- XML 3페이지, 페이지별 vertex/edge `24/17`, `25/17`, `29/14`, 전체 연결 참조 누락 0개를 검증했다. 문서 외 코드·계약·AWS·Jira·배포는 변경하지 않았다.

## 전체 제품·AWS 구성도 화살표 정리 (2026-09-01)

- 제품 페이지의 의미와 내용은 유지하면서 edge를 17개에서 14개로 줄였다. AI 요청/Callback은 양방향 한 줄이며, 결과 회귀·데이터 설명선은 박스 설명으로 대체했다.
- 앱의 Presigned PUT 선은 Identity와 Learning 사이 빈 통로를 수직으로 내려가 하단 전용 lane으로 S3에 연결되므로 서비스 도형을 관통하지 않는다.
- AWS 페이지의 Learning Core↔AI 왕복선도 양방향 한 줄로 합쳐 edge가 16개가 됐다. 기존 고정 routing은 유지했다.
- 최신 검증값은 페이지별 vertex/edge/routed `24/14/8`, `25/16/13`, `29/14/9`, 전체 누락 source/target 0개다.
- 별도 Jira와 애플리케이션·계약·AWS·배포 변경은 없다. diagrams.net 실제 화면에서 label offset의 최종 육안 확인만 남는다.

## 구성도 화살표 레이어 조정 (2026-09-01)

- 세 페이지 모두 큰 영역 배경을 최하단, 연결선을 중간, 실제 기능·서비스 도형과 텍스트를 최상단에 두었다.
- 화살표는 AWS/VPC/서비스 영역 색상 위에는 보이지만 개별 카드와 글자 뒤로 지나가며, 연결 경로·source/target·경유점은 유지했다.
- 페이지별 vertex/edge는 `24/14`, `25/16`, `29/14`로 유지됐다. foreground보다 앞에 남은 edge, edge 위로 잘못 올라온 영역 배경과 누락 source/target은 모두 0개다.
- 이번 변경은 별도 Jira가 아니며 애플리케이션·외부 계약·AWS·배포·Git commit/push 변경은 없다.
- diagrams.net 실제 화면에서 카드 뒤 가림 동작을 육안 확인하는 단계만 남는다.

## draw.io 실제 레이어 분리·렌더 검증 (2026-09-01)

- XML 순서만 바꾸는 방식은 실제 renderer에서 edge z-order를 보장하지 못해 폐기했다.
- 최신 파일은 각 페이지마다 top-level `배경`, `연결선`, `도형·텍스트` layer를 실제로 가진다. 모든 edge parent는 `edge-layer`이고 실제 카드는 foreground layer에 있다.
- 제품·AWS 페이지는 복원·재검증했고, 백엔드 상세는 사용자 원본 `제목 없는 다이어그램.drawio`를 수정하지 않고 읽기 전용으로 결합했다.
- Browser 스킬로 첫 페이지를 diagrams.net 편집기에 로드해 카드가 선보다 위에 렌더링되는 실제 화면을 확인했다.
- 검증값은 페이지별 `3 layers / 24 vertices / 14 edges`, `3 / 21 / 14`, `3 / 28 / 15`이며 잘못된 edge parent와 누락 source/target은 모두 0개다.
- 별도 Jira와 애플리케이션·외부 계약·AWS·배포·Git commit/push 변경은 없다. 로컬에서 수정 파일을 새로 열어 확인해야 한다.

## 데일리 학습 콘텐츠 수행 방법 문구 (2026-09-01)

- 수행 방법 표의 `개발 단계 > 데일리 학습 콘텐츠`에는 10초 챌린지를 중심으로 콘텐츠 자체 제작·전문가 검수, 매일 문제 제공, 음성 녹음, AI 분석, 교정·모범답안·피드백과 데이터 기반 개선 절차를 작성하는 방향을 권고했다.
- 제출용 기본 문장과 글자 수가 짧을 때 사용할 축약형을 함께 제공한다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 중간 발표용 개발 문제점 2개 선정 (2026-09-01)

- 중간 발표에는 `AI 채점 품질·신뢰도`와 `외부 AI API 비용·의존성` 두 가지를 사용하는 것으로 권장했다.
- 해결 방안은 품질 sample 검수·지표 모니터링과 대체 모델 사전 검증·유연한 모델 선택 구조다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 전체 서비스 개발 문제점·해결 방안 제출 문구 종료 (2026-09-01)

- 개발 측면 예상 문제점 7개와 동일 순서의 해결 방안 7개를 사용자가 제시한 문체로 작성 완료했다.
- AI 품질·비용, 서비스 계약, 음성/비동기 안정성, 확장성, 데이터 보호와 콘텐츠 품질을 포함한다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 전체 서비스 개발 측면 예상 문제점 문체 정리 (2026-09-01)

- 사용자가 제시한 형식에 맞춰 전체 서비스 개발 위험을 `발생 가능성` 문장과 같은 순서의 해결 방안 bullet로 정리했다.
- AI 품질·비용, 서비스 계약, 음성/비동기 안정성, 개인정보와 확장성을 포함한다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 전체 서비스 문제점·해결 방안·결과물 문구 종료 (2026-09-01)

- 토선생 전체 기능을 기준으로 개발 예상 문제점, 항목별 해결 방안과 결과물 형태의 제출용 문구 작성을 완료했다.
- 상세 문단, 문제점과 해결 방안 대응표 및 축약형을 제공했다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 전체 서비스 예상 문제점·해결 방안·결과물 문구 (2026-09-01)

- 예상 문제점과 결과물 형태의 범위를 데일리 콘텐츠에서 토선생 전체 서비스로 정정했다.
- 전체 위험은 범위·서비스 계약·AI 품질·음성/비동기 안정성·콘텐츠 운영·개인정보·비용/확장성·배포 운영으로 분류하고 각 항목에 해결 방안을 연결했다.
- 결과물은 모바일 앱, 앱 서버 3종, AI 서버, 콘텐츠 catalog, 학습 로드맵·챗봇, 계약·테스트·운영·배포 문서로 정리한다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 데일리 학습 콘텐츠 문구 작성 종료 (2026-09-01)

- 데일리 학습 콘텐츠의 수행 방법, 개발 측면 예상 문제점과 결과물 형태에 대한 제출용 기본 문장 및 축약형 작성을 완료했다.
- 문제점은 콘텐츠 품질·AI 일관성·음성/비동기 안정성·비용/개인정보로, 결과물은 앱 화면·catalog DB·API·AI 피드백·운영 문서로 정리했다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 데일리 학습 콘텐츠 예상 문제점·결과물 문구 (2026-09-01)

- 개발 측면 문제점은 콘텐츠 지속 확보·난이도 품질, AI 판정 일관성, 음성 업로드, 비동기 지연·중복, 비용·개인정보 보호로 정리했다.
- 결과물은 앱 화면, 콘텐츠 catalog DB, 백엔드·AI API 파이프라인, AI 피드백 결과와 테스트·운영 문서로 구분한다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 중간 발표용 개발 문제점 2개 최종 선정 (2026-09-01)

- 중간 발표용으로 `AI 채점 품질·신뢰도`와 `외부 AI API 비용·의존성`을 최종 추천했다.
- 해결 방향은 채점 표본 검수·품질 지표 모니터링과 대체 모델 검증·비용/성능 기반 모델 선택 구조다.
- 별도 Jira가 아니며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 학습 로드맵·챗봇 무료/유료 설명 정리 (2026-09-02)

- 무료 버전은 최초 목표 설정 시 입력한 목표 등급과 시험 준비 기간을 기준으로 사전 설계된 표준 학습 로드맵을 안내한다.
- 유료 버전은 표준 로드맵에 사용자의 학습 이력, 모의고사 결과와 피드백을 결합하여 챗봇이 취약점·우선순위·세부 학습 방법을 맞춤 안내한다.
- 유료 로드맵의 갱신 주기, 추천 범위, 설명 근거와 무료/유료 전환 조건은 후속 결정 사항이다.
- 별도 Jira가 없으며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 학습 로드맵·챗봇 상세 설명 확장 (2026-09-02)

- 기능 흐름을 목표 설정, 무료 표준 로드맵 매칭, 유료 학습 데이터 분석, 맞춤 코칭과 성과 기반 재조정으로 구체화했다.
- 개인화는 사전 검증된 로드맵의 범위 안에서 이루어지며 데이터가 부족하면 표준 경로를 유지하는 품질 원칙을 포함했다.
- 목표 등급·준비 기간 분류표, 갱신 시점과 챗봇 제안 항목은 후속 제품 결정 사항이다.
- 별도 Jira가 없으며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 학습 로드맵·챗봇 상세 설명 완료 (2026-09-02)

- 무료 표준 로드맵과 유료 데이터 기반 맞춤 코칭을 전체 기능 흐름, 갱신 방식과 품질 안전장치까지 포함한 최종 문안으로 작성했다.
- 데이터가 부족하면 표준 경로를 유지하고 개인화 결과에는 학습 이력·모의고사·AI 피드백의 근거가 연결되도록 설명했다.
- 로드맵 분류표, 갱신 시점, 추천 단위와 상품 전환 정책은 후속 제품 결정 사항이다.
- 별도 Jira가 없으며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## 실제 AWS 중심 간결 구성도 사전 확인 (2026-09-02)

- 첨부 예시처럼 상위 AWS 리소스와 핵심 통신 흐름만 남긴 간결한 draw.io로 재작성할 예정이다.
- 저장소에서는 GitHub Actions OIDC → ECR → ECS Fargate, S3 Presigned 업로드, MongoDB·Redis 사용을 확인했다.
- 기존 문서의 ALB, VPC Lattice, private Billing, AI ECS와 관측 구조는 실제 콘솔 배포 상태 확인 전에는 계획·추론으로 구분한다.
- 대상 환경·리전, ECS, ingress, VPC, Lattice, 데이터 저장소와 관측 리소스에 대한 사용자 답변 또는 로그인된 AWS 콘솔의 읽기 전용 확인이 다음 단계다.
- 별도 Jira가 없으며 애플리케이션·외부 계약·draw.io·AWS·배포·Git commit/push 변경은 없다.

## Production AWS 구성 확인 대기 (2026-09-02)

- 대상은 서울 리전 Production 단일 환경이며 현재 배포 서비스는 Identity·Learning Core·AI이고 Billing은 제외한다.
- 가비아 도메인 → 공유 ALB 구조이며 CloudFront·API Gateway·WAF는 사용하지 않는다. Cache는 Valkey이고 MongoDB Atlas는 클러스터로 분리돼 있다.
- 사용자가 콘솔 읽기 전용 확인을 승인했으며 AWS 로그인 페이지를 열어 인증 완료를 기다리고 있다.
- 로그인 후 ECS·ALB·VPC·S3·ECR·Valkey·CloudWatch 실배포 상태를 확인하여 간결한 draw.io로 작성한다.
- 별도 Jira가 없으며 AWS 설정·애플리케이션·외부 계약·draw.io·배포·Git commit/push 변경은 없다.

## 실제 AWS 기반 간결 Production 구성도 완료 (2026-09-02)

- `docs/architecture/tosunsaeng-production-aws-simple.drawio` 한 페이지를 신규 작성했다. Billing과 미사용 서비스를 제외하고 앱→가비아 DNS→공유 ALB→Identity/Learning, Service Connect 기반 Learning↔AI, S3·Valkey·Atlas와 OIDC→ECR→ECS만 표현했다.
- 실배포는 서울 리전의 단일 Fargate cluster에 Identity·Learning·AI task 각 1개이며, ALB는 Identity `8081`과 Learning `8080`만 공개한다. AI task에는 API 1개와 worker 4개가 함께 실행된다.
- Public subnet 2개·Private subnet 2개·NAT Gateway 1개, S3 앱 음성 bucket, 단일 node형 Valkey와 CloudWatch Container Insights를 반영했다.
- 실제 resource 이름은 `staging`이고 별도 production-named cluster·ALB는 확인되지 않아 구성도 하단에 명칭 확인 경고를 넣었다.
- Valkey 단일 node·Multi-AZ 비활성·전송 암호화 비활성은 남은 운영 위험이며 AWS 설정은 변경하지 않았다.
- 별도 Jira가 없고 코드·외부 계약·AWS 설정·배포·Git commit/push 변경은 없다.

## 실제 AWS 기반 간결 구성도 검증 종료 (2026-09-02)

- `docs/architecture/tosunsaeng-production-aws-simple.drawio` 작성과 XML·ID·diff 형식 검증을 완료했다.
- 구성도는 한 페이지, 23개 vertex, 13개 edge이며 연결선은 별도 하위 레이어에 배치했다.
- 발표 전 diagrams.net 육안 확인과 실제 `staging` resource 명칭을 Production 발표에서 어떻게 표현할지 결정해야 한다.
- Valkey 단일 node·Multi-AZ·전송 암호화 상태는 운영 전 별도 검토가 필요하다.
- 별도 Jira가 없으며 코드·외부 계약·AWS 설정·배포·Git commit/push 변경은 없다.

## TMI-125 SecurityFilterChain 순서 판단 보충 (2026-09-04)

- 현재 `@Order(2)`의 JWT와 Legacy chain은 `securityMatcher`가 없는 catch-all이라 모든 남은 요청에 매칭된다.
- `@Order(0)` 자체가 특별한 것은 아니며, catch-all인 2보다 앞서고 기존 UserWithdrawn의 1과 중복되지 않는 최소 변경 값이라 선택한다.
- 따라서 UserMerged 전용 chain을 `@Order(3)`으로 두면 `/internal/v1/events/user-merged` 요청이 먼저 `@Order(2)`에 잡혀 전용 workload decoder와 principal 검증이 실행되지 않는다.
- 현 구조를 유지하는 최소 변경안은 UserMerged `@Order(0)`, UserWithdrawn `@Order(1)`, 일반 JWT/Legacy `@Order(2)`다.
- `@Order(3)`도 기술적으로 가능하지만 그 경우 `@Order(2)`에서 internal 경로를 명시적으로 제외하도록 보안 구조를 함께 재설계해야 하므로 현재 계획에는 권장하지 않는다.
- 애플리케이션·보안 설정·테스트는 변경하지 않았고 공개 API와 외부 계약도 유지했다.
- `@Order(n)`는 여러 `SecurityFilterChain` 중 검사 순서를 지정하며 숫자가 작을수록 먼저 검사한다. 처음 matcher가 맞는 chain 하나만 요청을 처리하고 이후 chain은 검사하지 않는다.
- 여기서 “같은 종류의 구성요소”는 추상적인 전체 Spring 객체가 아니라, 현재 애플리케이션에 등록된 여러 개의 `SecurityFilterChain` bean을 뜻한다. 각 chain은 UserMerged, UserWithdrawn, 일반 API처럼 서로 다른 요청용 보안 규칙 묶음이다.
- `SecurityFilterChain`은 요청 경로·HTTP method가 담당 범위인지 선택한 뒤 인증 정보 존재 여부, JWT 서명·만료·issuer·audience 같은 유효성, 인증 필요 여부와 권한을 Controller 실행 전에 검사한다. 실제 UserMerged 업무 데이터 처리 자체는 Controller와 Service가 담당한다.
- TMI-125 설명 기준으로 인증 경계와 업무 경계를 구분했다. chain은 서버 진입 허용 여부를 결정하고, payload·멱등성·소유권 이전·Mongo Transaction은 진입 이후 Controller와 Service가 담당한다.
- UserMerged 호출은 앱이 Learning Core에 직접 보내는 요청이 아니라 Identity가 사용자 merge를 확정한 뒤 보내는 service-to-service 명령이다. Learning Core는 사용자 Access Token과 Identity workload Token을 구분하고, workload Token도 merge와 withdrawal의 audience를 path별로 묶어 다른 목적의 Token이 상호 사용되지 않게 해야 한다.
- 하나의 internal chain으로 합치는 설계도 가능하지만 path별 audience 선택·인가를 내부에서 별도로 구현해야 한다. 현재처럼 exact path별 chain과 decoder를 두는 편이 목적 제한과 테스트 경계가 명확하다.


## 2026-09-08 다우기술 노션 포트폴리오 토선생 소재 선별

- 브랜치: develop. 신규 Jira 없음.
- 목표: 사용자 요청에 따라 토선생 상세 페이지에 넣을 내용을 선별.
- 조사: 기존 포트폴리오 소재·트러블슈팅 문서, 각 서비스 현재 상태와 Learning Core 복구·Saga 테스트 및 MDC 구현을 대조.
- 결정: 빈 종합 피드백과 선택적 채점 복구, 시험 생성·사용권 Reservation Saga를 대표 사례로 추천하고 구조화 로그를 보조 사례로 제안. 인증·저장 모델·S3는 구조 설명에 배치.
- 구분: 구현 및 과거 테스트 기록은 운영 활성화 증거와 다르며 실제 결제·환불은 현재 구현 성과로 사용하지 않음. 처리량·비용·장애 감소 수치 미측정.
- 변경 파일: 이 저장소의 docs/codex/WORKLOG.md와 CURRENT_STATE.md에 조사 기록만 추가. 기존 미커밋 작업 보존.
- 검증: 소스·테스트 정적 조회. 애플리케이션 변경이 없어 Gradle 테스트는 재실행하지 않음.
- 유지: API·AI 계약·feature flag·코드·외부 서비스·Git 이력 변경 없음.
- 다음: 본인 역할과 사례별 설명 가능 범위 확인 후 노션 본문 작성.


## 2026-09-09 토선생 중간발표 PPT 3개 완료

- 신규 Jira 없음. 원본 PDF의 내용·순서를 바탕으로 Toss 참고 명료형, Inflearn 참고 학습형, Claude 참고 지면형 PPT 3개를 완성했다. 각 37장(본문 23장, 부록 14장).
- 산출물: `/Users/msde76/.codex/visualizations/2026/09/09/01a0854f-16fe-7032-a658-69d87c729c9e/toseonsaeng-presentations/output`의 최종 v3 PPTX 3개와 세가지_디자인_비교.png.
- 각 파일의 표 19개·차트 4개 편집 가능, 차트 데이터 workbook 포함. 37장·패키지·레이아웃 finalizer와 전체 111장 렌더링·개별 시각 검토 완료. 최종 수정 두 장 외에는 이전 검토 이미지와 픽셀 일치 확인.
- 사용 시 참고: 시연 영상은 원본에 없어 포함하지 않았다. Mac 글꼴 사용으로 Windows 등에서는 글꼴 대체와 줄바꿈 확인이 필요하다. PowerPoint 앱에서 직접 검증한 것은 아니다.
- 이번 요청의 제작은 완료했으며 다음은 사용자 디자인 선택에 따른 후속 수정이다. 기존 문서 변경을 보존했고 코드·외부 계약·운영 설정·타 저장소·commit/push 변경은 없다.


## 2026-09-10 중간보고서 분량 축약 권고 완료

- 관련 Jira 없음. 공유 Google Docs의 현재 내보내기 기준 33페이지를 확인하고 26~27페이지 축약안을 추천했다. 기존 PPT 수정 요청으로 해석하지 않았다.
- 27페이지 우선안: 요약표와 마지막 표의 페이지 넘침, AI 전략·일정표·수행 방법·문제/타당성·활용방안의 반복 설명을 합쳐 약 6페이지 절감 목표. 26페이지는 SEO/GEO 조치 설명과 구현 도식 반복 해설 추가 축약.
- 핵심 실험 수치·조건·한계와 필수 멘토/심의 항목은 보존한다. 페이지 감소량은 예상이며 실제 편집과 멘토 의견 작성 후 재확인이 필요하다. 목차와 지표 집계기간/분모 정리도 필요하다.
- 현재 요청은 추천까지 완료. Google Docs·PPT 원본·코드·외부 계약·운영 설정·Jira·Git 이력은 변경하지 않았고 기존 dirty 문서를 보존했다.

## 학습 기록 독립 삭제 구현 계획 확정 (2026-09-21)

- 별도 Jira 없이 `docs/codex/LEARNING_RECORD_DELETION_IMPLEMENTATION_PLAN.md`를 신규 작성했다. 현재 상태는 계획 확정·runtime 미구현이며 production 활성화 승인이 아니다.
- 범위는 계정·로그인과 독립된 모의고사+10초 Challenge 전체 기록 삭제다. 앱은 이중 확인과 한 UUID `Idempotency-Key`를 terminal까지 재사용하고, Learning Core는 Request userId 없이 JWT `sub`를 사용한다. Identity recent-auth·Token·RefreshSession 계약은 바꾸지 않는다.
- 요청 즉시 이전 기록을 숨기고 새 학습 write를 차단한다. durable Mongo operation은 `REQUESTED → FENCED → INVENTORYING → WAITING_COORDINATION → S3/Mongo/Redis 정리 → VERIFYING → COMPLETED`로 수렴하며 물리 삭제 목표는 24시간이다. 반복 실패는 `NEEDS_REVIEW`로 격리하고 hide/block을 유지한다.
- 삭제 대상은 Exam Session/Result/Summary/채점 Job·provider 결과, Challenge Attempt/Job/receipt, 사용자 S3 음성과 시험 Redis projection이다. catalog와 Billing reservation/consumption·TrialClaim, ExamCreationOperation, AttemptGroup outbox, ownership/merge/withdrawal 및 최소 삭제 멱등성·Callback tombstone은 삭제하지 않는다.
- 기존 `user_ownership_guards`의 `ACTIVE|MERGED` 의미는 유지한다. 별도 deletion operation을 두되 삭제 request와 모든 user-owned writer가 같은 ownership guard를 touch해 시작 경합을 선형화하고, read fence는 cutoff 이전 기록을 숨긴다.
- deletion 자체를 AttemptGroup failure나 `RETAKE_AVAILABLE` 사유로 사용하지 않는다. OPEN/GRADING group의 기존 소비·상태 증거를 보존하고, late Exam/Challenge Callback과 worker는 target tombstone을 확인해 데이터를 재생성하지 않는 성공 no-op으로 수렴한다.
- 기존 Presigned PUT 최대 5분의 잔여 capability 때문에 만료+clock skew 뒤 S3 final sweep을 필수로 한다. 기존 S3/Redis key와 Python AI `user_id=examId`, 공개 API/DTO/`BaseResponse`는 변경하지 않는다.
- runtime 구현 전 전용 `AGENTS.md` 허용 범위, 신규 API/mobile fixture, 30일 제안 tombstone 보존기간과 backup/log 고지, Billing OPEN replacement tombstone contract, S3 prefix list/delete 권한, `NEEDS_REVIEW` runbook과 Jira를 확인해야 한다.
- 이번 작업은 결정 문서의 승인 상태, 신규 계획 문서와 상태/작업 기록만 변경했다. Gradle 테스트는 실행하지 않으며 `git diff --check`와 작업 marker 단일 포함만 검증한다. 코드·DB·S3·Redis·AWS·Identity·Billing·모바일 앱·Jira·배포·Git commit/push 변경과 Secret/Token 기록은 없다.

## 학습 기록 삭제 구현 계획 사용자 설명 (2026-09-21)

- 별도 Jira 없이 확정 계획을 사용자 관점의 `이중 확인 → 삭제 접수 → 즉시 숨김·학습 차단 → 백그라운드 물리 삭제 → 완료 후 새 학습 허용` 흐름으로 풀어 설명했다.
- 앱은 화면 확인·UUID 멱등 key 보존·진행 표시·완료 후 로컬 학습 cache 제거를 담당하고, Learning Core는 JWT `sub` 식별·중복 요청 수렴·Mongo/S3/Redis 정리·Callback/worker 경합 차단을 담당한다. Identity의 로그인·Token·회원 상태는 바꾸지 않는다.
- 즉시 일괄 삭제하지 않는 이유는 진행 중 Billing/AttemptGroup·AI Callback과 아직 유효한 Presigned PUT이 데이터를 다시 만들거나 사용권 증거를 손상시킬 수 있기 때문이다. 따라서 화면에서는 즉시 숨기되 실제 저장소는 durable worker가 순서대로 정리한다.
- `NEEDS_REVIEW`는 사용자 기록을 복원하는 실패가 아니라, 기록은 계속 숨기고 학습도 차단한 채 운영 복구가 필요한 안전 격리 상태다. 삭제 완료 뒤에만 새 학습을 허용한다.
- runtime 구현 전 핵심 확인은 Billing OPEN group 재연결 계약, tombstone·backup/log 보존기간, S3 prefix 삭제 권한, 모바일 API fixture와 운영 복구 절차다. 설명만 수행했으며 runtime·외부 계약·인프라·Jira는 변경하지 않았다.

## 학습 기록 삭제 계획 설명 종료 기록 (2026-09-21)

- 별도 Jira 없이 사용자가 이해할 수 있는 수준으로 삭제 요청부터 완료까지의 흐름과 앱·Learning Core·Identity 책임을 구분했다.
- 핵심은 요청 즉시 기록을 숨기고 신규 학습을 차단한 뒤, Billing/AI/Presigned URL 경합을 정리하면서 MongoDB·S3·Redis를 비동기로 삭제하는 것이다.
- 사용자 학습 콘텐츠는 제거하지만 credit·무료 기회 사용, Billing·보안·멱등성 최소 증거는 보존한다. 24시간 목표 내 수렴하지 못하면 `NEEDS_REVIEW`로 격리하며 hide/block을 유지한다.
- 현재는 설명·문서 기록 단계이며 runtime 구현, Jira 등록, 외부 계약·DB·AWS·배포 변경은 없다. Secret과 Token은 기록하지 않았다.

## 2026-09-16 ECS 비용 절감 적용 현황

- Container Insights OFF 유지. Identity는 사용자 승인으로 health check grace를 300초로 늘린 뒤 revision 21(0.5 vCPU/1 GiB) 재배포 성공. 14:42 KST 배포 성공, 14:43 steady state, 새 ALB 대상 Healthy 및 새 개정 1개 실행/이전 개정 0개 확인.
- Learning Core revision 20(0.5 vCPU/2 GiB)도 14:50 KST 배포 성공. 실제 실행 태스크 사양·정상 상태 및 ALB Healthy 확인. 기존 grace 180초/Service Connect 유지. AI·NAT 변경 없음. 신규 Jira 없음.
- 두 서비스 모두 rolling min 100/max 200, circuit breaker/rollback과 기존 이미지를 유지. 대상 그룹 상태 검사 기준·보안/라우팅·공개 API는 변경하지 않았다. Identity의 첫 실패가 유예 부족 때문이라고 독립적으로 확정한 것은 아니다.
- 현재 구성 기준 비용 전망 약 $217.61/30일($7.25/일). CI OFF만 반영한 $262.17 대비 추가 약 $44.56/30일 절감 예상. 실청구·장시간 부하·앱 로그인/시험/채점 E2E는 미검증이며 자동 확장/세금/환율/사용량에 따라 달라진다.
- 앱 코드 변경 없음. Gradle 미실행, 문서 diff 검사. 기존 dirty 변경 보존, commit/push 없음. 다음 확인 권장: 실제 앱 로그인과 시험/채점, 최대 메모리·CPU·응답 지연 및 후속 청구.
- 후속 확인: Identity 태스크 health UNKNOWN은 ECS 컨테이너 상태 검사 미설정(콘솔 구성되지 않음)에 따른 표시다. 실행 상태 RUNNING·배포 성공과 ALB 정상 1/비정상 0을 재확인했다. Container Insights OFF와 무관하며 이번 확인에서 AWS 설정은 변경하지 않았다. 신규 Jira 없음.
- 컨테이너 health check 추가는 권장하되 미적용. 배포 이미지 검사 도구 존재와 liveness 전용 endpoint/보안 설정 검증 후 새 task definition으로 rolling 배포해야 한다. 로컬에는 health 노출만 있으며 probes 명시 설정과 curl/wget 명시 설치는 없다. DB 의존 종합 health를 생존 검사로 그대로 사용하면 외부 장애 시 재기동을 유발할 수 있어 주의. 신규 Jira 없음, 이번 질문은 검토만 수행.
- 후속 실검증: 운영 `/actuator/health`는 비인증 200/UP, `/actuator/health/liveness`는 비인증 401. revision 21 환경과 배포 commit에는 probe 활성화가 없고 보안은 exact `/actuator/health`만 공개한다. 동일 Dockerfile 로컬 이미지의 app 사용자에서 curl·wget 존재를 확인했으나 정확한 ECR bytes 직접 실행은 로컬 AWS CLI credential 부재로 미검증. AWS·코드·배포 변경 없음, 신규 Jira 없음.
- 기존 revision 20 JSON에도 컨테이너 `healthCheck` 필드가 없어 ECS health UNKNOWN은 이번 축소 이전부터 동일한 표시였다. 현재 revision 21 RUNNING·배포 성공·ALB 정상 1/비정상 0·공개 health 200/UP으로 즉시 운영 문제 신호는 없다. ALB unhealthy 기반 보호는 유지되며 전용 컨테이너 liveness는 후속 개선 대상이다. 신규 Jira·AWS 변경 없음.
- 프론트/ALB 설명 확인: 현재 계약은 Identity와 Learning Core를 서로 다른 base URL로 호출한다. 도메인은 ALB의 서비스 routing key이고 ALB가 실제 target group·task IP를 선택하므로 프론트가 개별 서버 주소를 아는 구조는 아니다. 단일 base URL 전환은 별도 ALB path routing·DNS/certificate·API path/프론트 계약 설계가 필요하다. 신규 Jira·설정 변경 없음.

## 학습 기록 삭제와 AI 품질 개선 데이터 경계 검토 (2026-09-21)

- 별도 Jira 없이 학습 기록 삭제가 음성·이력 기반 채점 품질 개선 사이클에 미치는 영향을 검토했다. 현재 저장소에는 별도 모델 학습·평가 dataset/export pipeline 구현이 확인되지 않으며, 기존 삭제 계획은 Learning Core의 운영 MongoDB·S3·Redis 원본만 범위로 한다.
- 현재 계획대로 삭제하면 해당 사용자의 원본 음성, transcript, 상세 결과·피드백은 이후 개인화나 모델 개선 표본으로 사용할 수 없다. 이것은 “모든 학습 기록 삭제”의 정상적인 의미이며, userId만 제거한 음성이나 transcript를 익명 데이터라고 보고 계속 보관해서는 안 된다.
- 권장 경계는 기본적으로 원본·개인 단위 파생 데이터를 삭제하고, 개인에게 재연결할 수 없는 최소 cohort 집계 품질 지표만 유지하는 것이다. raw 음성·답안이 필요한 human review/model training은 별도 명시적 opt-in, 분리 저장소·retention·lineage와 삭제/동의 철회 전파 계약이 있어야 한다.
- AI·분석 저장소에 원본 또는 재식별 가능한 복사본이 생기면 Learning Core 삭제만으로 완료 처리할 수 없다. 전체 data inventory와 deletion manifest/ack 계약이 필요하다. 이미 학습된 모델에 대한 source deletion과 모델 영향 제거는 동일하지 않으므로 retrain/unlearning/version 정책도 별도 결정해야 한다.
- 품질 개선 사이클은 동의 사용자의 curated sample·golden dataset·synthetic fixture와 비식별 집계 지표로 유지할 수 있다. 해당 경계를 기존 삭제 계획에 반영할지는 사용자 확정이 필요하며, 이번 검토에서는 계획서·runtime·외부 시스템을 변경하지 않았다.

## 학습 기록 삭제 후 누적 응시 횟수 보존 경계 (2026-09-21)

- 별도 Jira 없이 삭제 뒤 누적 모의고사 응시 횟수 보존 가능성을 검토했다. 서비스 전체·일/주/월 단위의 비식별 집계 count는 userId·examId·원본 event와 재연결할 수 없게 생성하면 품질·제품 지표로 유지할 수 있다.
- 반면 `특정 userId의 총 응시 7회`처럼 사용자별 정확한 누적값은 개인 학습 이력이므로 현재 “모든 학습 기록 삭제”와 양립하지 않는다. 이를 유지하거나 삭제 후 다시 사용자에게 보여주려면 기능명을 “상세 학습 기록 삭제”로 바꾸고 보존 목적·기간·노출을 별도 확정해야 한다.
- Billing consumption·TrialClaim 등 중복 지급 방지용 거래 증거는 기존 계획대로 보존할 수 있지만, 이를 학습 개인화나 일반 분석용 사용자별 횟수로 재사용하지 않는다. 목적 제한과 접근 경계를 유지한다.
- 권장안은 삭제 전 또는 정상 event 처리 시 `date/metric/examType/count` 같은 저카디널리티 집계만 별도 저장하고, userId·examId·정확한 시각·희소 조합과 사용자별 counter는 삭제 범위에 포함하는 것이다. lifetime unique user처럼 영구 식별이 필요한 지표는 별도 privacy 설계가 필요하다.
- 이번 검토에서는 계획서·runtime·데이터 저장소·외부 시스템을 변경하지 않았으며, 전체 집계와 사용자별 누적 중 어느 제품 요구인지 최종 확정이 남아 있다.

## 비식별 누적 응시·품질 집계 유지 확정 (2026-09-21)

- 별도 Jira 없이 사용자가 서비스 전체·기간별 비식별 통계를 유지하고 사용자별 누적 학습 counter는 삭제하는 권장안을 승인했다. 결정 문서와 구현 계획에 이를 반영했다.
- 유지 대상은 `exam_started_total`, `exam_completed_total`, 최종 채점 실패·grading retry, Challenge submit/completion처럼 durable 상태 전이에 의해 정확히 한 번 증가하는 저카디널리티 aggregate다. 일 단위 이상 bucket과 승인된 고정 dimension만 사용한다.
- userId·examId·attemptId, 정확한 event 시각, 원점수·자유문장·오디오 특성·희소 dimension 및 사용자별 누적 응시 횟수·평균 점수·최근 응시일은 보존 집계에 포함하지 않고 삭제한다. 삭제된 사용자의 과거 기여분은 개인과 재연결할 수 없는 aggregate에서 차감하지 않는다.
- replay 요청은 중복 집계하지 않는다. 임시 contribution/outbox를 쓸 경우 식별 가능한 staging data로 취급해 짧은 retention과 삭제 fence를 적용한다. exact lifetime unique user는 별도 privacy 설계 없이는 추가하지 않는다.
- Billing consumption·TrialClaim은 거래·중복 지급 방지 목적으로만 보존하고 분석·개인화 counter로 재사용하지 않는다. runtime 구현·DB·AI·AWS·배포·Jira는 변경하지 않았다.

## 비식별 누적 집계 계획서 반영 종료 기록 (2026-09-21)

- 별도 Jira 없이 서비스 전체·기간별 비식별 응시·채점 품질 aggregate 유지 결정을 삭제 결정서와 구현 계획서에 반영 완료했다.
- durable 상태 전이당 정확히 한 번 집계하고 replay 중복을 막는다. 사용자·시험 식별자, 정확한 시각, 원점수·자유문장·음성 특성·희소 dimension과 사용자별 lifetime counter는 삭제 대상으로 유지한다.
- 삭제된 사용자의 과거 기여분은 개인에게 재연결할 수 없는 최종 aggregate에서 차감하지 않는다. Billing 증거는 거래 목적으로만 제한하며 exact lifetime unique user는 별도 privacy 설계 전까지 제외한다.
- 문서 반영만 완료했으며 runtime·DB·AI·AWS·Jira·배포 변경은 없다. Secret과 Token을 기록하지 않았다.

## 학습 기록 삭제 잔여 6개 운영 결정 선택지 검토 (2026-09-21)

- 별도 Jira 없이 Billing OPEN 승계, 보존기간, S3 IAM, 모바일 문구, `NEEDS_REVIEW` runbook과 Jira 구성의 선택지·장단점을 정리했다.
- Billing OPEN은 hidden ExamSession 유지, 최소 coordination tombstone, 신규 Billing deletion 계약 중 tombstone을 권장한다. 같은 attemptGroup/consumption으로 replacement하여 추가 차감·복원을 만들지 않되 실제 Billing fixture 검증이 필요하다.
- 보존은 완료 command/Callback tombstone·일반 로그·암호화 backup 30일, Billing coordination은 OPEN/non-terminal 동안 무TTL 후 terminal+30일, 보안 감사 90일의 균형안을 제안한다. Billing ledger는 별도 법정·거래 정책을 따른다.
- S3는 bucket 전체 권한이 아니라 `temp/*`에 한정한 List/Delete를 권장한다. versioning 활성 시 현재 객체 삭제만으로 과거 version이 제거되지 않으므로 ListBucketVersions/DeleteObjectVersion 또는 검증된 lifecycle가 추가로 필요하다.
- 모바일은 `processing|completed|needs_review`만 노출하고 내부 stage·식별자는 숨긴다. 운영은 `NEEDS_REVIEW`에서도 hide/write block을 유지한 채 같은 operation을 복구하며 강제 완료·fence 해제를 금지한다.
- Jira는 상위 이슈와 backend core, Billing coordination, storage/IAM, Challenge/AI, aggregate/privacy, mobile, test/runbook 하위 이슈 구성을 권장한다. 현재 Jira 생성·runtime·AWS·모바일 변경은 수행하지 않았다.

## 학습 기록 삭제 잔여 운영 선택지 설명 종료 기록 (2026-09-21)

- 별도 Jira 없이 6개 잔여 운영 결정에 대해 선택지·장단점·권장 조합을 사용자에게 전달했다.
- 최종 권장 조합은 Billing OPEN coordination tombstone, 30일 중심 retention과 보안 감사 90일, S3 `temp/*` 제한 IAM, 모바일 3상태, fail-closed `NEEDS_REVIEW` 복구, 상위 Jira+하위 이슈 구조다.
- 실제 Billing contract, S3 versioning/lifecycle, 개인정보 보존정책과 모바일 fixture 확인 전에는 운영값 확정이나 production 활성화를 완료한 것으로 보지 않는다.
- 설명·기록만 수행했으며 Jira 생성, runtime·DB·AI·AWS·모바일·배포 변경은 없다. Secret과 Token을 기록하지 않았다.

## 삭제 완료 후 ExamSession 비보존 확정 (2026-09-21)

- 별도 Jira 없이 삭제 완료 뒤 원본 Learning Core `ExamSession`을 hidden 상태로 남기지 않는 경계를 확정하고 결정서·구현 계획서에 반영했다. Identity 로그인 Session·Access/Refresh Token은 이번 기능과 무관하며 유지한다.
- 진행 중 Billing/AttemptGroup coordination 동안에는 read fence로 원본 Session을 일시 숨길 수 있지만, `COMPLETED` 전에 allowlist 최소 필드의 별도 coordination tombstone을 원자 저장하고 원본 Session을 삭제한다.
- tombstone은 동일 OPEN attemptGroup/consumption replacement, 늦은 Callback 차단과 거래 정합성에만 사용한다. history/result source, 일반 분석·개인화 또는 ExamSession 재생성 source로 사용하지 않는다.
- deletion 대상 원본 ExamSession이 한 건이라도 남으면 `COMPLETED`로 전환하지 않는다. OPEN/non-terminal tombstone은 무TTL, terminal 뒤 승인된 기간 후 제거한다.
- 문서만 변경했으며 runtime·DB·Identity·Billing·AWS·Jira·배포 변경은 없다. Secret과 Token을 기록하지 않았다.

## NEEDS_REVIEW 의미 설명 (2026-09-21)

- 별도 Jira 없이 학습 기록 삭제 operation의 내부 `NEEDS_REVIEW`를 자동 재시도를 계속하거나 성공으로 오판하면 위험한 경우의 운영 격리 상태로 설명했다.
- 예시는 S3/IAM 반복 실패, Mongo 예상 삭제 건수 불일치, Billing OPEN coordination 미확정, unknown commit 증거 불충분, versioned S3 객체 잔존과 24시간 목표·재시도 예산 초과다.
- 이 상태에서도 학습 기록은 계속 숨기고 새 학습 write를 차단하며 계정·로그인 상태는 유지한다. 운영자는 같은 operation의 원인을 해결하고 검증한 뒤 재개하며 강제 완료·새 operation 생성·기록 재노출을 하지 않는다.
- 앱에는 내부 enum 대신 “일부 데이터 정리가 지연되고 있으며 기록은 숨겨져 있고 새 학습은 잠시 제한된다”는 고정 문구를 표시한다. 설명 외 runtime·Jira·외부 시스템 변경은 없다.

## NEEDS_REVIEW 설명 종료 기록 (2026-09-21)

- 별도 Jira 없이 `NEEDS_REVIEW`를 삭제 실패 확정이나 사용자 오류가 아니라, 자동 처리를 중단하고 같은 operation의 운영 복구가 필요한 fail-closed 상태로 최종 설명했다.
- 상태 중에는 기록 hide·신규 학습 block을 유지하고 계정·로그인은 보존한다. 원인 해결과 Mongo/S3/Redis·Billing 증거 재검증 후에만 `COMPLETED`로 전환한다.
- 사용자에게 내부 상태명·실패 원문을 노출하지 않고 정리 지연과 일시적 학습 제한 안내만 제공한다. runtime·Jira·외부 시스템은 변경하지 않았다.

## 삭제 정리 지연과 신규 학습 차단 분리 검토 (2026-09-21)

- 별도 Jira 없이 `NEEDS_REVIEW`의 모든 경우에 신규 학습을 막는 기존 계획이 사용자 경험상 과도함을 검토했다. 삭제 status와 새 학습 가능 여부를 분리하는 방향을 권장한다.
- cutoff와 target inventory가 sealed되고 read hide, late Callback fence, Billing OPEN tombstone이 확정되어 새 기록 오삭제·추가 차감 위험이 없으면 S3/Redis/old orphan/backup 정리 지연 중에도 새 학습을 허용할 수 있다.
- target inventory 불명확, deletion/ownership fence 실패, Billing group 미확정, Session↔tombstone unknown commit처럼 새 데이터 손상·중복 차감 위험이 있는 경우만 blocking `NEEDS_REVIEW`를 유지한다.
- 권장 모델은 non-blocking `CLEANUP_DELAYED`와 blocking `NEEDS_REVIEW`를 구분하거나, 공개 삭제 status와 `canStartLearning`을 별도 field로 제공하는 것이다. 완료로 거짓 처리하지 않으면서 새 학습 UX를 살릴 수 있다.
- 이 turn은 설계 판단만 기록했으며 기존 삭제 계획서·공개 계약·runtime·Jira·외부 시스템은 아직 변경하지 않았다.

## 위험도 기반 신규 학습 허용 설명 종료 기록 (2026-09-21)

- 별도 Jira 없이 삭제 대상 inventory와 Billing/Callback fence가 확정된 뒤에는 물리 정리 지연 중에도 새 학습을 허용하는 권장 흐름을 최종 설명했다.
- S3/Redis/old orphan/backup 정리 지연은 non-blocking `CLEANUP_DELAYED`, inventory·ownership·Billing·unknown commit 불확실성은 blocking `NEEDS_REVIEW`로 구분한다.
- API는 삭제 status와 `canStartLearning`을 분리하고, 새 학습 허용 뒤 deletion worker는 최초에 sealed한 examId/attemptId만 처리하며 userId 전체 삭제를 금지한다.
- 현재는 설계 검토 단계로 계획서·runtime·Jira·외부 시스템을 변경하지 않았다. Secret과 Token을 기록하지 않았다.

## Latest MongoDB Azure result archive assessment (2026-09-24)

- 별도 Jira 이슈 없이 원격 MongoDB의 문서 내용을 열람하지 않고 collection stats만 조회했다. DB 전체 논리 dataSize 약 237.3MB 중 `azure_results`가 4,751건·약 196.2MB로 약 82.7%를 차지해 용량 정리 효과가 크다.
- `azure_results`는 문항 상세 API의 `question.azureFeedback`에 사용된다. 삭제 시 외부 DTO 형태를 바꾸지 않더라도 기존 시험의 필드 값이 null/미포함으로 변경된다.
- Callback이 raw payload 저장을 계속하므로 1회성 삭제만으로는 재증가를 막지 못한다. 안전한 수행은 정확한 대상 DB·저장소 밖 archive 경로·향후 저장 중단 여부를 승인한 뒤, `mongodump --archive --gzip` 보존→checksum/restore dry-run/count 검증→삭제 최종 승인→drop→stats/API 검증 순으로 진행하는 것이다.
- 현재 `mongodump` 도구가 없어 archive를 생성하지 않았고, DB 삭제·drop·runtime 코드 변경을 수행하지 않았다. raw payload는 발화 데이터를 포함할 수 있으므로 archive는 Git 밖에 보관하고 Secret·Token·URI·payload를 문서와 로그에 남기지 않는다.

## Latest Azure frontend response confirmation (2026-09-24)

- 별도 Jira 이슈 없이 현재 앱 API 구현을 확인했다. 문항 상세 조회는 회차별 `AzureResult.raw_data.azure_speech_result`를 `result.question.azureFeedback`으로 반환한다.
- `azureFeedback`은 `PartResultDTO` 공개 필드이며 spoken word sequence, repeated word events, error counts, legend를 담는다. DTO가 null 필드를 생략하므로 Azure 데이터 삭제 후에는 기존 시험에서 해당 JSON 필드가 미포함될 수 있다.
- 현재 프론트 코드가 실제로 필드를 소비하는지는 앱 프론트 저장소 확인 대상이며, 이 turn에서는 backend·DB·외부 시스템을 변경하지 않았다.
