# TMI-217 deletion exact R2 — LC 계약 수락·구현 기준 동결

- 날짜: 2026-10-16 (Asia/Seoul)
- 수락 revision: `lc-billing-deletion-exact-r2-draft-3`
- 상태: LC 측 구현 전 기술 계약 수락·기준 동결. Billing 저장소의 접수 기록이나 공동 runtime 검증 완료를 대신하지 않는다.

## 1. 5줄 결론

1. retryNotBefore는 누락/invalid/더 이른 헤더로 단축하지 않는 draft-3 규칙을 수락한다.
2. correlationId는 UUID 강제 없이 ASCII `[A-Za-z0-9._-]` 전체 일치1~128자로 수락한다.
3. draft-3 > draft-2 > draft-1 순서로 최신 규칙을 적용하는 TMI-217 구현 전 기술 계약을 LC 측에서 동결한다.
4. 새 자료의 hash·산술·형식 검증을 통과했지만 서버 경계·경합31개와 전체scalar/JWT suite는 미검증이다.
5. 계약 수락은 fixture 전체 동결·제품 구현·DB/index 변경·배포/활성화 승인이 아니다.

## 2. 반드시 읽어야 하는 내용

기존 하한과 새 유효 candidate 중 최댓값을 보존하며 둘 다 없을 때만 null이다. 발신 due는 backoff와 이 하한 중 최댓값이다. I/O/timeout/lease 재획득에도 하한을 우회하지 않고,24시간 경계 이상은 자동발신 없이 운영확인을 유지한다. 기존 NEEDS_REVIEW의 자동재개·budget reset은 만들지 않는다.

correlationId는 새 삭제 route의 업무오류에만 적용한다. 필수 문자열·ASCII1~128자·전체문자열 검증이며 공백/개행/Unicode/null/숫자는 거절한다. 다른 API 규격을 바꾸지 않고, 인가·중복·응답 귀속 근거로 사용하지 않는다. 무검증 header echo 및 개인정보/토큰 삽입은 금지한다.

## 3. 결정 사항과 동결 경계

앞선 LC draft-2 회신의 두 미결 사항은 해소됐다. 추가 제품 정책 선택 없이 아래 원문의 명시된 wire·저장·오류·시각·보존/cleanup 분리 규칙을 구현 기준으로 수락한다. 기존 R2 사용자 정책은 유지하며 상충하는 구 조항은 최신 초안이 우선한다. 향후 의미 변경은 새 revision과 공동 수락으로 처리한다.

원문은 Billing 저장소에서 수정하지 않았다. 이번 기록은 LC 수락의 증거이며 Billing의 수락 접수·문서 상태 변경이나 외부 회신 전송까지 수행했다는 뜻은 아니다. 원문에 남아 있는 draft/미동결 표기를 임의로 변경하지 않는다.

## 4. 주요 위험·미확인 사항

전체 scalar raw suite·새 purpose signed JWT/port/query 및 실제 sender/DB/worker31개 경계·경합은 남는다. 자료 형식과 산술 검사를 실제 동작의 통과로 보지 않는다. 운영 설정명/실환경 inventory·DDL·최종purger 등 별도 gate는 이번 기술 계약 동결로 해제하지 않는다. 기본OFF와 기존 활성화 차단은 유지한다.

## 5. 이번 검증·다음 작업

[독립 검사기](../../scripts/contracts/verify_deletion_draft3.py)로 생산자 검사기 없이 supplied candidate/backoffDueAt에서 산술을 계산하고, correlationId 형식 및 정상503 원문 구조를 검사했다.

```sh
python3 -B scripts/contracts/verify_deletion_draft3.py /Users/msde76/.codex/worktrees/plan012-refund/billing/src/test/resources/contracts/lc-billing-deletion-exact-r2-draft-3
```

결과: manifest hash1, 하한/기대시각6개, correlationId8개와 추가 길이1/128/129 경계3개, 정상503 원문1개 확인 성공. header parser·랜덤 jitter·HTTP·DB·worker를 실행한 것이 아니다. draft-2 독립 raw7개 결과는 [별도 보고](TMI_217_DELETION_EXACT_R2_DRAFT_2_VALIDATION.md)에 유지하고 합산하지 않는다.

다음은 이 수락 기록을 Billing에 전달해 기준을 맞추고, 남은 fixture 제작·독립 검증과 별도 승인된 LC 구현/31개 runtime 검증을 진행하는 것이다. 제품 코드 변경이 없어 Gradle은 실행하지 않았다. 이번에는 검사기·본 보고·CURRENT_STATE·WORKLOG만 변경했고 기존 P6.1 dirty와 사용자파일을 보존했다. 공개API/AI/S3/Redis/fixture 원본·실DB·배포·Jira·commit/push는 변경하지 않았다.

## 6. 부록 — 동결 대상 원문 hash

기준 경로: `/Users/msde76/.codex/worktrees/plan012-refund/billing/docs/contracts/`

| 원문 | SHA-256 |
| --- | --- |
| TMI-217-DELETION-EXACT-R2-DRAFT-1.md | 20b07017d8d91d5d60c5d454887e84ad705befecd99ad205f65625268b95f088 |
| TMI-217-DELETION-EXACT-R2-DRAFT-2.md | b9b71cbe877bed7213a0274114771f4795303d9ce2757f8a3ec4cd435f0af730 |
| TMI-217-DELETION-EXACT-R2-DRAFT-3.md | daafb38a3133e343049dc028368ccffdb902ac2186493754621602a879c0257c |

신규 draft-3 `cases.json` 관측/manifest 일치 SHA-256: `56dfc463ad2ad77865342ee104682b51aefe36f05dfac788f3cbac450ec1b75d`. 이는 제한 자료의 bytes 식별이며 전체 fixture 완성·동결 증거가 아니다. 기존 원본은 재생성하지 않았다.
