# 화면 기준 API 초안

2026-10-01 초안, 2026-10-05 구현 상태 갱신. 제공된 주최자 화면 6장과 가입·로그인 요구를 기준으로 작성했다.
**아래 내용은 기능별 목표 계약이다.** 행사·모집·신청·부스·운영·지도·공지의 핵심 HTTP 경로는 현재 `local` 프로필에서 호출할 수 있다.
실행 순서와 검증 범위는 [화면 흐름 테스트 안내](WORKFLOW_TEST.md), 인증 세부 사항은 [인증 실행 안내](AUTH_START.md)를 참고한다.
V3 migration은 [여기](../src/main/resources/db/migration/V3__event_operations.sql), 검토용 전체 설계는 [보관 ERD](archive/erd-lab/SCHEMA.md)에 있다.
`deploy` 프로필에는 이 업무 API가 열려 있지 않다. 전화번호 인증·실제 서류 업로드/다운로드·결제·통계·알림톡도 이 계약의 범위 밖이다.

## 1. API를 나누는 기준

컴포넌트 개수나 DB 테이블 개수와 API 개수를 맞추지 않는다. 다음 기준으로 묶는다.

1. 같이 읽고 같이 갱신하는 데이터는 묶는다. 네비게이션의 행사명·조직·기간은 행사 조회 한 번으로 받는다.
2. 목록과 상세를 나눈다. 부스 목록은 7개를 한 번에 받고, 선택한 신청의 연락처·서류 상태는 상세 조회에서 받는다.
3. 권한이 다르면 응답을 나눈다. 방문객용 부스 조회에는 신청자 연락처·내부 메모를 포함하지 않는다.
4. 저장 단위는 한 작업의 성공/실패 단위다. 승인은 심사 상태·운영 부스·운영자 권한을 서버 트랜잭션 하나로 처리한다.
5. 반복 갱신 주기가 같으면 조회를 묶어도 된다. 운영 현황은 목록과 상태별 개수를 한 번에 갱신한다.

프론트의 `.map()`은 응답 배열을 화면에 반복 출력하는 데 쓴다. 목록 7개의 ID를 받은 뒤 각 행마다 별도 요청을 보내면 최초 요청을 포함해 8번 호출하게 된다.
서버는 필요한 테이블을 조인하고 목록에 필요한 필드만 담은 응답 DTO를 만든다. 프론트가 연락처를 숨기더라도 응답에 실려 있으면 이미 전달된 정보다.

| 화면 작업 | 권장 요청 단위 |
| --- | --- |
| 공통 네비게이션 | `GET /events/{eventId}` 결과를 페이지 간 재사용 |
| 대시보드 | 행사 정보 재사용 + `GET /events/{eventId}/dashboard` |
| 부스 관리 진입 | 신청 목록 + 신청 전체 요약. 선택 전 상세는 조회하지 않음 |
| 신청 '보기' | 상세 한 번으로 신청 정보·서류 상태 탭 데이터 제공 |
| 운영 현황 | 목록·집계를 함께 폴링, 선택한 부스만 별도 상세 조회 |
| 지도 편집 | 편집 데이터 조회 → 프론트에서 드래그 → 저장 시 핀 집합 한 번 전송 |
| 방문객 지도 | 게시본 조회 → 핀 클릭 시 공개 부스 상세 조회 |
| 공지 | 가벼운 목록 → 선택한 공지 상세 → 저장/게시 |

기준 참고: [Microsoft의 API 설계 지침](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design)은 작은 요청의 과도한 분할과 불필요하게 큰 응답을 함께 피하도록 설명한다. 위 요청 단위는 이 프로젝트 화면에 맞춘 제안이다.

## 2. 공통 계약

- 모든 경로 앞에 `/api`를 붙인다. 본문의 `eventId`, `applicationId` 등은 서로 다른 자원의 UUID다.
- JSON은 camelCase, 날짜는 UTC 또는 오프셋이 있는 ISO 8601이다. 예: `2026-09-22T09:00:00+09:00`. DB에는 `timestamptz`로 저장한다.
- 생성은 201, 조회·갱신은 200, 로그아웃·삭제는 204를 기본값으로 한다. 생성 응답에는 생성 ID를 포함한다.
- 페이지는 0부터 시작한다. `size`는 1~100, 검색어 `q`는 최대 100자다. 유효하지 않은 조건은 400, 결과가 없거나 마지막 페이지를 넘으면 빈 `content`와 실제 전체 개수를 반환한다.
- 정렬 필드는 허용 목록으로 검증하고 항상 ID를 마지막 정렬 기준으로 추가한다. 검색은 서버에서 처리한다. `q`의 `%`, `_`는 사용자 와일드카드로 해석하지 않는다.
- 예시 ID `event-1`, `application-1` 등은 읽기 쉬운 표시용 값이다. 실제 요청에는 UUID를 쓴다. 아래 JSON은 계약 예시이며 DB에 넣을 시드 데이터가 아니다.
- `revision`이 있는 자원은 수정 요청에 현재 revision을 보내고 서버가 비교한다. 성공 시 revision과 updatedAt을 갱신한다. 충돌은 409다. 클라이언트가 서버 소유 상태·작성자·검토자 필드를 임의로 지정할 수 없다.
- 문자열 길이 기본 상한: 이름·닉네임 100자, 제목 200자, 소개·본문 10,000자, 메모·반려 사유 2,000자. 요청 DTO에서 검증한다.

