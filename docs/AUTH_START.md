# DB 기반 인증 시작 안내

2026-10-03. 인증 코드는 `auth/controller`, `dto`, `entity`, `repository`, `service`에 나뉘어 있습니다.
로컬 앱 DB(`app` 스키마)에 가입 정보가 저장됩니다. `/api/v1`은 사용하지 않습니다.

| 메서드·경로 | 현재 동작 |
| --- | --- |
| `GET /api/auth/csrf` | CSRF 헤더명·토큰을 반환하고 세션 쿠키 설정 |
| `GET /api/auth/login-id-availability?loginId=...` | 형식 확인, DB 존재 여부로 `available` 반환 |
| `POST /api/auth/sign-up` | 개인 계정·로그인 정보 생성, `accountId` 반환, 201 |
| `POST /api/auth/login` | DB의 비밀번호 해시 비교, 새 세션 쿠키와 `id`, `nickname` 반환 |
| `GET /api/me` | 세션 계정 ID로 DB에서 본인 `id`, `nickname`, `phoneNumber`, `email` 조회 |
| `POST /api/auth/logout` | 세션·인증·CSRF 제거, 204 |
| `GET /api/dev/auth-data` | 현재 연결한 DB 이름, 테이블별 행 수, 조회 시각 반환 |
| `DELETE /api/dev/auth-data?confirmation=DELETE_LOCAL_DATA` | 로컬 계정·조직 데이터 전체 삭제, 204 |

현재 공개 범위는 기본 실행 프로필인 `local`의 127.0.0.1 서버입니다. `deploy` 또는 `local+deploy`에서는 인증·테스트 경로를 열지 않습니다.
자동 데모 데이터 생성은 제거했습니다. 서버 재시작은 기존 데이터를 보존하며 새 계정을 만들지 않습니다.
계정을 사용하려면 Swagger의 주최자/운영자 가입 예시로 먼저 회원가입합니다.

## DBeaver에서 보는 위치

| 항목 | 값 |
| --- | --- |
| Host / Port | `127.0.0.1` / `5433` |
| Database / Schema | `boothrock` / `app` |
| Username / Password | `boothrock_local` / `1234` |

Flyway의 V1은 `app` 스키마, V2는 `accounts`, `local_credentials`, `organizations`,
`organization_memberships`를 생성합니다. 로그인 ID는 DB UNIQUE 제약으로 보호됩니다.
조직과 담당자 소속은 개인 계정과 분리됩니다. 두 가입 유형 모두 조직·소속·관리 권한을 생성하지 않습니다.
`onboardingType`은 가입 후 화면 선택용입니다. 조직 생성 신청·검증과 초대 수락 API는 아직 없습니다.
기존 DB의 조직·소속 행은 이번 가입 정책 변경으로 삭제되지 않습니다.

## Swagger에서 직접 테스트

1. `GET /api/auth/csrf`를 실행하고 응답의 `token`을 복사합니다.
2. `POST /api/auth/sign-up`의 예시 선택 메뉴에서 주최자 또는 부스 운영자를 선택합니다. `X-CSRF-TOKEN`에 토큰을 넣고 실행합니다. 성공은 201이며 같은 아이디의 재가입은 409입니다.
3. DBeaver에서 [조회 SQL](local-db/check-auth-data.sql)을 실행합니다. 두 가입 유형 모두 계정·로그인 정보 두 테이블에만 저장됩니다. 조직 이름과 역할은 비어 있는 것이 정상입니다.
4. `POST /api/auth/login`에서 가입한 유형의 로그인 예시를 선택합니다. 같은 토큰으로 실행하고 성공 200을 확인합니다.
5. `GET /api/me`를 실행해 로그인한 계정 정보를 확인합니다.
6. 삭제하려면 CSRF 토큰을 다시 조회하고 `DELETE /api/dev/auth-data`의 헤더에 넣습니다. `confirmation`은 `DELETE_LOCAL_DATA`입니다. 성공 204 후 조회 SQL을 다시 실행합니다.

Swagger 입력 예시는 DB에 미리 들어 있는 계정이 아닙니다. 같은 브라우저에서 `127.0.0.1`로 주소를 통일해야 세션이 유지됩니다.
DBeaver에서 직접 삭제하려면 [삭제 SQL](local-db/clear-auth-data.sql)을 사용합니다. 테이블과 migration 이력은 남습니다.
새 계정 삽입은 비밀번호 해시까지 처리하는 회원가입 API로 진행합니다. 조직 소속은 생성하지 않습니다.
삭제 후 이전 로그인 세션은 로그아웃하고 다시 가입·로그인합니다. 서버 재시작으로도 세션을 종료할 수 있습니다.

## DBeaver 결과가 바뀌지 않을 때

