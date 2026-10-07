# 화면 흐름 테스트

이 안내는 `local` 프로필의 주최자 화면 6개와 부스 운영자·방문객 흐름을 Swagger에서 직접 확인하는 순서다. 서버와 DB 실행은 [README](../README.md)를 따른다. 자동 검증은 `./gradlew test integrationTest`로 실행하며, 임시 PostgreSQL을 사용하므로 개발 DB를 변경하지 않는다.

## 공통 준비

1. `http://127.0.0.1:8080/swagger-ui/index.html`을 연다. 브라우저 주소를 `localhost`와 섞지 않는다.
2. `GET /api/auth/csrf`의 `token`을 복사해 변경 요청의 `X-CSRF-TOKEN` 헤더에 넣는다. 로그인 뒤에는 토큰을 다시 받는다.
3. 주최자 계정과 운영자 계정을 각각 회원가입한다. `onboardingType`은 화면 선택용이고 관리 권한을 만들지 않는다.
4. 주최자와 운영자는 서로 다른 브라우저 프로필이나 시크릿 창에서 로그인한다. 세션 쿠키가 섞이면 역할 테스트가 불가능하다.
5. 아래 `{organizationId}`, `{eventId}`, `{applicationId}` 등은 앞선 응답의 실제 UUID로 바꾼다. revision도 항상 최신 응답값을 쓴다.

## 주최자: 행사와 모집

1. 주최자로 `POST /api/dev/organizations`에 `{"name":"축제 운영팀","contactEmail":"owner@example.com"}`을 보내 테스트 조직을 만든다. 이 경로는 실제 기관 인증이 아닌 로컬 테스트 전용이다.
2. `POST /api/organizations/{organizationId}/events`로 행사를 만든다. 예: `{"name":"캠퍼스 축제","venue":"대운동장","startsAt":"2026-11-10T09:00:00+09:00","endsAt":"2026-11-11T20:00:00+09:00","description":"축제 소개"}`.
3. `GET /api/events/{eventId}`가 네비게이션에 필요한 행사명·조직·기간을 반환하는지 확인한다. `PATCH /api/events/{eventId}`에 `{"revision":0,"publicationStatus":"PUBLISHED"}`를 보내 공개한다.
4. `PUT /api/events/{eventId}/recruitment`에 다음 폼을 보낸다. `closesAt`은 실제 테스트 시점보다 미래여야 한다.

```json
{
  "revision": 0,
  "introduction": "축제 부스를 모집합니다.",
  "publicationStatus": "PUBLISHED",
  "closesAt": "2026-11-05T18:00:00+09:00",
  "targetBoothCount": 20,
  "allowedCategoryCodes": ["FOOD", "BEVERAGE"],
  "participationFeeKrw": 50000,
  "feeNote": "승인 후 납부 안내",
  "documentRequirements": [{"name":"운영계획서","isRequired":true,"applicableCategoryCodes":[],"sortOrder":0}]
}
```

5. `GET /api/public/events/{eventId}/recruitment`에서 공고를 확인한다. `GET /api/events/{eventId}/application-invitation`은 아직 `invitation:null`이다. 필요하면 `POST`로 마감 전 만료 시각을 보내 코드를 발급할 수 있다. 코드 원문은 발급 응답에서만 보인다.

## 운영자: 프로필과 신청

1. 운영자 세션에서 `POST /api/me/booth-profiles`에 `{"name":"달빛 분식","categoryCode":"FOOD","introduction":"간식 판매"}`를 보낸다.
2. 반환된 `id`를 사용해 `POST /api/events/{eventId}/applications`에 `{"boothProfileId":"{profileId}","applicantName":"김운영","applicantPhone":"01012345678","applicantEmail":"operator@example.com"}`을 보낸다. 초대받아 비공개 행사에 신청하는 경우에만 `invitationCode`를 추가한다.
3. `GET /api/me/applications`와 `GET /api/me/applications/{applicationId}`에서 `PENDING`과 서류 확인 상태를 본다. 내부 심사 메모는 운영자 응답에 포함되지 않는다.

## 주최자: 심사와 운영