권한 약칭: `M`은 유효한 조직 OWNER 또는 해당 행사의 MANAGER, `S`는 유효한 행사 STAFF,
`O`는 해당 부스의 유효 운영자 소속, `A`는 로그인한 활성 계정, `P`는 비회원이다.
행사 소속이 있을 때 조직 소속도 유효한지 확인한다. 모든 요청에서 자원 소속과 현재 권한을 서버가 확인한다.
닉네임·기관명·가입 화면 선택은 권한 증명이 아니다. 조직 MEMBER는 행사 MANAGER 위임 없이 관리 권한을 얻지 않는다.

| 오류 | 용도 |
| --- | --- |
| 400 `VALIDATION_ERROR` | 필수값, 코드, 좌표, 페이지, 허용하지 않은 필드 |
| 401 `AUTHENTICATION_REQUIRED` | 로그인 필요/만료 |
| 403 `FORBIDDEN` | 접근 가능한 행사 안에서 해당 작업 권한 없음 |
| 404 `RESOURCE_NOT_FOUND` | 없는 자원 또는 다른 행사·다른 조직의 비공개 자원 |
| 409 `REVISION_CONFLICT` | 오래된 편집 내용으로 수정 |
| 409 `DUPLICATE_APPLICATION`, `BOOTH_CODE_TAKEN`, `INVALID_STATE` | 중복/상태 충돌 |
| 409 `RECRUITMENT_CLOSED`, `DOCUMENTS_NOT_VERIFIED`, `RECRUITMENT_TERMS_LOCKED` | 업무 조건 위반 |
| 413 `FILE_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE` | 이미지 크기/형식 오류 |
| 429 `RATE_LIMITED` | 로그인·가입·초대 코드 시도 제한 |

```json
{
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [{"field": "nickname", "message": "닉네임을 입력해 주세요."}],
  "requestId": "request-id"
}
```

## 3. 가입·로그인·행사 선택

주최자와 운영자는 같은 계정 테이블과 로그인 API를 사용한다. 회원가입은 개인 계정·로그인 정보만 만든다.
`onboardingType`은 가입 후 화면 선택용이며 조직 소속이나 관리 권한을 부여하지 않는다. 한 계정이 행사별로 주최자와 운영자 역할을 함께 가질 수 있다.
조직 생성 신청·검증과 기존 조직 초대 수락은 별도 후속 기능이다. 아래 조직 생성 계약도 소유권 검증 정책 확정 후 다시 검토해야 한다.

| 메서드·경로 | 요청 / 주요 응답 | 권한 |
| --- | --- | --- |
| `GET /auth/csrf` | CSRF 토큰과 보낼 헤더명 | P |
| `GET /auth/login-id-availability?loginId=...` | `available` 반환. 가입 시 중복을 다시 검사 | P |
| `POST /auth/sign-up` | 아래 가입 폼 → `accountId`. 조직·소속은 생성하지 않음 | P |
| `POST /auth/login` | `loginId`, `password` → 세션 쿠키 설정, 계정 `id`, `nickname` | P |
| `POST /auth/logout` | 세션 무효화 → 204 | A |
| `GET /me` | `id`, `nickname`, 본인 `phoneNumber`, `email` | A |
| `GET /me/organizations` | 유효 소속의 조직 `id`, `name`, `role` 목록 | A |
| `POST /organizations` | `name`, 선택 `contactEmail` → 새 조직 및 본인 OWNER 소속 | A |
| `GET /me/events?participation=ORGANIZER&page=0&size=20` | 권한 있는 행사 요약 목록. `OPERATOR`이면 본인 운영 부스가 있는 행사 | A |
| `POST /organizations/{organizationId}/events` | `name`, 선택 `venue`, `startsAt`, `endsAt`, `description` → 행사 ID·revision | 조직 OWNER |
| `GET /events/{eventId}` | 아래 행사 공통 응답 | M/S/O |
| `PATCH /events/{eventId}` | `revision` 및 수정할 `name`, `venue`, `startsAt`, `endsAt`, `description`, `posterAssetId`, `publicationStatus` | M |

