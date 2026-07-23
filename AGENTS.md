# 프로젝트 목적

이 저장소는 기존 토선생 웹 POC 백엔드를 복제하여 만든 앱용 Learning Core 서비스다.

기존 POC 저장소:
- Too-Much-I/web-back-end

현재 저장소:
- Too-Much-I/app-back-end-learning-core

기존 POC 저장소와 기존 POC 배포 환경은 절대 수정하지 않는다.
현재 저장소의 코드만 수정한다.

# 기술 환경

- Java 21
- Spring Boot
- Gradle Wrapper
- MongoDB
- Redis
- AWS S3
- Python AI 채점 서버 연동

기본 빌드 및 테스트 명령:
- ./gradlew clean test

# 최우선 호환성 규칙

기존 웹 프론트와 Python AI 서버의 호환성을 최우선으로 유지한다.

명시적인 요청이 없으면 다음 외부 계약을 변경하지 않는다.

- 기존 공개 API URL
- 기존 HTTP Method
- 기존 Path Parameter
- 기존 Query Parameter
- 기존 Request Body
- 기존 Response DTO
- 기존 BaseResponse 구조
- 기존 retryCount 방식
- 기존 S3 Presigned URL 발급 흐름
- 기존 음성 제출 흐름
- 기존 Polling 흐름
- 기존 AI 요청 구조
- 기존 AI Callback URL
- 기존 AI Callback JSON 구조

API를 더 RESTful하게 만든다는 이유로 기존 계약을 임의로 재설계하지 않는다.

다음과 같은 변경은 현재 작업 범위에 포함하지 않는다.

- upload-url GET API를 POST로 변경
- retryCount를 attemptId 방식으로 변경
- 별도의 시험 완료 API 추가
- 기존 성공 응답 코드를 201 또는 202로 일괄 변경
- 기존 응답 DTO에 새로운 필드 추가
- API 버전 변경

# 사용자 식별 규칙

클라이언트는 실제 userId를 직접 전달하지 않는다.

다음 위치에 userId를 추가하지 않는다.

- Request Body
- Path Parameter
- Query Parameter
- 기존 Response DTO

실제 사용자 ID는 UUID 형식 문자열로 관리한다.

현재 Identity 서버가 없으므로 CurrentUserProvider 추상화를 사용한다.

현재 단계에서는 LegacyCurrentUserProvider 또는 동일한 역할의 구현체가 고정 개발 UUID를 반환한다.

기본 개발 UUID:
- 00000000-0000-0000-0000-000000000001

기존 웹 프론트는 현재 단계에서 Authorization 헤더 없이도 동작해야 한다.

Identity 서버 연동 이후에는 JWT의 sub 클레임을 실제 userId로 사용한다.

Identity 연동 이후에도 프론트는 userId를 직접 보내지 않고 Authorization 헤더에 Access Token만 전달한다.

현재 작업에서는 다음을 수행하지 않는다.

- JWT 인증 강제
- 모든 API를 authenticated로 변경
- SecurityConfig의 전면 재설계
- Identity 서버 구현

# 시험 세션 규칙

시험 생성 시 다음 관계를 Learning Core의 MongoDB에 저장한다.

- examId -> 실제 userId

ExamSession은 최소한 다음 필드를 가진다.

- examId
- userId
- createdAt

status 또는 mockExamId는 기존 코드 흐름에서 명확히 얻을 수 있고 중복 상태를 만들지 않는 경우에만 추가한다.

시험 생성 API의 기존 Request와 Response는 변경하지 않는다.

외부 Response에 userId를 추가하지 않는다.

# AI 연동 규칙

Python AI 서버로 보내는 user_id에는 실제 사용자 UUID가 아니라 기존과 동일하게 examId를 전달한다.

반드시 다음 규칙을 유지한다.

- AI user_id = examId
- AI user_id != 실제 userId

Python AI 서버의 기존 필드명 user_id를 변경하지 않는다.

