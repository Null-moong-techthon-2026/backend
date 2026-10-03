# 부스럭 백엔드

행사 운영 서비스 부스럭의 백엔드입니다. **2026-10-03 현재 개인 계정 회원가입·로그인·로그아웃과 실제 PostgreSQL 연결까지 구현했습니다.** 행사·부스·지도 API는 구현 전입니다.

## 처음 실행하기

**Docker는 DB만 실행하고, 서버는 PC의 JDK 21로 실행합니다.** PostgreSQL·Gradle·DBeaver를 별도로 설치하거나 `.env` 파일을 만들 필요는 없습니다.

### 1. 준비

- Git, **JDK 21**, Docker와 Docker Compose를 설치합니다.
- macOS/Windows에서는 Docker Desktop을 실행하고 준비가 끝날 때까지 기다립니다. Linux에서는 Docker 엔진을 실행합니다.
- 터미널에서 아래 명령이 정상 동작하는지 확인합니다. Java 버전은 21이어야 합니다.

```bash
git --version
java -version
docker compose version
docker info
```

첫 실행은 PostgreSQL 이미지와 Gradle·라이브러리를 내려받으므로 인터넷 연결이 필요하며 시간이 걸릴 수 있습니다.

### 2. 저장소 받기

```bash
git clone https://github.com/Null-moong-techthon-2026/backend.git
cd backend
```

이미 저장소를 받은 경우 새로 복제하지 않고 해당 `backend` 폴더를 사용합니다. 아래 명령도 모두 그 폴더에서 실행합니다.

### 3. DB 실행

```bash
docker compose up -d --wait db
docker compose ps
```

`db`가 `healthy`이면 준비된 것입니다. DB는 `127.0.0.1:5433`에서 실행됩니다.
**테이블은 다음 단계에서 서버가 처음 시작될 때 자동으로 생성됩니다. SQL을 직접 넣을 필요는 없습니다.**

### 4. 서버 실행

macOS/Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

`Started BoothrockApiApplication` 로그가 나오면 서버가 준비된 것입니다. 실행 터미널은 계속 열어둡니다.
기본 프로필은 `local`이고 서버 주소는 `http://127.0.0.1:8080`입니다.

### 5. 실행 확인

브라우저에서 아래 주소를 엽니다. DB 내부를 직접 확인하지 않아도 됩니다.

| 확인 | 기본 주소 |
| --- | --- |
| Swagger UI | [Swagger 열기](http://127.0.0.1:8080/swagger-ui/index.html) |
| 서버 상태 | [System Status](http://127.0.0.1:8080/api/system/status) |
| DB 연결 상태 | [Readiness](http://127.0.0.1:8080/actuator/health/readiness) |
| OpenAPI JSON | [API 명세](http://127.0.0.1:8080/v3/api-docs) |

Swagger와 API 호출은 같은 브라우저에서 같은 호스트를 사용합니다. 예를 들어 `127.0.0.1`과 `localhost`를 혼용하면 세션 쿠키가 공유되지 않습니다.

새 PC의 DB에는 계정이 없습니다. 로그인 테스트는 아래 Swagger 안내에 따라 먼저 회원가입한 뒤 진행합니다.

### 종료와 다시 실행

서버 터미널에서 `Ctrl+C`를 누른 다음 DB를 중지합니다.

```bash
docker compose stop db
```

다음에는 Docker를 실행하고 **3번(DB 실행) → 4번(서버 실행)**만 반복합니다. 기존 데이터는 유지됩니다.

### 실행이 안 될 때

| 증상 | 확인할 내용 |
| --- | --- |
| Docker daemon 연결 오류 | Docker Desktop 또는 Docker 엔진이 실행 중인지 확인 |
| Java 실행 오류 / JDK 21을 찾지 못함 | JDK 21 설치, `JAVA_HOME` 및 터미널의 `java -version` 확인 |
| `gradlew` 실행 권한 오류 (macOS/Linux) | `chmod +x gradlew` 후 다시 실행 |
| DB 연결 실패 | `docker compose ps`에서 `db`가 `healthy`인지 확인. 원인은 `docker compose logs db`로 확인 |
| 5433 포트 사용 중 | 해당 포트를 쓰는 기존 DB를 확인하고 이 프로젝트 DB와 중복 실행하지 않음 |
| 8080 포트 사용 중 | 기존 서버를 확인하거나 아래처럼 8081로 실행 |

```bash
./gradlew bootRun --args='--server.port=8081'
```

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

다른 포트를 사용하면 Swagger 주소의 포트도 바꿉니다. DB가 계속 시작되지 않을 때는 데이터를 삭제하지 말고 로그를 공유합니다.

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

기술 구성: Spring Boot 4.1.1 / Gradle Wrapper 9.7.1 / PostgreSQL 17 / Flyway / Spring Data JPA / springdoc-openapi.

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

DB 내부 확인이 필요한 백엔드 담당자는 [선택 사항: DBeaver·SQL 안내](docs/local-db/README.md)를 참고합니다.

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

데이터 행만 비우려면 로컬 DELETE API에 새 CSRF 토큰과 `confirmation=DELETE_LOCAL_DATA`를 넣거나 [삭제 SQL](docs/local-db/clear-auth-data.sql)을 실행합니다.
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
