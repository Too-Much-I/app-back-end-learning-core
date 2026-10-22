# 업로드 URL 공통 쓰기 제거 — 2026-10-22

- 사용자 승인: 전용 브랜치 수정 후 운영에 직접 배포. 이번 변경의 commit/push/PR/main 병합·기존 운영 workflow 실행을 포함한다. 기존 Git/배포 금지의 이번 요청 한정 예외이며 IAM/Secret/DB 변경은 포함하지 않는다.
- 관련: TMI-125/TMI-193, Sentry 7803874302. 실제 upload-url 실패 스택은 getPresignedUrl→touchActive:47이고 CloudWatch는 Mongo112/3회 소진을 확인했다. 경쟁 상대 요청은 미확정이다.
- URL 발급만 read-only로 변경한다. 공통 owner guard revision 쓰기/생성 및 Mongo Transaction을 하지 않으며 서명 전후 소유권·삭제 상태·merged guard·환불 차단을 확인한다. MEMBER/탈퇴 필터는 변경하지 않는다.
- 완료 시험 재답변은 기존 Billing 무차감 조회와 서명 후5초 유효기간/현재 Session 검증을 유지한다. Job 승인 저장·답안 접수·채점 callback·삭제/merge writer fence는 불변이다.
- 최종 조회와 응답 사이 merge/delete 경합은 원자적으로 막지 않는다. 이미 생성된 최대5분 Presigned PUT capability가 남을 수 있는 위험을 사용자와 논의해 이 범위에서 수용한다. 늦은 업로드는 접수/콜백 보호로 학습 기록을 생성할 수 없어야 하며 S3 정리 동작은 변경하지 않는다. 원자 fence와 동일한 보장으로 표현하지 않는다.
- 공개 route/method/DTO/BaseResponse·S3 key/5분서명·기존 expiresIn60·Redis·AI 계약은 유지한다. guard 전체 OFF, retry 정책 변경, 요약 조건 완화, 기존 두 시험의 재처리/DB 복구는 하지 않는다.
- 검증: URL 발급 무쓰기/무Transaction, guard merged 거절, 서명 도중 소유권 변경·삭제 거절, 환불 차단, 재답변 승인 재검증, 기존 소유권/API/submit/callback 회귀. 배포 뒤 health 및 ECS 안정화 확인과 실제 앱 제출 검증은 구분한다.
