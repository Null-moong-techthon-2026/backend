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
| `DELETE /api/dev/auth-data?confirmation=DELETE_LOCAL_DATA` | 행사 데이터가 없을 때만 로컬 계정·조직 데이터 삭제, 204 또는 409 |

현재 공개 범위는 기본 실행 프로필인 `local`의 127.0.0.1 서버입니다. `deploy` 또는 `local+deploy`에서는 인증·테스트 경로를 열지 않습니다.
자동 데모 데이터 생성은 제거했습니다. 서버 재시작은 기존 데이터를 보존하며 새 계정을 만들지 않습니다.
계정을 사용하려면 Swagger의 주최자/운영자 가입 예시로 먼저 회원가입합니다.

DB·서버 실행 방법은 [프로젝트 README](../README.md)의 처음 실행하기를 참고합니다.
DBeaver 설치나 SQL 실행은 필요하지 않습니다. DB 내부 확인이 필요한 경우에만 [DBeaver·SQL 안내](local-db/README.md)를 참고합니다.

## Swagger에서 직접 테스트

1. `GET /api/auth/csrf`를 실행하고 응답의 `token`을 복사합니다.
2. `POST /api/auth/sign-up`의 예시 선택 메뉴에서 주최자 또는 부스 운영자를 선택합니다. `X-CSRF-TOKEN`에 토큰을 넣고 실행합니다. 성공은 201이며 같은 아이디의 재가입은 409입니다.
3. `GET /api/dev/auth-data`를 실행해 계정·로그인 정보 개수가 증가했는지 확인합니다. 두 가입 유형 모두 조직·소속·관리 권한을 생성하지 않습니다.
4. `POST /api/auth/login`에서 가입한 유형의 로그인 예시를 선택합니다. 같은 토큰으로 실행하고 성공 200을 확인합니다.
5. `GET /api/me`를 실행해 로그인한 계정 정보를 확인합니다.
6. CSRF 토큰을 다시 조회하고 `POST /api/auth/logout`을 실행합니다. 성공 204 후 `GET /api/me`는 401입니다.

Swagger 입력 예시는 DB에 미리 들어 있는 계정이 아닙니다. 같은 브라우저에서 `127.0.0.1`로 주소를 통일해야 세션이 유지됩니다.

행사 생성 전 인증 데이터만 초기화해야 할 때 새 CSRF 토큰으로 `DELETE /api/dev/auth-data?confirmation=DELETE_LOCAL_DATA`를 실행합니다.
행사 데이터가 있으면 409로 거절합니다. 성공 204일 때만 로컬 계정·로그인 정보·조직·소속이 삭제되며 `GET /api/dev/auth-data`로 확인할 수 있습니다.
삭제 후 기존 세션은 로그아웃하거나 서버를 재시작하고 다시 가입·로그인합니다. 일반 인증 테스트에는 이 삭제가 필요하지 않습니다.

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
행사·부스 ID는 대상 선택에 사용하며 현재 업무 API는 매 요청에서 계정의 소속과 권한을 검사합니다.
요청자가 `accountId`나 `role`을 보내도 본인 확인 또는 OWNER 권한의 근거가 아닙니다.

중복확인은 ID를 예약하지 않습니다. 가입 때 다시 확인하고 DB의 UNIQUE가 동시 요청을 막습니다.
비밀번호 원문은 DB와 응답에 저장하지 않고 BCrypt 해시만 저장합니다.
전화번호와 이메일은 아직 소유 인증 없이 연락처로 저장됩니다. 관리자 자격도 확인되지 않습니다.
로컬 프론트 출처 `http://127.0.0.1:5173`의 쿠키 포함 CORS는 지원합니다. 프론트 요청에는 `credentials: 'include'`가 필요하고 로그인 후 CSRF 토큰을 다시 받아야 합니다. 로그인 시도 제한, 비밀번호 재설정, 조직 인계, 배포 환경의 쿠키/CORS는 후속 작업입니다.