AI Callback에서 전달되는 user_id도 examId로 해석한다.

외부 JSON 필드명이 user_id라면 그대로 유지한다.

Java DTO 내부에서는 의미를 명확하게 하기 위해 examId라는 필드명을 사용할 수 있지만, 외부 JSON 계약은 @JsonProperty("user_id") 등으로 그대로 유지한다.

AI Callback 처리 순서는 다음과 같다.

1. Callback의 user_id를 examId로 해석한다.
2. ExamSessionRepository에서 examId로 ExamSession을 조회한다.
3. ExamSession.userId에서 실제 사용자 UUID를 가져온다.
4. ExamResult에 examId와 실제 userId를 함께 저장한다.
5. 기존 Azure, SpeechAce, LLM 채점 결과 저장 흐름을 유지한다.

실제 사용자 UUID를 Python AI 서버에 전송하지 않는다.

# ExamResult 규칙

ExamResult에 실제 userId 필드를 추가한다.

ExamResult에는 최소한 다음 식별 관계가 존재해야 한다.

- examId: 시험 세션 식별자
- userId: 실제 사용자 UUID
- questionNumber
- retryCount

examId와 userId를 서로 바꾸어 저장하지 않는다.

다음 외부 응답에는 userId를 추가하지 않는다.

- 시험 생성 응답
- 시험 상태 응답
- 시험 종합 결과 응답
- 문항별 결과 응답
- 업로드 URL 응답
- 음성 제출 응답
- 문항별 Polling 응답

userId는 내부 DB 저장과 사용자 소유권 검증 용도로만 사용한다.

# 시험 소유권 검증 규칙

사용자용 API에서 examId를 사용할 때 다음 조건을 확인한다.

- ExamSession.userId == CurrentUserProvider.getCurrentUserId()

examId를 입력받는 사용자용 API에는 소유권 검증을 적용한다.

최소 확인 대상은 다음과 같다.

- 시험 전체 상태 조회
- 시험 종합 결과 조회
- 문항별 결과 조회
- S3 업로드 URL 발급
- 음성 제출
- 개별 문항 상태 조회
- 그 밖에 examId를 입력받는 사용자용 공개 API

AI Callback은 사용자용 API가 아니므로 CurrentUserProvider 기반의 사용자 소유권 검증을 적용하지 않는다.

AI Callback에서는 examId로 ExamSession을 조회하여 실제 userId를 찾는다.

# 삭제할 기능

다음 API는 제거 대상이다.

- POST /api/v1/exams/trial
- POST /api/v1/exams/{examId}/terminate

위 두 API에만 사용되는 다음 코드는 제거할 수 있다.

- Controller 메서드
- Service 인터페이스 메서드
- Service 구현 메서드
- Request 또는 Response DTO
- Swagger 또는 OpenAPI 문서
- 테스트
- ErrorCode
- 미사용 import

다른 API에서도 사용하는 공유 코드는 삭제하지 않는다.

# 현재 추가하지 않을 기능

다음 기능은 현재 Learning Core 수정 범위에 포함하지 않는다.

- Identity 서버
- 이메일 회원가입
- 로그인
- 소셜 로그인
- Refresh Token
- JWT 인증 강제
- 10초 챌린지
- 스트릭
- 주간 학습 이력
- 취약 단어장
- attemptId 기반 재설계
- 메시지 큐
- Kafka
- SQS
- Redis 구조 전면 개편
- S3 Key 구조 전면 변경
- 관련 없는 대규모 리팩터링

# Redis 및 S3 규칙

기존 Redis 상태 및 Lock 흐름을 임의로 변경하지 않는다.

application.yml에 app.redis.key-prefix 설정이 있더라도 명시적인 구현 요청이 있기 전에는 Redis 키 구조를 전면 변경하지 않는다.

기존 S3 Presigned URL 및 Object Key 구조를 임의로 변경하지 않는다.