행사 생성 시 `event_recruitments`의 빈 DRAFT 행도 함께 만든다. `/me/events`의 조직 행사 범위는 OWNER 또는 실제 행사 위임을 기준으로 한다.
O의 행사 조회는 본인의 유효 운영 부스가 있는 행사에 한정한다. 신청 전 행사는 공개 모집 조회 또는 유효 초대 링크로 확인한다.

```json
{
  "onboardingType": "ORGANIZER",
  "loginId": "organizer01",
  "password": "<사용자 입력 비밀번호>",
  "nickname": "행사담당",
  "phoneNumber": "01000000000",
  "email": "organizer@example.com"
}
```

운영자 화면을 선택하려면 `onboardingType=OPERATOR`로 바꾼다. 두 유형 모두 조직 이름을 받지 않는다.
`organizationName`, `organizationId`, `role` 등 미정의 필드는 400으로 거절한다.
로그인 ID는 소문자 영문·숫자·밑줄 4~30자다. 비밀번호 해시는 서버에서 만들고 원문을 응답·로그·DB에 남기지 않는다.
이메일은 필수 연락처지만 입력만으로 인증 완료 처리하지 않는다. 이메일 확인/비밀번호 재설정 API는 이번 초안에 포함하지 않는다.

현재 인증 경로는 local 프로필 전용이다. `POST /auth/sign-up`은 앱 DB에 개인 계정과 로그인 정보만 저장한다.
`GET /me`는 DB의 본인 `id`, `nickname`, `phoneNumber`, `email`을 반환한다. 전화번호 인증은 아직 없다.
이 절의 행사/조직 추가 API는 후속 구현 범위다. 현재 인증 오류 응답은 `code`, `message`만 반환하며 공통 오류 계약은 후속 범위다.

인증은 기존 Spring Security 방향에 맞춰 세션 쿠키 방식을 제안한다. 변경 요청 전 CSRF 토큰을 받고 헤더로 보내며 로그인 후 토큰을 다시 받는다.
로그인 성공 시 세션 ID를 교체하고, 배포 쿠키는 HttpOnly·Secure를 적용한다. FE/BE가 다른 출처면 credentials와 허용 origin, 쿠키 사이트 정책을 배포 구조에 맞춰 함께 설정한다.

행사 공통 응답 예시:

```json
{
  "id": "event-1",
  "name": "비룡제 2026",
  "organization": {"id": "organization-1", "name": "총학생회"},
  "venue": "인하대학교 대운동장",
  "startsAt": "2026-09-22T09:00:00+09:00",
  "endsAt": "2026-09-24T22:00:00+09:00",
  "scheduleStatus": "ENDED",
  "publicationStatus": "PUBLISHED",
  "description": "축제 소개",
  "posterUrl": null,
  "revision": 2,
  "capabilities": ["MANAGE_EVENT", "REVIEW_APPLICATIONS", "EDIT_MAP"]
}
```

네비게이션과 대시보드는 이 데이터를 공유한다. 행사 편집 뒤 캐시를 갱신한다.
진행 상태는 서버 시각으로 계산하므로 시작·종료 경계나 화면 재진입에도 다시 조회한다. 위 날짜 예시는 작성일 기준 이미 종료된 행사다.

## 4. 대시보드·초대

| 메서드·경로 | 응답 / 동작 | 권한 |
| --- | --- | --- |
| `GET /events/{eventId}/dashboard` | `boothSummary`, 게시 공지 최대 3개 `recentAnnouncements`, `asOf` | M/S |
| `GET /events/{eventId}/application-invitation` | 현재 초대 `id`, `createdAt`, `expiresAt`, 계산한 `active`; 없으면 `invitation:null` | M |
| `POST /events/{eventId}/application-invitation` | `expiresAt` → 기존 초대 폐기 후 새 초대, 원문 `code`, `inviteUrl` 한 번 반환 | M |
| `DELETE /events/{eventId}/application-invitation` | 현재 초대 폐기. 이미 폐기/없음도 204 | M |
| `POST /application-invitations/resolve` | `code` → 신청에 필요한 행사·모집 공개용 정보 | P |

초대 코드는 암호학적 난수로 생성하고 DB에는 해시만 둔다. 재발급은 행사 행 잠금 안에서 기존 초대 폐기와 신규 생성을 처리한다.
발급 응답은 `Cache-Control: no-store`, 코드 원문은 로그에 남기지 않는다. 같은 코드로 여러 운영자가 각각 신청할 수 있다.
초대는 모집 공고가 PUBLISHED인 비보관 행사에서만 사용하고, `expiresAt`은 모집 마감 시각 이하여야 한다.
resolve와 실제 신청 모두 만료·폐기·모집 마감을 검사한다. resolve는 민감한 조직 내부 정보나 기존 신청 목록을 반환하지 않는다.

