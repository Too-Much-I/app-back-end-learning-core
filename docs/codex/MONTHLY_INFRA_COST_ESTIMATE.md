# 앱 백엔드 월 인프라 비용 추정

- 산정일: 2026-08-28
- 리전: AWS Asia Pacific (Seoul), `ap-northeast-2`
- 범위: production 24시간 상시 운영 + staging 테스트 시에만 실행
- 환율 가정: `1 USD = 1,400 KRW`
- 원화 VAT 포함 표시는 보수적으로 10%를 더한 값

이 문서는 청구서가 아니라 아직 확정되지 않은 리소스 크기를 가정한 예산안이다. 실제 AWS Cost Explorer, ECS Task Definition, Atlas/Redis plan과 월 트래픽을 확보하면 다시 계산해야 한다.

## 1. 공식 단가 근거

| 항목 | 2026-08-28 확인 단가 | 출처 |
|---|---:|---|
| Fargate Linux/x86 vCPU | $0.04656 / vCPU-hour | [AWS Fargate Pricing](https://aws.amazon.com/fargate/pricing/) 서울 선택 |
| Fargate Linux/x86 memory | $0.00511 / GB-hour | [AWS Fargate Pricing](https://aws.amazon.com/fargate/pricing/) 서울 선택 |
| ALB base | $0.0225 / hour | [Elastic Load Balancing Pricing](https://aws.amazon.com/elasticloadbalancing/pricing/) 서울 선택 |
| ALB LCU | $0.008 / LCU-hour | [Elastic Load Balancing Pricing](https://aws.amazon.com/elasticloadbalancing/pricing/) 서울 선택 |
| Public IPv4 | $0.005 / address-hour | [Amazon VPC Pricing](https://aws.amazon.com/vpc/pricing/) |
| NAT Gateway | $0.059 / gateway-hour | [Amazon VPC Pricing](https://aws.amazon.com/vpc/pricing/) 서울 선택 |
| NAT data processing | $0.059 / GB | [Amazon VPC Pricing](https://aws.amazon.com/vpc/pricing/) 서울 선택 |
| MongoDB Atlas Flex | $8~$30 / month | [MongoDB Pricing](https://www.mongodb.com/pricing) |
| MongoDB Atlas Dedicated M10 | starts at $56.94 / month | [MongoDB Pricing](https://www.mongodb.com/pricing) |
| ElastiCache Serverless for Valkey | starts at $6 / month | [Amazon ElastiCache Pricing](https://aws.amazon.com/elasticache/pricing/) |

Fargate와 ALB 계산에는 월 `730시간`을 사용한다. ECS cluster 자체에는 별도 cluster 요금이 없고 실제 Task compute와 주변 리소스에 요금이 붙는다.

## 2. 출시 초기 산정 가정

각 환경에 아래 서비스 Task 한 개씩을 24시간 실행한다고 가정한다.

| 서비스 | 환경당 크기 | 근거 상태 |
|---|---:|---|
| Identity | 0.5 vCPU / 1GB | 출시 초기 가정값, Task Definition 확인 필요 |
| Learning Core | 0.5 vCPU / 1GB | 출시 초기 가정값, Task Definition 확인 필요 |
| Billing/Entitlement | 0.25 vCPU / 0.5GB | 신규 서비스 최소 가정값 |
| Python AI API + worker 4개 | 2 vCPU / 4GB | 확인된 `tosunsaeng-ai` Task Definition 값 |

추가 가정:

- 환경별 ALB 한 개, 2개 AZ의 public IPv4 두 개
- ALB 평균 0.25 LCU/환경의 낮은 초기 트래픽
- 환경별 NAT Gateway 한 개, 두 환경 합계 월 50GB 처리
- production Atlas M10, staging Atlas Flex 최저 구간
- 환경별 ElastiCache Serverless for Valkey 최저 비용
- S3, ECR, CloudWatch Logs, Route 53, Secrets Manager와 소량 egress를 합쳐 월 $30 예비비

## 3. 기준 시나리오: 두 환경 모두 24시간

### 3.1 Fargate

공식:

```text
월 Task 비용 = (vCPU × $0.04656 + memory GB × $0.00511) × 730시간
```

| 서비스 | Task 1개/월 | 환경 수 | 합계 |
|---|---:|---:|---:|
| Identity 0.5/1GB | $20.72 | 2 | $41.45 |
| Learning Core 0.5/1GB | $20.72 | 2 | $41.45 |
| Billing 0.25/0.5GB | $10.36 | 2 | $20.72 |
| AI 2/4GB | $82.90 | 2 | $165.80 |
| **Fargate 합계** |  |  | **$269.42** |

### 3.2 네트워크·데이터 계층

| 항목 | 계산 | 월 비용 |
|---|---|---:|
| ALB 2개 | base $16.43 + IPv4 2개 $7.30 + 평균 0.25 LCU $1.46, 환경당 | $50.37 |
| NAT Gateway 2개 | `$0.059 × 730 × 2` | $86.14 |
| NAT 처리 50GB | `50 × $0.059` | $2.95 |
| Atlas | production M10 $56.94 + staging Flex $8 | $64.94 |
| Valkey | 환경별 최소 $6 | $12.00 |
| S3/ECR/Logs/DNS/Secrets/egress 예비비 | 실제 사용량 미확정 | $30.00 |

### 3.3 기준 합계

```text
Fargate              $269.42
ALB + public IPv4     $50.37
NAT                   $89.09
MongoDB Atlas         $64.94
Valkey                $12.00
기타 사용량 예비비    $30.00
--------------------------------
합계                 $515.82 / month
```

- 원화, VAT 전: 약 `722,000원`
- 원화, VAT 10% 포함 예산: 약 `794,000원`
- 실무 예산은 환율·로그·트래픽 오차를 포함해 **월 80만~90만원**으로 잡는 것이 안전하다.

## 4. 확정 운영안: production 24시간, staging 테스트 시에만

staging 월 사용시간을 우선 `40시간`으로 가정한다. 네 서비스의 staging Fargate 비용은 환경 합계 시간당 `$0.184535`이므로 약 `$7.38/month`다.

staging을 껐다는 의미는 리소스별로 다르다.

- ECS Service `desiredCount=0`: Fargate compute 요금은 멈춘다.
- ALB: 그대로 두면 시간당·IPv4 요금이 계속 발생한다.
- NAT Gateway: 그대로 두면 데이터가 없어도 시간당 `$0.059`가 계속 발생한다.
- Atlas Flex·Valkey: cluster/cache를 유지하면 각 서비스의 최소 월 비용이 남는다.
- ALB/NAT/DB/cache를 삭제하면 비용은 줄지만 매 테스트 전 IaC 재생성과 데이터 초기화가 필요하다.

### 4.1 staging 네트워크까지 테스트 때만 생성 — 최저 비용안

| 항목 | 월 비용 |
|---|---:|
| production Fargate 24시간 | $134.71 |
| staging Fargate 40시간 | $7.38 |
| production ALB + staging ALB 40시간 | 약 $26.57 |
| production NAT + staging NAT 40시간 + 소량 처리 | 약 $47.20 |
| Atlas production M10 + staging Flex 유지 | $64.94 |
| Valkey 두 환경 유지 | $12.00 |
| 기타 사용량 예비비 | $25.00 |
| **합계** | **약 $317.80** |

- 원화, VAT 전: 약 `445,000원`
- 원화, VAT 10% 포함: 약 `489,000원`
- 안전 예산: **월 50만~55만원**

이 안은 staging ALB와 NAT Gateway를 테스트할 때만 IaC로 만들고 종료 후 제거한다는 전제다. 수동 생성·삭제는 설정 drift와 삭제 누락 위험이 있어 권장하지 않는다.

### 4.2 staging Task만 끄고 ALB·NAT 유지 — 운영 편의안

staging Fargate만 월 40시간 실행하고 ALB·NAT·Atlas·Valkey를 계속 유지하면 약 `$383.49/month`, 환율과 VAT 가정상 **약 59만원**이다. 테스트를 자주 한다면 월 10만원가량을 더 내고 즉시 사용할 수 있는 이 방식이 단순하다.

따라서 현재 제품 단계의 현실적인 월 서버 예산은 **50만~60만원**이다. staging이 월 40시간보다 40시간 더 늘어날 때 네 서비스 Fargate 증가는 약 `$7.38`, VAT 포함 약 1.1만원으로 고정 네트워크 비용보다 영향이 작다.

## 5. 시나리오별 범위

| 시나리오 | 구성 차이 | USD/월 | 1,400원/USD + VAT 10% |
|---|---|---:|---:|
| 확정안 최저 | prod 24시간, staging 40시간 및 ALB/NAT도 필요시에만 | 약 $318 | 약 49만원 |
| 확정안 편의 | prod 24시간, staging Task 40시간, staging ALB/NAT 유지 | 약 $383 | 약 59만원 |
| 두 환경 상시 비교 | staging/prod 모두 Task 1개 24시간, NAT 환경별 1개 | 약 $516 | 약 79만원 |
| production HA | production Task 2개씩, NAT 2AZ, Atlas M30, 운영 로그 여유 | 약 $1,068 | 약 164만원 |

비용 최적화안에서 ALB와 DB를 계속 유지하면 staging Task를 0으로 줄여도 이들의 고정비는 남는다. NAT를 없애려면 단순 삭제가 아니라 public task 또는 VPC endpoint/egress 설계를 함께 검토해야 한다.

production HA 안은 public service와 AI Task를 두 AZ에 두 세트 운영하고 MongoDB Atlas를 공식 가격의 M30 `$0.54/hour` 수준으로 올리는 보수적 예산이다. M10도 replica set이지만 shared CPU이므로 성능 중요도가 높아지면 별도 부하 시험으로 tier를 결정한다.

## 6. 포함하지 않은 비용

다음은 서버 고정비가 아니거나 사용량·계약을 알 수 없어 위 합계에서 제외했다.

- AI 모델/provider 호출료, Azure/SpeechAce 등 문항별 외부 채점 비용
- Apple/Google 인앱결제 수수료
- MongoDB Atlas backup, egress, autoscaling과 추가 storage
- S3 음성 장기 보관량과 인터넷 data transfer 급증
- CloudWatch 대량 로그·Sentry 유료 plan
- AWS Support plan, 도메인 신규 구매, 환전·해외결제 수수료
- 배포 중 rolling update로 잠시 겹쳐 실행되는 Fargate Task

특히 시험 1회당 AI 외부 호출료는 사용자 수에 비례하므로 인프라 고정비와 분리해 `시험 1회당 변동원가`로 별도 산정해야 한다.

## 7. 실제 청구액으로 보정하는 데 필요한 값

1. staging/prod의 ECS Service 목록, desired count와 각 Task `cpu`/`memory`
2. ALB 수, AZ 수, 월 LCU와 public IPv4 수
3. NAT Gateway 수와 월 processed bytes
4. Atlas cluster tier·cloud/region·backup/storage/egress
5. Redis/Valkey 공급자·plan과 월 command/data 규모
6. CloudWatch ingestion/retention, S3 storage/request/egress
7. 월 시험 수, 시험당 음성 평균 크기와 AI provider 요청 단가

위 값을 AWS Cost Explorer와 각 공급자 청구서에서 확인하면 추정 오차를 크게 줄일 수 있다.

## 8. 2026-08-28 사용자 수정안 — AI 1 vCPU/2GB, Mongo 무료, 예비비 제외

사용자가 production AI Task를 `1 vCPU / 2GB`로 낮추고 현재 무료 MongoDB를 계속 사용하며 기타 예비비를 제외하기로 했다. 공식 Fargate 단가에서 AI Task 한 개의 24시간 월 비용은 다음과 같다.

```text
(1 vCPU × $0.04656 + 2GB × $0.00511) × 730시간
= $41.45/month
```

환경별 서비스 크기와 production 24시간 비용은 다음과 같다.

| 서비스 | 환경당 크기 | production 월 비용 |
|---|---:|---:|
| Identity | 0.5 vCPU / 1GB | $20.72 |
| Learning Core | 0.5 vCPU / 1GB | $20.72 |
| Billing | 0.25 vCPU / 0.5GB | $10.36 |
| AI API + Worker | 1 vCPU / 2GB | $41.45 |
| **production Fargate 합계** |  | **$93.26** |

staging도 같은 크기를 월 40시간 실행하면 Fargate 합계는 약 `$5.11`이다.

### staging ALB·NAT도 테스트 때만 생성

| 항목 | 월 비용 |
|---|---:|
| production Fargate | $93.26 |
| staging Fargate 40시간 | $5.11 |
| production ALB + staging ALB 40시간 | $26.57 |
| production NAT + staging NAT 40시간·소량 처리 | $47.20 |
| MongoDB | $0.00 |
| Valkey 두 환경 최소 가정 | $12.00 |
| 기타 예비비 | $0.00 |
| **합계** | **약 $184.14** |

- 환율 `1 USD=1,400 KRW`, VAT 전: 약 `258,000원`
- VAT 10% 포함: 약 `284,000원`
- 반올림 운영 예산: **월 29만~30만원**

### staging ECS만 끄고 ALB·NAT 유지

```text
production Fargate       $93.26
staging Fargate 40시간    $5.11
ALB 두 환경               $50.37
NAT 두 환경               $89.09
MongoDB                    $0.00
Valkey                    $12.00
--------------------------------
합계                     $249.83
```

- VAT 포함 원화: 약 `385,000원`
- 반올림 운영 예산: **월 39만~40만원**

따라서 수정안의 예상 범위는 staging 네트워크 제거 방식에 따라 **월 29만~40만원**이다. 이 계산은 사용자 요청대로 MongoDB backup/유료 tier와 기타 예비비를 0으로 두었으며 외부 AI provider 호출료는 여전히 포함하지 않는다.

현재 확인된 AI Task Definition은 `2 vCPU / 4GB`이고 API와 worker 4개가 같은 Task에 있다. `1 vCPU / 2GB`는 목표 크기이므로 적용 전에 staging에서 동시 채점 수, CPU throttling, peak RSS/OOM, queue backlog와 처리 p95를 검증해야 한다.

## 9. 2026-08-28 실제 크기·일 $1.26 관측 재정정

사용자가 현재 production에서 Identity `1 vCPU/3GB`, Learning Core `1 vCPU/3GB`, AI `2 vCPU/4GB`를 사용하며 표시된 비용은 `$12.6`이 아니라 약 `$1.26`이라고 재정정했다. 기존 표의 `$20.72`, `$20.72`, `$41.45`는 각각 이전 크기 `0.5 vCPU/1GB`, `0.5 vCPU/1GB`, `1 vCPU/2GB`의 월 비용이므로 새 크기와 맞지 않는다.

서울 Fargate 공식 단가로 Task가 각각 한 개라면:

| 서비스 | 실제 크기 | 시간당 | 일 24시간 | 월 730시간 |
|---|---:|---:|---:|---:|
| Identity | 1 vCPU / 3GB | $0.06189 | $1.485 | $45.18 |
| Learning Core | 1 vCPU / 3GB | $0.06189 | $1.485 | $45.18 |
| AI API + Worker | 2 vCPU / 4GB | $0.11356 | $2.725 | $82.90 |
| **합계, Task 각 1개** |  | **$0.23734** | **$5.696** | **$173.26** |

따라서 세 Task가 실제로 각각 한 개씩 24시간 실행된다면 `$1.26`은 하루 전체 Fargate 비용일 수 없다. 세 Task의 compute만 해도 약 `$5.696/day`이고, ALB·NAT·IPv4·로그 비용은 별도이기 때문이다. `$1.26`은 당일 현재까지의 부분 누적액, 일부 서비스/usage type만 선택한 값, Task의 간헐 실행, credit·discount가 반영된 net cost 중 하나일 가능성이 높다.

```text
Task 각 1개 24시간 compute     ≈ $5.696/day
$1.26 단순 30일 환산           = $37.80/month
환율 1,400원 단순 환산         ≈ 52,920원/month, VAT 전
```

단순 월 환산값은 `$1.26`이 완전한 24시간 비용으로 확인될 때만 유효하다. 현재 명시된 Task 크기·24시간 운영 조건과 모순되므로 고정비나 BEP 기준으로 바로 사용하면 안 된다.

확정 확인 순서:

1. 각 ECS Service의 `desired count`, `running count`, deployment별 Task 수 확인
2. Cost Explorer를 일 단위로 두고 `Service`, 이어서 `Usage type`으로 group
3. Fargate vCPU-hour·GB-hour, `NatGateway-Hours/Bytes`, ALB hours/LCU, public IPv4, CloudWatch Logs를 분리
4. AWS의 `$1.26`가 오늘 현재까지 누적액인지 전날 00:00~24:00 확정액인지 확인하고, amortized/net/unblended cost 및 credit·discount 포함 여부 확인

원인 분해 전에는 검증된 Fargate 크기 기준 `$173.26/month`를 compute 하한으로 유지하고, ALB·NAT·IPv4·로그 등을 추가해야 한다. `$1.26 × 30 = $37.80/month`는 비교용 단순 환산일 뿐 아직 운영 고정비로 확정하지 않는다.

## 10. 조직 계정 실제 가격을 기준으로 한 4서비스 축소 추정

사용자가 `$1.26/day`가 실제 조직 계정에 표시되는 비용과 정확히 일치한다고 확인했다. 향후 네 서비스를 Identity `1 vCPU/2GB`, Learning Core `1 vCPU/2GB`, Billing `1 vCPU/1GB`, AI `1 vCPU/2GB`로 조정한다고 가정한다.

서울 Fargate 정상 단가의 자원 비율은 다음과 같다.

| 구성 | 시간당 정상 단가 | 현재 대비 |
|---|---:|---:|
| 현재 3서비스: `1/3 + 1/3 + 2/4` | $0.23734 | 100% |
| 조정 후 4서비스: `1/2 + 1/2 + 1/1 + 1/2` | $0.22201 | 93.54% |

조직 계정의 할인·credit·정산 효과가 같은 비율로 계속 적용된다고 가정하면:

```text
조정 후 일 비용 = $1.26 × 0.22201 ÷ 0.23734 ≈ $1.179
30일 월 비용    ≈ $35.36
환율 1,400원    ≈ 49,500원, VAT 전
VAT 10% 포함    ≈ 54,500원
```

비교를 위해 조직 혜택이 사라진 정상 단가에서는 compute만 약 `$5.328/day`, 730시간 기준 `$162.07/month`, 환율 1,400원과 VAT 10% 적용 약 249,600원이다. ALB·NAT·public IPv4·CloudWatch·data transfer는 두 계산 모두 별도다.

AWS Organizations 가입 자체가 Fargate 요금을 자동으로 이 수준까지 할인하는 것은 아니다. 실제 차이는 payer 계정의 promotional credit, Savings Plans, private pricing, cost allocation 또는 조회한 cost type에서 생길 수 있다. 따라서 예산은 현재 혜택 유지 시 월 약 5.5만원, 혜택 소멸 대비 compute 안전선은 월 약 25만원으로 나누어 관리한다.

## 11. 조정 사양 기준 전체 서버비 재계산

사용자가 Fargate뿐 아니라 ALB·NAT·로그 등 다른 항목까지 포함한 **현재 AWS 전체 비용**을 `$1.26/day`가 아니라 `$5.49/day`로 재정정했다. 주변 비용을 다시 더하면 중복 계산이다. 또한 ALB·NAT 같은 고정비는 Task 크기를 낮춰도 줄지 않으므로 전체 `$5.49`에 compute 감소율 6.46%를 그대로 적용한 `$5.135/day`는 가능한 최저치다.

현재 전체 비용 중 compute 비중을 `C`라고 하면 조정 후 비용은 `$5.49 × (1 - 0.0646 × C)`다. Cost Explorer 분해 전에는 다음 범위로 본다.

| 시나리오 | 조정 후 일 비용 | 30일 | 1,400원·VAT 포함 |
|---|---:|---:|---:|
| 전체가 compute인 이론적 최저 | $5.135 | $154.06 | 약 237,300원 |
| compute 비중 70% 기준 | $5.242 | $157.25 | 약 242,200원 |
| 모두 고정비인 상한 | $5.490 | $164.70 | 약 253,600원 |

따라서 조정 후 **전체 production 서버비는 월 약 23.7만~25.4만원(VAT 포함)**으로 추정한다. 청구 지연·환율·staging 테스트 사용을 감안한 운영 예산은 **월 30만원**으로 수정한다.

| 월 예산 구성 | 산정 방식 | 금액 |
|---|---|---:|
| Production 기준 예상액 | compute 비중 70% 기준, 환율 1,400원·VAT 포함 | 약 242,200원 |
| Staging 테스트 여유 | production 예상액의 약 10% | 약 24,200원 |
| 환율·청구 지연·사용량 변동 완충액 | 잔여 반올림 buffer | 약 33,600원 |
| **월 운영 예산 합계** |  | **300,000원** |

최종 예산 관리표:

| 구분 | 월 예산 | 비고 |
|---|---:|---|
| Production AWS 전체 비용 | 242,200원 | 조정 사양, compute 비중 70%, 환율 1,400원·VAT 포함 기준 |
| Staging 테스트 비용 | 24,200원 | production 예상액의 약 10% |
| 변동 대응 예산 | 33,600원 | 환율, 청구 지연, 배포 중 Task 중복, 로그·전송량 변동 |
| **서버 고정비 예산** | **300,000원** | AWS 운영비 월 한도 |
| AI API 변동비 | 별도 | 완료 모의고사 1회당 250원 |

완충액은 별도 서비스의 확정 청구액이 아니라 환율 상승, Cost Explorer 반영 지연, 배포 중 Task 중복 실행과 소량 로그·전송량 변동을 흡수하기 위한 예산이다.

과거 `$1.26/day`와 월 7만원 예산은 소수점 입력 오류를 기반으로 했으므로 폐기한다. 최신 기준은 AWS 전체 `$5.49/day`, 조정 후 production 약 23.7만~25.4만원, 운영 예산 월 30만원이다.
