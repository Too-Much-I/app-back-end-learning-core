# 이어하기·프로모션 API R1

## 5줄 결론

- 한 브랜치 `feat/learning-resume-promotion`에서 두 신규 API를 추가한다.
- 이어하기는 기존 완료기록으로 첫 미완료 세트를 찾으며 새 진도 저장/Billing 조회는 없다.
- 프로모션 ID는 앱에서 받지 않고 서버의 단일 캠페인 설정을 사용한다.
- 프로모션은 기본 OFF, PDF/JPEG/PNG/HEIC 한 개 및 희망 기프티콘/이메일을 접수한다.
- 실서비스 활성화 전 개인정보 정책·비공개 저장소·접수기간을 확정해야 한다. 배포는 이번 범위가 아니다.

## 앱 계약

기존 인증과 BaseResponse를 사용한다. 사용자 ID는 토큰으로만 확인한다.

### 이어하기

`GET /api/v1/learning/resume?part=3&level=IH`

part=1~5, Part 1 권장 level=common(기존 정규화 유지), Part 2~5=IM3/IH/AL.
`result={"unitId":"세트 ID","allCompleted":false}`.
진도 없음/부분 완료는 같은 첫 미완료 세트, 세트 전체 완료는 다음 미완료 세트다.
정렬은 기존 sortOrder/_id, 진도는 인증 사용자+level+현재 contentVersion+lessonId 기준이다.
모두 완료면 `unitId=null, allCompleted=true`. 콘텐츠 없음은 404 LEARNING_NOT_FOUND,
빈 세트/누락·잘못 연결된 문항은 503 LEARNING_UNAVAILABLE이며 전체완료로 처리하지 않는다.
상세 본문/음성은 기존 상세 API에서 별도 유료권 검사를 수행한다.

### 프로모션

`POST /api/v1/promotions/applications`, multipart/form-data:

Swagger도 세 필드를 `requestBody.content.multipart/form-data`로 표시한다. 문자열 query parameter는 문서에서 제거했다.
앱/Swagger 모두 FormData 본문으로 전송하며 JSON 본문이나 URL 쿼리에 이메일을 넣지 않는다.
이는 문서 명시 보완이며 기존 Spring multipart 바인딩/인증/검증·응답 동작은 변경하지 않는다.

| 필드 | 형식 |
| --- | --- |
| gifticon | 필수 희망 기프티콘 상품명 문자열, 공백만 불가·제어문자 제외·최대 100자, 앞뒤 공백 제거 |
| email | 필수 수령 이메일, 최대 254자, 앞뒤 공백 제거, ASCII 일반 이메일 형식(점 포함 도메인), local-part 최대 64자 |
| report | 필수 PDF/JPEG/PNG/HEIC 파일 정확히 1개, 최대 9 MiB |

2026-10-27 사용자 요청으로 은행명/계좌번호/예금주를 제거했다. 새 신청에는 저장하지 않으며
구 필드를 보내면 400이다. 앱을 새 계약에 맞춰 배포해야 한다. 기프티콘은 상품명 자유 입력이며
상품 목록/자동 지급/이메일 발송·소유 확인은 포함하지 않는다. 이메일 대소문자는 보존한다.
기존 신청 문서는 마이그레이션/삭제하지 않으며 기존 신청자의 재신청 409 정책도 유지한다.

