# TMI-224 프로모션 푸시 프론트 계약

## 5줄 결론

1. 기존 앱의 `CASHBACK_EVENT_HOME` route를 재사용한다.
2. 호환 type은 `CASHBACK_EVENT`이며 앱 화면 분기는 route만 사용한다.
3. 수신거부 안내는 `설정 > 알림 > 이벤트·혜택 알림`이다.
4. 기존21시 모의고사 푸시 payload는 변경하지 않는다.
5. 이 문서는 코드 계약 확인이며 테스트 서버 배포·실수신 완료 증거가 아니다.

## 반드시 읽어야 하는 내용

```json
{
  "notification": {
    "title": "(광고) 토선생 성적 인증 이벤트",
    "body": "성적을 입력하고 기프티콘을 받아보세요!\n수신거부: 설정 > 알림 > 이벤트·혜택 알림"
  },
  "data": {
    "notificationId": "opaque-id",
    "type": "CASHBACK_EVENT",
    "route": "CASHBACK_EVENT_HOME"
  }
}
```

앱은 회원 화면에서 알림 열기를 처리한다. 모르는 route는 화면 이동을 하지 않는다.
광고 동의 토글은 서버가 해당 동의 항목을 제공할 때 표시되며 변경 실패 시 이전 화면 상태로 복구한다.

## 결정할 사항

클릭 경로/수신거부 메뉴는 기존 코드로 확정했으므로 추가 프론트 경로 결정은 없다.

## 위험과 미확인 사항

develop 코드와 설치된 스토어/테스트 앱은 같다고 단정할 수 없다. 실제 설치 빌드에서 클릭 및 동의 철회를 검증해야 한다.
서버 발송 기능은 기본 OFF, dryRun 기본 true다. Identity/Billing 적격 조회 인증 및 worker 중복·실패·철회 테스트를 완료한 뒤 테스트 환경에서 활성화해야 한다.

## 현재 구현

- `PromotionPushPolicy`: 제목/본문/type/route/수신거부 문구.
- `PromotionPushProperties`: 확인된 route/문구 기본값, OFF/dryRun 유지.
- `PromotionPushConfiguration.content`: 실제 FCM 구성과 테스트가 공유하는 payload content 생성.
- `PromotionPushPayloadTest`: 앱 계약 및 기존 모의고사 payload 분리 회귀.

## 부록: 확인 근거

프론트 develop 커밋 `934d4b8a7a11c97d55f77aaa1ab27002aaa87fa6` 기준:

- `src/features/push/push-open-route.ts`: 허용 route와 type 미사용.
- `src/features/push/push-open-route.test.ts`: CASHBACK_EVENT/CASHBACK_EVENT_HOME fixture.
- `src/navigation/MainTabNavigator.tsx:48`: PromotionApplication 이동.
- `src/navigation/RootNavigator.tsx`: 화면 등록.
- `src/features/push/push-notification-events.ts`: 앱 시작/백그라운드 알림 클릭 처리.
- `src/screens/settings/SettingsScreen.tsx`, `NotificationSettingsScreen.tsx`, `use-marketing-push-consent.ts`: 메뉴·동의 변경/실패 복구.