현재 화면처럼 기존 코드·링크를 매번 그대로 보여주는 것은 해시 저장과 양립하지 않는다. 발급 직후 복사, 이후 재발급 방식으로 화면을 조정하는 기본안이다.
`inviteUrl`은 프론트 가입/신청 화면으로 연결하며, 링크 방문 자체로 신청을 생성하거나 승인하지 않는다.

## 5. 부스 모집·운영자 신청

| 메서드·경로 | 요청 / 응답 | 권한 |
| --- | --- | --- |
| `GET /events/{eventId}/recruitment` | 모집 편집 필드·서류 요구사항·revision·계산한 모집 상태 | M |
| `PUT /events/{eventId}/recruitment` | 아래 폼 전체 + revision → 저장된 모집 정보 | M |
| `GET /public/events?page=0&size=20&recruitmentStatus=OPEN` | 게시된 행사 중 조건에 맞는 행사 요약 목록 | P |
| `GET /public/events/{eventId}/recruitment` | 공개 행사·공고의 소개, 마감일, 목표 수, 카테고리, 참가비, 요구 서류 | P |
| `GET /me/booth-profiles?page=0&size=20` | 본인 프로필 목록 | A |
| `POST /me/booth-profiles` | `name`, `categoryCode`, `introduction` → 프로필 ID·revision | A |
| `PATCH /me/booth-profiles/{boothProfileId}` | `revision`과 변경할 이름·카테고리·소개 | 소유자 |
| `POST /events/{eventId}/applications` | `boothProfileId`, `applicantName`, `applicantPhone`, `applicantEmail`, 선택 `invitationCode` | A |
| `GET /me/applications?page=0&size=20` | 본인 신청의 행사·부스명·심사 상태·신청 시각·반려 사유 | A |
| `GET /me/applications/{applicationId}` | 본인 신청 내용·서류 확인 상태·반려 사유, 내부 reviewNote 제외 | 신청자 |

모집 저장 예시:

```json
{
  "revision": 0,
  "introduction": "행사에 참여할 부스를 모집합니다.",
  "publicationStatus": "PUBLISHED",
  "closesAt": "2026-10-05T18:00:00+09:00",
  "targetBoothCount": 20,
  "allowedCategoryCodes": ["FOOD", "BEVERAGE", "EXPERIENCE", "GOODS"],
  "participationFeeKrw": 50000,
  "feeNote": "부스별 납부 안내",
  "documentRequirements": [
    {"name": "운영계획서", "isRequired": true, "applicableCategoryCodes": [], "sortOrder": 0}
  ]
}
```

새 서류 요구사항의 ID는 서버가 생성한다. 기존 요구사항은 반환된 `id`를 유지해 보낸다.
대표 이미지·장소·행사 기간은 행사 데이터가 원본이므로 모집 저장 요청에 중복으로 넣지 않는다.
모집 첫 신청 후에는 카테고리·참가비·서류 조건을 고정한다. 신청 제출과 조건 수정 모두 같은 행사 행을 잠가 접수 중 변경을 막는다.
공개 목록에는 행사 PUBLISHED와 모집 PUBLISHED 조건이 필요하다. 유효 초대는 행사 DRAFT도 최소 신청 정보로 접근하게 하지만 행사 ARCHIVED는 거절한다.

신청 시 본인 프로필 소유권, 선택 카테고리, 마감, 모집 게시 상태를 확인하고 신청 스냅샷과 적용 서류 확인 행을 함께 만든다.
닉네임을 신청자 이름으로 자동 확정하지 않는다. 신청 폼에서 실제 연락 담당자 이름을 받는다.
동일 행사·프로필에 PENDING/APPROVED 신청이 있으면 409다. 반려 후 새 신청은 새 ID로 만들고 예전 기록을 유지한다.
`targetBoothCount`는 안내 목표이며 자동 마감 기준으로 사용하지 않는다. 참가비는 안내만 하며 PG 결제 API는 없다.

## 6. 부스 관리: 목록·상세·심사

| 메서드·경로 | 요청 / 응답 | 권한 |
| --- | --- | --- |
| `GET /events/{eventId}/applications` | 아래 페이지 조회. 기본 `size=7` | M |
| `GET /events/{eventId}/applications/summary` | 행사 전체 `total`, `pending`, `approved`, `rejected` | M |
| `GET /events/{eventId}/applications/{applicationId}` | 신청 스냅샷, 연락처, 심사 결과, 서류 확인 상태, reviewNote, revision | M |
| `PATCH /events/{eventId}/applications/{applicationId}/review-note` | `revision`, `reviewNote` → 갱신된 revision | M |
| `POST /events/{eventId}/applications/{applicationId}/decision` | `revision`, `decision`, 선택 `boothCode`, `rejectionReason`, `reviewNote` | M |
| `PATCH /events/{eventId}/applications/{applicationId}/document-checks/{requirementId}` | `revision`, `checkStatus`, 선택 `reviewNote` | M |
| `POST /events/{eventId}/booths` | 직접 등록: `name`, `categoryCode`, `description`, 선택 `boothCode` | M |

