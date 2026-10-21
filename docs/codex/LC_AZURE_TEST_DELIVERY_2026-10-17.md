# Azure 삭제 보완 test 전달

## 5줄 결론

1. TMI-193/TMI-217: 사용자 승인으로 test Azure cleanup 인덱스만 교체했다.
2. `idx_deletion_exam_cleanup` 키는 `{exam_id:1,_id:1}`이며 비unique·비TTL이다. 다른 인덱스는 그대로다.
3. Azure 코드·테스트6파일 및 해당 작업 기록2파일만 develop 기반 별도 브랜치로 분리했다.
4. 전달본 clean test 1,375건(기존 skip1), Mongo334건 실패0, Node 전체123건 통과다.
5. 사용자가 이번 검증을 배포·서버 정상 상태로 한정했다. 실제 사용자 삭제/E2E와 운영 변경은 하지 않는다.

## 전달 및 검증 근거

- 브랜치 `codex/azure-deletion-field-fix`, 커밋 `1ed1c8906017181618ac818ec00c18f75e404f46`.
- PR https://github.com/Too-Much-I/app-back-end-learning-core/pull/49 (develop 대상).
- 기존 작업트리의 운영 readiness 커밋3개·별도 준비 문서/스크립트·DS_Store는 전달에서 제외하고 그대로 보존했다. 따라서 이전 전체 작업트리 테스트 건수와 이번 전달본 건수는 다르다.
- 테스트 DB `to-teacher-learning-core-test.azure_results`의 기존 index 이름/키/옵션을 실행 직전 검사하고 해당 index1개만 drop/create했다. 학습 문서 delete/update는 실행하지 않았다. 원 키 인덱스는 필요 시 재생성할 수 있으나 이번 목적에 맞지 않으므로 복원하지 않는다.
- 기존 test 실행 역할·네트워크·Mongo Secret 참조를 재사용한 공식 mongo:8.0 일회성 ECS 태스크. IAM/네트워크 접근 확대 없음.
- 태스크 `860c1e693518454d8cd1073887522753` STOPPED/exit0. 정의 `lc-test-azure-index-fix-20261017:1` INACTIVE. after 검증으로 올바른 키 및 다른 index 불변 확인.
- raw exception/URI/Token·개별 사용자/시험 자료는 출력하지 않았다. 증빙 화면 `/private/tmp/lc-test-azure-index-fix-20261017.png`.
- 공개 API/BaseResponse/AI user_id=examId/S3/Redis 계약 불변. 운영19건·legacy 최종 봉인은 보류 유지.

## 남은 확인

PR CI(단위·마이그레이션·Mongo 통합 검증) 통과 후 develop 머지 완료. merge commit `509fee047f9491ba8bfcf24181cfc781035ed73e`, 테스트 자동 배포 run `39040605235` 성공. test task45의 이미지가 merge commit의 ECR digest와 일치하고 running1/pending0, 새 대상8080/8444 healthy 및 공개 health UP 확인. 실제 삭제 검증은 이번 범위에서 제외하며 성공으로 보고하지 않는다.

환경변수·Secret 참조·portMappings·logConfiguration, 서비스 networkConfiguration/loadBalancers는 배포 전후 일치한다. 사용자 자료 삭제/업무 mutation 없음. 최종 재확인에서 ECS PRIMARY COMPLETED/running1/pending0, 새8080/8444 healthy. ALB 이전 대상은 draining으로 남아 있으며 최종 등록 해제 완료까지 관측한 것은 아니다. workflow의 Node20 deprecation/runner 전환 경고는 실패가 아니며 이번 범위에서 workflow를 변경하지 않는다.

완료 증빙: `/private/tmp/lc-test-azure-deployment-verified-20261017.png`. 사용자 작업트리의 기존 dirty는 그대로 유지하며, 이번 전달은 별도 `/private/tmp/lc-azure-deletion-delivery-20261017` 작업트리에서 수행했다. 예상 밖 코드/설정 변경은 없다. 다음 실제 삭제 검증은 별도 테스트 대상 지정 후 수행한다.

이번 배포 직전 test task는44, 운영은8로 관측했다. 이전 기록의 운영7과는 다르지만 이번 작업은 운영 서비스를 변경하지 않았다. 설정 원문은 출력하지 않고 test 배포 전후 비교를 위해 메모리에서만 유지한다.
