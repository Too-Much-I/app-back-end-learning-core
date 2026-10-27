# 파트별 학습 유료권 조회 — LC 구현 기준 확인 R1

- 날짜: 2026-10-23
- 상태: Billing 수락 기록과 LC 원문 bytes 대조 완료. 아래 동일 규격을 LC 구현 기준으로 확인한다. 제품 구현·fixture 실행·공동 연동 테스트·배포/활성화 완료가 아니다.
- 대상 revision: `lc-billing-part-learning-eligibility-recommendation-r1-review-2`
- [수락 대상 원문](LC-BILLING-PART-LEARNING-ELIGIBILITY-RECOMMENDATION-R1.md)
- SHA-256: `c27b981a1c1ec6f208089a1e3eae02f8f2bcefdaa3196d82fcc35dc70a545b99`

## 1. 확인 근거

사용자가 지정한 Billing `docs/contracts/LC-BILLING-PART-LEARNING-ELIGIBILITY-R1-REVIEW.md` §6 수락 기록을 읽었다. Billing의 `LC-BILLING-PART-LEARNING-ELIGIBILITY-R1-REVIEW-2-SNAPSHOT.md`와 LC 원문을 cmp로 대조해 byte 동일을 확인했고 SHA-256도 일치했다.

참조 `lc-billing-feedback-reanswer-eligibility-r1-draft-5` 원문 SHA-256은 `51fd0fb8fa43cffcf812fbaae717a43b04c1dfe937dcd30958b5750a819d746e`로 재확인했다. 인증 원본/기존 fixture는 수정하지 않았다. 수락 대상 원문의 작성 당시 후보/공동 확인 표현은 bytes 보존을 위해 수정하지 않고 이 별도 기록으로 현재 상태를 명시한다.

## 2. 구현 기준

- 유료권 전용 route/audience 및 네 응답 조합, 무차감·무캐시·16KiB·기존402/401/503/500 매핑을 그대로 사용한다.
- 단일 ACTIVE 및 activeEndsAt, 기간 중첩503, 미래 checkedAt 무허용 및 wall/monotonic 시각 검증을 유지한다.
- LC 원격 HTTP는 쓰기 Transaction 밖, Billing 내부는 기존 읽기 전용 snapshot으로 구분한다.
- Part1 포함 유료 콘텐츠/음성과 신규 submit에 조회한다. 무료/원문 없는 목록·진도/기존 결과·polling/준비 create/사용자 녹음 upload에는 조회하지 않는다.
- 실제 접수 근거 있는 replay/retry는 기존 근거와 소유권·삭제·탈퇴 검사로 처리한다. 새 승인 receipt/컬렉션/worker/분산 예약을 만들지 않는다.
- 기존 재답변·모의고사 정책을 변경하지 않는다. 추가 제품 정책 선택은 필요 없다.

## 3. 다음 작업과 범위

1. 후속 구현 요청에 따라 Billing 기본 OFF 유료권 조회 및 LC client/연결 지점 구현.
2. 각 서비스 독립 테스트 및 L01~L12, LC 파일 freeze/queue 실패·중복 요청 검증.
3. 공동 연동 검증 후 별도 승인으로 배포·활성화. 문서 bytes 일치를 실행 테스트 통과로 대체하지 않는다.

LC 계획의 앱 이어 풀기·학습 신규 생성300회/실패 재시도 일일 차감 제외는 별도 구현 항목으로 유지한다. 이번 작업에서는 문서/참조/기록만 갱신했고 제품 코드·DB·설정·Billing 저장소·Jira·외부 전송·commit/push·배포를 변경하지 않았다.