```http
GET /api/events/{eventId}/applications?page=0&size=7&q=떡볶이&categoryCode=FOOD&reviewStatus=PENDING&sort=submittedAt,desc
```

목록은 부스명 부분 검색이며 정렬 허용값은 `submittedAt,desc`, `submittedAt,asc`다. 마지막에 동일 방향 ID 정렬을 붙인다.

```json
{
  "content": [
    {
      "applicationId": "application-1",
      "boothName": "달빛 떡볶이",
      "submittedAt": "2026-10-01T09:00:00+09:00",
      "categoryCode": "FOOD",
      "applicantName": "신청 담당자",
      "reviewStatus": "PENDING",
      "eventBoothId": null,
      "boothCode": null
    }
  ],
  "page": 0,
  "size": 7,
  "totalElements": 1,
  "totalPages": 1
}
```

위 예시는 검색 결과 1개다. `totalElements`는 필터에 맞는 전체 신청 수이고, 상단 요약은 검색·필터와 무관한 행사 전체 신청 수다.
최초 진입 시 목록+요약 두 요청, 선택 시 상세 한 요청으로 충분하다. 페이지/검색/필터가 바뀌면 기존 선택 상세는 닫는 기본안이다.

상세 응답 구조:

```json
{
  "applicationId": "application-1",
  "revision": 0,
  "submittedAt": "2026-10-01T09:00:00+09:00",
  "reviewStatus": "PENDING",
  "booth": {"name": "달빛 떡볶이", "categoryCode": "FOOD", "introduction": "부스 소개"},
  "applicant": {"name": "신청 담당자", "phoneNumber": "01000000000", "email": "operator@example.com"},
  "documents": [{"requirementId": "requirement-1", "name": "운영계획서", "isRequired": true, "checkStatus": "NOT_SUBMITTED", "revision": 0}],
  "reviewNote": null,
  "rejectionReason": null,
  "reviewedAt": null,
  "eventBoothId": null,
  "boothCode": null
}
```

승인은 `decision=APPROVED`, 반려는 `decision=REJECTED`와 비어 있지 않은 `rejectionReason`이 필요하다.
심사자·심사 시각은 서버가 채운다. 외부 제출된 필수 서류의 `VERIFIED`를 확인한 후 승인한다.
서류는 외부 제출/검토 상태만 기록하며 원본 다운로드 URL을 반환하지 않는다. 담당자 메모에 서류 원문·건강 정보를 옮겨 적지 않는다.

승인은 행사와 신청을 정해진 순서로 잠근 뒤 상태 변경, 운영 부스, 신청자의 부스 소속, 감사 기록을 한 트랜잭션으로 만든다.
성공 응답은 `applicationId`, `reviewStatus`, `eventBoothId`, `boothCode`, `revision`이다.
같은 결정을 재전송하면 동일 결과를 반환하고 부스를 중복 생성하지 않는다. 승인 이후 반려 등 다른 결정은 이번 MVP에서 409다.
이미 처리된 요청의 재시도는 상태·결정 파라미터를 먼저 비교하고, 처리 전 수정은 revision을 비교한다.

부스 번호는 수동 지정하면 행사 내 중복을 검사한다. 생략 시 서버가 행사 행 잠금 안에서 `B001` 형식의 미사용 번호를 배정하는 안이다.
번호와 지도 핀 라벨은 별개다. 직접 등록도 번호를 생성하지만 신청 행/신청 통계는 늘리지 않는다.
직접 등록 부스는 초기 운영자 소속이 없고 주최자가 관리한다. UI의 '승인·직접등록 부스'에서 `/booths`로 함께 조회한다.

## 7. 지도 제작·이미지·공개 지도

