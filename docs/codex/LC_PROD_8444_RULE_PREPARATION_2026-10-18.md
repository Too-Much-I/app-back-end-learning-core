# LC 운영8444 전달·차단 규칙 준비

## 후속 적용:79 차단만 완료 (2026-10-18 12:14 KST)

- 사용자의79번 차단만 적용 승인에 따라 실제 listener를 재조회했다.78/79 비어 있음 및 기존70/80 확인 후79만 create-rule했다. 아래 준비 시점의 미적용 설명은 당시 이력이다. JSON은 여전히 전체 변경안이며 재실행하면 안 된다.
- 생성 ARN: `arn:aws:elasticloadbalancing:ap-northeast-2:889384901776:listener-rule/app/tosunsaeng-staging-alb/455925e3ff93252c/fd9dacab3823ed54/6d56affd5853585c`.
- exact api-prod host+환불 path, method/source 제한 없는 body 없는404. readback 확인 및 기존 모든 rule 객체의 변경 전후 동등성 검사 통과.78 전달은 미생성.
- CloudShell에서 공개 HTTPS 해당 경로 GET/빈 POST 각각404·본문0bytes 확인. 토큰/업무 event를 보내지 않았다. 이는 차단 확인이며8444/JWT·업무 검증이 아니다.
- LC taskDefinition/loadBalancers/networkConfiguration 불변, prod8 running1/pending0. 서비스 업데이트·기능ON·DB/봉인·IAM/SG·기존 규칙 수정 없음.
- 증빙 `/private/tmp/lc-prod-rule79-block-20261018.png`.78은 수신 runtime·봉인/배포 등 준비와 별도 적용 승인 이후 진행한다.

## 1. 5줄 결론

1. TMI-116/TMI-217/TMI-220. 2026-10-18 12:09 KST 전용 CloudShell에서 AWS describe 읽기 대조 완료.
2. LC prod8 running1/pending0/COMPLETED,8080만 연결. Billing prod5 및 양측 공유 NAT13.124.57.130을 독립 확인했다.
3. 운영8444 TG는 대상0·ALB 미연결. ALB SG→LC8444 제한 허용은 이미 존재한다.
4.443 listener의 빈 priority78/79에 exact 전달/동일 경로404 차단을 제안한다. 기존70/80·test/staging/Identity/Billing 규칙은 보존한다.
5. JSON 요청안을 준비했지만 AWS 규칙 적용·서비스 연결·배포·기능ON·DB/봉인은 수행하지 않았다.

## 2. 반드시 읽을 내용

이 문서는 읽기 대조 시점의 변경안이다. 적용 직전 revision/전체 rule·78/79 점유·NAT를 다시 확인해야 한다. TG health는 HTTP8080 /actuator/health여서 healthy만으로8444 TLS 정상 여부를 증명하지 못한다. receiver OFF에서는8444 connector가 없으므로 현재 빈 TG에 실트래픽을 전달하면 오류가 날 수 있다.

## 3. 정확한 준비안

- listener: `arn:aws:elasticloadbalancing:ap-northeast-2:889384901776:listener/app/tosunsaeng-staging-alb/455925e3ff93252c/fd9dacab3823ed54` (HTTPS443).
-78: host `api-prod.to-teacher.com` AND method POST AND path `/internal/v1/entitlements/access/events` AND source-ip `13.124.57.130/32` → HTTPS8444 TG `arn:aws:elasticloadbalancing:ap-northeast-2:889384901776:targetgroup/lc-prod-access-events-8444/1041faca66350508`.
-79: 같은 host AND 같은 exact path, method/source 제한 없음 → body 없는404 fixed-response. 허용 조건 불일치가 기존80번8080으로 흘러가지 않도록 한다.
- 두 요청은 [JSON 준비본](LC_PROD_8444_RULE_REQUESTS_2026-10-18.json)에 있다. 자동 실행 도구가 아니며 별도 승인 전 create-rule에 전달하지 않는다.
- 기존70번 `/v1/challenges/evaluations`는 AI prod TG,80번 api-prod 나머지는 LC8080 TG다. 두 규칙 변경/재정렬 없음.78보다 앞선 다른 규칙은 다른 host 또는 별도 evaluation path로 이번 exact 요청을 가로채지 않는다.
- 공유 NAT의 다른 서비스도 IP 조건을 만족한다. 전용 JWT issuer/sub/aud/key·scope 검증으로 신원을 제한한다. X-Forwarded-For 헤더를 source-ip 대신 신뢰하지 않는다.
- ALB fixed-response의 no-store 헤더는 이번 요청안에 포함할 수 없으므로 앱의401/403/404 no-store 계약과 동일하다고 주장하지 않는다. 캐시/CDN 유무와 경계 응답 정책은 적용 전 검토한다. 전체 listener에 영향 주는 전역 헤더 변경을 임의 추가하지 않는다.