- Swagger에서 `GET /api/dev/auth-data`를 실행해 현재 DB 이름과 행 수를 확인합니다.
- DBeaver SQL 편집기의 연결이 `127.0.0.1:5433`, DB `boothrock`인지 확인하고 조회 SQL을 다시 실행합니다. Docker 내부에서 반환되는 포트 `5432`는 정상입니다.
- 이미 열린 데이터 탭은 이전 조회 결과를 유지하므로 새로고침해야 합니다. 네비게이터의 테이블 목록 새로고침과 데이터 탭의 행 새로고침은 다릅니다.
- 로그인·로그아웃으로는 DB 행이 바뀌지 않습니다. 새 데이터는 회원가입 201 뒤, 삭제는 DELETE 204 뒤 확인합니다.
- API 개수와 SQL 개수가 다르면 연결 대상과 조회 필터를 확인합니다. 격리 수준이 REPEATABLE READ/SERIALIZABLE인 기존 트랜잭션이라면 미저장 편집을 확인하고 트랜잭션을 종료한 뒤 재조회합니다.

## 한 번에 인증 확인

서버 실행 후 `bash scripts/test-auth.sh`를 실행합니다. `curl`, `jq`가 필요합니다.
다른 포트는 `bash scripts/test-auth.sh http://127.0.0.1:8081`처럼 전달합니다.
고유 로그인 ID로 개인 계정을 한 개 만들어 남기며 생성된 ID를 출력합니다. 조직·소속은 만들지 않습니다.
기존 데이터를 일괄 삭제하지 않고 CSRF, 실제 가입, 중복 거절, DB 개수 증가, 로그인·내 정보·로그아웃을 확인합니다.

## 실행과 직접 호출

backend 폴더에서 JDK 21과 Docker를 준비합니다.

```bash
docker compose up -d --wait db
./gradlew bootRun --args='--spring.profiles.active=local'
```

다른 터미널에서 `curl`, `jq`로 확인합니다. 아래 가입 요청은 앱 DB에 **테스트 계정 한 개를 실제로 저장**합니다.

```bash
BASE=http://127.0.0.1:8080
COOKIE=$(mktemp)
LOGIN_ID="local_$(date +%s)"
CSRF=$(curl -fsS -c "$COOKIE" "$BASE/api/auth/csrf" | jq -r '.token')
curl -i -b "$COOKIE" -c "$COOKIE" \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $CSRF" \
  -d "{\"onboardingType\":\"ORGANIZER\",\"loginId\":\"$LOGIN_ID\",\"password\":\"DevOnly!2026\",\"nickname\":\"테스트 계정\",\"phoneNumber\":\"01000000000\",\"email\":\"test@example.com\"}" \
  "$BASE/api/auth/sign-up"
curl -fsS "$BASE/api/auth/login-id-availability?loginId=$LOGIN_ID"
curl -i -b "$COOKIE" -c "$COOKIE" \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $CSRF" \
  -d "{\"loginId\":\"$LOGIN_ID\",\"password\":\"DevOnly!2026\"}" \
  "$BASE/api/auth/login"
curl -i -b "$COOKIE" "$BASE/api/me"
CSRF=$(curl -fsS -b "$COOKIE" -c "$COOKIE" "$BASE/api/auth/csrf" | jq -r '.token')
curl -i -X POST -b "$COOKIE" -c "$COOKIE" -H "X-CSRF-TOKEN: $CSRF" "$BASE/api/auth/logout"
curl -i -b "$COOKIE" "$BASE/api/me"
rm "$COOKIE"
```

예상 순서는 가입 201 → 중복확인 `available:false` → 로그인 200 → 본인 조회 200 → 로그아웃 204 → 재조회 401입니다.
운영자 화면을 선택하려면 같은 본문에서 `onboardingType`을 `OPERATOR`로 바꿉니다.
`organizationName`, `organizationId`, `role` 등 계약에 없는 필드를 보내면 400으로 거절합니다.
CSRF 없는 POST는 거부됩니다. 실제 HTTP 테스트에서는 익명 요청이 401, MockMvc 검증에서는 403을 반환합니다.
로그인 후에는 토큰이 교체되므로 로그아웃 전에 새 토큰을 받습니다.

## 권한과 현재 경계

가입과 로그인에서 `loginId`를 사용합니다. 로그인 뒤 서버는 세션의 계정 ID를 사용합니다.
행사·부스 ID는 대상 선택에 사용하고, 향후 작업마다 그 계정의 소속과 권한을 검사해야 합니다.
요청자가 `accountId`나 `role`을 보내도 본인 확인 또는 OWNER 권한의 근거가 아닙니다.

중복확인은 ID를 예약하지 않습니다. 가입 때 다시 확인하고 DB의 UNIQUE가 동시 요청을 막습니다.
비밀번호 원문은 DB와 응답에 저장하지 않고 BCrypt 해시만 저장합니다.
전화번호와 이메일은 아직 소유 인증 없이 연락처로 저장됩니다. 관리자 자격도 확인되지 않습니다.
로그인 시도 제한, 비밀번호 재설정, 조직 인계, FE 별도 출처 쿠키/CORS는 후속 작업입니다.