| 메서드·경로 | 요청 / 응답 | 권한 |
| --- | --- | --- |
| `POST /events/{eventId}/media-assets` | multipart `file` → `mediaAssetId`, `widthPx`, `heightPx`, `readUrl`, `expiresAt` | M |
| `GET /events/{eventId}/map-editor` | `publishedMap` 요약, `draftMap`과 핀·revision, 없으면 null | M |
| `POST /events/{eventId}/floor-plans` | `mediaAssetId` 또는 `sourceFloorPlanId` 중 하나 → 새 DRAFT 지도 | M |
| `PUT /events/{eventId}/floor-plans/{floorPlanId}` | 아래 초안의 전체 핀 집합·revision → 저장된 지도·새 revision | M |
| `DELETE /events/{eventId}/floor-plans/{floorPlanId}?revision=0` | 초안만 삭제·핀 정리. 게시/보관 지도는 409 | M |
| `POST /events/{eventId}/floor-plans/{floorPlanId}/publication` | `revision` → 기존 게시본 보관 후 새 지도 게시 | M |
| `GET /events/{eventId}/booths?floorPlanId={id}&placementStatus=UNASSIGNED&page=0&size=20` | 배치할 승인/직접 등록 부스 목록 | M |
| `GET /public/events/{eventId}/map` | 게시된 지도 이미지·핀·최소 부스 요약 | P |

이미지는 PNG/JPEG 한 장, 최대 10 MiB다. 파일 내용과 MIME을 서버가 검사하고 이미지 해상도를 읽는다.
서버가 객체 키를 생성하고 업로드 완료된 이미지만 DB에 기록한다. 클라이언트가 bucket·objectKey·행사 외부 이미지 ID를 임의 지정할 수 없다.
지도·대표 이미지·공지 이미지는 이 업로드 API를 재사용한다. 읽기 URL은 권한 확인 후 발급하며 실제 Storage 연결은 아직 구현되지 않았다.

지도 PUT 예시:

```json
{
  "revision": 3,
  "pins": [
    {"id": "pin-1", "pinType": "BOOTH", "label": "1", "xRatio": 0.25, "yRatio": 0.4, "eventBoothId": "booth-1"},
    {"id": "pin-2", "pinType": "TOILET", "label": "화장실", "xRatio": 0.08, "yRatio": 0.15, "eventBoothId": null}
  ]
}
```

핀 ID는 프론트가 새 핀 생성 때 UUID로 만들고 저장 이후에도 유지한다. 기존 ID는 같은 지도 소속인지 서버가 검사한다.
PUT은 해당 초안의 전체 핀 집합을 교체한다. 누락한 기존 핀은 삭제되므로 부분 배열을 보내면 안 된다. 최대 500개다.
핀 드래그/할당/삭제는 저장 전까지 프론트 편집 상태다. 저장 실패 시 화면 상태를 유지하고, revision 충돌이면 최신본 재조회 여부를 안내한다.
원자적으로 검증·저장하며 모든 핀의 지도/행사 일치, 부스 중복 배치, 좌표 범위, 시설-부스 연결 금지를 검사한다.
서로 다른 핀의 부스를 맞바꾸는 저장도 가능하도록 갱신 중간 단계의 유일성 충돌을 고려한다.

새 이미지에는 새 초안을 만들고 핀을 다시 놓는다. `sourceFloorPlanId`로 같은 행사의 게시 지도를 복제하면 이미지를 재사용하고 핀에는 새 ID를 발급한다.
초안은 행사당 하나다. 기존 초안이 있으면 409로 알려 편집하거나 명시적으로 폐기하게 한다.
지도 저장은 공개 지도에 영향을 주지 않는다. '미리보기'는 프론트의 편집 상태로 표시하고, 별도 '게시' 작업에서 공개본을 바꾼다.
게시 변경은 행사 잠금 아래 이전 게시본을 ARCHIVED로 만든 뒤 초안을 PUBLISHED로 바꾸고 전체를 커밋한다.

공개 지도는 행사 PUBLISHED인 경우만 제공한다. 게시 지도가 없으면 404 `MAP_NOT_PUBLISHED`다.
이미지와 `floorPlanId`, `versionNo`, `revision`, 핀의 ID·종류·라벨·좌표를 반환한다.
부스 핀에는 `eventBoothId`, `boothCode`, `boothName`, `operationStatus`를 추가하고 소개·상품은 클릭 후 공개 부스 상세로 받는다.
미할당 부스 핀, HIDDEN/ARCHIVED 부스 핀은 방문객에게 제외한다. 시설 핀은 표시한다.

## 8. 운영 현황·운영자 수정

| 메서드·경로 | 요청 / 응답 | 권한 |
| --- | --- | --- |
| `GET /events/{eventId}/operations` | `summary`, 페이지 형태 `booths`, `asOf` | M/S |
| `GET /events/{eventId}/booths` | 승인/직접 등록 부스의 페이지 목록 | M/S |
| `GET /events/{eventId}/booths/{boothId}` | 부스 소개·상태·재고 요약·상품·게시 지도 위치·revision | M/S/본인 O |
| `PATCH /events/{eventId}/booths/{boothId}` | `revision`, `name`, `categoryCode`, `description`; `visibilityStatus`는 M만 | M/본인 O |
| `PATCH /events/{eventId}/booths/{boothId}/operation-status` | `revision`, `operationStatus` | M/본인 O |
| `POST /events/{eventId}/booths/{boothId}/items` | `name`, `description`, `priceKrw`, `stockStatus`, `sortOrder` | M/본인 O |
| `PATCH /events/{eventId}/booths/{boothId}/items/{itemId}` | `revision`과 변경할 상품 필드. 재고만 변경해도 같은 API 사용 | M/본인 O |
| `DELETE /events/{eventId}/booths/{boothId}/items/{itemId}?revision=0` | 해당 상품 삭제 | M/본인 O |
| `GET /me/booths?page=0&size=20` | 본인 소속 부스와 행사 요약 | A |

