# Azure 삭제 필드 매핑 보완

## 5줄 결론

1. 사용자 후속 구현 요청으로 Azure의 BSON `exam_id`를 삭제 경로에 일관되게 적용했다.
2. 검사기 lookup/cleanup index와 worker 정리/잔존 검증·persistence fence를 보완했다.
3. 기존 Azure 저장 형식·공개 API·AI 계약·owner/삭제 차단 규칙은 유지한다.
4. 운영 데이터/인덱스·봉인·설정·배포·commit/push는 변경하지 않았다.
5. 실제 부모 미연결19건과 개발 고정ID 시험2건은 별도 검토 대상이며 자동 정리하지 않는다.

## 변경 범위

- `DeletionChildFields`: raw Mongo 조회용 시험 참조 필드 매핑. Azure만 `exam_id`, 나머지는 기존 `examId`.
- `DeletionWorker`: collection별 필드로 기존 sealed target의 하위 자료만 정리하고 마지막 잔존 검사에도 같은 필드를 사용한다.
- `DeletionPersistenceFence`: 변환된 Azure Document의 `exam_id`로 부모/owner/삭제 차단 검사. `examId` fallback이나 guard 우회 없음.
- `learning-record-deletion-prepare.js`: Azure index `{exam_id:1,_id:1}`, integrity lookup의 부모 collection은 계속 exam_sessions.
- `DeletionFlowMongoTest`: 실제 Azure entity 직렬화로 과거 자료 삭제·신규 보존, 잔존 자료 검증, 정상 저장 검사 및 삭제 후 deny 회귀.
- 검사기 Node 테스트: 올바른 field/부모 조회 및 기존 잘못된 same-name index의 fail-closed 확인.

## 적용 전에 확인할 사항

기존 환경에 `idx_deletion_exam_cleanup`이 `{examId:1,_id:1}`로 설치돼 있다면 새 검사기는 충돌로 중지한다. 자동 drop/recreate하지 않으므로 해당 환경을 읽기 점검하고 승인된 별도 index 전환이 필요하다. 현재 작업에서는 실환경 index 검사를 다시 실행하거나 변경하지 않았다.

운영 삭제 준비는 남은 실제 미연결 자료·writer 경계·보존 승인 등을 해결하기 전 완료로 보지 않는다. 본 변경으로 역사적 자료를 일괄 삭제하거나 source/owner를 재작성하지 않는다. 기존19건의 발생 원인은 아직 미확정이다.

## 검증

- `./gradlew clean test`: XML1,396건, 실패/오류0, 기존 콘텐츠 fixture 조건부 skip1.
- `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 ./gradlew mongoIntegrationTest`:337건, 실패/오류/skip0. 집중 DeletionFlow 회귀도 별도 통과.
- `node --test scripts/mongodb/*.test.js`:123건 통과(삭제 검사기11개 포함), diff 공백 검사 통과.
- 최초 Mongo 실행은 로컬 Docker client API1.32/서버 최소1.40 불일치로 기동 전 실패했다. 기존 호환 옵션을 테스트 프로세스에만 적용해 재실행했다. 저장소 Docker 설정은 변경하지 않았다.

격리 Mongo만 사용하며 실제 운영 삭제/E2E 검증이 아니다. 기존 dirty 파일·다른 작업 기록은 보존했다. 관련 TMI-193/TMI-217.