별도의 S3 버킷을 사용하고 있으므로 app.s3.key-prefix를 적용하기 위해 기존 Object Key 계약을 자동으로 변경하지 않는다.

# 보안 및 설정 규칙

실제 Secret이나 자격증명을 저장소에 추가하지 않는다.

다음 값을 코드, 테스트, 문서, 로그에 작성하지 않는다.

- 실제 AWS Access Key
- 실제 AWS Secret Key
- 실제 MongoDB URI와 비밀번호
- 실제 JWT Secret
- 사용자 음성 데이터
- 사용자 발화 전체 Transcript
- Refresh Token

application.yml에서는 환경변수 참조 방식을 유지한다.

application-test.yml에는 가짜 테스트 값만 사용한다.

기존 POC용 GitHub Actions 배포 Workflow를 복원하지 않는다.

새로운 배포 Workflow는 명시적으로 요청받기 전에는 추가하지 않는다.

기존 POC EC2, 컨테이너 또는 배포 경로를 대상으로 설정하지 않는다.

# 테스트 규칙

변경한 비즈니스 로직에는 테스트를 추가한다.

테스트에서는 실제 운영 인프라를 호출하지 않는다.

다음 외부 의존성은 가능한 경우 Mock으로 처리한다.

- MongoDB Repository
- Redis
- AWS S3
- Python AI 서버
- Sentry

모든 구현 작업 후 다음 명령을 실행한다.

- ./gradlew clean test

기존 공개 API의 Request와 Response 구조가 바뀌지 않았는지 확인한다.

컴파일 경고를 현재 작업과 무관하게 일괄 수정하지 않는다.

# 코드 변경 규칙

기존 패키지 구조와 코드 스타일을 우선 따른다.

관련 없는 파일을 포맷하거나 리팩터링하지 않는다.

명시적인 요청 없이 새로운 운영 의존성을 추가하지 않는다.

작업 범위 밖의 버그를 발견하면 임의로 수정하지 말고 최종 보고에 기록한다.

기존 API 호환성에 영향을 줄 수 있는 변경은 구현 전에 명확히 보고한다.

# Git 규칙

Codex는 다음 명령을 직접 수행하지 않는다.

- git commit
- git push
- git push --force
- git reset --hard
- 기존 POC 저장소 수정
- 기존 POC 배포 실행

Codex는 파일 수정과 테스트까지만 수행한다.

커밋과 push는 사용자가 직접 수행한다.

# 작업 완료 보고 규칙

각 작업이 끝나면 다음 내용을 보고한다.

1. 변경한 파일
2. 변경한 동작
3. 유지한 외부 API 계약
4. 실행한 테스트
5. 테스트 결과
6. 남아 있는 위험 요소
7. 다음 작업 전에 확인할 사항

# 코드 리뷰 우선순위

리뷰할 때 다음 문제를 우선 확인한다.

1. 기존 공개 API URL, Method, Parameter, Response가 변경되었는가
2. BaseResponse 구조가 변경되었는가
3. Python AI의 user_id가 examId가 아닌 실제 userId로 변경되었는가
4. Callback 외부 JSON 구조가 변경되었는가
5. 클라이언트 Request 또는 Response에 실제 userId가 추가되었는가
6. 시험 생성 시 ExamSession 저장이 누락되었는가
7. ExamResult.userId 저장이 누락되었는가
8. examId와 실제 userId가 뒤바뀌었는가
9. 사용자용 examId API의 소유권 검증이 누락되었는가
10. AI Callback에 사용자용 소유권 검증을 잘못 적용했는가
11. 테스트가 실제 AWS, Atlas, Redis 또는 Sentry에 의존하는가
12. Secret 또는 개인정보가 코드나 로그에 추가되었는가
13. trial과 terminate 외의 API가 삭제되거나 변경되었는가
14. 관련 없는 대규모 리팩터링이 포함되었는가