운영 현황 기본 `page=0&size=8`. `q`, `categoryCode`, `operationStatus`, `stockSummary`, `placementStatus` 필터를 지원한다.
정렬은 `boothCode,asc` 또는 `boothCode,desc`와 ID 순서다. `/booths`도 같은 필터를 지원한다.
배치용 조회의 `floorPlanId`는 M만 지정할 수 있고 해당 행사 지도인지 검사한다. 생략 시 게시 지도를 사용한다.
운영 현황은 항상 게시 지도 기준이며 초안 선택 파라미터를 받지 않는다.

각 행은 `eventBoothId`, `boothCode`, `name`, `categoryCode`, `operationStatus`, `stockSummary`, `placementStatus`, `lastChangedAt`을 반환한다.
`summary`는 보관 부스를 제외한 행사 전체의 `total`, `preparing`, `open`, `soldOut`, `closed`다. 필터와 무관하고 다섯 상태 카드에 재사용한다.
`booths.totalElements`만 현재 필터 기준이다. 목록 필터와 표시의 재고 요약은 [이전 DB 설계의 집계 규칙](archive/erd-lab/SCHEMA.md)을 참고해 구현 시 다시 확정한다.
같은 응답 안의 요약·목록은 일관된 DB 스냅샷으로 읽는다. 한 SQL 또는 읽기 전용 REPEATABLE READ 트랜잭션으로 구현하는 안이다.

부스 상세의 지도 위치는 `mapPlacement:null` 또는 지도 이미지·지도 ID·해당 핀 좌표다. 전체 핀을 다시 내려주지 않는다.
상품이 없는 부스는 `stockSummary=NOT_TRACKED`이며 '재고 없음/품절'로 표시하지 않는다. 상품은 부스당 최대 100개인 MVP 제한을 둔다.
부스 운영 상태와 상품 재고는 수동으로 각각 바꾸며 한쪽이 다른 쪽을 자동 변경하지 않는다.

폴링 기본안은 활성 화면에서 5초 간격이다. 이전 요청이 끝나기 전에 중복 요청하지 않고 탭 비활성 시 멈춘다.
수동 새로고침도 같은 API를 사용한다. 선택 상세는 열려 있을 때만 갱신한다. 현재 초안에 WebSocket/SSE는 없다.

## 9. 공지 작성·게시

| 메서드·경로 | 요청 / 응답 | 권한 |
| --- | --- | --- |
| `GET /events/{eventId}/announcements?view=MANAGE&page=0&size=20` | 제목·대상·긴급·게시 상태·시각 목록, 선택 `q`, `audience` 필터 | M |
| `GET /events/{eventId}/announcements?view=READ&page=0&size=20` | 본인 대상의 게시된 공지만. 기본 view=READ | M/S/O |
| `GET /events/{eventId}/announcements/{announcementId}` | 제목·본문·이미지 URL·대상·긴급·게시 상태·revision | M 또는 게시된 공지의 유효 독자 |
| `POST /events/{eventId}/announcements` | 아래 작성 필드 → 새 공지 | M |
| `PUT /events/{eventId}/announcements/{announcementId}` | 전체 작성 필드 + `revision` → 저장·게시 | M |
| `DELETE /events/{eventId}/announcements/{announcementId}?revision=0` | DELETED로 변경 → 204 | M |

```json
{
  "revision": 2,
  "title": "기상 악화에 따른 운영 안내",
  "body": "운영 안내 본문",
  "imageAssetId": null,
  "audiences": ["STAFF", "OPERATORS", "PUBLIC"],
  "isUrgent": true,
  "publicationStatus": "PUBLISHED"
}
```