## 4. 관측 근거

| 대상 | 확인 결과 |
| --- | --- |
| LC 서비스 | tosunsaeng-learning-core-prod-service / prod8, PRIMARY COMPLETED,1/0 |
| 실제 컨테이너 이름/포트 | tosunsaeng-learning-core-test /8080 TCP (이름의 test와 서비스 prod 구분) |
|8444 TG | HTTPS/IP8444, LoadBalancerArns=[], targets=[] |
| LC SG | sg-0372f877eda5ef96f, sgr-0c61f041e2cd196f8: ALB sg-0e5462fa65a22ded8에서 TCP8444 |
| 양측 subnet | subnet-0fa638ca8effafa81, subnet-0dc3e0343bb98e3e3, public IP DISABLED |
| route tables | rtb-01781bcd3ebc4cc7c / rtb-036c1c56bacc666ef, 명시 subnet 연결·active 기본 경로 |
| NAT | nat-0a11238673df5ef5d available, public IP13.124.57.130 |
| Billing | tosunsaeng-billing-prod:5, 위 동일 subnet (업무 활성화/health는 이번 점검 대상 아님) |
|443 점유 priority |10,20,30,38,39,40,50,51,60,70,80,87–96,99–104,110–114 및 default |

키/Secret값·DB/개별 시험은 조회하지 않았다. DNS/공개 인증서의 이번 독립 재검증은 하지 않았으며 기존 Billing 인계와 구분한다. 변경 중인 AWS의 원자적 snapshot이 아닌 개별 describe 결과다.

## 5. 적용 전 조건·순서·복구

1. [전환 실행안](LC_PRODUCTION_CUTOVER_RUNBOOK_2026-10-18.md)의 writer 배출/봉인·검증된 이미지·owner/index·전체 runtime gate를 충족한다. 이번 규칙 준비가 그 승인을 대신하지 않는다.
2. 최신 서비스 설정을 보존해 task8444 TCP mapping 및 기존8080과 추가8444 TG 연결의 exact 변경안을 대조한다. 기존 SG 규칙은 재생성/확대하지 않는다. 승인된 수신ON 및 배포로 실제 connector가 열려야 한다.
3. listener 전체 rule과78/79가 그대로 비어 있는지 확인한다. 우선79 차단을 설치·검증하고78 전달을 설치한다. 중간 실패 시 내부 경로는 닫힌 상태로 유지한다. 절차는 실제 적용 승인 후 실행한다.
4. 정상 source/POST/path는 JWT 검증에 도달, 외부 source·GET은404, query/잘못된 Bearer는 receiver 계약대로 거절되는지 확인한다. trailing slash/encoded path·다른host·직접8080 우회도 별도 검증한다. ALB exact matcher만으로 모든 정규화 변형 차단을 보장하지 않는다.
5. 기존 앱/평가/test 규칙 보존을 readback으로 검증한다. 정상서명·중복·업무 처리는 지정된 계정/fixture의 별도 승인 범위다.
6. 문제 발생 시 신규78 전달 중단을 우선하고79 차단을 유지하는 fail-closed 복구를 검토한다. 기존70/80은 수정하지 않는다. 새 데이터가 생겼다면 구 이미지 자동 rollback은 금지한다. 새 규칙 삭제도 실제 ARN 대조와 승인 후 수행한다.

## 6. 검증 범위

AWS 읽기 대조와 JSON 정적 검사만 완료. 서버 HTTP/TLS/공동E2E 미실행. 실제 설정·권한·배포·봉인·Git commit/push 변경 없음.
