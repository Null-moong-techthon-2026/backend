# 부스럭 Backend

대학 축제의 **부스 모집부터 지도 배치·현장 운영까지 연결하는 서비스**, 부스럭의 백엔드입니다.

주최자·부스 운영자·스태프·방문객이 공통 API 서버를 통해 행사 정보를 관리하고 확인합니다. 핵심 기능 구현과 AWS 시연 배포를 완료했으며, 프론트엔드 소스는 별도 관리합니다.

## 주요 기능

| 사용자 | 기능 |
| --- | --- |
| 플랫폼 관리자 | 행사 개설 신청 승인·반려 |
| 주최자 | 모집·초대, 부스 신청·서류 심사·승인 취소, 지도·공지, 스태프 근무·출퇴근 관리 |
| 부스 운영자 | 참가 신청·서류 제출, 부스 소개·사진·상품·가격·재고 상태 관리 |
| 스태프 | 초대 참여, 일정·공지 조회, 하루 여러 구간 출근·퇴근 |
| 방문객 | 공개 행사·부스·메뉴·재고 상태·지도·공지 조회 |

재고는 충분·부족·품절·무제한 상태로 관리합니다. 사진과 PDF 서류·첨부는 S3에 저장하며 조회 권한을 검사합니다. 결제·정산, 후원·통계, 계정 수정·인증 확장은 후속 범위입니다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4.1.1, Spring Security |
| 데이터 | PostgreSQL 17, Spring Data JPA, Spring JDBC, Flyway |
| 인프라 | AWS EC2·RDS·S3, Docker Compose, Caddy |
| API 문서·빌드 | OpenAPI·Swagger UI, Gradle |

```text
사용자 화면 → 프론트 /api 프록시 → Spring Boot REST API
                                 ├─ PostgreSQL / AWS RDS
                                 └─ 파일 저장 / AWS S3
```

## 실행

JDK 21과 실행 중인 Docker가 필요합니다. 저장소 루트에서 실행합니다. Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용합니다.

```bash
docker compose up -d --wait db
./gradlew bootRun
```

- API: http://127.0.0.1:8080
- Swagger UI: http://127.0.0.1:8080/swagger-ui/index.html
- 로컬 DB: `127.0.0.1:5433` / DB `boothrock` / user `boothrock_local` / password `1234` (로컬 개발 전용)
- 기본 프로필은 `local`이며 Flyway가 스키마를 구성합니다. 운영 자격 증명과 파일은 저장소에 포함하지 않습니다.

## 빌드

```bash
./gradlew --no-daemon bootJar
```

빌드 결과는 `build/libs/app.jar`입니다. 이 저장소는 실행·배포용 구성이며, 테스트 코드와 시연 데이터 생성 도구는 배포 범위에 포함하지 않습니다.

## 연결·설정

- API 명세는 실행 중인 서버의 Swagger UI에서 확인합니다.
- 인증은 세션 쿠키 기반이며 요청에 `credentials: 'include'`를 사용합니다.
- 변경 요청 전 `GET /api/auth/csrf`로 조회한 토큰을 `X-CSRF-TOKEN`에 넣습니다. 로그인 후 토큰을 다시 조회합니다.
- 프론트의 `/api` 전달 설정과 허용 Origin을 맞춥니다. 로컬 기본 Origin은 `http://127.0.0.1:5173`입니다.
- AWS 설정은 `.env.staging.example`, `.env.proxy.example`을 참고합니다. 실제 환경 파일은 별도로 관리합니다.
- AWS 업무 API는 `compose.staging.yaml`과 `compose.staging-https.yaml`을 함께 사용해 `staging` 프로필로 실행합니다.