신규 POST에는 revision을 보내지 않는다. 이미지 최대 한 장, 제목·본문은 일반 텍스트로 시작한다.
DRAFT는 빈 제목·본문·대상도 저장 가능하다. 게시에는 제목·본문과 대상 하나 이상이 필요하다. 게시 시각은 서버가 채우며 게시 후 수정에도 최초 게시 시각을 유지한다.
게시된 공지의 PUT은 즉시 반영된다. 삭제된 공지는 이번 MVP에서 복원하지 않는다.
목록은 긴급 우선, 게시 시각/작성 시각 내림차순, ID 내림차순이다. 일반 화면과 대시보드도 같은 순서를 사용한다.
대상은 합집합이다. PUBLIC이면 누구나, OPERATORS는 해당 행사 부스 운영자, STAFF는 해당 행사 스태프에게 읽기를 허용한다. M은 관리 목적으로 모두 조회한다.
읽기 대상 필터는 권한 검사 이후의 추가 조건이다. 클라이언트가 `audience=STAFF`를 보내도 스태프 권한을 얻지 않는다.

## 10. 방문객 조회 경계

| 메서드·경로 | 응답 |
| --- | --- |
| `GET /public/events/{eventId}` | 게시 행사명·주최 조직명·소개·기간·장소·대표 이미지·진행 상태 |
| `GET /public/events/{eventId}/booths?page=0&size=20` | PUBLIC 부스만. 부스명 검색, categoryCode 필터, 번호·운영 상태·재고 요약 |
| `GET /public/events/{eventId}/booths/{boothId}` | 공개 소개·상품·운영 상태·재고 요약·게시 지도 위치 |
| `GET /public/events/{eventId}/map` | 7절의 게시 지도 |
| `GET /public/events/{eventId}/announcements?page=0&size=20` | 게시된 PUBLIC 대상 공지 목록 |
| `GET /public/events/{eventId}/announcements/{announcementId}` | 게시된 PUBLIC 대상 공지 상세 |

모든 방문객 조회는 행사 PUBLISHED가 전제다. 초안·내부 대상 공지를 직접 ID로 요청해도 404다.
목록에 없던 부스 상세도 동일한 공개 조건을 다시 검사한다. 신청 연락처·서류 상태·로그인 ID·내부 메모·감사 로그는 공개 응답에서 제외한다.

## 11. 구현 전 팀에서 확인할 UI 차이

| 쟁점 | 이번 초안의 기본안 |
| --- | --- |
| 가입의 운영기관이 기존 조직인가? | 가입에서는 조직을 받지 않음. 조직 생성 신청·검증과 기존 조직 초대는 별도 절차 |
| 서류 PDF '보기' | 기존 범위대로 원본 없이 외부 제출·확인 상태 표시 |
| 초대 코드 상시 표시 | 발급 때만 원문 반환. 재발급 UI 사용 |
| 지도 '저장하기'가 즉시 공개인가? | 초안 저장과 게시를 분리하고 게시 버튼 추가 |
| 직접 등록 부스의 목록 위치 | 신청 통계에서 제외, 승인·직접등록 부스 목록에 표시 |
| 모집 부스 수가 자동 정원인가? | 목표 수 표시. 자동 승인 차단 없음 |
| 내부 메모/반려 사유가 입력 칸 하나인가? | 입력 필드를 나누고 반려 사유만 신청자에게 노출 |

이 기본안으로 DB와 API 초안을 맞췄다. 기존 결정과 달라지는 정책은 팀에서 검토해야 한다.
부스 운영자·방문객 전체 화면은 아직 제공되지 않았으므로 해당 API는 주최자 흐름이 성립하는 최소 범위다.
정산·PG·알림톡·후원·사용자 분석 API는 이 초안에 포함하지 않는다.

## 12. 전화번호 인증 후속 검토

이 절은 인증 실행 안내에서 이동한 미구현 계획이며 현재 Git 게시 대상에서 제외한다.
가입 전에는 계정 ID가 없으므로 `phoneNumber`·임시 세션·가입 목적과 인증 요청 ID를 묶어야 한다.
서버가 난수 번호를 만들고 만료·재발송·실패 횟수·사용 여부를 관리하며 SMS 공급자는 문자 전달을 담당한다.

| 예정 경로 | 처리 |
| --- | --- |
| `POST /api/auth/phone-verifications` | 발송 요청 ID·만료 시각·재발송 가능 시각 반환 |
| `POST /api/auth/phone-verifications/{id}/confirm` | 세션·전화번호·목적·횟수와 번호 검사 |
| `POST /api/auth/sign-up`의 검증 ID | 같은 전화번호의 확인 결과를 한 번만 소비 |

3분 만료·60초 재발송 간격·5회 입력 제한은 검토할 기본값이다. 번호·IP별 발송량과 전체 비용 한도가 필요하다.
짧은 인증번호 원문과 단순 해시 대신 서버 비밀키 기반 HMAC 등을 사용한다.
실제 발송은 서비스 계정·발신번호 등록·키·비용 한도를 준비한 뒤 연결한다.
[Naver Cloud SENS 공식 발송 안내](https://api.ncloud-docs.com/docs/sens-sms-send)를 후보로 검토했으며 SMS API와 인증 테이블은 아직 없다.
