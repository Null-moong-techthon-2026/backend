# 부스럭 백엔드

행사 운영 서비스 부스럭의 백엔드. **`local` 프로필에서 인증, 행사·모집, 부스 신청·심사, 운영 현황, 지도, 공지 API 테스트 가능.** 같은 PC의 프론트 개발 서버(`http://127.0.0.1:5173`)에 로컬 CORS 허용. 배포용 인증과 파일 저장소는 미구현.

## 로컬 실행

**Docker는 DB만 실행. 서버는 로컬 JDK 21로 실행.** PostgreSQL·Gradle·DBeaver 별도 설치 및 `.env` 파일 불필요.

### 1. 준비

- Git, **JDK 21**, Docker와 Docker Compose 설치
- macOS/Windows: Docker Desktop 실행 후 준비 완료 확인. Linux: Docker 엔진 실행
- 아래 명령 확인. Java 버전은 21

```bash
git --version
java -version
docker compose version
docker info
```

첫 실행 시 PostgreSQL 이미지와 Gradle·라이브러리 다운로드. 인터넷 연결 필요.

### 2. 저장소 받기

```bash
git clone https://github.com/Null-moong-techthon-2026/backend.git
cd backend
```

이미 저장소가 있다면 복제 생략. 이후 명령은 모두 `backend` 폴더에서 실행.

### 3. DB 실행

```bash
docker compose up -d --wait db
docker compose ps
```

`db` 상태가 `healthy`이면 준비 완료. DB 주소: `127.0.0.1:5433`.
**테이블은 서버 첫 실행 시 자동 생성. 수동 SQL 입력 불필요.**

### 4. 서버 실행

macOS/Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

`Started BoothrockApiApplication` 로그 확인 후 서버 사용 가능. 실행 터미널 유지.
기본 프로필: `local`. 서버 주소: `http://127.0.0.1:8080`.

### 5. 실행 확인

브라우저에서 아래 주소로 확인. DB 내부 조회는 선택 사항.

