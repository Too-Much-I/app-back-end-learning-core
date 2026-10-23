# 파트별 학습 청크·기존 미디어 재사용 추가 계획 R1

- 날짜: 2026-10-23. 상태: Notion 요구와 현행 LC 코드 대조 후 계획 추가. 구현·활성화 미완료.
- 상위: [파트별 학습 계획](PART_LEARNING_ACCESS_RESUME_IMPLEMENTATION_PLAN.md).
- 근거: [청크 연결 안내](https://app.notion.com/p/chunks-402dcc5dbaeb8045b4dccc7602341892), [학습 추가 데이터 요구](https://app.notion.com/p/3fedcc5dbaeb80e4b76ae4ffdafec06b). 두 페이지 본문 확인. DB 완료 수치는 콘텐츠 담당 문서의 보고이며 이번에 DB를 독립 검증하지 않았다.

## 1. 5줄 결론

1. 기존 상세 응답의 문장에 선택적 `chunks`를 추가한다. 새 API나 청크 생성 서버는 만들지 않는다.
2. 청크는 같은 문장의 `words[]` 인덱스 범위다. 단어·문장·음성 타이밍은 수정하지 않는다.
3. 새 콘텐츠가 이전 버전 음성을 재사용하므로 manifest의 `mediaSource`를 읽는 조회·서명 경로 보완도 필요하다.
4. 첫 세트 무료, 유료 콘텐츠 제공 때만 이용권 조회, 제출 재조회 없음·세트 단위 이어 풀기 계획은 유지한다.
5. 코드·테스트를 먼저 준비한 뒤 환경별 버전을 전환한다. 설정만 먼저 바꾸면 미디어 조회가 실패할 수 있다.

## 2. 사용자가 반드시 읽어야 하는 내용

청크는 한국어 해석을 보며 의미 덩어리를 순서대로 맞추는 앱 퍼즐용이다. 서버가 새 채점을 하거나 퍼즐 진도를 저장하는 기능이 아니다. Part2~5에 적용하며 Part1은 청크가 없어도 된다.

`GET /api/v1/learning/units/{unitId}?level=IM3|IH|AL`의 `result.lessons[].answer.sentences[].chunks`를 추가한다. `from`과 `to`는 0부터 시작하며 끝 위치도 포함한다. 예를 들어 `[{"from":0,"to":3},{"from":4,"to":7}]`은 앞 네 단어와 뒤 네 단어다. 기존 문장 필드·URL·BaseResponse·AI 계약은 유지한다.

사용자가 이전할 예전 데이터가 없다고 확인했으므로 진도 이전 기능은 필요 없다. 새 버전은 문항 ID와 contentVersion이 바뀌므로 앱은 목록·상세 캐시를 갱신하고 기존 `CONTENT_VERSION_CHANGED` 처리로 새 문항을 연다. 이는 기존 기록 삭제 승인이 아니며 이번에 실DB를 확인하거나 정리하지 않았다.

## 3. 사용자 결정 사항

- 청크 연결 자체에는 추가 과금 정책이 필요 없다. 실제 구현·배포는 후속 요청으로 진행한다.
- 사용자 확인으로 이전할 예전 데이터가 없으므로 진도 자동 이전은 제외한다. 이 사항은 더 이상 미결 정책이 아니다.
- `label` 통일·새 `role` 필드는 이번 요구의 낮은 우선순위 질문이며 별도 과제다. 기존 label을 그대로 전달한다.

## 4. 위험과 미확인 사항

- Notion은 test/prod 신규 데이터 저장·재조회 완료를 보고하지만 LC 실제 API·오디오 재생은 별도 검증 전이다.
- `LearningCatalog.mediaMap()`은 현재 버전만 조회하고 `LearningStorage.media()`도 현재 버전 경로만 허용한다. v3 설정만 변경하면 v2 미디어 ID를 찾지 못하거나 서명을 거절한다.
- 이전 미디어라는 이유로 모든 버전·버킷을 검색하거나 서명하도록 완화하지 않는다. 현재 콘텐츠의 참조 관계와 승인된 source 범위를 둘 다 확인한다.
- 콘텐츠 담당의 경계 검토는 자동 검사·코딩 에이전트 검토이며 사람의 언어 품질 QA 완료로 표현하지 않는다. 길이 초과 청크는 의미 보존을 위한 허용 예외다.
- prod 체크리스트는 문서상 9개, test는 320개다. 청크 제공 완료가 모든 문항 채점 준비 완료를 뜻하지 않는다.

## 5. 최소 구현 범위와 순서

### A. 문장 청크 전달

- `LearningCatalog.detail()`의 명시적 문장 projection에 `chunks`만 추가한다.
- absent/null은 그대로 허용하고 앱은 문장 단위 처리 또는 퍼즐 제외를 한다. 임의 빈 배열·자동 분할 결과를 합성하지 않는다.
- words 텍스트·순서·타이밍과 기존 `validateWords` 검증은 유지한다. 청크의 정수 범위·연속성·전체 단어 coverage를 테스트한다.
- 2~7단어/45자/2~5조각은 콘텐츠 제작 권장 기준이다. 긴 명사구 등의 승인 예외를 런타임 오류로 만들지 않는다.

### B. manifest 기반 기존 미디어 재사용

- 현재 dataset/version의 ready manifest에서 `mediaPolicy=reuse-published-media` 및 `mediaSource`를 해석한다. source manifest도 ready인지 확인한다.
- 콘텐츠·체크리스트는 현재 버전을 사용하고, 미디어만 source dataset/version + 요청된 ID + `accessScope=authenticated-learning`으로 조회한다.
- `LearningStorage.media()`는 검증된 source bucket/prefix를 사용한다. prefix는 `answer-learning/{sourceDataset}/{sourceVersion}`, 객체는 그 아래 `/media/`여야 한다. 환경의 허용 버킷 검증을 유지하며 클라이언트가 bucket/prefix/scope를 정하지 못한다.
- 상세·목록 썸네일·미디어 URL 재발급을 같은 source 해석으로 맞춘다. `mediaSource` 없는 기존 manifest는 현재 dataset/version 경로를 사용한다. 잘못된 source는 다른 버전 fallback 없이 실패시킨다.
- 새 별도 manifest 캐시 시스템은 만들지 않는다. 캐시를 사용하게 된다면 manifest/version 변경 때 갱신하여 source 혼용을 방지한다.
- `counts.learning_media=0`은 새 행 추가가 없다는 뜻이며 참조 미디어가 없다는 뜻이 아니다. 기존 v2 미디어·S3를 복사하거나 삭제하지 않는다.

### C. 유료 접근·앱 연결

- 무료 첫 세트 판정은 현재 v3 unit 기준이다. 구 v2 media ID를 쓰더라도 그 미디어를 참조하는 현재 unit/answer 관계로 무료·유료를 판단한다. 참조 없는 ID를 이용한 URL 재발급은 허용하지 않는다.
- 목록 썸네일과 유료 본문/모범답안 음성을 구분하며 유료 음성 URL 재발급에도 콘텐츠 제공 검사를 적용한다.
- 청크 퍼즐·녹음 준비·submit/retry에 새로운 이용권 조회나 입장 기록을 추가하지 않는다. 기존 인증·소유권·삭제·탈퇴·요청 유효성은 유지한다.
- 앱은 words의 원문을 청크 범위로 묶어 섞고 재조립한다. 서버 퍼즐 채점·진도 collection·새 worker는 추가하지 않는다.
- 프론트 오류 안내는 [현행 API](../answer-learning/api.md)와 [콘텐츠 전용 보완](../contracts/LC-BILLING-PART-LEARNING-CONTENT-ONLY-AMENDMENT-R1.md)을 기준으로 연결 시 정리한다. 이번 청크 변경만을 위한 새 오류 체계는 만들지 않는다.

### D. 검증 및 전환

1. 선택적 chunks 전달·구 데이터/Part1 호환, words 불변, 범위 coverage 단위 테스트.
2. v3→v2 미디어 조회/URL 재발급, ready 누락·타버킷·타prefix·scope·미참조 ID 거절, 기존 manifest 호환 테스트.
3. Part2~5 × IM3/IH/AL 12개와 Part1 1개, 총 13개 상세 응답·음성 확인. 이는 향후 실행할 검증 명세이지 통과 결과가 아니다.
4. 첫 세트 무료/나머지 유료 접근 및 submit Billing 미호출 회귀. 새 버전 완료 기록·앱 캐시·버전 충돌 확인.
5. test manifest/미디어 참조 대조 후 test 전환·확인, 그 뒤 별도 승인으로 prod 적용. 이전 버전 설정과 미디어는 롤백용으로 보존한다.

## 6. 부록 — 버전과 근거

신규 dataset: `ts-level-bank-40-per-part-20261023-v3`

| 환경 | 새 contentVersion | 재사용 source contentVersion |
| --- | --- | --- |
| test | `20261023-a6d6884c69c9` | `20261021-2f495558078b` |
| prod | `20261023-2f02ebd572fe` | `20261021-cdcd4d428285` |

source dataset은 `ts-level-bank-40-per-part-20261021-v2`다. 환경별 manifest의 bucket/prefix/count/hash를 사용하고 test hash를 prod에 복사하지 않는다. 폐기된 복사 후보 `20261023-01377572eeff`, `20261023-6076d920855e`는 사용하지 않는다.

문서 보고: 환경별 200카드·360문항·1,000답안, Part2~5 960답안/2,844문장에 청크, 중복 제거 2,064문장, Part1 40답안은 청크 없음. 기존 미디어 3,459개 재사용·신규 미디어/S3 쓰기 0. LC는 이 수치를 이번 작업에서 실DB 재검증하지 않았다.

현행 코드 근거:

- `src/main/java/web/tosunsaeng/domain/answerlearning/LearningCatalog.java`: detail 문장 projection, current 범위 mediaMap, validateDataset, completed의 lessonId/contentVersion 조건.
- `src/main/java/web/tosunsaeng/domain/answerlearning/LearningStorage.java`: 현재 설정 bucket/version prefix 제한, 기존 GET URL 유효시간 1시간. Billing 판정 5초와 이 URL 만료시간은 별개이며 즉시 철회 기능은 추가하지 않는다.

이번 변경은 계획·작업 기록만이다. 제품 코드·원본 계약 bytes·실DB·S3·Notion·Jira·배포·commit/push는 변경하지 않았다.
