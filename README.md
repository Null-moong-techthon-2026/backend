# 부스럭 백엔드

행사 운영 서비스 부스럭의 백엔드입니다. **2026-10-03 현재 개인 계정 회원가입·로그인·로그아웃과 실제 PostgreSQL 연결까지 구현했습니다.** 행사·부스·지도 API는 구현 전입니다.

## 현재 구현 범위

| 항목 | 상태 |
| --- | --- |
| 개인 회원가입·아이디 중복 확인 | 구현 완료. DB UNIQUE 제약으로 중복 방지 |
| 로그인·내 정보·로그아웃 | 구현 완료. Spring Security 세션 쿠키 사용 |
| 비밀번호 저장 | BCrypt 해시 사용. 로그인 아이디는 원문 저장 |
| 계정·조직 DB 구조 | Flyway V1/V2 적용, `app` 스키마 사용 |
| 조직 생성·초대·관리 권한 | 구현 전. 회원가입으로 조직이나 OWNER 소속을 생성하지 않음 |
| 로컬 DB 개수 조회·전체 삭제 | 로컬 테스트 전용 API 제공 |
| 행사·부스·지도·공지 업무 API | 구현 전 |
| 전화번호·이메일 인증, 비밀번호 재설정 | 구현 전 |
| 프론트엔드 CORS·공개 운영 인증 | 구현 전 |

`onboardingType`은 가입 후 화면 선택용이며 조직 소속이나 권한을 부여하지 않습니다. 현재 계정 권한으로 저장하지 않습니다.
인증·테스트 API와 Swagger는 `local`에서만 활성화됩니다. `deploy` 또는 `local+deploy`에서는 인증·테스트 경로를 열지 않습니다.

## 실행 환경

- JDK 21
- Docker 및 Docker Compose
- Spring Boot 4.1.1 / Gradle Wrapper 9.7.1
- PostgreSQL 17 / Flyway / Spring Data JPA
- Swagger UI: springdoc-openapi

별도 Gradle 설치는 필요 없습니다. 기본 로컬 실행에는 `.env` 파일도 필요 없습니다.

## 로컬 실행

저장소를 처음 받는 경우:

```bash
git clone https://github.com/Null-moong-techthon-2026/backend.git
cd backend
```

백엔드 폴더에서 DB를 실행한 다음 서버를 실행합니다.

```bash
docker compose up -d --wait db
./gradlew bootRun
```

기본 프로필은 `local`입니다. Windows에서는 `gradlew.bat bootRun`을 사용합니다.
8080 포트를 이미 사용 중이면 기존 실행 서버를 종료하거나 다른 포트로 실행합니다.

```bash
./gradlew bootRun --args='--server.port=8081'
```