1. `GET /api/events/{eventId}/applications?size=7`과 `/applications/summary`로 목록·요약을 확인한다. `/applications/{applicationId}`는 연락처와 서류 상태를 포함한다.
2. 서류 요구사항의 `id`로 `PATCH /api/events/{eventId}/applications/{applicationId}/document-checks/{requirementId}`에 `{"revision":0,"checkStatus":"VERIFIED","reviewNote":"외부 제출 확인"}`을 보낸다.
3. `POST /api/events/{eventId}/applications/{applicationId}/decision`에 `{"revision":0,"decision":"APPROVED","reviewNote":"승인"}`을 보낸다. 응답의 `eventBoothId`, `boothCode`를 기록한다. 같은 결정을 재전송해도 부스가 중복 생성되지 않아야 한다. 필수 서류를 확인하기 전 승인은 409다.
4. 직접 등록은 `POST /api/events/{eventId}/booths`에 `{"name":"안내소","categoryCode":"OTHER","description":"현장 안내"}`를 보낸다. 직접 등록 부스는 신청 통계에 포함되지 않는다.
5. `GET /api/events/{eventId}/booths`, `/operations`, `/dashboard`를 확인한다. 방문객에게 보이게 하려면 승인 부스에 `PATCH /api/events/{eventId}/booths/{boothId}`로 `{"revision":0,"visibilityStatus":"PUBLIC"}`를 보낸다.
6. 운영자 세션에서 `GET /api/me/booths`를 확인한 뒤 `PATCH /api/events/{eventId}/booths/{boothId}/operation-status`에 최신 revision과 `operationStatus:"OPEN"`을 보낸다. `POST /items`에 상품명·가격·`stockStatus`를 넣으면 운영 현황의 `stockSummary`가 바뀐다.

## 지도·공지·방문객

1. 주최자가 `POST /api/events/{eventId}/media-assets`에 PNG/JPEG `file`을 업로드한다. 반환된 `mediaAssetId`로 `POST /api/events/{eventId}/floor-plans`에 `{"mediaAssetId":"{mediaAssetId}"}`를 보낸다.
2. `PUT /api/events/{eventId}/floor-plans/{floorPlanId}`에 핀 전체 배열을 저장한다. 부스 핀은 `eventBoothId`가 필요하고, 시설 핀은 없어야 한다. 새 핀마다 UUID를 지정한다.

```json
{
  "revision": 0,
  "pins": [
    {"id":"11111111-1111-4111-8111-111111111111","pinType":"BOOTH","label":"B001","xRatio":0.25,"yRatio":0.4,"eventBoothId":"{boothId}"},
    {"id":"22222222-2222-4222-8222-222222222222","pinType":"TOILET","label":"화장실","xRatio":0.8,"yRatio":0.2}
  ]
}
```

3. `GET /api/events/{eventId}/map-editor`는 초안을 보여주지만 방문객 `GET /api/public/events/{eventId}/map`은 아직 404다. `POST /api/events/{eventId}/floor-plans/{floorPlanId}/publication`에 저장 후 revision(`1`)을 보내 게시한다.
4. 주최자가 `POST /api/events/{eventId}/announcements`에 `{"title":"운영 안내","body":"행사가 시작됩니다.","audiences":["PUBLIC"],"isUrgent":true,"publicationStatus":"PUBLISHED"}`를 보낸다.
5. 비로그인 창에서 `/api/public/events/{eventId}`, `/booths`, `/booths/{boothId}`, `/map`, `/announcements`를 조회한다. 공개 부스 상세에는 상품·게시 지도 위치가 있으나 신청자 연락처·심사 메모는 없어야 한다.

## 경계와 초기화

- 주최자 외 계정의 심사·지도 수정은 거부된다. 운영자는 자신에게 승인된 부스만 수정한다. 모든 변경 요청은 CSRF 토큰이 필요하다.
- 방문객은 초안 지도, 비공개 부스, 내부 공지, 신청 정보에 접근할 수 없다. 공개 부스 목록에 `floorPlanId`를 지정하면 400이다.
- `DELETE /api/dev/auth-data`는 **행사 데이터가 있는 DB에서는 409**로 거절한다. 업무 데이터를 지우지 않은 채 계정만 삭제하지 않는다.
- 자동 테스트는 별도 임시 DB에서 돌아간다. 개발 DB를 새로 시작해야 하는 경우 볼륨 삭제가 데이터를 영구 제거한다는 점을 먼저 확인한다.
- 실제 서류 파일·결제·통계·알림톡과 `deploy` 프로필의 업무 API는 아직 테스트 범위 밖이다.
