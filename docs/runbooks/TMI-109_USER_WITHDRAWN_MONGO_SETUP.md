# TMI-109 UserWithdrawn Mongo 운영 준비

이 문서는 staging/prod에서 `TMI-109` consumer를 활성화하기 전에 적용할 MongoDB collection과 TTL index 절차다.

실제 Mongo URI, 계정, 비밀번호와 database 이름은 이 문서나 명령 이력에 기록하지 않는다. 승인된 운영 절차로 대상 database를 먼저 선택한 뒤 아래 명령을 실행한다.

## 1. 선행 조건

- 대상 MongoDB가 multi-document Transaction을 지원하는 replica set 또는 동등한 구성이다.
- Learning Core runtime principal은 아래 세 collection에 필요한 최소 read/write 권한을 가진다.
- `USER_WITHDRAWN_CONSUMER_ENABLED`와 `USER_WITHDRAWN_DENY_GATE_ENABLED`는 DDL 검증 전까지 `false`다.

## 2. Collection과 TTL index

```javascript
db.createCollection("user_withdrawn_event_inbox")
db.createCollection("withdrawn_user_access_denies")
db.createCollection("user_withdrawn_transaction_probe")

db.user_withdrawn_event_inbox.createIndex(
  { cleanupAt: 1 },
  {
    name: "ttl_user_withdrawn_inbox_cleanup",
    expireAfterSeconds: 0
  }
)

db.withdrawn_user_access_denies.createIndex(
  { expireAt: 1 },
  {
    name: "ttl_withdrawn_user_access_deny_expire",
    expireAfterSeconds: 0
  }
)
```

이미 collection 또는 같은 이름의 index가 존재하면 새로 만들지 말고 다음 검증 결과를 확인한다. 이름이 같지만 key 또는 `expireAfterSeconds`가 다르면 임의 수정하지 않고 rollout을 중단한다.

## 3. 검증

```javascript
db.user_withdrawn_event_inbox.getIndexes()
db.withdrawn_user_access_denies.getIndexes()
db.user_withdrawn_transaction_probe.getIndexes()
```

필수 조건:

- inbox TTL: `cleanupAt: 1`, `expireAfterSeconds: 0`
- deny marker TTL: `expireAt: 1`, `expireAfterSeconds: 0`
- probe collection이 미리 존재함
- 실제 사용자 event, userId, Token 또는 credential을 probe collection에 넣지 않음

staging/prod에서 consumer가 활성화되면 startup probe가 임시 canary를 Transaction 안에서 insert하고 rollback한 뒤 잔존 문서가 0건인지 확인한다. 실패하면 pod 기동과 rollout을 실패시킨다.

## 4. 활성화 순서

1. 위 collection/index 적용과 검증
2. staging에서 consumer와 deny gate 동시 활성화
3. startup probe와 workload 인증 확인
4. production에서 empty-store Learning Core consumer와 gate 선활성화
5. 후속 Jira `TMI-111` Identity publisher 활성화

```text
USER_WITHDRAWN_CONSUMER_ENABLED=true
USER_WITHDRAWN_DENY_GATE_ENABLED=true
```

## 5. Rollback

publisher와 workload ingress를 먼저 중지한다. 이미 저장된 marker가 있으면 deny gate를 끄지 않는다.

```text
USER_WITHDRAWN_CONSUMER_ENABLED=false
USER_WITHDRAWN_DENY_GATE_ENABLED=true
```

rollback 중 세 collection이나 TTL index를 삭제하지 않는다. 수정 배포 후 consumer와 gate를 다시 함께 활성화하고 동일 eventId로 pending/dead-letter를 replay한다.