| 확인 | 기본 주소 |
| --- | --- |
| Swagger UI | [Swagger 열기](http://127.0.0.1:8080/swagger-ui/index.html) |
| 서버 상태 | [System Status](http://127.0.0.1:8080/api/system/status) |
| DB 연결 상태 | [Readiness](http://127.0.0.1:8080/actuator/health/readiness) |
| OpenAPI JSON | [API 명세](http://127.0.0.1:8080/v3/api-docs) |

Swagger와 API 호출은 같은 브라우저에서 같은 호스트를 사용합니다. 예를 들어 `127.0.0.1`과 `localhost`를 혼용하면 세션 쿠키가 공유되지 않습니다.

## 인증 API

| 메서드·경로 | 용도 |
| --- | --- |
| `GET /api/auth/csrf` | CSRF 토큰과 헤더명 조회 |
| `GET /api/auth/login-id-availability?loginId=...` | 아이디 사용 가능 여부 확인 |
| `POST /api/auth/sign-up` | 개인 계정 생성, `accountId` 반환 |
| `POST /api/auth/login` | 세션 로그인, `id`·`nickname` 반환 |
| `GET /api/me` | 현재 로그인한 본인 정보 조회 |
| `POST /api/auth/logout` | 세션 종료 |
| `GET /api/dev/auth-data` | 현재 연결한 DB 이름·테이블별 행 수·조회 시각 확인 |
| `DELETE /api/dev/auth-data?confirmation=DELETE_LOCAL_DATA` | 로컬 계정·조직 데이터 전체 삭제 |

회원가입 예시:

```json
{
  "onboardingType": "ORGANIZER",
  "loginId": "festival_admin",
  "password": "DevOnly!2026",
  "nickname": "축제 운영자",
  "phoneNumber": "01000000001",
  "email": "organizer.test@example.com"
}
```

운영자 화면을 선택하려면 `onboardingType`을 `OPERATOR`로 바꿉니다.
`organizationName`, `organizationId`, `role` 등 정의하지 않은 필드는 400으로 거절합니다.
Swagger의 예시는 입력값이며, 계정은 가입 요청을 실행해야 생성됩니다. 자동 더미 생성은 없습니다.

## Swagger로 테스트

1. `GET /api/auth/csrf`를 실행하고 응답의 `token`을 복사합니다. 세션 쿠키는 브라우저에 자동 저장됩니다.
2. `POST /api/auth/sign-up`에서 예시를 선택하고 `X-CSRF-TOKEN`에 토큰을 넣어 실행합니다. 성공은 **201**, 같은 아이디로 재가입하면 **409**입니다.
3. `GET /api/dev/auth-data`로 계정·로그인 정보 개수가 증가했는지 확인합니다. 조직·소속은 증가하지 않습니다.
4. `POST /api/auth/login`에 가입한 아이디·비밀번호와 같은 CSRF 토큰을 넣습니다. 성공은 **200**입니다.
5. `GET /api/me`를 실행해 로그인한 계정 정보를 확인합니다.
6. CSRF 토큰을 다시 조회한 뒤 `POST /api/auth/logout`을 실행합니다. 성공은 **204**, 이후 `GET /api/me`는 **401**입니다.

로그인 성공 시 세션 ID와 CSRF 토큰이 교체됩니다. 토큰 없이 변경 요청을 보내면 거부됩니다.
로그인·로그아웃은 서버 메모리의 세션을 변경하므로 DB 행 수가 바뀌지 않습니다.
자세한 curl 예시는 [인증 실행 안내](docs/AUTH_START.md)에 있습니다.

## DBeaver로 확인

| 항목 | 기본값 |
| --- | --- |
| Driver | PostgreSQL |
| Host / Port | `127.0.0.1` / `5433` |
| Database / Schema | `boothrock` / `app` |
| Username / Password | `boothrock_local` / `1234` |

위 자격증명은 loopback에만 노출하는 로컬 개발 DB의 공개 테스트 값입니다.
Docker 내부 PostgreSQL 포트는 5432이며 외부에서는 5433으로 연결합니다.

`Schemas → app → Tables`에서 `accounts`, `local_credentials`, `organizations`, `organization_memberships`, `flyway_schema_history`를 확인합니다.
신규 회원가입은 계정·로그인 정보 두 테이블에만 저장됩니다. 계정의 `id`는 UUID이고 비밀번호는 `{bcrypt}...` 해시로 저장됩니다.

- [계정·조직 조회 SQL](docs/local-db/check-auth-data.sql)
- [계정·조직 전체 삭제 SQL](docs/local-db/clear-auth-data.sql)

가입 **201** 또는 삭제 **204** 후 데이터 탭을 새로고침하거나 조회 SQL을 다시 실행합니다. 열어둔 결과는 자동으로 갱신되지 않습니다.
API의 개수와 다르면 연결 대상·조회 필터를 확인합니다. 오래 열린 REPEATABLE READ/SERIALIZABLE 트랜잭션이라면 미저장 편집을 확인하고 트랜잭션을 종료한 뒤 재조회합니다.

## 테스트 명령

자동 테스트와 JAR 빌드:

```bash
./gradlew test integrationTest bootJar
```

`integrationTest`는 Docker에 별도의 임시 PostgreSQL을 생성하므로 실제 개발 DB와 분리됩니다.
현재 16개 테스트로 계정 저장·중복 거절·세션·CSRF·조직 권한 입력 거부·데이터 초기화를 검증합니다.
JAR 결과물은 `build/libs/app.jar`입니다.

실행 중인 로컬 서버의 실제 HTTP 흐름을 확인하려면 `curl`·`jq`가 필요합니다.

```bash
bash scripts/test-auth.sh
# 다른 포트: bash scripts/test-auth.sh http://127.0.0.1:8081
```

스크립트는 CSRF 없는 가입 거부, 조직·권한 필드 거부, 가입, 중복 가입 거부, 로그인, 내 정보, 로그아웃을 확인합니다.
매 실행마다 고유 아이디의 개인 계정 1개를 만들고 DBeaver 확인을 위해 남깁니다. 조직·소속을 생성하거나 기존 데이터를 전체 삭제하지 않습니다.

## 종료·데이터 초기화

서버는 실행 터미널에서 `Ctrl+C`로 종료합니다.

```bash
docker compose stop db
# 컨테이너 제거, 데이터 볼륨은 보존
docker compose down
```

DB는 named volume에 저장되어 재실행해도 데이터가 유지됩니다.
Flyway는 새 migration만 적용하며 Hibernate의 `ddl-auto=validate`는 테이블을 재생성하지 않습니다.
기존 볼륨의 DB 비밀번호는 Compose 값을 바꿔도 자동 변경되지 않습니다.

데이터 행만 비우려면 로컬 DELETE API에 새 CSRF 토큰과 `confirmation=DELETE_LOCAL_DATA`를 넣거나 위 삭제 SQL을 실행합니다.
이 작업은 **모든 계정·로그인 정보·조직·소속을 삭제**하지만 테이블 구조와 migration 이력은 유지합니다.
`docker compose down -v`는 DB 볼륨까지 삭제하므로 완전 초기화가 필요한 경우에만 사용합니다.

## 코드 구성

```text
src/main/java/kr/boothrock/api/
  auth/
    controller/    요청·응답 및 오류 처리
    dto/           가입·로그인 계약
    entity/        계정·로그인 정보·조직·소속 매핑
    repository/    DB 조회
    service/       가입·인증·세션 처리
  config/          보안·OpenAPI 설정
  dev/             로컬 데이터 확인·초기화
  system/          서버 상태 확인
src/main/resources/db/migration/  실제 앱 DB migration
src/test/                        자동 테스트
scripts/test-auth.sh              로컬 HTTP 검증
docs/AUTH_START.md                인증 테스트 안내
docs/local-db/                   DBeaver SQL
```

`Dockerfile`은 서버 이미지 빌드 구성을 제공합니다. 이미지의 기본 프로필은 `deploy`이며 현재 인증·테스트 API는 공개하지 않습니다.
적용된 migration 파일은 수정하지 않고 변경 시 새 migration을 추가합니다.
