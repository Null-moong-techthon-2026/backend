# 행사 개설 신청과 플랫폼 관리자

행사는 주최자가 직접 만들지 않습니다. 주최자가 **행사 개설 신청**을 내면 **플랫폼 관리자**가 승인·반려하고, 승인하면 행사가 열립니다.
(V4 migration: `src/main/resources/db/migration/V4__event_applications.sql`)

## 관리자 지정

가입 화면으로는 관리자가 될 수 없습니다(`POST /api/auth/sign-up`은 항상 `USER`). 가입한 계정을 DB에서 직접 지정합니다.

```sql
UPDATE app.accounts SET platform_role = 'ADMIN'
WHERE id = (SELECT account_id FROM app.local_credentials WHERE login_id = 'admin_login_id');
```

`GET /api/me`의 `platformRole`(`USER`/`ADMIN`)로 프론트가 관리자 여부를 압니다. 권한은 요청마다 DB에서 확인하므로 세션에 캐시되지 않습니다.

## API

| 메서드·경로 | 요청 / 응답 | 권한 |
| --- | --- | --- |
| `POST /api/event-applications` | 신청서 전체 → 201 신청. 검토 중인 신청이 이미 있으면 409 `DUPLICATE_APPLICATION` | 로그인 |
| `GET /api/me/event-applications?page=0&size=20` | 내 신청 목록(최신순), 반려 사유 포함 | 로그인 |
| `GET /api/admin/event-applications?status=&page=&size=` | 신청 목록 + `summary`(전체·검토 중·승인·반려 건수) | 관리자 |
| `GET /api/admin/event-applications/{id}` | 신청 상세 | 관리자 |
| `POST /api/admin/event-applications/{id}/decision` | `revision`, `decision`(`APPROVED`/`REJECTED`), 반려는 `rejectionReason` | 관리자 |

관리자가 아닌 계정은 `/api/admin/**`에서 403입니다.

신청서 필드: `orgName`, `orgType`(`COMPANY`/`STUDENT`/`PUBLIC`/`NONPROFIT`/`OTHER`), `orgEmail`, `businessNumber`(기업은 숫자 10자리 필수),
`affiliation`(학생회·공공기관은 필수), `representative`, `website`, `contactName`, `contactEmail`, `contactPhone`, `contactRole`, `eventName`,
`eventKind`(`UNIVERSITY`/`FLEA`/`POPUP`/`EXPO`/`LOCAL`/`OTHER`), `startDate`, `endDate`(`YYYY-MM-DD`), `venue`, `boothCount`, `handover`,
`paymentPlan`(`PREPAID`/`BASE_PLUS_SLOT`), `paymentMethod`(`CARD`/`TRANSFER`/`OTHER`), `receiptType`(`TAX_INVOICE`/`CASH_RECEIPT`/`NONE`),
`agreements`(`terms`, `privacy`, `outsourcing`, `billing`, `refund` 다섯 개 모두). 정의되지 않은 필드를 보내면 400입니다(상태를 신청자가
정할 수 없음).

## 승인하면 일어나는 일 (한 트랜잭션)

1. 신청자를 **OWNER**로 하는 단체를 만듭니다(이름·연락 이메일은 신청서의 조직명·조직 공식 이메일).
2. 행사를 **초안**으로 만들고 빈 모집 공고를 붙입니다. 이름·장소는 신청서 값이고, 기간은 한국 시간 기준으로 시작일 00:00부터
   종료일 23:59:59까지입니다.
3. 신청의 상태를 `APPROVED`로 바꾸고 만든 행사 id(`createdEventId`)를 기록합니다. 감사 로그가 남습니다.

그 뒤 신청자는 `GET /api/me/events`로 행사를 보고 기존 API로 관리합니다. 반려는 사유가 필수이고, 반려된 계정은 다시 신청할 수 있습니다.
같은 결정을 다시 보내면 결과만 돌려주고 행사를 또 만들지 않으며, 다른 결정은 409 `INVALID_STATE`입니다. `revision`이 다르면 409
`REVISION_CONFLICT`입니다. 승인된 계정도 나중에 새 행사를 다시 신청할 수 있습니다.

## 아직 없는 것

결제(PG 연동, 신청서의 결제 수단·증빙은 저장만), 약관·환불 규정 전문, 이메일·전화번호 인증, 신청 알림.