| 확인 | 기본 주소 |
| --- | --- |
| Swagger UI | [Swagger 열기](http://127.0.0.1:8080/swagger-ui/index.html) |
| 서버 상태 | [System Status](http://127.0.0.1:8080/api/system/status) |
| DB 연결 상태 | [Readiness](http://127.0.0.1:8080/actuator/health/readiness) |
| OpenAPI JSON | [API 명세](http://127.0.0.1:8080/v3/api-docs) |

Swagger와 API 호출에는 같은 호스트 사용. `127.0.0.1`과 `localhost` 혼용 시 세션 쿠키가 공유되지 않음.

새 DB에는 계정 없음. 로그인 테스트 전 아래 Swagger 절차로 회원가입 필요.

### 6. 프론트엔드 연동 (로컬)

이 저장소에는 **백엔드만** 포함. 프론트 프로젝트 실행은 검증 범위 밖. 브라우저 연동 조건은 아래와 같음.

| 항목 | 기본값 / 규칙 |
| --- | --- |
| API 주소 | `http://127.0.0.1:8080/api` |
| 허용 프론트 출처 | `http://127.0.0.1:5173` 한 곳. `local`에서만 쿠키 포함 CORS 허용 |
| 인증 | 서버 세션 쿠키. 모든 `fetch` 요청에 `credentials: 'include'` 사용 |
| 변경 요청 | 먼저 `GET /api/auth/csrf` 후 반환된 토큰을 `X-CSRF-TOKEN` 헤더에 전송. **로그인 후 토큰 재조회** |
| 이미지 | `readUrl`, `imageUrl`, `posterUrl`은 `/api/...` 상대 경로. 다른 출처에서 사용할 때 API 주소의 출처를 앞에 붙임 |
| ID·동시 수정 | 실제 UUID를 경로에 넣고, 수정 요청에는 직전 조회 결과의 `revision` 사용 |

Vite 실행 예: `npm run dev -- --host 127.0.0.1`. `localhost:5173`에서 `127.0.0.1:8080` 직접 호출 시 쿠키 사이트 정책과 허용 출처가 다름. 프론트 포트 변경 시 백엔드 실행 전에 `FRONTEND_ORIGIN=http://127.0.0.1:<포트>` 설정. 초대 링크 사용 시 `FRONTEND_INVITE_BASE_URL=http://127.0.0.1:<포트>/invite`도 설정. 백엔드 포트 변경 시 프론트 API 주소도 동일하게 변경.

```js
const API = 'http://127.0.0.1:8080';
const csrf = await fetch(`${API}/api/auth/csrf`, { credentials: 'include' }).then(r => r.json());
await fetch(`${API}/api/auth/login`, {
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token },
  body: JSON.stringify({ loginId, password }),
});
// 로그인 후 GET /api/auth/csrf를 다시 호출해 변경 요청에 사용할 토큰 조회.
```

현재 요청·응답 계약: [OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs). 구현 순서와 필수 필드: [화면 흐름 테스트 안내](docs/WORKFLOW_TEST.md). 서버는 `127.0.0.1`에만 바인딩되므로 다른 PC에서는 백엔드와 DB를 각각 로컬 실행해야 함. 현재 `deploy` 프로필에서는 업무·인증 API 미노출.

### 종료와 다시 실행

서버 터미널에서 `Ctrl+C`로 종료 후 DB 중지:

```bash
docker compose stop db
```

재실행 시 Docker 시작 후 **3번(DB 실행) → 4번(서버 실행)** 반복. 기존 데이터 유지.

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

포트 변경 시 Swagger 주소의 포트도 변경. DB 시작 실패가 계속되면 데이터를 삭제하지 말고 로그 확인.

## 현재 구현 범위

| 항목 | 상태 |
| --- | --- |
| 개인 회원가입·아이디 중복 확인 | 구현 완료. DB UNIQUE 제약으로 중복 방지 |
| 로그인·내 정보·로그아웃 | 구현 완료. Spring Security 세션 쿠키 사용 |
| 비밀번호 저장 | BCrypt 해시 사용. 로그인 아이디는 원문 저장 |
| 계정·행사·부스 DB 구조 | Flyway V1/V2/V3 적용, `app` 스키마 사용 |
| 조직 생성·초대·관리 권한 | 로컬 테스트 조직 생성만 제공. 회원가입으로 OWNER 소속을 생성하지 않음 |
| 로컬 DB 개수 조회·인증 데이터 삭제 | 로컬 테스트 전용 API 제공. 행사 데이터가 있으면 삭제는 409 |
| 행사·모집·부스·운영·지도·공지 업무 API | `local` 프로필에서 구현·통합 테스트. [화면 흐름 안내](docs/WORKFLOW_TEST.md) 참조 |
| 전화번호·이메일 인증, 비밀번호 재설정 | 구현 전 |
| 프론트엔드 CORS | `local`에서 `http://127.0.0.1:5173` 허용. 쿠키 포함 요청 가능 |
| 공개 운영 인증·배포 | 구현 전. `deploy` 프로필에서는 업무 API 미노출 |

`onboardingType`은 가입 후 화면 선택용. 조직 소속이나 권한을 부여하지 않으며 계정 권한으로 저장하지 않음.
인증·업무·테스트 API와 Swagger는 `local`에서만 활성화. `deploy` 또는 `local+deploy`에서는 해당 경로 미노출.

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
| `DELETE /api/dev/auth-data?confirmation=DELETE_LOCAL_DATA` | 행사 데이터가 없을 때만 로컬 계정·조직 데이터 삭제 |

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

운영자 화면 선택 시 `onboardingType`을 `OPERATOR`로 변경.
`organizationName`, `organizationId`, `role` 등 정의되지 않은 필드는 400 응답.
Swagger 예시는 입력값일 뿐이며 가입 요청 실행 시 계정 생성. 자동 더미 생성 없음.

## Swagger로 테스트

1. `GET /api/auth/csrf` 실행 후 응답의 `token` 복사. 세션 쿠키는 브라우저에 자동 저장.
2. `POST /api/auth/sign-up`에서 예시 선택 후 `X-CSRF-TOKEN`에 토큰 입력. 성공 **201**, 동일 아이디 재가입 **409**.
3. `GET /api/dev/auth-data`로 계정·로그인 정보 개수 증가 확인. 조직·소속은 증가하지 않음.
4. `POST /api/auth/login`에 가입한 아이디·비밀번호와 같은 CSRF 토큰 입력. 성공 **200**.
5. `GET /api/me`로 로그인 계정 정보 확인.
6. CSRF 토큰 재조회 후 `POST /api/auth/logout` 실행. 성공 **204**, 이후 `GET /api/me`는 **401**.

로그인 성공 시 세션 ID와 CSRF 토큰 교체. 토큰 없는 변경 요청은 거부.
로그인·로그아웃은 서버 메모리의 세션만 변경하므로 DB 행 수는 그대로 유지.
curl 예시: [인증 실행 안내](docs/AUTH_START.md). 행사부터 방문객 조회까지의 수동 검증: [화면 흐름 테스트 안내](docs/WORKFLOW_TEST.md).

DB 직접 조회: [DBeaver·SQL 안내](docs/local-db/README.md) (선택 사항).

## 테스트 명령

전체 자동 테스트와 JAR 빌드(Docker 필요):

```bash
./gradlew check bootJar
```

Docker 없이 컴파일·단위 테스트만 실행: `./gradlew test`. `integrationTest`는 Docker에 별도의 임시 PostgreSQL을 생성하며 실제 개발 DB와 분리.
PostgreSQL 통합 테스트 범위: 인증, 행사 생성·모집·신청/승인, 부스 운영, 핀 지도, 공지, 공개 범위.
JAR 결과물: `build/libs/app.jar`.

실행 중인 로컬 서버의 HTTP 흐름 검증에는 `curl`·`jq` 필요.

```bash
bash scripts/test-auth.sh
# 다른 포트: bash scripts/test-auth.sh http://127.0.0.1:8081
```

검증 항목: CSRF 없는 가입 거부, 조직·권한 필드 거부, 가입, 중복 가입 거부, 로그인, 내 정보, 로그아웃.
매 실행마다 고유 아이디의 개인 계정 1개 생성·유지. 조직·소속 생성 및 기존 데이터 전체 삭제 없음.

## 종료·데이터 초기화

서버 종료: 실행 터미널에서 `Ctrl+C`.

```bash
docker compose stop db
# 컨테이너 제거, 데이터 볼륨은 보존
docker compose down
```

DB 데이터는 named volume에 저장되며 재실행 후에도 유지.
Flyway는 새 migration만 적용. Hibernate의 `ddl-auto=validate`는 테이블을 재생성하지 않음.
Compose 설정값을 바꿔도 기존 볼륨의 DB 비밀번호는 자동 변경되지 않음.

행사가 없는 초기 인증 실험에서만 로컬 DELETE API에 새 CSRF 토큰과 `confirmation=DELETE_LOCAL_DATA`를 전달하거나 [삭제 SQL](docs/local-db/clear-auth-data.sql) 실행.
행사 데이터가 있으면 DELETE API는 **409**를 반환하고 계정·조직은 유지. 업무 데이터는 삭제하지 않음.
`docker compose down -v`는 DB 볼륨까지 삭제. 완전 초기화가 필요한 경우에만 사용.

## 코드 구성

```text
src/main/java/kr/boothrock/api/
  auth/
    controller/    요청·응답 및 오류 처리
    dto/           가입·로그인 계약
    entity/        계정·로그인 정보·조직·소속 매핑
    repository/    DB 조회
    service/       가입·인증·세션 처리
  event/           행사·모집·초대·대시보드
  booth/           프로필·신청 심사·부스·운영·상품
  map/             이미지·지도 초안·핀·게시
  announcement/    공지 작성·대상별 읽기
  common/          SQL 및 업무 오류·권한
  config/          보안·OpenAPI 설정
  dev/             로컬 데이터 확인·초기화
  system/          서버 상태 확인
src/main/resources/db/migration/  실제 앱 DB migration
src/test/                        자동 테스트
scripts/test-auth.sh              로컬 HTTP 검증
docs/AUTH_START.md                인증 테스트 안내
docs/WORKFLOW_TEST.md             화면 흐름 테스트 안내
docs/local-db/                   DBeaver SQL
```

`Dockerfile`: 서버 이미지 빌드 구성. 이미지 기본 프로필은 `deploy`이며 현재 인증·업무 API 미노출.
적용된 migration 파일은 수정하지 않고 변경 시 새 migration 추가.
