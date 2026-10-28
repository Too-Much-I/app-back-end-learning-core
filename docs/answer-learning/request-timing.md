# 학습 요청 시간 진단

- 학습 HTTP 요청 종료 시 `event=learning.request.timing` INFO 로그 한 건을 남긴다.
- `totalMs`: 필터 이후 인증·업무·응답 직렬화를 포함한 서버 처리시간. 앱 네트워크와 ALB 대기시간은 제외한다.
- `mongoCommandMs`/`mongoCommands`: 해당 동기 요청 스레드에서 성공하거나 실패한 Mongo 명령의 드라이버 경과시간 합계/횟수. getMore·트랜잭션 명령도 포함한다. 연결 풀 대기·서버 선택·애플리케이션 매핑 비용 및 비동기 worker는 포함하지 않는다. DB 서버의 explain 실행시간과 다르다.
- `billingMs`/`billingCalls`: 학습 권리 client.check 호출 전체의 경과시간/횟수. 실패도 포함하고 추가 호출·재시도를 만들지 않는다.
- 사용자별 ID, 본문, 헤더, 쿼리, 실제 URI, 예외 메시지, Mongo 명령/응답은 읽거나 출력하지 않는다. 고정 route 분류, 상태코드, 시간/횟수만 로그로 남긴다. 기존 requestId/trace 상관관계는 유지한다.

## 해석과 운영

목록/무료 상세에서는 Billing 호출0이 정상이다. 시간은 밀리초 단위로 절삭되므로0도 가능하다. `totalMs - mongoCommandMs - billingMs`를 DB 외 순수 CPU 시간으로 해석하지 않는다. 전체 서버 처리에는 S3/Redis·연결 대기·직렬화 등도 들어간다.

현재 학습 API는 동기 Servlet 경로다. 비동기 요청으로 바꾸면 별도 컨텍스트 전파/종료 시점 계측이 필요하다. 백그라운드 작업이나 타 API의 Mongo 명령은 합산하지 않는다. 모든 학습 요청(폴링 포함)에 한 줄이므로 로그량을 관찰한다. 진단 종료 시 `logging.level.web.tosunsaeng.domain.answerlearning.LearningTimingFilter=WARN`으로 출력 중단 가능하다. 전역 DEBUG를 켜지 않는다.

운영/테스트 모두 기존 기능 설정·권리 계약·공개 응답·AI 계약을 유지한다. CI 통과 후 이번 변경만 양쪽 브랜치로 전달하고 병렬 배포한다. 배포 health 성공과 실제 사용자 요청에서 timing 레코드 생성 확인은 구분한다.