추가 userId/promotionId 및 중복 필드/첨부는 받지 않는다.
9 MiB는 기존 multipart 전체 10 MiB 제한 안에 메타정보가 들어가도록 정한 구현값이다.
종전 10 MiB는 제안값이었으며 공통 업로드 설정은 변경하지 않았다.
확장자·클라이언트 MIME 대신 파일 시그니처를 검사한다. 완전한 파일 파싱/악성코드·성적 진위 검사는 아니다.
HEIC는 첫 ISO-BMFF ftyp box의 길이/정렬 및 HEVC 정지 이미지 brand(heic/heix/heim/heis)를 검사한다.
major 또는 compatible brand를 허용하며 generic mif1만 있는 HEIF, AVIF, 동영상은 지원 대상으로 보지 않는다.
원본을 image/heic로 저장하며 JPEG 변환/브라우저 미리보기는 제공하지 않는다. 실제 iOS 파일 선택·업로드는 앱 E2E 확인 대상이다.
성공 HTTP 200, `result={"applicationId":"접수 UUID"}`. 이메일/기프티콘/파일 URL/userId는 반환하지 않는다.
캠페인별 사용자 한 번, 수정 API 없음. 기존 신청이 있으면 409를 반환하고 덮어쓰지 않는다.

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | PROMOTION_INVALID_REQUEST | 입력/첨부 수 오류 |
| 409 | PROMOTION_CLOSED | opensAt 포함~closesAt 미포함 접수기간 밖 |
| 409 | PROMOTION_ALREADY_APPLIED | 이미 접수됨 |
| 413 | PROMOTION_REPORT_TOO_LARGE | 크기 초과 |
| 415 | PROMOTION_REPORT_UNSUPPORTED | 파일 시그니처 불일치 |
| 503 | PROMOTION_UNAVAILABLE | 저장 등 일시 실패/결과 불명 |

기존 인증 401 및 삭제/owner 제한을 유지한다. 기능 OFF 시 route 미등록.
응답 유실 후 재신청은 이미 저장됐으면 409다. 멱등키·수정·상태조회·관리자 API는 이번에 추가하지 않는다.

## 저장과 활성화 전 확인

- `app.promotion.enabled` 기본 false. 활성화하려면 campaign-id, bucket, opens-at, closes-at 설정 필수.
  환경변수 바인딩은 APP_PROMOTION_ENABLED / APP_PROMOTION_CAMPAIGNID / APP_PROMOTION_BUCKET /
  APP_PROMOTION_OPENSAT / APP_PROMOTION_CLOSESAT 사용 가능하다. 시각은 UTC ISO-8601.
- `promotion_applications` 컬렉션을 배포 전에 준비한다. 자동 생성/실DB 변경 없음.
  `_id=SHA256(campaignId + LF + userId)`로 Mongo 기본 unique 인덱스를 재사용한다.
  기존 데이터가 있다면 이 식별 규칙과 일치해야 하며 임의 재작성하지 않는다.
- 지정 bucket은 **전용 비공개**, Block Public Access/최소 IAM/암호화/제한 열람을 운영에서 확인한다.
  기존 음성 S3 bucket을 자동 재사용하지 않는다. 업로드는 SSE-S3, opaque key이며 원본 filename을 저장하지 않는다.
- S3 업로드는 Mongo Transaction 밖, 신청 insert는 기존 사용자 writer fence Transaction 안이다.
  중복 패자의 파일만 승자 기록 확인 후 정리한다. DB 결과 불명·S3 timeout·cleanup 실패 때는
  유효 파일 삭제를 피하기 위해 비공개 고아 파일이 남을 수 있다. 자동 TTL/정리 worker는 추가하지 않는다.
- 이메일·기프티콘 상품명은 DB 문서에 저장된다. 별도 필드 암호화는 추가하지 않았으므로 DB 암호화·접근권한을 확인한다. 기존 계좌정보의 일괄 삭제는 이 변경에 포함하지 않는다.
- 보존/폐기 기간, 수집 동의 및 담당자 열람 방법을 확정하기 전 실제 개인정보를 접수하지 않는다.
  기존 학습 삭제/탈퇴 정리와 새 신청·성적표의 보존 관계도 활성화 전에 결정해야 한다.
  코드가 기존 학습 삭제에 새 자료 자동 삭제를 연결했다고 해석하면 안 된다.
- 계정 이전 시 프로모션 신청 소유자 자동 이전은 없다. 자동 지급/OCR/실명 검증/새 관리자 화면은 제외한다.

## 검증 범위

단위·MockMvc 테스트로 세트 진행/전체완료/누락자료·인증·신청 중복/경합/결과불명·파일 제한을 검증한다.
외부 AWS/실DB를 사용하지 않는다. 운영 비공개 권한과 실제 파일 업로드/동의 화면 E2E는 배포 전 별도다.
